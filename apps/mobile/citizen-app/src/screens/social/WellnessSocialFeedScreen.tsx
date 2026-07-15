/**
 * WellnessSocialFeedScreen — the citizen-facing wellness-SOVEREIGN social feed
 * (/internal/v1/wellness/social/**), DISTINCT from the generic community-service SocialFeedScreen.
 * Consent-governed, with ephemeral "Right now" statuses (mood + expiry) and supportive posts.
 * Backed by Simba via the Experience BFF proxy through `wellnessSocialService`.
 *
 * NOTE: apps/mobile uses the pnpm workspace protocol; the vitest runner does not execute in this
 * environment. Type-checked (tsc --noEmit); no runtime unit test was run here (honest partial).
 */

import React, { useState } from "react";
import { View, Text, StyleSheet, ScrollView, RefreshControl, TouchableOpacity } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  Select,
  TextField,
  Badge,
  LoadingSpinner,
  EmptyState,
} from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  fetchSocialFeed,
  createPost,
  reactToPost,
  fetchActiveStatuses,
  createStatus,
  deleteStatus,
  type SocialPost,
  type SocialStatus,
} from "../../services/wellnessSocialService";
import { useAppStore } from "../../stores/appStore";

const MOOD_OPTIONS = [
  { label: "😊 Happy", value: "HAPPY" },
  { label: "💪 Motivated", value: "MOTIVATED" },
  { label: "😌 Calm", value: "CALM" },
  { label: "😴 Tired", value: "TIRED" },
  { label: "🙏 Grateful", value: "GRATEFUL" },
];

const VISIBILITY_OPTIONS = [
  { label: "Only me", value: "PRIVATE" },
  { label: "My followers", value: "FOLLOWER" },
  { label: "Everyone on Impilo", value: "PUBLIC_WITHIN_IMPILO" },
];

export function WellnessSocialFeedScreen() {
  const cpid = useAppStore((s) => s.profile?.cpid);
  const qc = useQueryClient();

  const feedQ = useQuery({
    queryKey: ["wellness-social-feed"],
    queryFn: () => fetchSocialFeed("ALL"),
  });
  const statusesQ = useQuery({
    queryKey: ["wellness-social-statuses"],
    queryFn: () => fetchActiveStatuses(50),
  });

  const [postBody, setPostBody] = useState("");
  const [statusText, setStatusText] = useState("");
  const [mood, setMood] = useState("HAPPY");
  const [visibility, setVisibility] = useState("PUBLIC_WITHIN_IMPILO");
  const [showStatus, setShowStatus] = useState(false);

  const postM = useMutation({
    mutationFn: () => createPost(postBody, "PUBLIC_WITHIN_IMPILO"),
    onSuccess: () => {
      setPostBody("");
      void qc.invalidateQueries({ queryKey: ["wellness-social-feed"] });
    },
  });
  const statusM = useMutation({
    mutationFn: () => createStatus(statusText, mood, visibility, 24),
    onSuccess: () => {
      setStatusText("");
      setShowStatus(false);
      void qc.invalidateQueries({ queryKey: ["wellness-social-statuses"] });
    },
  });
  const deleteStatusM = useMutation({
    mutationFn: (statusId: string) => deleteStatus(statusId),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["wellness-social-statuses"] }),
  });
  const reactM = useMutation({
    mutationFn: (postId: string) => reactToPost(postId, "ENCOURAGE"),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["wellness-social-feed"] }),
  });

  const statuses = statusesQ.data ?? [];
  const posts: SocialPost[] = feedQ.data?.posts ?? [];

  return (
    <Screen>
      <Header title="Wellness community" />
      <ScrollView
        style={styles.content}
        contentContainerStyle={styles.contentContainer}
        refreshControl={
          <RefreshControl
            refreshing={feedQ.isFetching || statusesQ.isFetching}
            onRefresh={() => {
              void feedQ.refetch();
              void statusesQ.refetch();
            }}
          />
        }
      >
        <Card>
          <CardBody>
            <View style={styles.rowBetween}>
              <Text style={styles.sectionTitle}>Right now</Text>
              <Button
                title={showStatus ? "Close" : "Share a status"}
                variant="ghost"
                size="sm"
                onPress={() => setShowStatus((s) => !s)}
                testID="wellness-status-toggle"
              />
            </View>

            {showStatus && (
              <View style={styles.form}>
                <TextField
                  label="How are you doing right now?"
                  value={statusText}
                  onChange={setStatusText}
                  placeholder="A short status"
                  testID="wellness-status-text"
                />
                <Select label="Mood" value={mood} onChange={setMood} options={MOOD_OPTIONS} testID="wellness-status-mood" />
                <Select
                  label="Who can see this?"
                  value={visibility}
                  onChange={setVisibility}
                  options={VISIBILITY_OPTIONS}
                  testID="wellness-status-visibility"
                />
                <Button
                  title={statusM.isPending ? "Posting…" : "Post status"}
                  variant="primary"
                  disabled={statusM.isPending || !statusText.trim()}
                  onPress={() => statusM.mutate()}
                  testID="wellness-status-submit"
                />
              </View>
            )}

            {statusesQ.isLoading ? (
              <LoadingSpinner />
            ) : statuses.length === 0 ? (
              <Text style={styles.muted}>No active statuses yet.</Text>
            ) : (
              <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.strip}>
                {statuses.map((s: SocialStatus) => (
                  <View key={s.statusId} style={styles.statusChip}>
                    {s.actorCpid === cpid ? (
                      <TouchableOpacity
                        onPress={() => deleteStatusM.mutate(s.statusId)}
                        testID={`wellness-status-delete-${s.statusId}`}
                      >
                        <Text style={styles.statusRemove}>✕</Text>
                      </TouchableOpacity>
                    ) : null}
                    <Text style={styles.statusText}>{s.text}</Text>
                    {s.mood ? <Badge variant="secondary">{s.mood}</Badge> : null}
                  </View>
                ))}
              </ScrollView>
            )}
          </CardBody>
        </Card>

        <Card>
          <CardBody>
            <View style={styles.form}>
              <TextField
                label="Share a wellness win"
                value={postBody}
                onChange={setPostBody}
                placeholder="Encourage the community…"
                multiline
                numberOfLines={3}
                testID="wellness-post-text"
              />
              <Button
                title={postM.isPending ? "Posting…" : "Post"}
                variant="primary"
                disabled={postM.isPending || !postBody.trim()}
                onPress={() => postM.mutate()}
                testID="wellness-post-submit"
              />
            </View>
          </CardBody>
        </Card>

        {feedQ.isLoading ? (
          <LoadingSpinner />
        ) : posts.length === 0 ? (
          <EmptyState
            title="No posts yet"
            description="Be the first to share a wellness win with the community."
          />
        ) : (
          posts.map((p) => (
            <Card key={p.postId}>
              <CardBody>
                <Text style={styles.muted}>
                  {p.actorType} · {new Date(p.createdAt).toLocaleDateString()}
                </Text>
                <Text style={styles.postBody}>{p.body}</Text>
                <View style={styles.rowBetween}>
                  <Button
                    title={`💪 ${p.reactionCount}`}
                    variant="ghost"
                    size="sm"
                    onPress={() => reactM.mutate(p.postId)}
                    testID={`wellness-react-${p.postId}`}
                  />
                  <Text style={styles.muted}>💬 {p.commentCount}</Text>
                </View>
              </CardBody>
            </Card>
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { flex: 1 },
  contentContainer: { padding: 16, gap: 16 },
  form: { gap: 12, marginTop: 8 },
  rowBetween: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  sectionTitle: { fontSize: 16, fontWeight: "600", color: "#0f172a" },
  muted: { fontSize: 12, color: "#64748b" },
  strip: { marginTop: 8 },
  statusChip: {
    minWidth: 128,
    maxWidth: 176,
    marginRight: 8,
    padding: 12,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: "#e2e8f0",
    backgroundColor: "#ffffff",
    gap: 6,
  },
  statusRemove: { alignSelf: "flex-end", color: "#94a3b8", fontSize: 12 },
  statusText: { fontSize: 13, color: "#334155" },
  postBody: { fontSize: 14, color: "#1e293b", marginVertical: 8 },
});
