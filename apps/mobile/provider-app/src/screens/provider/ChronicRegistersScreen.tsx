/**
 * Chronic disease registers — facility recall worklist.
 *
 * Mirrors GET /internal/v1/programmes/register (same as web /clinical/chronic-registers).
 */

import React, { useState } from "react";
import { View, Text, TouchableOpacity, ScrollView, StyleSheet } from "react-native";
import { useQuery } from "@tanstack/react-query";
import { ApiError } from "@impilo/mobile-api-client";
import { Badge, LoadingSpinner, colors } from "@impilo/mobile-design-system";
import { useAppStore } from "../../stores/appStore";
import { CHRONIC_REGISTERS, fetchChronicRegister } from "../../services/medicineService";
import { programmeLabel } from "../../features/medicine/medicine-summary";

export function ChronicRegistersScreen() {
  const { facilityId } = useAppStore();
  const [programme, setProgramme] = useState<string>(CHRONIC_REGISTERS[0].programme);

  const registerQuery = useQuery({
    queryKey: ["chronic-register", programme, facilityId],
    queryFn: () => fetchChronicRegister(programme, facilityId),
    enabled: !!programme,
  });

  const unavailable =
    registerQuery.isError &&
    registerQuery.error instanceof ApiError &&
    registerQuery.error.status >= 500;

  return (
    <ScrollView style={styles.wrap} testID="chronic-registers-screen">
      <Text style={styles.title}>Chronic disease registers</Text>
      <Text style={styles.subtitle}>
        Web: /clinical/chronic-registers · Facility worklist (partial mobile mirror)
      </Text>

      {!facilityId && (
        <Text style={styles.hint} testID="registers-needs-facility">
          Select a facility context so the register is scoped to your site.
        </Text>
      )}

      <View style={styles.topicRow}>
        {CHRONIC_REGISTERS.map((r) => (
          <TouchableOpacity
            key={r.programme}
            testID={`register-${r.programme}`}
            onPress={() => setProgramme(r.programme)}
            style={[styles.topicChip, programme === r.programme && styles.topicChipActive]}
          >
            <Text
              style={[styles.topicChipText, programme === r.programme && styles.topicChipTextActive]}
            >
              {r.label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {registerQuery.isLoading && <LoadingSpinner testID="registers-loading" />}

      {unavailable && (
        <View style={styles.errorBox} testID="registers-unavailable">
          <Text style={styles.errorTitle}>
            {programmeLabel(programme)} register could not be read
          </Text>
          <Text style={styles.errorBody}>
            This is not an empty register — retry before assuming no recall work exists.
          </Text>
        </View>
      )}

      {registerQuery.isError && !unavailable && (
        <View style={styles.errorBox} testID="registers-error">
          <Text style={styles.errorTitle}>Register read failed</Text>
          <Text style={styles.errorBody}>
            {registerQuery.error instanceof Error ? registerQuery.error.message : "Unknown error"}
          </Text>
        </View>
      )}

      {registerQuery.data && (
        <View testID="registers-results">
          <Text style={styles.summary}>
            {registerQuery.data.total} enrolled · {registerQuery.data.never_assessed} never assessed
            for control
          </Text>
          <Text style={styles.summaryNote}>
            {registerQuery.data.control_status_null_means_never_assessed}
          </Text>

          {registerQuery.data.entries.length === 0 ? (
            <Text style={styles.empty} testID="registers-empty">
              No entries on this register for the current facility scope.
            </Text>
          ) : (
            registerQuery.data.entries.map((entry) => (
              <View key={entry.enrolment_id} style={styles.entryCard} testID={`register-entry-${entry.enrolment_id}`}>
                <Text style={styles.entryCpid}>{entry.subject_cpid}</Text>
                <Text style={styles.entryMeta}>
                  {entry.status.replace(/_/g, " ")} · enrolled {entry.enrolled_on}
                </Text>
                {entry.control_status == null ? (
                  <Badge label="Control never assessed" variant="warning" />
                ) : (
                  <Badge label={entry.control_status.replace(/_/g, " ")} variant="outline" />
                )}
              </View>
            ))
          )}
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  wrap: { flex: 1, gap: 10 },
  title: { fontSize: 17, fontWeight: "700", color: colors.gray[900] },
  subtitle: { fontSize: 11, color: colors.gray[500], marginBottom: 8 },
  hint: { fontSize: 13, color: "#B45309", marginBottom: 8 },
  topicRow: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginVertical: 8 },
  topicChip: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: colors.gray[100],
    borderWidth: 1,
    borderColor: colors.gray[200],
  },
  topicChipActive: { backgroundColor: "#DBEAFE", borderColor: "#2563EB" },
  topicChipText: { fontSize: 11, color: colors.gray[700] },
  topicChipTextActive: { color: "#1D4ED8", fontWeight: "600" },
  errorBox: {
    backgroundColor: "#FEF2F2",
    borderRadius: 10,
    padding: 12,
    marginTop: 8,
    borderWidth: 1,
    borderColor: "#FECACA",
  },
  errorTitle: { fontSize: 13, fontWeight: "700", color: "#B91C1C" },
  errorBody: { fontSize: 12, color: colors.gray[600], marginTop: 4, lineHeight: 18 },
  summary: { fontSize: 14, fontWeight: "600", color: colors.gray[800], marginTop: 8 },
  summaryNote: { fontSize: 11, color: colors.gray[500], marginBottom: 10, fontStyle: "italic" },
  empty: { fontSize: 13, color: colors.gray[500] },
  entryCard: {
    backgroundColor: "#fff",
    borderRadius: 10,
    padding: 12,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: colors.gray[200],
    gap: 4,
  },
  entryCpid: { fontSize: 14, fontWeight: "600", color: colors.gray[900] },
  entryMeta: { fontSize: 12, color: colors.gray[500] },
});
