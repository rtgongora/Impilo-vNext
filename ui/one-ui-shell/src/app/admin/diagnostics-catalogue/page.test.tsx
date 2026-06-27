import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import DiagnosticsCataloguePage from "./page";

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div><h1>{title}</h1>{children}</div>
  ),
}));

const saveMutate = vi.fn();
// Stable references (react-query returns stable cached data between renders; mirror that so the
// load-effect doesn't re-run and clobber edits).
const CATALOGUE_RESULT = {
  data: { data: { labs: [{ id: "lab-1", specimenTypes: ["blood"], onboarded: true }] } },
  isLoading: false,
  isError: false,
};
const SAVE_RESULT = { mutate: saveMutate, isPending: false, isError: false, isSuccess: false };

vi.mock("@/hooks/queries/useDiagnosticsOrders", () => ({
  useCatalogue: () => CATALOGUE_RESULT,
  useSaveCatalogue: () => SAVE_RESULT,
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <DiagnosticsCataloguePage />
    </QueryClientProvider>,
  );
}

describe("DiagnosticsCataloguePage", () => {
  it("loads the catalogue JSON and saves edits to the right admin path", () => {
    renderPage();
    const textarea = screen.getByLabelText("Catalogue JSON") as HTMLTextAreaElement;
    expect(textarea.value).toContain("specimenTypes");

    fireEvent.change(textarea, { target: { value: '{"labs":[]}' } });
    fireEvent.click(screen.getByText("Save catalogue"));

    expect(saveMutate).toHaveBeenCalledWith({ adminPath: "service-catalogue", body: { labs: [] } });
  });

  it("rejects invalid JSON without calling save", () => {
    renderPage();
    const textarea = screen.getByLabelText("Catalogue JSON") as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: "{ not json" } });
    fireEvent.click(screen.getByText("Save catalogue"));
    expect(screen.getByText(/Invalid JSON/)).toBeInTheDocument();
  });
});
