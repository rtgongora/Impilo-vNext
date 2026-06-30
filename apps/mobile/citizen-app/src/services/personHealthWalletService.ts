/**
 * Unified Person Health Wallet — mobile service.
 *
 * Reads the Experience BFF wallet overview (CitizenWalletController), which composes the existing
 * sovereign owners (VITO, identity-assurance, PCT/BUTANO, tshepo-consent, mvumo, Costa,
 * notification-service, guidance/Nompilo). The mobile app owns no wallet data — this mirrors the
 * web `useWalletOverview` hook so web↔mobile stay in parity.
 *
 * NOTE: distinct from the financial `walletService.ts` (Mushe Wallet balance/transactions). This is
 * the PERSON HEALTH wallet — the identity/records/care/consent anchor experience.
 */
import { apiClient } from "@impilo/mobile-api-client";

export interface PersonWalletNextAction {
  code: string;
  title: string;
  href: string;
  why: string;
}

export interface PersonWalletProfileCompletion {
  present: number;
  total: number;
  percent: number;
  missing: string[];
}

export interface PersonWalletOverview {
  identity?: Record<string, unknown>;
  profileCompletion?: PersonWalletProfileCompletion;
  consent?: Record<string, unknown>;
  dependants?: Record<string, unknown>;
  records?: Record<string, unknown>;
  careTimeline?: Record<string, unknown>;
  payments?: Record<string, unknown>;
  commsPreferences?: Record<string, unknown>;
  guidance?: Record<string, unknown>;
  nextActions?: PersonWalletNextAction[];
  viewingAs?: "SELF" | "DELEGATE";
}

export async function fetchPersonWalletOverview(): Promise<PersonWalletOverview> {
  const response = await apiClient.get<{ data?: PersonWalletOverview }>(
    "/internal/v1/citizen/wallet/overview",
  );
  return response.data?.data ?? {};
}
