import type { FacilityResource } from "@/hooks/queries/useFacilities";

/** Seeded dev facility — matches BFF V40 + FacilityController seed. */
export const DEV_TEST_FACILITY: FacilityResource = {
  id: "a1b2c3d4-00ff-4000-8000-000000000099",
  type: "facility",
  attributes: {
    name: "Test Facility",
    code: "ZW-TEST-001",
    facilityType: "General Hospital",
    district: "Harare",
    province: "Harare Metropolitan",
    region: "Harare",
    status: "ACTIVE",
    latitude: -17.8252,
    longitude: 31.0335,
    bedCount: 50,
    capabilities: ["OUTPATIENT", "EMERGENCY", "TELEMEDICINE", "LABORATORY", "PHARMACY"],
    operatingModel: {
      facilityTier: "GENERAL_HOSPITAL",
      deploymentMode: "DEDICATED_POD",
      continuityClass: "LOCAL_EXECUTION_REQUIRED",
      workflowArchetype: "PRIMARY_CARE",
    },
  },
};

/** Golden-path demo patient — Tatenda Moyo (V4 seed). */
export const DEMO_PATIENT_ID = "a1000000-0000-0000-0000-000000000001";

export const DEMO_PATIENT_LABEL = "Tatenda Moyo";

/** Registry stub ids that map to the golden-path citizen presence anchor. */
const TELEMEDICINE_PATIENT_ID_ALIASES: Record<string, string> = {
  "pat-001": DEMO_PATIENT_ID,
};

/** Normalize patient ids so call invites reach citizen presence (tatenda.moyo@example.com). */
export function resolveTelemedicinePatientId(patientId: string | undefined | null): string {
  if (!patientId) return "";
  const trimmed = patientId.trim();
  return TELEMEDICINE_PATIENT_ID_ALIASES[trimmed] ?? trimmed;
}

/** Dev workspace for Test Facility (V40 seed). */
export const DEV_TEST_WORKSPACE = {
  id: "b1c2c3d4-00ff-4000-8000-000000000099",
  name: "Telemedicine Suite 1",
  workspaceType: "OPD",
  facilityId: DEV_TEST_FACILITY.id,
};
