import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { InterpretedObservationFlags } from "../InterpretedObservationFlags";
import type { InterpretedObservationDto } from "@/hooks/queries/useGuidance";

function obs(partial: Partial<InterpretedObservationDto>): InterpretedObservationDto {
  return {
    loinc_code: "2823-3",
    local_code: null,
    display_name: "Potassium",
    category: "LAB",
    value: 6.8,
    unit: "mmol/L",
    interpretation: "CRITICAL_HIGH",
    fhir_interpretation_code: "HH",
    abnormal: true,
    critical: true,
    reference_range: {
      low: 3.5, high: 5.1, critical_low: 2.5, critical_high: 6.5, unit: "mmol/L",
      source: "OBSERVATION_DEFINITION", artifact_id: "a", version: "1.0.0", canonical_url: "u",
    },
    delta: null,
    trend: "RISING",
    note: "Critically high",
    advisory: true,
    ...partial,
  };
}

describe("InterpretedObservationFlags", () => {
  it("renders a critical flag with value and reference range", () => {
    render(<InterpretedObservationFlags observations={[obs({})]} traceId="abcdef1234" />);
    expect(screen.getByTestId("interpreted-observation-flags")).toBeInTheDocument();
    expect(screen.getByText("Potassium")).toBeInTheDocument();
    expect(screen.getByText("Critical high")).toBeInTheDocument();
    expect(screen.getByText(/Ref 3.5–5.1 mmol\/L/)).toBeInTheDocument();
    expect(screen.getByText(/Advisory — does not override/)).toBeInTheDocument();
  });

  it("hides NORMAL observations unless showNormal", () => {
    const normal = obs({ interpretation: "NORMAL", critical: false, abnormal: false, value: 4.2 });
    const { rerender } = render(<InterpretedObservationFlags observations={[normal]} />);
    expect(screen.queryByTestId("interpreted-observation-flags")).not.toBeInTheDocument();
    rerender(<InterpretedObservationFlags observations={[normal]} showNormal />);
    expect(screen.getByText("Normal")).toBeInTheDocument();
  });

  it("renders NO_REFERENCE_RANGE explicitly (never as normal)", () => {
    const noRange = obs({ interpretation: "NO_REFERENCE_RANGE", critical: false, abnormal: false, reference_range: null });
    render(<InterpretedObservationFlags observations={[noRange]} />);
    expect(screen.getByText("No reference range")).toBeInTheDocument();
    expect(screen.queryByText("Normal")).not.toBeInTheDocument();
  });
});
