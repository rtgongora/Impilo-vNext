import React, { useState, useEffect, useCallback, useRef } from "react";
import {
  View,
  Text,
  ScrollView,
  Pressable,
  StyleSheet,
  RefreshControl,
  KeyboardAvoidingView,
  Platform,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import {
  Screen,
  Header,
  TextField,
  Select,
  Avatar,
  LoadingSpinner,
} from "@impilo/mobile-design-system";
import type { Conversation } from "@impilo/mobile-messaging";
import {
  fetchConversations,
  createConversation,
} from "../../services/messagingService";
import { useAppStore } from "../../stores/appStore";
import { ThreadViewScreen } from "./ThreadViewScreen";
import { useToast } from "../../components/Toast";
import { SkeletonConversationList } from "../../components/SkeletonLoader";
import {
  APP_GREEN,
  APP_GREEN_LIGHT,
  APP_GREEN_XLIGHT,
  APP_SURFACE,
  APP_BG,
  APP_TEXT,
  APP_TEXT_2,
  APP_TEXT_3,
  APP_BORDER,
} from "../../lib/colors";

const FILTERS = [
  { label: "All",      value: "" },
  { label: "Provider", value: "DIRECT" },
  { label: "Support",  value: "SUPPORT" },
];

export function MessagingInboxScreen() {
  const toast = useToast();
  const toastRef = useRef(toast);
  toastRef.current = toast;

  const setUnreadMessages = useAppStore((s) => s.setUnreadMessages);

  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [filter, setFilter] = useState("");
  const [selectedConversation, setSelectedConversation] = useState<Conversation | null>(null);
  const [showCompose, setShowCompose] = useState(false);
  const [newRecipientId, setNewRecipientId] = useState("");
  const [newSubject, setNewSubject] = useState("");
  const [newMessage, setNewMessage] = useState("");
  const [newType, setNewType] = useState<"DIRECT" | "SUPPORT">("DIRECT");
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    try {
      const result = await fetchConversations({ type: filter || undefined, size: 50 });
      setConversations(result.items);
      const totalUnread = result.items.reduce((sum, c) => sum + c.unreadCount, 0);
      setUnreadMessages(totalUnread);
    } catch {
      toastRef.current.error("Could not load messages. Pull down to retry.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [filter, setUnreadMessages]); // toast via ref — not a dep

  useEffect(() => {
    void load();
  }, [load]);

  const onRefresh = useCallback(() => {
    setIsRefreshing(true);
    void load(true);
  }, [load]);

  const handleCompose = useCallback(async () => {
    if (!newRecipientId.trim() || !newMessage.trim()) {
      toastRef.current.warning("Please fill in the recipient and message fields.");
      return;
    }
    setSubmitting(true);
    try {
      const conv = await createConversation({
        recipientId: newRecipientId.trim(),
        subject: newSubject || undefined,
        type: newType,
        initialMessage: newMessage,
      });
      setShowCompose(false);
      setNewRecipientId("");
      setNewSubject("");
      setNewMessage("");
      toastRef.current.success("Message sent!");
      setSelectedConversation(conv);
      void load(true);
    } catch {
      toastRef.current.error("Failed to send message. Please try again.");
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
          void load(true);
        }}
      />
    );
  }

  return (
    <Screen>
      <Header
        title="Messages"
        rightElement={
          <Pressable
            onPress={() => setShowCompose(!showCompose)}
            style={[styles.composeBtn, showCompose && styles.composeBtnActive]}
            hitSlop={8}
          >
            <Ionicons
              name={showCompose ? "close" : "create-outline"}
              size={20}
              color={showCompose ? APP_TEXT_2 : APP_GREEN}
            />
          </Pressable>
        }
      />

      {/* Compose Panel */}
      {showCompose ? (
        <KeyboardAvoidingView
          behavior={Platform.OS === "ios" ? "padding" : undefined}
        >
          <View style={styles.composePanel}>
            <Text style={styles.composePanelTitle}>New Message</Text>
            <Select
              label="Type"
              value={newType}
              onChange={(v: string) => setNewType(v as "DIRECT" | "SUPPORT")}
              options={[
                { label: "Message a Provider", value: "DIRECT" },
                { label: "Contact Support", value: "SUPPORT" },
              ]}
              testID="new-msg-type"
            />
            <View style={styles.recipientHintRow}>
              <TextField
                label={newType === "DIRECT" ? "Provider ID" : "Support Team ID"}
                value={newRecipientId}
                onChange={setNewRecipientId}
                placeholder={newType === "DIRECT" ? "Enter your provider's ID" : "Enter support team ID"}
                testID="new-msg-recipient"
              />
              <View style={styles.recipientHint}>
                <Ionicons name="information-circle-outline" size={14} color={APP_TEXT_3} />
                <Text style={styles.recipientHintText}>
                  {newType === "DIRECT"
                    ? "Find your provider's ID in your care team section"
                    : "Use 'general-support' for general enquiries"}
                </Text>
              </View>
            </View>
            <TextField
              label="Subject (optional)"
              value={newSubject}
              onChange={setNewSubject}
              placeholder="What is this about?"
              testID="new-msg-subject"
            />
            <TextField
              label="Message"
              value={newMessage}
              onChange={setNewMessage}
              placeholder="Type your message..."
              testID="new-msg-body"
            />
            <Pressable
              onPress={handleCompose}
              disabled={submitting}
              style={[
                styles.sendBtn,
                (submitting || !newRecipientId.trim() || !newMessage.trim()) && styles.sendBtnDisabled,
              ]}
              testID="send-new-message"
            >
              {submitting ? (
                <LoadingSpinner size="sm" />
              ) : (
                <>
                  <Ionicons name="send" size={16} color="#FFFFFF" />
                  <Text style={styles.sendBtnText}>Send Message</Text>
                </>
              )}
            </Pressable>
          </View>
        </KeyboardAvoidingView>
      ) : null}

      {/* Filter Pills */}
      <View style={styles.filterBar}>
        {FILTERS.map((f) => (
          <Pressable
            key={f.value}
            onPress={() => setFilter(f.value)}
            style={[styles.filterPill, filter === f.value && styles.filterPillActive]}
            testID={`filter-${f.value || "all"}`}
          >
            <Text style={[styles.filterPillText, filter === f.value && styles.filterPillTextActive]}>
              {f.label}
            </Text>
          </Pressable>
        ))}
        <View style={styles.filterSpacer} />
        {conversations.length > 0 ? (
          <Text style={styles.countText}>{conversations.length} conversations</Text>
        ) : null}
      </View>

      <ScrollView
        testID="messaging-inbox-screen"
        style={styles.scroll}
        contentContainerStyle={styles.container}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={isRefreshing}
            onRefresh={onRefresh}
            tintColor={APP_GREEN}
            colors={[APP_GREEN]}
          />
        }
      >
        {isLoading ? (
          <SkeletonConversationList />
        ) : conversations.length === 0 ? (
          <View style={styles.emptyBox}>
            <View style={styles.emptyIconWrap}>
              <Ionicons name="chatbubbles-outline" size={32} color={APP_TEXT_3} />
            </View>
            <Text style={styles.emptyTitle}>No conversations</Text>
            <Text style={styles.emptyMessage}>
              {filter
                ? "No conversations match this filter"
                : "Start a conversation with your provider or support team"}
            </Text>
            <Pressable
              style={styles.emptyAction}
              onPress={() => setShowCompose(true)}
            >
              <Ionicons name="create-outline" size={16} color="#FFFFFF" />
              <Text style={styles.emptyActionText}>New Message</Text>
            </Pressable>
          </View>
        ) : (
          conversations.map((conv) => (
            <Pressable
              key={conv.id}
              testID={`conversation-${conv.id}`}
              onPress={() => setSelectedConversation(conv)}
              style={({ pressed }) => [
                styles.convCard,
                conv.unreadCount > 0 && styles.convCardUnread,
                pressed && { opacity: 0.92 },
              ]}
            >
              <View style={styles.avatarWrap}>
                <Avatar
                  name={conv.participants?.[0]?.displayName ?? "?"}
                  size="md"
                />
                {conv.unreadCount > 0 ? (
                  <View style={styles.unreadBadge}>
                    <Text style={styles.unreadBadgeText}>
                      {conv.unreadCount > 9 ? "9+" : String(conv.unreadCount)}
                    </Text>
                  </View>
                ) : null}
              </View>

              <View style={styles.convInfo}>
                <View style={styles.convTopRow}>
                  <Text
                    style={[styles.convTitle, conv.unreadCount > 0 && styles.convTitleUnread]}
                    numberOfLines={1}
                  >
                    {conv.subject ?? conv.participants?.map((p) => p.displayName).join(", ") ?? "Conversation"}
                  </Text>
                  <Text style={styles.convDate}>
                    {new Date(conv.updatedAt).toLocaleDateString()}
                  </Text>
                </View>
                {conv.lastMessage ? (
                  <Text
                    style={[styles.convPreview, conv.unreadCount > 0 && styles.convPreviewUnread]}
                    numberOfLines={1}
                  >
                    {conv.lastMessage.body}
                  </Text>
                ) : null}
                <View style={styles.convMeta}>
                  <View
                    style={[
                      styles.typePill,
                      conv.type === "DIRECT" ? styles.typePillDirect : styles.typePillSupport,
                    ]}
                  >
                    <Ionicons
                      name={conv.type === "DIRECT" ? "person-outline" : "headset-outline"}
                      size={11}
                      color={conv.type === "DIRECT" ? "#1E40AF" : "#7C3AED"}
                    />
                    <Text
                      style={[
                        styles.typePillText,
                        conv.type === "DIRECT" ? styles.typePillDirect_text : styles.typePillSupport_text,
                      ]}
                    >
                      {conv.type === "DIRECT" ? "Provider" : "Support"}
                    </Text>
                  </View>
                </View>
              </View>

              <Ionicons name="chevron-forward" size={16} color={APP_TEXT_3} />
            </Pressable>
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  composeBtn: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: APP_GREEN_LIGHT,
    alignItems: "center",
    justifyContent: "center",
  },
  composeBtnActive: { backgroundColor: "#F3F4F6" },

  composePanel: {
    backgroundColor: APP_SURFACE,
    padding: 16,
    gap: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: APP_BORDER,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 4,
  },
  composePanelTitle: { fontSize: 15, fontWeight: "700", color: APP_TEXT },
  recipientHintRow: { gap: 6 },
  recipientHint: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 6,
    paddingHorizontal: 4,
  },
  recipientHintText: { fontSize: 12, color: APP_TEXT_3, flex: 1, lineHeight: 16 },
  sendBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    backgroundColor: APP_GREEN,
    paddingVertical: 14,
    borderRadius: 14,
    shadowColor: APP_GREEN,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 5,
  },
  sendBtnDisabled: { opacity: 0.5, shadowOpacity: 0 },
  sendBtnText: { fontSize: 15, fontWeight: "700", color: "#FFFFFF" },

  filterBar: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingHorizontal: 16,
    paddingVertical: 10,
    backgroundColor: APP_SURFACE,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: APP_BORDER,
  },
  filterPill: {
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 20,
    backgroundColor: "#F3F4F6",
  },
  filterPillActive: { backgroundColor: APP_GREEN_XLIGHT, borderWidth: 1, borderColor: APP_GREEN_LIGHT },
  filterPillText: { fontSize: 13, fontWeight: "500", color: APP_TEXT_2 },
  filterPillTextActive: { color: APP_GREEN, fontWeight: "700" },
  filterSpacer: { flex: 1 },
  countText: { fontSize: 12, color: APP_TEXT_3 },

  scroll: { flex: 1, backgroundColor: APP_BG },
  container: { padding: 14, gap: 10, paddingBottom: 40 },

  emptyBox: { alignItems: "center", paddingVertical: 64, gap: 12 },
  emptyIconWrap: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: "#F3F4F6",
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 4,
  },
  emptyTitle: { fontSize: 17, fontWeight: "700", color: APP_TEXT },
  emptyMessage: {
    fontSize: 14,
    color: APP_TEXT_3,
    textAlign: "center",
    paddingHorizontal: 32,
    lineHeight: 20,
  },
  emptyAction: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    marginTop: 4,
    paddingHorizontal: 20,
    paddingVertical: 12,
    backgroundColor: APP_GREEN,
    borderRadius: 14,
  },
  emptyActionText: { color: "#FFFFFF", fontWeight: "700", fontSize: 14 },

  convCard: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    backgroundColor: APP_SURFACE,
    borderRadius: 16,
    padding: 14,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  convCardUnread: {
    borderLeftWidth: 3,
    borderLeftColor: APP_GREEN,
    shadowOpacity: 0.08,
    elevation: 4,
  },
  avatarWrap: { position: "relative" },
  unreadBadge: {
    position: "absolute",
    top: -4,
    right: -4,
    backgroundColor: APP_GREEN,
    borderRadius: 10,
    minWidth: 18,
    height: 18,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 4,
    borderWidth: 2,
    borderColor: APP_SURFACE,
  },
  unreadBadgeText: { fontSize: 10, fontWeight: "700", color: "#FFFFFF" },

  convInfo: { flex: 1 },
  convTopRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
    marginBottom: 3,
  },
  convTitle: { fontSize: 14, fontWeight: "600", color: APP_TEXT, flex: 1, marginRight: 8 },
  convTitleUnread: { fontWeight: "800" },
  convDate: { fontSize: 11, color: APP_TEXT_3 },
  convPreview: { fontSize: 13, color: APP_TEXT_2, marginBottom: 6, lineHeight: 17 },
  convPreviewUnread: { color: APP_TEXT, fontWeight: "500" },
  convMeta: { flexDirection: "row", gap: 8 },

  typePill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 10,
  },
  typePillDirect: { backgroundColor: "#EFF6FF" },
  typePillSupport: { backgroundColor: "#F5F3FF" },
  typePillText: { fontSize: 11, fontWeight: "600" },
  typePillDirect_text: { color: "#1E40AF" },
  typePillSupport_text: { color: "#7C3AED" },
});
