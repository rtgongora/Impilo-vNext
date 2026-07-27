"use client";

import React from "react";
import {
  axisTickLabel,
  axisTicks,
  buildScale,
  detectDownwardCrossing,
  polylinePoints,
  valueTicks,
  type GrowthAxis,
  type PlottedMeasurement,
  type ReferenceCurve,
} from "./growth-curves";

/**
 * The growth chart, plotted against whichever standard the child is read on.
 *
 * The growth page has shown z-scores in a table since the measurements existed, and a
 * table is the wrong shape for the question a growth chart answers. A clinician needs to
 * see whether the trajectory is tracking a curve or falling away from it — the number
 * -2.1 SD says where the child is, the line says where they are going, and it is the
 * direction that decides whether to act.
 *
 * Hand-rolled SVG, following the partograph plot: this repository has no charting library
 * and does not need one for a plot of this shape.
 */

const VB_W = 720;
const VB_H = 380;
const ML = 52;
const MR = 20;
const MT = 24;
const MB = 40;

const CURVE_STYLE: Record<number, { stroke: string; dash?: string; label: string }> = {
  [-3]: { stroke: "#dc2626", dash: "4 3", label: "-3 SD" },
  [-2]: { stroke: "#f59e0b", dash: "4 3", label: "-2 SD" },
  [0]: { stroke: "#16a34a", label: "Median" },
  [2]: { stroke: "#f59e0b", dash: "4 3", label: "+2 SD" },
  [3]: { stroke: "#dc2626", dash: "4 3", label: "+3 SD" },
};

export interface GrowthStandardsChartProps {
  title: string;
  /** Unit of the plotted measurement, e.g. "kg" or "cm". */
  unit: string;
  curves: ReferenceCurve[];
  measurements: PlottedMeasurement[];
  /** What the horizontal axis measures. Defaults to age in days. */
  axis?: GrowthAxis;
  /**
   * The name of the standard being plotted. Shown on the chart itself, not merely nearby:
   * a reader must be able to tell from the drawing which chart these curves are, and the
   * Fenton licence requires its label to appear conspicuously wherever its data is drawn.
   */
  standardLabel?: string | null;
  /** Citation the standard's licence obliges the surface to carry. */
  citation?: string | null;
  className?: string;
  "data-testid"?: string;
}

export function GrowthStandardsChart({
  title,
  unit,
  curves,
  measurements,
  axis = "age_days",
  standardLabel,
  citation,
  className = "",
  "data-testid": testId = "growth-standards-chart",
}: GrowthStandardsChartProps) {
  const ordered = React.useMemo(
    () => [...measurements].sort((a, b) => a.x - b.x),
    [measurements],
  );

  if (curves.length === 0) {
    // Plotting a trajectory with no reference curves would look like a chart while
    // answering none of the questions a chart is for.
    return (
      <p className="text-sm text-muted-foreground" data-testid={`${testId}-unavailable`}>
        Reference curves for {title.toLowerCase()} are not available, so the measurements cannot be
        plotted against the growth standard.
      </p>
    );
  }

  const scale = buildScale(curves, ordered, {
    left: ML,
    right: MR,
    top: MT,
    bottom: MB,
    width: VB_W,
    height: VB_H,
  });

  const latest = ordered[ordered.length - 1];
  const faltering = detectDownwardCrossing(ordered);

  return (
    <figure className={className} data-testid={testId}>
      <svg viewBox={`0 0 ${VB_W} ${VB_H}`} role="img" aria-label={`${title} growth chart`} className="w-full">
        <title>{title}</title>
        {standardLabel && (
          <text x={ML} y={MT - 8} className="fill-muted-foreground" fontSize={10} data-testid="growth-standard-label">
            {standardLabel}
          </text>
        )}

        {/* Axes */}
        <line x1={ML} y1={MT} x2={ML} y2={VB_H - MB} className="stroke-border" strokeWidth={1} />
        <line x1={ML} y1={VB_H - MB} x2={VB_W - MR} y2={VB_H - MB} className="stroke-border" strokeWidth={1} />

        {valueTicks(scale).map((value) => (
          <g key={`y-${value}`}>
            <line
              x1={ML}
              y1={scale.yForValue(value)}
              x2={VB_W - MR}
              y2={scale.yForValue(value)}
              className="stroke-border"
              strokeWidth={0.5}
              opacity={0.4}
            />
            <text
              x={ML - 6}
              y={scale.yForValue(value) + 3}
              textAnchor="end"
              className="fill-muted-foreground"
              fontSize={10}
            >
              {value.toFixed(1)}
            </text>
          </g>
        ))}

        {axisTicks(scale).map((position) => (
          <text
            key={`x-${position}`}
            x={scale.xFor(position)}
            y={VB_H - MB + 14}
            textAnchor="middle"
            className="fill-muted-foreground"
            fontSize={10}
          >
            {axisTickLabel(position, axis)}
          </text>
        ))}

        {/* Reference curves */}
        {curves.map((curve) => {
          const style = CURVE_STYLE[curve.z] ?? { stroke: "#94a3b8", label: `${curve.z} SD` };
          return (
            <g key={`curve-${curve.z}`}>
              <polyline
                points={polylinePoints(curve.points, scale)}
                fill="none"
                stroke={style.stroke}
                strokeDasharray={style.dash}
                strokeWidth={curve.z === 0 ? 1.8 : 1.2}
                data-testid={`growth-curve-${curve.z}`}
              />
              {curve.points.length > 0 && (
                <text
                  x={VB_W - MR + 2}
                  y={scale.yForValue(curve.points[curve.points.length - 1].value) + 3}
                  textAnchor="start"
                  fill={style.stroke}
                  fontSize={9}
                >
                  {style.label}
                </text>
              )}
            </g>
          );
        })}

        {/* The child's trajectory */}
        {ordered.length > 1 && (
          <polyline
            points={polylinePoints(ordered, scale)}
            fill="none"
            className="stroke-foreground"
            strokeWidth={2}
            data-testid="growth-trajectory"
          />
        )}
        {ordered.map((point, index) => (
          <circle
            key={`point-${point.x}-${index}`}
            cx={scale.xFor(point.x)}
            cy={scale.yForValue(point.value)}
            r={3.5}
            className="fill-foreground"
            data-testid={`growth-point-${index}`}
          >
            <title>
              {`${point.value} ${unit} at ${axisTickLabel(point.x, axis)}`}
              {typeof point.zScore === "number" ? ` (${point.zScore.toFixed(2)} SD)` : ""}
            </title>
          </circle>
        ))}
      </svg>

      <figcaption className="mt-2 space-y-1 text-xs">
        {latest && (
          <p className="text-muted-foreground" data-testid="growth-latest-summary">
            Latest: {latest.value} {unit} at {axisTickLabel(latest.x, axis)}
            {typeof latest.zScore === "number" ? ` — ${latest.zScore.toFixed(2)} SD` : " — not scored"}
          </p>
        )}
        {citation && (
          <p className="text-muted-foreground" data-testid="growth-standard-citation">
            {citation}
          </p>
        )}
        {faltering.crossed && (
          <p className="font-medium text-red-600" data-testid="growth-faltering-callout">
            Trajectory has crossed a reference curve downward
            {typeof faltering.from === "number" && typeof faltering.to === "number"
              ? `, from ${faltering.from.toFixed(2)} to ${faltering.to.toFixed(2)} SD`
              : ""}
            . Weight can still be rising while a child loses ground against the standard.
          </p>
        )}
      </figcaption>
    </figure>
  );
}
