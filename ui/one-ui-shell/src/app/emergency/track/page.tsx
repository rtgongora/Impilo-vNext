"use client";

/**
 * Track entry — look up a raised emergency by its request id and open its live status.
 * Route: /emergency/track
 */

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Activity, Search } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";

export default function EmergencyTrackEntryPage() {
  const router = useRouter();
  const [id, setId] = useState("");

  const go = () => {
    const trimmed = id.trim();
    if (trimmed) router.push(`/emergency/track/${encodeURIComponent(trimmed)}`);
  };

  return (
    <AppLayout>
      <PageShell
        title="Track an emergency"
        subtitle="Enter the request id from your SOS to see live status"
        serviceSlug="daidzai"
      >
        <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
          <div className="space-y-3 rounded-2xl border border-border bg-card p-5 shadow-sm">
            <label className="block text-sm">
              <span className="mb-1 block font-medium">Request id</span>
              <input
                value={id}
                onChange={(e) => setId(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") go();
                }}
                className="w-full rounded-lg border border-border bg-background px-3 py-2 font-mono text-sm"
                placeholder="paste the request id"
              />
            </label>
            <button
              type="button"
              onClick={go}
              disabled={!id.trim()}
              className="inline-flex items-center gap-1.5 rounded-lg bg-teal-600 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-700 disabled:opacity-60"
            >
              <Search className="h-4 w-4" /> Track
            </button>
            <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <Activity className="h-3.5 w-3.5" /> After sending an SOS you are taken here
              automatically.
            </p>
          </div>

          <NompiloContextualGuidance routePath="/emergency/track/[id]" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
