"use client";

import React from "react";
import type { MarkedRegionFinding, RegionMapDef } from "./types";

/**
 * Renders an anatomical map whose regions can be marked.
 *
 * Deliberately fixed-scale with no pan or zoom: the whole map is always visible, so a
 * clinician can never mark the wrong place because the view had scrolled. Regions are real
 * buttons — focusable, labelled and keyboard-reachable — rather than click handlers on
 * shapes, so the map works without a pointer and reads correctly to a screen reader, which
 * matters because a body map is otherwise pure visual information.
 */
export interface BodyMapCanvasProps {
  map: RegionMapDef;
  findings: MarkedRegionFinding[];
  /** Called when a region is selected; the caller decides whether to add or edit. */
  onRegionSelect: (regionId: string) => void;
  /** Marks the region currently open in the editor. */
  activeRegionId?: string | null;
  readOnly?: boolean;
  className?: string;
  "data-testid"?: string;
}

const SEVERITY_FILL: Record<string, string> = {
  mild: "rgba(245, 158, 11, 0.45)",
  moderate: "rgba(249, 115, 22, 0.55)",
  severe: "rgba(220, 38, 38, 0.65)",
};

/** A marked region with no severity still has to look marked. */
const MARKED_FILL = "rgba(37, 99, 235, 0.45)";

export function BodyMapCanvas({
  map,
  findings,
  onRegionSelect,
  activeRegionId,
  readOnly = false,
  className = "",
  "data-testid": testId,
}: BodyMapCanvasProps) {
  const findingFor = (regionId: string) => findings.find((f) => f.regionId === regionId);

  return (
    <svg
      viewBox={map.viewBox}
      role="group"
      aria-label={`${map.title} body map`}
      data-testid={testId ?? `body-map-${map.id}`}
      className={`w-full max-w-sm mx-auto touch-manipulation ${className}`}
    >
      <title>{map.title}</title>

      {map.silhouettePaths.map((d, index) => (
        <path
          key={`silhouette-${index}`}
          d={d}
          className="fill-muted stroke-border"
          strokeWidth={1}
          // The outline is scenery: it must never intercept a tap meant for a region.
          pointerEvents="none"
          aria-hidden="true"
        />
      ))}

      {map.regions.map((region) => {
        const finding = findingFor(region.id);
        const marked = Boolean(finding);
        const isActive = activeRegionId === region.id;
        const fill = finding?.severity ? SEVERITY_FILL[finding.severity] : marked ? MARKED_FILL : "transparent";

        return (
          <path
            key={region.id}
            d={region.svgPath}
            role={readOnly ? "img" : "button"}
            tabIndex={readOnly ? -1 : 0}
            aria-label={
              marked
                ? `${region.label} — marked${finding?.severity ? `, ${finding.severity}` : ""}`
                : region.label
            }
            aria-pressed={readOnly ? undefined : marked}
            data-testid={`body-map-region-${region.id}`}
            data-marked={marked ? "true" : "false"}
            style={{ fill }}
            className={`stroke-border transition-colors ${
              readOnly ? "" : "cursor-pointer hover:stroke-primary focus:outline-none focus-visible:stroke-primary"
            } ${isActive ? "stroke-primary" : ""}`}
            strokeWidth={isActive ? 3 : 1.5}
            onClick={readOnly ? undefined : () => onRegionSelect(region.id)}
            onKeyDown={
              readOnly
                ? undefined
                : (event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      onRegionSelect(region.id);
                    }
                  }
            }
          />
        );
      })}
    </svg>
  );
}
