/**
 * ThreadViewScreen — View and send messages within a conversation.
 */

import React, { useState, useEffect, useCallback, useRef } from "react";
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
  const messagesEndRef = useRef<HTMLDivElement | null>(null);

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
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
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

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, {
      title: conversation.subject ?? "Conversation",
      leftElement: React.createElement(Button, {
        title: "\u2190 Back",
        variant: "ghost",
        size: "sm",
        onPress: onBack,
        testID: "thread-back",
      }),
    }),
    React.createElement(
      "div",
      {
        "data-testid": "thread-view-screen",
        style: { display: "flex", flexDirection: "column", height: "100%", padding: "0" },
      },

      // Messages list
      React.createElement(
        "div",
        {
          style: {
            flex: 1,
            overflow: "auto",
            padding: "16px",
            display: "flex",
            flexDirection: "column",
            gap: "8px",
          },
        },
        hasMore
          ? React.createElement(Button, {
              title: isLoading ? "Loading..." : "Load older messages",
              variant: "ghost",
              size: "sm",
              onPress: () => load(page + 1, false),
              disabled: isLoading,
              testID: "load-older",
            })
          : null,

        error
          ? React.createElement(ErrorState, { title: "Error", message: error.message, onRetry: () => load(0, true) })
          : null,

        isLoading && messages.length === 0
          ? React.createElement(LoadingSpinner, { size: "md" })
          : messages.map((msg) =>
              React.createElement(
                "div",
                {
                  key: msg.id,
                  "data-testid": `message-${msg.id}`,
                  style: {
                    display: "flex",
                    justifyContent: isOwnMessage(msg) ? "flex-end" : "flex-start",
                    marginBottom: "4px",
                  },
                },
                React.createElement(
                  "div",
                  {
                    style: {
                      maxWidth: "75%",
                      padding: "10px 14px",
                      borderRadius: "16px",
                      backgroundColor: isOwnMessage(msg) ? "#2563EB" : "#F3F4F6",
                      color: isOwnMessage(msg) ? "white" : "#111827",
                    },
                  },
                  !isOwnMessage(msg)
                    ? React.createElement("p", {
                        style: { fontSize: "12px", fontWeight: "600", color: "#6B7280", margin: "0 0 4px" },
                      }, msg.senderName)
                    : null,
                  React.createElement("p", { style: { fontSize: "14px", margin: 0, lineHeight: "1.4" } }, msg.body),
                  React.createElement("span", {
                    style: {
                      fontSize: "11px",
                      color: isOwnMessage(msg) ? "rgba(255,255,255,0.7)" : "#9CA3AF",
                      display: "block",
                      textAlign: "right",
                      marginTop: "4px",
                    },
                  }, new Date(msg.sentAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }))
                )
              )
            ),

        React.createElement("div", { ref: messagesEndRef })
      ),

      // Message input
      React.createElement(
        "div",
        {
          style: {
            padding: "12px 16px",
            borderTop: "1px solid #E5E7EB",
            display: "flex",
            gap: "8px",
            alignItems: "center",
            backgroundColor: "white",
          },
        },
        React.createElement(
          "div",
          { style: { flex: 1 } },
          React.createElement(TextField, {
            value: newMessage,
            onChange: setNewMessage,
            placeholder: "Type a message...",
            testID: "message-input",
          })
        ),
        React.createElement(Button, {
          title: sending ? "..." : "Send",
          variant: "primary",
          onPress: handleSend,
          disabled: sending || !newMessage.trim(),
          testID: "send-message",
        })
      )
    )
  );
}
