import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ProceduresCataloguePage from "./page";

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div><h1>{title}</h1>{children}</div>
  ),
}));

const searchState = vi.fn();
const detailState = vi.fn();
const checkState = vi.fn();

vi.mock("@/hooks/queries/useProceduresCatalogue", () => ({
  useCatalogueSearch: () => searchState(),
  useCatalogueDetail: () => detailState(),
  useAppropriatenessCheck: () => checkState(),
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ProceduresCataloguePage />
    </QueryClientProvider>,
  );
}

const ITEM = {
  definitionCode: "PROC-LAPAROTOMY",
  version: 1,
  clinicalName: "Exploratory laparotomy",
  category: "THEATRE",
  owningSpecialty: "SURGERY",
  purpose: "BOTH",
  permittedSettings: ["THEATRE"],
  lateralityApplicability: "MIDLINE",
  requiresSiteSideVerification: false,
  expectedDurationMin: 120,
  ziboCode: "54.11",
};

describe("ProceduresCataloguePage — empty vs unknown vs unavailable", () => {
  it("renders a genuine fetch failure as an error banner, never as an empty list", () => {
    // The exact failure this page is written to prevent: a downstream error rendering as
    // "no procedures found". isError must produce the unavailable banner, not the empty state.
    searchState.mockReturnValue({ isLoading: false, isError: true, data: undefined });
    detailState.mockReturnValue({ isLoading: false, isError: false, data: undefined });
    checkState.mockReturnValue({ isLoading: false, isError: false, data: undefined });

    renderPage();

    expect(screen.getByTestId("procedures-catalogue-unavailable")).toBeInTheDocument();
    expect(screen.queryByTestId("procedures-catalogue-list")).not.toBeInTheDocument();
    expect(screen.queryByText(/no published entries/i)).not.toBeInTheDocument();
  });

  it("distinguishes a genuinely empty catalogue from an unavailable one", () => {
    searchState.mockReturnValue({
      isLoading: false, isError: false,
      data: { items: [], matched: 0, catalogueSize: 0 },
    });
    detailState.mockReturnValue({ isLoading: false, isError: false, data: undefined });
    checkState.mockReturnValue({ isLoading: false, isError: false, data: undefined });

    renderPage();

    expect(screen.getByText(/catalogue has no published entries/i)).toBeInTheDocument();
    expect(screen.queryByTestId("procedures-catalogue-unavailable")).not.toBeInTheDocument();
  });

  it("distinguishes a filter matching nothing from an empty catalogue", () => {
    searchState.mockReturnValue({
      isLoading: false, isError: false,
      data: { items: [], matched: 0, catalogueSize: 66 },
    });
    detailState.mockReturnValue({ isLoading: false, isError: false, data: undefined });
    checkState.mockReturnValue({ isLoading: false, isError: false, data: undefined });

    renderPage();

    const noMatch = screen.getByTestId("procedures-catalogue-no-match");
    expect(noMatch).toHaveTextContent("66");
  });

  it("lists real catalogue entries and flags site/side procedures", () => {
    searchState.mockReturnValue({
      isLoading: false, isError: false,
      data: { items: [{ ...ITEM, requiresSiteSideVerification: true }], matched: 1, catalogueSize: 66 },
    });
    detailState.mockReturnValue({ isLoading: false, isError: false, data: undefined });
    checkState.mockReturnValue({ isLoading: false, isError: false, data: undefined });

    renderPage();

    expect(screen.getByText("Exploratory laparotomy")).toBeInTheDocument();
    expect(screen.getByText("site/side")).toBeInTheDocument();
  });
});

describe("ProceduresCataloguePage — catalogue detail and requirements", () => {
  it("shows requirements with their owner and overridability", () => {
    searchState.mockReturnValue({
      isLoading: false, isError: false,
      data: { items: [ITEM], matched: 1, catalogueSize: 66 },
    });
    detailState.mockReturnValue({
      isLoading: false, isError: false,
      data: {
        ...ITEM, synonyms: [], anatomicalSite: "Abdominal cavity", ageMinDays: null, ageMaxDays: null,
        pregnancyApplicability: "CAUTION", recoveryRequired: true, consentType: "CONSENT-SURGICAL",
        snomedCtCode: null, status: "PUBLISHED", approvingAuthority: "PENDING_MOHCC_RATIFICATION",
        sourceCitation: null,
        requirements: [{
          requirementKind: "SITE_SIDE_VERIFICATION", requirementCode: "SITE-SIDE-CONFIRMED",
          requirementLabel: "Anatomical site and side confirmed", obligation: "MANDATORY",
          conditionExpression: null, ownerRole: "PERFORMING_CLINICIAN", resolverService: null,
          overridableInEmergency: false, onResolverUnavailable: "BLOCK",
        }],
      },
    });
    checkState.mockReturnValue({ isLoading: false, isError: false, data: undefined });

    renderPage();
    fireEvent.click(screen.getByText("Exploratory laparotomy"));

    expect(screen.getByText("Anatomical site and side confirmed")).toBeInTheDocument();
    expect(screen.getByText(/Owner: PERFORMING_CLINICIAN/)).toBeInTheDocument();
    expect(screen.getByText(/not overridable/)).toBeInTheDocument();
    expect(screen.getByText(/pending MoHCC ratification/i)).toBeInTheDocument();
  });
});

describe("ProceduresCataloguePage — appropriateness check", () => {
  const DETAIL = {
    ...ITEM, synonyms: [], anatomicalSite: null, ageMinDays: null, ageMaxDays: null,
    pregnancyApplicability: "NO_CONSTRAINT", recoveryRequired: true, consentType: "CONSENT-SURGICAL",
    snomedCtCode: null, status: "PUBLISHED", approvingAuthority: "PENDING_MOHCC_RATIFICATION",
    sourceCitation: null, requirements: [],
  };

  it("renders a BLOCK detection distinctly, with its suggested action", () => {
    searchState.mockReturnValue({
      isLoading: false, isError: false,
      data: { items: [ITEM], matched: 1, catalogueSize: 66 },
    });
    detailState.mockReturnValue({ isLoading: false, isError: false, data: DETAIL });
    checkState.mockReturnValue({
      isLoading: false, isError: false,
      data: {
        outcome: "BLOCKED", detectionCount: 1,
        detections: [{
          code: "SIDE_NOT_SPECIFIED", disposition: "BLOCK", severity: "HIGH",
          message: "Side not specified", suggestedAction: "Record the side.",
        }],
      },
    });

    renderPage();
    fireEvent.click(screen.getByText("Exploratory laparotomy"));
    fireEvent.click(screen.getByTestId("procedures-run-appropriateness-check"));

    expect(screen.getByTestId("procedures-appropriateness-outcome")).toHaveTextContent("BLOCKED");
    expect(screen.getByText("Side not specified")).toBeInTheDocument();
    expect(screen.getByText(/Record the side\./)).toBeInTheDocument();
  });

  it("never lets a check failure read as a clean result", () => {
    searchState.mockReturnValue({
      isLoading: false, isError: false,
      data: { items: [ITEM], matched: 1, catalogueSize: 66 },
    });
    detailState.mockReturnValue({ isLoading: false, isError: false, data: DETAIL });
    checkState.mockReturnValue({ isLoading: false, isError: true, data: undefined });

    renderPage();
    fireEvent.click(screen.getByText("Exploratory laparotomy"));
    fireEvent.click(screen.getByTestId("procedures-run-appropriateness-check"));

    expect(screen.getByText(/could not evaluate appropriateness/i)).toBeInTheDocument();
    expect(screen.queryByTestId("procedures-appropriateness-outcome")).not.toBeInTheDocument();
  });
});
