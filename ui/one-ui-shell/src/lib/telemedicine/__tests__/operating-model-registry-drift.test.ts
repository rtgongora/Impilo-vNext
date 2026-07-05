/**
 * Drift guard: the experience-bff operating-model registry resources are the
 * canonical serialization of the TS seed spec in this directory. The BFF
 * serves them to /work/telemedicine/groups and
 * /work/telemedicine/virtual-hospitals; if either side changes without the
 * other, these tests fail rather than letting the UI seed spec and the served
 * document diverge silently.
 *
 * Regenerate the JSON from the TS spec (never edit the JSON by hand):
 * serialize { virtualHospitals: ALL_VIRTUAL_HOSPITALS } and the clinical-group
 * taxonomy document with JSON.stringify(…, null, 2).
 */

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { ALL_VIRTUAL_HOSPITALS } from "../virtual-hospitals";
import {
  CLINICAL_GROUP_TYPES,
  GROUP_CREATION_BLOCKED_REASON,
  GROUP_GOVERNANCE_REQUIREMENTS,
} from "../clinical-groups";

const RESOURCE_DIR = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../../../../../services/experience-bff/src/main/resources/telemedicine/operating-model",
);

function readResource(name: string): unknown {
  return JSON.parse(readFileSync(path.join(RESOURCE_DIR, name), "utf8"));
}

describe("operating-model registry drift (TS seed spec ↔ BFF resources)", () => {
  it("virtual-hospitals.json matches ALL_VIRTUAL_HOSPITALS exactly", () => {
    expect(readResource("virtual-hospitals.json")).toEqual(
      JSON.parse(JSON.stringify({ virtualHospitals: ALL_VIRTUAL_HOSPITALS })),
    );
  });

  it("clinical-groups.json matches the taxonomy and fail-closed governance exactly", () => {
    expect(readResource("clinical-groups.json")).toEqual(
      JSON.parse(
        JSON.stringify({
          creationBlocked: true,
          creationBlockedReason: GROUP_CREATION_BLOCKED_REASON,
          governanceRequirements: GROUP_GOVERNANCE_REQUIREMENTS,
          groupTypes: CLINICAL_GROUP_TYPES,
        }),
      ),
    );
  });
});
