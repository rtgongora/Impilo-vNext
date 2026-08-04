/**
 * OIDC login URL construction — the client half of the step-up continuation
 * round trip, tested by execution rather than by reading the source.
 *
 * A step-up is raised BY a challenge, so it is the one flow certain to have a
 * continuation to carry. If the browser omits the parameter, a user challenged
 * mid-journey re-authenticates successfully and lands nowhere near what they
 * were doing — the server accepting `continuation` is worthless on its own.
 *
 * This suite replaces a source-text assertion in the BFF's
 * `StepUpContinuationTest`, which sliced `beginOidcLogin`'s body up to the next
 * `export` and searched it for `query.set("continuation"`. When the query
 * construction was deliberately extracted into the shared `buildOidcLoginUrl`
 * (so the windowed sign-in and the redirect sign-in cannot drift apart), the
 * call moved past the slice boundary and the assertion failed while the
 * behaviour was correct. Behaviour is what matters, so behaviour is what is
 * asserted here.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { beginOidcLogin, buildOidcLoginUrl } from "../web-session";

function queryOf(url: string): URLSearchParams {
  return new URLSearchParams(url.slice(url.indexOf("?") + 1));
}

describe("buildOidcLoginUrl — continuation reaches the authorize query string", () => {
  it("carries the continuation issued by a trust challenge", () => {
    const url = buildOidcLoginUrl({
      returnTo: "/patients/1/notes",
      requiredAcr: "urn:impilo:aal2",
      continuation: "cont-abc",
    });

    expect(url.startsWith("/internal/v1/auth/oidc/authorize?")).toBe(true);
    const q = queryOf(url);
    expect(q.get("continuation")).toBe("cont-abc");
    // The rest of the step-up shape must survive alongside it.
    expect(q.get("returnTo")).toBe("/patients/1/notes");
    expect(q.get("acr")).toBe("urn:impilo:aal2");
  });

  it("omits the parameter entirely when there is no continuation", () => {
    const q = queryOf(buildOidcLoginUrl({ returnTo: "/home", requiredAcr: "urn:impilo:aal2" }));
    expect(q.has("continuation")).toBe(false);
    expect(q.get("returnTo")).toBe("/home");
  });

  it("treats a blank continuation as absent rather than sending an empty id", () => {
    const q = queryOf(buildOidcLoginUrl({ returnTo: "/home", continuation: "   " }));
    expect(q.has("continuation")).toBe(false);
  });

  it("trims a padded continuation id", () => {
    const q = queryOf(buildOidcLoginUrl({ returnTo: "/home", continuation: "  cont-xyz  " }));
    expect(q.get("continuation")).toBe("cont-xyz");
  });

  it("defaults returnTo rather than emitting an empty destination", () => {
    const q = queryOf(buildOidcLoginUrl({ continuation: "cont-1" }));
    expect(q.get("returnTo")).toBe("/home");
    expect(q.get("continuation")).toBe("cont-1");
  });
});

describe("beginOidcLogin — the redirect path navigates to that same URL", () => {
  const assign = vi.fn();
  let originalLocation: Location;

  beforeEach(() => {
    assign.mockClear();
    originalLocation = window.location;
    Object.defineProperty(window, "location", {
      value: { ...originalLocation, assign },
      writable: true,
      configurable: true,
    });
  });

  afterEach(() => {
    Object.defineProperty(window, "location", {
      value: originalLocation,
      writable: true,
      configurable: true,
    });
  });

  it("sends the continuation through the full-page redirect", () => {
    beginOidcLogin({
      returnTo: "/patients/1/notes",
      requiredAcr: "urn:impilo:aal2",
      continuation: "cont-abc",
    });

    expect(assign).toHaveBeenCalledTimes(1);
    const navigatedTo = assign.mock.calls[0][0] as string;
    expect(queryOf(navigatedTo).get("continuation")).toBe("cont-abc");
  });

  it("navigates to exactly what the shared builder produced — one construction, two paths", () => {
    const input = {
      returnTo: "/work/queue",
      loginHint: "user@example.org",
      requiredAcr: "urn:impilo:aal3" as const,
      continuation: "cont-999",
    };

    beginOidcLogin(input);

    // The windowed sign-in uses buildOidcLoginUrl directly; the redirect uses
    // beginOidcLogin. If these two ever diverge, a windowed login and a
    // redirect login stop agreeing about acr or the continuation id.
    expect(assign).toHaveBeenCalledWith(buildOidcLoginUrl(input));
  });
});
