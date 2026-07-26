/**
 * VashandiAvailabilityScreen — leave and availability records for the signed-in worker.
 */
import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet, RefreshControl } from "react-native";
import { Screen, Header, Card, CardBody, CardHeader, Badge, Button, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import { fetchMyAvailability, type VashandiAvailabilityRow } from "../../services/vashandiService";
import { fetchSessionExperienceContract } from "../../services/sessionExperienceService";

const STATUS_VARIANT: Record<string, "primary" | "secondary" | "warning" | "outline"> = {
  approved: "primary",
  pending: "warning",
  rejected: "outline",
  cancelled: "secondary",
};

function formatDate(value: string): string {
  if (!value) return "—";
  return new Date(value).toLocaleDateString([], { month: "short", day: "numeric", year: "numeric" });
}

export function VashandiAvailabilityScreen() {
  const [rows, setRows] = useState<VashandiAvailabilityRow[]>([]);
  const [leaveStatus, setLeaveStatus] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    else setLoading(true);
    setError(null);
    try {
      const contract = await fetchSessionExperienceContract();
      setLeaveStatus(contract.leaveAvailabilityStatus ?? null);
      const query = contract.vashandiWorkforceProfileId
        ? { workforce_profile_id: contract.vashandiWorkforceProfileId }
        : undefined;
      setRows(await fetchMyAvailability(query));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load availability");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return (
      <Screen>
        <Header title="My Availability" subtitle="Leave and availability" />
        <LoadingSpinner size="lg" />
      </Screen>
    );
  }

  if (error) {
    return (
      <Screen>
        <Header title="My Availability" subtitle="Leave and availability" />
        <ErrorState title="Unable to load availability" message={error} onRetry={() => void load()} />
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="My Availability" subtitle="Governed leave and unavailability records" />
      <ScrollView
        testID="vashandi-availability-screen"
        style={styles.container}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void load(true)} />}
      >
        <Card>
          <CardBody>
            <Text style={styles.sectionTitle}>Current leave status</Text>
            <Badge variant="secondary">{leaveStatus ?? "not_set"}</Badge>
          </CardBody>
        </Card>

        <CardHeader>Leave records</CardHeader>
        {rows.length === 0 ? (
          <EmptyState title="No leave records" message="Approved or pending leave will appear here." />
        ) : (
          rows.map((row) => (
            <Card key={row.id}>
              <CardBody>
                <View style={styles.row}>
                  <Text style={styles.title}>{row.leaveType.replace(/_/g, " ")}</Text>
                  <Badge variant={STATUS_VARIANT[row.status] ?? "outline"}>{row.status}</Badge>
                </View>
                <Text style={styles.meta}>
                  {formatDate(row.startDate)} – {formatDate(row.endDate)}
                </Text>
              </CardBody>
            </Card>
          ))
        )}

        <View style={styles.footer}>
          <Button title="Refresh" variant="outline" onPress={() => void load(true)} testID="refresh-vashandi-availability" />
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16 },
  sectionTitle: { fontSize: 15, fontWeight: "700", color: colors.gray[900], marginBottom: 8 },
  row: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: 8 },
  title: { fontSize: 14, fontWeight: "600", color: colors.gray[900], textTransform: "capitalize", flex: 1 },
  meta: { fontSize: 13, color: colors.gray[500], marginTop: 4 },
  footer: { marginTop: 16, marginBottom: 32 },
});
