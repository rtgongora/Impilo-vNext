/**
 * Golden thread for the read-only Registry surface (NCZ-W1A).
 *
 * Two properties are worth guarding here, and neither is cosmetic.
 *
 * The page must be REACHABLE — a route row without a link from the hub is a page nobody finds, and
 * this codebase already carries twelve orphaned pages that were built and never navigable.
 *
 * The page must stay READ-ONLY. Configuration changes are four-eyes acts enforced in schema; a
 * write affordance here would be a way around that, and a check that only greps for the word
 * "read only" would keep passing after someone added a Save button. So this asserts the ABSENCE of
 * the mutating verbs, which is the thing that would actually break.
 */

import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { ROUTES } from "../routes";

const root = join(__dirname, "..", "..");
const read = (p: string) => readFileSync(join(root, p), "utf8");

const PAGE = "app/work/regulatory/[orgId]/configuration/page.tsx";
const HUB = "app/work/regulatory/[orgId]/page.tsx";
const HOOK = "hooks/queries/useRegulatoryConfiguration.ts";

describe("regulatory configuration surface", () => {
  it("is registered as a route", () => {
    const route = ROUTES.find((r) => r.path === "/work/regulatory/[orgId]/configuration");
    expect(route).toBeDefined();
    expect(route?.guard).toBe("auth");
    expect(route?.navZone).toBe("work");
  });

  it("is linked from the regulatory workspace hub, not merely registered", () => {
    expect(read(HUB)).toContain("/configuration");
  });

  it("lives on the canonical /work/regulatory tree, not the /work/regulators stubs", () => {
    const page = read(PAGE);
    expect(page).toContain("/work/regulatory/");
    expect(page).not.toContain("/work/regulators/");
  });

  it("offers no way to change configuration", () => {
    const page = read(PAGE);
    // The Registry's four-eyes rail is enforced in schema; a write path from this screen would be
    // a way around it. Assert the mutating calls are absent rather than that a label says so.
    expect(page).not.toMatch(/apiClient\.(post|put|patch|delete)/);
    expect(page).not.toMatch(/useMutation/);
  });

  it("renders an unset value as awaiting a named Council decision, never blank or zero", () => {
    const page = read(PAGE);
    expect(page).toContain("PENDING_REGULATOR_APPROVAL");
    expect(page).toContain("awaiting Council decision");
    // The decision reference must reach the screen — "pending" without saying pending on what is
    // barely better than silence, which is the hole V014 closed on the server side.
    expect(page).toContain("decisionRef");
  });

  it("says plainly that a failed source load leaves the activated configuration untouched", () => {
    const page = read(PAGE);
    expect(page).toContain("LOAD_FAILED");
    expect(page).toContain("unaffected");
  });

  it("distinguishes a pack with nothing activated from one with no configuration", () => {
    const page = read(PAGE);
    expect(page).toContain("No configuration has been activated");
    expect(page).toContain("deployment never performs it");
  });

  it("types the response shape the BFF composes, not the org-registry entities", () => {
    const hook = read(HOOK);
    expect(hook).toContain("loadState");
    expect(hook).toContain("activatedConfigurationAffected");
    expect(hook).toContain("decisionRef");
    // A raw entity passthrough would surface `attributes`; this contract is composed deliberately.
    expect(hook).not.toContain("attributes");
  });
});
