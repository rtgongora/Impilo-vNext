import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

/**
 * HPA-W4 golden thread: the importer stamps facility metadata (source_system,
 * source_effective_date, verification_status, incomplete flags) → the tuso public
 * profile surfaces them on FacilityResponse → the BFF passes the profile through →
 * the shell FacilityProfile carries them → FacilityHpaDisclosure renders an honest,
 * dated regulatory disclosure with claim/correct CTAs on the anonymous find-care lane.
 */
describe("HPA citizen disclosure golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");
  const read = (p: string) => readFileSync(resolve(repoRoot, p), "utf8");

  it("the disclosure states the dated HPA source and never implies open", () => {
    const c = read("ui/one-ui-shell/src/components/public/find-care/FacilityHpaDisclosure.tsx");
    expect(c).toContain("Listed in HPA regulatory information");
    expect(c).toContain("does not confirm that this facility is currently open");
    expect(c).toContain("Map location awaiting confirmation");
    expect(c).toContain("call before travelling");
    // Completion actions link to the authenticated claim/confirm flow.
    expect(c).toContain("/facility/claim");
    expect(c).toContain("Claim this facility");
  });

  it("the detail page renders the disclosure", () => {
    const d = read("ui/one-ui-shell/src/components/public/find-care/FindCareFacilityDetail.tsx");
    expect(d).toContain("FacilityHpaDisclosure");
  });

  it("the FacilityProfile type carries the disclosure fields", () => {
    const t = read("ui/one-ui-shell/src/lib/find-care/types.ts");
    expect(t).toContain("sourceSystem");
    expect(t).toContain("verificationStatus");
    expect(t).toContain("sourceEffectiveDate");
  });

  it("the tuso public profile surfaces the importer metadata (real wire)", () => {
    const ctrl = read(
      "services/tuso-service/src/main/java/zw/gov/mohcc/impilo/tuso/api/controller/PublicFacilityController.java",
    );
    expect(ctrl).toContain('metaString(entity, "source_system")');
    expect(ctrl).toContain('metaString(entity, "verification_status")');
    const dto = read(
      "services/tuso-service/src/main/java/zw/gov/mohcc/impilo/tuso/api/dto/FacilityResponse.java",
    );
    expect(dto).toContain("sourceSystem");
    expect(dto).toContain("verificationStatus");
  });
});
