import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TeleconsultReadinessChecklist } from "./TeleconsultReadinessChecklist";

describe("TeleconsultReadinessChecklist", () => {
  it("reports READY when all three signals are ok", () => {
    render(<TeleconsultReadinessChecklist consentOk authorityOk patientContextOk />);
    expect(screen.getByTestId("teleconsult-readiness-checklist")).toBeInTheDocument();
    expect(screen.getByTestId("readiness-verdict")).toHaveTextContent("READY");
    expect(screen.getAllByTestId("readiness-row-ok")).toHaveLength(3);
  });

  it("reports BLOCKED and marks the failing rows when a signal is missing", () => {
    render(
      <TeleconsultReadinessChecklist consentOk={false} authorityOk patientContextOk={false} />,
    );
    expect(screen.getByTestId("readiness-verdict")).toHaveTextContent("BLOCKED");
    expect(screen.getAllByTestId("readiness-row-blocked")).toHaveLength(2);
    expect(screen.getAllByTestId("readiness-row-ok")).toHaveLength(1);
  });

  it("surfaces the consent hint when consent is not verified", () => {
    render(
      <TeleconsultReadinessChecklist consentOk={false} authorityOk patientContextOk />,
    );
    expect(screen.getByText(/Record patient consent before starting/i)).toBeInTheDocument();
  });
});
