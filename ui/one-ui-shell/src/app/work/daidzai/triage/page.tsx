"use client";

/**
 * Call-taker SOS triage workspace (WU5) — intake an emergency call, then triage the
 * request to mint an incident (+ trauma episode for trauma). Wired to the real Daidzai
 * endpoints: POST /daidzai/requests, GET /daidzai/requests/{id},
 * POST /daidzai/requests/{id}/triage. On triage the call-taker hands off to EMS dispatch.
 * Route: /work/daidzai/triage
 *
 * NOTE: daidzai-service exposes no "list pending requests" endpoint, so this is an
 * intake→triage cockpit (create or look up a request by id), not a queue. A pending-
 * queue list needs a new sovereign endpoint (flagged).
 */

import { useState } from "react";
import Link from "next/link";
import { AlertTriangle, Loader2, PhoneCall } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { useCreateSosRequest, useDaidzaiRequest, useTriageRequest } from "@/hooks/queries/useDaidzai";

const CATEGORIES = ["TRAUMA", "CARDIAC", "OBSTETRIC", "RESPIRATORY", "MEDICAL", "OTHER"] as const;
const SEVERITIES = ["CRITICAL", "SERIOUS", "STABLE"] as const;

function str(v: unknown): string {
  return v == null ? "" : String(v);
}

export default function DaidzaiTriagePage() {
  const create = useCreateSosRequest();
  const triage = useTriageRequest();

  const [requestId, setRequestId] = useState<string | null>(null);
  const request = useDaidzaiRequest(requestId ?? undefined);
  const [incident, setIncident] = useState<Record<string, unknown> | null>(null);

  const [form, setForm] = useState({
    subjectLabel: "",
    emergencyCategory: "TRAUMA" as string,
    severity: "CRITICAL" as string,
    description: "",
    locationDescription: "",
  });

  const submitIntake = (e: React.FormEvent) => {
    e.preventDefault();
    setIncident(null);
    create.mutate(
      {
        requesterType: "BYSTANDER",
        subjectIdentityMode: "ANONYMOUS",
        subjectLabel: form.subjectLabel.trim() || "Unknown caller",
        emergencyCategory: form.emergencyCategory,
        severity: form.severity,
        description: form.description.trim(),
        locationDescription: form.locationDescription.trim(),
        channel: "PHONE",
      },
      { onSuccess: (r) => setRequestId(str(r.id) || null) },
    );
  };

  const doTriage = () => {
    if (!requestId) return;
    triage.mutate(requestId, { onSuccess: (inc) => setIncident(inc) });
  };

  const req = request.data;
  const episodeId = incident ? str(incident.traumaEpisodeId) : "";
  const incidentId = incident ? str(incident.id) : "";

  return (
    <AppLayout>
      <PageShell title="Call-taker triage" subtitle="Intake an emergency call and triage it into an incident" serviceSlug="daidzai">
        <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
          <div className="space-y-5">
            {/* Intake */}
            <form onSubmit={submitIntake} className="rounded-xl border border-border bg-card p-4 shadow-sm space-y-3">
              <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground"><PhoneCall className="h-4 w-4 text-teal-600" /> New emergency call</h2>
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="text-xs font-medium text-muted-foreground">
                  Category
                  <select value={form.emergencyCategory} onChange={(e) => setForm((f) => ({ ...f, emergencyCategory: e.target.value }))} className="mt-1 w-full rounded border px-2 py-1.5 text-sm">
                    {CATEGORIES.map((c) => <option key={c} value={c}>{c}</option>)}
                  </select>
                </label>
                <label className="text-xs font-medium text-muted-foreground">
                  Severity
                  <select value={form.severity} onChange={(e) => setForm((f) => ({ ...f, severity: e.target.value }))} className="mt-1 w-full rounded border px-2 py-1.5 text-sm">
                    {SEVERITIES.map((s) => <option key={s} value={s}>{s}</option>)}
                  </select>
                </label>
                <label className="text-xs font-medium text-muted-foreground sm:col-span-2">
                  Patient label
                  <input value={form.subjectLabel} onChange={(e) => setForm((f) => ({ ...f, subjectLabel: e.target.value }))} placeholder="e.g. adult male RTC" className="mt-1 w-full rounded border px-2 py-1.5 text-sm" />
                </label>
                <label className="text-xs font-medium text-muted-foreground sm:col-span-2">
                  Location
                  <input value={form.locationDescription} onChange={(e) => setForm((f) => ({ ...f, locationDescription: e.target.value }))} placeholder="e.g. Harare-Bulawayo highway, km 42" className="mt-1 w-full rounded border px-2 py-1.5 text-sm" />
                </label>
                <label className="text-xs font-medium text-muted-foreground sm:col-span-2">
                  Description
                  <textarea value={form.description} onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))} rows={2} className="mt-1 w-full rounded border px-2 py-1.5 text-sm" />
                </label>
              </div>
              <button type="submit" disabled={create.isPending} className="rounded-lg bg-teal-600 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-700 disabled:opacity-50">
                {create.isPending ? "Logging call…" : "Log call"}
              </button>
              {create.isError && <p className="text-xs text-danger">Could not log the call — retry.</p>}
            </form>

            {/* Triage */}
            {requestId && (
              <div className="rounded-xl border border-border bg-card p-4 shadow-sm space-y-3">
                <h2 className="text-sm font-semibold text-foreground">Triage request</h2>
                <p className="text-sm text-muted-foreground">
                  Request <code className="font-mono text-xs">{str(req?.requestReference) || requestId}</code> — status <strong>{str(req?.status) || "…"}</strong>
                </p>
                {!incident ? (
                  <button type="button" onClick={doTriage} disabled={triage.isPending} className="rounded-lg bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-50">
                    {triage.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : "Triage → create incident"}
                  </button>
                ) : (
                  <div className="space-y-2 rounded-lg border border-green-200 bg-success-soft p-3 text-sm text-primary-hover">
                    <p>Incident <code className="font-mono text-xs">{str(incident.incidentReference) || incidentId}</code> created — triage {str(incident.triageCategory) || "—"}.</p>
                    <div className="flex flex-wrap gap-3">
                      <Link href={`/work/daidzai/dispatch`} className="text-primary underline">→ EMS dispatch</Link>
                      {episodeId ? <Link href={`/clinical/emergency/episode/${episodeId}`} className="text-primary underline">→ Trauma episode</Link> : null}
                    </div>
                  </div>
                )}
                {triage.isError && (
                  <p className="flex items-center gap-1 text-xs text-danger"><AlertTriangle className="h-3 w-3" /> Triage failed — retry.</p>
                )}
              </div>
            )}
          </div>

          <NompiloContextualGuidance routePath="/work/daidzai/triage" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
