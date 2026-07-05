"use client";

import { useCallback, useRef, useState } from "react";
import { Loader2, VideoOff } from "lucide-react";
import {
  useCreateMediaBookmark,
  useDeleteMediaBookmark,
  useMediaBookmarks,
  useMediaChapters,
  useMediaPlayback,
  type FundoMediaAsset,
} from "@/hooks/queries/useFundoPlayer";
import { useWatchProgress } from "./useWatchProgress";
import { PlaybackControls } from "./PlaybackControls";
import { ChapterSidebar } from "./ChapterSidebar";
import { BookmarkList } from "./BookmarkList";
import { TranscriptPane } from "./TranscriptPane";
import { CompletionBanner } from "./CompletionBanner";
import { QuizInterrupt } from "./QuizInterrupt";

export interface CoursePlayerProps {
  assetId: string;
  enrolmentId: string;
  /** Course context for the QuizInterrupt (falls back to the asset's courseId). */
  courseId?: string;
  title?: string;
}

/**
 * Recorded-media course player (LEARNING_RECORDING W4): signed-URL playback of
 * governed media assets with debounced watch-progress tracking, resume from
 * the furthest watched position, chapters, learner bookmarks, transcript pane
 * and completion feedback wired to the WATCH_THRESHOLD rule.
 */
export function CoursePlayer({ assetId, enrolmentId, courseId, title }: CoursePlayerProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const resumedRef = useRef(false);
  const [playing, setPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState<number | null>(null);
  const [playbackRate, setPlaybackRate] = useState(1);

  const { data: playbackData, isLoading } = useMediaPlayback(assetId, enrolmentId);
  const { data: chaptersData } = useMediaChapters(assetId);
  const { data: bookmarksData } = useMediaBookmarks(assetId, enrolmentId);
  const createBookmark = useCreateMediaBookmark();
  const deleteBookmark = useDeleteMediaBookmark();
  const watch = useWatchProgress({ assetId, enrolmentId });

  const playback = playbackData?.data;
  const asset: FundoMediaAsset | undefined = playback?.asset;
  const playbackUrl = playback?.playbackUrl ?? null;
  const effectiveCourseId = courseId ?? asset?.courseId ?? undefined;
  const chapters = chaptersData?.data?.items ?? [];
  const bookmarks = bookmarksData?.data?.items ?? [];

  const seekTo = useCallback((seconds: number) => {
    const video = videoRef.current;
    if (video) {
      video.currentTime = seconds;
      setCurrentTime(seconds);
    }
  }, []);

  const handleLoadedMetadata = useCallback(() => {
    const video = videoRef.current;
    if (!video) return;
    setDuration(Number.isFinite(video.duration) ? video.duration : null);
    // Resume once per mount from the furthest watched position (skip when the
    // recording was effectively finished so replays start from the top).
    if (!resumedRef.current) {
      resumedRef.current = true;
      const resume = watch.resumePositionSeconds;
      if (resume > 5 && Number.isFinite(video.duration) && resume < video.duration - 5) {
        video.currentTime = resume;
        setCurrentTime(resume);
      }
    }
  }, [watch.resumePositionSeconds]);

  const handleTimeUpdate = useCallback(() => {
    const video = videoRef.current;
    if (!video) return;
    setCurrentTime(video.currentTime);
    watch.onTimeUpdate(
      video.currentTime,
      Number.isFinite(video.duration) ? video.duration : null,
      !video.paused && !video.ended,
    );
  }, [watch]);

  const handlePlayPause = useCallback(() => {
    const video = videoRef.current;
    if (!video) return;
    if (video.paused) {
      void video.play();
    } else {
      video.pause();
    }
  }, []);

  const handleRateChange = useCallback((rate: number) => {
    setPlaybackRate(rate);
    if (videoRef.current) videoRef.current.playbackRate = rate;
  }, []);

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-border bg-card p-6 text-sm text-muted-foreground" data-testid="course-player-loading">
        <Loader2 className="h-4 w-4 animate-spin" /> Preparing playback…
      </div>
    );
  }

  if (!asset || !playbackUrl) {
    return (
      <div className="flex items-start gap-2 rounded-lg border border-warning/35 bg-warning-soft p-4 text-sm text-warning-foreground" data-testid="course-player-unavailable">
        <VideoOff className="mt-0.5 h-4 w-4 shrink-0" />
        <div>
          <p className="font-medium">Playback is not available right now.</p>
          <p className="mt-0.5 text-xs">
            {playback?.playbackUnavailableReason === "SIGNED_URL_UNAVAILABLE"
              ? "The media store could not issue a playback link — try again shortly."
              : "This recording has no playable source yet."}
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_280px]" data-testid="course-player" data-asset-id={assetId}>
      <div className="min-w-0">
        <div className="overflow-hidden rounded-t-lg border border-border bg-black">
          {/* eslint-disable-next-line jsx-a11y/media-has-caption -- captions arrive via captionRef when produced; see TranscriptPane honest seam */}
          <video
            ref={videoRef}
            src={playbackUrl}
            className="aspect-video w-full"
            data-testid="course-player-video"
            onLoadedMetadata={handleLoadedMetadata}
            onTimeUpdate={handleTimeUpdate}
            onPlay={() => setPlaying(true)}
            onPause={() => {
              setPlaying(false);
              watch.flush();
            }}
            onEnded={() => {
              setPlaying(false);
              watch.flush();
            }}
            playsInline
            preload="metadata"
          >
            {asset.captionRef ? <track kind="captions" src={asset.captionRef} default /> : null}
          </video>
        </div>
        <PlaybackControls
          playing={playing}
          currentTime={currentTime}
          duration={duration ?? asset.durationSeconds ?? null}
          playbackRate={playbackRate}
          onPlayPause={handlePlayPause}
          onSeek={seekTo}
          onRateChange={handleRateChange}
        />
        <div className="mt-3 space-y-2">
          <CompletionBanner
            completed={watch.completed}
            percent={watch.percent}
            thresholdPercent={watch.thresholdPercent}
          />
          <QuizInterrupt courseId={effectiveCourseId} enrolmentId={enrolmentId} visible={watch.completed} />
          {title || asset.title ? (
            <p className="text-xs text-muted-foreground" data-testid="course-player-title">
              {title ?? asset.title}
            </p>
          ) : null}
        </div>
      </div>
      <div className="space-y-3">
        <ChapterSidebar chapters={chapters} currentTime={currentTime} onJump={seekTo} />
        <BookmarkList
          bookmarks={bookmarks}
          currentTime={currentTime}
          onJump={seekTo}
          busy={createBookmark.isPending}
          onAdd={(positionSeconds, note) =>
            createBookmark.mutate({
              assetId,
              body: { enrolmentId, positionSeconds, note: note || undefined },
            })
          }
          onDelete={(bookmarkId) => deleteBookmark.mutate({ bookmarkId, enrolmentId, assetId })}
        />
        <TranscriptPane transcript={asset.transcript} />
      </div>
    </div>
  );
}
