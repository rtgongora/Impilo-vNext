/**
 * Courier proof must never submit fabricated evidence_ref literals.
 */
import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";

describe("CourierProofScreen evidence integrity", () => {
  const src = readFileSync(
    join(__dirname, "../../screens/courier/CourierProofScreen.tsx"),
    "utf8",
  );

  it("does not hardcode fabricated signature/stamp evidence_ref strings", () => {
    expect(src).not.toContain('"signature-captured"');
    expect(src).not.toContain('"facility-stamp"');
  });

  it("requires a captured evidence URI before photo proof submit", () => {
    expect(src).toContain("expo-image-picker");
    expect(src).toContain("evidenceUri");
    expect(src).toContain("Photo required");
    expect(src).toContain("evidence_ref: evidenceUri");
  });
});
