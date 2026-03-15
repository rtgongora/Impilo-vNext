/**
 * FollowUpScreen — Scheduled follow-up visits and outreach calendar.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
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

  return (
    <Screen>
      <Header title="Follow-Up Schedule" />
      <ScrollView testID="follow-up-screen" style={styles.container}>
        {loading ? (
          <LoadingSpinner size="md" />
        ) : error ? (
          <ErrorState title="Error" message={error} onRetry={loadSchedule} />
        ) : schedule.length === 0 ? (
          <EmptyState title="No follow-ups scheduled" message="All households are up to date" />
        ) : (
          schedule.map(({ household, daysOverdue }) => (
            <Card key={household.id}>
              <CardBody>
                <View testID={`followup-${household.id}`} style={styles.itemRow}>
                  <View style={styles.itemDetails}>
                    <Text style={styles.boldText}>{household.headOfHousehold}</Text>
                    <Text style={styles.addressText}>{household.address}</Text>
                    <View style={styles.badgeRow}>
                      {daysOverdue > 0 ? (
                        <Badge variant="destructive">{`${daysOverdue} days overdue`}</Badge>
                      ) : (
                        <Badge variant="secondary">{`Due in ${Math.abs(daysOverdue)} days`}</Badge>
                      )}
                      <Badge
                        variant={household.riskCategory === "HIGH" ? "destructive" : "outline"}
                      >
                        {household.riskCategory}
                      </Badge>
                    </View>
                  </View>
                  <Button
                    title="Start Visit"
                    variant="primary"
                    size="sm"
                    onPress={() => {}}
                    testID={`start-followup-${household.id}`}
                  />
                </View>
              </CardBody>
            </Card>
          ))
        )}
        <View style={styles.refreshContainer}>
          <Button title="Refresh" variant="outline" onPress={loadSchedule} testID="refresh-schedule" />
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  itemRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  itemDetails: {
    flex: 1,
  },
  boldText: {
    fontWeight: "700",
  },
  addressText: {
    fontSize: 14,
    color: "#6B7280",
  },
  badgeRow: {
    flexDirection: "row",
    gap: 4,
    marginTop: 4,
  },
  refreshContainer: {
    marginTop: 16,
  },
});
