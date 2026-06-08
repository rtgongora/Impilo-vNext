"use client";

/**
 * Nhume Delivery Detail
 *
 * Single delivery surface used by dispatchers, providers, facility runners,
 * couriers and citizens. All sensitive actions go through the backend, which
 * enforces Trust-Layer policy. The page is structured around four tabs:
 *  • Overview & lifecycle (status, transitions)
 *  • Timeline & tracking (status + location events)
 *  • Chain of custody (events + new event capture)
 *  • Items & packages
 */

import { useParams } from "next/navigation";
import { useState } from "react";
import Link from "next/link";
import { ArrowLeft, Loader2, Send, CheckCircle2, XCircle, Truck, MapPin, ShieldCheck, Package } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NhumeStatusChip, NhumePriorityChip } from "@/components/nhume/NhumeStatusChip";
import { DeliveryTrackMapPanel } from "@/components/maps/DeliveryTrackMapPanel";
import {
  useNhumeDelivery,
  useNhumeTimeline,
  useNhumeTracking,
  useNhumeCustody,
  useSubmitDelivery,
  useApproveDelivery,
  useRejectDelivery,
  useCancelDelivery,
  useAssignDelivery,
  useAcceptDelivery,
  usePickupDelivery,
  useStartDeliveryTransit,
  useFailDelivery,
  useReturnDelivery,
  useRecordCustody,
  useRecordProof,
} from "@/hooks/useNhume";

type TabId = "overview" | "timeline" | "custody" | "items";

const TABS: Array<{ id: TabId; label: string; Icon: typeof Truck }> = [
  { id: "overview", label: "Overview", Icon: Truck },
  { id: "timeline", label: "Timeline & tracking", Icon: MapPin },
  { id: "custody", label: "Chain of custody", Icon: ShieldCheck },
  { id: "items", label: "Items & packages", Icon: Package },
];

export default function NhumeDeliveryDetailPage() {
  const params = useParams<{ deliveryId: string }>();
  const id = params?.deliveryId as string | undefined;
  const [tab, setTab] = useState<TabId>("overview");

  const { data: delivery, isPending, isError } = useNhumeDelivery(id);

  return (
    <AppLayout>
      <PageShell
        title="Delivery Detail"
        subtitle="Live state, lifecycle controls, tracking and chain of custody"
        icon={<Truck className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link href="/nhume/deliveries" className="inline-flex items-center gap-1 text-sm text-gray-600 hover:text-gray-900">
            <ArrowLeft className="h-4 w-4" />
            Back to deliveries
          </Link>
        </div>

        {isPending && (
          <div className="rounded-2xl border border-gray-200 bg-white p-10 text-center text-gray-500">
            <Loader2 className="inline-block h-5 w-5 animate-spin text-teal-500 mr-2" />
            Loading delivery…
          </div>
        )}

        {isError && (
          <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
            Delivery could not be loaded.
          </div>
        )}

        {delivery && id && (
          <>
            <HeaderCard delivery={delivery} />

            <div className="mt-6 flex gap-2 border-b border-gray-200">
              {TABS.map(({ id: tid, label, Icon }) => (
                <button
                  key={tid}
                  onClick={() => setTab(tid)}
                  className={
                    "inline-flex items-center gap-2 px-3 py-2 text-sm border-b-2 -mb-px " +
                    (tab === tid
                      ? "border-teal-600 text-teal-700 font-medium"
                      : "border-transparent text-gray-500 hover:text-gray-800")
                  }
                >
                  <Icon className="h-4 w-4" />
                  {label}
                </button>
              ))}
            </div>

            <div className="mt-6">
              {tab === "overview" && <OverviewTab id={id} delivery={delivery} />}
              {tab === "timeline" && <TimelineTab id={id} delivery={delivery as Record<string, unknown>} />}
              {tab === "custody" && <CustodyTab id={id} />}
              {tab === "items" && <ItemsTab delivery={delivery} />}
            </div>
          </>
        )}
      </PageShell>
    </AppLayout>
  );
}

// ---- Header --------------------------------------------------------------

function HeaderCard({ delivery }: { delivery: Record<string, unknown> }) {
  const reference = String(delivery.reference ?? delivery.delivery_id ?? "");
  const status = String(delivery.status ?? "");
  const priority = String(delivery.priority ?? "STANDARD");
  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-gray-900">{reference}</h2>
          <p className="text-sm text-gray-600 mt-1">
            {String(delivery.delivery_type ?? "—")} · source {String(delivery.request_source ?? "—")}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <NhumePriorityChip priority={priority} />
          <NhumeStatusChip status={status} />
        </div>
      </div>
      <dl className="mt-4 grid grid-cols-1 md:grid-cols-3 gap-x-6 gap-y-2 text-sm">
        <Pair label="Origin" value={String(delivery.origin_label ?? "—")} />
        <Pair label="Destination" value={String(delivery.destination_label ?? "—")} />
        <Pair label="Recipient" value={String(delivery.recipient_label ?? "—")} />
        <Pair label="SLA due" value={delivery.sla_due_at ? new Date(String(delivery.sla_due_at)).toLocaleString() : "—"} />
        <Pair label="Created" value={delivery.created_at ? new Date(String(delivery.created_at)).toLocaleString() : "—"} />
        <Pair label="Updated" value={delivery.updated_at ? new Date(String(delivery.updated_at)).toLocaleString() : "—"} />
      </dl>
    </div>
  );
}

function Pair({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-gray-500">{label}</dt>
      <dd className="font-medium text-gray-900">{value}</dd>
    </div>
  );
}

// ---- Overview tab --------------------------------------------------------

function OverviewTab({ id, delivery }: { id: string; delivery: Record<string, unknown> }) {
  const status = String(delivery.status ?? "");
  const submit = useSubmitDelivery(id);
  const approve = useApproveDelivery(id);
  const reject = useRejectDelivery(id);
  const cancel = useCancelDelivery(id);
  const assign = useAssignDelivery(id);
  const accept = useAcceptDelivery(id);
  const pickup = usePickupDelivery(id);
  const start = useStartDeliveryTransit(id);
  const fail = useFailDelivery(id);
  const retDel = useReturnDelivery(id);
  const proof = useRecordProof(id);

  const [reason, setReason] = useState("");
  const [courierId, setCourierId] = useState("");
  const [assetId, setAssetId] = useState("");
  const [otp, setOtp] = useState("");

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <div className="rounded-2xl border border-gray-200 bg-white p-5">
        <h3 className="font-semibold text-gray-900 mb-3">Lifecycle controls</h3>
        <p className="text-xs text-gray-500 mb-4">
          The backend enforces valid transitions and Trust-Layer policy. Buttons are visible to all
          actors; rejection from the server will be surfaced inline.
        </p>
        <div className="flex flex-wrap gap-2">
          <Btn label="Submit" disabled={!["DRAFT"].includes(status) || submit.isPending} onClick={() => submit.mutate({})} icon={<Send className="h-4 w-4" />} />
          <Btn label="Approve" disabled={!["SUBMITTED", "AWAITING_APPROVAL", "VALIDATION_REQUIRED"].includes(status) || approve.isPending} onClick={() => approve.mutate({})} icon={<CheckCircle2 className="h-4 w-4" />} variant="success" />
          <Btn label="Reject" disabled={!["SUBMITTED", "AWAITING_APPROVAL", "VALIDATION_REQUIRED"].includes(status) || reject.isPending} onClick={() => reject.mutate({ reason: reason || "rejected" })} icon={<XCircle className="h-4 w-4" />} variant="danger" />
          <Btn label="Cancel" disabled={["DELIVERED", "CANCELLED", "RETURNED", "CLOSED", "FAILED"].includes(status) || cancel.isPending} onClick={() => cancel.mutate({ reason: reason || "cancelled" })} variant="danger" />
          <Btn label="Accept" disabled={!["ASSIGNED"].includes(status) || accept.isPending} onClick={() => accept.mutate({})} />
          <Btn label="Start pickup" disabled={!["ACCEPTED", "ASSIGNED"].includes(status) || pickup.isPending} onClick={() => pickup.mutate({})} />
          <Btn label="Start transit" disabled={!["PICKED_UP"].includes(status) || start.isPending} onClick={() => start.mutate({})} />
          <Btn label="Mark failed" disabled={["DELIVERED", "CANCELLED", "RETURNED", "CLOSED"].includes(status) || fail.isPending} onClick={() => fail.mutate({ reason: reason || "failed" })} variant="danger" />
          <Btn label="Return" disabled={!["FAILED", "ATTEMPTED"].includes(status) || retDel.isPending} onClick={() => retDel.mutate({})} />
        </div>

        <label className="mt-4 block text-xs text-gray-600">Reason / notes for next action</label>
        <input
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="Optional — sent with the next reject / cancel / fail action"
          className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm"
        />
      </div>

      <div className="rounded-2xl border border-gray-200 bg-white p-5">
        <h3 className="font-semibold text-gray-900 mb-3">Assign courier / asset</h3>
        <div className="grid grid-cols-1 gap-3">
          <label className="block text-xs text-gray-600">
            Courier ID
            <input value={courierId} onChange={(e) => setCourierId(e.target.value)} placeholder="UUID from /nhume/couriers" className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm" />
          </label>
          <label className="block text-xs text-gray-600">
            Asset ID (optional)
            <input value={assetId} onChange={(e) => setAssetId(e.target.value)} placeholder="UUID from /nhume/fleet" className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm" />
          </label>
          <Btn
            label="Assign"
            disabled={!courierId || assign.isPending}
            onClick={() => assign.mutate({ courier_id: courierId, asset_id: assetId || undefined, reason: reason || undefined })}
          />
        </div>

        <hr className="my-5 border-gray-100" />

        <h3 className="font-semibold text-gray-900 mb-3">Capture proof of delivery</h3>
        <div className="grid grid-cols-1 gap-3">
          <label className="block text-xs text-gray-600">
            OTP / evidence reference
            <input value={otp} onChange={(e) => setOtp(e.target.value)} placeholder="e.g. 1234 or photo:abc.jpg" className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm" />
          </label>
          <Btn
            label="Capture OTP proof & mark delivered"
            disabled={!otp || proof.isPending}
            onClick={() => proof.mutate({ method: "OTP", otp_code: otp })}
            variant="success"
          />
        </div>

        {(approve.isError || reject.isError || cancel.isError || assign.isError || pickup.isError || start.isError || proof.isError) && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 p-2 text-xs text-rose-800">
            One of the last actions was rejected — verify status, role and Trust-Layer headers.
          </div>
        )}
      </div>
    </div>
  );
}

function Btn({
  label,
  onClick,
  disabled,
  icon,
  variant = "default",
}: {
  label: string;
  onClick: () => void;
  disabled?: boolean;
  icon?: React.ReactNode;
  variant?: "default" | "success" | "danger";
}) {
  const base = "inline-flex items-center gap-1.5 rounded-xl px-3 py-1.5 text-sm font-medium disabled:opacity-50";
  const theme =
    variant === "success"
      ? "bg-emerald-600 text-white hover:bg-emerald-700"
      : variant === "danger"
        ? "bg-rose-600 text-white hover:bg-rose-700"
        : "bg-gray-900 text-white hover:bg-gray-700";
  return (
    <button onClick={onClick} disabled={disabled} className={`${base} ${theme}`}>
      {icon}
      {label}
    </button>
  );
}

// ---- Timeline + tracking tab --------------------------------------------

function TimelineTab({ id, delivery }: { id: string; delivery: Record<string, unknown> }) {
  const { data: timeline } = useNhumeTimeline(id);
  const { data: tracking } = useNhumeTracking(id);
  const events = timeline?.data ?? [];
  const points = tracking?.data ?? [];

  return (
    <div className="space-y-4">
      <DeliveryTrackMapPanel delivery={delivery} trackingPoints={points} />
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <div className="rounded-2xl border border-gray-200 bg-white">
        <div className="border-b border-gray-100 px-5 py-4">
          <h3 className="font-semibold text-gray-900">Status timeline</h3>
        </div>
        {events.length === 0 ? (
          <div className="px-5 py-8 text-sm text-gray-500 text-center">No status events yet.</div>
        ) : (
          <ol className="divide-y divide-gray-100">
            {events.map((event, idx) => {
              const ev = event as Record<string, unknown>;
              return (
                <li key={idx} className="px-5 py-3">
                  <div className="flex items-center justify-between">
                    <NhumeStatusChip status={String(ev.status ?? "")} />
                    <span className="text-xs text-gray-500">
                      {ev.recorded_at ? new Date(String(ev.recorded_at)).toLocaleString() : ""}
                    </span>
                  </div>
                  {ev.notes ? <p className="mt-1 text-xs text-gray-600">{String(ev.notes)}</p> : null}
                </li>
              );
            })}
          </ol>
        )}
      </div>
      <div className="rounded-2xl border border-gray-200 bg-white">
        <div className="border-b border-gray-100 px-5 py-4">
          <h3 className="font-semibold text-gray-900">Location updates</h3>
          <p className="text-xs text-gray-500 mt-1">
            Privacy-safe coordinates from courier devices, telematics or autonomous-platform
            telemetry. Polling pauses once the delivery enters a terminal state.
          </p>
        </div>
        {points.length === 0 ? (
          <div className="px-5 py-8 text-sm text-gray-500 text-center">No location updates yet.</div>
        ) : (
          <ol className="divide-y divide-gray-100">
            {points.slice(-20).reverse().map((pt, idx) => {
              const p = pt as Record<string, unknown>;
              return (
                <li key={idx} className="px-5 py-3 text-sm flex justify-between">
                  <span className="text-gray-800">
                    {String(p.latitude ?? "")}, {String(p.longitude ?? "")}
                  </span>
                  <span className="text-xs text-gray-500">
                    {p.recorded_at ? new Date(String(p.recorded_at)).toLocaleString() : ""}
                  </span>
                </li>
              );
            })}
          </ol>
        )}
      </div>
      </div>
    </div>
  );
}

// ---- Custody tab ---------------------------------------------------------

function CustodyTab({ id }: { id: string }) {
  const { data } = useNhumeCustody(id);
  const events = data?.data ?? [];
  const mut = useRecordCustody(id);

  const [kind, setKind] = useState("PREPARED");
  const [holder, setHolder] = useState("");
  const [seal, setSeal] = useState("");
  const [temp, setTemp] = useState<string>("");
  const [exception, setException] = useState(false);
  const [notes, setNotes] = useState("");

  function submit(e: React.FormEvent) {
    e.preventDefault();
    mut.mutate({
      event_kind: kind,
      custody_holder_ref: holder || undefined,
      seal_id: seal || undefined,
      temperature_c: temp ? Number(temp) : undefined,
      exception,
      notes: notes || undefined,
    });
    setNotes("");
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <div className="rounded-2xl border border-gray-200 bg-white">
        <div className="border-b border-gray-100 px-5 py-4">
          <h3 className="font-semibold text-gray-900">Chain of custody</h3>
        </div>
        {events.length === 0 ? (
          <div className="px-5 py-8 text-sm text-gray-500 text-center">No custody events yet.</div>
        ) : (
          <ol className="divide-y divide-gray-100">
            {events.map((event, idx) => {
              const ev = event as Record<string, unknown>;
              return (
                <li key={idx} className="px-5 py-3">
                  <div className="flex items-center justify-between">
                    <span className="font-medium text-gray-900">{String(ev.event_kind ?? "")}</span>
                    <span className="text-xs text-gray-500">
                      {ev.recorded_at ? new Date(String(ev.recorded_at)).toLocaleString() : ""}
                    </span>
                  </div>
                  <div className="mt-1 text-xs text-gray-600">
                    {ev.seal_id ? <>Seal {String(ev.seal_id)} · </> : null}
                    {ev.temperature_c !== undefined && ev.temperature_c !== null ? <>{String(ev.temperature_c)}°C · </> : null}
                    {ev.exception ? <span className="text-rose-700 font-medium">EXCEPTION</span> : null}
                  </div>
                  {ev.notes ? <p className="mt-1 text-xs text-gray-700">{String(ev.notes)}</p> : null}
                </li>
              );
            })}
          </ol>
        )}
      </div>
      <form onSubmit={submit} className="rounded-2xl border border-gray-200 bg-white p-5 space-y-3">
        <h3 className="font-semibold text-gray-900">Record custody event</h3>
        <label className="block text-xs text-gray-600">
          Event
          <select value={kind} onChange={(e) => setKind(e.target.value)} className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm">
            {[
              "PREPARED", "PACKED", "SEALED", "RELEASED", "PICKED_UP", "TRANSFERRED",
              "ARRIVED_HUB", "DEPARTED_HUB", "DELIVERED", "RETURNED", "DAMAGED",
              "LOST", "TEMP_BREACH", "DISPUTED", "RECONCILED",
            ].map((k) => <option key={k} value={k}>{k}</option>)}
          </select>
        </label>
        <label className="block text-xs text-gray-600">
          Holder ref (provider / facility / courier ID)
          <input value={holder} onChange={(e) => setHolder(e.target.value)} className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm" />
        </label>
        <label className="block text-xs text-gray-600">
          Seal ID
          <input value={seal} onChange={(e) => setSeal(e.target.value)} className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm" />
        </label>
        <label className="block text-xs text-gray-600">
          Temperature (°C)
          <input type="number" step="0.1" value={temp} onChange={(e) => setTemp(e.target.value)} className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm" />
        </label>
        <label className="inline-flex items-center gap-2 text-sm">
          <input type="checkbox" checked={exception} onChange={(e) => setException(e.target.checked)} />
          Mark as exception (cold-chain breach, damage, lost, etc.)
        </label>
        <label className="block text-xs text-gray-600">
          Notes
          <textarea value={notes} onChange={(e) => setNotes(e.target.value)} className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm" rows={3} />
        </label>
        <button type="submit" disabled={mut.isPending} className="inline-flex items-center gap-2 rounded-xl bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50">
          {mut.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
          Record event
        </button>
      </form>
    </div>
  );
}

// ---- Items tab -----------------------------------------------------------

function ItemsTab({ delivery }: { delivery: Record<string, unknown> }) {
  const items = (delivery.items as Array<Record<string, unknown>> | undefined) ?? [];
  const packages = (delivery.packages as Array<Record<string, unknown>> | undefined) ?? [];
  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <div className="rounded-2xl border border-gray-200 bg-white">
        <div className="border-b border-gray-100 px-5 py-4">
          <h3 className="font-semibold text-gray-900">Items</h3>
        </div>
        {items.length === 0 ? (
          <div className="px-5 py-8 text-sm text-gray-500 text-center">No items.</div>
        ) : (
          <ul className="divide-y divide-gray-100">
            {items.map((it, idx) => (
              <li key={idx} className="px-5 py-3 text-sm">
                <div className="font-medium text-gray-900">{String(it.description ?? "—")}</div>
                <div className="text-xs text-gray-500 mt-1">
                  Qty {String(it.quantity ?? 1)} {String(it.unit ?? "")} ·
                  {it.cold_chain ? " cold-chain" : ""}{it.controlled ? " · controlled" : ""}{it.hazardous ? " · hazardous" : ""}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="rounded-2xl border border-gray-200 bg-white">
        <div className="border-b border-gray-100 px-5 py-4">
          <h3 className="font-semibold text-gray-900">Packages</h3>
        </div>
        {packages.length === 0 ? (
          <div className="px-5 py-8 text-sm text-gray-500 text-center">No packages declared.</div>
        ) : (
          <ul className="divide-y divide-gray-100">
            {packages.map((pkg, idx) => (
              <li key={idx} className="px-5 py-3 text-sm">
                <div className="font-medium text-gray-900">{String(pkg.package_kind ?? "PACKAGE")}</div>
                <div className="text-xs text-gray-500 mt-1">
                  {pkg.seal_id ? <>Seal {String(pkg.seal_id)} · </> : null}
                  {pkg.weight_kg ? <>{String(pkg.weight_kg)}kg · </> : null}
                  {pkg.temperature_target_min_c !== undefined && pkg.temperature_target_max_c !== undefined
                    ? `${pkg.temperature_target_min_c}–${pkg.temperature_target_max_c}°C`
                    : null}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
