import { describe, expect, it } from "vitest";
import { experiencePalettes } from "../src/theme/experience";

describe("mobile experience palettes", () => {
  it("gives Citizen a warm, calm canvas with accessible green focus", () => {
    expect(experiencePalettes.citizen.canvas).toBe("#F6FBF7");
    expect(experiencePalettes.citizen.focus).toBe("#006B36");
    expect(experiencePalettes.citizen.navigationSurface).not.toBe("#FFFFFF");
  });

  it("gives Provider a distinct professional teal shell", () => {
    expect(experiencePalettes.provider.canvas).toBe("#F2F8F8");
    expect(experiencePalettes.provider.focus).toBe("#0B5E59");
    expect(experiencePalettes.provider.navigationSurface).not.toBe(experiencePalettes.citizen.navigationSurface);
  });
});
