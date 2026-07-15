import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EmergencyConsentExceptionPanel } from "./EmergencyConsentExceptionPanel";

const post = vi.fn();
vi.mock("@/lib/api-client", () => ({ apiClient: { post: (u: string, b: unknown) => post(u, b) } }));

describe("EmergencyConsentExceptionPanel (Lane 1)", () => {
  beforeEach(() => post.mockReset());

  it("requires a clinical justification before it will submit", () => {
    render(<EmergencyConsentExceptionPanel caseId="c-1" />);
    expect((screen.getByText("Record emergency consent exception") as HTMLButtonElement).disabled).toBe(true);
  });

  it("records the exception and surfaces consent_status=EMERGENCY_EXCEPTION (no fabricated GRANTED consent)", async () => {
    post.mockResolvedValue({ data: { consent_status: "EMERGENCY_EXCEPTION", emergency_consent_basis: "DEFERRED" } });
    render(<EmergencyConsentExceptionPanel caseId="c-5" />);
    fireEvent.change(screen.getByTestId("consent-reason"), { target: { value: "Life-threatening haemorrhage" } });
    fireEvent.click(screen.getByText("Record emergency consent exception"));
    await waitFor(() => expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-5/consent/emergency-exception", expect.objectContaining({ basis: "DEFERRED", reason: "Life-threatening haemorrhage" })));
    expect(await screen.findByTestId("consent-exception-status")).toHaveTextContent("EMERGENCY_EXCEPTION");
  });
});
