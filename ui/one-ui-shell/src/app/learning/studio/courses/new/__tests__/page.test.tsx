import type { ReactNode } from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import FundoStudioNewCoursePage from "../page";

/**
 * Silent-failure regression coverage: a rejected or id-less create must
 * surface a visible error instead of leaving the author on a dead button.
 */

const { createState, routerState } = vi.hoisted(() => ({
  createState: {
    mutateAsync: vi.fn<(body: unknown) => Promise<unknown>>(),
    isPending: false,
  },
  routerState: { push: vi.fn() },
}));

vi.mock("next/navigation", () => ({
  useRouter: () => routerState,
  usePathname: () => "/learning/studio/courses/new",
}));

vi.mock("@/components/learning/FundoStudioWorkspace", () => ({
  FundoStudioWorkspace: ({ title, children }: { title: string; children: ReactNode }) => (
    <section>
      <h1>{title}</h1>
      {children}
    </section>
  ),
}));

vi.mock("@/hooks/queries/useFundoLms", () => ({
  useFundoLanguageOptions: () => ({ data: undefined }),
}));

vi.mock("@/hooks/queries/useFundoStudio", () => ({
  useCreateFundoCourse: () => createState,
}));

async function fillAndCreate() {
  fireEvent.change(screen.getByLabelText(/course title/i), { target: { value: "IPC Basics" } });
  fireEvent.click(screen.getByRole("button", { name: /create draft course/i }));
}

describe("Fundo Studio new-course page", () => {
  afterEach(() => {
    cleanup();
    createState.mutateAsync.mockReset();
    routerState.push.mockReset();
  });

  it("navigates to the builder when the create succeeds", async () => {
    createState.mutateAsync.mockResolvedValue({ data: { course: { id: "course-9" } } });
    render(<FundoStudioNewCoursePage />);
    await fillAndCreate();
    await waitFor(() => expect(routerState.push).toHaveBeenCalledWith("/learning/studio/courses/course-9/builder"));
    expect(screen.queryByTestId("fundo-create-course-error")).not.toBeInTheDocument();
  });

  it("shows the backend error message when the create is rejected", async () => {
    createState.mutateAsync.mockRejectedValue({ status: 400, error: { message: "Course code already exists" } });
    render(<FundoStudioNewCoursePage />);
    await fillAndCreate();
    expect(await screen.findByTestId("fundo-create-course-error")).toHaveTextContent("Course code already exists");
    expect(routerState.push).not.toHaveBeenCalled();
  });

  it("explains a 403 as missing authoring permission", async () => {
    createState.mutateAsync.mockRejectedValue({ status: 403 });
    render(<FundoStudioNewCoursePage />);
    await fillAndCreate();
    expect(await screen.findByTestId("fundo-create-course-error")).toHaveTextContent(/authoring permission/i);
  });

  it("treats a success response without a course id as an error, not a silent no-op", async () => {
    createState.mutateAsync.mockResolvedValue({ data: {} });
    render(<FundoStudioNewCoursePage />);
    await fillAndCreate();
    expect(await screen.findByTestId("fundo-create-course-error")).toHaveTextContent(/no course id/i);
    expect(routerState.push).not.toHaveBeenCalled();
  });
});
