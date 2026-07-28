/**
 * Persona table for golden-journey specs — mirrors docs/demo/persona-truth-pack.md.
 * Seed the estate first: scripts/operator/seed-persona-truth-pack.sh
 */

export interface JourneyPersona {
  username: string;
  displayName: string;
  roles: string[];
  providerId?: string;
  healthId?: string;
  /** Facility the persona works at (1 = Harare Central, 2 = Parirenyatwa). */
  facilityNamePattern?: RegExp;
}

export const PERSONA_PASSWORD = process.env.PERSONA_PASSWORD ?? "ImpiloTest123!";

export const PERSONAS = {
  doctor: {
    username: "dr.mapfumo",
    displayName: "Tendai Mapfumo",
    roles: ["CLINICIAN"],
    providerId: "PROV-ZW-00001",
    healthId: "c0000000-0000-4000-8000-000000000001",
    facilityNamePattern: /harare central/i,
  },
  nurse: {
    username: "nurse.chienda",
    displayName: "Rumbidzai Chienda",
    roles: ["NURSE"],
    providerId: "PROV-ZW-00007",
    healthId: "c0000000-0000-4000-8000-000000000007",
    facilityNamePattern: /harare central/i,
  },
  clerk: {
    username: "clerk.dube",
    displayName: "Nyasha Dube",
    roles: ["SUPPORT_AGENT"],
    providerId: "PROV-ZW-00008",
    healthId: "c0000000-0000-4000-8000-000000000008",
    facilityNamePattern: /harare central/i,
  },
  specialist: {
    username: "dr.gwena",
    displayName: "Rudo Gwena",
    roles: ["CLINICIAN"],
    providerId: "PROV-ZW-00009",
    healthId: "c0000000-0000-4000-8000-000000000009",
    facilityNamePattern: /parirenyatwa/i,
  },
  radiographer: {
    username: "rad.nkomo",
    displayName: "Sipho Nkomo",
    roles: ["CLINICIAN"],
    providerId: "PROV-ZW-00010",
    healthId: "c0000000-0000-4000-8000-000000000010",
    facilityNamePattern: /harare central/i,
  },
  pharmacist: {
    username: "pharm.zimba",
    displayName: "Faith Zimba",
    roles: ["PHARMACIST"],
    providerId: "PROV-ZW-00004",
    healthId: "c0000000-0000-4000-8000-000000000004",
    facilityNamePattern: /harare central/i,
  },
  trainer: {
    username: "trainer.chikafu",
    displayName: "Fungai Chikafu",
    roles: ["CLINICIAN", "TRAINER"],
    providerId: "PROV-ZW-00011",
    healthId: "c0000000-0000-4000-8000-000000000011",
    facilityNamePattern: /harare central/i,
  },
  learner: {
    username: "learner.tembo",
    displayName: "Kudzai Tembo",
    roles: ["NURSE"],
    providerId: "PROV-ZW-00012",
    healthId: "c0000000-0000-4000-8000-000000000012",
    facilityNamePattern: /harare central/i,
  },
  citizen: {
    username: "citizen.moyo",
    displayName: "Tatenda Moyo",
    roles: ["CITIZEN"],
  },
  facilityAdmin: {
    username: "admin.harare",
    displayName: "Harare Admin",
    roles: ["FACILITY_ADMIN"],
  },
  nationalAdmin: {
    username: "admin.central",
    displayName: "Central Admin",
    roles: ["SYSTEM_ADMIN"],
  },
  regulator: {
    username: "regulator.hpcz",
    displayName: "Chipo Marimo",
    roles: ["HIE_ADMIN"],
  },
  iatgOfficer: {
    username: "iatg.gono",
    displayName: "Nomsa Gono",
    roles: ["SYSTEM_ADMIN", "HIE_ADMIN"],
  },
  hpaRegistrar: {
    username: "hpa.registrar",
    displayName: "Tarisai Makoni",
    roles: ["HPA_REGISTRAR"],
  },
  hpaInspector: {
    username: "hpa.inspector",
    displayName: "Blessing Ncube",
    roles: ["HPA_INSPECTOR"],
  },
  msikaOperator: {
    username: "msika.operator",
    displayName: "Tariro Mutasa",
    roles: ["MARKETPLACE_OPERATOR"],
  },
  msikaSeller: {
    username: "msika.seller",
    displayName: "Simba Chikore",
    roles: ["MARKETPLACE_SELLER"],
    providerId: "PROV-ZW-00014",
    healthId: "c0000000-0000-4000-8000-000000000019",
    facilityNamePattern: /harare central/i,
  },
  msikaVendor: {
    username: "msika.vendor",
    displayName: "Rutendo Mhofu",
    roles: ["VENDOR"],
  },
} satisfies Record<string, JourneyPersona>;

export type PersonaKey = keyof typeof PERSONAS;

/**
 * The persona's health ID, or a loud failure.
 *
 * `healthId` is optional on Persona because not every persona is a patient. Three theatre journeys
 * filled it straight into a patient field, which TypeScript never checked (the specs were excluded
 * from every type-check in the repo) — so a persona without one would have submitted an empty
 * patient identifier and the journey would have "passed" against the wrong record.
 */
export function requireHealthId(persona: { username: string; healthId?: string }): string {
  if (!persona.healthId) {
    throw new Error(
      `Persona ${persona.username} has no healthId — it cannot stand in for a patient here.`,
    );
  }
  return persona.healthId;
}
