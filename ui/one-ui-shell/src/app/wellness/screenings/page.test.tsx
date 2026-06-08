import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import WellnessScreeningsPage from "./page";

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

vi.mock("@/components/FeatureMaturityBadge", () => ({
  FeatureMaturityBadge: ({ status }: { status: string }) => <span>{status}</span>,
}));

vi.mock("@/hooks/queries/useGuidance", () => ({
  useReminders: () => ({
    data: {
      data: [
        {
          id: "rem-1",
          type: "SCREENING",
          title: "Blood pressure check",
          description: "Annual screening due",
          dueDate: "2026-07-01",
          priority: "HIGH",
          dismissed: false,
        },
        {
          id: "rem-2",
          type: "MEDICATION",
          title: "Take medication",
          description: "Ignored on this page",
          priority: "LOW",
          dismissed: false,
        },
      ],
    },
    isLoading: false,
    isError: false,
  }),
}));

describe("WellnessScreeningsPage", () => {
  it("renders screening reminders from guidance BFF without fabricated fixtures", () => {
    render(<WellnessScreeningsPage />);
    expect(screen.getByText("Screening Schedule")).toBeInTheDocument();
    expect(screen.getByText("Blood pressure check")).toBeInTheDocument();
    expect(screen.queryByText("Take medication")).not.toBeInTheDocument();
    expect(screen.getByText(/Integration boundary/i)).toBeInTheDocument();
    expect(screen.getByText("partial")).toBeInTheDocument();
  });
});
