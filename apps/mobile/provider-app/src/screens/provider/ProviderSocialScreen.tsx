/**
 * ProviderSocialScreen — Professional communities of practice, mentorship
 * groups, facility-page updates, and operational announcements for clinicians,
 * CHWs, and facility staff.
 *
 * Backed by experience-bff /internal/v1/mobile/provider/social/*.
 */

import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, RefreshControl } from "react-native";
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
  TextField,
  Select,
} from "@impilo/mobile-design-system";
import {
  createProviderPost,
  fetchProviderCommunities,
  fetchProviderFeed,
  fetchProviderGroups,
  reactToProviderPost,
  type ProviderFeedScope,
  type ProviderSocialCommunity,
  type ProviderSocialGroup,
  type ProviderSocialPost,
} from "../../services/providerSocialService";
import { useAppStore } from "../../stores/appStore";

const SCOPES: Array<{ value: ProviderFeedScope; label: string }> = [
  { value: "home", label: "Home" },
  { value: "facility", label: "Facility" },
  { value: "announcements", label: "Announcements" },
  { value: "learning", label: "Learning" },
  { value: "public_health", label: "Public health" },
];

const TABS = ["Feed", "Communities", "Groups", "Compose"] as const;
type Tab = (typeof TABS)[number];

export function ProviderSocialScreen() {
  const [tab, setTab] = useState<Tab>("Feed");
  const { setProviderTab } = useAppStore();
  return (
    <Screen>
      <Header title="Professional Network" />
      <View style={styles.workbenchLink}>
        <Button
          title="Wellness Social Workbench"
          size="sm"
          variant="secondary"
          onPress={() => setProviderTab("wellnessSocial")}
          testID="open-wellness-social-workbench"
        />
      </View>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.tabsScroll} contentContainerStyle={styles.tabsRow}>
        {TABS.map((t) => (
          <Button
            key={t}
            title={t}
            size="sm"
            variant={tab === t ? "primary" : "ghost"}
            onPress={() => setTab(t)}
            testID={`provider-social-tab-${t}`}
          />
        ))}
      </ScrollView>
      <View style={{ flex: 1 }}>
        {tab === "Feed" && <FeedTab />}
        {tab === "Communities" && <CommunitiesTab />}
        {tab === "Groups" && <GroupsTab />}
        {tab === "Compose" && <ComposeTab />}
      </View>
    </Screen>
  );
}

function FeedTab() {
  const [scope, setScope] = useState<ProviderFeedScope>("home");
  const [items, setItems] = useState<ProviderSocialPost[]>([]);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const load = useCallback(async (s: ProviderFeedScope) => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetchProviderFeed(s, { limit: 25 });
      setItems(res.items);
    } catch (e) {
      setError(e instanceof Error ? e : new Error(String(e)));
      setItems([]);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => { load(scope); }, [load, scope]);

  return (
    <ScrollView
      style={styles.body}
      contentContainerStyle={styles.bodyContent}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); load(scope); }} />}
    >
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.scopeRow}>
        {SCOPES.map((s) => (
          <Button
            key={s.value}
            title={s.label}
            size="sm"
            variant={scope === s.value ? "primary" : "ghost"}
            onPress={() => setScope(s.value)}
            testID={`provider-feed-scope-${s.value}`}
          />
        ))}
      </ScrollView>

      {error ? <ErrorState title="Couldn't load feed" message={error.message} onRetry={() => load(scope)} /> : null}
      {loading && items.length === 0 ? (
        <LoadingSpinner size="md" />
      ) : items.length === 0 ? (
        <EmptyState title="No posts" message="Join a community or follow a facility page to populate your feed." />
      ) : (
        items.map((p) => (
          <Card key={p.id}>
            <CardBody>
              <View testID={`provider-post-${p.id}`}>
                <View style={styles.postHeader}>
                  <View style={styles.avatar}><Text style={styles.avatarText}>{p.author.displayName.charAt(0).toUpperCase()}</Text></View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.authorName}>{p.author.displayName}</Text>
                    <Text style={styles.meta}>
                      {p.author.roleBadge ? `${p.author.roleBadge} · ` : ""}
                      {p.author.facilityName ? `${p.author.facilityName} · ` : ""}
                      {formatTimestamp(p.publishedAt ?? p.createdAt)}
                    </Text>
                  </View>
                  {p.kind === "announcement" ? <Badge label="Announcement" variant="destructive" /> : null}
                </View>
                {p.title ? <Text style={styles.title}>{p.title}</Text> : null}
                <Text style={styles.postBody}>{p.body}</Text>
                {p.topics && p.topics.length > 0 ? (
                  <View style={styles.topicRow}>
                    {p.topics.map((t) => (
                      <View key={t} style={styles.topic}><Text style={styles.topicText}>#{t}</Text></View>
                    ))}
                  </View>
                ) : null}
                <View style={styles.actionsRow}>
                  <Button
                    title={`${p.reactionSummary?.viewerReaction === "LIKE" ? "♥" : "♡"} ${p.reactionSummary?.total ?? 0}`}
                    variant="ghost"
                    size="sm"
                    onPress={() => reactToProviderPost(p.id).then(() => load(scope)).catch(() => undefined)}
                    testID={`provider-like-${p.id}`}
                  />
                  <Text style={styles.commentsText}>{(p.commentCount ?? 0) + " comments"}</Text>
                </View>
              </View>
            </CardBody>
          </Card>
        ))
      )}
    </ScrollView>
  );
}

function CommunitiesTab() {
  const [items, setItems] = useState<ProviderSocialCommunity[]>([]);
  const [loading, setLoading] = useState(true);
  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await fetchProviderCommunities("community_of_practice"));
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { load(); }, [load]);

  return (
    <ScrollView style={styles.body} contentContainerStyle={styles.bodyContent}>
      {loading ? <LoadingSpinner /> : items.length === 0 ? (
        <EmptyState title="No communities" message="Communities of practice will appear here." />
      ) : (
        items.map((c) => (
          <Card key={c.id}>
            <CardBody>
              <Text style={styles.title}>{c.name}</Text>
              <Text style={styles.meta}>{c.category} · {c.memberCount} members · {c.kind}</Text>
              {c.description ? <Text style={styles.postBody}>{c.description}</Text> : null}
            </CardBody>
          </Card>
        ))
      )}
    </ScrollView>
  );
}

function GroupsTab() {
  const [items, setItems] = useState<ProviderSocialGroup[]>([]);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    setLoading(true);
    fetchProviderGroups().then(setItems).finally(() => setLoading(false));
  }, []);

  return (
    <ScrollView style={styles.body} contentContainerStyle={styles.bodyContent}>
      {loading ? <LoadingSpinner /> : items.length === 0 ? (
        <EmptyState title="No groups" message="Mentorship and training groups will appear here." />
      ) : (
        items.map((g) => (
          <Card key={g.id}>
            <CardBody>
              <Text style={styles.title}>{g.name}</Text>
              <Text style={styles.meta}>{g.category || "Group"} · {g.memberCount} members</Text>
              {g.description ? <Text style={styles.postBody}>{g.description}</Text> : null}
            </CardBody>
          </Card>
        ))
      )}
    </ScrollView>
  );
}

function ComposeTab() {
  const [kind, setKind] = useState("text");
  const [body, setBody] = useState("");
  const [visibility, setVisibility] = useState("facility");
  const [busy, setBusy] = useState(false);
  const [hint, setHint] = useState<string | null>(null);

  const submit = async (asDraft = false) => {
    if (!body.trim()) return;
    setBusy(true);
    setHint(null);
    try {
      await createProviderPost({ kind, body: body.trim(), visibility, saveAsDraft: asDraft, source: "provider-app" });
      setBody("");
      setHint(asDraft ? "Draft saved." : "Posted.");
    } catch (e) {
      setHint(e instanceof Error ? e.message : "Couldn't post.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <ScrollView style={styles.body} contentContainerStyle={styles.bodyContent}>
      <Card>
        <CardBody>
          <View style={{ gap: 12 }}>
            <Select
              label="Kind"
              value={kind}
              onChange={setKind}
              options={[
                { value: "text", label: "Update" },
                { value: "announcement", label: "Announcement" },
                { value: "learning", label: "Learning resource" },
                { value: "event", label: "Event" },
                { value: "question", label: "Question" },
              ]}
            />
            <TextField
              label="Message"
              value={body}
              onChangeText={setBody}
              placeholder="Share an update with your colleagues…"
              multiline
              numberOfLines={5}
              testID="provider-compose-body"
            />
            <Select
              label="Visibility"
              value={visibility}
              onChange={setVisibility}
              options={[
                { value: "facility", label: "Facility" },
                { value: "organisation", label: "Organisation" },
                { value: "community", label: "Community" },
                { value: "role_only", label: "Role peers only" },
                { value: "private", label: "Only me (draft)" },
              ]}
            />
            {hint ? <Text style={styles.hint}>{hint}</Text> : null}
            <View style={{ flexDirection: "row", justifyContent: "flex-end", gap: 8 }}>
              <Button title="Save draft" size="sm" variant="secondary" onPress={() => submit(true)} disabled={busy || !body.trim()} testID="provider-compose-draft" />
              <Button title={busy ? "Posting…" : "Post"} size="sm" variant="primary" onPress={() => submit(false)} disabled={busy || !body.trim()} testID="provider-compose-submit" />
            </View>
          </View>
        </CardBody>
      </Card>
    </ScrollView>
  );
}

function formatTimestamp(value?: string | null): string {
  if (!value) return "";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return "";
  const diff = Math.floor((Date.now() - d.getTime()) / 60_000);
  if (diff < 1) return "just now";
  if (diff < 60) return `${diff}m`;
  const h = Math.floor(diff / 60);
  if (h < 24) return `${h}h`;
  const days = Math.floor(h / 24);
  if (days < 7) return `${days}d`;
  return d.toLocaleDateString();
}

const styles = StyleSheet.create({
  workbenchLink: { paddingHorizontal: 12, paddingTop: 6, alignItems: "flex-start" },
  tabsScroll: { flexGrow: 0, paddingHorizontal: 12, paddingVertical: 6, backgroundColor: "#FFFFFF" },
  tabsRow: { gap: 6 },
  body: { flex: 1 },
  bodyContent: { padding: 16, gap: 12 },
  scopeRow: { gap: 6, paddingBottom: 8 },
  postHeader: { flexDirection: "row", gap: 10, alignItems: "center", marginBottom: 8 },
  avatar: { width: 36, height: 36, borderRadius: 18, backgroundColor: "#1E40AF", alignItems: "center", justifyContent: "center" },
  avatarText: { color: "#fff", fontWeight: "700" },
  authorName: { fontSize: 14, fontWeight: "600", color: "#111827" },
  meta: { fontSize: 11, color: "#6B7280" },
  title: { fontSize: 16, fontWeight: "600", color: "#111827", marginTop: 2 },
  postBody: { fontSize: 14, color: "#374151", lineHeight: 20, marginTop: 4 },
  topicRow: { flexDirection: "row", flexWrap: "wrap", gap: 6, marginTop: 8 },
  topic: { paddingHorizontal: 8, paddingVertical: 2, backgroundColor: "#E0E7FF", borderRadius: 999 },
  topicText: { fontSize: 11, color: "#3730A3" },
  actionsRow: { flexDirection: "row", alignItems: "center", gap: 12, marginTop: 12, borderTopWidth: 1, borderTopColor: "#F3F4F6", paddingTop: 8 },
  commentsText: { fontSize: 12, color: "#6B7280", flex: 1 },
  hint: { fontSize: 12, color: "#6B7280" },
});
