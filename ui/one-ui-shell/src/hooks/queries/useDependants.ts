/**
 * Experience UI — Add a child / dependant (CJ14).
 *
 * The guardian registers a minor as a person in their own right (own Health ID via
 * VITO) and a time-bound GUARDIAN delegation is recorded on mvumo. Backed by the
 * composed BFF endpoint POST /internal/v1/citizen/dependants.
 */

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface AddDependantRequest {
  givenName: string;
  familyName: string;
  dateOfBirth: string; // ISO YYYY-MM-DD
  sex?: string;
  scope?: string[];
}

export interface AddDependantResult {
  status: "CREATED";
  dependantSubjectRef?: string;
  dependant?: { givenName?: string; familyName?: string; dateOfBirth?: string };
  guardianship?: { type?: string; expiresAt?: string };
}

export function useAddDependant() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: AddDependantRequest) =>
      apiClient.post<ApiResponse<AddDependantResult>>("/internal/v1/citizen/dependants", body),
    onSuccess: () => {
      // The new guardian relationship shows up in the wallet's dependants card.
      void queryClient.invalidateQueries({ queryKey: ["wallet"] });
    },
  });
}

/** Extract a human message from the api-client's thrown {status, error:{message}} shape. */
export function dependantErrorMessage(err: unknown): string {
  if (err && typeof err === "object") {
    const e = err as { error?: { message?: string }; message?: string };
    return e.error?.message || e.message || "The dependant could not be added. Please try again.";
  }
  return "The dependant could not be added. Please try again.";
}
