/**
 * Experience UI — Encounter Billing Query Hook
 *
 * Read-only clinical billing visibility for an encounter, backed by the BFF
 * composition endpoint `GET /internal/v1/encounters/{id}/billing` (COSTA bills
 * + payments with honest derived states). Mutations stay on /finance surfaces.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface EncounterBillPayment {
  id: string | null;
  payment_type: string | null;
  amount: number;
  paid_amount: number;
  status: string | null;
  mushex_intent_id: string | null;
  paid_at: string | null;
}

export interface EncounterBillResource {
  bill_id: string | null;
  status: string | null;
  bill_type: string | null;
  currency: string | null;
  total_charge: number;
  total_payable: number;
  insurer_payable: number;
  patient_payable: number;
  subsidy_payable: number;
  write_off: number;
  coverage_status: string | null;
  coverage_plan_code: string | null;
  created_at: string | null;
  finalized_at: string | null;
  payments_state?: "UNAVAILABLE";
  payments: EncounterBillPayment[];
  /** Honest derived state, e.g. BILLING_PENDING, SHORTFALL_DUE, PAID, COVERED. */
  state: string;
}

export interface EncounterBillingData {
  encounter_id: string;
  billing_state: string;
  bills: EncounterBillResource[];
}

interface EncounterBillingResponse {
  data: EncounterBillingData;
  meta?: Record<string, unknown>;
}

export function useEncounterBilling(encounterId: string) {
  return useQuery<EncounterBillingResponse>({
    queryKey: ["encounter-billing", encounterId],
    queryFn: () =>
      apiClient.get<EncounterBillingResponse>(
        `/internal/v1/encounters/${encodeURIComponent(encounterId)}/billing`
      ),
    enabled: !!encounterId,
    retry: 1,
  });
}
