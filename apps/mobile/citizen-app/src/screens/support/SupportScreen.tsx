/**
 * SupportScreen — Help, support tickets, and knowledge articles.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet, ScrollView, Pressable } from "react-native";
import {
  Card,
  CardHeader,
  CardBody,
  Button,
  Badge,
  TextField,
  Select,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { fetchTickets, createTicket, fetchArticles } from "../../services/supportService";
import type { SupportTicket, KnowledgeArticle } from "../../services/supportService";

const STATUS_COLORS: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  OPEN: "default",
  IN_PROGRESS: "secondary",
  RESOLVED: "secondary",
  CLOSED: "outline",
  PENDING: "default",
};

type ActiveTab = "tickets" | "faq";

export function SupportScreen() {
  const [activeTab, setActiveTab] = useState<ActiveTab>("tickets");
  const [tickets, setTickets] = useState<SupportTicket[]>([]);
  const [articles, setArticles] = useState<KnowledgeArticle[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // Form state
  const [category, setCategory] = useState("GENERAL");
  const [subject, setSubject] = useState("");
  const [description, setDescription] = useState("");

  const loadTickets = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchTickets({ size: 50 });
      setTickets(result.items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  const loadArticles = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchArticles({ size: 50 });
      setArticles(result.items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (activeTab === "tickets") {
      loadTickets();
    } else {
      loadArticles();
    }
  }, [activeTab, loadTickets, loadArticles]);

  const resetForm = useCallback(() => {
    setCategory("GENERAL");
    setSubject("");
    setDescription("");
  }, []);

  const handleCreateTicket = useCallback(async () => {
    setSubmitting(true);
    try {
      await createTicket({ category, subject, description });
      setShowForm(false);
      resetForm();
      loadTickets();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setSubmitting(false);
    }
  }, [category, subject, description, loadTickets, resetForm]);

  if (error) {
    return (
      <ErrorState
        title="Error"
        message={error.message}
        onRetry={activeTab === "tickets" ? loadTickets : loadArticles}
      />
    );
  }

  return (
    <ScrollView testID="support-screen" style={styles.scrollView} showsVerticalScrollIndicator={false}>
      <View style={styles.container}>
        <Text style={styles.heading}>Help & Support</Text>

        {/* Tab bar */}
        <View style={styles.tabBar}>
          <Pressable
            testID="tab-tickets"
            onPress={() => setActiveTab("tickets")}
            style={[styles.tab, activeTab === "tickets" && styles.tabActive]}
          >
            <Text style={[styles.tabText, activeTab === "tickets" && styles.tabTextActive]}>
              My Tickets
            </Text>
          </Pressable>
          <Pressable
            testID="tab-faq"
            onPress={() => setActiveTab("faq")}
            style={[styles.tab, activeTab === "faq" && styles.tabActive]}
          >
            <Text style={[styles.tabText, activeTab === "faq" && styles.tabTextActive]}>
              FAQ / Articles
            </Text>
          </Pressable>
        </View>

        {isLoading ? (
          <LoadingSpinner size="md" />
        ) : activeTab === "tickets" ? (
          <>
            <View style={styles.headerRow}>
              <Text style={styles.subHeading}>Support Tickets</Text>
              <Button
                title={showForm ? "Cancel" : "New Ticket"}
                variant={showForm ? "ghost" : "primary"}
                size="sm"
                onPress={() => {
                  setShowForm(!showForm);
                  if (showForm) resetForm();
                }}
                testID="toggle-ticket-form"
              />
            </View>

            {showForm ? (
              <Card>
                <CardHeader title="Create Support Ticket" />
                <CardBody>
                  <View style={styles.formContainer}>
                    <Select
                      label="Category"
                      value={category}
                      onChange={setCategory}
                      options={[
                        { label: "Technical", value: "TECHNICAL" },
                        { label: "Clinical", value: "CLINICAL" },
                        { label: "Billing", value: "BILLING" },
                        { label: "General", value: "GENERAL" },
                      ]}
                      testID="ticket-category"
                    />
                    <TextField
                      label="Subject"
                      value={subject}
                      onChange={setSubject}
                      placeholder="Brief subject line"
                      testID="ticket-subject"
                    />
                    <TextField
                      label="Description"
                      value={description}
                      onChange={setDescription}
                      placeholder="Describe your issue in detail"
                      testID="ticket-description"
                    />
                    <Button
                      title={submitting ? "Submitting..." : "Submit Ticket"}
                      variant="primary"
                      onPress={handleCreateTicket}
                      disabled={submitting || !subject || !description}
                      testID="submit-ticket"
                    />
                  </View>
                </CardBody>
              </Card>
            ) : null}

            {tickets.length === 0 ? (
              <EmptyState
                title="No tickets"
                message="Create a support ticket if you need help"
              />
            ) : (
              tickets.map((ticket) => (
                <Card key={ticket.id}>
                  <CardBody>
                    <View testID={`ticket-${ticket.id}`}>
                      <View style={styles.badgeRow}>
                        <Text style={styles.ticketSubject}>{ticket.subject}</Text>
                        <Badge variant={STATUS_COLORS[ticket.status] ?? "outline"}>
                          {ticket.status}
                        </Badge>
                      </View>
                      <Text style={styles.ticketCategory}>{ticket.category}</Text>
                      <Text style={styles.ticketDescription} numberOfLines={2}>
                        {ticket.description}
                      </Text>
                      <Text style={styles.ticketDate}>
                        {`Created: ${new Date(ticket.createdAt).toLocaleDateString()}`}
                      </Text>
                      {ticket.resolution ? (
                        <Text style={styles.resolutionText}>
                          {`Resolution: ${ticket.resolution}`}
                        </Text>
                      ) : null}
                    </View>
                  </CardBody>
                </Card>
              ))
            )}
          </>
        ) : (
          <>
            <Text style={styles.subHeading}>Knowledge Base</Text>
            {articles.length === 0 ? (
              <EmptyState
                title="No articles"
                message="Knowledge articles will appear here soon"
              />
            ) : (
              articles.map((article) => (
                <Card key={article.id}>
                  <CardBody>
                    <View testID={`article-${article.id}`}>
                      <Text style={styles.articleTitle}>{article.title}</Text>
                      <View style={styles.tagRow}>
                        <Badge variant="outline">{article.category}</Badge>
                        {article.tags.slice(0, 3).map((tag) => (
                          <Text key={tag} style={styles.tagText}>
                            {tag}
                          </Text>
                        ))}
                      </View>
                      <Text style={styles.articleBody} numberOfLines={3}>
                        {article.body}
                      </Text>
                      <Text style={styles.articleDate}>
                        {new Date(article.publishedAt).toLocaleDateString()}
                      </Text>
                    </View>
                  </CardBody>
                </Card>
              ))
            )}
          </>
        )}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
  },
  container: {
    gap: 12,
    paddingBottom: 24,
  },
  heading: {
    fontSize: 18,
    fontWeight: "600",
  },
  subHeading: {
    fontSize: 16,
    fontWeight: "600",
  },
  tabBar: {
    flexDirection: "row",
    backgroundColor: "#F3F4F6",
    borderRadius: 8,
    padding: 4,
  },
  tab: {
    flex: 1,
    paddingVertical: 8,
    alignItems: "center",
    borderRadius: 6,
  },
  tabActive: {
    backgroundColor: "#FFFFFF",
    shadowColor: "#000",
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 1,
  },
  tabText: {
    fontSize: 14,
    fontWeight: "500",
    color: "#6B7280",
  },
  tabTextActive: {
    color: "#111827",
    fontWeight: "600",
  },
  headerRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  formContainer: {
    gap: 12,
  },
  badgeRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    marginBottom: 4,
    flexWrap: "wrap",
  },
  ticketSubject: {
    fontWeight: "700",
    fontSize: 15,
  },
  ticketCategory: {
    fontSize: 13,
    color: "#6B7280",
    marginVertical: 2,
  },
  ticketDescription: {
    fontSize: 13,
    color: "#374151",
    marginVertical: 2,
  },
  ticketDate: {
    fontSize: 12,
    color: "#9CA3AF",
    marginTop: 4,
  },
  resolutionText: {
    fontSize: 13,
    color: "#10B981",
    marginTop: 4,
  },
  articleTitle: {
    fontWeight: "700",
    fontSize: 15,
    marginBottom: 4,
  },
  tagRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    marginBottom: 8,
    flexWrap: "wrap",
  },
  tagText: {
    fontSize: 12,
    color: "#6B7280",
    backgroundColor: "#F3F4F6",
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 4,
  },
  articleBody: {
    fontSize: 13,
    color: "#374151",
    lineHeight: 18,
    marginBottom: 4,
  },
  articleDate: {
    fontSize: 12,
    color: "#9CA3AF",
  },
});
