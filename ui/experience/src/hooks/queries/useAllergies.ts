/**
 * Experience UI — Allergies Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface AllergyResource {
  id: string;
  type: "allergy";
  attributes: {
    patientId: string;
    allergen: string;
    allergenType: string;
    reaction: string | null;
    severity: string;
    status: string;
    onsetDate: string | null;
    recordedBy: string;
    createdAt: string;
  };
}

interface CreateAllergyPayload {
  patientId: string;
  allergen: string;
  allergenType: string;
  reaction?: string | null;
  severity: string;
  onsetDate?: string | null;
  [key: string]: unknown;
}

type AllergiesResponse = ApiResponse<AllergyResource[]>;
type AllergyResponse = ApiResponse<AllergyResource>;

export function useAllergies(patientId: string) {
  return useQuery<AllergiesResponse>({
    queryKey: ["allergies", { patientId }],
    queryFn: () =>
      apiClient.get<AllergiesResponse>(
        `/internal/v1/allergies?patient_id=${encodeURIComponent(patientId)}`
      ),
    enabled: !!patientId,
  });
}

export function useCreateAllergy() {
  const queryClient = useQueryClient();

  return useMutation<AllergyResponse, unknown, CreateAllergyPayload>({
    mutationFn: (payload: CreateAllergyPayload) =>
      apiClient.post<AllergyResponse>("/internal/v1/allergies", payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["allergies"] });
    },
  });
}

export function useDeleteAllergy() {
  const queryClient = useQueryClient();

  return useMutation<AllergyResponse, unknown, { id: string }>({
    mutationFn: ({ id }: { id: string }) =>
      apiClient.delete<AllergyResponse>(`/internal/v1/allergies/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["allergies"] });
    },
  });
}
