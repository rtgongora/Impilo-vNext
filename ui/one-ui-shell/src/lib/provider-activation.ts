/**
 * Maps VARAPI / BFF provider listings into the activation UI contract.
 */

export interface ProviderActivationRecord {
  providerId: string;
  displayName: string;
  cadre: string;
  registrationNumber: string;
  status: string;
  licensureExpiry?: string;
}

type VarapiProviderPayload = {
  providerId?: string;
  providerPublicId?: string;
  givenName?: string;
  familyName?: string;
  displayName?: string;
  cadre?: string;
  profession?: string;
  registrationNumber?: string;
  practiceNumber?: string;
  status?: string;
  licensureExpiry?: string;
};

function mapVarapiProvider(row: VarapiProviderPayload): ProviderActivationRecord | null {
  const providerId = (row.providerId ?? row.providerPublicId ?? "").trim();
  if (!providerId) return null;

  const given = row.givenName?.trim() ?? "";
  const family = row.familyName?.trim() ?? "";
  const displayName =
    row.displayName?.trim() ||
    `${given} ${family}`.trim() ||
    providerId;

  return {
    providerId,
    displayName,
    cadre: row.cadre?.trim() || row.profession?.trim() || "Provider",
    registrationNumber: row.registrationNumber?.trim() || row.practiceNumber?.trim() || providerId,
    status: row.status?.trim() || "UNKNOWN",
    licensureExpiry: row.licensureExpiry,
  };
}

/** Accepts BFF `{ data: object | object[] }` or a bare provider object/array. */
export function normalizeProviderListingResponse(payload: unknown): ProviderActivationRecord[] {
  if (!payload || typeof payload !== "object") return [];

  const envelope = payload as { data?: unknown };
  const raw = envelope.data !== undefined ? envelope.data : payload;
  const rows = Array.isArray(raw) ? raw : raw ? [raw] : [];

  return rows
    .map((row) => mapVarapiProvider(row as VarapiProviderPayload))
    .filter((row): row is ProviderActivationRecord => row !== null);
}

export function recordFromLinkedProviderId(
  providerId: string,
  displayName?: string,
): ProviderActivationRecord {
  return {
    providerId,
    displayName: displayName?.trim() || providerId,
    cadre: "Provider",
    registrationNumber: providerId,
    status: "ACTIVE",
  };
}
