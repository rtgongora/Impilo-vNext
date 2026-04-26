import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface PersonalCredentialResource {
  credentialId: string;
  subjectName: string;
  credentialType: string;
  title: string;
  issuedBy: string;
  status: string;
  validFrom: string;
  validTo: string | null;
  verificationUrl: string | null;
  createdAt: string;
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

function unwrapItems(payload: unknown): unknown[] {
  const direct = asRecord(payload);
  if (!direct) return Array.isArray(payload) ? payload : [];
  const nested = asRecord(direct.data);
  if (nested?.items && Array.isArray(nested.items)) return nested.items;
  if (Array.isArray(direct.items)) return direct.items;
  return [];
}

function normalizeCredential(item: unknown): PersonalCredentialResource {
  const record = asRecord(item);
  return {
    credentialId: readString(record, ["credentialId", "id"]),
    subjectName: readString(record, ["subjectName"]),
    credentialType: readString(record, ["credentialType"]) || "CREDENTIAL",
    title: readString(record, ["title"]) || "Credential",
    issuedBy: readString(record, ["issuedBy"]),
    status: readString(record, ["status"]) || "ACTIVE",
    validFrom: readString(record, ["validFrom", "issuedAt"]),
    validTo: readString(record, ["validTo"]) || null,
    verificationUrl: readString(record, ["verificationUrl"]) || null,
    createdAt: readString(record, ["createdAt", "issuedAt"]),
  };
}

export function usePersonalCredentials() {
  return useQuery<ApiResponse<PersonalCredentialResource[]>>({
    queryKey: ["personal-credentials"],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponse<unknown>>("/internal/v1/credentials?scope=MY&page=0&size=20");
      return {
        ...response,
        data: unwrapItems(response.data).map(normalizeCredential),
      };
    },
  });
}
