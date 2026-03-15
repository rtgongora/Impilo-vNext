/**
 * MessagingInboxScreen — Conversation inbox for citizen-provider and citizen-support messaging.
 */

import React, { useState, useEffect, useCallback } from "react";
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
    return React.createElement(ThreadViewScreen, {
      conversation: selectedConversation,
      onBack: () => {
        setSelectedConversation(null);
        load();
      },
    });
  }

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, { title: "Messages" }),
    React.createElement(
      "div",
      { "data-testid": "messaging-inbox-screen", style: { padding: "16px", display: "flex", flexDirection: "column", gap: "16px" } },

      // Actions bar
      React.createElement(
        "div",
        { style: { display: "flex", justifyContent: "space-between", alignItems: "center" } },
        React.createElement(
          "div",
          { style: { display: "flex", gap: "8px" } },
          React.createElement(Button, {
            title: "All",
            variant: filter === "" ? "primary" : "ghost",
            size: "sm",
            onPress: () => setFilter(""),
            testID: "filter-all",
          }),
          React.createElement(Button, {
            title: "Provider",
            variant: filter === "DIRECT" ? "primary" : "ghost",
            size: "sm",
            onPress: () => setFilter("DIRECT"),
            testID: "filter-direct",
          }),
          React.createElement(Button, {
            title: "Support",
            variant: filter === "SUPPORT" ? "primary" : "ghost",
            size: "sm",
            onPress: () => setFilter("SUPPORT"),
            testID: "filter-support",
          })
        ),
        React.createElement(Button, {
          title: showNewMessage ? "Cancel" : "New Message",
          variant: showNewMessage ? "ghost" : "primary",
          size: "sm",
          onPress: () => setShowNewMessage(!showNewMessage),
          testID: "toggle-new-message",
        })
      ),

      // New message form
      showNewMessage
        ? React.createElement(
            Card,
            null,
            React.createElement(
              CardBody,
              null,
              React.createElement(
                "div",
                { style: { display: "flex", flexDirection: "column", gap: "12px" } },
                React.createElement(Select, {
                  label: "Type",
                  value: newType,
                  onChange: (v: string) => setNewType(v as "DIRECT" | "SUPPORT"),
                  options: [
                    { label: "Message Provider", value: "DIRECT" },
                    { label: "Contact Support", value: "SUPPORT" },
                  ],
                  testID: "new-msg-type",
                }),
                React.createElement(TextField, {
                  label: newType === "DIRECT" ? "Provider ID" : "Support Team ID",
                  value: newRecipientId,
                  onChange: setNewRecipientId,
                  placeholder: "Enter recipient ID",
                  testID: "new-msg-recipient",
                }),
                React.createElement(TextField, {
                  label: "Subject (optional)",
                  value: newSubject,
                  onChange: setNewSubject,
                  placeholder: "Message subject",
                  testID: "new-msg-subject",
                }),
                React.createElement(TextField, {
                  label: "Message",
                  value: newMessage,
                  onChange: setNewMessage,
                  placeholder: "Type your message...",
                  testID: "new-msg-body",
                }),
                React.createElement(Button, {
                  title: submitting ? "Sending..." : "Send",
                  variant: "primary",
                  onPress: handleCreateConversation,
                  disabled: submitting || !newRecipientId || !newMessage,
                  testID: "send-new-message",
                })
              )
            )
          )
        : null,

      error
        ? React.createElement(ErrorState, { title: "Error", message: error.message, onRetry: load })
        : null,

      // Conversation list
      isLoading
        ? React.createElement(LoadingSpinner, { size: "md" })
        : conversations.length === 0
          ? React.createElement(EmptyState, {
              title: "No conversations",
              message: "Start a conversation with your provider or support team",
            })
          : conversations.map((conv) =>
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
                      style: {
                        display: "flex",
                        alignItems: "center",
                        gap: "12px",
                        cursor: "pointer",
                        opacity: conv.unreadCount > 0 ? 1 : 0.8,
                      },
                      onClick: () => setSelectedConversation(conv),
                    },
                    React.createElement(Avatar, {
                      name: conv.participants?.[0]?.displayName ?? "Unknown",
                      size: "md",
                    }),
                    React.createElement(
                      "div",
                      { style: { flex: 1 } },
                      React.createElement(
                        "div",
                        { style: { display: "flex", justifyContent: "space-between", alignItems: "center" } },
                        React.createElement("strong", { style: { fontSize: "14px" } },
                          conv.subject ?? conv.participants?.map((p) => p.displayName).join(", ") ?? "Conversation"
                        ),
                        conv.unreadCount > 0
                          ? React.createElement(Badge, { variant: "destructive", children: String(conv.unreadCount) })
                          : null
                      ),
                      conv.lastMessage
                        ? React.createElement("p", { style: { fontSize: "13px", color: "#6B7280", margin: "2px 0", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" } },
                            conv.lastMessage.body
                          )
                        : null,
                      React.createElement(
                        "div",
                        { style: { display: "flex", gap: "8px", alignItems: "center", marginTop: "4px" } },
                        React.createElement(Badge, { variant: "outline", children: conv.type }),
                        React.createElement("span", { style: { fontSize: "12px", color: "#9CA3AF" } },
                          new Date(conv.updatedAt).toLocaleDateString()
                        )
                      )
                    )
                  )
                )
              )
            )
    )
  );
}
