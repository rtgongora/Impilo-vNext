/**
 * Clerking continuity — read-only problems + visit attestations.
 *
 * Write paths (extensions, conditional attest) remain web-only (NOT BUILT on mobile).
 */

import React, { useMemo } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { useQuery } from "@tanstack/react-query";
import { LoadingSpinner, Badge, colors } from "@impilo/mobile-design-system";
import {
  fetchConditions,
  fetchVisitAttestations,
} from "../../services/medicineService";
import { groupProblems } from "../../features/medicine/medicine-summary";

type Props = {
  patientId: string;
  encounterId: string;
  embedded?: boolean;
};

export function ClerkingContinuityScreen({ patientId, encounterId, embedded }: Props) {
  const conditionsQuery = useQuery({
    queryKey: ["clerking", "conditions", patientId],
    queryFn: () => fetchConditions(patientId),
    enabled: !!patientId,
  });
  const attestationsQuery = useQuery({
    queryKey: ["clerking", "visit-attestations", patientId, encounterId],
    queryFn: () => fetchVisitAttestations(patientId, encounterId),
    enabled: !!patientId && !!encounterId,
  });

  const problems = useMemo(
    () => groupProblems(conditionsQuery.data ?? []).active,
    [conditionsQuery.data],
  );

  const unavailable: string[] = [];
  if (conditionsQuery.isError) unavailable.push("problem list");
  if (attestationsQuery.isError) unavailable.push("visit attestations");

  const loading = conditionsQuery.isLoading || attestationsQuery.isLoading;

  if (!patientId || !encounterId) {
    return (
      <View style={styles.section} testID="clerking-needs-encounter">
        <Text style={styles.hint}>
          Clerking continuity needs an active encounter so visit attestations can be scoped.
        </Text>
      </View>
    );
  }

  if (loading) {
    return (
      <View style={styles.section} testID="clerking-loading">
        <LoadingSpinner />
        <Text style={styles.hint}>Loading clerking continuity…</Text>
      </View>
    );
  }

  const attestations = attestationsQuery.data ?? [];

  return (
    <ScrollView style={styles.wrap} testID="clerking-continuity-shell">
      {!embedded && <Text style={styles.title}>Clerking continuity</Text>}
      <Text style={styles.subtitle}>Read-only mirror · Web: /ehr/…/history</Text>

      {unavailable.length > 0 && (
        <View style={styles.banner} testID="clerking-unavailable-banner">
          <Text style={styles.bannerTitle}>Could not load: {unavailable.join(", ")}</Text>
          <Text style={styles.bannerBody}>
            Failed reads are named — this is not a record that there is none.
          </Text>
        </View>
      )}

      <View style={styles.panel} testID="clerking-panel-problems">
        <Text style={styles.panelTitle}>Active problems</Text>
        {unavailable.includes("problem list") ? (
          <Text style={styles.unavailable} testID="clerking-unavailable">
            Problem list unavailable — this is not a record that there is none.
          </Text>
        ) : problems.length === 0 ? (
          <Text style={styles.empty}>No active problems recorded.</Text>
        ) : (
          problems.map((c) => (
            <Text key={c.id} style={styles.listItem}>
              · {c.attributes.conditionName}
              {c.attributes.icdCode ? ` (${c.attributes.icdCode})` : ""}
            </Text>
          ))
        )}
      </View>

      <View style={styles.panel} testID="clerking-panel-attestations">
        <Text style={styles.panelTitle}>Visit attestations</Text>
        {unavailable.includes("visit attestations") ? (
          <Text style={styles.unavailable} testID="clerking-unavailable">
            Visit attestations unavailable — do not invent confirmation.
          </Text>
        ) : attestations.length === 0 ? (
          <Text style={styles.empty} testID="clerking-attestations-empty">
            No visit attestations recorded for this encounter yet.
          </Text>
        ) : (
          attestations.map((row, index) => {
            const section = String(row.section ?? row.problem_id ?? "Section");
            const stance = String(row.stance ?? "—");
            return (
              <View key={String(row.attestation_id ?? index)} style={styles.attestationRow}>
                <Text style={styles.listItem}>{section}</Text>
                <Badge label={stance} variant="outline" />
                {row.notes ? <Text style={styles.note}>{String(row.notes)}</Text> : null}
              </View>
            );
          })
        )}
      </View>

      <View style={styles.notBuiltPanel} testID="clerking-not-built">
        <Badge label="NOT BUILT" variant="warning" />
        <Text style={styles.notBuiltText}>
          Structured clerking extensions, conditional attest write-back, examination (§7), and
          medication reconciliation panels are web-only. Mobile shows problems + attestations read-only.
        </Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  wrap: { flex: 1 },
  section: { padding: 16, gap: 8 },
  title: { fontSize: 17, fontWeight: "700", color: colors.gray[900], marginBottom: 4 },
  subtitle: { fontSize: 11, color: colors.gray[500], marginBottom: 12 },
  hint: { fontSize: 13, color: colors.gray[500] },
  banner: {
    backgroundColor: "#FFFBEB",
    borderRadius: 10,
    padding: 12,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: "#FDE68A",
  },
  bannerTitle: { fontSize: 13, fontWeight: "700", color: "#92400E" },
  bannerBody: { fontSize: 12, color: colors.gray[600], marginTop: 4 },
  panel: {
    backgroundColor: colors.gray[50],
    borderRadius: 12,
    padding: 12,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: colors.gray[200],
  },
  panelTitle: { fontSize: 14, fontWeight: "700", color: colors.gray[900], marginBottom: 8 },
  unavailable: { fontSize: 13, color: "#B45309", lineHeight: 18 },
  empty: { fontSize: 13, color: colors.gray[500] },
  listItem: { fontSize: 14, color: colors.gray[800], marginBottom: 4 },
  attestationRow: { marginBottom: 10, gap: 4 },
  note: { fontSize: 12, color: colors.gray[500], fontStyle: "italic" },
  notBuiltPanel: {
    backgroundColor: "#FFFBEB",
    borderRadius: 10,
    padding: 12,
    gap: 8,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: "#FDE68A",
  },
  notBuiltText: { fontSize: 12, color: colors.gray[700], lineHeight: 18 },
});
