"use client";

/**
 * My Regulatory Affairs (ROM-W3). The signed-in professional's own regulatory standing across
 * every council they are registered with — registrations, register, good standing and any
 * restrictions. Reads /internal/v1/me/regulatory/summary, which resolves the caller's OWN record
 * from the trust context (never a providerId param), so this is genuinely self-service.
 *
 * Route: /professional/regulatory
 */

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  BadgeCheck,
  ChevronRight,
  FileText,
  Loader2,
  RefreshCw,
  ScrollText,
  ShieldCheck,
} from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface Restriction {
  type: string;
  description: string;
  status: string;
  validFrom?: string | null;
  validTo?: string | null;
}
interface CouncilRegistration {
  councilCode: string;
  councilName: string;
  registrationNumber?: string | null;
  registrationCategory?: string | null;
  status: string;
  standing: string;
  registrationDate?: string | null;
  expiryDate?: string | null;
  restrictions: Restriction[];
}
interface Summary {
  linked: boolean;
  providerPublicId?: string | null;
  councils: CouncilRegistration[];
}
interface RenewalEligibility {
  linked: boolean;
  providerPublicId?: string | null;
  eligible: boolean;
  cpdRequirementMet: boolean;
  earnedPoints: number;
  requiredPoints: number;
  outstanding: string[];
}
interface Application {
  id: number;
  applicationType: string;
  workflowState: string;
  councilCode: string;
  submittedAt?: string | null;
}

function unwrapObject<T>(payload: unknown): T {
  const p = (payload as { data?: unknown })?.data ?? payload;
  return p as T;
}
function unwrapList<T>(payload: unknown): T[] {
  const p = (payload as { data?: unknown })?.data ?? payload;
  return Array.isArray(p) ? (p as T[]) : [];
}
function unwrap(payload: unknown): Summary {
  const s = unwrapObject<Partial<Summary>>(payload);
  return { linked: !!s?.linked, providerPublicId: s?.providerPublicId ?? null, councils: s?.councils ?? [] };
}
function unwrapRenewal(payload: unknown): RenewalEligibility {
  const r = unwrapObject<Partial<RenewalEligibility>>(payload);
  return {
    linked: !!r?.linked,
    providerPublicId: r?.providerPublicId ?? null,
    eligible: !!r?.eligible,
    cpdRequirementMet: !!r?.cpdRequirementMet,
    earnedPoints: Number(r?.earnedPoints ?? 0),
    requiredPoints: Number(r?.requiredPoints ?? 0),
    outstanding: r?.outstanding ?? [],
  };
}

const STANDING_STYLE: Record<string, string> = {
  GOOD: "bg-teal-50 text-teal-700 border-teal-200",
  NOT_GOOD: "bg-red-50 text-red-700 border-red-200",
  UNDER_INVESTIGATION: "bg-amber-50 text-amber-800 border-amber-200",
  SUSPENDED: "bg-red-50 text-red-700 border-red-200",
};

export default function MyRegulatoryAffairsPage() {
  const [summary, setSummary] = useState<Summary | null>(null);
  const [renewal, setRenewal] = useState<RenewalEligibility | null>(null);
  const [applications, setApplications] = useState<Application[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    // Fetch all three in parallel; tolerate individual failures so one failing
    // lane never blanks the whole page. The summary is the primary lane — only it
    // sets the page-level error.
    const [summaryRes, renewalRes, applicationsRes] = await Promise.allSettled([
      apiClient.get<unknown>("/internal/v1/me/regulatory/summary"),
      apiClient.get<unknown>("/internal/v1/me/regulatory/renewal-eligibility"),
      apiClient.get<unknown>("/internal/v1/me/regulatory/applications"),
    ]);
    if (summaryRes.status === "fulfilled") {
      setSummary(unwrap(summaryRes.value));
    } else {
      setError("Could not load your regulatory affairs.");
    }
    setRenewal(renewalRes.status === "fulfilled" ? unwrapRenewal(renewalRes.value) : null);
    setApplications(applicationsRes.status === "fulfilled" ? unwrapList<Application>(applicationsRes.value) : []);
    setLoading(false);
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <AppLayout>
      <PageShell
        title="My Regulatory Affairs"
        subtitle="Your registration and standing with each health regulator — in one place."
        serviceSlug="varapi"
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
          {!loading && !error && renewal?.linked && <RenewalCard renewal={renewal} />}

          {loading ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading…
            </div>
          ) : error ? (
            <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</div>
          ) : !summary?.linked ? (
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
              We could not find a professional registration linked to your account. If you are a
              registered practitioner, activate your Provider ID first — your council registrations
              will then appear here.
            </div>
          ) : summary.councils.length === 0 ? (
            <div className="rounded-lg border border-border bg-muted/40 p-4 text-sm text-muted-foreground">
              Your account is linked, but you have no council registrations on record yet.
            </div>
          ) : (
            summary.councils.map((c, i) => (
              <section key={`${c.councilCode}-${i}`} className="rounded-2xl border border-border bg-card p-4">
                <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <BadgeCheck className="h-4 w-4 text-teal-600" />
                    <span className="text-sm font-semibold text-foreground">{c.councilName}</span>
                    <span className="text-xs text-muted-foreground">({c.councilCode})</span>
                  </div>
                  <span
                    className={`rounded-full border px-2 py-0.5 text-[11px] font-medium ${
                      STANDING_STYLE[c.standing] ?? "border-border text-muted-foreground"
                    }`}
                  >
                    {c.standing.replace(/_/g, " ").toLowerCase()}
                  </span>
                </div>
                <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs sm:grid-cols-4">
                  <Field label="Registration no." value={c.registrationNumber} mono />
                  <Field label="Category" value={c.registrationCategory} />
                  <Field label="Status" value={c.status?.replace(/_/g, " ").toLowerCase()} />
                  <Field label="Expires" value={c.expiryDate} />
                </dl>
                {c.restrictions.length > 0 && (
                  <div className="mt-3 space-y-1.5 rounded-lg border border-amber-200 bg-amber-50 p-2.5">
                    <div className="flex items-center gap-1.5 text-xs font-semibold text-amber-900">
                      <AlertTriangle className="h-3.5 w-3.5" /> Conditions on your practice
                    </div>
                    {c.restrictions.map((r, j) => (
                      <div key={j} className="text-xs text-amber-900">
                        <span className="font-medium">{r.type.replace(/_/g, " ").toLowerCase()}:</span>{" "}
                        {r.description}
                      </div>
                    ))}
                  </div>
                )}
              </section>
            ))
          )}

          {!loading && !error && summary?.linked && (
            <section className="space-y-3">
              <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
                <FileText className="h-4 w-4 text-teal-600" /> My applications
              </h2>
              {applications.length === 0 ? (
                <p className="rounded-lg border border-border bg-muted/40 p-3 text-sm text-muted-foreground">
                  No applications in progress.
                </p>
              ) : (
                <ul className="space-y-2">
                  {applications.map((a) => (
                    <li key={a.id}>
                      <Link
                        href={`/professional/regulatory/applications/${a.id}`}
                        className="flex items-center justify-between gap-3 rounded-xl border border-border bg-card p-3 transition hover:border-teal-300 hover:bg-teal-50/40"
                      >
                        <div className="min-w-0">
                          <div className="truncate text-sm font-medium text-foreground">
                            {a.applicationType?.replace(/_/g, " ") || "Application"}
                            <span className="ml-1.5 text-xs font-normal text-muted-foreground">· {a.councilCode}</span>
                          </div>
                          <div className="mt-0.5 text-xs text-muted-foreground">
                            {a.workflowState?.replace(/_/g, " ").toLowerCase()}
                            {a.submittedAt ? ` · submitted ${a.submittedAt}` : ""}
                          </div>
                        </div>
                        <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" />
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          )}

          <div className="flex items-start gap-2 rounded-lg border border-border bg-muted/40 p-3 text-xs text-muted-foreground">
            <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-teal-600" />
            <span>
              This is your own record, drawn live from each regulator. To renew, apply, or request a
              certificate of good standing, use the actions in each council&apos;s section as they
              become available.
            </span>
          </div>
          <p className="flex items-center gap-1.5 text-[11px] text-muted-foreground">
            <ScrollText className="h-3.5 w-3.5" /> Registrations are held by the councils; Impilo
            displays them — it does not decide them.
          </p>
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}

function RenewalCard({ renewal }: { renewal: RenewalEligibility }) {
  const pct =
    renewal.requiredPoints > 0
      ? Math.min(100, Math.round((renewal.earnedPoints / renewal.requiredPoints) * 100))
      : renewal.cpdRequirementMet
        ? 100
        : 0;

  if (renewal.eligible) {
    return (
      <section className="rounded-2xl border border-teal-200 bg-teal-50 p-4">
        <div className="flex items-center gap-2 text-sm font-semibold text-teal-800">
          <RefreshCw className="h-4 w-4" /> You can renew
        </div>
        <p className="mt-1 text-xs text-teal-800">
          You have met the requirements to renew your registration. Renewal is CPD-gated by your
          council; you may proceed when the council opens your renewal window.
        </p>
        <div className="mt-2 text-xs text-teal-800">
          {renewal.earnedPoints} of {renewal.requiredPoints} CPD points
        </div>
      </section>
    );
  }

  return (
    <section className="rounded-2xl border border-amber-200 bg-amber-50 p-4">
      <div className="flex items-center gap-2 text-sm font-semibold text-amber-900">
        <RefreshCw className="h-4 w-4" /> Not yet eligible to renew
      </div>
      <div className="mt-2 text-xs text-amber-900">
        {renewal.earnedPoints} of {renewal.requiredPoints} CPD points
      </div>
      <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-amber-200">
        <div className="h-full rounded-full bg-amber-500" style={{ width: `${pct}%` }} />
      </div>
      {renewal.outstanding.length > 0 && (
        <ul className="mt-3 space-y-1">
          {renewal.outstanding.map((o, i) => (
            <li key={i} className="flex items-start gap-1.5 text-xs text-amber-900">
              <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" /> {o}
            </li>
          ))}
        </ul>
      )}
      <p className="mt-3 text-[11px] text-amber-800">
        Renewal is CPD-gated by your council — it becomes available once your continuing-development
        requirement is met.
      </p>
    </section>
  );
}

function Field({ label, value, mono }: { label: string; value?: string | null; mono?: boolean }) {
  return (
    <div>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className={`text-foreground ${mono ? "font-mono" : ""}`}>{value || "—"}</dd>
    </div>
  );
}
