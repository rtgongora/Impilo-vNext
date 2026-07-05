"use client";

import type { ReactNode } from "react";
import { useLessonMedia } from "@/hooks/queries/useFundoPlayer";
import { CoursePlayer } from "./CoursePlayer";

export interface LessonMediaPlayerProps {
  lessonId: string;
  enrolmentId?: string;
  courseId?: string;
  title?: string;
  /** Rendered when the lesson has no governed media asset (legacy contentRef video). */
  fallback: ReactNode;
}

/**
 * Delegation seam between FundoLessonContent and the governed course player:
 * lessons whose live replay (or authored recording) was adopted as a
 * lrn_media_asset play through CoursePlayer (signed URLs + watch progress);
 * lessons that only carry a legacy contentRef URL keep the original
 * iframe/native rendering via {@code fallback}.
 */
export function LessonMediaPlayer({ lessonId, enrolmentId, courseId, title, fallback }: LessonMediaPlayerProps) {
  const { data, isLoading } = useLessonMedia(lessonId);
  const asset = data?.data?.asset ?? null;

  if (!isLoading && asset && enrolmentId) {
    return <CoursePlayer assetId={asset.id} enrolmentId={enrolmentId} courseId={courseId} title={title} />;
  }
  if (isLoading) {
    return null; // brief flash-free gap; the fallback would flicker otherwise
  }
  return <>{fallback}</>;
}
