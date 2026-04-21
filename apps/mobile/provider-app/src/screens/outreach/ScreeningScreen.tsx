/**
 * ScreeningScreen — Community screening and registration.
 *
 * Supports offline capture with sync queue visibility.
 */

import React, { useState, useCallback } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  TextField,
  Select,
  Badge,
  ErrorState,
} from "@impilo/mobile-design-system";
import { recordScreening, recordImmunization } from "../../services/householdService";
import { useOfflineStore, useSyncEngine } from "@impilo/mobile-offline";
import { useAppStore } from "../../stores/appStore";

const SCREENING_TYPES = [
  { value: "MALARIA", label: "Malaria RDT" },
  { value: "HIV", label: "HIV Testing" },
  { value: "TB", label: "TB Screening" },
  { value: "MALNUTRITION", label: "Malnutrition (MUAC)" },
  { value: "HYPERTENSION", label: "Hypertension" },
  { value: "DIABETES", label: "Diabetes" },
  { value: "ANTENATAL", label: "Antenatal Check" },
];

const VACCINES = [
  { value: "BCG", label: "BCG" },
  { value: "OPV", label: "Oral Polio" },
  { value: "PENTA", label: "Pentavalent" },
  { value: "PCV", label: "Pneumococcal" },
  { value: "ROTA", label: "Rotavirus" },
  { value: "MEASLES", label: "Measles" },
  { value: "HPV", label: "HPV" },
  { value: "COVID19", label: "COVID-19" },
];

export function ScreeningScreen() {
  const { isOnline } = useAppStore();
  const { pendingCount } = useSyncEngine();
  const [mode, setMode] = useState<"screening" | "immunization">("screening");
  const [patientId, setPatientId] = useState("");
  const [screeningType, setScreeningType] = useState("MALARIA");
  const [result, setResult] = useState("");
  const [vaccineName, setVaccineName] = useState("BCG");
  const [doseNumber, setDoseNumber] = useState("1");
  const [batchNumber, setBatchNumber] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const handleScreening = useCallback(async () => {
    if (!patientId.trim()) return;
    setSaving(true);
    setError(null);
    setSuccessMsg(null);
    try {
      await recordScreening({
        patientId,
        screeningType,
        results: { result, recorded_offline: !isOnline },
      });
      setSuccessMsg(`${screeningType} screening recorded`);
      setPatientId("");
      setResult("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record screening");
    } finally {
      setSaving(false);
    }
  }, [patientId, screeningType, result, isOnline]);

  const handleImmunization = useCallback(async () => {
    if (!patientId.trim() || !batchNumber.trim()) return;
    setSaving(true);
    setError(null);
    setSuccessMsg(null);
    const vaccine = VACCINES.find((v) => v.value === vaccineName);
    try {
      await recordImmunization({
        patientId,
        vaccineName: vaccine?.label ?? vaccineName,
        vaccineCode: vaccineName,
        doseNumber: parseInt(doseNumber, 10),
        batchNumber,
        site: "LEFT_DELTOID",
      });
      setSuccessMsg(`${vaccine?.label} dose ${doseNumber} recorded`);
      setPatientId("");
      setBatchNumber("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record immunization");
    } finally {
      setSaving(false);
    }
  }, [patientId, vaccineName, doseNumber, batchNumber]);

  return (
    <Screen>
      <Header title={mode === "screening" ? "Screenings" : "Immunizations"} />
      <ScrollView testID="screening-screen" style={styles.container}>
        {/* Mode toggle */}
        <View style={styles.modeToggle}>
          <Button
            title="Screening"
            variant={mode === "screening" ? "primary" : "outline"}
            onPress={() => setMode("screening")}
            testID="mode-screening"
          />
          <Button
            title="Immunization"
            variant={mode === "immunization" ? "primary" : "outline"}
            onPress={() => setMode("immunization")}
            testID="mode-immunization"
          />
          {pendingCount > 0 && (
            <Badge variant="secondary">{`${pendingCount} pending sync`}</Badge>
          )}
        </View>

        <Card>
          <CardBody>
            <TextField
              label="Patient ID or CPID"
              value={patientId}
              onChange={setPatientId}
              testID="screening-patient-id"
            />

            {mode === "screening" ? (
              <>
                <Select
                  label="Screening Type"
                  value={screeningType}
                  options={SCREENING_TYPES}
                  onChange={setScreeningType}
                  testID="screening-type"
                />
                <TextField
                  label="Result"
                  value={result}
                  onChange={setResult}
                  placeholder="Positive / Negative / Value"
                  testID="screening-result"
                />
                <Button
                  title="Record Screening"
                  onPress={handleScreening}
                  loading={saving}
                  fullWidth
                  testID="record-screening-btn"
                />
              </>
            ) : (
              <>
                <Select
                  label="Vaccine"
                  value={vaccineName}
                  options={VACCINES}
                  onChange={setVaccineName}
                  testID="vaccine-select"
                />
                <TextField
                  label="Dose Number"
                  value={doseNumber}
                  onChange={setDoseNumber}
                  testID="dose-number"
                />
                <TextField
                  label="Batch Number"
                  value={batchNumber}
                  onChange={setBatchNumber}
                  testID="batch-number"
                />
                <Button
                  title="Record Immunization"
                  onPress={handleImmunization}
                  loading={saving}
                  fullWidth
                  testID="record-immunization-btn"
                />
              </>
            )}

            {error && <ErrorState title="Error" message={error} />}
            {successMsg && (
              <Text style={styles.successText}>{successMsg}</Text>
            )}
          </CardBody>
        </Card>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  modeToggle: {
    flexDirection: "row",
    gap: 8,
    marginBottom: 16,
  },
  successText: {
    marginTop: 8,
    color: "#009739",
    fontWeight: "600",
  },
});
