import { describe, expect, it } from "vitest";
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

  it("supports context-aware variants via `variant`", () => {
    const { rerender } = render(<ServiceLogo slug="vito" variant="hero" />);
    expect(screen.getByTestId("service-logo")).toHaveAttribute("data-variant", "hero");
    rerender(<ServiceLogo slug="vito" variant="nav" />);
    expect(screen.getByTestId("service-logo")).toHaveAttribute("data-variant", "nav");
  });

  it("keeps backwards compatibility with the `size` prop", () => {
    render(<ServiceLogo slug="fundo" size="compact" />);
    expect(screen.getByTestId("service-logo")).toHaveAttribute("data-variant", "compact");
    expect(screen.getByRole("img", { name: "Fundo logo" })).toHaveAttribute(
      "src",
      "/brand/services/fundo-logo.png",
    );
  });

  it("renders the watermark variant as decorative (aria-hidden, empty alt, no accessible name)", () => {
    render(<ServiceLogo slug="rito" variant="watermark" />);
    const node = screen.getByTestId("service-logo-watermark");
    expect(node).toHaveAttribute("aria-hidden", "true");
    // decorative image is hidden from the accessibility tree
    expect(screen.queryByRole("img", { name: "Rito logo" })).not.toBeInTheDocument();
    const img = node.querySelector("img");
    expect(img).toHaveAttribute("alt", "");
  });

  it("still falls back to icon for unknown slug across variants", () => {
    render(<ServiceLogo slug="nope" variant="header" fallbackIcon="Shield" />);
    expect(screen.getByTestId("service-logo-fallback")).toBeInTheDocument();
  });
});
