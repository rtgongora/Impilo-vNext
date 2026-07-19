import { render, screen, fireEvent } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AppointmentCheckinQr } from "./AppointmentCheckinQr";

const useCheckinToken = vi.fn();
vi.mock("@/hooks/queries/useCheckinToken", () => ({
  useCheckinToken: (id: string, enabled: boolean) => useCheckinToken(id, enabled),
}));

// Render qrcode.react as a stub that echoes the encoded value so we can assert on it.
vi.mock("qrcode.react", () => ({
  QRCodeSVG: ({ value }: { value: string }) => <div data-testid="qr" data-value={value} />,
}));

function idle() {
  return { data: undefined, isLoading: false, isError: false, isFetching: false, refetch: vi.fn() };
}
function loaded(token: string, expiresAt?: string) {
  return {
    data: { data: { appointmentId: "a1", token, expiresAt } },
    isLoading: false,
    isError: false,
    isFetching: false,
    refetch: vi.fn(),
  };
}

describe("AppointmentCheckinQr (CJ11)", () => {
  beforeEach(() => useCheckinToken.mockReset());

  it("only fetches the token after the citizen opens the QR (enabled gating)", () => {
    useCheckinToken.mockImplementation((_id, enabled) => {
      // The hook is called with enabled=false until the button is clicked.
      return enabled ? loaded("tok-123") : idle();
    });
    render(<AppointmentCheckinQr appointmentId="a1" />);
    expect(screen.queryByTestId("qr")).toBeNull();

    fireEvent.click(screen.getByText("Show my check-in QR"));
    expect(screen.getByTestId("qr")).toHaveAttribute("data-value", "tok-123");
  });

  it("renders the QR from the signed token and shows validity", () => {
    useCheckinToken.mockReturnValue(loaded("tok-abc", "2026-07-19T12:00:00Z"));
    render(<AppointmentCheckinQr appointmentId="a1" />);
    fireEvent.click(screen.getByText("Show my check-in QR"));
    expect(screen.getByTestId("qr")).toHaveAttribute("data-value", "tok-abc");
    expect(screen.getByText(/Valid until/i)).toBeInTheDocument();
  });

  it("shows a retryable error when the token can't be prepared", () => {
    useCheckinToken.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      isFetching: false,
      refetch: vi.fn(),
    });
    render(<AppointmentCheckinQr appointmentId="a1" />);
    fireEvent.click(screen.getByText("Show my check-in QR"));
    expect(screen.getByText(/couldn.?t prepare your check-in code/i)).toBeInTheDocument();
    expect(screen.queryByTestId("qr")).toBeNull();
  });
});
