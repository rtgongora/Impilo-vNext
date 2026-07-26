/**
 * Interactive anatomical selectors for clinical examination.
 *
 * Examination findings have a location, and until now there was no way to record one
 * except in prose. "Rash on the left lower leg" typed into a notes field cannot be counted,
 * compared between visits, or read by a rule; the same finding written three ways is three
 * findings. A body map turns location into structured, coded data without asking the
 * clinician to hunt for a code.
 *
 * These graphics are data-entry instruments, not illustrations. A region only exists if a
 * clinician would meaningfully record something there, and every region carries a
 * terminology code so the finding means the same thing downstream as it did at the bedside.
 */

/** Which side of the body a finding is on. Midline is a distinct answer, not a missing one. */
export type Laterality = "left" | "right" | "midline";

export type FindingSeverity = "mild" | "moderate" | "severe";

/**
 * A selectable region of an anatomical map.
 *
 * @param svgPath   the hit area and the drawn shape, in the map's viewBox coordinates
 * @param locationCode terminology binding so the location survives leaving this screen
 */
export interface BodyRegionDef {
  id: string;
  label: string;
  svgPath: string;
  laterality?: Laterality;
  /** Groups regions for summary and for keyboard order, e.g. "torso", "upper limb". */
  group: string;
  locationCode: { system: string; code: string; display?: string };
}

/**
 * One anatomical view: the silhouette plus its selectable regions.
 *
 * @param silhouettePaths non-interactive outline drawn beneath the regions, so a clinician
 *                        sees a body rather than a set of disconnected shapes
 */
export interface RegionMapDef {
  id: string;
  title: string;
  viewBox: string;
  silhouettePaths: string[];
  regions: BodyRegionDef[];
}

/**
 * A finding recorded against a region.
 *
 * Every field beyond the region is optional because a clinician marking "something here"
 * quickly is more useful than one who abandons the map because it demanded six answers.
 * The instrument decides which fields it asks for.
 */
export interface MarkedRegionFinding {
  regionId: string;
  locationCode: { system: string; code: string; display?: string };
  laterality?: Laterality;
  /** Instrument-specific: rash morphology, wound type, joint finding… */
  morphology?: string;
  severity?: FindingSeverity;
  /** Free text, deliberately short — the structured fields carry the meaning. */
  note?: string;
  onset?: string;
  /**
   * Whether consent was recorded for a clinical photograph of this finding. Tri-state on
   * purpose: not asked is not the same as declined, and a photograph must never be taken
   * on the strength of a blank.
   */
  photoConsent?: "granted" | "declined" | "not_asked";
}

/**
 * A named examination instrument: which map it draws and which structured fields it asks
 * for once a region is marked.
 *
 * @param calculate optional derived measure, e.g. burn surface area from marked regions
 */
export interface BodyMapInstrument {
  id: string;
  title: string;
  description: string;
  maps: RegionMapDef[];
  /** Morphology options offered for this instrument; omitted means the field is not asked. */
  morphologyOptions?: { code: string; display: string }[];
  asksSeverity?: boolean;
  asksLaterality?: boolean;
  asksPhotoConsent?: boolean;
  calculate?: (findings: MarkedRegionFinding[], context: { ageMonths?: number }) => {
    label: string;
    value: string;
  } | null;
}

/** SNOMED CT body-structure codes; the system every location code in this module uses. */
export const SNOMED_BODY_STRUCTURE = "http://snomed.info/sct";

export function findingKey(finding: MarkedRegionFinding): string {
  return finding.laterality ? `${finding.regionId}:${finding.laterality}` : finding.regionId;
}

/** True when the value is a body-map finding list, for narrowing an untyped form value. */
export function isMarkedRegionFindings(value: unknown): value is MarkedRegionFinding[] {
  return (
    Array.isArray(value) &&
    value.every((entry) => typeof entry === "object" && entry !== null && "regionId" in entry)
  );
}

/** A one-line clinical summary of what was marked, for review screens and handover. */
export function summariseFindings(findings: MarkedRegionFinding[], map?: RegionMapDef): string {
  if (findings.length === 0) {
    return "No findings marked";
  }
  const labelFor = (finding: MarkedRegionFinding) => {
    const region = map?.regions.find((r) => r.id === finding.regionId);
    const base = region?.label ?? finding.locationCode.display ?? finding.regionId;
    const parts = [base];
    if (finding.morphology) parts.push(finding.morphology);
    if (finding.severity) parts.push(finding.severity);
    return parts.join(" — ");
  };
  return findings.map(labelFor).join("; ");
}
