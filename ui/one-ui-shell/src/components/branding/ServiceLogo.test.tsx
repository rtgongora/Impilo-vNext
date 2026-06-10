import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { ServiceLogo } from "@/components/branding/ServiceLogo";

describe("ServiceLogo", () => {
  it("renders logo image for a known service", () => {
    render(<ServiceLogo slug="fundo" size="card" />);
    const img = screen.getByRole("img", { name: "Fundo logo" });
    expect(img).toHaveAttribute("src", "/brand/services/fundo-logo.png");
  });

  it("shows service name when showName is true", () => {
    render(<ServiceLogo slug="vito" showName />);
    expect(screen.getByText("Vito")).toBeInTheDocument();
  });

  it("falls back to icon when image fails to load", () => {
    render(<ServiceLogo slug="tshepo" />);
    const img = screen.getByRole("img", { name: "Tshepo logo" });
    fireEvent.error(img);
    expect(screen.getByTestId("service-logo-fallback")).toBeInTheDocument();
  });

  it("uses fallback icon for unknown slug", () => {
    render(<ServiceLogo slug="not-a-real-service" fallbackIcon="Shield" />);
    expect(screen.getByTestId("service-logo-fallback")).toBeInTheDocument();
  });

  it("sets accessible alt text", () => {
    render(<ServiceLogo slug="nompilo" />);
    expect(screen.getByRole("img", { name: "Nompilo logo" })).toBeInTheDocument();
  });
});
