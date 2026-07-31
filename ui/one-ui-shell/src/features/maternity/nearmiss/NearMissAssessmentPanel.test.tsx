import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
import type { NearMissClassifyResponse } from "./useMaternalNearMiss";

const classifyMutate = vi.fn();

const definitionState: {
  isLoading: boolean;
  isError: boolean;
  definition: {
    id: string;
    title: string;
    sections: Array<{
      id: string;
      title: string;
      description?: string;
      fields: Array<{
        id: string;
        linkId: string;
        label: string;
        kind: string;
        unit?: string;
        options?: { code: string; display: string }[];
      }>;
    }>;
  } | null;
} = {
  isLoading: false,
  isError: false,
  definition: {
    id: "maternal-near-miss-v1",
    title: "Maternal near-miss",
    sections: [
      {
        id: "cardiovascular",
        title: "Cardiovascular",
        fields: [
          {
            id: "nearMiss.shock",
            linkId: "nearMiss.shock",
            label: "Shock",
            kind: "coded_single",
            options: [
              { code: "PRESENT", display: "Yes" },
              { code: "ABSENT", display: "No" },
            ],
          },
          {
            id: "nearMiss.lactateMmolL",
            linkId: "nearMiss.lactateMmolL",
            label: "Lactate",
            kind: "decimal",
            unit: "mmol/L",
          },
        ],
      },
    ],
  },
};

vi.mock("./useMaternalNearMiss", async () => {
  const actual = await vi.importActual<typeof import("./useMaternalNearMiss")>("./useMaternalNearMiss");
  return {
    ...actual,
    useNearMissFormDefinition: () => definitionState,
    useClassifyMaternalNearMiss: () => ({
      mutate: classifyMutate,
      isPending: false,
      isError: false,
      error: null,
      reset: vi.fn(),
    }),
  };
});

import { NearMissAssessmentPanel } from "./NearMissAssessmentPanel";

function successResponse(overrides: Partial<NearMissClassifyResponse["data"]> = {}, metaOverrides: Partial<NearMissClassifyResponse["meta"]> = {}): NearMissClassifyResponse {
  return {
    data: {
      classification_code: "NEAR_MISS_NOT_MET",
      classification_name: "WHO near-miss criteria not met",
      status: "CLASSIFIED",
      is_near_miss: false,
      provisional: false,
      unresolved_criteria: [],
      missing_inputs: [],
      rationale: "No organ-dysfunction criterion was met on the information recorded.",
      review_required: false,
      review_note: null,
      ...overrides,
    },
    meta: {
      request_id: "req-1",
      correlation_id: "corr-1",
      form_key: "impilo.maternal.nearmiss.assessment.v1",
      criteria_recorded: 1,
      criteria_left_blank: [],
      ...metaOverrides,
    },
  };
}

describe("NearMissAssessmentPanel", () => {
  beforeEach(() => {
    classifyMutate.mockReset();
  });

  it("renders form-21 sections from the fetched definition, unanswered by default", () => {
    render(<NearMissAssessmentPanel patientId="p1" />);
    expect(screen.getByText("Cardiovascular")).toBeInTheDocument();
    expect(screen.getByText("Shock")).toBeInTheDocument();
    expect(screen.getByText(/Lactate/)).toBeInTheDocument();
    // Blank by default — never pre-answered.
    expect(screen.getByText("not assessed")).toBeInTheDocument();
  });

  it("omits a blank field and sends only what was answered on submit", async () => {
    const user = userEvent.setup();
    render(<NearMissAssessmentPanel patientId="p1" />);

    await user.click(screen.getByTestId("near-miss-field-nearMiss.shock-PRESENT"));
    await user.click(screen.getByRole("button", { name: "Classify near-miss" }));

    expect(classifyMutate).toHaveBeenCalledTimes(1);
    const [answers] = classifyMutate.mock.calls[0];
    expect(answers).toEqual({ "nearMiss.shock": "PRESENT" });
    expect(answers).not.toHaveProperty("nearMiss.lactateMmolL");
  });

  it("clicking the same coded option again retracts it back to blank rather than toggling", async () => {
    const user = userEvent.setup();
    render(<NearMissAssessmentPanel patientId="p1" />);

    const presentBtn = screen.getByTestId("near-miss-field-nearMiss.shock-PRESENT");
    await user.click(presentBtn);
    expect(presentBtn).toHaveAttribute("aria-pressed", "true");
    await user.click(presentBtn);
    expect(presentBtn).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByText("not assessed")).toBeInTheDocument();
  });

  it("renders a near-miss identification distinctly, with its rationale and review note", async () => {
    const user = userEvent.setup();
    classifyMutate.mockImplementation((_answers, opts) => {
      opts?.onSuccess?.(
        successResponse({
          classification_code: "NEAR_MISS_CARDIOVASCULAR",
          classification_name: "Cardiovascular dysfunction",
          is_near_miss: true,
          review_required: true,
          review_note: "She met the WHO near-miss criteria and survived.",
          rationale: "Signs meet the criteria for NEAR_MISS_CARDIOVASCULAR.",
        }),
      );
    });
    render(<NearMissAssessmentPanel patientId="p1" />);
    await user.click(screen.getByRole("button", { name: "Classify near-miss" }));

    expect(await screen.findByText(/Near-miss identified/)).toBeInTheDocument();
    expect(screen.getByText(/Cardiovascular dysfunction/)).toBeInTheDocument();
    expect(screen.getByText(/She met the WHO near-miss criteria/)).toBeInTheDocument();
  });

  it("never renders INDETERMINATE as 'no near-miss', and names the missing inputs", async () => {
    const user = userEvent.setup();
    classifyMutate.mockImplementation((_answers, opts) => {
      opts?.onSuccess?.(
        successResponse({
          classification_code: null,
          classification_name: null,
          status: "INDETERMINATE",
          is_near_miss: false,
          unresolved_criteria: ["NEAR_MISS_CARDIOVASCULAR"],
          missing_inputs: ["nearMiss.lactateMmolL"],
          rationale: "Cannot classify: Cardiovascular dysfunction could not be excluded.",
        }),
      );
    });
    render(<NearMissAssessmentPanel patientId="p1" />);
    await user.click(screen.getByRole("button", { name: "Classify near-miss" }));

    expect(await screen.findByText(/Indeterminate — a near-miss cannot be ruled out/)).toBeInTheDocument();
    expect(screen.queryByText(/no near-miss/i)).not.toBeInTheDocument();
    // "Cardiovascular" appears both as the section heading and as the humanised unresolved-tier
    // name; "Lactate" both as the field label and as the named missing input — both are expected.
    expect(screen.getAllByText("Cardiovascular").length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText(/Lactate/).length).toBeGreaterThanOrEqual(2);
  });

  it("renders NOT_MET with its non-reassuring disclaimer intact", async () => {
    const user = userEvent.setup();
    classifyMutate.mockImplementation((_answers, opts) => {
      opts?.onSuccess?.(successResponse());
    });
    render(<NearMissAssessmentPanel patientId="p1" />);
    await user.click(screen.getByRole("button", { name: "Classify near-miss" }));

    expect(await screen.findByText("WHO near-miss criteria not met")).toBeInTheDocument();
    expect(screen.getByText(/No organ-dysfunction criterion was met/)).toBeInTheDocument();
  });

  it("names blank criteria even when a definite classification was reached", async () => {
    const user = userEvent.setup();
    classifyMutate.mockImplementation((_answers, opts) => {
      opts?.onSuccess?.(successResponse({}, { criteria_left_blank: ["lactateMmolL"] }));
    });
    render(<NearMissAssessmentPanel patientId="p1" />);
    await user.click(screen.getByRole("button", { name: "Classify near-miss" }));

    expect(await screen.findByText(/Left blank — not assessed, not recorded as absent/)).toBeInTheDocument();
    expect(screen.getAllByText(/Lactate/).length).toBeGreaterThanOrEqual(2);
  });
});
