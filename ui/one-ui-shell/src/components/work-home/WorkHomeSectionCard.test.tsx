// @vitest-environment jsdom
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { WorkHomeSectionCard } from "./WorkHomeSectionCard";
import type { WorkHomeSection } from "@/hooks/queries/useWorkHome";

afterEach(() => cleanup());

function section(overrides: Partial<WorkHomeSection>): WorkHomeSection {
  return {
    sectionId: "clinical-worklist",
    title: "Clinical worklist",
    status: "OK",
    items: [],
    buckets: {},
    summary: {},
    generatedAt: "2026-07-28T05:00:00Z",
    ...overrides,
  };
}

describe("WorkHomeSectionCard", () => {
  it("renders items grouped under their bucket labels with counts", () => {
    const s = section({
      items: [{ id: "1", title: "See patient", priority: "URGENT" }],
      buckets: { ACT_NOW: [{ id: "1", title: "See patient", priority: "URGENT" }] },
    });
    render(<WorkHomeSectionCard section={s} />);

    expect(screen.getByText("Clinical worklist")).toBeTruthy();
    expect(screen.getByText("Act now (1)")).toBeTruthy();
    expect(screen.getByText("See patient")).toBeTruthy();
  });

  it("shows the honest empty-state note when status is EMPTY, no items rendered", () => {
    const s = section({ status: "EMPTY", note: "No items to show" });
    render(<WorkHomeSectionCard section={s} />);

    expect(screen.getByText("No items to show")).toBeTruthy();
  });

  it("shows a degraded banner with the BFF's note, and a retry button", () => {
    const s = section({ status: "DEGRADED", note: "section timed out" });
    render(<WorkHomeSectionCard section={s} onRetry={vi.fn()} />);

    expect(screen.getByText("section timed out")).toBeTruthy();
    expect(screen.getByText("Retry")).toBeTruthy();
  });

  it("calls onRetry with the sectionId when the retry button is clicked", async () => {
    const onRetry = vi.fn().mockResolvedValue(undefined);
    const s = section({ status: "DEGRADED", note: "section timed out" });
    render(<WorkHomeSectionCard section={s} onRetry={onRetry} />);

    fireEvent.click(screen.getByText("Retry"));

    await waitFor(() => expect(onRetry).toHaveBeenCalledWith("clinical-worklist"));
  });

  it("does not render a retry button when onRetry is not provided, even if degraded", () => {
    const s = section({ status: "DEGRADED", note: "section timed out" });
    render(<WorkHomeSectionCard section={s} />);

    expect(screen.queryByText("Retry")).toBeNull();
  });

  it("caps the rendered item list at 6 per bucket and shows an overflow count", () => {
    const items = Array.from({ length: 9 }, (_, i) => ({ id: String(i), title: `Item ${i}`, priority: "MEDIUM" }));
    const s = section({ items, buckets: { FOR_AWARENESS: items } });
    render(<WorkHomeSectionCard section={s} />);

    expect(screen.getByText("+3 more")).toBeTruthy();
  });

  it("stamps the section's own sectionId as a data attribute for e2e family-set assertions (F2)", () => {
    const s = section({ sectionId: "facility-status" });
    const { container } = render(<WorkHomeSectionCard section={s} />);

    expect(container.querySelector('[data-section-id="facility-status"]')).toBeTruthy();
  });
});
