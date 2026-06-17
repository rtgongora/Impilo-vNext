import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { FundoStudioWorkspace } from "./FundoStudioWorkspace";

const mockUsePathname = vi.fn();

vi.mock("next/navigation", () => ({
  usePathname: () => mockUsePathname(),
}));

describe("FundoStudioWorkspace", () => {
  beforeEach(() => {
    mockUsePathname.mockReturnValue("/learning/studio/courses");
  });

  it("renders compact studio quick access without nesting the app shell", () => {
    const { container } = render(
      <FundoStudioWorkspace title="Studio Courses" subtitle="Create course outlines.">
        <div>Course content</div>
      </FundoStudioWorkspace>,
    );

    expect(screen.getByRole("heading", { name: "Studio Courses" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Courses/i })).toHaveAttribute("href", "/learning/studio/courses");
    expect(screen.getByRole("link", { name: /Assessments/i })).toHaveAttribute("href", "/learning/studio/assessments");
    expect(screen.getByRole("link", { name: /Analytics/i })).toHaveAttribute("href", "/learning/studio/analytics");
    expect(screen.getByText("Course content")).toBeInTheDocument();
    expect(container.querySelector("[data-sidebar]")).not.toBeInTheDocument();
  });
});
