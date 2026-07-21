import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { ROUTES } from "@/lib/routes";
import { DOMAIN_LABELS, unwrapList, unwrapObject } from "@/lib/rito/reputation";

/**
 * Golden thread for the interactive reputation surfaces (RW12-14): the write/manage UI is
 * registered and bound to the real BFF persona lanes — closing the loop the read-only cards left
 * open (rating submission, provider response, management console, moderation queue).
 */
const SRC = join(process.cwd(), "src");
const read = (p: string) => readFileSync(join(SRC, p), "utf8");

describe("RW12-14 interactive reputation surfaces", () => {
  it("registers the three interactive routes", () => {
    const paths = ROUTES.map((r) => r.path);
    expect(paths).toContain("/feedback/visit/[encounterRef]");
    expect(paths).toContain("/work/rito/my-reputation");
    expect(paths).toContain("/work/facility/rito/reputation");
  });

  it("RW12 rating page posts to the citizen rating lane and captures experience domains", () => {
    const page = read("app/feedback/visit/[encounterRef]/page.tsx");
    expect(page).toContain("/internal/v1/nompilo/rito/ratings");
    expect(page).toContain("CLIENT_EXPERIENCE");
    expect(page).toContain("ACCESS_PROCESS");
    // Identity shield messaging is present (respondent protected).
    expect(page).toContain("respondentIdentityProtected");
  });

  it("RW13 provider surface reads management view + own ratings and can respond", () => {
    const page = read("app/work/rito/my-reputation/page.tsx");
    expect(page).toContain("/internal/v1/work/rito/my-reputation");
    expect(page).toContain("/internal/v1/work/rito/my-ratings");
    expect(page).toContain("/response");
    // Defaults to the signed-in provider.
    expect(page).toContain("linkedIds");
  });

  it("RW14 console reads the moderation queue and moderates", () => {
    const page = read("app/work/facility/rito/reputation/page.tsx");
    expect(page).toContain("/internal/v1/facility/rito/moderation-queue");
    expect(page).toContain("/moderate");
    expect(page).toContain("REVIEW_BOMBING_SUSPECTED");
  });

  it("shared reputation helpers expose the four canonical domains + tolerant unwrappers", () => {
    expect(Object.keys(DOMAIN_LABELS)).toEqual(
      expect.arrayContaining([
        "CLIENT_EXPERIENCE",
        "ACCESS_PROCESS",
        "PROFESSIONAL_QUALITY",
        "SAFETY_ACCOUNTABILITY",
      ]),
    );
    expect(unwrapList([1, 2])).toEqual([1, 2]);
    expect(unwrapList({ data: [3] })).toEqual([3]);
    expect(unwrapObject({ a: 1 })).toEqual({ a: 1 });
    expect(unwrapObject({ data: { b: 2 } })).toEqual({ b: 2 });
    expect(unwrapObject([1, 2])).toBeNull();
  });
});
