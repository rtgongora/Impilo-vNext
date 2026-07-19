"use client";

/**
 * Facility trust, verification & governance (D-L6/D-L8, FJ6/FJ7/FJ9).
 * Route: /facility/[id]/trust
 *
 * A facility administrator sees the facility's graded TRUST dimensions
 * (transparency), opens a VERIFICATION case (evidence + claimed GPS, checked
 * against the Ndila anchor upstream), and raises GOVERNANCE requests (high-risk
 * update / transfer / duplicate report) that a steward adjudicates. Every submit
 * binds to the session identity and is proven, never faked; downstream verdicts
 * surface verbatim.
 */

import { useMemo, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, CheckCircle2, RefreshCw, ShieldAlert, ShieldCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  facilityLifecycleErrorMessage,
  useFacilityCompletenessDimensions,
  useFacilityDataGaps,
  useFacilityTrustDimensions,
  useFacilityVerificationCases,
  useOpenFacilityGovernanceCase,
  useOpenFacilityVerification,
  useRecomputeFacilityTrust,
  useResolveDataGap,
  type FacilityGovernanceAction,
} from "@/hooks/queries/useFacilityLifecycle";

const inputClass =
  "w-full rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground";

const VERIFICATION_TYPES = ["GPS_ATTESTATION", "DOCUMENT_REVIEW", "VIDEO_ATTESTATION", "SITE_VISIT"];

const GOVERNANCE_ACTIONS: { action: FacilityGovernanceAction; label: string; hint: string }[] = [
  { action: "high-risk-update", label: "High-risk update", hint: "Change name / ownership / location" },
  { action: "transfer", label: "Transfer", hint: "Change administering organisation" },
  { action: "duplicate-report", label: "Report duplicate", hint: "Flag a duplicate facility record" },
];

function trustTone(status?: string): string {
  const s = (status ?? "").toUpperCase();
  if (["VERIFIED", "STRONG", "GREEN", "PASS"].some((k) => s.includes(k)))
    return "border-success/35 bg-success-soft text-success";
  if (["FAIL", "RED", "REVOKED", "WEAK"].some((k) => s.includes(k)))
    return "border-danger/28 bg-danger-soft text-red-800";
  return "border-border bg-muted text-muted-foreground";
}

export default function FacilityTrustPage() {
  const params = useParams();
  const facilityUuid = params.id as string;

  const trust = useFacilityTrustDimensions(facilityUuid);
  const completeness = useFacilityCompletenessDimensions(facilityUuid);
  const dataGaps = useFacilityDataGaps(facilityUuid);
  const resolveGap = useResolveDataGap();
  const recompute = useRecomputeFacilityTrust(facilityUuid);
  const cases = useFacilityVerificationCases(facilityUuid);
  const openVerification = useOpenFacilityVerification();
  const openGovernance = useOpenFacilityGovernanceCase();

  const [verificationType, setVerificationType] = useState(VERIFICATION_TYPES[0]);
  const [evidenceRef, setEvidenceRef] = useState("");
  const [verifyError, setVerifyError] = useState("");
  const [verifySubmitted, setVerifySubmitted] = useState(false);

  const [govNote, setGovNote] = useState("");
  const [govError, setGovError] = useState("");
  const [govSubmittedRef, setGovSubmittedRef] = useState<string | null>(null);

  const dimensions = trust.data ?? [];
  const verificationCases = useMemo(() => cases.data ?? [], [cases.data]);

  function handleOpenVerification(e: React.FormEvent) {
    e.preventDefault();
    setVerifyError("");
    if (!evidenceRef.trim()) {
      setVerifyError("Provide an evidence reference for the verification.");
      return;
    }
    openVerification.mutate(
      { facilityUuid, verificationType, evidenceRefs: [evidenceRef.trim()] },
      {
        onSuccess: () => {
          setVerifySubmitted(true);
          setEvidenceRef("");
        },
        onError: (err) =>
          setVerifyError(facilityLifecycleErrorMessage(err, "The verification case could not be opened.")),
      },
    );
  }

  function raiseGovernance(action: FacilityGovernanceAction) {
    setGovError("");
    setGovSubmittedRef(null);
    openGovernance.mutate(
      { facilityUuid, action, payload: govNote.trim() ? { note: govNote.trim() } : {} },
      {
        onSuccess: (res) => {
          setGovSubmittedRef(res.caseRef ?? "recorded");
          setGovNote("");
        },
        onError: (err) =>
          setGovError(facilityLifecycleErrorMessage(err, "The governance request could not be raised.")),
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Facility trust & governance"
        subtitle="Transparency, verification and change requests for this facility"
      >
        <div className="mx-auto max-w-3xl space-y-8">
          <Link
            href={`/facility/${facilityUuid}`}
            className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back to facility
          </Link>

          {/* Trust dimensions */}
          <section className="space-y-3">
            <div className="flex items-center justify-between">
              <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
                <ShieldCheck className="h-4 w-4 text-primary" /> Trust dimensions
              </h2>
              <button
                type="button"
                onClick={() => recompute.mutate()}
                disabled={recompute.isPending}
                className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:bg-muted disabled:opacity-50"
              >
                <RefreshCw className={`h-3.5 w-3.5 ${recompute.isPending ? "animate-spin" : ""}`} />
                Recompute
              </button>
            </div>
            {trust.isLoading && <p className="text-sm text-muted-foreground">Loading trust dimensions…</p>}
            {!trust.isLoading && dimensions.length === 0 && (
              <p className="text-sm text-muted-foreground">
                No trust dimensions computed yet. Use Recompute to materialise them.
              </p>
            )}
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
              {dimensions.map((d, i) => (
                <div
                  key={`${d.dimension}-${i}`}
                  className="flex items-center justify-between rounded-lg border border-border bg-card p-3"
                  data-testid="trust-dimension-row"
                >
                  <span className="text-sm font-medium text-foreground">
                    {(d.dimension ?? "Dimension").replace(/_/g, " ")}
                  </span>
                  <span
                    className={`rounded-full border px-2.5 py-0.5 text-xs font-medium ${trustTone(d.status)}`}
                  >
                    {d.status ?? "UNKNOWN"}
                  </span>
                </div>
              ))}
            </div>
          </section>

          {/* Completeness & data gaps (V036 / HPA enrichment) */}
          <section className="space-y-3" data-testid="facility-completeness">
            <h2 className="text-sm font-semibold text-foreground">Completeness & data gaps</h2>
            {completeness.data && completeness.data.length > 0 && (
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
                {completeness.data.map((d) => (
                  <div
                    key={d.dimension}
                    className="flex items-center justify-between rounded-lg border border-border bg-card p-2.5"
                    data-testid="completeness-dimension-row"
                  >
                    <span className="text-xs font-medium text-foreground">
                      {(d.dimension ?? "").replace(/_/g, " ")}
                    </span>
                    <span
                      className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${
                        d.status === "COMPLETE"
                          ? "bg-success-soft text-success"
                          : d.status === "PARTIAL"
                            ? "bg-muted text-muted-foreground"
                            : "bg-danger-soft text-red-800"
                      }`}
                    >
                      {d.status ?? "MISSING"}
                    </span>
                  </div>
                ))}
              </div>
            )}
            {dataGaps.isLoading && <p className="text-sm text-muted-foreground">Loading data gaps…</p>}
            {!dataGaps.isLoading && (dataGaps.data ?? []).length === 0 && (
              <p className="text-sm text-muted-foreground">No outstanding data gaps for this facility.</p>
            )}
            <ul className="space-y-2">
              {(dataGaps.data ?? []).map((t) => {
                const done = t.status === "RESOLVED" || t.status === "DISMISSED";
                return (
                  <li
                    key={t.id}
                    className="rounded-lg border border-border bg-card p-3 text-sm"
                    data-testid="data-gap-row"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <p className="font-medium text-foreground">{t.required_information}</p>
                        <p className="mt-0.5 text-xs text-muted-foreground">
                          {[t.dimension, t.responsible_actor, t.priority].filter(Boolean).join(" · ")}
                          {t.accepted_evidence ? ` · evidence: ${t.accepted_evidence}` : ""}
                        </p>
                      </div>
                      {done ? (
                        <span className="shrink-0 rounded-full bg-success-soft px-2 py-0.5 text-[10px] font-medium uppercase text-success">
                          {t.status}
                        </span>
                      ) : (
                        <div className="flex shrink-0 gap-1.5">
                          <button
                            type="button"
                            onClick={() =>
                              t.id != null &&
                              resolveGap.mutate({ facilityUuid, taskId: t.id, status: "RESOLVED" })
                            }
                            disabled={resolveGap.isPending}
                            className="rounded-lg bg-primary px-2.5 py-1 text-xs font-medium text-primary-foreground hover:bg-primary-hover disabled:opacity-50"
                          >
                            Mark resolved
                          </button>
                          <button
                            type="button"
                            onClick={() =>
                              t.id != null &&
                              resolveGap.mutate({ facilityUuid, taskId: t.id, status: "IN_PROGRESS" })
                            }
                            disabled={resolveGap.isPending}
                            className="rounded-lg border border-border px-2.5 py-1 text-xs font-medium text-foreground hover:bg-muted disabled:opacity-50"
                          >
                            In progress
                          </button>
                        </div>
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>
          </section>

          {/* Verification */}
          <section className="space-y-3">
            <h2 className="text-sm font-semibold text-foreground">Verification</h2>
            {verifySubmitted && (
              <div className="flex items-start gap-2 rounded-lg border border-success/35 bg-success-soft p-3 text-sm">
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-success" />
                <span className="text-muted-foreground">
                  Verification case opened. A verifier will review the evidence.
                </span>
              </div>
            )}
            <form onSubmit={handleOpenVerification} className="space-y-3 rounded-2xl border border-border bg-card p-4">
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div>
                  <label htmlFor="vtype" className="mb-1 block text-sm font-medium text-foreground">
                    Verification type
                  </label>
                  <select
                    id="vtype"
                    value={verificationType}
                    onChange={(e) => setVerificationType(e.target.value)}
                    className={inputClass}
                  >
                    {VERIFICATION_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {t.replace(/_/g, " ")}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label htmlFor="vevidence" className="mb-1 block text-sm font-medium text-foreground">
                    Evidence reference
                  </label>
                  <input
                    id="vevidence"
                    type="text"
                    value={evidenceRef}
                    onChange={(e) => setEvidenceRef(e.target.value)}
                    placeholder="Document id"
                    className={inputClass}
                  />
                </div>
              </div>
              {verifyError && (
                <div className="flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft p-3 text-sm text-red-800">
                  <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
                  <span>{verifyError}</span>
                </div>
              )}
              <button
                type="submit"
                disabled={openVerification.isPending}
                className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary-hover disabled:opacity-50"
              >
                {openVerification.isPending ? "Opening…" : "Open verification case"}
              </button>
            </form>

            {verificationCases.length > 0 && (
              <ul className="space-y-2">
                {verificationCases.map((c, i) => (
                  <li
                    key={c.caseRef ?? i}
                    className="flex items-center justify-between rounded-lg border border-border bg-card p-3 text-sm"
                    data-testid="verification-case-row"
                  >
                    <span className="font-medium text-foreground">
                      {(c.verificationType ?? "Verification").replace(/_/g, " ")}
                    </span>
                    <span className="text-xs uppercase tracking-wide text-muted-foreground">
                      {c.status ?? "OPEN"}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {/* Governance */}
          <section className="space-y-3">
            <h2 className="text-sm font-semibold text-foreground">Governance requests</h2>
            {govSubmittedRef && (
              <div className="flex items-start gap-2 rounded-lg border border-success/35 bg-success-soft p-3 text-sm">
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-success" />
                <span className="text-muted-foreground">
                  Request raised{govSubmittedRef !== "recorded" ? ` (${govSubmittedRef})` : ""}. A steward
                  will review it. No change takes effect until it is approved.
                </span>
              </div>
            )}
            <div className="rounded-2xl border border-border bg-card p-4 space-y-3">
              <div>
                <label htmlFor="govnote" className="mb-1 block text-sm font-medium text-foreground">
                  Note (optional)
                </label>
                <textarea
                  id="govnote"
                  value={govNote}
                  onChange={(e) => setGovNote(e.target.value)}
                  rows={2}
                  className={inputClass}
                  placeholder="What is changing and why"
                />
              </div>
              {govError && (
                <div className="flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft p-3 text-sm text-red-800">
                  <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
                  <span>{govError}</span>
                </div>
              )}
              <div className="flex flex-wrap gap-2">
                {GOVERNANCE_ACTIONS.map((g) => (
                  <button
                    key={g.action}
                    type="button"
                    onClick={() => raiseGovernance(g.action)}
                    disabled={openGovernance.isPending}
                    title={g.hint}
                    data-testid={`governance-${g.action}`}
                    className="rounded-lg border border-border px-3 py-2 text-sm font-medium text-foreground hover:border-primary/40 hover:bg-primary/5 disabled:opacity-50"
                  >
                    {g.label}
                  </button>
                ))}
              </div>
            </div>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
