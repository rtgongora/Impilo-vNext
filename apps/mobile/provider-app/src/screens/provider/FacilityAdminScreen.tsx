/**
 * FacilityAdminScreen — Facility overview, staff roster, and audit log.
 *
 * Provides operational visibility into facility bed count, occupancy,
 * staff on duty, queue length, roster details, and recent audit entries.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Screen, Header, Card, CardHeader, CardBody, Badge, Button, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import {
  fetchFacilityStats,
  fetchStaffRoster,
  fetchAuditLog,
} from "../../services/facilityAdminService";
import type { FacilityStats, StaffMember, AuditEntry } from "../../types";

type SectionView = "overview" | "roster" | "audit";

export function FacilityAdminScreen() {
  const [section, setSection] = useState<SectionView>("overview");
  const [stats, setStats] = useState<FacilityStats | null>(null);
  const [staff, setStaff] = useState<StaffMember[]>([]);
  const [auditEntries, setAuditEntries] = useState<AuditEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      if (section === "overview") {
        const s = await fetchFacilityStats();
        setStats(s);
      } else if (section === "roster") {
        const r = await fetchStaffRoster();
        setStaff(r);
      } else {
        const a = await fetchAuditLog({ limit: 50 });
        setAuditEntries(a);
      }
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load facility data"
      );
    } finally {
      setLoading(false);
    }
  }, [section]);

  useEffect(() => {
    load();
  }, [load]);

  const SHIFT_BADGE_VARIANT: Record<string, "primary" | "secondary"> = {
    ON_DUTY: "primary",
    OFF_DUTY: "secondary",
  };

  return (
    <Screen>
      <Header title="Facility Admin" />
      <View testID="facility-admin-screen" style={styles.container}>
        {/* Section toggle */}
        <View style={styles.toggleRow}>
          <Button
            title="Overview"
            variant={section === "overview" ? "primary" : "outline"}
            onPress={() => setSection("overview")}
            testID="section-overview"
          />
          <Button
            title="Staff"
            variant={section === "roster" ? "primary" : "outline"}
            onPress={() => setSection("roster")}
            testID="section-roster"
          />
          <Button
            title="Audit"
            variant={section === "audit" ? "primary" : "outline"}
            onPress={() => setSection("audit")}
            testID="section-audit"
          />
        </View>

        {loading ? (
          <LoadingSpinner size="md" />
        ) : error ? (
          <ErrorState title="Error" message={error} onRetry={load} />
        ) : (
          <ScrollView
            style={styles.scrollArea}
            contentContainerStyle={styles.scrollContent}
          >
            {/* ── Overview ─────────────────────────────────────────── */}
            {section === "overview" && stats && (
              <>
                <Card>
                  <CardHeader title={stats.facilityName} />
                  <CardBody>
                    <View style={styles.statsGrid}>
                      <View style={styles.statItem}>
                        <Text style={styles.statValue}>{stats.bedCount}</Text>
                        <Text style={styles.statLabel}>Total Beds</Text>
                      </View>
                      <View style={styles.statItem}>
                        <Text style={styles.statValue}>
                          {Math.round(stats.occupancyRate * 100)}%
                        </Text>
                        <Text style={styles.statLabel}>Occupancy</Text>
                      </View>
                      <View style={styles.statItem}>
                        <Text style={styles.statValue}>
                          {stats.staffOnDuty}
                        </Text>
                        <Text style={styles.statLabel}>Staff on Duty</Text>
                      </View>
                      <View style={styles.statItem}>
                        <Text style={styles.statValue}>
                          {stats.queueLength}
                        </Text>
                        <Text style={styles.statLabel}>Queue Length</Text>
                      </View>
                    </View>
                  </CardBody>
                </Card>
                <Text style={styles.footerNote}>
                  Last updated:{" "}
                  {new Date(stats.updatedAt).toLocaleTimeString()}
                </Text>
              </>
            )}

            {/* ── Staff Roster ─────────────────────────────────────── */}
            {section === "roster" &&
              (staff.length === 0 ? (
                <EmptyState
                  title="No staff data"
                  message="Staff roster is empty"
                />
              ) : (
                staff.map((member) => (
                  <Card key={member.id}>
                    <CardBody>
                      <View
                        testID={`staff-${member.id}`}
                        style={styles.listRow}
                      >
                        <View style={styles.listRowInfo}>
                          <Text style={styles.boldText}>{member.name}</Text>
                          <Text style={styles.detailText}>{member.role}</Text>
                          {member.department && (
                            <Text style={styles.detailText}>
                              {member.department}
                            </Text>
                          )}
                        </View>
                        <Badge
                          variant={
                            SHIFT_BADGE_VARIANT[member.shiftStatus] ??
                            "secondary"
                          }
                        >
                          {member.shiftStatus === "ON_DUTY"
                            ? "On Duty"
                            : "Off Duty"}
                        </Badge>
                      </View>
                    </CardBody>
                  </Card>
                ))
              ))}

            {/* ── Audit Log ────────────────────────────────────────── */}
            {section === "audit" &&
              (auditEntries.length === 0 ? (
                <EmptyState
                  title="No audit entries"
                  message="No recent audit activity"
                />
              ) : (
                auditEntries.map((entry) => (
                  <Card key={entry.id}>
                    <CardBody>
                      <View
                        testID={`audit-${entry.id}`}
                        style={styles.auditRow}
                      >
                        <Text style={styles.auditTime}>
                          {new Date(entry.timestamp).toLocaleString()}
                        </Text>
                        <Text style={styles.boldText}>
                          {entry.actorName}
                        </Text>
                        <Text style={styles.detailText}>
                          {entry.action} — {entry.resource}
                          {entry.resourceId ? ` (${entry.resourceId})` : ""}
                        </Text>
                        {entry.details && (
                          <Text style={styles.detailText}>
                            {entry.details}
                          </Text>
                        )}
                      </View>
                    </CardBody>
                  </Card>
                ))
              ))}
          </ScrollView>
        )}

        <View style={styles.refreshContainer}>
          <Button
            title="Refresh"
            variant="outline"
            onPress={load}
            testID="refresh-facility"
          />
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
  },
  toggleRow: {
    flexDirection: "row",
    gap: 8,
    marginBottom: 16,
  },
  scrollArea: {
    flex: 1,
  },
  scrollContent: {
    gap: 12,
    paddingBottom: 16,
  },
  statsGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 16,
  },
  statItem: {
    width: "45%",
    alignItems: "center",
    paddingVertical: 12,
  },
  statValue: {
    fontSize: 28,
    fontWeight: "900",
    color: colors.gray[900],
  },
  statLabel: {
    fontSize: 13,
    color: colors.gray[500],
    marginTop: 4,
  },
  listRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  listRowInfo: {
    flex: 1,
    gap: 2,
  },
  boldText: {
    fontWeight: "700",
    color: colors.gray[900],
  },
  detailText: {
    fontSize: 13,
    color: colors.gray[500],
  },
  auditRow: {
    gap: 4,
  },
  auditTime: {
    fontSize: 11,
    color: colors.gray[400],
    fontWeight: "600",
  },
  footerNote: {
    fontSize: 12,
    color: colors.gray[400],
    textAlign: "center",
    marginTop: 8,
  },
  refreshContainer: {
    marginTop: 8,
  },
});
