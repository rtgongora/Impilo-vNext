import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AuthHeroContent } from "./AuthHeroContent";

describe("AuthHeroContent", () => {
  it("renders the new landing copy and not legacy messaging", () => {
    render(<AuthHeroContent variant="desktop" />);

    expect(screen.getByText("One Health OS.")).toBeInTheDocument();
    expect(screen.getByText("Your health-life experience, connected.")).toBeInTheDocument();
    expect(screen.getByText("Care in motion. Work in rhythm. Life in sync.")).toBeInTheDocument();
    expect(screen.getByText("Human first.")).toBeInTheDocument();
    expect(screen.getByText("Trust locked.")).toBeInTheDocument();
    expect(screen.getByText("Live now.")).toBeInTheDocument();

    expect(screen.queryByText(/One experience\./i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Empowering healthcare providers/i)).not.toBeInTheDocument();
    expect(screen.queryByText("Person-Centered")).not.toBeInTheDocument();
    expect(screen.queryByText("Trust-First")).not.toBeInTheDocument();
    expect(screen.queryByText("Real-time")).not.toBeInTheDocument();
  });

  it("renders compact variant for mobile hero strip", () => {
    render(<AuthHeroContent variant="compact" />);
    expect(screen.getByTestId("auth-mobile-hero-copy")).toBeInTheDocument();
    expect(screen.getByText("Your health-life experience, connected.")).toBeInTheDocument();
  });
});
