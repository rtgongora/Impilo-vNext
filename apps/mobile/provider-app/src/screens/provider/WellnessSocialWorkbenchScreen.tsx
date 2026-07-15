/**
 * WellnessSocialWorkbenchScreen — provider/programme workbench for the wellness-SOVEREIGN social layer
 * (/internal/v1/wellness/social/**), DISTINCT from the generic ProviderSocialScreen (community-service).
 *
 * Sub-views: Official post composer, Announcement composer, Moderation inbox, Engagement summary
 * (aggregate-only), and Flag-to-PCT (safety report → care linkage). Backed by
 * wellnessSocialWorkbenchService.ts through the Experience BFF wellness proxy.
 */

import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet, RefreshControl } from "react-native";
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
  actOnReport,
  fetchEngagementSummary,
  fetchGroups,
  fetchModerationInbox,
  flagToPct,
  publishGroupAnnouncement,
  publishOfficialPost,
  type ContentReport,
  type ProgrammeEngagementSummary,
  type SocialGroupRef,
} from "../../services/wellnessSocialWorkbenchService";

const TABS = [
  "Official Post",
  "Announcement",
  "Moderation",
  "Engagement",
  "Flag to PCT",
] as const;
type Tab = (typeof TABS)[number];

export function WellnessSocialWorkbenchScreen() {
  const [tab, setTab] = useState<Tab>("Official Post");
  return (
    <Screen>
      <Header title="Wellness Social Workbench" />
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.tabsScroll}
        contentContainerStyle={styles.tabsRow}
      >
        {TABS.map((t) => (
          <Button
            key={t}
            title={t}
            size="sm"
            variant={tab === t ? "primary" : "ghost"}
            onPress={() => setTab(t)}
            testID={`wellness-social-workbench-tab-${t}`}
          />
        ))}
      </ScrollView>
      <View style={{ flex: 1 }}>
        {tab === "Official Post" && <OfficialPostComposer />}
        {tab === "Announcement" && <AnnouncementComposer />}
        {tab === "Moderation" && <ModerationInbox />}
        {tab === "Engagement" && <EngagementSummary />}
        {tab === "Flag to PCT" && <FlagToPct />}
      </View>
    </Screen>
  );
}

// ── shared group picker ─────────────────────────────────────────────────────
function useGroups() {
  const [groups, setGroups] = useState<SocialGroupRef[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setGroups(await fetchGroups());
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load groups");
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    void load();
  }, [load]);
  return { groups, loading, error, reload: load };
}

// ── Official post composer ──────────────────────────────────────────────────
export function OfficialPostComposer() {
  const { groups, loading, error } = useGroups();
  const [groupId, setGroupId] = useState("");
  const [body, setBody] = useState("");
  const [status, setStatus] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit() {
    if (!groupId || !body.trim()) return;
    setSubmitting(true);
    setStatus(null);
    try {
      await publishOfficialPost(groupId, body.trim());
      setBody("");
      setStatus("Official post published.");
    } catch (e) {
      setStatus(e instanceof Error ? e.message : "Failed to publish");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) return <LoadingSpinner />;
  if (error) return <ErrorState message={error} />;

  return (
    <ScrollView contentContainerStyle={styles.body}>
      <Card>
        <CardBody>
          <Text style={styles.label}>Group</Text>
          <Select
            value={groupId}
            onChange={setGroupId}
            options={groups.map((g) => ({ value: g.groupId, label: g.name }))}
            placeholder="Select a group"
          />
          <Text style={styles.label}>Message</Text>
          <TextField
            value={body}
            onChangeText={setBody}
            multiline
            placeholder="Share supportive, milestone-safe content (no clinical values)"
          />
          <Button title="Publish official post" onPress={submit} loading={submitting} disabled={!groupId} />
          {status && <Text style={styles.status}>{status}</Text>}
        </CardBody>
      </Card>
    </ScrollView>
  );
}

// ── Announcement composer ───────────────────────────────────────────────────
export function AnnouncementComposer() {
  const { groups, loading, error } = useGroups();
  const [groupId, setGroupId] = useState("");
  const [body, setBody] = useState("");
  const [status, setStatus] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit() {
    if (!groupId || !body.trim()) return;
    setSubmitting(true);
    setStatus(null);
    try {
      await publishGroupAnnouncement(groupId, body.trim());
      setBody("");
      setStatus("Announcement published.");
    } catch (e) {
      setStatus(e instanceof Error ? e.message : "Failed to publish");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) return <LoadingSpinner />;
  if (error) return <ErrorState message={error} />;

  return (
    <ScrollView contentContainerStyle={styles.body}>
      <Card>
        <CardBody>
          <Text style={styles.label}>Group</Text>
          <Select
            value={groupId}
            onChange={setGroupId}
            options={groups.map((g) => ({ value: g.groupId, label: g.name }))}
            placeholder="Select a group"
          />
          <Text style={styles.label}>Announcement</Text>
          <TextField value={body} onChangeText={setBody} multiline placeholder="Official announcement" />
          <Button title="Publish announcement" onPress={submit} loading={submitting} disabled={!groupId} />
          {status && <Text style={styles.status}>{status}</Text>}
        </CardBody>
      </Card>
    </ScrollView>
  );
}

// ── Moderation inbox ────────────────────────────────────────────────────────
const ACTIONS = ["HIDDEN", "REMOVED", "FLAGGED", "ESCALATED", "RESTORED", "REJECTED"];

export function ModerationInbox() {
  const [reports, setReports] = useState<ContentReport[]>([]);
  const [backlog, setBacklog] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const { reports: r, backlog: b } = await fetchModerationInbox("OPEN");
      setReports(r);
      setBacklog(b);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load inbox");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function act(reportId: string, actionState: string) {
    await actOnReport(reportId, actionState);
    void load();
  }

  if (loading) return <LoadingSpinner />;
  if (error) return <ErrorState message={error} onRetry={load} />;
  if (reports.length === 0) return <EmptyState title="No open reports" message="Backlog is clear." />;

  return (
    <ScrollView
      contentContainerStyle={styles.body}
      refreshControl={<RefreshControl refreshing={loading} onRefresh={load} />}
    >
      <Text style={styles.backlog}>Backlog: {backlog}</Text>
      {reports.map((r) => (
        <Card key={r.reportId}>
          <CardBody>
            <View style={styles.rowBetween}>
              <Badge label={r.reason} variant="destructive" />
              {r.careLinkageId ? <Badge label="Escalated to care" variant="warning" /> : null}
            </View>
            {r.detail ? <Text style={styles.detail}>{r.detail}</Text> : null}
            <Text style={styles.mono}>subject {r.subjectId}</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.actionsRow}>
              {ACTIONS.map((a) => (
                <Button key={a} title={a} size="sm" variant="ghost" onPress={() => act(r.reportId, a)} />
              ))}
            </ScrollView>
          </CardBody>
        </Card>
      ))}
    </ScrollView>
  );
}

// ── Engagement summary ──────────────────────────────────────────────────────
export function EngagementSummary() {
  const [summary, setSummary] = useState<ProgrammeEngagementSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setSummary(await fetchEngagementSummary());
    } catch (e) {
      setError(e instanceof Error ? e.message : "Access denied or no programme context");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) return <LoadingSpinner />;
  if (error) return <ErrorState message={error} onRetry={load} />;
  if (!summary) return <EmptyState title="No data" message="No engagement metrics available." />;

  const metrics: Array<[string, number]> = [
    ["Groups", summary.groups],
    ["Communities", summary.communities],
    ["Active members", summary.activeMembers],
    ["Posts", summary.posts],
    ["Official posts", summary.officialPosts],
    ["Challenge enrolment", summary.challengeEnrolment],
    ["Challenge completions", summary.challengeCompletions],
    ["Completion rate %", summary.challengeCompletionPct],
    ["Reported content", summary.reportedContent],
    ["Moderation backlog", summary.moderationBacklog],
    ["Safety escalations", summary.safetyEscalations],
  ];

  return (
    <ScrollView contentContainerStyle={styles.body}>
      <Text style={styles.note}>Aggregate only — no individual social content is shown.</Text>
      {metrics.map(([label, value]) => (
        <Card key={label}>
          <CardBody>
            <View style={styles.rowBetween}>
              <Text style={styles.metricLabel}>{label}</Text>
              <Text style={styles.metricValue}>{value}</Text>
            </View>
          </CardBody>
        </Card>
      ))}
    </ScrollView>
  );
}

// ── Flag to PCT ─────────────────────────────────────────────────────────────
export function FlagToPct() {
  const [subjectType, setSubjectType] = useState("POST");
  const [subjectId, setSubjectId] = useState("");
  const [owner, setOwner] = useState("");
  const [reason, setReason] = useState<"SELF_HARM" | "ABUSE">("SELF_HARM");
  const [detail, setDetail] = useState("");
  const [status, setStatus] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit() {
    if (!subjectId || !owner) return;
    setSubmitting(true);
    setStatus(null);
    try {
      const res = await flagToPct(subjectType, subjectId, owner, reason, detail || undefined);
      setStatus(
        res.escalated
          ? `Escalated to care (linkage ${res.careLinkageId ?? "created"}).`
          : "Report filed.",
      );
    } catch (e) {
      setStatus(e instanceof Error ? e.message : "Failed to flag");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <ScrollView contentContainerStyle={styles.body}>
      <Card>
        <CardBody>
          <Text style={styles.note}>
            Safety reports (self-harm / abuse) escalate to PCT via a persisted care linkage — never
            handled socially.
          </Text>
          <Text style={styles.label}>Subject type</Text>
          <Select
            value={subjectType}
            onChange={setSubjectType}
            options={[
              { value: "POST", label: "Post" },
              { value: "COMMENT", label: "Comment" },
              { value: "REEL", label: "Reel" },
            ]}
          />
          <Text style={styles.label}>Subject ID</Text>
          <TextField value={subjectId} onChangeText={setSubjectId} placeholder="content UUID" />
          <Text style={styles.label}>Content owner CPID</Text>
          <TextField value={owner} onChangeText={setOwner} placeholder="person cpid" />
          <Text style={styles.label}>Reason</Text>
          <Select
            value={reason}
            onChange={(v) => setReason(v as "SELF_HARM" | "ABUSE")}
            options={[
              { value: "SELF_HARM", label: "Self-harm" },
              { value: "ABUSE", label: "Abuse" },
            ]}
          />
          <Text style={styles.label}>Detail</Text>
          <TextField value={detail} onChangeText={setDetail} multiline placeholder="Context (optional)" />
          <Button title="Flag to PCT" onPress={submit} loading={submitting} variant="destructive" />
          {status && <Text style={styles.status}>{status}</Text>}
        </CardBody>
      </Card>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  tabsScroll: { maxHeight: 52 },
  tabsRow: { paddingHorizontal: 12, gap: 8, alignItems: "center" },
  body: { padding: 12, gap: 12 },
  label: { fontSize: 13, fontWeight: "600", marginTop: 8, marginBottom: 4 },
  status: { marginTop: 8, fontSize: 13, color: "#1E40AF" },
  note: { fontSize: 12, color: "#6B7280", marginBottom: 8 },
  backlog: { fontSize: 13, color: "#6B7280", marginBottom: 4 },
  detail: { fontSize: 14, marginVertical: 6 },
  mono: { fontFamily: "monospace", fontSize: 11, color: "#6B7280" },
  actionsRow: { marginTop: 8 },
  rowBetween: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  metricLabel: { fontSize: 14, color: "#374151" },
  metricValue: { fontSize: 18, fontWeight: "700" },
});
