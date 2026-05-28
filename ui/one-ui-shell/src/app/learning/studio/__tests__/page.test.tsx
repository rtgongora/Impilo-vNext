import type { ReactNode } from "react";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import FundoStudioPage from "../page";

const { dashboardState } = vi.hoisted(() => ({
  dashboardState: {
    data: { data: { draftCourses: 3, publishedCourses: 8, aiPendingReview: 2, libraryResources: 12, mediaDrafts: 4 } },
    isLoading: false,
  },
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/learning/studio",
}));

vi.mock("@/components/learning/FundoStudioWorkspace", () => ({
  FundoStudioWorkspace: ({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) => (
    <section>
      <h1>{title}</h1>
      <p>{subtitle}</p>
      {children}
    </section>
  ),
}));

vi.mock("@/hooks/queries/useFundoStudio", () => ({
  useFundoStudioDashboard: () => dashboardState,
}));

describe("Fundo Studio page", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders studio KPI cards", () => {
    render(<FundoStudioPage />);
    expect(screen.getByRole("heading", { name: "Fundo Studio" })).toBeInTheDocument();
    expect(screen.getByText("Draft courses")).toBeInTheDocument();
    expect(screen.getByText("Published courses")).toBeInTheDocument();
    expect(screen.getByText("AI awaiting review")).toBeInTheDocument();
  });
});
