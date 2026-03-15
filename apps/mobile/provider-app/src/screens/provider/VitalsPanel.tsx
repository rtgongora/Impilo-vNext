/**
 * VitalsPanel — Vitals capture within an encounter.
 */

import React, { useState, useCallback, useEffect } from "react";
import { Card, CardBody, Button, TextField, Select, VitalCard, ErrorState } from "@impilo/mobile-design-system";
import { recordVital, getVitalsForEncounter } from "../../services/vitalsService";
import { encounterStore } from "../../stores/encounterStore";
import type { VitalType, VitalReading } from "../../types";

const VITAL_OPTIONS: { value: VitalType; label: string; unit: string }[] = [
  { value: "BLOOD_PRESSURE", label: "Blood Pressure", unit: "mmHg" },
  { value: "HEART_RATE", label: "Heart Rate", unit: "bpm" },
  { value: "TEMPERATURE", label: "Temperature", unit: "°C" },
  { value: "RESPIRATORY_RATE", label: "Respiratory Rate", unit: "/min" },
  { value: "OXYGEN_SATURATION", label: "SpO2", unit: "%" },
  { value: "WEIGHT", label: "Weight", unit: "kg" },
  { value: "HEIGHT", label: "Height", unit: "cm" },
  { value: "BMI", label: "BMI", unit: "kg/m²" },
  { value: "BLOOD_GLUCOSE", label: "Blood Glucose", unit: "mmol/L" },
];

interface VitalsPanelProps {
  encounterId: string;
}

export function VitalsPanel({ encounterId }: VitalsPanelProps) {
  const [selectedType, setSelectedType] = useState<VitalType>("BLOOD_PRESSURE");
  const [value, setValue] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [vitals, setVitals] = useState<VitalReading[]>([]);

  useEffect(() => {
    getVitalsForEncounter(encounterId)
      .then((v) => {
        setVitals(v);
        v.forEach((vital) => encounterStore.getState().addVital(vital));
      })
      .catch(() => {});
  }, [encounterId]);

  const handleRecord = useCallback(async () => {
    if (!value.trim()) return;
    setSaving(true);
    setError(null);
    const vitalOption = VITAL_OPTIONS.find((o) => o.value === selectedType);
    try {
      const vital = await recordVital({
        encounterId,
        type: selectedType,
        value: parseFloat(value),
        unit: vitalOption?.unit ?? "",
      });
      encounterStore.getState().addVital(vital);
      setVitals((prev) => [...prev, vital]);
      setValue("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record vital");
    } finally {
      setSaving(false);
    }
  }, [encounterId, selectedType, value]);

  return React.createElement(
    "div",
    { "data-testid": "vitals-panel" },

    // Input form
    React.createElement(
      Card,
      null,
      React.createElement(
        CardBody,
        null,
        React.createElement(
          "div",
          { style: { display: "flex", gap: "8px", alignItems: "flex-end" } },
          React.createElement(Select, {
            label: "Vital Type",
            value: selectedType,
            options: VITAL_OPTIONS.map((o) => ({ value: o.value, label: o.label })),
            onChange: (v: string) => setSelectedType(v as VitalType),
            testID: "vital-type-select",
          }),
          React.createElement(TextField, {
            label: "Value",
            value,
            onChange: setValue,
            placeholder: "Enter value",
            testID: "vital-value-input",
          }),
          React.createElement(Button, {
            title: "Record",
            onPress: handleRecord,
            loading: saving,
            testID: "record-vital-btn",
          })
        ),
        error ? React.createElement(ErrorState, { title: "Error", message: error }) : null
      )
    ),

    // Recorded vitals
    React.createElement(
      "div",
      { style: { marginTop: "12px", display: "grid", gridTemplateColumns: "1fr 1fr", gap: "8px" } },
      vitals.map((v) =>
        React.createElement(VitalCard, {
          key: v.id,
          label: VITAL_OPTIONS.find((o) => o.value === v.type)?.label ?? v.type,
          value: String(v.value),
          unit: v.unit,
          timestamp: v.measuredAt,
        })
      )
    )
  );
}
