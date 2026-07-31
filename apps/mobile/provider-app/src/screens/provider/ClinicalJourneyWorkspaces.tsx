/**
 * RMNP W13-C — mobile clinical journey workspaces with forced form keys.
 */

import React, { useEffect, useMemo, useState } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, TextInput } from "react-native";
import { useMutation, useQuery } from "@tanstack/react-query";
import { colors } from "@impilo/mobile-design-system";
import { getFormCatalog, submitEncounterForm } from "../../services/encounterFormsService";

const FORM_KEYS = {
  ancFirst: "impilo.anc.contact1.v1",
  ancFollowup: "impilo.anc.contact.followup.v1",
  pncMaternal: "impilo.pnc.maternal.contact.v1",
  pncNewborn: "impilo.pnc.newborn.contact.v1",
  fpEligibility: "impilo.fp.eligibility.v1",
} as const;

interface ClinicalField {
  id: string;
  label: string;
  kind: string;
  options?: { code: string; display: string }[];
}
interface ClinicalSection { id: string; title: string; fields: ClinicalField[] }
interface ClinicalDefinition { id: string; title: string; sections: ClinicalSection[] }

function useCatalogForm(formKey: string) {
  const catalog = useQuery({ queryKey: ["forms-catalog", formKey], queryFn: getFormCatalog });
  const definition = useMemo(() => {
    for (const entry of catalog.data ?? []) {
      if (entry.formKey !== formKey) continue;
      try {
        return JSON.parse(entry.definitionJson) as ClinicalDefinition;
      } catch {
        return null;
      }
    }
    return null;
  }, [catalog.data, formKey]);
  return { definition, isLoading: catalog.isLoading };
}

function GovernedEncounterFormWorkspace({
  formKey,
  title,
  hint,
  testId,
  headerNote,
}: {
  formKey: string;
  title: string;
  hint: string;
  testId: string;
  headerNote?: string;
}) {
  const [encounterId, setEncounterId] = useState("");
  const [answers, setAnswers] = useState<Record<string, unknown>>({});
  const [submitted, setSubmitted] = useState(false);
  const form = useCatalogForm(formKey);
  const submit = useMutation({
    mutationFn: () => submitEncounterForm(encounterId.trim(), formKey, answers),
    onSuccess: () => setSubmitted(true),
  });

  const screeningIncomplete =
    (formKey === FORM_KEYS.pncMaternal || formKey === FORM_KEYS.pncNewborn) &&
    answers.screeningComplete !== "YES";

  return (
    <ScrollView style={styles.workspace} testID={testId}>
      <Text style={styles.workspaceTitle}>{title}</Text>
      <Text style={styles.hint}>{hint}</Text>
      <Text style={styles.formKey}>Form: {formKey}</Text>
      {headerNote ? <Text style={styles.headerNote}>{headerNote}</Text> : null}
      <TextInput
        style={styles.input}
        placeholder="Encounter ID *"
        value={encounterId}
        onChangeText={setEncounterId}
        testID={`${testId}-encounter-id`}
      />
      {form.isLoading && <Text style={styles.hint}>Loading form…</Text>}
      {form.definition?.sections.map((section) => (
        <View key={section.id} style={styles.section}>
          <Text style={styles.sectionTitle}>{section.title}</Text>
          {section.fields.slice(0, 3).map((field) =>
            field.kind === "coded_single" && field.options ? (
              <View key={field.id} style={styles.field}>
                <Text style={styles.label}>{field.label}</Text>
                <View style={styles.options}>
                  {field.options.map((opt) => (
                    <Pressable
                      key={opt.code}
                      onPress={() =>
                        setAnswers((a) => ({
                          ...a,
                          [field.id]: a[field.id] === opt.code ? undefined : opt.code,
                        }))
                      }
                      style={[styles.option, answers[field.id] === opt.code && styles.optionSelected]}
                      testID={`field-${field.id}-opt-${opt.code}`}
                    >
                      <Text style={styles.optionText}>{opt.display}</Text>
                    </Pressable>
                  ))}
                </View>
              </View>
            ) : null,
          )}
        </View>
      ))}
      {submitted && screeningIncomplete && (
        <View style={styles.warningBox} testID={`${testId}-screening-incomplete`}>
          <Text style={styles.warningTitle}>Screening not completed</Text>
          <Text style={styles.warningBody}>Not the same as no danger signs.</Text>
        </View>
      )}
      <Pressable
        style={[styles.primaryBtn, (!encounterId.trim() || submit.isPending) && styles.primaryBtnDisabled]}
        disabled={!encounterId.trim() || submit.isPending}
        onPress={() => submit.mutate()}
        testID={`${testId}-submit`}
      >
        <Text style={styles.primaryBtnText}>{submit.isPending ? "Submitting…" : "Submit & record"}</Text>
      </Pressable>
      {submitted && !submit.isError && (
        <Text style={styles.successText} testID={`${testId}-submitted`}>Recorded.</Text>
      )}
    </ScrollView>
  );
}

export function AncFirstContactWorkspace() {
  return (
    <GovernedEncounterFormWorkspace
      testId="anc-first-contact-workspace"
      formKey={FORM_KEYS.ancFirst}
      title="ANC first contact"
      hint="Forced impilo.anc.contact1.v1 — not resolver-dependent."
    />
  );
}

export function AncFollowUpWorkspace() {
  return (
    <GovernedEncounterFormWorkspace
      testId="anc-followup-workspace"
      formKey={FORM_KEYS.ancFollowup}
      title="ANC follow-up"
      hint="impilo.anc.contact.followup.v1"
    />
  );
}

export function FacilityPncMaternalWorkspace() {
  return (
    <GovernedEncounterFormWorkspace
      testId="facility-pnc-maternal-workspace"
      formKey={FORM_KEYS.pncMaternal}
      title="Facility PNC — mother"
      hint="Distinct from CHW community postnatal home visits."
    />
  );
}

export function FacilityPncNewbornWorkspace() {
  return (
    <GovernedEncounterFormWorkspace
      testId="facility-pnc-newborn-workspace"
      formKey={FORM_KEYS.pncNewborn}
      title="Facility PNC — newborn"
      hint="Local signs only."
      headerNote="PSBI is not restated — use impilo.young-infant.imci.v1 on the paediatric workspace for systemic signs."
    />
  );
}

export function FpEligibilityWorkspace() {
  return (
    <GovernedEncounterFormWorkspace
      testId="fp-eligibility-workspace"
      formKey={FORM_KEYS.fpEligibility}
      title="FP eligibility"
      hint="Form 18 — impilo.fp.eligibility.v1"
    />
  );
}

const styles = StyleSheet.create({
  workspace: { flex: 1 },
  workspaceTitle: { fontSize: 16, fontWeight: "700", color: colors.gray[900], marginBottom: 8 },
  hint: { fontSize: 13, color: colors.gray[500] },
  formKey: { fontSize: 11, color: colors.gray[600], marginTop: 4, fontFamily: "monospace" },
  headerNote: { fontSize: 12, color: "#0369A1", backgroundColor: "#E0F2FE", padding: 8, borderRadius: 8, marginTop: 8 },
  input: { borderWidth: 1, borderColor: colors.gray[300], borderRadius: 8, padding: 8, fontSize: 13, marginTop: 8 },
  section: { marginTop: 10 },
  sectionTitle: { fontSize: 13, fontWeight: "600", color: colors.gray[700] },
  field: { marginTop: 6 },
  label: { fontSize: 12, color: colors.gray[700] },
  options: { flexDirection: "row", flexWrap: "wrap", gap: 6, marginTop: 4 },
  option: { borderWidth: 1, borderColor: colors.gray[300], borderRadius: 8, paddingHorizontal: 8, paddingVertical: 4 },
  optionSelected: { backgroundColor: "#2563EB", borderColor: "#2563EB" },
  optionText: { fontSize: 12 },
  primaryBtn: { backgroundColor: "#2563EB", borderRadius: 10, paddingVertical: 12, alignItems: "center", marginTop: 12 },
  primaryBtnDisabled: { opacity: 0.5 },
  primaryBtnText: { color: "#fff", fontWeight: "700" },
  successText: { color: "#15803D", marginTop: 8, fontSize: 13 },
  warningBox: { backgroundColor: "#FEF3C7", padding: 10, borderRadius: 8, marginTop: 8 },
  warningTitle: { fontWeight: "700", color: "#92400E", fontSize: 13 },
  warningBody: { color: "#92400E", fontSize: 12, marginTop: 4 },
});
