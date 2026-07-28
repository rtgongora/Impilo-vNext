import { describe, expect, it } from "vitest";
import { workHomeSectionMeta, WORK_HOME_BUCKET_LABELS, WORK_HOME_FAMILY_LABELS } from "./section-registry";

describe("workHomeSectionMeta", () => {
  it("returns known icon metadata for a registered section id", () => {
    const meta = workHomeSectionMeta("clinical-worklist");
    expect(meta.icon).toBeDefined();
    expect(meta.accentClassName).toContain("rose");
  });

  it("falls back to generic metadata for an unregistered section id — never throws", () => {
    const meta = workHomeSectionMeta("some-future-section-nobody-registered-yet");
    expect(meta.icon).toBeDefined();
    expect(meta.accentClassName).toBe("text-muted-foreground");
  });

  it("covers every section id emitted by the BFF's family adapters (Phase E4)", () => {
    const knownBffSectionIds = [
      "clinical-worklist",
      "virtual-worklist",
      "department-profile",
      "facility-status",
      "facility-admin-claims",
      "jurisdiction-profile",
      "programme-profile",
      "support-tickets",
      "regulatory-appointments",
      "professional-alerts",
    ];
    for (const id of knownBffSectionIds) {
      expect(workHomeSectionMeta(id).accentClassName).not.toBe("text-muted-foreground");
    }
  });
});

describe("WORK_HOME_BUCKET_LABELS", () => {
  it("has a human label for every bucket the BFF's WorkHomeBucketing emits", () => {
    const bffBucketKeys = [
      "ACT_NOW",
      "ACT_TODAY",
      "AWAITING_SOMEONE_ELSE",
      "UPCOMING",
      "FOR_AWARENESS",
      "COMPLETED",
    ];
    for (const key of bffBucketKeys) {
      expect(WORK_HOME_BUCKET_LABELS[key]).toBeTruthy();
    }
  });
});

describe("WORK_HOME_FAMILY_LABELS", () => {
  it("has a human label for every WorkHomeFamily the BFF can return", () => {
    const bffFamilies = [
      "FACILITY_CLINICAL",
      "VIRTUAL_CARE",
      "DEPARTMENT_MANAGEMENT",
      "FACILITY_MANAGEMENT",
      "OVERSIGHT",
      "PROGRAMME_MANAGEMENT",
      "SUPPORT",
      "REGULATORY",
    ];
    for (const family of bffFamilies) {
      expect(WORK_HOME_FAMILY_LABELS[family]).toBeTruthy();
    }
  });
});
