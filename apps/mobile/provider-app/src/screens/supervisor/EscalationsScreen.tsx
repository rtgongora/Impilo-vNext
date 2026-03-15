/**
 * EscalationsScreen — Escalation/approval queue and support ticket linkage.
 */

import React, { useState, useEffect, useCallback } from "react";
import { Screen, Header, Card, CardBody, Button, Badge, TextField, LoadingSpinner, EmptyState, ErrorState } from "@impilo/mobile-design-system";
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

  useEffect(() => { load(); }, [load]);

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

  return React.createElement(
    Screen, null,
    React.createElement(Header, { title: "Escalations & Support" }),
    React.createElement("div", { "data-testid": "escalations-screen", style: { padding: "16px" } },
      React.createElement("div", { style: { display: "flex", gap: "8px", marginBottom: "16px" } },
        React.createElement(Button, { title: "Escalations", variant: view === "escalations" ? "primary" : "outline", onPress: () => setView("escalations"), testID: "view-escalations" }),
        React.createElement(Button, { title: "Support Tickets", variant: view === "tickets" ? "primary" : "outline", onPress: () => setView("tickets"), testID: "view-tickets" })
      ),

      loading
        ? React.createElement(LoadingSpinner, { size: "md" })
        : error
          ? React.createElement(ErrorState, { title: "Error", message: error, onRetry: load })
          : view === "escalations"
            ? escalations.length === 0
              ? React.createElement(EmptyState, { title: "No escalations", message: "All clear" })
              : escalations.map((esc) =>
                  React.createElement(Card, { key: esc.id },
                    React.createElement(CardBody, null,
                      React.createElement("div", { "data-testid": `escalation-${esc.id}` },
                        React.createElement("div", { style: { display: "flex", justifyContent: "space-between" } },
                          React.createElement("strong", null, esc.title),
                          React.createElement(Badge, { variant: SEVERITY_COLORS[esc.severity] as "destructive" | "secondary" | "outline", children: esc.severity })
                        ),
                        React.createElement("p", { style: { fontSize: "14px", color: "#6B7280" } }, esc.description),
                        React.createElement("div", { style: { display: "flex", gap: "4px", marginTop: "4px" } },
                          React.createElement(Badge, { variant: "outline", children: esc.type }),
                          React.createElement(Badge, { variant: "secondary", children: esc.status }),
                          React.createElement("span", { style: { fontSize: "12px", color: "#9CA3AF" } }, `By: ${esc.raisedByName}`)
                        ),
                        esc.status === "PENDING"
                          ? React.createElement("div", { style: { display: "flex", gap: "8px", marginTop: "8px" } },
                              React.createElement(Button, { title: "Acknowledge", variant: "secondary", size: "sm", onPress: () => handleAcknowledge(esc.id), testID: `ack-${esc.id}` }),
                              React.createElement(Button, { title: "Resolve", variant: "primary", size: "sm", onPress: () => handleResolve(esc.id), testID: `resolve-${esc.id}` })
                            )
                          : null
                      )
                    )
                  )
                )
            : React.createElement(React.Fragment, null,
                React.createElement("div", { style: { marginBottom: "12px" } },
                  React.createElement(Button, { title: "New Ticket", variant: "primary", onPress: () => setShowNewTicket(true), testID: "new-ticket-btn" })
                ),
                showNewTicket
                  ? React.createElement(Card, null,
                      React.createElement(CardBody, null,
                        React.createElement(TextField, { label: "Title", value: ticketForm.title, onChange: (v: string) => setTicketForm((f) => ({ ...f, title: v })), testID: "ticket-title" }),
                        React.createElement(TextField, { label: "Description", value: ticketForm.description, onChange: (v: string) => setTicketForm((f) => ({ ...f, description: v })), testID: "ticket-desc" }),
                        React.createElement("div", { style: { display: "flex", gap: "8px", marginTop: "12px" } },
                          React.createElement(Button, { title: "Submit", onPress: handleCreateTicket, loading: saving, testID: "submit-ticket" }),
                          React.createElement(Button, { title: "Cancel", variant: "ghost", onPress: () => setShowNewTicket(false) })
                        )
                      )
                    )
                  : null,
                tickets.length === 0
                  ? React.createElement(EmptyState, { title: "No tickets", message: "Create a support ticket" })
                  : tickets.map((t) =>
                      React.createElement(Card, { key: t.id },
                        React.createElement(CardBody, null,
                          React.createElement("div", { "data-testid": `ticket-${t.id}` },
                            React.createElement("strong", null, t.title),
                            React.createElement("p", { style: { fontSize: "14px", color: "#6B7280" } }, t.description),
                            React.createElement("div", { style: { display: "flex", gap: "4px", marginTop: "4px" } },
                              React.createElement(Badge, { variant: "secondary", children: t.status }),
                              React.createElement(Badge, { variant: "outline", children: t.priority }),
                              React.createElement(Badge, { variant: "outline", children: t.category })
                            )
                          )
                        )
                      )
                    )
              ),

      React.createElement("div", { style: { marginTop: "16px" } },
        React.createElement(Button, { title: "Refresh", variant: "outline", onPress: load, testID: "refresh-escalations" })
      )
    )
  );
}
