"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Loader2, Shield } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  BillingTimingDoctrineBanner,
  BILLING_TIMING_MODES,
  SERVICE_ACCESS_STATUSES,
  type BillingTimingModeValue,
} from "@/components/billing/ServiceAccessPaymentPanel";
import {
  useRegisterServiceAccessDecision,
  useServiceAccessDecisionsList,
  type ServiceAccessDecisionRow,
} from "@/hooks/queries/useServiceAccessDecisions";

const EXEMPTION_DEFERRED_STATUSES = SERVICE_ACCESS_STATUSES.filter(
  (status) => status.includes("EXEMPT") || status.includes("DEFERRED") || status.includes("WAIVER"),
);

function formatDecisionRow(row: ServiceAccessDecisionRow) {
  const ts = row.decision_timestamp ? new Date(row.decision_timestamp).toLocaleString() : "—";
  return `${row.access_status} · ${row.billing_timing_mode ?? "—"} · ${ts}`;
}

export default function FinanceServiceAccessPage() {
  const searchParams = useSearchParams();
  const encounterId = searchParams.get("encounterId") ?? "";
  const patientId = searchParams.get("patientId") ?? searchParams.get("patientCpid") ?? searchParams.get("cpid") ?? "";

  const [filterEncounter, setFilterEncounter] = useState(encounterId);
  const [accessStatus, setAccessStatus] = useState("EXEMPT");
  const [timing, setTiming] = useState<BillingTimingModeValue>("PRE_SERVICE");
  const [serviceName, setServiceName] = useState("");
  const [reason, setReason] = useState("");
  const [estimated, setEstimated] = useState("");
  const [submitErr, setSubmitErr] = useState<string | null>(null);

  const decisionsQ = useServiceAccessDecisionsList(filterEncounter.trim() || null);
  const register = useRegisterServiceAccessDecision();

  const decisions = decisionsQ.data ?? [];
  const exemptionDeferredRows = useMemo(
    () =>
      decisions.filter((row) => {
        const status = String(row.access_status ?? "").toUpperCase();
        return status.includes("EXEMPT") || status.includes("DEFERRED") || status.includes("WAIVER");
      }),
    [decisions],
  );

  async function submitDecision() {
    setSubmitErr(null);
    const body: Record<string, unknown> = {
      access_status: accessStatus,
      billing_timing_mode: timing,
    };
    if (patientId.trim()) body.patient_cpid = patientId.trim();
    if (filterEncounter.trim()) body.encounter_id = filterEncounter.trim();
    if (serviceName.trim()) body.requested_service_name = serviceName.trim();
    if (reason.trim()) body.policy_reference = reason.trim();
    const n = Number.parseFloat(estimated.replace(/[^0-9.-]/g, ""));
    if (!Number.isNaN(n) && estimated.trim()) body.estimated_cost = n;

    try {
      await register.mutateAsync(body);
      setReason("");
      setServiceName("");
      setEstimated("");
    } catch {
      setSubmitErr("Could not register service access decision.");
    }
  }

  return (
    <AppLayout>
      <PageShell
        title="Service access decisions"
        subtitle="Exemption, deferred payment, and waiver registration via COSTA service-access-decisions BFF"
        icon={<Shield className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link href="/finance" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Finance dashboard
          </Link>
        </div>

        <div className="space-y-6">
          <BillingTimingDoctrineBanner />

          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Scope</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              GET/POST <code className="text-[10px]">/internal/v1/finance/service-access-decisions</code>
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <input
                value={filterEncounter}
                onChange={(e) => setFilterEncounter(e.target.value)}
                placeholder="Encounter id (optional filter)"
                className="min-w-[220px] flex-1 rounded-lg border border-border px-3 py-2 text-sm font-mono"
                aria-label="Encounter id filter"
              />
              <button
                type="button"
                className="rounded-lg border border-border px-4 py-2 text-sm hover:bg-background"
                onClick={() => void decisionsQ.refetch()}
              >
                Refresh list
              </button>
            </div>
          </section>

          <div className="grid gap-6 lg:grid-cols-2">
            <section className="rounded-xl border border-warning/35 bg-warning-soft/40 p-5">
              <h2 className="text-sm font-semibold text-warning-foreground">Register exemption or deferred access</h2>
              <div className="mt-3 grid gap-3">
                <label className="text-xs text-foreground">
                  Access status
                  <select
                    value={accessStatus}
                    onChange={(e) => setAccessStatus(e.target.value)}
                    className="mt-1 w-full rounded-lg border border-border bg-card px-3 py-2 text-sm"
                  >
                    {EXEMPTION_DEFERRED_STATUSES.map((status) => (
                      <option key={status} value={status}>
                        {status.replace(/_/g, " ")}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="text-xs text-foreground">
                  Billing timing
                  <select
                    value={timing}
                    onChange={(e) => setTiming(e.target.value as BillingTimingModeValue)}
                    className="mt-1 w-full rounded-lg border border-border bg-card px-3 py-2 text-sm"
                  >
                    {BILLING_TIMING_MODES.map((mode) => (
                      <option key={mode} value={mode}>
                        {mode.replace(/_/g, " ")}
                      </option>
                    ))}
                  </select>
                </label>
                <input
                  value={serviceName}
                  onChange={(e) => setServiceName(e.target.value)}
                  placeholder="Requested service name"
                  className="rounded-lg border border-border px-3 py-2 text-sm"
                />
                <input
                  value={estimated}
                  onChange={(e) => setEstimated(e.target.value)}
                  placeholder="Estimated cost (optional)"
                  className="rounded-lg border border-border px-3 py-2 text-sm"
                />
                <input
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="Policy / exemption reason"
                  className="rounded-lg border border-border px-3 py-2 text-sm"
                />
              </div>
              {submitErr ? <p className="mt-2 text-xs text-danger">{submitErr}</p> : null}
              {register.isSuccess ? (
                <p className="mt-2 text-xs text-primary-hover">Decision registered successfully.</p>
              ) : null}
              <button
                type="button"
                disabled={register.isPending}
                onClick={() => void submitDecision()}
                className="mt-3 rounded-lg bg-amber-700 px-4 py-2 text-sm font-medium text-white hover:bg-amber-800 disabled:opacity-50"
              >
                {register.isPending ? <Loader2 className="inline h-4 w-4 animate-spin" /> : null}
                Register decision
              </button>
            </section>

            <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
              <h2 className="text-sm font-semibold text-foreground">Decision history</h2>
              <p className="mt-1 text-xs text-muted-foreground">
                {exemptionDeferredRows.length} exemption/deferred/waiver row(s)
                {filterEncounter.trim() ? ` for encounter ${filterEncounter.trim()}` : " (all scopes)"}.
              </p>
              {decisionsQ.isLoading ? (
                <p className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading…
                </p>
              ) : decisionsQ.isError ? (
                <p className="mt-3 text-sm text-danger">Could not load service access decisions.</p>
              ) : exemptionDeferredRows.length === 0 ? (
                <p className="mt-3 text-sm text-muted-foreground">No exemption or deferred decisions on record.</p>
              ) : (
                <ul className="mt-3 space-y-2 text-xs">
                  {exemptionDeferredRows.map((row) => (
                    <li
                      key={row.service_access_decision_id}
                      className="rounded-lg border border-border bg-background px-3 py-2"
                    >
                      <p className="font-medium text-foreground">{formatDecisionRow(row)}</p>
                      {row.requested_service_name ? (
                        <p className="text-muted-foreground">{row.requested_service_name}</p>
                      ) : null}
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
