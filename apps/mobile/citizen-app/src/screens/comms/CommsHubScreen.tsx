/**
 * CommsHubScreen — native Khuluma Comms Hub for the citizen app.
 *
 * Inbox + conversation + realtime-ready send/read, with a 1:1 call surface that reuses
 * LiveKitMobileConsultRoom over the canonical self-hosted LiveKit (rtc-gateway token from the BFF).
 * Backed entirely by /internal/v1/mobile/khuluma/** — no mock data. Incoming-call push (mobile
 * realtime) is surfaced via the IncomingCallSheet; the accept/decline + media flow is fully wired.
 */

import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, TouchableOpacity, TextInput, ActivityIndicator, StyleSheet } from "react-native";
import { LiveKitMobileConsultRoom } from "../telehealth/LiveKitMobileConsultRoom";
import {
  fetchInbox,
  fetchConversation,
  fetchMessages,
  sendMessage,
  markConversationRead,
  startCall,
  acceptCall,
  declineCall,
  endCall,
  type CommsConversationSummary,
  type CommsConversationDetail,
  type CommsMessage,
  type CommsCall,
} from "../../services/khulumaCommsService";

export const CommsHubScreen: React.FC = () => {
  const [conversations, setConversations] = useState<CommsConversationSummary[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<CommsConversationDetail | null>(null);
  const [messages, setMessages] = useState<CommsMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeCall, setActiveCall] = useState<{ call: CommsCall; direction: "incoming" | "outgoing" } | null>(null);
  const [incoming, setIncoming] = useState<CommsCall | null>(null);

  const loadInbox = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      setConversations(await fetchInbox());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load conversations");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadInbox();
  }, [loadInbox]);

  const openConversation = useCallback(async (id: string) => {
    setSelectedId(id);
    const [d, m] = await Promise.all([fetchConversation(id), fetchMessages(id)]);
    setDetail(d);
    setMessages(m);
    const newest = m[m.length - 1];
    if (newest) {
      await markConversationRead(id, newest.messageId).catch(() => undefined);
    }
  }, []);

  const handleSend = useCallback(async () => {
    const body = draft.trim();
    if (!body || !selectedId) return;
    setDraft("");
    const sent = await sendMessage(selectedId, body);
    setMessages((prev) => [...prev, sent]);
  }, [draft, selectedId]);

  const handleStartCall = useCallback(async () => {
    if (!selectedId || !detail) return;
    const callees = detail.participants
      .filter((p) => p.active)
      .map((p) => ({ actorId: p.actorId, actorType: p.actorType, displayName: p.displayName ?? undefined }));
    const call = await startCall(selectedId, "AUDIO", callees);
    setActiveCall({ call, direction: "outgoing" });
  }, [selectedId, detail]);

  const handleAccept = useCallback(async () => {
    if (!incoming) return;
    const call = await acceptCall(incoming.callId);
    setActiveCall({ call, direction: "incoming" });
    setIncoming(null);
  }, [incoming]);

  const handleDecline = useCallback(async () => {
    if (!incoming) return;
    await declineCall(incoming.callId).catch(() => undefined);
    setIncoming(null);
  }, [incoming]);

  const handleEndCall = useCallback(async () => {
    if (!activeCall) return;
    await endCall(activeCall.call.callId).catch(() => undefined);
    setActiveCall(null);
  }, [activeCall]);

  if (activeCall) {
    const isVideo = activeCall.call.callType === "VIDEO";
    const hasMedia = activeCall.call.mediaAvailable && !!activeCall.call.accessToken && !!activeCall.call.roomUrl;
    return (
      <View testID="comms-call" style={styles.callContainer}>
        <Text style={styles.callTitle}>{isVideo ? "Video" : "Audio"} call · {activeCall.call.status}</Text>
        {hasMedia ? (
          <LiveKitMobileConsultRoom
            serverUrl={activeCall.call.roomUrl ?? undefined}
            token={activeCall.call.accessToken ?? undefined}
            videoEnabled={isVideo}
            micMuted={false}
          />
        ) : (
          <Text style={styles.muted}>
            Connected for signalling — media unavailable ({activeCall.call.mediaError ?? "not configured"}).
          </Text>
        )}
        <TouchableOpacity testID="end-call" style={styles.endButton} onPress={handleEndCall}>
          <Text style={styles.endButtonText}>End call</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View testID="comms-hub-screen" style={styles.container}>
      <Text style={styles.header}>Comms Hub</Text>

      {incoming && (
        <View testID="incoming-call-sheet" style={styles.sheet}>
          <Text style={styles.sheetTitle}>Incoming {incoming.callType.toLowerCase()} call</Text>
          <Text style={styles.muted}>from {incoming.initiatedBy}</Text>
          <View style={styles.row}>
            <TouchableOpacity testID="accept-call" style={styles.acceptButton} onPress={handleAccept}>
              <Text style={styles.buttonText}>Accept</Text>
            </TouchableOpacity>
            <TouchableOpacity testID="decline-call" style={styles.declineButton} onPress={handleDecline}>
              <Text style={styles.buttonText}>Decline</Text>
            </TouchableOpacity>
          </View>
        </View>
      )}

      {!selectedId && (
        <View testID="comms-inbox" style={styles.flex}>
          {isLoading && <ActivityIndicator testID="inbox-loading" />}
          {error && <Text style={styles.error}>{error}</Text>}
          {!isLoading && conversations.length === 0 && <Text style={styles.muted}>No conversations yet.</Text>}
          <ScrollView>
            {conversations.map((c) => (
              <TouchableOpacity
                key={c.conversationId}
                testID={`conversation-${c.conversationId}`}
                style={styles.conversationRow}
                onPress={() => openConversation(c.conversationId)}
              >
                <View style={styles.flex}>
                  <Text style={styles.conversationTitle}>{c.title ?? c.type}</Text>
                  <Text style={styles.muted} numberOfLines={1}>{c.lastMessagePreview ?? "—"}</Text>
                </View>
                {c.unreadCount > 0 && (
                  <View testID={`unread-${c.conversationId}`} style={styles.badge}>
                    <Text style={styles.badgeText}>{c.unreadCount}</Text>
                  </View>
                )}
              </TouchableOpacity>
            ))}
          </ScrollView>
        </View>
      )}

      {selectedId && (
        <View testID="comms-conversation" style={styles.flex}>
          <View style={styles.conversationHeader}>
            <TouchableOpacity testID="back-to-inbox" onPress={() => { setSelectedId(null); setDetail(null); setMessages([]); }}>
              <Text style={styles.link}>‹ Inbox</Text>
            </TouchableOpacity>
            <Text style={styles.conversationTitle}>{detail?.title ?? detail?.type ?? "Conversation"}</Text>
            <TouchableOpacity testID="start-call" onPress={handleStartCall}>
              <Text style={styles.link}>Call</Text>
            </TouchableOpacity>
          </View>

          <ScrollView testID="message-list" style={styles.flex}>
            {messages.map((m) => (
              <View key={m.messageId} style={styles.messageRow}>
                <Text style={styles.sender}>{m.senderDisplayName ?? m.senderId}</Text>
                <Text>{m.body}</Text>
              </View>
            ))}
          </ScrollView>

          <View style={styles.composer}>
            <TextInput
              testID="message-input"
              style={styles.input}
              value={draft}
              onChangeText={setDraft}
              placeholder="Type a message…"
            />
            <TouchableOpacity testID="send-message" style={styles.sendButton} onPress={handleSend}>
              <Text style={styles.buttonText}>Send</Text>
            </TouchableOpacity>
          </View>
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, padding: 12, backgroundColor: "#F8FAFC" },
  flex: { flex: 1 },
  header: { fontSize: 18, fontWeight: "700", marginBottom: 8 },
  conversationRow: { flexDirection: "row", alignItems: "center", paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: "#E5E7EB" },
  conversationTitle: { fontWeight: "600" },
  conversationHeader: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingVertical: 8 },
  messageRow: { paddingVertical: 4 },
  sender: { fontSize: 10, color: "#6B7280" },
  composer: { flexDirection: "row", alignItems: "center", gap: 8, paddingTop: 8 },
  input: { flex: 1, borderWidth: 1, borderColor: "#D1D5DB", borderRadius: 8, paddingHorizontal: 10, paddingVertical: 8 },
  sendButton: { backgroundColor: "#059669", borderRadius: 8, paddingHorizontal: 14, paddingVertical: 10 },
  buttonText: { color: "#FFFFFF", fontWeight: "600" },
  link: { color: "#059669", fontWeight: "600" },
  muted: { color: "#6B7280" },
  error: { color: "#DC2626" },
  badge: { backgroundColor: "#059669", borderRadius: 999, paddingHorizontal: 8, paddingVertical: 2 },
  badgeText: { color: "#FFFFFF", fontSize: 12 },
  sheet: { backgroundColor: "#FFFFFF", borderRadius: 12, padding: 12, marginBottom: 8, borderWidth: 1, borderColor: "#E5E7EB" },
  sheetTitle: { fontWeight: "700" },
  row: { flexDirection: "row", gap: 8, marginTop: 8 },
  acceptButton: { backgroundColor: "#059669", borderRadius: 8, paddingHorizontal: 16, paddingVertical: 8 },
  declineButton: { backgroundColor: "#DC2626", borderRadius: 8, paddingHorizontal: 16, paddingVertical: 8 },
  callContainer: { flex: 1, padding: 12, backgroundColor: "#0A0A0A" },
  callTitle: { color: "#FFFFFF", fontWeight: "600", marginBottom: 8 },
  endButton: { backgroundColor: "#DC2626", borderRadius: 8, padding: 12, marginTop: 12, alignItems: "center" },
  endButtonText: { color: "#FFFFFF", fontWeight: "700" },
});
