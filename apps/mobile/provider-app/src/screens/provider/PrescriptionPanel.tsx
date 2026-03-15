/**
 * PrescriptionPanel — Rx creation within an encounter.
 */

import React, { useState, useCallback, useEffect } from "react";
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

  return React.createElement(
    "div",
    { "data-testid": "prescription-panel" },

    React.createElement(
      Card,
      null,
      React.createElement(
        CardBody,
        null,
        React.createElement(
          "div",
          { style: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: "8px" } },
          React.createElement(TextField, { label: "Medication", value: medication, onChange: setMedication, testID: "rx-medication" }),
          React.createElement(TextField, { label: "Dosage", value: dosage, onChange: setDosage, testID: "rx-dosage" }),
          React.createElement(TextField, { label: "Frequency", value: frequency, onChange: setFrequency, testID: "rx-frequency" }),
          React.createElement(TextField, { label: "Duration", value: duration, onChange: setDuration, testID: "rx-duration" }),
          React.createElement(TextField, { label: "Quantity", value: quantity, onChange: setQuantity, testID: "rx-quantity" }),
          React.createElement(TextField, { label: "Instructions", value: instructions, onChange: setInstructions, testID: "rx-instructions" })
        ),
        React.createElement(
          "div",
          { style: { marginTop: "12px" } },
          React.createElement(Button, {
            title: "Add Prescription",
            onPress: handleCreate,
            loading: saving,
            testID: "add-rx-btn",
          })
        ),
        error ? React.createElement(ErrorState, { title: "Error", message: error }) : null
      )
    ),

    React.createElement(
      "div",
      { style: { marginTop: "12px" } },
      prescriptions.map((rx) =>
        React.createElement(RxCard, {
          key: rx.id,
          medication: rx.medication,
          dosage: rx.dosage,
          frequency: rx.frequency,
          status: rx.status,
        })
      )
    )
  );
}
