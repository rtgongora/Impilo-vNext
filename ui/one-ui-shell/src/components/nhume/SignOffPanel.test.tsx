import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { SignOffPanel } from "./SignOffPanel";

const proofMutate = vi.fn().mockResolvedValue({});
vi.mock("@/hooks/useNhume", () => ({
  useRecordProof: () => ({ mutateAsync: proofMutate, isPending: false }),
}));

beforeEach(() => proofMutate.mockClear());

describe("SignOffPanel — collection & drop-off sign-off", () => {
  it("collection (PICKUP) records proof without marking delivered", async () => {
    render(<SignOffPanel deliveryId="d-1" stage="PICKUP" />);
    fireEvent.change(screen.getByPlaceholderText(/name \/ role/i), { target: { value: "Sr Dube (origin)" } });
    fireEvent.click(screen.getByRole("button", { name: /confirm collection/i }));

    await waitFor(() => expect(proofMutate).toHaveBeenCalledTimes(1));
    const p = proofMutate.mock.calls[0][0];
    expect(p.proof_stage).toBe("PICKUP");
    expect(p.mark_delivered).toBe(false);
    expect(p.captured_by).toBe("Sr Dube (origin)");
    expect(p.method).toBe("RECIPIENT_SIGNATURE");
  });

  it("drop-off (DELIVERY) records proof and marks delivered", async () => {
    render(<SignOffPanel deliveryId="d-1" stage="DELIVERY" />);
    fireEvent.change(screen.getByPlaceholderText(/name \/ role/i), { target: { value: "Dr Moyo (receiving)" } });
    fireEvent.click(screen.getByRole("button", { name: /confirm drop-off & mark delivered/i }));

    await waitFor(() => expect(proofMutate).toHaveBeenCalledTimes(1));
    const p = proofMutate.mock.calls[0][0];
    expect(p.proof_stage).toBe("DELIVERY");
    expect(p.mark_delivered).toBe(true);
    expect(p.captured_by).toBe("Dr Moyo (receiving)");
  });

  it("blocks sign-off without recording who — no API call", async () => {
    render(<SignOffPanel deliveryId="d-1" stage="PICKUP" />);
    fireEvent.click(screen.getByRole("button", { name: /confirm collection/i }));
    expect(await screen.findByText(/who released the cargo/i)).toBeInTheDocument();
    expect(proofMutate).not.toHaveBeenCalled();
  });
});
