"use client";

/**
 * Emergency consent-exception panel (Lane 1). Records — and audits — that surgery proceeds without
 * ordinary informed consent, on one of three MVUMO bases (DEFERRED / PROXY / TWO_DOCTOR). It does NOT
 * fabricate a GRANTED consent: the service sets consent_status = EMERGENCY_EXCEPTION and arms the
 * override that the WHO Sign-In / start recognises. A clinical justification is mandatory.
 * Binds: POST /internal/v1/theatre/cases/{id}/consent/emergency-exception
 */

import { useState } from "react";
import { ShieldAlert } from "lucide-react";
import { apiClient } from "@/lib/api-client";

function unwrap<T>(res: unknown): T {
  const o = res as { data?: T };
  return (o && o.data !== undefined ? o.data : res) as T;
}
function errMessage(e: unknown): string {
  const o = e as { error?: { message?: string }; status?: number };
  if (o?.error?.message) return o.error.message;
  if (o?.status) return `Request failed (HTTP ${o.status}).`;
  return "Could not record the consent exception. Please try again.";
}

export function EmergencyConsentExceptionPanel({
  caseId,
  consentStatus,
  onRecorded,
}: {
  caseId: string;
  consentStatus?: string;
  onRecorded?: () => void;
}) {
  const [basis, setBasis] = useState("DEFERRED");
  const [reason, setReason] = useState("");
  const [authorisedBy, setAuthorisedBy] = useState("");
  const [secondClinician, setSecondClinician] = useState("");
  const [proxyName, setProxyName] = useState("");
  const [proxyRelationship, setProxyRelationship] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [result, setResult] = useState<string | null>(consentStatus === "EMERGENCY_EXCEPTION" ? "EMERGENCY_EXCEPTION" : null);

  const submit = async () => {
    setBusy(true);
    setMsg(null);
    try {
      const body: Record<string, unknown> = { basis, reason };
      if (authorisedBy) body.authorisedBy = authorisedBy;
      if (basis === "TWO_DOCTOR") body.secondClinician = secondClinician;
      if (basis === "PROXY") {
        body.proxyName = proxyName;
        body.proxyRelationship = proxyRelationship;
      }
      const res = await apiClient.post(`/internal/v1/theatre/cases/${caseId}/consent/emergency-exception`, body);
      const data = unwrap<{ consent_status?: string; emergency_consent_basis?: string }>(res);
      setResult(data?.consent_status ?? "EMERGENCY_EXCEPTION");
      setMsg("Emergency consent exception recorded and audited. Start is now override-gated.");
      onRecorded?.();
    } catch (e) {
      setMsg(errMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const needSecond = basis === "TWO_DOCTOR" && !secondClinician;
  const needProxy = basis === "PROXY" && !proxyName;

  return (
    <section className="rounded-xl border border-amber-200 bg-amber-50/40 p-4" data-testid="emergency-consent-panel">
      <h3 className="mb-3 flex items-center gap-1.5 text-sm font-semibold text-amber-800"><ShieldAlert className="h-4 w-4" /> Emergency consent exception</h3>

      {result === "EMERGENCY_EXCEPTION" && (
        <p data-testid="consent-exception-status" className="mb-2 rounded-lg border border-amber-300 bg-amber-100 p-2 text-xs font-medium text-amber-900">
          consent_status = EMERGENCY_EXCEPTION — start is override-gated (no GRANTED consent was fabricated).
        </p>
      )}
      {msg && <p className="mb-2 rounded-lg border border-border bg-card p-2 text-xs">{msg}</p>}

      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Basis</span>
          <select value={basis} onChange={(e) => setBasis(e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-1.5">
            <option value="DEFERRED">DEFERRED — unable to consent, no proxy</option>
            <option value="PROXY">PROXY — next-of-kin / surrogate</option>
            <option value="TWO_DOCTOR">TWO_DOCTOR — two independent clinicians</option>
          </select>
        </label>
        <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Authorising clinician (optional)</span><input type="text" value={authorisedBy} onChange={(e) => setAuthorisedBy(e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
        <label className="block text-sm sm:col-span-2"><span className="mb-1 block text-xs font-medium text-muted-foreground">Clinical justification (required)</span><input type="text" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Life-threatening haemorrhage, unconscious patient" className="w-full rounded-lg border border-border bg-background px-3 py-1.5" data-testid="consent-reason" /></label>
        {basis === "TWO_DOCTOR" && (
          <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Second clinician (required)</span><input type="text" value={secondClinician} onChange={(e) => setSecondClinician(e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
        )}
        {basis === "PROXY" && (
          <>
            <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Proxy / next-of-kin (required)</span><input type="text" value={proxyName} onChange={(e) => setProxyName(e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
            <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Relationship</span><input type="text" value={proxyRelationship} onChange={(e) => setProxyRelationship(e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
          </>
        )}
      </div>
      <button type="button" disabled={busy || !reason || needSecond || needProxy} onClick={() => void submit()} className="mt-3 rounded-lg border border-amber-300 bg-amber-100 px-3 py-1.5 text-sm font-medium text-amber-900 hover:bg-amber-200 disabled:opacity-50">Record emergency consent exception</button>
    </section>
  );
}
