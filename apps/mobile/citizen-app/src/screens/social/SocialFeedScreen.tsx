/**
 * SocialFeedScreen — Community feed with health tips, campaigns, reminders,
 * and citizen-authored posts. Now backed by the real social timeline
 * (community-service via experience-bff /internal/v1/mobile/citizen/social/feed)
 * and equipped with a TimelineComposer.
 *
 * Visible posts come from the new social timeline. Likes are wired to the
 * reactions endpoint. Older feed item callers that depended on the legacy
 * /internal/v1/mobile/citizen/feed shape continue to work — that endpoint is
 * still mounted in the BFF and now wraps the same social data.
 */

import React, { useState, useEffect, useCallback, useMemo } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, RefreshControl } from "react-native";
import { Screen, Header, Card, CardBody, Button, Badge, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import {
  fetchSocialFeed,
  reactToSocialPost,
  unreactSocialPost,
  bookmarkSocialPost,
  type SocialFeedScope,
  type SocialPost,
} from "../../services/socialService";
import { TimelineComposer } from "./TimelineComposer";

const SCOPE_FILTERS: Array<{ label: string; value: SocialFeedScope }> = [
  { label: "Home", value: "home" },
  { label: "Following", value: "following" },
  { label: "Wellness", value: "wellness" },
  { label: "Learning", value: "learning" },
  { label: "Public health", value: "public_health" },
  { label: "Announcements", value: "announcements" },
  { label: "Saved", value: "saved" },
];

const KIND_LABEL: Record<string, { label: string; tone: "default" | "secondary" | "destructive" | "outline" }> = {
  announcement: { label: "Announcement", tone: "destructive" },
  health_promotion: { label: "Health", tone: "default" },
  campaign: { label: "Campaign", tone: "secondary" },
  event: { label: "Event", tone: "secondary" },
  question: { label: "Question", tone: "outline" },
  learning: { label: "Learning", tone: "secondary" },
  resource: { label: "Resource", tone: "secondary" },
  text: { label: "Update", tone: "outline" },
};

export function SocialFeedScreen() {
  const [items, setItems] = useState<SocialPost[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [scope, setScope] = useState<SocialFeedScope>("home");
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const load = useCallback(async (nextScope: SocialFeedScope) => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await fetchSocialFeed(nextScope, { limit: 25 });
      setItems(res.items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
      setItems([]);
    } finally {
      setIsLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    load(scope);
  }, [load, scope]);

  const onRefresh = useCallback(() => {
    setRefreshing(true);
    load(scope);
  }, [load, scope]);

  const handleReact = useCallback(async (post: SocialPost) => {
    const wasLiked = post.reactionSummary?.viewerReaction === "LIKE";
    setItems((prev) =>
      prev.map((p) =>
        p.id === post.id
          ? {
              ...p,
              reactionSummary: {
                ...p.reactionSummary,
                total: Math.max(0, (p.reactionSummary?.total ?? 0) + (wasLiked ? -1 : 1)),
                viewerReaction: wasLiked ? null : "LIKE",
              },
            }
          : p,
      ),
    );
    try {
      if (wasLiked) await unreactSocialPost(post.id);
      else await reactToSocialPost(post.id, "LIKE");
    } catch {
      load(scope);
    }
  }, [load, scope]);

  const handleBookmark = useCallback(async (post: SocialPost) => {
    const next = !post.bookmarked;
    setItems((prev) => prev.map((p) => (p.id === post.id ? { ...p, bookmarked: next } : p)));
    try {
      await bookmarkSocialPost(post.id, next);
    } catch {
      setItems((prev) => prev.map((p) => (p.id === post.id ? { ...p, bookmarked: !next } : p)));
    }
  }, []);

  const filters = useMemo(() => SCOPE_FILTERS, []);

  return (
    <Screen>
      <Header title="Timeline" />
      <ScrollView
        testID="social-feed-screen"
        style={styles.scrollView}
        contentContainerStyle={styles.container}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
      >
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filterScroll} contentContainerStyle={styles.filterContent}>
          {filters.map((cat) => (
            <Button
              key={cat.value}
              title={cat.label}
              variant={scope === cat.value ? "primary" : "ghost"}
              size="sm"
              onPress={() => setScope(cat.value)}
              testID={`feed-filter-${cat.value}`}
            />
          ))}
        </ScrollView>

        <TimelineComposer onCreated={() => load(scope)} />

        {error ? <ErrorState title="Couldn't load timeline" message={error.message} onRetry={() => load(scope)} /> : null}

        {isLoading && items.length === 0 ? (
          <LoadingSpinner size="md" />
        ) : items.length === 0 ? (
          <EmptyState title="No posts yet" message="Pull to refresh, or follow a community/page to populate this feed." />
        ) : (
          items.map((post) => {
            const expanded = expandedId === post.id;
            const kindMeta = KIND_LABEL[post.kind] ?? KIND_LABEL.text;
            const viewerLiked = post.reactionSummary?.viewerReaction === "LIKE";
            return (
              <Card key={post.id}>
                <CardBody>
                  <View testID={`feed-item-${post.id}`}>
                    <View style={styles.feedItemHeader}>
                      <View style={styles.authorRow}>
                        <View style={styles.avatar}>
                          <Text style={styles.avatarText}>{post.author.displayName.charAt(0).toUpperCase()}</Text>
                        </View>
                        <View style={{ flex: 1 }}>
                          <Text style={styles.authorName}>{post.author.displayName}</Text>
                          <Text style={styles.metaText}>
                            {post.author.roleBadge ? `${post.author.roleBadge} · ` : ""}
                            {formatTimestamp(post.publishedAt ?? post.createdAt)}
                          </Text>
                        </View>
                        <Badge variant={kindMeta.tone}>{kindMeta.label}</Badge>
                      </View>
                    </View>

                    {post.title ? <Text style={styles.feedItemTitle}>{post.title}</Text> : null}

                    <TouchableOpacity onPress={() => setExpandedId(expanded ? null : post.id)} activeOpacity={0.7}>
                      <Text style={styles.feedItemBody} numberOfLines={expanded ? undefined : 4}>
                        {post.body}
                      </Text>
                    </TouchableOpacity>

                    {post.topics && post.topics.length > 0 ? (
                      <View style={styles.topicRow}>
                        {post.topics.map((t) => (
                          <View key={t} style={styles.topic}><Text style={styles.topicText}>#{t}</Text></View>
                        ))}
                      </View>
                    ) : null}

                    <View style={styles.actionsRow}>
                      <Button
                        title={`${viewerLiked ? "\u2764\uFE0F" : "\u2661"} ${post.reactionSummary?.total ?? 0}`}
                        variant="ghost"
                        size="sm"
                        onPress={() => handleReact(post)}
                        testID={`like-${post.id}`}
                      />
                      <Text style={styles.commentsText}>{(post.commentCount ?? 0) + " comments"}</Text>
                      <Button
                        title={post.bookmarked ? "Saved" : "Save"}
                        variant="ghost"
                        size="sm"
                        onPress={() => handleBookmark(post)}
                        testID={`bookmark-${post.id}`}
                      />
                    </View>
                  </View>
                </CardBody>
              </Card>
            );
          })
        )}
      </ScrollView>
    </Screen>
  );
}

function formatTimestamp(value?: string | null): string {
  if (!value) return "";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return "";
  const diffMins = Math.floor((Date.now() - d.getTime()) / 60_000);
  if (diffMins < 1) return "just now";
  if (diffMins < 60) return `${diffMins}m`;
  const h = Math.floor(diffMins / 60);
  if (h < 24) return `${h}h`;
  const days = Math.floor(h / 24);
  if (days < 7) return `${days}d`;
  return d.toLocaleDateString();
}

const styles = StyleSheet.create({
  scrollView: { flex: 1 },
  container: { padding: 16, gap: 12 },
  filterScroll: { flexGrow: 0 },
  filterContent: { gap: 8, paddingBottom: 4 },
  feedItemHeader: { marginBottom: 8 },
  authorRow: { flexDirection: "row", alignItems: "center", gap: 10 },
  avatar: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: "#4F46E5",
    alignItems: "center",
    justifyContent: "center",
  },
  avatarText: { color: "#fff", fontWeight: "600" },
  authorName: { fontSize: 14, fontWeight: "600", color: colors.gray[900] },
  metaText: { fontSize: 11, color: colors.gray[500] },
  feedItemTitle: { fontSize: 16, fontWeight: "600", color: colors.gray[900], marginTop: 4 },
  feedItemBody: { fontSize: 14, color: colors.gray[700], lineHeight: 21, marginTop: 4 },
  topicRow: { flexDirection: "row", flexWrap: "wrap", gap: 6, marginTop: 8 },
  topic: { paddingHorizontal: 8, paddingVertical: 2, backgroundColor: "#EEF2FF", borderRadius: 999 },
  topicText: { fontSize: 11, color: "#3730A3" },
  actionsRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    marginTop: 12,
    borderTopWidth: 1,
    borderTopColor: colors.gray[100],
    paddingTop: 8,
  },
  commentsText: { fontSize: 12, color: colors.gray[500], flex: 1 },
});
