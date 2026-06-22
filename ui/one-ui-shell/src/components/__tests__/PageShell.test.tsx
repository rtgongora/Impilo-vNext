import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { PageShell } from "../PageShell";

describe("PageShell", () => {
  it("renders title", () => {
    render(<PageShell title="Patient Queue" />);
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Patient Queue");
  });

  it("renders subtitle when provided", () => {
    render(<PageShell title="Dashboard" subtitle="Overview of your facility" />);
    expect(screen.getByText("Overview of your facility")).toBeInTheDocument();
  });

  it("does not render subtitle when not provided", () => {
    const { container } = render(
      <PageShell title="Dashboard">
        <div data-testid="content" />
      </PageShell>,
    );
    const header = container.querySelector(".impilo-surface-card");
    const paragraphs = header?.querySelectorAll("p.text-sm.text-muted-foreground") ?? [];
    expect(paragraphs).toHaveLength(0);
  });

  it("renders children when provided", () => {
    render(
      <PageShell title="Test">
        <div data-testid="child-content">Hello World</div>
      </PageShell>
    );
    expect(screen.getByTestId("child-content")).toBeInTheDocument();
    expect(screen.getByText("Hello World")).toBeInTheDocument();
  });

  it("renders default empty state when no children", () => {
    render(<PageShell title="Empty Page" />);
    expect(screen.getByText("No data available")).toBeInTheDocument();
  });

  it("renders custom empty state label when no children", () => {
    render(<PageShell title="Empty" emptyStateLabel="Nothing to show here" />);
    expect(screen.getByText("Nothing to show here")).toBeInTheDocument();
  });

  it("prefers children over empty state", () => {
    render(
      <PageShell title="Test" emptyStateLabel="Should not appear">
        <p>Actual content</p>
      </PageShell>
    );
    expect(screen.getByText("Actual content")).toBeInTheDocument();
    expect(screen.queryByText("Should not appear")).not.toBeInTheDocument();
  });

  it("applies correct heading CSS classes", () => {
    render(<PageShell title="Styled" />);
    const heading = screen.getByRole("heading", { level: 1 });
    expect(heading.className).toContain("text-xl");
    expect(heading.className).toContain("font-semibold");
    expect(heading.className).toContain("tracking-tight");
  });

  it("renders service logo when serviceSlug is provided", () => {
    render(<PageShell title="Fundo Studio" serviceSlug="fundo" />);
    expect(screen.getByRole("img", { name: "Fundo logo" })).toBeInTheDocument();
  });

  it("applies bottom margin to the hero header container", () => {
    const { container } = render(<PageShell title="Test" />);
    const titleContainer = container.firstElementChild?.firstElementChild;
    expect(titleContainer?.className).toContain("mb-5");
  });

  it("skips the title card when hideHeader is set", () => {
    render(
      <PageShell title="Home" hideHeader>
        <div data-testid="home-body">Welcome</div>
      </PageShell>,
    );
    expect(screen.queryByRole("heading", { level: 1 })).not.toBeInTheDocument();
    expect(screen.getByTestId("home-body")).toBeInTheDocument();
  });
});
