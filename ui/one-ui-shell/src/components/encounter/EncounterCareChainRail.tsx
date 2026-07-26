"use client";

import Link from "next/link";
import { Loader2, Receipt, ScanLine, Wallet } from "lucide-react";
import { useLabOrders } from "@/hooks/queries/useLabOrders";
import { useCostaIntelCostEvents } from "@/hooks/queries/useCostaIntel";
import { useFinanceBillingInvoicesForEncounter } from "@/hooks/queries/useFinanceBillingWorkspace";
import { usePayerOpsPaymentIntentsBySource } from "@/hooks/queries/useFinancePayerOps";
import { useFinanceSettlementsByIntentIds } from "@/hooks/queries/useFinanceSettlements";

interface EncounterCareChainRailProps {
  encounterId: string;
  patientId: string;
  patientCpid?: string;
}

function extractRows(payload: unknown): Record<string, unknown>[] {
  if (payload == null) return [];
  if (Array.isArray(payload)) return payload as Record<string, unknown>[];
  if (typeof payload === "object") {
    const obj = payload as Record<string, unknown>;
    if (Array.isArray(obj.data)) return obj.data as Record<string, unknown>[];
    if (Array.isArray(obj.items)) return obj.items as Record<string, unknown>[];
  }
  return [];
}

export function EncounterCareChainRail({ encounterId, patientId, patientCpid }: EncounterCareChainRailProps) {
  const ordersQ = useLabOrders(patientId);
  const costEventsQ = useCostaIntelCostEvents(encounterId, patientCpid);
  const invoicesQ = useFinanceBillingInvoicesForEncounter(encounterId, Boolean(encounterId));

  const encounterOrders = (ordersQ.data?.data ?? []).filter((order) => {
    const attrs = order.attributes as Record<string, unknown>;
    const linked = attrs.encounterId ?? attrs.encounter_id;
    return String(linked ?? "") === encounterId;
  });

  const invoiceRows = extractRows(invoicesQ.data);
  const billIds = invoiceRows
    .map((row) => String(row.bill_id ?? row.billId ?? ""))
    .filter((id) => id.length > 0);

  const intentsQ = usePayerOpsPaymentIntentsBySource("COSTA_BILL", billIds, billIds.length > 0);
  const intentRows = extractRows(intentsQ.data);
  const intentIds = intentRows
    .map((row) => String(row.intentId ?? row.intent_id ?? row.id ?? ""))
    .filter((id) => id.length > 0);

  const settlementsQ = useFinanceSettlementsByIntentIds(intentIds, intentIds.length > 0);
  const settlementCount = extractRows(settlementsQ.data).length;

  const loading =
    ordersQ.isLoading || costEventsQ.isLoading || invoicesQ.isLoading || intentsQ.isLoading || settlementsQ.isLoading;

  const costEventCount = (costEventsQ.data ?? []).length;

  // Every number on this rail is a count, and a count of zero is a claim: nothing ordered on this
  // encounter, nothing billed, nothing paid. The chain is also cascaded — a failed invoice read
  // yields no bill ids, which disables the intents query, which disables settlements — so an
  // outage one link up renders three confident zeros downstream.
  const ordersUnavailable = ordersQ.isError;
  const billingUnavailable = costEventsQ.isError || invoicesQ.isError;
  const paymentsUnavailable = invoicesQ.isError || intentsQ.isError || settlementsQ.isError;

  return (
    <section
      className="rounded-xl border border-border bg-card px-4 py-4 shadow-sm"
      data-testid="encounter-care-chain-rail"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-foreground">Encounter care & billing chain</h3>
        <Link
          href={`/finance/costa/encounter/${encounterId}?patientId=${encodeURIComponent(patientId)}${patientCpid ? `&patientCpid=${encodeURIComponent(patientCpid)}` : ""}`}
          className="text-xs font-medium text-primary hover:underline"
        >
          Full COSTA timeline →
        </Link>
      </div>

      {loading ? (
        <div className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading OROS, COSTA, and MusheX signals…
        </div>
      ) : (
        <div className="mt-3 grid gap-3 sm:grid-cols-3">
          <div className="rounded-lg border border-cyan-100 bg-cyan-50/60 p-3">
            <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-cyan-800">
              <ScanLine className="h-3.5 w-3.5" />
              OROS orders
            </div>
            <p className="mt-1 text-2xl font-semibold text-foreground">
              {ordersUnavailable ? "—" : encounterOrders.length}
            </p>
            <p className="text-xs text-muted-foreground">
              {ordersUnavailable
                ? "OROS unreachable — order count unknown, not zero."
                : "Lab & imaging orders linked to this encounter."}
            </p>
          </div>

          <div className="rounded-lg border border-emerald-100 bg-success-soft/60 p-3">
            <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-primary-hover">
              <Receipt className="h-3.5 w-3.5" />
              Costa billing
            </div>
            <p className="mt-1 text-2xl font-semibold text-foreground">
              {billingUnavailable ? "—" : costEventCount}
            </p>
            <p className="text-xs text-muted-foreground">
              {billingUnavailable ? (
                "COSTA unreachable — cost events and invoice rows unknown, not zero."
              ) : (
                <>
                  Cost events · {invoiceRows.length} invoice row{invoiceRows.length === 1 ? "" : "s"}.
                </>
              )}
            </p>
          </div>

          <div className="rounded-lg border border-violet-100 bg-violet-50/60 p-3">
            <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-violet-800">
              <Wallet className="h-3.5 w-3.5" />
              MusheX payments
            </div>
            <p className="mt-1 text-2xl font-semibold text-foreground">
              {paymentsUnavailable ? "—" : intentRows.length}
            </p>
            <p className="text-xs text-muted-foreground">
              {paymentsUnavailable ? (
                "MusheX chain unreachable — payment intents and settlements unknown, not zero."
              ) : (
                <>
                  Payment intents · {settlementCount} linked settlement row{settlementCount === 1 ? "" : "s"}.
                </>
              )}
            </p>
          </div>
        </div>
      )}
    </section>
  );
}
