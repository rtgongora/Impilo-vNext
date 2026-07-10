"use client";

/**
 * Daidzai command board — provider entry. Real incident queue via experience-BFF
 * (GET /internal/v1/daidzai/incidents). Triage a request, then dispatch from the
 * incident. No fake dashboards.
 * Route: /work/daidzai
 */

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { AlertTriangle, Loader2, RefreshCw, Siren } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { apiClient } from "@/lib/api-client";

interface Incident {
  id: string;
  incidentReference?: string;
  incidentType?: string;
  emergencyCategory?: string;
  severity?: string;
  triageCategory?: string;
  status?: string;
  createdAt?: string;
}

function asArray(p: unknown): Incident[] {
  if (Array.isArray(p)) return p as Incident[];
  return [];
}
function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Could not load the incident queue. Please try again.";
}

const TRIAGE_TONE: Record<string, string> = {
  RED: "bg-red-100 text-red-700",
  ORANGE: "bg-orange-100 text-orange-700",
  YELLOW: "bg-amber-100 text-amber-700",
  GREEN: "bg-emerald-100 text-emerald-700",
  BLACK: "bg-slate-800 text-white",
};

/**
 * Escalation intake from Nhume: /work/daidzai?source=nhume&delivery=<id>&ref=<code>&priority=<p>
 * Creates a real incident via the BFF and writes the incident ref back onto the
 * Nhume delivery (metadata.links.daidzaiIncidentRef) so both sides stay linked.
 */
function NhumeEscalationCard({ deliveryId, deliveryRef, priority, onCreated }: {
  deliveryId: string;
  deliveryRef: string;
  priority: string;
  onCreated: () => void;
}) {
  const [category, setCategory] = useState("MEDICAL_TRANSPORT");
  const [severity, setSeverity] = useState(priority === "EMERGENCY" ? "CRITICAL" : "SEVERE");
  const [description, setDescription] = useState(
    `Escalated from Nhume delivery ${deliveryRef || deliveryId} (priority ${priority || "UNKNOWN"}).`,
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<string | null>(null);
  const [linkBack, setLinkBack] = useState<"ok" | "failed" | null>(null);

  const escalate = async () => {
    setBusy(true);
    setError(null);
    try {
      const inc = await apiClient.post<{ id?: string; incidentReference?: string }>(
        "/internal/v1/daidzai/incidents",
        { emergencyCategory: category, severity, description },
      );
      const incidentId = inc?.id ?? inc?.incidentReference ?? "";
      setCreated(inc?.incidentReference ?? incidentId);
      if (incidentId && deliveryId) {
        try {
          await apiClient.post(`/internal/v1/nhume/deliveries/${deliveryId}/links`, {
            key: "daidzaiIncidentRef",
            ref: incidentId,
          });
          setLinkBack("ok");
        } catch {
          setLinkBack("failed");
        }
      }
      onCreated();
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setBusy(false);
    }
  };

  if (created) {
    return (
      <div className="mb-4 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
        <div className="mb-1 flex items-center gap-2 font-semibold">
          <Siren className="h-4 w-4" /> Incident {created} opened from Nhume delivery {deliveryRef || deliveryId}
        </div>
        {linkBack === "ok" ? (
          <p>The incident reference was written back onto the delivery&apos;s linked records.</p>
        ) : linkBack === "failed" ? (
          <p className="text-amber-700">
            Incident created, but the link-back to the Nhume delivery failed — attach it from the delivery page.
          </p>
        ) : null}
      </div>
    );
  }

  return (
    <div className="mb-4 rounded-xl border border-rose-200 bg-rose-50 p-4">
      <div className="mb-2 flex items-center gap-2 text-sm font-semibold text-rose-900">
        <Siren className="h-4 w-4" /> Escalation from Nhume delivery {deliveryRef || deliveryId}
      </div>
      <div className="grid gap-2 sm:grid-cols-2">
        <label className="text-xs text-rose-900">
          Emergency category
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            className="mt-1 w-full rounded-lg border border-rose-200 bg-white px-2 py-1.5 text-sm text-foreground"
          >
            <option value="MEDICAL_TRANSPORT">Medical transport</option>
            <option value="MASS_CASUALTY">Mass casualty</option>
            <option value="OBSTETRIC">Obstetric</option>
            <option value="TRAUMA">Trauma</option>
            <option value="CARDIAC">Cardiac</option>
            <option value="OTHER">Other</option>
          </select>
        </label>
        <label className="text-xs text-rose-900">
          Severity
          <select
            value={severity}
            onChange={(e) => setSeverity(e.target.value)}
            className="mt-1 w-full rounded-lg border border-rose-200 bg-white px-2 py-1.5 text-sm text-foreground"
          >
            <option value="CRITICAL">Critical</option>
            <option value="SEVERE">Severe</option>
            <option value="MODERATE">Moderate</option>
          </select>
        </label>
        <label className="text-xs text-rose-900 sm:col-span-2">
          Description
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={2}
            className="mt-1 w-full rounded-lg border border-rose-200 bg-white px-2 py-1.5 text-sm text-foreground"
          />
        </label>
      </div>
      {error ? <p className="mt-2 text-xs text-rose-700">{error}</p> : null}
      <button
        type="button"
        onClick={() => void escalate()}
        disabled={busy}
        className="mt-3 inline-flex items-center gap-2 rounded-lg bg-rose-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-rose-700 disabled:opacity-60"
      >
        {busy ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Siren className="h-3.5 w-3.5" />}
        Open incident
      </button>
    </div>
  );
}

export default function DaidzaiCommandPage() {
  const searchParams = useSearchParams();
  const escalationSource = searchParams?.get("source") ?? "";
  const escalationDelivery = searchParams?.get("delivery") ?? "";
  const escalationRef = searchParams?.get("ref") ?? "";
  const escalationPriority = searchParams?.get("priority") ?? "";
  const [data, setData] = useState<Incident[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/daidzai/incidents");
      setData(asArray(res));
    } catch (e) {
      setError(errMessage(e));
      setData(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <AppLayout>
      <PageShell
        title="Emergency command"
        subtitle="Live incident queue — Daidzai coordinates Nhume, Ndila, PCT and Rito"
        serviceSlug="daidzai"
      >
        {escalationSource === "nhume" && escalationDelivery ? (
          <NhumeEscalationCard
            deliveryId={escalationDelivery}
            deliveryRef={escalationRef}
            priority={escalationPriority}
            onCreated={() => void load()}
          />
        ) : null}
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div className="flex gap-2 text-sm">
            <Link href="/work/daidzai/dispatch" className="rounded-lg border border-border px-3 py-1.5 hover:bg-background">
              Dispatch
            </Link>
            <Link href="/work/daidzai/missions" className="rounded-lg border border-border px-3 py-1.5 hover:bg-background">
              Missions
            </Link>
            <Link href="/work/daidzai/disasters" className="rounded-lg border border-border px-3 py-1.5 hover:bg-background">
              Disasters
            </Link>
          </div>
          <button
            type="button"
            onClick={() => void load()}
            className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground"
          >
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </button>
        </div>

        {loading ? (
          <div className="flex items-center gap-2 py-16 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading incidents…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800">
            <div className="mb-2 flex items-center gap-2 font-semibold">
              <AlertTriangle className="h-4 w-4" /> Could not load incidents
            </div>
            <p className="mb-3">{error}</p>
            <button
              type="button"
              onClick={() => void load()}
              className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 bg-white px-3 py-1.5 font-medium text-red-700 hover:bg-red-100"
            >
              <RefreshCw className="h-3.5 w-3.5" /> Retry
            </button>
          </div>
        ) : !data || data.length === 0 ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            <Siren className="mx-auto mb-2 h-6 w-6 opacity-50" />
            No active emergency incidents.
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-border bg-card shadow-sm">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead className="border-b border-border bg-background text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th className="px-4 py-3">Reference</th>
                  <th className="px-4 py-3">Type</th>
                  <th className="px-4 py-3">Category</th>
                  <th className="px-4 py-3">Triage</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Opened</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.map((inc) => (
                  <tr key={inc.id} className="hover:bg-background/80">
                    <td className="px-4 py-3 font-mono text-xs">
                      <Link
                        href={`/work/daidzai/missions?incident=${encodeURIComponent(inc.id)}`}
                        className="font-semibold text-teal-700 hover:underline"
                      >
                        {inc.incidentReference ?? inc.id}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{inc.incidentType ?? "—"}</td>
                    <td className="px-4 py-3 text-foreground">
                      {inc.emergencyCategory?.replace(/_/g, " ") ?? "—"}
                    </td>
                    <td className="px-4 py-3">
                      {inc.triageCategory ? (
                        <span
                          className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${TRIAGE_TONE[inc.triageCategory] ?? "bg-slate-100 text-slate-600"}`}
                        >
                          {inc.triageCategory}
                        </span>
                      ) : (
                        "—"
                      )}
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{inc.status ?? "—"}</td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {inc.createdAt ? new Date(inc.createdAt).toLocaleString() : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="mt-6">
          <NompiloContextualGuidance routePath="/work/daidzai" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
