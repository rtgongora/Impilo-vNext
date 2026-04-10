import { describe, expect, it } from "vitest";
import { publicHealthTabFromSearchParam } from "./publicHealthTabParams";

describe("publicHealthTabFromSearchParam", () => {
  it("defaults to dashboard for null or empty", () => {
    expect(publicHealthTabFromSearchParam(null)).toBe("dashboard");
    expect(publicHealthTabFromSearchParam("")).toBe("dashboard");
  });

  it("maps known tabs case-insensitively", () => {
    expect(publicHealthTabFromSearchParam("Surveillance")).toBe("surveillance");
    expect(publicHealthTabFromSearchParam("CAMPAIGNS")).toBe("campaigns");
  });

  it("maps sites to field (INDAWO / home deep link)", () => {
    expect(publicHealthTabFromSearchParam("sites")).toBe("field");
  });

  it("falls back to dashboard for unknown values", () => {
    expect(publicHealthTabFromSearchParam("unknown-tab")).toBe("dashboard");
  });
});
