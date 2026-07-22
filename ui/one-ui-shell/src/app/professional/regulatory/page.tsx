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
import { AlertTriangle, BadgeCheck, Loader2, ScrollText, ShieldCheck } from "lucide-react";
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

function unwrap(payload: unknown): Summary {
  const p = (payload as { data?: unknown })?.data ?? payload;
  const s = p as Partial<Summary>;
  return { linked: !!s?.linked, providerPublicId: s?.providerPublicId ?? null, councils: s?.councils ?? [] };
}

const STANDING_STYLE: Record<string, string> = {
  GOOD: "bg-teal-50 text-teal-700 border-teal-200",
  NOT_GOOD: "bg-red-50 text-red-700 border-red-200",
  UNDER_INVESTIGATION: "bg-amber-50 text-amber-800 border-amber-200",
  SUSPENDED: "bg-red-50 text-red-700 border-red-200",
};

export default function MyRegulatoryAffairsPage() {
  const [summary, setSummary] = useState<Summary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/me/regulatory/summary");
      setSummary(unwrap(res));
    } catch {
      setError("Could not load your regulatory affairs.");
    } finally {
      setLoading(false);
    }
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

function Field({ label, value, mono }: { label: string; value?: string | null; mono?: boolean }) {
  return (
    <div>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className={`text-foreground ${mono ? "font-mono" : ""}`}>{value || "—"}</dd>
    </div>
  );
}
