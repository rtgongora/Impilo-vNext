import type { ReactNode } from "react";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import FundoStudioPublishPage from "../page";

const { catalogState, updateMutation, scheduleMutation, readinessState } = vi.hoisted(() => ({
  catalogState: {
    data: {
      data: {
        items: [{ id: "c-1", title: "Course A", status: "DRAFT", category: "GENERAL", level: "FOUNDATION" }],
      },
    },
    refetch: vi.fn(),
  },
  updateMutation: { mutateAsync: vi.fn(async () => ({ data: { course: { id: "c-1", status: "PUBLISHED" } } })) },
  scheduleMutation: { mutateAsync: vi.fn(async () => ({ data: { notification: { id: "n-1" } } })) },
  readinessState: { data: { data: { readinessChecklist: [{ item: "Course metadata", ok: true }] } } },
}));

vi.mock("@/components/learning/FundoStudioWorkspace", () => ({
  FundoStudioWorkspace: ({ title, children }: { title: string; children: ReactNode }) => (
    <section>
      <h1>{title}</h1>
      {children}
    </section>
  ),
}));

vi.mock("@/hooks/queries/useFundoCatalog", () => ({
  useFundoCatalog: () => catalogState,
}));

vi.mock("@/hooks/queries/useFundoStudio", () => ({
  useFundoStudioReadiness: () => readinessState,
  useUpdateFundoCourseById: () => updateMutation,
  useScheduleLearningNotification: () => scheduleMutation,
}));

vi.mock("@/components/learning/LearningSubjectPicker", () => ({
  useLearningSubject: () => ({ subjectType: "PROVIDER", subjectId: "provider-1" }),
}));

describe("FundoStudioPublishPage", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("publishes selected course and schedules notification", async () => {
    render(<FundoStudioPublishPage />);
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "c-1" } });
    fireEvent.click(screen.getAllByRole("button", { name: "Publish" })[0]);
    expect(updateMutation.mutateAsync).toHaveBeenCalledWith(expect.objectContaining({ courseId: "c-1", status: "PUBLISHED" }));
  });

  it("publishes straight from a course row without pre-selecting", async () => {
    render(<FundoStudioPublishPage />);
    const rowPublish = screen.getAllByRole("button", { name: "Publish" }).at(-1)!;
    fireEvent.click(rowPublish);
    expect(updateMutation.mutateAsync).toHaveBeenCalledWith(expect.objectContaining({ status: "PUBLISHED" }));
  });
});
