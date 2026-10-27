export const ENROLLMENT_STAGES = [
  "REQUESTED", "BROKER_CLIENT_PENDING", "BROKER_CLIENT_CREATED", "ACL_PENDING",
  "ACL_ASSIGNED", "BROKER_VERIFIED", "CREDENTIAL_DELIVERY_PENDING",
  "DEVICE_VERIFICATION_PENDING", "COMPLETED",
];

export function formatState(value) {
  return (value || "UNKNOWN").replaceAll("_", " ");
}

export function recoveryPresentation(operation) {
  const disposition = operation?.recoveryOutcome;
  const reason = operation?.recoveryReason;
  if (disposition === "RETRYABLE") return { title: "Retry is safe", message: reason || "The backend marked this operation safe to retry.", action: "reconcile" };
  if (disposition === "SAFE_TO_CONTINUE") return { title: "Safe to continue", message: reason || "The backend found no conflicting resource state.", action: null };
  if (disposition === "CREDENTIAL_REISSUANCE_REQUIRED") return { title: "Credential reissuance required", message: reason || "Credentials must be reissued through the secure backend delivery flow.", action: null };
  if (disposition === "MANUAL_REVIEW_REQUIRED") return { title: "Manual review required", message: reason || "An administrator must review the enrollment before it can continue.", action: null };
  if (disposition === "TERMINAL_FAILURE") return { title: "Enrollment cannot continue", message: reason || "The backend reported a terminal enrollment failure.", action: null };
  if (operation?.reconciliationRequired || disposition === "RECONCILIATION_REQUIRED") return { title: "Reconciliation required", message: reason || "The backend requires an owner-authorized reconciliation before continuing.", action: "reconcile" };
  return null;
}

export function mergePairingResponse(current, response) {
  return {
    ...current,
    ...response,
    ...(response.status === "ACTIVE" ? {} : { pairingToken: undefined }),
  };
}

export function isTerminalPairingState(state) {
  return ["CLAIMED", "EXPIRED", "CANCELLED", "LOCKED"].includes(state);
}

export function isTerminalEnrollmentState(state) {
  return ["COMPLETED", "FAILED", "RECONCILIATION_REQUIRED", "CANCELLED"].includes(state);
}

export function isVerifiedCompletion(operation) {
  return operation?.state === "COMPLETED"
    && operation.credentialDelivered === true
    && operation.deviceVerified === true;
}

export function canStartActivation(operation) {
  return operation?.state === "DEVICE_VERIFICATION_PENDING"
    && operation.credentialDelivered === true
    && operation.deviceVerified !== true;
}
