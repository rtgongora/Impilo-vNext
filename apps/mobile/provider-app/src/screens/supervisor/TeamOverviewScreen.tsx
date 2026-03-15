/**
 * TeamOverviewScreen — Team members with task load and status.
 */

import React, { useState, useEffect, useCallback } from "react";
import { Screen, Header, Card, CardBody, Button, Badge, Avatar, LoadingSpinner, EmptyState, ErrorState } from "@impilo/mobile-design-system";
import { getTeamMembers } from "../../services/supportService";
import { useAppStore } from "../../stores/appStore";
import type { TeamMember } from "../../types";

export function TeamOverviewScreen() {
  const { facilityId } = useAppStore();
  const [members, setMembers] = useState<TeamMember[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!facilityId) return;
    setLoading(true);
    setError(null);
    try {
      const m = await getTeamMembers(facilityId);
      setMembers(m);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load team");
    } finally {
      setLoading(false);
    }
  }, [facilityId]);

  useEffect(() => { load(); }, [load]);

  if (loading) return React.createElement(Screen, null, React.createElement(Header, { title: "Team" }), React.createElement(LoadingSpinner, { size: "lg" }));
  if (error) return React.createElement(Screen, null, React.createElement(Header, { title: "Team" }), React.createElement(ErrorState, { title: "Error", message: error, onRetry: load }));

  return React.createElement(
    Screen, null,
    React.createElement(Header, { title: "Team Overview" }),
    React.createElement("div", { "data-testid": "team-overview", style: { padding: "16px" } },
      members.length === 0
        ? React.createElement(EmptyState, { title: "No team members", message: "Team data is not available" })
        : members.map((m) =>
            React.createElement(Card, { key: m.id },
              React.createElement(CardBody, null,
                React.createElement("div", { "data-testid": `team-member-${m.id}`, style: { display: "flex", alignItems: "center", gap: "12px" } },
                  React.createElement(Avatar, { name: m.name, size: "md" }),
                  React.createElement("div", { style: { flex: 1 } },
                    React.createElement("strong", null, m.name),
                    React.createElement("p", { style: { fontSize: "14px", color: "#6B7280" } }, m.role),
                    React.createElement("div", { style: { display: "flex", gap: "4px", marginTop: "4px" } },
                      React.createElement(Badge, {
                        variant: m.status === "ACTIVE" ? "primary" : m.status === "OFFLINE" ? "destructive" : "secondary",
                        children: m.status,
                      }),
                      React.createElement(Badge, { variant: "outline", children: `${m.activeTasks} active` }),
                      React.createElement(Badge, { variant: "outline", children: `${m.completedToday} done today` })
                    )
                  )
                )
              )
            )
          ),
      React.createElement("div", { style: { marginTop: "16px" } },
        React.createElement(Button, { title: "Refresh", variant: "outline", onPress: load, testID: "refresh-team" })
      )
    )
  );
}
