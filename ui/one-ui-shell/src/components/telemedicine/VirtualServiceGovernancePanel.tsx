"use client";

/**
 * Registry-admin governance panel for a virtual service-delivery entity (Lane E Wave 2).
 * Surfaces the governed lifecycle — add routing seam → activate (fail-closed: needs ≥1
 * active seam + ≥1 queue def) → suspend — plus live materialisation and on-duty coverage
 * read-backs. This is a DISPLAY gate on the registry-admin group; TUSO enforces the real
 * authorization and preconditions server-side (a non-admin who forces the call gets 403).
 */

import { useState } from "react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { matchesRequiredRole } from "@/lib/auth/role-groups";
import {
  useVirtualServiceGovernance,
  useVirtualServiceReadbacks,
} from "@/hooks/queries/useTelemedicineOperatingModel";

const LIFECYCLE_TONE: Record<string, string> = {
  ACTIVE: "bg-emerald-50 text-emerald-700 border-emerald-200",
  ACTIVATION_REQUESTED: "bg-amber-50 text-amber-700 border-amber-200",
  CONFIGURED: "bg-slate-100 text-slate-600 border-slate-200",
  SUSPENDED: "bg-red-50 text-red-700 border-red-200",
};

export function VirtualServiceGovernancePanel({ vsUid }: { vsUid: string }) {
  const hasRole = useAuthStore((s) => s.hasRole);
  const isRegistryAdmin = matchesRequiredRole(hasRole, "REGISTRY_ADMIN");

  const readbacks = useVirtualServiceReadbacks(isRegistryAdmin ? vsUid : "");
  const { addSeam, activate, suspend } = useVirtualServiceGovernance(vsUid);

  const [routingType, setRoutingType] = useState("SPECIALTY_POOL");
  const [targetRef, setTargetRef] = useState("");
  const [note, setNote] = useState("");

  // Governance is registry-admin only; hide entirely for everyone else.
  if (!isRegistryAdmin) return null;

  const data = readbacks.data;
  const lifecycle = data?.lifecycleStatus ?? "—";
  const tone = LIFECYCLE_TONE[lifecycle] ?? "bg-slate-100 text-slate-600 border-slate-200";
  const busy = addSeam.isPending || activate.isPending || suspend.isPending;
  const lastError =
    (addSeam.error as Error | null)?.message ??
    (activate.error as Error | null)?.message ??
    (suspend.error as Error | null)?.message ??
    null;

  return (
    <section
      className="mt-6 rounded-xl border border-slate-300 bg-white p-5"
      data-testid="vh-governance-panel"
    >
      <div className="flex items-center justify-between gap-3">
        <h2 className="font-semibold text-slate-900">Governance</h2>
        <span className={`rounded-full border px-2.5 py-0.5 text-xs font-medium ${tone}`}>
          {lifecycle}
        </span>
      </div>
      <p className="mt-1 text-xs text-slate-500">
        Registry-admin only. Activation is fail-closed — an institution goes live only once
        it carries an active routing seam and its queues materialise in PCT.
      </p>

      {/* Live read-backs */}
      <dl className="mt-4 grid gap-3 sm:grid-cols-3">
        <div className="rounded-lg border border-slate-200 p-3">
          <dt className="text-[11px] uppercase tracking-wide text-slate-500">Primary pool</dt>
          <dd className="mt-1 font-mono text-sm text-slate-800">
            {data?.primaryPoolId ?? "none yet"}
          </dd>
        </div>
        <div className="rounded-lg border border-slate-200 p-3">
          <dt className="text-[11px] uppercase tracking-wide text-slate-500">Materialisation</dt>
          <dd className="mt-1 text-sm text-slate-800">
            {data?.materializationDegraded
              ? "PCT unreachable"
              : data?.materialization
                ? "Live"
                : "Not materialised"}
          </dd>
        </div>
        <div className="rounded-lg border border-slate-200 p-3">
          <dt className="text-[11px] uppercase tracking-wide text-slate-500">On duty</dt>
          <dd className="mt-1 text-sm text-slate-800">
            {data?.duty?.onDutyCount ?? 0} provider(s)
            {data?.duty?.nextShiftStart
              ? ` · next ${new Date(data.duty.nextShiftStart).toLocaleString()}`
              : ""}
          </dd>
        </div>
      </dl>

      {lastError && (
        <div
          className="mt-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800"
          data-testid="vh-governance-error"
        >
          {lastError}
        </div>
      )}

      {/* Add routing seam */}
      <div className="mt-4 rounded-lg border border-slate-200 p-4">
        <h3 className="text-sm font-medium text-slate-800">Add routing seam</h3>
        <div className="mt-2 grid gap-2 sm:grid-cols-3">
          <select
            value={routingType}
            onChange={(e) => setRoutingType(e.target.value)}
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
            aria-label="Routing type"
          >
            <option value="SPECIALTY_POOL">SPECIALTY_POOL</option>
            <option value="TEAM">TEAM</option>
            <option value="FACILITY_SERVICE">FACILITY_SERVICE</option>
          </select>
          <input
            value={targetRef}
            onChange={(e) => setTargetRef(e.target.value)}
            placeholder="Target pool key"
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
          />
          <input
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Note (optional)"
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <button
          type="button"
          disabled={busy || !targetRef.trim()}
          onClick={() =>
            addSeam.mutate(
              { routingType, targetRef: targetRef.trim(), note: note.trim() || undefined },
              { onSuccess: () => { setTargetRef(""); setNote(""); } },
            )
          }
          className="mt-3 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-800 hover:bg-slate-50 disabled:opacity-50"
          data-testid="vh-add-seam"
        >
          {addSeam.isPending ? "Adding…" : "Add seam"}
        </button>
      </div>

      {/* Lifecycle actions */}
      <div className="mt-4 flex flex-wrap gap-3">
        <button
          type="button"
          disabled={busy || lifecycle === "ACTIVE"}
          onClick={() => activate.mutate()}
          className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-50"
          data-testid="vh-activate"
        >
          {activate.isPending ? "Requesting…" : "Activate"}
        </button>
        <button
          type="button"
          disabled={busy || lifecycle === "SUSPENDED" || lifecycle === "CONFIGURED"}
          onClick={() => {
            const reason = window.prompt("Reason for suspending this institution?");
            if (reason !== null) suspend.mutate({ reason: reason || undefined });
          }}
          className="rounded-lg border border-red-300 bg-white px-4 py-2 text-sm font-semibold text-red-700 hover:bg-red-50 disabled:opacity-50"
          data-testid="vh-suspend"
        >
          {suspend.isPending ? "Suspending…" : "Suspend"}
        </button>
      </div>
    </section>
  );
}
