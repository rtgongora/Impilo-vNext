import type { Laterality, MarkedRegionFinding } from "@/features/body-map/core/types";

/**
 * Bridges ExaminationShell ↔ the V113 graphic/site/laterality columns.
 *
 * The body-map feature stores interactive marks; the examination record stores three closed-
 * vocabulary fields. This adapter is the only translation — marks are never persisted as a
 * second JSON shape beside the finding row.
 */

/**
 * Which V113 diagrams apply to each examination region.
 *
 * Most regions have one. Neurology and skin offer two because the bedside question is different
 * (deficit vs dermatome; lesion vs pressure injury) and both are first-class §7 graphics.
 */
export const REGION_GRAPHICS: Record<string, readonly string[]> = {
  RESPIRATORY: ["CHEST_AUSCULTATION"],
  CARDIOVASCULAR: ["CARDIAC_FINDINGS"],
  ABDOMEN: ["ABDOMINAL_REGIONS"],
  NEUROLOGY: ["NEUROLOGICAL_DEFICITS", "DERMATOMES"],
  PERIPHERAL_VASCULAR: ["PERIPHERAL_PULSES"],
  OEDEMA: ["OEDEMA_SITES"],
  MUSCULOSKELETAL: ["JOINTS"],
  SKIN: ["SKIN_LESIONS", "PRESSURE_INJURIES"],
  FEET: ["DIABETIC_FOOT"],
};

export type V113Laterality = "LEFT" | "RIGHT" | "BILATERAL" | "MIDLINE";

/** Body-map lowercase laterality → V113 CHECK vocabulary. */
export function toV113Laterality(
  laterality?: Laterality | string | null,
): V113Laterality | undefined {
  if (!laterality) return undefined;
  switch (laterality.toLowerCase()) {
    case "left":
      return "LEFT";
    case "right":
      return "RIGHT";
    case "midline":
      return "MIDLINE";
    case "bilateral":
      return "BILATERAL";
    default:
      return undefined;
  }
}

/** V113 laterality → body-map laterality for a read-only redraw. BILATERAL has no map side. */
export function fromV113Laterality(laterality?: string | null): Laterality | undefined {
  if (!laterality) return undefined;
  switch (laterality.toUpperCase()) {
    case "LEFT":
      return "left";
    case "RIGHT":
      return "right";
    case "MIDLINE":
      return "midline";
    default:
      return undefined;
  }
}

/**
 * Collapse interactive marks into the three V113 columns.
 *
 * Last pin wins for {@code site}: one finding row still equals one examination region
 * ({@code uq_pct_examination_findings_region}), so multi-pin detail belongs in prose {@code detail},
 * not a parallel findings array.
 */
export function marksToGraphicFields(
  graphic: string,
  marks: MarkedRegionFinding[],
): { graphic?: string; site?: string; laterality?: string } {
  if (marks.length === 0) {
    return {};
  }
  const last = marks[marks.length - 1];
  const site = last.regionId.slice(0, 64);
  const laterality = toV113Laterality(last.laterality);
  return {
    graphic,
    site,
    ...(laterality ? { laterality } : {}),
  };
}

/** Rebuild a single mark for a read-only BodyMapField from a stored finding. */
export function findingToMarks(finding: {
  site?: string | null;
  laterality?: string | null;
  graphic?: string | null;
}): MarkedRegionFinding[] {
  if (!finding.site || !finding.graphic) return [];
  return [
    {
      regionId: finding.site,
      locationCode: {
        system: "http://snomed.info/sct",
        code: finding.site,
        display: finding.site,
      },
      laterality: fromV113Laterality(finding.laterality),
    },
  ];
}
