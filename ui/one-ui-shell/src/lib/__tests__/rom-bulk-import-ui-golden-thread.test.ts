import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Golden thread for the ROM bulk-import UI: a council officer can USE the register ingest rail —
 * STAGE an existing register (upload/paste CSV), have rows MATCHED against existing records, REVIEW
 * (accept/reject) each staged row, and APPLY the accepted rows. The page must make the "grants no
 * authority" boundary explicit: applying writes register data only and never provisions a login.
 *
 * This asserts the page wiring only — the route is new and registered separately, so no ROUTES
 * lookup is made.
 */
const SRC = join(process.cwd(), "src");
const read = (p: string) => readFileSync(join(SRC, p), "utf8");

describe("ROM bulk-import UI", () => {
  const page = read("app/work/regulators/[regulatorId]/bulk-import/page.tsx");

  it("lists prior imports and reads a batch's staged rows", () => {
    expect(page).toContain("/internal/v1/regulatory/console/imports");
    expect(page).toContain("/rows");
  });

  it("stages an upload by posting to the imports endpoint", () => {
    expect(page).toContain('apiClient.post<unknown>("/internal/v1/regulatory/console/imports"');
  });

  it("reviews staged rows and applies the accepted ones", () => {
    expect(page).toContain("/review");
    expect(page).toContain("/apply");
  });

  it("makes the no-authority boundary explicit in the copy", () => {
    expect(page).toContain("grants no");
  });
});
