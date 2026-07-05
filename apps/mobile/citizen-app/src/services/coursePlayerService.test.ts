import { beforeEach, describe, expect, it, vi } from "vitest";

const getMock = vi.fn();
const postMock = vi.fn();

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: {
    get: (...args: unknown[]) => getMock(...args),
    post: (...args: unknown[]) => postMock(...args),
  },
}));

import {
  fetchCourseVideoLessons,
  fetchLessonMediaAsset,
  fetchMediaPlayback,
  upsertWatchProgress,
} from "./coursePlayerService";

describe("coursePlayerService (W4 watch contract)", () => {
  beforeEach(() => {
    getMock.mockReset();
    postMock.mockReset();
  });

  it("extracts VIDEO lessons from the course structure in order", async () => {
    getMock.mockResolvedValue({
      data: {
        data: {
          structure: {
            modules: [
              {
                lessons: [
                  { id: "l1", title: "Intro video", contentType: "VIDEO" },
                  { id: "l2", title: "Reading", contentType: "TEXT" },
                ],
              },
              { lessons: [{ id: "l3", title: "Demo video", contentType: "VIDEO" }] },
            ],
          },
        },
      },
    });

    const lessons = await fetchCourseVideoLessons("course-1");

    expect(getMock).toHaveBeenCalledWith("/internal/v1/learning/v11/courses/course-1/structure");
    expect(lessons).toEqual([
      { id: "l1", title: "Intro video" },
      { id: "l3", title: "Demo video" },
    ]);
  });

  it("resolves lesson media assets and playback URLs through the BFF routes", async () => {
    getMock.mockResolvedValueOnce({ data: { data: { asset: { id: "a1", storageKind: "DOCUMENT_OBJECT" } } } });
    const asset = await fetchLessonMediaAsset("l1");
    expect(asset?.id).toBe("a1");

    getMock.mockResolvedValueOnce({
      data: { data: { asset: { id: "a1" }, playbackUrl: "https://minio.example/signed.mp4" } },
    });
    const playback = await fetchMediaPlayback("a1", "enr-1");
    expect(getMock).toHaveBeenLastCalledWith(
      "/internal/v1/learning/v11/media/a1/playback?enrolmentId=enr-1",
    );
    expect(playback.playbackUrl).toBe("https://minio.example/signed.mp4");
  });

  it("upserts watch progress with the monotonic payload shape", async () => {
    postMock.mockResolvedValue({
      data: { data: { watchProgress: { percent: 91, completedAt: "2026-07-05T10:00:00Z", thresholdPercent: 90 } } },
    });

    const wp = await upsertWatchProgress("a1", {
      enrolmentId: "enr-1",
      positionSeconds: 540,
      watchedSeconds: 545,
      durationSeconds: 600,
    });

    expect(postMock).toHaveBeenCalledWith(
      "/internal/v1/learning/v11/media/a1/watch-progress",
      { enrolmentId: "enr-1", positionSeconds: 540, watchedSeconds: 545, durationSeconds: 600 },
    );
    expect(wp?.percent).toBe(91);
    expect(wp?.completedAt).toBeTruthy();
  });
});
