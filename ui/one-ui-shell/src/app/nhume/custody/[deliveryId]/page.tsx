"use client";

/**
 * Chain of Custody — focused full-page view.
 *
 * Mirrors the custody tab on the delivery detail page, but as a dedicated
 * print/share-friendly surface for auditors, public health authorities or
 * couriers handing over a high-risk specimen.
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, ShieldCheck, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useNhumeCustody, useNhumeDelivery } from "@/hooks/useNhume";

export default function NhumeCustodyPage() {
  const params = useParams<{ deliveryId: string }>();
  const id = params?.deliveryId as string | undefined;
  const { data: delivery } = useNhumeDelivery(id);
  const { data, isPending } = useNhumeCustody(id);
  const events = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Chain of Custody" subtitle="Full custody log with seal IDs, temperature and exception flags" icon={<ShieldCheck className="h-6 w-6" />}>
        <div className="mb-4">
          <Link href={`/nhume/deliveries/${id}`} className="inline-flex items-center gap-1 text-sm text-gray-600 hover:text-gray-900">
            <ArrowLeft className="h-4 w-4" /> Back to delivery
          </Link>
        </div>
        {delivery && (
          <div className="rounded-2xl border border-gray-200 bg-white p-4 mb-4">
            <h2 className="font-semibold text-gray-900">{String((delivery as Record<string, unknown>).reference ?? id)}</h2>
            <p className="text-sm text-gray-600 mt-1">
              {String((delivery as Record<string, unknown>).delivery_type ?? "")} ·
              {String((delivery as Record<string, unknown>).origin_label ?? "")} → {String((delivery as Record<string, unknown>).destination_label ?? "")}
            </p>
          </div>
        )}
        {isPending ? (
          <div className="rounded-2xl border border-gray-200 bg-white p-10 text-center text-gray-500">
            <Loader2 className="inline-block h-5 w-5 animate-spin text-teal-500 mr-2" /> Loading…
          </div>
        ) : events.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-gray-300 bg-gray-50 p-8 text-sm text-gray-600 text-center">
            No custody events recorded for this delivery.
          </div>
        ) : (
          <ol className="space-y-3">
            {events.map((event, idx) => {
              const ev = event as Record<string, unknown>;
              const isException = Boolean(ev.exception);
              return (
                <li key={idx} className={`rounded-2xl border bg-white p-4 ${isException ? "border-rose-200" : "border-gray-200"}`}>
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-gray-900">{String(ev.event_kind ?? "")}</span>
                    {isException && <span className="inline-flex items-center rounded-full bg-rose-100 text-rose-700 px-2 py-0.5 text-[11px]">EXCEPTION</span>}
                  </div>
                  <div className="text-xs text-gray-500 mt-1">
                    {ev.recorded_at ? new Date(String(ev.recorded_at)).toLocaleString() : ""}
                  </div>
                  <dl className="mt-2 grid grid-cols-2 md:grid-cols-4 gap-x-4 gap-y-1 text-xs">
                    {ev.custody_holder_ref ? <Pair label="Holder" value={String(ev.custody_holder_ref)} /> : null}
                    {ev.custody_location_label ? <Pair label="Location" value={String(ev.custody_location_label)} /> : null}
                    {ev.seal_id ? <Pair label="Seal" value={String(ev.seal_id)} /> : null}
                    {ev.temperature_c !== undefined && ev.temperature_c !== null ? <Pair label="Temp °C" value={String(ev.temperature_c)} /> : null}
                    {ev.verification_method ? <Pair label="Verification" value={String(ev.verification_method)} /> : null}
                  </dl>
                  {ev.notes ? <p className="mt-2 text-sm text-gray-700">{String(ev.notes)}</p> : null}
                </li>
              );
            })}
          </ol>
        )}
      </PageShell>
    </AppLayout>
  );
}

function Pair({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="uppercase tracking-wide text-gray-500">{label}</dt>
      <dd className="font-medium text-gray-900">{value}</dd>
    </div>
  );
}
