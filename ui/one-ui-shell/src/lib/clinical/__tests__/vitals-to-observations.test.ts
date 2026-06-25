import { describe, it, expect } from "vitest";
import { vitalsToObservationInputs } from "../vitals-to-observations";

describe("vitalsToObservationInputs", () => {
  it("maps numeric vitals to LOINC + UCUM observation inputs", () => {
    const obs = vitalsToObservationInputs({ heart_rate: 110, systolic_bp: 128 });
    const hr = obs.find((o) => o.loincCode === "8867-4");
    expect(hr).toBeDefined();
    expect(hr?.value).toBe(110);
    expect(hr?.unit).toBe("/min");
    expect(hr?.category).toBe("VITAL");
    const sbp = obs.find((o) => o.loincCode === "8480-6");
    expect(sbp?.unit).toBe("mm[Hg]");
  });

  it("omits absent / non-numeric vitals", () => {
    const obs = vitalsToObservationInputs({ heart_rate: null, spo2: undefined, temperature_c: 37.0 });
    expect(obs.map((o) => o.loincCode)).toEqual(["8310-5"]);
  });

  it("returns empty for no vitals", () => {
    expect(vitalsToObservationInputs({})).toEqual([]);
  });
});
