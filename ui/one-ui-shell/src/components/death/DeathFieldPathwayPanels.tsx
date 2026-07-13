"use client";

/**
 * Community / field death pathway panels (R7/G16): verbal autopsy (WHO VA — probable cause only,
 * never a certification) and non-facility field body management (safe & dignified burial / release /
 * handover). Both engine + BFF existed; this surfaces them. Respectful language; no forensic detail.
 */

import { useCallback, useEffect, useState } from "react";
import { ClipboardEdit, Loader2, PackageCheck, Plus } from "lucide-react";
import { apiClient } from "@/lib/api-client";

const VA_INSTRUMENTS = ["WHO_VA_2022", "WHO_VA_2016", "OTHER"];
const VA_METHODS = ["PHYSICIAN_REVIEW", "COMPUTER_CODED_INTERVA", "COMPUTER_CODED_INSILICOVA", "EXPERT_PANEL"];
const DISPOSITIONS = ["SAFE_AND_DIGNIFIED_BURIAL", "FAMILY_RELEASE", "FUNERAL_DIRECTOR", "POLICE_HANDOVER", "UNRECOVERED"];

function asArray(res: unknown): Record<string, unknown>[] {
  const data = (res as { data?: unknown })?.data ?? res;
  return Array.isArray(data) ? (data as Record<string, unknown>[]) : [];
}

export function DeathFieldPathwayPanels({ caseId }: { caseId: string }) {
  return (
    <div className="grid gap-4 lg:grid-cols-2" data-testid="death-field-pathway">
      <VerbalAutopsyPanel caseId={caseId} />
      <FieldBodyPanel caseId={caseId} />
    </div>
  );
}

function VerbalAutopsyPanel({ caseId }: { caseId: string }) {
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(true);
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [form, setForm] = useState({
    instrument: VA_INSTRUMENTS[0],
    intervieweeRelationship: "",
    narrative: "",
    probableUnderlyingCause: "",
    causeAssignmentMethod: VA_METHODS[0],
    complete: false,
  });

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(asArray(await apiClient.get(`/internal/v1/death/cases/${caseId}/verbal-autopsy`)));
    } catch {
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [caseId]);
  useEffect(() => void load(), [load]);

  async function submit() {
    setBusy(true);
    setErr(null);
    try {
      await apiClient.post(`/internal/v1/death/cases/${caseId}/verbal-autopsy`, {
        instrument: form.instrument,
        intervieweeRelationship: form.intervieweeRelationship || undefined,
        narrative: form.narrative || undefined,
        probableUnderlyingCause: form.probableUnderlyingCause || undefined,
        causeAssignmentMethod: form.causeAssignmentMethod,
        complete: form.complete,
      });
      setShow(false);
      setForm({ instrument: VA_INSTRUMENTS[0], intervieweeRelationship: "", narrative: "", probableUnderlyingCause: "", causeAssignmentMethod: VA_METHODS[0], complete: false });
      await load();
    } catch {
      setErr("Could not record the verbal autopsy.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="rounded-xl border border-border bg-card p-4">
      <div className="mb-2 flex items-center justify-between">
        <h3 className="flex items-center gap-2 text-sm font-semibold"><ClipboardEdit className="h-4 w-4 text-primary" /> Verbal autopsy</h3>
        <button type="button" onClick={() => setShow((s) => !s)} className="inline-flex items-center gap-1 rounded-lg border border-border px-2 py-1 text-xs hover:bg-background"><Plus className="h-3 w-3" /> Record</button>
      </div>
      <p className="mb-2 text-xs text-muted-foreground">WHO verbal autopsy for a community/field death — yields a <em>probable</em> cause only, never a certification.</p>

      {show && (
        <div className="mb-3 space-y-2 rounded-lg border border-border bg-background p-2">
          <select value={form.instrument} onChange={(e) => setForm({ ...form, instrument: e.target.value })} className="w-full rounded border border-border bg-card px-2 py-1.5 text-sm">
            {VA_INSTRUMENTS.map((i) => <option key={i} value={i}>{i.replace(/_/g, " ")}</option>)}
          </select>
          <input value={form.intervieweeRelationship} onChange={(e) => setForm({ ...form, intervieweeRelationship: e.target.value })} placeholder="Interviewee relationship" className="w-full rounded border border-border px-2 py-1.5 text-sm" />
          <textarea value={form.narrative} onChange={(e) => setForm({ ...form, narrative: e.target.value })} rows={2} placeholder="History narrative" className="w-full rounded border border-border px-2 py-1.5 text-sm" />
          <input value={form.probableUnderlyingCause} onChange={(e) => setForm({ ...form, probableUnderlyingCause: e.target.value })} placeholder="Probable underlying cause" className="w-full rounded border border-border px-2 py-1.5 text-sm" />
          <select value={form.causeAssignmentMethod} onChange={(e) => setForm({ ...form, causeAssignmentMethod: e.target.value })} className="w-full rounded border border-border bg-card px-2 py-1.5 text-sm">
            {VA_METHODS.map((m) => <option key={m} value={m}>{m.replace(/_/g, " ")}</option>)}
          </select>
          <label className="flex items-center gap-2 text-xs text-muted-foreground">
            <input type="checkbox" checked={form.complete} onChange={(e) => setForm({ ...form, complete: e.target.checked })} />
            Mark complete (assigns probable cause)
          </label>
          {err && <p className="text-xs text-red-600">{err}</p>}
          <button type="button" disabled={busy} onClick={() => void submit()} className="rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50">{busy ? "Saving…" : "Save"}</button>
        </div>
      )}

      {loading ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</div>
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted-foreground">No verbal autopsy recorded.</p>
      ) : (
        <ul className="space-y-1.5 text-sm">
          {rows.map((r, i) => (
            <li key={String(r.verbalAutopsyId ?? r.id ?? i)} className="rounded-lg bg-background px-3 py-2">
              <span className="font-medium">{String(r.probableUnderlyingCause ?? "Probable cause pending")}</span>
              <span className="ml-2 text-xs uppercase text-muted-foreground">{String(r.status ?? "DRAFT")}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function FieldBodyPanel({ caseId }: { caseId: string }) {
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(true);
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [form, setForm] = useState({
    dispositionType: DISPOSITIONS[0],
    infectiousRisk: false,
    location: "",
    releasedToName: "",
    releasedToRelationship: "",
    notes: "",
  });

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(asArray(await apiClient.get(`/internal/v1/death/cases/${caseId}/field-body-management`)));
    } catch {
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [caseId]);
  useEffect(() => void load(), [load]);

  async function submit() {
    setBusy(true);
    setErr(null);
    try {
      await apiClient.post(`/internal/v1/death/cases/${caseId}/field-body-management`, {
        dispositionType: form.dispositionType,
        infectiousRisk: form.infectiousRisk,
        safeBurialProtocolApplied: form.infectiousRisk,
        location: form.location || undefined,
        releasedToName: form.releasedToName || undefined,
        releasedToRelationship: form.releasedToRelationship || undefined,
        notes: form.notes || undefined,
      });
      setShow(false);
      setForm({ dispositionType: DISPOSITIONS[0], infectiousRisk: false, location: "", releasedToName: "", releasedToRelationship: "", notes: "" });
      await load();
    } catch {
      setErr("Could not record the disposition (an open medico-legal referral blocks non-police release).");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="rounded-xl border border-border bg-card p-4">
      <div className="mb-2 flex items-center justify-between">
        <h3 className="flex items-center gap-2 text-sm font-semibold"><PackageCheck className="h-4 w-4 text-primary" /> Field body management</h3>
        <button type="button" onClick={() => setShow((s) => !s)} className="inline-flex items-center gap-1 rounded-lg border border-border px-2 py-1 text-xs hover:bg-background"><Plus className="h-3 w-3" /> Record</button>
      </div>
      <p className="mb-2 text-xs text-muted-foreground">Non-facility disposition — safe &amp; dignified burial, release, or handover (no mortuary custody).</p>

      {show && (
        <div className="mb-3 space-y-2 rounded-lg border border-border bg-background p-2">
          <select value={form.dispositionType} onChange={(e) => setForm({ ...form, dispositionType: e.target.value })} className="w-full rounded border border-border bg-card px-2 py-1.5 text-sm">
            {DISPOSITIONS.map((d) => <option key={d} value={d}>{d.replace(/_/g, " ")}</option>)}
          </select>
          <label className="flex items-center gap-2 text-xs text-muted-foreground">
            <input type="checkbox" checked={form.infectiousRisk} onChange={(e) => setForm({ ...form, infectiousRisk: e.target.checked })} />
            Infectious risk (apply safe-burial protocol)
          </label>
          <input value={form.location} onChange={(e) => setForm({ ...form, location: e.target.value })} placeholder="Location" className="w-full rounded border border-border px-2 py-1.5 text-sm" />
          <input value={form.releasedToName} onChange={(e) => setForm({ ...form, releasedToName: e.target.value })} placeholder="Released to (name)" className="w-full rounded border border-border px-2 py-1.5 text-sm" />
          <input value={form.releasedToRelationship} onChange={(e) => setForm({ ...form, releasedToRelationship: e.target.value })} placeholder="Relationship" className="w-full rounded border border-border px-2 py-1.5 text-sm" />
          <textarea value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} rows={2} placeholder="Notes" className="w-full rounded border border-border px-2 py-1.5 text-sm" />
          {err && <p className="text-xs text-red-600">{err}</p>}
          <button type="button" disabled={busy} onClick={() => void submit()} className="rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50">{busy ? "Saving…" : "Save"}</button>
        </div>
      )}

      {loading ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</div>
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted-foreground">No field disposition recorded.</p>
      ) : (
        <ul className="space-y-1.5 text-sm">
          {rows.map((r, i) => (
            <li key={String(r.fieldBodyId ?? r.id ?? i)} className="rounded-lg bg-background px-3 py-2">
              <span className="font-medium">{String(r.dispositionType ?? "Disposition").replace(/_/g, " ")}</span>
              {r.location ? <span className="ml-2 text-xs text-muted-foreground">{String(r.location)}</span> : null}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
