import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { ROUTES } from "@/lib/routes";

/**
 * Golden thread for ROM-W3 "My Regulatory Affairs": the self-service page is registered and reads
 * the AUTHENTICATED person's own record (no providerId param — the admin-plane defect it replaces).
 */
const SRC = join(process.cwd(), "src");
const read = (p: string) => readFileSync(join(SRC, p), "utf8");

describe("ROM-W3 My Regulatory Affairs", () => {
  it("registers the self-service route under my_professional", () => {
    const route = ROUTES.find((r) => r.path === "/professional/regulatory");
    expect(route).toBeDefined();
    expect(route?.navZone).toBe("professional");
  });

  it("reads the caller's own record via the me lane (never a providerId param)", () => {
    const page = read("app/professional/regulatory/page.tsx");
    expect(page).toContain("/internal/v1/me/regulatory/summary");
    // No providerId query param — the caller is resolved from the trust context, not a param.
    expect(page).not.toContain("providerId=");
    // Surfaces standing + restrictions (conditions on practice).
    expect(page).toContain("standing");
    expect(page).toContain("restrictions");
  });
});
