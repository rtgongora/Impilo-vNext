/**
 * DischargeScreen — Mobile discharge workflow.
 *
 * Presents a form for completing a patient discharge with all required fields.
 */

import React, { useState, useCallback } from "react";
import { colors } from "@impilo/mobile-design-system";
import {
  View,
  Text,
  ScrollView,
  Pressable,
  TextInput,
  StyleSheet,
  ActivityIndicator,
  Alert,
} from "react-native";
import {
  submitDischarge,
  type DischargeInput,
  type DischargeType,
} from "../../services/dischargeService";

const DISCHARGE_TYPES: { value: DischargeType; label: string }[] = [
  { value: "routine", label: "Routine" },
  { value: "against_medical_advice", label: "Against Medical Advice" },
  { value: "transfer", label: "Transfer" },
  { value: "deceased", label: "Deceased" },
  { value: "administrative", label: "Administrative" },
];

interface DischargeScreenProps {
  encounterId: string;
  onGoBack: () => void;
  onDischargeComplete?: () => void;
}

export function DischargeScreen({
  encounterId,
  onGoBack,
  onDischargeComplete,
}: DischargeScreenProps) {
  const [dischargeType, setDischargeType] = useState<DischargeType>("routine");
  const [diagnosis, setDiagnosis] = useState("");
  const [treatmentSummary, setTreatmentSummary] = useState("");
  const [followUp, setFollowUp] = useState("");
  const [medicationsAtDischarge, setMedicationsAtDischarge] = useState("");
  const [patientInstructions, setPatientInstructions] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = useCallback(async () => {
    if (!diagnosis.trim()) {
      Alert.alert("Validation", "Diagnosis is required.");
      return;
    }
    if (!treatmentSummary.trim()) {
      Alert.alert("Validation", "Treatment summary is required.");
      return;
    }

    setSubmitting(true);
    try {
      const data: DischargeInput = {
        discharge_type: dischargeType,
        diagnosis: diagnosis.trim(),
        treatment_summary: treatmentSummary.trim(),
        follow_up: followUp.trim(),
        medications_at_discharge: medicationsAtDischarge.trim(),
        patient_instructions: patientInstructions.trim(),
      };
      await submitDischarge(encounterId, data);
      Alert.alert("Success", "Discharge submitted successfully.", [
        { text: "OK", onPress: onDischargeComplete },
      ]);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to submit discharge";
      Alert.alert("Error", message);
    } finally {
      setSubmitting(false);
    }
  }, [
    encounterId,
    dischargeType,
    diagnosis,
    treatmentSummary,
    followUp,
    medicationsAtDischarge,
    patientInstructions,
    onDischargeComplete,
  ]);

  return (
    <ScrollView
      testID="discharge-screen"
      style={styles.container}
      keyboardShouldPersistTaps="handled"
    >
      {/* Go Back */}
      <Pressable
        onPress={onGoBack}
        style={styles.goBack}
        accessibilityRole="button"
        accessibilityLabel="Go back"
        testID="go-back-btn"
      >
        <Text style={styles.goBackText}>{"← Go Back"}</Text>
      </Pressable>

      <Text style={styles.title}>Patient Discharge</Text>

      {/* Discharge Type Picker */}
      <Text style={styles.label}>Discharge Type</Text>
      <View style={styles.pickerRow}>
        {DISCHARGE_TYPES.map((dt) => (
          <Pressable
            key={dt.value}
            onPress={() => setDischargeType(dt.value)}
            style={[
              styles.pickerOption,
              dischargeType === dt.value && styles.pickerOptionSelected,
            ]}
            accessibilityRole="button"
            accessibilityState={{ selected: dischargeType === dt.value }}
            testID={`discharge-type-${dt.value}`}
          >
            <Text
              style={[
                styles.pickerOptionText,
                dischargeType === dt.value && styles.pickerOptionTextSelected,
              ]}
            >
              {dt.label}
            </Text>
          </Pressable>
        ))}
      </View>

      {/* Diagnosis */}
      <Text style={styles.label}>Diagnosis *</Text>
      <TextInput
        style={styles.input}
        value={diagnosis}
        onChangeText={setDiagnosis}
        placeholder="Final diagnosis"
        multiline
        numberOfLines={2}
        testID="diagnosis-input"
        accessibilityLabel="Diagnosis"
      />

      {/* Treatment Summary */}
      <Text style={styles.label}>Treatment Summary *</Text>
      <TextInput
        style={[styles.input, styles.inputTall]}
        value={treatmentSummary}
        onChangeText={setTreatmentSummary}
        placeholder="Summary of treatment provided"
        multiline
        numberOfLines={4}
        testID="treatment-summary-input"
        accessibilityLabel="Treatment summary"
      />

      {/* Follow-up */}
      <Text style={styles.label}>Follow-up Instructions</Text>
      <TextInput
        style={styles.input}
        value={followUp}
        onChangeText={setFollowUp}
        placeholder="Follow-up appointments and timeline"
        multiline
        numberOfLines={2}
        testID="follow-up-input"
        accessibilityLabel="Follow-up instructions"
      />

      {/* Medications at Discharge */}
      <Text style={styles.label}>Medications at Discharge</Text>
      <TextInput
        style={[styles.input, styles.inputTall]}
        value={medicationsAtDischarge}
        onChangeText={setMedicationsAtDischarge}
        placeholder="List medications prescribed at discharge"
        multiline
        numberOfLines={4}
        testID="medications-input"
        accessibilityLabel="Medications at discharge"
      />

      {/* Patient Instructions */}
      <Text style={styles.label}>Patient Instructions</Text>
      <TextInput
        style={[styles.input, styles.inputTall]}
        value={patientInstructions}
        onChangeText={setPatientInstructions}
        placeholder="Instructions for the patient post-discharge"
        multiline
        numberOfLines={4}
        testID="patient-instructions-input"
        accessibilityLabel="Patient instructions"
      />

      {/* Submit */}
      <Pressable
        onPress={handleSubmit}
        disabled={submitting}
        style={[styles.submitButton, submitting && styles.submitButtonDisabled]}
        accessibilityRole="button"
        accessibilityLabel="Submit discharge"
        testID="submit-discharge-btn"
      >
        {submitting ? (
          <ActivityIndicator color="#FFFFFF" />
        ) : (
          <Text style={styles.submitButtonText}>Submit Discharge</Text>
        )}
      </Pressable>

      {/* Bottom spacing */}
      <View style={styles.bottomSpacer} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
    backgroundColor: "#FFFFFF",
  },
  goBack: {
    paddingVertical: 8,
    marginBottom: 8,
  },
  goBackText: {
    fontSize: 16,
    color: "#2563EB",
    fontWeight: "600",
  },
  title: {
    fontSize: 22,
    fontWeight: "700",
    color: colors.gray[900],
    marginBottom: 20,
  },
  label: {
    fontSize: 14,
    fontWeight: "600",
    color: colors.gray[700],
    marginBottom: 6,
    marginTop: 12,
  },
  pickerRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  pickerOption: {
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: colors.gray[300],
    backgroundColor: colors.gray[50],
  },
  pickerOptionSelected: {
    borderColor: "#2563EB",
    backgroundColor: "#EFF6FF",
  },
  pickerOptionText: {
    fontSize: 13,
    color: colors.gray[500],
  },
  pickerOptionTextSelected: {
    color: "#2563EB",
    fontWeight: "600",
  },
  input: {
    borderWidth: 1,
    borderColor: colors.gray[300],
    borderRadius: 8,
    padding: 12,
    fontSize: 15,
    color: colors.gray[900],
    backgroundColor: colors.gray[50],
    textAlignVertical: "top",
  },
  inputTall: {
    minHeight: 100,
  },
  submitButton: {
    marginTop: 24,
    backgroundColor: "#2563EB",
    borderRadius: 10,
    paddingVertical: 14,
    alignItems: "center",
    justifyContent: "center",
  },
  submitButtonDisabled: {
    backgroundColor: "#93C5FD",
  },
  submitButtonText: {
    color: "#FFFFFF",
    fontSize: 16,
    fontWeight: "700",
  },
  bottomSpacer: {
    height: 40,
  },
});
