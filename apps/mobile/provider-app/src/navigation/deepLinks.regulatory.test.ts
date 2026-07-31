import { describe, expect, it } from "vitest";
import { resolveProviderDeepLink } from "./deepLinks";

describe("provider regulatory deep links", () => {
  it("opens My Regulatory Affairs under professional", () => {
    const intent = resolveProviderDeepLink("https://impilo.mohcc.gov.zw/professional/regulatory");
    expect(intent).toEqual({
      mode: "provider",
      tab: "professional",
      professionalSurface: "regulatory",
    });
  });

  it("carries a contributor invite id", () => {
    const intent = resolveProviderDeepLink(
      "impilo-provider://professional/regulatory/contribute/99",
    );
    expect(intent?.tab).toBe("professional");
    expect(intent?.professionalSurface).toBe("regulatory");
    expect(intent?.inviteId).toBe("99");
  });
});
