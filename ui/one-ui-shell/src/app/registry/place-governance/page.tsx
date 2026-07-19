"use client";

/**
 * Place Governance Console (steward) — D-L4/D-L5/D-L6/D-L8, FJ6/FJ7/FJ9 + SJ1/SJ2.
 * Route: /registry/place-governance
 *
 * The steward review surface for the place self-service case types that the IATG
 * Trust Console does NOT own (it owns the facility-admin-appointment queue). Here
 * a registry steward triages:
 *   - facility & site registration cases (by outcome),
 *   - site operator-grant requests (approve),
 *   - facility verification & governance cases (decide APPROVE/REJECT), per facility.
 *
 * Every action is steward-gated upstream (X-Actor-Type); the UI role guard is
 * defence-in-depth. Decisions bind to the session identity and propagate the
 * upstream verdict verbatim — nothing is faked, no change takes effect until a
 * decision is recorded.
 */

import { useState } from "react";
import { Building2, CheckCircle2, ClipboardCheck, MapPinned, ShieldAlert, ShieldCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  facilityLifecycleErrorMessage,
  useDecideFacilityGovernance,
  useDecideFacilityVerification,
  useFacilityGovernanceCases,
  useFacilityRegistrationCases,
  useFacilityVerificationCases,
} from "@/hooks/queries/useFacilityLifecycle";
import {
  useApproveOperatorGrant,
  useOperatorGrantQueue,
  useSiteRegistrationCases,
} from "@/hooks/queries/useSiteSelfService";

const inputClass =
  "w-full rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground";

const REG_OUTCOMES = ["DUPLICATE_REVIEW", "PROVISIONAL_CREATED", "MATCHED_EXISTING", "REJECTED"];
const GOV_TYPES = ["HIGH_RISK_UPDATE", "TRANSFER", "DUPLICATE_REPORT"];

function Section({
  icon,
  title,
  children,
}: {
  icon: React.ReactNode;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section className="space-y-3 rounded-2xl border border-border bg-card p-5">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
        {icon}
        {title}
      </h2>
      {children}
    </section>
  );
}

export default function PlaceGovernanceConsolePage() {
  // Global registration review queues
  const [facilityOutcome, setFacilityOutcome] = useState(REG_OUTCOMES[0]);
  const [siteOutcome, setSiteOutcome] = useState(REG_OUTCOMES[0]);
  const facilityRegCases = useFacilityRegistrationCases(facilityOutcome);
  const siteRegCases = useSiteRegistrationCases(siteOutcome);

  // Site operator-grant approvals
  const grantQueue = useOperatorGrantQueue("PENDING");
  const approveGrant = useApproveOperatorGrant();

  // Facility-scoped verification + governance decisions
  const [facilityUuid, setFacilityUuid] = useState("");
  const [activeFacility, setActiveFacility] = useState("");
  const [govType, setGovType] = useState(GOV_TYPES[0]);
  const verificationCases = useFacilityVerificationCases(activeFacility);
  const governanceCases = useFacilityGovernanceCases(activeFacility, govType, !!activeFacility);
  const decideVerification = useDecideFacilityVerification();
  const decideGovernance = useDecideFacilityGovernance();

  const [error, setError] = useState("");

  function loadFacility(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setActiveFacility(facilityUuid.trim());
  }

  function decide(
    kind: "verification" | "governance",
    caseId: string | undefined,
    decision: "APPROVED" | "REJECTED",
  ) {
    if (!caseId || !activeFacility) return;
    setError("");
    const input = { facilityUuid: activeFacility, caseId, decision };
    const onError = (err: unknown) =>
      setError(facilityLifecycleErrorMessage(err, "The decision could not be recorded."));
    if (kind === "verification") {
      decideVerification.mutate(input, { onError });
    } else {
      decideGovernance.mutate(input, { onError });
    }
  }

  return (
    <AppLayout>
      <PageShell
        title="Place governance"
        subtitle="Steward review of facility & site registration, verification and governance cases"
      >
        <div className="mx-auto max-w-3xl space-y-6">
          {error && (
            <div className="flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft p-3 text-sm text-red-800">
              <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {/* Facility registrations */}
          <Section icon={<Building2 className="h-4 w-4 text-primary" />} title="Facility registrations">
            <select
              value={facilityOutcome}
              onChange={(e) => setFacilityOutcome(e.target.value)}
              className={inputClass}
            >
              {REG_OUTCOMES.map((o) => (
                <option key={o} value={o}>
                  {o.replace(/_/g, " ")}
                </option>
              ))}
            </select>
            <QueueList
              loading={facilityRegCases.isLoading}
              empty="No facility registration cases in this outcome."
              rows={(facilityRegCases.data ?? []).map((c) => ({
                key: c.caseRef ?? "",
                primary: c.submittedName ?? c.caseRef ?? "Registration",
                secondary: [c.outcome, c.createdAt].filter(Boolean).join(" · "),
                testid: "facility-reg-case-row",
              }))}
            />
          </Section>

          {/* Site registrations */}
          <Section icon={<MapPinned className="h-4 w-4 text-primary" />} title="Site registrations">
            <select value={siteOutcome} onChange={(e) => setSiteOutcome(e.target.value)} className={inputClass}>
              {REG_OUTCOMES.map((o) => (
                <option key={o} value={o}>
                  {o.replace(/_/g, " ")}
                </option>
              ))}
            </select>
            <QueueList
              loading={siteRegCases.isLoading}
              empty="No site registration cases in this outcome."
              rows={(siteRegCases.data ?? []).map((c) => ({
                key: c.caseRef ?? "",
                primary: c.submittedName ?? c.caseRef ?? "Registration",
                secondary: [c.outcome, c.createdAt].filter(Boolean).join(" · "),
                testid: "site-reg-case-row",
              }))}
            />
          </Section>

          {/* Site operator-grant approvals */}
          <Section icon={<ClipboardCheck className="h-4 w-4 text-primary" />} title="Site operator-access requests">
            {grantQueue.isLoading && <p className="text-sm text-muted-foreground">Loading requests…</p>}
            {!grantQueue.isLoading && (grantQueue.data ?? []).length === 0 && (
              <p className="text-sm text-muted-foreground">No pending operator-access requests.</p>
            )}
            <ul className="space-y-2">
              {(grantQueue.data ?? []).map((g) => (
                <li
                  key={g.id}
                  className="flex items-center justify-between gap-2 rounded-lg border border-border bg-background p-3 text-sm"
                  data-testid="operator-grant-queue-row"
                >
                  <div className="min-w-0">
                    <p className="truncate font-medium text-foreground">
                      {(g.role ?? "Operator").replace(/_/g, " ")}
                    </p>
                    <p className="truncate text-xs text-muted-foreground">
                      Site {g.siteId} · {g.approvalState ?? "PENDING"}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => g.id && approveGrant.mutate(g.id)}
                    disabled={approveGrant.isPending}
                    data-testid="approve-operator-grant"
                    className="shrink-0 rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary-hover disabled:opacity-50"
                  >
                    Approve
                  </button>
                </li>
              ))}
            </ul>
          </Section>

          {/* Facility-scoped verification + governance */}
          <Section icon={<ShieldCheck className="h-4 w-4 text-primary" />} title="Facility verification & governance">
            <form onSubmit={loadFacility} className="flex gap-2">
              <input
                type="text"
                value={facilityUuid}
                onChange={(e) => setFacilityUuid(e.target.value)}
                placeholder="Facility UUID"
                className={inputClass}
              />
              <button
                type="submit"
                className="shrink-0 rounded-lg border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-muted"
              >
                Load
              </button>
            </form>

            {activeFacility && (
              <>
                <div className="space-y-2">
                  <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                    Verification cases
                  </p>
                  <QueueDecisionList
                    loading={verificationCases.isLoading}
                    empty="No verification cases for this facility."
                    rows={(verificationCases.data ?? []).map((c) => ({
                      key: c.caseRef ?? "",
                      caseId: c.caseRef,
                      primary: (c.verificationType ?? "Verification").replace(/_/g, " "),
                      secondary: c.status ?? "OPEN",
                      testid: "steward-verification-row",
                    }))}
                    pending={decideVerification.isPending}
                    onDecide={(caseId, decision) => decide("verification", caseId, decision)}
                  />
                </div>

                <div className="space-y-2">
                  <div className="flex items-center gap-2">
                    <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                      Governance cases
                    </p>
                    <select
                      value={govType}
                      onChange={(e) => setGovType(e.target.value)}
                      className="rounded-lg border border-border bg-background px-2 py-1 text-xs"
                    >
                      {GOV_TYPES.map((t) => (
                        <option key={t} value={t}>
                          {t.replace(/_/g, " ")}
                        </option>
                      ))}
                    </select>
                  </div>
                  <QueueDecisionList
                    loading={governanceCases.isLoading}
                    empty="No governance cases of this type for this facility."
                    rows={(governanceCases.data ?? []).map((c) => ({
                      key: c.caseRef ?? "",
                      caseId: c.caseRef,
                      primary: (c.caseType ?? "Governance").replace(/_/g, " "),
                      secondary: c.status ?? "OPEN",
                      testid: "steward-governance-row",
                    }))}
                    pending={decideGovernance.isPending}
                    onDecide={(caseId, decision) => decide("governance", caseId, decision)}
                  />
                </div>
              </>
            )}
          </Section>
        </div>
      </PageShell>
    </AppLayout>
  );
}

interface Row {
  key: string;
  primary: string;
  secondary: string;
  testid: string;
}

function QueueList({ loading, empty, rows }: { loading: boolean; empty: string; rows: Row[] }) {
  if (loading) return <p className="text-sm text-muted-foreground">Loading…</p>;
  if (rows.length === 0) return <p className="text-sm text-muted-foreground">{empty}</p>;
  return (
    <ul className="space-y-2">
      {rows.map((r) => (
        <li
          key={r.key}
          className="rounded-lg border border-border bg-background p-3 text-sm"
          data-testid={r.testid}
        >
          <p className="font-medium text-foreground">{r.primary}</p>
          {r.secondary && <p className="mt-0.5 text-xs text-muted-foreground">{r.secondary}</p>}
        </li>
      ))}
    </ul>
  );
}

function QueueDecisionList({
  loading,
  empty,
  rows,
  pending,
  onDecide,
}: {
  loading: boolean;
  empty: string;
  rows: (Row & { caseId?: string })[];
  pending: boolean;
  onDecide: (caseId: string | undefined, decision: "APPROVED" | "REJECTED") => void;
}) {
  if (loading) return <p className="text-sm text-muted-foreground">Loading…</p>;
  if (rows.length === 0) return <p className="text-sm text-muted-foreground">{empty}</p>;
  return (
    <ul className="space-y-2">
      {rows.map((r) => (
        <li
          key={r.key}
          className="flex items-center justify-between gap-2 rounded-lg border border-border bg-background p-3 text-sm"
          data-testid={r.testid}
        >
          <div className="min-w-0">
            <p className="truncate font-medium text-foreground">{r.primary}</p>
            <p className="truncate text-xs text-muted-foreground">{r.secondary}</p>
          </div>
          <div className="flex shrink-0 gap-2">
            <button
              type="button"
              onClick={() => onDecide(r.caseId, "APPROVED")}
              disabled={pending}
              className="inline-flex items-center gap-1 rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary-hover disabled:opacity-50"
            >
              <CheckCircle2 className="h-3.5 w-3.5" /> Approve
            </button>
            <button
              type="button"
              onClick={() => onDecide(r.caseId, "REJECTED")}
              disabled={pending}
              className="rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:bg-muted disabled:opacity-50"
            >
              Reject
            </button>
          </div>
        </li>
      ))}
    </ul>
  );
}
