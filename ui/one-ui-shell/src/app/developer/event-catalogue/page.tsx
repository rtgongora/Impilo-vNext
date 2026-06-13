"use client";

import Link from "next/link";
import { useState } from "react";
import { BookOpen, ChevronLeft, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useEventCatalogue, type EventCatalogueEntry } from "@/hooks/queries/useHealthOsLauncher";

export default function EventCataloguePage() {
  const [classification, setClassification] = useState<"INTERNAL_PLATFORM" | "EXTERNALLY_PUBLISHABLE" | "ALL">(
    "ALL",
  );
  const eventsQuery = useEventCatalogue({
    classification: classification === "ALL" ? undefined : classification,
  });

  const events = eventsQuery.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Event catalogue"
        subtitle="Source of truth for which events flow inside the sovereign Health OS and which are curated for external app subscription. Maintained by the Integration Hub governance plane."
        icon={<BookOpen className="h-6 w-6" />}
      >
        <div className="space-y-5">
          <Link href="/developer" className="inline-flex items-center gap-1 text-sm text-primary-hover hover:underline">
            <ChevronLeft className="h-4 w-4" /> Back to developer portal
          </Link>

          <section className="flex flex-wrap items-center gap-2 rounded-2xl border border-border bg-card p-3 shadow-sm">
            {(["ALL", "INTERNAL_PLATFORM", "EXTERNALLY_PUBLISHABLE"] as const).map((c) => (
              <button
                key={c}
                type="button"
                onClick={() => setClassification(c)}
                className={`rounded-full px-3 py-1.5 text-xs font-semibold ${
                  classification === c
                    ? "bg-primary-hover text-white"
                    : "bg-neutral-100 text-foreground hover:bg-primary-soft"
                }`}
              >
                {c.replace("_", " ")}
              </button>
            ))}
          </section>

          <section className="overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
            <table className="w-full text-left text-sm">
              <thead className="bg-background text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                <tr>
                  <th className="px-4 py-2">Topic</th>
                  <th className="px-4 py-2">Owner</th>
                  <th className="px-4 py-2">Sensitivity</th>
                  <th className="px-4 py-2">Schema</th>
                  <th className="px-4 py-2">Externally publishable?</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {eventsQuery.isLoading ? (
                  <tr>
                    <td colSpan={5} className="px-4 py-6 text-center text-muted-foreground">
                      <Loader2 className="mx-auto h-4 w-4 animate-spin" />
                    </td>
                  </tr>
                ) : events.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-4 py-6 text-center text-muted-foreground">
                      No events available with the current filter.
                    </td>
                  </tr>
                ) : (
                  events.map((e: EventCatalogueEntry) => (
                    <tr key={e.id}>
                      <td className="px-4 py-3 font-mono text-xs text-foreground">
                        {e.topic}
                        {e.deprecated ? (
                          <span className="ml-1 rounded bg-warning-soft px-1 text-[9px] text-warning-foreground">deprecated</span>
                        ) : null}
                      </td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">{e.ownerServiceId}</td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">{e.sensitivityTier}</td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">
                        {e.schemaRef ? (
                          <a
                            href={e.schemaRef}
                            className="text-primary-hover hover:underline"
                            target="_blank"
                            rel="noreferrer"
                          >
                            {e.schemaRef.split("/").slice(-1)[0]} (v{e.schemaVersion ?? "?"})
                          </a>
                        ) : (
                          "—"
                        )}
                      </td>
                      <td className="px-4 py-3 text-xs">
                        {e.partnerPublishable ? (
                          <span className="rounded-full bg-success-soft px-2 py-0.5 text-[10px] font-semibold text-primary-hover">
                            EXTERNALLY PUBLISHABLE
                          </span>
                        ) : (
                          <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-[10px] font-semibold text-muted-foreground">
                            INTERNAL ONLY
                          </span>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </section>

          <section className="rounded-2xl border border-border bg-background p-4 text-xs text-muted-foreground">
            <p>
              <strong>Doctrine.</strong> Internal platform events power coordination between sovereign services and
              are not automatically exposed to external apps. Only events with classification{" "}
              <code>EXTERNALLY_PUBLISHABLE</code> may be subscribed to by approved external applications via a webhook
              subscription with an active integration contract.
            </p>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
