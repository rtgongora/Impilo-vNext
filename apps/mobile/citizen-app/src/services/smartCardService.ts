/**
 * Unified SMART card — citizen service module.
 *
 * Routes are the BFF card plane (experience-bff `CitizenCardController` → `MusheWalletServiceClient`
 * → mushe-wallet-service), under the authenticated `/internal/v1/wallet/**` space. The wallet owner
 * is implied by the `x-actor-id` trust header the API client injects — no id query params.
 *
 *   GET  /internal/v1/wallet/cards                          → my card(s)
 *   PUT  /internal/v1/wallet/cards/{cardId}/phr-carry       → toggle PHR-carry function
 *   POST /internal/v1/wallet/cards/pay/biometric            → biometric scan-to-pay
 *   POST /internal/v1/wallet/bill-contributions             → create a "help pay my bill" link
 *   GET  /internal/v1/wallet/bill-contributions/{token}     → view a link
 *   POST /internal/v1/wallet/bill-contributions/{token}/contribute → chip in
 */
import { apiClient } from "@impilo/mobile-api-client";

const V1 = "/internal/v1/wallet";

type Envelope<T> = { data: T };

export interface SmartCard {
  cardId: string;
  walletId: string;
  maskedCardNumber: string;
  cardType: string;
  status: string;
  vitoCardNumber: string | null;
  phrEnabled: boolean;
}

export interface BillContributionRequest {
  id: string;
  shareToken: string;
  title: string;
  billRef?: string;
  targetAmount?: string;
  raisedAmount?: string;
  currency: string;
  status: string;
}

/** The current citizen's card(s). */
export async function fetchMyCards(): Promise<SmartCard[]> {
  const response = await apiClient.get<Envelope<unknown>>(`${V1}/cards`);
  return adaptCards(response.data?.data);
}

/** Toggle the card's PHR-carry function (carry my health record on the card). Fail-closed server-side. */
export async function setPhrCarry(cardId: string, enabled: boolean): Promise<SmartCard | null> {
  const response = await apiClient.put<Envelope<unknown>>(`${V1}/cards/${cardId}/phr-carry`, {
    enabled,
  });
  return adaptCard(response.data?.data);
}

export interface BiometricPayInput {
  vitoCardNumber: string;
  payload: string; // canonical signed JSON
  signature: string; // Base64 DER ECDSA-SHA256
}

/** Biometric scan-to-pay — the signed payload must assert authMethod=BIOMETRIC (enforced server-side). */
export async function payBiometric(input: BiometricPayInput): Promise<unknown> {
  const response = await apiClient.post<Envelope<unknown>>(`${V1}/cards/pay/biometric`, input);
  return response.data?.data;
}

/** Create a shareable "help pay my bill" contribution request for my wallet. */
export async function createBillContribution(input: {
  title: string;
  billRef?: string;
  targetAmount?: string;
  currency?: string;
}): Promise<BillContributionRequest | null> {
  const response = await apiClient.post<Envelope<unknown>>(`${V1}/bill-contributions`, input);
  return adaptContribution(response.data?.data);
}

/** View a contribution request by its share token. */
export async function viewBillContribution(
  shareToken: string,
): Promise<{ request: BillContributionRequest | null; contributions: unknown[] }> {
  const response = await apiClient.get<Envelope<{ request?: unknown; contributions?: unknown[] }>>(
    `${V1}/bill-contributions/${shareToken}`,
  );
  const data = response.data?.data;
  return {
    request: adaptContribution(data?.request),
    contributions: Array.isArray(data?.contributions) ? data!.contributions! : [],
  };
}

/** Contribute toward a shared bill. */
export async function contributeToBill(
  shareToken: string,
  input: { amount: string; contributorName?: string; message?: string },
): Promise<unknown> {
  const response = await apiClient.post<Envelope<unknown>>(
    `${V1}/bill-contributions/${shareToken}/contribute`,
    input,
  );
  return response.data?.data;
}

// ── adapters (tolerant of pass-through / attributes shapes) ───────────────

function adaptCards(raw: unknown): SmartCard[] {
  const root = asRecord(raw);
  const list = Array.isArray(raw)
    ? raw
    : Array.isArray(root?.cards)
      ? (root!.cards as unknown[])
      : [];
  return list.map(adaptCard).filter((c): c is SmartCard => c !== null);
}

function adaptCard(raw: unknown): SmartCard | null {
  const node = asRecord(raw);
  if (!node) return null;
  const attrs = asRecord(node.attributes) ?? node;
  const cardId = str(node.cardId, attrs.cardId, node.id, attrs.id);
  if (!cardId) return null;
  return {
    cardId,
    walletId: str(attrs.walletId) ?? "",
    maskedCardNumber: str(attrs.maskedCardNumber) ?? "•••• •••• •••• ••••",
    cardType: str(attrs.cardType) ?? "DUAL",
    status: str(attrs.status) ?? "ISSUED",
    vitoCardNumber: str(attrs.vitoCardNumber) ?? null,
    phrEnabled: attrs.phrEnabled === true,
  };
}

function adaptContribution(raw: unknown): BillContributionRequest | null {
  const node = asRecord(raw);
  if (!node) return null;
  const attrs = asRecord(node.attributes) ?? node;
  const id = str(node.id, attrs.id);
  const shareToken = str(attrs.shareToken);
  if (!shareToken) return null;
  return {
    id: id ?? "",
    shareToken,
    title: str(attrs.title) ?? "Bill contribution",
    billRef: str(attrs.billRef) ?? undefined,
    targetAmount: str(attrs.targetAmount) ?? undefined,
    raisedAmount: str(attrs.raisedAmount) ?? undefined,
    currency: str(attrs.currency) ?? "USD",
    status: str(attrs.status) ?? "OPEN",
  };
}

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : undefined;
}

function str(...candidates: unknown[]): string | undefined {
  for (const c of candidates) {
    if (typeof c === "string" && c.length > 0) return c;
    if (typeof c === "number") return String(c);
  }
  return undefined;
}
