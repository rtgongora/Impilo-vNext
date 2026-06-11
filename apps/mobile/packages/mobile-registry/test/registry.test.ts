import { describe, expect, it } from "vitest";
import {
  CANONICAL_SERVICE_SLUGS,
  MOBILE_SERVICE_WIRING,
  MOBILE_SOVEREIGN_SERVICES,
  listServicesForSurface,
} from "../src";

const REQUIRED_SLUGS = [
  "vito",
  "varapi",
  "tuso",
  "tshepo",
  "butano",
  "ubomi",
  "zibo",
  "msika",
  "indawo",
  "pct",
  "costa",
  "mushex",
  "oros",
  "simba",
  "ndila",
  "nhume",
  "fundo",
  "nompilo",
  "madi",
  "pacs",
  "live",
] as const;

describe("mobile-registry", () => {
  it("includes all canonical sovereign services", () => {
    expect(CANONICAL_SERVICE_SLUGS.sort()).toEqual([...REQUIRED_SLUGS].sort());
  });

  it("every service has wiring metadata with status flags", () => {
    for (const slug of REQUIRED_SLUGS) {
      const wiring = MOBILE_SERVICE_WIRING[slug];
      expect(wiring.slug).toBe(slug);
      expect(wiring.wiringStatus).toBeTruthy();
      expect(wiring.mobileStatus).toBeTruthy();
      expect(wiring.supportsLoadingState).toBe(true);
      expect(wiring.supportsErrorState).toBe(true);
    }
  });

  it("citizen and provider surfaces have core journeys", () => {
    const citizen = listServicesForSurface("citizen");
    const provider = listServicesForSurface("provider");
    expect(citizen.some((s) => s.slug === "vito")).toBe(true);
    expect(citizen.some((s) => s.slug === "pct")).toBe(true);
    expect(provider.some((s) => s.slug === "varapi")).toBe(true);
    expect(provider.some((s) => s.slug === "oros")).toBe(true);
  });

  it("registry entries align with wiring slugs", () => {
    for (const slug of REQUIRED_SLUGS) {
      expect(MOBILE_SOVEREIGN_SERVICES[slug].slug).toBe(slug);
      expect(MOBILE_SERVICE_WIRING[slug].name).toBe(MOBILE_SOVEREIGN_SERVICES[slug].name);
    }
  });
});
