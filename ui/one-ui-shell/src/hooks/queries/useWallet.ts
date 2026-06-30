"use client";

/**
 * Unified Person Health Wallet hooks — Health OS §1 (Identity), §5 (Record), §7 (Consent).
 *
 * The wallet is the person anchor experience: one place to see identity & trust, profile
 * completion, health record summary, care timeline, consents, dependants, payments, comms
 * preferences, and Nompilo next-steps. These hooks read the Experience BFF aggregation
 * (CitizenWalletController) which composes the existing sovereign owners — the wallet owns no
 * data of its own. Demographic corrections route to VITO's correction workflow (no inline
 * mutation of high-trust fields).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type AnyRecord = Record<string, unknown>;

export interface WalletNextAction {
  code: string;
  title: string;
  href: string;
  why: string;
}

export interface WalletProfileCompletion {
  present: number;
  total: number;
  percent: number;
  missing: string[];
}

export interface WalletOverview {
  identity?: AnyRecord;
  profileCompletion?: WalletProfileCompletion;
  consent?: AnyRecord;
  dependants?: AnyRecord;
  records?: AnyRecord;
  careTimeline?: AnyRecord;
  payments?: AnyRecord;
  commsPreferences?: AnyRecord;
  guidance?: AnyRecord;
  nextActions?: WalletNextAction[];
  viewingAs?: "SELF" | "DELEGATE";
  subjectId?: string;
}

export function useWalletOverview() {
  return useQuery({
    queryKey: ["wallet", "overview"],
    queryFn: () => apiClient.get<ApiResponse<WalletOverview>>("/internal/v1/citizen/wallet/overview"),
  });
}

export interface CorrectionRequest {
  field: string;
  currentValue?: string;
  requestedValue: string;
  reason?: string;
}

export function useSubmitCorrection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CorrectionRequest) =>
      apiClient.post<ApiResponse<AnyRecord>>("/internal/v1/citizen/wallet/profile/correction", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["wallet", "overview"] });
    },
  });
}
