import { describe, expect, it } from "vitest";
import {
  MEDICINE_EXAMINATION_INSTRUMENTS,
  V113_GRAPHIC_CODES,
} from "@/features/body-map/instruments/medicine-examination-instruments";
import { instrumentById } from "@/features/body-map/instruments";
import type { MarkedRegionFinding } from "@/features/body-map/core/types";
import {
  REGION_GRAPHICS,
  findingToMarks,
  fromV113Laterality,
  marksToGraphicFields,
  toV113Laterality,
} from "../v113-graphics";

/** The closed set V113 CHECK accepts — keep in lockstep with ExaminationVocabulary.GRAPHICS. */
const V113_EXPECTED = [
  "CHEST_AUSCULTATION",
  "CARDIAC_FINDINGS",
  "ABDOMINAL_REGIONS",
  "NEUROLOGICAL_DEFICITS",
  "DERMATOMES",
  "PERIPHERAL_PULSES",
  "OEDEMA_SITES",
  "JOINTS",
  "SKIN_LESIONS",
  "DIABETIC_FOOT",
  "PRESSURE_INJURIES",
] as const;

describe("V113 examination graphics", () => {
  it("registers exactly the eleven V113 graphic codes as instruments", () => {
    expect(V113_GRAPHIC_CODES.sort()).toEqual([...V113_EXPECTED].sort());
    for (const code of V113_EXPECTED) {
      expect(instrumentById(code)?.id).toBe(code);
      expect(MEDICINE_EXAMINATION_INSTRUMENTS[code].maps.length).toBeGreaterThan(0);
    }
  });

  it("every region-linked graphic resolves to a drawable instrument", () => {
    const linked = Object.values(REGION_GRAPHICS).flat();
    expect(new Set(linked).size).toBe(V113_EXPECTED.length);
    for (const code of linked) {
      expect(instrumentById(code), `${code} must be registered`).toBeDefined();
    }
  });

  it("maps body-map laterality onto the V113 CHECK vocabulary", () => {
    expect(toV113Laterality("left")).toBe("LEFT");
    expect(toV113Laterality("right")).toBe("RIGHT");
    expect(toV113Laterality("midline")).toBe("MIDLINE");
    expect(toV113Laterality("bilateral")).toBe("BILATERAL");
    expect(fromV113Laterality("LEFT")).toBe("left");
    expect(fromV113Laterality("BILATERAL")).toBeUndefined();
  });

  it("collapses marks to graphic/site/laterality with last pin winning", () => {
    // One finding row still equals one examination region; multi-pin detail is not a second shape.
    const marks: MarkedRegionFinding[] = [
      {
        regionId: "upper-right",
        locationCode: { system: "http://snomed.info/sct", code: "1" },
        laterality: "right",
      },
      {
        regionId: "lower-left",
        locationCode: { system: "http://snomed.info/sct", code: "2" },
        laterality: "left",
      },
    ];
    expect(marksToGraphicFields("CHEST_AUSCULTATION", marks)).toEqual({
      graphic: "CHEST_AUSCULTATION",
      site: "lower-left",
      laterality: "LEFT",
    });
    expect(marksToGraphicFields("CHEST_AUSCULTATION", [])).toEqual({});
  });

  it("rebuilds a single read-only mark from a stored finding", () => {
    expect(
      findingToMarks({
        graphic: "DIABETIC_FOOT",
        site: "plantar-heel",
        laterality: "LEFT",
      }),
    ).toEqual([
      expect.objectContaining({
        regionId: "plantar-heel",
        laterality: "left",
      }),
    ]);
    expect(findingToMarks({ graphic: null, site: null, laterality: null })).toEqual([]);
  });
});
