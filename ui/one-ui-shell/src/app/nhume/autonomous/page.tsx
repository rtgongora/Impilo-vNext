"use client";

/**
 * Autonomous Delivery Integration
 *
 * Operator surface for drones, autonomous ground vehicles, robots and smart
 * locker networks. Each provider is exposed through an integration adapter
 * registered in the backend; this page lists missions, their status and lets
 * authorised operators create new ones.
 */

import { Plane, Loader2, Plus, X } from "lucide-react";
import { useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useNhumeMissions, useNhumeIntegrations, useCreateMission, useCancelMission } from "@/hooks/useNhume";

const MISSION_TONE: Record<string, string> = {
  DRAFT: "bg-gray-100 text-gray-700 border-gray-200",
  SUBMITTED: "bg-impilo-50 text-impilo-700 border-impilo-200",
  ACCEPTED: "bg-indigo-50 text-indigo-700 border-indigo-200",
  REJECTED: "bg-rose-50 text-rose-700 border-rose-200",
  AWAITING_CLEARANCE: "bg-amber-50 text-amber-800 border-amber-200",
  SCHEDULED: "bg-sky-50 text-sky-700 border-sky-200",
  LAUNCH_READY: "bg-violet-50 text-violet-700 border-violet-200",
  LAUNCHED: "bg-violet-50 text-violet-700 border-violet-200",
  EN_ROUTE: "bg-violet-50 text-violet-700 border-violet-200",
  HOLDING: "bg-amber-50 text-amber-800 border-amber-200",
  ARRIVED: "bg-teal-50 text-teal-700 border-teal-200",
  DELIVERED: "bg-emerald-50 text-emerald-700 border-emerald-200",
  RETURNING: "bg-sky-50 text-sky-700 border-sky-200",
  RETURNED: "bg-orange-50 text-orange-700 border-orange-200",
  FAILED: "bg-rose-50 text-rose-700 border-rose-200",
  CANCELLED: "bg-gray-100 text-gray-700 border-gray-200",
  EMERGENCY_LANDING: "bg-rose-50 text-rose-700 border-rose-200",
  MANUAL_INTERVENTION: "bg-amber-50 text-amber-800 border-amber-200",
  COMPLETED: "bg-emerald-50 text-emerald-700 border-emerald-200",
};

export default function NhumeAutonomousPage() {
  const missions = useNhumeMissions();
  const integrations = useNhumeIntegrations();
  const createMission = useCreateMission();
  const [showCreate, setShowCreate] = useState(false);

  return (
    <AppLayout>
      <PageShell
        title="Autonomous Delivery"
        subtitle="Drone, robot, autonomous vehicle and smart-locker missions"
        icon={<Plane className="h-6 w-6" />}
      >
        <div className="rounded-2xl border border-amber-100 bg-amber-50 p-4 text-sm text-amber-900 mb-4">
          <strong>Simulation note:</strong> providers flagged with simulation mode (e.g.
          <code> sim-drone-1</code>, <code>sim-robot-1</code>) execute deterministic state
          transitions for development and demos only. Real platforms are wired through their own
          adapters and HTTPS webhooks.
        </div>

        <div className="flex items-center justify-between mb-4">
          <h3 className="font-semibold text-gray-900">Missions</h3>
          <button
            onClick={() => setShowCreate((v) => !v)}
            className="inline-flex items-center gap-2 rounded-xl bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700"
          >
            {showCreate ? <X className="h-4 w-4" /> : <Plus className="h-4 w-4" />}
            {showCreate ? "Close" : "New mission"}
          </button>
        </div>

        {showCreate && (
          <MissionForm
            providers={(integrations.data?.data ?? []) as Array<Record<string, unknown>>}
            isPending={createMission.isPending}
            onCancel={() => setShowCreate(false)}
            onSubmit={(body) => createMission.mutate(body, { onSuccess: () => setShowCreate(false) })}
          />
        )}

        {missions.isPending && (
          <div className="rounded-2xl border border-gray-200 bg-white p-10 text-center text-gray-500">
            <Loader2 className="inline-block h-5 w-5 animate-spin text-teal-500 mr-2" /> Loading missions…
          </div>
        )}

        {missions.data && (
          <div className="rounded-2xl border border-gray-200 bg-white overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
                <tr>
                  <th className="px-4 py-3 text-left">Mission</th>
                  <th className="px-4 py-3 text-left">Provider</th>
                  <th className="px-4 py-3 text-left">Status</th>
                  <th className="px-4 py-3 text-left">Last update</th>
                  <th className="px-4 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {(missions.data.data ?? []).map((m, idx) => <MissionRow key={idx} m={m as Record<string, unknown>} />)}
                {missions.data.data?.length === 0 && (
                  <tr><td colSpan={5} className="px-4 py-8 text-center text-sm text-gray-500">No autonomous missions yet.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}

function MissionRow({ m }: { m: Record<string, unknown> }) {
  const id = String(m.mission_id ?? m.id ?? "");
  const cancel = useCancelMission(id);
  const status = String(m.status ?? "DRAFT").toUpperCase();
  return (
    <tr>
      <td className="px-4 py-3 font-medium text-gray-900">{String(m.mission_reference ?? id)}</td>
      <td className="px-4 py-3 text-gray-700">{String(m.provider_code ?? "—")}</td>
      <td className="px-4 py-3">
        <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[11px] ${MISSION_TONE[status] ?? MISSION_TONE.DRAFT}`}>{status}</span>
      </td>
      <td className="px-4 py-3 text-xs text-gray-500">{m.updated_at ? new Date(String(m.updated_at)).toLocaleString() : ""}</td>
      <td className="px-4 py-3 text-right">
        <button
          disabled={["DELIVERED", "CANCELLED", "FAILED", "RETURNED", "COMPLETED"].includes(status) || cancel.isPending}
          onClick={() => cancel.mutate({ reason: "operator cancel" })}
          className="rounded-xl border border-gray-300 px-3 py-1 text-xs text-gray-700 hover:bg-gray-50 disabled:opacity-50"
        >
          Cancel
        </button>
      </td>
    </tr>
  );
}

function MissionForm({
  providers,
  isPending,
  onSubmit,
  onCancel,
}: {
  providers: Array<Record<string, unknown>>;
  isPending: boolean;
  onSubmit: (body: Record<string, unknown>) => void;
  onCancel: () => void;
}) {
  const [providerCode, setProviderCode] = useState(String(providers[0]?.provider_code ?? "sim-drone-1"));
  const [deliveryId, setDeliveryId] = useState("");
  const [payloadKg, setPayloadKg] = useState("1.0");
  const [launchSite, setLaunchSite] = useState("");
  const [landingSite, setLandingSite] = useState("");

  function submit(e: React.FormEvent) {
    e.preventDefault();
    onSubmit({
      provider_code: providerCode,
      delivery_id: deliveryId || undefined,
      payload_weight_kg: Number(payloadKg) || undefined,
      launch_site_label: launchSite || undefined,
      landing_site_label: landingSite || undefined,
    });
  }

  return (
    <form onSubmit={submit} className="rounded-2xl border border-gray-200 bg-white p-5 mb-4 space-y-3">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        <label className="block text-xs text-gray-600">
          Provider
          <select value={providerCode} onChange={(e) => setProviderCode(e.target.value)} className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm">
            {(providers.length === 0 ? [{ provider_code: "sim-drone-1", display_name: "Simulated drone" }] : providers).map((p, idx) => (
              <option key={idx} value={String(p.provider_code ?? "")}>
                {String(p.display_name ?? p.provider_code)} ({String(p.provider_kind ?? "")})
              </option>
            ))}
          </select>
        </label>
        <label className="block text-xs text-gray-600">
          Linked delivery ID (optional)
          <input value={deliveryId} onChange={(e) => setDeliveryId(e.target.value)} className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm" placeholder="UUID" />
        </label>
        <label className="block text-xs text-gray-600">
          Payload (kg)
          <input value={payloadKg} onChange={(e) => setPayloadKg(e.target.value)} type="number" step="0.1" className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm" />
        </label>
        <label className="block text-xs text-gray-600">
          Launch site label
          <input value={launchSite} onChange={(e) => setLaunchSite(e.target.value)} className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm" />
        </label>
        <label className="block text-xs text-gray-600">
          Landing site label
          <input value={landingSite} onChange={(e) => setLandingSite(e.target.value)} className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm" />
        </label>
      </div>
      <div className="flex justify-end gap-2">
        <button type="button" onClick={onCancel} className="rounded-xl border border-gray-300 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">Cancel</button>
        <button type="submit" disabled={isPending} className="inline-flex items-center gap-2 rounded-xl bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50">
          {isPending && <Loader2 className="h-4 w-4 animate-spin" />}
          Create mission
        </button>
      </div>
    </form>
  );
}
