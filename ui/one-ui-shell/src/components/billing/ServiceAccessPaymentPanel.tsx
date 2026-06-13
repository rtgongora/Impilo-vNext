"use client";

/**
 * Pre-service service access / payment requirement UX (COSTA doctrine).
 * Persists via BFF → COSTA {@code /costa/v1/service-access-decisions}.
 */

import { useMemo, useState } from "react";
import { Shield, X } from "lucide-react";
import {
  useRegisterServiceAccessDecision,
  useServiceAccessDecisionsList,
  type ServiceAccessDecisionRow,
} from "@/hooks/queries/useServiceAccessDecisions";

export const BILLING_TIMING_MODES = [
  "PRE_SERVICE",
  "POINT_OF_CARE",
  "POST_SERVICE",
  "PERIODIC",
  "EPISODE_BASED",
  "PACKAGE_BASED",
  "CLAIM_BASED",
  "DEFERRED",
] as const;

export type BillingTimingModeValue = (typeof BILLING_TIMING_MODES)[number];

export const SERVICE_ACCESS_STATUSES = [
  "ALLOWED_WITHOUT_PAYMENT",
  "PAYMENT_REQUIRED_BEFORE_SERVICE",
  "DEPOSIT_REQUIRED",
  "AUTHORISATION_REQUIRED",
  "COVERED_BY_PAYER",
  "EXEMPT",
  "WAIVER_REQUIRED",
  "DEFERRED_PAYMENT_ALLOWED",
  "BLOCKED_PENDING_PAYMENT",
  "BLOCKED_PENDING_AUTHORISATION",
  "EMERGENCY_OVERRIDE",
] as const;

const ACCESS_ACTIONS = [
  { id: "pay_now", label: "Pay now", status: "PAYMENT_REQUIRED_BEFORE_SERVICE" as const },
  { id: "request_authorisation", label: "Request authorisation", status: "AUTHORISATION_REQUIRED" as const },
  { id: "mark_covered", label: "Mark covered", status: "COVERED_BY_PAYER" as const },
  { id: "apply_exemption", label: "Apply exemption", status: "EXEMPT" as const },
  { id: "request_waiver", label: "Request waiver", status: "WAIVER_REQUIRED" as const },
  { id: "allow_deferred", label: "Allow deferred payment", status: "DEFERRED_PAYMENT_ALLOWED" as const },
  { id: "emergency_override", label: "Emergency override", status: "EMERGENCY_OVERRIDE" as const },
  { id: "cancel_service", label: "Cancel service", status: "BLOCKED_PENDING_PAYMENT" as const },
] as const;

export type ServiceAccessPaymentPanelProps = {
  encounterId?: string | null;
  patientId?: string | null;
};

export function BillingTimingDoctrineBanner() {
  return (
    <div className="rounded-lg border border-warning/35 bg-warning-soft/80 p-4 text-sm text-warning-foreground">
      <p className="font-medium">Pre-service billing and access</p>
      <p className="mt-2">
        Some services may require payment, deposit, authorisation, cover confirmation, exemption, waiver, or deferred
        payment approval before service delivery.
      </p>
      <p className="mt-2 text-warning-foreground/90">
        Billing timing depends on service type, facility policy, payer rules, exemption status, and emergency rules.
        COSTA supports pre-service, point-of-care, post-service, claim-based, package-based, periodic, and deferred
        billing; costing continues even when billing has not yet happened.
      </p>
    </div>
  );
}

function formatDecisionRow(row: ServiceAccessDecisionRow) {
  const ts = row.decision_timestamp ? new Date(row.decision_timestamp).toLocaleString() : "—";
  return `${row.access_status} · ${row.billing_timing_mode ?? "—"} · ${ts}`;
}

export function ServiceAccessPaymentPanel({ encounterId, patientId }: ServiceAccessPaymentPanelProps) {
  const [open, setOpen] = useState(false);
  const [timing, setTiming] = useState<BillingTimingModeValue>("PRE_SERVICE");
  const [accessStatus, setAccessStatus] = useState<string>("PAYMENT_REQUIRED_BEFORE_SERVICE");
  const [estimated, setEstimated] = useState("");
  const [tariffLabel, setTariffLabel] = useState("");
  const [serviceName, setServiceName] = useState("");
  const [lastAction, setLastAction] = useState<string | null>(null);

  const { data: decisions = [], isLoading, isError, refetch } = useServiceAccessDecisionsList(encounterId);
  const register = useRegisterServiceAccessDecision();

  const summary = useMemo(
    () => ({
      timing,
      estimatedCost: estimated || "—",
      tariff: tariffLabel || "—",
    }),
    [timing, estimated, tariffLabel],
  );

  async function submitDecision() {
    const body: Record<string, unknown> = {
      access_status: accessStatus,
      billing_timing_mode: timing,
    };
    if (patientId?.trim()) body.patient_cpid = patientId.trim();
    if (encounterId?.trim()) body.encounter_id = encounterId.trim();
    if (serviceName.trim()) body.requested_service_name = serviceName.trim();
    const n = Number.parseFloat(estimated.replace(/[^0-9.-]/g, ""));
    if (!Number.isNaN(n) && estimated.trim()) body.estimated_cost = n;
    if (tariffLabel.trim()) body.requested_service_id = tariffLabel.trim();

    await register.mutateAsync(body);
    setOpen(false);
  }

  return (
    <div className="space-y-3">
      <BillingTimingDoctrineBanner />
      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="inline-flex items-center gap-2 rounded-lg bg-neutral-900 px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover"
        >
          <Shield className="h-4 w-4" />
          Service access / payment requirement
        </button>
        <button
          type="button"
          className="text-xs font-medium text-muted-foreground underline decoration-slate-300 hover:text-foreground"
          onClick={() => void refetch()}
        >
          Refresh decisions
        </button>
      </div>
      {lastAction ? (
        <p className="text-xs text-muted-foreground">
          Last operator path (sets access status): <strong>{lastAction}</strong>
        </p>
      ) : null}
      {isLoading ? (
        <p className="text-xs text-muted-foreground">Loading service access decisions…</p>
      ) : isError ? (
        <p className="text-xs text-warning-foreground">Could not load decisions (check finance role / BFF).</p>
      ) : decisions.length > 0 ? (
        <div className="rounded-lg border border-border bg-card p-3 text-xs text-foreground">
          <p className="font-medium text-foreground">Recent decisions (COSTA)</p>
          <ul className="mt-2 list-disc space-y-1 pl-4">
            {decisions.slice(0, 5).map((d) => (
              <li key={d.service_access_decision_id}>{formatDecisionRow(d)}</li>
            ))}
          </ul>
        </div>
      ) : (
        <p className="text-xs text-muted-foreground">No service access decisions recorded for this filter yet.</p>
      )}
      {register.isError ? (
        <p className="text-xs text-red-600">
          Register failed: {register.error instanceof Error ? register.error.message : "Unknown error"}
        </p>
      ) : null}

      {open ? (
        <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-4 sm:items-center">
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="service-access-title"
            className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl border border-border bg-card shadow-xl"
          >
            <div className="flex items-start justify-between border-b border-border px-5 py-4">
              <div>
                <h2 id="service-access-title" className="text-base font-semibold text-foreground">
                  Service access before care
                </h2>
                <p className="mt-1 text-xs text-muted-foreground">
                  Registers a COSTA ServiceAccessDecision via Experience BFF (persists to costing-engine).
                </p>
              </div>
              <button
                type="button"
                aria-label="Close"
                className="rounded p-1 text-muted-foreground hover:bg-neutral-100"
                onClick={() => setOpen(false)}
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="space-y-4 px-5 py-4 text-sm">
              <label className="block text-xs font-medium text-muted-foreground">
                Access status (required)
                <select
                  className="mt-1 block w-full rounded-lg border border-border px-2 py-2 text-sm"
                  value={accessStatus}
                  onChange={(e) => setAccessStatus(e.target.value)}
                >
                  {SERVICE_ACCESS_STATUSES.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block text-xs font-medium text-muted-foreground">
                Billing timing mode
                <select
                  className="mt-1 block w-full rounded-lg border border-border px-2 py-2 text-sm"
                  value={timing}
                  onChange={(e) => setTiming(e.target.value as BillingTimingModeValue)}
                >
                  {BILLING_TIMING_MODES.map((m) => (
                    <option key={m} value={m}>
                      {m}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block text-xs font-medium text-muted-foreground">
                Requested service name (optional)
                <input
                  className="mt-1 block w-full rounded-lg border border-border px-2 py-2 text-sm"
                  value={serviceName}
                  onChange={(e) => setServiceName(e.target.value)}
                  placeholder="e.g. Initial consultation"
                />
              </label>
              <label className="block text-xs font-medium text-muted-foreground">
                Estimated cost (display / optional number)
                <input
                  className="mt-1 block w-full rounded-lg border border-border px-2 py-2 text-sm"
                  value={estimated}
                  onChange={(e) => setEstimated(e.target.value)}
                  placeholder="e.g. 120.00"
                />
              </label>
              <label className="block text-xs font-medium text-muted-foreground">
                Tariff / service code context (optional)
                <input
                  className="mt-1 block w-full rounded-lg border border-border px-2 py-2 text-sm"
                  value={tariffLabel}
                  onChange={(e) => setTariffLabel(e.target.value)}
                  placeholder="Tariff list code or service id"
                />
              </label>
              <div className="rounded-lg bg-background p-3 text-xs text-foreground">
                <p className="font-medium text-foreground">Summary</p>
                <ul className="mt-2 list-disc space-y-1 pl-4">
                  <li>Encounter: {encounterId?.trim() || "—"}</li>
                  <li>Patient CPID: {patientId?.trim() || "—"}</li>
                  <li>Billing timing: {summary.timing}</li>
                  <li>Access status: {accessStatus}</li>
                  <li>Estimated cost: {summary.estimatedCost}</li>
                  <li>Tariff context: {summary.tariff}</li>
                </ul>
              </div>
              <div>
                <p className="text-xs font-medium text-foreground">Quick actions (sets access status)</p>
                <div className="mt-2 flex flex-wrap gap-2">
                  {ACCESS_ACTIONS.map((a) => (
                    <button
                      key={a.id}
                      type="button"
                      className="rounded-lg border border-border px-2 py-1.5 text-xs font-medium text-foreground hover:bg-background"
                      onClick={() => {
                        setAccessStatus(a.status);
                        setLastAction(a.label);
                      }}
                    >
                      {a.label}
                    </button>
                  ))}
                </div>
              </div>
            </div>
            <div className="flex flex-wrap justify-end gap-2 border-t border-border px-5 py-3">
              <button
                type="button"
                className="rounded-lg border border-border px-4 py-2 text-sm text-foreground hover:bg-background"
                onClick={() => setOpen(false)}
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={register.isPending}
                className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary disabled:opacity-60"
                onClick={() => void submitDecision().catch(() => undefined)}
              >
                {register.isPending ? "Saving…" : "Register decision"}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
