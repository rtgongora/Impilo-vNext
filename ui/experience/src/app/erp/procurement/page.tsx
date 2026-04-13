"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useProcGrn,
  useProcInvoices,
  useProcPurchaseOrders,
  useProcRequisitions,
  useProcRfqs,
  useProcRfqResponses,
  useProcSuppliers,
} from "@/hooks/queries/useProcurement";

type Rfq = { rfqId?: string };

export default function ErpProcurementPage() {
  const suppliers = useProcSuppliers();
  const requisitions = useProcRequisitions();
  const pos = useProcPurchaseOrders();
  const grn = useProcGrn();
  const invoices = useProcInvoices();
  const rfqs = useProcRfqs();
  const rfqList = rfqs.data as Rfq[] | undefined;
  const defaultRfq = useMemo(() => {
    if (!Array.isArray(rfqList) || rfqList.length === 0) return "";
    return rfqList[0].rfqId ?? "";
  }, [rfqList]);
  const [rfqId, setRfqId] = useState("");
  const effRfq = rfqId || defaultRfq;
  const responses = useProcRfqResponses(effRfq || null);

  return (
    <AppLayout>
      <PageShell title="Procurement" subtitle="Suppliers through RFQs via BFF">
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <Link href="/erp" className="text-sm text-blue-600 hover:underline">
            ← ERP hub
          </Link>
          <label className="flex items-center gap-2 text-sm text-slate-700">
            RFQ
            <select
              className="rounded border border-slate-300 px-2 py-1 text-sm"
              value={effRfq}
              onChange={(e) => setRfqId(e.target.value)}
            >
              {Array.isArray(rfqList) &&
                rfqList.map((r) => (
                  <option key={r.rfqId} value={r.rfqId ?? ""}>
                    {r.rfqId}
                  </option>
                ))}
            </select>
          </label>
        </div>

        <div className="space-y-6">
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Suppliers</h3>
            <pre className="mt-2 max-h-40 overflow-auto text-xs">
              {JSON.stringify(suppliers.data ?? suppliers.error ?? suppliers.isLoading, null, 2)}
            </pre>
          </section>
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Requisitions</h3>
            <pre className="mt-2 max-h-40 overflow-auto text-xs">
              {JSON.stringify(requisitions.data ?? requisitions.error ?? requisitions.isLoading, null, 2)}
            </pre>
          </section>
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Purchase orders</h3>
            <pre className="mt-2 max-h-40 overflow-auto text-xs">
              {JSON.stringify(pos.data ?? pos.error ?? pos.isLoading, null, 2)}
            </pre>
          </section>
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Goods received</h3>
            <pre className="mt-2 max-h-40 overflow-auto text-xs">
              {JSON.stringify(grn.data ?? grn.error ?? grn.isLoading, null, 2)}
            </pre>
          </section>
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Invoices</h3>
            <pre className="mt-2 max-h-40 overflow-auto text-xs">
              {JSON.stringify(invoices.data ?? invoices.error ?? invoices.isLoading, null, 2)}
            </pre>
          </section>
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">RFQs</h3>
            <pre className="mt-2 max-h-40 overflow-auto text-xs">
              {JSON.stringify(rfqs.data ?? rfqs.error ?? rfqs.isLoading, null, 2)}
            </pre>
          </section>
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">RFQ responses</h3>
            <pre className="mt-2 max-h-40 overflow-auto text-xs">
              {JSON.stringify(responses.data ?? responses.error ?? responses.isLoading, null, 2)}
            </pre>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
