import type {
  CoreJourneyType,
  CoreTransactionState,
  CoreTransactionType,
  PersonJourneyStage,
  PlatformJourneyStage,
  ProviderJourneyStage,
} from "./types";

const FLOW: CoreTransactionState[] = [
  "DRAFT",
  "INITIATED",
  "IDENTITY_PENDING",
  "IDENTITY_RESOLVED",
  "TRUST_CONTEXT_ESTABLISHED",
  "SERVICE_SELECTED",
  "COSTING_REQUIRED",
  "COST_ESTIMATED",
  "COVERAGE_CHECK_PENDING",
  "COVERAGE_CONFIRMED",
  "EXEMPTION_CONFIRMED",
  "PRE_SERVICE_PAYMENT_REQUIRED",
  "PRE_SERVICE_PAYMENT_PENDING",
  "PRE_SERVICE_PAYMENT_COMPLETED",
  "ACCESS_GRANTED",
  "SCHEDULED",
  "QUEUED",
  "TASKED",
  "TRIAGE_IN_PROGRESS",
  "READY_FOR_PROVIDER",
  "IN_SERVICE",
  "ORDERS_PENDING",
  "ANCILLARY_IN_PROGRESS",
  "PROVIDER_REVIEW_PENDING",
  "POST_SERVICE_BILLING_PENDING",
  "FINANCIAL_PROCESSING",
  "CLIENT_INSTRUCTIONS_PENDING",
  "CLINICAL_COMPLETION_PENDING",
  "SHR_UPDATE_PENDING",
  "FOLLOW_UP_ACTIVE",
  "CLAIM_PENDING",
  "RECONCILIATION_PENDING",
  "COMPLETED",
  "CLOSED",
];

const TERMINAL: CoreTransactionState[] = ["COMPLETED", "CLOSED", "CANCELLED", "NO_SHOW"];

export function getAllowedNextStates(
  from: CoreTransactionState,
  transactionType: CoreTransactionType,
): CoreTransactionState[] {
  if (TERMINAL.includes(from)) return [];
  const index = FLOW.indexOf(from);
  const defaultNext = index >= 0 ? [FLOW[index + 1]] : [];
  const branches: CoreTransactionState[] = [
    "CANCELLED",
    "NO_SHOW",
    "REFERRED_OUT",
    "TRANSFERRED",
    "PENDING_SYNC",
    "FAILED_SYNC",
    "SERVICE_DEFERRED",
    "ACCESS_DENIED",
    "CONSENT_DENIED",
  ];
  if (from === "PRE_SERVICE_PAYMENT_REQUIRED") {
    return ["PRE_SERVICE_PAYMENT_PENDING", "EXEMPTION_CONFIRMED", "ACCESS_BLOCKED_PAYMENT_REQUIRED"];
  }
  if (from === "PRE_SERVICE_PAYMENT_PENDING") {
    return ["PRE_SERVICE_PAYMENT_COMPLETED", "PRE_SERVICE_PAYMENT_FAILED", "PENDING_PAYMENT"];
  }
  if (transactionType === "EMERGENCY" && from === "INITIATED") {
    return ["EMERGENCY_OVERRIDE", "IN_SERVICE", "PROVISIONAL_IDENTITY", ...branches];
  }
  return [...defaultNext.filter(Boolean), ...branches];
}

export function isValidCoreTransactionTransition(
  from: CoreTransactionState,
  to: CoreTransactionState,
  type: CoreTransactionType,
): boolean {
  return getAllowedNextStates(from, type).includes(to);
}

export function isTerminalCoreTransactionState(state: CoreTransactionState): boolean {
  return TERMINAL.includes(state);
}

export function getPersonJourneyStageForTransactionState(state: CoreTransactionState): PersonJourneyStage {
  if (["DRAFT", "INITIATED"].includes(state)) return "FIND_CARE";
  if (["IDENTITY_PENDING", "IDENTITY_RESOLVED", "PROVISIONAL_IDENTITY"].includes(state)) return "IDENTIFY_ME";
  if (state === "SERVICE_SELECTED") return "SELECT_SERVICE";
  if (["COSTING_REQUIRED", "COST_ESTIMATED", "COVERAGE_CHECK_PENDING", "COVERAGE_CONFIRMED", "EXEMPTION_CONFIRMED"].includes(state)) {
    return "CONFIRM_COST_COVERAGE_OR_EXEMPTION";
  }
  if (["PRE_SERVICE_PAYMENT_REQUIRED", "PRE_SERVICE_PAYMENT_PENDING", "PRE_SERVICE_PAYMENT_FAILED"].includes(state)) {
    return "PAY_UPFRONT_WHERE_REQUIRED";
  }
  if (["PRE_SERVICE_PAYMENT_COMPLETED", "ACCESS_GRANTED", "SCHEDULED", "QUEUED", "TASKED"].includes(state)) {
    return "ACCESS_SERVICE";
  }
  if (["TRIAGE_IN_PROGRESS", "READY_FOR_PROVIDER"].includes(state)) return "PREPARE_FOR_CARE";
  if (["IN_SERVICE", "ORDERS_PENDING", "ANCILLARY_IN_PROGRESS", "PROVIDER_REVIEW_PENDING"].includes(state)) return "RECEIVE_CARE";
  if (["FINANCIAL_PROCESSING", "POST_SERVICE_BILLING_PENDING", "CLAIM_PENDING", "RECONCILIATION_PENDING", "PENDING_PAYMENT", "PENDING_CLAIM", "PAYMENT_FAILED", "CLAIM_REJECTED"].includes(state)) {
    return "SETTLE_ADDITIONAL_CHARGES_OR_CLAIMS";
  }
  if (["CLIENT_INSTRUCTIONS_PENDING", "CLINICAL_COMPLETION_PENDING", "SHR_UPDATE_PENDING"].includes(state)) return "KNOW_WHAT_HAPPENED";
  if (["FOLLOW_UP_ACTIVE", "REFERRED_OUT"].includes(state)) return "CONTINUE_CARE";
  return "GIVE_FEEDBACK";
}

export function getProviderJourneyStageForTransactionState(state: CoreTransactionState): ProviderJourneyStage {
  if (["DRAFT", "INITIATED"].includes(state)) return "START_DUTY";
  if (["SCHEDULED", "QUEUED", "TASKED"].includes(state)) return "SEE_MY_WORK";
  if (["IDENTITY_PENDING", "IDENTITY_RESOLVED", "PROVISIONAL_IDENTITY"].includes(state)) return "OPEN_CLIENT_CONTEXT";
  if (["TRUST_CONTEXT_ESTABLISHED", "ACCESS_GRANTED", "ACCESS_BLOCKED_PAYMENT_REQUIRED"].includes(state)) return "CONFIRM_SERVICE_ACCESS_STATUS";
  if (["TRIAGE_IN_PROGRESS", "READY_FOR_PROVIDER"].includes(state)) return "PREPARE_TRIAGE_OR_SCREEN";
  if (["IN_SERVICE", "EMERGENCY_OVERRIDE"].includes(state)) return "DELIVER_CARE";
  if (["ORDERS_PENDING", "ANCILLARY_IN_PROGRESS", "PENDING_RESULT"].includes(state)) return "ORDER_ACTIONS";
  if (["PROVIDER_REVIEW_PENDING", "CLINICAL_COMPLETION_PENDING", "SHR_UPDATE_PENDING"].includes(state)) return "COMPLETE_TRANSACTION";
  if (["FINANCIAL_PROCESSING", "POST_SERVICE_BILLING_PENDING", "CLAIM_PENDING", "PENDING_CLAIM", "PENDING_PAYMENT"].includes(state)) {
    return "CONFIRM_CHARGES_CLAIMS_OR_FOLLOW_UP";
  }
  return "CONTINUE_RESPONSIBILITY";
}

export function getPlatformJourneyStageForTransactionState(state: CoreTransactionState): PlatformJourneyStage {
  if (state === "INITIATED") return "RECEIVE_TRIGGER";
  if (["IDENTITY_PENDING", "IDENTITY_RESOLVED", "PROVISIONAL_IDENTITY"].includes(state)) return "RESOLVE_IDENTITY";
  if (["TRUST_CONTEXT_ESTABLISHED", "CONSENT_DENIED", "ACCESS_DENIED"].includes(state)) return "ESTABLISH_TRUST_CONTEXT";
  if (["SERVICE_SELECTED", "SCHEDULED", "QUEUED", "TASKED"].includes(state)) return "RESOLVE_SERVICE_AND_WORKFLOW";
  if (["COSTING_REQUIRED", "COST_ESTIMATED", "COVERAGE_CHECK_PENDING", "COVERAGE_CONFIRMED", "EXEMPTION_CONFIRMED"].includes(state)) return "DETERMINE_COSTING_COVERAGE_OR_PAYMENT_RULES";
  if (["PRE_SERVICE_PAYMENT_REQUIRED", "PRE_SERVICE_PAYMENT_PENDING", "PRE_SERVICE_PAYMENT_COMPLETED", "PRE_SERVICE_PAYMENT_FAILED", "ACCESS_BLOCKED_PAYMENT_REQUIRED"].includes(state)) return "APPLY_PRE_SERVICE_PAYMENT_GATE";
  if (["ACCESS_GRANTED", "TRIAGE_IN_PROGRESS", "READY_FOR_PROVIDER"].includes(state)) return "MANAGE_STATE_MACHINE";
  if (state === "IN_SERVICE") return "COMPOSE_EXPERIENCE_VIEW";
  if (["ORDERS_PENDING", "ANCILLARY_IN_PROGRESS", "PROVIDER_REVIEW_PENDING"].includes(state)) return "EXECUTE_CLINICAL_OR_SERVICE_ACTION";
  if (["FINANCIAL_PROCESSING", "POST_SERVICE_BILLING_PENDING", "CLAIM_PENDING", "RECONCILIATION_PENDING", "PENDING_PAYMENT", "PENDING_CLAIM"].includes(state)) return "EXECUTE_FINANCIAL_OR_ENTERPRISE_FLOW";
  if (["CLIENT_INSTRUCTIONS_PENDING", "CLINICAL_COMPLETION_PENDING", "SHR_UPDATE_PENDING", "FOLLOW_UP_ACTIVE"].includes(state)) return "UPDATE_RECORD_AND_CONTINUITY";
  if (["COMPLETED", "CLOSED"].includes(state)) return "EMIT_EVENTS_AND_AUDIT";
  if (["PENDING_RECONCILIATION", "PENDING_SYNC", "FAILED_SYNC"].includes(state)) return "HANDLE_FAILURE_OFFLINE_AND_RECONCILIATION";
  return "FEED_REPORTING_AND_INTELLIGENCE";
}

export function getCurrentJourneyStepStatus(
  state: CoreTransactionState,
  failureModes: string[],
  journeyType: CoreJourneyType,
): "COMPLETED" | "CURRENT" | "PENDING" | "BLOCKED" | "FAILED" {
  if (failureModes.length > 0) return "BLOCKED";
  if (isTerminalCoreTransactionState(state)) return "COMPLETED";
  if (["PRE_SERVICE_PAYMENT_FAILED", "FAILED_SYNC", "PAYMENT_FAILED", "CLAIM_REJECTED"].includes(state)) {
    return "FAILED";
  }
  if (journeyType === "PLATFORM" && state === "PENDING_SYNC") return "PENDING";
  return "CURRENT";
}
