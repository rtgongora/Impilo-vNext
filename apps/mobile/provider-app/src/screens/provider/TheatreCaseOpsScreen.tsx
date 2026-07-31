/**
 * TheatreCaseOpsScreen — Wave M4 case-scoped ops (ops-only; does not replace TheatreCaseScreen).
 *
 * Anaesthesia chart (incl. vitals/monitoring channels) + CSSD instrument sets +
 * controlled-drug register. List GET failures surface unavailable — never empty registers.
 */
import React, { useState } from "react";
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  RefreshControl,
  TextInput,
} from "react-native";
import { Screen, Header, Badge, LoadingSpinner, ErrorState, Button, colors } from "@impilo/mobile-design-system";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getAnaesthesiaChart,
  addAnaesthesiaChartEntry,
  listInstrumentSets,
  issueInstrumentSet,
  returnInstrumentSet,
  listControlledDrugs,
  recordControlledDrug,
} from "../../services/theatreOpsService";

type Props = { caseId: string; onBack: () => void };

function str(v: unknown, fallback = "—"): string {
  if (v == null || v === "") return fallback;
  return String(v);
}

export function TheatreCaseOpsScreen({ caseId, onBack }: Props) {
  const qc = useQueryClient();
  const [channel, setChannel] = useState("VITAL");
  const [label, setLabel] = useState("");
  const [valueText, setValueText] = useState("");
  const [unit, setUnit] = useState("");
  const [setCode, setSetCode] = useState("");
  const [drugCode, setDrugCode] = useState("");
  const [witness, setWitness] = useState("");
  const [drugAction, setDrugAction] = useState("ADMINISTER");

  const chartQuery = useQuery({
    queryKey: ["theatre-ops", "anaesthesia", caseId],
    queryFn: () => getAnaesthesiaChart(caseId),
    retry: false,
  });
  const setsQuery = useQuery({
    queryKey: ["theatre-ops", "instrument-sets", caseId],
    queryFn: () => listInstrumentSets(caseId),
    retry: false,
  });
  const drugsQuery = useQuery({
    queryKey: ["theatre-ops", "controlled-drugs", caseId],
    queryFn: () => listControlledDrugs(caseId),
    retry: false,
  });

  const addEntry = useMutation({
    mutationFn: () =>
      addAnaesthesiaChartEntry(caseId, {
        channel,
        label,
        ...(valueText ? { valueText } : {}),
        ...(unit ? { unit } : {}),
      }),
    onSuccess: () => {
      setLabel("");
      setValueText("");
      setUnit("");
      void qc.invalidateQueries({ queryKey: ["theatre-ops", "anaesthesia", caseId] });
    },
  });
  const issueSet = useMutation({
    mutationFn: () => issueInstrumentSet(caseId, { setCode }),
    onSuccess: () => {
      setSetCode("");
      void qc.invalidateQueries({ queryKey: ["theatre-ops", "instrument-sets", caseId] });
    },
  });
  const returnSet = useMutation({
    mutationFn: () => returnInstrumentSet(caseId, {}),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["theatre-ops", "instrument-sets", caseId] }),
  });
  const recordDrug = useMutation({
    mutationFn: () =>
      recordControlledDrug(caseId, {
        action: drugAction,
        itemCode: drugCode,
        witnessProvider: witness,
      }),
    onSuccess: () => {
      setDrugCode("");
      setWitness("");
      void qc.invalidateQueries({ queryKey: ["theatre-ops", "controlled-drugs", caseId] });
    },
  });

  const refreshAll = () => {
    void chartQuery.refetch();
    void setsQuery.refetch();
    void drugsQuery.refetch();
  };

  if (chartQuery.isLoading && setsQuery.isLoading && drugsQuery.isLoading) {
    return (
      <Screen>
        <Header title="Case ops" onBack={onBack} />
        <LoadingSpinner />
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="Case ops" onBack={onBack} subtitle={caseId} />
      <ScrollView
        testID="theatre-case-ops"
        style={styles.container}
        refreshControl={
          <RefreshControl
            refreshing={chartQuery.isRefetching || setsQuery.isRefetching || drugsQuery.isRefetching}
            onRefresh={refreshAll}
          />
        }
      >
        <Text style={styles.section}>Anaesthesia chart</Text>
        {chartQuery.isError ? (
          <ErrorState
            testID="theatre-anaesthesia-error"
            title="Anaesthesia chart unavailable"
            message="Could not load chart entries. Do not treat this as no vitals / agents recorded."
            onRetry={() => void chartQuery.refetch()}
          />
        ) : (chartQuery.data ?? []).length === 0 ? (
          <Text testID="theatre-anaesthesia-empty" style={styles.empty}>
            No chart entries yet. Blood administered appears as a projected MADI channel.
          </Text>
        ) : (
          (chartQuery.data ?? []).map((e, i) => (
            <View key={i} style={styles.card} testID={`theatre-anaesthesia-row-${i}`}>
              <View style={styles.metaRow}>
                <Badge label={str(e.channel)} variant="info" />
                {e.projected ? <Badge label="projected · MADI" variant="warning" /> : null}
              </View>
              <Text style={styles.cardTitle}>{str(e.label)}</Text>
              <Text style={styles.cardMeta}>
                {str(e.value_text ?? e.valueText ?? e.value_numeric ?? e.valueNumeric, "")}
                {e.unit ? ` ${String(e.unit)}` : ""}
              </Text>
            </View>
          ))
        )}
        <View style={styles.form}>
          <TextInput
            testID="theatre-anaesthesia-channel"
            style={styles.input}
            value={channel}
            onChangeText={setChannel}
            placeholder="Channel (VITAL, AGENT, …)"
          />
          <TextInput
            testID="theatre-anaesthesia-label"
            style={styles.input}
            value={label}
            onChangeText={setLabel}
            placeholder="Label"
          />
          <TextInput
            testID="theatre-anaesthesia-value"
            style={styles.input}
            value={valueText}
            onChangeText={setValueText}
            placeholder="Value"
          />
          <TextInput
            testID="theatre-anaesthesia-unit"
            style={styles.input}
            value={unit}
            onChangeText={setUnit}
            placeholder="Unit"
          />
          <Button
            testID="theatre-anaesthesia-add"
            title="Add chart entry"
            onPress={() => addEntry.mutate()}
            disabled={!label || addEntry.isPending}
          />
          {addEntry.isError ? (
            <Text style={styles.danger}>Chart entry failed — nothing was recorded.</Text>
          ) : null}
        </View>

        <Text style={styles.section}>CSSD instrument sets</Text>
        {setsQuery.isError ? (
          <ErrorState
            testID="theatre-instrument-sets-error"
            title="Instrument sets unavailable"
            message="Could not load CSSD sets. Do not treat this as an empty sterile register."
            onRetry={() => void setsQuery.refetch()}
          />
        ) : (setsQuery.data ?? []).length === 0 ? (
          <Text testID="theatre-instrument-sets-empty" style={styles.empty}>
            No instrument sets issued for this case.
          </Text>
        ) : (
          (setsQuery.data ?? []).map((s, i) => (
            <View key={str(s.id, `set-${i}`)} style={styles.card}>
              <Text style={styles.cardTitle}>{str(s.set_code ?? s.setCode ?? s.code)}</Text>
              <Text style={styles.cardMeta}>{str(s.status)}</Text>
            </View>
          ))
        )}
        <View style={styles.form}>
          <TextInput
            testID="theatre-instrument-set-code"
            style={styles.input}
            value={setCode}
            onChangeText={setSetCode}
            placeholder="Set code"
          />
          <Button
            testID="theatre-instrument-set-issue"
            title="Issue set"
            onPress={() => issueSet.mutate()}
            disabled={!setCode || issueSet.isPending}
          />
          <Button
            testID="theatre-instrument-set-return"
            title="Return sets"
            variant="secondary"
            onPress={() => returnSet.mutate()}
            disabled={returnSet.isPending}
          />
        </View>

        <Text style={styles.section}>Controlled drugs register</Text>
        {drugsQuery.isError ? (
          <ErrorState
            testID="theatre-controlled-drugs-error"
            title="Controlled-drug register unavailable"
            message="Could not load the register. Do not treat this as an empty drug book."
            onRetry={() => void drugsQuery.refetch()}
          />
        ) : (drugsQuery.data ?? []).length === 0 ? (
          <Text testID="theatre-controlled-drugs-empty" style={styles.empty}>
            No controlled-drug entries for this case.
          </Text>
        ) : (
          (drugsQuery.data ?? []).map((d, i) => (
            <View key={str(d.id, `drug-${i}`)} style={styles.card}>
              <Text style={styles.cardTitle}>{str(d.item_code ?? d.itemCode)}</Text>
              <Text style={styles.cardMeta}>
                {str(d.action)} · witness {str(d.witness_provider ?? d.witnessProvider)}
              </Text>
            </View>
          ))
        )}
        <View style={[styles.form, styles.last]}>
          <TextInput
            testID="theatre-drug-action"
            style={styles.input}
            value={drugAction}
            onChangeText={setDrugAction}
            placeholder="Action (ADMINISTER)"
          />
          <TextInput
            testID="theatre-drug-code"
            style={styles.input}
            value={drugCode}
            onChangeText={setDrugCode}
            placeholder="Item code"
          />
          <TextInput
            testID="theatre-drug-witness"
            style={styles.input}
            value={witness}
            onChangeText={setWitness}
            placeholder="Witness provider id"
          />
          <Button
            testID="theatre-drug-record"
            title="Record controlled drug"
            onPress={() => recordDrug.mutate()}
            disabled={!drugCode || !witness || recordDrug.isPending}
          />
          {recordDrug.isError ? (
            <Text style={styles.danger}>Drug register write failed — nothing was recorded.</Text>
          ) : null}
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  section: {
    marginTop: 8,
    marginBottom: 8,
    fontSize: 16,
    fontWeight: "700",
    color: colors.gray[900],
  },
  empty: { padding: 16, textAlign: "center", color: colors.gray[500], fontSize: 14 },
  danger: { color: "#991B1B", fontSize: 13 },
  card: {
    backgroundColor: "#FFF",
    borderRadius: 10,
    borderWidth: 1,
    borderColor: colors.gray[200],
    padding: 12,
    marginBottom: 8,
    gap: 4,
  },
  cardTitle: { fontSize: 14, fontWeight: "700", color: colors.gray[900] },
  cardMeta: { fontSize: 12, color: colors.gray[500] },
  metaRow: { flexDirection: "row", gap: 6, flexWrap: "wrap" },
  form: { gap: 8, marginBottom: 16 },
  last: { marginBottom: 32 },
  input: {
    borderWidth: 1,
    borderColor: colors.gray[300],
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 14,
    color: colors.gray[900],
    backgroundColor: "#FFF",
  },
});
