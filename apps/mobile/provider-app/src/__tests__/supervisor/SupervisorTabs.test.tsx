/**
 * SupervisorTabs — Work Home is the first tab (parity with ProviderTabs).
 */
import { describe, it, expect, vi } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";

describe("SupervisorTabs", () => {
  it("mounts WorkHomeScreen as the first tab", () => {
    const src = readFileSync(
      join(__dirname, "../../navigation/SupervisorTabs.tsx"),
      "utf8",
    );
    expect(src).toContain('key: "workhome"');
    expect(src).toContain("WorkHomeScreen");
    expect(src.indexOf("workhome")).toBeLessThan(src.indexOf('key: "dashboard"'));
  });
});
