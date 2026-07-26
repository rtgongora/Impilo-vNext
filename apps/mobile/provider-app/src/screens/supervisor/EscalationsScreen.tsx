/**
 * EscalationsScreen — Escalation/approval queue and support ticket linkage.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Screen, Header, Card, CardBody, Button, Badge, TextField, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import { getEscalations, acknowledgeEscalation, resolveEscalation, getSupportTickets, createSupportTicket } from "../../services/supportService";
import { useAppStore } from "../../stores/appStore";
import type { Escalation, SupportTicket } from "../../types";

export function EscalationsScreen() {
  const { facilityId } = useAppStore();
  const [view, setView] = useState<"escalations" | "tickets">("escalations");
  const [escalations, setEscalations] = useState<Escalation[]>([]);
  const [tickets, setTickets] = useState<SupportTicket[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showNewTicket, setShowNewTicket] = useState(false);
  const [ticketForm, setTicketForm] = useState({ title: "", description: "", category: "GENERAL" });
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    if (!facilityId) return;
    setLoading(true);
    setError(null);
    try {
      const [esc, tix] = await Promise.all([
        getEscalations(facilityId),
        getSupportTickets(facilityId),
      ]);
      setEscalations(esc);
      setTickets(tix);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load data");
    } finally {
      setLoading(false);
    }
  }, [facilityId]);

  useEffect(() => {
    load();
  }, [load]);

  const handleAcknowledge = useCallback(async (id: string) => {
    try { await acknowledgeEscalation(id); load(); } catch {}
  }, [load]);

  const handleResolve = useCallback(async (id: string) => {
    try { await resolveEscalation(id, "Resolved by supervisor"); load(); } catch {}
  }, [load]);

  const handleCreateTicket = useCallback(async () => {
    if (!facilityId || !ticketForm.title) return;
    setSaving(true);
    try {
      await createSupportTicket({ ...ticketForm, priority: "MEDIUM", facilityId });
      setShowNewTicket(false);
      setTicketForm({ title: "", description: "", category: "GENERAL" });
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create ticket");
    } finally {
      setSaving(false);
    }
  }, [facilityId, ticketForm, load]);

  const SEVERITY_COLORS: Record<string, string> = { CRITICAL: "destructive", HIGH: "destructive", MEDIUM: "secondary", LOW: "outline" };

  return (
    <Screen>
      <Header title="Escalations & Support" />
      <ScrollView testID="escalations-screen" style={styles.container}>
        <View style={styles.toggleRow}>
          <Button
            title="Escalations"
            variant={view === "escalations" ? "primary" : "outline"}
            onPress={() => setView("escalations")}
            testID="view-escalations"
          />
          <Button
            title="Support Tickets"
            variant={view === "tickets" ? "primary" : "outline"}
            onPress={() => setView("tickets")}
            testID="view-tickets"
          />
        </View>

        {loading ? (
          <LoadingSpinner size="md" />
        ) : error ? (
          <ErrorState title="Error" message={error} onRetry={load} />
        ) : view === "escalations" ? (
          escalations.length === 0 ? (
            <EmptyState title="No escalations" message="All clear" />
          ) : (
            escalations.map((esc) => (
              <Card key={esc.id}>
                <CardBody>
                  <View testID={`escalation-${esc.id}`}>
                    <View style={styles.itemHeader}>
                      <Text style={styles.boldText}>{esc.title}</Text>
                      <Badge
                        variant={SEVERITY_COLORS[esc.severity] as "destructive" | "secondary" | "outline"}
                      >
                        {esc.severity}
                      </Badge>
                    </View>
                    <Text style={styles.descriptionText}>{esc.description}</Text>
                    <View style={styles.badgeRow}>
                      <Badge variant="outline">{esc.type}</Badge>
                      <Badge variant="secondary">{esc.status}</Badge>
                      <Text style={styles.raisedByText}>{`By: ${esc.raisedByName}`}</Text>
                    </View>
                    {esc.status === "PENDING" && (
                      <View style={styles.actionRow}>
                        <Button
                          title="Acknowledge"
                          variant="secondary"
                          size="sm"
                          onPress={() => handleAcknowledge(esc.id)}
                          testID={`ack-${esc.id}`}
                        />
                        <Button
                          title="Resolve"
                          variant="primary"
                          size="sm"
                          onPress={() => handleResolve(esc.id)}
                          testID={`resolve-${esc.id}`}
                        />
                      </View>
                    )}
                  </View>
                </CardBody>
              </Card>
            ))
          )
        ) : (
          <>
            <View style={styles.newTicketContainer}>
              <Button
                title="New Ticket"
                variant="primary"
                onPress={() => setShowNewTicket(true)}
                testID="new-ticket-btn"
              />
            </View>
            {showNewTicket && (
              <Card>
                <CardBody>
                  <TextField
                    label="Title"
                    value={ticketForm.title}
                    onChange={(v: string) => setTicketForm((f) => ({ ...f, title: v }))}
                    testID="ticket-title"
                  />
                  <TextField
                    label="Description"
                    value={ticketForm.description}
                    onChange={(v: string) => setTicketForm((f) => ({ ...f, description: v }))}
                    testID="ticket-desc"
                  />
                  <View style={styles.formActions}>
                    <Button title="Submit" onPress={handleCreateTicket} loading={saving} testID="submit-ticket" />
                    <Button title="Cancel" variant="ghost" onPress={() => setShowNewTicket(false)} />
                  </View>
                </CardBody>
              </Card>
            )}
            {tickets.length === 0 ? (
              <EmptyState title="No tickets" message="Create a support ticket" />
            ) : (
              tickets.map((t) => (
                <Card key={t.id}>
                  <CardBody>
                    <View testID={`ticket-${t.id}`}>
                      <Text style={styles.boldText}>{t.title}</Text>
                      <Text style={styles.descriptionText}>{t.description}</Text>
                      <View style={styles.badgeRow}>
                        <Badge variant="secondary">{t.status}</Badge>
                        <Badge variant="outline">{t.priority}</Badge>
                        <Badge variant="outline">{t.category}</Badge>
                      </View>
                    </View>
                  </CardBody>
                </Card>
              ))
            )}
          </>
        )}

        <View style={styles.refreshContainer}>
          <Button title="Refresh" variant="outline" onPress={load} testID="refresh-escalations" />
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  toggleRow: {
    flexDirection: "row",
    gap: 8,
    marginBottom: 16,
  },
  itemHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
  },
  boldText: {
    fontWeight: "700",
  },
  descriptionText: {
    fontSize: 14,
    color: colors.gray[500],
  },
  badgeRow: {
    flexDirection: "row",
    gap: 4,
    marginTop: 4,
  },
  raisedByText: {
    fontSize: 12,
    color: colors.gray[400],
  },
  actionRow: {
    flexDirection: "row",
    gap: 8,
    marginTop: 8,
  },
  newTicketContainer: {
    marginBottom: 12,
  },
  formActions: {
    flexDirection: "row",
    gap: 8,
    marginTop: 12,
  },
  refreshContainer: {
    marginTop: 16,
  },
});
