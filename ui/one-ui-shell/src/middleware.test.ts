import { describe, expect, it } from "vitest";
import { NextRequest } from "next/server";
import { isPublicPath, middleware, PUBLIC_PREFIXES } from "./middleware";

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

describe("middleware double-slash normalization", () => {
  it("redirects duplicate-slash paths to the collapsed path", () => {
    const req = new NextRequest("http://41.57.127.235//telemedicine/session/abc");
    const res = middleware(req);
    expect(res.status).toBe(307);
    expect(new URL(res.headers.get("location")!).pathname).toBe("/telemedicine/session/abc");
  });

  it("collapses runs of slashes anywhere in the path", () => {
    const req = new NextRequest("http://41.57.127.235/telemedicine//session///abc");
    const res = middleware(req);
    expect(res.status).toBe(307);
    expect(new URL(res.headers.get("location")!).pathname).toBe("/telemedicine/session/abc");
  });

  it("leaves clean paths alone (session gate still applies)", () => {
    const req = new NextRequest("http://41.57.127.235/telemedicine/session/abc");
    const res = middleware(req);
    // No session cookie → login redirect, NOT a slash-normalization redirect.
    expect(new URL(res.headers.get("location")!).pathname).toBe("/auth/login");
  });
});
