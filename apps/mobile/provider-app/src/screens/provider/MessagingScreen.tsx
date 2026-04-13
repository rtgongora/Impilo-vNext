/**
 * MessagingScreen — Provider-to-provider and provider-to-patient messaging.
 *
 * Uses @impilo/mobile-messaging for conversations and real-time channels.
 */

import React, { useState, useCallback, useEffect } from "react";
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  TextField,
  Avatar,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import {
  useConversations,
  useMessages,
  useChannel,
  type Conversation,
  type Message,
} from "@impilo/mobile-messaging";
import { useAuth } from "@impilo/mobile-auth";

export function MessagingScreen() {
  const auth = useAuth();
  const { conversations, isLoading, error, refresh } = useConversations();
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null);

  if (activeConversationId) {
    return (
      <ConversationView
        conversationId={activeConversationId}
        currentUserId={auth.user?.sub ?? ""}
        onBack={() => setActiveConversationId(null)}
      />
    );
  }

  return (
    <Screen>
      <Header title="Messages" />
      <ScrollView testID="messaging-screen" style={styles.container}>
        {isLoading ? (
          <LoadingSpinner size="md" />
        ) : error ? (
          <ErrorState title="Error" message={error.message} onRetry={refresh} />
        ) : conversations.length === 0 ? (
          <EmptyState title="No conversations" message="Start a new conversation" />
        ) : (
          conversations.map((conv: Conversation) => (
            <Card key={conv.id}>
              <CardBody>
                <TouchableOpacity
                  testID={`conversation-${conv.id}`}
                  style={styles.conversationRow}
                  onPress={() => setActiveConversationId(conv.id)}
                >
                  <Avatar name={conv.title ?? conv.subject ?? "Unknown"} size="md" />
                  <View style={styles.conversationInfo}>
                    <Text style={styles.bold}>{conv.title ?? conv.subject ?? ""}</Text>
                    {conv.lastMessage ? (
                      <Text style={styles.lastMessage}>
                        {(conv.lastMessage.content ?? conv.lastMessage.body ?? "").substring(0, 60)}
                      </Text>
                    ) : null}
                    <Text style={styles.timestamp}>
                      {conv.updatedAt ? new Date(conv.updatedAt).toLocaleString() : ""}
                    </Text>
                  </View>
                  {conv.unreadCount > 0 ? (
                    <Badge variant="primary">{String(conv.unreadCount)}</Badge>
                  ) : null}
                </TouchableOpacity>
              </CardBody>
            </Card>
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

interface ConversationViewProps {
  conversationId: string;
  currentUserId: string;
  onBack: () => void;
}

function ConversationView({ conversationId, currentUserId, onBack }: ConversationViewProps) {
  const { messages, isLoading, error, refresh, send } = useMessages(conversationId);
  const [newMessage, setNewMessage] = useState("");
  const [sending, setSending] = useState(false);

  // Subscribe to real-time updates
  useChannel(`conversation:${conversationId}`);

  const handleSend = useCallback(async () => {
    if (!newMessage.trim()) return;
    setSending(true);
    try {
      await send(newMessage);
      setNewMessage("");
      refresh();
    } catch {
      // Error handling via channel reconnect
    } finally {
      setSending(false);
    }
  }, [newMessage, refresh, send]);

  return (
    <Screen>
      <Header title="Conversation" />
      <View testID="conversation-view" style={styles.conversationContainer}>
        <View style={styles.backRow}>
          <Button title="Back" variant="ghost" size="sm" onPress={onBack} testID="back-btn" />
        </View>

        {/* Messages */}
        <ScrollView style={styles.messagesScroll}>
          {isLoading ? (
            <LoadingSpinner size="sm" />
          ) : (
            messages.map((msg: Message) => (
              <View
                key={msg.id}
                testID={`message-${msg.id}`}
                style={[
                  styles.messageWrapper,
                  {
                    justifyContent:
                      msg.senderId === currentUserId ? "flex-end" : "flex-start",
                  },
                ]}
              >
                <View
                  style={[
                    styles.messageBubble,
                    {
                      backgroundColor:
                        msg.senderId === currentUserId ? "#3B82F6" : "#F3F4F6",
                    },
                  ]}
                >
                  <Text
                    style={{
                      color: msg.senderId === currentUserId ? "#FFFFFF" : "#111827",
                    }}
                  >
                    {msg.content ?? msg.body}
                  </Text>
                  <Text
                    style={[
                      styles.messageTime,
                      {
                        color: msg.senderId === currentUserId ? "rgba(255,255,255,0.7)" : "rgba(0,0,0,0.4)",
                      },
                    ]}
                  >
                    {new Date(msg.sentAt).toLocaleTimeString()}
                  </Text>
                </View>
              </View>
            ))
          )}
        </ScrollView>

        {/* Input */}
        <View style={styles.inputRow}>
          <TextField
            label=""
            value={newMessage}
            onChange={setNewMessage}
            placeholder="Type a message..."
            testID="message-input"
          />
          <Button
            title="Send"
            onPress={handleSend}
            loading={sending}
            testID="send-message-btn"
          />
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  conversationRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  conversationInfo: {
    flex: 1,
  },
  bold: {
    fontWeight: "bold",
  },
  lastMessage: {
    fontSize: 14,
    color: "#6B7280",
    marginVertical: 2,
  },
  timestamp: {
    fontSize: 12,
    color: "#9CA3AF",
  },
  conversationContainer: {
    flex: 1,
  },
  backRow: {
    paddingHorizontal: 16,
    paddingVertical: 8,
  },
  messagesScroll: {
    flex: 1,
    padding: 16,
  },
  messageWrapper: {
    flexDirection: "row",
    marginBottom: 8,
  },
  messageBubble: {
    padding: 8,
    paddingHorizontal: 12,
    borderRadius: 12,
    maxWidth: "70%",
  },
  messageTime: {
    fontSize: 10,
  },
  inputRow: {
    padding: 12,
    paddingHorizontal: 16,
    borderTopWidth: 1,
    borderTopColor: "#E5E7EB",
    flexDirection: "row",
    gap: 8,
  },
});
