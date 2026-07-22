import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Golden thread for the ROM regulator console UI: a council officer can USE the registration-review
 * console — work the queue of open applications, read the full correspondence (including internal
 * notes), open/close RFIs, post applicant-visible or internal messages, and record a decision. The
 * page also traps the RECUSAL_REQUIRED guard (an officer may not process their own application).
 *
 * This asserts the page wiring only — the route already exists, so no ROUTES lookup is made.
 */
const SRC = join(process.cwd(), "src");
const read = (p: string) => readFileSync(join(SRC, p), "utf8");

describe("ROM regulator console UI", () => {
  const page = read("app/work/regulators/[regulatorId]/registration-review/page.tsx");

  it("no longer renders the ScopedAdministrationSurface stub", () => {
    expect(page).not.toContain("ScopedAdministrationSurface");
  });

  it("reads the console application queue and a selected application's correspondence", () => {
    expect(page).toContain("/internal/v1/regulatory/console/applications");
    expect(page).toContain("/correspondence");
  });

  it("posts RFIs and records decisions", () => {
    expect(page).toContain("/rfis");
    expect(page).toContain("/decision");
  });

  it("traps the RECUSAL_REQUIRED own-application guard", () => {
    expect(page).toContain("RECUSAL_REQUIRED");
  });
});
