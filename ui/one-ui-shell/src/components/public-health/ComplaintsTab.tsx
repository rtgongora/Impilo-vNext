"use client";

import { useState } from "react";
import { AlertTriangle, Loader2, Plus } from "lucide-react";
import {
  COMPLAINT_TRANSITIONS,
  useAcknowledgePublicHealthAlert,
  useFilePublicHealthComplaint,
  usePublicHealthAlerts,
  usePublicHealthComplaints,
  useTransitionPublicHealthComplaint,
} from "@/hooks/queries/usePublicHealth";
import { countHighOrCriticalSurveillanceAlerts, countOpenSurveillanceAlerts } from "./publicHealthAlertMetrics";

export function ComplaintsTab() {
  const [panel, setPanel] = useState<"environmental" | "health_alerts">("environmental");
  const { data: alerts = [], isLoading: alertsLoading, isError: alertsError } = usePublicHealthAlerts();
  const { data: complaints = [], isLoading: complaintsLoading } = usePublicHealthComplaints();
  const acknowledgeAlert = useAcknowledgePublicHealthAlert();
  const fileComplaint = useFilePublicHealthComplaint();
  const transitionComplaint = useTransitionPublicHealthComplaint();

  const [showForm, setShowForm] = useState(false);
  const [title, setTitle] = useState("");
  const [category, setCategory] = useState("NUISANCE");
  const [description, setDescription] = useState("");
  const [province, setProvince] = useState("");
  const [district, setDistrict] = useState("");

  const openCount = countOpenSurveillanceAlerts(alerts);
  const criticalish = countHighOrCriticalSurveillanceAlerts(alerts);
  const openComplaints = complaints.filter((c) => c.status !== "CLOSED").length;

  async function submitComplaint() {
    if (!title.trim()) return;
    try {
      await fileComplaint.mutateAsync({
        title,
        category,
        description,
        province,
        district,
      });
      setTitle("");
      setDescription("");
      setShowForm(false);
    } catch {
      // mutation error surfaced by react-query
    }
  }

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-emerald-200 bg-emerald-50/90 p-3 text-xs text-emerald-950">
        <strong>Live:</strong> environmental complaints and surveillance alerts use governed BFF routes with Tshepo PDP
        checks on mutations.
      </div>

      <div className="flex flex-wrap gap-1 border-b border-gray-200">
        {[
          { key: "environmental" as const, label: "Environmental & nuisance (live)" },
          { key: "health_alerts" as const, label: "Health & surveillance alerts (live)" },
        ].map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setPanel(t.key)}
            className={`px-3 py-2 text-sm font-medium border-b-2 transition-colors ${
              panel === t.key ? "border-amber-600 text-amber-600" : "border-transparent text-gray-500 hover:text-gray-700"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {panel === "environmental" && (
        <>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            {[
              { label: "Open complaints", value: String(openComplaints), color: "text-emerald-700" },
              { label: "Received today", value: String(complaints.length), color: "text-gray-700" },
              { label: "Open surveillance alerts", value: String(openCount), color: "text-amber-700" },
              { label: "High / critical alerts", value: String(criticalish), color: "text-red-700" },
            ].map((kpi) => (
              <div key={kpi.label} className="rounded-lg border border-gray-200 bg-white p-3 text-center">
                <p className={`text-2xl font-bold ${kpi.color}`}>{kpi.value}</p>
                <p className="text-xs font-medium text-gray-900">{kpi.label}</p>
              </div>
            ))}
          </div>

          <div className="flex justify-end">
            <button
              type="button"
              onClick={() => setShowForm((s) => !s)}
              className="inline-flex items-center gap-1 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-700"
            >
              <Plus className="h-3.5 w-3.5" /> File complaint
            </button>
          </div>

          {showForm && (
            <div className="rounded-lg border border-emerald-200 bg-white p-4 space-y-3">
              <input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Complaint title *"
                className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
              />
              <select value={category} onChange={(e) => setCategory(e.target.value)} className="w-full rounded border border-gray-300 px-3 py-2 text-sm">
                <option value="NUISANCE">Nuisance</option>
                <option value="WATER">Water quality</option>
                <option value="WASTE">Solid waste</option>
                <option value="AIR">Air quality</option>
                <option value="FOOD">Food safety</option>
              </select>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Description"
                rows={2}
                className="w-full rounded border border-gray-300 px-3 py-2 text-sm resize-none"
              />
              <div className="grid grid-cols-2 gap-2">
                <input value={province} onChange={(e) => setProvince(e.target.value)} placeholder="Province" className="rounded border border-gray-300 px-3 py-2 text-sm" />
                <input value={district} onChange={(e) => setDistrict(e.target.value)} placeholder="District" className="rounded border border-gray-300 px-3 py-2 text-sm" />
              </div>
              <button
                type="button"
                disabled={fileComplaint.isPending}
                onClick={() => void submitComplaint()}
                className="rounded-lg bg-emerald-600 px-4 py-2 text-sm text-white disabled:opacity-50"
              >
                {fileComplaint.isPending ? "Submitting…" : "Submit complaint"}
              </button>
            </div>
          )}

          <div className="rounded-lg border border-gray-200 bg-white">
            <div className="border-b px-4 py-3">
              <h4 className="text-sm font-semibold text-gray-900">Environmental complaints register</h4>
            </div>
            <div className="p-4">
              {complaintsLoading ? (
                <div className="flex items-center gap-2 text-sm text-gray-500 py-6">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading complaints…
                </div>
              ) : complaints.length === 0 ? (
                <p className="text-sm text-gray-500">No complaints filed yet.</p>
              ) : (
                <ul className="divide-y divide-gray-100">
                  {complaints.map((c) => {
                    const next = COMPLAINT_TRANSITIONS[c.status];
                    return (
                      <li key={c.id} className="flex flex-wrap items-center justify-between gap-2 py-3">
                        <div>
                          <p className="text-sm font-medium text-gray-900">{c.title}</p>
                          <p className="text-xs text-gray-500">
                            {c.category} · {c.district !== "—" ? `${c.district}, ${c.province}` : c.province}
                          </p>
                        </div>
                        <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] text-emerald-800">{c.status}</span>
                        {next && (
                          <button
                            type="button"
                            disabled={transitionComplaint.isPending}
                            onClick={() => transitionComplaint.mutate({ complaintId: c.id, status: next })}
                            className="text-[10px] text-impilo-600 hover:underline"
                          >
                            → {next}
                          </button>
                        )}
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>
          </div>
        </>
      )}

      {panel === "health_alerts" && (
        <div className="rounded-lg border border-gray-200 bg-white">
          <div className="border-b px-4 py-3">
            <h4 className="flex items-center gap-2 text-sm font-semibold text-gray-900">
              <AlertTriangle className="h-4 w-4" /> Surveillance alerts
            </h4>
          </div>
          <div className="p-4">
            {alertsLoading && (
              <div className="flex items-center justify-center gap-2 py-8 text-sm text-gray-500">
                <Loader2 className="h-5 w-5 animate-spin" /> Loading alerts…
              </div>
            )}
            {alertsError && <p className="py-4 text-center text-sm text-red-600">Failed to load alerts.</p>}
            {!alertsLoading && !alertsError && alerts.length === 0 && (
              <p className="py-6 text-center text-sm text-gray-500">No alerts returned.</p>
            )}
            {!alertsLoading && !alertsError && alerts.length > 0 && (
              <ul className="max-h-[420px] divide-y divide-gray-100 overflow-y-auto">
                {alerts.map((a) => (
                  <li key={a.id} className="flex flex-wrap justify-between gap-2 py-3">
                    <div>
                      <p className="text-sm font-medium text-gray-900">{a.title}</p>
                      <p className="text-xs text-gray-500">
                        {a.location} · {a.severity} · {a.detectedAt || "—"}
                      </p>
                    </div>
                    <span className="h-fit rounded-full bg-slate-100 px-2 py-0.5 text-[10px] text-slate-700">{a.status}</span>
                    {a.status !== "acknowledged" && (
                      <button
                        type="button"
                        disabled={acknowledgeAlert.isPending}
                        onClick={() => acknowledgeAlert.mutate(a.id)}
                        className="h-fit rounded-md border border-slate-300 px-2 py-0.5 text-[10px] text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                      >
                        Acknowledge
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
