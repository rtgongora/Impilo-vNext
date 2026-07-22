import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Golden thread for the ROM committee & hearings console UI: a committee member can USE the console —
 * see the cases docketed to them (and only those), docket another member onto a case, schedule a
 * hearing, and file an appeal. The page also traps the RECUSAL_REQUIRED guard (a member may not be
 * docketed to their own case).
 *
 * This asserts the page wiring only — the route is registered separately, so no ROUTES lookup is made.
 */
const SRC = join(process.cwd(), "src");
const read = (p: string) => readFileSync(join(SRC, p), "utf8");

describe("ROM committee & hearings console UI", () => {
  const page = read("app/work/regulators/[regulatorId]/committee/page.tsx");

  it("reads only the cases docketed to the signed-in committee member", () => {
    expect(page).toContain("/internal/v1/regulatory/console/hearings/my-docket");
    expect(page).toContain("A committee member sees only the cases docketed to them.");
    expect(page).toContain("No cases are docketed to you.");
  });

  it("posts docket, schedule and appeal actions", () => {
    expect(page).toContain("/hearings/docket");
    expect(page).toContain("/schedule");
    expect(page).toContain("/appeal");
  });

  it("traps the RECUSAL_REQUIRED own-case guard", () => {
    expect(page).toContain("RECUSAL_REQUIRED");
  });
});
