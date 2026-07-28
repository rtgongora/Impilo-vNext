// @vitest-environment jsdom
import { render, screen, cleanup, fireEvent } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { WorkHomeWorkplacePicker } from "./WorkHomeWorkplacePicker";
import type { ResolvedWorkContextView } from "@/lib/trust";

afterEach(() => cleanup());

function context(overrides: Partial<ResolvedWorkContextView>): ResolvedWorkContextView {
  return {
    contextId: "ctx-1",
    contextKind: "facility",
    sourceSystem: "VASHANDI",
    availableModes: ["CLINICAL_CARE"],
    defaultMode: "CLINICAL_CARE",
    restrictions: [],
    label: "Parirenyatwa — Oncology Ward B",
    groupHint: "today",
    ...overrides,
  };
}

describe("WorkHomeWorkplacePicker", () => {
  it("shows an honest empty state when there are no resolved work contexts at all", () => {
    render(<WorkHomeWorkplacePicker contexts={[]} onSelect={vi.fn()} />);
    expect(screen.getByText("No work assignment found")).toBeTruthy();
  });

  it("groups contexts by groupHint using the plan's canonical switcher group labels", () => {
    const contexts = [
      context({ contextId: "a", groupHint: "today", label: "Today's shift" }),
      context({ contextId: "b", groupHint: "regular", label: "Regular clinic" }),
      context({ contextId: "c", groupHint: "oversight", label: "District oversight" }),
    ];
    render(<WorkHomeWorkplacePicker contexts={contexts} onSelect={vi.fn()} />);

    expect(screen.getByText("Today")).toBeTruthy();
    expect(screen.getByText("My regular workplaces")).toBeTruthy();
    expect(screen.getByText("Oversight roles")).toBeTruthy();
    expect(screen.getByText("Today's shift")).toBeTruthy();
    expect(screen.getByText("Regular clinic")).toBeTruthy();
    expect(screen.getByText("District oversight")).toBeTruthy();
  });

  it("calls onSelect with the contextId and defaultMode when a workplace is chosen", () => {
    const onSelect = vi.fn();
    const contexts = [context({ contextId: "ctx-9", defaultMode: "FACILITY_MANAGEMENT", label: "My facility" })];
    render(<WorkHomeWorkplacePicker contexts={contexts} onSelect={onSelect} />);

    fireEvent.click(screen.getByText("My facility"));

    expect(onSelect).toHaveBeenCalledWith("ctx-9", "FACILITY_MANAGEMENT");
  });

  it("surfaces restrictions on a workplace card without hiding the workplace itself", () => {
    const contexts = [context({ label: "Restricted post", restrictions: ["LEAVE_ACTIVE"] })];
    render(<WorkHomeWorkplacePicker contexts={contexts} onSelect={vi.fn()} />);

    expect(screen.getByText("Restricted post")).toBeTruthy();
    expect(screen.getByText("LEAVE_ACTIVE")).toBeTruthy();
  });

  it("does not render an empty group heading when no context has that groupHint", () => {
    const contexts = [context({ groupHint: "personal", label: "My wellness space" })];
    render(<WorkHomeWorkplacePicker contexts={contexts} onSelect={vi.fn()} />);

    expect(screen.queryByText("Virtual work")).toBeNull();
    expect(screen.getByText("My wellness space")).toBeTruthy();
  });
});
