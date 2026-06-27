import { describe, expect, it } from "vitest";
import { isPublicPath, PUBLIC_PREFIXES } from "./middleware";

describe("isPublicPath (G-CZO-02 public L0 entry)", () => {
  it("treats the public welcome surface as reachable without a session", () => {
    expect(isPublicPath("/welcome")).toBe(true);
    expect(isPublicPath("/welcome/find-care")).toBe(true);
    expect(isPublicPath("/welcome/emergency")).toBe(true);
    expect(isPublicPath("/welcome/accessibility")).toBe(true);
  });

  it("lets the root through so it can decide welcome-vs-home by session", () => {
    expect(isPublicPath("/")).toBe(true);
  });

  it("keeps authenticated surfaces gated", () => {
    expect(isPublicPath("/home")).toBe(false);
    expect(isPublicPath("/citizen/health-id/request")).toBe(false);
    expect(isPublicPath("/settings/privacy")).toBe(false);
  });

  it("preserves the pre-existing public prefixes", () => {
    for (const prefix of ["/auth", "/verify", "/share", "/privacy", "/terms", "/consent"]) {
      expect(PUBLIC_PREFIXES).toContain(prefix);
      expect(isPublicPath(prefix + "/anything")).toBe(true);
    }
  });
});
