import { describe, expect, it } from "vitest";
import {
  buildPostLoginResolvingPath,
  isSafeReturnTo,
  resolvePostLoginDestination,
} from "@/lib/resolve-post-login-destination";

describe("resolvePostLoginDestination", () => {
  it("routes activated provider to provider-workspace when facility is set", () => {
    const result = resolvePostLoginDestination({
      user: {
        actorType: "PROVIDER",
        roles: ["CLINICIAN"],
        providerActivated: true,
        providerId: "PRV-001",
      },
      hasFacility: true,
    });
    expect(result).toEqual({
      href: "/provider-workspace",
      operationalMode: "facility_work",
    });
  });

  it("applies facility guard for provider-workspace when facility is missing", () => {
    const result = resolvePostLoginDestination({
      user: {
        actorType: "PROVIDER",
        roles: ["CLINICIAN"],
        providerActivated: true,
        providerId: "PRV-001",
      },
      hasFacility: false,
    });
    expect(result.href).toBe("/facility?returnTo=%2Fprovider-workspace");
    expect(result.operationalMode).toBe("facility_work");
  });

  it("routes citizen to home with my_life mode", () => {
    const result = resolvePostLoginDestination({
      user: {
        actorType: "CITIZEN",
        roles: ["CITIZEN"],
        providerActivated: false,
      },
    });
    expect(result).toEqual({ href: "/home", operationalMode: "my_life" });
  });

  it("sends linked-but-inactive provider to activation", () => {
    const result = resolvePostLoginDestination({
      user: {
        actorType: "CITIZEN",
        roles: ["CLINICIAN"],
        providerActivated: false,
      },
      linkedProviderId: "PRV-2024-00001",
    });
    expect(result.href).toBe("/provider/activate?returnTo=%2Fprovider-workspace");
  });

  it("honors safe returnTo and applies facility guard when needed", () => {
    const result = resolvePostLoginDestination({
      user: {
        actorType: "PROVIDER",
        roles: ["CLINICIAN"],
        providerActivated: true,
        providerId: "PRV-001",
      },
      hasFacility: false,
      returnTo: "/clinical",
    });
    expect(result.href).toBe("/facility?returnTo=%2Fclinical");
  });

  it("rejects unsafe returnTo values", () => {
    expect(isSafeReturnTo("//evil.example")).toBe(false);
    expect(isSafeReturnTo("/auth/login")).toBe(false);
    expect(isSafeReturnTo("/home")).toBe(true);
  });

  it("buildPostLoginResolvingPath preserves returnTo", () => {
    expect(buildPostLoginResolvingPath("/queue")).toBe(
      "/auth/resolving?returnTo=%2Fqueue",
    );
    expect(buildPostLoginResolvingPath()).toBe("/auth/resolving");
  });
});
