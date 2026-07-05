"use client";

import { useCallback, useRef, useState } from "react";
import { PlaybackControls } from "./PlaybackControls";

export interface SessionReplayPlayerProps {
  playbackUrl: string;
  title?: string;
  /** Optional transcript rendered under the surface when it exists. */
  transcript?: string | null;
}

/**
 * Replay surface for published live-event recordings (LEARNING_RECORDING W4).
 * Shares the PlaybackControls core with CoursePlayer but carries no enrolment
 * context — replay watch minutes stay tracked by the hosting page (the
 * existing useLiveTrackMinutes flow), and course watch progress only accrues
 * through the enrolment-scoped CoursePlayer.
 */
export function SessionReplayPlayer({ playbackUrl, title }: SessionReplayPlayerProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [playing, setPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState<number | null>(null);
  const [playbackRate, setPlaybackRate] = useState(1);

  const seekTo = useCallback((seconds: number) => {
    const video = videoRef.current;
    if (video) {
      video.currentTime = seconds;
      setCurrentTime(seconds);
    }
  }, []);

  return (
    <div data-testid="session-replay-player">
      <div className="overflow-hidden rounded-t-lg border border-border bg-black">
        {/* eslint-disable-next-line jsx-a11y/media-has-caption -- see TranscriptPane honest seam */}
        <video
          ref={videoRef}
          src={playbackUrl}
          className="aspect-video w-full"
          data-testid="session-replay-video"
          onLoadedMetadata={() => {
            const v = videoRef.current;
            if (v) setDuration(Number.isFinite(v.duration) ? v.duration : null);
          }}
          onTimeUpdate={() => setCurrentTime(videoRef.current?.currentTime ?? 0)}
          onPlay={() => setPlaying(true)}
          onPause={() => setPlaying(false)}
          onEnded={() => setPlaying(false)}
          playsInline
          preload="metadata"
        />
      </div>
      <PlaybackControls
        playing={playing}
        currentTime={currentTime}
        duration={duration}
        playbackRate={playbackRate}
        onPlayPause={() => {
          const v = videoRef.current;
          if (!v) return;
          if (v.paused) void v.play();
          else v.pause();
        }}
        onSeek={seekTo}
        onRateChange={(rate) => {
          setPlaybackRate(rate);
          if (videoRef.current) videoRef.current.playbackRate = rate;
        }}
      />
      {title ? <p className="mt-2 text-xs text-muted-foreground">{title}</p> : null}
    </div>
  );
}
