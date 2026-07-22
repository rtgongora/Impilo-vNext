"use client";

import { History, Loader2 } from "lucide-react";
import { useTelemedicineSessions, type TelemedicineSession } from "@/hooks/queries/useTelemedicine";

/**
 * TM-B14/B5 — prior teleconsults for the same patient, on the console right pane.
 *
 * Reuses `useTelemedicineSessions` filtered to the patient CPID (the BFF list requires a
 * patient/referrer/facility filter). Lists the most recent prior consultations — date, specialty,
 * and lifecycle status — excluding the session currently open. Honest empty/loading/error states.
 */
export function TeleconsultPriorConsultsPanel({
  patientCpid,
  currentSessionId,
  limit = 4,
}: {
  patientCpid: string;
  currentSessionId?: string;
  limit?: number;
}) {
  const { data, isLoading, isError } = useTelemedicineSessions(
    { patientId: patientCpid },
    { enabled: Boolean(patientCpid) },
  );

  const all = data?.data ?? [];
  const prior = all
    .filter((s) => s.id !== currentSessionId)
    .sort((a, b) => sortTime(b) - sortTime(a))
    .slice(0, limit);

  return (
    <section
      className="rounded-xl border border-border bg-card p-3 shadow-sm"
      data-testid="teleconsult-prior-consults"
    >
      <div className="flex items-center gap-2">
        <History className="h-4 w-4 text-primary" />
        <h3 className="text-sm font-semibold text-foreground">Prior teleconsults</h3>
      </div>

      {isLoading ? (
        <div className="mt-3 flex items-center gap-2 text-sm text-muted-foreground" data-testid="teleconsult-prior-loading">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading prior consults…
        </div>
      ) : isError ? (
        <p className="mt-3 text-sm text-red-600" data-testid="teleconsult-prior-error">
          Unable to load prior teleconsults.
        </p>
      ) : prior.length === 0 ? (
        <p className="mt-3 text-xs text-muted-foreground" data-testid="teleconsult-prior-empty">
          No prior teleconsults on record for this patient.
        </p>
      ) : (
        <ul className="mt-3 space-y-1.5" data-testid="teleconsult-prior-list">
          {prior.map((s) => (
            <li key={s.id} className="rounded bg-background px-2 py-1.5 text-xs">
              <div className="flex items-center justify-between gap-2">
                <span className="font-medium text-foreground">{specialtyOf(s)}</span>
                <span className="shrink-0 rounded-full bg-neutral-100 px-2 py-0.5 text-[10px] font-medium text-muted-foreground">
                  {s.attributes.status || "—"}
                </span>
              </div>
              <p className="mt-0.5 text-[11px] text-muted-foreground">{dateOf(s)}</p>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function specialtyOf(s: TelemedicineSession): string {
  const attrs = s.attributes as Record<string, unknown>;
  const specialty = String(attrs.specialty ?? attrs.session_type ?? "").trim();
  return specialty || "Teleconsult";
}

function timeStringOf(s: TelemedicineSession): string {
  return s.attributes.started_at || s.attributes.scheduled_at || s.attributes.created_at || "";
}

function dateOf(s: TelemedicineSession): string {
  const raw = timeStringOf(s);
  if (!raw) return "Date unknown";
  const d = new Date(raw);
  return Number.isNaN(d.getTime()) ? "Date unknown" : d.toLocaleDateString();
}

function sortTime(s: TelemedicineSession): number {
  const d = new Date(timeStringOf(s));
  const t = d.getTime();
  return Number.isNaN(t) ? 0 : t;
}
