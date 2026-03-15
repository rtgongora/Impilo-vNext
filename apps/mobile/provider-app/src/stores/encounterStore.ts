/**
 * Encounter Store — Active encounter workflow state.
 *
 * Tracks the current encounter being worked on, including vitals,
 * diagnoses, prescriptions, and lab orders added during the visit.
 */

import { createStore } from "zustand/vanilla";
import { useSyncExternalStore } from "react";
import type {
  Encounter,
  VitalReading,
  Diagnosis,
  Prescription,
  LabOrder,
  Referral,
} from "../types";

export interface EncounterState {
  activeEncounter: Encounter | null;
  vitals: VitalReading[];
  diagnoses: Diagnosis[];
  prescriptions: Prescription[];
  labOrders: LabOrder[];
  referrals: Referral[];
  isDirty: boolean;

  setActiveEncounter: (encounter: Encounter | null) => void;
  addVital: (vital: VitalReading) => void;
  removeVital: (id: string) => void;
  addDiagnosis: (diagnosis: Diagnosis) => void;
  removeDiagnosis: (id: string) => void;
  addPrescription: (prescription: Prescription) => void;
  removePrescription: (id: string) => void;
  addLabOrder: (order: LabOrder) => void;
  removeLabOrder: (id: string) => void;
  addReferral: (referral: Referral) => void;
  removeReferral: (id: string) => void;
  clearEncounter: () => void;
}

export const encounterStore = createStore<EncounterState>((set) => ({
  activeEncounter: null,
  vitals: [],
  diagnoses: [],
  prescriptions: [],
  labOrders: [],
  referrals: [],
  isDirty: false,

  setActiveEncounter: (encounter) =>
    set({
      activeEncounter: encounter,
      vitals: encounter?.vitals ?? [],
      diagnoses: [],
      prescriptions: [],
      labOrders: [],
      referrals: [],
      isDirty: false,
    }),

  addVital: (vital) =>
    set((state) => ({
      vitals: [...state.vitals, vital],
      isDirty: true,
    })),

  removeVital: (id) =>
    set((state) => ({
      vitals: state.vitals.filter((v) => v.id !== id),
      isDirty: true,
    })),

  addDiagnosis: (diagnosis) =>
    set((state) => ({
      diagnoses: [...state.diagnoses, diagnosis],
      isDirty: true,
    })),

  removeDiagnosis: (id) =>
    set((state) => ({
      diagnoses: state.diagnoses.filter((d) => d.id !== id),
      isDirty: true,
    })),

  addPrescription: (prescription) =>
    set((state) => ({
      prescriptions: [...state.prescriptions, prescription],
      isDirty: true,
    })),

  removePrescription: (id) =>
    set((state) => ({
      prescriptions: state.prescriptions.filter((p) => p.id !== id),
      isDirty: true,
    })),

  addLabOrder: (order) =>
    set((state) => ({
      labOrders: [...state.labOrders, order],
      isDirty: true,
    })),

  removeLabOrder: (id) =>
    set((state) => ({
      labOrders: state.labOrders.filter((o) => o.id !== id),
      isDirty: true,
    })),

  addReferral: (referral) =>
    set((state) => ({
      referrals: [...state.referrals, referral],
      isDirty: true,
    })),

  removeReferral: (id) =>
    set((state) => ({
      referrals: state.referrals.filter((r) => r.id !== id),
      isDirty: true,
    })),

  clearEncounter: () =>
    set({
      activeEncounter: null,
      vitals: [],
      diagnoses: [],
      prescriptions: [],
      labOrders: [],
      referrals: [],
      isDirty: false,
    }),
}));

export function useEncounterStore(): EncounterState {
  return useSyncExternalStore(
    encounterStore.subscribe,
    () => encounterStore.getState(),
    () => encounterStore.getState()
  );
}
