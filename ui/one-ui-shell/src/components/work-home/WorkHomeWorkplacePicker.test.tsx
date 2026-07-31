// @vitest-environment jsdom
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { WorkHomeWorkplacePicker } from "./WorkHomeWorkplacePicker";
import type { ResolvedWorkContextView } from "@/lib/trust";

vi.mock("@/hooks/queries/useSwitchWorkContext", () => ({
  useSwitchWorkContext: () => vi.fn(),
}));

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
    render(<WorkHomeWorkplacePicker contexts={[]} />);
    expect(screen.getByText("No work assignment found")).toBeTruthy();
  });

  it("groups contexts by groupHint using the plan's canonical switcher group labels", () => {
    const contexts = [
      context({ contextId: "a", groupHint: "today", label: "Today's shift" }),
      context({ contextId: "b", groupHint: "regular", label: "Regular clinic" }),
      context({ contextId: "c", groupHint: "oversight", label: "District oversight" }),
    ];
    render(<WorkHomeWorkplacePicker contexts={contexts} />);

    expect(screen.getByText("Today")).toBeTruthy();
    expect(screen.getByText("My regular workplaces")).toBeTruthy();
    expect(screen.getByText("Oversight roles")).toBeTruthy();
  });

  it("mints with the context and defaultMode when a single-mode workplace is chosen", async () => {
    const onSelect = vi.fn().mockResolvedValue(undefined);
    const ctx = context({
      contextId: "ctx-9",
      defaultMode: "FACILITY_MANAGEMENT",
      availableModes: ["FACILITY_MANAGEMENT"],
      label: "My facility",
    });
    render(<WorkHomeWorkplacePicker contexts={[ctx]} onSelect={onSelect} />);

    fireEvent.click(screen.getByText("My facility"));

    await waitFor(() => {
      expect(onSelect).toHaveBeenCalledWith(ctx, "FACILITY_MANAGEMENT");
    });
  });

  it("surfaces programme restrictions in plain language without hiding the workplace", () => {
    const contexts = [
      context({ label: "Restricted post", restrictions: ["PROGRAMME_SUSPENDED"] }),
    ];
    render(<WorkHomeWorkplacePicker contexts={contexts} />);

    expect(screen.getByText("Restricted post")).toBeTruthy();
    expect(screen.getByText(/programme is suspended/i)).toBeTruthy();
    expect(screen.queryByText("PROGRAMME_SUSPENDED")).toBeNull();
  });

  it("offers a mode chooser when a workplace grants more than one mode", async () => {
    const onSelect = vi.fn().mockResolvedValue(undefined);
    const ctx = context({
      label: "Multi-mode ward",
      availableModes: ["CLINICAL_CARE", "DEPARTMENT_MANAGEMENT"],
      defaultMode: "CLINICAL_CARE",
    });
    render(<WorkHomeWorkplacePicker contexts={[ctx]} onSelect={onSelect} />);

    fireEvent.click(screen.getByText("Multi-mode ward"));
    expect(screen.getByText("Clinical Care")).toBeTruthy();
    expect(screen.getByText("Department Management")).toBeTruthy();

    fireEvent.click(screen.getByText("Department Management"));
    await waitFor(() => {
      expect(onSelect).toHaveBeenCalledWith(ctx, "DEPARTMENT_MANAGEMENT");
    });
  });
});
