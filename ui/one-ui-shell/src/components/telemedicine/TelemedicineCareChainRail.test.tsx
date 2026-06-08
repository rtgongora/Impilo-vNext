import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TelemedicineCareChainRail } from "./TelemedicineCareChainRail";

describe("TelemedicineCareChainRail", () => {
  it("renders care chain links for triage, referrals, and telemedicine", () => {
    render(<TelemedicineCareChainRail />);

    expect(screen.getByTestId("telemedicine-care-chain-rail")).toBeInTheDocument();
    expect(screen.getByTestId("telemedicine-care-chain-queue-triage")).toHaveAttribute("href", "/queue/triage");
    expect(screen.getByTestId("telemedicine-care-chain-queue-incoming-referrals")).toHaveAttribute(
      "href",
      "/queue/incoming-referrals",
    );
    expect(screen.getByTestId("telemedicine-care-chain-telemedicine-new")).toHaveAttribute(
      "href",
      "/telemedicine/new",
    );
    expect(screen.getByTestId("telemedicine-care-chain-telemedicine")).toHaveAttribute("href", "/telemedicine");
  });
});
