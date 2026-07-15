import { describe, it, expect, beforeEach } from "vitest";
import { appStore } from "./appStore";

describe("appStore onboarding + guest gateway intent", () => {
  beforeEach(() => appStore.getState().setOnboardingScreen(null));

  it("defaults to no onboarding destination", () => {
    expect(appStore.getState().onboardingScreen).toBeNull();
  });

  it("routes pre-auth onboarding destinations (signup + contact-signup)", () => {
    appStore.getState().setOnboardingScreen("signup");
    expect(appStore.getState().onboardingScreen).toBe("signup");
    appStore.getState().setOnboardingScreen("contact-signup");
    expect(appStore.getState().onboardingScreen).toBe("contact-signup");
  });

  it("routes anonymous guest gateway destinations", () => {
    for (const dest of ["health-info", "verify", "track-emergency"] as const) {
      appStore.getState().setOnboardingScreen(dest);
      expect(appStore.getState().onboardingScreen).toBe(dest);
    }
  });

  it("clears back to the login screen", () => {
    appStore.getState().setOnboardingScreen("verify");
    appStore.getState().setOnboardingScreen(null);
    expect(appStore.getState().onboardingScreen).toBeNull();
  });

  it("advances to the post-auth assurance step", () => {
    appStore.getState().setOnboardingScreen("assurance");
    expect(appStore.getState().onboardingScreen).toBe("assurance");
  });
});
