import { describe, it, expect } from "vitest";
import { translate, LOCALES, type Locale } from "./index";
import en from "./locales/en.json";

describe("translate() — C8 i18n fallback contract", () => {
  it("returns the locale's own value when the key exists", () => {
    // 'sn' carries a real translation for the critical Sign-in chrome string.
    expect(translate("sn", "public.chrome.signIn")).toBe("Pinda");
    expect(translate("nd", "public.chrome.signIn")).toBe("Ngena");
  });

  it("falls back to English when a key is missing in the target locale", () => {
    // 'public.download.title' is intentionally NOT translated in sn/nd — the
    // English fallback must be returned rather than the raw key.
    const enValue = (en as { public: { download: { title: string } } }).public.download.title;
    expect(enValue).toBe("Take Impilo with you");
    expect(translate("sn", "public.download.title")).toBe(enValue);
    expect(translate("nd", "public.download.title")).toBe(enValue);
  });

  it("returns the key itself as a last resort when it is absent everywhere", () => {
    expect(translate("en", "public.does.not.exist")).toBe("public.does.not.exist");
    expect(translate("sn", "public.does.not.exist")).toBe("public.does.not.exist");
  });

  it("resolves English directly for every English key path used by the front door", () => {
    expect(translate("en", "public.emergency.title")).toBe("If life is at risk, get help now");
    expect(translate("en", "public.findCare.search")).toBe("Search");
  });

  it("exposes the three official languages with native names", () => {
    const codes = Object.keys(LOCALES) as Locale[];
    expect(codes).toEqual(["en", "sn", "nd"]);
    expect(LOCALES.sn.nativeName).toBe("chiShona");
    expect(LOCALES.nd.nativeName).toBe("isiNdebele");
  });
});
