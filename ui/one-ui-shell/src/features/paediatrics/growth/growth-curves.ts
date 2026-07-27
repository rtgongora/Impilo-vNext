/**
 * Reference-curve geometry for a growth chart.
 *
 * The z-scores themselves are computed server-side and stored with the measurement, so
 * they carry the standard and engine version that produced them and cannot drift. What
 * the client needs in addition is the shape of the reference curves to draw the point
 * against — a number without a curve tells a clinician the child is at -2.1 SD but not
 * whether the trajectory is falling away or tracking steadily, which is the question a
 * growth chart exists to answer.
 *
 * The horizontal axis is not always age. A child born preterm is read against the Fenton
 * 2013 chart, whose axis is completed postmenstrual weeks, so the geometry here is written
 * in terms of a neutral axis position and the axis kind travels with the data. Labelling
 * a postmenstrual week as though it were a day of age would misplace every point on the
 * chart by the length of a pregnancy.
 *
 * These helpers are pure and exported so the scale arithmetic can be unit-tested without
 * rendering, following the pattern set by the partograph plot.
 */

/** The z-score curves a growth chart conventionally shows. */
export const REFERENCE_Z_SCORES = [-3, -2, 0, 2, 3] as const;

export type ReferenceZ = (typeof REFERENCE_Z_SCORES)[number];

/** What the horizontal axis measures, as declared by the standard being plotted. */
export type GrowthAxis = "age_days" | "postmenstrual_weeks";

export interface CurvePoint {
  /** Position on the standard's own axis: days of age, or completed postmenstrual weeks. */
  x: number;
  value: number;
}

export interface ReferenceCurve {
  z: ReferenceZ;
  points: CurvePoint[];
}

export interface PlottedMeasurement {
  x: number;
  value: number;
  zScore?: number | null;
  measuredAt?: string;
}

export interface ChartScale {
  xFor: (x: number) => number;
  yForValue: (value: number) => number;
  minX: number;
  maxX: number;
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
  const positions: number[] = [];
  const values: number[] = [];

  curves.forEach((curve) =>
    curve.points.forEach((point) => {
      positions.push(point.x);
      values.push(point.value);
    }),
  );
  measurements.forEach((m) => {
    positions.push(m.x);
    values.push(m.value);
  });

  const minX = positions.length ? Math.min(...positions) : 0;
  const maxX = positions.length ? Math.max(...positions) : 1;
  const rawMin = values.length ? Math.min(...values) : 0;
  const rawMax = values.length ? Math.max(...values) : 1;

  // A little headroom so a point sitting exactly on the extreme curve is not drawn on
  // the axis line itself.
  const pad = (rawMax - rawMin) * 0.06 || 1;
  const minValue = rawMin - pad;
  const maxValue = rawMax + pad;

  const xSpan = maxX - minX || 1;
  const valueSpan = maxValue - minValue || 1;
  const plotWidth = box.width - box.left - box.right;
  const plotHeight = box.height - box.top - box.bottom;

  return {
    minX,
    maxX,
    minValue,
    maxValue,
    xFor: (x) => box.left + ((x - minX) / xSpan) * plotWidth,
    // SVG y grows downward; a larger measurement must sit higher on the chart.
    yForValue: (value) => box.top + plotHeight - ((value - minValue) / valueSpan) * plotHeight,
  };
}

export function polylinePoints(points: { x: number; value: number }[], scale: ChartScale): string {
  return points
    .map((point) => `${scale.xFor(point.x).toFixed(1)},${scale.yForValue(point.value).toFixed(1)}`)
    .join(" ");
}

/** Axis tick labels in the unit a clinician thinks in at that point on the chart. */
export function axisTickLabel(x: number, axis: GrowthAxis = "age_days"): string {
  if (axis === "postmenstrual_weeks") {
    // Preterm care counts in weeks since the last menstrual period, and says so, because
    // "30 weeks" means something entirely different from "30 weeks old".
    return `${Math.round(x)}w PMA`;
  }
  if (x < 60) {
    return `${Math.round(x)}d`;
  }
  if (x < 730) {
    return `${Math.round(x / 30.4375)}m`;
  }
  // Round to one decimal, then drop a trailing ".0": a two-year-old is "2y", not "2.0y".
  const years = Math.round((x / 365.25) * 10) / 10;
  return Number.isInteger(years) ? `${years}y` : `${years.toFixed(1)}y`;
}

export function axisTicks(scale: ChartScale, count = 6): number[] {
  const span = scale.maxX - scale.minX;
  if (span <= 0) return [scale.minX];
  return Array.from({ length: count }, (_, i) => scale.minX + (span * i) / (count - 1));
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
    .sort((a, b) => a.x - b.x);
  if (scored.length < 2) {
    return { crossed: false };
  }
  const first = scored[0].zScore as number;
  const last = scored[scored.length - 1].zScore as number;

  const crossedLine = REFERENCE_Z_SCORES.some((z) => first > z && last <= z);
  return crossedLine ? { crossed: true, from: first, to: last } : { crossed: false, from: first, to: last };
}
