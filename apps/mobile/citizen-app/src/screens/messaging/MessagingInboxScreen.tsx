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
  TextInput,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import {
  Screen,
  Header,
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

// Initials from a display name for avatar fallback
function initials(name: string): string {
  return name
    .split(" ")
    .map((w) => w[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  const now = new Date();
  const diffDays = Math.floor((now.getTime() - d.getTime()) / 86_400_000);
  if (diffDays === 0) return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  if (diffDays === 1) return "Yesterday";
  if (diffDays < 7)  return d.toLocaleDateString([], { weekday: "short" });
  return d.toLocaleDateString([], { day: "numeric", month: "short" });
}

export function MessagingInboxScreen() {
  const toast    = useToast();
  const toastRef = useRef(toast);
  toastRef.current = toast;

  const setUnreadMessages = useAppStore((s) => s.setUnreadMessages);

  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [isLoading,    setIsLoading]    = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [filter, setFilter] = useState("");
  const [selectedConversation, setSelectedConversation] = useState<Conversation | null>(null);

  // Compose state
  const [showCompose,    setShowCompose]    = useState(false);
  const [newType,        setNewType]        = useState<"DIRECT" | "SUPPORT">("DIRECT");
  const [newRecipientId, setNewRecipientId] = useState("");
  const [newSubject,     setNewSubject]     = useState("");
  const [newMessage,     setNewMessage]     = useState("");
  const [submitting,     setSubmitting]     = useState(false);

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
  }, [filter, setUnreadMessages]);

  useEffect(() => { void load(); }, [load]);

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

  const closeCompose = useCallback(() => {
    setShowCompose(false);
    setNewRecipientId("");
    setNewSubject("");
    setNewMessage("");
  }, []);

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

  const totalUnread = conversations.reduce((s, c) => s + c.unreadCount, 0);

  return (
    <Screen>
      <Header
        title="Messages"
        rightElement={
          <Pressable
            onPress={() => (showCompose ? closeCompose() : setShowCompose(true))}
            style={[styles.composeBtn, showCompose && styles.composeBtnActive]}
            hitSlop={8}
          >
            <Ionicons
              name={showCompose ? "close" : "create-outline"}
              size={19}
              color={showCompose ? APP_TEXT_2 : APP_GREEN}
            />
          </Pressable>
        }
      />

      {/* Compose panel */}
      {showCompose ? (
        <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : undefined}>
          <View style={styles.composePanel}>
            <Text style={styles.composePanelTitle}>New Message</Text>

            {/* Type toggle */}
            <View style={styles.typeToggleRow}>
              {(["DIRECT", "SUPPORT"] as const).map((t) => (
                <Pressable
                  key={t}
                  onPress={() => setNewType(t)}
                  style={[styles.typeToggleBtn, newType === t && styles.typeToggleBtnActive]}
                >
                  <Ionicons
                    name={t === "DIRECT" ? "person-outline" : "headset-outline"}
                    size={13}
                    color={newType === t ? APP_GREEN : APP_TEXT_3}
                  />
                  <Text style={[styles.typeToggleText, newType === t && styles.typeToggleTextActive]}>
                    {t === "DIRECT" ? "Provider" : "Support"}
                  </Text>
                </Pressable>
              ))}
            </View>

            <View style={styles.inputGroup}>
              <Text style={styles.fieldLabel}>
                {newType === "DIRECT" ? "Provider ID" : "Team ID"}
              </Text>
              <View style={styles.inputWrap}>
                <TextInput
                  style={styles.input}
                  value={newRecipientId}
                  onChangeText={setNewRecipientId}
                  placeholder={newType === "DIRECT" ? "Enter your provider's ID" : "e.g. general-support"}
                  placeholderTextColor={APP_TEXT_3}
                  autoCapitalize="none"
                  testID="new-msg-recipient"
                />
              </View>
            </View>

            <View style={styles.inputGroup}>
              <Text style={styles.fieldLabel}>Subject (optional)</Text>
              <View style={styles.inputWrap}>
                <TextInput
                  style={styles.input}
                  value={newSubject}
                  onChangeText={setNewSubject}
                  placeholder="What is this about?"
                  placeholderTextColor={APP_TEXT_3}
                  testID="new-msg-subject"
                />
              </View>
            </View>

            <View style={styles.inputGroup}>
              <Text style={styles.fieldLabel}>Message</Text>
              <View style={[styles.inputWrap, { alignItems: "flex-start", paddingTop: 12 }]}>
                <TextInput
                  style={[styles.input, { minHeight: 72, textAlignVertical: "top" }]}
                  value={newMessage}
                  onChangeText={setNewMessage}
                  placeholder="Type your message..."
                  placeholderTextColor={APP_TEXT_3}
                  multiline
                  testID="new-msg-body"
                />
              </View>
            </View>

            <Pressable
              onPress={handleCompose}
              disabled={submitting || !newRecipientId.trim() || !newMessage.trim()}
              style={[
                styles.sendBtn,
                (submitting || !newRecipientId.trim() || !newMessage.trim()) && { opacity: 0.45 },
              ]}
              testID="send-new-message"
            >
              {submitting ? (
                <LoadingSpinner size="sm" />
              ) : (
                <>
                  <Ionicons name="send" size={15} color="#fff" />
                  <Text style={styles.sendBtnText}>Send Message</Text>
                </>
              )}
            </Pressable>
          </View>
        </KeyboardAvoidingView>
      ) : null}

      {/* Filter + count row */}
      <View style={styles.filterBar}>
        {FILTERS.map((f) => (
          <Pressable
            key={f.value}
            onPress={() => setFilter(f.value)}
            style={[styles.chip, filter === f.value && styles.chipActive]}
            testID={`filter-${f.value || "all"}`}
          >
            <Text style={[styles.chipText, filter === f.value && styles.chipTextActive]}>
              {f.label}
            </Text>
          </Pressable>
        ))}
        <View style={{ flex: 1 }} />
        {totalUnread > 0 ? (
          <View style={styles.unreadSummary}>
            <Text style={styles.unreadSummaryText}>{totalUnread} unread</Text>
          </View>
        ) : null}
      </View>

      <ScrollView
        testID="messaging-inbox-screen"
        style={styles.scroll}
        contentContainerStyle={styles.container}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} tintColor={APP_GREEN} colors={[APP_GREEN]} />
        }
      >
        {isLoading ? (
          <SkeletonConversationList />
        ) : conversations.length === 0 ? (
          <View style={styles.emptyWrap}>
            <Ionicons name="chatbubbles-outline" size={36} color={APP_TEXT_3} />
            <Text style={styles.emptyTitle}>No conversations</Text>
            <Text style={styles.emptyMsg}>
              {filter
                ? "No conversations match this filter"
                : "Start a conversation with your provider or support team"}
            </Text>
            <Pressable style={styles.emptyAction} onPress={() => setShowCompose(true)}>
              <Ionicons name="create-outline" size={15} color="#fff" />
              <Text style={styles.emptyActionText}>New Message</Text>
            </Pressable>
          </View>
        ) : (
          conversations.map((conv) => {
            const name = conv.participants?.[0]?.displayName ?? "Unknown";
            const isUnread = conv.unreadCount > 0;
            const isSupport = conv.type === "SUPPORT";

            return (
              <Pressable
                key={conv.id}
                testID={`conversation-${conv.id}`}
                onPress={() => setSelectedConversation(conv)}
                style={({ pressed }) => [styles.convCard, pressed && { opacity: 0.94 }]}
              >
                {/* Avatar + unread dot */}
                <View style={styles.avatarWrap}>
                  <Avatar name={name} size="md" />
                  {isUnread ? <View style={styles.unreadDot} /> : null}
                </View>

                {/* Content */}
                <View style={styles.convContent}>
                  <View style={styles.convTopRow}>
                    <Text
                      style={[styles.convName, isUnread && styles.convNameUnread]}
                      numberOfLines={1}
                    >
                      {name}
                    </Text>
                    <Text style={styles.convDate}>{formatDate(conv.updatedAt)}</Text>
                  </View>

                  {conv.subject ? (
                    <Text style={styles.convSubject} numberOfLines={1}>{conv.subject}</Text>
                  ) : null}

                  {conv.lastMessage ? (
                    <Text
                      style={[styles.convPreview, isUnread && styles.convPreviewUnread]}
                      numberOfLines={1}
                    >
                      {conv.lastMessage.body}
                    </Text>
                  ) : null}

                  {/* Type label + unread count */}
                  <View style={styles.convFooter}>
                    <Text style={[styles.typeLabel, isSupport && styles.typeLabelSupport]}>
                      {isSupport ? "Support" : "Provider"}
                    </Text>
                    {isUnread ? (
                      <View style={styles.unreadBadge}>
                        <Text style={styles.unreadBadgeText}>
                          {conv.unreadCount > 9 ? "9+" : String(conv.unreadCount)}
                        </Text>
                      </View>
                    ) : null}
                  </View>
                </View>

                <Ionicons name="chevron-forward" size={15} color={APP_BORDER} />
              </Pressable>
            );
          })
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  // ── Header compose button ─────────────────────────────────────────────────
  composeBtn: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: APP_GREEN_XLIGHT,
    alignItems: "center",
    justifyContent: "center",
  },
  composeBtnActive: { backgroundColor: APP_BG },

  // ── Compose panel ─────────────────────────────────────────────────────────
  composePanel: {
    backgroundColor: APP_SURFACE,
    padding: 16,
    gap: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: APP_BORDER,
  },
  composePanelTitle: { fontSize: 14, fontWeight: "700", color: APP_TEXT },
  typeToggleRow: { flexDirection: "row", gap: 8 },
  typeToggleBtn: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 5,
    paddingVertical: 9,
    borderRadius: 12,
    backgroundColor: APP_BG,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: APP_BORDER,
  },
  typeToggleBtnActive: { backgroundColor: APP_GREEN_XLIGHT, borderColor: APP_GREEN_LIGHT },
  typeToggleText: { fontSize: 13, fontWeight: "500", color: APP_TEXT_3 },
  typeToggleTextActive: { color: APP_GREEN, fontWeight: "700" },
  inputGroup: { gap: 5 },
  fieldLabel: { fontSize: 12, fontWeight: "600", color: APP_TEXT_3, textTransform: "uppercase", letterSpacing: 0.7 },
  inputWrap: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: APP_BG,
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: APP_BORDER,
  },
  input: { flex: 1, fontSize: 14, color: APP_TEXT, padding: 0 },
  sendBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    backgroundColor: APP_GREEN,
    paddingVertical: 13,
    borderRadius: 13,
  },
  sendBtnText: { fontSize: 14, fontWeight: "700", color: "#fff" },

  // ── Filter bar ────────────────────────────────────────────────────────────
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
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 20,
    backgroundColor: APP_BG,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: APP_BORDER,
  },
  chipActive: { backgroundColor: APP_GREEN_XLIGHT, borderColor: APP_GREEN_LIGHT },
  chipText: { fontSize: 13, fontWeight: "500", color: APP_TEXT_2 },
  chipTextActive: { color: APP_GREEN, fontWeight: "700" },
  unreadSummary: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    backgroundColor: APP_GREEN_XLIGHT,
    borderRadius: 12,
  },
  unreadSummaryText: { fontSize: 11, fontWeight: "700", color: APP_GREEN },

  // ── List ──────────────────────────────────────────────────────────────────
  scroll: { flex: 1, backgroundColor: APP_BG },
  container: { padding: 16, gap: 8, paddingBottom: 48 },

  // ── Conversation card ─────────────────────────────────────────────────────
  convCard: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    backgroundColor: APP_SURFACE,
    borderRadius: 16,
    padding: 14,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: APP_BORDER,
  },
  avatarWrap: { position: "relative", flexShrink: 0 },
  unreadDot: {
    position: "absolute",
    top: 0,
    right: 0,
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: APP_GREEN,
    borderWidth: 2,
    borderColor: APP_SURFACE,
  },
  convContent: { flex: 1, gap: 2 },
  convTopRow: { flexDirection: "row", alignItems: "baseline", gap: 6 },
  convName: { flex: 1, fontSize: 14, fontWeight: "600", color: APP_TEXT },
  convNameUnread: { fontWeight: "800" },
  convDate: { fontSize: 11, color: APP_TEXT_3, flexShrink: 0 },
  convSubject: { fontSize: 13, fontWeight: "600", color: APP_TEXT_2 },
  convPreview: { fontSize: 13, color: APP_TEXT_3, lineHeight: 18 },
  convPreviewUnread: { color: APP_TEXT_2 },
  convFooter: { flexDirection: "row", alignItems: "center", gap: 6, marginTop: 4 },
  typeLabel: { fontSize: 11, fontWeight: "600", color: APP_GREEN },
  typeLabelSupport: { color: APP_TEXT_3 },
  unreadBadge: {
    backgroundColor: APP_GREEN,
    borderRadius: 10,
    minWidth: 18,
    height: 18,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 5,
  },
  unreadBadgeText: { fontSize: 10, fontWeight: "800", color: "#fff" },

  // ── Empty state ───────────────────────────────────────────────────────────
  emptyWrap: { alignItems: "center", paddingVertical: 60, gap: 8 },
  emptyTitle: { fontSize: 16, fontWeight: "700", color: APP_TEXT, marginTop: 4 },
  emptyMsg: { fontSize: 14, color: APP_TEXT_3, textAlign: "center", paddingHorizontal: 40, lineHeight: 20 },
  emptyAction: {
    flexDirection: "row",
    alignItems: "center",
    gap: 7,
    marginTop: 8,
    paddingHorizontal: 20,
    paddingVertical: 11,
    backgroundColor: APP_GREEN,
    borderRadius: 12,
  },
  emptyActionText: { color: "#fff", fontWeight: "700", fontSize: 13 },
});
