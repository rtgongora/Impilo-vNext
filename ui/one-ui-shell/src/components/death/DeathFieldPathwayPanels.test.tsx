import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { DeathFieldPathwayPanels } from "./DeathFieldPathwayPanels";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

describe("DeathFieldPathwayPanels", () => {
  beforeEach(() => {
    get.mockReset().mockResolvedValue({ data: [] });
    post.mockReset().mockResolvedValue({ data: {} });
  });

  it("records a verbal autopsy and a field disposition to the death BFF", async () => {
    render(<DeathFieldPathwayPanels caseId="case-1" />);

    // Both lists load on mount.
    await waitFor(() =>
      expect(get.mock.calls.some((c) => String(c[0]).includes("/verbal-autopsy"))).toBe(true),
    );
    expect(get.mock.calls.some((c) => String(c[0]).includes("/field-body-management"))).toBe(true);

    // Verbal autopsy: open the form, fill the underlying cause, save.
    fireEvent.click(screen.getAllByRole("button", { name: /Record/ })[0]);
    fireEvent.change(screen.getByPlaceholderText(/Probable underlying cause/), { target: { value: "Malaria" } });
    fireEvent.click(screen.getByRole("button", { name: /^Save$/ }));
    await waitFor(() =>
      expect(post.mock.calls.some((c) => String(c[0]) === "/internal/v1/death/cases/case-1/verbal-autopsy")).toBe(true),
    );
    const vaCall = post.mock.calls.find((c) => String(c[0]).includes("/verbal-autopsy"));
    expect((vaCall?.[1] as { probableUnderlyingCause?: string })?.probableUnderlyingCause).toBe("Malaria");

    // Field body: open the second form, save the default disposition.
    fireEvent.click(screen.getAllByRole("button", { name: /Record/ })[1]);
    fireEvent.click(screen.getAllByRole("button", { name: /^Save$/ })[0]);
    await waitFor(() =>
      expect(post.mock.calls.some((c) => String(c[0]) === "/internal/v1/death/cases/case-1/field-body-management")).toBe(true),
    );
    const fbCall = post.mock.calls.find((c) => String(c[0]).includes("/field-body-management"));
    expect((fbCall?.[1] as { dispositionType?: string })?.dispositionType).toBe("SAFE_AND_DIGNIFIED_BURIAL");
  });
});
