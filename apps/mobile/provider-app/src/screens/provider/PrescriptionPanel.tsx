/**
 * PrescriptionPanel — Rx creation within an encounter.
 */

import React, { useState, useCallback, useEffect } from "react";
import { View, StyleSheet, Text } from "react-native";
import { Card, CardBody, Button, TextField, RxCard, ErrorState, colors } from "@impilo/mobile-design-system";
import { createPrescription, cancelPrescription, getPrescriptionsForPatient } from "../../services/prescriptionService";
import { checkDrugInteractions } from "../../services/queueService";
import { encounterStore, useEncounterStore } from "../../stores/encounterStore";
import type { Prescription } from "../../types";

interface PrescriptionPanelProps {
  encounterId: string;
  patientId: string;
}

interface SafetyEval {
  blockReason: string | null;
  warnings: string[];
}

export function evaluatePrescriptionSafety(params: {
  existingPrescriptions: Prescription[];
  medication: string;
  dosage: string;
  frequency: string;
  duration: string;
  quantity: number;
  interactionRows: unknown[];
}): SafetyEval {
  const normalizedMedication = params.medication.trim().toLowerCase();
  if (!normalizedMedication) {
    return { blockReason: "Medication name is required.", warnings: [] };
  }
  if (!params.dosage.trim()) {
    return { blockReason: "Dosage is required.", warnings: [] };
  }
  if (!params.frequency.trim()) {
    return { blockReason: "Frequency is required.", warnings: [] };
  }
  if (!params.duration.trim()) {
    return { blockReason: "Duration is required.", warnings: [] };
  }
  if (!Number.isFinite(params.quantity) || params.quantity <= 0) {
    return { blockReason: "Quantity must be greater than zero.", warnings: [] };
  }

  const duplicate = params.existingPrescriptions.some((rx) => {
    const active = rx.status === "ACTIVE";
    return active && rx.medication.trim().toLowerCase() === normalizedMedication;
  });
  if (duplicate) {
    return {
      blockReason: "An active prescription for this medication already exists in the encounter.",
      warnings: [],
    };
  }

  let severe = false;
  const warnings: string[] = [];
  for (const row of params.interactionRows) {
    const r = row as Record<string, unknown>;
    const severity = String(r.severity ?? r.level ?? "").toUpperCase();
    const note = String(r.message ?? r.description ?? "").trim();
    if (severity === "HIGH" || severity === "SEVERE" || severity === "CRITICAL") {
      severe = true;
      continue;
    }
    if (severity === "MEDIUM" || severity === "MODERATE") {
      warnings.push(note || "Moderate interaction flagged.");
    }
  }

  if (severe) {
    return {
      blockReason:
        "Drug interaction check flagged a severe/contraindicated combination. Review in Clinical Tools before prescribing.",
      warnings,
    };
  }
  return { blockReason: null, warnings };
}

export function PrescriptionPanel({ encounterId, patientId }: PrescriptionPanelProps) {
  const { prescriptions } = useEncounterStore();
  const [medication, setMedication] = useState("");
  const [dosage, setDosage] = useState("");
  const [frequency, setFrequency] = useState("");
  const [duration, setDuration] = useState("");
  const [quantity, setQuantity] = useState("");
  const [instructions, setInstructions] = useState("");
  const [route, setRoute] = useState("PO");
  const [indication, setIndication] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [interactionNote, setInteractionNote] = useState<string | null>(null);
  const [interactionWarnings, setInteractionWarnings] = useState<string[]>([]);

  useEffect(() => {
    getPrescriptionsForPatient(patientId)
      .then((rxList) =>
        rxList
          .filter((rx) => rx.encounterId === encounterId)
          .forEach((rx) => encounterStore.getState().addPrescription(rx))
      )
      .catch(() => {});
  }, [encounterId, patientId]);

  const handleCreate = useCallback(async () => {
    if (!medication.trim() || !dosage.trim()) return;
    setSaving(true);
    setError(null);
    setInteractionNote(null);
    setInteractionWarnings([]);
    try {
      const meds = [
        ...prescriptions.map((p) => p.medication).filter(Boolean),
        medication.trim(),
      ];
      const interactionRows = await checkDrugInteractions(meds);

      const safety = evaluatePrescriptionSafety({
        existingPrescriptions: prescriptions,
        medication,
        dosage,
        frequency,
        duration,
        quantity: parseInt(quantity, 10) || 0,
        interactionRows,
      });
      if (safety.blockReason) {
        setError(safety.blockReason);
        setSaving(false);
        return;
      }
      if (safety.warnings.length > 0) {
        setInteractionWarnings(safety.warnings.slice(0, 3));
      }
      if (interactionRows.length > 0) {
        setInteractionNote(`Interaction check completed: ${interactionRows.length} potential interaction(s), no severe blocks.`);
      } else {
        setInteractionNote("Interaction check completed: no significant interactions found.");
      }

      const rx = await createPrescription({
        encounterId,
        patientId,
        medication,
        dosage,
        frequency,
        duration,
        quantity: parseInt(quantity, 10) || 0,
        instructions: instructions || undefined,
        route: route || "PO",
        indication: indication || undefined,
      });
      encounterStore.getState().addPrescription(rx);
      setMedication("");
      setDosage("");
      setFrequency("");
      setDuration("");
      setQuantity("");
      setInstructions("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create prescription");
    } finally {
      setSaving(false);
    }
  }, [encounterId, patientId, medication, dosage, frequency, duration, quantity, instructions]);

  return (
    <View testID="prescription-panel">
      <Card>
        <CardBody>
          <View style={styles.formGrid}>
            <TextField label="Medication" value={medication} onChange={setMedication} testID="rx-medication" />
            <TextField label="Dosage" value={dosage} onChange={setDosage} testID="rx-dosage" />
            <TextField label="Frequency" value={frequency} onChange={setFrequency} testID="rx-frequency" />
            <TextField label="Duration" value={duration} onChange={setDuration} testID="rx-duration" />
            <TextField label="Quantity" value={quantity} onChange={setQuantity} testID="rx-quantity" />
            <TextField label="Route" value={route} onChange={setRoute} testID="rx-route" />
            <TextField label="Indication" value={indication} onChange={setIndication} testID="rx-indication" />
            <TextField label="Instructions" value={instructions} onChange={setInstructions} testID="rx-instructions" />
          </View>
          <View style={styles.buttonContainer}>
            <Button
              title="Add Prescription"
              onPress={handleCreate}
              loading={saving}
              testID="add-rx-btn"
            />
          </View>
          {interactionNote ? <Text style={styles.interactionInfo}>{interactionNote}</Text> : null}
          {interactionWarnings.length > 0 ? (
            <View style={styles.warningBox}>
              {interactionWarnings.map((warning, idx) => (
                <Text key={`warning-${idx}`} style={styles.warningText}>{`- ${warning}`}</Text>
              ))}
            </View>
          ) : null}
          {error ? <ErrorState title="Error" message={error} /> : null}
        </CardBody>
      </Card>

      <View style={styles.prescriptionsList}>
        {prescriptions.map((rx) => (
          <View key={rx.id} style={styles.rxRow}>
            <RxCard
              medicationName={rx.medication}
              dosage={rx.dosage}
              frequency={rx.frequency}
              status={
                rx.status === "DISPENSED" || rx.status === "EXPIRED" ? "COMPLETED" : rx.status
              }
            />
            {rx.status === "ACTIVE" ? (
              <Button
                title="Cancel"
                variant="outline"
                onPress={async () => {
                  try {
                    const cancelled = await cancelPrescription(rx.id);
                    encounterStore.getState().addPrescription(cancelled);
                  } catch (err) {
                    setError(err instanceof Error ? err.message : "Failed to cancel prescription");
                  }
                }}
                testID={`cancel-rx-${rx.id}`}
              />
            ) : null}
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  formGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  buttonContainer: {
    marginTop: 12,
  },
  prescriptionsList: {
    marginTop: 12,
    gap: 8,
  },
  rxRow: {
    gap: 6,
  },
  interactionInfo: {
    fontSize: 12,
    color: colors.ui.success.text,
    marginTop: 8,
  },
  warningBox: {
    marginTop: 8,
    borderWidth: 1,
    borderColor: colors.ui.warning.main,
    backgroundColor: "#FFFBEB",
    borderRadius: 8,
    padding: 8,
    gap: 4,
  },
  warningText: {
    fontSize: 12,
    color: colors.ui.warning.text,
  },
});
