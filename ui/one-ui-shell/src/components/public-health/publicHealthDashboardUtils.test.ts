import { describe, expect, it } from "vitest";
import {
  formatPublicHealthCompact,
  isPublicHealthCampaignActive,
  isPublicHealthCaseOpen,
  isPublicHealthSignalActive,
} from "./publicHealthDashboardUtils";

describe("formatPublicHealthCompact", () => {
  it("formats thousands with K suffix", () => {
    expect(formatPublicHealthCompact(1500)).toBe("2K");
    expect(formatPublicHealthCompact(999)).toBe("999");
  });

  it("formats millions with M suffix", () => {
    expect(formatPublicHealthCompact(2_500_000)).toBe("2.5M");
  });

  it("handles invalid numbers", () => {
    expect(formatPublicHealthCompact(Number.NaN)).toBe("0");
    expect(formatPublicHealthCompact(-1)).toBe("0");
  });
});

describe("isPublicHealthCampaignActive", () => {
  it("returns false for terminal statuses", () => {
    expect(isPublicHealthCampaignActive("completed")).toBe(false);
    expect(isPublicHealthCampaignActive("CLOSED")).toBe(false);
    expect(isPublicHealthCampaignActive("Cancelled")).toBe(false);
  });

  it("returns true for in-flight statuses", () => {
    expect(isPublicHealthCampaignActive("planning")).toBe(true);
    expect(isPublicHealthCampaignActive("active")).toBe(true);
  });
});

describe("isPublicHealthCaseOpen", () => {
  it("treats known open statuses as open", () => {
    expect(isPublicHealthCaseOpen("OPEN")).toBe(true);
    expect(isPublicHealthCaseOpen("")).toBe(true);
  });

  it("treats closed as not open", () => {
    expect(isPublicHealthCaseOpen("CLOSED")).toBe(false);
  });
});

describe("isPublicHealthSignalActive", () => {
  it("excludes terminal signal statuses", () => {
    expect(isPublicHealthSignalActive("CLOSED")).toBe(false);
    expect(isPublicHealthSignalActive("resolved")).toBe(false);
  });

  it("includes active", () => {
    expect(isPublicHealthSignalActive("ACTIVE")).toBe(true);
  });
});
