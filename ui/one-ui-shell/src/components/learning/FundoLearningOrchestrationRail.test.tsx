import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { FundoLearningOrchestrationRail } from "./FundoLearningOrchestrationRail";

vi.mock("@/components/learning/LearningSubjectPicker", () => ({
  useLearningSubject: () => ({ subjectType: "PERSON", subjectId: "actor-1" }),
}));

vi.mock("@/hooks/queries/useFundoLms", () => ({
  summarizeFundoMyLearning: () => ({ inProgress: 2, required: 1, overdue: 0, cpdEligible: 1 }),
  useFundoMyLearning: () => ({
    data: { data: { inProgress: [{ id: "e1" }, { id: "e2" }] } },
    isLoading: false,
  }),
  useFundoEnrolments: () => ({ data: { data: [{ id: "en1" }] }, isLoading: false }),
}));

vi.mock("@/hooks/queries/useFundoCatalog", () => ({
  useFundoCatalog: () => ({
    data: { data: { items: [{ id: "c1" }, { id: "c2" }, { id: "c3" }] } },
    isLoading: false,
  }),
}));

describe("FundoLearningOrchestrationRail", () => {
  it("shows catalogue and enrolment orchestration", () => {
    render(<FundoLearningOrchestrationRail />);
    expect(screen.getByTestId("fundo-learning-orchestration-rail")).toBeInTheDocument();
    expect(screen.getByTestId("fundo-kpi-strip")).toHaveTextContent("2 in progress");
    expect(screen.getByTestId("fundo-catalog-probe")).toHaveTextContent("3 published course(s)");
    expect(screen.queryByRole("link", { name: /Browse catalog/i })).not.toBeInTheDocument();
  });
});
