import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CtgTracePlot } from "./CtgTracePlot";
import { ctgTraceOriginMs, ctgTimeWindowSec, flattenCtgChannelSamples } from "./ctgTraceUtils";
import type { CtgTraceChunk } from "@/hooks/queries/useFetalMonitoring";

describe("ctgTraceUtils", () => {
  const chunks: CtgTraceChunk[] = [
    {
      id: "c1",
      channel: "FHR",
      started_at: "2026-04-11T10:00:00.000Z",
      sample_rate_hz: 2,
      samples: [120, 122],
    },
    {
      id: "c2",
      channel: "FHR",
      started_at: "2026-04-11T10:00:01.000Z",
      sample_rate_hz: 2,
      samples: [125],
    },
  ];

  it("ctgTraceOriginMs picks earliest chunk start", () => {
    expect(ctgTraceOriginMs(chunks)).toBe(Date.parse("2026-04-11T10:00:00.000Z"));
  });

  it("flattenCtgChannelSamples builds timeline from origin", () => {
    const origin = ctgTraceOriginMs(chunks)!;
    const pts = flattenCtgChannelSamples(chunks, "FHR", origin);
    expect(pts.length).toBe(3);
    expect(pts[0].tSec).toBeCloseTo(0, 5);
    expect(pts[0].value).toBe(120);
    expect(pts[1].tSec).toBeCloseTo(0.5, 5);
    expect(pts[2].tSec).toBeCloseTo(1, 5);
  });

  it("ctgTimeWindowSec pads empty window", () => {
    expect(ctgTimeWindowSec([[]])).toEqual([0, 60]);
  });
});

describe("CtgTracePlot", () => {
  it("shows empty message when no samples", () => {
    render(<CtgTracePlot fhr={[]} toco={[]} maternalHr={[]} annotationMarks={[]} />);
    expect(screen.getByText(/No trace chunks yet/)).toBeInTheDocument();
  });

  it("renders img with polling disclaimer when FHR points exist", () => {
    const fhr = [
      { tSec: 0, value: 130 },
      { tSec: 1, value: 135 },
    ];
    render(<CtgTracePlot fhr={fhr} toco={[]} maternalHr={[]} annotationMarks={[]} />);
    const img = screen.getByRole("img", { name: /CTG trace preview/i });
    expect(img.getAttribute("aria-label") ?? "").toMatch(/HTTP polling/);
    expect(img.getAttribute("aria-label") ?? "").toMatch(/not a live websocket/);
  });
});
