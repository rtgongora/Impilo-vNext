"use client";

/**
 * Inbound & Handover — the receiving end of a movement (Journey 7).
 *
 * A receiving facility sees what is arriving and confirms handover. Handover
 * adapts by cargo: a patient is received by a clinician, a specimen by the lab,
 * a commodity by the store, blood by authorised staff, a vaccine by the
 * cold-chain focal person. Confirming records proof of delivery on the mission,
 * closing the loop with an auditable event — not UI-only state.
 */

import { useMemo, useState } from "react";
import Link from "next/link";
import { PackageCheck, Loader2, CheckCircle2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NhumeStatusChip, NhumePriorityChip } from "@/components/nhume/NhumeStatusChip";
import { useNhumeDeliveries, useRecordProof } from "@/hooks/useNhume";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import type { DeliverySummary } from "@/lib/nhume";

/** Statuses that mean "on the way to / at the receiving end". */
const INBOUND_STATUSES = new Set([
  "PICKED_UP",
  "IN_TRANSIT",
  "AT_DESTINATION",
  "DELIVERY_ATTEMPTED",
  "ARRIVED",
]);

function handoverLabelFor(deliveryType?: string | null): string {
  switch (String(deliveryType ?? "").toUpperCase()) {
    case "LAB_SAMPLE_PICKUP":
    case "LAB_RESULT_DELIVERY":
      return "Lab receives specimen";
    case "BLOOD_PRODUCT":
      return "Authorised staff receives blood";
    case "VACCINE":
    case "COLD_CHAIN":
      return "Cold-chain focal receives vaccines";
    case "MEDICINE":
    case "PRESCRIPTION_REFILL":
    case "PROGRAMME_COMMODITY":
      return "Store receives stock";
    case "EQUIPMENT":
    case "ASSISTIVE_DEVICE":
      return "Facility receives equipment";
    case "FACILITY_TRANSFER":
      return "Clinician receives patient";
    case "DOCUMENT_DELIVERY":
      return "Office receives documents";
    default:
      return "Confirm handover";
  }
}

export default function NhumeInboundPage() {
  const facility = useFacilityStore((s) => s.facility);
  const { data, isPending } = useNhumeDeliveries({ size: 200 });
  const rows = useMemo(
    () => (data?.data ?? []).filter((d) => INBOUND_STATUSES.has(String(d.status).toUpperCase())),
    [data],
  );

  return (
    <AppLayout>
      <PageShell
        title="Inbound & Handover"
        subtitle={facility ? `Incoming movements — ${facility.name}` : "Incoming movements to receive and hand over"}
        icon={<PackageCheck className="h-6 w-6" />}
      >
        {isPending ? (
          <div className="rounded-2xl border border-border bg-card p-10 text-center text-muted-foreground">
            <Loader2 className="inline-block h-5 w-5 animate-spin text-teal-500 mr-2" />
            Loading inbound movements…
          </div>
        ) : rows.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-border bg-background p-12 text-center">
            <PackageCheck className="mx-auto h-10 w-10 text-muted-foreground" />
            <h3 className="mt-3 font-semibold text-foreground">Nothing inbound right now</h3>
            <p className="mt-1 text-sm text-muted-foreground">
              Movements appear here once they are picked up and en route to a destination.
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {rows.map((d) => (
              <InboundRow key={d.delivery_id} delivery={d} />
            ))}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}

function InboundRow({ delivery }: { delivery: DeliverySummary }) {
  const proof = useRecordProof(delivery.delivery_id);
  const [receiver, setReceiver] = useState("");
  const [open, setOpen] = useState(false);
  const label = handoverLabelFor(delivery.delivery_type);
  const done = ["DELIVERED", "CLOSED"].includes(String(delivery.status).toUpperCase());

  async function confirm() {
    await proof.mutateAsync({
      method: "FACILITY_STAMP",
      recipient_name: receiver || undefined,
      notes: `Handover received at destination — ${label}`,
    });
    setOpen(false);
  }

  return (
    <div className="rounded-2xl border border-border bg-card p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <Link href={`/nhume/deliveries/${delivery.delivery_id}`} className="font-medium text-teal-700 hover:text-teal-900">
            {delivery.reference}
          </Link>
          <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <span>{String(delivery.delivery_type ?? "—").replace(/_/g, " ")}</span>
            <span>·</span>
            <span className="truncate">{delivery.origin_label ?? "origin"} → {delivery.destination_label ?? "destination"}</span>
            {delivery.cold_chain_required ? <span className="rounded-full bg-info-soft px-2 py-0.5 text-[11px] text-primary-hover">cold-chain</span> : null}
            {delivery.chain_of_custody_required ? <span className="rounded-full bg-warning-soft px-2 py-0.5 text-[11px] text-warning-foreground">custody</span> : null}
          </div>
        </div>
        <div className="flex items-center gap-2">
          <NhumePriorityChip priority={String(delivery.priority ?? "STANDARD")} />
          <NhumeStatusChip status={String(delivery.status)} />
          {!done ? (
            <button
              type="button"
              onClick={() => setOpen((v) => !v)}
              className="inline-flex items-center gap-2 rounded-xl bg-teal-600 px-3 py-2 text-sm font-medium text-white hover:bg-primary"
            >
              <CheckCircle2 className="h-4 w-4" /> {label}
            </button>
          ) : (
            <span className="inline-flex items-center gap-1 rounded-xl bg-success-soft px-3 py-2 text-sm text-primary-hover">
              <CheckCircle2 className="h-4 w-4" /> Handed over
            </span>
          )}
        </div>
      </div>

      {open ? (
        <div className="mt-3 flex flex-wrap items-end gap-2 border-t border-border pt-3">
          <label className="text-xs text-muted-foreground">
            Received by
            <input
              value={receiver}
              onChange={(e) => setReceiver(e.target.value)}
              placeholder="name / role of receiver"
              className="mt-1 block w-64 rounded-lg border border-border px-3 py-2 text-sm"
            />
          </label>
          <button
            type="button"
            onClick={confirm}
            disabled={proof.isPending}
            className="inline-flex items-center gap-2 rounded-xl bg-teal-600 px-3 py-2 text-sm font-medium text-white hover:bg-primary disabled:opacity-60"
          >
            {proof.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            Confirm receipt
          </button>
          <button type="button" onClick={() => setOpen(false)} className="rounded-xl border border-border px-3 py-2 text-sm text-foreground hover:bg-background">
            Cancel
          </button>
          {proof.isError ? <span className="text-xs text-danger">Handover was rejected — check status and role.</span> : null}
        </div>
      ) : null}
    </div>
  );
}
