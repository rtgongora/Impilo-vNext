"use client";

/**
 * Resuscitation ABCDE workspace (WU1) — emergency-pressure clinical surface for an
 * active resuscitation. Rapid, large-target entry of ABCDE phases, CPR cycles,
 * medications and free-text observations, all streamed onto ONE emergency activation
 * and stamped with the canonical trauma episode. Every write hits a real
 * experience-bff endpoint (/internal/v1/emergency/{activationId}/*); nothing here is
 * mocked. Reused by the ED visit journey (Resus step) and the standalone resus route.
 */

import { useMemo, useState } from "react";
import {
  Activity,
  AlertTriangle,
  HeartPulse,
  Loader2,
  Pill,
  StickyNote,
  Timer,
  Zap,
} from "lucide-react";
import {
  RESUS_PHASES,
  ARREST_RHYTHMS,
  useResuscitation,
  useResuscitationActions,
  type ResusRow,
  type ResusPhase,
} from "@/hooks/queries/useEmergency";

const PHASE_LABEL: Record<string, string> = {
  AIRWAY: "A · Airway",
  BREATHING: "B · Breathing",
  CIRCULATION: "C · Circulation",
  DISABILITY: "D · Disability",
  EXPOSURE: "E · Exposure",
  ARREST: "Arrest / rhythm",
};

const QUICK_MEDS: Array<{ name: string; dose: string; route: string }> = [
  { name: "Adrenaline", dose: "1 mg", route: "IV" },
  { name: "Amiodarone", dose: "300 mg", route: "IV" },
  { name: "Tranexamic acid", dose: "1 g", route: "IV" },
  { name: "Calcium gluconate", dose: "10 mL 10%", route: "IV" },
];

function str(v: unknown): string {
  return v == null ? "" : String(v);
}

/** Pull a timestamp out of an unknown row, trying the columns the inpatient SoR uses. */
function rowWhen(r: ResusRow): string {
  return str(
    r.recorded_at ?? r.created_at ?? r.started_at ?? r.occurred_at ?? r.performed_at ?? r.start_time,
  );
}

function fmtWhen(iso: string): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

type TimelineItem = { id: string; when: string; kind: "PHASE" | "CPR" | "MED" | "NOTE"; label: string; detail: string };

function EmptyHint({ children }: { children: React.ReactNode }) {
  return <p className="rounded-lg border border-dashed border-border bg-background px-3 py-4 text-center text-xs text-muted-foreground">{children}</p>;
}

export interface ResuscitationWorkspaceProps {
  activationId: string;
  traumaEpisodeId?: string;
  /** Glanceable identity strip — kept visible at all times under emergency pressure. */
  patientLabel?: string;
  traumaId?: string;
  mechanism?: string;
  triageCategory?: string;
  location?: string;
  allergies?: string;
  /** Called after the resus is stood down so the parent can advance its own flow. */
  onEnded?: (outcome: string) => void;
}

export function ResuscitationWorkspace({
  activationId,
  traumaEpisodeId,
  patientLabel,
  traumaId,
  mechanism,
  triageCategory,
  location,
  allergies,
  onEnded,
}: ResuscitationWorkspaceProps) {
  const { record, phases, cprCycles, medications } = useResuscitation(activationId);
  const actions = useResuscitationActions(activationId, traumaEpisodeId);

  const [rhythm, setRhythm] = useState<string>("PEA");
  const [note, setNote] = useState("");
  const [medName, setMedName] = useState("");
  const [medDose, setMedDose] = useState("");
  const [shock, setShock] = useState(false);
  const [outcome, setOutcome] = useState("ROSC");

  const cprCount = cprCycles.data?.length ?? 0;

  const timeline = useMemo<TimelineItem[]>(() => {
    const items: TimelineItem[] = [];
    for (const p of phases.data ?? []) {
      const ph = str(p.phase ?? p.phase_type ?? p.channel);
      items.push({
        id: `p-${str(p.id)}`,
        when: rowWhen(p),
        kind: "PHASE",
        label: PHASE_LABEL[ph] ?? ph ?? "Phase",
        detail: [str(p.rhythm), str(p.notes ?? p.description), str(p.outcome)].filter(Boolean).join(" · "),
      });
    }
    (cprCycles.data ?? []).forEach((c, i) => {
      items.push({
        id: `c-${str(c.id) || i}`,
        when: rowWhen(c),
        kind: "CPR",
        label: `CPR cycle ${str(c.cycle_number ?? c.cycleNumber ?? i + 1)}`,
        detail: [str(c.rhythm), c.shock_delivered || c.shockDelivered ? "shock delivered" : "", str(c.description)].filter(Boolean).join(" · "),
      });
    });
    for (const m of medications.data ?? []) {
      items.push({
        id: `m-${str(m.id)}`,
        when: rowWhen(m),
        kind: "MED",
        label: str(m.name ?? m.medication ?? m.description) || "Medication",
        detail: [str(m.dose), str(m.route)].filter(Boolean).join(" "),
      });
    }
    return items.sort((a, b) => (a.when < b.when ? -1 : a.when > b.when ? 1 : 0));
  }, [phases.data, cprCycles.data, medications.data]);

  const busy = actions.startPhase.isPending || actions.addCprCycle.isPending || actions.addMedication.isPending;
  const loading = phases.isLoading || cprCycles.isLoading || medications.isLoading;

  const rec = record.data ?? null;
  const isEnded = !!(rec && (rec.outcome || rec.ended_at));

  return (
    <div className="space-y-4" data-testid="resuscitation-workspace">
      {/* Glanceable identity + situation strip */}
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-xl border border-red-200 bg-danger-soft/50 px-4 py-3 text-sm">
        <span className="flex items-center gap-1.5 font-semibold text-red-900">
          <HeartPulse className="h-4 w-4" /> Resuscitation in progress
        </span>
        {patientLabel ? <span className="font-medium text-foreground">{patientLabel}</span> : null}
        {traumaId ? <span className="rounded bg-orange-100 px-1.5 py-0.5 font-mono text-xs text-orange-900">Trauma {traumaId.slice(0, 8)}</span> : null}
        {mechanism ? <span className="text-muted-foreground">Mechanism: {mechanism}</span> : null}
        {triageCategory ? <span className="rounded bg-red-100 px-1.5 py-0.5 text-xs font-semibold text-red-800">{triageCategory}</span> : null}
        {location ? <span className="text-muted-foreground">{location}</span> : null}
        {allergies ? (
          <span className="flex items-center gap-1 rounded bg-yellow-100 px-1.5 py-0.5 text-xs font-semibold text-yellow-900">
            <AlertTriangle className="h-3 w-3" /> Allergies: {allergies}
          </span>
        ) : (
          <span className="text-xs text-muted-foreground">Allergies: not recorded</span>
        )}
      </div>

      {isEnded && (
        <div className="rounded-xl border border-green-200 bg-success-soft px-4 py-3 text-sm text-primary-hover">
          Resuscitation stood down — outcome <strong>{str(rec?.outcome) || "recorded"}</strong>.
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-[1fr_360px]">
        {/* Rapid entry column */}
        <div className="space-y-4">
          {/* Arrest / CPR */}
          <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
            <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
              <Zap className="h-4 w-4 text-red-600" /> Arrest &amp; CPR
            </h3>
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <span className="text-xs font-medium text-muted-foreground">Rhythm</span>
              {ARREST_RHYTHMS.map((r) => (
                <button
                  key={r}
                  type="button"
                  onClick={() => setRhythm(r)}
                  className={`rounded-lg px-3 py-2 text-sm font-medium ${rhythm === r ? "bg-red-600 text-white" : "bg-neutral-100 text-foreground hover:bg-neutral-200"}`}
                >
                  {r.replace(/_/g, " ")}
                </button>
              ))}
            </div>
            <div className="mt-3 flex flex-wrap items-center gap-3">
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={shock} onChange={(e) => setShock(e.target.checked)} className="h-4 w-4" />
                Shock delivered this cycle
              </label>
              <button
                type="button"
                disabled={actions.addCprCycle.isPending || isEnded}
                onClick={() =>
                  actions.addCprCycle.mutate(
                    { cycleNumber: cprCount + 1, rhythm, shockDelivered: shock },
                    { onSuccess: () => setShock(false) },
                  )
                }
                className="inline-flex items-center gap-2 rounded-lg bg-red-600 px-5 py-3 text-base font-semibold text-white hover:bg-red-700 disabled:opacity-50"
              >
                {actions.addCprCycle.isPending ? <Loader2 className="h-5 w-5 animate-spin" /> : <HeartPulse className="h-5 w-5" />}
                Log CPR cycle #{cprCount + 1}
              </button>
              <span className="text-sm text-muted-foreground">{cprCount} cycle{cprCount === 1 ? "" : "s"} logged</span>
            </div>
          </section>

          {/* ABCDE rapid phase entry */}
          <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
            <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
              <Activity className="h-4 w-4 text-primary" /> ABCDE assessment
            </h3>
            <p className="mt-1 text-xs text-muted-foreground">Tap a channel to timestamp an assessment onto the resus record.</p>
            <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-3">
              {RESUS_PHASES.map((p: ResusPhase) => (
                <button
                  key={p}
                  type="button"
                  disabled={busy || isEnded}
                  onClick={() => actions.startPhase.mutate({ phase: p, rhythm: p === "ARREST" ? rhythm : undefined, notes: note || undefined })}
                  className="rounded-lg border border-border bg-background px-3 py-3 text-sm font-medium text-foreground hover:border-primary/40 hover:bg-primary/5 disabled:opacity-50"
                >
                  {PHASE_LABEL[p]}
                </button>
              ))}
            </div>
            <div className="mt-3 flex gap-2">
              <input
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="Optional note attached to the next channel / observation"
                className="flex-1 rounded-lg border border-border px-3 py-2 text-sm"
              />
              <button
                type="button"
                disabled={!note.trim() || actions.logAction.isPending || isEnded}
                onClick={() => actions.logAction.mutate({ description: note.trim() }, { onSuccess: () => setNote("") })}
                className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-2 text-sm hover:bg-background disabled:opacity-50"
              >
                <StickyNote className="h-4 w-4" /> Note
              </button>
            </div>
          </section>

          {/* Medications */}
          <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
            <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
              <Pill className="h-4 w-4 text-purple-600" /> Medications
            </h3>
            <div className="mt-3 flex flex-wrap gap-2">
              {QUICK_MEDS.map((m) => (
                <button
                  key={m.name}
                  type="button"
                  disabled={actions.addMedication.isPending || isEnded}
                  onClick={() => actions.addMedication.mutate({ name: m.name, dose: m.dose, route: m.route })}
                  className="rounded-lg bg-purple-50 px-3 py-2 text-sm font-medium text-purple-900 hover:bg-purple-100 disabled:opacity-50"
                >
                  {m.name} {m.dose}
                </button>
              ))}
            </div>
            <div className="mt-3 flex flex-wrap gap-2">
              <input value={medName} onChange={(e) => setMedName(e.target.value)} placeholder="Drug" className="w-40 rounded-lg border border-border px-3 py-2 text-sm" />
              <input value={medDose} onChange={(e) => setMedDose(e.target.value)} placeholder="Dose / route" className="w-40 rounded-lg border border-border px-3 py-2 text-sm" />
              <button
                type="button"
                disabled={!medName.trim() || actions.addMedication.isPending || isEnded}
                onClick={() =>
                  actions.addMedication.mutate(
                    { name: medName.trim(), dose: medDose.trim() },
                    { onSuccess: () => { setMedName(""); setMedDose(""); } },
                  )
                }
                className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-2 text-sm hover:bg-background disabled:opacity-50"
              >
                Add
              </button>
            </div>
          </section>

          {/* Stand down */}
          {!isEnded && (
            <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
              <h3 className="text-sm font-semibold text-foreground">Stand down</h3>
              <div className="mt-3 flex flex-wrap items-center gap-2">
                <select value={outcome} onChange={(e) => setOutcome(e.target.value)} className="rounded-lg border border-border px-3 py-2 text-sm">
                  {["ROSC", "STABILISED", "TRANSFERRED", "DECEASED"].map((o) => (
                    <option key={o} value={o}>{o}</option>
                  ))}
                </select>
                <button
                  type="button"
                  disabled={actions.end.isPending}
                  onClick={() => actions.end.mutate({ outcome }, { onSuccess: () => onEnded?.(outcome) })}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-neutral-900 px-4 py-2 text-sm font-semibold text-white hover:bg-neutral-800 disabled:opacity-50"
                >
                  {actions.end.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                  End resuscitation
                </button>
              </div>
            </section>
          )}
        </div>

        {/* Timeline column */}
        <aside className="rounded-xl border border-border bg-card p-4 shadow-sm">
          <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
            <Timer className="h-4 w-4 text-muted-foreground" /> Resus timeline
          </h3>
          {loading ? (
            <div className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading…
            </div>
          ) : timeline.length === 0 ? (
            <div className="mt-3">
              <EmptyHint>No resus events yet. Log CPR, tap an ABCDE channel, or record a drug — each timestamps here.</EmptyHint>
            </div>
          ) : (
            <ol className="mt-3 space-y-2">
              {timeline.map((t) => (
                <li key={t.id} className="flex gap-2 text-sm">
                  <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-red-500" aria-hidden />
                  <div className="min-w-0">
                    <div className="flex items-baseline gap-2">
                      <span className="font-medium text-foreground">{t.label}</span>
                      <span className="font-mono text-[11px] text-muted-foreground">{fmtWhen(t.when)}</span>
                    </div>
                    {t.detail ? <div className="truncate text-xs text-muted-foreground">{t.detail}</div> : null}
                  </div>
                </li>
              ))}
            </ol>
          )}
        </aside>
      </div>
    </div>
  );
}
