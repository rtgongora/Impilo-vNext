"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ClipboardList, ExternalLink, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useBloodOrderDetail,
  useCollectOrderSample,
  useCrossmatchOrder,
  useIssueOrderUnit,
  useReserveOrderUnit,
  useSubmitBloodOrder,
} from "@/hooks/queries/useMadi";

export default function BloodOrderDetailPage() {
  const params = useParams<{ orderId: string }>();
  const orderId = params.orderId;
  const { data: detail, isPending: detailLoading } = useBloodOrderDetail(orderId);
  const submit = useSubmitBloodOrder(orderId);
  const sample = useCollectOrderSample(orderId);
  const crossmatch = useCrossmatchOrder(orderId);
  const reserve = useReserveOrderUnit(orderId);
  const issue = useIssueOrderUnit(orderId);

  const [sampleNumber, setSampleNumber] = useState("");
  const [sampleId, setSampleId] = useState("");
  const [unitId, setUnitId] = useState("");
  const [crossmatchResult, setCrossmatchResult] = useState("COMPATIBLE");

  const order = detail?.order;
  const orosDeepLink = detail?.orosDeepLinkPath;
  const butanoHint = detail?.butanoServiceRequestHint;
  const orosRef = order?.orosOrderRef as string | undefined;
  const status = order?.status as string | undefined;

  return (
    <AppLayout>
      <PageShell title="Blood Order" subtitle={`Order ${orderId}`} icon={<ClipboardList className="h-6 w-6" />}>
        {detailLoading && (
          <div className="mb-4 flex items-center gap-2 text-sm text-gray-500">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading order…
          </div>
        )}

        {order && (
          <div className="mb-6 rounded-2xl border border-gray-200 bg-white p-4 text-sm space-y-2">
            <div className="flex flex-wrap gap-4">
              <span>
                Status: <strong>{status ?? "—"}</strong>
              </span>
              <span>
                Patient: <code className="text-xs">{String(order.patientCpid ?? "")}</code>
              </span>
              <span>
                Component: <strong>{String(order.componentType ?? "")}</strong> ({String(order.bloodGroup ?? "")})
              </span>
            </div>
            {orosRef && (
              <div className="pt-2 border-t border-gray-100 flex flex-wrap items-center gap-3">
                <span className="text-gray-600">
                  OROS ServiceRequest: <code className="text-xs">{orosRef}</code>
                </span>
                {orosDeepLink && (
                  <Link
                    href={orosDeepLink}
                    className="inline-flex items-center gap-1 text-rose-600 font-medium hover:underline"
                  >
                    View in Lab worklist <ExternalLink className="h-3.5 w-3.5" />
                  </Link>
                )}
                {butanoHint && (
                  <Link
                    href={`/butano/timeline?resource=ServiceRequest&ref=${encodeURIComponent(orosRef)}`}
                    className="inline-flex items-center gap-1 text-gray-600 hover:text-rose-600 text-xs"
                  >
                    SHR ServiceRequest <ExternalLink className="h-3 w-3" />
                  </Link>
                )}
              </div>
            )}
          </div>
        )}

        <div className="space-y-6">
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => submit.mutate()}
              disabled={submit.isPending}
              className="rounded-xl bg-rose-600 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
            >
              {submit.isPending ? <Loader2 className="h-4 w-4 animate-spin inline" /> : null} Submit order
            </button>
            <Link href="/madi/orders" className="rounded-xl border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50">
              New order
            </Link>
          </div>

          <section className="rounded-2xl border border-gray-200 bg-white p-5 space-y-3">
            <h2 className="text-sm font-semibold text-gray-900">1. Collect sample</h2>
            <div className="flex gap-2">
              <input
                value={sampleNumber}
                onChange={(e) => setSampleNumber(e.target.value)}
                placeholder="Sample number"
                className="flex-1 rounded-xl border border-gray-300 px-3 py-2 text-sm"
              />
              <button
                type="button"
                onClick={() => sample.mutate({ sample_number: sampleNumber })}
                disabled={!sampleNumber || sample.isPending}
                className="rounded-xl bg-gray-800 px-3 py-2 text-sm text-white disabled:opacity-50"
              >
                Record
              </button>
            </div>
          </section>

          <section className="rounded-2xl border border-gray-200 bg-white p-5 space-y-3">
            <h2 className="text-sm font-semibold text-gray-900">2. Crossmatch</h2>
            <input
              value={sampleId}
              onChange={(e) => setSampleId(e.target.value)}
              placeholder="Sample UUID"
              className="w-full rounded-xl border border-gray-300 px-3 py-2 text-sm font-mono mb-2"
            />
            <input
              value={unitId}
              onChange={(e) => setUnitId(e.target.value)}
              placeholder="Unit UUID"
              className="w-full rounded-xl border border-gray-300 px-3 py-2 text-sm font-mono mb-2"
            />
            <select
              value={crossmatchResult}
              onChange={(e) => setCrossmatchResult(e.target.value)}
              className="w-full rounded-xl border border-gray-300 px-3 py-2 text-sm bg-white mb-2"
            >
              <option value="COMPATIBLE">Compatible</option>
              <option value="INCOMPATIBLE">Incompatible</option>
              <option value="INCONCLUSIVE">Inconclusive</option>
            </select>
            <button
              type="button"
              onClick={() =>
                crossmatch.mutate({
                  sample_id: sampleId,
                  unit_id: unitId,
                  result: crossmatchResult as "COMPATIBLE" | "INCOMPATIBLE" | "INCONCLUSIVE",
                })
              }
              disabled={!sampleId || !unitId || crossmatch.isPending}
              className="rounded-xl bg-gray-800 px-3 py-2 text-sm text-white disabled:opacity-50"
            >
              Record crossmatch
            </button>
          </section>

          <section className="rounded-2xl border border-gray-200 bg-white p-5 space-y-3">
            <h2 className="text-sm font-semibold text-gray-900">3. Reserve & issue</h2>
            <button
              type="button"
              onClick={() => reserve.mutate({ unit_id: unitId })}
              disabled={!unitId || reserve.isPending}
              className="mr-2 rounded-xl border border-gray-300 px-3 py-2 text-sm disabled:opacity-50"
            >
              Reserve unit
            </button>
            <button
              type="button"
              onClick={() => issue.mutate({ unit_id: unitId })}
              disabled={!unitId || issue.isPending}
              className="rounded-xl bg-rose-600 px-3 py-2 text-sm text-white disabled:opacity-50"
            >
              Issue unit
            </button>
            <Link href="/madi/transfusion" className="block text-sm text-rose-600 mt-2">
              → Record transfusion
            </Link>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
