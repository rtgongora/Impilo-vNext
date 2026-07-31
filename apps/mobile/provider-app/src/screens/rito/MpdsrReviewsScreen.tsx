/**
 * MpdsrReviewsScreen — confidential MPDSR review worklist for committee members (W14-E parity).
 *
 * Mobile mirrors the web ops worklist (/rito/mpdsr) as a read surface: tap a review to expand
 * its committee detail (three-delay findings, CAPA actions, firewalled readiness tasks). The
 * write lifecycle — meetings, findings, actions, close — stays on the web ops surface where the
 * committee sits; this screen never composes review narrative into anything shareable.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from "react-native";
import { Card, CardBody, Badge, Button, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import {
  fetchMpdsrReviews,
  fetchMpdsrReview,
  type MpdsrReview,
  type MpdsrReviewDetail,
} from "../../services/mpdsrService";

function statusVariant(status: string | null | undefined): "default" | "info" | "success" | "warning" | "error" {
  switch ((status ?? "").toUpperCase()) {
    case "OPEN":
      return "info";
    case "IN_REVIEW":
    case "COMMITTEE":
      return "warning";
    case "CLOSED":
      return "success";
    default:
      return "default";
  }
}

function ReviewDetailBody({ reviewId }: { reviewId: string }) {
  const [detail, setDetail] = useState<MpdsrReviewDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      setDetail(await fetchMpdsrReview(reviewId));
    } catch {
      setError(true);
      setDetail(null);
    } finally {
      setLoading(false);
    }
  }, [reviewId]);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) return <Text style={styles.hint}>Loading review detail…</Text>;
  if (error) {
    return (
      <Text style={styles.errorText} accessibilityRole="alert">
        The review detail could not be loaded.
      </Text>
    );
  }

  const findings = detail?.findings ?? [];
  const actions = detail?.actions ?? [];
  const readinessTasks = detail?.readinessTasks ?? [];

  return (
    <View style={styles.detail} testID={`mpdsr-detail-${reviewId}`}>
      <Text style={styles.detailTitle}>Three-delay findings ({findings.length})</Text>
      {findings.length === 0 ? (
        <Text style={styles.hint}>No findings recorded yet.</Text>
      ) : (
        findings.map((f) => (
          <Text key={f.id} style={styles.detailLine}>
            {f.delayType ?? "—"}: {f.modifiableFactor ?? "—"}
          </Text>
        ))
      )}

      <Text style={styles.detailTitle}>CAPA actions ({actions.length})</Text>
      {actions.length === 0 ? (
        <Text style={styles.hint}>No actions linked yet.</Text>
      ) : (
        actions.map((a) => (
          <Text key={a.id} style={styles.detailLine}>
            {a.title ?? "—"} — {a.status ?? "—"}
          </Text>
        ))
      )}

      <Text style={styles.detailTitle}>Readiness tasks ({readinessTasks.length})</Text>
      <Text style={styles.hint}>
        Firewalled — readiness tasks never carry review narrative or death identifiers.
      </Text>
      {readinessTasks.map((t) => (
        <Text key={t.id} style={styles.detailLine}>
          {t.taskReference ?? t.id} — {t.status ?? "—"}
        </Text>
      ))}
    </View>
  );
}

export function MpdsrReviewsScreen() {
  const [reviews, setReviews] = useState<MpdsrReview[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setReviews(await fetchMpdsrReviews());
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return <LoadingSpinner size="md" />;
  }

  if (error) {
    return <ErrorState title="Could not load MPDSR reviews" message={error.message} onRetry={load} />;
  }

  if (reviews.length === 0) {
    return (
      <EmptyState
        title="No MPDSR reviews"
        message="Death cases flagged for review in PCT will appear here. An empty list is not proof that no review is due."
      />
    );
  }

  return (
    <ScrollView testID="mpdsr-reviews-screen" style={styles.scrollView} showsVerticalScrollIndicator={false}>
      <View style={styles.container}>
        <View style={styles.headerRow}>
          <Text style={styles.heading}>MPDSR reviews</Text>
          <Button title="Refresh" variant="ghost" size="sm" onPress={load} testID="mpdsr-refresh" />
        </View>
        <Text style={styles.subText}>
          Confidential maternal &amp; perinatal death surveillance — committee work, not the
          maternity chart. Meetings, findings and closure are recorded on the web ops surface.
        </Text>

        {reviews.map((r) => (
          <Card key={r.id}>
            <CardBody>
              <TouchableOpacity
                onPress={() => setExpandedId(expandedId === r.id ? null : r.id)}
                testID={`mpdsr-review-${r.id}`}
              >
                <View style={styles.badgeRow}>
                  <Text style={styles.reviewTitle} numberOfLines={1}>
                    Review {r.id.slice(0, 8)}…
                  </Text>
                  <Badge variant={statusVariant(r.status)}>{r.status ?? "OPEN"}</Badge>
                </View>
                <View style={styles.metaRow}>
                  {r.reviewLevel ? <Badge variant="outline">{r.reviewLevel}</Badge> : null}
                  {r.triggerFlags ? <Badge variant="secondary">{r.triggerFlags}</Badge> : null}
                </View>
                {r.openedAt ? (
                  <Text style={styles.date}>{`Opened: ${new Date(r.openedAt).toLocaleString()}`}</Text>
                ) : null}
              </TouchableOpacity>
              {expandedId === r.id ? <ReviewDetailBody reviewId={r.id} /> : null}
            </CardBody>
          </Card>
        ))}
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
  headerRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  heading: {
    fontSize: 16,
    fontWeight: "700",
    color: colors.gray[900],
  },
  subText: {
    fontSize: 12,
    color: colors.gray[500],
  },
  badgeRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 6,
  },
  reviewTitle: {
    flex: 1,
    fontSize: 15,
    fontWeight: "600",
    color: colors.gray[900],
  },
  metaRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    flexWrap: "wrap",
    marginBottom: 6,
  },
  date: {
    fontSize: 12,
    color: colors.gray[400],
  },
  detail: {
    marginTop: 10,
    borderTopWidth: 1,
    borderTopColor: colors.gray[100],
    paddingTop: 8,
    gap: 4,
  },
  detailTitle: {
    fontSize: 13,
    fontWeight: "700",
    color: colors.gray[900],
    marginTop: 6,
  },
  detailLine: {
    fontSize: 13,
    color: colors.gray[700],
  },
  hint: {
    fontSize: 12,
    color: colors.gray[400],
  },
  errorText: {
    fontSize: 13,
    color: "#991B1B",
  },
});
