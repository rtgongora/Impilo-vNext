import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

/**
 * RW6 golden thread: the read-only, Rito-sourced patient-experience summary flows
 * Rito public read → varapi RitoClient → PublicPractitionerVerificationResponse
 * → BFF (transparent JsonNode passthrough) → shell verify-practitioner card.
 */
describe("Rito experience surfacing golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");
  const read = (p: string) => readFileSync(resolve(repoRoot, p), "utf8");

  it("the shell verify page renders the Rito-sourced patient-experience card", () => {
    const page = read("ui/one-ui-shell/src/app/verify/practitioner/page.tsx");
    expect(page).toContain("experienceSummary");
    expect(page).toContain("Patient experience");
    expect(page).toContain("verified interaction");
    expect(page).toContain("Source:");
    expect(page).toContain("ExperienceSummary");
  });

  it("varapi composes the experience summary read-only (never owns it)", () => {
    const dto = read(
      "services/varapi-service/src/main/java/zw/gov/mohcc/impilo/varapi/api/dto/PublicPractitionerVerificationResponse.java",
    );
    expect(dto).toContain("ExperienceSummary experienceSummary");
    expect(dto).toContain("it never owns or stores it");

    const client = read(
      "services/varapi-service/src/main/java/zw/gov/mohcc/impilo/varapi/integration/RitoClient.java",
    );
    expect(client).toContain("/v1/public/rito/reputation/");
    expect(client).toContain("isEnabled()"); // gated + fail-open
  });

  it("Rito exposes the public verified-experience read (provider + facility)", () => {
    const controller = read(
      "services/rito-quality-safety-service/src/main/java/zw/gov/mohcc/impilo/rito/api/controller/PublicReputationController.java",
    );
    expect(controller).toContain("/v1/public/rito/reputation");
    expect(controller).toContain("/facility/{facilityId}");
  });

  it("Tuso composes a facility experience card (RW8), read-only from Rito", () => {
    const card = read("ui/one-ui-shell/src/components/public/find-care/FacilityExperienceCard.tsx");
    expect(card).toContain("Patient experience");
    expect(card).toContain("Source:");

    const client = read(
      "services/tuso-service/src/main/java/zw/gov/mohcc/impilo/tuso/integration/RitoClient.java",
    );
    expect(client).toContain("/v1/public/rito/reputation/facility/");
    expect(client).toContain("it never owns or stores ratings");
  });
});
