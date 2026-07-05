import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CoursePlayer } from "./CoursePlayer";

type AnyRecord = Record<string, unknown>;

let playback: AnyRecord | null;
let chapters: AnyRecord[] = [];
let bookmarks: AnyRecord[] = [];
let completionRules: AnyRecord[] = [];
const createBookmarkMutate = vi.fn();
const deleteBookmarkMutate = vi.fn();

const watchController: AnyRecord = {};

vi.mock("@/hooks/queries/useFundoPlayer", () => ({
  useMediaPlayback: () => ({ data: playback ? { data: playback } : undefined, isLoading: false }),
  useMediaChapters: () => ({ data: { data: { assetId: "asset-1", items: chapters } } }),
  useMediaBookmarks: () => ({ data: { data: { assetId: "asset-1", enrolmentId: "enr-1", items: bookmarks } } }),
  useCreateMediaBookmark: () => ({ mutate: createBookmarkMutate, isPending: false }),
  useDeleteMediaBookmark: () => ({ mutate: deleteBookmarkMutate }),
  useCourseCompletionRules: () => ({ data: { data: { courseId: "course-1", items: completionRules } } }),
}));

vi.mock("./useWatchProgress", () => ({
  useWatchProgress: () => watchController,
}));

function resetWatchController(overrides: AnyRecord = {}) {
  Object.assign(watchController, {
    progress: null,
    resumePositionSeconds: 0,
    completed: false,
    percent: 0,
    thresholdPercent: 90,
    onTimeUpdate: vi.fn(),
    flush: vi.fn(),
    ...overrides,
  });
}

describe("CoursePlayer", () => {
  beforeEach(() => {
    playback = {
      asset: {
        id: "asset-1",
        title: "Cold chain refresher — replay",
        mediaType: "VIDEO",
        storageKind: "DOCUMENT_OBJECT",
        storageRef: "doc-1",
        durationSeconds: 600,
        courseId: "course-1",
        transcript: null,
      },
      playbackUrl: "https://minio.example/signed/replay.mp4",
    };
    chapters = [];
    bookmarks = [];
    completionRules = [];
    createBookmarkMutate.mockReset();
    deleteBookmarkMutate.mockReset();
    resetWatchController();
  });

  it("renders the signed playback URL in the video surface", () => {
    render(<CoursePlayer assetId="asset-1" enrolmentId="enr-1" />);
    const video = screen.getByTestId("course-player-video") as HTMLVideoElement;
    expect(video).toHaveAttribute("src", "https://minio.example/signed/replay.mp4");
    expect(screen.getByTestId("player-controls")).toBeInTheDocument();
  });

  it("resumes from the furthest watched position on loadedmetadata", () => {
    resetWatchController({ resumePositionSeconds: 240 });
    render(<CoursePlayer assetId="asset-1" enrolmentId="enr-1" />);
    const video = screen.getByTestId("course-player-video") as HTMLVideoElement;
    Object.defineProperty(video, "duration", { value: 600, configurable: true });

    fireEvent.loadedMetadata(video);

    expect(video.currentTime).toBe(240);
  });

  it("does not resume when the recording was effectively finished", () => {
    resetWatchController({ resumePositionSeconds: 598 });
    render(<CoursePlayer assetId="asset-1" enrolmentId="enr-1" />);
    const video = screen.getByTestId("course-player-video") as HTMLVideoElement;
    Object.defineProperty(video, "duration", { value: 600, configurable: true });

    fireEvent.loadedMetadata(video);

    expect(video.currentTime).toBe(0);
  });

  it("shows in-progress completion state with the threshold", () => {
    resetWatchController({ percent: 45, thresholdPercent: 80 });
    render(<CoursePlayer assetId="asset-1" enrolmentId="enr-1" />);
    const banner = screen.getByTestId("player-completion-banner");
    expect(banner).toHaveAttribute("data-state", "in-progress");
    expect(banner.textContent).toContain("45%");
    expect(banner.textContent).toContain("80%");
  });

  it("fires the completion banner and quiz interrupt at the threshold", () => {
    resetWatchController({ completed: true, percent: 92 });
    completionRules = [{ ruleType: "QUIZ_REQUIRED", required: true }];
    render(<CoursePlayer assetId="asset-1" enrolmentId="enr-1" courseId="course-1" />);

    expect(screen.getByTestId("player-completion-banner")).toHaveAttribute("data-state", "completed");
    expect(screen.getByTestId("player-quiz-interrupt")).toBeInTheDocument();
    expect(screen.getByTestId("player-quiz-link")).toHaveAttribute("href", "/learning/enrolments/enr-1");
  });

  it("omits the quiz interrupt when the course has no quiz rule", () => {
    resetWatchController({ completed: true, percent: 95 });
    completionRules = [{ ruleType: "ATTENDANCE_THRESHOLD", required: true }];
    render(<CoursePlayer assetId="asset-1" enrolmentId="enr-1" courseId="course-1" />);

    expect(screen.queryByTestId("player-quiz-interrupt")).not.toBeInTheDocument();
  });

  it("renders chapters and jumps the playhead on click", () => {
    chapters = [
      { id: "c1", assetId: "asset-1", title: "Intro", startSeconds: 0, order: 0 },
      { id: "c2", assetId: "asset-1", title: "Storage temperatures", startSeconds: 180, order: 1 },
    ];
    render(<CoursePlayer assetId="asset-1" enrolmentId="enr-1" />);
    const video = screen.getByTestId("course-player-video") as HTMLVideoElement;

    const items = screen.getAllByTestId("player-chapter-item");
    expect(items).toHaveLength(2);
    fireEvent.click(items[1]);
    expect(video.currentTime).toBe(180);
  });

  it("adds and deletes bookmarks through the mutations", () => {
    bookmarks = [{ id: "b1", enrolmentId: "enr-1", assetId: "asset-1", positionSeconds: 42, note: "Check this" }];
    render(<CoursePlayer assetId="asset-1" enrolmentId="enr-1" />);

    fireEvent.click(screen.getByTestId("player-bookmark-add"));
    expect(createBookmarkMutate).toHaveBeenCalledWith(
      expect.objectContaining({ assetId: "asset-1", body: expect.objectContaining({ enrolmentId: "enr-1" }) }),
    );

    fireEvent.click(screen.getByTestId("player-bookmark-delete"));
    expect(deleteBookmarkMutate).toHaveBeenCalledWith(
      expect.objectContaining({ bookmarkId: "b1", enrolmentId: "enr-1", assetId: "asset-1" }),
    );
  });

  it("renders the transcript pane only when a transcript exists", () => {
    (playback!.asset as AnyRecord).transcript = "Facilitator: welcome to the cold chain refresher…";
    render(<CoursePlayer assetId="asset-1" enrolmentId="enr-1" />);
    expect(screen.getByTestId("player-transcript")).toBeInTheDocument();
  });

  it("shows an honest unavailable state when no playback URL could be issued", () => {
    playback = {
      asset: { id: "asset-1", storageKind: "DOCUMENT_OBJECT", storageRef: "doc-1" },
      playbackUrl: null,
      playbackUnavailableReason: "SIGNED_URL_UNAVAILABLE",
    };
    render(<CoursePlayer assetId="asset-1" enrolmentId="enr-1" />);
    expect(screen.getByTestId("course-player-unavailable")).toBeInTheDocument();
    expect(screen.queryByTestId("course-player-video")).not.toBeInTheDocument();
  });
});
