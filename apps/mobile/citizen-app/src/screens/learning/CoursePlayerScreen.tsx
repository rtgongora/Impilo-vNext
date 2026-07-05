import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from "react-native";
import { useVideoPlayer, VideoView } from "expo-video";
import { useQuery } from "@tanstack/react-query";
import { Card, CardBody, Header, LoadingSpinner, Screen } from "@impilo/mobile-design-system";
import {
  fetchCourseVideoLessons,
  fetchLessonMediaAsset,
  fetchMediaChapters,
  fetchMediaPlayback,
  fetchWatchProgress,
  upsertWatchProgress,
  type CourseMediaAsset,
} from "../../services/coursePlayerService";

export interface CoursePlayerEnrolment {
  id: string;
  courseId: string;
  title?: string;
}

interface CoursePlayerScreenProps {
  enrolment: CoursePlayerEnrolment;
  onBack: () => void;
}

const FLUSH_INTERVAL_MS = 10_000;

function formatClock(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  const m = Math.floor(s / 60);
  const sec = String(s % 60).padStart(2, "0");
  return `${m}:${sec}`;
}

/**
 * Citizen course player (LEARNING_RECORDING W4): plays governed media assets
 * (adopted live replays / authored recordings) through expo-video with the
 * same watch-progress contract as the web CoursePlayer — signed-URL playback,
 * debounced monotonic upserts, resume from the furthest watched position, and
 * chapter navigation. Lessons without a governed asset are not listed here;
 * they stay on their existing content surfaces.
 */
export function CoursePlayerScreen({ enrolment, onBack }: CoursePlayerScreenProps) {
  const [selectedLessonId, setSelectedLessonId] = useState<string | null>(null);
  const [completed, setCompleted] = useState(false);
  const [percent, setPercent] = useState(0);
  const [threshold, setThreshold] = useState(90);

  // VIDEO lessons of the course that actually carry a governed media asset.
  const playable = useQuery({
    queryKey: ["citizen-fundo", "course-player-lessons", enrolment.courseId],
    queryFn: async () => {
      const lessons = await fetchCourseVideoLessons(enrolment.courseId);
      const withAssets: Array<{ lessonId: string; title: string; asset: CourseMediaAsset }> = [];
      for (const lesson of lessons) {
        const asset = await fetchLessonMediaAsset(lesson.id);
        if (asset) withAssets.push({ lessonId: lesson.id, title: lesson.title, asset });
      }
      return withAssets;
    },
  });

  const current = useMemo(() => {
    const items = playable.data ?? [];
    if (items.length === 0) return null;
    return items.find((i) => i.lessonId === selectedLessonId) ?? items[0];
  }, [playable.data, selectedLessonId]);

  const assetId = current?.asset.id;

  const playback = useQuery({
    queryKey: ["citizen-fundo", "media-playback", assetId, enrolment.id],
    enabled: Boolean(assetId),
    queryFn: () => fetchMediaPlayback(assetId!, enrolment.id),
  });
  const chapters = useQuery({
    queryKey: ["citizen-fundo", "media-chapters", assetId],
    enabled: Boolean(assetId),
    queryFn: () => fetchMediaChapters(assetId!),
  });
  const serverProgress = useQuery({
    queryKey: ["citizen-fundo", "media-watch-progress", assetId, enrolment.id],
    enabled: Boolean(assetId),
    queryFn: () => fetchWatchProgress(assetId!, enrolment.id),
  });

  const playbackUrl = playback.data?.playbackUrl ?? null;
  const player = useVideoPlayer(playbackUrl, (p) => {
    p.timeUpdateEventInterval = 1;
  });

  // ── watch-progress accumulation (same monotonic contract as web) ──────────
  const watchedRef = useRef(0);
  const positionRef = useRef(0);
  const durationRef = useRef<number | null>(null);
  const dirtyRef = useRef(false);
  const lastFlushRef = useRef(Date.now());
  const resumedRef = useRef(false);
  const seededRef = useRef<string | null>(null);

  useEffect(() => {
    const sp = serverProgress.data;
    const key = `${assetId}:${enrolment.id}`;
    if (sp && seededRef.current !== key) {
      seededRef.current = key;
      watchedRef.current = sp.watchedSeconds ?? 0;
      setCompleted(Boolean(sp.completedAt));
      setPercent(sp.percent ?? 0);
      setThreshold(sp.thresholdPercent ?? 90);
    }
  }, [serverProgress.data, assetId, enrolment.id]);

  const flush = useCallback(() => {
    if (!assetId || !dirtyRef.current) return;
    dirtyRef.current = false;
    lastFlushRef.current = Date.now();
    void upsertWatchProgress(assetId, {
      enrolmentId: enrolment.id,
      positionSeconds: Math.round(positionRef.current),
      watchedSeconds: Math.round(watchedRef.current),
      durationSeconds: durationRef.current != null ? Math.round(durationRef.current) : undefined,
    })
      .then((wp) => {
        if (wp) {
          setPercent(wp.percent ?? 0);
          setThreshold(wp.thresholdPercent ?? 90);
          if (wp.completedAt) setCompleted(true);
        }
      })
      .catch(() => {
        dirtyRef.current = true; // retry on the next flush window
      });
  }, [assetId, enrolment.id]);

  // Time updates: accumulate play-time, resume once, debounce flushes.
  useEffect(() => {
    if (!player) return;
    const lastTick = { at: null as number | null };
    const sub = player.addListener("timeUpdate", (event) => {
      positionRef.current = event.currentTime ?? 0;
      const duration = player.duration;
      if (Number.isFinite(duration) && duration > 0) durationRef.current = duration;

      if (!resumedRef.current && durationRef.current) {
        resumedRef.current = true;
        const resume = serverProgress.data?.furthestPositionSeconds ?? 0;
        if (resume > 5 && resume < durationRef.current - 5) {
          player.currentTime = resume;
        }
      }

      const now = Date.now();
      if (player.playing) {
        if (lastTick.at != null) {
          const delta = Math.min((now - lastTick.at) / 1000, 2);
          if (delta > 0) {
            watchedRef.current += delta;
            dirtyRef.current = true;
          }
        }
        lastTick.at = now;
        if (now - lastFlushRef.current >= FLUSH_INTERVAL_MS) flush();
      } else {
        lastTick.at = null;
      }
    });
    return () => {
      sub.remove();
    };
  }, [player, flush, serverProgress.data]);

  // Final flush on unmount / lesson switch.
  useEffect(() => {
    return () => flush();
  }, [flush, assetId]);

  const items = playable.data ?? [];

  return (
    <Screen>
      <Header title={enrolment.title ?? "Course player"} subtitle="Recorded lessons with watch progress" />
      <ScrollView contentContainerStyle={styles.content} testID="course-player-screen">
        <TouchableOpacity onPress={onBack} testID="course-player-back">
          <Text style={styles.backLink}>← Back to learning</Text>
        </TouchableOpacity>

        {playable.isLoading ? (
          <View style={styles.loading}>
            <LoadingSpinner />
          </View>
        ) : items.length === 0 ? (
          <Card>
            <CardBody>
              <Text style={styles.emptyText}>
                This course has no recorded lessons yet. Replays appear here after a live session is
                published.
              </Text>
            </CardBody>
          </Card>
        ) : (
          <>
            {playbackUrl ? (
              <View style={styles.videoWrap} testID="course-player-video">
                <VideoView
                  player={player}
                  style={styles.video}
                  contentFit="contain"
                  nativeControls
                  allowsFullscreen
                />
              </View>
            ) : playback.isLoading ? (
              <View style={styles.loading}>
                <LoadingSpinner />
              </View>
            ) : (
              <Card>
                <CardBody>
                  <Text style={styles.emptyText}>
                    Playback is not available right now — the media store could not issue a playback
                    link. Pull to retry shortly.
                  </Text>
                </CardBody>
              </Card>
            )}

            <Card testID="course-player-progress">
              <CardBody>
                {completed ? (
                  <Text style={styles.completedText}>
                    ✓ Watch requirement met — this recording counts towards your completion.
                  </Text>
                ) : (
                  <Text style={styles.progressText}>
                    Watched {Math.round(percent)}% — {threshold}% counts as complete
                  </Text>
                )}
              </CardBody>
            </Card>

            {(chapters.data ?? []).length > 0 ? (
              <View style={styles.section}>
                <Text style={styles.sectionLabel}>Chapters</Text>
                {(chapters.data ?? []).map((chapter) => (
                  <TouchableOpacity
                    key={chapter.id}
                    onPress={() => {
                      player.currentTime = chapter.startSeconds;
                    }}
                    testID={`course-player-chapter-${chapter.id}`}
                  >
                    <Card style={styles.rowCard}>
                      <CardBody>
                        <Text style={styles.rowTitle}>{chapter.title}</Text>
                        <Text style={styles.rowMeta}>{formatClock(chapter.startSeconds)}</Text>
                      </CardBody>
                    </Card>
                  </TouchableOpacity>
                ))}
              </View>
            ) : null}

            {items.length > 1 ? (
              <View style={styles.section}>
                <Text style={styles.sectionLabel}>Recorded lessons</Text>
                {items.map((item) => (
                  <TouchableOpacity
                    key={item.lessonId}
                    onPress={() => {
                      flush();
                      resumedRef.current = false;
                      seededRef.current = null;
                      setSelectedLessonId(item.lessonId);
                    }}
                    testID={`course-player-lesson-${item.lessonId}`}
                  >
                    <Card style={item.lessonId === current?.lessonId ? styles.rowCardActive : styles.rowCard}>
                      <CardBody>
                        <Text style={styles.rowTitle}>{item.title}</Text>
                        <Text style={styles.rowMeta}>
                          {item.asset.durationSeconds ? formatClock(item.asset.durationSeconds) : "Recording"}
                        </Text>
                      </CardBody>
                    </Card>
                  </TouchableOpacity>
                ))}
              </View>
            ) : null}

            {current?.asset.transcript ? (
              <View style={styles.section}>
                <Text style={styles.sectionLabel}>Transcript</Text>
                <Card>
                  <CardBody>
                    <Text style={styles.transcript}>{current.asset.transcript}</Text>
                  </CardBody>
                </Card>
              </View>
            ) : null}
          </>
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { padding: 16, gap: 10 },
  backLink: { color: "#047857", fontSize: 13, fontWeight: "600" },
  loading: { paddingVertical: 24, alignItems: "center" },
  videoWrap: { borderRadius: 12, overflow: "hidden", backgroundColor: "#000" },
  video: { width: "100%", aspectRatio: 16 / 9 },
  section: { gap: 6, marginTop: 4 },
  sectionLabel: { fontSize: 12, fontWeight: "700", color: "#6B7280", textTransform: "uppercase" },
  rowCard: { marginBottom: 6 },
  rowCardActive: { marginBottom: 6, borderColor: "#047857", borderWidth: 1 },
  rowTitle: { fontSize: 13, fontWeight: "600", color: "#0F172A" },
  rowMeta: { marginTop: 2, fontSize: 11, color: "#64748B" },
  progressText: { fontSize: 12, color: "#334155" },
  completedText: { fontSize: 12, fontWeight: "600", color: "#047857" },
  transcript: { fontSize: 12, color: "#334155", lineHeight: 18 },
  emptyText: { fontSize: 13, color: "#6B7280" },
});
