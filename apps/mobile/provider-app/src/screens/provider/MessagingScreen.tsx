/**
 * MessagingScreen — Provider-to-provider and provider-to-patient messaging.
 *
 * Uses @impilo/mobile-messaging for conversations and real-time channels.
 */

import React, { useState, useCallback, useEffect } from "react";
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
  sendMessage,
  createConversation,
  useChannel,
  type Conversation,
  type Message,
} from "@impilo/mobile-messaging";
import { useAuth } from "@impilo/mobile-auth";

export function MessagingScreen() {
  const auth = useAuth();
  const { conversations, loading, error, refresh } = useConversations();
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null);

  if (activeConversationId) {
    return React.createElement(ConversationView, {
      conversationId: activeConversationId,
      currentUserId: auth.user?.sub ?? "",
      onBack: () => setActiveConversationId(null),
    });
  }

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, { title: "Messages" }),
    React.createElement(
      "div",
      { "data-testid": "messaging-screen", style: { padding: "16px" } },
      loading
        ? React.createElement(LoadingSpinner, { size: "md" })
        : error
          ? React.createElement(ErrorState, { title: "Error", message: error, onRetry: refresh })
          : conversations.length === 0
            ? React.createElement(EmptyState, { title: "No conversations", message: "Start a new conversation" })
            : conversations.map((conv: Conversation) =>
                React.createElement(
                  Card,
                  { key: conv.id },
                  React.createElement(
                    CardBody,
                    null,
                    React.createElement(
                      "div",
                      {
                        "data-testid": `conversation-${conv.id}`,
                        style: { display: "flex", alignItems: "center", gap: "12px", cursor: "pointer" },
                        onClick: () => setActiveConversationId(conv.id),
                      },
                      React.createElement(Avatar, { name: conv.title ?? "Unknown", size: "md" }),
                      React.createElement(
                        "div",
                        { style: { flex: 1 } },
                        React.createElement("strong", null, conv.title),
                        conv.lastMessage
                          ? React.createElement("p", { style: { fontSize: "14px", color: "#6B7280", margin: "2px 0" } },
                              conv.lastMessage.content.substring(0, 60)
                            )
                          : null,
                        React.createElement(
                          "span",
                          { style: { fontSize: "12px", color: "#9CA3AF" } },
                          conv.updatedAt ? new Date(conv.updatedAt).toLocaleString() : ""
                        )
                      ),
                      conv.unreadCount > 0
                        ? React.createElement(Badge, { variant: "primary", children: String(conv.unreadCount) })
                        : null
                    )
                  )
                )
              )
    )
  );
}

interface ConversationViewProps {
  conversationId: string;
  currentUserId: string;
  onBack: () => void;
}

function ConversationView({ conversationId, currentUserId, onBack }: ConversationViewProps) {
  const { messages, loading, error, refresh } = useMessages(conversationId);
  const [newMessage, setNewMessage] = useState("");
  const [sending, setSending] = useState(false);

  // Subscribe to real-time updates
  const { status: channelStatus } = useChannel(`conversation:${conversationId}`, {
    onMessage: () => refresh(),
  });

  const handleSend = useCallback(async () => {
    if (!newMessage.trim()) return;
    setSending(true);
    try {
      await sendMessage(conversationId, { content: newMessage, contentType: "TEXT" });
      setNewMessage("");
      refresh();
    } catch {
      // Error handling via channel reconnect
    } finally {
      setSending(false);
    }
  }, [conversationId, newMessage, refresh]);

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, { title: "Conversation" }),
    React.createElement(
      "div",
      { "data-testid": "conversation-view", style: { display: "flex", flexDirection: "column", height: "calc(100vh - 120px)" } },

      React.createElement(
        "div",
        { style: { padding: "8px 16px" } },
        React.createElement(Button, { title: "Back", variant: "ghost", size: "sm", onPress: onBack, testID: "back-btn" })
      ),

      // Messages
      React.createElement(
        "div",
        { style: { flex: 1, overflow: "auto", padding: "16px" } },
        loading
          ? React.createElement(LoadingSpinner, { size: "sm" })
          : messages.map((msg: Message) =>
              React.createElement(
                "div",
                {
                  key: msg.id,
                  "data-testid": `message-${msg.id}`,
                  style: {
                    display: "flex",
                    justifyContent: msg.senderId === currentUserId ? "flex-end" : "flex-start",
                    marginBottom: "8px",
                  },
                },
                React.createElement(
                  "div",
                  {
                    style: {
                      backgroundColor: msg.senderId === currentUserId ? "#3B82F6" : "#F3F4F6",
                      color: msg.senderId === currentUserId ? "#FFFFFF" : "#111827",
                      padding: "8px 12px",
                      borderRadius: "12px",
                      maxWidth: "70%",
                    },
                  },
                  React.createElement("p", { style: { margin: 0 } }, msg.content),
                  React.createElement(
                    "span",
                    { style: { fontSize: "10px", opacity: 0.7 } },
                    new Date(msg.sentAt).toLocaleTimeString()
                  )
                )
              )
            )
      ),

      // Input
      React.createElement(
        "div",
        { style: { padding: "12px 16px", borderTop: "1px solid #E5E7EB", display: "flex", gap: "8px" } },
        React.createElement(TextField, {
          label: "",
          value: newMessage,
          onChange: setNewMessage,
          placeholder: "Type a message...",
          testID: "message-input",
        }),
        React.createElement(Button, {
          title: "Send",
          onPress: handleSend,
          loading: sending,
          testID: "send-message-btn",
        })
      )
    )
  );
}
