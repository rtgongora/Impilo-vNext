import React, { useEffect, useState } from "react";
import { View, Text, StyleSheet } from "react-native";
import { Button, TextField, colors } from "@impilo/mobile-design-system";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  eventConversation,
  meetingFromEvent,
  fetchMessages,
  sendMessage,
} from "../../services/khulumaCommsService";

/**
 * The Khuluma discussion for an Impilo Live event, rendered inline on the mobile event screen — so
 * Impilo Live and Khuluma are one product family, not parallel screens. Resolves the conversation
 * anchored to the event; if none exists, an opt-in "Start discussion" attaches one idempotently.
 */
export function EventDiscussionSection({ eventId, title }: { eventId: string; title?: string }) {
  const queryClient = useQueryClient();
  const [conversationId, setConversationId] = useState<string | null | undefined>(undefined);
  const [draft, setDraft] = useState("");
  const [starting, setStarting] = useState(false);

  useEffect(() => {
    let active = true;
    void eventConversation(eventId).then((m) => {
      if (active) setConversationId(m?.conversationId ?? null);
    });
    return () => {
      active = false;
    };
  }, [eventId]);

  const messagesQuery = useQuery({
    queryKey: ["live-event-discussion", conversationId],
    queryFn: () => fetchMessages(conversationId as string),
    enabled: Boolean(conversationId),
    refetchInterval: 4000,
  });

  const start = async () => {
    setStarting(true);
    try {
      const m = await meetingFromEvent(eventId, []);
      setConversationId(m.conversationId);
    } finally {
      setStarting(false);
    }
  };

  const handleSend = async () => {
    const body = draft.trim();
    if (!body || !conversationId) return;
    setDraft("");
    await sendMessage(conversationId, body);
    queryClient.invalidateQueries({ queryKey: ["live-event-discussion", conversationId] });
  };

  return (
    <View style={styles.section} testID="event-discussion">
      <Text style={styles.title}>Discussion · Khuluma</Text>
      {conversationId === undefined && <Text style={styles.muted}>Loading…</Text>}
      {conversationId === null && (
        <Button
          title={starting ? "Starting…" : "Start discussion"}
          variant="ghost"
          onPress={start}
          disabled={starting}
          testID="start-discussion"
        />
      )}
      {conversationId ? (
        <>
          <View testID="event-discussion-messages" style={styles.messages}>
            {(messagesQuery.data ?? []).map((m) => (
              <View key={m.messageId} style={styles.msg}>
                <Text style={styles.sender}>{m.senderDisplayName ?? m.senderId}</Text>
                <Text>{m.body}</Text>
              </View>
            ))}
            {messagesQuery.data?.length === 0 ? <Text style={styles.muted}>No messages yet.</Text> : null}
          </View>
          <TextField label="Message" value={draft} onChange={setDraft} placeholder="Message…" testID="discussion-input" />
          <Button title="Send" onPress={handleSend} testID="discussion-send" />
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  section: { backgroundColor: "#FFFFFF", borderRadius: 12, padding: 14, borderWidth: 1, borderColor: colors.gray[200], gap: 10 },
  title: { fontSize: 14, fontWeight: "600", color: colors.gray[900] },
  messages: { gap: 6 },
  msg: { paddingVertical: 2 },
  sender: { fontSize: 10, color: colors.gray[500] },
  muted: { fontSize: 13, color: colors.gray[500] },
});
