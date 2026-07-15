/**
 * HealthInfoScreen — guest-first public health information (no sign-in), the citizen mobile
 * mirror of the web PublicHealthInfo. Browses/searches the anonymous gateway guidance lane
 * and renders governed public disclosure only (categories, summaries, plain-language body).
 *
 * Distinct from PublicHealthScreen, which uses the AUTHENTICATED /mobile/citizen/public-health
 * lane. This is the guest gateway surface: publicApiClient, no PII, no personalization.
 */
import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView, Pressable, TouchableOpacity } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import {
  Screen,
  Header,
  Card,
  CardBody,
  TextField,
  Button,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import {
  fetchPublicEducation,
  fetchPublicEducationCategories,
  fetchPublicEducationArticle,
  type PublicEducationPage,
  type PublicEducationCategory,
  type PublicEducationArticle,
} from "../../services/gatewayHealthInfoService";

const GREEN = "#059669";
const UNAVAILABLE =
  "Health information is temporarily unavailable. In an emergency, call 999 or 112 or go to the nearest health facility.";

function categoryLabel(slug: string): string {
  const words = slug.replace(/-/g, " ").trim();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export function HealthInfoScreen({ onBack }: { onBack?: () => void }) {
  const [categories, setCategories] = useState<PublicEducationCategory[]>([]);
  const [activeCategory, setActiveCategory] = useState("");
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PublicEducationPage | null>(null);
  const [listLoading, setListLoading] = useState(true);
  const [listError, setListError] = useState<string | null>(null);

  const [article, setArticle] = useState<PublicEducationArticle | null>(null);
  const [articleLoading, setArticleLoading] = useState(false);
  const [articleError, setArticleError] = useState<string | null>(null);

  const loadList = useCallback(async (category: string, q: string, nextPage: number) => {
    setListLoading(true);
    setListError(null);
    try {
      const res = await fetchPublicEducation({ category: category || undefined, q: q || undefined, page: nextPage, size: 10 });
      setResult(res);
      setActiveCategory(category);
      setQuery(q);
      setPage(nextPage);
    } catch {
      setResult(null);
      setListError(UNAVAILABLE);
    } finally {
      setListLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadList("", "", 0);
    void (async () => {
      const cats = await fetchPublicEducationCategories().catch(() => []);
      setCategories(cats);
    })();
  }, [loadList]);

  const openArticle = useCallback(async (id: string) => {
    setArticleLoading(true);
    setArticleError(null);
    try {
      setArticle(await fetchPublicEducationArticle(id));
    } catch {
      setArticleError("This article could not be loaded right now. It may have been unpublished.");
    } finally {
      setArticleLoading(false);
    }
  }, []);

  if (article) {
    return (
      <Screen>
        <Header title={categoryLabel(article.category)} />
        <ScrollView contentContainerStyle={styles.body}>
          <Pressable onPress={() => setArticle(null)} style={styles.backRow}>
            <Ionicons name="arrow-back" size={18} color={GREEN} />
            <Text style={styles.backText}>All topics</Text>
          </Pressable>
          <Text style={styles.articleTitle}>{article.title}</Text>
          <Text style={styles.articleSummary}>{article.summary}</Text>
          {article.body.split(/\n\n+/).map((paragraph, i) => (
            <Text key={i} style={styles.articleBody}>
              {paragraph}
            </Text>
          ))}
          <Text style={styles.disclaimer}>
            This is general health information, not personal medical advice. For advice about your own
            health, speak to a health worker. In an emergency, call 999 or 112.
          </Text>
        </ScrollView>
      </Screen>
    );
  }

  return (
    <Screen>
      <Header
        title="Health information"
        subtitle="Trusted, plain-language guidance — no sign-in needed"
        leftElement={
          onBack ? (
            <Pressable onPress={onBack} hitSlop={8} accessibilityLabel="Back">
              <Ionicons name="arrow-back" size={22} color={GREEN} />
            </Pressable>
          ) : undefined
        }
      />
      <ScrollView testID="health-info-screen" contentContainerStyle={styles.body}>
        <View style={styles.searchRow}>
          <View style={styles.searchField}>
            <TextField
              testID="health-info-search"
              value={search}
              onChangeText={setSearch}
              placeholder="Search health topics"
              autoCapitalize="none"
            />
          </View>
          <Button title="Search" size="md" onPress={() => void loadList(activeCategory, search, 0)} testID="health-info-search-go" />
        </View>

        {categories.length > 0 ? (
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipRow}>
            <TopicChip label="All topics" active={activeCategory === ""} onPress={() => void loadList("", query, 0)} />
            {categories.map((c) => (
              <TopicChip
                key={c.category}
                label={`${categoryLabel(c.category)} (${c.count})`}
                active={activeCategory === c.category}
                onPress={() => void loadList(c.category, query, 0)}
              />
            ))}
          </ScrollView>
        ) : null}

        {articleError ? <Text style={styles.inlineError}>{articleError}</Text> : null}

        {listLoading ? (
          <LoadingSpinner />
        ) : listError ? (
          <ErrorState message={listError} onRetry={() => void loadList(activeCategory, query, page)} />
        ) : result && result.data.length === 0 ? (
          <EmptyState
            title="No articles here yet"
            description="Try another topic or search term — more content is added over time."
          />
        ) : result ? (
          <Card>
            <CardBody>
              {result.data.map((topic, idx) => (
                <TouchableOpacity
                  key={topic.id}
                  testID={`health-info-topic-${topic.id}`}
                  onPress={() => void openArticle(topic.id)}
                  disabled={articleLoading}
                  style={[styles.topicRow, idx < result.data.length - 1 ? styles.topicRowBorder : undefined]}
                >
                  <Text style={styles.topicCategory}>{categoryLabel(topic.category)}</Text>
                  <Text style={styles.topicTitle}>{topic.title}</Text>
                  <Text style={styles.topicSummary}>{topic.summary}</Text>
                </TouchableOpacity>
              ))}
            </CardBody>
          </Card>
        ) : null}

        {result && result.meta.totalPages > 1 ? (
          <View style={styles.pager}>
            <Button
              title="Previous"
              variant="outline"
              size="sm"
              disabled={listLoading || page === 0}
              onPress={() => void loadList(activeCategory, query, page - 1)}
            />
            <Text style={styles.pagerText}>
              Page {page + 1} of {result.meta.totalPages}
            </Text>
            <Button
              title="Next"
              variant="outline"
              size="sm"
              disabled={listLoading || page >= result.meta.totalPages - 1}
              onPress={() => void loadList(activeCategory, query, page + 1)}
            />
          </View>
        ) : null}
      </ScrollView>
    </Screen>
  );
}

function TopicChip({ label, active, onPress }: { label: string; active: boolean; onPress: () => void }) {
  return (
    <TouchableOpacity
      onPress={onPress}
      style={[styles.chip, active ? styles.chipActive : styles.chipInactive]}
      activeOpacity={0.85}
    >
      <Text style={[styles.chipText, active ? styles.chipTextActive : styles.chipTextInactive]}>{label}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  body: { padding: 16, gap: 12 },
  searchRow: { flexDirection: "row", alignItems: "flex-end", gap: 8 },
  searchField: { flex: 1 },
  chipRow: { gap: 8, paddingVertical: 4 },
  chip: { borderRadius: 20, paddingVertical: 6, paddingHorizontal: 12, borderWidth: 1 },
  chipActive: { backgroundColor: GREEN, borderColor: GREEN },
  chipInactive: { backgroundColor: "#FFFFFF", borderColor: "#D1D5DB" },
  chipText: { fontSize: 12, fontWeight: "600" },
  chipTextActive: { color: "#FFFFFF" },
  chipTextInactive: { color: "#374151" },
  topicRow: { paddingVertical: 12, gap: 2 },
  topicRowBorder: { borderBottomWidth: 1, borderBottomColor: "#F3F4F6" },
  topicCategory: { fontSize: 11, fontWeight: "700", color: GREEN, textTransform: "uppercase" },
  topicTitle: { fontSize: 15, fontWeight: "700", color: "#111827" },
  topicSummary: { fontSize: 13, color: "#6B7280" },
  pager: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginTop: 4 },
  pagerText: { fontSize: 12, color: "#6B7280" },
  backRow: { flexDirection: "row", alignItems: "center", gap: 6, marginBottom: 8 },
  backText: { color: GREEN, fontSize: 14, fontWeight: "600" },
  articleTitle: { fontSize: 22, fontWeight: "800", color: "#111827", marginTop: 4 },
  articleSummary: { fontSize: 14, color: "#6B7280", marginTop: 6, marginBottom: 8 },
  articleBody: { fontSize: 15, color: "#374151", lineHeight: 22, marginTop: 8 },
  disclaimer: { fontSize: 12, color: "#9CA3AF", marginTop: 20, lineHeight: 18 },
  inlineError: { fontSize: 13, color: "#DC2626" },
});
