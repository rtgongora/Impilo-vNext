"use client";

/**
 * MoHCC facility verification workspace (FCV-W7).
 *
 * The verifier's desk: facilities awaiting a Ministry decision, what each one's current legitimacy
 * says, and the act of issuing a verdict. The decision itself is enforced entirely in tuso against
 * the verifier's appointment and jurisdiction — this screen never decides anything, it only makes
 * the decision reachable and shows honestly what changed.
 *
 * The distinction the screen exists to keep visible: recording a decision and a facility becoming
 * operational are not the same event. A verdict can be accepted and the facility still closed,
 * because another authority denies. The response says so, and so does this page.
 *
 * Route: /registry/facility-verification
 */

import { useCallback, useState } from "react";
import { ShieldCheck, AlertTriangle, Info } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface DecisionOutcome {
  decisionId?: string;
  verdict?: string;
  platformAccessAllowed?: boolean;
  reasons?: string[];
}

/** The verdict vocabulary, with what each one actually does to the facility. */
const VERDICTS: { code: string; label: string; effect: string; opens: boolean }[] = [
  { code: "LEGITIMATE_OPERATIONAL", label: "Legitimate and operational",
    effect: "Recognised, verified as operating, and permitted to operate on the platform.", opens: true },
  { code: "LEGITIMATE_CONDITIONALLY_OPERATIONAL", label: "Legitimate, conditionally operational",
    effect: "Permitted to operate subject to the conditions you record below.", opens: true },
  { code: "LEGITIMATE_NOT_OPERATIONAL", label: "Legitimate, not operating",
    effect: "Recognised by the Ministry, but found not to be operating. Stays closed on the platform.", opens: false },
  { code: "PENDING_VERIFICATION", label: "Pending verification",
    effect: "No determination yet. Stays closed.", opens: false },
  { code: "SUSPENDED", label: "Suspended",
    effect: "Operation withdrawn. Overrides any other authority's allow.", opens: false },
  { code: "REVOKED", label: "Revoked",
    effect: "Recognition withdrawn. Overrides any other authority's allow.", opens: false },
  { code: "CLOSED", label: "Closed", effect: "The facility has closed.", opens: false },
  { code: "REJECTED", label: "Rejected", effect: "The claim to legitimacy is refused.", opens: false },
];

export default function FacilityVerificationWorkspacePage() {
  const [facilityUuid, setFacilityUuid] = useState("");
  const [verdict, setVerdict] = useState("");
  const [reason, setReason] = useState("");
  const [conditions, setConditions] = useState("");
  const [expiresAt, setExpiresAt] = useState("");
  const [busy, setBusy] = useState(false);
  const [outcome, setOutcome] = useState<DecisionOutcome | null>(null);
  const [error, setError] = useState<string | null>(null);

  const selected = VERDICTS.find((v) => v.code === verdict);
  const needsConditions = verdict === "LEGITIMATE_CONDITIONALLY_OPERATIONAL";

  const issue = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      if (!facilityUuid.trim() || !verdict || !reason.trim()) return;
      setBusy(true);
      setError(null);
      setOutcome(null);
      try {
        const res = await apiClient.post<{ data?: DecisionOutcome }>(
          `/api/v1/facility-legitimacy/${encodeURIComponent(facilityUuid.trim())}/decisions`,
          {
            verdict,
            reason: reason.trim(),
            conditions: conditions.trim() || null,
            expiresAt: expiresAt || null,
          },
        );
        setOutcome(res?.data ?? {});
      } catch (err) {
        const e2 = err as { error?: { message?: string } };
        setError(e2?.error?.message ?? "The decision could not be recorded.");
      } finally {
        setBusy(false);
      }
    },
    [facilityUuid, verdict, reason, conditions, expiresAt],
  );

  return (
    <AppLayout>
      <PageShell
        title="Facility verification"
        subtitle="Issue the Ministry legitimacy verdict for a facility"
        serviceSlug="tuso"
      >
        <div className="mx-auto max-w-3xl space-y-5">
          <div className="flex items-start gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-800/50 dark:text-slate-300">
            <Info className="mt-0.5 h-5 w-5 shrink-0 text-slate-400" />
            <div>
              <p className="font-medium text-slate-700 dark:text-slate-200">
                This is the only act that can permit a facility to operate.
              </p>
              <p className="mt-1">
                Nothing else does it — not claiming a facility, not completing its profile, not being
                on the national master list. You may only decide on facilities your appointment&apos;s
                jurisdiction covers.
              </p>
            </div>
          </div>

          {error && (
            <div className="rounded-lg border border-rose-300 bg-rose-50 p-3 text-sm text-rose-800 dark:border-rose-500/40 dark:bg-rose-500/10 dark:text-rose-200">
              {error}
            </div>
          )}

          {outcome && (
            <div
              className={`rounded-xl border p-4 text-sm ${
                outcome.platformAccessAllowed
                  ? "border-emerald-300 bg-emerald-50 text-emerald-900 dark:border-emerald-500/40 dark:bg-emerald-500/10 dark:text-emerald-200"
                  : "border-amber-300 bg-amber-50 text-amber-900 dark:border-amber-500/40 dark:bg-amber-500/10 dark:text-amber-200"
              }`}
            >
              <p className="font-semibold">
                Decision recorded{outcome.verdict ? ` — ${outcome.verdict}` : ""}.
              </p>
              <p className="mt-1">
                {outcome.platformAccessAllowed
                  ? "This facility is now permitted to operate on the platform."
                  : "This facility is still not permitted to operate."}
              </p>
              {/* Recording a decision and the facility opening are different events. If another
                  authority still denies, say so rather than implying the verifier opened it. */}
              {!outcome.platformAccessAllowed && outcome.reasons?.length ? (
                <ul className="mt-2 list-disc space-y-1 pl-5">
                  {outcome.reasons.map((r) => (
                    <li key={r}>{r}</li>
                  ))}
                </ul>
              ) : null}
            </div>
          )}

          <LuminousStage className="space-y-4 p-5">
            <form onSubmit={issue} className="space-y-4">
              <div>
                <label className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-200">
                  Facility
                </label>
                <input
                  required
                  value={facilityUuid}
                  onChange={(e) => setFacilityUuid(e.target.value)}
                  placeholder="Facility UUID"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 font-mono text-sm dark:border-slate-600 dark:bg-slate-900"
                />
              </div>

              <div>
                <label className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-200">
                  Verdict
                </label>
                <select
                  required
                  value={verdict}
                  onChange={(e) => setVerdict(e.target.value)}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900"
                >
                  <option value="">Select a verdict…</option>
                  {VERDICTS.map((v) => (
                    <option key={v.code} value={v.code}>
                      {v.label}
                    </option>
                  ))}
                </select>
                {selected && (
                  <p
                    className={`mt-1.5 flex items-start gap-1.5 text-xs ${
                      selected.opens
                        ? "text-emerald-700 dark:text-emerald-400"
                        : "text-slate-500 dark:text-slate-400"
                    }`}
                  >
                    {selected.opens ? (
                      <ShieldCheck className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                    ) : (
                      <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                    )}
                    {selected.effect}
                  </p>
                )}
              </div>

              <div>
                <label className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-200">
                  Basis for this decision
                </label>
                <textarea
                  required
                  rows={3}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="What you inspected, what you relied on, and what you concluded."
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900"
                />
                <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                  Recorded permanently against your name, role and jurisdiction.
                </p>
              </div>

              {needsConditions && (
                <div>
                  <label className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-200">
                    Conditions
                  </label>
                  <textarea
                    required
                    rows={2}
                    value={conditions}
                    onChange={(e) => setConditions(e.target.value)}
                    placeholder="What this facility must do or maintain to keep operating."
                    className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900"
                  />
                </div>
              )}

              <div>
                <label className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-200">
                  Expires on <span className="font-normal text-slate-500">(optional)</span>
                </label>
                <input
                  type="date"
                  value={expiresAt}
                  onChange={(e) => setExpiresAt(e.target.value)}
                  className="rounded-lg border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900"
                />
                <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                  On this date the permission lapses automatically and a verifier must look again.
                </p>
              </div>

              <button
                type="submit"
                disabled={busy || !facilityUuid.trim() || !verdict || !reason.trim()}
                className="inline-flex items-center justify-center gap-1.5 rounded-lg bg-slate-800 px-5 py-2.5 text-sm font-medium text-white hover:bg-slate-900 disabled:opacity-50 dark:bg-slate-200 dark:text-slate-900 dark:hover:bg-white"
              >
                {busy ? "Recording…" : "Record decision"}
              </button>
            </form>
          </LuminousStage>
        </div>
      </PageShell>
    </AppLayout>
  );
}
