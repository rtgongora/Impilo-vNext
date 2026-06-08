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

interface UploadDocumentFilePayload {
  patientId: string;
  file: File;
  documentType: string;
  title: string;
  description?: string | null;
  encounter_id?: string | null;
  uploaded_by?: string;
}

/** Multipart upload → document-store object → PCT clinical document index. */
export function useRequestDocumentOcr(objectId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiClient.post<ApiResponse<unknown>>(
        `/internal/v1/clinical-documents/objects/${encodeURIComponent(String(objectId))}/ocr`,
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["clinical-documents"] });
      if (objectId) {
        void queryClient.invalidateQueries({ queryKey: ["clinical-documents", "ocr", objectId] });
      }
    },
  });
}

export function useDocumentOcrStatus(objectId: string | undefined) {
  return useQuery({
    queryKey: ["clinical-documents", "ocr", objectId],
    queryFn: () =>
      apiClient.get<ApiResponse<unknown>>(
        `/internal/v1/clinical-documents/objects/${encodeURIComponent(String(objectId))}/ocr`,
      ),
    enabled: !!objectId,
    refetchInterval: (query) => {
      const status = String((query.state.data as { data?: { status?: string } } | undefined)?.data?.status ?? "").toUpperCase();
      return status === "PENDING" || status === "RUNNING" ? 3000 : false;
    },
  });
}

export function useUploadDocumentFile() {
  const queryClient = useQueryClient();

  return useMutation<ClinicalDocumentResponse, unknown, UploadDocumentFilePayload>({
    mutationFn: async (payload) => {
      const form = new FormData();
      form.append("file", payload.file);
      form.append("patient_id", payload.patientId);
      form.append("document_type", payload.documentType);
      form.append("title", payload.title);
      if (payload.description) form.append("description", payload.description);
      if (payload.encounter_id) form.append("encounter_id", payload.encounter_id);
      if (payload.uploaded_by) form.append("uploaded_by", payload.uploaded_by);
      return apiClient.postForm<ClinicalDocumentResponse>("/internal/v1/clinical-documents/upload", form);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["clinical-documents"] });
    },
  });
}
