import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * HAR W6 golden thread — the register's largest population must stay reachable in the browser.
 *
 * The HPA import landed 5,509 facilities as IMPORTED_PENDING_CONFIGURATION. The registry page had
 * no option for that lifecycle state and no pagination, so those rows were both unfilterable and,
 * beyond the first 30 of 7,285, unreachable. The data was there the whole time; the screen simply
 * could not ask for it. These assertions fail if either regression returns.
 */
describe("registry facility browser", () => {
  const page = readFileSync(
    join(process.cwd(), "src/app/registry/facilities/page.tsx"),
    "utf8",
  );

  it("offers the HPA-listed lifecycle state as a filter", () => {
    expect(page).toContain('value="IMPORTED_PENDING_CONFIGURATION"');
  });

  it("labels it honestly rather than as an operating state", () => {
    // "Registered with HPA — awaiting configuration" must not read as "active" or "operating".
    const optionLine = page
      .split("\n")
      .find((line) => line.includes("IMPORTED_PENDING_CONFIGURATION"));
    expect(optionLine).toBeDefined();
    expect(optionLine!.toLowerCase()).toContain("hpa");
    expect(optionLine!.toLowerCase()).not.toContain("operating");
  });

  it("pages through the register instead of pinning to the first screenful", () => {
    expect(page).toContain("setPage");
    expect(page).toContain("hasNext");
    expect(page).toContain("totalElements");
    // The old hardcoded `page: 0` is gone.
    expect(page).not.toContain("page: 0,");
  });

  it("filters by province and resets to the first page when a filter changes", () => {
    expect(page).toContain("All provinces");
    expect(page).toContain("setProvince(event.target.value); setPage(0)");
    expect(page).toContain("setRegulatoryStatus(event.target.value); setPage(0)");
  });
});
