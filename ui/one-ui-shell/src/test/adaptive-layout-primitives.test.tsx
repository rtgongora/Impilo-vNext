import { fireEvent, render, screen } from "@testing-library/react";
import { createRef } from "react";
import { describe, expect, it, vi } from "vitest";
import {
  AdaptiveGrid,
  Card,
  FormField,
  FormGrid,
  FormSection,
  FullHeightWorkspace,
  MoreBelow,
  SplitView,
  StickyActionBar,
  Stepper,
  WorkspaceScrollPane,
} from "shared-ui";

/**
 * Adaptive-workspace primitives (screen real-estate remediation W0):
 * responsive form grids, sticky actions, steppers with a mobile variant,
 * overflow cues, split views, and full-height workspaces. Asserts the
 * responsive-collapse and accessibility invariants each primitive owns.
 */

describe("FormGrid + FormField", () => {
  it("is single-column on phones and multi-column on wider viewports", () => {
    const { container } = render(
      <FormGrid columns={3}>
        <FormField>one</FormField>
      </FormGrid>,
    );
    const grid = container.firstElementChild as HTMLElement;
    expect(grid.className).toContain("grid-cols-1");
    expect(grid.className).toContain("sm:grid-cols-2");
    expect(grid.className).toContain("lg:grid-cols-3");
  });

  it("lets narrative fields span the full width", () => {
    const { container } = render(
      <FormGrid>
        <FormField span="full">notes</FormField>
      </FormGrid>,
    );
    expect((container.querySelector(".col-span-full") as HTMLElement).textContent).toBe("notes");
  });

  it("renders label, required marker, and announces errors via role=alert", () => {
    render(
      <FormGrid>
        <FormField label="Date of birth" htmlFor="dob" required error="Required field">
          <input id="dob" />
        </FormField>
      </FormGrid>,
    );
    expect(screen.getByLabelText(/Date of birth/)).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("Required field");
  });

  it("FormSection groups fields with a subtle boundary, not a giant card", () => {
    render(
      <FormSection title="Contact details" description="How we reach the person">
        <div>fields</div>
      </FormSection>,
    );
    expect(screen.getByRole("heading", { name: "Contact details" })).toBeInTheDocument();
  });
});

describe("StickyActionBar", () => {
  it("is a labelled toolbar stuck above the shell taskbar and safe area", () => {
    render(
      <StickyActionBar status="Draft saved">
        <button type="button">Continue</button>
      </StickyActionBar>,
    );
    const bar = screen.getByTestId("sticky-action-bar");
    expect(bar.className).toContain("sticky");
    expect(bar.style.bottom).toContain("--shell-taskbar-height");
    expect(bar.style.bottom).toContain("safe-area-inset-bottom");
    expect(screen.getByRole("toolbar", { name: "Workflow actions" })).toBeInTheDocument();
    expect(screen.getByText("Draft saved")).toBeInTheDocument();
  });
});

describe("Stepper", () => {
  const steps = [
    { id: "person", label: "Person" },
    { id: "contact", label: "Contact" },
    { id: "review", label: "Review" },
  ];

  it("marks the current step for assistive tech and shows the mobile counter", () => {
    render(<Stepper steps={steps} activeIndex={1} />);
    const current = screen.getAllByText("Contact")[0].closest("li");
    expect(current).toHaveAttribute("aria-current", "step");
    expect(screen.getByText("Step 2 of 3")).toBeInTheDocument();
    expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "67");
  });

  it("only completed steps are clickable, and only when a handler is provided", () => {
    const onSelect = vi.fn();
    render(<Stepper steps={steps} activeIndex={1} onStepSelect={onSelect} />);
    fireEvent.click(screen.getByRole("button", { name: /Go back to step 1/ }));
    expect(onSelect).toHaveBeenCalledWith("person", 0);
    // Upcoming step renders no button.
    expect(screen.queryByRole("button", { name: /step 3/i })).not.toBeInTheDocument();
  });

  it("renders no buttons at all for a purely indicative stepper", () => {
    render(<Stepper steps={steps} activeIndex={2} />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});

describe("MoreBelow", () => {
  function mockScrollMetrics(el: HTMLElement, { scrollHeight, clientHeight, scrollTop }: Record<string, number>) {
    Object.defineProperty(el, "scrollHeight", { configurable: true, value: scrollHeight });
    Object.defineProperty(el, "clientHeight", { configurable: true, value: clientHeight });
    el.scrollTop = scrollTop;
  }

  it("shows while content remains below and hides at the end", () => {
    const ref = createRef<HTMLDivElement>();
    render(
      <div ref={ref} style={{ position: "relative" }}>
        <MoreBelow targetRef={ref as React.RefObject<HTMLElement>} variant="absolute" />
      </div>,
    );
    const el = ref.current as HTMLDivElement;
    mockScrollMetrics(el, { scrollHeight: 2000, clientHeight: 600, scrollTop: 0 });
    fireEvent.scroll(el);
    expect(screen.getByTestId("more-below-cue")).toBeInTheDocument();

    mockScrollMetrics(el, { scrollHeight: 2000, clientHeight: 600, scrollTop: 1400 });
    fireEvent.scroll(el);
    expect(screen.queryByTestId("more-below-cue")).not.toBeInTheDocument();
  });

  it("is an accessible button that advances the scroll (no hover dependency)", () => {
    const ref = createRef<HTMLDivElement>();
    render(
      <div ref={ref} style={{ position: "relative" }}>
        <MoreBelow targetRef={ref as React.RefObject<HTMLElement>} variant="absolute" />
      </div>,
    );
    const el = ref.current as HTMLDivElement;
    mockScrollMetrics(el, { scrollHeight: 2000, clientHeight: 600, scrollTop: 0 });
    fireEvent.scroll(el);
    const cue = screen.getByRole("button", { name: /scroll down/i });
    el.scrollTo = undefined as unknown as typeof el.scrollTo;
    fireEvent.click(cue);
    expect(el.scrollTop).toBe(480);
  });
});

describe("SplitView", () => {
  it("stacks on narrow viewports and splits at the requested breakpoint", () => {
    const { container } = render(
      <SplitView side={<div>panel</div>} splitAt="lg" sideWidth="md">
        <div>work</div>
      </SplitView>,
    );
    const grid = container.firstElementChild as HTMLElement;
    expect(grid.className).toContain("grid-cols-1");
    expect(grid.className).toContain("lg:grid-cols-[minmax(0,1fr)_320px]");
  });

  it("keeps the primary pane first in reading order when the panel sits left", () => {
    const { container } = render(
      <SplitView side={<div>panel</div>} sidePosition="left" splitAt="md" sideWidth="sm">
        <div>work</div>
      </SplitView>,
    );
    const grid = container.firstElementChild as HTMLElement;
    expect(grid.className).toContain("md:grid-cols-[280px_minmax(0,1fr)]");
    const [main] = Array.from(grid.children) as HTMLElement[];
    expect(main.textContent).toBe("work");
    expect(main.className).toContain("md:order-2");
  });
});

describe("FullHeightWorkspace", () => {
  it("fills the available height with a single internal scroll pane", () => {
    render(
      <FullHeightWorkspace data-testid="workspace">
        <WorkspaceScrollPane aria-label="Results">rows</WorkspaceScrollPane>
      </FullHeightWorkspace>,
    );
    const ws = screen.getByTestId("workspace");
    expect(ws.className).toContain("h-full");
    expect(ws.className).toContain("min-h-0");
    const pane = screen.getByRole("region", { name: "Results" });
    expect(pane.className).toContain("overflow-auto");
    expect(pane).toHaveAttribute("tabindex", "0");
  });
});

describe("AdaptiveGrid + Card density", () => {
  it("uses auto-fit columns so tiles size to content, not a fixed design grid", () => {
    const { container } = render(
      <AdaptiveGrid min="sm" gap="compact">
        <div>tile</div>
      </AdaptiveGrid>,
    );
    const grid = container.firstElementChild as HTMLElement;
    expect(grid.className).toContain("auto-fit,minmax(13rem,1fr)");
    expect(grid.className).toContain("gap-2");
  });

  it("Card density=compact tightens padding and radius; default stays unchanged", () => {
    const { container: compact } = render(<Card density="compact">c</Card>);
    const compactEl = compact.firstElementChild as HTMLElement;
    expect(compactEl.className).toContain("rounded-xl");
    expect(compactEl.className).toContain("p-3");

    const { container: def } = render(<Card>d</Card>);
    const defEl = def.firstElementChild as HTMLElement;
    expect(defEl.className).toContain("rounded-2xl");
    expect(defEl.className).toContain("p-4");
  });
});
