/**
 * FollowUpScreen — Scheduled follow-up visits and outreach calendar.
 */

import React, { useState, useEffect, useCallback } from "react";
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
import { getHouseholds, getVisitsForHousehold } from "../../services/householdService";
import { useAppStore } from "../../stores/appStore";
import type { Household, CommunityVisit } from "../../types";

interface ScheduleItem {
  household: Household;
  lastVisit?: CommunityVisit;
  daysOverdue: number;
}

export function FollowUpScreen() {
  const { facilityId } = useAppStore();
  const [schedule, setSchedule] = useState<ScheduleItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadSchedule = useCallback(async () => {
    if (!facilityId) return;
    setLoading(true);
    setError(null);
    try {
      const result = await getHouseholds(facilityId, 0, 100);
      const now = new Date();
      const items: ScheduleItem[] = result.households
        .filter((h) => h.nextVisitDate)
        .map((h) => {
          const nextDate = new Date(h.nextVisitDate!);
          const daysOverdue = Math.floor((now.getTime() - nextDate.getTime()) / (1000 * 60 * 60 * 24));
          return { household: h, daysOverdue };
        })
        .sort((a, b) => b.daysOverdue - a.daysOverdue);
      setSchedule(items);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load schedule");
    } finally {
      setLoading(false);
    }
  }, [facilityId]);

  useEffect(() => {
    loadSchedule();
  }, [loadSchedule]);

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, { title: "Follow-Up Schedule" }),
    React.createElement(
      "div",
      { "data-testid": "follow-up-screen", style: { padding: "16px" } },
      loading
        ? React.createElement(LoadingSpinner, { size: "md" })
        : error
          ? React.createElement(ErrorState, { title: "Error", message: error, onRetry: loadSchedule })
          : schedule.length === 0
            ? React.createElement(EmptyState, { title: "No follow-ups scheduled", message: "All households are up to date" })
            : schedule.map(({ household, daysOverdue }) =>
                React.createElement(
                  Card,
                  { key: household.id },
                  React.createElement(
                    CardBody,
                    null,
                    React.createElement(
                      "div",
                      {
                        "data-testid": `followup-${household.id}`,
                        style: { display: "flex", justifyContent: "space-between", alignItems: "center" },
                      },
                      React.createElement(
                        "div",
                        null,
                        React.createElement("strong", null, household.headOfHousehold),
                        React.createElement("p", { style: { fontSize: "14px", color: "#6B7280" } }, household.address),
                        React.createElement(
                          "div",
                          { style: { display: "flex", gap: "4px", marginTop: "4px" } },
                          daysOverdue > 0
                            ? React.createElement(Badge, { variant: "destructive", children: `${daysOverdue} days overdue` })
                            : React.createElement(Badge, { variant: "secondary", children: `Due in ${Math.abs(daysOverdue)} days` }),
                          React.createElement(Badge, {
                            variant: household.riskCategory === "HIGH" ? "destructive" : "outline",
                            children: household.riskCategory,
                          })
                        )
                      ),
                      React.createElement(Button, {
                        title: "Start Visit",
                        variant: "primary",
                        size: "sm",
                        onPress: () => {},
                        testID: `start-followup-${household.id}`,
                      })
                    )
                  )
                )
              ),
      React.createElement("div", { style: { marginTop: "16px" } },
        React.createElement(Button, { title: "Refresh", variant: "outline", onPress: loadSchedule, testID: "refresh-schedule" })
      )
    )
  );
}
