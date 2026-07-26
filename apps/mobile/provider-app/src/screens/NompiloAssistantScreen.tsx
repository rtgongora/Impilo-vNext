/**
 * NompiloAssistantScreen (provider) — modal chat for the provider assistant.
 *
 * Provider tone is calm and clinical, not chatty. Nompilo here helps with
 * navigation, queue/task explanations, delivery tracking, learning shortcuts,
 * and integration-status interpretation. It never generates clinical decisions.
 */

import React, { useCallback, useMemo, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TextInput,
  Pressable,
  KeyboardAvoidingView,
  Platform,
} from "react-native";
import { Badge, Button, colors } from "@impilo/mobile-design-system";
import { useNompilo, NOMPILO_DISCLAIMER } from "@impilo/mobile-nompilo";
import { useAppStore } from "../stores/appStore";

const ACCENT = "#009739";

export interface NompiloAssistantScreenProps {
  onClose?: () => void;
  surface?: string;
}

export function NompiloAssistantScreen({ onClose, surface }: NompiloAssistantScreenProps) {
  const { facilityName, workspaceName, mode } = useAppStore();
  const [draft, setDraft] = useState("");

  const context = useMemo(
    () => ({
      role: providerRoleFor(mode),
      surface: surface ?? `provider-${mode}`,
      hints: {
        facility: facilityName,
        workspace: workspaceName,
        activeMode: mode,
      },
    }),
    [facilityName, workspaceName, mode, surface]
  );

  const { history, loading, lastResult, send } = useNompilo({
    context,
    seed: [
      {
        role: "assistant",
        content: "Hi, I'm Nompilo. How can I help with your work today?",
      },
    ],
  });

  const submit = useCallback(async () => {
    if (!draft.trim()) return;
    const next = draft;
    setDraft("");
    await send(next);
  }, [draft, send]);

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === "ios" ? "padding" : undefined}
      style={styles.container}
    >
      <View style={styles.header}>
        <Text style={styles.title}>Nompilo</Text>
        <Text style={styles.subtitle}>
          {facilityName ? `${facilityName} · ` : ""}
          {workspaceName ?? "Provider helper"}
        </Text>
        {lastResult?.usedFallback ? (
          <View style={styles.bannerWarn}>
            <Text style={styles.bannerWarnText}>
              Assistant model is unavailable. I'll reply with simple guidance until it's back.
            </Text>
          </View>
        ) : null}
      </View>

      <ScrollView
        style={styles.chat}
        contentContainerStyle={styles.chatContent}
        keyboardShouldPersistTaps="handled"
      >
        {history.map((msg, idx) => (
          <View
            key={`${msg.role}-${idx}`}
            style={[
              styles.bubble,
              msg.role === "user" ? styles.userBubble : styles.assistantBubble,
            ]}
          >
            <Text
              style={[
                styles.bubbleText,
                msg.role === "user" ? styles.userText : styles.assistantText,
              ]}
            >
              {msg.content}
            </Text>
          </View>
        ))}
        {loading ? (
          <View style={[styles.bubble, styles.assistantBubble]}>
            <Text style={styles.assistantText}>Thinking…</Text>
          </View>
        ) : null}
      </ScrollView>

      {lastResult?.suggestions && lastResult.suggestions.length > 0 ? (
        <View style={styles.suggestionRow}>
          {lastResult.suggestions.slice(0, 4).map((s) => (
            <Pressable
              key={s}
              onPress={() => send(s)}
              style={styles.suggestion}
              accessibilityRole="button"
              accessibilityLabel={`Ask: ${s}`}
            >
              <Text style={styles.suggestionText}>{s}</Text>
            </Pressable>
          ))}
        </View>
      ) : null}

      <View style={styles.inputRow}>
        <TextInput
          value={draft}
          onChangeText={setDraft}
          placeholder="Ask Nompilo…"
          style={styles.input}
          editable={!loading}
          accessibilityLabel="Message Nompilo"
          returnKeyType="send"
          onSubmitEditing={submit}
        />
        <Button
          title={loading ? "…" : "Send"}
          onPress={submit}
          disabled={loading || !draft.trim()}
        />
      </View>

      <View style={styles.disclaimerRow}>
        <Badge variant="outline">Decision support only</Badge>
        <Text style={styles.disclaimer}>{NOMPILO_DISCLAIMER}</Text>
      </View>

      {onClose ? (
        <Pressable onPress={onClose} style={styles.closeLink} accessibilityRole="button">
          <Text style={styles.closeLinkText}>Close</Text>
        </Pressable>
      ) : null}
    </KeyboardAvoidingView>
  );
}

function providerRoleFor(mode: string | undefined): "PROVIDER" | "COURIER" | "SUPERVISOR" | "OUTREACH" {
  switch (mode) {
    case "courier":
      return "COURIER";
    case "supervisor":
      return "SUPERVISOR";
    case "outreach":
      return "OUTREACH";
    default:
      return "PROVIDER";
  }
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: { paddingBottom: 12, gap: 4 },
  title: { fontSize: 22, fontWeight: "800", color: "#0F172A" },
  subtitle: { fontSize: 12, color: "#64748B" },
  bannerWarn: {
    marginTop: 8,
    backgroundColor: colors.ui.warning.light,
    borderColor: "#FCD34D",
    borderWidth: 1,
    borderRadius: 10,
    padding: 8,
  },
  bannerWarnText: { fontSize: 12, color: colors.ui.warning.text },
  chat: { flex: 1 },
  chatContent: { paddingVertical: 12, gap: 8 },
  bubble: {
    maxWidth: "85%",
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 14,
  },
  userBubble: {
    alignSelf: "flex-end",
    backgroundColor: ACCENT,
  },
  assistantBubble: {
    alignSelf: "flex-start",
    backgroundColor: "#F1F5F9",
  },
  bubbleText: { fontSize: 14, lineHeight: 20 },
  userText: { color: "#FFFFFF" },
  assistantText: { color: "#0F172A" },
  suggestionRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 6,
    marginVertical: 8,
  },
  suggestion: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 14,
    backgroundColor: "#ECFDF5",
    borderColor: "#A7F3D0",
    borderWidth: 1,
  },
  suggestionText: { fontSize: 12, color: colors.ui.success.text, fontWeight: "600" },
  inputRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingVertical: 8,
  },
  input: {
    flex: 1,
    borderWidth: 1,
    borderColor: "#CBD5E1",
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 14,
    backgroundColor: "#FFFFFF",
  },
  disclaimerRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingTop: 8,
  },
  disclaimer: { fontSize: 10, color: "#94A3B8", flexShrink: 1 },
  closeLink: { alignItems: "center", paddingVertical: 10 },
  closeLinkText: { color: "#64748B", fontSize: 13 },
});
