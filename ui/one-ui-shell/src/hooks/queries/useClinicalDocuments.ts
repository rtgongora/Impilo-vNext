/**
 * Experience UI — Clinical Documents Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface ClinicalDocumentResource {
  id: string;
  type: "clinical_document";
  attributes: {
    patientId: string;
    documentType: string;
    title: string;
    description: string | null;
    mimeType: string;
    fileSize: number;
    uploadedBy: string;
    status: string;
    createdAt: string;
  };
}

interface UploadDocumentPayload {
  patientId: string;
  documentType: string;
  title: string;
  description?: string | null;
  [key: string]: unknown;
}

type ClinicalDocumentsResponse = ApiResponse<ClinicalDocumentResource[]>;
type ClinicalDocumentResponse = ApiResponse<ClinicalDocumentResource>;

export function useClinicalDocuments(patientId: string) {
  return useQuery<ClinicalDocumentsResponse>({
    queryKey: ["clinical-documents", { patientId }],
    queryFn: () =>
      apiClient.get<ClinicalDocumentsResponse>(
        `/internal/v1/clinical-documents?patient_id=${encodeURIComponent(patientId)}`
      ),
    enabled: !!patientId,
  });
}

export function useUploadDocument() {
  const queryClient = useQueryClient();

  return useMutation<ClinicalDocumentResponse, unknown, UploadDocumentPayload>({
    mutationFn: (payload: UploadDocumentPayload) =>
      apiClient.post<ClinicalDocumentResponse>("/internal/v1/clinical-documents", payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["clinical-documents"] });
    },
  });
}
