import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import {
  CoreTransactionShell,
  ClientJourneyStepper,
  ProviderJourneyStepper,
  PlatformJourneyMonitor,
  NompiloAccessibilityAssistPanel,
  NompiloFeedbackPrompt,
  NompiloHandoffPanel,
  NompiloGlobalSearchBar,
  NompiloVoiceDictationButton,
  NompiloServiceFinderResults,
  NompiloProviderFinderResults,
  NompiloCommodityFinderResults,
  NompiloFundoLearningAssistant,
  NompiloDashboardAssistant,
  NompiloLoginBriefingCard,
  NompiloSupportEscalationPanel,
  NompiloWellnessEventResults,
  NompiloCommandResultCard,
  TransactionContextPanel,
} from "../components";
import {
  accessibilityAssistanceRequestedTransaction,
  clientPostVisitFeedbackTransaction,
  emergencyProvisionalIdentityTransaction,
  failedPaymentTransaction,
  facilityWalkInTransaction,
  nompiloVoiceDictationFixture,
  nompiloServiceSearchQueryFixture,
  nompiloProviderSearchQueryFixture,
  nompiloCommoditySearchQueryFixture,
  nompiloFundoQuizRequestFixture,
  nompiloDashboardRequestFixture,
  nompiloReportRequestFixture,
  nompiloSupportTicketCreationFixture,
  nompiloWellnessEventQueryFixture,
  offlineSyncPendingTransaction,
  preServicePaymentRequiredTransaction,
} from "../fixtures/core-transactions";
import {
  classifyNompiloCommandIntent,
  filterSearchSourcesByPermissions,
  isNompiloSourceOfTruth,
} from "../nompilo-command";
import {
  getAllowedNextStates,
  getCurrentJourneyStepStatus,
  getPersonJourneyStageForTransactionState,
  getPlatformJourneyStageForTransactionState,
  getProviderJourneyStageForTransactionState,
  isTerminalCoreTransactionState,
  isValidCoreTransactionTransition,
} from "../state-machine";

describe("core transaction state-machine", () => {
  it("validates normal transitions", () => {
    expect(isValidCoreTransactionTransition("IDENTITY_RESOLVED", "TRUST_CONTEXT_ESTABLISHED", "FACILITY_WALK_IN")).toBe(true);
  });

  it("rejects invalid transitions", () => {
    expect(isValidCoreTransactionTransition("DRAFT", "COMPLETED", "FACILITY_WALK_IN")).toBe(false);
  });

  it("recognizes terminal states", () => {
    expect(isTerminalCoreTransactionState("COMPLETED")).toBe(true);
    expect(isTerminalCoreTransactionState("IN_SERVICE")).toBe(false);
  });

  it("supports emergency branch transitions", () => {
    const states = getAllowedNextStates("INITIATED", "EMERGENCY");
    expect(states).toContain("EMERGENCY_OVERRIDE");
    expect(states).toContain("PROVISIONAL_IDENTITY");
  });

  it("maps state to person/provider/platform journey stages", () => {
    expect(getPersonJourneyStageForTransactionState("PRE_SERVICE_PAYMENT_REQUIRED")).toBe(
      "PAY_UPFRONT_WHERE_REQUIRED",
    );
    expect(getProviderJourneyStageForTransactionState("IN_SERVICE")).toBe("DELIVER_CARE");
    expect(getPlatformJourneyStageForTransactionState("PENDING_SYNC")).toBe(
      "HANDLE_FAILURE_OFFLINE_AND_RECONCILIATION",
    );
  });

  it("reports current journey step status", () => {
    expect(getCurrentJourneyStepStatus("IN_SERVICE", [], "PERSON")).toBe("CURRENT");
    expect(getCurrentJourneyStepStatus("FAILED_SYNC", ["SYNC_FAILED"], "PLATFORM")).toBe("BLOCKED");
  });
});

describe("core transaction composed view and rendering", () => {
  it("requires canonical event payload fields", () => {
    const eventPayload = {
      transaction_id: "tx-1",
      correlation_id: "corr-1",
      tenant_id: "tenant-1",
      pod_id: "pod-1",
      facility_id: "fac-1",
      actor_id: "actor-1",
      actor_type: "PROVIDER",
      purpose_of_use: "TREATMENT",
      service_code: "svc.walk-in",
      transaction_type: "FACILITY_WALK_IN",
      state_before: "IDENTITY_RESOLVED",
      state_after: "TRUST_CONTEXT_ESTABLISHED",
      timestamp: "2026-05-01T08:00:00Z",
      source_system: "one-ui-shell-test",
      offline_sync_status: "ONLINE",
      audit: { correlation_id: "corr-1", source_system: "one-ui-shell-test", audit_required: true },
      trust: { consent_required: true, consent_granted: true, purpose_of_use: "TREATMENT", emergency_override_used: false },
    };
    expect(eventPayload.audit.audit_required).toBe(true);
    expect(eventPayload.trust.consent_required).toBe(true);
    expect(eventPayload.state_before).toBeTruthy();
    expect(eventPayload.state_after).toBeTruthy();
  });

  it("matches expected bff view shape fields", () => {
    expect(facilityWalkInTransaction.transaction.id).toBeTruthy();
    expect(facilityWalkInTransaction.journeys.platform.currentStage).toBeTruthy();
    expect(facilityWalkInTransaction.clientSummary.identityStatus).toBeTruthy();
    expect(Array.isArray(facilityWalkInTransaction.timeline)).toBe(true);
    expect(Array.isArray(facilityWalkInTransaction.nextActions)).toBe(true);
    expect(Array.isArray(facilityWalkInTransaction.permissions)).toBe(true);
    expect(facilityWalkInTransaction.nompilo.available).toBe(true);
  });

  it("renders timeline and next actions", () => {
    render(<CoreTransactionShell transaction={facilityWalkInTransaction} status="ready" />);
    expect(screen.getByText("Transaction Timeline")).toBeInTheDocument();
    expect(screen.getByText("Next Actions")).toBeInTheDocument();
    expect(screen.getByText("Transaction Context")).toBeInTheDocument();
  });

  it("renders failure and offline sync status", () => {
    render(<CoreTransactionShell transaction={emergencyProvisionalIdentityTransaction} status="ready" />);
    expect(screen.getByText(/pending sync/i)).toBeInTheDocument();
    expect(screen.getByText("Failure Modes")).toBeInTheDocument();
  });

  it("renders loading, empty, error, and permission states", () => {
    const { rerender } = render(<CoreTransactionShell status="loading" />);
    expect(screen.getByText(/Loading core transaction/)).toBeInTheDocument();
    rerender(<CoreTransactionShell status="empty" />);
    expect(screen.getByText(/No transaction selected/)).toBeInTheDocument();
    rerender(<CoreTransactionShell status="error" />);
    expect(screen.getByText(/Unable to load transaction/)).toBeInTheDocument();
    rerender(<CoreTransactionShell status="permission-denied" />);
    expect(screen.getByText(/do not have permission/i)).toBeInTheDocument();
  });

  it("renders client and provider steppers", () => {
    render(
      <>
        <ClientJourneyStepper />
        <ProviderJourneyStepper />
      </>,
    );
    expect(screen.getByText("Client Journey")).toBeInTheDocument();
    expect(screen.getByText("Provider Journey")).toBeInTheDocument();
  });

  it("renders platform journey monitor", () => {
    render(<PlatformJourneyMonitor transaction={offlineSyncPendingTransaction} />);
    expect(screen.getByText("Platform Journey")).toBeInTheDocument();
  });

  it("renders standalone transaction context panel", () => {
    render(<TransactionContextPanel transaction={facilityWalkInTransaction} />);
    expect(screen.getByText("Transaction Context")).toBeInTheDocument();
    expect(screen.getByText(/Source of truth/i)).toBeInTheDocument();
  });

  it("renders pre-service payment gate and failed payment guidance", () => {
    render(
      <>
        <CoreTransactionShell transaction={preServicePaymentRequiredTransaction} status="ready" />
        <CoreTransactionShell transaction={failedPaymentTransaction} status="ready" />
      </>,
    );
    expect(screen.getAllByText(/Failure Modes/).length).toBeGreaterThan(0);
  });

  it("renders accessibility and feedback prompts", () => {
    render(
      <>
        <NompiloAccessibilityAssistPanel transaction={accessibilityAssistanceRequestedTransaction} />
        <NompiloFeedbackPrompt transaction={clientPostVisitFeedbackTransaction} />
        <NompiloHandoffPanel transaction={clientPostVisitFeedbackTransaction} />
      </>,
    );
    expect(screen.getByText("Nompilo Accessibility Assist")).toBeInTheDocument();
    expect(screen.getByText("Nompilo Feedback Prompt")).toBeInTheDocument();
    expect(screen.getByText("Nompilo Handoff")).toBeInTheDocument();
  });

  it("renders permission-denied guidance state", () => {
    render(<CoreTransactionShell status="permission-denied" />);
    expect(screen.getByText(/do not have permission/i)).toBeInTheDocument();
  });

  it("classifies command intent fixtures", () => {
    expect(classifyNompiloCommandIntent(nompiloServiceSearchQueryFixture)).toBe("FIND_SERVICE");
    expect(classifyNompiloCommandIntent(nompiloProviderSearchQueryFixture)).toBe("FIND_PROVIDER");
    expect(classifyNompiloCommandIntent(nompiloCommoditySearchQueryFixture)).toBe("FIND_COMMODITY");
    expect(classifyNompiloCommandIntent(nompiloFundoQuizRequestFixture)).toBe("REQUEST_QUIZ");
    expect(classifyNompiloCommandIntent(nompiloDashboardRequestFixture)).toBe("REQUEST_DASHBOARD");
    expect(classifyNompiloCommandIntent(nompiloReportRequestFixture)).toBe("REQUEST_REPORT");
    expect(classifyNompiloCommandIntent(nompiloSupportTicketCreationFixture.summary)).toBe("REQUEST_SUPPORT");
  });

  it("filters search sources by permission", () => {
    const filtered = filterSearchSourcesByPermissions(
      facilityWalkInTransaction.nompiloCommand.availableSearchSources,
      facilityWalkInTransaction.nompiloCommand.toolPermissions,
    );
    expect(filtered).toContain("VITO_CLIENT_REGISTRY");
    expect(filtered).not.toContain("GOVERNED_INTERNET_SEARCH");
  });

  it("renders global search and voice dictation surfaces", () => {
    render(
      <>
        <NompiloGlobalSearchBar transaction={facilityWalkInTransaction} />
        <NompiloVoiceDictationButton transaction={facilityWalkInTransaction} />
      </>,
    );
    expect(screen.getByText("Ask Nompilo")).toBeInTheDocument();
    expect(screen.getByText(/Voice Dictation On/i)).toBeInTheDocument();
    expect(nompiloVoiceDictationFixture).toContain("ANC");
  });

  it("renders service, provider, commodity and wellness finders", () => {
    render(
      <>
        <NompiloServiceFinderResults transaction={facilityWalkInTransaction} />
        <NompiloProviderFinderResults transaction={facilityWalkInTransaction} />
        <NompiloCommodityFinderResults />
        <NompiloWellnessEventResults />
      </>,
    );
    expect(screen.getByText("Nompilo Service Finder")).toBeInTheDocument();
    expect(screen.getByText("Nompilo Provider Finder")).toBeInTheDocument();
    expect(screen.getByText("Nompilo Commodity Finder")).toBeInTheDocument();
    expect(screen.getByText("Nompilo Wellness Finder")).toBeInTheDocument();
    expect(nompiloWellnessEventQueryFixture).toContain("wellness");
  });

  it("renders fundo and dashboard/report assistants", () => {
    render(
      <>
        <NompiloFundoLearningAssistant transaction={facilityWalkInTransaction} />
        <NompiloDashboardAssistant transaction={facilityWalkInTransaction} />
      </>,
    );
    expect(screen.getByText("Nompilo Fundo Learning Assistant")).toBeInTheDocument();
    expect(screen.getByText("Nompilo Dashboard Assistant")).toBeInTheDocument();
  });

  it("renders login briefing and support handoff", () => {
    render(
      <>
        <NompiloLoginBriefingCard transaction={facilityWalkInTransaction} />
        <NompiloSupportEscalationPanel transaction={facilityWalkInTransaction} />
      </>,
    );
    expect(screen.getByText("Nompilo Login Briefing")).toBeInTheDocument();
    expect(screen.getByText("Nompilo Support and Escalation")).toBeInTheDocument();
  });

  it("does not allow Nompilo to become source of truth", () => {
    expect(
      isNompiloSourceOfTruth({
        intent: "ASK_WORKFLOW_HELP",
        summary: "helper summary",
        authoritativeSource: "NOMPILO_KNOWLEDGE_BASE",
        confidence: "MEDIUM",
        generatedExplanation: "Nompilo composes response from source services.",
      }),
    ).toBe(false);
  });

  it("keeps command surfaces from breaking journey scaffolding", () => {
    render(
      <>
        <CoreTransactionShell transaction={facilityWalkInTransaction} status="ready" />
        <NompiloCommandResultCard transaction={facilityWalkInTransaction} />
      </>,
    );
    expect(screen.getByText("Three Synchronized Journeys")).toBeInTheDocument();
    expect(screen.getAllByText("Nompilo Command Result").length).toBeGreaterThan(0);
  });
});
