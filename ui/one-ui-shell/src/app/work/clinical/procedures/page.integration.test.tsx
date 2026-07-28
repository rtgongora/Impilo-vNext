import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import ProceduresCataloguePage from "./page";

/**
 * The genuine end-to-end mutation-proof for the empty/unknown/unavailable guarantee.
 *
 * page.test.tsx proves the page reacts correctly GIVEN `isError`, by mocking the hooks
 * module directly. That is necessary but not sufficient — it assumes the chain from a real
 * HTTP failure to `isError` actually holds. This file proves the chain itself: a real fetch
 * 502, through the REAL `apiClient` and the REAL `useCatalogueSearch` hook (neither mocked
 * here), rendering the REAL page. Nothing about the query layer is faked; only `fetch` is,
 * which is the actual network boundary and the only honest place to inject the failure.
 *
 * This is the specific claim the whole wave rests on, proven rather than assumed: a live BFF
 * 502 once rendered as "no conditions" for every patient on the adult problem list because a
 * client fell through `data ?? []` on error. The mutation below is that exact scenario,
 * replayed against this surface.
 */

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div><h1>{title}</h1>{children}</div>
  ),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);
vi.stubGlobal("crypto", { randomUUID: vi.fn(() => "test-uuid") });

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ProceduresCataloguePage />
    </QueryClientProvider>,
  );
}

describe("Procedures catalogue — real apiClient, real hook, mutation-proof", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("MUTATION: a real 502 renders the unavailable banner, never the empty-catalogue text", async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 502,
      json: () => Promise.resolve({ error: { code: "SERVICE_UNAVAILABLE", message: "procedures-service unreachable" } }),
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId("procedures-catalogue-unavailable")).toBeInTheDocument();
    });
    // The specific failure this test exists to catch: an error state that ALSO satisfies the
    // empty-catalogue copy would mean a reader (or a future refactor) could conflate the two.
    expect(screen.queryByText(/catalogue has no published entries/i)).not.toBeInTheDocument();
    expect(screen.queryByTestId("procedures-catalogue-list")).not.toBeInTheDocument();
  });

  it("CONTROL: a real 200 with catalogueSize=0 renders the empty-catalogue text, not the error banner", async () => {
    // The other half of the mutation pair. Without this, the test above could pass for the
    // wrong reason — a page that always renders the unavailable banner regardless of what
    // fetch returns would also satisfy it.
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ items: [], matched: 0, catalogueSize: 0 }),
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/catalogue has no published entries/i)).toBeInTheDocument();
    });
    expect(screen.queryByTestId("procedures-catalogue-unavailable")).not.toBeInTheDocument();
  });

  it("CONTROL: a real 200 with real items renders them, proving the happy path was not broken to pass the other two", async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({
        items: [{
          definitionCode: "PROC-LAPAROTOMY", version: 1, clinicalName: "Exploratory laparotomy",
          category: "THEATRE", owningSpecialty: "SURGERY", purpose: "BOTH",
          permittedSettings: ["THEATRE"], lateralityApplicability: "MIDLINE",
          requiresSiteSideVerification: false, expectedDurationMin: 120, ziboCode: "54.11",
        }],
        matched: 1, catalogueSize: 66,
      }),
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Exploratory laparotomy")).toBeInTheDocument();
    });
    expect(screen.queryByTestId("procedures-catalogue-unavailable")).not.toBeInTheDocument();
    expect(screen.queryByText(/catalogue has no published entries/i)).not.toBeInTheDocument();
  });
});
