"use client";

/**
 * Index route for the encounter segment.
 *
 * Fixes QA #11: navigating to `/ehr/{patientId}/encounter` with no encounter id
 * (e.g. a builder that emitted the bare path, or a stale/queue link when no
 * encounter is active) previously hit the framework 404 ("Page Unavailable")
 * because only `[encounterId]/` existed. This resolves the patient's active
 * encounter and forwards to it, or falls back to the encounters list.
 */

import { useEffect } from "react";
import { useParams, useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounters } from "@/hooks/queries/useEncounters";

export default function EncounterIndexRedirect() {
  const params = useParams();
  const router = useRouter();
  const patientId = String(params.patientId ?? "");
  const { data, isPending } = useEncounters(patientId);

  useEffect(() => {
    if (!patientId || isPending) return;
    const encounters = data?.data ?? [];
    const active = encounters.find(
      (e) => e.attributes.status === "ACTIVE" || e.attributes.status === "IN_PROGRESS",
    );
    if (active) {
      router.replace(`/ehr/${patientId}/encounter/${active.id}`);
    } else {
      router.replace(`/ehr/${patientId}/encounters`);
    }
  }, [patientId, isPending, data, router]);

  return (
    <AppLayout>
      <PageShell title="Encounter" subtitle="Opening the active encounter…">
        <div className="flex items-center gap-2 text-muted-foreground">
          <Loader2 className="h-5 w-5 animate-spin" /> Loading encounter…
        </div>
      </PageShell>
    </AppLayout>
  );
}
