import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { ROUTES } from "@/lib/routes";

/**
 * Golden thread for the ROM applicant self-service UI: the professional can USE their regulatory
 * affairs — see renewal eligibility and their applications, then work a single application's
 * correspondence (respond to RFIs, message the council). Both pages read the AUTHENTICATED person's
 * own record (no providerId param — self-service, resolved from the trust context).
 */
const SRC = join(process.cwd(), "src");
const read = (p: string) => readFileSync(join(SRC, p), "utf8");

describe("ROM applicant self-service UI", () => {
  it("registers the application detail route", () => {
    const route = ROUTES.find((r) => r.path === "/professional/regulatory/applications/[id]");
    expect(route).toBeDefined();
  });

  it("My Regulatory Affairs reads renewal eligibility and applications", () => {
    const page = read("app/professional/regulatory/page.tsx");
    expect(page).toContain("/internal/v1/me/regulatory/renewal-eligibility");
    expect(page).toContain("/internal/v1/me/regulatory/applications");
    expect(page).not.toContain("providerId=");
  });

  it("the application detail page reads correspondence and posts RFI responses + messages", () => {
    const page = read("app/professional/regulatory/applications/[id]/page.tsx");
    expect(page).toContain("/correspondence");
    expect(page).toContain("/rfis/");
    expect(page).toContain("/messages");
    expect(page).not.toContain("providerId=");
  });
});
