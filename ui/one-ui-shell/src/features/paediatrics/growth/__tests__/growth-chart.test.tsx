import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { GrowthStandardsChart } from "../GrowthStandardsChart";
import {
  axisTickLabel,
  buildScale,
  detectDownwardCrossing,
  polylinePoints,
  REFERENCE_Z_SCORES,
  type PlottedMeasurement,
  type ReferenceCurve,
} from "../growth-curves";

const BOX = { left: 50, right: 20, top: 20, bottom: 40, width: 720, height: 380 };

const curves: ReferenceCurve[] = [
  { z: -3, points: [{ x: 0, value: 2.1 }, { x: 365, value: 6.9 }] },
  { z: -2, points: [{ x: 0, value: 2.5 }, { x: 365, value: 7.7 }] },
  { z: 0, points: [{ x: 0, value: 3.3 }, { x: 365, value: 9.6 }] },
  { z: 2, points: [{ x: 0, value: 4.4 }, { x: 365, value: 12.0 }] },
  { z: 3, points: [{ x: 0, value: 5.0 }, { x: 365, value: 13.3 }] },
];

describe("growth chart scale", () => {
  it("maps a larger measurement higher on the chart", () => {
    // SVG y grows downward, so this is the one arithmetic mistake that would silently
    // invert every chart in the platform.
    const scale = buildScale(curves, [], BOX);
    expect(scale.yForValue(10)).toBeLessThan(scale.yForValue(3));
  });

  it("maps an older age further right", () => {
    const scale = buildScale(curves, [], BOX);
    expect(scale.xFor(365)).toBeGreaterThan(scale.xFor(0));
  });

  it("keeps the plot inside its margins", () => {
    const scale = buildScale(curves, [], BOX);
    expect(scale.xFor(0)).toBeCloseTo(BOX.left, 1);
    expect(scale.xFor(365)).toBeCloseTo(BOX.width - BOX.right, 1);
  });

  it("widens the domain to include a measurement outside the reference range", () => {
    // A chart that clips an extreme reading hides exactly the point worth seeing.
    const extreme: PlottedMeasurement[] = [{ x: 200, value: 1.2 }];
    const scale = buildScale(curves, extreme, BOX);
    expect(scale.minValue).toBeLessThan(1.2);
    const y = scale.yForValue(1.2);
    expect(y).toBeLessThanOrEqual(BOX.height - BOX.bottom);
    expect(y).toBeGreaterThanOrEqual(BOX.top);
  });

  it("renders polyline points as SVG coordinate pairs", () => {
    const scale = buildScale(curves, [], BOX);
    const points = polylinePoints([{ x: 0, value: 3.3 }], scale);
    expect(points).toMatch(/^\d+\.\d,\d+\.\d$/);
  });
});

describe("age tick labels", () => {
  it("uses the unit a clinician thinks in at that age", () => {
    expect(axisTickLabel(20)).toBe("20d");
    expect(axisTickLabel(180)).toBe("6m");
    expect(axisTickLabel(730)).toBe("2y");
  });

  it("labels a preterm chart in postmenstrual weeks, never as days of age", () => {
    // "30 weeks" and "30 weeks old" are different children. The unit has to say which.
    expect(axisTickLabel(30, "postmenstrual_weeks")).toBe("30w PMA");
    expect(axisTickLabel(30, "age_days")).toBe("30d");
  });
});

describe("downward crossing detection", () => {
  it("flags a trajectory that crosses a curve downward", () => {
    const result = detectDownwardCrossing([
      { x: 180, value: 7.2, zScore: -1.4 },
      { x: 300, value: 7.9, zScore: -2.2 },
    ]);
    expect(result.crossed).toBe(true);
    expect(result.from).toBe(-1.4);
    expect(result.to).toBe(-2.2);
  });

  it("does not flag a steady trajectory", () => {
    expect(
      detectDownwardCrossing([
        { x: 180, value: 7.2, zScore: -0.5 },
        { x: 300, value: 8.4, zScore: -0.4 },
      ]).crossed,
    ).toBe(false);
  });

  it("does not flag an improving trajectory that crosses upward", () => {
    expect(
      detectDownwardCrossing([
        { x: 180, value: 6.4, zScore: -2.4 },
        { x: 300, value: 8.4, zScore: -1.6 },
      ]).crossed,
    ).toBe(false);
  });

  it("needs two scored points before it claims anything", () => {
    expect(detectDownwardCrossing([{ x: 180, value: 7.2, zScore: -2.9 }]).crossed).toBe(false);
    expect(detectDownwardCrossing([]).crossed).toBe(false);
  });
});

describe("GrowthStandardsChart", () => {
  const measurements: PlottedMeasurement[] = [
    { x: 180, value: 7.2, zScore: -1.4 },
    { x: 300, value: 7.9, zScore: -2.2 },
  ];

  it("draws every reference curve and the child's trajectory", () => {
    render(
      <GrowthStandardsChart title="Weight for age" unit="kg" curves={curves} measurements={measurements} />,
    );

    REFERENCE_Z_SCORES.forEach((z) => {
      expect(screen.getByTestId(`growth-curve-${z}`)).toBeTruthy();
    });
    expect(screen.getByTestId("growth-trajectory")).toBeTruthy();
    expect(screen.getByTestId("growth-point-0")).toBeTruthy();
    expect(screen.getByTestId("growth-point-1")).toBeTruthy();
  });

  it("calls out faltering in the terms a clinician would use", () => {
    render(
      <GrowthStandardsChart title="Weight for age" unit="kg" curves={curves} measurements={measurements} />,
    );
    const callout = screen.getByTestId("growth-faltering-callout").textContent ?? "";
    expect(callout).toContain("crossed a reference curve downward");
    // The insight the chart exists to give: rising weight, falling position.
    expect(callout).toContain("Weight can still be rising");
  });

  it("does not cry faltering on a steady child", () => {
    render(
      <GrowthStandardsChart
        title="Weight for age"
        unit="kg"
        curves={curves}
        measurements={[
          { x: 180, value: 7.6, zScore: -0.6 },
          { x: 300, value: 9.0, zScore: -0.5 },
        ]}
      />,
    );
    expect(screen.queryByTestId("growth-faltering-callout")).toBeNull();
  });

  it("says a measurement is not scored rather than implying a score", () => {
    render(
      <GrowthStandardsChart
        title="Weight for age"
        unit="kg"
        curves={curves}
        measurements={[{ x: 2000, value: 22 }]}
      />,
    );
    expect(screen.getByTestId("growth-latest-summary").textContent).toContain("not scored");
  });

  it("explains itself rather than drawing an empty chart when curves are missing", () => {
    render(<GrowthStandardsChart title="Weight for age" unit="kg" curves={[]} measurements={measurements} />);
    expect(screen.getByTestId("growth-standards-chart-unavailable")).toBeTruthy();
    expect(screen.queryByTestId("growth-trajectory")).toBeNull();
  });

  it("renders with a single measurement and no trajectory line", () => {
    render(
      <GrowthStandardsChart
        title="Weight for age"
        unit="kg"
        curves={curves}
        measurements={[{ x: 180, value: 7.2, zScore: -1.4 }]}
      />,
    );
    expect(screen.getByTestId("growth-point-0")).toBeTruthy();
    expect(screen.queryByTestId("growth-trajectory")).toBeNull();
  });
});
