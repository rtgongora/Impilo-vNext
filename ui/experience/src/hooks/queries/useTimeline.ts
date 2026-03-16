/**
 * Experience UI — Timeline Query Hooks
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface TimelineEntryResource {
  id: string;
  type: "timeline_entry";
  attributes: {
    patientId: string;
    encounterId: string | null;
    eventType: string;
    title: string;
    description: string | null;
    sourceType: string;
    sourceId: string;
    actorName: string | null;
    occurredAt: string;
  };
}

type TimelineResponse = ApiResponse<TimelineEntryResource[]>;

export function useTimeline(patientId: string) {
  return useQuery<TimelineResponse>({
    queryKey: ["timeline", { patientId }],
    queryFn: () =>
      apiClient.get<TimelineResponse>(
        `/internal/v1/timeline?patient_id=${encodeURIComponent(patientId)}`
      ),
    enabled: !!patientId,
  });
}

export function useEncounterTimeline(encounterId: string) {
  return useQuery<TimelineResponse>({
    queryKey: ["timeline", { encounterId }],
    queryFn: () =>
      apiClient.get<TimelineResponse>(
        `/internal/v1/timeline?encounter_id=${encodeURIComponent(encounterId)}`
      ),
    enabled: !!encounterId,
  });
}
