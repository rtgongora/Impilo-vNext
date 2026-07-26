/**
 * TeamOverviewScreen — Team members with task load and status.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Screen, Header, Card, CardBody, Button, Badge, Avatar, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
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

  useEffect(() => {
    load();
  }, [load]);

  if (loading) {
    return (
      <Screen>
        <Header title="Team" />
        <LoadingSpinner size="lg" />
      </Screen>
    );
  }

  if (error) {
    return (
      <Screen>
        <Header title="Team" />
        <ErrorState title="Error" message={error} onRetry={load} />
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="Team Overview" />
      <ScrollView testID="team-overview" style={styles.container}>
        {members.length === 0 ? (
          <EmptyState title="No team members" message="Team data is not available" />
        ) : (
          members.map((m) => (
            <Card key={m.id}>
              <CardBody>
                <View testID={`team-member-${m.id}`} style={styles.memberRow}>
                  <Avatar name={m.name} size="md" />
                  <View style={styles.memberDetails}>
                    <Text style={styles.boldText}>{m.name}</Text>
                    <Text style={styles.roleText}>{m.role}</Text>
                    <View style={styles.badgeRow}>
                      <Badge
                        variant={m.status === "ACTIVE" ? "primary" : m.status === "OFFLINE" ? "destructive" : "secondary"}
                      >
                        {m.status}
                      </Badge>
                      <Badge variant="outline">{`${m.activeTasks} active`}</Badge>
                      <Badge variant="outline">{`${m.completedToday} done today`}</Badge>
                    </View>
                  </View>
                </View>
              </CardBody>
            </Card>
          ))
        )}
        <View style={styles.refreshContainer}>
          <Button title="Refresh" variant="outline" onPress={load} testID="refresh-team" />
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  memberRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  memberDetails: {
    flex: 1,
  },
  boldText: {
    fontWeight: "700",
  },
  roleText: {
    fontSize: 14,
    color: colors.gray[500],
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
