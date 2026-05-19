"use client";

import type { ReactNode } from "react";
import type { CoreTransactionView } from "./types";

function panel(title: string, body: ReactNode) {
  return (
    <section className="impilo-surface-card p-5">
      <h3 className="text-sm font-semibold text-[color:var(--text-primary)]">{title}</h3>
      <div className="mt-2 text-sm text-[color:var(--text-secondary)]">{body}</div>
    </section>
  );
}

export function TransactionStateBadge({ state }: { state: string }) {
  return <span className="impilo-chip border-[color:var(--nompilo)]/30 bg-[color:var(--nompilo-soft)] text-[color:var(--nompilo)]">{state}</span>;
}

export function TransactionTypeBadge({ type }: { type: string }) {
  return <span className="impilo-chip">{type}</span>;
}

export function OfflineSyncStatusBadge({ status }: { status: string }) {
  const tone = status.includes("FAILED")
    ? "impilo-status-danger"
    : status.includes("PENDING")
      ? "impilo-status-warning"
      : "impilo-status-success";
  return <span className={`${tone}`}>Sync: {status}</span>;
}

export function TrustContextBanner({ transaction }: { transaction: CoreTransactionView }) {
  return panel("Trust Context", `${transaction.trustContext.accessStatus} (${transaction.trustContext.consentStatus}) for ${transaction.trustContext.purposeOfUse}`);
}

export function ClientIdentityBanner({ transaction }: { transaction: CoreTransactionView }) {
  return panel("Client Identity", `${transaction.clientSummary.clientRef ?? transaction.clientSummary.clientAlias} - ${transaction.clientSummary.identityStatus}`);
}

export function ProviderWorkspaceBanner({ transaction }: { transaction: CoreTransactionView }) {
  return panel("Provider Workspace", `${transaction.providerContext.providerRef ?? transaction.providerContext.actorId} @ ${transaction.facilityContext.facilityId}/${transaction.facilityContext.workspaceId ?? "default"}`);
}

export function QueueStatusCard({ transaction }: { transaction: CoreTransactionView }) {
  return panel("Queue and Task", `Current state: ${transaction.transaction.currentState}`);
}

export function EncounterProgressPanel({ transaction }: { transaction: CoreTransactionView }) {
  return panel("Encounter Progress", `Encounter: ${transaction.clinicalContext.encounterId ?? "Pending encounter creation"}`);
}

export function OrdersAndActionsPanel({ transaction }: { transaction: CoreTransactionView }) {
  return panel("Orders and Actions", `${transaction.clinicalContext.ordersPending ?? 0} order(s) pending`);
}

export function FinancialContextPanel({ transaction }: { transaction: CoreTransactionView }) {
  return panel("Financial Context", `${transaction.financialContext.costingStatus} / ${transaction.financialContext.paymentStatus} / ${transaction.financialContext.claimStatus}`);
}

export function FollowUpPanel({ transaction }: { transaction: CoreTransactionView }) {
  return panel("Follow-up", transaction.followUp.required ? transaction.followUp.note ?? "Follow-up required" : "No follow-up required");
}

export function AuditTracePanel({ transaction }: { transaction: CoreTransactionView }) {
  return panel("Audit Trace", `Correlation: ${transaction.auditSummary.correlationId} (${transaction.auditSummary.sourceSystem})`);
}

export function FailureModePanel({ transaction }: { transaction: CoreTransactionView }) {
  return panel("Failure Modes", transaction.failureModes.length ? transaction.failureModes.join(", ") : "No active failure modes");
}

export function NextActionPanel({ transaction }: { transaction: CoreTransactionView }) {
  return panel("Next Actions", (
    <ul className="list-disc pl-5">
      {transaction.nextActions.map((action) => <li key={action.code}>{action.label}</li>)}
    </ul>
  ));
}

export function TransactionTimeline({ transaction }: { transaction: CoreTransactionView }) {
  return panel("Transaction Timeline", (
    <ol className="space-y-1">
      {transaction.timeline.map((entry) => (
        <li key={`${entry.eventName}-${entry.at}`}>
          <span className="font-medium">{entry.eventName}</span> - {entry.status} ({entry.stateBefore} {"->"} {entry.stateAfter})
        </li>
      ))}
    </ol>
  ));
}

export function ClientJourneyStepper() {
  return panel("Client Journey", "Find Care -> Identify Me -> Select Service -> Cost/Coverage -> Access Service -> Receive Care -> Continue Care -> Give Feedback");
}

export function ProviderJourneyStepper() {
  return panel("Provider Journey", "Start Duty -> See Work -> Open Client Context -> Prepare -> Deliver Care -> Order Actions -> Complete -> Continue Responsibility");
}

export function PlatformJourneyMonitor({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Platform Journey",
    `Current: ${transaction.journeys.platform.currentStage}. Blockers: ${
      transaction.journeys.platform.blockers.length ? transaction.journeys.platform.blockers.join(", ") : "none"
    }`,
  );
}

export function TransactionPlaneMapPanel() {
  return panel(
    "Seven-Plane Transaction Map",
    "Trust | Registry | Clinical | Data | Integration | Experience | Enterprise",
  );
}

export function ThreeJourneyTransactionMap({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Three Synchronized Journeys",
    `${transaction.journeys.person.currentStage} | ${transaction.journeys.provider.currentStage} | ${transaction.journeys.platform.currentStage}`,
  );
}

export function JourneyStageCard({
  label,
  currentStage,
}: {
  label: string;
  currentStage: string;
}) {
  return panel(label, `Current stage: ${currentStage}`);
}

export function JourneyBlockerPanel({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Journey Blockers",
    transaction.failureModes.length ? transaction.failureModes.join(", ") : "No blockers in active journey.",
  );
}

export function TransactionContextPanel({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Transaction Context",
    <div className="space-y-1.5 text-xs">
      <p>
        <span className="font-semibold text-slate-700">Type/State:</span> {transaction.transaction.type} /{" "}
        {transaction.transaction.currentState}
      </p>
      <p>
        <span className="font-semibold text-slate-700">Journeys:</span> {transaction.journeys.person.currentStage} |{" "}
        {transaction.journeys.provider.currentStage} | {transaction.journeys.platform.currentStage}
      </p>
      <p>
        <span className="font-semibold text-slate-700">Trust:</span> {transaction.trustContext.accessStatus} (
        {transaction.trustContext.consentStatus})
      </p>
      <p>
        <span className="font-semibold text-slate-700">Payment/Access:</span> {transaction.financialContext.paymentStatus}
      </p>
      <p>
        <span className="font-semibold text-slate-700">Sync:</span> {transaction.offlineSyncStatus}
      </p>
      <p>
        <span className="font-semibold text-slate-700">Blockers:</span>{" "}
        {transaction.failureModes.length ? transaction.failureModes.join(", ") : "none"}
      </p>
      <p>
        <span className="font-semibold text-slate-700">Source of truth:</span> Experience BFF composition only; sovereign
        services remain authoritative.
      </p>
    </div>,
  );
}

export function JourneyNextActionPanel({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Journey Next Actions",
    <ul className="list-disc pl-5">
      {transaction.nextActions.map((action) => (
        <li key={action.code}>{action.label}</li>
      ))}
    </ul>,
  );
}

export function NompiloCompanionButton() {
  return (
    <button
      type="button"
      className="rounded-full border border-[color:var(--nompilo)]/30 bg-[color:var(--nompilo-soft)] px-3 py-2 text-xs font-semibold text-[color:var(--nompilo)]"
    >
      Nompilo
    </button>
  );
}

export function NompiloInlineHint({ transaction }: { transaction: CoreTransactionView }) {
  const hint = transaction.nompilo.currentGuidance[0]?.message ?? "Nompilo is available for guidance.";
  return panel("Nompilo Inline Hint", hint);
}

export function NompiloJourneyGuide({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Nompilo Journey Guide",
    `Mode: ${transaction.nompilo.mode}. Current guidance count: ${transaction.nompilo.currentGuidance.length}.`,
  );
}

export function NompiloSmartNudge({ transaction }: { transaction: CoreTransactionView }) {
  const nudge = transaction.nompilo.nextBestActions[0]?.label ?? "Review next best action.";
  return panel("Nompilo Smart Nudge", nudge);
}

export function NompiloExplainer({ transaction }: { transaction: CoreTransactionView }) {
  return panel("Nompilo Explainer", transaction.nompilo.privacyNotice.summary);
}

export function NompiloGuidedWorkflowPanel({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Nompilo Guided Workflow",
    `Suggested questions: ${transaction.nompilo.suggestedQuestions.map((q) => q.prompt).join(" | ")}`,
  );
}

export function NompiloAccessibilityAssistPanel({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Nompilo Accessibility Assist",
    transaction.nompilo.accessibilityOptions
      .filter((option) => option.enabled)
      .map((option) => option.code)
      .join(", "),
  );
}

export function NompiloFeedbackPrompt({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Nompilo Feedback Prompt",
    transaction.nompilo.feedbackPrompts.map((prompt) => `${prompt.channel}: ${prompt.prompt}`).join(" | "),
  );
}

export function NompiloHandoffPanel({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Nompilo Handoff",
    transaction.nompilo.handoffOptions.map((option) => option.label).join(", "),
  );
}

export function NompiloProviderAssistPanel({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Nompilo Provider Assist",
    `Provider assist is ${transaction.nompilo.available ? "available" : "unavailable"} for current workflow.`,
  );
}

export function NompiloOperationsInsightPanel({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Nompilo Operations Insight",
    transaction.failureModes.length
      ? `Operations insight: ${transaction.failureModes.join(", ")}`
      : "No operational blocker trend detected.",
  );
}

export function NompiloCompanionPanel({ transaction }: { transaction: CoreTransactionView }) {
  return (
    <div className="grid gap-3 lg:grid-cols-2">
      <NompiloInlineHint transaction={transaction} />
      <NompiloJourneyGuide transaction={transaction} />
      <NompiloSmartNudge transaction={transaction} />
      <NompiloExplainer transaction={transaction} />
      <NompiloGuidedWorkflowPanel transaction={transaction} />
      <NompiloAccessibilityAssistPanel transaction={transaction} />
      <NompiloFeedbackPrompt transaction={transaction} />
      <NompiloHandoffPanel transaction={transaction} />
      <NompiloProviderAssistPanel transaction={transaction} />
      <NompiloOperationsInsightPanel transaction={transaction} />
    </div>
  );
}

export function NompiloGlobalSearchBar({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Ask Nompilo",
    `Surfaces: ${transaction.nompiloCommand.surfaces.join(", ")}. Voice: ${
      transaction.nompiloCommand.voiceEnabled ? "enabled" : "disabled"
    }.`,
  );
}

export function NompiloCommandPalette({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Nompilo Command Palette",
    transaction.nompiloCommand.suggestedCommands.join(", "),
  );
}

export function NompiloVoiceDictationButton({ transaction }: { transaction: CoreTransactionView }) {
  return (
    <button
      type="button"
      className="rounded-full border border-[color:var(--nompilo)]/25 bg-[color:var(--nompilo-soft)] px-3 py-2 text-xs font-semibold text-[color:var(--nompilo)]"
    >
      {transaction.nompiloCommand.dictationEnabled ? "Voice Dictation On" : "Voice Dictation Off"}
    </button>
  );
}

export function NompiloCommandSuggestions({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Nompilo Command Suggestions",
    `${transaction.nompiloCommand.suggestedCommands.join(" | ")} | Recent: ${transaction.nompiloCommand.recentCommands.join(" | ")}`,
  );
}

export function NompiloSearchResultsPanel({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Nompilo Search Results",
    `Sources: ${transaction.nompiloCommand.availableSearchSources.join(", ")}`,
  );
}

export function NompiloServiceFinderResults({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Nompilo Service Finder",
    `Service discovery sources: ${transaction.nompiloCommand.availableSearchSources
      .filter((source) => source.includes("MSIKA") || source.includes("TUSO"))
      .join(", ")}`,
  );
}

export function NompiloProviderFinderResults({ transaction }: { transaction: CoreTransactionView }) {
  return panel("Nompilo Provider Finder", `Role-aware provider support for ${transaction.providerContext.roleCode ?? "PROVIDER"}.`);
}

export function NompiloCommodityFinderResults() {
  return panel("Nompilo Commodity Finder", "Marketplace and commodity alternatives are policy-filtered and role-safe.");
}

export function NompiloWellnessEventResults() {
  return panel("Nompilo Wellness Finder", "Discover marathons, screenings, classes, groups, and community events.");
}

export function NompiloSupportEscalationPanel({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Nompilo Support and Escalation",
    transaction.nompiloCommand.supportHandoff.enabled
      ? `Handoff destination: ${transaction.nompiloCommand.supportHandoff.destination ?? "Support Desk"}`
      : "Support handoff unavailable",
  );
}

export function NompiloFundoLearningAssistant({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Nompilo Fundo Learning Assistant",
    transaction.nompiloCommand.learningCapabilities.join(", "),
  );
}

export function NompiloDashboardAssistant({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Nompilo Dashboard Assistant",
    transaction.nompiloCommand.analyticsCapabilities.join(", "),
  );
}

export function NompiloReportBuilderPanel() {
  return panel("Nompilo Report Builder", "Generate authorised reports, narratives, and briefing summaries.");
}

export function NompiloLoginBriefingCard({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Nompilo Login Briefing",
    `${transaction.nompiloCommand.loginBriefing.headline} | Highlights: ${transaction.nompiloCommand.loginBriefing.highlights.join(", ")}`,
  );
}

export function NompiloCommandResultCard({ transaction }: { transaction: CoreTransactionView }) {
  return panel(
    "Nompilo Command Result",
    `Latest intent context: ${transaction.nompiloCommand.recentCommands[0] ?? "ASK_WORKFLOW_HELP"}. Nompilo composes guidance only; source services remain authoritative for truth.`,
  );
}

export function CoreTransactionDoctrineBanner() {
  return (
    <div className="rounded-3xl border border-[color:var(--primary-muted)] bg-[linear-gradient(90deg,var(--primary-soft),var(--surface-warm))] p-4 text-sm text-[color:var(--text-primary)] shadow-impilo-card">
      Core Transaction Doctrine: every meaningful action is stateful, trusted, auditable, and journey-aligned.
    </div>
  );
}

export function CoreTransactionShell({
  transaction,
  status,
}: {
  transaction?: CoreTransactionView;
  status: "loading" | "empty" | "error" | "permission-denied" | "ready";
}) {
  if (status === "loading") return <p className="text-sm text-[color:var(--text-secondary)]">Loading core transaction...</p>;
  if (status === "empty") return <p className="text-sm text-[color:var(--text-secondary)]">No transaction selected yet.</p>;
  if (status === "error") return <p className="text-sm text-[color:var(--danger)]">Unable to load transaction. Retry or contact support.</p>;
  if (status === "permission-denied") return <p className="text-sm text-amber-700">You do not have permission for this transaction context.</p>;
  if (!transaction) return null;
  if (transaction.offlineSyncStatus === "SYNC_PENDING") {
    return (
      <div className="space-y-4">
        <CoreTransactionDoctrineBanner />
        <p className="text-sm text-amber-700">
          Offline capture detected. This transaction is pending sync and remains auditable.
        </p>
        <FailureModePanel transaction={transaction} />
        <TransactionTimeline transaction={transaction} />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <CoreTransactionDoctrineBanner />
      <div className="flex flex-wrap gap-2">
        <TransactionTypeBadge type={transaction.transaction.type} />
        <TransactionStateBadge state={transaction.transaction.currentState} />
        <OfflineSyncStatusBadge status={transaction.offlineSyncStatus} />
        <NompiloCompanionButton />
      </div>
      <div className="grid gap-3 lg:grid-cols-2">
        <TransactionContextPanel transaction={transaction} />
        <ClientIdentityBanner transaction={transaction} />
        <ProviderWorkspaceBanner transaction={transaction} />
        <TrustContextBanner transaction={transaction} />
        <QueueStatusCard transaction={transaction} />
        <EncounterProgressPanel transaction={transaction} />
        <OrdersAndActionsPanel transaction={transaction} />
        <FinancialContextPanel transaction={transaction} />
        <FollowUpPanel transaction={transaction} />
        <AuditTracePanel transaction={transaction} />
        <FailureModePanel transaction={transaction} />
        <JourneyStageCard label="Person Journey Stage" currentStage={transaction.journeys.person.currentStage} />
        <JourneyStageCard label="Provider Journey Stage" currentStage={transaction.journeys.provider.currentStage} />
        <JourneyStageCard label="Platform Journey Stage" currentStage={transaction.journeys.platform.currentStage} />
      </div>
      <NextActionPanel transaction={transaction} />
      <JourneyNextActionPanel transaction={transaction} />
      <JourneyBlockerPanel transaction={transaction} />
      <ThreeJourneyTransactionMap transaction={transaction} />
      <TransactionTimeline transaction={transaction} />
      <PlatformJourneyMonitor transaction={transaction} />
      <NompiloCompanionPanel transaction={transaction} />
      <NompiloGlobalSearchBar transaction={transaction} />
      <NompiloCommandPalette transaction={transaction} />
      <NompiloCommandSuggestions transaction={transaction} />
      <NompiloSearchResultsPanel transaction={transaction} />
      <NompiloServiceFinderResults transaction={transaction} />
      <NompiloProviderFinderResults transaction={transaction} />
      <NompiloCommodityFinderResults />
      <NompiloWellnessEventResults />
      <NompiloSupportEscalationPanel transaction={transaction} />
      <NompiloFundoLearningAssistant transaction={transaction} />
      <NompiloDashboardAssistant transaction={transaction} />
      <NompiloReportBuilderPanel />
      <NompiloLoginBriefingCard transaction={transaction} />
      <NompiloCommandResultCard transaction={transaction} />
      <NompiloVoiceDictationButton transaction={transaction} />
      <TransactionPlaneMapPanel />
    </div>
  );
}
