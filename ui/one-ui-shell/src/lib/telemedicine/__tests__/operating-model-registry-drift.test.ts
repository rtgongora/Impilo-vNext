/**
 * Drift guard for the virtual-service registry.
 *
 * The virtual-hospital directory is now sovereign in TUSO (V022/V023), served
 * through the experience-bff operating-model registry with the canonical
 * document `contracts/telemedicine/virtual-hospitals.json` as the static
 * fallback (the UI's ALL_VIRTUAL_HOSPITALS data constant was deleted in the
 * Wave-2 de-dup; the UI now reads the BFF via useVirtualHospitals()). This test
 * pins the two file copies together — the canonical contracts doc and the BFF
 * resource copy it is generated into — so they can never diverge silently.
 * Regenerate both from the seed generator (tools/generate-virtual-service-seed.mjs),
 * never edit the JSON by hand.
 *
 * The clinical-group taxonomy is still a TS seed spec in this directory, so its
 * drift assertion against the served document is unchanged.
 */

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { describe, expect, it } from "vitest";
import {
  CLINICAL_GROUP_TYPES,
  GROUP_CREATION_BLOCKED_REASON,
  GROUP_GOVERNANCE_REQUIREMENTS,
} from "../clinical-groups";

const RESOURCE_DIR = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../../../../../services/experience-bff/src/main/resources/telemedicine/operating-model",
);

const CONTRACTS_DIR = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../../../../../contracts/telemedicine",
);

function readResource(name: string): unknown {
  return JSON.parse(readFileSync(path.join(RESOURCE_DIR, name), "utf8"));
}

function readContract(name: string): unknown {
  return JSON.parse(readFileSync(path.join(CONTRACTS_DIR, name), "utf8"));
}

describe("operating-model registry drift (canonical contract ↔ BFF resources)", () => {
  it("BFF virtual-hospitals.json fallback matches the canonical contracts document", () => {
    expect(readResource("virtual-hospitals.json")).toEqual(
      readContract("virtual-hospitals.json"),
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
