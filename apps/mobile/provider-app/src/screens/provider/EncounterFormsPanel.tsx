/**
 * Encounter Structured Forms panel (mobile parity).
 *
 * Resolves the patient/cadre/setting-aware form set, lets the provider open a form, capture structured
 * answers, and submit — persisted server-side (draft → submit) via canonical PCT. Prohibited forms are
 * shown greyed with their reason (no fake completions). Offline: submissions ride the existing offline
 * clinical queue when the network is unavailable (the BFF/PCT path is idempotent).
 */

import React, { useMemo, useState } from "react";
import { View, Text, Pressable, TextInput, ScrollView, StyleSheet } from "react-native";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  resolveEncounterForms,
  getFormCatalog,
  listEncounterFormResponses,
  submitEncounterForm,
  type FormObligation,
  type ResolveParams,
} from "../../services/encounterFormsService";

interface ClinicalField {
  id: string;
  label: string;
  kind: string;
  unit?: string;
  options?: { code: string; display: string }[];
  validation?: { required?: boolean };
}
interface ClinicalSection {
  id: string;
  title: string;
  fields: ClinicalField[];
}
interface ClinicalDefinition {
  id: string;
  title: string;
  sections: ClinicalSection[];
}

export interface EncounterFormsPanelProps {
  encounterId: string;
  resolveParams: ResolveParams;
}

export function EncounterFormsPanel({ encounterId, resolveParams }: EncounterFormsPanelProps) {
  const qc = useQueryClient();
  const resolution = useQuery({
    queryKey: ["mobile-encounter-forms-resolve", encounterId, resolveParams],
    queryFn: () => resolveEncounterForms(encounterId, resolveParams),
  });
  const catalog = useQuery({ queryKey: ["mobile-forms-catalog"], queryFn: getFormCatalog });
  const responses = useQuery({
    queryKey: ["mobile-encounter-form-responses", encounterId],
    queryFn: () => listEncounterFormResponses(encounterId),
  });
  const submit = useMutation({
    mutationFn: ({ formKey, answers }: { formKey: string; answers: Record<string, unknown> }) =>
      submitEncounterForm(encounterId, formKey, answers),
    onSuccess: () => {
      setSubmitted(true);
      void qc.invalidateQueries({ queryKey: ["mobile-encounter-form-responses", encounterId] });
    },
  });

  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [answers, setAnswers] = useState<Record<string, unknown>>({});
  const [submitted, setSubmitted] = useState(false);

  const definitionsByKey = useMemo(() => {
    const map = new Map<string, ClinicalDefinition>();
    for (const e of catalog.data ?? []) {
      try {
        map.set(e.formKey, JSON.parse(e.definitionJson) as ClinicalDefinition);
      } catch {
        // skip malformed
      }
    }
    return map;
  }, [catalog.data]);

  const selected = selectedKey ? definitionsByKey.get(selectedKey) ?? null : null;

  const openForm = (formKey: string) => {
    setSelectedKey(formKey);
    setAnswers({});
    setSubmitted(false);
  };

  if (resolution.isLoading) {
    return <Text style={styles.muted}>Resolving encounter forms…</Text>;
  }
  if (resolution.isError || !resolution.data) {
    return (
      <Text style={styles.error} accessibilityRole="alert">
        Could not resolve encounter forms.
      </Text>
    );
  }
  const res = resolution.data;

  const group = (title: string, kind: string, items: FormObligation[], selectable: boolean) => {
    if (!items.length) return null;
    return (
      <View style={styles.group} testID={`forms-group-${kind}`}>
        <Text style={styles.groupTitle}>{title}</Text>
        {items.map((o) => (
          <Pressable
            key={o.formKey}
            disabled={!selectable || !definitionsByKey.has(o.formKey)}
            onPress={() => selectable && openForm(o.formKey)}
            style={[styles.row, kind === "PROHIBITED" && styles.rowProhibited]}
            testID={`form-row-${o.formKey}`}
          >
            <Text style={styles.rowName}>{o.name}</Text>
            <Text style={styles.rowMeta}>
              {kind === "PROHIBITED" ? (o.reason ?? "Not permitted") : `v${o.formVersion}`}
            </Text>
          </Pressable>
        ))}
      </View>
    );
  };

  return (
    <ScrollView testID="encounter-forms-panel">
      {group("Mandatory", "MANDATORY", res.mandatory, true)}
      {group("Recommended", "RECOMMENDED", res.recommended, true)}
      {group("Optional", "OPTIONAL", res.optional, true)}
      {group("Requires countersignature", "COUNTERSIGN_REQUIRED", res.countersignRequired, true)}
      {group("Not permitted", "PROHIBITED", res.prohibited, false)}

      {(responses.data?.length ?? 0) > 0 && (
        <View style={styles.group} testID="forms-submitted">
          <Text style={styles.groupTitle}>Documented this encounter</Text>
          {responses.data!.map((r) => (
            <Text key={r.responseId} style={styles.submittedRow}>
              {r.formKey} — {r.status}
            </Text>
          ))}
        </View>
      )}

      {selected && (
        <View style={styles.form} testID="form-fields">
          <Text style={styles.formTitle}>{selected.title}</Text>
          {selected.sections.map((sec) => (
            <View key={sec.id} style={styles.section}>
              <Text style={styles.sectionTitle}>{sec.title}</Text>
              {sec.fields.map((f) => renderField(f, answers, setAnswers))}
            </View>
          ))}
          {submitted ? (
            <Text style={styles.success} accessibilityRole="text">
              Submitted and recorded on the encounter.
            </Text>
          ) : (
            <Pressable
              disabled={submit.isPending}
              onPress={() => submit.mutate({ formKey: selectedKey!, answers })}
              style={styles.submit}
              testID="form-submit"
            >
              <Text style={styles.submitText}>{submit.isPending ? "Submitting…" : "Submit & record"}</Text>
            </Pressable>
          )}
          {submit.isError && (
            <Text style={styles.error} accessibilityRole="alert">
              Submission failed.
            </Text>
          )}
        </View>
      )}
    </ScrollView>
  );
}

function renderField(
  f: ClinicalField,
  answers: Record<string, unknown>,
  setAnswers: React.Dispatch<React.SetStateAction<Record<string, unknown>>>,
) {
  const set = (v: unknown) => setAnswers((a) => ({ ...a, [f.id]: v }));
  const label = `${f.label}${f.validation?.required ? " *" : ""}`;
  if (f.kind === "coded_single" && f.options) {
    return (
      <View key={f.id} style={styles.field}>
        <Text style={styles.label}>{label}</Text>
        <View style={styles.options}>
          {f.options.map((opt) => (
            <Pressable
              key={opt.code}
              onPress={() => set(opt.code)}
              style={[styles.option, answers[f.id] === opt.code && styles.optionSelected]}
              testID={`field-${f.id}-opt-${opt.code}`}
            >
              <Text style={styles.optionText}>{opt.display}</Text>
            </Pressable>
          ))}
        </View>
      </View>
    );
  }
  const keyboard = f.kind === "numeric_with_unit" ? "numeric" : "default";
  return (
    <View key={f.id} style={styles.field}>
      <Text style={styles.label}>
        {label}
        {f.unit ? ` (${f.unit})` : ""}
      </Text>
      <TextInput
        testID={`field-${f.id}`}
        value={answers[f.id] === undefined || answers[f.id] === null ? "" : String(answers[f.id])}
        keyboardType={keyboard as "numeric" | "default"}
        onChangeText={(t: string) => set(f.kind === "numeric_with_unit" && t !== "" ? Number(t) : t)}
        style={styles.input}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  muted: { color: "#64748b", fontSize: 12 },
  error: { color: "#b91c1c", fontSize: 12 },
  success: { color: "#15803d", fontSize: 12, marginTop: 8 },
  group: { marginBottom: 12 },
  groupTitle: { fontSize: 11, fontWeight: "600", textTransform: "uppercase", color: "#64748b", marginBottom: 4 },
  row: { flexDirection: "row", justifyContent: "space-between", padding: 10, borderRadius: 8, borderWidth: 1, borderColor: "#e2e8f0", marginBottom: 4 },
  rowProhibited: { opacity: 0.6 },
  rowName: { fontSize: 13, color: "#0f172a" },
  rowMeta: { fontSize: 10, color: "#64748b" },
  submittedRow: { fontSize: 11, color: "#0f172a" },
  form: { marginTop: 8, padding: 10, borderWidth: 1, borderColor: "#e2e8f0", borderRadius: 8 },
  formTitle: { fontSize: 14, fontWeight: "600", marginBottom: 8 },
  section: { marginBottom: 10 },
  sectionTitle: { fontSize: 12, fontWeight: "600", marginBottom: 4 },
  field: { marginBottom: 8 },
  label: { fontSize: 12, color: "#334155", marginBottom: 2 },
  input: { borderWidth: 1, borderColor: "#cbd5e1", borderRadius: 6, padding: 6, fontSize: 13 },
  options: { flexDirection: "row", flexWrap: "wrap" },
  option: { paddingVertical: 4, paddingHorizontal: 8, borderRadius: 6, borderWidth: 1, borderColor: "#cbd5e1", marginRight: 4, marginBottom: 4 },
  optionSelected: { backgroundColor: "#dbeafe", borderColor: "#3b82f6" },
  optionText: { fontSize: 12 },
  submit: { backgroundColor: "#2563eb", padding: 10, borderRadius: 8, alignItems: "center", marginTop: 6 },
  submitText: { color: "white", fontSize: 13, fontWeight: "600" },
});
