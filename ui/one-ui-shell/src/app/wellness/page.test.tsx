import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import WellnessPage from "./page";

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({
    children,
    title,
    subtitle,
  }: {
    children: ReactNode;
    title: string;
    subtitle?: string;
  }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector: (state: { user: { id: string } | null }) => unknown) =>
    selector({ user: { id: "user-1" } }),
}));

vi.mock("@/hooks/queries/useCitizenWellness", () => ({
  useWellnessActivities: () => ({
    data: [],
    isLoading: false,
  }),
}));

describe("WellnessPage", () => {
  it("renders the wellness hub with doctrine-aligned subtitle", () => {
    render(<WellnessPage />);

    expect(screen.getByRole("heading", { level: 1, name: "Wellness Hub" })).toBeInTheDocument();
    expect(screen.getByText("Move. Eat. Sleep. Rest. Recover. Live.")).toBeInTheDocument();
  });

  it("shows the dashboard hero link", () => {
    render(<WellnessPage />);

    expect(screen.getByText("My Wellness Dashboard")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /My Wellness Dashboard/i })).toHaveAttribute(
      "href",
      "/wellness/dashboard",
    );
  });

  it("renders all 10 wellness section tiles with correct links", () => {
    render(<WellnessPage />);

    const sections = [
      { label: "Health Goals", href: "/wellness/goals" },
      { label: "Activity & Fitness", href: "/wellness/activity" },
      { label: "Diet & Nutrition", href: "/wellness/diet" },
      { label: "Sleep & Recovery", href: "/wellness/sleep" },
      { label: "Clubs & Communities", href: "/wellness/clubs" },
      { label: "Challenges", href: "/wellness/challenges" },
      { label: "Routes & Places", href: "/wellness/routes" },
      { label: "Coaching & Habits", href: "/wellness/coaching" },
      { label: "Prevention Programs", href: "/wellness/programs" },
      { label: "Screening Schedule", href: "/wellness/screenings" },
    ];

    for (const section of sections) {
      expect(screen.getByText(section.label)).toBeInTheDocument();
      expect(screen.getByRole("link", { name: new RegExp(section.label) })).toHaveAttribute(
        "href",
        section.href,
      );
    }
  });
});
