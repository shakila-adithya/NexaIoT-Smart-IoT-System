package com.nexaiot.server.enrollment;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "device_enrollment_operations")
public class DeviceEnrollmentOperation {

    @Id
    private String id;

    @Indexed
    private String deviceId;

    @Indexed(unique = true)
    private String pairingSessionId;

    @Indexed
    private String ownerEmail;

    private String enrollmentAttemptId;
    private EnrollmentState state;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant startedAt;
    private Instant completedAt;
    private int retryCount;
    private String lastFailureCode;
    private String brokerUsername;
    private String brokerClientId;
    private String brokerRoleName;
    private String credentialReference;
    private String brokerResourceOwnershipMarker;
    private boolean reconciliationRequired;
    private boolean brokerIdentityVerified;
    private boolean credentialDelivered;
    private boolean deviceVerified;
    private String trustAnchorPublicKey;
    private String trustAnchorFingerprint;
    private Instant trustAnchorRegisteredAt;
    private String trustAnchorChallenge;
    private Instant trustAnchorChallengeExpiresAt;
    private String credentialEscrowNonce;
    private String credentialEscrowCiphertext;
    private String credentialEscrowKeyVersion;
    private Instant credentialEscrowCreatedAt;
    private Instant credentialEscrowExpiresAt;
    private String credentialDeliveryChallenge;
    private Instant credentialDeliveryChallengeExpiresAt;
    private String activationChallenge;
    private Instant activationChallengeExpiresAt;
    private String lastRecoveryDisposition;
    private String lastRecoveryReason;

    /** Present only while the operation owns the device enrollment slot. */
    @Indexed(unique = true, sparse = true)
    private String activeOperationKey;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getPairingSessionId() { return pairingSessionId; }
    public void setPairingSessionId(String pairingSessionId) { this.pairingSessionId = pairingSessionId; }
    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }
    public String getEnrollmentAttemptId() { return enrollmentAttemptId; }
    public void setEnrollmentAttemptId(String enrollmentAttemptId) { this.enrollmentAttemptId = enrollmentAttemptId; }
    public EnrollmentState getState() { return state; }
    public void setState(EnrollmentState state) { this.state = state; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public String getLastFailureCode() { return lastFailureCode; }
    public void setLastFailureCode(String lastFailureCode) { this.lastFailureCode = lastFailureCode; }
    public String getBrokerUsername() { return brokerUsername; }
    public void setBrokerUsername(String brokerUsername) { this.brokerUsername = brokerUsername; }
    public String getBrokerClientId() { return brokerClientId; }
    public void setBrokerClientId(String brokerClientId) { this.brokerClientId = brokerClientId; }
    public String getBrokerRoleName() { return brokerRoleName; }
    public void setBrokerRoleName(String brokerRoleName) { this.brokerRoleName = brokerRoleName; }
    public String getCredentialReference() { return credentialReference; }
    public void setCredentialReference(String credentialReference) { this.credentialReference = credentialReference; }
    public String getBrokerResourceOwnershipMarker() { return brokerResourceOwnershipMarker; }
    public void setBrokerResourceOwnershipMarker(String marker) { this.brokerResourceOwnershipMarker = marker; }
    public boolean isReconciliationRequired() { return reconciliationRequired; }
    public void setReconciliationRequired(boolean required) { this.reconciliationRequired = required; }
    public boolean isBrokerIdentityVerified() { return brokerIdentityVerified; }
    public void setBrokerIdentityVerified(boolean verified) { this.brokerIdentityVerified = verified; }
    public boolean isCredentialDelivered() { return credentialDelivered; }
    public void setCredentialDelivered(boolean delivered) { this.credentialDelivered = delivered; }
    public boolean isDeviceVerified() { return deviceVerified; }
    public void setDeviceVerified(boolean verified) { this.deviceVerified = verified; }
    public String getTrustAnchorPublicKey() { return trustAnchorPublicKey; }
    public void setTrustAnchorPublicKey(String publicKey) { this.trustAnchorPublicKey = publicKey; }
    public String getTrustAnchorFingerprint() { return trustAnchorFingerprint; }
    public void setTrustAnchorFingerprint(String fingerprint) { this.trustAnchorFingerprint = fingerprint; }
    public Instant getTrustAnchorRegisteredAt() { return trustAnchorRegisteredAt; }
    public void setTrustAnchorRegisteredAt(Instant registeredAt) { this.trustAnchorRegisteredAt = registeredAt; }
    public String getTrustAnchorChallenge() { return trustAnchorChallenge; }
    public void setTrustAnchorChallenge(String challenge) { this.trustAnchorChallenge = challenge; }
    public Instant getTrustAnchorChallengeExpiresAt() { return trustAnchorChallengeExpiresAt; }
    public void setTrustAnchorChallengeExpiresAt(Instant expiresAt) { this.trustAnchorChallengeExpiresAt = expiresAt; }
    public String getCredentialEscrowNonce() { return credentialEscrowNonce; }
    public void setCredentialEscrowNonce(String nonce) { this.credentialEscrowNonce = nonce; }
    public String getCredentialEscrowCiphertext() { return credentialEscrowCiphertext; }
    public void setCredentialEscrowCiphertext(String ciphertext) { this.credentialEscrowCiphertext = ciphertext; }
    public String getCredentialEscrowKeyVersion() { return credentialEscrowKeyVersion; }
    public void setCredentialEscrowKeyVersion(String keyVersion) { this.credentialEscrowKeyVersion = keyVersion; }
    public Instant getCredentialEscrowCreatedAt() { return credentialEscrowCreatedAt; }
    public void setCredentialEscrowCreatedAt(Instant createdAt) { this.credentialEscrowCreatedAt = createdAt; }
    public Instant getCredentialEscrowExpiresAt() { return credentialEscrowExpiresAt; }
    public void setCredentialEscrowExpiresAt(Instant expiresAt) { this.credentialEscrowExpiresAt = expiresAt; }
    public String getCredentialDeliveryChallenge() { return credentialDeliveryChallenge; }
    public void setCredentialDeliveryChallenge(String challenge) { this.credentialDeliveryChallenge = challenge; }
    public Instant getCredentialDeliveryChallengeExpiresAt() { return credentialDeliveryChallengeExpiresAt; }
    public void setCredentialDeliveryChallengeExpiresAt(Instant expiresAt) { this.credentialDeliveryChallengeExpiresAt = expiresAt; }
    public String getActivationChallenge() { return activationChallenge; }
    public void setActivationChallenge(String challenge) { this.activationChallenge = challenge; }
    public Instant getActivationChallengeExpiresAt() { return activationChallengeExpiresAt; }
    public void setActivationChallengeExpiresAt(Instant expiresAt) { this.activationChallengeExpiresAt = expiresAt; }
    public String getLastRecoveryDisposition() { return lastRecoveryDisposition; }
    public void setLastRecoveryDisposition(String disposition) { this.lastRecoveryDisposition = disposition; }
    public String getLastRecoveryReason() { return lastRecoveryReason; }
    public void setLastRecoveryReason(String reason) { this.lastRecoveryReason = reason; }
    public String getActiveOperationKey() { return activeOperationKey; }
    public void setActiveOperationKey(String activeOperationKey) { this.activeOperationKey = activeOperationKey; }
}
