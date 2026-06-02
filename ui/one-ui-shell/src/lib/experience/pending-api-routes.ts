/**
 * Planned BFF routes for Lovable-parity surfaces not yet wired in Experience.
 * Backend team: implement these under the Experience BFF; UI shows pending toasts until live.
 */
export enum PendingApiRoute {
  // ── Professional hub ─────────────────────────────────────────────
  ProfessionalPriorityFeed = "GET /internal/v1/professional/priority-feed",
  ProfessionalPatientPanel = "GET /internal/v1/professional/patient-panel",
  ProfessionalMergedSchedule = "GET /internal/v1/professional/schedule/merged",
  ProfessionalCpdCycle = "GET /internal/v1/professional/cpd/cycle",
  ProfessionalCpdCourses = "GET /internal/v1/professional/cpd/courses",
  ProfessionalCpdLogActivity = "POST /internal/v1/professional/cpd/activities",
  ProfessionalLandelaInbox = "GET /internal/v1/professional/landela/notifications",
  ProfessionalMohEmail = "GET /internal/v1/professional/email/inbox",
  ProfessionalCertifications = "GET /internal/v1/professional/certifications",

  // ── Portal / My Life (citizen) ───────────────────────────────────
  PortalHealthTimeline = "GET /internal/v1/portal/health-timeline",
  PortalWallet = "GET /internal/v1/portal/wallet",
  PortalWalletTopUp = "POST /internal/v1/portal/wallet/top-up",
  PortalRemoteMonitoring = "GET /internal/v1/portal/remote-monitoring/devices",
  PortalSecureMessaging = "GET /internal/v1/portal/messages",
  PortalConsentDashboard = "GET /internal/v1/portal/consents",
  PortalQueueStatus = "GET /internal/v1/portal/queue-status",
  PortalServiceDiscovery = "GET /internal/v1/portal/services/discovery",
  PortalEmergencySos = "POST /internal/v1/portal/emergency/sos",
  PortalDocumentScan = "POST /internal/v1/portal/documents/scan",

  // ── Social hub ───────────────────────────────────────────────────
  SocialTimelineFeed = "GET /internal/v1/social/timeline",
  SocialCommunities = "GET /internal/v1/social/communities",
  SocialClubs = "GET /internal/v1/social/clubs",
  SocialProfessionalPages = "GET /internal/v1/social/professional-pages",
  SocialCrowdfunding = "GET /internal/v1/social/crowdfunding",
  SocialNewsWidget = "GET /internal/v1/social/news",

  // ── Patient / registry (BFF → VITO / PCT / BUTANO) ───────────────
  RegistryCoveragePreview = "POST /internal/v1/registry/coverage/preview",
  FhirShrPatientWrite = "PUT /internal/v1/fhir/Patient",
  PatientBiometricCapture = "POST /internal/v1/patients/biometrics",
  PatientPhotoCapture = "POST /internal/v1/patients/photo",
  PctJourneyAutoStart = "POST /internal/v1/pct/journeys/auto",
}

export enum PersonalHealthTab {
  Home = "home",
  HealthId = "health-id",
  Appointments = "appointments",
  Records = "records",
  Medications = "medications",
  Wallet = "wallet",
  Privacy = "privacy",
  Monitoring = "monitoring",
  Messages = "messages",
  Marketplace = "marketplace",
  Wellness = "wellness",
  Services = "services",
  Queue = "queue",
  Emergency = "emergency",
}

export enum PersonalSocialTab {
  Timeline = "timeline",
  Communities = "communities",
  Clubs = "clubs",
  Pages = "pages",
  Crowdfunding = "crowdfunding",
}

export enum ProfessionalHubTab {
  Dashboard = "dashboard",
  Affiliations = "affiliations",
  Patients = "patients",
  Schedule = "schedule",
  Credentials = "credentials",
}
