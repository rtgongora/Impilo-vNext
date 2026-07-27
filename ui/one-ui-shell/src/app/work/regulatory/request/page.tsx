"use client";

/**
 * Request regulatory access (RB-6). Closes the picker dead-end: a person with no regulatory
 * appointment could see "appointments are issued by a registrar" and do nothing. This authenticated
 * page lets them request to found a regulator's administration through the RB-3 bootstrap rail
 * (POST /api/v1/regulator-bootstrap/requests) — verified against authoritative records, under
 * four-eyes, never self-activating.
 *
 * The submit is LOA3-gated and fail-closed at the BFF: an unproven (or assurance-unreachable)
 * claimant is refused with IDENTITY_PROOFING_REQUIRED, and this page routes them to identity
 * verification rather than swallowing it.
 *
 * Route: /work/regulatory/request
 */

import { useCallback, useState } from "react";
import Link from "next/link";
import { ArrowLeft, ShieldCheck, Info, CheckCircle2 } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import { REGULATORY_ORGS } from "@/lib/regulatory/organisations";

interface ApiError {
  status?: number;
  error?: { code?: string; message?: string };
}

/** The founding role — the only role this bootstrap rail may grant, and only when a regulator has
 *  zero active administrators (org-registry enforces both). Ordinary roles come by invitation. */
const FOUNDING_ROLE = "FOUNDING_REGULATOR_ADMINISTRATOR";

export default function RegulatoryAccessRequestPage() {
  const [organisationId, setOrganisationId] = useState("");
  const [evidenceRef, setEvidenceRef] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState<{ publicId?: string } | null>(null);
  const [error, setError] = useState<{ code?: string; text: string } | null>(null);

  const submit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      if (!organisationId || !evidenceRef.trim()) return;
      setSubmitting(true);
      setError(null);
      try {
        const res = await apiClient.post<{ data?: { publicId?: string; public_id?: string } }>(
          "/api/v1/regulator-bootstrap/requests",
          {
            organisationId,
            claimedRoleCode: FOUNDING_ROLE,
            evidenceRef: evidenceRef.trim(),
          },
        );
        const data = res?.data ?? {};
        setSubmitted({ publicId: data.publicId ?? data.public_id });
      } catch (err) {
        const e2 = err as ApiError;
        setError({
          code: e2?.error?.code,
          text: e2?.error?.message || "The request could not be submitted.",
        });
      } finally {
        setSubmitting(false);
      }
    },
    [organisationId, evidenceRef],
  );

  const needsProofing = error?.code === "IDENTITY_PROOFING_REQUIRED";

  return (
    <AppLayout>
      <PageShell
        title="Request regulatory access"
        subtitle="Request to found a regulator's administration"
        serviceSlug="varapi"
      >
        <div className="mx-auto max-w-2xl space-y-5">
          <Link
            href="/work/regulatory"
            className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
          >
            <ArrowLeft className="h-4 w-4" /> Back to regulatory workspaces
          </Link>

          {submitted ? (
            <LuminousStage className="space-y-3 p-6 text-center">
              <CheckCircle2 className="mx-auto h-10 w-10 text-emerald-500" />
              <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-100">
                Request submitted
              </h2>
              <p className="text-sm text-slate-500 dark:text-slate-400">
                Your request to found this regulator&apos;s administration has been recorded
                {submitted.publicId ? (
                  <>
                    {" "}
                    as <span className="font-mono">{submitted.publicId}</span>
                  </>
                ) : null}
                . It is verified against authoritative records and requires two separate approvers —
                the person who submits it can never be one of them. You will be notified when it is
                activated or if more information is needed.
              </p>
            </LuminousStage>
          ) : (
            <>
              <div className="flex items-start gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-800/50 dark:text-slate-300">
                <Info className="mt-0.5 h-5 w-5 shrink-0 text-slate-400" />
                <div>
                  <p className="font-medium text-slate-700 dark:text-slate-200">
                    This is the founding path, used once per regulator.
                  </p>
                  <p className="mt-1">
                    It applies only when a regulator has no administrator yet. If administrators are
                    already in place, ask one of them to invite you into a role instead — that is the
                    ordinary path and does not need this request.
                  </p>
                </div>
              </div>

              {error && (
                <div className="rounded-lg border border-rose-300 bg-rose-50 p-3 text-sm text-rose-800 dark:border-rose-500/40 dark:bg-rose-500/10 dark:text-rose-200">
                  <p>{error.text}</p>
                  {needsProofing && (
                    <Link
                      href="/citizen/wallet/identity"
                      className="mt-2 inline-flex items-center gap-1 font-medium underline"
                    >
                      <ShieldCheck className="h-4 w-4" /> Verify your identity
                    </Link>
                  )}
                </div>
              )}

              <LuminousStage className="space-y-4 p-5">
                <form onSubmit={submit} className="space-y-4">
                  <div>
                    <label className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-200">
                      Regulator
                    </label>
                    <select
                      required
                      value={organisationId}
                      onChange={(e) => setOrganisationId(e.target.value)}
                      className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900"
                    >
                      <option value="">Select a regulator…</option>
                      {REGULATORY_ORGS.map((o) => (
                        <option key={o.id} value={o.id}>
                          {o.name} ({o.code})
                        </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-200">
                      Evidence of authority
                    </label>
                    <input
                      type="text"
                      required
                      value={evidenceRef}
                      onChange={(e) => setEvidenceRef(e.target.value)}
                      placeholder="Gazette notice, council minute or appointment letter reference"
                      className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900"
                    />
                    <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                      A reference reviewers can check against authoritative records. Your request is
                      verified against those records before any approval.
                    </p>
                  </div>

                  <button
                    type="submit"
                    disabled={submitting || !organisationId || !evidenceRef.trim()}
                    className="inline-flex items-center justify-center gap-1.5 rounded-lg bg-slate-800 px-5 py-2.5 text-sm font-medium text-white hover:bg-slate-900 disabled:opacity-50 dark:bg-slate-200 dark:text-slate-900 dark:hover:bg-white"
                  >
                    {submitting ? "Submitting…" : "Submit request"}
                  </button>
                </form>
              </LuminousStage>
            </>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
