"use client";

/**
 * Death case detail — real case via experience-BFF (GET /internal/v1/death/cases/[id]).
 * Shows confirmation, medico-legal + public-health screening, certification + registration
 * status with the audit trail, and links onward to certification, mortuary and registration.
 * Respectful language throughout. No forensic detail is surfaced beyond the medico-legal flag.
 * Route: /work/clinical/death-cases/[id]
 */

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { HeartCrack, Loader2, RefreshCw, ShieldAlert, FileText, Building2, ClipboardList } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { DeathFieldPathwayPanels } from "@/components/death/DeathFieldPathwayPanels";
import { apiClient } from "@/lib/api-client";

interface DeathCase {
  caseId?: string;
  id?: string;
  patientCpid?: string;
  deceasedIdentityStatus?: string;
  temporaryIdentityRef?: string;
  placeOfDeathContext?: string;
  placeOfDeathLocation?: string;
  deathDatetime?: string;
  confirmedBy?: string;
  confirmedByRole?: string;
  confirmedAt?: string;
  coronerReferralRequired?: boolean;
  medicoLegalTriggers?: string;
  coronerReferralStatus?: string;
  publicHealthFlags?: string;
  deathReviewRequired?: boolean;
  certificationStatus?: string;
  civilRegistrationStatus?: string;
  bodyReleaseBlocked?: boolean;
}
interface AuditEntry {
  auditId?: string;
  eventType?: string;
  reason?: string;
  actorId?: string;
  actorRole?: string;
  occurredAt?: string;
}

function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Could not load this death case. Please try again.";
}

export default function DeathCaseDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params?.id ?? "";
  const [data, setData] = useState<DeathCase | null>(null);
  const [audit, setAudit] = useState<AuditEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const c = await apiClient.get<DeathCase>(`/internal/v1/death/cases/${id}`);
      setData(c);
      try {
        const a = await apiClient.get<unknown>(`/internal/v1/death/cases/${id}/audit`);
        setAudit(Array.isArray(a) ? (a as AuditEntry[]) : []);
      } catch {
        setAudit([]);
      }
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <AppLayout>
      <PageShell title="Death case" subtitle="Confirmation, screening, certification and next steps" icon={<HeartCrack className="h-5 w-5" />}>
        {loading ? (
          <div className="flex items-center justify-center py-16 text-muted-foreground">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-sm text-red-700">
            <p className="mb-3">{error}</p>
            <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 bg-white px-3 py-1.5 font-medium text-red-700 hover:bg-red-100">
              <RefreshCw className="h-3.5 w-3.5" /> Retry
            </button>
          </div>
        ) : !data ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">Death case not found.</div>
        ) : (
          <div className="space-y-6">
            {data.coronerReferralRequired && (
              <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
                <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0" />
                <div>
                  <p className="font-medium">This death needs the competent authority</p>
                  <p className="mt-1">A coroner / medico-legal referral is required ({data.medicoLegalTriggers || "—"}). Routine certification and body release are held until the authority responds. Status: {data.coronerReferralStatus ?? "PENDING_REFERRAL"}.</p>
                </div>
              </div>
            )}

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="rounded-xl border border-border bg-card p-4">
                <h3 className="mb-2 text-sm font-semibold">Confirmation</h3>
                <dl className="space-y-1 text-sm text-muted-foreground">
                  <div className="flex justify-between"><dt>Person</dt><dd>{data.patientCpid ?? (data.deceasedIdentityStatus === "KNOWN" ? "—" : `Unidentified (${data.temporaryIdentityRef ?? "temp"})`)}</dd></div>
                  <div className="flex justify-between"><dt>Time of death</dt><dd>{data.deathDatetime ? new Date(data.deathDatetime).toLocaleString() : "—"}</dd></div>
                  <div className="flex justify-between"><dt>Place</dt><dd>{data.placeOfDeathContext ?? "—"}{data.placeOfDeathLocation ? ` · ${data.placeOfDeathLocation}` : ""}</dd></div>
                  <div className="flex justify-between"><dt>Confirmed by</dt><dd>{data.confirmedBy ?? "—"} {data.confirmedByRole ? `(${data.confirmedByRole})` : ""}</dd></div>
                </dl>
              </div>
              <div className="rounded-xl border border-border bg-card p-4">
                <h3 className="mb-2 text-sm font-semibold">Screening &amp; status</h3>
                <dl className="space-y-1 text-sm text-muted-foreground">
                  <div className="flex justify-between"><dt>Public-health flags</dt><dd>{data.publicHealthFlags || "None"}</dd></div>
                  <div className="flex justify-between"><dt>Mortality review</dt><dd>{data.deathReviewRequired ? "Required" : "Not required"}</dd></div>
                  <div className="flex justify-between"><dt>Certification</dt><dd>{data.certificationStatus ?? "NOT_STARTED"}</dd></div>
                  <div className="flex justify-between"><dt>Registration</dt><dd>{data.civilRegistrationStatus ?? "NOT_STARTED"}</dd></div>
                </dl>
              </div>
            </div>

            <div className="flex flex-wrap gap-2">
              <Link href="/work/clinical/mortality-certification" className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-3 py-1.5 text-sm font-medium hover:bg-background"><FileText className="h-3.5 w-3.5" /> Certify cause of death</Link>
              <Link href="/work/mortuary" className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-3 py-1.5 text-sm font-medium hover:bg-background"><Building2 className="h-3.5 w-3.5" /> Mortuary &amp; body</Link>
              <Link href="/work/crvs/death-registration" className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-3 py-1.5 text-sm font-medium hover:bg-background"><ClipboardList className="h-3.5 w-3.5" /> Registration</Link>
            </div>

            {/* Community / field death pathway (G16) — verbal autopsy + non-facility body management. */}
            <DeathFieldPathwayPanels caseId={id} />

            <div className="rounded-xl border border-border bg-card p-4">
              <h3 className="mb-3 text-sm font-semibold">Audit &amp; amendment trail</h3>
              {audit.length === 0 ? (
                <p className="text-sm text-muted-foreground">No recorded events yet.</p>
              ) : (
                <ul className="space-y-2 text-sm">
                  {audit.map((a) => (
                    <li key={a.auditId} className="flex items-start justify-between gap-4 border-b border-slate-100 pb-2 last:border-0">
                      <div>
                        <span className="font-medium">{a.eventType}</span>
                        <span className="ml-2 text-muted-foreground">{a.reason}</span>
                      </div>
                      <div className="shrink-0 text-right text-xs text-muted-foreground">
                        {a.actorId} {a.actorRole ? `(${a.actorRole})` : ""}<br />
                        {a.occurredAt ? new Date(a.occurredAt).toLocaleString() : ""}
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        )}

        <div className="mt-6">
          <NompiloContextualGuidance routePath="/work/clinical/death-cases/[id]" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
