"use client";

import React from "react";
import { BodyMapCanvas } from "./core/BodyMapCanvas";
import { RegionFindingEditor } from "./core/RegionFindingEditor";
import { instrumentById } from "./instruments";
import { summariseFindings, type MarkedRegionFinding } from "./core/types";

/**
 * A body map as a form field: pick a view, mark regions, describe each finding.
 *
 * The value is the finding list itself, so it rides the ordinary form-response spine —
 * drafts, submission, versioning and provenance all work unchanged, and there is no
 * parallel store for examination findings to drift out of sync with the encounter.
 */
export interface BodyMapFieldProps {
  instrumentId: string;
  value: MarkedRegionFinding[];
  onChange: (findings: MarkedRegionFinding[]) => void;
  readOnly?: boolean;
  "data-testid"?: string;
}

export function BodyMapField({
  instrumentId,
  value,
  onChange,
  readOnly = false,
  "data-testid": testId,
}: BodyMapFieldProps) {
  const instrument = instrumentById(instrumentId);
  const [mapIndex, setMapIndex] = React.useState(0);
  const [activeRegionId, setActiveRegionId] = React.useState<string | null>(null);

  if (!instrument) {
    // Naming a missing instrument beats rendering an empty box that looks like a working
    // examination with nothing found.
    return (
      <p className="text-xs text-red-600" data-testid={testId ? `${testId}-unknown` : undefined}>
        Unknown examination instrument: {instrumentId}
      </p>
    );
  }

  const map = instrument.maps[Math.min(mapIndex, instrument.maps.length - 1)];
  const findings = value ?? [];
  const activeRegion = activeRegionId ? map.regions.find((r) => r.id === activeRegionId) : undefined;
  const activeFinding = activeRegionId ? findings.find((f) => f.regionId === activeRegionId) : undefined;

  const selectRegion = (regionId: string) => {
    const region = map.regions.find((r) => r.id === regionId);
    if (!region) return;
    if (!findings.some((f) => f.regionId === regionId)) {
      onChange([
        ...findings,
        {
          regionId,
          locationCode: region.locationCode,
          laterality: region.laterality,
        },
      ]);
    }
    setActiveRegionId(regionId);
  };

  const updateFinding = (updated: MarkedRegionFinding) => {
    onChange(findings.map((f) => (f.regionId === updated.regionId ? updated : f)));
  };

  const removeFinding = (regionId: string) => {
    onChange(findings.filter((f) => f.regionId !== regionId));
    setActiveRegionId(null);
  };

  return (
    <div className="space-y-3" data-testid={testId ?? `body-map-field-${instrument.id}`}>
      {instrument.maps.length > 1 && (
        <div className="flex gap-2" role="tablist" aria-label={`${instrument.title} views`}>
          {instrument.maps.map((candidate, index) => (
            <button
              key={candidate.id}
              type="button"
              role="tab"
              aria-selected={index === mapIndex}
              data-testid={`body-map-view-${candidate.id}`}
              onClick={() => {
                setMapIndex(index);
                setActiveRegionId(null);
              }}
              className={`min-h-[44px] flex-1 rounded-md border px-3 text-sm font-medium ${
                index === mapIndex
                  ? "border-primary bg-primary text-primary-foreground"
                  : "border-border bg-background text-foreground"
              }`}
            >
              {candidate.title}
            </button>
          ))}
        </div>
      )}

      <BodyMapCanvas
        map={map}
        findings={findings}
        onRegionSelect={selectRegion}
        activeRegionId={activeRegionId}
        readOnly={readOnly}
      />

      {activeRegion && activeFinding && !readOnly && (
        <RegionFindingEditor
          region={activeRegion}
          instrument={instrument}
          finding={activeFinding}
          onChange={updateFinding}
          onRemove={() => removeFinding(activeRegion.id)}
          onDone={() => setActiveRegionId(null)}
        />
      )}

      <p className="text-xs text-muted-foreground" data-testid="body-map-summary">
        {summariseFindings(findings, map)}
      </p>
    </div>
  );
}
