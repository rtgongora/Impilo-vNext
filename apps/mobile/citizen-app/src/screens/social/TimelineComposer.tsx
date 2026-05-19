/**
 * TimelineComposer — Mobile post composer used at the top of the citizen
 * social timeline. Supports text, question, event, health promotion, learning
 * post kinds; visibility selection; topics; Nompilo assist (improve, simplify,
 * translate, suggest tags, safety check) with graceful fallback.
 */

import React, { useState } from "react";
import { View, Text, StyleSheet, TouchableOpacity, ScrollView } from "react-native";
import {
  Button,
  Card,
  CardBody,
  TextField,
  Select,
} from "@impilo/mobile-design-system";
import {
  createSocialPost,
  nompiloComposerAssist,
  type CreateSocialPostInput,
} from "../../services/socialService";

const POST_KINDS = [
  { value: "text", label: "Update" },
  { value: "question", label: "Question" },
  { value: "event", label: "Event" },
  { value: "learning", label: "Learning" },
  { value: "health_promotion", label: "Health promotion" },
];

const VISIBILITY_OPTIONS = [
  { value: "public", label: "Public" },
  { value: "community", label: "Community only" },
  { value: "group", label: "Group only" },
  { value: "private", label: "Only me (draft)" },
];

const ASSIST_OPS: Array<{ op: "improve" | "simplify" | "translate" | "suggest_tags" | "safety_check"; label: string; language?: string }> = [
  { op: "improve", label: "Improve" },
  { op: "simplify", label: "Simplify" },
  { op: "translate", label: "Translate · sn", language: "sn" },
  { op: "translate", label: "Translate · nd", language: "nd" },
  { op: "suggest_tags", label: "Suggest tags" },
  { op: "safety_check", label: "Safety check" },
];

interface Props {
  defaultScope?: { communityId?: string; groupId?: string; pageId?: string };
  onCreated?: () => void;
}

export function TimelineComposer({ defaultScope, onCreated }: Props) {
  const [expanded, setExpanded] = useState(false);
  const [kind, setKind] = useState("text");
  const [body, setBody] = useState("");
  const [visibility, setVisibility] = useState("public");
  const [topics, setTopics] = useState<string[]>([]);
  const [topicDraft, setTopicDraft] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [assistBusy, setAssistBusy] = useState(false);
  const [hint, setHint] = useState<string | null>(null);

  const reset = () => {
    setExpanded(false);
    setKind("text");
    setBody("");
    setVisibility("public");
    setTopics([]);
    setTopicDraft("");
    setHint(null);
  };

  const addTopic = () => {
    const t = topicDraft.trim().replace(/^#/, "");
    if (!t || topics.includes(t)) return;
    setTopics((prev) => [...prev, t].slice(0, 8));
    setTopicDraft("");
  };

  const submit = async (asDraft = false) => {
    if (!body.trim()) return;
    setSubmitting(true);
    setHint(null);
    try {
      const input: CreateSocialPostInput = {
        kind,
        body: body.trim(),
        visibility,
        topics,
        scope: defaultScope,
        saveAsDraft: asDraft,
        source: "citizen-app",
      };
      await createSocialPost(input);
      reset();
      onCreated?.();
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Couldn't post right now.";
      setHint(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const runAssist = async (op: typeof ASSIST_OPS[number]["op"], language?: string) => {
    if (!body.trim()) {
      setHint("Type something first, then ask Nompilo.");
      return;
    }
    setAssistBusy(true);
    setHint("Nompilo is thinking…");
    try {
      const res = await nompiloComposerAssist(op, body, { language });
      if (op === "suggest_tags") {
        const list = Array.isArray(res.result) ? (res.result as string[]) : [];
        setTopics((prev) => Array.from(new Set([...prev, ...list.map((s) => s.replace(/^#/, ""))])).slice(0, 8));
        setHint(`Added ${list.length} suggested tag${list.length === 1 ? "" : "s"}.`);
      } else if (op === "safety_check") {
        const flagged = (res.result as { flagged?: boolean })?.flagged;
        setHint(flagged ? "Nompilo flagged this content. Review before posting." : "Nompilo found no issues.");
      } else {
        const text = extractText(res.result);
        if (text) {
          setBody(text);
          setHint(`Updated${res.fallbackUsed ? " (offline mode)" : ""}.`);
        } else {
          setHint("Nompilo couldn't update the draft.");
        }
      }
    } catch {
      setHint("Nompilo assist unavailable.");
    } finally {
      setAssistBusy(false);
    }
  };

  if (!expanded) {
    return (
      <Card>
        <CardBody>
          <TouchableOpacity testID="composer-open" onPress={() => setExpanded(true)} activeOpacity={0.7}>
            <Text style={styles.composerPrompt}>What would you like to share?</Text>
          </TouchableOpacity>
        </CardBody>
      </Card>
    );
  }

  return (
    <Card>
      <CardBody>
        <View style={{ gap: 12 }}>
          {/* Kind chips */}
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipRow}>
            {POST_KINDS.map((k) => (
              <Button
                key={k.value}
                title={k.label}
                size="sm"
                variant={kind === k.value ? "primary" : "ghost"}
                onPress={() => setKind(k.value)}
                testID={`composer-kind-${k.value}`}
              />
            ))}
          </ScrollView>

          <TextField
            label="Your message"
            value={body}
            onChangeText={setBody}
            placeholder={placeholderFor(kind)}
            multiline
            numberOfLines={4}
            testID="composer-body"
          />

          {/* Topics */}
          <View>
            <Text style={styles.label}>Topics</Text>
            <View style={styles.topicRow}>
              {topics.map((t) => (
                <View key={t} style={styles.topic}>
                  <Text style={styles.topicText}>#{t}</Text>
                  <TouchableOpacity onPress={() => setTopics((prev) => prev.filter((x) => x !== t))}>
                    <Text style={styles.topicRemove}>×</Text>
                  </TouchableOpacity>
                </View>
              ))}
              <View style={styles.topicInputWrap}>
                <TextField
                  value={topicDraft}
                  onChangeText={(v) => {
                    if (v.endsWith(",") || v.endsWith(" ")) {
                      setTopicDraft(v.replace(/[,\s]+$/, ""));
                      addTopic();
                    } else {
                      setTopicDraft(v);
                    }
                  }}
                  placeholder="Add a topic"
                  testID="composer-topic-input"
                />
              </View>
            </View>
          </View>

          <Select
            label="Visibility"
            value={visibility}
            onChange={setVisibility}
            options={VISIBILITY_OPTIONS}
          />

          {/* Nompilo */}
          <View style={styles.nompiloPanel}>
            <Text style={styles.nompiloLabel}>Ask Nompilo</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipRow}>
              {ASSIST_OPS.map((a, idx) => (
                <Button
                  key={`${a.op}-${idx}`}
                  title={a.label}
                  size="sm"
                  variant="ghost"
                  onPress={() => runAssist(a.op, a.language)}
                  disabled={assistBusy}
                  testID={`composer-assist-${a.op}-${idx}`}
                />
              ))}
            </ScrollView>
            {hint ? <Text style={styles.hint}>{hint}</Text> : null}
          </View>

          <View style={styles.actionRow}>
            <Button title="Cancel" variant="ghost" size="sm" onPress={reset} disabled={submitting} testID="composer-cancel" />
            <Button title="Save draft" variant="secondary" size="sm" onPress={() => submit(true)} disabled={submitting || !body.trim()} testID="composer-draft" />
            <Button title={submitting ? "Posting…" : "Post"} variant="primary" size="sm" onPress={() => submit(false)} disabled={submitting || !body.trim()} testID="composer-submit" />
          </View>
        </View>
      </CardBody>
    </Card>
  );
}

function placeholderFor(kind: string): string {
  switch (kind) {
    case "question": return "Ask your community a question…";
    case "event": return "Describe the event: when, where, who is invited…";
    case "learning": return "Share a resource, link, or summary…";
    case "health_promotion": return "Write a clear, friendly health message…";
    default: return "Share an update, story, or insight…";
  }
}

function extractText(result: unknown): string | null {
  if (typeof result === "string") return result;
  if (result && typeof result === "object") {
    const r = result as Record<string, unknown>;
    if (typeof r.text === "string") return r.text;
    if (typeof r.content === "string") return r.content;
    if (typeof r.result === "string") return r.result;
  }
  return null;
}

const styles = StyleSheet.create({
  composerPrompt: {
    fontSize: 15,
    color: "#6B7280",
    paddingVertical: 6,
  },
  label: {
    fontSize: 12,
    fontWeight: "600",
    color: "#374151",
    marginBottom: 4,
  },
  chipRow: { gap: 8, paddingVertical: 2 },
  topicRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    alignItems: "center",
    gap: 6,
  },
  topic: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 8,
    paddingVertical: 3,
    backgroundColor: "#EEF2FF",
    borderRadius: 999,
    gap: 4,
  },
  topicText: { fontSize: 12, color: "#3730A3" },
  topicRemove: { fontSize: 14, color: "#3730A3", marginLeft: 2 },
  topicInputWrap: { minWidth: 120, flexGrow: 1 },
  nompiloPanel: {
    backgroundColor: "#F8FAFC",
    padding: 10,
    borderRadius: 12,
    gap: 6,
  },
  nompiloLabel: { fontSize: 12, fontWeight: "600", color: "#374151" },
  hint: { fontSize: 11, color: "#6B7280" },
  actionRow: {
    flexDirection: "row",
    justifyContent: "flex-end",
    gap: 8,
  },
});
