import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ModuleWorkspaceHero } from "./ModuleWorkspaceHero";

describe("ModuleWorkspaceHero", () => {
  it("renders a protected logo tile with title and subtitle for Madi", () => {
    render(
      <ModuleWorkspaceHero
        title="Madi"
        subtitle="Blood donation, transfusion safety and haemovigilance"
        serviceSlug="madi"
      />,
    );

    expect(screen.getByTestId("module-workspace-hero")).toBeInTheDocument();
    expect(screen.getByTestId("module-hero-logo-tile")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1, name: "Madi" })).toBeInTheDocument();
    expect(screen.getByText("Blood donation, transfusion safety and haemovigilance")).toBeInTheDocument();
    expect(screen.getByTestId("service-logo")).toHaveAttribute("data-service-slug", "madi");
  });

  it("falls back gracefully when branding is unknown", () => {
    render(
      <ModuleWorkspaceHero
        title="Custom Module"
        subtitle="A bespoke workspace"
        serviceSlug="unknown-service"
      />,
    );

    expect(screen.getByTestId("module-hero-logo-tile")).toBeInTheDocument();
    expect(screen.getByTestId("service-logo-fallback")).toBeInTheDocument();
  });
});
