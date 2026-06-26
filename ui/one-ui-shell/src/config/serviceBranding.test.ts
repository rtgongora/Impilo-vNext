import { describe, expect, it } from "vitest";
import {
  COMMAND_CENTRE_TILE_SLUGS,
  inferServiceSlugFromPath,
  SERVICE_SURFACE_COVERAGE,
  SERVICE_BRANDING,
  getServiceBranding,
  listSovereignServices,
  resolveServiceSlug,
} from "@/config/serviceBranding";

describe("serviceBranding registry", () => {
  it("lists all 18 sovereign services", () => {
    expect(listSovereignServices()).toHaveLength(18);
  });

  it("maps every service to a public logo path", () => {
    for (const entry of listSovereignServices()) {
      expect(entry.logo).toMatch(/^\/brand\/services\/[a-z]+-logo\.png$/);
      expect(entry.name).toBeTruthy();
      expect(entry.description).toBeTruthy();
      expect(entry.domain).toBeTruthy();
      expect(entry.fallbackIcon).toBeTruthy();
    }
  });

  it("includes Fundo with learning aliases", () => {
    const fundo = SERVICE_BRANDING.fundo;
    expect(fundo.name).toBe("Fundo");
    expect(fundo.logo).toBe("/brand/services/fundo-logo.png");
    expect(fundo.aliases).toContain("learning");
    expect(fundo.aliases).toContain("impilo-fundo");
  });

  it("resolves canonical slugs and aliases", () => {
    expect(resolveServiceSlug("fundo")).toBe("fundo");
    expect(resolveServiceSlug("Impilo Fundo")).toBe("fundo");
    expect(resolveServiceSlug("learning")).toBe("fundo");
    expect(resolveServiceSlug("vito")).toBe("vito");
    expect(resolveServiceSlug("ops_vito")).toBe("vito");
    expect(resolveServiceSlug("marketplace")).toBe("msika");
    expect(resolveServiceSlug("mushex-platform")).toBe("mushex");
    expect(resolveServiceSlug("unknown-service")).toBeUndefined();
  });

  it("resolves command centre tile ids", () => {
    expect(COMMAND_CENTRE_TILE_SLUGS.fundo).toBe("fundo");
    expect(COMMAND_CENTRE_TILE_SLUGS["wellness-hub"]).toBe("simba");
    expect(getServiceBranding(COMMAND_CENTRE_TILE_SLUGS.vito)?.name).toBe("Vito");
  });

  it("infers service slug from route prefixes", () => {
    expect(inferServiceSlugFromPath("/learning/studio/courses")).toBe("fundo");
    expect(inferServiceSlugFromPath("/learning/admin/moderation")).toBe("fundo");
    expect(inferServiceSlugFromPath("/operations/butano")).toBe("butano");
    expect(inferServiceSlugFromPath("/public-health/site-registry/abc")).toBe("indawo");
    expect(inferServiceSlugFromPath("/registry/trust")).toBe("tshepo");
    expect(inferServiceSlugFromPath("/work/system-admin/tshepo")).toBe("tshepo");
    expect(inferServiceSlugFromPath("/home")).toBeUndefined();
  });

  it("documents surface coverage for all sovereign services", () => {
    for (const entry of listSovereignServices()) {
      expect(SERVICE_SURFACE_COVERAGE[entry.slug]).toBeDefined();
      expect(SERVICE_SURFACE_COVERAGE[entry.slug].status).toMatch(/^(surfaced|partial|no_dedicated_route)$/);
    }
  });
});
