import { describe, expect, it } from "vitest";
import { isSchedulingClusterPath } from "../scheduling-paths";

describe("isSchedulingClusterPath", () => {
  it("matches scheduling hub and nested routes", () => {
    expect(isSchedulingClusterPath("/scheduling")).toBe(true);
    expect(isSchedulingClusterPath("/scheduling/roster")).toBe(true);
    expect(isSchedulingClusterPath("/scheduling/on-call")).toBe(true);
    expect(isSchedulingClusterPath("/scheduling/noticeboard")).toBe(true);
  });

  it("does not match unrelated paths", () => {
    expect(isSchedulingClusterPath("/scheduling-other")).toBe(false);
    expect(isSchedulingClusterPath("/queue")).toBe(false);
    expect(isSchedulingClusterPath("/")).toBe(false);
  });
});
