"use client";

import React from "react";
import { SegmentedControl } from "shared-ui";
import type { BodyMapInstrument, BodyRegionDef, MarkedRegionFinding } from "./types";

/**
 * Captures the structured detail of a finding once a region has been marked.
 *
 * Only the region itself is required. A clinician who marks "something here" and moves on
 * has still recorded more than prose would have, and a form that demands six answers before
 * it accepts the first is a form people stop using. The instrument decides which fields are
 * worth asking for; anything it does not ask for is simply absent, never guessed.
 */
export interface RegionFindingEditorProps {
  region: BodyRegionDef;
  instrument: BodyMapInstrument;
  finding: MarkedRegionFinding;
  onChange: (finding: MarkedRegionFinding) => void;
  onRemove: () => void;
  onDone: () => void;
}

export function RegionFindingEditor({
  region,
  instrument,
  finding,
  onChange,
  onRemove,
  onDone,
}: RegionFindingEditorProps) {
  const set = (patch: Partial<MarkedRegionFinding>) => onChange({ ...finding, ...patch });

  return (
    <div
      className="rounded-lg border border-border bg-background p-4 space-y-4"
      role="group"
      aria-label={`Finding at ${region.label}`}
      data-testid="region-finding-editor"
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-medium text-foreground">{region.label}</p>
          <p className="text-xs text-muted-foreground">{region.locationCode.display ?? region.locationCode.code}</p>
        </div>
        <button
          type="button"
          onClick={onRemove}
          data-testid="region-finding-remove"
          className="min-h-[44px] rounded-md border border-border px-3 text-sm text-foreground hover:bg-muted"
        >
          Remove
        </button>
      </div>

      {instrument.asksLaterality !== false && region.laterality !== "midline" && (
        <div>
          <span id="laterality-label" className="text-xs font-medium text-muted-foreground">
            Side
          </span>
          <div className="mt-1">
            <SegmentedControl
              aria-labelledby="laterality-label"
              data-testid="finding-laterality"
              options={[
                { value: "left", label: "Left" },
                { value: "right", label: "Right" },
                { value: "midline", label: "Midline" },
              ]}
              value={finding.laterality ?? region.laterality ?? ""}
              onChange={(v) => set({ laterality: v as MarkedRegionFinding["laterality"] })}
            />
          </div>
        </div>
      )}

      {instrument.morphologyOptions && instrument.morphologyOptions.length > 0 && (
        <div>
          <span id="morphology-label" className="text-xs font-medium text-muted-foreground">
            Appearance
          </span>
          <div className="mt-1">
            <SegmentedControl
              aria-labelledby="morphology-label"
              data-testid="finding-morphology"
              options={instrument.morphologyOptions.map((o) => ({ value: o.code, label: o.display }))}
              value={finding.morphology ?? ""}
              onChange={(v) => set({ morphology: v })}
            />
          </div>
        </div>
      )}

      {instrument.asksSeverity && (
        <div>
          <span id="severity-label" className="text-xs font-medium text-muted-foreground">
            Severity
          </span>
          <div className="mt-1">
            <SegmentedControl
              aria-labelledby="severity-label"
              data-testid="finding-severity"
              options={[
                { value: "mild", label: "Mild", tone: "warning" },
                { value: "moderate", label: "Moderate", tone: "warning" },
                { value: "severe", label: "Severe", tone: "danger" },
              ]}
              value={finding.severity ?? ""}
              onChange={(v) => set({ severity: v as MarkedRegionFinding["severity"] })}
            />
          </div>
        </div>
      )}

      {instrument.asksPhotoConsent && (
        <div>
          <span id="photo-consent-label" className="text-xs font-medium text-muted-foreground">
            Consent for a clinical photograph
          </span>
          <div className="mt-1">
            {/* Tri-state: "not asked" is the honest default. A photograph must never be
                taken on the strength of a blank field. */}
            <SegmentedControl
              aria-labelledby="photo-consent-label"
              data-testid="finding-photo-consent"
              options={[
                { value: "granted", label: "Granted", tone: "positive" },
                { value: "declined", label: "Declined", tone: "danger" },
                { value: "not_asked", label: "Not asked", tone: "muted" },
              ]}
              value={finding.photoConsent ?? "not_asked"}
              onChange={(v) => set({ photoConsent: v as MarkedRegionFinding["photoConsent"] })}
            />
          </div>
        </div>
      )}

      <label className="block">
        <span className="text-xs font-medium text-muted-foreground">Note</span>
        <textarea
          rows={2}
          data-testid="finding-note"
          className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
          placeholder="Only what the structured fields cannot carry"
          value={finding.note ?? ""}
          onChange={(e) => set({ note: e.target.value })}
        />
      </label>

      <button
        type="button"
        onClick={onDone}
        data-testid="region-finding-done"
        className="min-h-[44px] w-full rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground"
      >
        Done
      </button>
    </div>
  );
}
