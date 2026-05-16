import type {
  CoreTransactionTimelineEntry,
  CoreTransactionType,
  CoreTransactionView,
} from "../types";

function baseFixture(
  id: string,
  type: CoreTransactionType,
  state: CoreTransactionView["transaction"]["currentState"],
  stage: CoreTransactionView["transaction"]["lifecycleStage"],
): CoreTransactionView {
  const timeline: CoreTransactionTimelineEntry[] = [
    {
      at: "2026-05-01T08:00:00Z",
      eventName: "core.transaction.initiated",
      stateBefore: "DRAFT",
      stateAfter: "INITIATED",
      actorId: "provider-9001",
      status: "COMPLETED" as const,
      journeyType: "PLATFORM" as const,
      journeyStage: "RECEIVE_TRIGGER" as const,
    },
    {
      at: "2026-05-01T08:03:00Z",
      eventName: "core.identity.resolved",
      stateBefore: "IDENTITY_PENDING",
      stateAfter: "IDENTITY_RESOLVED",
      actorId: "provider-9001",
      status: "COMPLETED" as const,
      journeyType: "PERSON" as const,
      journeyStage: "IDENTIFY_ME" as const,
    },
    {
      at: "2026-05-01T08:05:00Z",
      eventName: "core.trust.context.established",
      stateBefore: "IDENTITY_RESOLVED",
      stateAfter: "TRUST_CONTEXT_ESTABLISHED",
      actorId: "provider-9001",
      status: "COMPLETED" as const,
      journeyType: "PLATFORM" as const,
      journeyStage: "ESTABLISH_TRUST_CONTEXT" as const,
    },
    {
      at: "2026-05-01T08:10:00Z",
      eventName: "core.encounter.started",
      stateBefore: "READY_FOR_PROVIDER",
      stateAfter: "IN_SERVICE",
      actorId: "provider-9001",
      status: "CURRENT" as const,
      journeyType: "PROVIDER" as const,
      journeyStage: "DELIVER_CARE" as const,
    },
  ];
  const nextActions = [
    { code: "ORDER_LAB", label: "Order laboratory panel" },
    { code: "GENERATE_INSTRUCTIONS", label: "Generate client instructions" },
  ];
  return {
    fixture: true,
    transaction: { id, type, currentState: state, lifecycleStage: stage, serviceCode: `svc.${type.toLowerCase()}` },
    journeys: {
      person: {
        journeyType: "PERSON",
        currentStage: "RECEIVE_CARE",
        stages: [
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
        ],
        visibleToActor: ["CLIENT", "PROVIDER"],
        nextActions,
        blockers: [],
        timelineEntries: timeline,
        completionStatus: "IN_PROGRESS",
      },
      provider: {
        journeyType: "PROVIDER",
        currentStage: "DELIVER_CARE",
        stages: [
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
        ],
        visibleToActor: ["PROVIDER", "PLATFORM_OPERATOR"],
        nextActions,
        blockers: [],
        timelineEntries: timeline,
        completionStatus: "IN_PROGRESS",
      },
      platform: {
        journeyType: "PLATFORM",
        currentStage: "MANAGE_STATE_MACHINE",
        stages: [
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
        ],
        visibleToActor: ["PLATFORM_OPERATOR"],
        nextActions,
        blockers: [],
        timelineEntries: timeline,
        completionStatus: "IN_PROGRESS",
      },
    },
    clientSummary: { clientRef: "vito:CL-1001", clientAlias: "CLIENT-ALIAS-1001", identityStatus: "RESOLVED" },
    providerContext: { providerRef: "varapi:PR-9001", actorId: "provider-9001", roleCode: "PROVIDER" },
    facilityContext: { facilityId: "tuso:FAC-001", workspaceId: "WS-TRIAGE" },
    trustContext: { purposeOfUse: "TREATMENT", consentStatus: "GRANTED", accessStatus: "ALLOW" },
    clinicalContext: { encounterId: `enc-${id}`, ordersPending: 1, resultsPending: 0 },
    financialContext: { costingStatus: "CALCULATED", paymentStatus: "PENDING", claimStatus: "NOT_APPLICABLE" },
    followUp: { required: true, note: "Return in 2 weeks." },
    timeline,
    nextActions,
    permissions: [
      { code: "core.transaction.view", allowed: true },
      { code: "core.transaction.advance", allowed: true },
    ],
    auditSummary: { correlationId: `corr-${id}`, sourceSystem: "one-ui-shell-fixture", auditRequired: true },
    offlineSyncStatus: "ONLINE",
    failureModes: [],
    nompilo: {
      available: true,
      mode: "CONTEXTUAL_COMPANION",
      currentGuidance: [
        {
          id: "guidance-1",
          message: "You are in the care delivery stage. I can explain the next step.",
          surfaceType: "FLOATING_ASSISTANT",
        },
      ],
      suggestedQuestions: [{ code: "WHAT_NEXT", prompt: "What happens next?" }],
      nextBestActions: [{ code: "REVIEW_TIMELINE", label: "Review timeline and pending tasks" }],
      accessibilityOptions: [
        { code: "SIMPLE_LANGUAGE", enabled: true, description: "Use plain language" },
        { code: "READ_ALOUD_AUDIO_GUIDANCE", enabled: true, description: "Read guidance aloud" },
      ],
      feedbackPrompts: [
        { id: "feedback-1", channel: "IN_APP", prompt: "How easy was this step?" },
      ],
      handoffOptions: [
        { id: "handoff-1", label: "Talk to staff", destination: "FACILITY_HELP_DESK" },
      ],
      privacyNotice: { summary: "Nompilo guidance follows trust and consent policy." },
      confidence: "HIGH",
    },
    nompiloCommand: {
      enabled: true,
      surfaces: [
        "GLOBAL_SEARCH_BAR",
        "COMMAND_PALETTE",
        "FLOATING_ASSISTANT",
        "VOICE_DICTATION",
        "INLINE_JOURNEY_PROMPT",
        "FUNDO_LEARNING_ASSISTANT",
        "DASHBOARD_ASSISTANT",
        "PROVIDER_WORKSPACE_ASSISTANT",
        "CLIENT_APP_ASSISTANT",
        "SUPPORT_HELP_INTERFACE",
        "MOBILE_CHAT",
        "MOBILE_VOICE",
      ],
      availableSearchSources: [
        "VITO_CLIENT_REGISTRY",
        "VARAPI_PROVIDER_REGISTRY",
        "TUSO_FACILITY_REGISTRY",
        "MSIKA_SERVICE_PRODUCT_CATALOGUE",
        "MSIKA_FLOW_FULFILMENT",
        "INDAWO_PUBLIC_HEALTH_SITES",
        "ZIBO_TERMINOLOGY",
        "FUNDO_LEARNING",
        "BUTANO_CLINICAL_SUMMARY",
        "COSTA_COSTING",
        "MUSHEX_PAYMENTS_CLAIMS",
        "DATA_PLANE_ANALYTICS",
        "DOCUMENT_SERVICE",
        "SUPPORT_HELPDESK",
        "NOMPILO_KNOWLEDGE_BASE",
      ],
      suggestedCommands: ["FIND_SERVICE", "FIND_PROVIDER", "ASK_PAYMENT_OR_CLAIM_HELP", "REQUEST_SUPPORT"],
      recentCommands: ["CHECK_STATUS", "REQUEST_SUMMARY"],
      voiceEnabled: true,
      dictationEnabled: true,
      toolPermissions: [
        { source: "VITO_CLIENT_REGISTRY", allowed: true },
        { source: "BUTANO_CLINICAL_SUMMARY", allowed: true },
        { source: "GOVERNED_INTERNET_SEARCH", allowed: false, reason: "External search disabled for current role." },
      ],
      loginBriefing: {
        role: "PROVIDER",
        headline: "You have 8 queued clients and 2 incomplete notes.",
        highlights: ["2 urgent tasks", "1 payment failure case", "1 sync backlog item"],
        urgentItems: ["Follow up PRE_SERVICE_PAYMENT_FAILED for tx-payment-failed-001"],
      },
      supportHandoff: {
        enabled: true,
        destination: "FACILITY_HELP_DESK",
        queue: "tier-1",
      },
      analyticsCapabilities: ["Ask indicator trends", "Generate dashboard view", "Compile narrative summary"],
      learningCapabilities: ["Explain module", "Start quiz", "Suggest refresher"],
    },
  };
}

export const facilityWalkInTransaction = baseFixture("tx-walk-in-001", "FACILITY_WALK_IN", "QUEUED", "SCHEDULING_OR_QUEUE");
export const appointmentTransaction = baseFixture("tx-appointment-001", "APPOINTMENT", "READY_FOR_PROVIDER", "PROVIDER_ASSIGNMENT");
export const emergencyProvisionalIdentityTransaction = {
  ...baseFixture("tx-emergency-001", "EMERGENCY", "EMERGENCY_OVERRIDE", "ENCOUNTER_OR_DELIVERY"),
  clientSummary: { clientAlias: "UNKNOWN-CLIENT", identityStatus: "PROVISIONAL" },
  trustContext: { purposeOfUse: "EMERGENCY", consentStatus: "DEFERRED", accessStatus: "BREAK_GLASS" },
  offlineSyncStatus: "SYNC_PENDING" as const,
  failureModes: ["OFFLINE_SYNC_PENDING", "EMERGENCY_OVERRIDE_REQUIRED"],
};
export const telemedicineTransaction = baseFixture("tx-telemedicine-001", "TELEMEDICINE", "IN_SERVICE", "ENCOUNTER_OR_DELIVERY");
export const referralTransaction = baseFixture("tx-referral-001", "REFERRAL", "FOLLOW_UP_ACTIVE", "FOLLOW_UP_CONTINUITY");
export const chronicCareTransaction = baseFixture("tx-chronic-001", "CHRONIC_CARE", "FOLLOW_UP_ACTIVE", "FOLLOW_UP_CONTINUITY");
export const pharmacyTransaction = baseFixture("tx-pharmacy-001", "PHARMACY", "ORDERS_PENDING", "ORDERS_AND_ANCILLARY");
export const laboratoryTransaction = baseFixture("tx-lab-001", "LABORATORY", "PENDING_RESULT", "ORDERS_AND_ANCILLARY");
export const imagingTransaction = baseFixture("tx-imaging-001", "IMAGING", "ANCILLARY_IN_PROGRESS", "ORDERS_AND_ANCILLARY");
export const communityOutreachTransaction = baseFixture("tx-community-001", "COMMUNITY_OUTREACH", "PENDING_SYNC", "REPORTING_ANALYTICS_AUDIT");
export const marketplaceTransaction = baseFixture("tx-market-001", "MARKETPLACE", "FINANCIAL_PROCESSING", "FINANCIAL_SETTLEMENT");
export const administrativeHealthTransaction = baseFixture("tx-admin-001", "ADMINISTRATIVE_HEALTH", "CLIENT_INSTRUCTIONS_PENDING", "INSTRUCTIONS_AND_NOTIFICATIONS");
export const wellnessTransaction = baseFixture("tx-wellness-001", "WELLNESS", "FOLLOW_UP_ACTIVE", "FOLLOW_UP_CONTINUITY");
export const trainingCompetencyTransaction = baseFixture("tx-training-001", "TRAINING_OR_COMPETENCY", "TASKED", "SCHEDULING_OR_QUEUE");
export const supervisionMentorshipTransaction = baseFixture("tx-mentor-001", "SUPERVISION_OR_MENTORSHIP", "TASKED", "SCHEDULING_OR_QUEUE");
export const preServicePaymentRequiredTransaction = baseFixture("tx-payment-gate-001", "LABORATORY", "PRE_SERVICE_PAYMENT_REQUIRED", "COST_AND_COVERAGE");
export const failedPaymentTransaction = {
  ...baseFixture("tx-payment-failed-001", "IMAGING", "PRE_SERVICE_PAYMENT_FAILED", "COST_AND_COVERAGE"),
  failureModes: ["PRE_SERVICE_PAYMENT_FAILED", "PAYMENT_FAILED"],
};
export const offlineSyncPendingTransaction = {
  ...baseFixture("tx-offline-001", "COMMUNITY_OUTREACH", "PENDING_SYNC", "REPORTING_ANALYTICS_AUDIT"),
  offlineSyncStatus: "SYNC_PENDING" as const,
  failureModes: ["OFFLINE_SYNC_PENDING"],
};
export const accessibilityAssistanceRequestedTransaction = {
  ...baseFixture("tx-accessibility-001", "FACILITY_WALK_IN", "ACCESS_GRANTED", "SCHEDULING_OR_QUEUE"),
  nompilo: {
    ...baseFixture("tmp", "FACILITY_WALK_IN", "ACCESS_GRANTED", "SCHEDULING_OR_QUEUE").nompilo,
    mode: "ACCESSIBILITY_SUPPORT" as const,
    accessibilityOptions: [
      { code: "SIMPLE_LANGUAGE", enabled: true, description: "Use plain language" },
      { code: "KEYBOARD_NAVIGATION", enabled: true, description: "Keyboard-first navigation" },
      { code: "HIGH_CONTRAST", enabled: true, description: "High contrast display" },
    ],
  },
};
export const providerIncompleteEncounterTransaction = {
  ...baseFixture("tx-provider-incomplete-001", "FACILITY_WALK_IN", "PROVIDER_REVIEW_PENDING", "ENCOUNTER_OR_DELIVERY"),
  failureModes: ["DOCUMENTATION_INCOMPLETE"],
};
export const clientPostVisitFeedbackTransaction = {
  ...baseFixture("tx-feedback-001", "APPOINTMENT", "COMPLETED", "COMPLETION"),
  nompilo: {
    ...baseFixture("tmp2", "APPOINTMENT", "COMPLETED", "COMPLETION").nompilo,
    mode: "FEEDBACK_CAPTURE" as const,
    feedbackPrompts: [
      { id: "feedback-post-visit", channel: "SMS" as const, prompt: "How was your visit today?" },
      { id: "feedback-accessibility", channel: "IN_APP" as const, prompt: "Did accessibility support meet your needs?" },
    ],
  },
};

export const coreTransactionFixtures: CoreTransactionView[] = [
  facilityWalkInTransaction,
  appointmentTransaction,
  emergencyProvisionalIdentityTransaction,
  telemedicineTransaction,
  referralTransaction,
  chronicCareTransaction,
  pharmacyTransaction,
  laboratoryTransaction,
  imagingTransaction,
  communityOutreachTransaction,
  marketplaceTransaction,
  administrativeHealthTransaction,
  wellnessTransaction,
  trainingCompetencyTransaction,
  supervisionMentorshipTransaction,
  preServicePaymentRequiredTransaction,
  failedPaymentTransaction,
  offlineSyncPendingTransaction,
  accessibilityAssistanceRequestedTransaction,
  providerIncompleteEncounterTransaction,
  clientPostVisitFeedbackTransaction,
];

export const nompiloServiceSearchQueryFixture = "Find ANC services near me with telemedicine option";
export const nompiloProviderSearchQueryFixture = "Find available ANC provider at FAC-001";
export const nompiloCommoditySearchQueryFixture = "Check availability of iron supplements";
export const nompiloWellnessEventQueryFixture = "Find maternal classes and community wellness events this month";
export const nompiloPaymentHelpQueryFixture = "Why did my payment fail and what are my claim options?";
export const nompiloFailedPaymentSupportRequestFixture = {
  category: "PAYMENT_FAILURE",
  summary: "Pre-service payment failed for imaging transaction",
  transactionId: "tx-payment-failed-001",
  priority: "HIGH" as const,
};
export const nompiloFundoModuleExplanationFixture = "Explain Fundo ANC danger signs refresher module";
export const nompiloFundoQuizRequestFixture = "Quiz me on triage protocols";
export const nompiloDashboardRequestFixture = "Show dashboard for district sync failure trends by facility";
export const nompiloReportRequestFixture = "Generate weekly maternal health operations report";
export const nompiloClientLoginBriefingFixture = {
  role: "CLIENT",
  headline: "You have 1 appointment and 1 refill due.",
  highlights: ["Appointment tomorrow", "Lab result ready"],
  urgentItems: [],
};
export const nompiloProviderLoginBriefingFixture = {
  role: "PROVIDER",
  headline: "Queue is heavy this morning.",
  highlights: ["8 queued clients", "2 incomplete notes"],
  urgentItems: ["Review urgent lab result for enc-1"],
};
export const nompiloFacilityManagerLoginBriefingFixture = {
  role: "PLATFORM_OPERATOR",
  headline: "Operational bottlenecks detected.",
  highlights: ["3 payment failures", "2 sync failures"],
  urgentItems: ["Resolve workspace outage in WS-TRIAGE"],
};
export const nompiloNationalProgrammeLoginBriefingFixture = {
  role: "PLATFORM_OPERATOR",
  headline: "Programme trend summary ready.",
  highlights: ["Coverage gap in district north", "Late reporting in 4 facilities"],
  urgentItems: ["Escalate reporting quality issue in pod-2"],
};
export const nompiloSupportTicketCreationFixture = {
  category: "SUPPORT",
  summary: "Need assistance with blocked referral workflow",
  transactionId: "tx-referral-001",
  priority: "MEDIUM" as const,
};
export const nompiloVoiceDictationFixture = "I need help finding ANC services";
