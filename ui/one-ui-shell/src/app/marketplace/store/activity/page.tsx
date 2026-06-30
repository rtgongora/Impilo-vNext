"use client";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { useStoreActivity } from "@/hooks/queries/useMsikaStore";

export default function MarketplaceActivityPage() {
  const activity = useStoreActivity();
  const raw = activity.data;
  const unavailable = raw && typeof raw === "object" && (raw as Record<string, unknown>).status === "UNAVAILABLE";

  return (
    <AppLayout>
      <PageShell
        title="My Marketplace Activity"
        subtitle="Your requests, bookings and orders. The timeline composes Msika (the request), Costa (billing), MusheX (payment) and the fulfilment owner."
        serviceSlug="msika"
      >
        <div className="space-y-6">
          <NompiloContextualGuidance routePath="/marketplace/store/activity" />

          <section className="rounded-2xl border border-border bg-card p-5 shadow-sm">
            {activity.isLoading ? (
              <p className="text-sm text-muted-foreground">Loading your activity…</p>
            ) : activity.isError ? (
              <p className="text-sm text-red-600">Activity is unavailable right now.</p>
            ) : unavailable ? (
              <p className="text-sm text-amber-700">
                The order service (msika-flow) is unavailable — your activity could not be loaded. This is shown honestly,
                not as an empty list.
              </p>
            ) : raw == null ? (
              <p className="text-sm text-muted-foreground">No marketplace activity yet.</p>
            ) : (
              <pre className="overflow-auto rounded-lg bg-background p-3 text-xs text-foreground">
                {typeof raw === "string" ? raw : JSON.stringify(raw, null, 2)}
              </pre>
            )}
          </section>
          <p className="rounded-lg border border-border bg-background px-4 py-2 text-xs text-muted-foreground">
            Orders, billing and payment status are owned by msika-flow, Costa and MusheX respectively — Msika composes,
            it does not own them.
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
