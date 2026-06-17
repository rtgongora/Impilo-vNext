import { describe, expect, it } from "vitest";
import { resolveImagingViewerEngine } from "@/lib/imaging/resolveViewerEngine";

describe("resolveImagingViewerEngine", () => {
  it("defaults to DICOMWEB_STACK when launch context and request are empty", () => {
    expect(resolveImagingViewerEngine(undefined, null)).toBe("DICOMWEB_STACK");
  });

  it("uses launch context engine when set", () => {
    expect(resolveImagingViewerEngine("OHIF", null)).toBe("OHIF");
  });

  it("uses requested viewer type when launch context is absent", () => {
    expect(resolveImagingViewerEngine(undefined, "ohif")).toBe("OHIF");
  });

  it("prefers launch context over requested type for governed engines", () => {
    expect(resolveImagingViewerEngine("DICOMWEB_STACK", "OHIF")).toBe("DICOMWEB_STACK");
  });

  it("honours DWV_NATIVE from request even when launch context normalises to DICOMWEB_STACK", () => {
    expect(resolveImagingViewerEngine("DICOMWEB_STACK", "DWV_NATIVE")).toBe("DWV_NATIVE");
  });
});
