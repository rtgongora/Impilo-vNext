/**
 * Experience UI — Clinical Notes Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface ClinicalNoteResource {
  id: string;
  type: "clinical_note";
  attributes: {
    patientId: string;
    encounterId: string;
    noteType: string;
    subjective: string | null;
    objective: string | null;
    assessment: string | null;
    plan: string | null;
    body: string | null;
    authorId: string;
    authorName: string;
    status: string;
    signedAt: string | null;
    createdAt: string;
  };
}

interface CreateNotePayload {
  patientId: string;
  encounterId: string;
  noteType: string;
  subjective?: string | null;
  objective?: string | null;
  assessment?: string | null;
  plan?: string | null;
  body?: string | null;
  [key: string]: unknown;
}

type ClinicalNotesResponse = ApiResponse<ClinicalNoteResource[]>;
type ClinicalNoteResponse = ApiResponse<ClinicalNoteResource>;

export function useClinicalNotes(patientId: string) {
  return useQuery<ClinicalNotesResponse>({
    queryKey: ["clinical-notes", { patientId }],
    queryFn: () =>
      apiClient.get<ClinicalNotesResponse>(
        `/internal/v1/clinical-notes?patient_id=${encodeURIComponent(patientId)}`
      ),
    enabled: !!patientId,
  });
}

export function useClinicalNote(id: string) {
  return useQuery<ClinicalNoteResponse>({
    queryKey: ["clinical-notes", id],
    queryFn: () =>
      apiClient.get<ClinicalNoteResponse>(`/internal/v1/clinical-notes/${id}`),
    enabled: !!id,
  });
}

export function useCreateNote() {
  const queryClient = useQueryClient();

  return useMutation<ClinicalNoteResponse, unknown, CreateNotePayload>({
    mutationFn: (payload: CreateNotePayload) =>
      apiClient.post<ClinicalNoteResponse>("/internal/v1/clinical-notes", payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["clinical-notes"] });
    },
  });
}

export function useSignNote() {
  const queryClient = useQueryClient();

  return useMutation<ClinicalNoteResponse, unknown, { id: string }>({
    mutationFn: ({ id }: { id: string }) =>
      apiClient.post<ClinicalNoteResponse>(`/internal/v1/clinical-notes/${id}/sign`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["clinical-notes"] });
    },
  });
}
