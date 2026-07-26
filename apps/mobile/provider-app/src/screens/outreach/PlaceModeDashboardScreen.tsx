/**
 * PlaceModeDashboardScreen — Indawo public-health place operations for outreach/provider cadres.
 *
 * Summary tiles, live surveillance alerts, outbreak register (with report-outbreak action), and
 * field teams (with deploy action). Backed by the live experience-bff place-mode routes (same as
 * the web Indawo surfaces) via placeModeService. Closes part of GAP-19 (no mobile place mode).
 * Real data only — no mock rows.
 */

import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet, TextInput } from "react-native";
import { Screen, Header, Card, CardHeader, CardBody, Badge, Button, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import type { BadgeVariant } from "@impilo/mobile-design-system";
import {
  createOutbreak,
  deployFieldTeam,
  fetchFieldTeams,
  fetchOutbreaks,
  fetchPlaceModeSummary,
  fetchSurveillanceAlerts,
  type FieldTeam,
  type Outbreak,
  type PlaceModeSummary,
  type SurveillanceAlert,
} from "../../services/placeModeService";

type Section = "summary" | "alerts" | "outbreaks" | "teams";

function severityVariant(severity: string): BadgeVariant {
  switch (severity?.toUpperCase()) {
    case "CRITICAL":
    case "HIGH":
      return "destructive";
    case "MEDIUM":
      return "warning";
    case "LOW":
      return "info";
    default:
      return "secondary";
  }
}

export function PlaceModeDashboardScreen() {
  const [section, setSection] = useState<Section>("summary");
  const [summary, setSummary] = useState<PlaceModeSummary | null>(null);
  const [alerts, setAlerts] = useState<SurveillanceAlert[]>([]);
  const [outbreaks, setOutbreaks] = useState<Outbreak[]>([]);
  const [teams, setTeams] = useState<FieldTeam[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  // Report-outbreak inline form
  const [newCondition, setNewCondition] = useState("");
  const [newName, setNewName] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      if (section === "summary") setSummary(await fetchPlaceModeSummary());
      else if (section === "alerts") setAlerts(await fetchSurveillanceAlerts("OPEN"));
      else if (section === "outbreaks") setOutbreaks(await fetchOutbreaks());
      else setTeams(await fetchFieldTeams());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load place mode");
    } finally {
      setLoading(false);
    }
  }, [section]);

  useEffect(() => {
    load();
  }, [load]);

  const onReportOutbreak = useCallback(async () => {
    if (!newCondition.trim() || !newName.trim()) return;
    setBusy("create");
    try {
      await createOutbreak({ conditionCode: newCondition.trim(), name: newName.trim() });
      setNewCondition("");
      setNewName("");
      await load();
    } finally {
      setBusy(null);
    }
  }, [newCondition, newName, load]);

  const onDeploy = useCallback(
    async (teamId: string) => {
      setBusy(teamId);
      try {
        await deployFieldTeam({ teamId });
        await load();
      } finally {
        setBusy(null);
      }
    },
    [load],
  );

  return (
    <Screen>
      <Header title="Place Mode" />
      <View testID="place-mode-screen" style={styles.container}>
        <View style={styles.toggleRow}>
          {(["summary", "alerts", "outbreaks", "teams"] as Section[]).map((s) => (
            <Button
              key={s}
              title={s[0].toUpperCase() + s.slice(1)}
              variant={section === s ? "primary" : "outline"}
              onPress={() => setSection(s)}
              testID={`section-${s}`}
            />
          ))}
        </View>

        {loading ? (
          <LoadingSpinner size="md" />
        ) : error ? (
          <ErrorState title="Error" message={error} onRetry={load} />
        ) : (
          <ScrollView style={styles.scrollArea} contentContainerStyle={styles.scrollContent}>
            {section === "summary" && summary && (
              <Card>
                <CardBody>
                  <View style={styles.statsGrid}>
                    <View style={styles.statItem}>
                      <Text style={styles.statValue}>{summary.openAlerts}</Text>
                      <Text style={styles.statLabel}>Open Alerts</Text>
                    </View>
                    <View style={styles.statItem}>
                      <Text style={styles.statValue}>{summary.activeOutbreaks}</Text>
                      <Text style={styles.statLabel}>Active Outbreaks</Text>
                    </View>
                    <View style={styles.statItem}>
                      <Text style={styles.statValue}>{summary.teams}</Text>
                      <Text style={styles.statLabel}>Field Teams</Text>
                    </View>
                  </View>
                </CardBody>
              </Card>
            )}

            {section === "alerts" &&
              (alerts.length === 0 ? (
                <EmptyState title="No open alerts" message="No surveillance signals right now" />
              ) : (
                alerts.map((a) => (
                  <Card key={a.alertId}>
                    <CardBody>
                      <View testID={`alert-${a.alertId}`} style={styles.rowInfo}>
                        <View style={styles.titleRow}>
                          <Badge variant={severityVariant(a.severity)}>{a.severity}</Badge>
                          <Text style={styles.boldText}>{a.signalType}</Text>
                        </View>
                        {a.description ? <Text style={styles.detailText}>{a.description}</Text> : null}
                        <Text style={styles.detailText}>{a.caseCount} case(s)</Text>
                      </View>
                    </CardBody>
                  </Card>
                ))
              ))}

            {section === "outbreaks" && (
              <>
                <Card>
                  <CardHeader title="Report an outbreak" />
                  <CardBody>
                    <TextInput
                      testID="outbreak-condition"
                      style={styles.input}
                      placeholder="Condition code (e.g. A00)"
                      value={newCondition}
                      onChangeText={setNewCondition}
                    />
                    <TextInput
                      testID="outbreak-name"
                      style={styles.input}
                      placeholder="Outbreak name"
                      value={newName}
                      onChangeText={setNewName}
                    />
                    <Button
                      title={busy === "create" ? "Reporting…" : "Report outbreak"}
                      variant="primary"
                      onPress={onReportOutbreak}
                      disabled={busy === "create" || !newCondition.trim() || !newName.trim()}
                      testID="report-outbreak"
                    />
                  </CardBody>
                </Card>
                {outbreaks.length === 0 ? (
                  <EmptyState title="No active outbreaks" message="No outbreaks on the register" />
                ) : (
                  outbreaks.map((o) => (
                    <Card key={o.outbreakId}>
                      <CardBody>
                        <View testID={`outbreak-${o.outbreakId}`} style={styles.rowInfo}>
                          <View style={styles.titleRow}>
                            <Badge variant={severityVariant(o.severity)}>{o.severity}</Badge>
                            <Text style={styles.boldText}>{o.name}</Text>
                          </View>
                          <Text style={styles.detailText}>
                            {o.conditionCode} · {o.caseCount} case(s) · {o.deathCount} death(s)
                          </Text>
                          {o.areaDescription ? (
                            <Text style={styles.detailText}>{o.areaDescription}</Text>
                          ) : null}
                        </View>
                      </CardBody>
                    </Card>
                  ))
                )}
              </>
            )}

            {section === "teams" &&
              (teams.length === 0 ? (
                <EmptyState title="No field teams" message="No teams configured" />
              ) : (
                teams.map((t) => (
                  <Card key={t.teamId}>
                    <CardBody>
                      <View style={styles.listRow}>
                        <View style={styles.listRowInfo}>
                          <Text style={styles.boldText}>{t.name}</Text>
                          <Text style={styles.detailText}>
                            {t.teamType} · {t.memberCount} member(s)
                          </Text>
                        </View>
                        <View style={styles.teamActions}>
                          <Badge variant={t.status === "DEPLOYED" ? "warning" : "success"}>
                            {t.status}
                          </Badge>
                          <Button
                            title={busy === t.teamId ? "…" : "Deploy"}
                            variant="outline"
                            onPress={() => onDeploy(t.teamId)}
                            disabled={busy === t.teamId}
                            testID={`deploy-${t.teamId}`}
                          />
                        </View>
                      </View>
                    </CardBody>
                  </Card>
                ))
              ))}
          </ScrollView>
        )}

        <View style={styles.refreshContainer}>
          <Button title="Refresh" variant="outline" onPress={load} testID="refresh-place-mode" />
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  toggleRow: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 16 },
  scrollArea: { flex: 1 },
  scrollContent: { gap: 12, paddingBottom: 16 },
  statsGrid: { flexDirection: "row", flexWrap: "wrap", gap: 16 },
  statItem: { width: "30%", alignItems: "center", paddingVertical: 12 },
  statValue: { fontSize: 26, fontWeight: "900", color: colors.gray[900] },
  statLabel: { fontSize: 12, color: colors.gray[500], marginTop: 4, textAlign: "center" },
  rowInfo: { gap: 4 },
  titleRow: { flexDirection: "row", alignItems: "center", gap: 8 },
  listRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  listRowInfo: { flex: 1, gap: 2 },
  teamActions: { flexDirection: "row", alignItems: "center", gap: 8 },
  boldText: { fontWeight: "700", color: colors.gray[900] },
  detailText: { fontSize: 13, color: colors.gray[500] },
  input: {
    borderWidth: 1,
    borderColor: colors.gray[300],
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    marginBottom: 8,
    color: colors.gray[900],
  },
  refreshContainer: { marginTop: 8 },
});
