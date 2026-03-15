/**
 * MessagingInboxScreen — Conversation inbox for citizen-provider and citizen-support messaging.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  Badge,
  TextField,
  Select,
  Avatar,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import type { Conversation } from "@impilo/mobile-messaging";
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

  // Thread view
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
        {/* Actions bar */}
        <View style={styles.actionsBar}>
          <View style={styles.filterRow}>
            <Button
              title="All"
              variant={filter === "" ? "primary" : "ghost"}
              size="sm"
              onPress={() => setFilter("")}
              testID="filter-all"
            />
            <Button
              title="Provider"
              variant={filter === "DIRECT" ? "primary" : "ghost"}
              size="sm"
              onPress={() => setFilter("DIRECT")}
              testID="filter-direct"
            />
            <Button
              title="Support"
              variant={filter === "SUPPORT" ? "primary" : "ghost"}
              size="sm"
              onPress={() => setFilter("SUPPORT")}
              testID="filter-support"
            />
          </View>
          <Button
            title={showNewMessage ? "Cancel" : "New Message"}
            variant={showNewMessage ? "ghost" : "primary"}
            size="sm"
            onPress={() => setShowNewMessage(!showNewMessage)}
            testID="toggle-new-message"
          />
        </View>

        {/* New message form */}
        {showNewMessage ? (
          <Card>
            <CardBody>
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
                <Button
                  title={submitting ? "Sending..." : "Send"}
                  variant="primary"
                  onPress={handleCreateConversation}
                  disabled={submitting || !newRecipientId || !newMessage}
                  testID="send-new-message"
                />
              </View>
            </CardBody>
          </Card>
        ) : null}

        {error ? (
          <ErrorState title="Error" message={error.message} onRetry={load} />
        ) : null}

        {/* Conversation list */}
        {isLoading ? (
          <LoadingSpinner size="md" />
        ) : conversations.length === 0 ? (
          <EmptyState
            title="No conversations"
            message="Start a conversation with your provider or support team"
          />
        ) : (
          conversations.map((conv) => (
            <Card key={conv.id}>
              <CardBody>
                <TouchableOpacity
                  testID={`conversation-${conv.id}`}
                  style={[
                    styles.conversationRow,
                    { opacity: conv.unreadCount > 0 ? 1 : 0.8 },
                  ]}
                  onPress={() => setSelectedConversation(conv)}
                  activeOpacity={0.7}
                >
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
                      <Badge variant="outline">{conv.type}</Badge>
                      <Text style={styles.dateText}>
                        {new Date(conv.updatedAt).toLocaleDateString()}
                      </Text>
                    </View>
                  </View>
                </TouchableOpacity>
              </CardBody>
            </Card>
          ))
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
  actionsBar: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  filterRow: {
    flexDirection: "row",
    gap: 8,
  },
  formContainer: {
    gap: 12,
  },
  conversationRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  conversationInfo: {
    flex: 1,
  },
  conversationHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  conversationTitle: {
    fontSize: 14,
    fontWeight: "700",
    flex: 1,
  },
  lastMessageText: {
    fontSize: 13,
    color: "#6B7280",
    marginVertical: 2,
  },
  conversationMeta: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    marginTop: 4,
  },
  dateText: {
    fontSize: 12,
    color: "#9CA3AF",
  },
});
