"use client";

/**
 * Observation / short stay for one emergency episode.
 *
 * pct has modelled this since W9b (V208) and it had no BFF proxy and no UI until now, so the one
 * state that is neither an admission nor a discharge could not be recorded at all.
 */

import Link from "next/link";
import { useState } from "react";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { ObservationStayPanel } from "@/features/emergency/observation/ObservationStayPanel";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useEmergencyEpisode,
  useEmergencyObservationStayActions,
  useEmergencyObservationStays,
} from "@/hooks/queries/useEmergencyEpisode";
import { bffErrorMessage } from "@/lib/bff-errors";

export default function EmergencyObservationPage({ params }: { params: { episodeId: string } }) {
  const { user } = useAuthStore();
  const episode = useEmergencyEpisode(params.episodeId);
  const stays = useEmergencyObservationStays(params.episodeId);
  const actions = useEmergencyObservationStayActions(params.episodeId);
  const [serverError, setServerError] = useState<string | null>(null);

  const actor = user?.providerId ?? user?.staffId ?? user?.id ?? "";

  return (
    <AppLayout>
      <PageShell
        title="Observation / short stay"
        subtitle={
          episode.data
            ? `Episode ${String(episode.data.episode_reference ?? params.episodeId)}`
            : "pct.emergency_observation_stay"
        }
      >
        <div className="mb-4">
          <Link href={`/clinical/emergency/spine/${params.episodeId}`} className="text-sm text-primary underline">
            ← Episode spine
          </Link>
        </div>

        <ObservationStayPanel
          stays={stays.data}
          isLoading={stays.isLoading}
          isError={stays.isError}
          isMutating={actions.start.isPending || actions.end.isPending}
          serverError={serverError}
          onStart={async ({ reason }) => {
            setServerError(null);
            try {
              await actions.start.mutateAsync({ reason, startedBy: actor });
            } catch (error) {
              setServerError(bffErrorMessage(error, "this observation stay"));
            }
          }}
          onEnd={async ({ stayId, outcome }) => {
            setServerError(null);
            try {
              await actions.end.mutateAsync({ stayId, outcome, endedBy: actor });
            } catch (error) {
              setServerError(bffErrorMessage(error, "this observation stay"));
            }
          }}
        />
      </PageShell>
    </AppLayout>
  );
}
