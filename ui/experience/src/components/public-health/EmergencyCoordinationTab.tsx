"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { Loader2, Siren } from "lucide-react";
import {
  useActivateEmergency,
  useEmergencyActivations,
  useEndEmergency,
  useLogEmergencyAction,
  type EmergencyActivationRow,
} from "@/hooks/queries/useEmergency";
import { useAuthStore } from "@/hooks/useAuthStore";

const PROTOCOL_OPTIONS = ["CODE_BLUE", "TRAUMA", "RSI", "MATERNITY", "TOXICOLOGY", "OTHER"] as const;

function asString(v: unknown): string {
  return v == null ? "" : String(v);
}

function formatWhen(iso: unknown): string {
  const s = asString(iso);
  if (!s) return "—";
  const d = new Date(s);
  return Number.isNaN(d.getTime()) ? s : d.toLocaleString();
}

/**
 * Live **emergency activations** from Experience BFF (`/internal/v1/emergency/*`).
 * EOC level, sitreps, resource tracker, agency directory, IAP — **unsupported** (removed demo tables).
 */
export function EmergencyCoordinationTab() {
  const { user } = useAuthStore();
  const performedBy = user?.id ?? "";
  const performedByName = user?.displayName ?? performedBy;

  const { data, isLoading, isError, refetch } = useEmergencyActivations();
  const activate = useActivateEmergency();
  const logAction = useLogEmergencyAction();
  const endEmergency = useEndEmergency();

  const rows = (data?.data ?? []) as EmergencyActivationRow[];
  const activeRows = useMemo(
    () => rows.filter((r) => asString(r.status).toUpperCase() === "ACTIVE"),
    [rows],
  );

  const [protocolType, setProtocolType] = useState<string>(PROTOCOL_OPTIONS[0]);
  const [patientId, setPatientId] = useState("");
  const [encounterId, setEncounterId] = useState("");
  const [teamLeader, setTeamLeader] = useState("");
  const [location, setLocation] = useState("");

  const [actionFor, setActionFor] = useState<EmergencyActivationRow | null>(null);
  const [actionType, setActionType] = useState("NOTE");
  const [actionDescription, setActionDescription] = useState("");

  const [endFor, setEndFor] = useState<EmergencyActivationRow | null>(null);
  const [endOutcome, setEndOutcome] = useState("STABILISED");
  const [endNotes, setEndNotes] = useState("");

  const [activeSubTab, setActiveSubTab] = useState<"activations" | "unsupported">("activations");

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-red-200 bg-red-50/90 p-3 text-xs text-red-950">
        <strong>Live:</strong> protocol activations below use the same BFF as{" "}
        <Link href="/clinical/emergency" className="underline font-medium">
          ED / Casualty
        </Link>
        . <strong>EOC dashboards, sitreps, logistics tables, and agency directories</strong> are not on{" "}
        <code className="text-[10px]">/internal/v1/emergency/*</code> — prior demo content was removed.
      </div>

      <div className={`rounded-lg border p-3 ${activeRows.length > 0 ? "border-amber-200 bg-amber-50" : "border-gray-200 bg-gray-50"}`}>
        <div className="flex items-center gap-2">
          <Siren className="h-5 w-5 text-amber-700" />
          <p className="text-sm font-medium text-gray-900">
            Active protocol activations: {activeRows.length}
            {rows.length > 0 && <span className="text-gray-500 font-normal"> ({rows.length} recent total)</span>}
          </p>
        </div>
      </div>

      <div className="flex gap-1 border-b border-gray-200">
        <button
          type="button"
          onClick={() => setActiveSubTab("activations")}
          className={`px-3 py-2 text-sm font-medium border-b-2 ${
            activeSubTab === "activations" ? "border-amber-600 text-amber-600" : "border-transparent text-gray-500"
          }`}
        >
          Activations
        </button>
        <button
          type="button"
          onClick={() => setActiveSubTab("unsupported")}
          className={`px-3 py-2 text-sm font-medium border-b-2 ${
            activeSubTab === "unsupported" ? "border-amber-600 text-amber-600" : "border-transparent text-gray-500"
          }`}
        >
          EOC / SitRep / logistics (unsupported)
        </button>
      </div>

      {activeSubTab === "unsupported" && (
        <div className="bg-white rounded-lg border border-gray-200 p-6 text-sm text-gray-600 space-y-2">
          <p>
            <strong>Gap:</strong> situation reports, resource mobilization grids, inter-agency contact lists, and IAP widgets
            need dedicated persistence and BFF routes. None are exposed on the current Experience emergency contract.
          </p>
          <p className="text-xs text-gray-500">
            Blocker examples (for backlog): <code className="text-[10px]">GET /internal/v1/eoc/status</code>,{" "}
            <code className="text-[10px]">GET /internal/v1/sitreps</code>, or domain-specific service proxies.
          </p>
        </div>
      )}

      {activeSubTab === "activations" && (
        <>
          <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
            <h4 className="text-sm font-semibold text-gray-900">Start activation</h4>
            <form onSubmit={(e) => { e.preventDefault(); activate.mutate({ protocolType, patientId: patientId.trim() || null, encounterId: encounterId.trim() || null, teamLeader: teamLeader.trim(), location: location.trim() }); }} className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
              <label className="block">
                Protocol
                <select value={protocolType} onChange={(e) => setProtocolType(e.target.value)} className="mt-1 w-full border rounded px-2 py-1.5">
                  {PROTOCOL_OPTIONS.map((p) => (
                    <option key={p} value={p}>{p}</option>
                  ))}
                </select>
              </label>
              <label className="block">
                Location
                <input value={location} onChange={(e) => setLocation(e.target.value)} className="mt-1 w-full border rounded px-2 py-1.5" placeholder="Ward / bay" />
              </label>
              <label className="block">
                Patient UUID (optional)
                <input value={patientId} onChange={(e) => setPatientId(e.target.value)} className="mt-1 w-full border rounded px-2 py-1.5 font-mono text-[10px]" />
              </label>
              <label className="block">
                Encounter UUID (optional)
                <input value={encounterId} onChange={(e) => setEncounterId(e.target.value)} className="mt-1 w-full border rounded px-2 py-1.5 font-mono text-[10px]" />
              </label>
              <label className="block md:col-span-2">
                Team leader
                <input value={teamLeader} onChange={(e) => setTeamLeader(e.target.value)} className="mt-1 w-full border rounded px-2 py-1.5" />
              </label>
              <button type="submit" disabled={activate.isPending} className="md:col-span-2 py-2 bg-red-600 text-white rounded text-xs font-medium disabled:opacity-50">
                {activate.isPending ? "Submitting…" : "Activate protocol"}
              </button>
            </form>
          </div>

          <div className="bg-white rounded-lg border border-gray-200">
            <div className="px-4 py-3 border-b flex justify-between items-center">
              <h4 className="text-sm font-semibold">Recent activations</h4>
              <button type="button" onClick={() => refetch()} className="text-xs text-blue-600 hover:underline">Refresh</button>
            </div>
            <div className="p-4">
              {isLoading && (
                <div className="flex items-center gap-2 text-sm text-gray-500 py-6 justify-center">
                  <Loader2 className="h-5 w-5 animate-spin" /> Loading…
                </div>
              )}
              {isError && <p className="text-sm text-red-600 text-center py-4">Failed to load activations.</p>}
              {!isLoading && !isError && rows.length === 0 && (
                <p className="text-sm text-gray-500 text-center py-6">No activations returned.</p>
              )}
              {!isLoading && !isError && rows.length > 0 && (
                <ul className="divide-y divide-gray-100 max-h-[360px] overflow-y-auto">
                  {rows.map((r) => (
                    <li key={asString(r.id)} className="py-3 flex flex-wrap gap-2 justify-between items-start">
                      <div>
                        <p className="font-medium text-sm">{asString(r.protocol_type)}</p>
                        <p className="text-xs text-gray-500">
                          {formatWhen(r.activation_time)} · {asString(r.location) || "—"} · {asString(r.status)}
                        </p>
                      </div>
                      <div className="flex gap-1">
                        <button type="button" className="text-[10px] px-2 py-1 border rounded" onClick={() => setActionFor(r)}>Log action</button>
                        <button type="button" className="text-[10px] px-2 py-1 border rounded" onClick={() => setEndFor(r)}>End</button>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          {actionFor && (
            <div className="bg-slate-50 border border-slate-200 rounded-lg p-4 space-y-2">
              <h4 className="text-sm font-semibold">Log action — {asString(actionFor.protocol_type)}</h4>
              <form onSubmit={(e) => { e.preventDefault(); if (!actionFor.id) return; logAction.mutate({ id: actionFor.id, actionType, description: actionDescription.trim(), performedBy: performedByName || performedBy }, { onSuccess: () => { setActionFor(null); setActionDescription(""); } }); }} className="space-y-2 text-xs">
                <input value={actionType} onChange={(e) => setActionType(e.target.value)} className="w-full border rounded px-2 py-1.5" placeholder="Action type" />
                <textarea value={actionDescription} onChange={(e) => setActionDescription(e.target.value)} className="w-full border rounded px-2 py-1.5 min-h-[60px]" placeholder="Description" />
                <div className="flex gap-2">
                  <button type="submit" disabled={logAction.isPending} className="px-3 py-1.5 bg-blue-600 text-white rounded">Save</button>
                  <button type="button" onClick={() => setActionFor(null)} className="px-3 py-1.5 border rounded">Cancel</button>
                </div>
              </form>
            </div>
          )}

          {endFor && (
            <div className="bg-slate-50 border border-slate-200 rounded-lg p-4 space-y-2">
              <h4 className="text-sm font-semibold">End activation</h4>
              <form onSubmit={(e) => { e.preventDefault(); if (!endFor.id) return; endEmergency.mutate({ id: endFor.id, outcome: endOutcome, notes: endNotes.trim() }, { onSuccess: () => { setEndFor(null); setEndNotes(""); } }); }} className="space-y-2 text-xs">
                <input value={endOutcome} onChange={(e) => setEndOutcome(e.target.value)} className="w-full border rounded px-2 py-1.5" />
                <textarea value={endNotes} onChange={(e) => setEndNotes(e.target.value)} className="w-full border rounded px-2 py-1.5 min-h-[50px]" placeholder="Notes" />
                <div className="flex gap-2">
                  <button type="submit" disabled={endEmergency.isPending} className="px-3 py-1.5 bg-red-700 text-white rounded">End</button>
                  <button type="button" onClick={() => setEndFor(null)} className="px-3 py-1.5 border rounded">Cancel</button>
                </div>
              </form>
            </div>
          )}
        </>
      )}
    </div>
  );
}
