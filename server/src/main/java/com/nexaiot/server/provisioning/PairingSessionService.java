package com.nexaiot.server.provisioning;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceProvisioningInfo;
import com.nexaiot.server.device.DeviceProvisioningStatus;
import com.nexaiot.server.device.DeviceRepository;

@Service
public class PairingSessionService {

    private final DeviceRepository deviceRepository;
    private final ProvisioningSessionRepository sessionRepository;
    private final PairingTokenService tokenService;
    private final MongoTemplate mongoTemplate;
    private final long expirySeconds;
    private final int maximumAttempts;

    public PairingSessionService(
            DeviceRepository deviceRepository,
            ProvisioningSessionRepository sessionRepository,
            PairingTokenService tokenService,
            MongoTemplate mongoTemplate,
            @Value("${provisioning.pairing.expiry-seconds:600}") long expirySeconds,
            @Value("${provisioning.pairing.maximum-attempts:5}") int maximumAttempts
    ) {
        if (expirySeconds <= 0 || maximumAttempts <= 0) {
            throw new IllegalArgumentException("Pairing policy values must be positive.");
        }
        this.deviceRepository = deviceRepository;
        this.sessionRepository = sessionRepository;
        this.tokenService = tokenService;
        this.mongoTemplate = mongoTemplate;
        this.expirySeconds = expirySeconds;
        this.maximumAttempts = maximumAttempts;
    }

    public PairingSessionStart startPairingSession(String deviceId, String ownerEmail) {
        String owner = normalizeEmail(ownerEmail);
        Device device = deviceRepository.findByIdAndOwnerEmail(deviceId, owner)
                .orElseThrow(() -> new IllegalArgumentException("Device not found."));

        DeviceProvisioningInfo provisioning = provisioningInfo(device);
        if (provisioning.getStatus() == DeviceProvisioningStatus.PROVISIONED
                || provisioning.getStatus() == DeviceProvisioningStatus.DECOMMISSIONED) {
            throw new IllegalStateException("Device cannot start a pairing session.");
        }

        sessionRepository.findByDeviceIdAndOwnerEmailAndStatus(
                        deviceId, owner, PairingSessionStatus.ACTIVE)
                .ifPresent(session -> {
                    throw new IllegalStateException("An active pairing session already exists.");
                });

        Instant now = Instant.now();
        String rawToken = tokenService.generateToken();

        ProvisioningSession session = new ProvisioningSession();
        session.setDeviceId(device.getId());
        session.setOwnerEmail(owner);
        session.setTokenHash(tokenService.hashToken(rawToken));
        session.setStatus(PairingSessionStatus.ACTIVE);
        session.setCreatedAt(now);
        session.setExpiresAt(now.plusSeconds(expirySeconds));
        session.setAttemptCount(0);
        session.setMaximumAttempts(maximumAttempts);
        session.setHardwareIdentityHash(hardwareIdentityHash(device));

        ProvisioningSession saved = sessionRepository.save(session);
        provisioning.setStatus(DeviceProvisioningStatus.PROVISIONING);
        provisioning.setLastProvisioningAttemptAt(now);
        device.setProvisioningInfo(provisioning);
        deviceRepository.save(device);

        return new PairingSessionStart(
                saved.getId(), rawToken, saved.getExpiresAt(), saved.getMaximumAttempts());
    }

    public PairingClaimResult claimPairingSession(String sessionId, String candidateToken) {
        if (sessionId == null || sessionId.isBlank() || candidateToken == null
                || candidateToken.isBlank()) {
            return new PairingClaimResult(PairingClaimStatus.INVALID, sessionId, null);
        }

        ProvisioningSession current = sessionRepository.findById(sessionId).orElse(null);
        if (current == null) {
            return new PairingClaimResult(PairingClaimStatus.INVALID, sessionId, null);
        }

        Instant now = Instant.now();
        if (current.getStatus() == PairingSessionStatus.EXPIRED
                || current.getExpiresAt().isBefore(now)) {
            expireIfActive(sessionId, now);
            return new PairingClaimResult(PairingClaimStatus.EXPIRED, sessionId,
                    current.getExpiresAt());
        }
        if (current.getStatus() == PairingSessionStatus.CANCELLED) {
            return new PairingClaimResult(PairingClaimStatus.CANCELLED, sessionId,
                    current.getExpiresAt());
        }
        if (current.getStatus() == PairingSessionStatus.LOCKED) {
            return new PairingClaimResult(PairingClaimStatus.LOCKED, sessionId,
                    current.getExpiresAt());
        }
        if (current.getStatus() == PairingSessionStatus.CLAIMED) {
            return new PairingClaimResult(PairingClaimStatus.ALREADY_CLAIMED, sessionId,
                    current.getExpiresAt());
        }

        if (!tokenService.matches(candidateToken, current.getTokenHash())) {
            return recordFailedAttempt(current, now);
        }

        Query claimQuery = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(sessionId),
                Criteria.where("tokenHash").is(current.getTokenHash()),
                Criteria.where("status").is(PairingSessionStatus.ACTIVE),
                Criteria.where("expiresAt").gt(now),
                Criteria.where("attemptCount").lt(current.getMaximumAttempts())
        ));
        Update claimUpdate = new Update()
                .set("status", PairingSessionStatus.CLAIMED)
                .set("consumedAt", now);
        ProvisioningSession claimed = mongoTemplate.findAndModify(
                claimQuery,
                claimUpdate,
                FindAndModifyOptions.options().returnNew(true),
                ProvisioningSession.class);

        if (claimed == null) {
            ProvisioningSession latest = sessionRepository.findById(sessionId).orElse(current);
            return resultForCurrentState(latest, sessionId);
        }

        return new PairingClaimResult(PairingClaimStatus.CLAIMED, sessionId,
                claimed.getExpiresAt());
    }

    public void cancelPairingSession(String sessionId, String ownerEmail) {
        String owner = normalizeEmail(ownerEmail);
        Instant now = Instant.now();
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(sessionId),
                Criteria.where("ownerEmail").is(owner),
                Criteria.where("status").is(PairingSessionStatus.ACTIVE)
        ));
        Update update = new Update()
                .set("status", PairingSessionStatus.CANCELLED)
                .set("consumedAt", now)
                .set("failureReason", "Cancelled by device owner");
        ProvisioningSession cancelled = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true),
                ProvisioningSession.class);
        if (cancelled == null) {
            throw new IllegalArgumentException("Pairing session not found or no longer active.");
        }

        deviceRepository.findByIdAndOwnerEmail(cancelled.getDeviceId(), owner)
                .ifPresent(device -> {
                    DeviceProvisioningInfo info = provisioningInfo(device);
                    if (info.getStatus() == DeviceProvisioningStatus.PROVISIONING) {
                        info.setStatus(DeviceProvisioningStatus.REGISTERED);
                        device.setProvisioningInfo(info);
                        deviceRepository.save(device);
                    }
                });
    }

    public ProvisioningSession getOwnerSession(String sessionId, String ownerEmail) {
        return sessionRepository.findById(sessionId)
                .filter(session -> session.getOwnerEmail().equals(normalizeEmail(ownerEmail)))
                .map(this::safeSession)
                .orElseThrow(() -> new IllegalArgumentException("Pairing session not found."));
    }

    private PairingClaimResult recordFailedAttempt(ProvisioningSession current, Instant now) {
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(current.getId()),
                Criteria.where("status").is(PairingSessionStatus.ACTIVE),
                Criteria.where("expiresAt").gt(now),
                Criteria.where("attemptCount").lt(current.getMaximumAttempts())
        ));
        ProvisioningSession updated = mongoTemplate.findAndModify(
                query,
                new Update().inc("attemptCount", 1),
                FindAndModifyOptions.options().returnNew(true),
                ProvisioningSession.class);
        if (updated == null) {
            return resultForCurrentState(
                    sessionRepository.findById(current.getId()).orElse(current), current.getId());
        }
        if (updated.getAttemptCount() >= updated.getMaximumAttempts()) {
            mongoTemplate.updateFirst(
                    new Query(new Criteria().andOperator(
                            Criteria.where("_id").is(updated.getId()),
                            Criteria.where("status").is(PairingSessionStatus.ACTIVE))),
                    new Update().set("status", PairingSessionStatus.LOCKED)
                            .set("failureReason", "Maximum pairing attempts exceeded"),
                    ProvisioningSession.class);
            return new PairingClaimResult(PairingClaimStatus.LOCKED, updated.getId(),
                    updated.getExpiresAt());
        }
        return new PairingClaimResult(PairingClaimStatus.INVALID, updated.getId(),
                updated.getExpiresAt());
    }

    private void expireIfActive(String sessionId, Instant now) {
        mongoTemplate.updateFirst(
                new Query(new Criteria().andOperator(
                        Criteria.where("_id").is(sessionId),
                        Criteria.where("status").is(PairingSessionStatus.ACTIVE))),
                new Update().set("status", PairingSessionStatus.EXPIRED)
                        .set("failureReason", "Pairing session expired"),
                ProvisioningSession.class);
    }

    private PairingClaimResult resultForCurrentState(
            ProvisioningSession session,
            String sessionId
    ) {
        PairingClaimStatus status = switch (session.getStatus()) {
            case CLAIMED -> PairingClaimStatus.ALREADY_CLAIMED;
            case EXPIRED -> PairingClaimStatus.EXPIRED;
            case CANCELLED -> PairingClaimStatus.CANCELLED;
            case LOCKED -> PairingClaimStatus.LOCKED;
            case ACTIVE -> PairingClaimStatus.INVALID;
        };
        return new PairingClaimResult(status, sessionId, session.getExpiresAt());
    }

    private ProvisioningSession safeSession(ProvisioningSession session) {
        ProvisioningSession safe = new ProvisioningSession();
        safe.setId(session.getId());
        safe.setDeviceId(session.getDeviceId());
        safe.setOwnerEmail(session.getOwnerEmail());
        safe.setStatus(session.getStatus());
        safe.setCreatedAt(session.getCreatedAt());
        safe.setExpiresAt(session.getExpiresAt());
        safe.setConsumedAt(session.getConsumedAt());
        safe.setAttemptCount(session.getAttemptCount());
        safe.setMaximumAttempts(session.getMaximumAttempts());
        safe.setFailureReason(session.getFailureReason());
        return safe;
    }

    private DeviceProvisioningInfo provisioningInfo(Device device) {
        if (device.getProvisioningInfo() == null) {
            DeviceProvisioningInfo info = new DeviceProvisioningInfo();
            info.setStatus(DeviceProvisioningStatus.REGISTERED);
            device.setProvisioningInfo(info);
        }
        return device.getProvisioningInfo();
    }

    private String hardwareIdentityHash(Device device) {
        return device.getHardwareIdentity() == null
                ? null
                : device.getHardwareIdentity().getHardwareIdentityHash();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated owner is required.");
        }
        return email.trim().toLowerCase();
    }
}
