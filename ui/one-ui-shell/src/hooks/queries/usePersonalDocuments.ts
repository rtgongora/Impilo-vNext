import { useMutation, useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface PersonalDocumentResource {
  id: string;
  title: string;
  originalFilename: string;
  documentTypeCode: string;
  mimeType: string;
  lifecycleState: string;
  createdAt: string;
  fileSize: number | null;
}

export interface PersonalDocumentDownloadResource {
  url: string | null;
  expiresAt: string | null;
}

type UnknownRecord = Record<string, unknown>;

function asRecord(value: unknown): UnknownRecord | null {
  return typeof value === "object" && value !== null ? (value as UnknownRecord) : null;
}

function readString(record: UnknownRecord | null, keys: string[]): string {
  if (!record) return "";
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.trim()) return value;
  }
  return "";
}

function readNumber(record: UnknownRecord | null, keys: string[]): number | null {
  if (!record) return null;
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "number" && Number.isFinite(value)) return value;
    if (typeof value === "string" && value.trim()) {
      const parsed = Number(value);
      if (Number.isFinite(parsed)) return parsed;
    }
  }
  return null;
}

function unwrapItems(payload: unknown): unknown[] {
  if (Array.isArray(payload)) return payload;

  const record = asRecord(payload);
  if (!record) return [];

  const nestedData = record.data;
  if (Array.isArray(nestedData)) return nestedData;

  const nestedRecord = asRecord(nestedData);
  if (nestedRecord) {
    if (Array.isArray(nestedRecord.items)) return nestedRecord.items;
    if (Array.isArray(nestedRecord.content)) return nestedRecord.content;
  }

  if (Array.isArray(record.items)) return record.items;
  if (Array.isArray(record.content)) return record.content;

  return [];
}

function unwrapPayload(payload: unknown): UnknownRecord | null {
  const direct = asRecord(payload);
  if (!direct) return null;
  return asRecord(direct.data) ?? direct;
}

function normalizeDocument(item: unknown): PersonalDocumentResource {
  const record = asRecord(item);
  const id = readString(record, ["documentId", "id", "object_id", "objectId"]);
  const title = readString(record, ["title", "originalFilename", "filename", "name"]) || "Untitled document";

  return {
    id,
    title,
    originalFilename: readString(record, ["originalFilename", "filename", "name", "title"]) || title,
    documentTypeCode: readString(record, ["documentTypeCode", "document_type_code", "documentType", "category"]) || "DOCUMENT",
    mimeType: readString(record, ["mimeType", "mime_type", "contentType", "content_type"]) || "application/octet-stream",
    lifecycleState: readString(record, ["lifecycleState", "lifecycle_state", "status"]) || "AVAILABLE",
    createdAt: readString(record, ["createdAt", "created_at", "uploadedAt", "uploaded_at"]),
    fileSize: readNumber(record, ["fileSize", "file_size", "size", "size_bytes"]),
  };
}

function normalizeDownload(payload: unknown): PersonalDocumentDownloadResource {
  const record = unwrapPayload(payload);
  return {
    url: readString(record, ["url", "signedUrl", "downloadUrl", "href"]) || null,
    expiresAt: readString(record, ["expiresAt", "expires_at", "expiry", "validUntil"]) || null,
  };
}

export function usePersonalDocuments() {
  return useQuery<ApiResponse<PersonalDocumentResource[]>>({
    queryKey: ["personal-documents"],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponse<unknown>>("/internal/v1/clinical-tools/documents");
      return {
        ...response,
        data: unwrapItems(response.data).map(normalizeDocument),
      };
    },
  });
}

export function usePersonalDocumentDownloadUrl() {
  return useMutation<ApiResponse<PersonalDocumentDownloadResource>, unknown, string>({
    mutationFn: async (documentId) => {
      const response = await apiClient.get<ApiResponse<unknown>>(
        `/internal/v1/clinical-tools/documents/${encodeURIComponent(documentId)}/download-url`,
      );
      return {
        ...response,
        data: normalizeDownload(response.data),
      };
    },
  });
}
