import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PartographLabourPlot, partographHoursSinceStart } from "./PartographLabourPlot";
import type { PartographPoint } from "@/hooks/queries/usePartograph";

describe("partographHoursSinceStart", () => {
  it("computes hours from session start to observation", () => {
    expect(
      partographHoursSinceStart("2026-04-11T08:00:00.000Z", "2026-04-11T10:00:00.000Z"),
    ).toBeCloseTo(2, 5);
  });

  it("returns null when timestamps missing", () => {
    expect(partographHoursSinceStart(undefined, "2026-04-11T10:00:00.000Z")).toBeNull();
  });
});

describe("PartographLabourPlot", () => {
  const startedAt = "2026-04-11T08:00:00.000Z";

  it("renders empty-state copy when there are no points", () => {
    render(<PartographLabourPlot startedAt={startedAt} points={[]} />);
    expect(screen.getByText(/No observations yet/)).toBeInTheDocument();
  });

  it("renders chart img with CTG disclaimer and dilation path when data exists", () => {
    const points: PartographPoint[] = [
      {
        id: "p1",
        observed_at: "2026-04-11T09:00:00.000Z",
        cervical_dilation_cm: 4,
        fetal_descent_fifths: 0,
        fetal_heart_rate_bpm: 140,
        contraction_frequency_10min: 3,
      },
      {
        id: "p2",
        observed_at: "2026-04-11T10:30:00.000Z",
        cervical_dilation_cm: 7,
        fetal_descent_fifths: 2,
        fetal_heart_rate_bpm: 152,
        contraction_frequency_10min: 5,
      },
    ];
    const { container } = render(<PartographLabourPlot startedAt={startedAt} points={points} />);
    const img = screen.getByRole("img", { name: /Partograph:/i });
    expect(img).toBeInTheDocument();
    expect(img.getAttribute("aria-label") ?? "").toMatch(/Continuous CTG is on the separate CTG panel/);
    const path = container.querySelector("path[stroke='#db2777']");
    expect(path).toBeTruthy();
    expect(path?.getAttribute("d")).toMatch(/^M/);
  });
});
