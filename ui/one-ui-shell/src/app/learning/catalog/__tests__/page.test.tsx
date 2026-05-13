import type { ReactNode } from "react";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import LearningCataloguePage from "../page";

/**
 * Phase 6B — vitest coverage for the live Impilo Fundo catalogue page.
 *
 * Stubs out shell scaffolding (AppLayout, PageShell), Next.js router
 * primitives, and the `useFundoCatalog` hook so the page renders
 * deterministically against three fixture states: loaded, empty, and
 * error.
 */

const { catalogState } = vi.hoisted(() => ({
  catalogState: {
    data: undefined as
      | { data: { limit: number; items: Array<Record<string, unknown>> } }
      | undefined,
    isLoading: false,
    isError: false,
  },
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/learning/catalog",
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({
    title,
    subtitle,
    children,
  }: {
    title: string;
    subtitle?: string;
    children: ReactNode;
  }) => (
    <section>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </section>
  ),
}));

vi.mock("@/hooks/queries/useFundoCatalog", () => ({
  useFundoCatalog: () => catalogState,
}));

function setState(state: Partial<typeof catalogState>) {
  Object.assign(catalogState, state);
}

describe("Phase 6B — LearningCataloguePage", () => {
  afterEach(() => {
    setState({ data: undefined, isLoading: false, isError: false });
    cleanup();
  });

  it("renders course cards from the v1.1 catalogue", () => {
    setState({
      data: {
        data: {
          limit: 25,
          items: [
            {
              id: "course-1",
              code: "FUNDO-CTX-A",
              title: "Foundation A",
              description: "An orientation course for new users",
              category: "ORIENTATION",
              level: "INTRODUCTORY",
              status: "PUBLISHED",
              language: "en",
              estimatedDurationMinutes: 30,
              mandatory: true,
              cpdEligible: false,
            },
            {
              id: "course-2",
              code: "FUNDO-CTX-B",
              title: "Foundation B (CPD)",
              description: null,
              category: "EHR_BASICS",
              level: "INTRODUCTORY",
              status: "PUBLISHED",
              language: "en",
              estimatedDurationMinutes: null,
              mandatory: false,
              cpdEligible: true,
              cpdPoints: 2,
            },
          ],
        },
      },
    });

    render(<LearningCataloguePage />);

    expect(screen.getByRole("heading", { level: 1, name: "Impilo Fundo Catalogue" })).toBeInTheDocument();
    expect(screen.getByText("Foundation A")).toBeInTheDocument();
    expect(screen.getByText("Foundation B (CPD)")).toBeInTheDocument();
    expect(screen.getByText("CPD")).toBeInTheDocument();
    expect(screen.getByText("Mandatory")).toBeInTheDocument();
    expect(screen.getByText("30 min")).toBeInTheDocument();
  });

  it("renders an empty state when the catalogue is empty", () => {
    setState({ data: { data: { limit: 25, items: [] } } });
    render(<LearningCataloguePage />);
    expect(screen.getByText(/No catalogue items/i)).toBeInTheDocument();
  });

  it("renders a graceful error state when learning-service is unreachable", () => {
    setState({ isError: true });
    render(<LearningCataloguePage />);
    expect(screen.getByText(/currently unavailable/i)).toBeInTheDocument();
  });

  it("renders a loading state while the catalogue is fetching", () => {
    setState({ isLoading: true });
    render(<LearningCataloguePage />);
    expect(screen.getByText(/Loading catalogue/i)).toBeInTheDocument();
  });
});
