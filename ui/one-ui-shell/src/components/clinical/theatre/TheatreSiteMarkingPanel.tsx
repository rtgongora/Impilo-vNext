"use client";

/**
 * Wave B3 — surgical site/side marking on the theatre case.
 *
 * Binds BodyMapField + SURGICAL_SITE_MARKING_INSTRUMENTS to
 * POST /internal/v1/theatre/cases/{id}/site-side. Laterality is required (closed vocab);
 * anatomical site is the optional body-map region label. A failed confirm never clears a
 * previously confirmed site/side from case detail — failure must not look unmarked.
 */

import { useEffect, useMemo, useState } from "react";
import { MapPinned, Loader2 } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { BodyMapField } from "@/features/body-map/BodyMapField";
import { SURGICAL_SITE_MARKING_INSTRUMENTS } from "@/features/body-map/instruments/surgical-instruments";
import { instrumentById } from "@/features/body-map/instruments";
import type { MarkedRegionFinding } from "@/features/body-map/core/types";

export const SITE_LATERALITY_OPTIONS = [
  "LEFT",
  "RIGHT",
  "BILATERAL",
  "MIDLINE",
  "NOT_APPLICABLE",
  "MULTIPLE",
] as const;

export type SiteLaterality = (typeof SITE_LATERALITY_OPTIONS)[number];

const DEFAULT_INSTRUMENT_ID = Object.keys(SURGICAL_SITE_MARKING_INSTRUMENTS)[0] ?? "surgical-abdominal-site-marking";

function errMessage(e: unknown): string {
  const o = e as { error?: { message?: string }; status?: number };
  if (o?.error?.message) return o.error.message;
  if (o?.status) return `Request failed (HTTP ${o.status}).`;
  return "Site/side was NOT confirmed.";
}

function mapFindingLaterality(findings: MarkedRegionFinding[]): SiteLaterality | "" {
  const sides = new Set(
    findings
      .map((f) => f.laterality)
      .filter((l): l is NonNullable<typeof l> => !!l)
      .map((l) => l.toLowerCase()),
  );
  if (sides.size === 0) return "";
  if (sides.size > 1) return "MULTIPLE";
  const only = [...sides][0];
  if (only === "left") return "LEFT";
  if (only === "right") return "RIGHT";
  if (only === "midline") return "MIDLINE";
  return "";
}

function siteLabelFromFindings(findings: MarkedRegionFinding[], instrumentId: string): string | undefined {
  if (findings.length === 0) return undefined;
  const instrument = instrumentById(instrumentId);
  const labels = findings.map((f) => {
    const region = instrument?.maps.flatMap((m) => m.regions).find((r) => r.id === f.regionId);
    return region?.label ?? f.locationCode?.display ?? f.regionId;
  });
  return labels.filter(Boolean).join("; ") || undefined;
}

export function TheatreSiteMarkingPanel({
  caseId,
  laterality,
  anatomicalSite,
  siteSideConfirmedBy,
  siteSideConfirmedAt,
  onConfirmed,
}: {
  caseId: string;
  laterality?: string | null;
  anatomicalSite?: string | null;
  siteSideConfirmedBy?: string | null;
  siteSideConfirmedAt?: string | null;
  onConfirmed: () => void;
}) {
  const instruments = useMemo(
    () => Object.values(SURGICAL_SITE_MARKING_INSTRUMENTS),
    [],
  );
  const [instrumentId, setInstrumentId] = useState(DEFAULT_INSTRUMENT_ID);
  const [findings, setFindings] = useState<MarkedRegionFinding[]>([]);
  const [side, setSide] = useState<SiteLaterality | "">("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  const confirmed = !!(laterality && siteSideConfirmedBy && siteSideConfirmedAt);

  useEffect(() => {
    // Prefill the selector from a confirmed case — never wipe confirmation on a failed re-confirm.
    if (laterality && SITE_LATERALITY_OPTIONS.includes(laterality as SiteLaterality)) {
      setSide(laterality as SiteLaterality);
    }
  }, [laterality]);

  const onFindingsChange = (next: MarkedRegionFinding[]) => {
    setFindings(next);
    const derived = mapFindingLaterality(next);
    if (derived) setSide(derived);
  };

  const canSubmit = side !== "" && !busy;

  const submit = async () => {
    if (!canSubmit) return;
    setBusy(true);
    setMsg(null);
    setFailed(false);
    const anatomical = siteLabelFromFindings(findings, instrumentId);
    try {
      await apiClient.post(`/internal/v1/theatre/cases/${caseId}/site-side`, {
        laterality: side,
        ...(anatomical ? { anatomicalSite: anatomical } : {}),
      });
      setMsg("Site and side confirmed.");
      onConfirmed();
    } catch (e) {
      setFailed(true);
      setMsg(errMessage(e));
      // Do not clear laterality / confirmation props — failure must not look unmarked.
    } finally {
      setBusy(false);
    }
  };

  return (
    <section
      className="rounded-xl border border-border bg-card p-4"
      data-testid="theatre-site-marking-panel"
    >
      <h3 className="mb-1 flex items-center gap-1.5 text-sm font-semibold">
        <MapPinned className="h-4 w-4" /> Surgical site marking
      </h3>
      <p className="mb-3 text-xs text-muted-foreground">
        Confirm the marked operative site and laterality before WHO Sign-In. A lateralised
        procedure cannot start without this confirmation.
      </p>

      {confirmed ? (
        <div
          className="mb-3 rounded-lg border border-emerald-200 bg-emerald-50/60 px-3 py-2 text-xs"
          data-testid="site-marking-confirmed"
        >
          <p className="font-semibold text-emerald-900">
            Confirmed: {[anatomicalSite, laterality].filter(Boolean).join(" · ")}
          </p>
          <p className="mt-0.5 text-muted-foreground">
            by {siteSideConfirmedBy}
            {siteSideConfirmedAt ? ` · ${new Date(siteSideConfirmedAt).toLocaleString()}` : ""}
          </p>
        </div>
      ) : (
        <p
          className="mb-3 rounded-lg border border-amber-200 bg-amber-50/60 px-3 py-2 text-xs font-semibold text-amber-900"
          data-testid="site-marking-unconfirmed"
        >
          Site/side not yet confirmed
        </p>
      )}

      <label className="mb-3 block text-xs">
        <span className="mb-1 block font-medium text-muted-foreground">Map</span>
        <select
          value={instrumentId}
          onChange={(e) => {
            setInstrumentId(e.target.value);
            setFindings([]);
          }}
          className="w-full rounded-lg border border-border bg-background px-2 py-1.5 sm:w-auto"
          data-testid="site-marking-instrument"
        >
          {instruments.map((inst) => (
            <option key={inst.id} value={inst.id}>
              {inst.title}
            </option>
          ))}
        </select>
      </label>

      <BodyMapField
        instrumentId={instrumentId}
        value={findings}
        onChange={onFindingsChange}
        data-testid="site-marking-body-map"
      />

      <div className="mt-3 flex flex-wrap items-end gap-2">
        <label className="block text-xs">
          <span className="mb-1 block font-medium text-muted-foreground">Laterality (required)</span>
          <select
            value={side}
            onChange={(e) => setSide(e.target.value as SiteLaterality | "")}
            className="rounded-lg border border-border bg-background px-2 py-1.5"
            data-testid="site-marking-laterality"
          >
            <option value="">Choose…</option>
            {SITE_LATERALITY_OPTIONS.map((opt) => (
              <option key={opt} value={opt}>
                {opt}
              </option>
            ))}
          </select>
        </label>
        <button
          type="button"
          disabled={!canSubmit}
          onClick={() => void submit()}
          className="rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50"
          data-testid="site-marking-confirm"
        >
          {busy ? (
            <span className="inline-flex items-center gap-1">
              <Loader2 className="h-3.5 w-3.5 animate-spin" /> Confirming…
            </span>
          ) : (
            "Confirm site and side"
          )}
        </button>
      </div>

      {msg && (
        <p
          className={`mt-2 text-xs ${failed ? "text-danger" : "text-emerald-700"}`}
          role={failed ? "alert" : undefined}
          data-testid={failed ? "site-marking-failed" : "site-marking-success"}
        >
          {msg}
        </p>
      )}
    </section>
  );
}
