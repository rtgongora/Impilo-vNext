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
  | "SUPERVISION_OR_MENTORSHIP";

export type CoreTransactionLifecycleStage =
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

export interface CoreTransactionTimelineEntry {
  at: string;
  eventName: string;
  stateBefore: CoreTransactionState;
  stateAfter: CoreTransactionState;
  actorId: string;
  status: "COMPLETED" | "CURRENT" | "PENDING" | "EXCEPTION" | "FAILED" | "BLOCKED";
  journeyType?: CoreJourneyType;
  journeyStage?: CoreJourneyStage;
}

export interface CoreTransactionJourneyView {
  journeyType: CoreJourneyType;
  currentStage: CoreJourneyStage;
  stages: CoreJourneyStage[];
  visibleToActor: string[];
  nextActions: Array<{ code: string; label: string }>;
  blockers: string[];
  blockerReasons?: string[];
  timelineEntries: CoreTransactionTimelineEntry[];
  completionStatus: "NOT_STARTED" | "IN_PROGRESS" | "BLOCKED" | "COMPLETED";
}

export interface NompiloCompanionContext {
  available: boolean;
  mode: NompiloCompanionMode;
  currentGuidance: Array<{ id: string; message: string; surfaceType: NompiloSurfaceType }>;
  suggestedQuestions: Array<{ code: string; prompt: string }>;
  nextBestActions: Array<{ code: string; label: string }>;
  accessibilityOptions: Array<{ code: string; enabled: boolean; description: string }>;
  feedbackPrompts: Array<{ id: string; channel: NompiloFeedbackChannel; prompt: string }>;
  handoffOptions: Array<{ id: string; label: string; destination: string }>;
  privacyNotice: { summary: string; consentReference?: string };
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
  role: string;
  headline: string;
  highlights: string[];
  urgentItems: string[];
}

export interface NompiloCommandResult {
  intent: NompiloCommandIntent;
  summary: string;
  authoritativeSource: NompiloSearchSource;
  confidence: "LOW" | "MEDIUM" | "HIGH";
  generatedExplanation?: string;
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
  supportHandoff: { enabled: boolean; destination?: string; queue?: string };
  analyticsCapabilities: string[];
  learningCapabilities: string[];
}

export interface CoreTransactionSummary {
  id: string;
  type: CoreTransactionType;
  currentState: CoreTransactionState;
  lifecycleStage: CoreTransactionLifecycleStage;
  serviceCode: string;
}

export interface CoreTransactionView {
  fixture: true;
  transaction: CoreTransactionSummary;
  journeys: {
    person: CoreTransactionJourneyView;
    provider: CoreTransactionJourneyView;
    platform: CoreTransactionJourneyView;
  };
  clientSummary: { clientRef?: string; clientAlias?: string; identityStatus: string };
  providerContext: { providerRef?: string; actorId: string; roleCode?: string };
  facilityContext: { facilityId: string; workspaceId?: string };
  trustContext: { purposeOfUse: string; consentStatus: string; accessStatus: string };
  clinicalContext: { encounterId?: string; ordersPending?: number; resultsPending?: number };
  financialContext: { costingStatus: string; paymentStatus: string; claimStatus: string };
  followUp: { required: boolean; note?: string };
  timeline: CoreTransactionTimelineEntry[];
  nextActions: Array<{ code: string; label: string }>;
  permissions: Array<{ code: string; allowed: boolean }>;
  auditSummary: { correlationId: string; sourceSystem: string; auditRequired: boolean };
  offlineSyncStatus:
    | "ONLINE"
    | "OFFLINE_CAPTURED"
    | "SYNC_PENDING"
    | "SYNC_IN_PROGRESS"
    | "SYNC_FAILED"
    | "SYNC_RECONCILED";
  failureModes: string[];
  blockerReasons?: string[];
  nompilo: NompiloCompanionContext;
  nompiloCommand: NompiloCommandContext;
}
