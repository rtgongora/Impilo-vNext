/**
 * Golden thread for the professional-registers materialisation surface (NCZ task #97).
 *
 * The backend wrote provenance and a reconcile. Without this surface those facts are only true
 * inside the database — the hub previously labelled "Registers" opened the applications queue.
 * These checks guard reachability, the provenance fields the page must show, and that reconcile
 * lives here rather than on the read-only configuration page.
 */

import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { ROUTES } from "../routes";

const root = join(__dirname, "..", "..");
const read = (p: string) => readFileSync(join(root, p), "utf8");

const PAGE = "app/work/regulatory/[orgId]/registers/page.tsx";
const HUB = "app/work/regulatory/[orgId]/page.tsx";
const CONFIG = "app/work/regulatory/[orgId]/configuration/page.tsx";
const HOOK = "hooks/queries/useProfessionalRegisters.ts";
const BFF =
  "../../../services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/RegulatoryRegistersController.java";
const VARAPI =
  "../../../services/varapi-service/src/main/java/zw/gov/mohcc/impilo/varapi/api/controller/RegisterMaterialisationController.java";

describe("professional registers surface", () => {
  it("is registered as a route", () => {
    const route = ROUTES.find((r) => r.path === "/work/regulatory/[orgId]/registers");
    expect(route).toBeDefined();
    expect(route?.guard).toBe("auth");
    expect(route?.navZone).toBe("work");
  });

  it("is linked from the regulatory workspace hub as Registers, not the applications queue", () => {
    const hub = read(HUB);
    expect(hub).toContain('label: "Registers"');
    expect(hub).toContain("`/work/regulatory/${encodeURIComponent(orgId)}/registers`");
    // Applications still owns the review queue — Registers must not point there.
    expect(hub).toMatch(
      /label:\s*"Applications"[\s\S]*?registration-review/,
    );
  });

  it("lives on the canonical /work/regulatory tree", () => {
    const page = read(PAGE);
    expect(page).toContain("/work/regulatory/");
    expect(page).not.toContain("/work/regulators/");
  });

  it("shows provenance that distinguishes configuration from a migration seed", () => {
    const page = read(PAGE);
    expect(page).toContain("CONFIG_PACK");
    expect(page).toContain("MIGRATION_SEED");
    expect(page).toContain("From configuration");
    expect(page).toContain("Migration seed");
    expect(page).toContain("statutoryTitle");
    expect(page).toContain("configDefinitionKey");
    expect(page).toContain("RETIRED");
  });

  it("offers reconcile with the full report, and keeps configuration read-only", () => {
    const page = read(PAGE);
    expect(page).toContain("useReconcileProfessionalRegisters");
    expect(page).toContain("Reconcile from configuration");
    expect(page).toContain("created");
    expect(page).toContain("retired");
    expect(page).toContain("reinstated");

    const config = read(CONFIG);
    expect(config).not.toMatch(/useReconcileProfessionalRegisters|useMutation/);
    expect(config).toContain("/registers");
    expect(config).toContain("view live registers");
  });

  it("types the BFF-composed catalogue, including the summary counts", () => {
    const hook = read(HOOK);
    expect(hook).toContain("fromConfiguration");
    expect(hook).toContain("fromMigrationSeed");
    expect(hook).toContain("configSemanticVersion");
    expect(hook).toContain("/api/v1/regulatory/organizations/");
    expect(hook).toContain("/reconcile");
  });

  it("is backed by a BFF composition and an organisation-scoped varapi endpoint", () => {
    const bff = read(BFF);
    expect(bff).toContain("/organizations/{organizationId}/registers");
    expect(bff).toContain("composeCatalogue");
    expect(bff).toContain("CONFIG_PACK");

    const varapi = read(VARAPI);
    expect(varapi).toContain("/organizations/{organizationId}/registers");
    expect(varapi).toContain("reconcileForOrganisation");
    expect(varapi).toContain("ProfessionalRegisterViews");
  });
});
