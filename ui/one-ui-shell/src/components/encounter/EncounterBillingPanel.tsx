"use client";

/**
 * EncounterBillingPanel — read-only clinical billing visibility.
 *
 * Shows COSTA bill state, coverage split, patient shortfall, and payment
 * status for an encounter using honest derived states from the BFF
 * (`GET /internal/v1/encounters/{id}/billing`). No billing mutations happen
 * here — finance actions stay on /finance surfaces (linked below).
 */

import Link from "next/link";
import { AlertCircle, Loader2, Receipt } from "lucide-react";
import {
  useEncounterBilling,
  type EncounterBillResource,
} from "@/hooks/queries/useEncounterBilling";

interface Props {
  patientId: string;
  encounterId: string;
}

const STATE_LABELS: Record<string, string> = {
  NOT_BILLED: "Not billed",
  BILLING_PENDING: "Billing pending",
  BILL_GENERATED: "Bill generated",
  COVERED: "Fully covered",
  SHORTFALL_DUE: "Shortfall due",
  PAYMENT_PENDING: "Payment pending",
  PAID: "Paid",
  VOIDED: "Voided",
  UNAVAILABLE: "Unavailable",
};

function stateChrome(state: string): string {
  switch (state) {
    case "PAID":
    case "COVERED":
      return "bg-emerald-100 text-primary-hover border-success/25";
    case "SHORTFALL_DUE":
      return "bg-red-100 text-red-800 border-danger/28";
    case "PAYMENT_PENDING":
    case "BILLING_PENDING":
    case "BILL_GENERATED":
      return "bg-amber-100 text-warning-foreground border-warning/35";
    case "VOIDED":
      return "bg-neutral-100 text-muted-foreground border-border";
    default:
      return "bg-neutral-100 text-foreground border-border";
  }
}

function money(amount: number, currency: string | null): string {
  return `${currency ?? ""} ${amount.toFixed(2)}`.trim();
}

function BillRow({ bill, patientId, encounterId }: { bill: EncounterBillResource; patientId: string; encounterId: string }) {
  const label = STATE_LABELS[bill.state] ?? bill.state;
  const payments = Array.isArray(bill.payments) ? bill.payments : [];
  return (
    <div className="rounded-lg border border-border bg-background p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="font-mono text-[11px] text-muted-foreground">
            {bill.bill_id ?? "—"}
          </span>
          <span
            className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-medium ${stateChrome(bill.state)}`}
          >
            {label}
          </span>
          {bill.coverage_status ? (
            <span className="text-[10px] text-muted-foreground">
              Coverage: {bill.coverage_status}
              {bill.coverage_plan_code ? ` (${bill.coverage_plan_code})` : ""}
            </span>
          ) : null}
        </div>
        {bill.bill_id ? (
          <Link
            href={`/finance/billing/${encodeURIComponent(bill.bill_id)}?patientId=${encodeURIComponent(patientId)}&encounterId=${encodeURIComponent(encounterId)}`}
            className="text-xs font-medium text-primary hover:underline"
          >
            Open bill →
          </Link>
        ) : null}
      </div>
      <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-xs sm:grid-cols-4">
        <div>
          <dt className="text-muted-foreground">Total payable</dt>
          <dd className="font-medium text-foreground">{money(bill.total_payable, bill.currency)}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Coverage pays</dt>
          <dd className="font-medium text-foreground">{money(bill.insurer_payable, bill.currency)}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Patient shortfall</dt>
          <dd className={`font-medium ${bill.patient_payable > 0 ? "text-danger" : "text-foreground"}`}>
            {money(bill.patient_payable, bill.currency)}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Payments</dt>
          <dd className="font-medium text-foreground">
            {bill.payments_state === "UNAVAILABLE"
              ? "Unavailable"
              : payments.length === 0
                ? "None recorded"
                : payments
                    .map((p) => `${p.payment_type ?? "?"} ${p.status ?? "?"}`)
                    .join(", ")}
          </dd>
        </div>
      </dl>
    </div>
  );
}

export function EncounterBillingPanel({ patientId, encounterId }: Props) {
  const billingQ = useEncounterBilling(encounterId);
  const data = billingQ.data?.data;
  const bills = Array.isArray(data?.bills) ? data.bills : [];

  return (
    <section className="rounded-xl border border-border bg-card p-4">
      <div className="mb-2 flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Receipt className="h-4 w-4 text-primary" />
          <h3 className="text-sm font-medium text-foreground">Billing status (COSTA)</h3>
        </div>
        {data ? (
          <span
            className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-medium ${stateChrome(data.billing_state)}`}
          >
            {STATE_LABELS[data.billing_state] ?? data.billing_state}
          </span>
        ) : null}
      </div>
      <p className="mb-3 text-xs text-muted-foreground">
        Read-only billing view for this encounter: bill state, coverage split, patient shortfall,
        and payment status. Billing actions stay in the finance workspace.
      </p>

      {billingQ.isLoading ? (
        <p className="flex items-center gap-2 text-xs text-muted-foreground">
          <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading billing status…
        </p>
      ) : billingQ.isError ? (
        <p className="flex items-center gap-2 text-xs text-danger">
          <AlertCircle className="h-3.5 w-3.5" />
          Billing status is currently unavailable for this encounter.
        </p>
      ) : bills.length === 0 ? (
        <p className="text-xs text-muted-foreground">
          No bill exists for this encounter yet. A draft bill is created when the encounter is
          closed or discharged.
        </p>
      ) : (
        <div className="space-y-2">
          {bills.map((bill, idx) => (
            <BillRow
              key={bill.bill_id ?? idx}
              bill={bill}
              patientId={patientId}
              encounterId={encounterId}
            />
          ))}
        </div>
      )}
    </section>
  );
}
