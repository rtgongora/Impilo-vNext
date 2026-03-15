/**
 * SocialFeedScreen — Community feed with health tips, campaigns, and reminders.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, Image, TouchableOpacity, StyleSheet } from "react-native";
import * as Linking from "expo-linking";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { fetchFeed, likeFeedItem, unlikeFeedItem } from "../../services/feedService";
import type { FeedItem, FeedItemType } from "../../types";

const CATEGORY_FILTERS = [
  { label: "All", value: "" },
  { label: "Health Tips", value: "HEALTH_TIP" },
  { label: "Campaigns", value: "CAMPAIGN" },
  { label: "Reminders", value: "REMINDER" },
  { label: "Community", value: "COMMUNITY" },
  { label: "Announcements", value: "ANNOUNCEMENT" },
];

const TYPE_COLORS: Record<FeedItemType, "default" | "secondary" | "destructive" | "outline"> = {
  ANNOUNCEMENT: "destructive",
  HEALTH_TIP: "default",
  CAMPAIGN: "secondary",
  REMINDER: "outline",
  COMMUNITY: "secondary",
};

export function SocialFeedScreen() {
  const [items, setItems] = useState<FeedItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [filter, setFilter] = useState("");
  const [hasMore, setHasMore] = useState(true);
  const [page, setPage] = useState(0);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const load = useCallback(async (pageNum: number, replace: boolean) => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchFeed({
        category: filter || undefined,
        page: pageNum,
        size: 20,
      });
      setItems((prev) => replace ? result.items : [...prev, ...result.items]);
      setHasMore(result.hasNext);
      setPage(pageNum);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, [filter]);

  useEffect(() => {
    load(0, true);
  }, [load]);

  const handleLikeToggle = useCallback(async (item: FeedItem) => {
    try {
      if (item.liked) {
        await unlikeFeedItem(item.id);
      } else {
        await likeFeedItem(item.id);
      }
      setItems((prev) =>
        prev.map((i) =>
          i.id === item.id
            ? { ...i, liked: !i.liked, likesCount: i.liked ? i.likesCount - 1 : i.likesCount + 1 }
            : i
        )
      );
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    }
  }, []);

  return (
    <Screen>
      <Header title="Feed" />
      <ScrollView testID="social-feed-screen" style={styles.scrollView} contentContainerStyle={styles.container}>
        {/* Category filters */}
        <ScrollView horizontal style={styles.filterScroll} contentContainerStyle={styles.filterContent}>
          {CATEGORY_FILTERS.map((cat) => (
            <Button
              key={cat.value}
              title={cat.label}
              variant={filter === cat.value ? "primary" : "ghost"}
              size="sm"
              onPress={() => setFilter(cat.value)}
              testID={`feed-filter-${cat.value || "all"}`}
            />
          ))}
        </ScrollView>

        {error ? (
          <ErrorState title="Error" message={error.message} onRetry={() => load(0, true)} />
        ) : null}

        {isLoading && items.length === 0 ? (
          <LoadingSpinner size="md" />
        ) : items.length === 0 ? (
          <EmptyState
            title="No posts yet"
            message="Check back later for health tips, campaigns, and community updates"
          />
        ) : (
          <>
            {items.map((item) => (
              <Card key={item.id}>
                <CardBody>
                  <View testID={`feed-item-${item.id}`}>
                    {/* Header row */}
                    <View style={styles.feedItemHeader}>
                      <Badge variant={TYPE_COLORS[item.type]}>
                        {item.type.replace("_", " ")}
                      </Badge>
                      <Text style={styles.dateText}>
                        {new Date(item.publishedAt).toLocaleDateString()}
                      </Text>
                    </View>

                    {/* Title */}
                    <Text style={styles.feedItemTitle}>{item.title}</Text>

                    {/* Image */}
                    {item.imageUrl ? (
                      <Image
                        source={{ uri: item.imageUrl }}
                        accessibilityLabel={item.title}
                        style={styles.feedItemImage}
                        resizeMode="cover"
                      />
                    ) : null}

                    {/* Body (truncated or expanded) */}
                    <TouchableOpacity
                      onPress={() => setExpandedId(expandedId === item.id ? null : item.id)}
                      activeOpacity={0.7}
                    >
                      <Text
                        style={styles.feedItemBody}
                        numberOfLines={expandedId !== item.id ? 3 : undefined}
                      >
                        {item.body}
                      </Text>
                    </TouchableOpacity>

                    {/* Author */}
                    {item.author ? (
                      <Text style={styles.authorText}>{`By ${item.author}`}</Text>
                    ) : null}

                    {/* Actions */}
                    <View style={styles.actionsRow}>
                      <Button
                        title={`${item.liked ? "\u2764\uFE0F" : "\u2661"} ${item.likesCount}`}
                        variant="ghost"
                        size="sm"
                        onPress={() => handleLikeToggle(item)}
                        testID={`like-${item.id}`}
                      />
                      <Text style={styles.commentsText}>
                        {`${item.commentsCount} comments`}
                      </Text>
                      {item.actionUrl ? (
                        <Button
                          title="Learn More"
                          variant="secondary"
                          size="sm"
                          onPress={() => { Linking.openURL(item.actionUrl); }}
                          testID={`action-${item.id}`}
                        />
                      ) : null}
                    </View>
                  </View>
                </CardBody>
              </Card>
            ))}

            {/* Load more */}
            {hasMore ? (
              <Button
                title={isLoading ? "Loading..." : "Load More"}
                variant="ghost"
                onPress={() => load(page + 1, false)}
                disabled={isLoading}
                testID="feed-load-more"
              />
            ) : null}
          </>
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
  },
  container: {
    padding: 16,
    gap: 16,
  },
  filterScroll: {
    flexGrow: 0,
  },
  filterContent: {
    gap: 8,
    paddingBottom: 4,
  },
  feedItemHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 8,
  },
  dateText: {
    fontSize: 12,
    color: "#9CA3AF",
  },
  feedItemTitle: {
    fontSize: 16,
    fontWeight: "600",
    marginBottom: 8,
  },
  feedItemImage: {
    width: "100%",
    height: 200,
    borderRadius: 8,
    marginBottom: 8,
  },
  feedItemBody: {
    fontSize: 14,
    color: "#374151",
    lineHeight: 21,
    marginBottom: 8,
  },
  authorText: {
    fontSize: 13,
    color: "#6B7280",
    marginBottom: 8,
  },
  actionsRow: {
    flexDirection: "row",
    gap: 16,
    alignItems: "center",
  },
  commentsText: {
    fontSize: 13,
    color: "#6B7280",
  },
});
