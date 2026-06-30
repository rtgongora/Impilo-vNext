"use client";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { useModerationQueue, useListingDecision } from "@/hooks/queries/useMsikaStore";

export default function ModerationPage() {
  const queue = useModerationQueue();
  const decide = useListingDecision();
  const listings = queue.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Listing Moderation"
        subtitle="Approve, reject or suspend listings against catalogue, safety and eligibility rules. Every decision is audited — there is no admin bypass."
        serviceSlug="msika"
      >
        <div className="space-y-6">
          <NompiloContextualGuidance routePath="/marketplace/seller/moderation" />

          <section className="rounded-2xl border border-border bg-card p-5 shadow-sm">
            {queue.isLoading ? (
              <p className="text-sm text-muted-foreground">Loading queue…</p>
            ) : queue.isError ? (
              <p className="text-sm text-red-600">Could not load the moderation queue.</p>
            ) : listings.length === 0 ? (
              <p className="text-sm text-muted-foreground">No listings awaiting moderation.</p>
            ) : (
              <ul className="divide-y divide-border">
                {listings.map((l) => (
                  <li key={l.listingId} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <div className="font-medium text-foreground">{l.title}</div>
                      <div className="text-xs text-muted-foreground">
                        {l.riskClassification.replace("_", " ")} · {l.sellerType} {l.sellerId} · {l.status.replace("_", " ")}
                      </div>
                    </div>
                    <div className="flex gap-2">
                      <button
                        onClick={() => decide.mutate({ listingId: l.listingId, decision: "approve" })}
                        disabled={decide.isPending}
                        className="rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
                      >
                        Approve
                      </button>
                      <button
                        onClick={() => decide.mutate({ listingId: l.listingId, decision: "reject" })}
                        disabled={decide.isPending}
                        className="rounded-lg border border-red-300 px-3 py-1.5 text-xs font-medium text-red-700 disabled:opacity-50"
                      >
                        Reject
                      </button>
                      <button
                        onClick={() => decide.mutate({ listingId: l.listingId, decision: "suspend" })}
                        disabled={decide.isPending}
                        className="rounded-lg border border-amber-300 px-3 py-1.5 text-xs font-medium text-amber-700 disabled:opacity-50"
                      >
                        Suspend
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
