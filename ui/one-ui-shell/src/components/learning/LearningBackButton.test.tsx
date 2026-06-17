import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { LearningBackButton } from "./LearningBackButton";

const mockBack = vi.fn();
const mockPush = vi.fn();
const mockUsePathname = vi.fn();

vi.mock("next/navigation", () => ({
  usePathname: () => mockUsePathname(),
  useRouter: () => ({
    back: mockBack,
    push: mockPush,
  }),
}));

describe("LearningBackButton", () => {
  beforeEach(() => {
    mockBack.mockClear();
    mockPush.mockClear();
    mockUsePathname.mockReturnValue("/learning/catalog");
  });

  it("does not render on the learning root", () => {
    mockUsePathname.mockReturnValue("/learning");

    render(<LearningBackButton />);

    expect(screen.queryByRole("button", { name: "Back" })).not.toBeInTheDocument();
  });

  it("renders on nested learning routes", () => {
    render(<LearningBackButton />);

    expect(screen.getByRole("button", { name: "Back" })).toBeInTheDocument();
  });

  it("goes to the previous learning section instead of browser history", async () => {
    mockUsePathname.mockReturnValue("/learning/enrolments/enrol-1/lessons/lesson-2");

    render(<LearningBackButton />);
    await userEvent.click(screen.getByRole("button", { name: "Back" }));

    expect(mockPush).toHaveBeenCalledWith("/learning/enrolments/enrol-1");
    expect(mockBack).not.toHaveBeenCalled();
  });
});
