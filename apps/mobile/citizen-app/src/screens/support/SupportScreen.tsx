import React, { useState, useEffect, useCallback, useRef } from "react";
import {
  View, Text, ScrollView, Pressable, StyleSheet,
  RefreshControl, Linking,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner, TextField, Select } from "@impilo/mobile-design-system";
import { fetchTickets, createTicket, fetchArticles, type SupportTicket, type KnowledgeArticle } from "../../services/supportService";
import { USE_SEED_DATA } from "../../data/seedHealthRecords";
import { useToast } from "../../components/Toast";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT, APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

const SEED_TICKETS: SupportTicket[] = [
  { id: "tkt-001", category: "ACCOUNT",       subject: "Cannot access Health ID QR code",     description: "When I tap on Health ID, the QR code does not display.", priority: "HIGH",   status: "IN_PROGRESS", createdAt: new Date(Date.now() - 2  * 86_400_000).toISOString(), updatedAt: new Date(Date.now() - 1  * 86_400_000).toISOString() },
  { id: "tkt-002", category: "RECORDS",        subject: "Missing lab result from PAGH",        description: "My HbA1c result from 3 months ago is not showing in my Records section.", priority: "MEDIUM", status: "RESOLVED",   resolution: "Lab result has been manually linked to your profile. Please refresh the app.", createdAt: new Date(Date.now() - 14 * 86_400_000).toISOString(), updatedAt: new Date(Date.now() - 10 * 86_400_000).toISOString() },
];

const SEED_ARTICLES: KnowledgeArticle[] = [
  { id: "faq-001", title: "How do I book an appointment?",             body: "Tap 'Health' → 'Appointments' → 'Book'. Select your facility, appointment type, and preferred date. Your care team will confirm within 24 hours.",          category: "Appointments", tags: ["booking", "appointment"], publishedAt: new Date(Date.now() - 30 * 86_400_000).toISOString() },
  { id: "faq-002", title: "Why can't I see my lab results?",           body: "Results appear after your care team reviews them, usually within 1–3 business days of collection. If a result is missing after 5 days, please contact support.",  category: "Records",      tags: ["labs", "results"],       publishedAt: new Date(Date.now() - 30 * 86_400_000).toISOString() },
  { id: "faq-003", title: "How do I request a prescription refill?",   body: "Go to 'Health' → 'Prescriptions', tap the medication, then tap 'Request Refill'. Your prescriber will be notified and the prescription will be prepared.",       category: "Medications",  tags: ["refill", "prescription"],publishedAt: new Date(Date.now() - 30 * 86_400_000).toISOString() },
  { id: "faq-004", title: "How do I share my records with a provider?", body: "Go to 'Health' → 'Share Records' to generate a one-time share code. Give this code to your provider. It expires in 24 hours for your security.",             category: "Sharing",      tags: ["share", "records"],      publishedAt: new Date(Date.now() - 30 * 86_400_000).toISOString() },
  { id: "faq-005", title: "How do I join a remote queue?",             body: "From the 'Services' tab, find your facility and select 'Join Queue'. You will receive push notifications when your turn approaches.",                             category: "Queue",        tags: ["queue", "facility"],     publishedAt: new Date(Date.now() - 30 * 86_400_000).toISOString() },
  { id: "faq-006", title: "What should I do if I lose my Health ID?",  body: "Go to 'Health' → 'ID Recovery' and follow the guided steps. You can recover via SMS OTP, National ID, or by visiting a registered health facility.",            category: "Account",      tags: ["recovery", "health id"], publishedAt: new Date(Date.now() - 30 * 86_400_000).toISOString() },
];

const STATUS_CFG: Record<string, { color: string; bg: string; label: string }> = {
  OPEN:        { color: APP_GREEN,  bg: APP_GREEN_LIGHT,  label: "Open"        },
  IN_PROGRESS: { color: APP_GOLD,   bg: APP_GOLD_LIGHT,   label: "In Progress" },
  PENDING:     { color: APP_GOLD,   bg: APP_GOLD_LIGHT,   label: "Pending"     },
  RESOLVED:    { color: APP_TEXT_2, bg: "#EEF2F2",        label: "Resolved"    },
  CLOSED:      { color: APP_TEXT_3, bg: "#F3F4F6",        label: "Closed"      },
};

const PRIORITY_CFG: Record<string, { color: string }> = {
  HIGH:   { color: APP_RED  },
  MEDIUM: { color: APP_GOLD },
  LOW:    { color: APP_TEXT_3 },
};

const TICKET_CATEGORIES = [
  { label: "General",         value: "GENERAL"     },
  { label: "Account",         value: "ACCOUNT"     },
  { label: "Medical Records", value: "RECORDS"     },
  { label: "Appointments",    value: "APPOINTMENTS"},
  { label: "Medications",     value: "MEDICATIONS" },
  { label: "Technical",       value: "TECHNICAL"   },
];

type Tab = "faq" | "tickets";

export function SupportScreen() {
  const toast    = useToast();
  const toastRef = useRef(toast);
  toastRef.current = toast;

  const [tab, setTab]                   = useState<Tab>("faq");
  const [tickets, setTickets]           = useState<SupportTicket[]>([]);
  const [articles, setArticles]         = useState<KnowledgeArticle[]>([]);
  const [isLoading, setIsLoading]       = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [showForm, setShowForm]         = useState(false);
  const [submitting, setSubmitting]     = useState(false);
  const [expandedArticle, setExpandedArticle] = useState<string | null>(null);
  const [expandedTicket, setExpandedTicket]   = useState<string | null>(null);

  const [category, setCategory]   = useState("GENERAL");
  const [subject, setSubject]     = useState("");
  const [description, setDescription] = useState("");

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    try {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 350));
        setTickets(SEED_TICKETS);
        setArticles(SEED_ARTICLES);
      } else if (tab === "tickets") {
        const r = await fetchTickets({ size: 50 });
        setTickets(r.items);
      } else {
        const r = await fetchArticles({ size: 50 });
        setArticles(r.items);
      }
    } catch {
      toastRef.current.error("Could not load support content.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [tab]);

  useEffect(() => { void load(); }, [load]);
  const onRefresh = useCallback(() => { setIsRefreshing(true); void load(true); }, [load]);

  const handleCreateTicket = useCallback(async () => {
    if (!subject.trim() || !description.trim()) { toastRef.current.warning("Please fill in subject and description."); return; }
    setSubmitting(true);
    try {
      if (!USE_SEED_DATA) await createTicket({ category, subject, description });
      else await new Promise((r) => setTimeout(r, 600));
      setShowForm(false); setSubject(""); setDescription(""); setCategory("GENERAL");
      toastRef.current.success("Support ticket submitted. We'll respond within 24 hours.");
      void load(true);
    } catch {
      toastRef.current.error("Could not submit ticket. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }, [category, subject, description, load]);

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  return (
    <View style={s.root}>
      {/* Tab bar */}
      <View style={s.tabBar}>
        {(["faq", "tickets"] as Tab[]).map((t) => (
          <Pressable key={t} onPress={() => setTab(t)} style={[s.tab, tab === t && s.tabActive]}>
            <Ionicons name={t === "faq" ? "help-circle-outline" : "receipt-outline"} size={14} color={tab === t ? APP_GREEN : APP_TEXT_2} />
            <Text style={[s.tabText, tab === t && s.tabTextActive]}>
              {t === "faq" ? "FAQ & Help" : `My Tickets${tickets.length > 0 ? ` (${tickets.length})` : ""}`}
            </Text>
          </Pressable>
        ))}
      </View>

      <ScrollView
        style={s.scroll}
        contentContainerStyle={s.content}
        showsVerticalScrollIndicator={false}
        refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} tintColor={APP_GREEN} colors={[APP_GREEN]} />}
      >
        {/* ── FAQ tab ─────────────────────────────────────────────────────── */}
        {tab === "faq" ? (
          <>
            {/* Contact options */}
            <View style={s.contactRow}>
              <Pressable onPress={() => Linking.openURL("tel:0800467456")} style={s.contactCard}>
                <View style={[s.contactIcon, { backgroundColor: APP_GREEN_LIGHT }]}>
                  <Ionicons name="call-outline" size={20} color={APP_GREEN} />
                </View>
                <Text style={s.contactLabel}>Call us</Text>
                <Text style={s.contactSub}>0800 IMPILO</Text>
              </Pressable>
              <Pressable onPress={() => Linking.openURL("mailto:support@impilo.gov.zw")} style={s.contactCard}>
                <View style={[s.contactIcon, { backgroundColor: APP_GOLD_LIGHT }]}>
                  <Ionicons name="mail-outline" size={20} color={APP_GOLD} />
                </View>
                <Text style={s.contactLabel}>Email us</Text>
                <Text style={s.contactSub}>support@impilo.gov.zw</Text>
              </Pressable>
              <Pressable onPress={() => setTab("tickets")} style={s.contactCard}>
                <View style={[s.contactIcon, { backgroundColor: APP_GREEN_XLIGHT }]}>
                  <Ionicons name="create-outline" size={20} color={APP_GREEN_DARK} />
                </View>
                <Text style={s.contactLabel}>Ticket</Text>
                <Text style={s.contactSub}>Raise issue</Text>
              </Pressable>
            </View>

            <Text style={s.sectionLabel}>Frequently Asked Questions</Text>
            {articles.map((art) => {
              const isOpen = expandedArticle === art.id;
              return (
                <Pressable
                  key={art.id}
                  onPress={() => setExpandedArticle(isOpen ? null : art.id)}
                  style={s.faqCard}
                >
                  <View style={s.faqHeader}>
                    <View style={[s.faqIcon, { backgroundColor: APP_GREEN_XLIGHT }]}>
                      <Ionicons name="help-circle-outline" size={16} color={APP_GREEN} />
                    </View>
                    <Text style={s.faqQuestion} numberOfLines={isOpen ? undefined : 2}>{art.title}</Text>
                    <Ionicons name={isOpen ? "chevron-up" : "chevron-down"} size={15} color={APP_TEXT_3} />
                  </View>
                  {isOpen ? (
                    <View style={s.faqBody}>
                      <Text style={s.faqAnswer}>{art.body}</Text>
                      <View style={s.faqTags}>
                        {art.tags.map((tag) => (
                          <View key={tag} style={s.faqTag}><Text style={s.faqTagText}>{tag}</Text></View>
                        ))}
                      </View>
                    </View>
                  ) : null}
                </Pressable>
              );
            })}
          </>
        ) : null}

        {/* ── Tickets tab ──────────────────────────────────────────────────── */}
        {tab === "tickets" ? (
          <>
            <Pressable onPress={() => setShowForm(!showForm)} style={[s.newTicketBtn, showForm && s.newTicketBtnActive]}>
              <Ionicons name={showForm ? "close" : "add"} size={16} color={showForm ? APP_TEXT_2 : "#FFFFFF"} />
              <Text style={[s.newTicketBtnText, showForm && { color: APP_TEXT_2 }]}>
                {showForm ? "Cancel" : "Raise a Support Ticket"}
              </Text>
            </Pressable>

            {showForm ? (
              <View style={s.formCard}>
                <Text style={s.formTitle}>New Support Ticket</Text>
                <Select label="Category" value={category} onChange={setCategory} options={TICKET_CATEGORIES} />
                <TextField label="Subject" value={subject} onChange={setSubject} placeholder="Brief description of the issue" />
                <TextField label="Description" value={description} onChange={setDescription} placeholder="Provide as much detail as possible…" />
                <Pressable onPress={handleCreateTicket} disabled={submitting} style={[s.submitBtn, submitting && s.submitBtnDisabled]}>
                  {submitting ? <LoadingSpinner size="sm" /> : (
                    <>
                      <Ionicons name="send-outline" size={15} color="#FFFFFF" />
                      <Text style={s.submitBtnText}>Submit Ticket</Text>
                    </>
                  )}
                </Pressable>
              </View>
            ) : null}

            {tickets.length === 0 ? (
              <View style={s.empty}>
                <Ionicons name="receipt-outline" size={52} color={APP_GREEN_LIGHT} />
                <Text style={s.emptyTitle}>No support tickets</Text>
                <Text style={s.emptySub}>Raise a ticket and we'll get back to you within 24 hours.</Text>
              </View>
            ) : (
              tickets.map((ticket) => {
                const isOpen = expandedTicket === ticket.id;
                const cfg    = STATUS_CFG[ticket.status] ?? STATUS_CFG.OPEN;
                const pri    = PRIORITY_CFG[ticket.priority] ?? PRIORITY_CFG.LOW;
                return (
                  <Pressable
                    key={ticket.id}
                    onPress={() => setExpandedTicket(isOpen ? null : ticket.id)}
                    style={[s.ticketCard, { borderLeftColor: cfg.color }]}
                  >
                    <View style={s.ticketTop}>
                      <View style={s.ticketMain}>
                        <Text style={s.ticketSubject}>{ticket.subject}</Text>
                        <Text style={s.ticketCategory}>{ticket.category}</Text>
                        <Text style={s.ticketDate}>{new Date(ticket.createdAt).toLocaleDateString()}</Text>
                      </View>
                      <View style={s.ticketRight}>
                        <View style={[s.statusChip, { backgroundColor: cfg.bg }]}>
                          <Text style={[s.statusText, { color: cfg.color }]}>{cfg.label}</Text>
                        </View>
                        <View style={s.priorityRow}>
                          <View style={[s.priorityDot, { backgroundColor: pri.color }]} />
                          <Text style={[s.priorityText, { color: pri.color }]}>{ticket.priority}</Text>
                        </View>
                        <Ionicons name={isOpen ? "chevron-up" : "chevron-down"} size={14} color={APP_TEXT_3} style={{ marginTop: 4 }} />
                      </View>
                    </View>
                    {isOpen ? (
                      <View style={s.ticketExpanded}>
                        <Text style={s.ticketDesc}>{ticket.description}</Text>
                        {ticket.resolution ? (
                          <View style={s.resolutionBox}>
                            <Ionicons name="checkmark-circle-outline" size={14} color={APP_GREEN} />
                            <View>
                              <Text style={s.resolutionLabel}>Resolution</Text>
                              <Text style={s.resolutionText}>{ticket.resolution}</Text>
                            </View>
                          </View>
                        ) : null}
                      </View>
                    ) : null}
                  </Pressable>
                );
              })
            )}
          </>
        ) : null}
      </ScrollView>
    </View>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  centred: { flex: 1, alignItems: "center", justifyContent: "center" },
  tabBar: { flexDirection: "row", backgroundColor: APP_SURFACE, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: APP_BORDER },
  tab: { flex: 1, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 6, paddingVertical: 11 },
  tabActive: { borderBottomWidth: 2, borderBottomColor: APP_GREEN },
  tabText: { fontSize: 13, fontWeight: "500", color: APP_TEXT_2 },
  tabTextActive: { color: APP_GREEN, fontWeight: "700" },
  scroll: { flex: 1 },
  content: { padding: 16, gap: 12, paddingBottom: 40 },
  contactRow: { flexDirection: "row", gap: 10 },
  contactCard: { flex: 1, backgroundColor: APP_SURFACE, borderRadius: 14, padding: 12, alignItems: "center", gap: 6, shadowColor: "#000", shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.05, shadowRadius: 4, elevation: 2 },
  contactIcon: { width: 40, height: 40, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  contactLabel: { fontSize: 12, fontWeight: "700", color: APP_TEXT },
  contactSub: { fontSize: 10, color: APP_TEXT_3, textAlign: "center" },
  sectionLabel: { fontSize: 12, fontWeight: "700", color: APP_TEXT_2, textTransform: "uppercase", letterSpacing: 0.8, paddingHorizontal: 4 },
  faqCard: { backgroundColor: APP_SURFACE, borderRadius: 16, overflow: "hidden", shadowColor: "#000", shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.05, shadowRadius: 4, elevation: 2 },
  faqHeader: { flexDirection: "row", alignItems: "center", gap: 12, padding: 14 },
  faqIcon: { width: 34, height: 34, borderRadius: 10, alignItems: "center", justifyContent: "center" },
  faqQuestion: { flex: 1, fontSize: 14, fontWeight: "600", color: APP_TEXT },
  faqBody: { paddingHorizontal: 14, paddingBottom: 14, gap: 10, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER },
  faqAnswer: { fontSize: 13, color: APP_TEXT_2, lineHeight: 20 },
  faqTags: { flexDirection: "row", gap: 6, flexWrap: "wrap" },
  faqTag: { backgroundColor: APP_GREEN_XLIGHT, paddingHorizontal: 8, paddingVertical: 3, borderRadius: 8 },
  faqTagText: { fontSize: 10, fontWeight: "600", color: APP_GREEN_DARK },
  newTicketBtn: {
    flexDirection: "row", alignItems: "center", justifyContent: "center",
    gap: 8, backgroundColor: APP_GREEN, paddingVertical: 13, borderRadius: 16,
  },
  newTicketBtnActive: { backgroundColor: "#EEF2F2" },
  newTicketBtnText: { fontSize: 14, fontWeight: "700", color: "#FFFFFF" },
  formCard: { backgroundColor: APP_SURFACE, borderRadius: 18, padding: 16, gap: 12, shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 8, elevation: 3 },
  formTitle: { fontSize: 16, fontWeight: "700", color: APP_TEXT },
  submitBtn: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, backgroundColor: APP_GREEN, paddingVertical: 13, borderRadius: 12 },
  submitBtnDisabled: { opacity: 0.5 },
  submitBtnText: { fontSize: 14, fontWeight: "700", color: "#FFFFFF" },
  ticketCard: { backgroundColor: APP_SURFACE, borderRadius: 16, borderLeftWidth: 4, overflow: "hidden", shadowColor: "#000", shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.05, shadowRadius: 4, elevation: 2 },
  ticketTop: { flexDirection: "row", alignItems: "flex-start", gap: 12, padding: 14 },
  ticketMain: { flex: 1 },
  ticketSubject: { fontSize: 14, fontWeight: "700", color: APP_TEXT, marginBottom: 3 },
  ticketCategory: { fontSize: 12, color: APP_TEXT_2 },
  ticketDate: { fontSize: 11, color: APP_TEXT_3, marginTop: 3 },
  ticketRight: { alignItems: "flex-end", gap: 5 },
  statusChip: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 10 },
  statusText: { fontSize: 11, fontWeight: "700" },
  priorityRow: { flexDirection: "row", alignItems: "center", gap: 4 },
  priorityDot: { width: 6, height: 6, borderRadius: 3 },
  priorityText: { fontSize: 10, fontWeight: "600" },
  ticketExpanded: { paddingHorizontal: 14, paddingBottom: 14, gap: 10, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER },
  ticketDesc: { fontSize: 13, color: APP_TEXT_2, lineHeight: 19 },
  resolutionBox: { flexDirection: "row", gap: 10, backgroundColor: APP_GREEN_XLIGHT, borderRadius: 12, padding: 12 },
  resolutionLabel: { fontSize: 11, fontWeight: "700", color: APP_GREEN_DARK, marginBottom: 3 },
  resolutionText: { fontSize: 13, color: APP_TEXT_2, lineHeight: 18 },
  empty: { alignItems: "center", paddingVertical: 60, gap: 12 },
  emptyTitle: { fontSize: 17, fontWeight: "700", color: APP_TEXT },
  emptySub: { fontSize: 13, color: APP_TEXT_3, textAlign: "center", paddingHorizontal: 32, lineHeight: 19 },
});
