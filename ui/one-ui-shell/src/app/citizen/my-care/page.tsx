"use client";

import Link from "next/link";
import { AlertTriangle, Activity, FileText, Clock, Receipt, Share2, ArrowRight } from "lucide-react";
import { useCitizenHealthSummary } from "@/hooks/queries/useCitizenHealthSummary";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";

type AnyRecord = Record<string, unknown>;

/** Pull a human-readable label from a heterogeneous clinical record. */
function labelOf(record: AnyRecord): string {
  for (const key of ["name", "display", "title", "label", "text", "description", "code"]) {
    const v = record[key];
    if (typeof v === "string" && v.trim().length > 0) return v;
    if (v && typeof v === "object") {
      const nested = (v as AnyRecord).display ?? (v as AnyRecord).text ?? (v as AnyRecord).code;
      if (typeof nested === "string" && nested.trim().length > 0) return nested;
    }
  }
  return "Unnamed entry";
}

/** Real, wired citizen routes this hub hands off into (all registered in routes.ts). */
const CONTINUE_CARE = [
  { href: "/home/conditions", title: "My conditions", desc: "Active problems on your record", Icon: Activity },
  { href: "/home/results", title: "My results", desc: "Lab and diagnostic results", Icon: FileText },
  { href: "/citizen/wallet/timeline", title: "Care timeline", desc: "Everything that has happened, in order", Icon: Clock },
  { href: "/citizen/wallet/records", title: "Health records", desc: "Your full health record", Icon: FileText },
  { href: "/citizen/wallet/payments", title: "Payments & bills", desc: "Charges, cover and receipts", Icon: Receipt },
  { href: "/citizen/record-sharing", title: "Share my record", desc: "Give a provider time-boxed access", Icon: Share2 },
] as const;

export default function CitizenMyCarePage() {
  const { isLoading, isError, refetch, conditions, allergies, results } = useCitizenHealthSummary();

  const hasGlance = conditions.length > 0 || allergies.length > 0 || results.length > 0;

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <h1 className="text-lg font-semibold text-foreground">My care</h1>
          <p className="text-xs text-muted-foreground">
            Your health at a glance, and everything you need to carry on with your care.
          </p>
        </div>
        <FeatureMaturityBadge
          status="partial"
          detail="Live at-a-glance from your health record. Live queue and visit-status aggregation is being expanded."
        />
      </header>

      {isLoading && (
        <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
          Loading your care summary…
        </div>
      )}

      {isError && !isLoading && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4 text-sm">
          <p className="font-medium text-foreground">We couldn&apos;t load your care summary.</p>
          <button
            type="button"
            onClick={() => refetch()}
            className="mt-2 rounded-md border border-border px-3 py-1.5 text-xs font-medium hover:border-primary/30"
          >
            Try again
          </button>
        </div>
      )}

      {!isLoading && !isError && (
        <section className="space-y-4" aria-live="polite">
          <h2 className="text-sm font-semibold text-foreground">Your health at a glance</h2>

          {!hasGlance ? (
            <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
              Nothing to show yet. Once you have a visit, results or an active condition, it will appear here.
            </div>
          ) : (
            <div className="grid gap-3 sm:grid-cols-3">
              {/* Allergies first — safety critical */}
              <div className="rounded-lg border border-border bg-card p-4">
                <div className="flex items-center gap-2 text-sm font-medium text-foreground">
                  <AlertTriangle className="h-4 w-4 text-amber-600" aria-hidden />
                  Allergies
                </div>
                <p className="mt-1 text-2xl font-semibold text-foreground">{allergies.length}</p>
                {allergies.length > 0 && (
                  <ul className="mt-2 space-y-0.5 text-xs text-muted-foreground">
                    {allergies.slice(0, 3).map((a, i) => (
                      <li key={i} className="truncate">{labelOf(a)}</li>
                    ))}
                  </ul>
                )}
              </div>

              <div className="rounded-lg border border-border bg-card p-4">
                <div className="flex items-center gap-2 text-sm font-medium text-foreground">
                  <Activity className="h-4 w-4 text-primary" aria-hidden />
                  Active conditions
                </div>
                <p className="mt-1 text-2xl font-semibold text-foreground">{conditions.length}</p>
                {conditions.length > 0 && (
                  <ul className="mt-2 space-y-0.5 text-xs text-muted-foreground">
                    {conditions.slice(0, 3).map((c, i) => (
                      <li key={i} className="truncate">{labelOf(c)}</li>
                    ))}
                  </ul>
                )}
              </div>

              <div className="rounded-lg border border-border bg-card p-4">
                <div className="flex items-center gap-2 text-sm font-medium text-foreground">
                  <FileText className="h-4 w-4 text-primary" aria-hidden />
                  Recent results
                </div>
                <p className="mt-1 text-2xl font-semibold text-foreground">{results.length}</p>
                <Link href="/home/results" className="mt-2 inline-block text-xs font-medium text-primary hover:underline">
                  View results
                </Link>
              </div>
            </div>
          )}
        </section>
      )}

      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-foreground">Continue your care</h2>
        <div className="grid gap-3 sm:grid-cols-2">
          {CONTINUE_CARE.map(({ href, title, desc, Icon }) => (
            <Link
              key={href}
              href={href}
              className="group flex items-center gap-3 rounded-lg border border-border bg-card p-4 transition-colors hover:border-primary/40"
            >
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary-soft text-primary">
                <Icon className="h-4 w-4" aria-hidden />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-medium text-foreground">{title}</span>
                <span className="block truncate text-xs text-muted-foreground">{desc}</span>
              </span>
              <ArrowRight className="h-4 w-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5" aria-hidden />
            </Link>
          ))}
        </div>
      </section>

      <p className="text-[11px] text-muted-foreground">
        Following a specific visit or hospital stay? Open it from your care timeline or the reference your care team gave you.
      </p>
    </div>
  );
}
