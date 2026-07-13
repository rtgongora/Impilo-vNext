"use client";

/**
 * Sorting Desk — pre-triage visit-type sort (R7/G12).
 * Route: /work/sorting-desk (auth)
 *
 * The first touch after a patient arrives: assign the journey a visit type within a work context
 * (OUTPATIENT / CASUALTY / INPATIENT / PROCEDURE / COMMUNITY / VIRTUAL) so the Cadre Engine can route
 * it. Fast-track visit types are flagged. The catalogue carries no PHI; the sort record is owned by PCT.
 */

import { useState } from "react";
import { ArrowRight, CheckCircle2, Loader2, Zap } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useVisitTypes, useLatestSort, useSortJourney } from "@/hooks/queries/useSortingDesk";

export default function SortingDeskPage() {
  const [journeyInput, setJourneyInput] = useState("");
  const [journeyId, setJourneyId] = useState("");
  const [context, setContext] = useState("");
  const [visitType, setVisitType] = useState("");
  const [arrivalReason, setArrivalReason] = useState("");

  const catalogQ = useVisitTypes();
  const catalog = catalogQ.data ?? {};
  const contexts = Object.keys(catalog);
  const visitTypes = context ? catalog[context] ?? [] : [];
  const latestQ = useLatestSort(journeyId || undefined);
  const sortM = useSortJourney();

  const selected = visitTypes.find((v) => v.code === visitType);

  return (
    <AppLayout>
      <PageShell title="Sorting desk">
        <div className="mx-auto max-w-2xl space-y-4">
          <p className="text-sm text-muted-foreground">
            Assign an arrived journey its visit type so it can be routed. This sits between arrival and
            triage — it doesn&apos;t change the journey state, it labels it for the Cadre Engine.
          </p>

          {/* Journey lookup */}
          <div className="flex items-end gap-2 rounded-lg border border-border bg-card p-4">
            <label className="flex-1 text-xs text-muted-foreground">
              Journey ID
              <input
                value={journeyInput}
                onChange={(e) => setJourneyInput(e.target.value)}
                placeholder="Journey / encounter id"
                className="mt-0.5 block w-full rounded border border-border px-2 py-1.5 text-sm"
              />
            </label>
            <button
              type="button"
              disabled={!journeyInput.trim()}
              onClick={() => setJourneyId(journeyInput.trim())}
              className="rounded-lg border border-border px-3 py-1.5 text-sm hover:bg-background disabled:opacity-50"
            >
              Load
            </button>
          </div>

          {journeyId && (
            <>
              {/* Current sort */}
              <div className="rounded-lg border border-border bg-card p-4">
                <p className="text-xs font-semibold uppercase text-muted-foreground">Current sort</p>
                {latestQ.isLoading ? (
                  <div className="mt-1 flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</div>
                ) : latestQ.data ? (
                  <p className="mt-1 text-sm text-foreground">
                    {String(latestQ.data.context ?? "")} · <span className="font-medium">{String(latestQ.data.visitType ?? "")}</span>
                    {latestQ.data.arrivalReason ? ` — ${String(latestQ.data.arrivalReason)}` : ""}
                  </p>
                ) : (
                  <p className="mt-1 text-sm text-muted-foreground">Not yet sorted.</p>
                )}
              </div>

              {/* Sort form */}
              <div className="space-y-3 rounded-lg border border-border bg-card p-4">
                <p className="text-xs font-semibold uppercase text-muted-foreground">Sort this journey</p>
                {catalogQ.isLoading ? (
                  <div className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading catalogue…</div>
                ) : (
                  <>
                    <label className="block text-xs text-muted-foreground">
                      Context
                      <select
                        value={context}
                        onChange={(e) => { setContext(e.target.value); setVisitType(""); }}
                        className="mt-0.5 block w-full rounded border border-border bg-card px-2 py-1.5 text-sm"
                      >
                        <option value="">Select context…</option>
                        {contexts.map((c) => <option key={c} value={c}>{c}</option>)}
                      </select>
                    </label>

                    {context && (
                      <label className="block text-xs text-muted-foreground">
                        Visit type
                        <select
                          value={visitType}
                          onChange={(e) => setVisitType(e.target.value)}
                          className="mt-0.5 block w-full rounded border border-border bg-card px-2 py-1.5 text-sm"
                        >
                          <option value="">Select visit type…</option>
                          {visitTypes.map((v) => (
                            <option key={v.code} value={v.code}>{v.label}{v.fastTrackDefault ? " (fast-track)" : ""}</option>
                          ))}
                        </select>
                      </label>
                    )}

                    {selected?.fastTrackDefault && (
                      <p className="flex items-center gap-1 text-xs text-amber-600"><Zap className="h-3 w-3" /> Fast-track visit type</p>
                    )}

                    <label className="block text-xs text-muted-foreground">
                      Arrival reason (optional)
                      <input
                        value={arrivalReason}
                        onChange={(e) => setArrivalReason(e.target.value)}
                        placeholder="Presenting reason for this visit"
                        className="mt-0.5 block w-full rounded border border-border px-2 py-1.5 text-sm"
                      />
                    </label>

                    <button
                      type="button"
                      disabled={sortM.isPending || !context || !visitType}
                      onClick={() =>
                        sortM.mutate(
                          { journeyId, context, visitType, arrivalReason: arrivalReason.trim() || undefined },
                          { onSuccess: () => setArrivalReason("") },
                        )
                      }
                      className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-2 text-sm font-medium text-white hover:bg-primary-hover disabled:opacity-50"
                    >
                      {sortM.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <ArrowRight className="h-4 w-4" />}
                      Sort journey
                    </button>
                    {sortM.isSuccess && (
                      <p className="flex items-center gap-1 text-xs text-green-600"><CheckCircle2 className="h-3.5 w-3.5" /> Journey sorted.</p>
                    )}
                    {sortM.isError && <p className="text-xs text-red-600">Sort was rejected.</p>}
                  </>
                )}
              </div>
            </>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
