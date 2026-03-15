/**
 * ScreeningScreen — Community screening and registration.
 *
 * Supports offline capture with sync queue visibility.
 */

import React, { useState, useCallback } from "react";
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

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, { title: mode === "screening" ? "Screenings" : "Immunizations" }),
    React.createElement(
      "div",
      { "data-testid": "screening-screen", style: { padding: "16px" } },

      // Mode toggle
      React.createElement(
        "div",
        { style: { display: "flex", gap: "8px", marginBottom: "16px" } },
        React.createElement(Button, {
          title: "Screening",
          variant: mode === "screening" ? "primary" : "outline",
          onPress: () => setMode("screening"),
          testID: "mode-screening",
        }),
        React.createElement(Button, {
          title: "Immunization",
          variant: mode === "immunization" ? "primary" : "outline",
          onPress: () => setMode("immunization"),
          testID: "mode-immunization",
        }),
        pendingCount > 0
          ? React.createElement(Badge, { variant: "secondary", children: `${pendingCount} pending sync` })
          : null
      ),

      React.createElement(
        Card,
        null,
        React.createElement(
          CardBody,
          null,
          React.createElement(TextField, {
            label: "Patient ID or CPID",
            value: patientId,
            onChange: setPatientId,
            testID: "screening-patient-id",
          }),

          mode === "screening"
            ? React.createElement(
                React.Fragment,
                null,
                React.createElement(Select, {
                  label: "Screening Type",
                  value: screeningType,
                  options: SCREENING_TYPES,
                  onChange: setScreeningType,
                  testID: "screening-type",
                }),
                React.createElement(TextField, {
                  label: "Result",
                  value: result,
                  onChange: setResult,
                  placeholder: "Positive / Negative / Value",
                  testID: "screening-result",
                }),
                React.createElement(Button, {
                  title: "Record Screening",
                  onPress: handleScreening,
                  loading: saving,
                  fullWidth: true,
                  testID: "record-screening-btn",
                })
              )
            : React.createElement(
                React.Fragment,
                null,
                React.createElement(Select, {
                  label: "Vaccine",
                  value: vaccineName,
                  options: VACCINES,
                  onChange: setVaccineName,
                  testID: "vaccine-select",
                }),
                React.createElement(TextField, {
                  label: "Dose Number",
                  value: doseNumber,
                  onChange: setDoseNumber,
                  testID: "dose-number",
                }),
                React.createElement(TextField, {
                  label: "Batch Number",
                  value: batchNumber,
                  onChange: setBatchNumber,
                  testID: "batch-number",
                }),
                React.createElement(Button, {
                  title: "Record Immunization",
                  onPress: handleImmunization,
                  loading: saving,
                  fullWidth: true,
                  testID: "record-immunization-btn",
                })
              ),

          error ? React.createElement(ErrorState, { title: "Error", message: error }) : null,
          successMsg
            ? React.createElement("div", { style: { marginTop: "8px", color: "#059669", fontWeight: "600" } }, successMsg)
            : null
        )
      )
    )
  );
}
