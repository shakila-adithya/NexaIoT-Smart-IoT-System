import { useCallback, useEffect, useRef, useState } from "react";
import { Check, Clipboard, KeyRound, RefreshCw, ShieldCheck, X } from "lucide-react";

import {
  cancelPairingSession,
  getEnrollmentOperation,
  getPairingSession,
  getProvisioningMetadata,
  reconcileEnrollment,
  startEnrollment,
  startActivation,
  startPairingSession,
} from "../../api/provisioningApi.js";
import Button from "../ui/Button.jsx";
import SectionCard from "../ui/SectionCard.jsx";
import {
  ENROLLMENT_STAGES,
  canStartActivation,
  formatState,
  isTerminalEnrollmentState,
  isTerminalPairingState,
  isVerifiedCompletion,
  mergePairingResponse,
  recoveryPresentation,
} from "./provisioningState.js";

const DEFAULT_POLL_DELAY = 5000;
const MAX_POLL_DELAY = 30000;


function formatDate(value) {
  if (!value) return "Not available";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Not available" : date.toLocaleString();
}

function stateClass(value) {
  if (["CLAIMED", "COMPLETED"].includes(value)) return "bg-[#eaf8f3] text-[#16805f]";
  if (["EXPIRED", "CANCELLED", "FAILED", "LOCKED", "RECONCILIATION_REQUIRED"].includes(value)) return "bg-[#fdecee] text-[#c33b49]";
  return "bg-[#eef4ff] text-[#416bc4]";
}

function StatusPill({ value }) {
  return <span className={`inline-flex rounded-full px-2.5 py-1 text-[9px] font-semibold uppercase tracking-[0.04em] ${stateClass(value)}`}>{formatState(value)}</span>;
}

function safeStorageGet(key) {
  try { return sessionStorage.getItem(key); } catch { return null; }
}

function safeStorageSet(key, value) {
  try { sessionStorage.setItem(key, value); } catch { /* Backend remains authoritative. */ }
}

function safeStorageRemove(key) {
  try { sessionStorage.removeItem(key); } catch { /* Storage may be disabled. */ }
}

export default function ProvisioningPanel({ deviceId }) {
  const operationStorageKey = `nexaiot:enrollment-operation:${deviceId}`;
  const mountedRef = useRef(true);
  const pairingPollInFlight = useRef(false);
  const operationPollInFlight = useRef(false);
  const [metadata, setMetadata] = useState(null);
  const [pairing, setPairing] = useState(null);
  const [operation, setOperation] = useState(null);
  const [loading, setLoading] = useState(true);
  const [action, setAction] = useState("");
  const [error, setError] = useState("");
  const [pollingPaused, setPollingPaused] = useState(false);
  const [pollingNotice, setPollingNotice] = useState("");
  const [pairingPollDelay, setPairingPollDelay] = useState(DEFAULT_POLL_DELAY);
  const [operationPollDelay, setOperationPollDelay] = useState(4000);
  const [copied, setCopied] = useState(false);

  const handlePollError = useCallback((requestError, resource) => {
    if (!mountedRef.current) return;
    if (requestError.status === 401) {
      setPollingPaused(true);
      setPollingNotice("Your session expired. Sign in again to continue monitoring this operation.");
      return;
    }
    setPollingNotice(`${resource} status could not be refreshed. Retrying with a slower interval.`);
    if (resource === "Pairing") setPairingPollDelay((delay) => Math.min(delay * 2, MAX_POLL_DELAY));
    if (resource === "Enrollment") setOperationPollDelay((delay) => Math.min(delay * 2, MAX_POLL_DELAY));
  }, []);

  const refreshMetadata = useCallback(async () => {
    const response = await getProvisioningMetadata(deviceId);
    if (mountedRef.current) setMetadata(response);
    return response;
  }, [deviceId]);

  const refreshPairing = useCallback(async (sessionId, polling = false) => {
    if (polling && (pollingPaused || pairingPollInFlight.current)) return null;
    if (polling) pairingPollInFlight.current = true;
    try {
      const response = await getPairingSession(deviceId, sessionId);
      if (mountedRef.current) {
        setPairing((current) => mergePairingResponse(current, response));
        if (polling) { setPairingPollDelay(DEFAULT_POLL_DELAY); setPollingNotice(""); }
      }
      return response;
    } catch (requestError) {
      if (polling) handlePollError(requestError, "Pairing");
      throw requestError;
    } finally {
      if (polling) pairingPollInFlight.current = false;
    }
  }, [deviceId, handlePollError, pollingPaused]);

  const refreshOperation = useCallback(async (operationId, polling = false) => {
    if (polling && (pollingPaused || operationPollInFlight.current)) return null;
    if (polling) operationPollInFlight.current = true;
    try {
      const response = await getEnrollmentOperation(deviceId, operationId);
      if (mountedRef.current) {
        setOperation(response);
        if (polling) { setOperationPollDelay(4000); setPollingNotice(""); }
      }
      return response;
    } catch (requestError) {
      if (polling) handlePollError(requestError, "Enrollment");
      throw requestError;
    } finally {
      if (polling) operationPollInFlight.current = false;
    }
  }, [deviceId, handlePollError, pollingPaused]);

  useEffect(() => {
    mountedRef.current = true;
    let cancelled = false;
    async function load() {
      try {
        setLoading(true); setError("");
        const response = await getProvisioningMetadata(deviceId);
        if (cancelled || !mountedRef.current) return;
        setMetadata(response);
        if (response.activePairingSessionId) await refreshPairing(response.activePairingSessionId);
        const storedOperationId = safeStorageGet(operationStorageKey);
        if (storedOperationId) {
          try { await refreshOperation(storedOperationId); } catch { safeStorageRemove(operationStorageKey); }
        }
      } catch (requestError) {
        if (!cancelled && mountedRef.current) setError(requestError.message || "Unable to load provisioning status.");
      } finally {
        if (!cancelled && mountedRef.current) setLoading(false);
      }
    }
    load();
    return () => {
      cancelled = true; mountedRef.current = false;
      pairingPollInFlight.current = false; operationPollInFlight.current = false;
    };
  }, [deviceId, operationStorageKey, refreshOperation, refreshPairing]);

  useEffect(() => {
    if (pollingPaused || !pairing?.sessionId || isTerminalPairingState(pairing.status)) return undefined;
    const interval = window.setInterval(() => refreshPairing(pairing.sessionId, true).catch(() => {}), pairingPollDelay);
    return () => window.clearInterval(interval);
  }, [pairing?.sessionId, pairing?.status, pairingPollDelay, pollingPaused, refreshPairing]);

  useEffect(() => {
    if (pollingPaused || !operation?.operationId || isTerminalEnrollmentState(operation.state)) return undefined;
    const interval = window.setInterval(() => refreshOperation(operation.operationId, true).catch(() => {}), operationPollDelay);
    return () => window.clearInterval(interval);
  }, [operation?.operationId, operation?.state, operationPollDelay, pollingPaused, refreshOperation]);

  const runAction = async (name, callback) => {
    if (action) return;
    try { setAction(name); setError(""); setPollingPaused(false); setPollingNotice(""); await callback(); }
    catch (requestError) { if (mountedRef.current) setError(requestError.message || "Provisioning request failed."); }
    finally { if (mountedRef.current) setAction(""); }
  };

  const handleStartPairing = () => runAction("start-pairing", async () => {
    const response = await startPairingSession(deviceId);
    if (!mountedRef.current) return;
    setPairing(response); await refreshMetadata();
  });

  const handleRefresh = () => runAction("refresh", async () => {
    const response = await refreshMetadata();
    if (response.activePairingSessionId) await refreshPairing(response.activePairingSessionId);
    if (operation?.operationId) await refreshOperation(operation.operationId);
  });

  const handleCancelPairing = () => runAction("cancel-pairing", async () => {
    await cancelPairingSession(deviceId, pairing.sessionId);
    const response = await refreshPairing(pairing.sessionId);
    if (response.status !== "CANCELLED") throw new Error("The backend did not confirm pairing cancellation.");
    await refreshMetadata();
  });

  const handleStartEnrollment = () => runAction("start-enrollment", async () => {
    const response = await startEnrollment(deviceId, pairing.sessionId);
    if (mountedRef.current) { setOperation(response); safeStorageSet(operationStorageKey, response.operationId); }
  });

  const handleReconcile = () => runAction("reconcile", async () => {
    const response = await reconcileEnrollment(deviceId, operation.operationId);
    if (mountedRef.current) setOperation(response);
  });

  const handleStartActivation = () => runAction("start-activation", async () => {
    await startActivation(deviceId, operation.operationId);
    await refreshOperation(operation.operationId);
  });

  const handleCopyToken = async () => {
    if (!pairing?.pairingToken || !navigator.clipboard) return;
    try { await navigator.clipboard.writeText(pairing.pairingToken); setCopied(true); window.setTimeout(() => setCopied(false), 1800); }
    catch { setError("The pairing token could not be copied."); }
  };

  const recovery = recoveryPresentation(operation);
  const canStartEnrollment = pairing?.status === "CLAIMED" && !operation;
  const canCancel = pairing?.status === "ACTIVE";
  const canReconcile = recovery?.action === "reconcile";
  const activationAvailable = canStartActivation(operation);
  const currentStageIndex = ENROLLMENT_STAGES.indexOf(operation?.state);
  const incompleteCompletion = operation?.state === "COMPLETED" && !isVerifiedCompletion(operation);

  return (
    <SectionCard className="mt-5 p-5" compact>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex items-start gap-3"><div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[12px] bg-[#eaf9fc] text-[#08a9c4]"><ShieldCheck size={19} aria-hidden="true" /></div><div><h2 className="text-[15px] font-semibold text-[#102a3a]">Secure pairing and enrollment</h2><p className="mt-1 text-[10px] leading-4 text-[#6b8290]">Owner-authorized progress from pairing through backend verification.</p>{metadata?.status ? <p className="mt-1 text-[9px] text-[#8ba0ab]">Backend provisioning status: <span className="font-semibold text-[#526b79]">{formatState(metadata.status)}</span></p> : null}</div></div>
        <Button type="button" size="sm" variant="secondary" onClick={handleRefresh} loading={action === "refresh"} icon={RefreshCw}>Refresh status</Button>
      </div>
      {loading ? <p className="mt-5 text-[10px] text-[#6b8290]" aria-busy="true">Loading secure provisioning status...</p> : null}
      {error ? <div className="mt-4 rounded-[10px] border border-red-200 bg-red-50 p-3 text-[10px] text-red-600" role="alert">{error}</div> : null}
      {pollingNotice ? <div className="mt-4 rounded-[10px] border border-[#f1dfb9] bg-[#fffaf0] p-3 text-[10px] text-[#8a650c]" role="status">{pollingNotice}</div> : null}
      {!loading ? <div className="mt-5 grid gap-4 lg:grid-cols-2">
        <section className="rounded-[12px] border border-[#dce8ee] bg-[#f8fbfd] p-4"><div className="flex items-center justify-between gap-3"><div className="flex items-center gap-2"><KeyRound size={15} className="text-[#08a9c4]" /><h3 className="text-[12px] font-semibold text-[#102a3a]">Owner pairing</h3></div>{pairing ? <StatusPill value={pairing.status} /> : <StatusPill value="NOT_STARTED" />}</div>{pairing ? <div className="mt-4 space-y-2.5 text-[10px] text-[#6b8290]"><p>Expires: <span className="font-medium text-[#425c6b]">{formatDate(pairing.expiresAt)}</span></p><p>Attempts: <span className="font-medium text-[#425c6b]">{pairing.attemptCount ?? 0} / {pairing.maximumAttempts ?? "—"}</span></p>{pairing.failureReason ? <p className="text-red-600">Failure: {pairing.failureReason}</p> : null}{pairing.pairingToken && pairing.status === "ACTIVE" ? <div className="rounded-[9px] border border-[#cfe7ed] bg-white p-3"><p className="font-semibold text-[#102a3a]">Secure device hand-off token</p><p className="mt-1 break-all font-mono text-[11px] text-[#087f98]">{pairing.pairingToken}</p><button type="button" onClick={handleCopyToken} className="mt-2 inline-flex items-center gap-1.5 text-[9px] font-semibold text-[#087f98]">{copied ? <Check size={12} /> : <Clipboard size={12} />} {copied ? "Copied" : "Copy token"}</button><p className="mt-2 text-[9px] leading-4 text-[#7d929e]">This one-time token is shown only after authorized creation. It is never stored by the frontend and cannot be retrieved after refresh.</p></div> : null}<div className="flex flex-wrap gap-2 pt-1">{canCancel ? <Button type="button" size="sm" variant="secondary" onClick={handleCancelPairing} loading={action === "cancel-pairing"} icon={X}>Cancel pairing</Button> : null}{canStartEnrollment ? <Button type="button" size="sm" onClick={handleStartEnrollment} loading={action === "start-enrollment"} icon={ShieldCheck}>Start authorized enrollment</Button> : null}</div></div> : <div className="mt-4"><p className="text-[10px] leading-4 text-[#6b8290]">Create a time-limited pairing session for the physical device.</p><Button type="button" size="sm" className="mt-3" onClick={handleStartPairing} loading={action === "start-pairing"} icon={KeyRound}>Start pairing</Button></div>}</section>
        <section className="rounded-[12px] border border-[#dce8ee] bg-[#f8fbfd] p-4"><div className="flex items-center justify-between gap-3"><div className="flex items-center gap-2"><RefreshCw size={15} className="text-[#08a9c4]" /><h3 className="text-[12px] font-semibold text-[#102a3a]">Enrollment progress</h3></div>{operation ? <StatusPill value={operation.state} /> : <StatusPill value="NOT_STARTED" />}</div>{operation ? <div className="mt-4 space-y-3"><ol className="space-y-2" aria-label="Enrollment lifecycle">{ENROLLMENT_STAGES.map((stage, index) => { const completed = currentStageIndex >= 0 && index < currentStageIndex; const current = stage === operation.state; return <li key={stage} className="flex items-center gap-2 text-[9px]"><span className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full border text-[8px] font-bold ${completed ? "border-[#16a57a] bg-[#eaf8f3] text-[#16805f]" : current ? "border-[#08a9c4] bg-[#eaf9fc] text-[#087f98]" : "border-[#dce8ee] bg-white text-[#9aaeba]"}`}>{completed ? <Check size={11} /> : index + 1}</span><span className={current ? "font-semibold text-[#102a3a]" : completed ? "text-[#526b79]" : "text-[#9aaeba]"}>{formatState(stage)}</span>{current ? <span className="ml-auto text-[#087f98]">Current</span> : null}</li>; })}</ol><div className="grid grid-cols-2 gap-2 text-[10px]"><div><span className="text-[#8ba0ab]">Updated</span><p className="mt-1 font-medium text-[#425c6b]">{formatDate(operation.updatedAt)}</p></div><div><span className="text-[#8ba0ab]">Attempt</span><p className="mt-1 break-all font-medium text-[#425c6b]">{operation.enrollmentAttemptId || "Pending"}</p></div></div><div className="grid gap-2 sm:grid-cols-3">{[["Broker verified", operation.brokerIdentityVerified], ["Credential delivery", operation.credentialDelivered], ["Device verified", operation.deviceVerified]].map(([label, value]) => <div key={label} className="rounded-[9px] border border-[#dce8ee] bg-white px-3 py-2 text-[9px]"><span className="text-[#7d929e]">{label}</span><p className={`mt-1 font-semibold ${value ? "text-[#16805f]" : "text-[#9aaeba]"}`}>{value ? "Verified" : "Pending"}</p></div>)}</div>{activationAvailable ? <div className="rounded-[9px] border border-[#cfe7ed] bg-white p-3"><p className="text-[10px] font-semibold text-[#102a3a]">Device credentials delivered</p><p className="mt-1 text-[9px] leading-4 text-[#6b8290]">Ask the device to complete its signed activation handshake.</p><Button type="button" size="sm" className="mt-3" onClick={handleStartActivation} loading={action === "start-activation"} icon={ShieldCheck}>Start device activation</Button></div> : null}{incompleteCompletion ? <div className="rounded-[9px] border border-[#f1dfb9] bg-[#fffaf0] p-3 text-[10px] text-[#8a650c]">The backend returned COMPLETED, but credential and device verification flags are incomplete. This screen does not treat the device as active.</div> : null}{recovery ? <div className="rounded-[9px] border border-[#f1dfb9] bg-[#fffaf0] p-3 text-[10px] text-[#8a650c]"><p className="font-semibold">{recovery.title}</p><p className="mt-1 leading-4">{recovery.message}</p>{canReconcile ? <Button type="button" size="sm" className="mt-3" onClick={handleReconcile} loading={action === "reconcile"} icon={RefreshCw}>Reconcile enrollment</Button> : null}</div> : null}<p className="text-[9px] text-[#7d929e]">State and recovery guidance are read from the authenticated backend.</p></div> : <p className="mt-4 text-[10px] leading-4 text-[#6b8290]">Enrollment becomes available after the device claims the pairing session.</p>}</section>
      </div> : null}
    </SectionCard>
  );
}
