"use client";

import type { ReactNode } from "react";
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
import { countEnvelopeList } from "@/lib/apiEnvelope";

type Rfq = { rfqId?: string };

function SectionCard(props: {
  title: string;
  count: number;
  loading: boolean;
  error: boolean;
  children?: ReactNode;
}) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-slate-800">{props.title}</h3>
        <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-700">
          {props.loading ? "…" : props.error ? "—" : props.count}
        </span>
      </div>
      {props.error ? <p className="mt-2 text-xs text-rose-700">Request failed — check BFF and procurement service.</p> : null}
      {props.children}
    </section>
  );
}

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
      <PageShell title="Procurement" subtitle="Suppliers, requisitions, POs, GRN, invoices, and RFQs via Experience BFF.">
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <Link href="/erp" className="text-sm text-impilo-500 hover:underline">
            ← ERP hub
          </Link>
          <Link href="/enterprise" className="text-sm text-slate-600 hover:underline">
            Enterprise dashboard
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

        <div className="grid gap-4 md:grid-cols-2">
          <SectionCard
            title="Suppliers"
            count={countEnvelopeList(suppliers.data)}
            loading={suppliers.isLoading}
            error={Boolean(suppliers.error)}
          />
          <SectionCard
            title="Requisitions"
            count={countEnvelopeList(requisitions.data)}
            loading={requisitions.isLoading}
            error={Boolean(requisitions.error)}
          />
          <SectionCard
            title="Purchase orders"
            count={countEnvelopeList(pos.data)}
            loading={pos.isLoading}
            error={Boolean(pos.error)}
          />
          <SectionCard title="Goods received" count={countEnvelopeList(grn.data)} loading={grn.isLoading} error={Boolean(grn.error)} />
          <SectionCard title="Invoices" count={countEnvelopeList(invoices.data)} loading={invoices.isLoading} error={Boolean(invoices.error)} />
          <SectionCard title="RFQs" count={countEnvelopeList(rfqs.data)} loading={rfqs.isLoading} error={Boolean(rfqs.error)} />
          <SectionCard
            title="RFQ responses"
            count={countEnvelopeList(responses.data)}
            loading={responses.isLoading}
            error={Boolean(responses.error)}
          />
        </div>

        <p className="mt-6 text-xs text-slate-500">
          Detailed row explorers ship with datagrids once procurement contracts stabilise; counts above are derived from API envelopes
          only — no mock rows.
        </p>
      </PageShell>
    </AppLayout>
  );
}
