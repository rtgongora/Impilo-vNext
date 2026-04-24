import { create } from "zustand";

export interface Patient {
  cpid: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: "MALE" | "FEMALE" | "OTHER";
  nationalId?: string;
  facilityId?: string;
  allergies: string[];
  conditions: string[];
}

export interface Encounter {
  id: string;
  patientCpid: string;
  encounterType: "OPD" | "IPD" | "EMERGENCY" | "TELEHEALTH" | "HOME_VISIT";
  status: "PLANNED" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
  startedAt: string;
  chiefComplaint?: string;
  notes: string[];
  vitals: VitalSign[];
  diagnoses: Diagnosis[];
  prescriptions: Prescription[];
}

export interface VitalSign {
  type: "BP" | "TEMP" | "HR" | "RR" | "SPO2" | "WEIGHT" | "HEIGHT" | "BMI";
  value: string;
  unit: string;
  recordedAt: string;
}

export interface Diagnosis {
  code: string;
  display: string;
  type: "PRIMARY" | "SECONDARY" | "DIFFERENTIAL";
}

export interface Prescription {
  medication: string;
  dosage: string;
  frequency: string;
  duration: string;
  status: "ACTIVE" | "COMPLETED" | "CANCELLED";
}

interface EhrState {
  activePatient: Patient | null;
  activeEncounter: Encounter | null;
  recentPatients: Patient[];
  setActivePatient: (patient: Patient | null) => void;
  setActiveEncounter: (encounter: Encounter | null) => void;
  addRecentPatient: (patient: Patient) => void;
  startEncounter: (patientCpid: string, type: Encounter["encounterType"]) => void;
}

export const useEhrStore = create<EhrState>((set) => ({
  activePatient: null,
  activeEncounter: null,
  recentPatients: [],

  setActivePatient: (patient) =>
    set({ activePatient: patient, activeEncounter: null }),

  setActiveEncounter: (encounter) => set({ activeEncounter: encounter }),

  addRecentPatient: (patient) =>
    set((state) => ({
      recentPatients: [
        patient,
        ...state.recentPatients.filter((p) => p.cpid !== patient.cpid),
      ].slice(0, 10),
    })),

  startEncounter: (patientCpid, type) => {
    const encounter: Encounter = {
      id: crypto.randomUUID(),
      patientCpid,
      encounterType: type,
      status: "IN_PROGRESS",
      startedAt: new Date().toISOString(),
      notes: [],
      vitals: [],
      diagnoses: [],
      prescriptions: [],
    };
    set({ activeEncounter: encounter });
  },
}));
