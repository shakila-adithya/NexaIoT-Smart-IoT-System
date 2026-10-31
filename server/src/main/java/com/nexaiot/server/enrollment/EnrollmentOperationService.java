package com.nexaiot.server.enrollment;

import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.provisioning.PairingSessionStatus;
import com.nexaiot.server.provisioning.ProvisioningSession;
import com.nexaiot.server.provisioning.ProvisioningSessionRepository;

@Service
public class EnrollmentOperationService {

    private final DeviceRepository deviceRepository;
    private final ProvisioningSessionRepository pairingSessionRepository;
    private final DeviceEnrollmentOperationRepository operationRepository;
    private final MongoTemplate mongoTemplate;

    public EnrollmentOperationService(
            DeviceRepository deviceRepository,
            ProvisioningSessionRepository pairingSessionRepository,
            DeviceEnrollmentOperationRepository operationRepository,
            MongoTemplate mongoTemplate
    ) {
        this.deviceRepository = deviceRepository;
        this.pairingSessionRepository = pairingSessionRepository;
        this.operationRepository = operationRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Associates exactly one claimed B2 session with one durable operation.
     * The pairing token itself is never copied into this document.
     */
    public DeviceEnrollmentOperation createForClaimedSession(
            String pairingSessionId,
            String ownerEmail
    ) {
        String owner = normalize(ownerEmail);
        ProvisioningSession session = pairingSessionRepository.findById(pairingSessionId)
                .filter(candidate -> owner.equals(candidate.getOwnerEmail()))
                .orElseThrow(() -> new IllegalArgumentException("Pairing session not found."));

        if (session.getStatus() != PairingSessionStatus.CLAIMED) {
            throw new IllegalStateException("Pairing session is not claimable for enrollment.");
        }
        if (session.getDeviceId() == null
                || deviceRepository.findByIdAndOwnerEmail(session.getDeviceId(), owner).isEmpty()) {
            throw new IllegalArgumentException("Device not found.");
        }

        return operationRepository.findByPairingSessionIdAndOwnerEmail(pairingSessionId, owner)
                .orElseGet(() -> insertOperation(session, owner));
    }

    public DeviceEnrollmentOperation transition(
            String operationId,
            EnrollmentState expectedState,
            EnrollmentState targetState,
            String ownerEmail
    ) {
        if (!expectedState.canTransitionTo(targetState)) {
            throw new IllegalStateException("Illegal enrollment state transition.");
        }

        Instant now = Instant.now();
        Query query = ownerStateQuery(operationId, ownerEmail, expectedState);
        Update update = new Update()
                .set("state", targetState)
                .set("updatedAt", now)
                .inc("version", 1);
        if (targetState == EnrollmentState.BROKER_CLIENT_PENDING && expectedState == EnrollmentState.REQUESTED) {
            update.set("startedAt", now);
        }
        if (targetState == EnrollmentState.COMPLETED) {
            update.set("completedAt", now)
                    .unset("activeOperationKey");
        }
        if (targetState == EnrollmentState.CANCELLED) {
            update.unset("activeOperationKey");
        }

        DeviceEnrollmentOperation transitioned = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                DeviceEnrollmentOperation.class);
        if (transitioned == null) {
            throw new EnrollmentConcurrencyException(
                    "Enrollment operation changed concurrently or is not owner-accessible.");
        }
        return transitioned;
    }

    public DeviceEnrollmentOperation recordBrokerIdentity(
            String operationId,
            String ownerEmail,
            String brokerUsername,
            String brokerClientId,
            String brokerRoleName,
            String ownershipMarker
    ) {
        Instant now = Instant.now();
        Query query = ownerStateQuery(operationId, ownerEmail, EnrollmentState.BROKER_CLIENT_PENDING);
        Update update = new Update()
                .set("state", EnrollmentState.BROKER_CLIENT_CREATED)
                .set("brokerUsername", brokerUsername)
                .set("brokerClientId", brokerClientId)
                .set("brokerRoleName", brokerRoleName)
                .set("brokerResourceOwnershipMarker", ownershipMarker)
                .set("updatedAt", now)
                .inc("version", 1);
        return cas(query, update);
    }

    public DeviceEnrollmentOperation markBrokerVerified(
            String operationId,
            String ownerEmail
    ) {
        Query query = ownerStateQuery(operationId, ownerEmail, EnrollmentState.ACL_ASSIGNED);
        Update update = new Update()
                .set("state", EnrollmentState.BROKER_VERIFIED)
                .set("brokerIdentityVerified", true)
                .set("updatedAt", Instant.now())
                .inc("version", 1);
        return cas(query, update);
    }

    public DeviceEnrollmentOperation markBrokerTimeout(
            String operationId,
            String ownerEmail,
            EnrollmentState expectedState
    ) {
        if (!expectedState.canTransitionTo(EnrollmentState.RECONCILIATION_REQUIRED)) {
            throw new IllegalStateException("State cannot enter reconciliation.");
        }
        return markReconciliationRequired(operationId, ownerEmail, expectedState,
                "BROKER_OPERATION_UNKNOWN_OUTCOME");
    }

    public DeviceEnrollmentOperation markReconciliationRequired(
            String operationId,
            String ownerEmail,
            EnrollmentState expectedState,
            String failureCode
    ) {
        if (!expectedState.canTransitionTo(EnrollmentState.RECONCILIATION_REQUIRED)) {
            throw new IllegalStateException("State cannot enter reconciliation.");
        }
        Query query = ownerStateQuery(operationId, ownerEmail, expectedState);
        Update update = new Update()
                .set("state", EnrollmentState.RECONCILIATION_REQUIRED)
                .set("reconciliationRequired", true)
                .set("lastFailureCode", failureCode)
                .set("updatedAt", Instant.now())
                .inc("version", 1);
        return cas(query, update);
    }

    public DeviceEnrollmentOperation markFailed(
            String operationId,
            String ownerEmail,
            EnrollmentState expectedState,
            String failureCode
    ) {
        if (!expectedState.canTransitionTo(EnrollmentState.FAILED)) {
            throw new IllegalStateException("State cannot be marked failed.");
        }
        Query query = ownerStateQuery(operationId, ownerEmail, expectedState);
        Update update = new Update()
                .set("state", EnrollmentState.FAILED)
                .set("lastFailureCode", failureCode)
                .set("updatedAt", Instant.now())
                .inc("version", 1);
        return cas(query, update);
    }

    public DeviceEnrollmentOperation recordRecoveryAssessment(
            String operationId,
            String ownerEmail,
            int expectedVersion,
            RecoveryAssessment assessment
    ) {
        if (assessment == null) {
            throw new IllegalArgumentException("Recovery assessment is required.");
        }
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(operationId),
                Criteria.where("ownerEmail").is(normalize(ownerEmail)),
                Criteria.where("version").is(expectedVersion)
        ));
        Update update = new Update()
                .set("lastRecoveryDisposition", assessment.outcome().name())
                .set("lastRecoveryReason", assessment.reason())
                .set("updatedAt", Instant.now())
                .inc("version", 1);
        return cas(query, update);
    }

    public DeviceEnrollmentOperation markCredentialsDelivered(
            String operationId,
            String ownerEmail
    ) {
        return markFlag(operationId, ownerEmail,
                EnrollmentState.CREDENTIAL_DELIVERY_PENDING, "credentialDelivered");
    }

    public DeviceEnrollmentOperation markDeviceVerified(
            String operationId,
            String ownerEmail
    ) {
        return markFlag(operationId, ownerEmail,
                EnrollmentState.DEVICE_VERIFICATION_PENDING, "deviceVerified");
    }

    public DeviceEnrollmentOperation complete(
            String operationId,
            String ownerEmail
    ) {
        DeviceEnrollmentOperation operation = operationRepository.findById(operationId)
                .filter(candidate -> normalize(ownerEmail).equals(candidate.getOwnerEmail()))
                .orElseThrow(() -> new IllegalArgumentException("Enrollment operation not found."));
        if (operation.getState() != EnrollmentState.DEVICE_VERIFICATION_PENDING
                || !operation.isBrokerIdentityVerified()
                || !operation.isCredentialDelivered()
                || !operation.isDeviceVerified()) {
            throw new IllegalStateException("Enrollment completion requirements are not satisfied.");
        }
        return transition(operationId, EnrollmentState.DEVICE_VERIFICATION_PENDING,
                EnrollmentState.COMPLETED, ownerEmail);
    }

    private DeviceEnrollmentOperation markFlag(
            String operationId,
            String ownerEmail,
            EnrollmentState expectedState,
            String field
    ) {
        Query query = ownerStateQuery(operationId, ownerEmail, expectedState);
        Update update = new Update()
                .set(field, true)
                .set("updatedAt", Instant.now())
                .inc("version", 1);
        return cas(query, update);
    }

    private DeviceEnrollmentOperation insertOperation(
            ProvisioningSession session,
            String owner
    ) {
        Instant now = Instant.now();
        DeviceEnrollmentOperation operation = new DeviceEnrollmentOperation();
        operation.setDeviceId(session.getDeviceId());
        operation.setPairingSessionId(session.getId());
        operation.setOwnerEmail(owner);
        operation.setEnrollmentAttemptId(UUID.randomUUID().toString());
        operation.setState(EnrollmentState.REQUESTED);
        operation.setVersion(0);
        operation.setCreatedAt(now);
        operation.setUpdatedAt(now);
        operation.setRetryCount(0);
        operation.setActiveOperationKey(session.getDeviceId());
        try {
            return operationRepository.save(operation);
        } catch (DuplicateKeyException exception) {
            return operationRepository.findByPairingSessionIdAndOwnerEmail(session.getId(), owner)
                    .orElseThrow(() -> new EnrollmentConcurrencyException(
                            "Enrollment operation was created concurrently."));
        }
    }

    private DeviceEnrollmentOperation cas(Query query, Update update) {
        DeviceEnrollmentOperation result = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true),
                DeviceEnrollmentOperation.class);
        if (result == null) {
            throw new EnrollmentConcurrencyException(
                    "Enrollment operation changed concurrently or is not owner-accessible.");
        }
        return result;
    }

    private Query ownerStateQuery(
            String operationId,
            String ownerEmail,
            EnrollmentState state
    ) {
        DeviceEnrollmentOperation current = operationRepository.findById(operationId)
                .filter(operation -> normalize(ownerEmail).equals(operation.getOwnerEmail()))
                .filter(operation -> operation.getState() == state)
                .orElseThrow(() -> new EnrollmentConcurrencyException(
                        "Enrollment operation changed concurrently or is not owner-accessible."));
        return new Query(new Criteria().andOperator(
                Criteria.where("_id").is(operationId),
                Criteria.where("ownerEmail").is(normalize(ownerEmail)),
                Criteria.where("state").is(state),
                Criteria.where("version").is(current.getVersion())
        ));
    }

    private String normalize(String ownerEmail) {
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new IllegalArgumentException("Authenticated owner is required.");
        }
        return ownerEmail.trim().toLowerCase();
    }
}
