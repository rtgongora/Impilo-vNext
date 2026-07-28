import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
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
const safetyPauseState = vi.fn();
const sedationLevelState = vi.fn();
const recoverySettingState = vi.fn();
const aftercareTemplateState = vi.fn();

const IDLE = { isLoading: false, isError: false, data: undefined };

vi.mock("@/hooks/queries/useProceduresCatalogue", () => ({
  useCatalogueSearch: () => searchState(),
  useCatalogueDetail: () => detailState(),
  useAppropriatenessCheck: () => checkState(),
  useSafetyPauseTemplate: (code: string | null) => safetyPauseState(code),
  useSedationLevel: (code: string | null) => sedationLevelState(code),
  useRecoverySetting: (code: string | null) => recoverySettingState(code),
  // Called twice by PostProcedurePanels (specific code, then fallback code) — the mock must
  // answer per-argument, the same reason searchState/detailState answer per-hook rather than
  // sharing one canned value.
  useAftercareTemplate: (code: string | null) => aftercareTemplateState(code),
}));

beforeEach(() => {
  // Default every P-R2 panel hook to idle so tests that don't exercise the panels (most of
  // this file, predating them) don't need to know these hooks exist.
  safetyPauseState.mockReturnValue(IDLE);
  sedationLevelState.mockReturnValue(IDLE);
  recoverySettingState.mockReturnValue(IDLE);
  aftercareTemplateState.mockReturnValue(IDLE);
});

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

describe("ProceduresCataloguePage — Wave P-R2 post-procedure panels (safety pause / sedation / recovery / aftercare)", () => {
  const DETAIL = {
    ...ITEM, synonyms: [], anatomicalSite: null, ageMinDays: null, ageMaxDays: null,
    pregnancyApplicability: "NO_CONSTRAINT", recoveryRequired: true, consentType: "CONSENT-SURGICAL",
    snomedCtCode: null, status: "PUBLISHED", approvingAuthority: "PENDING_MOHCC_RATIFICATION",
    sourceCitation: null, requirements: [],
    safetyPauseTemplate: "SAFETY-PAUSE-SURGERY",
    defaultSedationLevelCode: "GENERAL_ANAESTHESIA",
    defaultRecoverySettingCode: "PACU",
    aftercareTemplate: "AFTERCARE-LAPAROTOMY",
    defaultAftercareTemplateCode: "AFTERCARE-THEATRE",
  };

  function selectDetail() {
    searchState.mockReturnValue({
      isLoading: false, isError: false,
      data: { items: [ITEM], matched: 1, catalogueSize: 66 },
    });
    detailState.mockReturnValue({ isLoading: false, isError: false, data: DETAIL });
    checkState.mockReturnValue(IDLE);
  }

  it("does not render any panel when a procedure declares none of the four linkages", () => {
    searchState.mockReturnValue({
      isLoading: false, isError: false,
      data: { items: [ITEM], matched: 1, catalogueSize: 66 },
    });
    detailState.mockReturnValue({
      isLoading: false, isError: false,
      data: { ...DETAIL, safetyPauseTemplate: null, defaultSedationLevelCode: null,
               defaultRecoverySettingCode: null, aftercareTemplate: null, defaultAftercareTemplateCode: null },
    });
    checkState.mockReturnValue(IDLE);

    renderPage();
    fireEvent.click(screen.getByText("Exploratory laparotomy"));

    expect(screen.queryByTestId("procedures-safety-pause-panel")).not.toBeInTheDocument();
    expect(screen.queryByTestId("procedures-sedation-panel")).not.toBeInTheDocument();
    expect(screen.queryByTestId("procedures-recovery-panel")).not.toBeInTheDocument();
    expect(screen.queryByTestId("procedures-aftercare-panel")).not.toBeInTheDocument();
  });

  it("renders the safety-pause confirmation items", () => {
    selectDetail();
    safetyPauseState.mockReturnValue({
      isLoading: false, isError: false,
      data: {
        templateCode: "SAFETY-PAUSE-SURGERY", templateName: "WHO Surgical Safety Checklist Time Out",
        applicableSetting: "THEATRE", description: null, status: "PUBLISHED", approvingAuthority: "PENDING_MOHCC_RATIFICATION",
        confirmationItems: [{ confirmationItem: "PATIENT", promptText: "Confirm patient identity, out loud, with the whole team." }],
      },
    });

    renderPage();
    fireEvent.click(screen.getByText("Exploratory laparotomy"));

    expect(screen.getByTestId("procedures-safety-pause-panel")).toHaveTextContent(
      "Confirm patient identity, out loud, with the whole team.",
    );
  });

  it("renders the sedation level and its resolved rescue capability", () => {
    selectDetail();
    sedationLevelState.mockReturnValue({
      isLoading: false, isError: false,
      data: {
        levelCode: "GENERAL_ANAESTHESIA", levelLabel: "General anaesthesia", depthRank: 5,
        monitoringRequired: "Continuous pulse oximetry, capnography, cardiac monitoring, temperature.",
        providerCompetenceRequired: "An anaesthesia provider throughout.", typicalRecoveryCriteria: null,
        rescueCapability: null,
      },
    });

    renderPage();
    fireEvent.click(screen.getByText("Exploratory laparotomy"));

    expect(screen.getByTestId("procedures-sedation-panel")).toHaveTextContent("General anaesthesia");
  });

  it("renders the recovery setting's discharge-readiness criteria", () => {
    selectDetail();
    recoverySettingState.mockReturnValue({
      isLoading: false, isError: false,
      data: {
        settingCode: "PACU", settingLabel: "Post-anaesthesia care unit", minimumObservationMinutes: 60,
        dischargeReadinessCriteria: "Aldrete or equivalent discharge-readiness score met.",
        monitoringRequired: "Continuous pulse oximetry.",
      },
    });

    renderPage();
    fireEvent.click(screen.getByText("Exploratory laparotomy"));

    expect(screen.getByTestId("procedures-recovery-panel")).toHaveTextContent(
      "Aldrete or equivalent discharge-readiness score met.",
    );
  });

  /** The two-step resolution's primary path: the specific per-procedure code resolves directly. */
  it("prefers the specific aftercare template over the coarse fallback when the specific one resolves", () => {
    selectDetail();
    aftercareTemplateState.mockImplementation((code: string | null) => {
      if (code === "AFTERCARE-LAPAROTOMY") {
        return {
          isLoading: false, isError: false,
          data: {
            templateCode: "AFTERCARE-LAPAROTOMY", templateName: "Laparotomy aftercare",
            applicableSetting: "THEATRE", description: null, status: "PUBLISHED",
            approvingAuthority: "PENDING_MOHCC_RATIFICATION", contentMaturity: "ENGINEERING_SEED",
            instructions: [{ instructionKind: "WOUND_SITE_CARE", instructionText: "Keep the abdominal wound clean and dry." }],
            deliveryChannels: ["CLINICAL_SUMMARY"],
          },
        };
      }
      return IDLE; // fallback query not enabled — the specific one already resolved
    });

    renderPage();
    fireEvent.click(screen.getByText("Exploratory laparotomy"));

    const panel = screen.getByTestId("procedures-aftercare-panel");
    expect(panel).toHaveTextContent("Keep the abdominal wound clean and dry.");
    expect(panel).not.toHaveTextContent(/generic/i);
  });

  /**
   * The two-step resolution's fallback path — the finding V007 exists to fix for a growing
   * subset, but most of the catalogue still lands here. MUTATION-SHAPED: proves the fallback
   * copy actually distinguishes itself from the primary path, not just that content renders.
   */
  it("falls back to the coarse setting-class template and labels it as generic when the specific one has no row", () => {
    selectDetail();
    aftercareTemplateState.mockImplementation((code: string | null) => {
      if (code === "AFTERCARE-LAPAROTOMY") {
        return { isLoading: false, isError: true, data: undefined }; // specific code: no template row
      }
      if (code === "AFTERCARE-THEATRE") {
        return {
          isLoading: false, isError: false,
          data: {
            templateCode: "AFTERCARE-THEATRE", templateName: "Post-surgical aftercare",
            applicableSetting: "THEATRE", description: null, status: "PUBLISHED",
            approvingAuthority: "PENDING_MOHCC_RATIFICATION", contentMaturity: "ENGINEERING_SEED",
            instructions: [{ instructionKind: "WOUND_SITE_CARE", instructionText: "Keep the wound clean and dry." }],
            deliveryChannels: ["CLINICAL_SUMMARY"],
          },
        };
      }
      return IDLE;
    });

    renderPage();
    fireEvent.click(screen.getByText("Exploratory laparotomy"));

    const panel = screen.getByTestId("procedures-aftercare-panel");
    expect(panel).toHaveTextContent("Keep the wound clean and dry.");
    expect(panel).toHaveTextContent(/generic/i);
    expect(panel).toHaveTextContent("AFTERCARE-LAPAROTOMY"); // names the code that's still missing
  });

  it("renders unavailable, not empty, when neither the specific nor the fallback aftercare template can be read", () => {
    selectDetail();
    aftercareTemplateState.mockImplementation((code: string | null) => {
      if (code === "AFTERCARE-LAPAROTOMY") return { isLoading: false, isError: true, data: undefined };
      if (code === "AFTERCARE-THEATRE") return { isLoading: false, isError: true, data: undefined };
      return IDLE;
    });

    renderPage();
    fireEvent.click(screen.getByText("Exploratory laparotomy"));

    const panel = screen.getByTestId("procedures-aftercare-panel");
    expect(panel).toHaveTextContent(/could not load an aftercare template/i);
    expect(panel).not.toHaveTextContent(/no aftercare template declared/i);
  });
});
