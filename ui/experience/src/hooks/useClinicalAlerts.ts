/**
 * Clinical Decision Support — Rule-based alerts from patient data.
 *
 * Evaluates clinical rules against allergies, conditions, vitals,
 * and medications to generate actionable alerts. No ML required —
 * purely imperative rules based on clinical guidelines.
 */

import { useMemo } from "react";

export type AlertSeverity = "critical" | "warning" | "info";

export interface ClinicalAlert {
  id: string;
  severity: AlertSeverity;
  title: string;
  message: string;
  source: string;
}

interface PatientData {
  allergies?: Array<{ allergen?: string; severity?: string; status?: string }>;
  conditions?: Array<{ condition_name?: string; icd_code?: string; clinical_status?: string; severity?: string }>;
  vitals?: { systolic?: number; diastolic?: number; heart_rate?: number; temperature?: number; oxygen_saturation?: number; respiratory_rate?: number };
  medications?: Array<{ medication_name?: string; status?: string }>;
}

function evaluateRules(data: PatientData): ClinicalAlert[] {
  const alerts: ClinicalAlert[] = [];
  const { allergies = [], conditions = [], vitals, medications = [] } = data;

  // ── Vital sign rules ──────────────────────────────────────────
  if (vitals) {
    if (vitals.systolic && vitals.systolic >= 180) {
      alerts.push({ id: "bp-crisis", severity: "critical", title: "Hypertensive Crisis",
        message: `Systolic BP ${vitals.systolic} mmHg — immediate intervention required`, source: "Vitals" });
    } else if (vitals.systolic && vitals.systolic >= 140) {
      alerts.push({ id: "bp-high", severity: "warning", title: "Elevated Blood Pressure",
        message: `Systolic BP ${vitals.systolic} mmHg — monitor and reassess`, source: "Vitals" });
    }
    if (vitals.systolic && vitals.systolic < 90) {
      alerts.push({ id: "bp-low", severity: "critical", title: "Hypotension",
        message: `Systolic BP ${vitals.systolic} mmHg — assess for shock`, source: "Vitals" });
    }
    if (vitals.heart_rate && vitals.heart_rate > 120) {
      alerts.push({ id: "hr-tachy", severity: "warning", title: "Tachycardia",
        message: `Heart rate ${vitals.heart_rate} bpm — evaluate cause`, source: "Vitals" });
    }
    if (vitals.heart_rate && vitals.heart_rate < 50) {
      alerts.push({ id: "hr-brady", severity: "warning", title: "Bradycardia",
        message: `Heart rate ${vitals.heart_rate} bpm — assess for conduction abnormality`, source: "Vitals" });
    }
    if (vitals.oxygen_saturation && vitals.oxygen_saturation < 92) {
      alerts.push({ id: "spo2-low", severity: "critical", title: "Hypoxemia",
        message: `SpO2 ${vitals.oxygen_saturation}% — supplemental oxygen required`, source: "Vitals" });
    }
    if (vitals.temperature && vitals.temperature >= 38.5) {
      alerts.push({ id: "temp-fever", severity: "warning", title: "Fever",
        message: `Temperature ${vitals.temperature}°C — investigate source of infection`, source: "Vitals" });
    }
    if (vitals.temperature && vitals.temperature >= 40) {
      alerts.push({ id: "temp-high", severity: "critical", title: "Hyperthermia",
        message: `Temperature ${vitals.temperature}°C — immediate cooling measures`, source: "Vitals" });
    }
    if (vitals.respiratory_rate && vitals.respiratory_rate > 25) {
      alerts.push({ id: "rr-high", severity: "warning", title: "Tachypnea",
        message: `Respiratory rate ${vitals.respiratory_rate}/min — assess respiratory distress`, source: "Vitals" });
    }
  }

  // ── Allergy interaction rules ─────────────────────────────────
  const activeAllergies = allergies.filter((a) => a.status === "ACTIVE");
  const activeMeds = medications.filter((m) => m.status === "ACTIVE" || m.status === "PENDING");

  for (const allergy of activeAllergies) {
    const allergenLower = (allergy.allergen ?? "").toLowerCase();
    for (const med of activeMeds) {
      const medLower = (med.medication_name ?? "").toLowerCase();
      // Penicillin family check
      if (allergenLower.includes("penicillin") && (medLower.includes("amoxicillin") || medLower.includes("ampicillin") || medLower.includes("penicillin"))) {
        alerts.push({ id: `allergy-${allergenLower}-${medLower}`, severity: "critical",
          title: "Drug-Allergy Interaction", message: `Patient allergic to ${allergy.allergen} — active medication ${med.medication_name} may cause reaction`, source: "Allergies" });
      }
      // Sulfa check
      if (allergenLower.includes("sulfa") && (medLower.includes("sulfamethoxazole") || medLower.includes("bactrim"))) {
        alerts.push({ id: `allergy-sulfa-${medLower}`, severity: "critical",
          title: "Drug-Allergy Interaction", message: `Patient allergic to sulfa drugs — ${med.medication_name} contraindicated`, source: "Allergies" });
      }
    }
    // Severe allergy flag
    if (allergy.severity === "SEVERE" || allergy.severity === "LIFE_THREATENING") {
      alerts.push({ id: `severe-allergy-${allergenLower}`, severity: "warning",
        title: "Severe Allergy", message: `${allergy.allergen} — ${allergy.severity} severity. Verify all prescriptions.`, source: "Allergies" });
    }
  }

  // ── Condition-based rules ─────────────────────────────────────
  const activeConditions = conditions.filter((c) => c.clinical_status === "ACTIVE");
  for (const condition of activeConditions) {
    const name = (condition.condition_name ?? "").toLowerCase();
    if (name.includes("diabetes") || condition.icd_code?.startsWith("E11")) {
      alerts.push({ id: "dm-monitor", severity: "info", title: "Diabetes Mellitus",
        message: "Active diabetes — monitor glucose levels and HbA1c", source: "Conditions" });
    }
    if (name.includes("hypertension") || condition.icd_code?.startsWith("I10")) {
      alerts.push({ id: "htn-monitor", severity: "info", title: "Hypertension",
        message: "Active hypertension — ensure BP monitoring and medication compliance", source: "Conditions" });
    }
    if (name.includes("asthma") || condition.icd_code?.startsWith("J45")) {
      alerts.push({ id: "asthma-alert", severity: "info", title: "Asthma",
        message: "Active asthma — avoid beta-blockers and NSAIDs if possible", source: "Conditions" });
    }
    if (condition.severity === "SEVERE") {
      alerts.push({ id: `severe-${name}`, severity: "warning", title: "Severe Condition",
        message: `${condition.condition_name} — severity: SEVERE. Close monitoring required.`, source: "Conditions" });
    }
  }

  return alerts;
}

export function useClinicalAlerts(data: PatientData): ClinicalAlert[] {
  return useMemo(() => evaluateRules(data), [data]);
}
