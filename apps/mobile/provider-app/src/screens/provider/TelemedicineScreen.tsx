/**
 * TelemedicineScreen — Video/audio consultation participation.
 *
 * Manages telemedicine session lifecycle: join → in-progress → end.
 */

import React, { useState, useCallback, useEffect } from "react";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { apiClient } from "@impilo/mobile-api-client";
import { useChannel } from "@impilo/mobile-messaging";
import type { TelemedicineSession } from "../../types";

export function TelemedicineScreen() {
  const [sessions, setSessions] = useState<TelemedicineSession[]>([]);
  const [activeSession, setActiveSession] = useState<TelemedicineSession | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadSessions = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await apiClient.get<{
        data: {
          id: string;
          attributes: {
            encounter_id: string;
            patient_id: string;
            provider_id: string;
            status: TelemedicineSession["status"];
            scheduled_at: string;
            started_at?: string;
            ended_at?: string;
            session_token?: string;
            channel_id?: string;
          };
        }[];
      }>("/internal/v1/mobile/provider/telemedicine/sessions");
      setSessions(
        response.data.data.map((s) => ({
          id: s.id,
          encounterId: s.attributes.encounter_id,
          patientId: s.attributes.patient_id,
          providerId: s.attributes.provider_id,
          status: s.attributes.status,
          scheduledAt: s.attributes.scheduled_at,
          startedAt: s.attributes.started_at,
          endedAt: s.attributes.ended_at,
          sessionToken: s.attributes.session_token,
          channelId: s.attributes.channel_id,
        }))
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load sessions");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSessions();
  }, [loadSessions]);

  const handleJoin = useCallback(async (session: TelemedicineSession) => {
    try {
      const response = await apiClient.post<{
        data: {
          id: string;
          attributes: {
            encounter_id: string;
            patient_id: string;
            provider_id: string;
            status: TelemedicineSession["status"];
            scheduled_at: string;
            started_at: string;
            session_token: string;
            channel_id: string;
          };
        };
      }>(`/internal/v1/mobile/provider/telemedicine/sessions/${session.id}/join`);
      const s = response.data.data;
      setActiveSession({
        id: s.id,
        encounterId: s.attributes.encounter_id,
        patientId: s.attributes.patient_id,
        providerId: s.attributes.provider_id,
        status: s.attributes.status,
        scheduledAt: s.attributes.scheduled_at,
        startedAt: s.attributes.started_at,
        sessionToken: s.attributes.session_token,
        channelId: s.attributes.channel_id,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to join session");
    }
  }, []);

  const handleEnd = useCallback(async () => {
    if (!activeSession) return;
    try {
      await apiClient.post(`/internal/v1/mobile/provider/telemedicine/sessions/${activeSession.id}/end`);
      setActiveSession(null);
      loadSessions();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to end session");
    }
  }, [activeSession, loadSessions]);

  // Real-time signaling for active session
  const { status: channelStatus } = useChannel(
    activeSession?.channelId ? `telehealth:${activeSession.channelId}` : "",
    {
      onMessage: (event) => {
        if (event.type === "session_ended") {
          setActiveSession(null);
          loadSessions();
        }
      },
    }
  );

  if (activeSession) {
    return React.createElement(
      Screen,
      null,
      React.createElement(Header, { title: "Telemedicine Session" }),
      React.createElement(
        "div",
        { "data-testid": "telemedicine-active", style: { padding: "16px" } },
        React.createElement(
          Card,
          null,
          React.createElement(
            CardBody,
            null,
            React.createElement(
              "div",
              { style: { textAlign: "center", padding: "48px 0" } },
              React.createElement(Badge, { variant: "primary", children: "IN PROGRESS" }),
              React.createElement(
                "div",
                {
                  style: {
                    width: "100%",
                    height: "300px",
                    backgroundColor: "#1F2937",
                    borderRadius: "12px",
                    margin: "24px 0",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    color: "#FFFFFF",
                    fontSize: "18px",
                  },
                  "data-testid": "video-container",
                },
                "Video Stream Active"
              ),
              React.createElement(
                "div",
                { style: { display: "flex", gap: "12px", justifyContent: "center" } },
                React.createElement(Button, {
                  title: "End Session",
                  variant: "destructive",
                  onPress: handleEnd,
                  testID: "end-session-btn",
                })
              )
            )
          )
        )
      )
    );
  }

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, { title: "Telemedicine" }),
    React.createElement(
      "div",
      { "data-testid": "telemedicine-screen", style: { padding: "16px" } },
      loading
        ? React.createElement(LoadingSpinner, { size: "md" })
        : error
          ? React.createElement(ErrorState, { title: "Error", message: error, onRetry: loadSessions })
          : sessions.length === 0
            ? React.createElement(EmptyState, { title: "No scheduled sessions", message: "Telemedicine sessions will appear here" })
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
                        "data-testid": `session-${session.id}`,
                        style: { display: "flex", justifyContent: "space-between", alignItems: "center" },
                      },
                      React.createElement(
                        "div",
                        null,
                        React.createElement(Badge, { variant: "secondary", children: session.status }),
                        React.createElement(
                          "p",
                          { style: { fontSize: "14px", marginTop: "4px" } },
                          `Scheduled: ${new Date(session.scheduledAt).toLocaleString()}`
                        )
                      ),
                      session.status === "SCHEDULED" || session.status === "WAITING"
                        ? React.createElement(Button, {
                            title: "Join",
                            variant: "primary",
                            onPress: () => handleJoin(session),
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
