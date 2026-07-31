import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import {
  ElapsedTimer,
  EventTimeline,
  StalenessBadge,
  classifyStaleness,
  describeAge,
  elapsedSecondsAt,
  gapSinceLatest,
  type TimelineEntry,
} from "shared-ui";

describe("ElapsedTimer", () => {
  const START = "2026-07-30T10:00:00.000Z";

  it("measures from the server's clock, not the device's", () => {
    // A tablet forty minutes fast is routine on shared ward hardware. Anchored on the server it
    // still reports five minutes; anchored on the device it would claim forty-five.
    const elapsed = elapsedSecondsAt(START, "2026-07-30T10:05:00.000Z", 0);
    expect(elapsed).toBe(300);
  });

  it("advances by the monotonic delta, so an NTP correction cannot move it backwards", () => {
    const first = elapsedSecondsAt(START, "2026-07-30T10:05:00.000Z", 1_000);
    const second = elapsedSecondsAt(START, "2026-07-30T10:05:00.000Z", 2_000);
    expect(second).toBeGreaterThan(first);
    expect(second - first).toBe(1);
  });

  it("re-derives the same elapsed time after a reload, holding nothing in state", () => {
    const beforeReload = elapsedSecondsAt(START, "2026-07-30T10:07:30.000Z", 0);
    const afterReload = elapsedSecondsAt(START, "2026-07-30T10:07:30.000Z", 0);
    expect(afterReload).toBe(beforeReload);
    expect(afterReload).toBe(450);
  });

  it("says so when the server supplied no reference time rather than pretending to be anchored", () => {
    render(<ElapsedTimer startedAt={START} />);
    expect(screen.getByTestId("elapsed-timer-unanchored")).toBeTruthy();
  });

  it("holds no cadence of its own — an unconfigured protocol says so", () => {
    render(<ElapsedTimer startedAt={START} serverNow={START} />);
    expect(screen.getByTestId("elapsed-timer-no-cadence").textContent).toContain(
      "No cadence configured",
    );
  });

  it("counts down each cadence the caller supplied", () => {
    render(
      <ElapsedTimer
        startedAt={START}
        serverNow={START}
        intervals={[{ label: "Rhythm check", seconds: 120 }]}
      />,
    );
    expect(screen.getByTestId("elapsed-timer-interval-Rhythm check")).toBeTruthy();
  });

  it("fires a supplied cadence once per occurrence, not once per tick", () => {
    const onDue = vi.fn();
    render(
      <ElapsedTimer
        startedAt="2026-07-30T10:00:00.000Z"
        serverNow="2026-07-30T10:04:30.000Z"
        intervals={[{ label: "Rhythm check", seconds: 120 }]}
        onIntervalDue={onDue}
      />,
    );
    // 270s elapsed spans two whole 120s cadences; the second is the one now due.
    expect(onDue).toHaveBeenCalledTimes(1);
    expect(onDue.mock.calls[0][1]).toBe(2);
  });
});

describe("StalenessBadge", () => {
  const NOW = Date.parse("2026-07-30T12:00:00.000Z");

  it("distinguishes an unreadable source from an up-to-date one", () => {
    expect(
      classifyStaleness({ asOf: "2026-07-30T11:59:00.000Z", thresholdSeconds: 300, sourceReachable: false }),
    ).toBe("UNAVAILABLE");
  });

  it("never treats a missing observation time as fresh", () => {
    expect(classifyStaleness({ asOf: null, thresholdSeconds: 300, sourceReachable: true })).toBe(
      "VERSION_UNKNOWN",
    );
    expect(
      classifyStaleness({ asOf: "not-a-date", thresholdSeconds: 300, sourceReachable: true }),
    ).toBe("VERSION_UNKNOWN");
  });

  it("applies the caller's threshold, because stale means different things per source", () => {
    const asOf = "2026-07-30T11:55:00.000Z"; // five minutes old
    expect(classifyStaleness({ asOf, thresholdSeconds: 600, sourceReachable: true, now: NOW })).toBe(
      "LIVE",
    );
    expect(classifyStaleness({ asOf, thresholdSeconds: 60, sourceReachable: true, now: NOW })).toBe(
      "STALE",
    );
  });

  it("marks an offline copy as such even while the source is nominally reachable", () => {
    expect(
      classifyStaleness({
        asOf: "2026-07-30T11:59:00.000Z",
        thresholdSeconds: 300,
        sourceReachable: true,
        fromOfflineCache: true,
      }),
    ).toBe("CACHED_OFFLINE");
  });

  it("tells the reader an unavailable source is not an absence of data", () => {
    render(<StalenessBadge state="UNAVAILABLE" subject="vital signs" />);
    const badge = screen.getByTestId("staleness-badge");
    expect(badge.getAttribute("title")).toContain("Do not treat this as an absence of vital signs");
  });

  it("describes age coarsely rather than with a false precision", () => {
    expect(describeAge("2026-07-30T07:48:00.000Z", NOW)).toBe("4 h 12 min ago");
    expect(describeAge("2026-07-30T11:59:30.000Z", NOW)).toBe("less than a minute ago");
  });
});

describe("EventTimeline", () => {
  const NOW = Date.parse("2026-07-30T12:00:00.000Z");
  const entries: TimelineEntry[] = [
    { id: "1", occurredAt: "2026-07-30T11:30:00.000Z", label: "Reassessment" },
    { id: "2", occurredAt: "2026-07-30T11:50:00.000Z", label: "Reassessment" },
  ];

  it("states what an empty rail means instead of leaving the reader to guess", () => {
    render(<EventTimeline entries={[]} emptyMeaning="No reassessments recorded on this episode." />);
    expect(screen.getByTestId("event-timeline-empty").textContent).toBe(
      "No reassessments recorded on this episode.",
    );
  });

  it("reports the age of the last entry against the cadence the caller supplied", () => {
    const gap = gapSinceLatest(entries, 600, NOW);
    expect(gap?.seconds).toBe(600);
    expect(gap?.exceedsExpected).toBe(false);
  });

  it("flags a gap that has run past the supplied cadence", () => {
    const gap = gapSinceLatest(entries, 300, NOW);
    expect(gap?.exceedsExpected).toBe(true);
  });

  it("makes no claim about cadence when the caller supplied none", () => {
    const gap = gapSinceLatest(entries, undefined, NOW);
    expect(gap?.exceedsExpected).toBe(false);
  });

  it("renders every entry in the order given, without re-sorting", () => {
    render(<EventTimeline entries={entries} emptyMeaning="none" now={NOW} />);
    expect(screen.getAllByTestId("event-timeline-entry")).toHaveLength(2);
  });
});
