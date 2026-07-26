/**
 * TrackFeedbackScreen — Track a previously-submitted Rito case.
 *
 * The citizen enters a case id/reference, and we fetch the case plus its
 * lifecycle timeline from the experience-bff persona endpoints. Honest
 * not-found handling: a 404 from the BFF surfaces as a clear empty message.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { Card, CardHeader, CardBody, Button, Badge, TextField, LoadingSpinner, colors } from "@impilo/mobile-design-system";
import { ApiError } from "@impilo/mobile-api-client";
import { trackCase, caseTimeline } from "../../services/ritoFeedbackService";
import type { RitoCase, RitoCaseEvent } from "../../services/ritoFeedbackService";
import { appStore, useAppStore } from "../../stores/appStore";

const STATUS_VARIANT: Record<string, "default" | "info" | "success" | "warning" | "error"> = {
  NEW: "info",
  OPEN: "info",
  ACKNOWLEDGED: "info",
  TRIAGED: "warning",
  ASSIGNED: "warning",
  IN_PROGRESS: "warning",
  ESCALATED: "error",
  RESOLVED: "success",
  CLOSED: "default",
  CANCELLED: "default",
};

function statusVariant(status: string): "default" | "info" | "success" | "warning" | "error" {
  return STATUS_VARIANT[status?.toUpperCase()] ?? "default";
}

export function TrackFeedbackScreen() {
  const seededReference = useAppStore((state) => state.ritoTrackReference);
  const [reference, setReference] = useState("");
  const [loading, setLoading] = useState(false);
  const [notFound, setNotFound] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [ritoCase, setRitoCase] = useState<RitoCase | null>(null);
  const [timeline, setTimeline] = useState<RitoCaseEvent[]>([]);

  const runTrack = useCallback(async (value: string) => {
    const trimmed = value.trim();
    if (!trimmed) return;
    setLoading(true);
    setError(null);
    setNotFound(false);
    setRitoCase(null);
    setTimeline([]);
    try {
      const found = await trackCase(trimmed);
      setRitoCase(found);
      // Timeline is best-effort; a case with no events yet returns an empty list.
      try {
        setTimeline(await caseTimeline(found.id ?? trimmed));
      } catch {
        setTimeline([]);
      }
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setNotFound(true);
      } else {
        setError(err instanceof Error ? err : new Error(String(err)));
      }
    } finally {
      setLoading(false);
    }
  }, []);

  // If we arrived here from a fresh submission, seed and auto-track once.
  useEffect(() => {
    if (seededReference) {
      setReference(seededReference);
      void runTrack(seededReference);
      appStore.getState().setRitoTrackReference(null);
    }
  }, [seededReference, runTrack]);

  return (
    <ScrollView testID="rito-track-screen" style={styles.scrollView} showsVerticalScrollIndicator={false}>
      <View style={styles.container}>
        <Text style={styles.heading}>Track your feedback</Text>
        <Text style={styles.subText}>
          Enter the case reference you were given when you submitted your feedback.
        </Text>

        <Card>
          <CardBody>
            <View style={styles.formContainer}>
              <TextField
                label="Case reference or id"
                value={reference}
                onChange={setReference}
                placeholder="e.g. RITO-2026-000123"
                autoCapitalize="characters"
                testID="rito-track-reference"
              />
              <Button
                title={loading ? "Looking up..." : "Track"}
                variant="primary"
                onPress={() => runTrack(reference)}
                disabled={loading || !reference.trim()}
                testID="rito-track-submit"
              />
            </View>
          </CardBody>
        </Card>

        {loading ? <LoadingSpinner size="md" /> : null}

        {error ? (
          <Card>
            <CardBody>
              <Text accessibilityRole="alert" style={styles.errorText} testID="rito-track-error">
                {error.message}
              </Text>
            </CardBody>
          </Card>
        ) : null}

        {notFound ? (
          <Card>
            <CardBody>
              <Text style={styles.notFoundText} testID="rito-track-not-found">
                {`No case found for "${reference.trim()}". Check the reference and try again.`}
              </Text>
            </CardBody>
          </Card>
        ) : null}

        {ritoCase ? (
          <Card>
            <CardHeader title={ritoCase.title || ritoCase.caseReference} />
            <CardBody>
              <View style={styles.caseBody}>
                <View style={styles.referenceRow}>
                  <Text style={styles.referenceLabel}>Reference</Text>
                  <Badge variant="primary">{ritoCase.caseReference}</Badge>
                </View>
                <View style={styles.referenceRow}>
                  <Text style={styles.referenceLabel}>Status</Text>
                  <Badge variant={statusVariant(ritoCase.status)}>{ritoCase.status}</Badge>
                </View>
                <View style={styles.referenceRow}>
                  <Text style={styles.referenceLabel}>Type</Text>
                  <Badge variant="outline">{ritoCase.caseType}</Badge>
                </View>
                {ritoCase.resolutionSummary ? (
                  <Text style={styles.resolutionText}>
                    {`Resolution: ${ritoCase.resolutionSummary}`}
                  </Text>
                ) : null}
              </View>
            </CardBody>
          </Card>
        ) : null}

        {ritoCase ? (
          <Card>
            <CardHeader title="Progress" />
            <CardBody>
              {timeline.length === 0 ? (
                <Text style={styles.emptyTimeline} testID="rito-timeline-empty">
                  No updates yet. We will record progress here as your case moves forward.
                </Text>
              ) : (
                <View style={styles.timeline}>
                  {timeline.map((event) => (
                    <View key={event.id} style={styles.timelineItem} testID={`rito-event-${event.id}`}>
                      <View style={styles.timelineDot} />
                      <View style={styles.timelineContent}>
                        <Text style={styles.timelineType}>{event.eventType}</Text>
                        {event.toStatus ? (
                          <Text style={styles.timelineStatus}>
                            {event.fromStatus ? `${event.fromStatus} → ${event.toStatus}` : event.toStatus}
                          </Text>
                        ) : null}
                        {event.note ? <Text style={styles.timelineNote}>{event.note}</Text> : null}
                        <Text style={styles.timelineDate}>
                          {new Date(event.occurredAt).toLocaleString()}
                        </Text>
                      </View>
                    </View>
                  ))}
                </View>
              )}
            </CardBody>
          </Card>
        ) : null}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
  },
  container: {
    gap: 12,
    paddingBottom: 24,
  },
  heading: {
    fontSize: 18,
    fontWeight: "600",
  },
  subText: {
    fontSize: 13,
    color: colors.gray[500],
  },
  formContainer: {
    gap: 14,
  },
  errorText: {
    fontSize: 13,
    color: colors.ui.error.main,
  },
  notFoundText: {
    fontSize: 14,
    color: colors.gray[700],
  },
  caseBody: {
    gap: 10,
  },
  referenceRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  referenceLabel: {
    fontSize: 13,
    color: colors.gray[500],
    fontWeight: "500",
  },
  resolutionText: {
    fontSize: 13,
    color: "#059669",
    marginTop: 4,
  },
  emptyTimeline: {
    fontSize: 13,
    color: colors.gray[400],
  },
  timeline: {
    gap: 12,
  },
  timelineItem: {
    flexDirection: "row",
    gap: 10,
  },
  timelineDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: "#059669",
    marginTop: 4,
  },
  timelineContent: {
    flex: 1,
    gap: 2,
  },
  timelineType: {
    fontSize: 14,
    fontWeight: "600",
    color: colors.gray[900],
  },
  timelineStatus: {
    fontSize: 13,
    color: colors.gray[700],
  },
  timelineNote: {
    fontSize: 13,
    color: colors.gray[600],
  },
  timelineDate: {
    fontSize: 12,
    color: colors.gray[400],
  },
});
