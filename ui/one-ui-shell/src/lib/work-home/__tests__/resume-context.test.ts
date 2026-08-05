import { describe, it, expect } from "vitest";
import { buildResumeOffer, soleMode } from "../resume-context";
import { buildContextGuardRedirect } from "../../resolve-post-login-destination";
import type { ResolvedWorkContextView } from "../../trust";

function ctx(over: Partial<ResolvedWorkContextView> = {}): ResolvedWorkContextView {
  return {
    contextId: "ctx-1",
    contextKind: "facility",
    sourceSystem: "VASHANDI",
    availableModes: ["CLINICAL_CARE"],
    defaultMode: "CLINICAL_CARE",
    restrictions: [],
    label: "Parirenyatwa — OPD",
    groupHint: "today",
    ...over,
  };
}

describe("buildResumeOffer", () => {
  it("offers a one-tap resume when the BFF ranked a context", () => {
    const offer = buildResumeOffer({
      contexts: [ctx(), ctx({ contextId: "ctx-2", label: "Harare Central" })],
      recommendedContextId: "ctx-1",
      sourceStatuses: [{ system: "VASHANDI", state: "LIVE" }],
      isLoading: false,
    });

    expect(offer.state).toBe("RESUMABLE");
    expect(offer.recommended?.contextId).toBe("ctx-1");
    expect(offer.recommendedMode).toBe("CLINICAL_CARE");
    // The recommended one must not also appear in the "somewhere else" list.
    expect(offer.others.map((c) => c.contextId)).toEqual(["ctx-2"]);
  });

  it("does not guess a mode when the context grants several", () => {
    const offer = buildResumeOffer({
      contexts: [ctx({ availableModes: ["CLINICAL_CARE", "DEPARTMENT_MANAGEMENT"], defaultMode: null })],
      recommendedContextId: "ctx-1",
      sourceStatuses: [],
      isLoading: false,
    });

    // Which authority they want is a real question — resume must not answer it for them.
    expect(offer.state).toBe("RESUMABLE");
    expect(offer.recommendedMode).toBeNull();
  });

  it("asks them to choose when nothing is ranked", () => {
    const offer = buildResumeOffer({
      contexts: [ctx(), ctx({ contextId: "ctx-2" })],
      recommendedContextId: null,
      sourceStatuses: [],
      isLoading: false,
    });
    expect(offer.state).toBe("CHOOSE");
    expect(offer.others).toHaveLength(2);
  });

  it("treats a recommendation that is not in the list as no recommendation", () => {
    const offer = buildResumeOffer({
      contexts: [ctx()],
      recommendedContextId: "ctx-vanished",
      sourceStatuses: [],
      isLoading: false,
    });
    expect(offer.state).toBe("CHOOSE");
  });

  it("distinguishes DEGRADED from EMPTY — the difference is a lie or the truth", () => {
    const unreachable = buildResumeOffer({
      contexts: [],
      recommendedContextId: null,
      sourceStatuses: [{ system: "VASHANDI", state: "DEGRADED", message: "timeout" }],
      isLoading: false,
    });
    // "You have no workplaces" would be a false statement about this person's postings.
    expect(unreachable.state).toBe("DEGRADED");
    expect(unreachable.degradedSources).toEqual(["VASHANDI"]);

    const genuinelyNone = buildResumeOffer({
      contexts: [],
      recommendedContextId: null,
      sourceStatuses: [{ system: "VASHANDI", state: "EMPTY" }],
      isLoading: false,
    });
    expect(genuinelyNone.state).toBe("EMPTY");
  });

  it("never reports a verdict while still loading", () => {
    const offer = buildResumeOffer({
      contexts: [],
      recommendedContextId: null,
      sourceStatuses: [],
      isLoading: true,
    });
    expect(offer.state).toBe("LOADING");
  });
});

describe("soleMode", () => {
  it("returns the single mode", () => {
    expect(soleMode(ctx({ availableModes: ["VIRTUAL_CARE"], defaultMode: null }))).toBe("VIRTUAL_CARE");
  });
  it("prefers the declared default when several are offered", () => {
    expect(soleMode(ctx({ availableModes: ["CLINICAL_CARE", "VIRTUAL_CARE"], defaultMode: "VIRTUAL_CARE" })))
      .toBe("VIRTUAL_CARE");
  });
  it("returns null when there is no defensible single answer", () => {
    expect(soleMode(ctx({ availableModes: ["CLINICAL_CARE", "VIRTUAL_CARE"], defaultMode: null }))).toBeNull();
    expect(soleMode(ctx({ availableModes: [], defaultMode: null }))).toBeNull();
  });
});

describe("buildContextGuardRedirect", () => {
  it("sends the facility guard to the resolved-context resume surface, not the registry", () => {
    // 148 routes carry guard:"facility". Each one used to land on a national facility list.
    expect(buildContextGuardRedirect("/facility", "/clinical/queue"))
      .toBe("/work/resume?returnTo=%2Fclinical%2Fqueue");
  });

  it("leaves workspace and shift alone — nothing resolved to offer back yet", () => {
    expect(buildContextGuardRedirect("/workspace", "/clinical/x")).toBe("/workspace?returnTo=%2Fclinical%2Fx");
    expect(buildContextGuardRedirect("/shift", "/clinical/x")).toBe("/shift?returnTo=%2Fclinical%2Fx");
  });

  it("cannot build a redirect that returns to itself", () => {
    expect(buildContextGuardRedirect("/facility", "/work/resume")).toBe("/work/resume");
  });

  it("drops an unsafe returnTo rather than replaying it", () => {
    expect(buildContextGuardRedirect("/facility", "//attacker.example")).toBe("/work/resume");
    expect(buildContextGuardRedirect("/facility", "/auth/login")).toBe("/work/resume");
  });
});
