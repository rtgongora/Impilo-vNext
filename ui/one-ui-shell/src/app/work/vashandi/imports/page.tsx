"use client";

import { VashandiShell } from "@/components/vashandi/VashandiShell";
import { VashandiFriendlyBlockedState } from "@/components/vashandi/VashandiFriendlyBlockedState";

export default function Page() {
  return (
    <VashandiShell title="Workforce Imports" subtitle="Bulk workforce profile and assignment import bridge">
      <div className="rounded-2xl border border-border bg-card p-6 text-sm text-muted-foreground">
        Workforce import bridge is exposed via BFF reconcile and governance import flows. No standalone import endpoint
        is registered on <code className="text-xs">/internal/v1/vashandi/**</code> yet — use Administration & Governance
        bulk import or reconcile workforce profiles after upstream registry sync.
      </div>
      <VashandiFriendlyBlockedState
        state="pending_backend"
        title="Import bridge pending UI wiring"
        description="Connect this surface when vashandi-workforce-service import bridge is exposed on the BFF."
        backHref="/work/vashandi"
      />
    </VashandiShell>
  );
}
