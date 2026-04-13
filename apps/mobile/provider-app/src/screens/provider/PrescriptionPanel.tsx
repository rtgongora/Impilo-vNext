/**
 * PrescriptionPanel — Rx creation within an encounter.
 */

import React, { useState, useCallback, useEffect } from "react";
import { View, StyleSheet } from "react-native";
import { Card, CardBody, Button, TextField, RxCard, ErrorState } from "@impilo/mobile-design-system";
import { createPrescription, getPrescriptionsForEncounter } from "../../services/prescriptionService";
import { encounterStore, useEncounterStore } from "../../stores/encounterStore";
import type { Prescription } from "../../types";

interface PrescriptionPanelProps {
  encounterId: string;
  patientId: string;
}

export function PrescriptionPanel({ encounterId, patientId }: PrescriptionPanelProps) {
  const { prescriptions } = useEncounterStore();
  const [medication, setMedication] = useState("");
  const [dosage, setDosage] = useState("");
  const [frequency, setFrequency] = useState("");
  const [duration, setDuration] = useState("");
  const [quantity, setQuantity] = useState("");
  const [instructions, setInstructions] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getPrescriptionsForEncounter(encounterId)
      .then((rxList) => rxList.forEach((rx) => encounterStore.getState().addPrescription(rx)))
      .catch(() => {});
  }, [encounterId]);

  const handleCreate = useCallback(async () => {
    if (!medication.trim() || !dosage.trim()) return;
    setSaving(true);
    setError(null);
    try {
      const rx = await createPrescription({
        encounterId,
        patientId,
        medication,
        dosage,
        frequency,
        duration,
        quantity: parseInt(quantity, 10) || 0,
        instructions: instructions || undefined,
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
          {error ? <ErrorState title="Error" message={error} /> : null}
        </CardBody>
      </Card>

      <View style={styles.prescriptionsList}>
        {prescriptions.map((rx) => (
          <RxCard
            key={rx.id}
            medicationName={rx.medication}
            dosage={rx.dosage}
            frequency={rx.frequency}
            status={
              rx.status === "DISPENSED" || rx.status === "EXPIRED" ? "COMPLETED" : rx.status
            }
          />
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
  },
});
