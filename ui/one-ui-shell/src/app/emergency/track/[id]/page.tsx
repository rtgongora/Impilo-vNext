"use client";

/**
 * Track a raised emergency request — real status via experience-BFF
 * (GET /internal/v1/daidzai/requests/{id}). Shows request status + the linked
 * incident mission timeline if one exists. No fabricated ambulance position.
 * Route: /emergency/track/[id]
 */

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { Activity, AlertTriangle, Loader2, RefreshCw } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { apiClient } from "@/lib/api-client";

interface EmergencyRequest {
  id: string;
  requestReference?: string;
  status?: string;
  emergencyCategory?: string;
  severity?: string;
  incidentId?: string;
  createdAt?: string;
}

interface MissionEvent {
  id: string;
  status?: string;
  note?: string;
  occurredAt?: string;
}

function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status === 404) return "We could not find that request.";
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Could not load your request. Please try again.";
}

const STATUS_LABEL: Record<string, string> = {
  RECEIVED: "Received",
  TRIAGED: "Triaged",
  LINKED: "Triaged",
  DISPATCH_REQUESTED: "Help requested",
  RESPONDING: "Responding",
  ON_SCENE: "On scene",
  TRANSPORTING: "Transporting",
  HANDOVER: "Handed over to care",
};

export default function EmergencyTrackPage() {
  const params = useParams<{ id: string }>();
  const id = params?.id;
  const [req, setReq] = useState<EmergencyRequest | null>(null);
  const [missions, setMissions] = useState<MissionEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const r = await apiClient.get<EmergencyRequest>(
        `/internal/v1/daidzai/requests/${encodeURIComponent(id)}`
      );
      setReq(r);
      if (r?.incidentId) {
        try {
          const m = await apiClient.get<MissionEvent[]>(
            `/internal/v1/daidzai/incidents/${encodeURIComponent(r.incidentId)}/missions`
          );
          setMissions(Array.isArray(m) ? m : []);
        } catch {
          setMissions([]);
        }
      } else {
        setMissions([]);
      }
    } catch (e) {
      setError(errMessage(e));
      setReq(null);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <AppLayout>
      <PageShell
        title="Track your request"
        subtitle="Live status of the emergency you raised"
        serviceSlug="daidzai"
      >
        <div className="mb-4 flex justify-end">
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
            <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800">
            <div className="mb-2 flex items-center gap-2 font-semibold">
              <AlertTriangle className="h-4 w-4" /> Could not load request
            </div>
            <p>{error}</p>
          </div>
        ) : req ? (
          <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
            <div className="space-y-4">
              <div className="rounded-2xl border border-border bg-card p-5 shadow-sm">
                <div className="mb-2 font-mono text-xs text-muted-foreground">
                  {req.requestReference ?? req.id}
                </div>
                <div className="text-lg font-semibold">
                  {STATUS_LABEL[req.status ?? ""] ?? req.status ?? "—"}
                </div>
                <div className="mt-1 text-sm text-muted-foreground">
                  {req.emergencyCategory?.replace(/_/g, " ")} · {req.severity}
                </div>
              </div>

              <div className="rounded-2xl border border-border bg-card p-5 shadow-sm">
                <div className="mb-3 flex items-center gap-2 font-semibold">
                  <Activity className="h-4 w-4 text-teal-600" /> Response timeline
                </div>
                {missions.length === 0 ? (
                  <p className="text-sm text-muted-foreground">
                    No response updates yet. You&apos;ll see each step here as the team acts.
                  </p>
                ) : (
                  <ol className="space-y-3">
                    {missions.map((m) => (
                      <li key={m.id} className="flex gap-3">
                        <span className="mt-1 h-2 w-2 shrink-0 rounded-full bg-teal-500" />
                        <div>
                          <div className="text-sm font-medium">
                            {STATUS_LABEL[m.status ?? ""] ?? m.status}
                          </div>
                          {m.note ? (
                            <div className="text-xs text-muted-foreground">{m.note}</div>
                          ) : null}
                          {m.occurredAt ? (
                            <div className="text-xs text-muted-foreground">
                              {new Date(m.occurredAt).toLocaleString()}
                            </div>
                          ) : null}
                        </div>
                      </li>
                    ))}
                  </ol>
                )}
              </div>
            </div>

            <NompiloContextualGuidance routePath="/emergency/track/[id]" />
          </div>
        ) : null}
      </PageShell>
    </AppLayout>
  );
}
