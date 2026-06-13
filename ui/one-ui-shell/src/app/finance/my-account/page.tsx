"use client";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { LANDELA_METADATA_COLUMNS } from "@/lib/json-api/generic-table-columns";
/**
 * Patient financial dashboard — balances, transactions, payment plans, documents.
 * Route: /finance/my-account
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import {
  ArrowLeft,
  CreditCard,
  FileText,
  Loader2,
  PiggyBank,
  Receipt,
  Wallet,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useCreatePaymentPlan,
  useFinancialDocument,
  useFinancialDocuments,
  useGenerateDocument,
  usePatientAccount,
  usePatientOutstanding,
  usePatientTransactions,
  usePayInstallment,
  usePaymentPlans,
  useRecordPayment,
} from "@/hooks/queries/usePatientFinancials";

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === "object" ? (v as Record<string, unknown>) : {};
}

function readNum(r: Record<string, unknown>, ...keys: string[]) {
  for (const k of keys) {
    const x = r[k];
    if (typeof x === "number") return x;
    if (typeof x === "string" && x.trim()) {
      const n = Number(x);
      if (!Number.isNaN(n)) return n;
    }
  }
  return 0;
}

function readStr(r: Record<string, unknown>, ...keys: string[]) {
  for (const k of keys) {
    const x = r[k];
    if (typeof x === "string" && x.length > 0) return x;
  }
  return "";
}

function unwrapItems(v: unknown): unknown[] {
  if (!v) return [];
  if (Array.isArray(v)) return v;
  const r = v as { items?: unknown };
  if (Array.isArray(r.items)) return r.items;
  return [];
}

function formatMoney(n: number, currency = "USD") {
  try {
    return new Intl.NumberFormat(undefined, { style: "currency", currency }).format(n);
  } catch {
    return `${currency} ${n.toFixed(2)}`;
  }
}

export default function MyHealthcareAccountPage() {
  const cpid = useAuthStore((s) => s.user?.id) ?? "";
  const [viewDocId, setViewDocId] = useState<string | null>(null);
  const [planFormOpen, setPlanFormOpen] = useState(false);
  const [planForm, setPlanForm] = useState({
    totalAmount: "",
    installmentAmount: "",
    installmentsTotal: "6",
    frequency: "MONTHLY",
  });

  const accountQ = usePatientAccount(cpid || null);
  const txQ = usePatientTransactions(cpid || null, 0, 25);
  const outQ = usePatientOutstanding(cpid || null);
  const plansQ = usePaymentPlans(cpid || null);
  const docsQ = useFinancialDocuments(cpid || null, null, 0, 20);
  const docDetailQ = useFinancialDocument(viewDocId);

  const recordPayment = useRecordPayment(cpid || null);
  const createPlan = useCreatePaymentPlan();
  const payInstallment = usePayInstallment();
  const genDoc = useGenerateDocument();

  const summary = useMemo(() => asRecord(accountQ.data), [accountQ.data]);
  const currency = readStr(summary, "currency") || "USD";
  const txRows = useMemo(() => unwrapItems(txQ.data).map(asRecord), [txQ.data]);
  const outstanding = useMemo(() => unwrapItems(outQ.data).map(asRecord), [outQ.data]);
  const plans = useMemo(() => unwrapItems(plansQ.data).map(asRecord), [plansQ.data]);
  const docs = useMemo(() => unwrapItems(docsQ.data).map(asRecord), [docsQ.data]);

  const payBill = (billId: string) => {
    const raw = window.prompt("Amount to pay (patient portion)?", "");
    if (raw == null || !raw.trim()) return;
    const amount = Number(raw);
    if (Number.isNaN(amount) || amount <= 0) return;
    recordPayment.mutate({
      billId,
      amount,
      paymentMethod: "MANUAL",
      paymentRef: `patient-portal-${Date.now()}`,
    });
  };

  const submitPlan = () => {
    if (!cpid) return;
    const total = Number(planForm.totalAmount);
    const inst = Number(planForm.installmentAmount);
    const n = Number(planForm.installmentsTotal);
    if (Number.isNaN(total) || Number.isNaN(inst) || Number.isNaN(n)) return;
    createPlan.mutate(
      {
        patientCpid: cpid,
        totalAmount: total,
        installmentAmount: inst,
        installmentsTotal: n,
        frequency: planForm.frequency,
      },
      { onSuccess: () => setPlanFormOpen(false) },
    );
  };

  const runGenerate = (docType: string) => {
    if (!cpid) return;
    const period =
      docType === "TAX_CERTIFICATE"
        ? window.prompt("Tax year (e.g. 2025)?", String(new Date().getFullYear() - 1))
        : undefined;
    genDoc.mutate({
      docType,
      subjectType: "PATIENT",
      subjectRef: cpid,
      period: period ?? undefined,
    });
  };

  return (
    <AppLayout>
      <PageShell
        title="My healthcare account"
        subtitle="Balances, payment history, payment plans, and financial documents"
        icon={<Wallet className="h-6 w-6" />}
      >
        <div className="mb-6">
          <Link
            href="/home"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" />
            Back to home
          </Link>
        </div>

        {!cpid && (
          <p className="text-sm text-warning-foreground bg-warning-soft border border-amber-100 rounded-lg px-3 py-2">
            Sign in to view your financial account.
          </p>
        )}

        {cpid && (
          <div className="space-y-8">
            {/* Summary */}
            <section className="rounded-xl border border-border bg-card shadow-sm p-5">
              <h2 className="text-sm font-semibold text-foreground flex items-center gap-2 mb-4">
                <PiggyBank className="h-4 w-4 text-primary" />
                Account summary
              </h2>
              {accountQ.isLoading && (
                <div className="flex items-center gap-2 text-muted-foreground text-sm">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading…
                </div>
              )}
              {accountQ.isError && (
                <p className="text-sm text-danger">Could not load account summary.</p>
              )}
              {!accountQ.isLoading && !accountQ.isError && (
                <div className="grid grid-cols-2 md:grid-cols-5 gap-3 text-sm">
                  <div className="rounded-lg bg-background p-3">
                    <div className="text-muted-foreground text-xs">Total billed</div>
                    <div className="font-semibold text-foreground">
                      {formatMoney(readNum(summary, "totalBilled", "total_billed"), currency)}
                    </div>
                  </div>
                  <div className="rounded-lg bg-background p-3">
                    <div className="text-muted-foreground text-xs">You paid</div>
                    <div className="font-semibold text-foreground">
                      {formatMoney(readNum(summary, "totalPaid", "total_paid"), currency)}
                    </div>
                  </div>
                  <div className="rounded-lg bg-background p-3">
                    <div className="text-muted-foreground text-xs">Insurer covered</div>
                    <div className="font-semibold text-foreground">
                      {formatMoney(readNum(summary, "totalInsurer", "total_insurer"), currency)}
                    </div>
                  </div>
                  <div className="rounded-lg bg-warning-soft p-3 border border-amber-100">
                    <div className="text-warning-foreground text-xs">Outstanding</div>
                    <div className="font-semibold text-warning-foreground">
                      {formatMoney(readNum(summary, "totalOutstanding", "total_outstanding"), currency)}
                    </div>
                  </div>
                  <div className="rounded-lg bg-background p-3">
                    <div className="text-muted-foreground text-xs">Write-offs</div>
                    <div className="font-semibold text-foreground">
                      {formatMoney(readNum(summary, "totalWriteoff", "total_writeoff"), currency)}
                    </div>
                  </div>
                </div>
              )}
            </section>

            {/* Transactions */}
            <section className="rounded-xl border border-border bg-card shadow-sm overflow-hidden">
              <div className="border-b border-border px-4 py-3 bg-background/80 flex items-center gap-2">
                <Receipt className="h-4 w-4 text-muted-foreground" />
                <h2 className="text-sm font-semibold text-foreground">Transaction history</h2>
              </div>
              <div className="overflow-x-auto">
                {txQ.isLoading && (
                  <div className="p-6 flex items-center gap-2 text-muted-foreground text-sm">
                    <Loader2 className="h-4 w-4 animate-spin" /> Loading…
                  </div>
                )}
                {!txQ.isLoading && txQ.isError && (
                  <p className="p-4 text-sm text-danger">Could not load transactions.</p>
                )}
                {!txQ.isLoading && !txQ.isError && (
                  <table className="min-w-full text-sm">
                    <thead className="bg-background text-left text-xs text-muted-foreground uppercase tracking-wide">
                      <tr>
                        <th className="px-4 py-2">Date</th>
                        <th className="px-4 py-2">Description</th>
                        <th className="px-4 py-2">Type</th>
                        <th className="px-4 py-2 text-right">Patient</th>
                        <th className="px-4 py-2 text-right">Running bal.</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {txRows.length === 0 && (
                        <tr>
                          <td colSpan={5} className="px-4 py-6 text-center text-muted-foreground">
                            No transactions yet.
                          </td>
                        </tr>
                      )}
                      {txRows.map((row, i) => (
                        <tr key={readStr(row, "txnId", "txn_id") || String(i)} className="hover:bg-background/80">
                          <td className="px-4 py-2 whitespace-nowrap text-foreground">
                            {readStr(row, "txnDate", "txn_date").slice(0, 16).replace("T", " ")}
                          </td>
                          <td className="px-4 py-2 text-foreground max-w-xs truncate">
                            {readStr(row, "description")}
                          </td>
                          <td className="px-4 py-2">
                            <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs font-medium text-foreground">
                              {readStr(row, "txnType", "txn_type")}
                            </span>
                          </td>
                          <td className="px-4 py-2 text-right tabular-nums">
                            {formatMoney(readNum(row, "patientAmount", "patient_amount"), currency)}
                          </td>
                          <td className="px-4 py-2 text-right tabular-nums font-medium text-foreground">
                            {formatMoney(readNum(row, "runningBalance", "running_balance"), currency)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </section>

            {/* Outstanding */}
            <section className="rounded-xl border border-border bg-card shadow-sm overflow-hidden">
              <div className="border-b border-border px-4 py-3 bg-danger-soft/60 flex items-center gap-2">
                <CreditCard className="h-4 w-4 text-danger" />
                <h2 className="text-sm font-semibold text-foreground">Outstanding bills</h2>
              </div>
              <div className="p-4 space-y-3">
                {outQ.isLoading && (
                  <div className="flex items-center gap-2 text-muted-foreground text-sm">
                    <Loader2 className="h-4 w-4 animate-spin" /> Loading…
                  </div>
                )}
                {!outQ.isLoading && outstanding.length === 0 && (
                  <p className="text-sm text-muted-foreground">No outstanding patient balances.</p>
                )}
                {outstanding.map((row, i) => (
                  <div
                    key={readStr(row, "billId", "bill_id") || String(i)}
                    className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border bg-background/50 px-3 py-2"
                  >
                    <div>
                      <div className="text-xs text-muted-foreground">Bill</div>
                      <div className="font-mono text-sm text-foreground">
                        {readStr(row, "billId", "bill_id")}
                      </div>
                      <div className="text-xs text-muted-foreground mt-1">
                        Balance due:{" "}
                        <span className="font-semibold text-rose-800">
                          {formatMoney(readNum(row, "balanceDue", "balance_due"), currency)}
                        </span>
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => payBill(readStr(row, "billId", "bill_id"))}
                      disabled={recordPayment.isPending}
                      className="rounded-lg bg-rose-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-rose-700 disabled:opacity-50"
                    >
                      Pay
                    </button>
                  </div>
                ))}
              </div>
            </section>

            {/* Payment plans */}
            <section className="rounded-xl border border-border bg-card shadow-sm overflow-hidden">
              <div className="border-b border-border px-4 py-3 bg-info-soft/60 flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <Wallet className="h-4 w-4 text-primary-hover" />
                  <h2 className="text-sm font-semibold text-foreground">Payment plans</h2>
                </div>
                <button
                  type="button"
                  onClick={() => setPlanFormOpen((v) => !v)}
                  className="text-xs font-medium text-primary-hover hover:underline"
                >
                  {planFormOpen ? "Close form" : "New plan"}
                </button>
              </div>
              <div className="p-4 space-y-4">
                {planFormOpen && (
                  <div className="rounded-lg border border-indigo-100 bg-info-soft/30 p-3 space-y-2 text-sm">
                    <div className="grid grid-cols-2 gap-2">
                      <label className="block">
                        <span className="text-xs text-muted-foreground">Total amount</span>
                        <input
                          className="mt-0.5 w-full rounded border border-border px-2 py-1"
                          value={planForm.totalAmount}
                          onChange={(e) => setPlanForm((f) => ({ ...f, totalAmount: e.target.value }))}
                        />
                      </label>
                      <label className="block">
                        <span className="text-xs text-muted-foreground">Installment</span>
                        <input
                          className="mt-0.5 w-full rounded border border-border px-2 py-1"
                          value={planForm.installmentAmount}
                          onChange={(e) => setPlanForm((f) => ({ ...f, installmentAmount: e.target.value }))}
                        />
                      </label>
                    </div>
                    <div className="grid grid-cols-2 gap-2">
                      <label className="block">
                        <span className="text-xs text-muted-foreground"># Installments</span>
                        <input
                          className="mt-0.5 w-full rounded border border-border px-2 py-1"
                          value={planForm.installmentsTotal}
                          onChange={(e) => setPlanForm((f) => ({ ...f, installmentsTotal: e.target.value }))}
                        />
                      </label>
                      <label className="block">
                        <span className="text-xs text-muted-foreground">Frequency</span>
                        <select
                          className="mt-0.5 w-full rounded border border-border px-2 py-1"
                          value={planForm.frequency}
                          onChange={(e) => setPlanForm((f) => ({ ...f, frequency: e.target.value }))}
                        >
                          <option value="MONTHLY">Monthly</option>
                          <option value="WEEKLY">Weekly</option>
                          <option value="QUARTERLY">Quarterly</option>
                        </select>
                      </label>
                    </div>
                    <button
                      type="button"
                      onClick={submitPlan}
                      disabled={createPlan.isPending}
                      className="rounded-lg bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-primary disabled:opacity-50"
                    >
                      Create plan
                    </button>
                  </div>
                )}
                {plansQ.isLoading && (
                  <div className="flex items-center gap-2 text-muted-foreground text-sm">
                    <Loader2 className="h-4 w-4 animate-spin" /> Loading…
                  </div>
                )}
                {!plansQ.isLoading && plans.length === 0 && (
                  <p className="text-sm text-muted-foreground">No payment plans.</p>
                )}
                {plans.map((pl, i) => {
                  const total = readNum(pl, "totalAmount", "total_amount");
                  const paid = readNum(pl, "paidAmount", "paid_amount");
                  const pct = total > 0 ? Math.min(100, (paid / total) * 100) : 0;
                  const planId = readStr(pl, "planId", "plan_id");
                  const status = readStr(pl, "status");
                  return (
                    <div key={planId || String(i)} className="rounded-lg border border-border p-3">
                      <div className="flex justify-between text-sm">
                        <span className="font-medium text-foreground">{status}</span>
                        <span className="text-muted-foreground tabular-nums">
                          {formatMoney(paid, currency)} / {formatMoney(total, currency)}
                        </span>
                      </div>
                      <div className="mt-2 h-2 w-full rounded-full bg-neutral-100 overflow-hidden">
                        <div
                          className="h-full rounded-full bg-indigo-500 transition-all"
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                      {status.toUpperCase() === "ACTIVE" && planId && (
                        <button
                          type="button"
                          onClick={() => payInstallment.mutate({ planId })}
                          disabled={payInstallment.isPending}
                          className="mt-2 text-xs font-medium text-primary-hover hover:underline disabled:opacity-50"
                        >
                          Record installment
                        </button>
                      )}
                    </div>
                  );
                })}
              </div>
            </section>

            {/* Documents */}
            <section className="rounded-xl border border-border bg-card shadow-sm overflow-hidden">
              <div className="border-b border-border px-4 py-3 bg-teal-50/60 flex items-center gap-2">
                <FileText className="h-4 w-4 text-teal-700" />
                <h2 className="text-sm font-semibold text-foreground">Documents</h2>
              </div>
              <div className="p-4 space-y-3">
                <div className="flex flex-wrap gap-2">
                  {(
                    [
                      ["TAX_CERTIFICATE", "Tax certificate"],
                      ["PATIENT_INVOICE", "Invoice (needs bill id)"],
                    ] as const
                  ).map(([type, label]) => (
                    <button
                      key={type}
                      type="button"
                      onClick={() => runGenerate(type)}
                      disabled={genDoc.isPending}
                      className="rounded-lg border border-teal-200 bg-card px-2 py-1 text-xs font-medium text-teal-800 hover:bg-teal-50 disabled:opacity-50"
                    >
                      Generate {label}
                    </button>
                  ))}
                </div>
                <p className="text-xs text-muted-foreground">
                  For invoices and receipts tied to a specific bill or payment, generate from finance operations
                  or use subject_ref of that bill/payment id with the API.
                </p>
                {docsQ.isLoading && (
                  <div className="flex items-center gap-2 text-muted-foreground text-sm">
                    <Loader2 className="h-4 w-4 animate-spin" /> Loading…
                  </div>
                )}
                {!docsQ.isLoading && docs.length === 0 && (
                  <p className="text-sm text-muted-foreground">No documents stored yet.</p>
                )}
                <ul className="divide-y divide-gray-100 border border-border rounded-lg">
                  {docs.map((d, i) => {
                    const id = readStr(d, "docId", "doc_id");
                    const dtype = readStr(d, "docType", "doc_type");
                    const title = readStr(d, "title") || dtype;
                    return (
                      <li key={id || String(i)} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2">
                        <div>
                          <div className="text-sm font-medium text-foreground">{title}</div>
                          <div className="text-xs text-muted-foreground">{dtype}</div>
                        </div>
                        <button
                          type="button"
                          onClick={() => setViewDocId(id)}
                          className="text-xs font-medium text-teal-700 hover:underline"
                        >
                          View JSON
                        </button>
                      </li>
                    );
                  })}
                </ul>
                {viewDocId && (
                  <div className="rounded-lg border border-border bg-neutral-900 p-3 text-xs text-green-100 font-mono overflow-auto max-h-64">
                    <div className="flex justify-between mb-2 text-muted-foreground">
                      <span>Document {viewDocId}</span>
                      <button type="button" onClick={() => setViewDocId(null)} className="hover:text-white">
                        Close
                      </button>
                    </div>
                    {docDetailQ.isLoading && <span>Loading…</span>}
                    {!docDetailQ.isLoading && (
                      <JsonApiDataTable data={docDetailQ.data} columns={LANDELA_METADATA_COLUMNS} isLoading={docDetailQ.isPending} error={docDetailQ.error as Error | null} emptyTitle="No document detail" />
                    )}
                  </div>
                )}
              </div>
            </section>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
