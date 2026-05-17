/**
 * SystemStatusScreen (provider) — Integration Service health view.
 *
 * Shows the canonical mobile status for every integration domain enabled at
 * the current facility (PACS, LIMS, eLMIS, telemedicine, MusheX, Comms Hub,
 * Nhume, Ndila, registries…). Provider variant — includes adapter names,
 * correlation IDs, and last-sync timestamps to support incident escalation.
 *
 * Backed by `@impilo/mobile-integration` which talks to the BFF mobile
 * provider surface (`/internal/v1/mobile/provider/integration/statuses`). If
 * the BFF is unreachable, the screen degrades to a "no integrations to show"
 * empty state — it never throws.
 */

import React, { useMemo } from "react";
import { ScrollView, StyleSheet, Text, View, RefreshControl } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  EmptyState,
  LoadingSpinner,
  IntegrationStatusBadge,
} from "@impilo/mobile-design-system";
import {
  useIntegrationStatuses,
  integrationStatusCopy,
  type IntegrationStatusSummary,
  type IntegrationDomain,
} from "@impilo/mobile-integration";

const DOMAIN_LABEL: Record<IntegrationDomain, string> = {
  PACS: "PACS / imaging",
  LIMS: "LIMS / labs",
  ELMIS: "eLMIS / stock",
  TELEMEDICINE: "Telemedicine",
  COMMS_HUB: "Comms Hub",
  MUSHEX: "MusheX / payments",
  CLAIMS: "Claims",
  NHUME: "Nhume / dispatch",
  NDILA: "Ndila / maps",
  EMR: "External EMR",
  REGISTRY: "Registries",
  CRVS: "Civil registration",
  INSURANCE: "Insurance",
  OTHER: "Other",
};

export function SystemStatusScreen() {
  const { statuses, loading, empty, refresh } = useIntegrationStatuses("PROVIDER");

  const grouped = useMemo(() => groupByDomain(statuses), [statuses]);

  if (loading && statuses.length === 0) {
    return (
      <Screen>
        <Header title="System status" subtitle="Integration health" />
        <LoadingSpinner size="md" />
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="System status" subtitle="Integration health" />
      <ScrollView
        style={{ flex: 1 }}
        contentContainerStyle={styles.scroll}
        refreshControl={<RefreshControl refreshing={loading} onRefresh={refresh} />}
      >
        {empty ? (
          <EmptyState
            title="No integrations to show"
            message="No integration adapters are reporting for your facility right now. Pull to refresh."
          />
        ) : (
          (Object.keys(grouped) as IntegrationDomain[]).map((domain) => (
            <Card key={domain}>
              <CardBody>
                <Text style={styles.domain}>{DOMAIN_LABEL[domain] ?? domain}</Text>
                {grouped[domain].map((s) => (
                  <View key={s.id} style={styles.row} testID={`integration-row-${s.id}`}>
                    <View style={styles.rowMain}>
                      <Text style={styles.adapter}>{s.adapter ?? domain}</Text>
                      <Text style={styles.message} numberOfLines={2}>
                        {s.message ?? integrationStatusCopy(s.status).message}
                      </Text>
                      {s.lastSyncedAt ? (
                        <Text style={styles.muted}>Last sync · {formatTime(s.lastSyncedAt)}</Text>
                      ) : null}
                      {s.correlationId ? (
                        <Text style={styles.muted} selectable>
                          Ref · {s.correlationId.slice(0, 8)}
                        </Text>
                      ) : null}
                    </View>
                    <IntegrationStatusBadge
                      status={s.status as never}
                      testID={`integration-badge-${s.id}`}
                    />
                  </View>
                ))}
              </CardBody>
            </Card>
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

function groupByDomain(
  rows: IntegrationStatusSummary[]
): Record<IntegrationDomain, IntegrationStatusSummary[]> {
  const out: Partial<Record<IntegrationDomain, IntegrationStatusSummary[]>> = {};
  for (const row of rows) {
    const key = row.domain ?? "OTHER";
    (out[key] ??= []).push(row);
  }
  return out as Record<IntegrationDomain, IntegrationStatusSummary[]>;
}

function formatTime(iso: string): string {
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

const styles = StyleSheet.create({
  scroll: { padding: 16, gap: 12 },
  domain: { fontSize: 13, fontWeight: "700", color: "#0F172A", marginBottom: 8 },
  row: {
    flexDirection: "row",
    alignItems: "flex-start",
    justifyContent: "space-between",
    gap: 12,
    paddingVertical: 8,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: "#E2E8F0",
  },
  rowMain: { flex: 1, gap: 2 },
  adapter: { fontSize: 14, fontWeight: "600", color: "#0F172A" },
  message: { fontSize: 12, color: "#475569" },
  muted: { fontSize: 11, color: "#94A3B8" },
});
