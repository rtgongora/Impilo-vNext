import { useState } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { OtpCodeInput } from "./OtpCodeInput";

/** Controlled harness mirroring how the pages drive OtpCodeInput. */
function Harness({
  onComplete,
  disabled,
}: {
  onComplete?: (code: string) => void;
  disabled?: boolean;
}) {
  const [value, setValue] = useState("");
  return (
    <OtpCodeInput
      value={value}
      onChange={setValue}
      onComplete={onComplete}
      disabled={disabled}
      autoFocus
    />
  );
}

describe("OtpCodeInput", () => {
  it("renders six numeric boxes and auto-advances on typing", async () => {
    const user = userEvent.setup();
    render(<Harness />);

    const boxes = screen.getAllByRole("textbox");
    expect(boxes).toHaveLength(6);

    await user.type(boxes[0], "1");
    // Auto-advance moved focus to the second box.
    expect(boxes[1]).toHaveFocus();
    await user.type(boxes[1], "2");
    expect(boxes[0]).toHaveValue("1");
    expect(boxes[1]).toHaveValue("2");
  });

  it("ignores non-numeric input", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const boxes = screen.getAllByRole("textbox");
    await user.type(boxes[0], "a");
    expect(boxes[0]).toHaveValue("");
  });

  it("fires onComplete once all six digits are entered", async () => {
    const onComplete = vi.fn();
    const user = userEvent.setup();
    render(<Harness onComplete={onComplete} />);

    const boxes = screen.getAllByRole("textbox");
    for (let i = 0; i < 6; i++) {
      await user.type(boxes[i], String(i + 1));
    }
    expect(onComplete).toHaveBeenCalledWith("123456");
  });

  it("splits a pasted code across boxes and completes", async () => {
    const onComplete = vi.fn();
    const user = userEvent.setup();
    render(<Harness onComplete={onComplete} />);

    const boxes = screen.getAllByRole("textbox");
    boxes[0].focus();
    await user.paste("654321");

    expect(boxes[0]).toHaveValue("6");
    expect(boxes[5]).toHaveValue("1");
    expect(onComplete).toHaveBeenCalledWith("654321");
  });

  it("moves focus to the previous box on backspace when empty", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const boxes = screen.getAllByRole("textbox");

    await user.type(boxes[0], "1");
    expect(boxes[1]).toHaveFocus();
    // Second box is empty — backspace should hop back to the first box.
    await user.keyboard("{Backspace}");
    expect(boxes[0]).toHaveFocus();
  });

  it("disables every box when disabled", () => {
    render(<Harness disabled />);
    for (const box of screen.getAllByRole("textbox")) {
      expect(box).toBeDisabled();
    }
  });
});
