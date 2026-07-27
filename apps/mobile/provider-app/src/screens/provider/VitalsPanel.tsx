/**
 * VitalsPanel — Vitals capture within an encounter.
 */

import React, { useState, useCallback, useEffect } from "react";
import { View, StyleSheet } from "react-native";
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
  patientId: string;
}

export function VitalsPanel({ encounterId, patientId }: VitalsPanelProps) {
  const [selectedType, setSelectedType] = useState<VitalType>("BLOOD_PRESSURE");
  const [value, setValue] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [vitals, setVitals] = useState<VitalReading[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    getVitalsForEncounter(encounterId)
      .then((v) => {
        setLoadError(null);
        setVitals(v);
        v.forEach((vital) => encounterStore.getState().addVital(vital));
      })
      .catch(() => {
        // An empty grid with no message reads as "no vitals recorded this encounter" — an
        // affirmative clinical claim the app cannot make when the read failed.
        setLoadError("Vitals could not be loaded — not an absence of observations.");
      });
  }, [encounterId]);

  const handleRecord = useCallback(async () => {
    if (!value.trim()) return;
    setSaving(true);
    setError(null);
    const vitalOption = VITAL_OPTIONS.find((o) => o.value === selectedType);
    try {
      const vital = await recordVital({
        encounterId,
        patientId,
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

  return (
    <View testID="vitals-panel">
      {/* Input form */}
      <Card>
        <CardBody>
          <View style={styles.inputRow}>
            <Select
              label="Vital Type"
              value={selectedType}
              options={VITAL_OPTIONS.map((o) => ({ value: o.value, label: o.label }))}
              onChange={(v: string) => setSelectedType(v as VitalType)}
              testID="vital-type-select"
            />
            <TextField
              label="Value"
              value={value}
              onChange={setValue}
              placeholder="Enter value"
              testID="vital-value-input"
            />
            <Button
              title="Record"
              onPress={handleRecord}
              loading={saving}
              testID="record-vital-btn"
            />
          </View>
          {error ? <ErrorState title="Error" message={error} /> : null}
        </CardBody>
      </Card>

      {/* Recorded vitals */}
      {loadError ? <ErrorState title="Vitals unavailable" message={loadError} /> : null}
      <View style={styles.vitalsGrid}>
        {vitals.map((v) => (
          <VitalCard
            key={v.id}
            label={VITAL_OPTIONS.find((o) => o.value === v.type)?.label ?? v.type}
            value={String(v.value)}
            unit={v.unit}
            timestamp={v.measuredAt}
          />
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  inputRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "flex-end",
  },
  vitalsGrid: {
    marginTop: 12,
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
});
