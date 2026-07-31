/**
 * Adult medicine workspace — programmes, problem list, allergies.
 *
 * Mirrors web honesty: a failed read is named; it never renders as an empty list.
 */

import React, { useMemo } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { useQuery } from "@tanstack/react-query";
import { ApiError } from "@impilo/mobile-api-client";
import { Badge, LoadingSpinner, colors } from "@impilo/mobile-design-system";
import {
  fetchAllergies,
  fetchConditions,
  fetchProgrammeEnrolments,
} from "../../services/medicineService";
import {
  allergyLabelsFromResources,
  exitedEnrolments,
  groupProblems,
  isConfidential,
  openEnrolments,
  programmeLabel,
} from "../../features/medicine/medicine-summary";

type Props = {
  patientId: string;
  /** When true, omit outer title (parent supplies context). */
  embedded?: boolean;
};

function UnavailableLine({ label, testId }: { label: string; testId: string }) {
  return (
    <Text style={styles.unavailable} testID={testId} accessibilityRole="alert">
      {label} could not be read — this is not a statement that the patient has none.
    </Text>
  );
}

export function MedicineWorkspaceScreen({ patientId, embedded }: Props) {
  const conditionsQuery = useQuery({
    queryKey: ["medicine", "conditions", patientId],
    queryFn: () => fetchConditions(patientId),
    enabled: !!patientId,
  });
  const allergiesQuery = useQuery({
    queryKey: ["medicine", "allergies", patientId],
    queryFn: () => fetchAllergies(patientId),
    enabled: !!patientId,
  });
  const programmesQuery = useQuery({
    queryKey: ["medicine", "programmes", patientId],
    queryFn: () => fetchProgrammeEnrolments(patientId),
    enabled: !!patientId,
  });

  const context = useMemo(() => {
    const problems = groupProblems(conditionsQuery.data ?? []);
    const enrolments = programmesQuery.data ?? [];
    const allergyLabels = allergyLabelsFromResources(allergiesQuery.data ?? []);
    const unavailable: string[] = [];
    if (conditionsQuery.isError) unavailable.push("problem list");
    if (allergiesQuery.isError) unavailable.push("allergies");
    if (programmesQuery.isError) unavailable.push("care programmes");
    return {
      problems,
      openProgrammes: openEnrolments(enrolments),
      exitedProgrammes: exitedEnrolments(enrolments),
      allergyLabels,
      unavailable,
    };
  }, [
    conditionsQuery.data,
    conditionsQuery.isError,
    allergiesQuery.data,
    allergiesQuery.isError,
    programmesQuery.data,
    programmesQuery.isError,
  ]);

  const loading =
    conditionsQuery.isLoading || allergiesQuery.isLoading || programmesQuery.isLoading;

  if (!patientId) {
    return (
      <View style={styles.section} testID="medicine-workspace-needs-patient">
        <Text style={styles.hint}>Select a patient with an active encounter to open the medicine workspace.</Text>
      </View>
    );
  }

  if (loading) {
    return (
      <View style={styles.section} testID="medicine-workspace-loading">
        <LoadingSpinner />
        <Text style={styles.hint}>Loading the medical record…</Text>
      </View>
    );
  }

  const gatewayHint =
    conditionsQuery.error instanceof ApiError || allergiesQuery.error instanceof ApiError
      ? "Upstream read failed — retry before recording decisions against missing sections."
      : null;

  return (
    <ScrollView style={styles.wrap} testID="medicine-workspace">
      {!embedded && (
        <Text style={styles.title}>Adult medicine workspace</Text>
      )}
      <Text style={styles.subtitle}>
        Web spine: /ehr/{patientId}/medicine · Mobile mirror (partial)
      </Text>

      {context.unavailable.length > 0 && (
        <View style={styles.banner} testID="medicine-workspace-unavailable">
          <Text style={styles.bannerTitle}>Could not load: {context.unavailable.join(", ")}</Text>
          <Text style={styles.bannerBody}>
            This is not the same as the patient having none. Do not record clinical decisions against
            the missing sections.
          </Text>
          {gatewayHint && <Text style={styles.bannerBody}>{gatewayHint}</Text>}
        </View>
      )}

      <View style={styles.panel} testID="panel-programmes">
        <Text style={styles.panelTitle}>Care programmes</Text>
        {context.unavailable.includes("care programmes") ? (
          <UnavailableLine label="Care programmes" testId="programmes-unavailable" />
        ) : context.openProgrammes.length === 0 ? (
          <Text style={styles.empty} testID="programmes-empty">
            No open HIV or TB programme enrolment is recorded for this patient.
          </Text>
        ) : (
          context.openProgrammes.map((e) => (
            <View key={e.enrolment_id} style={styles.row}>
              <Text style={styles.rowMain}>
                {programmeLabel(e.programme)}
                {isConfidential(e) ? " · Confidential" : ""}
              </Text>
              <Text style={styles.rowMeta}>{e.status.replace(/_/g, " ")}</Text>
            </View>
          ))
        )}
        {!context.unavailable.includes("care programmes") && context.exitedProgrammes.length > 0 && (
          <Text style={styles.exited} testID="programmes-exited">
            {context.exitedProgrammes.length} ended enrolment
            {context.exitedProgrammes.length === 1 ? "" : "s"}
          </Text>
        )}
      </View>

      <View style={styles.panel} testID="panel-problems">
        <Text style={styles.panelTitle}>Problem list</Text>
        {context.unavailable.includes("problem list") ? (
          <UnavailableLine label="The problem list" testId="problems-unavailable" />
        ) : context.problems.active.length === 0 ? (
          <Text style={styles.empty} testID="problems-empty">
            No active problems are recorded for this patient.
          </Text>
        ) : (
          context.problems.active.map((c) => (
            <View key={c.id} style={styles.row}>
              <Text style={styles.rowMain}>{c.attributes.conditionName}</Text>
              <Text style={styles.rowMeta}>
                {c.attributes.icdCode ?? "—"} · {String(c.attributes.clinicalStatus).toLowerCase()}
              </Text>
            </View>
          ))
        )}
        {!context.unavailable.includes("problem list") &&
          (context.problems.remission.length > 0 || context.problems.inactive.length > 0) && (
            <Text style={styles.exited} testID="problems-other">
              {context.problems.remission.length > 0 && `${context.problems.remission.length} in remission`}
              {context.problems.remission.length > 0 && context.problems.inactive.length > 0 && " · "}
              {context.problems.inactive.length > 0 &&
                `${context.problems.inactive.length} inactive or resolved`}
            </Text>
          )}
      </View>

      <View style={styles.panel} testID="panel-allergies">
        <Text style={styles.panelTitle}>Allergies</Text>
        {context.unavailable.includes("allergies") ? (
          <UnavailableLine
            label="Allergies"
            testId="allergies-unavailable"
          />
        ) : context.allergyLabels.length === 0 ? (
          <Text style={styles.empty} testID="allergies-empty">
            No allergies recorded.
          </Text>
        ) : (
          <Text style={styles.allergyList}>{context.allergyLabels.join(", ")}</Text>
        )}
      </View>

      <View style={styles.notBuiltPanel} testID="medicine-not-built">
        <Badge label="NOT BUILT" variant="warning" />
        <Text style={styles.notBuiltText}>
          Examination (§7), thirteen specialty tool screens, multimorbidity view, and ambulatory order
          sets are on web only. Specialty labels in Tools → Specialty remain IN_DEVELOPMENT.
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
    backgroundColor: "#FEF2F2",
    borderRadius: 10,
    padding: 12,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: "#FECACA",
  },
  bannerTitle: { fontSize: 13, fontWeight: "700", color: "#B91C1C" },
  bannerBody: { fontSize: 12, color: colors.gray[600], marginTop: 4, lineHeight: 18 },
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
  empty: { fontSize: 13, color: colors.gray[500], lineHeight: 18 },
  row: { marginBottom: 6 },
  rowMain: { fontSize: 14, color: colors.gray[900] },
  rowMeta: { fontSize: 11, color: colors.gray[500], marginTop: 2 },
  exited: { fontSize: 11, color: colors.gray[500], marginTop: 6 },
  allergyList: { fontSize: 14, color: colors.gray[800] },
  notBuiltPanel: {
    backgroundColor: "#FFFBEB",
    borderRadius: 10,
    padding: 12,
    gap: 8,
    marginTop: 4,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: "#FDE68A",
  },
  notBuiltText: { fontSize: 12, color: colors.gray[700], lineHeight: 18 },
});
