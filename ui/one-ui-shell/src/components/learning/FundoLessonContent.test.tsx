import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { FundoLessonContent } from "./FundoLessonContent";

afterEach(() => cleanup());

describe("FundoLessonContent", () => {
  it("renders text lessons", () => {
    render(<FundoLessonContent lesson={{ contentType: "TEXT", contentBody: "Hello learner" }} />);
    expect(screen.getByTestId("fundo-lesson-content")).toHaveAttribute("data-content-type", "TEXT");
    expect(screen.getByText("Hello learner")).toBeInTheDocument();
  });

  it("embeds youtube video lessons", () => {
    render(
      <FundoLessonContent
        lesson={{
          title: "Orientation video",
          contentType: "VIDEO",
          contentRef: "https://www.youtube.com/watch?v=demo123",
        }}
      />,
    );
    expect(screen.getByTestId("fundo-lesson-video-embed")).toBeInTheDocument();
    expect(screen.getByTitle("Orientation video")).toHaveAttribute("src", "https://www.youtube.com/embed/demo123");
  });

  it("renders practical task checklist", async () => {
    const user = userEvent.setup();
    render(
      <FundoLessonContent
        lesson={{
          contentType: "PRACTICAL_TASK",
          contentBody: '["Verify patient identity","Capture consent"]',
        }}
      />,
    );
    expect(screen.getByTestId("fundo-lesson-practical")).toBeInTheDocument();
    const boxes = screen.getAllByRole("checkbox");
    await user.click(boxes[0]);
    expect(screen.getByText(/1 of 2 checklist items marked/)).toBeInTheDocument();
  });
});
