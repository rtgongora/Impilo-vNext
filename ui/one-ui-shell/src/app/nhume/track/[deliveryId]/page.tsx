"use client";

/**
 * Citizen-facing delivery tracking page.
 *
 * Designed to be the privacy-safe view a recipient sees when they tap "Track
 * delivery" from the citizen app or open a tracking link. Sensitive context
 * (commodity details, diagnosis) is intentionally omitted; only the status,
 * ETA, generalised origin/destination and high-level location updates show.
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { MapPin, Phone, MessageCircle, Loader2, Truck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NhumeStatusChip } from "@/components/nhume/NhumeStatusChip";
import { DeliveryTrackMapPanel } from "@/components/maps/DeliveryTrackMapPanel";
import { useNhumeDelivery, useNhumeTimeline, useNhumeTracking } from "@/hooks/useNhume";

export default function NhumeTrackPage() {
  const params = useParams<{ deliveryId: string }>();
  const id = params?.deliveryId as string | undefined;
  const { data: delivery, isPending, isError } = useNhumeDelivery(id);
  const { data: timeline } = useNhumeTimeline(id);
  const { data: tracking } = useNhumeTracking(id);

  return (
    <AppLayout>
      <PageShell title="Track delivery" subtitle="Status, ETA and updates — privacy-safe view" icon={<Truck className="h-6 w-6" />}>
        {isPending && (
          <div className="rounded-2xl border border-gray-200 bg-white p-10 text-center text-gray-500">
            <Loader2 className="inline-block h-5 w-5 animate-spin text-teal-500 mr-2" /> Loading…
          </div>
        )}
        {isError && (
          <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
            We could not load this delivery — please check the link and try again.
          </div>
        )}
        {delivery && (
          <div className="space-y-4">
            <div className="rounded-2xl border border-teal-200 bg-teal-50 p-5">
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-semibold text-teal-900">{String((delivery as Record<string, unknown>).reference ?? id)}</h2>
                <NhumeStatusChip status={String((delivery as Record<string, unknown>).status ?? "")} />
              </div>
              <p className="text-sm text-teal-900 mt-1">
                {String((delivery as Record<string, unknown>).origin_label ?? "Origin")} →
                {" "}{String((delivery as Record<string, unknown>).destination_label ?? "Destination")}
              </p>
              {Boolean((delivery as Record<string, unknown>).sla_due_at) && (
                <p className="text-xs text-teal-800 mt-2">
                  Expected by {new Date(String((delivery as Record<string, unknown>).sla_due_at)).toLocaleString()}
                </p>
              )}
            </div>

            <DeliveryTrackMapPanel
              delivery={delivery as Record<string, unknown>}
              trackingPoints={tracking?.data ?? []}
            />

            <div className="rounded-2xl border border-gray-200 bg-white">
              <div className="border-b border-gray-100 px-5 py-3 flex items-center gap-2">
                <MapPin className="h-4 w-4 text-teal-600" />
                <h3 className="font-semibold text-gray-900">Status timeline</h3>
              </div>
              {timeline && timeline.data?.length ? (
                <ol className="divide-y divide-gray-100">
                  {timeline.data.slice(-12).reverse().map((event, idx) => {
                    const ev = event as Record<string, unknown>;
                    return (
                      <li key={idx} className="px-5 py-3 flex items-center justify-between">
                        <NhumeStatusChip status={String(ev.status ?? "")} />
                        <span className="text-xs text-gray-500">{ev.recorded_at ? new Date(String(ev.recorded_at)).toLocaleString() : ""}</span>
                      </li>
                    );
                  })}
                </ol>
              ) : (
                <div className="px-5 py-8 text-sm text-gray-500 text-center">Awaiting status updates.</div>
              )}
            </div>

            {tracking && tracking.data && tracking.data.length > 0 && (
              <div className="rounded-2xl border border-gray-200 bg-white">
                <div className="border-b border-gray-100 px-5 py-3"><h3 className="font-semibold text-gray-900">Recent location updates</h3></div>
                <ul className="divide-y divide-gray-100 max-h-72 overflow-auto">
                  {tracking.data.slice(-10).reverse().map((pt, idx) => {
                    const p = pt as Record<string, unknown>;
                    return (
                      <li key={idx} className="px-5 py-3 text-sm flex justify-between">
                        <span className="text-gray-700">approx. {String(p.latitude ?? "")}, {String(p.longitude ?? "")}</span>
                        <span className="text-xs text-gray-500">{p.recorded_at ? new Date(String(p.recorded_at)).toLocaleString() : ""}</span>
                      </li>
                    );
                  })}
                </ul>
              </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <Link href="/communication" className="rounded-2xl border border-gray-200 bg-white p-4 flex items-center gap-3 hover:border-teal-300 hover:shadow-sm">
                <MessageCircle className="h-5 w-5 text-teal-600" />
                <div>
                  <div className="font-medium text-gray-900">Contact support</div>
                  <div className="text-xs text-gray-600">Through Comms Hub messaging</div>
                </div>
              </Link>
              <Link href="/ask" className="rounded-2xl border border-gray-200 bg-white p-4 flex items-center gap-3 hover:border-teal-300 hover:shadow-sm">
                <Phone className="h-5 w-5 text-teal-600" />
                <div>
                  <div className="font-medium text-gray-900">Ask Nompilo for help</div>
                  <div className="text-xs text-gray-600">Reschedule, contact courier, explain status</div>
                </div>
              </Link>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
