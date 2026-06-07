import type { ActorType, PurposeOfUse } from "./health-os-identifiers";

export type CoreJourneyType = "PERSON" | "PROVIDER" | "PLATFORM";

export type PersonJourneyStage =
  | "FIND_CARE"
  | "IDENTIFY_ME"
  | "SELECT_SERVICE"
  | "CONFIRM_COST_COVERAGE_OR_EXEMPTION"
  | "PAY_UPFRONT_WHERE_REQUIRED"
  | "ACCESS_SERVICE"
  | "PREPARE_FOR_CARE"
  | "RECEIVE_CARE"
  | "SETTLE_ADDITIONAL_CHARGES_OR_CLAIMS"
  | "KNOW_WHAT_HAPPENED"
  | "CONTINUE_CARE"
  | "GIVE_FEEDBACK";

export type ProviderJourneyStage =
  | "START_DUTY"
  | "SEE_MY_WORK"
  | "OPEN_CLIENT_CONTEXT"
  | "CONFIRM_SERVICE_ACCESS_STATUS"
  | "PREPARE_TRIAGE_OR_SCREEN"
  | "DELIVER_CARE"
  | "ORDER_ACTIONS"
  | "COMPLETE_TRANSACTION"
  | "CONFIRM_CHARGES_CLAIMS_OR_FOLLOW_UP"
  | "CONTINUE_RESPONSIBILITY";

export type PlatformJourneyStage =
  | "RECEIVE_TRIGGER"
  | "RESOLVE_IDENTITY"
  | "ESTABLISH_TRUST_CONTEXT"
  | "RESOLVE_SERVICE_AND_WORKFLOW"
  | "DETERMINE_COSTING_COVERAGE_OR_PAYMENT_RULES"
  | "APPLY_PRE_SERVICE_PAYMENT_GATE"
  | "MANAGE_STATE_MACHINE"
  | "COMPOSE_EXPERIENCE_VIEW"
  | "EXECUTE_CLINICAL_OR_SERVICE_ACTION"
  | "EXECUTE_FINANCIAL_OR_ENTERPRISE_FLOW"
  | "UPDATE_RECORD_AND_CONTINUITY"
  | "EMIT_EVENTS_AND_AUDIT"
  | "FEED_REPORTING_AND_INTELLIGENCE"
  | "HANDLE_FAILURE_OFFLINE_AND_RECONCILIATION";

export type CoreJourneyStage = PersonJourneyStage | ProviderJourneyStage | PlatformJourneyStage;

export type CoreTransactionState =
  | "DRAFT"
  | "INITIATED"
  | "IDENTITY_PENDING"
  | "IDENTITY_RESOLVED"
  | "TRUST_CONTEXT_ESTABLISHED"
  | "SERVICE_SELECTED"
  | "COSTING_REQUIRED"
  | "COST_ESTIMATED"
  | "COVERAGE_CHECK_PENDING"
  | "COVERAGE_CONFIRMED"
  | "EXEMPTION_CONFIRMED"
  | "PRE_SERVICE_PAYMENT_REQUIRED"
  | "PRE_SERVICE_PAYMENT_PENDING"
  | "PRE_SERVICE_PAYMENT_COMPLETED"
  | "PRE_SERVICE_PAYMENT_FAILED"
  | "ACCESS_GRANTED"
  | "ACCESS_BLOCKED_PAYMENT_REQUIRED"
  | "SCHEDULED"
  | "QUEUED"
  | "TASKED"
  | "TRIAGE_IN_PROGRESS"
  | "READY_FOR_PROVIDER"
  | "IN_SERVICE"
  | "ORDERS_PENDING"
  | "ANCILLARY_IN_PROGRESS"
  | "PROVIDER_REVIEW_PENDING"
  | "FINANCIAL_PROCESSING"
  | "POST_SERVICE_BILLING_PENDING"
  | "CLIENT_INSTRUCTIONS_PENDING"
  | "CLINICAL_COMPLETION_PENDING"
  | "SHR_UPDATE_PENDING"
  | "FOLLOW_UP_ACTIVE"
  | "CLAIM_PENDING"
  | "RECONCILIATION_PENDING"
  | "COMPLETED"
  | "CLOSED"
  | "CANCELLED"
  | "NO_SHOW"
  | "REFERRED_OUT"
  | "TRANSFERRED"
  | "ADMITTED"
  | "EMERGENCY_OVERRIDE"
  | "PROVISIONAL_IDENTITY"
  | "PENDING_RECONCILIATION"
  | "PENDING_PAYMENT"
  | "PENDING_CLAIM"
  | "PENDING_RESULT"
  | "PENDING_SIGNATURE"
  | "PENDING_SYNC"
  | "FAILED_SYNC"
  | "DUPLICATE_SUSPECTED"
  | "CONSENT_DENIED"
  | "ACCESS_DENIED"
  | "PAYMENT_FAILED"
  | "CLAIM_REJECTED"
  | "SERVICE_DEFERRED";

export type CoreTransactionType =
  | "FACILITY_WALK_IN"
  | "APPOINTMENT"
  | "EMERGENCY"
  | "TELEMEDICINE"
  | "REFERRAL"
  | "CHRONIC_CARE"
  | "PHARMACY"
  | "LABORATORY"
  | "IMAGING"
  | "COMMUNITY_OUTREACH"
  | "MARKETPLACE"
  | "ADMINISTRATIVE_HEALTH"
  | "WELLNESS"
  | "TRAINING_OR_COMPETENCY"
  | "SUPERVISION_OR_MENTORSHIP"
  | "BLOOD_DONATION"
  | "BLOOD_ORDER"
  | "TRANSFUSION"
  | "HAEMOVIGILANCE";

export type CoreTransactionLifecycleStage =
  | "NEED_OR_TRIGGER"
  | "ENTRY"
  | "IDENTITY"
  | "TRUST_AND_CONSENT"
  | "SERVICE_SELECTION"
  | "COST_AND_COVERAGE"
  | "SCHEDULING_OR_QUEUE"
  | "TRIAGE_OR_ELIGIBILITY"
  | "PROVIDER_ASSIGNMENT"
  | "ENCOUNTER_OR_DELIVERY"
  | "ORDERS_AND_ANCILLARY"
  | "FINANCIAL_SETTLEMENT"
  | "RECORD_CONTRIBUTION"
  | "INSTRUCTIONS_AND_NOTIFICATIONS"
  | "FOLLOW_UP_CONTINUITY"
  | "REPORTING_ANALYTICS_AUDIT"
  | "COMPLETION";

export type CoreTransactionActorType =
  | ActorType
  | "COMMUNITY_ACTOR"
  | "DIGITAL_SERVICE"
  | "CLIENT"
  | "PROVIDER"
  | "PLATFORM_OPERATOR";

export interface CoreTransactionActor {
  actorId: string;
  actorType: CoreTransactionActorType;
  providerRef?: string;
  displayName?: string;
}

export type CoreTransactionEventName =
  | "core.transaction.initiated"
  | "core.identity.resolution.requested"
  | "core.identity.resolved"
  | "core.identity.provisional.created"
  | "core.trust.context.established"
  | "core.consent.checked"
  | "core.consent.granted"
  | "core.consent.denied"
  | "core.service.selected"
  | "core.costing.required"
  | "core.cost.estimated"
  | "core.coverage.check.requested"
  | "core.coverage.confirmed"
  | "core.exemption.confirmed"
  | "core.pre_service_payment.required"
  | "core.pre_service_payment.initiated"
  | "core.pre_service_payment.completed"
  | "core.pre_service_payment.failed"
  | "core.access.granted"
  | "core.access.blocked.payment_required"
  | "core.appointment.created"
  | "core.queue.joined"
  | "core.triage.started"
  | "core.triage.completed"
  | "core.provider.assigned"
  | "core.encounter.started"
  | "core.encounter.updated"
  | "core.order.created"
  | "core.order.completed"
  | "core.result.available"
  | "core.prescription.created"
  | "core.dispense.completed"
  | "core.referral.created"
  | "core.referral.accepted"
  | "core.referral.completed"
  | "core.invoice.generated"
  | "core.payment.initiated"
  | "core.payment.completed"
  | "core.claim.submitted"
  | "core.claim.adjudicated"
  | "core.client.instructions.generated"
  | "core.shr.update.requested"
  | "core.shr.updated"
  | "core.followup.created"
  | "core.notification.sent"
  | "core.feedback.requested"
  | "core.feedback.submitted"
  | "core.nompilo.guidance.requested"
  | "core.nompilo.guidance.delivered"
  | "core.nompilo.handoff.requested"
  | "core.transaction.completed"
  | "core.transaction.closed"
  | "core.transaction.audit.flagged";

export type CoreTransactionFailureMode =
  | "IDENTITY_NOT_FOUND"
  | "DUPLICATE_SUSPECTED"
  | "CONSENT_DENIED"
  | "PROVIDER_NOT_AUTHORISED"
  | "DEVICE_NOT_TRUSTED"
  | "FACILITY_CONTEXT_MISSING"
  | "WORKSPACE_NOT_SELECTED"
  | "SERVICE_UNAVAILABLE"
  | "PROVIDER_UNAVAILABLE"
  | "PRE_SERVICE_PAYMENT_REQUIRED"
  | "PRE_SERVICE_PAYMENT_FAILED"
  | "PAYMENT_FAILED"
  | "CLAIM_REJECTED"
  | "LAB_RESULT_DELAYED"
  | "REFERRAL_REJECTED"
  | "OFFLINE_SYNC_PENDING"
  | "SYNC_FAILED"
  | "DOCUMENTATION_INCOMPLETE"
  | "NO_SHOW"
  | "EMERGENCY_OVERRIDE_REQUIRED"
  | "ACCESSIBILITY_SUPPORT_UNAVAILABLE"
  | "NOMPILO_HUMAN_HANDOFF_REQUIRED"
  | "AUDIT_ANOMALY_DETECTED";

export type CoreTransactionOfflineSyncStatus =
  | "ONLINE"
  | "OFFLINE_CAPTURED"
  | "SYNC_PENDING"
  | "SYNC_IN_PROGRESS"
  | "SYNC_FAILED"
  | "SYNC_RECONCILED";

export type NompiloSurfaceType =
  | "FLOATING_ASSISTANT"
  | "INLINE_HINT"
  | "JOURNEY_STEP_GUIDE"
  | "SMART_NUDGE"
  | "EXPLAINER"
  | "GUIDED_WORKFLOW"
  | "ACCESSIBILITY_ASSIST"
  | "FEEDBACK_PROMPT"
  | "HUMAN_HANDOFF"
  | "PROVIDER_WORKFLOW_ASSIST"
  | "PLATFORM_OPERATIONS_INSIGHT";

export type NompiloCompanionMode =
  | "PASSIVE_AVAILABLE"
  | "CONTEXTUAL_COMPANION"
  | "STEP_BY_STEP_GUIDE"
  | "ACCESSIBILITY_SUPPORT"
  | "FEEDBACK_CAPTURE"
  | "HUMAN_HANDOFF"
  | "OPERATIONS_INSIGHT";

export type NompiloFeedbackChannel =
  | "IN_APP"
  | "WEB_PORTAL"
  | "SMS"
  | "WHATSAPP_STYLE_MESSAGE"
  | "USSD"
  | "KIOSK"
  | "CALL_CENTRE"
  | "EMAIL"
  | "COMMUNITY_HEALTH_WORKER_ASSISTED"
  | "FACILITY_FEEDBACK_DESK";

export type NompiloJourneySupportLevel = "LOW" | "MODERATE" | "HIGH" | "CRITICAL";

export type NompiloCommandSurface =
  | "GLOBAL_SEARCH_BAR"
  | "COMMAND_PALETTE"
  | "FLOATING_ASSISTANT"
  | "VOICE_DICTATION"
  | "INLINE_JOURNEY_PROMPT"
  | "FUNDO_LEARNING_ASSISTANT"
  | "DASHBOARD_ASSISTANT"
  | "PROVIDER_WORKSPACE_ASSISTANT"
  | "CLIENT_APP_ASSISTANT"
  | "KIOSK_ASSISTANT"
  | "SUPPORT_HELP_INTERFACE"
  | "MOBILE_CHAT"
  | "MOBILE_VOICE";

export type NompiloSearchScope =
  | "TRANSACTION_CONTEXT"
  | "TENANT"
  | "POD"
  | "FACILITY"
  | "WORKSPACE"
  | "ROLE_BOUNDARY"
  | "APPROVED_EXTERNAL";

export type NompiloSearchSource =
  | "VITO_CLIENT_REGISTRY"
  | "VARAPI_PROVIDER_REGISTRY"
  | "TUSO_FACILITY_REGISTRY"
  | "MSIKA_SERVICE_PRODUCT_CATALOGUE"
  | "MSIKA_FLOW_FULFILMENT"
  | "INDAWO_PUBLIC_HEALTH_SITES"
  | "ZIBO_TERMINOLOGY"
  | "FUNDO_LEARNING"
  | "BUTANO_CLINICAL_SUMMARY"
  | "COSTA_COSTING"
  | "MUSHEX_PAYMENTS_CLAIMS"
  | "DATA_PLANE_ANALYTICS"
  | "DOCUMENT_SERVICE"
  | "SUPPORT_HELPDESK"
  | "NOMPILO_KNOWLEDGE_BASE"
  | "APPROVED_EXTERNAL_SOURCE"
  | "GOVERNED_INTERNET_SEARCH";

export type NompiloCommandIntent =
  | "FIND_SERVICE"
  | "FIND_PROVIDER"
  | "FIND_FACILITY"
  | "FIND_COMMODITY"
  | "FIND_WELLNESS_EVENT"
  | "ASK_HEALTH_SYSTEM_QUESTION"
  | "ASK_WORKFLOW_HELP"
  | "ASK_CLINICAL_EXPLAINER"
  | "ASK_PAYMENT_OR_CLAIM_HELP"
  | "REQUEST_SUPPORT"
  | "REQUEST_HUMAN_HANDOFF"
  | "ASK_FUNDO_HELP"
  | "REQUEST_QUIZ"
  | "REQUEST_SUMMARY"
  | "REQUEST_REPORT"
  | "REQUEST_DASHBOARD"
  | "REQUEST_ANALYSIS"
  | "REQUEST_LOGIN_BRIEFING"
  | "DICTATE_NEED"
  | "NAVIGATE_TO_SCREEN"
  | "START_TRANSACTION"
  | "CHECK_STATUS"
  | "GIVE_FEEDBACK";

export interface NompiloSuggestedQuestion {
  code: string;
  prompt: string;
}

export interface NompiloNextBestAction {
  code: string;
  label: string;
  targetRoute?: string;
}

export interface NompiloAccessibilityOption {
  code:
    | "SIMPLE_LANGUAGE"
    | "READ_ALOUD_AUDIO_GUIDANCE"
    | "VOICE_INPUT"
    | "KEYBOARD_NAVIGATION"
    | "SCREEN_READER_FRIENDLY_GUIDANCE"
    | "LARGE_TEXT"
    | "HIGH_CONTRAST"
    | "REDUCED_MOTION"
    | "CAREGIVER_PROXY_ASSISTANCE"
    | "STEP_BY_STEP_MODE"
    | "LOW_BANDWIDTH_MODE"
    | "MULTILINGUAL_EXPANSION";
  enabled: boolean;
  description: string;
}

export interface NompiloFeedbackPrompt {
  id: string;
  channel: NompiloFeedbackChannel;
  prompt: string;
  stage?: CoreJourneyStage;
}

export interface NompiloHandoffOption {
  id: string;
  label: string;
  destination: "CARE_TEAM" | "FACILITY_HELP_DESK" | "CALL_CENTRE" | "OPERATIONS_DESK";
}

export interface NompiloPrivacyNotice {
  summary: string;
  consentReference?: string;
}

export interface NompiloGuidance {
  id: string;
  message: string;
  tone: "CALM" | "NEUTRAL" | "URGENT";
  surfaceType: NompiloSurfaceType;
}

export interface NompiloCompanionContext {
  contextId: string;
  available: boolean;
  mode: NompiloCompanionMode;
  supportLevel: NompiloJourneySupportLevel;
  currentGuidance: NompiloGuidance[];
  suggestedQuestions: NompiloSuggestedQuestion[];
  nextBestActions: NompiloNextBestAction[];
  accessibilityOptions: NompiloAccessibilityOption[];
  feedbackPrompts: NompiloFeedbackPrompt[];
  handoffOptions: NompiloHandoffOption[];
  privacyNotice: NompiloPrivacyNotice;
  confidence: "LOW" | "MEDIUM" | "HIGH";
}

export interface NompiloToolPermission {
  source: NompiloSearchSource;
  allowed: boolean;
  reason?: string;
}

export interface NompiloToolInvocation {
  source: NompiloSearchSource;
  scope: NompiloSearchScope;
  query: string;
  invokedAt: string;
  policyReference?: string;
}

export interface NompiloVoiceInputContext {
  voiceEnabled: boolean;
  dictationEnabled: boolean;
  locale: string;
  transcript?: string;
}

export interface NompiloGlobalSearchResult {
  id: string;
  title: string;
  description: string;
  source: NompiloSearchSource;
  route?: string;
  confidence: "LOW" | "MEDIUM" | "HIGH";
}

export interface NompiloServiceFinderResult {
  serviceCode: string;
  serviceName: string;
  facilityId?: string;
  availability?: string;
  paymentGuidance?: string;
}

export interface NompiloProviderFinderResult {
  providerRef: string;
  displayName: string;
  cadre: string;
  facilityId?: string;
  availability?: string;
}

export interface NompiloCommodityFinderResult {
  commodityCode: string;
  name: string;
  availabilityStatus: "AVAILABLE" | "LIMITED" | "UNAVAILABLE";
  alternative?: string;
}

export interface NompiloWellnessEventResult {
  id: string;
  name: string;
  eventType: string;
  location: string;
  schedule: string;
}

export interface NompiloSupportTicketRequest {
  category: string;
  summary: string;
  transactionId?: string;
  priority?: "LOW" | "MEDIUM" | "HIGH" | "URGENT";
}

export interface NompiloFundoLearningAssist {
  moduleId: string;
  moduleTitle: string;
  assistMode: "EXPLAIN" | "QUIZ" | "REFRESHER" | "ASSESSMENT_PREP";
}

export interface NompiloAnalyticsRequest {
  question: string;
  indicatorIds?: string[];
}

export interface NompiloReportRequest {
  title: string;
  reportType: "OPERATIONAL" | "PROGRAMME" | "FINANCE" | "QUALITY";
}

export interface NompiloDashboardRequest {
  dashboardId: string;
  filters?: Record<string, string>;
}

export interface NompiloLoginBriefing {
  role: CoreTransactionActorType;
  headline: string;
  highlights: string[];
  urgentItems: string[];
}

export interface NompiloCommandResult {
  intent: NompiloCommandIntent;
  summary: string;
  authoritativeSource: NompiloSearchSource;
  confidence: "LOW" | "MEDIUM" | "HIGH";
  systemData?: Record<string, unknown>;
  registryData?: Record<string, unknown>;
  clinicalData?: Record<string, unknown>;
  userProvidedText?: string;
  generatedExplanation?: string;
  externalSourceInformation?: string;
  uncertainInformation?: string;
}

export interface NompiloCommandContext {
  enabled: boolean;
  surfaces: NompiloCommandSurface[];
  availableSearchSources: NompiloSearchSource[];
  suggestedCommands: NompiloCommandIntent[];
  recentCommands: NompiloCommandIntent[];
  voiceEnabled: boolean;
  dictationEnabled: boolean;
  toolPermissions: NompiloToolPermission[];
  loginBriefing: NompiloLoginBriefing;
  supportHandoff: {
    enabled: boolean;
    queue?: string;
    destination?: string;
  };
  analyticsCapabilities: string[];
  learningCapabilities: string[];
}

export interface CoreTransactionClientContext {
  clientRef?: string;
  clientAlias?: string;
  provisionalIdentity: boolean;
}

export interface CoreTransactionProviderContext {
  providerRef?: string;
  providerId?: string;
  roleCode?: string;
  workspaceRole?: string;
}

export interface CoreTransactionFacilityContext {
  facilityId: string;
  workspaceId?: string;
  siteRef?: string;
}

export interface CoreTransactionTrustContext {
  purposeOfUse: PurposeOfUse | "CARE_COORDINATION";
  consentRequired: boolean;
  consentGranted?: boolean;
  consentReference?: string;
  emergencyOverrideUsed: boolean;
  breakGlassReference?: string;
  accessDecision?: "ALLOW" | "DENY" | "STEP_UP_REQUIRED";
}

export interface CoreTransactionRegistryContext {
  serviceCode: string;
  serviceName?: string;
  terminologySystem?: string;
  terminologyCode?: string;
}

export interface CoreTransactionClinicalContext {
  encounterId?: string;
  orderRefs: string[];
  resultRefs: string[];
  shrContributionPending: boolean;
}

export interface CoreTransactionFinancialContext {
  costingStatus: "NOT_APPLICABLE" | "PENDING" | "CALCULATED";
  billingStatus: "NOT_APPLICABLE" | "PENDING" | "INVOICED";
  paymentStatus:
    | "NOT_APPLICABLE"
    | "PENDING"
    | "PAID"
    | "FAILED"
    | "EXEMPT"
    | "COVERED"
    | "PRE_SERVICE_REQUIRED";
  claimStatus: "NOT_APPLICABLE" | "PENDING" | "SUBMITTED" | "APPROVED" | "REJECTED";
}

export interface CoreTransactionFollowUpContext {
  followUpRequired: boolean;
  nextFollowUpDate?: string;
  referralRequired?: boolean;
}

export interface CoreTransactionAuditContext {
  correlationId: string;
  requestId?: string;
  sourceSystem: string;
  deviceFingerprint?: string;
  auditRequired: boolean;
}

export interface CoreTransactionContext {
  tenantId: string;
  podId: string;
  facilityContext: CoreTransactionFacilityContext;
  clientContext: CoreTransactionClientContext;
  providerContext?: CoreTransactionProviderContext;
  trustContext: CoreTransactionTrustContext;
  registryContext: CoreTransactionRegistryContext;
  clinicalContext?: CoreTransactionClinicalContext;
  financialContext?: CoreTransactionFinancialContext;
  followUpContext?: CoreTransactionFollowUpContext;
  auditContext: CoreTransactionAuditContext;
}

export interface CoreTransactionPermission {
  code: string;
  allowed: boolean;
  reason?: string;
}

export interface CoreTransactionNextAction {
  code: string;
  label: string;
  requiresPermission: string;
  targetState?: CoreTransactionState;
}

export type CoreTransactionJourneyStepStatus =
  | "COMPLETED"
  | "CURRENT"
  | "PENDING"
  | "BLOCKED"
  | "FAILED";

export interface CoreTransactionJourneyAlignment {
  journeyType: CoreJourneyType;
  currentStage: CoreJourneyStage;
  stages: CoreJourneyStage[];
  visibleToActor: CoreTransactionActorType[];
  nextActions: CoreTransactionNextAction[];
  blockers: CoreTransactionFailureMode[];
  timelineEntries: CoreTransactionTimelineEntry[];
  completionStatus: "NOT_STARTED" | "IN_PROGRESS" | "BLOCKED" | "COMPLETED";
}

export interface CoreTransactionJourneyView extends CoreTransactionJourneyAlignment {}

export interface CoreTransactionTimelineEntry {
  at: string;
  eventName: CoreTransactionEventName;
  stateBefore: CoreTransactionState;
  stateAfter: CoreTransactionState;
  actorId: string;
  journeyType?: CoreJourneyType;
  journeyStage?: CoreJourneyStage;
  notes?: string;
}

export interface CoreTransactionEvent {
  name: CoreTransactionEventName;
  transactionId: string;
  correlationId: string;
  tenantId: string;
  podId: string;
  facilityId: string;
  workspaceId?: string;
  clientRef?: string;
  clientAlias?: string;
  providerRef?: string;
  actorId: string;
  actorType: CoreTransactionActorType;
  purposeOfUse: CoreTransactionTrustContext["purposeOfUse"];
  serviceCode: string;
  transactionType: CoreTransactionType;
  stateBefore: CoreTransactionState;
  stateAfter: CoreTransactionState;
  timestamp: string;
  sourceSystem: string;
  deviceFingerprint?: string;
  offlineSyncStatus: CoreTransactionOfflineSyncStatus;
  journeyType?: CoreJourneyType;
  journeyStage?: CoreJourneyStage;
  nompiloContextId?: string;
  metadata?: Record<string, unknown>;
}

export interface CoreTransactionServiceOwnership {
  trust: "tshepo" | "mvumo";
  registry: "vito" | "varapi" | "tuso" | "zibo" | "msika" | "indawo";
  clinical: "butano" | "pct" | "oros" | "pharmacy";
  integration: "msika-flow" | "integration-hub" | "offline-sync" | "notification";
  enterprise: "mushex" | "costa" | "coverage" | "general-ledger";
  experience: "experience-bff" | "one-ui-shell" | "nompilo";
  data: "ndr" | "data-warehouse" | "reporting" | "surveillance";
}

export interface CoreTransactionPlaneOwnership {
  primaryPlane:
    | "trust"
    | "registry"
    | "clinical"
    | "data"
    | "integration"
    | "experience"
    | "enterprise";
  supportingPlanes: Array<
    "trust" | "registry" | "clinical" | "data" | "integration" | "experience" | "enterprise"
  >;
}

export interface CoreTransactionBffView {
  transaction: CoreTransaction;
  journeys: {
    person: CoreTransactionJourneyView;
    provider: CoreTransactionJourneyView;
    platform: CoreTransactionJourneyView;
  };
  clientSummary: CoreTransactionClientContext;
  providerContext?: CoreTransactionProviderContext;
  facilityContext: CoreTransactionFacilityContext;
  trustContext: CoreTransactionTrustContext;
  clinicalContext?: CoreTransactionClinicalContext;
  orders: string[];
  financialContext?: CoreTransactionFinancialContext;
  followUp?: CoreTransactionFollowUpContext;
  timeline: CoreTransactionTimelineEntry[];
  nextActions: CoreTransactionNextAction[];
  permissions: CoreTransactionPermission[];
  auditSummary: CoreTransactionAuditContext;
  offlineSyncStatus: CoreTransactionOfflineSyncStatus;
  failureModes: CoreTransactionFailureMode[];
  nompilo: NompiloCompanionContext;
  nompiloCommand: NompiloCommandContext;
}

export function filterNompiloSearchSourcesByPermissions(
  sources: NompiloSearchSource[],
  permissions: NompiloToolPermission[],
): NompiloSearchSource[] {
  const allowed = new Set(permissions.filter((permission) => permission.allowed).map((permission) => permission.source));
  return sources.filter((source) => allowed.has(source));
}

export interface CoreTransaction {
  id: string;
  type: CoreTransactionType;
  lifecycleStage: CoreTransactionLifecycleStage;
  currentState: CoreTransactionState;
  previousState?: CoreTransactionState;
  actor: CoreTransactionActor;
  context: CoreTransactionContext;
  permissions: CoreTransactionPermission[];
  nextActions: CoreTransactionNextAction[];
  timeline: CoreTransactionTimelineEntry[];
  events: CoreTransactionEvent[];
  offlineSyncStatus: CoreTransactionOfflineSyncStatus;
  failureModes: CoreTransactionFailureMode[];
  createdAt: string;
  updatedAt: string;
}

const TERMINAL_STATES: CoreTransactionState[] = [
  "COMPLETED",
  "CLOSED",
  "CANCELLED",
  "NO_SHOW",
  "CLAIM_REJECTED",
];

const DEFAULT_FLOW: CoreTransactionState[] = [
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

const PERSON_STAGES: PersonJourneyStage[] = [
  "FIND_CARE",
  "IDENTIFY_ME",
  "SELECT_SERVICE",
  "CONFIRM_COST_COVERAGE_OR_EXEMPTION",
  "PAY_UPFRONT_WHERE_REQUIRED",
  "ACCESS_SERVICE",
  "PREPARE_FOR_CARE",
  "RECEIVE_CARE",
  "SETTLE_ADDITIONAL_CHARGES_OR_CLAIMS",
  "KNOW_WHAT_HAPPENED",
  "CONTINUE_CARE",
  "GIVE_FEEDBACK",
];

const PROVIDER_STAGES: ProviderJourneyStage[] = [
  "START_DUTY",
  "SEE_MY_WORK",
  "OPEN_CLIENT_CONTEXT",
  "CONFIRM_SERVICE_ACCESS_STATUS",
  "PREPARE_TRIAGE_OR_SCREEN",
  "DELIVER_CARE",
  "ORDER_ACTIONS",
  "COMPLETE_TRANSACTION",
  "CONFIRM_CHARGES_CLAIMS_OR_FOLLOW_UP",
  "CONTINUE_RESPONSIBILITY",
];

const PLATFORM_STAGES: PlatformJourneyStage[] = [
  "RECEIVE_TRIGGER",
  "RESOLVE_IDENTITY",
  "ESTABLISH_TRUST_CONTEXT",
  "RESOLVE_SERVICE_AND_WORKFLOW",
  "DETERMINE_COSTING_COVERAGE_OR_PAYMENT_RULES",
  "APPLY_PRE_SERVICE_PAYMENT_GATE",
  "MANAGE_STATE_MACHINE",
  "COMPOSE_EXPERIENCE_VIEW",
  "EXECUTE_CLINICAL_OR_SERVICE_ACTION",
  "EXECUTE_FINANCIAL_OR_ENTERPRISE_FLOW",
  "UPDATE_RECORD_AND_CONTINUITY",
  "EMIT_EVENTS_AND_AUDIT",
  "FEED_REPORTING_AND_INTELLIGENCE",
  "HANDLE_FAILURE_OFFLINE_AND_RECONCILIATION",
];

export function getAllowedNextStates(
  state: CoreTransactionState,
  transactionType: CoreTransactionType,
): CoreTransactionState[] {
  if (isTerminalCoreTransactionState(state)) return [];
  const index = DEFAULT_FLOW.indexOf(state);
  const defaultNext = index >= 0 ? [DEFAULT_FLOW[index + 1]] : [];
  const commonBranches: CoreTransactionState[] = [
    "CANCELLED",
    "NO_SHOW",
    "REFERRED_OUT",
    "TRANSFERRED",
    "PENDING_SYNC",
    "FAILED_SYNC",
    "ACCESS_DENIED",
    "CONSENT_DENIED",
    "SERVICE_DEFERRED",
  ];
  if (transactionType === "EMERGENCY" && state === "INITIATED") {
    return ["EMERGENCY_OVERRIDE", "IN_SERVICE", "PROVISIONAL_IDENTITY", ...commonBranches];
  }
  if (state === "PRE_SERVICE_PAYMENT_REQUIRED") {
    return ["PRE_SERVICE_PAYMENT_PENDING", "EXEMPTION_CONFIRMED", "ACCESS_BLOCKED_PAYMENT_REQUIRED"];
  }
  if (state === "PRE_SERVICE_PAYMENT_PENDING") {
    return ["PRE_SERVICE_PAYMENT_COMPLETED", "PRE_SERVICE_PAYMENT_FAILED", "PENDING_PAYMENT"];
  }
  return [...defaultNext.filter(Boolean), ...commonBranches];
}

export function isValidCoreTransactionTransition(
  from: CoreTransactionState,
  to: CoreTransactionState,
  transactionType: CoreTransactionType = "FACILITY_WALK_IN",
): boolean {
  return getAllowedNextStates(from, transactionType).includes(to);
}

export function isTerminalCoreTransactionState(state: CoreTransactionState): boolean {
  return TERMINAL_STATES.includes(state);
}

export function requiresTrustContext(state: CoreTransactionState): boolean {
  return !["DRAFT", "INITIATED", "IDENTITY_PENDING", "PROVISIONAL_IDENTITY"].includes(state);
}

export function requiresIdentityResolution(state: CoreTransactionState): boolean {
  return [
    "IDENTITY_PENDING",
    "IDENTITY_RESOLVED",
    "TRUST_CONTEXT_ESTABLISHED",
    "SERVICE_SELECTED",
    "COSTING_REQUIRED",
  ].includes(state);
}

export function requiresAuditEvent(eventName: CoreTransactionEventName): boolean {
  return (
    eventName.startsWith("core.transaction.") ||
    eventName.includes("consent") ||
    eventName.includes("payment") ||
    eventName.includes("claim") ||
    eventName.includes("audit") ||
    eventName.includes("nompilo")
  );
}

export function getLifecycleStageForState(state: CoreTransactionState): CoreTransactionLifecycleStage {
  if (state === "DRAFT" || state === "INITIATED") return "ENTRY";
  if (["IDENTITY_PENDING", "IDENTITY_RESOLVED", "PROVISIONAL_IDENTITY"].includes(state)) return "IDENTITY";
  if (["TRUST_CONTEXT_ESTABLISHED", "CONSENT_DENIED", "ACCESS_DENIED"].includes(state)) return "TRUST_AND_CONSENT";
  if (state === "SERVICE_SELECTED") return "SERVICE_SELECTION";
  if (
    [
      "COSTING_REQUIRED",
      "COST_ESTIMATED",
      "COVERAGE_CHECK_PENDING",
      "COVERAGE_CONFIRMED",
      "EXEMPTION_CONFIRMED",
      "PRE_SERVICE_PAYMENT_REQUIRED",
      "PRE_SERVICE_PAYMENT_PENDING",
      "PRE_SERVICE_PAYMENT_COMPLETED",
      "PRE_SERVICE_PAYMENT_FAILED",
      "ACCESS_BLOCKED_PAYMENT_REQUIRED",
    ].includes(state)
  ) {
    return "COST_AND_COVERAGE";
  }
  if (["SCHEDULED", "QUEUED", "TASKED", "ACCESS_GRANTED"].includes(state)) return "SCHEDULING_OR_QUEUE";
  if (state === "TRIAGE_IN_PROGRESS") return "TRIAGE_OR_ELIGIBILITY";
  if (state === "READY_FOR_PROVIDER") return "PROVIDER_ASSIGNMENT";
  if (["IN_SERVICE", "EMERGENCY_OVERRIDE"].includes(state)) return "ENCOUNTER_OR_DELIVERY";
  if (["ORDERS_PENDING", "ANCILLARY_IN_PROGRESS", "PENDING_RESULT"].includes(state)) return "ORDERS_AND_ANCILLARY";
  if (
    [
      "FINANCIAL_PROCESSING",
      "POST_SERVICE_BILLING_PENDING",
      "PENDING_PAYMENT",
      "PENDING_CLAIM",
      "CLAIM_PENDING",
      "RECONCILIATION_PENDING",
      "PAYMENT_FAILED",
      "CLAIM_REJECTED",
    ].includes(state)
  ) {
    return "FINANCIAL_SETTLEMENT";
  }
  if (state === "SHR_UPDATE_PENDING") return "RECORD_CONTRIBUTION";
  if (state === "CLIENT_INSTRUCTIONS_PENDING") return "INSTRUCTIONS_AND_NOTIFICATIONS";
  if (["FOLLOW_UP_ACTIVE", "REFERRED_OUT"].includes(state)) return "FOLLOW_UP_CONTINUITY";
  if (["PENDING_SYNC", "FAILED_SYNC", "PENDING_RECONCILIATION"].includes(state)) return "REPORTING_ANALYTICS_AUDIT";
  if (["COMPLETED", "CLOSED", "CANCELLED", "NO_SHOW"].includes(state)) return "COMPLETION";
  return "NEED_OR_TRIGGER";
}

export function getPersonJourneyStageForTransactionState(
  state: CoreTransactionState,
): PersonJourneyStage {
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

export function getProviderJourneyStageForTransactionState(
  state: CoreTransactionState,
): ProviderJourneyStage {
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

export function getPlatformJourneyStageForTransactionState(
  state: CoreTransactionState,
): PlatformJourneyStage {
  if (state === "INITIATED") return "RECEIVE_TRIGGER";
  if (["IDENTITY_PENDING", "IDENTITY_RESOLVED", "PROVISIONAL_IDENTITY"].includes(state)) return "RESOLVE_IDENTITY";
  if (["TRUST_CONTEXT_ESTABLISHED", "CONSENT_DENIED", "ACCESS_DENIED"].includes(state)) return "ESTABLISH_TRUST_CONTEXT";
  if (["SERVICE_SELECTED", "SCHEDULED", "QUEUED", "TASKED"].includes(state)) return "RESOLVE_SERVICE_AND_WORKFLOW";
  if (["COSTING_REQUIRED", "COST_ESTIMATED", "COVERAGE_CHECK_PENDING", "COVERAGE_CONFIRMED", "EXEMPTION_CONFIRMED"].includes(state)) {
    return "DETERMINE_COSTING_COVERAGE_OR_PAYMENT_RULES";
  }
  if (["PRE_SERVICE_PAYMENT_REQUIRED", "PRE_SERVICE_PAYMENT_PENDING", "PRE_SERVICE_PAYMENT_COMPLETED", "PRE_SERVICE_PAYMENT_FAILED", "ACCESS_BLOCKED_PAYMENT_REQUIRED"].includes(state)) {
    return "APPLY_PRE_SERVICE_PAYMENT_GATE";
  }
  if (["ACCESS_GRANTED", "TRIAGE_IN_PROGRESS", "READY_FOR_PROVIDER"].includes(state)) return "MANAGE_STATE_MACHINE";
  if (state === "IN_SERVICE") return "COMPOSE_EXPERIENCE_VIEW";
  if (["ORDERS_PENDING", "ANCILLARY_IN_PROGRESS", "PROVIDER_REVIEW_PENDING"].includes(state)) return "EXECUTE_CLINICAL_OR_SERVICE_ACTION";
  if (["FINANCIAL_PROCESSING", "POST_SERVICE_BILLING_PENDING", "CLAIM_PENDING", "RECONCILIATION_PENDING", "PENDING_PAYMENT", "PENDING_CLAIM"].includes(state)) {
    return "EXECUTE_FINANCIAL_OR_ENTERPRISE_FLOW";
  }
  if (["CLIENT_INSTRUCTIONS_PENDING", "CLINICAL_COMPLETION_PENDING", "SHR_UPDATE_PENDING", "FOLLOW_UP_ACTIVE"].includes(state)) {
    return "UPDATE_RECORD_AND_CONTINUITY";
  }
  if (["COMPLETED", "CLOSED"].includes(state)) return "EMIT_EVENTS_AND_AUDIT";
  if (["PENDING_RECONCILIATION", "PENDING_SYNC", "FAILED_SYNC"].includes(state)) return "HANDLE_FAILURE_OFFLINE_AND_RECONCILIATION";
  return "FEED_REPORTING_AND_INTELLIGENCE";
}

export function getJourneyAlignmentForTransaction(
  transaction: CoreTransaction,
): CoreTransactionJourneyAlignment[] {
  return [
    {
      journeyType: "PERSON",
      currentStage: getPersonJourneyStageForTransactionState(transaction.currentState),
      stages: PERSON_STAGES,
      visibleToActor: ["CLIENT", "PROVIDER", "PLATFORM_OPERATOR"],
      nextActions: transaction.nextActions,
      blockers: transaction.failureModes,
      timelineEntries: transaction.timeline,
      completionStatus: isTerminalCoreTransactionState(transaction.currentState) ? "COMPLETED" : "IN_PROGRESS",
    },
    {
      journeyType: "PROVIDER",
      currentStage: getProviderJourneyStageForTransactionState(transaction.currentState),
      stages: PROVIDER_STAGES,
      visibleToActor: ["PROVIDER", "PLATFORM_OPERATOR"],
      nextActions: transaction.nextActions,
      blockers: transaction.failureModes,
      timelineEntries: transaction.timeline,
      completionStatus: isTerminalCoreTransactionState(transaction.currentState) ? "COMPLETED" : "IN_PROGRESS",
    },
    {
      journeyType: "PLATFORM",
      currentStage: getPlatformJourneyStageForTransactionState(transaction.currentState),
      stages: PLATFORM_STAGES,
      visibleToActor: ["PLATFORM_OPERATOR"],
      nextActions: transaction.nextActions,
      blockers: transaction.failureModes,
      timelineEntries: transaction.timeline,
      completionStatus: isTerminalCoreTransactionState(transaction.currentState) ? "COMPLETED" : "IN_PROGRESS",
    },
  ];
}

export function getVisibleJourneyStagesForActor(
  actorType: CoreTransactionActorType,
  transaction: CoreTransaction,
): CoreJourneyStage[] {
  if (actorType === "CLIENT") {
    return PERSON_STAGES;
  }
  if (actorType === "PROVIDER") {
    return [...PERSON_STAGES, ...PROVIDER_STAGES];
  }
  return [
    ...PERSON_STAGES,
    ...PROVIDER_STAGES,
    ...PLATFORM_STAGES,
    getPlatformJourneyStageForTransactionState(transaction.currentState),
  ];
}

export function getCurrentJourneyStepStatus(
  transaction: CoreTransaction,
  journeyType: CoreJourneyType,
): CoreTransactionJourneyStepStatus {
  if (transaction.failureModes.length > 0) return "BLOCKED";
  if (isTerminalCoreTransactionState(transaction.currentState)) return "COMPLETED";
  if (["PRE_SERVICE_PAYMENT_FAILED", "FAILED_SYNC", "PAYMENT_FAILED", "CLAIM_REJECTED"].includes(transaction.currentState)) {
    return "FAILED";
  }
  if (journeyType === "PLATFORM" && transaction.offlineSyncStatus === "SYNC_PENDING") return "PENDING";
  return "CURRENT";
}

export function getNompiloSurfacesForJourneyStage(
  journeyType: CoreJourneyType,
  stage: CoreJourneyStage,
  actorType: CoreTransactionActorType,
): NompiloSurfaceType[] {
  if (journeyType === "PLATFORM") return ["PLATFORM_OPERATIONS_INSIGHT", "SMART_NUDGE"];
  if (journeyType === "PROVIDER") return ["PROVIDER_WORKFLOW_ASSIST", "INLINE_HINT", "HUMAN_HANDOFF"];
  if (stage === "GIVE_FEEDBACK") return ["FEEDBACK_PROMPT", "FLOATING_ASSISTANT"];
  if (actorType === "CLIENT") return ["JOURNEY_STEP_GUIDE", "EXPLAINER", "ACCESSIBILITY_ASSIST"];
  return ["FLOATING_ASSISTANT", "INLINE_HINT"];
}

export function getNompiloGuidanceForTransactionState(
  state: CoreTransactionState,
  transactionType: CoreTransactionType,
): NompiloGuidance[] {
  if (state === "PRE_SERVICE_PAYMENT_REQUIRED") {
    return [
      {
        id: "payment-gate",
        message: "This service requires payment or exemption confirmation before access.",
        tone: "NEUTRAL",
        surfaceType: "SMART_NUDGE",
      },
    ];
  }
  if (transactionType === "EMERGENCY") {
    return [
      {
        id: "emergency-calm",
        message: "Emergency care proceeds first. Reconciliation and payment can follow later.",
        tone: "CALM",
        surfaceType: "EXPLAINER",
      },
    ];
  }
  return [
    {
      id: "default-next-step",
      message: "I can help explain the current step and next allowed action.",
      tone: "CALM",
      surfaceType: "FLOATING_ASSISTANT",
    },
  ];
}

export function getNompiloAccessibilityOptions(
  actorType: CoreTransactionActorType,
  preferences?: Partial<Record<NompiloAccessibilityOption["code"], boolean>>,
): NompiloAccessibilityOption[] {
  const base: NompiloAccessibilityOption[] = [
    { code: "SIMPLE_LANGUAGE", enabled: true, description: "Use plain language guidance." },
    { code: "READ_ALOUD_AUDIO_GUIDANCE", enabled: true, description: "Read steps aloud." },
    { code: "VOICE_INPUT", enabled: actorType !== "DIGITAL_SERVICE", description: "Capture input by voice." },
    { code: "KEYBOARD_NAVIGATION", enabled: true, description: "Keyboard-first flow support." },
    { code: "SCREEN_READER_FRIENDLY_GUIDANCE", enabled: true, description: "Structured screen reader labels." },
    { code: "LARGE_TEXT", enabled: true, description: "Large text mode." },
    { code: "HIGH_CONTRAST", enabled: true, description: "High contrast mode." },
    { code: "REDUCED_MOTION", enabled: true, description: "Disable motion-heavy transitions." },
    { code: "CAREGIVER_PROXY_ASSISTANCE", enabled: true, description: "Proxy/caregiver handoff support." },
    { code: "STEP_BY_STEP_MODE", enabled: true, description: "One-step-at-a-time progression." },
    { code: "LOW_BANDWIDTH_MODE", enabled: true, description: "Lightweight low-network rendering." },
    { code: "MULTILINGUAL_EXPANSION", enabled: true, description: "Language adaptation support." },
  ];
  return base.map((option) => ({
    ...option,
    enabled: preferences?.[option.code] ?? option.enabled,
  }));
}

export function getNompiloFeedbackPromptsForStage(
  journeyType: CoreJourneyType,
  stage: CoreJourneyStage,
): NompiloFeedbackPrompt[] {
  return [
    {
      id: `${journeyType}-${stage}-feedback`,
      channel: "IN_APP",
      prompt: "How was this step for you?",
      stage,
    },
  ];
}

export function requiresHumanHandoff(nompiloContext: NompiloCompanionContext): boolean {
  return nompiloContext.mode === "HUMAN_HANDOFF" || nompiloContext.confidence === "LOW";
}

export function canNompiloSurfaceClinicalGuidance(
  actorType: CoreTransactionActorType,
  permissions: CoreTransactionPermission[],
  purposeOfUse: CoreTransactionTrustContext["purposeOfUse"],
): boolean {
  const hasPermission = permissions.some((p) => p.code === "core.transaction.view" && p.allowed);
  return hasPermission && actorType !== "CLIENT" && purposeOfUse !== "RESEARCH";
}

export const CORE_TRANSACTION_DOCTRINE_NOTES = {
  invariant: "Every meaningful action must move state, emit event, and leave audit meaning.",
  antiDuplication:
    "Experience and BFF compose sovereign truth from Vito, Varapi, Tuso, Tshepo, Butano, Msika, MusheX, Costa and other owning services.",
  emergency:
    "Emergency care can precede full identity/consent/payment but must reconcile with elevated audit.",
  offline: "Offline transactions are governed and reconcilable, never shadow truth.",
} as const;
