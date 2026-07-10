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
import { ArrowLeft, Loader2, Send, CheckCircle2, XCircle, Truck, MapPin, ShieldCheck, Package, ExternalLink, Link2, Siren } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NhumeStatusChip, NhumePriorityChip } from "@/components/nhume/NhumeStatusChip";
import { SignOffPanel } from "@/components/nhume/SignOffPanel";
import { readDeliveryLinks, readWriteBackOutcomes } from "@/lib/nhume/cargo-profiles";

/** Transport modes offered at assignment — bikes and bicycles included. */
const ASSIGN_MODES = [
  "MOTORCYCLE", "BICYCLE", "CAR", "VAN", "TRUCK", "AMBULANCE",
  "WALKING_COURIER", "BOAT", "DRONE", "COLD_CHAIN_VEHICLE",
  "SPECIMEN_TRANSPORT_VEHICLE", "BLOOD_TRANSPORT_VEHICLE", "PARTNER_COURIER",
];
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
          <Link href="/nhume/deliveries" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" />
            Back to deliveries
          </Link>
        </div>

        {isPending && (
          <div className="rounded-2xl border border-border bg-card p-10 text-center text-muted-foreground">
            <Loader2 className="inline-block h-5 w-5 animate-spin text-teal-500 mr-2" />
            Loading delivery…
          </div>
        )}

        {isError && (
          <div className="rounded-2xl border border-danger/28 bg-danger-soft p-4 text-sm text-rose-800">
            Delivery could not be loaded.
          </div>
        )}

        {delivery && id && (
          <>
            <HeaderCard delivery={delivery} />

            <div className="mt-6 flex gap-2 border-b border-border">
              {TABS.map(({ id: tid, label, Icon }) => (
                <button
                  key={tid}
                  onClick={() => setTab(tid)}
                  className={
                    "inline-flex items-center gap-2 px-3 py-2 text-sm border-b-2 -mb-px " +
                    (tab === tid
                      ? "border-teal-600 text-teal-700 font-medium"
                      : "border-transparent text-muted-foreground hover:text-foreground")
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
    <div className="rounded-2xl border border-border bg-card p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-foreground">{reference}</h2>
          <p className="text-sm text-muted-foreground mt-1">
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
      <dt className="text-xs uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className="font-medium text-foreground">{value}</dd>
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

  const [reason, setReason] = useState("");
  const [courierId, setCourierId] = useState("");
  const [assetId, setAssetId] = useState("");
  const [mode, setMode] = useState("MOTORCYCLE");

  const canCollect = ["ASSIGNED", "ACCEPTED", "EN_ROUTE_TO_PICKUP", "AT_PICKUP"].includes(status);
  const canDropOff = ["PICKED_UP", "IN_TRANSIT", "AT_DESTINATION", "DELIVERY_ATTEMPTED", "ARRIVED"].includes(status);

  return (
    <div className="space-y-4">
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <div className="rounded-2xl border border-border bg-card p-5">
        <h3 className="font-semibold text-foreground mb-3">Lifecycle controls</h3>
        <p className="text-xs text-muted-foreground mb-4">
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

        <label className="mt-4 block text-xs text-muted-foreground">Reason / notes for next action</label>
        <input
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="Optional — sent with the next reject / cancel / fail action"
          className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm"
        />
      </div>

      <div className="rounded-2xl border border-border bg-card p-5">
        <h3 className="font-semibold text-foreground mb-3">Assign courier / asset &amp; mode</h3>
        <div className="grid grid-cols-1 gap-3">
          <label className="block text-xs text-muted-foreground">
            Courier ID
            <input value={courierId} onChange={(e) => setCourierId(e.target.value)} placeholder="UUID from /nhume/couriers" className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm" />
          </label>
          <label className="block text-xs text-muted-foreground">
            Asset ID (optional)
            <input value={assetId} onChange={(e) => setAssetId(e.target.value)} placeholder="UUID from /nhume/fleet" className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm" />
          </label>
          <label className="block text-xs text-muted-foreground">
            Mode of delivery
            <select value={mode} onChange={(e) => setMode(e.target.value)} className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm">
              {ASSIGN_MODES.map((m) => <option key={m} value={m}>{m.replace(/_/g, " ").toLowerCase()}</option>)}
            </select>
          </label>
          <Btn
            label="Assign"
            disabled={!courierId || assign.isPending}
            onClick={() => assign.mutate({ courier_id: courierId, asset_id: assetId || undefined, mode, reason: reason || undefined })}
          />
        </div>

        <hr className="my-5 border-border" />

        <h3 className="font-semibold text-foreground mb-1">Collection &amp; drop-off sign-off</h3>
        <p className="mb-3 text-xs text-muted-foreground">
          Capture who released the cargo at collection and who received it at drop-off. The drop-off
          sign-off marks the mission delivered. Both are recorded as auditable proof events.
        </p>
        {canCollect ? <SignOffPanel deliveryId={id} stage="PICKUP" /> : null}
        {canCollect && canDropOff ? <div className="h-2" /> : null}
        {canDropOff ? <SignOffPanel deliveryId={id} stage="DELIVERY" /> : null}
        {!canCollect && !canDropOff ? (
          <p className="rounded-lg border border-border bg-background px-3 py-2 text-xs text-muted-foreground">
            Sign-off becomes available once the mission is assigned and moving.
          </p>
        ) : null}

        {(approve.isError || reject.isError || cancel.isError || assign.isError || pickup.isError || start.isError) && (
          <div className="mt-3 rounded-lg border border-danger/28 bg-danger-soft p-2 text-xs text-rose-800">
            One of the last actions was rejected — verify status, role and Trust-Layer headers.
          </div>
        )}
      </div>
    </div>
      <LinkedReferencesPanel delivery={delivery} />
    </div>
  );
}

/**
 * Honest integration surface: this mission MOVES cargo whose truth lives in
 * another system (Dura stock, OROS lab order, MADI blood order, PCT referral,
 * Daidzai incident). We display the linked references captured at creation and
 * deep-link out. On drop-off sign-off nhume-service confirms the movement with
 * each owning service; those outcomes (including failures) render here.
 */
function LinkedReferencesPanel({ delivery }: { delivery: Record<string, unknown> }) {
  const links = readDeliveryLinks(delivery.metadata);
  const writeBacks = readWriteBackOutcomes(delivery.metadata);
  const clinical = delivery.clinical_context_ref ? String(delivery.clinical_context_ref) : "";
  const priority = String(delivery.priority ?? "").toUpperCase();
  const canEscalate = priority === "EMERGENCY" || priority === "URGENT";

  return (
    <div className="rounded-2xl border border-border bg-card p-5">
      <div className="mb-3 flex items-center gap-2">
        <Link2 className="h-4 w-4 text-teal-600" />
        <h3 className="font-semibold text-foreground">Linked records &amp; coordination</h3>
      </div>

      {links.length === 0 && !clinical ? (
        <p className="text-sm text-muted-foreground">
          No linked records. Missions moving lab samples, blood, stock or patients carry a reference to the
          owning system (OROS / MADI / Dura / PCT) captured at creation.
        </p>
      ) : (
        <ul className="grid grid-cols-1 gap-2 sm:grid-cols-2">
          {links.map((l) => (
            <li key={`${l.system}-${l.ref}`}>
              <Link href={l.href} className="flex items-center justify-between rounded-xl border border-border px-3 py-2 text-sm hover:border-teal-300 hover:bg-background">
                <span>
                  <span className="mr-2 rounded-full bg-primary-soft px-2 py-0.5 text-[11px] font-medium text-teal-700">{l.system}</span>
                  <span className="text-foreground">{l.label}</span>
                  <span className="ml-2 font-mono text-xs text-muted-foreground">{l.ref}</span>
                  {writeBacks[l.system] ? (
                    <span
                      title={writeBacks[l.system].detail}
                      className={`ml-2 rounded-full px-2 py-0.5 text-[11px] font-medium ${
                        writeBacks[l.system].status === "OK"
                          ? "bg-emerald-100 text-emerald-700"
                          : writeBacks[l.system].status === "FAILED"
                            ? "bg-rose-100 text-rose-700"
                            : "bg-amber-100 text-amber-700"
                      }`}
                    >
                      {writeBacks[l.system].status === "OK"
                        ? "Confirmed with owner"
                        : writeBacks[l.system].status === "FAILED"
                          ? "Confirmation failed"
                          : writeBacks[l.system].status === "SKIPPED_NO_TRANSITION"
                            ? "Manual step in owner"
                            : "No action needed"}
                    </span>
                  ) : null}
                </span>
                <ExternalLink className="h-3.5 w-3.5 text-muted-foreground" />
              </Link>
            </li>
          ))}
          {clinical && !links.some((l) => l.ref === clinical) ? (
            <li>
              <span className="flex items-center gap-2 rounded-xl border border-border px-3 py-2 text-sm">
                <span className="rounded-full bg-primary-soft px-2 py-0.5 text-[11px] font-medium text-teal-700">CLINICAL</span>
                <span className="font-mono text-xs text-muted-foreground">{clinical}</span>
              </span>
            </li>
          ) : null}
        </ul>
      )}

      <div className="mt-4 flex items-center justify-between gap-2 border-t border-border pt-4">
        <p className="text-xs text-muted-foreground">
          Mass-casualty or disaster-scale movement is coordinated in Daidzai, which owns emergency incidents.
        </p>
        <Link
          href={`/work/daidzai?source=nhume&delivery=${encodeURIComponent(String(delivery.delivery_id ?? ""))}&ref=${encodeURIComponent(String(delivery.reference_code ?? ""))}&priority=${encodeURIComponent(priority)}`}
          className={`inline-flex shrink-0 items-center gap-2 rounded-xl px-3 py-2 text-sm font-medium ${
            canEscalate ? "bg-rose-600 text-white hover:bg-rose-700" : "border border-border text-foreground hover:bg-background"
          }`}
        >
          <Siren className="h-4 w-4" />
          Escalate to Daidzai
        </Link>
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
        : "bg-neutral-900 text-white hover:bg-gray-700";
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
      <div className="rounded-2xl border border-border bg-card">
        <div className="border-b border-border px-5 py-4">
          <h3 className="font-semibold text-foreground">Status timeline</h3>
        </div>
        {events.length === 0 ? (
          <div className="px-5 py-8 text-sm text-muted-foreground text-center">No status events yet.</div>
        ) : (
          <ol className="divide-y divide-gray-100">
            {events.map((event, idx) => {
              const ev = event as Record<string, unknown>;
              return (
                <li key={idx} className="px-5 py-3">
                  <div className="flex items-center justify-between">
                    <NhumeStatusChip status={String(ev.status ?? "")} />
                    <span className="text-xs text-muted-foreground">
                      {ev.recorded_at ? new Date(String(ev.recorded_at)).toLocaleString() : ""}
                    </span>
                  </div>
                  {ev.notes ? <p className="mt-1 text-xs text-muted-foreground">{String(ev.notes)}</p> : null}
                </li>
              );
            })}
          </ol>
        )}
      </div>
      <div className="rounded-2xl border border-border bg-card">
        <div className="border-b border-border px-5 py-4">
          <h3 className="font-semibold text-foreground">Location updates</h3>
          <p className="text-xs text-muted-foreground mt-1">
            Privacy-safe coordinates from courier devices, telematics or autonomous-platform
            telemetry. Polling pauses once the delivery enters a terminal state.
          </p>
        </div>
        {points.length === 0 ? (
          <div className="px-5 py-8 text-sm text-muted-foreground text-center">No location updates yet.</div>
        ) : (
          <ol className="divide-y divide-gray-100">
            {points.slice(-20).reverse().map((pt, idx) => {
              const p = pt as Record<string, unknown>;
              return (
                <li key={idx} className="px-5 py-3 text-sm flex justify-between">
                  <span className="text-foreground">
                    {String(p.latitude ?? "")}, {String(p.longitude ?? "")}
                  </span>
                  <span className="text-xs text-muted-foreground">
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
      <div className="rounded-2xl border border-border bg-card">
        <div className="border-b border-border px-5 py-4">
          <h3 className="font-semibold text-foreground">Chain of custody</h3>
        </div>
        {events.length === 0 ? (
          <div className="px-5 py-8 text-sm text-muted-foreground text-center">No custody events yet.</div>
        ) : (
          <ol className="divide-y divide-gray-100">
            {events.map((event, idx) => {
              const ev = event as Record<string, unknown>;
              return (
                <li key={idx} className="px-5 py-3">
                  <div className="flex items-center justify-between">
                    <span className="font-medium text-foreground">{String(ev.event_kind ?? "")}</span>
                    <span className="text-xs text-muted-foreground">
                      {ev.recorded_at ? new Date(String(ev.recorded_at)).toLocaleString() : ""}
                    </span>
                  </div>
                  <div className="mt-1 text-xs text-muted-foreground">
                    {ev.seal_id ? <>Seal {String(ev.seal_id)} · </> : null}
                    {ev.temperature_c !== undefined && ev.temperature_c !== null ? <>{String(ev.temperature_c)}°C · </> : null}
                    {ev.exception ? <span className="text-danger font-medium">EXCEPTION</span> : null}
                  </div>
                  {ev.notes ? <p className="mt-1 text-xs text-foreground">{String(ev.notes)}</p> : null}
                </li>
              );
            })}
          </ol>
        )}
      </div>
      <form onSubmit={submit} className="rounded-2xl border border-border bg-card p-5 space-y-3">
        <h3 className="font-semibold text-foreground">Record custody event</h3>
        <label className="block text-xs text-muted-foreground">
          Event
          <select value={kind} onChange={(e) => setKind(e.target.value)} className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm">
            {[
              "PREPARED", "PACKED", "SEALED", "RELEASED", "PICKED_UP", "TRANSFERRED",
              "ARRIVED_HUB", "DEPARTED_HUB", "DELIVERED", "RETURNED", "DAMAGED",
              "LOST", "TEMP_BREACH", "DISPUTED", "RECONCILED",
            ].map((k) => <option key={k} value={k}>{k}</option>)}
          </select>
        </label>
        <label className="block text-xs text-muted-foreground">
          Holder ref (provider / facility / courier ID)
          <input value={holder} onChange={(e) => setHolder(e.target.value)} className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm" />
        </label>
        <label className="block text-xs text-muted-foreground">
          Seal ID
          <input value={seal} onChange={(e) => setSeal(e.target.value)} className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm" />
        </label>
        <label className="block text-xs text-muted-foreground">
          Temperature (°C)
          <input type="number" step="0.1" value={temp} onChange={(e) => setTemp(e.target.value)} className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm" />
        </label>
        <label className="inline-flex items-center gap-2 text-sm">
          <input type="checkbox" checked={exception} onChange={(e) => setException(e.target.checked)} />
          Mark as exception (cold-chain breach, damage, lost, etc.)
        </label>
        <label className="block text-xs text-muted-foreground">
          Notes
          <textarea value={notes} onChange={(e) => setNotes(e.target.value)} className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm" rows={3} />
        </label>
        <button type="submit" disabled={mut.isPending} className="inline-flex items-center gap-2 rounded-xl bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary disabled:opacity-50">
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
      <div className="rounded-2xl border border-border bg-card">
        <div className="border-b border-border px-5 py-4">
          <h3 className="font-semibold text-foreground">Items</h3>
        </div>
        {items.length === 0 ? (
          <div className="px-5 py-8 text-sm text-muted-foreground text-center">No items.</div>
        ) : (
          <ul className="divide-y divide-gray-100">
            {items.map((it, idx) => (
              <li key={idx} className="px-5 py-3 text-sm">
                <div className="font-medium text-foreground">{String(it.description ?? "—")}</div>
                <div className="text-xs text-muted-foreground mt-1">
                  Qty {String(it.quantity ?? 1)} {String(it.unit ?? "")} ·
                  {it.cold_chain ? " cold-chain" : ""}{it.controlled ? " · controlled" : ""}{it.hazardous ? " · hazardous" : ""}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="rounded-2xl border border-border bg-card">
        <div className="border-b border-border px-5 py-4">
          <h3 className="font-semibold text-foreground">Packages</h3>
        </div>
        {packages.length === 0 ? (
          <div className="px-5 py-8 text-sm text-muted-foreground text-center">No packages declared.</div>
        ) : (
          <ul className="divide-y divide-gray-100">
            {packages.map((pkg, idx) => (
              <li key={idx} className="px-5 py-3 text-sm">
                <div className="font-medium text-foreground">{String(pkg.package_kind ?? "PACKAGE")}</div>
                <div className="text-xs text-muted-foreground mt-1">
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
