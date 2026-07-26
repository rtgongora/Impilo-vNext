import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { SegmentedControl } from "shared-ui";

const YES_NO = [
  { value: "yes", label: "Yes", tone: "danger" as const },
  { value: "no", label: "No", tone: "positive" as const },
];

describe("SegmentedControl", () => {
  it("presents every option at once rather than hiding them behind a dropdown", () => {
    render(<SegmentedControl options={YES_NO} value="no" onChange={() => {}} aria-label="Convulsions" />);

    expect(screen.getByRole("radio", { name: "Yes" })).toBeTruthy();
    expect(screen.getByRole("radio", { name: "No" })).toBeTruthy();
    expect(screen.getByRole("radiogroup", { name: "Convulsions" })).toBeTruthy();
  });

  it("marks the selected option for assistive technology", () => {
    render(<SegmentedControl options={YES_NO} value="no" onChange={() => {}} aria-label="Convulsions" />);

    expect(screen.getByRole("radio", { name: "No" }).getAttribute("aria-checked")).toBe("true");
    expect(screen.getByRole("radio", { name: "Yes" }).getAttribute("aria-checked")).toBe("false");
  });

  it("reports the chosen value", () => {
    const onChange = vi.fn();
    render(<SegmentedControl options={YES_NO} value={null} onChange={onChange} aria-label="Convulsions" />);

    fireEvent.click(screen.getByRole("radio", { name: "Yes" }));
    expect(onChange).toHaveBeenCalledWith("yes");
  });

  it("does not clear a selection on re-click unless deselection is allowed", () => {
    const onChange = vi.fn();
    const { rerender } = render(
      <SegmentedControl options={YES_NO} value="yes" onChange={onChange} aria-label="Convulsions" />,
    );
    fireEvent.click(screen.getByRole("radio", { name: "Yes" }));
    expect(onChange).toHaveBeenCalledWith("yes");

    onChange.mockClear();
    rerender(
      <SegmentedControl options={YES_NO} value="yes" onChange={onChange} allowDeselect aria-label="Convulsions" />,
    );
    fireEvent.click(screen.getByRole("radio", { name: "Yes" }));
    expect(onChange).toHaveBeenCalledWith("");
  });

  it("moves between options with the arrow keys", () => {
    render(<SegmentedControl options={YES_NO} value="yes" onChange={() => {}} aria-label="Convulsions" />);

    const yes = screen.getByRole("radio", { name: "Yes" });
    yes.focus();
    fireEvent.keyDown(yes, { key: "ArrowRight" });
    expect(document.activeElement).toBe(screen.getByRole("radio", { name: "No" }));
  });

  it("keeps only one option in the tab order", () => {
    render(<SegmentedControl options={YES_NO} value="no" onChange={() => {}} aria-label="Convulsions" />);

    expect(screen.getByRole("radio", { name: "No" }).getAttribute("tabindex")).toBe("0");
    expect(screen.getByRole("radio", { name: "Yes" }).getAttribute("tabindex")).toBe("-1");
  });

  it("gives every option a touch-sized target", () => {
    render(<SegmentedControl options={YES_NO} value={null} onChange={() => {}} aria-label="Convulsions" />);

    screen.getAllByRole("radio").forEach((option) => {
      expect(option.className).toContain("min-h-[44px]");
    });
  });

  it("falls back to a select when the options would not fit as segments", () => {
    // A control that overflows its row on a phone is worse than a dropdown.
    const many = Array.from({ length: 8 }, (_, i) => ({ value: `o${i}`, label: `Option ${i}` }));
    render(<SegmentedControl options={many} value={null} onChange={() => {}} aria-label="Many" />);

    expect(screen.getByRole("combobox", { name: "Many" })).toBeTruthy();
    expect(screen.queryAllByRole("radio")).toHaveLength(0);
  });

  it("also falls back when a few options carry long labels", () => {
    const wordy = [
      { value: "a", label: "Child too distressed to complete the examination" },
      { value: "b", label: "Examination deferred to the next contact" },
    ];
    render(<SegmentedControl options={wordy} value={null} onChange={() => {}} aria-label="Finding" />);

    expect(screen.getByRole("combobox", { name: "Finding" })).toBeTruthy();
  });

  it("ignores clicks on a disabled option", () => {
    const onChange = vi.fn();
    render(
      <SegmentedControl
        options={[YES_NO[0], { ...YES_NO[1], disabled: true }]}
        value={null}
        onChange={onChange}
        aria-label="Convulsions"
      />,
    );

    fireEvent.click(screen.getByRole("radio", { name: "No" }));
    expect(onChange).not.toHaveBeenCalled();
  });
});
