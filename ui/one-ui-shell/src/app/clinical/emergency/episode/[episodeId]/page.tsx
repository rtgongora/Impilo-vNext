"use client";

/**
 * Consolidated trauma-episode page (WU5) — the whole journey for one canonical
 * trauma_episode_id (INCIDENT→ED→RESUS→BLOOD→DISPOSITION) plus the trauma-scoped
 * unknown-patient identity mint + reconcile surface. Route:
 * /clinical/emergency/episode/{episodeId}
 */

import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { TraumaEpisodeTimeline } from "@/components/clinical/TraumaEpisodeTimeline";
import { TraumaIdentityReconcile } from "@/components/clinical/TraumaIdentityReconcile";
import { useTraumaEpisode } from "@/hooks/queries/useDaidzai";

export default function TraumaEpisodePage({ params }: { params: { episodeId: string } }) {
  const episode = useTraumaEpisode(params.episodeId);
  const mode = episode.data?.subjectIdentityMode ? String(episode.data.subjectIdentityMode) : undefined;
  const unidentified = mode === "ANONYMOUS" || mode === "PROVISIONAL" || !episode.data?.subjectHealthId;

  return (
    <AppLayout>
      <PageShell title="Trauma episode" subtitle="One canonical episode — the full spine across every phase owner">
        <div className="mb-4 flex justify-end">
          <Link href="/clinical/emergency" className="text-sm text-primary underline">← ED trackboard</Link>
        </div>
        <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
          <TraumaEpisodeTimeline episodeId={params.episodeId} />
          <div className="space-y-4">
            {unidentified && <TraumaIdentityReconcile subjectIdentityMode={mode} />}
            <NompiloContextualGuidance routePath="/clinical/emergency/episode" />
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
