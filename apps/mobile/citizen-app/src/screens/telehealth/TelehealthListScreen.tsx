/**
 * TelehealthListScreen — List teleconsult sessions and request new ones.
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  Screen,
  Header,
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
import { fetchSessions, requestTeleconsult } from "../../services/telehealthService";
import type { TelehealthSession } from "../../types";
import { TelehealthSessionScreen } from "./TelehealthSessionScreen";

const STATUS_COLORS: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  REQUESTED: "outline",
  SCHEDULED: "default",
  IN_PROGRESS: "secondary",
  COMPLETED: "secondary",
  CANCELLED: "destructive",
};

export function TelehealthListScreen() {
  const [sessions, setSessions] = useState<TelehealthSession[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [showRequest, setShowRequest] = useState(false);
  const [reason, setReason] = useState("");
  const [preferredDate, setPreferredDate] = useState("");
  const [sessionType, setSessionType] = useState("VIDEO");
  const [providerId, setProviderId] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [activeSession, setActiveSession] = useState<TelehealthSession | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchSessions({ size: 50 });
      setSessions(result.items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleRequest = useCallback(async () => {
    setSubmitting(true);
    try {
      await requestTeleconsult({
        reason,
        preferredDate: preferredDate || undefined,
        sessionType,
        providerId: providerId || undefined,
      });
      setShowRequest(false);
      setReason("");
      setPreferredDate("");
      setProviderId("");
      load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setSubmitting(false);
    }
  }, [reason, preferredDate, sessionType, providerId, load]);

  // Active session view
  if (activeSession) {
    return React.createElement(TelehealthSessionScreen, {
      session: activeSession,
      onBack: () => {
        setActiveSession(null);
        load();
      },
    });
  }

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, { title: "Telehealth" }),
    React.createElement(
      "div",
      { "data-testid": "telehealth-list-screen", style: { padding: "16px", display: "flex", flexDirection: "column", gap: "16px" } },

      React.createElement(
        "div",
        { style: { display: "flex", justifyContent: "space-between", alignItems: "center" } },
        React.createElement("h3", { style: { margin: 0 } }, "Teleconsultations"),
        React.createElement(Button, {
          title: showRequest ? "Cancel" : "Request Teleconsult",
          variant: showRequest ? "ghost" : "primary",
          size: "sm",
          onPress: () => setShowRequest(!showRequest),
          testID: "toggle-telehealth-request",
        })
      ),

      // Request form
      showRequest
        ? React.createElement(
            Card,
            null,
            React.createElement(CardHeader, { title: "Request Teleconsultation" }),
            React.createElement(
              CardBody,
              null,
              React.createElement(
                "div",
                { style: { display: "flex", flexDirection: "column", gap: "12px" } },
                React.createElement(TextField, {
                  label: "Reason for Consultation",
                  value: reason,
                  onChange: setReason,
                  placeholder: "Describe your concern",
                  testID: "telehealth-reason",
                }),
                React.createElement(Select, {
                  label: "Session Type",
                  value: sessionType,
                  onChange: setSessionType,
                  options: [
                    { label: "Video Call", value: "VIDEO" },
                    { label: "Audio Call", value: "AUDIO" },
                    { label: "Chat", value: "CHAT" },
                  ],
                  testID: "telehealth-type",
                }),
                React.createElement(TextField, {
                  label: "Preferred Date (optional)",
                  value: preferredDate,
                  onChange: setPreferredDate,
                  placeholder: "YYYY-MM-DD",
                  testID: "telehealth-date",
                }),
                React.createElement(TextField, {
                  label: "Preferred Provider ID (optional)",
                  value: providerId,
                  onChange: setProviderId,
                  placeholder: "Leave blank for next available",
                  testID: "telehealth-provider",
                }),
                React.createElement(Button, {
                  title: submitting ? "Submitting..." : "Submit Request",
                  variant: "primary",
                  onPress: handleRequest,
                  disabled: submitting || !reason,
                  testID: "submit-telehealth-request",
                })
              )
            )
          )
        : null,

      error
        ? React.createElement(ErrorState, { title: "Error", message: error.message, onRetry: load })
        : null,

      // Sessions list
      isLoading
        ? React.createElement(LoadingSpinner, { size: "md" })
        : sessions.length === 0
          ? React.createElement(EmptyState, {
              title: "No teleconsultations",
              message: "Request your first teleconsultation using the button above",
            })
          : sessions.map((session) =>
              React.createElement(
                Card,
                { key: session.id },
                React.createElement(
                  CardBody,
                  null,
                  React.createElement(
                    "div",
                    {
                      "data-testid": `telehealth-session-${session.id}`,
                      style: { display: "flex", justifyContent: "space-between", alignItems: "flex-start" },
                    },
                    React.createElement(
                      "div",
                      null,
                      React.createElement(
                        "div",
                        { style: { display: "flex", gap: "8px", alignItems: "center", marginBottom: "4px" } },
                        React.createElement("strong", null, session.sessionType),
                        React.createElement(Badge, {
                          variant: STATUS_COLORS[session.status] ?? "outline",
                          children: session.status,
                        })
                      ),
                      React.createElement("p", { style: { fontSize: "14px", color: "#374151", margin: "2px 0" } },
                        `Dr. ${session.providerName}`
                      ),
                      React.createElement("p", { style: { fontSize: "13px", color: "#6B7280", margin: "2px 0" } },
                        `Scheduled: ${new Date(session.scheduledAt).toLocaleString()}`
                      ),
                      session.notes
                        ? React.createElement("p", { style: { fontSize: "13px", color: "#9CA3AF", margin: "2px 0" } }, session.notes)
                        : null
                    ),
                    session.status === "SCHEDULED" || session.status === "IN_PROGRESS"
                      ? React.createElement(Button, {
                          title: session.status === "IN_PROGRESS" ? "Rejoin" : "Join",
                          variant: "primary",
                          size: "sm",
                          onPress: () => setActiveSession(session),
                          testID: `join-session-${session.id}`,
                        })
                      : null
                  )
                )
              )
            )
    )
  );
}
