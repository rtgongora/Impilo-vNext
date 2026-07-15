import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get } }));

import { PatientSearchField, type PatientHit } from "./PatientSearchField";

describe("PatientSearchField", () => {
  beforeEach(() => get.mockReset());

  it("searches the client registry by name and emits the selected Health ID", async () => {
    const user = (await import("@testing-library/user-event")).default.setup();
    get.mockResolvedValue({ data: { items: [{ healthId: "H-9", displayName: "John Moyo", crid: "CRID-9", impiloId: "IMP-9" }] } });
    const onSelect = vi.fn();
    render(<PatientSearchField onSelect={onSelect} />);

    await user.type(screen.getByTestId("patient-search-input"), "John");
    await waitFor(() => expect(screen.getByText("John Moyo")).toBeInTheDocument());
    expect(get).toHaveBeenCalledWith(expect.stringContaining("/internal/v1/client-registry/clients?query=John"));

    await user.click(screen.getByText("John Moyo"));
    expect(onSelect).toHaveBeenCalledWith(expect.objectContaining<Partial<PatientHit>>({ healthId: "H-9" }));
  });

  it("does not search for queries under 2 chars", async () => {
    const user = (await import("@testing-library/user-event")).default.setup();
    render(<PatientSearchField onSelect={vi.fn()} />);
    await user.type(screen.getByTestId("patient-search-input"), "J");
    expect(get).not.toHaveBeenCalled();
  });
});
