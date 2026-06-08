import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { BreakGlassRequestPanel } from "./BreakGlassRequestPanel";

const { mutateAsync } = vi.hoisted(() => ({
  mutateAsync: vi.fn(),
}));

vi.mock("@/hooks/queries/useTrustBreakGlass", () => ({
  useRequestBreakGlass: () => ({
    mutateAsync,
    isPending: false,
  }),
}));

describe("BreakGlassRequestPanel", () => {
  beforeEach(() => {
    mutateAsync.mockReset();
    mutateAsync.mockResolvedValue({ data: { id: "bg-1" } });
  });

  it("requires clinical justification", async () => {
    render(<BreakGlassRequestPanel patientId="cpid-1" />);
    const form = screen.getByLabelText(/clinical justification/i).closest("form");
    expect(form).not.toBeNull();
    fireEvent.submit(form!);
    expect(await screen.findByText(/mandatory reason/i)).toBeInTheDocument();
    expect(mutateAsync).not.toHaveBeenCalled();
  });

  it("submits break-glass request with patient correlation", async () => {
    const user = userEvent.setup();
    render(<BreakGlassRequestPanel patientId="cpid-1" resourceType="PATIENT_RECORD" />);

    await user.type(
      screen.getByLabelText(/clinical justification/i),
      "Unresponsive patient in resuscitation bay",
    );
    fireEvent.click(screen.getByRole("button", { name: /submit break-glass request/i }));

    await waitFor(() =>
      expect(mutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          reason: "Unresponsive patient in resuscitation bay",
          resourceType: "PATIENT_RECORD",
          resourceId: "cpid-1",
          patientId: "cpid-1",
        }),
      ),
    );
    expect(screen.getByRole("link", { name: /open break-glass review log/i })).toHaveAttribute(
      "href",
      "/admin/break-glass?requestId=bg-1",
    );
  });
});
