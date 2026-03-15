/**
 * TelehealthSessionScreen — Active teleconsult session with join/end flow.
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
  LoadingSpinner,
  ErrorState,
} from "@impilo/mobile-design-system";
import { joinSession, endSession, fetchSession } from "../../services/telehealthService";
import { useChannel } from "@impilo/mobile-messaging";
import type { TelehealthSession } from "../../types";

interface TelehealthSessionScreenProps {
  session: TelehealthSession;
  onBack: () => void;
}

export function TelehealthSessionScreen({ session: initialSession, onBack }: TelehealthSessionScreenProps) {
  const [session, setSession] = useState<TelehealthSession>(initialSession);
  const [sessionToken, setSessionToken] = useState<string | null>(null);
  const [sessionChannel, setSessionChannel] = useState<string | null>(null);
  const [isJoining, setIsJoining] = useState(false);
  const [isEnding, setIsEnding] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [endNotes, setEndNotes] = useState("");
  const [showEndForm, setShowEndForm] = useState(false);
  const [elapsed, setElapsed] = useState(0);

  // Real-time channel for session events
  const channel = useChannel(sessionChannel ?? `telehealth-${session.id}`);

  // Elapsed timer when in session
  useEffect(() => {
    if (session.status === "IN_PROGRESS" && sessionToken) {
      const interval = setInterval(() => {
        setElapsed((prev) => prev + 1);
      }, 1000);
      return () => clearInterval(interval);
    }
  }, [session.status, sessionToken]);

  const handleJoin = useCallback(async () => {
    setIsJoining(true);
    setError(null);
    try {
      const result = await joinSession(session.id);
      setSession(result.session);
      setSessionToken(result.token);
      setSessionChannel(result.channel);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsJoining(false);
    }
  }, [session.id]);

  const handleEnd = useCallback(async () => {
    setIsEnding(true);
    setError(null);
    try {
      await endSession(session.id, endNotes || undefined);
      const updated = await fetchSession(session.id);
      setSession(updated);
      setSessionToken(null);
      setShowEndForm(false);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsEnding(false);
    }
  }, [session.id, endNotes]);

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
  };

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, {
      title: "Teleconsultation",
      leftElement: React.createElement(Button, {
        title: "\u2190 Back",
        variant: "ghost",
        size: "sm",
        onPress: onBack,
        testID: "session-back",
      }),
    }),
    React.createElement(
      "div",
      { "data-testid": "telehealth-session-screen", style: { padding: "16px", display: "flex", flexDirection: "column", gap: "16px" } },

      error
        ? React.createElement(ErrorState, { title: "Error", message: error.message, onRetry: () => setError(null) })
        : null,

      // Session info
      React.createElement(
        Card,
        null,
        React.createElement(CardHeader, { title: "Session Details" }),
        React.createElement(
          CardBody,
          null,
          React.createElement(
            "div",
            { style: { display: "flex", flexDirection: "column", gap: "8px" } },
            React.createElement(
              "div",
              { style: { display: "flex", gap: "8px", alignItems: "center" } },
              React.createElement("strong", null, session.sessionType),
              React.createElement(Badge, {
                variant: session.status === "IN_PROGRESS" ? "default" : session.status === "COMPLETED" ? "secondary" : "outline",
                children: session.status,
              })
            ),
            React.createElement("p", { style: { fontSize: "14px", margin: 0 } }, `Provider: Dr. ${session.providerName}`),
            React.createElement("p", { style: { fontSize: "14px", margin: 0, color: "#6B7280" } },
              `Scheduled: ${new Date(session.scheduledAt).toLocaleString()}`
            ),
            session.status === "IN_PROGRESS" && sessionToken
              ? React.createElement("p", { style: { fontSize: "20px", fontWeight: "700", color: "#2563EB", margin: "8px 0" } },
                  `Duration: ${formatTime(elapsed)}`
                )
              : null,
            channel.isConnected
              ? React.createElement("p", { style: { fontSize: "12px", color: "#059669", margin: 0 } }, "\u25CF Connected to session channel")
              : null
          )
        )
      ),

      // Active session area
      sessionToken
        ? React.createElement(
            Card,
            null,
            React.createElement(
              CardBody,
              null,
              React.createElement(
                "div",
                {
                  "data-testid": "active-session-area",
                  style: {
                    backgroundColor: "#111827",
                    borderRadius: "12px",
                    height: "300px",
                    display: "flex",
                    justifyContent: "center",
                    alignItems: "center",
                    marginBottom: "16px",
                  },
                },
                React.createElement("p", { style: { color: "white", fontSize: "16px" } },
                  session.sessionType === "VIDEO"
                    ? "Video session active"
                    : session.sessionType === "AUDIO"
                      ? "Audio session active"
                      : "Chat session active"
                )
              ),

              // Session controls
              React.createElement(
                "div",
                { style: { display: "flex", gap: "8px", justifyContent: "center" } },
                React.createElement(Button, {
                  title: "End Session",
                  variant: "primary",
                  onPress: () => setShowEndForm(true),
                  testID: "end-session-btn",
                })
              )
            )
          )
        : null,

      // End session form
      showEndForm
        ? React.createElement(
            Card,
            null,
            React.createElement(CardHeader, { title: "End Session" }),
            React.createElement(
              CardBody,
              null,
              React.createElement(
                "div",
                { style: { display: "flex", flexDirection: "column", gap: "12px" } },
                React.createElement(TextField, {
                  label: "Notes (optional)",
                  value: endNotes,
                  onChange: setEndNotes,
                  placeholder: "Any post-session notes",
                  testID: "end-session-notes",
                }),
                React.createElement(
                  "div",
                  { style: { display: "flex", gap: "8px" } },
                  React.createElement(Button, {
                    title: isEnding ? "Ending..." : "Confirm End",
                    variant: "primary",
                    onPress: handleEnd,
                    disabled: isEnding,
                    testID: "confirm-end-session",
                  }),
                  React.createElement(Button, {
                    title: "Continue",
                    variant: "ghost",
                    onPress: () => setShowEndForm(false),
                  })
                )
              )
            )
          )
        : null,

      // Pre-session: Join button
      !sessionToken && (session.status === "SCHEDULED" || session.status === "IN_PROGRESS")
        ? React.createElement(
            "div",
            { style: { textAlign: "center" } },
            React.createElement(Button, {
              title: isJoining ? "Joining..." : "Join Session",
              variant: "primary",
              size: "lg",
              onPress: handleJoin,
              disabled: isJoining,
              testID: "join-session",
            })
          )
        : null,

      // Post-session summary
      session.status === "COMPLETED"
        ? React.createElement(
            Card,
            null,
            React.createElement(CardHeader, { title: "Session Summary" }),
            React.createElement(
              CardBody,
              null,
              React.createElement("p", { style: { fontSize: "14px", margin: "4px 0" } },
                `Started: ${session.startedAt ? new Date(session.startedAt).toLocaleString() : "N/A"}`
              ),
              React.createElement("p", { style: { fontSize: "14px", margin: "4px 0" } },
                `Ended: ${session.endedAt ? new Date(session.endedAt).toLocaleString() : "N/A"}`
              ),
              session.notes
                ? React.createElement("p", { style: { fontSize: "14px", margin: "4px 0" } }, `Notes: ${session.notes}`)
                : null
            )
          )
        : null
    )
  );
}
