/**
 * SocialFeedScreen — Community feed with health tips, campaigns, and reminders.
 */

import React, { useState, useEffect, useCallback } from "react";
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

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, { title: "Feed" }),
    React.createElement(
      "div",
      { "data-testid": "social-feed-screen", style: { padding: "16px", display: "flex", flexDirection: "column", gap: "16px" } },

      // Category filters
      React.createElement(
        "div",
        { style: { display: "flex", gap: "8px", overflowX: "auto", paddingBottom: "4px" } },
        CATEGORY_FILTERS.map((cat) =>
          React.createElement(Button, {
            key: cat.value,
            title: cat.label,
            variant: filter === cat.value ? "primary" : "ghost",
            size: "sm",
            onPress: () => setFilter(cat.value),
            testID: `feed-filter-${cat.value || "all"}`,
          })
        )
      ),

      error
        ? React.createElement(ErrorState, { title: "Error", message: error.message, onRetry: () => load(0, true) })
        : null,

      isLoading && items.length === 0
        ? React.createElement(LoadingSpinner, { size: "md" })
        : items.length === 0
          ? React.createElement(EmptyState, {
              title: "No posts yet",
              message: "Check back later for health tips, campaigns, and community updates",
            })
          : React.createElement(
              React.Fragment,
              null,
              items.map((item) =>
                React.createElement(
                  Card,
                  { key: item.id },
                  React.createElement(
                    CardBody,
                    null,
                    React.createElement(
                      "div",
                      { "data-testid": `feed-item-${item.id}` },

                      // Header row
                      React.createElement(
                        "div",
                        { style: { display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "8px" } },
                        React.createElement(Badge, {
                          variant: TYPE_COLORS[item.type],
                          children: item.type.replace("_", " "),
                        }),
                        React.createElement("span", { style: { fontSize: "12px", color: "#9CA3AF" } },
                          new Date(item.publishedAt).toLocaleDateString()
                        )
                      ),

                      // Title
                      React.createElement("h4", { style: { margin: "0 0 8px", fontSize: "16px" } }, item.title),

                      // Image
                      item.imageUrl
                        ? React.createElement("img", {
                            src: item.imageUrl,
                            alt: item.title,
                            style: { width: "100%", borderRadius: "8px", marginBottom: "8px", maxHeight: "200px", objectFit: "cover" },
                          })
                        : null,

                      // Body (truncated or expanded)
                      React.createElement(
                        "p",
                        {
                          style: {
                            fontSize: "14px",
                            color: "#374151",
                            margin: "0 0 8px",
                            lineHeight: "1.5",
                            ...(expandedId !== item.id ? { overflow: "hidden", display: "-webkit-box", WebkitLineClamp: 3, WebkitBoxOrient: "vertical" as const } : {}),
                          },
                          onClick: () => setExpandedId(expandedId === item.id ? null : item.id),
                        },
                        item.body
                      ),

                      // Author
                      item.author
                        ? React.createElement("p", { style: { fontSize: "13px", color: "#6B7280", margin: "0 0 8px" } },
                            `By ${item.author}`
                          )
                        : null,

                      // Actions
                      React.createElement(
                        "div",
                        { style: { display: "flex", gap: "16px", alignItems: "center" } },
                        React.createElement(Button, {
                          title: `${item.liked ? "\u2764\uFE0F" : "\u2661"} ${item.likesCount}`,
                          variant: "ghost",
                          size: "sm",
                          onPress: () => handleLikeToggle(item),
                          testID: `like-${item.id}`,
                        }),
                        React.createElement("span", { style: { fontSize: "13px", color: "#6B7280" } },
                          `${item.commentsCount} comments`
                        ),
                        item.actionUrl
                          ? React.createElement(Button, {
                              title: "Learn More",
                              variant: "secondary",
                              size: "sm",
                              onPress: () => { window.open(item.actionUrl, "_blank"); },
                              testID: `action-${item.id}`,
                            })
                          : null
                      )
                    )
                  )
                )
              ),

              // Load more
              hasMore
                ? React.createElement(Button, {
                    title: isLoading ? "Loading..." : "Load More",
                    variant: "ghost",
                    onPress: () => load(page + 1, false),
                    disabled: isLoading,
                    testID: "feed-load-more",
                  })
                : null
            )
    )
  );
}
