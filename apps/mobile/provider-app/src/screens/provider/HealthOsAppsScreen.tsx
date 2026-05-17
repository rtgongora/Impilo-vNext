/**
 * HealthOsAppsScreen (provider) — provider/admin view of the Health OS
 * Capability Marketplace. Lists installed clinical and operational apps for
 * the provider's facility, AI skills available to their role, and the
 * available-to-request capabilities, with a one-tap request-access flow.
 *
 * Backed by msika-apps-service via experience-bff:
 *   GET  /internal/v1/marketplace/launcher
 *   POST /internal/v1/marketplace/activation-requests
 */

import React, { useCallback, useEffect, useState } from "react";
import { Pressable, View, Text, ScrollView, StyleSheet, RefreshControl } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  Badge,
  EmptyState,
  LoadingSpinner,
} from "@impilo/mobile-design-system";
import {
  fetchHealthOsLauncher,
  requestCapabilityActivation,
  type LauncherApp,
} from "../../services/healthOsLauncherService";
import { SystemStatusScreen } from "./SystemStatusScreen";

const BLUE = "#1E40AF";

const STATE_TONE: Record<LauncherApp["state"], { label: string; color: string }> = {
  INSTALLED:                  { label: "Installed",                color: BLUE },
  AVAILABLE:                  { label: "Available",                color: "#1d4ed8" },
  REQUEST_ACCESS:             { label: "Request access",           color: "#d97706" },
  PENDING_APPROVAL:           { label: "Pending approval",         color: "#d97706" },
  NOT_AVAILABLE_AT_FACILITY:  { label: "Not at this facility",     color: "#94a3b8" },
  REQUIRES_CONFIGURATION:     { label: "Awaiting configuration",   color: "#d97706" },
  TEMPORARILY_UNAVAILABLE:    { label: "Temporarily unavailable",  color: "#94a3b8" },
  SUSPENDED:                  { label: "Suspended",                color: "#b91c1c" },
  DEPRECATED:                 { label: "Deprecated",               color: "#94a3b8" },
};

export function HealthOsAppsScreen() {
  const [apps, setApps] = useState<LauncherApp[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [requesting, setRequesting] = useState<string | null>(null);
  const [requestMessage, setRequestMessage] = useState<string | null>(null);
  const [showStatus, setShowStatus] = useState(false);

  if (showStatus) {
    return (
      <View style={{ flex: 1 }}>
        <Pressable
          onPress={() => setShowStatus(false)}
          accessibilityRole="button"
          accessibilityLabel="Back to Health OS apps"
          style={styles.backRow}
          testID="back-to-apps"
        >
          <Ionicons name="chevron-back" size={18} color="#1E40AF" />
          <Text style={styles.backText}>Back to apps</Text>
        </Pressable>
        <View style={{ flex: 1 }}>
          <SystemStatusScreen />
        </View>
      </View>
    );
  }

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      setApps(await fetchHealthOsLauncher());
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const handleRequest = useCallback(async (app: LauncherApp) => {
    setRequesting(app.id);
    setRequestMessage(null);
    try {
      const result = await requestCapabilityActivation({
        itemId: app.id,
        purposeOfUse: "TREATMENT",
        justification: `Provider-initiated request to enable ${app.name} for this facility.`,
      });
      const id = (result && typeof result === "object" && "id" in result)
        ? String((result as { id?: unknown }).id ?? "")
        : "";
      setRequestMessage(`Submitted${id ? ` (request ${id.slice(0, 6)})` : ""}.`);
      void load();
    } catch (e) {
      setRequestMessage(e instanceof Error ? e.message : "Could not submit request, please try again later.");
    } finally {
      setRequesting(null);
    }
  }, [load]);

  return (
    <Screen>
      <Header title="Health OS Apps" subtitle="Approved provider apps, extensions, workflow packs and AI skills" />

      <ScrollView
        contentContainerStyle={styles.scroll}
        refreshControl={<RefreshControl refreshing={isLoading} onRefresh={load} colors={[BLUE]} />}
      >
        <Pressable
          onPress={() => setShowStatus(true)}
          style={styles.statusEntry}
          accessibilityRole="button"
          accessibilityLabel="Open system status"
          testID="open-system-status"
        >
          <Ionicons name="pulse-outline" size={18} color={BLUE} />
          <Text style={styles.statusEntryText}>System status (integrations)</Text>
          <Ionicons name="chevron-forward" size={16} color="#94A3B8" />
        </Pressable>

        {error ? (
          <Card style={styles.errorCard}>
            <CardBody>
              <Text style={styles.errorText}>{error}</Text>
              <Button onPress={load} variant="outline" title="Retry" />
            </CardBody>
          </Card>
        ) : null}

        {requestMessage ? (
          <Card style={styles.toast}>
            <CardBody>
              <Text style={styles.toastText}>{requestMessage}</Text>
            </CardBody>
          </Card>
        ) : null}

        {isLoading && apps.length === 0 ? (
          <View style={styles.loading}>
            <LoadingSpinner />
          </View>
        ) : apps.length === 0 ? (
          <EmptyState
            icon={<Ionicons name="apps-outline" size={32} color="#94a3b8" />}
            title="No apps yet"
            description="Once your facility activates Health OS apps, plugins or AI skills, they will appear here."
          />
        ) : (
          apps.map((app) => {
            const tone = STATE_TONE[app.state];
            const isInstalled = app.state === "INSTALLED";
            const isRequestable = app.state === "REQUEST_ACCESS" || app.state === "NOT_AVAILABLE_AT_FACILITY";
            return (
              <Card key={app.id} style={styles.card}>
                <CardBody>
                  <View style={styles.cardHeader}>
                    <View style={styles.iconWrap}>
                      <Ionicons name={(iconForApp(app) as never)} size={22} color={BLUE} />
                    </View>
                    <View style={styles.cardTitleWrap}>
                      <Text style={styles.cardTitle}>{app.name}</Text>
                      <Text style={styles.cardSubtitle}>{app.type} · {app.category}</Text>
                    </View>
                    <Badge variant={isInstalled ? "default" : "outline"}>
                      <Text style={[styles.badgeText, { color: tone.color }]}>{tone.label}</Text>
                    </Badge>
                  </View>
                  <Text style={styles.description}>{app.description}</Text>
                  {app.reason ? <Text style={styles.reason}>{app.reason}</Text> : null}
                  {isRequestable ? (
                    <Button
                      onPress={() => handleRequest(app)}
                      variant="primary"
                      disabled={requesting === app.id}
                      title={requesting === app.id ? "Submitting..." : "Request access"}
                    />
                  ) : null}
                </CardBody>
              </Card>
            );
          })
        )}
      </ScrollView>
    </Screen>
  );
}

function iconForApp(app: LauncherApp): string {
  if (app.type === "AI_SKILL") return "sparkles-outline";
  if (app.type === "EXTENSION") return "extension-puzzle-outline";
  if (app.type === "WORKFLOW_PACK") return "git-network-outline";
  switch (app.category) {
    case "TELEMEDICINE":      return "videocam-outline";
    case "IMAGING":           return "scan-outline";
    case "LABORATORY":        return "flask-outline";
    case "LOGISTICS":         return "bicycle-outline";
    case "PUBLIC_HEALTH":     return "shield-checkmark-outline";
    case "CLAIMS":            return "wallet-outline";
    case "TRAINING":          return "school-outline";
    case "WELLNESS":          return "heart-outline";
    case "AI":                return "sparkles-outline";
    default:                  return "apps-outline";
  }
}

const styles = StyleSheet.create({
  scroll: { padding: 16, gap: 12 },
  loading: { padding: 24, alignItems: "center" },
  card: { marginBottom: 12 },
  cardHeader: { flexDirection: "row", alignItems: "center", marginBottom: 8 },
  iconWrap: {
    width: 36,
    height: 36,
    borderRadius: 10,
    backgroundColor: "#EFF6FF",
    alignItems: "center",
    justifyContent: "center",
    marginRight: 10,
  },
  cardTitleWrap: { flex: 1 },
  cardTitle: { fontSize: 15, fontWeight: "600", color: "#0f172a" },
  cardSubtitle: { fontSize: 11, color: "#64748b" },
  badgeText: { fontSize: 10, fontWeight: "700" },
  description: { fontSize: 13, color: "#475569", marginBottom: 8 },
  reason: { fontSize: 11, color: "#92400e", marginBottom: 8 },
  errorCard: { borderColor: "#fecaca", borderWidth: 1, marginBottom: 12 },
  errorText: { color: "#b91c1c", fontSize: 13, marginBottom: 8 },
  toast: { borderColor: "#bfdbfe", borderWidth: 1, marginBottom: 12 },
  toastText: { color: "#1e3a8a", fontSize: 12 },
  statusEntry: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingVertical: 10,
    paddingHorizontal: 12,
    backgroundColor: "#EFF6FF",
    borderRadius: 12,
    marginBottom: 12,
  },
  statusEntryText: { flex: 1, color: "#1E40AF", fontWeight: "600", fontSize: 13 },
  backRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  backText: { color: "#1E40AF", fontWeight: "600", fontSize: 13 },
});
