import { renderHook, act } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useWatchProgress } from "./useWatchProgress";

const mutateMock = vi.fn();
let serverProgress: Record<string, unknown> | null = null;

vi.mock("@/hooks/queries/useFundoPlayer", () => ({
  useMediaWatchProgress: () => ({ data: serverProgress ? { data: { watchProgress: serverProgress } } : undefined }),
  useUpsertWatchProgress: () => ({ mutate: mutateMock }),
}));

const ASSET = "asset-1";
const ENROLMENT = "enrolment-1";

describe("useWatchProgress", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mutateMock.mockReset();
    serverProgress = null;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("resumes from the furthest watched position reported by the server", () => {
    serverProgress = {
      enrolmentId: ENROLMENT,
      assetId: ASSET,
      watchedSeconds: 300,
      furthestPositionSeconds: 480,
      lastPositionSeconds: 120,
      percent: 30,
      thresholdPercent: 90,
      completedAt: null,
      durationSeconds: 1000,
      id: "wp-1",
    };
    const { result } = renderHook(() => useWatchProgress({ assetId: ASSET, enrolmentId: ENROLMENT }));

    expect(result.current.resumePositionSeconds).toBe(480);
    expect(result.current.percent).toBe(30);
    expect(result.current.completed).toBe(false);
    expect(result.current.thresholdPercent).toBe(90);
  });

  it("debounces progress upserts to at most one per flush interval while playing", () => {
    const { result } = renderHook(() =>
      useWatchProgress({ assetId: ASSET, enrolmentId: ENROLMENT, flushIntervalMs: 10_000 }),
    );

    // Simulate 12 seconds of playing ticks (timeupdate ~every second).
    for (let s = 1; s <= 12; s += 1) {
      act(() => {
        vi.advanceTimersByTime(1000);
        result.current.onTimeUpdate(s, 600, true);
      });
    }

    // 12s of play with a 10s debounce → exactly one flush.
    expect(mutateMock).toHaveBeenCalledTimes(1);
    const [{ assetId, body }] = mutateMock.mock.calls[0];
    expect(assetId).toBe(ASSET);
    expect(body.enrolmentId).toBe(ENROLMENT);
    expect(body.durationSeconds).toBe(600);
    expect(body.watchedSeconds).toBeGreaterThanOrEqual(9);
    expect(body.watchedSeconds).toBeLessThanOrEqual(12);
  });

  it("does not accumulate watch time while paused or scrubbing", () => {
    const { result } = renderHook(() =>
      useWatchProgress({ assetId: ASSET, enrolmentId: ENROLMENT, flushIntervalMs: 5_000 }),
    );

    // Paused ticks — position jumps (scrubbing) but playing=false.
    for (let s = 0; s <= 6; s += 1) {
      act(() => {
        vi.advanceTimersByTime(1000);
        result.current.onTimeUpdate(s * 60, 600, false);
      });
    }
    expect(mutateMock).not.toHaveBeenCalled();
  });

  it("flushes pending progress on unmount", () => {
    const { result, unmount } = renderHook(() =>
      useWatchProgress({ assetId: ASSET, enrolmentId: ENROLMENT, flushIntervalMs: 60_000 }),
    );

    act(() => {
      vi.advanceTimersByTime(1000);
      result.current.onTimeUpdate(1, 600, true);
      vi.advanceTimersByTime(1000);
      result.current.onTimeUpdate(2, 600, true);
    });
    expect(mutateMock).not.toHaveBeenCalled(); // still inside the debounce window

    unmount();
    expect(mutateMock).toHaveBeenCalledTimes(1);
  });

  it("fires completed when the threshold milestone comes back from the server", () => {
    const { result } = renderHook(() =>
      useWatchProgress({ assetId: ASSET, enrolmentId: ENROLMENT, flushIntervalMs: 1_000 }),
    );

    mutateMock.mockImplementation((_vars, opts) => {
      opts?.onSuccess?.({
        data: {
          watchProgress: {
            enrolmentId: ENROLMENT,
            assetId: ASSET,
            percent: 92,
            completedAt: "2026-07-05T10:00:00Z",
            thresholdPercent: 90,
            watchedSeconds: 552,
            durationSeconds: 600,
            furthestPositionSeconds: 552,
            lastPositionSeconds: 552,
            id: "wp-1",
          },
        },
      });
    });

    act(() => {
      vi.advanceTimersByTime(1000);
      result.current.onTimeUpdate(551, 600, true);
      vi.advanceTimersByTime(1500);
      result.current.onTimeUpdate(552, 600, true);
    });

    expect(result.current.completed).toBe(true);
    expect(result.current.percent).toBe(92);
  });
});
