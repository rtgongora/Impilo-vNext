import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, beforeEach } from "vitest";
import { ActingForBanner } from "./ActingForBanner";
import { useDelegationStore } from "@/hooks/useDelegationStore";

describe("ActingForBanner (G-CZO-03)", () => {
  beforeEach(() => useDelegationStore.getState().exitContext());

  it("renders nothing when not acting for anyone", () => {
    const { container } = render(<ActingForBanner />);
    expect(container).toBeEmptyDOMElement();
  });

  it("shows the subject + relationship and exits on click", () => {
    useDelegationStore.getState().enterContext({
      subjectRef: "Patient/cpid-9",
      subjectLabel: "Tendai Moyo",
      relationshipType: "GUARDIAN",
    });

    render(<ActingForBanner />);
    expect(screen.getByText("Tendai Moyo")).toBeInTheDocument();
    expect(screen.getByText(/guardian/i)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /exit/i }));
    expect(useDelegationStore.getState().actingFor).toBeNull();
    expect(sessionStorage.getItem("exp:acting_for_subject")).toBeNull();
  });

  it("mirrors the subject ref to sessionStorage for the api-client to send as X-Subject-ID", () => {
    useDelegationStore.getState().enterContext({
      subjectRef: "Patient/cpid-42",
      subjectLabel: "Rudo",
      relationshipType: "CAREGIVER",
    });
    expect(sessionStorage.getItem("exp:acting_for_subject")).toBe("Patient/cpid-42");
  });
});
