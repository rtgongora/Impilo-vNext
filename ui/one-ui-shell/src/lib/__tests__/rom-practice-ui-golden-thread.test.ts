import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Golden thread for the ROM practice-regulation applicant/owner UI: a prospective practice owner
 * builds a PRACTICE ESTABLISHMENT case (create a draft, then submit it) BEFORE any facility exists;
 * the canonical facility is created only when the regulator APPROVES. The page reads the
 * AUTHENTICATED person's own establishment cases (no providerId param — self-service, resolved from
 * the trust context). No ROUTES assertion here — the route is registered separately.
 */
const SRC = join(process.cwd(), "src");
const read = (p: string) => readFileSync(join(SRC, p), "utf8");

describe("ROM practice-regulation applicant/owner UI", () => {
  const page = read("app/professional/practice-regulation/page.tsx");

  it("reads the caller's own practice establishments (self-service, no providerId)", () => {
    expect(page).toContain("/internal/v1/me/regulatory/practice-establishments");
    expect(page).not.toContain("providerId=");
  });

  it("creates a draft by posting to the establishments endpoint", () => {
    expect(page).toContain(
      'apiClient.post<unknown>("/internal/v1/me/regulatory/practice-establishments"',
    );
  });

  it("submits a draft by posting to /submit", () => {
    expect(page).toContain("/submit");
  });

  it("shows the facility-only-on-approval doctrine copy", () => {
    expect(page).toContain("created on approval");
  });
});
