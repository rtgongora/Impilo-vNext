/**
 * Reference-curve geometry for the WHO growth chart.
 *
 * The z-scores themselves are computed server-side and stored with the measurement, so
 * they carry the standard and engine version that produced them and cannot drift. What
 * the client needs in addition is the shape of the reference curves to draw the point
 * against — a number without a curve tells a clinician the child is at -2.1 SD but not
 * whether the trajectory is falling away or tracking steadily, which is the question a
 * growth chart exists to answer.
 *
 * These helpers are pure and exported so the scale arithmetic can be unit-tested without
 * rendering, following the pattern set by the partograph plot.
 */

/** The z-score curves a WHO chart conventionally shows. */
export const REFERENCE_Z_SCORES = [-3, -2, 0, 2, 3] as const;

export type ReferenceZ = (typeof REFERENCE_Z_SCORES)[number];

export interface CurvePoint {
  ageDays: number;
  value: number;
}

export interface ReferenceCurve {
  z: ReferenceZ;
  points: CurvePoint[];
}

export interface PlottedMeasurement {
  ageDays: number;
  value: number;
  zScore?: number | null;
  measuredAt?: string;
}

export interface ChartScale {
  xForAgeDays: (ageDays: number) => number;
  yForValue: (value: number) => number;
  minAgeDays: number;
  maxAgeDays: number;
  minValue: number;
  maxValue: number;
}

/**
 * Builds the plot scale from the curves and the child's own points.
 *
 * The domain always includes every plotted measurement, even one far outside the
 * reference range: a chart that silently clips an implausible or extreme value hides
 * exactly the reading a clinician most needs to see.
 */
export function buildScale(
  curves: ReferenceCurve[],
  measurements: PlottedMeasurement[],
  box: { left: number; right: number; top: number; bottom: number; width: number; height: number },
): ChartScale {
  const ages: number[] = [];
  const values: number[] = [];

  curves.forEach((curve) =>
    curve.points.forEach((point) => {
      ages.push(point.ageDays);
      values.push(point.value);
    }),
  );
  measurements.forEach((m) => {
    ages.push(m.ageDays);
    values.push(m.value);
  });

  const minAgeDays = ages.length ? Math.min(...ages) : 0;
  const maxAgeDays = ages.length ? Math.max(...ages) : 1;
  const rawMin = values.length ? Math.min(...values) : 0;
  const rawMax = values.length ? Math.max(...values) : 1;

  // A little headroom so a point sitting exactly on the extreme curve is not drawn on
  // the axis line itself.
  const pad = (rawMax - rawMin) * 0.06 || 1;
  const minValue = rawMin - pad;
  const maxValue = rawMax + pad;

  const ageSpan = maxAgeDays - minAgeDays || 1;
  const valueSpan = maxValue - minValue || 1;
  const plotWidth = box.width - box.left - box.right;
  const plotHeight = box.height - box.top - box.bottom;

  return {
    minAgeDays,
    maxAgeDays,
    minValue,
    maxValue,
    xForAgeDays: (ageDays) => box.left + ((ageDays - minAgeDays) / ageSpan) * plotWidth,
    // SVG y grows downward; a larger measurement must sit higher on the chart.
    yForValue: (value) => box.top + plotHeight - ((value - minValue) / valueSpan) * plotHeight,
  };
}

export function polylinePoints(
  points: { ageDays: number; value: number }[],
  scale: ChartScale,
): string {
  return points
    .map((point) => `${scale.xForAgeDays(point.ageDays).toFixed(1)},${scale.yForValue(point.value).toFixed(1)}`)
    .join(" ");
}

/** Age tick labels in the unit a clinician thinks in at that age. */
export function ageTickLabel(ageDays: number): string {
  if (ageDays < 60) {
    return `${Math.round(ageDays)}d`;
  }
  if (ageDays < 730) {
    return `${Math.round(ageDays / 30.4375)}m`;
  }
  // Round to one decimal, then drop a trailing ".0": a two-year-old is "2y", not "2.0y".
  const years = Math.round((ageDays / 365.25) * 10) / 10;
  return Number.isInteger(years) ? `${years}y` : `${years.toFixed(1)}y`;
}

export function ageTicks(scale: ChartScale, count = 6): number[] {
  const span = scale.maxAgeDays - scale.minAgeDays;
  if (span <= 0) return [scale.minAgeDays];
  return Array.from({ length: count }, (_, i) => scale.minAgeDays + (span * i) / (count - 1));
}

export function valueTicks(scale: ChartScale, count = 5): number[] {
  const span = scale.maxValue - scale.minValue;
  if (span <= 0) return [scale.minValue];
  return Array.from({ length: count }, (_, i) => scale.minValue + (span * i) / (count - 1));
}

/**
 * Detects a downward crossing of a reference curve between consecutive measurements.
 *
 * This is the signal a growth chart is read for: a child whose weight is still rising can
 * be crossing curves downward, and that is growth faltering even though every individual
 * number looks like a gain.
 */
export function detectDownwardCrossing(measurements: PlottedMeasurement[]): {
  crossed: boolean;
  from?: number;
  to?: number;
} {
  const scored = measurements
    .filter((m) => typeof m.zScore === "number")
    .sort((a, b) => a.ageDays - b.ageDays);
  if (scored.length < 2) {
    return { crossed: false };
  }
  const first = scored[0].zScore as number;
  const last = scored[scored.length - 1].zScore as number;

  const crossedLine = REFERENCE_Z_SCORES.some((z) => first > z && last <= z);
  return crossedLine ? { crossed: true, from: first, to: last } : { crossed: false, from: first, to: last };
}
