"use client";

import { CheckCircle2, XCircle, ShieldCheck } from "lucide-react";

/**
 * TM-B14/B5 — pre-consult readiness gate on the console right pane.
 *
 * Pure presentational: it derives nothing from the network itself. The console page already
 * computes the governed consent status and provider-authority (standing) status from the referral
 * payload, and the patient-context query already reports whether the summary loaded — those three
 * signals are passed in as props so this component never re-fetches.
 *
 *   consentOk   — consent verified: consentStatus is GRANTED/OBTAINED (or an explicit token).
 *   authorityOk — provider standing: authorityStatus resolves ACTIVE / GOOD_STANDING / VERIFIED.
 *   patientContextOk — the patient-summary query succeeded (context loaded).
 */
export interface TeleconsultReadinessChecklistProps {
  consentOk: boolean;
  authorityOk: boolean;
  patientContextOk: boolean;
}

function Row({ label, ok, hint }: { label: string; ok: boolean; hint: string }) {
  return (
    <li className="flex items-start gap-2 text-xs" data-testid={`readiness-row-${ok ? "ok" : "blocked"}`}>
      {ok ? (
        <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-green-600" aria-label="ready" />
      ) : (
        <XCircle className="mt-0.5 h-4 w-4 shrink-0 text-red-500" aria-label="not ready" />
      )}
      <span>
        <span className={`font-medium ${ok ? "text-foreground" : "text-red-700"}`}>{label}</span>
        <span className="block text-[11px] text-muted-foreground">{hint}</span>
      </span>
    </li>
  );
}

export function TeleconsultReadinessChecklist({
  consentOk,
  authorityOk,
  patientContextOk,
}: TeleconsultReadinessChecklistProps) {
  const allReady = consentOk && authorityOk && patientContextOk;

  return (
    <section
      className={`rounded-xl border p-3 shadow-sm ${allReady ? "border-green-300 bg-green-50" : "border-amber-300 bg-warning-soft"}`}
      data-testid="teleconsult-readiness-checklist"
    >
      <div className="flex items-center gap-2">
        <ShieldCheck className={`h-4 w-4 ${allReady ? "text-green-600" : "text-warning-foreground"}`} />
        <h3 className="text-sm font-semibold text-foreground">Consult readiness</h3>
        <span
          className={`ml-auto rounded-full px-2 py-0.5 text-[10px] font-medium ${
            allReady ? "bg-emerald-100 text-emerald-700" : "bg-amber-100 text-amber-800"
          }`}
          data-testid="readiness-verdict"
        >
          {allReady ? "READY" : "BLOCKED"}
        </span>
      </div>
      <ul className="mt-3 space-y-2">
        <Row
          label="Consent verified"
          ok={consentOk}
          hint={consentOk ? "Patient consent is on the referral." : "Record patient consent before starting."}
        />
        <Row
          label="Provider authority"
          ok={authorityOk}
          hint={authorityOk ? "Provider standing is active." : "Provider standing is not confirmed active."}
        />
        <Row
          label="Patient context loaded"
          ok={patientContextOk}
          hint={patientContextOk ? "Longitudinal summary is available." : "Patient summary has not loaded yet."}
        />
      </ul>
    </section>
  );
}
