/**
 * ThreadViewScreen — View and send messages within a conversation.
 */

import React, { useState, useEffect, useCallback, useRef } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  TextField,
  Avatar,
  LoadingSpinner,
  ErrorState,
} from "@impilo/mobile-design-system";
import type { Conversation, Message } from "@impilo/mobile-messaging";
import { fetchMessages, sendMessage, markConversationRead } from "../../services/messagingService";
import { useAppStore } from "../../stores/appStore";

interface ThreadViewScreenProps {
  conversation: Conversation;
  onBack: () => void;
}

export function ThreadViewScreen({ conversation, onBack }: ThreadViewScreenProps) {
  const { profile } = useAppStore();
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [newMessage, setNewMessage] = useState("");
  const [sending, setSending] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [page, setPage] = useState(0);
  const scrollViewRef = useRef<ScrollView | null>(null);

  const load = useCallback(async (pageNum: number, replace: boolean) => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchMessages(conversation.id, { page: pageNum, size: 50 });
      setMessages((prev) => replace ? result.items : [...result.items, ...prev]);
      setHasMore(result.hasNext);
      setPage(pageNum);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, [conversation.id]);

  useEffect(() => {
    load(0, true);
    markConversationRead(conversation.id).catch(() => { /* best effort */ });
  }, [load, conversation.id]);

  useEffect(() => {
    scrollViewRef.current?.scrollToEnd({ animated: true });
  }, [messages]);

  const handleSend = useCallback(async () => {
    if (!newMessage.trim()) return;
    setSending(true);
    try {
      const msg = await sendMessage(conversation.id, newMessage.trim());
      setMessages((prev) => [...prev, msg]);
      setNewMessage("");
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setSending(false);
    }
  }, [conversation.id, newMessage]);

  const isOwnMessage = (msg: Message) => msg.senderId === profile?.cpid;

  return (
    <Screen>
      <Header
        title={conversation.subject ?? "Conversation"}
        leftElement={
          <Button
            title={"\u2190 Back"}
            variant="ghost"
            size="sm"
            onPress={onBack}
            testID="thread-back"
          />
        }
      />
      <View testID="thread-view-screen" style={styles.container}>
        {/* Messages list */}
        <ScrollView
          ref={scrollViewRef}
          style={styles.messagesList}
          contentContainerStyle={styles.messagesContent}
        >
          {hasMore ? (
            <Button
              title={isLoading ? "Loading..." : "Load older messages"}
              variant="ghost"
              size="sm"
              onPress={() => load(page + 1, false)}
              disabled={isLoading}
              testID="load-older"
            />
          ) : null}

          {error ? (
            <ErrorState title="Error" message={error.message} onRetry={() => load(0, true)} />
          ) : null}

          {isLoading && messages.length === 0 ? (
            <LoadingSpinner size="md" />
          ) : (
            messages.map((msg) => (
              <View
                key={msg.id}
                testID={`message-${msg.id}`}
                style={[
                  styles.messageRow,
                  {
                    justifyContent: isOwnMessage(msg) ? "flex-end" : "flex-start",
                  },
                ]}
              >
                <View
                  style={[
                    styles.messageBubble,
                    {
                      backgroundColor: isOwnMessage(msg) ? "#2563EB" : "#F3F4F6",
                    },
                  ]}
                >
                  {!isOwnMessage(msg) ? (
                    <Text style={styles.senderName}>{msg.senderName}</Text>
                  ) : null}
                  <Text
                    style={[
                      styles.messageBody,
                      { color: isOwnMessage(msg) ? "white" : "#111827" },
                    ]}
                  >
                    {msg.body}
                  </Text>
                  <Text
                    style={[
                      styles.messageTime,
                      {
                        color: isOwnMessage(msg) ? "rgba(255,255,255,0.7)" : "#9CA3AF",
                      },
                    ]}
                  >
                    {new Date(msg.sentAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                  </Text>
                </View>
              </View>
            ))
          )}
        </ScrollView>

        {/* Message input */}
        <View style={styles.inputContainer}>
          <View style={styles.inputField}>
            <TextField
              value={newMessage}
              onChange={setNewMessage}
              placeholder="Type a message..."
              testID="message-input"
            />
          </View>
          <Button
            title={sending ? "..." : "Send"}
            variant="primary"
            onPress={handleSend}
            disabled={sending || !newMessage.trim()}
            testID="send-message"
          />
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  messagesList: {
    flex: 1,
  },
  messagesContent: {
    padding: 16,
    gap: 8,
  },
  messageRow: {
    flexDirection: "row",
    marginBottom: 4,
  },
  messageBubble: {
    maxWidth: "75%",
    paddingVertical: 10,
    paddingHorizontal: 14,
    borderRadius: 16,
  },
  senderName: {
    fontSize: 12,
    fontWeight: "600",
    color: "#6B7280",
    marginBottom: 4,
  },
  messageBody: {
    fontSize: 14,
    lineHeight: 20,
  },
  messageTime: {
    fontSize: 11,
    textAlign: "right",
    marginTop: 4,
  },
  inputContainer: {
    padding: 12,
    paddingHorizontal: 16,
    borderTopWidth: 1,
    borderTopColor: "#E5E7EB",
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    backgroundColor: "white",
  },
  inputField: {
    flex: 1,
  },
});
