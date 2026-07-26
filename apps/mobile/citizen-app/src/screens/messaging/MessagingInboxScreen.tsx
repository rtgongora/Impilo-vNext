/**
 * MessagingInboxScreen — Conversation inbox for citizen-provider and citizen-support messaging.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Screen, Header, Card, CardBody, Button, Badge, TextField, Select, Avatar, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import { useCommunicationDashboard, type Conversation } from "@impilo/mobile-messaging";
import {
  fetchConversations,
  createConversation,
} from "../../services/messagingService";
import { appStore } from "../../stores/appStore";
import { ThreadViewScreen } from "./ThreadViewScreen";

export function MessagingInboxScreen() {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [filter, setFilter] = useState<string>("");
  const [selectedConversation, setSelectedConversation] = useState<Conversation | null>(null);
  const [showNewMessage, setShowNewMessage] = useState(false);
  const [newRecipientId, setNewRecipientId] = useState("");
  const [newSubject, setNewSubject] = useState("");
  const [newMessage, setNewMessage] = useState("");
  const [newType, setNewType] = useState<"DIRECT" | "SUPPORT">("DIRECT");
  const [submitting, setSubmitting] = useState(false);
  const { dashboard } = useCommunicationDashboard();

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchConversations({
        type: filter || undefined,
        size: 50,
      });
      setConversations(result.items);
      const totalUnread = result.items.reduce((sum, c) => sum + c.unreadCount, 0);
      appStore.getState().setUnreadMessages(totalUnread);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, [filter]);

  useEffect(() => {
    load();
  }, [load]);

  const handleCreateConversation = useCallback(async () => {
    setSubmitting(true);
    try {
      const conv = await createConversation({
        recipientId: newRecipientId,
        subject: newSubject || undefined,
        type: newType,
        initialMessage: newMessage,
      });
      setShowNewMessage(false);
      setNewRecipientId("");
      setNewSubject("");
      setNewMessage("");
      setSelectedConversation(conv);
      load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setSubmitting(false);
    }
  }, [newRecipientId, newSubject, newMessage, newType, load]);

  if (selectedConversation) {
    return (
      <ThreadViewScreen
        conversation={selectedConversation}
        onBack={() => {
          setSelectedConversation(null);
          load();
        }}
      />
    );
  }

  return (
    <Screen>
      <Header title="Messages" />
      <ScrollView testID="messaging-inbox-screen" style={styles.scrollView} contentContainerStyle={styles.container}>
        {dashboard ? (
          <View style={styles.kpiRow}>
            <View style={styles.kpiCard}>
              <Text style={styles.kpiValue}>{String(dashboard.active_threads ?? 0)}</Text>
              <Text style={styles.kpiLabel}>Active threads</Text>
            </View>
            <View style={styles.kpiCard}>
              <Text style={styles.kpiValue}>{String(dashboard.open_clinical_pages ?? 0)}</Text>
              <Text style={styles.kpiLabel}>Open pages</Text>
            </View>
            <View style={styles.kpiCard}>
              <Text style={styles.kpiValue}>{String(dashboard.sent_today ?? 0)}</Text>
              <Text style={styles.kpiLabel}>Sent today</Text>
            </View>
          </View>
        ) : null}
        <View style={styles.actionsBar}>
          <View style={styles.filterRow}>
            {[
              { label: "All", value: "" },
              { label: "Provider", value: "DIRECT" },
              { label: "Support", value: "SUPPORT" },
            ].map((f) => (
              <TouchableOpacity
                key={f.value}
                onPress={() => setFilter(f.value)}
                style={[styles.filterPill, filter === f.value && styles.filterPillActive]}
                testID={`filter-${f.value || "all"}`}
              >
                <Text style={[styles.filterPillText, filter === f.value && styles.filterPillTextActive]}>
                  {f.label}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
          <TouchableOpacity
            onPress={() => setShowNewMessage(!showNewMessage)}
            style={[styles.newMessageBtn, showNewMessage && styles.newMessageBtnCancel]}
            testID="toggle-new-message"
          >
            {!showNewMessage && <Ionicons name="create-outline" size={16} color="#FFFFFF" style={styles.btnIcon} />}
            <Text style={[styles.newMessageBtnText, showNewMessage && styles.newMessageBtnTextCancel]}>
              {showNewMessage ? "Cancel" : "New"}
            </Text>
          </TouchableOpacity>
        </View>

        {showNewMessage ? (
          <View style={styles.formCard}>
            <Text style={styles.formTitle}>New Message</Text>
            <View style={styles.formContainer}>
              <Select
                label="Type"
                value={newType}
                onChange={(v: string) => setNewType(v as "DIRECT" | "SUPPORT")}
                options={[
                  { label: "Message Provider", value: "DIRECT" },
                  { label: "Contact Support", value: "SUPPORT" },
                ]}
                testID="new-msg-type"
              />
              <TextField
                label={newType === "DIRECT" ? "Provider ID" : "Support Team ID"}
                value={newRecipientId}
                onChange={setNewRecipientId}
                placeholder="Enter recipient ID"
                testID="new-msg-recipient"
              />
              <TextField
                label="Subject (optional)"
                value={newSubject}
                onChange={setNewSubject}
                placeholder="Message subject"
                testID="new-msg-subject"
              />
              <TextField
                label="Message"
                value={newMessage}
                onChange={setNewMessage}
                placeholder="Type your message..."
                testID="new-msg-body"
              />
              <TouchableOpacity
                onPress={handleCreateConversation}
                disabled={submitting || !newRecipientId || !newMessage}
                style={[styles.sendBtn, (submitting || !newRecipientId || !newMessage) && styles.sendBtnDisabled]}
                testID="send-new-message"
              >
                <Ionicons name="send" size={16} color="#FFFFFF" style={styles.btnIcon} />
                <Text style={styles.sendBtnText}>{submitting ? "Sending..." : "Send"}</Text>
              </TouchableOpacity>
            </View>
          </View>
        ) : null}

        {error ? (
          <ErrorState title="Error" message={error.message} onRetry={load} />
        ) : null}

        {isLoading ? (
          <View style={styles.centered}>
            <LoadingSpinner size="md" />
          </View>
        ) : conversations.length === 0 ? (
          <View style={styles.emptyContainer}>
            <Ionicons name="chatbubbles-outline" size={48} color={colors.gray[300]} />
            <Text style={styles.emptyTitle}>No conversations</Text>
            <Text style={styles.emptyMessage}>Start a conversation with your provider or support team</Text>
          </View>
        ) : (
          conversations.map((conv) => (
            <TouchableOpacity
              key={conv.id}
              testID={`conversation-${conv.id}`}
              activeOpacity={0.7}
              onPress={() => setSelectedConversation(conv)}
            >
              <View style={[styles.convCard, conv.unreadCount > 0 && styles.convCardUnread]}>
                <Avatar
                  name={conv.participants?.[0]?.displayName ?? "Unknown"}
                  size="md"
                />
                <View style={styles.conversationInfo}>
                  <View style={styles.conversationHeader}>
                    <Text style={styles.conversationTitle} numberOfLines={1}>
                      {conv.subject ?? conv.participants?.map((p) => p.displayName).join(", ") ?? "Conversation"}
                    </Text>
                    {conv.unreadCount > 0 ? (
                      <Badge variant="destructive">{String(conv.unreadCount)}</Badge>
                    ) : null}
                  </View>
                  {conv.lastMessage ? (
                    <Text style={styles.lastMessageText} numberOfLines={1}>
                      {conv.lastMessage.body}
                    </Text>
                  ) : null}
                  <View style={styles.conversationMeta}>
                    <View style={[styles.typePill, conv.type === "DIRECT" ? styles.typePillDirect : styles.typePillSupport]}>
                      <Text style={[styles.typePillText, conv.type === "DIRECT" ? styles.typePillTextDirect : styles.typePillTextSupport]}>
                        {conv.type === "DIRECT" ? "Provider" : "Support"}
                      </Text>
                    </View>
                    <Text style={styles.dateText}>
                      {new Date(conv.updatedAt).toLocaleDateString()}
                    </Text>
                  </View>
                </View>
              </View>
            </TouchableOpacity>
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
    backgroundColor: "#F8FAFC",
  },
  container: {
    padding: 16,
    gap: 10,
  },
  actionsBar: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 4,
  },
  kpiRow: {
    flexDirection: "row",
    gap: 8,
  },
  kpiCard: {
    flex: 1,
    backgroundColor: "#FFFFFF",
    borderRadius: 12,
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderWidth: 1,
    borderColor: colors.gray[200],
  },
  kpiValue: {
    fontSize: 17,
    fontWeight: "700",
    color: colors.gray[900],
  },
  kpiLabel: {
    fontSize: 11,
    color: colors.gray[500],
    marginTop: 2,
  },
  filterRow: {
    flexDirection: "row",
    gap: 6,
  },
  filterPill: {
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 20,
    backgroundColor: colors.gray[100],
  },
  filterPillActive: {
    backgroundColor: colors.ui.success.light,
  },
  filterPillText: {
    fontSize: 13,
    fontWeight: "500",
    color: colors.gray[500],
  },
  filterPillTextActive: {
    color: "#059669",
    fontWeight: "600",
  },
  newMessageBtn: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#059669",
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    gap: 4,
  },
  newMessageBtnCancel: {
    backgroundColor: colors.gray[100],
  },
  newMessageBtnText: {
    fontSize: 13,
    fontWeight: "600",
    color: "#FFFFFF",
  },
  newMessageBtnTextCancel: {
    color: colors.gray[500],
  },
  btnIcon: {
    marginRight: 2,
  },
  formCard: {
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    padding: 16,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 8,
    elevation: 3,
  },
  formTitle: {
    fontSize: 16,
    fontWeight: "700",
    color: colors.gray[900],
    marginBottom: 14,
  },
  formContainer: {
    gap: 12,
  },
  sendBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#059669",
    paddingVertical: 12,
    borderRadius: 12,
    gap: 6,
  },
  sendBtnDisabled: {
    opacity: 0.5,
  },
  sendBtnText: {
    fontSize: 15,
    fontWeight: "600",
    color: "#FFFFFF",
  },
  centered: {
    alignItems: "center",
    paddingVertical: 40,
  },
  emptyContainer: {
    alignItems: "center",
    paddingVertical: 56,
    gap: 10,
  },
  emptyTitle: {
    fontSize: 16,
    fontWeight: "600",
    color: colors.gray[700],
  },
  emptyMessage: {
    fontSize: 14,
    color: colors.gray[400],
    textAlign: "center",
    paddingHorizontal: 24,
  },
  convCard: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    backgroundColor: "#FFFFFF",
    borderRadius: 14,
    padding: 14,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  convCardUnread: {
    borderLeftWidth: 3,
    borderLeftColor: "#059669",
  },
  conversationInfo: {
    flex: 1,
  },
  conversationHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 3,
  },
  conversationTitle: {
    fontSize: 14,
    fontWeight: "700",
    color: colors.gray[900],
    flex: 1,
    marginRight: 8,
  },
  lastMessageText: {
    fontSize: 13,
    color: colors.gray[500],
    marginBottom: 6,
  },
  conversationMeta: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
  },
  typePill: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 10,
  },
  typePillDirect: {
    backgroundColor: "#EFF6FF",
  },
  typePillSupport: {
    backgroundColor: "#F5F3FF",
  },
  typePillText: {
    fontSize: 11,
    fontWeight: "600",
  },
  typePillTextDirect: {
    color: "#1E40AF",
  },
  typePillTextSupport: {
    color: "#7C3AED",
  },
  dateText: {
    fontSize: 12,
    color: colors.gray[400],
    marginLeft: "auto",
  },
});
