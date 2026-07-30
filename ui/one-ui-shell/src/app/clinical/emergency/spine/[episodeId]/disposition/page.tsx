"use client";

/**
 * The episode-level disposition — pct's fifteen types and its mandatory-content gate.
 *
 * Distinct from the ED visit's own coarser disposition: `EdVisitService.recordDisposition` maps its
 * seven outcomes onto these best-effort and deliberately skips any mapping that cannot satisfy the
 * gate, leaving the episode honestly open rather than closing it on invented data. This page is how
 * a clinician closes those episodes properly.
 */

import Link from "next/link";
import { useState } from "react";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { DispositionForm } from "@/features/emergency/disposition/DispositionForm";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useEmergencyDisposition,
  useEmergencyEpisode,
  useRecordEmergencyDisposition,
} from "@/hooks/queries/useEmergencyEpisode";
import { bffErrorMessage } from "@/lib/bff-errors";

export default function EmergencyDispositionPage({ params }: { params: { episodeId: string } }) {
  const { user } = useAuthStore();
  const episode = useEmergencyEpisode(params.episodeId);
  const disposition = useEmergencyDisposition(params.episodeId);
  const record = useRecordEmergencyDisposition(params.episodeId);
  const [serverError, setServerError] = useState<string | null>(null);

  const actor = user?.providerId ?? user?.staffId ?? user?.id ?? "";

  return (
    <AppLayout>
      <PageShell
        title="Episode disposition"
        subtitle={
          episode.data
            ? `Episode ${String(episode.data.episode_reference ?? params.episodeId)}`
            : "pct.emergency_disposition — fifteen types, one per episode"
        }
      >
        <div className="mb-4">
          <Link href={`/clinical/emergency/spine/${params.episodeId}`} className="text-sm text-primary underline">
            ← Episode spine
          </Link>
        </div>

        {disposition.isLoading ? (
          <p className="text-sm text-muted-foreground">Loading the disposition…</p>
        ) : disposition.isError ? (
          <p
            className="mb-4 rounded-lg border border-danger/30 bg-danger-soft p-3 text-sm text-danger"
            data-testid="disposition-unreadable"
          >
            The disposition could not be retrieved. Do not treat this as an absence of a recorded
            disposition — recording a second one would create a competing record.
          </p>
        ) : (
          <DispositionForm
            existing={disposition.data}
            isSubmitting={record.isPending}
            serverError={serverError}
            onSubmit={async (draft) => {
              setServerError(null);
              try {
                await record.mutateAsync({
                  dispositionType: draft.dispositionType,
                  dispositionReason: draft.dispositionReason,
                  destination: draft.destination,
                  causeOrCircumstances: draft.causeOrCircumstances,
                  lastSeenAt: draft.lastSeenAt
                    ? new Date(draft.lastSeenAt).toISOString()
                    : undefined,
                  disposedBy: actor,
                });
              } catch (error) {
                setServerError(bffErrorMessage(error, "this disposition"));
              }
            }}
          />
        )}
      </PageShell>
    </AppLayout>
  );
}
