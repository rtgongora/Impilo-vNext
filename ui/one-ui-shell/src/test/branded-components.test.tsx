import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { StatusBadge, AlertBanner, NompiloTipCard, ServiceCard } from "shared-ui";

describe("Impilo branded shared-ui components", () => {
  it("StatusBadge renders success variant", () => {
    render(<StatusBadge variant="success">Live</StatusBadge>);
    expect(screen.getByText("Live")).toBeTruthy();
  });

  it("StatusBadge pending uses yellow soft background", () => {
    const { container } = render(<StatusBadge variant="pending">Pending</StatusBadge>);
    expect(container.firstChild).toHaveClass("bg-warning-soft");
  });

  it("AlertBanner renders danger role alert", () => {
    render(
      <AlertBanner variant="danger" title="Blocked">
        Action required
      </AlertBanner>,
    );
    expect(screen.getByRole("alert")).toBeTruthy();
  });

  it("NompiloTipCard renders accessible tip", () => {
    render(<NompiloTipCard message="Try searching for a provider" />);
    expect(screen.getByLabelText("Nompilo tip")).toBeTruthy();
  });

  it("ServiceCard renders title without href", () => {
    render(<ServiceCard title="Registry" description="Client registry" />);
    expect(screen.getByText("Registry")).toBeTruthy();
  });
});
