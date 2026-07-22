import { describe, expect, it } from "vitest";
import {
  parseSpecialtyConcepts,
  FALLBACK_SPECIALTIES,
  FALLBACK_SPECIALTY_LABELS,
  CLINICAL_SPECIALTY_CANONICAL_URL,
} from "./useClinicalSpecialties";

// A minimal governed CodeSystem artifact as ZIBO seeds it.
const FHIR_CODESYSTEM = {
  resourceType: "CodeSystem",
  url: CLINICAL_SPECIALTY_CANONICAL_URL,
  concept: [
    { code: "internal-medicine", display: "Internal Medicine" },
    { code: "surgery", display: "Surgery" },
    { code: "paediatrics", display: "Paediatrics" },
  ],
};

describe("parseSpecialtyConcepts — governed ValueSet extraction (TM-B2/B3)", () => {
  it("extracts {code, display} when the node is the FHIR CodeSystem itself", () => {
    const out = parseSpecialtyConcepts(FHIR_CODESYSTEM);
    expect(out).toEqual([
      { code: "internal-medicine", display: "Internal Medicine" },
      { code: "surgery", display: "Surgery" },
      { code: "paediatrics", display: "Paediatrics" },
    ]);
  });

  it("unwraps the ArtifactEntity `contentJson` string (the real resolve shape)", () => {
    // resolve returns the serialized ArtifactEntity; its content_json column is a STRING.
    const artifact = {
      artifactId: "abc",
      canonicalUrl: CLINICAL_SPECIALTY_CANONICAL_URL,
      fhirType: "CODE_SYSTEM",
      contentJson: JSON.stringify(FHIR_CODESYSTEM),
    };
    const out = parseSpecialtyConcepts(artifact);
    expect(out).toHaveLength(3);
    expect(out[0]).toEqual({ code: "internal-medicine", display: "Internal Medicine" });
  });

  it("unwraps a nested `content` object envelope", () => {
    const out = parseSpecialtyConcepts({ content: FHIR_CODESYSTEM });
    expect(out).toHaveLength(3);
  });

  it("unwraps a nested `resource` envelope", () => {
    const out = parseSpecialtyConcepts({ resource: FHIR_CODESYSTEM });
    expect(out).toHaveLength(3);
  });

  it("degrades display to code when display is missing", () => {
    const out = parseSpecialtyConcepts({ concept: [{ code: "oncology" }] });
    expect(out).toEqual([{ code: "oncology", display: "oncology" }]);
  });

  it("skips concepts without a code", () => {
    const out = parseSpecialtyConcepts({
      concept: [{ display: "No code here" }, { code: "ent", display: "ENT" }],
    });
    expect(out).toEqual([{ code: "ent", display: "ENT" }]);
  });

  it("returns [] for empty, null, malformed, or non-CodeSystem input (hook then falls back)", () => {
    expect(parseSpecialtyConcepts(null)).toEqual([]);
    expect(parseSpecialtyConcepts(undefined)).toEqual([]);
    expect(parseSpecialtyConcepts({})).toEqual([]);
    expect(parseSpecialtyConcepts({ concept: [] })).toEqual([]);
    expect(parseSpecialtyConcepts({ contentJson: "not-json{" })).toEqual([]);
    expect(parseSpecialtyConcepts("garbage")).toEqual([]);
  });
});

describe("clinical-specialty fallback constant", () => {
  it("mirrors the 18 previously-hardcoded labels with code === display", () => {
    expect(FALLBACK_SPECIALTIES).toHaveLength(18);
    expect(FALLBACK_SPECIALTIES).toHaveLength(FALLBACK_SPECIALTY_LABELS.length);
    for (const s of FALLBACK_SPECIALTIES) {
      expect(s.code).toBe(s.display);
    }
    expect(FALLBACK_SPECIALTIES.map((s) => s.display)).toContain("Internal Medicine");
    expect(FALLBACK_SPECIALTIES.map((s) => s.display)).toContain("Obstetrics & Gynaecology");
  });
});
