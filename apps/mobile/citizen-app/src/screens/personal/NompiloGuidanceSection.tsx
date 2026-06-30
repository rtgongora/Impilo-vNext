import React from "react";
import { View, Text, TouchableOpacity, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useQuery, useMutation } from "@tanstack/react-query";
import {
  fetchNompiloContext,
  dismissGuidance,
  requestNompiloFollowUp,
  type NompiloGuidanceItem,
  type NompiloSeverity,
} from "../../services/nompiloGuidanceService";
import { appStore } from "../../stores/appStore";

/**
 * Nompilo Contextual Guidance — mobile.
 *
 * Renders the route-aware guidance set resolved by the BFF NompiloGuidanceController from real
 * service signals (trust, profile, payments). Mirrors the web NompiloContextualGuidance component
 * for web↔mobile parity. Every card routes to a real owning section — no dead buttons, no demo
 * guidance. Cards can be dismissed, and where the catalogue allows it a Khuluma follow-up can be
 * requested (Nompilo requests; Khuluma sends). Renders nothing when there is no guidance.
 */

const SEVERITY_COLOR: Record<NompiloSeverity, string> = {
  INFO: "#0f766e",
  RECOMMEND: "#0284c7",
  WARN: "#b45309",
  CRITICAL: "#b91c1c",
};

/**
 * Map a guidance CTA route onto the Personal section the mobile app can navigate to. The web routes
 * are the source of truth; this resolves them to the mobile section ids (see WalletOverviewSection).
 */
function sectionForRoute(route?: string | null): string | null {
  if (!route) return null;
  if (route.includes("/identity")) return "health-id";
  if (route.includes("/payments")) return "finance";
  if (route.includes("/records")) return "records";
  if (route.includes("/profile")) return "health-id";
  return null;
}

function GuidanceCard({ item, routePath }: { item: NompiloGuidanceItem; routePath: string }) {
  const dismiss = useMutation({ mutationFn: () => dismissGuidance(item.key) });
  const followUp = useMutation({
    mutationFn: () => requestNompiloFollowUp({ guidanceKey: item.key, reason: item.title, routePath }),
  });
  const section = sectionForRoute(item.ctaRoute);

  if (dismiss.isSuccess) return null;

  return (
    <View
      style={[styles.card, { borderLeftColor: SEVERITY_COLOR[item.severity] ?? SEVERITY_COLOR.INFO }]}
      testID={`nompilo-guidance-${item.key}`}
    >
      <View style={styles.row}>
        <Text style={styles.title} accessibilityLabel={item.accessibilityNote ?? item.title}>
          {item.title}
        </Text>
        <TouchableOpacity
          onPress={() => dismiss.mutate()}
          accessibilityLabel={`Dismiss ${item.title}`}
          testID={`nompilo-dismiss-${item.key}`}
        >
          <Ionicons name="close" size={16} color="#6b7280" />
        </TouchableOpacity>
      </View>
      <Text style={styles.body}>{item.body}</Text>
      <View style={styles.actions}>
        {section ? (
          <TouchableOpacity
            style={styles.cta}
            testID={`nompilo-cta-${item.key}`}
            onPress={() => appStore.getState().setPersonalSectionRequest(section)}
          >
            <Text style={styles.ctaText}>{item.ctaLabel ?? "Open"}</Text>
            <Ionicons name="arrow-forward" size={12} color="#0f766e" />
          </TouchableOpacity>
        ) : null}
        {item.khulumaFollowUpAvailable ? (
          <TouchableOpacity
            style={styles.followUp}
            disabled={followUp.isPending || followUp.isSuccess}
            testID={`nompilo-followup-${item.key}`}
            onPress={() => followUp.mutate()}
          >
            <Ionicons name="notifications-outline" size={12} color="#374151" />
            <Text style={styles.followUpText}>
              {followUp.isSuccess ? "Follow-up requested" : "Remind me"}
            </Text>
          </TouchableOpacity>
        ) : null}
        {item.ownerService ? <Text style={styles.via}>via {item.ownerService}</Text> : null}
      </View>
    </View>
  );
}

export function NompiloGuidanceSection({ routePath = "/citizen/wallet" }: { routePath?: string }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["nompilo", "context", routePath],
    queryFn: () => fetchNompiloContext(routePath),
  });

  const items = data?.guidance ?? [];
  // Quiet states — Nompilo never invents guidance to fill space.
  if (isLoading || isError || items.length === 0) {
    return null;
  }

  return (
    <View style={styles.container} testID="nompilo-guidance-section">
      <Text style={styles.header}>Nompilo · your guide</Text>
      {items.map((item) => (
        <GuidanceCard key={item.key} item={item} routePath={routePath} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { marginTop: 12, marginBottom: 4 },
  header: { fontSize: 13, fontWeight: "600", color: "#111827", marginBottom: 8 },
  card: {
    borderLeftWidth: 3,
    borderWidth: 1,
    borderColor: "#e5e7eb",
    borderRadius: 10,
    padding: 12,
    marginBottom: 8,
    backgroundColor: "#ffffff",
  },
  row: { flexDirection: "row", justifyContent: "space-between", alignItems: "flex-start" },
  title: { flex: 1, fontSize: 14, fontWeight: "600", color: "#111827", paddingRight: 8 },
  body: { fontSize: 12, color: "#4b5563", marginTop: 2 },
  actions: { flexDirection: "row", alignItems: "center", flexWrap: "wrap", marginTop: 8, gap: 10 },
  cta: { flexDirection: "row", alignItems: "center", gap: 4 },
  ctaText: { fontSize: 12, fontWeight: "600", color: "#0f766e" },
  followUp: { flexDirection: "row", alignItems: "center", gap: 4 },
  followUpText: { fontSize: 12, color: "#374151" },
  via: { fontSize: 10, color: "#9ca3af", textTransform: "uppercase" },
});
