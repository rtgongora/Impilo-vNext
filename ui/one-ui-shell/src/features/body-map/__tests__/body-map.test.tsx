import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { BodyMapField } from "../BodyMapField";
import { BodyMapCanvas } from "../core/BodyMapCanvas";
import { CHILD_BODY_BACK, CHILD_BODY_FRONT } from "../regions/child-body";
import {
  ABDOMEN_NINE_REGION_MAP,
  NEWBORN_EXAM_MAP,
  RESPIRATORY_ZONES_MAP,
} from "../regions/clinical-maps";
import { BODY_MAP_INSTRUMENTS, instrumentById } from "../instruments";
import { isMarkedRegionFindings, summariseFindings, type MarkedRegionFinding } from "../core/types";

const ALL_MAPS = [
  CHILD_BODY_FRONT,
  CHILD_BODY_BACK,
  NEWBORN_EXAM_MAP,
  RESPIRATORY_ZONES_MAP,
  ABDOMEN_NINE_REGION_MAP,
];

describe("region map definitions", () => {
  it.each(ALL_MAPS)("$id has usable, coded regions", (map) => {
    expect(map.regions.length).toBeGreaterThan(0);
    map.regions.forEach((region) => {
      expect(region.svgPath, `${region.id} needs a hit area`).toBeTruthy();
      expect(region.label, `${region.id} needs a label`).toBeTruthy();
      // Without a code the location cannot survive leaving this screen, which is the
      // whole reason for marking it on a map rather than typing it into a note.
      expect(region.locationCode.code, `${region.id} needs a terminology code`).toBeTruthy();
      expect(region.locationCode.system).toContain("snomed");
    });
  });

  it.each(ALL_MAPS)("$id has unique region ids", (map) => {
    const ids = map.regions.map((r) => r.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("the newborn map covers what a newborn check actually looks at", () => {
    const ids = NEWBORN_EXAM_MAP.regions.map((r) => r.id);
    // Each of these is a specific thing a general body map cannot express.
    expect(ids).toContain("fontanelle");
    expect(ids).toContain("umbilicus");
    expect(ids).toContain("femoral-pulses");
    expect(ids).toContain("hips");
    expect(ids).toContain("spine-sacrum");
    expect(ids).toContain("genitalia-anus");
  });

  it("the abdomen map offers nine regions, not four", () => {
    // Epigastric versus periumbilical pain can decide a surgical referral in a child.
    expect(ABDOMEN_NINE_REGION_MAP.regions).toHaveLength(9);
    const ids = ABDOMEN_NINE_REGION_MAP.regions.map((r) => r.id);
    expect(ids).toContain("epigastrium");
    expect(ids).toContain("right-iliac");
    expect(ids).toContain("umbilical");
  });

  it("the back map calls out the sacrum separately", () => {
    // Folded into "lower back", pressure injury and spinal dysraphism get missed.
    expect(CHILD_BODY_BACK.regions.map((r) => r.id)).toContain("sacrum");
  });
});

describe("instruments", () => {
  it("each instrument names maps and is resolvable by id", () => {
    Object.values(BODY_MAP_INSTRUMENTS).forEach((instrument) => {
      expect(instrument.maps.length).toBeGreaterThan(0);
      expect(instrumentById(instrument.id)).toBe(instrument);
    });
  });

  it("asks only the questions that fit the examination", () => {
    // Asking every question on every map is what makes structured examination slower
    // than prose, which is how it stops being used.
    expect(BODY_MAP_INSTRUMENTS["respiratory"].asksSeverity).toBe(false);
    expect(BODY_MAP_INSTRUMENTS["respiratory"].asksPhotoConsent).toBe(false);
    // Bruising in a child can be a safeguarding matter, so consent must be recordable.
    expect(BODY_MAP_INSTRUMENTS["general-body"].asksPhotoConsent).toBe(true);
    expect(BODY_MAP_INSTRUMENTS["general-body"].asksSeverity).toBe(true);
  });
});

describe("BodyMapCanvas", () => {
  it("renders every region as a labelled, focusable control", () => {
    render(
      <BodyMapCanvas map={ABDOMEN_NINE_REGION_MAP} findings={[]} onRegionSelect={() => {}} />,
    );
    const buttons = screen.getAllByRole("button");
    expect(buttons).toHaveLength(9);
    expect(screen.getByRole("button", { name: "Epigastrium" })).toBeTruthy();
    buttons.forEach((b) => expect(b.getAttribute("tabindex")).toBe("0"));
  });

  it("announces a marked region as marked", () => {
    const findings: MarkedRegionFinding[] = [
      {
        regionId: "epigastrium",
        locationCode: ABDOMEN_NINE_REGION_MAP.regions[1].locationCode,
        severity: "moderate",
      },
    ];
    render(<BodyMapCanvas map={ABDOMEN_NINE_REGION_MAP} findings={findings} onRegionSelect={() => {}} />);

    const marked = screen.getByTestId("body-map-region-epigastrium");
    expect(marked.getAttribute("data-marked")).toBe("true");
    expect(marked.getAttribute("aria-label")).toContain("marked");
    expect(marked.getAttribute("aria-label")).toContain("moderate");
  });

  it("is operable from the keyboard", () => {
    const onRegionSelect = vi.fn();
    render(
      <BodyMapCanvas map={ABDOMEN_NINE_REGION_MAP} findings={[]} onRegionSelect={onRegionSelect} />,
    );
    fireEvent.keyDown(screen.getByTestId("body-map-region-umbilical"), { key: "Enter" });
    expect(onRegionSelect).toHaveBeenCalledWith("umbilical");
  });

  it("does not accept marks when read-only", () => {
    const onRegionSelect = vi.fn();
    render(
      <BodyMapCanvas
        map={ABDOMEN_NINE_REGION_MAP}
        findings={[]}
        onRegionSelect={onRegionSelect}
        readOnly
      />,
    );
    fireEvent.click(screen.getByTestId("body-map-region-umbilical"));
    expect(onRegionSelect).not.toHaveBeenCalled();
  });
});

describe("BodyMapField", () => {
  it("marking a region records it with its terminology code", () => {
    const onChange = vi.fn();
    render(<BodyMapField instrumentId="abdomen" value={[]} onChange={onChange} />);

    fireEvent.click(screen.getByTestId("body-map-region-right-iliac"));

    expect(onChange).toHaveBeenCalledTimes(1);
    const [findings] = onChange.mock.calls[0];
    expect(findings).toHaveLength(1);
    expect(findings[0].regionId).toBe("right-iliac");
    expect(findings[0].locationCode.code).toBeTruthy();
  });

  it("opens the editor for a marked region and can remove the finding", () => {
    const onChange = vi.fn();
    const existing: MarkedRegionFinding[] = [
      { regionId: "umbilical", locationCode: ABDOMEN_NINE_REGION_MAP.regions[4].locationCode },
    ];
    render(<BodyMapField instrumentId="abdomen" value={existing} onChange={onChange} />);

    fireEvent.click(screen.getByTestId("body-map-region-umbilical"));
    expect(screen.getByTestId("region-finding-editor")).toBeTruthy();

    fireEvent.click(screen.getByTestId("region-finding-remove"));
    expect(onChange).toHaveBeenLastCalledWith([]);
  });

  it("offers front and back views when the instrument has both", () => {
    render(<BodyMapField instrumentId="general-body" value={[]} onChange={() => {}} />);

    expect(screen.getByTestId("body-map-view-child-body-front")).toBeTruthy();
    const back = screen.getByTestId("body-map-view-child-body-back");
    fireEvent.click(back);
    expect(screen.getByTestId("body-map-region-sacrum")).toBeTruthy();
  });

  it("defaults photo consent to not-asked rather than implying it was given", () => {
    const existing: MarkedRegionFinding[] = [
      { regionId: "chest-left", locationCode: CHILD_BODY_FRONT.regions[2].locationCode },
    ];
    render(<BodyMapField instrumentId="general-body" value={existing} onChange={() => {}} />);
    fireEvent.click(screen.getByTestId("body-map-region-chest-left"));

    const consent = screen.getByTestId("finding-photo-consent-option-not_asked");
    expect(consent.getAttribute("aria-checked")).toBe("true");
  });

  it("names a missing instrument instead of rendering an empty examination", () => {
    render(<BodyMapField instrumentId="does-not-exist" value={[]} onChange={() => {}} />);
    expect(screen.getByText(/Unknown examination instrument/)).toBeTruthy();
  });

  it("summarises what was marked", () => {
    const findings: MarkedRegionFinding[] = [
      {
        regionId: "right-iliac",
        locationCode: ABDOMEN_NINE_REGION_MAP.regions[6].locationCode,
        morphology: "tender",
        severity: "moderate",
      },
    ];
    render(<BodyMapField instrumentId="abdomen" value={findings} onChange={() => {}} />);
    const summary = screen.getByTestId("body-map-summary").textContent ?? "";
    expect(summary).toContain("Right iliac fossa");
    expect(summary).toContain("tender");
  });

  it("says so plainly when nothing is marked", () => {
    render(<BodyMapField instrumentId="abdomen" value={[]} onChange={() => {}} />);
    expect(screen.getByTestId("body-map-summary").textContent).toBe("No findings marked");
  });
});

describe("finding helpers", () => {
  it("narrows a form value to a finding list", () => {
    expect(isMarkedRegionFindings([{ regionId: "x", locationCode: { system: "s", code: "c" } }])).toBe(true);
    expect(isMarkedRegionFindings("not findings")).toBe(false);
    expect(isMarkedRegionFindings([{ nope: true }])).toBe(false);
  });

  it("summarises an empty list without pretending an examination happened", () => {
    expect(summariseFindings([])).toBe("No findings marked");
  });
});
