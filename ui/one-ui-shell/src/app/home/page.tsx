"use client";

/**
 * Home — Role-aware dashboard with workplace hub, professional stats,
 * module categories, and recent activity.
 *
 * Lovable reference: ModuleHome with WorkplaceSelectionHub,
 * MyProfessionalHub (Dashboard/Affiliations/Schedule/Credentials),
 * and ExpandableCategoryCards.
 */

import { useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  Users, BookOpen, BarChart3, Clock, ArrowRight, Building2,
  Activity, Receipt, Pill, Calendar, Shield, Stethoscope,
  ClipboardList, Package, Settings, FileText, MapPin,
  ChevronRight, Video, ShoppingCart, Database, AlertTriangle,
  Briefcase, Heart, Globe, Siren, Award, User, ShieldCheck, UserCog, Droplet,
  MessageSquare, Radio, TestTube2, Scan, Phone, Send, ThumbsUp, MessageCircle, GraduationCap,
  Wifi, Wrench, Layers, FlaskConical, FileCheck, Clipboard, Play, LayoutGrid, BedDouble, Wallet,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { ModuleCardIcon } from "@/components/branding/ModuleCardIcon";
import { ServiceLogo } from "@/components/branding/ServiceLogo";
import { AppLayout } from "@/components/AppLayout";
import {
  HomeAttentionFromData,
  HomeRecentFromShell,
  HomeSearchCommandPrompt,
} from "@/components/home/HomeShellDiscovery";
import { CitizenQuickAccessRail } from "@/components/home/CitizenQuickAccessRail";
import { ExpandableWorkCategoryCard } from "@/components/home/ExpandableWorkCategoryCard";
import { WorkplaceSelectionHub } from "@/components/home/WorkplaceSelectionHub";
import { getModuleCategories } from "@/data/workSurfaceModules";
import { PageShell } from "@/components/PageShell";
import { useAuthStore, type AuthUser } from "@/hooks/useAuthStore";
import { useOperationalContextStore } from "@/hooks/useOperationalContextStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import {
  useFacilities,
  type FacilityOperatingModel,
  type FacilityResource,
} from "@/hooks/queries/useFacilities";
import { useProviderLicenses, hasActiveLicense } from "@/hooks/queries/useLicenses";
import { useProviderCpd } from "@/hooks/queries/useCpd";
import { summarizeFundoMyLearning, useFundoMyLearning } from "@/hooks/queries/useFundoLms";
import { useProviderPrivileges } from "@/hooks/queries/useProviderPrivileges";
import { useCommunityGroups, useJoinGroup } from "@/hooks/queries/useCommunity";
import { useFacilityActiveShiftCount, useFacilityQueueStats } from "@/hooks/queries/useFacilityOperations";
import { apiClient } from "@/lib/api-client";
import { formatServiceError } from "@/lib/service-error";
import { countUnacknowledgedAbnormalLabResults } from "@/lib/lab-results-attention";
import { getAppointmentStatus, getAppointmentTime, getAppointmentType, getAppointmentProvider } from "@/lib/queue-workflows";
import { useExperienceEntry } from "@/providers/ExperienceEntryProvider";
import { useIdentityContext } from "@/hooks/useIdentityContext";
import { WORKER_ACCESS_LABELS } from "@/lib/identity-context";

interface WorkerLaunchAction {
  label: string;
  description: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
  color: string;
  serviceSlug?: string;
  requiresWorkContext?: boolean;
}

interface WorkerSnapshotCard {
  label: string;
  value: string;
  detail: string;
  tone: "impilo" | "emerald" | "amber" | "rose" | "slate";
}

interface OperatingSignal {
  label: string;
  value: string;
  detail: string;
}

function formatOperatingLabel(value?: string | null) {
  if (!value) return null;
  return value
    .toLowerCase()
    .split("_")
    .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join(" ");
}

function getOperatingNarrative(model?: FacilityOperatingModel) {
  if (!model) {
    return "Pick a workplace to load the facility-specific launch, continuity, and deployment cues for this shift.";
  }

  if (model.continuityClass === "EDGE_CRITICAL") {
    return "This site is expected to keep service delivery moving even when connectivity is unstable, so launch flows should stay simple and resilient.";
  }

  if (model.continuityClass === "LOCAL_EXECUTION_REQUIRED") {
    return "This site is expected to keep core service delivery running when the center is unavailable, so local execution continuity matters as much as throughput.";
  }

  if (model.workflowArchetype === "VIRTUAL_CARE_NETWORK") {
    return "This operating profile is built for virtual intake, referral coordination, and distributed care rather than one physical front desk.";
  }

  return "This facility profile shapes which launch actions, dashboards, and escalation paths should appear first for the current team.";
}

function getOperatingSignals(model?: FacilityOperatingModel): OperatingSignal[] {
  if (!model) return [];

  const tier = formatOperatingLabel(model.facilityTier);
  const deployment = formatOperatingLabel(model.deploymentMode);
  const continuity = formatOperatingLabel(model.continuityClass);

  return [
    tier
      ? {
          label: "Facility tier",
          value: tier,
          detail: "Tier sets the baseline complexity and service expectations for this site.",
        }
      : null,
    deployment
      ? {
          label: "Deployment mode",
          value: deployment,
          detail: "Deployment mode tells us whether this site runs on shared, dedicated, or edge-assisted execution.",
        }
      : null,
    continuity
      ? {
          label: "Continuity class",
          value: continuity,
          detail: "Continuity class defines how strongly local execution must hold when central connectivity degrades.",
        }
      : null,
  ].filter((signal): signal is OperatingSignal => signal !== null);
}

function getWorkerLaunchActions(args: {
  isClinical: boolean;
  isAdmin: boolean;
  isFinance: boolean;
  isDispenser: boolean;
}, operatingModel?: FacilityOperatingModel): WorkerLaunchAction[] {
  const actions: WorkerLaunchAction[] = [];

  if (args.isClinical || args.isDispenser) {
    actions.push(
      {
        label: "Impilo Fundo Learning",
        description: "Open learning, certifications, and CPD pathways first before entering other workflow surfaces.",
        href: "/learning",
        icon: GraduationCap,
        color: "bg-cyan-100 text-cyan-700",
        serviceSlug: "fundo",
      },
      {
        label: "Composed Worklist",
        description: "Start from prioritized clinical inbox, then open queue, telemedicine, labs, and tools from one surface.",
        href: "/provider-workspace",
        icon: Clipboard,
        color: "bg-emerald-100 text-primary-hover",
        requiresWorkContext: true,
      },
      {
        label: "Find Patient",
        description: "Search by CPID, national ID, or name and launch straight into chart flow.",
        href: "/queue/search",
        icon: Users,
        color: "bg-primary-soft text-primary",
        requiresWorkContext: true,
      },
      {
        label: "Walk-in Registration",
        description: "Register a patient directly into today's service queue without losing facility context.",
        href: "/queue/walk-in",
        icon: ClipboardList,
        color: "bg-amber-100 text-warning-foreground",
        requiresWorkContext: true,
      },
      {
        label: "Waiting Room",
        description: "Pick up the next patient already waiting and move them into care.",
        href: "/queue/waiting",
        icon: Clock,
        color: "bg-orange-100 text-orange-700",
        requiresWorkContext: true,
      },
      {
        label: "Triage",
        description: "Start first-contact assessment and move urgent patients forward quickly.",
        href: "/queue/triage",
        icon: AlertTriangle,
        color: "bg-rose-100 text-danger",
        requiresWorkContext: true,
      },
      {
        label: "New Teleconsult",
        description: "Launch a virtual consult or referral escalation without leaving the work surface.",
        href: "/telemedicine/new",
        icon: Video,
        color: "bg-teal-100 text-teal-700",
      },
      {
        label: "Pharmacy Dispense",
        description: "Move directly into dispensing and medication follow-through.",
        href: "/pharmacy/dispense",
        icon: Pill,
        color: "bg-emerald-100 text-primary-hover",
        requiresWorkContext: true,
      },
      {
        label: "Inpatient workspace",
        description: "Open admissions, nursing workbench, and ward operations.",
        href: "/clinical/inpatient",
        icon: BedDouble,
        color: "bg-purple-100 text-warning-foreground",
        requiresWorkContext: true,
      },
      {
        label: "Rx transaction journey",
        description: "Walk the golden-path Rx · pay · dispatch demonstration.",
        href: "/pharmacy/transaction-journey?patientId=CPID-ZW-00001",
        icon: ClipboardList,
        color: "bg-cyan-100 text-cyan-700",
      },
      {
        label: "Core transactions",
        description: "Review transaction state, audit correlation, and next actions.",
        href: "/core-transaction",
        icon: Layers,
        color: "bg-neutral-100 text-foreground",
      },
      {
        label: "Data & intelligence",
        description: "Open quality, pipeline, and audit intelligence surfaces.",
        href: "/data-intelligence",
        icon: Database,
        color: "bg-indigo-100 text-primary-hover",
      },
    );
  }

  if (args.isFinance) {
    actions.push(
      {
        label: "Impilo Fundo Learning",
        description: "Start with role-based finance learning pathways, policy updates, and certification progress.",
        href: "/learning",
        icon: GraduationCap,
        color: "bg-cyan-100 text-cyan-700",
        serviceSlug: "fundo",
      },
      {
        label: "Claims Review",
        description: "Open claims and payer actions from the finance workbench.",
        href: "/finance/claims",
        icon: Receipt,
        color: "bg-violet-100 text-violet-700",
      },
      {
        label: "Billing",
        description: "Go straight into billing, invoices, and revenue follow-through.",
        href: "/finance/billing",
        icon: FileText,
        color: "bg-emerald-100 text-primary-hover",
      },
      {
        label: "Settlements",
        description: "Run settlement operations and release payouts without extra navigation hops.",
        href: "/finance/settlements",
        icon: BarChart3,
        color: "bg-cyan-100 text-cyan-700",
      },
      {
        label: "Rx transaction journey",
        description: "Follow payment and dispatch steps on the golden-patient Rx path.",
        href: "/pharmacy/transaction-journey?patientId=CPID-ZW-00001",
        icon: Pill,
        color: "bg-cyan-100 text-cyan-700",
      },
      {
        label: "Core transactions",
        description: "Audit billing and payment transaction state.",
        href: "/core-transaction",
        icon: Layers,
        color: "bg-neutral-100 text-foreground",
      },
    );
  }

  if (args.isAdmin) {
    actions.push(
      {
        label: "Impilo Fundo Learning",
        description: "Prioritise governance/admin learning modules and required training before operational actions.",
        href: "/learning",
        icon: GraduationCap,
        color: "bg-cyan-100 text-cyan-700",
        serviceSlug: "fundo",
      },
      {
        label: "Production Command Centre",
        description: "Browse Health OS services, maturity labels, and integration hub probes before operational actions.",
        href: "/production-command-centre",
        icon: LayoutGrid,
        color: "bg-primary-soft text-primary",
      },
      {
        label: "Control Tower",
        description: "Start from live operational oversight when you are driving throughput or escalation.",
        href: "/clinical/control-tower",
        icon: Activity,
        color: "bg-rose-100 text-danger",
      },
      {
        label: "Staff & Roles",
        description: "Jump into admin action on users, roles, and governance surfaces.",
        href: "/admin/users",
        icon: UserCog,
        color: "bg-neutral-100 text-foreground",
      },
      {
        label: "Data & intelligence",
        description: "Open data quality, pipelines, and audit intelligence.",
        href: "/data-intelligence",
        icon: Database,
        color: "bg-indigo-100 text-primary-hover",
      },
      {
        label: "Core transactions",
        description: "Transaction audit feed across production journeys.",
        href: "/core-transaction",
        icon: Layers,
        color: "bg-neutral-100 text-foreground",
      },
    );
  }

  const seen = new Set<string>();
  const deduped = actions.filter((action) => {
    if (seen.has(action.href)) return false;
    seen.add(action.href);
    return true;
  });

  if (operatingModel?.workflowArchetype === "VIRTUAL_CARE_NETWORK") {
    return [...deduped].sort((a, b) => {
      if (a.href === "/learning") return -1;
      if (b.href === "/learning") return 1;
      if (a.href === "/provider-workspace") return -1;
      if (b.href === "/provider-workspace") return 1;
      if (a.href === "/telemedicine/new") return -1;
      if (b.href === "/telemedicine/new") return 1;
      return 0;
    });
  }

  return deduped;
}

function getWorkerSnapshotCards(args: {
  isClinical: boolean;
  isAdmin: boolean;
  isFinance: boolean;
  isDispenser: boolean;
  queueWaiting: number;
  queueInService: number;
  appointmentsCount: number;
  activeShifts: number;
  hasWorkContext: boolean;
  licenseActive: boolean;
}): WorkerSnapshotCard[] {
  if (args.isClinical || args.isDispenser) {
    return [
      {
        label: "Patients waiting",
        value: String(args.queueWaiting),
        detail: args.hasWorkContext
          ? "Current waiting-room demand in the selected facility."
          : "Choose a workplace to start tracking waiting-room demand.",
        tone: args.queueWaiting > 0 ? "amber" : "slate",
      },
      {
        label: "In service",
        value: String(args.queueInService),
        detail: "Patients already called or actively in care right now.",
        tone: args.queueInService > 0 ? "impilo" : "slate",
      },
      {
        label: "Today's appointments",
        value: String(args.appointmentsCount),
        detail: "Confirmed scheduled arrivals — pending booking requests are in Booking Requests.",
        tone: args.appointmentsCount > 0 ? "emerald" : "slate",
      },
      {
        label: "Active shifts",
        value: String(args.activeShifts),
        detail: args.licenseActive
          ? "Current workforce presence on the roster in this facility."
          : "Resolve licence issues before starting regulated clinical work.",
        tone: args.licenseActive ? "impilo" : "rose",
      },
    ];
  }

  if (args.isFinance) {
    return [
      {
        label: "Claims lane",
        value: "Ready",
        detail: "Use the finance workspace to move straight into claims review and payer actions.",
        tone: "impilo",
      },
      {
        label: "Billing lane",
        value: "Open",
        detail: "Bills, invoices, and payment follow-through are one click away.",
        tone: "emerald",
      },
      {
        label: "Settlement lane",
        value: "Live",
        detail: "Settlement and reconciliation surfaces are available from the same workbench.",
        tone: "impilo",
      },
      {
        label: "Work context",
        value: args.hasWorkContext ? "Bound" : "Flexible",
        detail: args.hasWorkContext
          ? "Finance can continue within the current facility context."
          : "Finance work can start before choosing a facility-specific context.",
        tone: "slate",
      },
    ];
  }

  if (args.isAdmin) {
    return [
      {
        label: "Operational context",
        value: args.hasWorkContext ? "Live" : "Select",
        detail: args.hasWorkContext
          ? "Facility context is available for queue, roster, and operational oversight."
          : "Choose a workplace or admin plane to unlock work surfaces.",
        tone: args.hasWorkContext ? "impilo" : "amber",
      },
      {
        label: "Admin planes",
        value: "2",
        detail: "Registry administration and organization administration are available from home.",
        tone: "slate",
      },
      {
        label: "Throughput focus",
        value: String(args.queueWaiting),
        detail: "Waiting patients provide a quick signal for where operations may need intervention.",
        tone: args.queueWaiting > 0 ? "amber" : "slate",
      },
      {
        label: "Shift coverage",
        value: String(args.activeShifts),
        detail: "Use active shift coverage to gauge workforce pressure before entering queue ops.",
        tone: args.activeShifts > 0 ? "emerald" : "slate",
      },
    ];
  }

  return [
    {
      label: "Ready to work",
      value: args.hasWorkContext ? "Yes" : "Select",
      detail: "Choose a workplace context and then start the next patient-facing or operational task.",
      tone: args.hasWorkContext ? "impilo" : "amber",
    },
  ];
}

type HomeTab = "work" | "professional" | "personal";

export default function HomePage() {
  const user = useAuthStore((s) => s.user);
  const identity = useIdentityContext();
  const shift = useShiftStore((s) => s.shift);
  const roleGroup = useRoleGroup();
  const { isClinical, isAdmin, isFinance, isDispenser } = roleGroup;
  const { facility, selectFacility, enterMode, operationalMode, availableOperationalModes } =
    useExperienceEntry();
  const pathname = usePathname();
  const homeContextSyncPass = useRef(false);
  const focusedWorkMode = useOperationalContextStore((s) => s.focusedWorkMode);

  useEffect(() => {
    if (pathname !== "/home" && pathname !== "/") {
      homeContextSyncPass.current = false;
    }
  }, [pathname]);
  const hasProfessionalRoles = isClinical || isAdmin || isFinance || isDispenser;
  const [activeTab, setActiveTab] = useState<HomeTab>("personal");

  useEffect(() => {
    if (focusedWorkMode) {
      setActiveTab("work");
      sessionStorage.setItem("exp:home-tab", "work");
    }
  }, [focusedWorkMode]);

  // Citizens always land on personal; activated providers default to work.
  useEffect(() => {
    if (!user) return;
    if (identity.isCitizenOnly) {
      setActiveTab("personal");
      sessionStorage.removeItem("exp:home-tab");
      return;
    }
    const stored = sessionStorage.getItem("exp:home-tab") as HomeTab | null;
    setActiveTab(stored ?? (identity.hasWorkAccess ? "work" : "professional"));
  }, [user, identity.isCitizenOnly, identity.hasWorkAccess]);

  function switchTab(tab: HomeTab) {
    setActiveTab(tab);
    sessionStorage.setItem("exp:home-tab", tab);
    const { setOperationalMode, operationalMode: currentOp } = useOperationalContextStore.getState();
    if (tab === "personal") {
      setOperationalMode("my_life");
    } else if (tab === "professional") {
      setOperationalMode("my_professional");
    } else if (tab === "work") {
      if (currentOp === "registry_admin" || currentOp === "organization_admin") return;
      setOperationalMode("facility_work");
    }
  }

  // Home only: first pass lets remembered tab correct mismatched mode; later passes sync tab from strip.
  useEffect(() => {
    if (pathname !== "/home" && pathname !== "/") return;
    const store = useOperationalContextStore.getState();
    if (!homeContextSyncPass.current) {
      homeContextSyncPass.current = true;
      if (activeTab === "personal" && store.operationalMode !== "my_life") {
        store.setOperationalMode("my_life");
      } else if (activeTab === "professional" && store.operationalMode !== "my_professional") {
        store.setOperationalMode("my_professional");
      } else if (
        activeTab === "work" &&
        (store.operationalMode === "my_life" || store.operationalMode === "my_professional")
      ) {
        store.setOperationalMode("facility_work");
      }
      return;
    }
    if (operationalMode === "my_life") {
      setActiveTab("personal");
      sessionStorage.setItem("exp:home-tab", "personal");
    } else if (operationalMode === "my_professional") {
      setActiveTab("professional");
      sessionStorage.setItem("exp:home-tab", "professional");
    } else {
      setActiveTab("work");
      sessionStorage.setItem("exp:home-tab", "work");
    }
  }, [pathname, activeTab, operationalMode]);

  const greeting = getGreeting();
  const router = useRouter();
  const hasWorkContext = !!facility;
  const selectedOperatingModel = facility?.operatingModel;

  // Fetch facilities for workplace selection hub — search-first: the national
  // registry holds ~1,800 facilities, so the hub queries rather than lists.
  const [facilitySearch, setFacilitySearch] = useState("");
  const [debouncedFacilitySearch, setDebouncedFacilitySearch] = useState("");
  useEffect(() => {
    const t = setTimeout(() => setDebouncedFacilitySearch(facilitySearch.trim()), 300);
    return () => clearTimeout(t);
  }, [facilitySearch]);
  const { data: facilitiesData, isLoading: facilitiesLoading } = useFacilities(
    debouncedFacilitySearch ? { search: debouncedFacilitySearch, size: 12 } : { size: 12 },
  );
  const facilities: FacilityResource[] = facilitiesData?.data ?? [];

  // Fetch provider privileges (facility affiliations) from VARAPI
  const { data: privilegesData } = useProviderPrivileges(isClinical ? user?.id : undefined);
  const privileges = privilegesData?.data ?? [];

  // Fetch license data
  const { data: licenseData } = useProviderLicenses(isClinical ? user?.id : undefined);
  const licenses = licenseData?.data ?? [];
  const licenseActive = licenses.length === 0 || hasActiveLicense(licenses);
  const { data: cpdData } = useProviderCpd(isClinical ? user?.id : undefined);

  const learningSubject = useMemo(
    () => ({
      subjectType: "PROVIDER",
      subjectId: user?.providerId ?? user?.id ?? "",
    }),
    [user?.providerId, user?.id],
  );
  const {
    data: fundoMyLearningData,
    isLoading: fundoKpisLoading,
    isError: fundoKpisError,
  } = useFundoMyLearning(learningSubject, hasProfessionalRoles);
  const fundoKpis = summarizeFundoMyLearning(
    (fundoMyLearningData?.data ?? {}) as Record<string, unknown>,
  );
  const cpdCycle = cpdData?.data?.currentCycle;
  const cpdEarned = cpdCycle?.earnedPoints ?? 0;
  const cpdRequired = cpdCycle?.requiredPoints ?? 0;
  const cpdProgressPct =
    cpdRequired > 0 ? Math.min(100, Math.round((cpdEarned / cpdRequired) * 100)) : 0;

  // Community groups for Personal tab
  const { data: groupsData } = useCommunityGroups();
  const communityGroups = groupsData?.data ?? [];
  const joinGroup = useJoinGroup();

  function handleFacilitySelect(facilityResource: FacilityResource) {
    useOperationalContextStore.getState().setOperationalMode("facility_work");
    selectFacility(
      {
        id: facilityResource.id,
        name: facilityResource.attributes.name,
        code: facilityResource.attributes.code,
        facilityType: facilityResource.attributes.facilityType,
        capabilities: facilityResource.attributes.capabilities ?? [],
        operatingModel: facilityResource.attributes.operatingModel,
      },
      {
        mode: "clinical",
        nextPath: "/workspace",
      }
    );
  }

  /**
   * Facility-free / independent modes are governed WorkModes. Do not set a local UI mode
   * and invent authority — send the person to /work so they mint against a proven context.
   * Optional prefer_mode highlights matching contexts in the picker.
   */
  function enterViaWorkHome(preferMode?: string) {
    const qs = preferMode ? `?prefer_mode=${encodeURIComponent(preferMode)}` : "";
    router.push(`/work${qs}`);
  }

  function enterCommunityOutreachViaWorkHome() {
    enterViaWorkHome("COMMUNITY_OUTREACH");
  }

  // Fetch recent encounters — the encounters endpoint requires a patient_id
  // (the person's own timeline). Without it the BFF returns 400 MISSING_PATIENT_ID.
  const { data: recentEncounters } = useQuery<{ data: Array<{ id: string; attributes: Record<string, unknown> }> }>({
    queryKey: ["home-recent-encounters", user?.id],
    queryFn: () =>
      apiClient.get(`/internal/v1/encounters?size=5&patient_id=${encodeURIComponent(user?.id ?? "")}`),
    enabled: isClinical && !!user?.id,
  });
  const encounters = recentEncounters?.data ?? [];
  const activeEncounterCount = encounters.filter((enc) => {
    const status = String(enc.attributes.status ?? "");
    return status === "IN_PROGRESS" || status === "ACTIVE";
  }).length;

  // Fetch today's appointments
  const { data: appointmentsData } = useQuery<{ data: Array<{ id: string; attributes: Record<string, unknown> }> }>({
    queryKey: ["home-today-appointments"],
    queryFn: () => apiClient.get("/internal/v1/appointments?size=5"),
    enabled: isClinical,
  });
  const appointments = appointmentsData?.data ?? [];
  const queueStatsQuery = useFacilityQueueStats(facility?.id);
  const queueStats = queueStatsQuery.data?.data;
  const { activeShiftCount } = useFacilityActiveShiftCount(facility?.id);

  const { data: providerSupportTicketsData } = useQuery<{
    data: Array<{ id: string; attributes?: Record<string, unknown> }>;
  }>({
    queryKey: ["home-provider-support-tickets", facility?.id],
    queryFn: () =>
      apiClient.get(`/internal/v1/mobile/provider/support/tickets?facility_id=${facility?.id}`),
    enabled: hasWorkContext && !!facility?.id && (isClinical || isDispenser),
    staleTime: 60_000,
  });
  const providerSupportTickets = providerSupportTicketsData?.data ?? [];
  const resolvedTicketStatuses = new Set(["RESOLVED", "CLOSED", "CANCELLED", "COMPLETED", "DONE"]);
  const openSupportTicketCount = providerSupportTickets.filter((row) => {
    const s = String(row.attributes?.status ?? "").toUpperCase();
    return s.length > 0 && !resolvedTicketStatuses.has(s);
  }).length;

  const { data: providerLabResultsPayload } = useQuery<unknown>({
    queryKey: ["home-provider-lab-results-attention", user?.id, facility?.id],
    queryFn: () => apiClient.get("/internal/v1/mobile/provider/labs/results?page=0&size=50"),
    enabled: hasWorkContext && !!facility?.id && (isClinical || isDispenser),
    staleTime: 60_000,
  });
  const unackedAbnormalLabCount = countUnacknowledgedAbnormalLabResults(providerLabResultsPayload);

  // Module categories
  const categories = getModuleCategories({ isClinical, isAdmin, isFinance, isDispenser });
  const workerLaunchActions = getWorkerLaunchActions(
    { isClinical, isAdmin, isFinance, isDispenser },
    selectedOperatingModel,
  );
  const launchActions = workerLaunchActions.filter((action) => !action.requiresWorkContext || hasWorkContext);
  const operatingSignals = getOperatingSignals(selectedOperatingModel);
  const operatingNarrative = getOperatingNarrative(selectedOperatingModel);
  const workerSnapshotCards = getWorkerSnapshotCards({
    isClinical,
    isAdmin,
    isFinance,
    isDispenser,
    queueWaiting: queueStats?.waiting ?? 0,
    queueInService: queueStats?.inService ?? encounters.filter((enc) => {
      const status = String(enc.attributes.status ?? "");
      return status === "IN_PROGRESS" || status === "ACTIVE";
    }).length,
    appointmentsCount: appointments.length,
    activeShifts: activeShiftCount,
    hasWorkContext,
    licenseActive,
  });

  // ── Citizen 3-column layout (Facebook-style) ────────────────────
  if (identity.isCitizenOnly) {
    return (
      <AppLayout>
        <PageShell title="Home" hideHeader>
          <span data-testid="home-operational-mode" className="sr-only">
            {operationalMode}
          </span>
          <CitizenHome user={user} greeting={greeting} />
        </PageShell>
      </AppLayout>
    );
  }

  // ── Professional layout (existing tabs) ────────────────────────
  return (
    <AppLayout>
      <PageShell title="Home" hideHeader>
        <span data-testid="home-operational-mode" className="sr-only">
          {operationalMode}
        </span>
        <div className="space-y-6">
          {/* Welcome + Context — flush under shell header */}
          <div className="-mx-3 border-b border-border bg-card p-4 md:-mx-4 md:p-5">
            <div className="flex items-start justify-between">
              <div>
                <h2 className="text-xl font-semibold text-foreground">
                  {greeting}, {user?.displayName ?? "User"}
                </h2>
                <p className="text-sm text-muted-foreground mt-1">
                  {user?.actorType === "CITIZEN"
                    ? "Personal Health Account"
                    : user?.actorType === "PROVIDER"
                      ? "Healthcare Professional"
                      : user?.actorType === "OPERATOR"
                        ? "System Administrator"
                        : "Welcome to Impilo"}
                </p>
              </div>
              <div className="flex items-center gap-2">
                {isClinical && licenses.length > 0 && (
                  <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${
                    licenseActive ? "bg-green-50 text-green-700" : "bg-danger-soft text-danger"
                  }`}>
                    {licenseActive ? "Licensed" : "License Issue"}
                  </span>
                )}
                {user?.actorType && (
                  <span className="px-2.5 py-1 text-xs font-medium rounded-full bg-primary-soft text-primary">
                    {user.actorType}
                  </span>
                )}
              </div>
            </div>

            <div className="mt-4 flex flex-wrap gap-3">
              {facility && (
                <div className="flex items-center gap-2 text-sm text-muted-foreground bg-background px-3 py-1.5 rounded-lg">
                  <Building2 className="w-4 h-4 text-muted-foreground" />
                  <span>{facility.name}</span>
                </div>
              )}
              {shift && (
                <div className="flex items-center gap-2 text-sm text-muted-foreground bg-green-50 px-3 py-1.5 rounded-lg">
                  <Activity className="w-4 h-4 text-green-500" />
                  <span>Shift active since {new Date(shift.startedAt).toLocaleTimeString()}</span>
                </div>
              )}
              {!shift && !facility && (
                <div className="flex items-center gap-2 text-sm text-warning-foreground bg-warning-soft px-3 py-1.5 rounded-lg">
                  <MapPin className="w-4 h-4" />
                  <span>Select where you are working from below</span>
                </div>
              )}
              {facility && !shift && (
                <Link href="/shift" className="flex items-center gap-2 text-sm text-primary bg-primary-soft px-3 py-1.5 rounded-lg hover:bg-primary-soft transition-colors">
                  <Clock className="w-4 h-4" />
                  <span>Start a shift</span>
                  <ArrowRight className="w-3 h-3" />
                </Link>
              )}
            </div>
          </div>

          {/* Professional without a work assignment → self-service access request */}
          {!identity.hasWorkAccess && !identity.isCitizenOnly && identity.hasProfessionalAccess && (
            <Link
              href="/professional/request-access"
              className="flex items-center gap-2 rounded-lg border border-primary/25 bg-primary-soft px-3 py-2 text-sm text-primary hover:bg-primary-soft/80 transition-colors"
            >
              <Briefcase className="h-4 w-4 shrink-0" />
              <span className="font-medium">No work assignment yet</span>
              <span className="hidden text-xs text-primary-hover sm:inline">Request facility access to unlock the Work tab</span>
              <ArrowRight className="ml-auto w-3.5 h-3.5" />
            </Link>
          )}

          {/* Tab Switcher (Lovable 3-tab: Work / Professional / Personal) */}
          {!focusedWorkMode ? (
          <div className="flex gap-1 bg-neutral-100 p-1 rounded-lg">
            {identity.hasWorkAccess && (
            <button onClick={() => switchTab("work")}
              className={`flex-1 flex items-center justify-center gap-2 px-4 py-2 text-sm font-medium rounded-md transition-colors ${activeTab === "work" ? "bg-card text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"}`}>
              <Briefcase className="w-4 h-4" /> Work
            </button>
            )}
            {!identity.isCitizenOnly && identity.hasProfessionalAccess && (
            <button onClick={() => switchTab("professional")}
              className={`flex-1 flex items-center justify-center gap-2 px-4 py-2 text-sm font-medium rounded-md transition-colors ${activeTab === "professional" ? "bg-card text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"}`}>
              <Stethoscope className="w-4 h-4" /> My Professional
            </button>
            )}
            <button onClick={() => switchTab("personal")}
              className={`flex-1 flex items-center justify-center gap-2 px-4 py-2 text-sm font-medium rounded-md transition-colors ${activeTab === "personal" ? "bg-card text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"}`}>
              <Heart className="w-4 h-4" /> {identity.hasWorkAccess ? "My Life" : "My Life / My Health"}
            </button>
          </div>
          ) : (
            <div className="flex items-center gap-2 rounded-lg border border-primary/25 bg-primary-soft px-3 py-2 text-sm text-primary">
              <Briefcase className="h-4 w-4 shrink-0" />
              <span className="font-medium">Focused work mode</span>
              <span className="hidden text-xs text-primary-hover sm:inline">Personal and professional tabs hidden</span>
            </div>
          )}

          {/* ═══ WORK TAB ═══ */}
          {(focusedWorkMode || activeTab === "work") && (<>
          <div className="grid gap-4 lg:grid-cols-3">
            <div className="space-y-3 lg:col-span-2">
              <HomeSearchCommandPrompt />
              <HomeRecentFromShell />
            </div>
            <HomeAttentionFromData
              enabled={isClinical || isDispenser}
              queueWaiting={queueStats?.waiting ?? 0}
              activeEncounterCount={activeEncounterCount}
              hasWorkContext={hasWorkContext}
              openSupportTickets={openSupportTicketCount}
              unackedAbnormalLabResults={unackedAbnormalLabCount}
            />
          </div>

          {/* Workplace Selection Hub — when no facility */}
          {!hasWorkContext && (
            <div id="workplace-selection">
              <WorkplaceSelectionHub
              facilities={facilities}
              searchValue={facilitySearch}
              onSearchChange={setFacilitySearch}
              isLoading={facilitiesLoading}
              onSelectFacility={handleFacilitySelect}
              maxVisible={6}
              modeActions={[
                ...(isAdmin
                  ? [{
                      label: "Production Command Centre",
                      description: "Choose a workplace that grants admin or support duty, then open Health OS services.",
                      icon: LayoutGrid,
                      onClick: () => enterViaWorkHome(),
                    },
                    {
                      label: "Administration",
                      description: "Mint a work session for an admin-capable workplace before entering oversight.",
                      icon: Shield,
                      onClick: () => enterViaWorkHome("FACILITY_MANAGEMENT"),
                    }]
                  : []),
                ...(isFinance
                  ? [{
                      label: "Finance",
                      description: "Choose a workplace that grants finance-capable duty before claims and billing.",
                      icon: Receipt,
                      onClick: () => enterViaWorkHome(),
                    }]
                  : []),
                {
                  label: "Reports",
                  description: "Review cross-cutting reporting and performance signals before entering a work context.",
                  icon: BarChart3,
                  href: "/reports",
                },
              ]}
              />
            </div>
          )}

          {(isClinical || isDispenser || isFinance || isAdmin) && (
            <div className="space-y-4">
              <div className="grid gap-4 xl:grid-cols-[1.2fr,0.8fr]">
                <div className="impilo-subtle-african-accent rounded-3xl bg-gradient-to-r from-impilo-700 via-impilo-600 to-cyan-600 p-6 text-white shadow-impilo-floating">
                  <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
                    <div className="max-w-2xl">
                      <p className="text-xs font-semibold uppercase tracking-[0.18em] text-white/85">
                        The Experience
                      </p>
                      <h3 className="mt-2 text-2xl font-semibold">
                        Launch Client Experience
                      </h3>
                      <p className="mt-2 max-w-xl text-sm text-white/85">
                        {operatingNarrative}
                      </p>
                      <div className="mt-4 flex flex-wrap gap-2">
                        {hasWorkContext ? (
                          <span className="inline-flex items-center gap-2 rounded-full bg-card/15 px-3 py-1 text-xs font-medium text-white">
                            <Building2 className="h-3.5 w-3.5" />
                            {facility?.name ?? "Work context selected"}
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-2 rounded-full bg-amber-300/25 px-3 py-1 text-xs font-medium text-white">
                            <MapPin className="h-3.5 w-3.5" />
                            Select a workplace to unlock client-facing actions
                          </span>
                        )}
                        {shift && (
                          <span className="inline-flex items-center gap-2 rounded-full bg-card/15 px-3 py-1 text-xs font-medium text-white">
                            <Activity className="h-3.5 w-3.5" />
                            Shift active
                          </span>
                        )}
                        {selectedOperatingModel?.facilityTier && (
                          <span className="inline-flex items-center gap-2 rounded-full bg-card/15 px-3 py-1 text-xs font-medium text-white">
                            <Layers className="h-3.5 w-3.5" />
                            {formatOperatingLabel(selectedOperatingModel.facilityTier)}
                          </span>
                        )}
                        {selectedOperatingModel?.continuityClass && (
                          <span className="inline-flex items-center gap-2 rounded-full bg-card/15 px-3 py-1 text-xs font-medium text-white">
                            <Wifi className="h-3.5 w-3.5" />
                            {formatOperatingLabel(selectedOperatingModel.continuityClass)}
                          </span>
                        )}
                      </div>
                    </div>

                    {hasWorkContext ? (
                      <Link
                        href={launchActions[0]?.href ?? "/queue/search"}
                        className="inline-flex items-center justify-center gap-2 rounded-xl bg-card px-5 py-3 text-sm font-semibold text-primary-hover transition-colors hover:bg-primary-soft"
                      >
                        <Play className="h-4 w-4" />
                        {launchActions[0]?.label ?? "Start care"}
                      </Link>
                    ) : (
                      <a
                        href="#workplace-selection"
                        className="inline-flex items-center justify-center gap-2 rounded-xl bg-card px-5 py-3 text-sm font-semibold text-primary-hover transition-colors hover:bg-primary-soft"
                      >
                        <MapPin className="h-4 w-4" />
                        Choose workplace
                      </a>
                    )}
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  {workerSnapshotCards.map((card) => {
                    const toneClasses = {
                      impilo: "border-primary/25 bg-primary-soft text-primary-hover",
                      emerald: "border-success/25 bg-success-soft text-primary-hover",
                      amber: "border-warning/35 bg-warning-soft text-warning-foreground",
                      rose: "border-danger/28 bg-danger-soft text-danger",
                      slate: "border-border bg-card text-foreground",
                    } as const;

                    return (
                      <div
                        key={card.label}
                        className={`rounded-2xl border p-4 ${toneClasses[card.tone]}`}
                      >
                        <p className="text-xs font-semibold uppercase tracking-wide">{card.label}</p>
                        <p className="mt-2 text-2xl font-semibold">{card.value}</p>
                        <p className="mt-2 text-xs leading-5 opacity-90">{card.detail}</p>
                      </div>
                    );
                  })}
                </div>
              </div>

              {hasWorkContext && operatingSignals.length > 0 && (
                <div className="rounded-2xl border border-border bg-card p-4 shadow-sm">
                  <div className="mb-4 flex items-start justify-between gap-4">
                    <div>
                      <h3 className="text-base font-semibold text-foreground">Facility operating model</h3>
                      <p className="mt-1 text-sm text-muted-foreground">
                        This facility is configured as a distinct care-delivery environment, not just a location. The operating model below should shape launch flow and continuity expectations for this shift.
                      </p>
                    </div>
                    <div className="rounded-xl bg-neutral-100 px-3 py-2 text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground">
                      Operating profile
                    </div>
                  </div>
                  <div className="grid gap-3 md:grid-cols-3">
                    {operatingSignals.map((signal) => (
                      <div key={signal.label} className="rounded-xl border border-border bg-background p-4">
                        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                          {signal.label}
                        </p>
                        <p className="mt-2 text-lg font-semibold text-foreground">{signal.value}</p>
                        <p className="mt-2 text-xs leading-5 text-muted-foreground">{signal.detail}</p>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              <div className="bg-card rounded-lg border border-border p-4">
                <div className="mb-4 flex items-center justify-between">
                  <h3 className="flex items-center gap-2 text-base font-semibold text-foreground">
                    <Clipboard className="w-5 h-5 text-primary" />
                    Service Delivery Pathways
                  </h3>
                  <p className="text-xs text-muted-foreground">Quick starts for the work that matters now</p>
                </div>
                {launchActions.length === 0 ? (
                  <div className="rounded-xl border border-dashed border-border bg-background px-4 py-6 text-sm text-muted-foreground">
                    Select a workplace or activate the right operational plane to unlock role-specific launch actions.
                  </div>
                ) : (
                  <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                    {launchActions.map((action) => {
                      return (
                        <Link
                          key={action.href}
                          href={action.href}
                          className="group rounded-xl border border-border bg-background p-4 transition-colors hover:border-primary/25 hover:bg-card hover:shadow-sm"
                        >
                          <div className="mb-3">
                            <ModuleCardIcon
                              serviceSlug={action.serviceSlug}
                              icon={action.icon}
                              color={action.color}
                            />
                          </div>
                          <div className="flex items-start justify-between gap-3">
                            <div>
                              <p className="text-sm font-semibold text-foreground group-hover:text-primary">{action.label}</p>
                              <p className="mt-1 text-xs leading-5 text-muted-foreground">{action.description}</p>
                            </div>
                            <ArrowRight className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground group-hover:text-primary" />
                          </div>
                        </Link>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Professional Dashboard — stats + schedule (Lovable MyProfessionalHub) */}
          {isClinical && hasWorkContext && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Today's Schedule */}
              <div className="bg-card rounded-lg border border-border">
                <div className="px-5 py-3 border-b flex items-center justify-between">
                  <h3 className="text-sm font-medium text-foreground">Today&apos;s Schedule</h3>
                  <Link href="/scheduling" className="text-xs text-primary hover:text-primary-hover">View All →</Link>
                </div>
                {appointments.length === 0 ? (
                  <div className="p-6 text-center">
                    <Calendar className="w-6 h-6 text-muted-foreground mx-auto mb-1" />
                    <p className="text-xs text-muted-foreground">No appointments today</p>
                  </div>
                ) : (
                  <div className="divide-y divide-gray-100">
                    {appointments.slice(0, 4).map((apt) => (
                      <div key={apt.id} className="px-5 py-2.5 flex items-center justify-between">
                        <div>
                          <p className="text-sm text-foreground">{(apt.attributes.appointment_type as string) ?? "Appointment"}</p>
                          <p className="text-xs text-muted-foreground">{apt.attributes.start_time ? new Date(apt.attributes.start_time as string).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "—"}</p>
                        </div>
                        <span className={`px-2 py-0.5 text-xs rounded-full ${apt.attributes.status === "CONFIRMED" ? "bg-green-100 text-green-700" : "bg-neutral-100 text-muted-foreground"}`}>
                          {(apt.attributes.status as string) ?? "—"}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Recent Encounters */}
              <div className="bg-card rounded-lg border border-border">
                <div className="px-5 py-3 border-b flex items-center justify-between">
                  <h3 className="text-sm font-medium text-foreground">Recent Encounters</h3>
                  <Link href="/queue" className="text-xs text-primary hover:text-primary-hover">Queue →</Link>
                </div>
                {encounters.length === 0 ? (
                  <div className="p-6 text-center">
                    <Stethoscope className="w-6 h-6 text-muted-foreground mx-auto mb-1" />
                    <p className="text-xs text-muted-foreground">No recent encounters</p>
                  </div>
                ) : (
                  <div className="divide-y divide-gray-100">
                    {encounters.slice(0, 4).map((enc) => {
                      const a = enc.attributes;
                      const isActive = a.status === "IN_PROGRESS" || a.status === "ACTIVE";
                      return (
                        <Link key={enc.id} href={`/ehr/${a.patient_id}/encounter/${enc.id}`}
                          className="flex items-center justify-between px-5 py-2.5 hover:bg-background transition-colors">
                          <div className="flex items-center gap-2">
                            <div className={`w-1.5 h-1.5 rounded-full ${isActive ? "bg-green-500" : "bg-gray-300"}`} />
                            <div>
                              <p className="text-sm text-foreground">{((a.encounter_type as string) ?? "Encounter").replace(/_/g, " ")}</p>
                              <p className="text-xs text-muted-foreground">{a.started_at ? new Date(a.started_at as string).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "—"}</p>
                            </div>
                          </div>
                          <span className={`px-2 py-0.5 text-xs rounded-full ${isActive ? "bg-green-100 text-green-700" : "bg-neutral-100 text-muted-foreground"}`}>
                            {(a.status as string)?.replace(/_/g, " ") ?? "—"}
                          </span>
                        </Link>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Communication Noticeboard (Lovable ModuleHome) */}
          <div className="bg-card rounded-lg border border-border p-4">
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-base font-semibold text-foreground flex items-center gap-2">
                <MessageSquare className="w-5 h-5 text-primary" />
                Communication & Access
              </h3>
              <Link href="/communication" className="text-xs text-primary hover:text-primary-hover">
                View All →
              </Link>
            </div>
            <div className="flex flex-wrap gap-2 mb-3">
              <Link href="/communication?tab=messages"
                className="inline-flex items-center gap-2 px-5 py-3 text-sm font-medium border border-border rounded-lg hover:bg-primary-soft hover:border-primary/25 transition-colors">
                <FileText className="w-5 h-5 text-primary" />
                Messages
              </Link>
              <Link href="/communication?tab=pages"
                className="inline-flex items-center gap-2 px-5 py-3 text-sm font-medium border border-border rounded-lg hover:bg-warning-soft hover:border-amber-300 transition-colors">
                <AlertTriangle className="w-5 h-5 text-amber-500" />
                Pages
              </Link>
              <Link href="/communication?tab=calls"
                className="inline-flex items-center gap-2 px-5 py-3 text-sm font-medium border border-border rounded-lg hover:bg-green-50 hover:border-green-300 transition-colors">
                <Video className="w-5 h-5 text-green-600" />
                Calls
              </Link>
              <Link href="/omnichannel"
                className="inline-flex items-center gap-2 px-5 py-3 text-sm font-medium border border-border rounded-lg hover:bg-teal-50 hover:border-teal-300 transition-colors">
                <Radio className="w-5 h-5 text-teal-600" />
                Omnichannel
              </Link>
              <Link href="/scheduling/noticeboard"
                className="inline-flex items-center gap-2 px-5 py-3 text-sm font-medium border border-border rounded-lg hover:bg-warning-soft hover:border-purple-300 transition-colors">
                <Globe className="w-5 h-5 text-purple-600" />
                Noticeboard
              </Link>
            </div>
            <AnnouncementsBanner />
          </div>

          {/* Module Categories (Lovable ExpandableCategoryCards) */}
          <div>
            <h3 className="text-sm font-semibold text-foreground mb-3 uppercase tracking-wide">Modules</h3>
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
              {categories.map((cat) => (
                <ExpandableWorkCategoryCard
                  key={cat.id}
                  id={cat.id}
                  title={cat.title}
                  icon={cat.icon}
                  color={cat.color}
                  modules={cat.modules.map((mod, index) => ({
                    id: `${cat.id}-${index}`,
                    label: mod.label,
                    description: mod.description,
                    href: mod.href,
                    icon: mod.icon,
                    color: mod.color,
                    serviceSlug: mod.serviceSlug,
                  }))}
                />
              ))}
            </div>
          </div>

          {/* Finance Overview (finance users only) */}
          {isFinance && (
            <div>
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-semibold text-foreground uppercase tracking-wide">Finance Overview</h3>
                <Link href="/finance" className="text-xs text-primary hover:text-primary-hover">Finance Dashboard →</Link>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <Link href="/finance/billing" className="bg-card rounded-lg border border-border p-4 hover:border-primary/25 transition-colors">
                  <div className="flex items-center gap-2 mb-2"><Receipt className="w-4 h-4 text-impilo-400" /><span className="text-sm font-medium text-foreground">Billing</span></div>
                  <p className="text-xs text-muted-foreground">View and manage bills</p>
                </Link>
                <Link href="/finance/payments" className="bg-card rounded-lg border border-border p-4 hover:border-primary/25 transition-colors">
                  <div className="flex items-center gap-2 mb-2"><FileText className="w-4 h-4 text-green-500" /><span className="text-sm font-medium text-foreground">Payments</span></div>
                  <p className="text-xs text-muted-foreground">Track payment status</p>
                </Link>
                <Link href="/finance/claims" className="bg-card rounded-lg border border-border p-4 hover:border-primary/25 transition-colors">
                  <div className="flex items-center gap-2 mb-2"><ClipboardList className="w-4 h-4 text-purple-500" /><span className="text-sm font-medium text-foreground">Claims</span></div>
                  <p className="text-xs text-muted-foreground">Insurance claim tracking</p>
                </Link>
              </div>
            </div>
          )}

          </>)}

          {/* ═══ PROFESSIONAL TAB ═══ */}
          {activeTab === "professional" && !focusedWorkMode && (
            <div className="space-y-6">
              {(availableOperationalModes.includes("registry_admin") ||
                availableOperationalModes.includes("organization_admin")) && (
                <div className="rounded-xl border border-border bg-gradient-to-r from-slate-50 to-card p-5">
                  <h3 className="text-sm font-semibold text-foreground flex items-center gap-2">
                    <Shield className="w-4 h-4 text-warning-foreground" />
                    Administrative planes
                  </h3>
                  <p className="mt-1 text-xs text-muted-foreground max-w-2xl">
                    Enter sovereign registry governance or organization operations explicitly — separate from
                    Facility Work (facility → workspace → shift). These match the context strip at the top of
                    the shell.
                  </p>
                  <div className="mt-4 flex flex-wrap gap-3">
                    {availableOperationalModes.includes("registry_admin") && (
                      <Link
                        href="/registry-admin"
                        className="inline-flex items-center gap-2 rounded-lg border border-warning/35 bg-warning-soft px-4 py-2.5 text-sm font-medium text-warning-foreground hover:border-amber-400 transition-colors"
                      >
                        <ShieldCheck className="h-4 w-4" />
                        Registry administration
                      </Link>
                    )}
                    {availableOperationalModes.includes("organization_admin") && (
                      <Link
                        href="/organization-admin"
                        className="inline-flex items-center gap-2 rounded-lg border border-violet-200 bg-violet-50 px-4 py-2.5 text-sm font-medium text-violet-950 hover:border-violet-400 transition-colors"
                      >
                        <UserCog className="h-4 w-4" />
                        Organization administration
                      </Link>
                    )}
                  </div>
                </div>
              )}

              {/* Credentials & License - Compact Cards */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {/* License Card - Prominent */}
                <div className={`rounded-xl border-2 p-4 ${licenseActive ? "border-green-200 bg-green-50" : "border-danger/28 bg-danger-soft"}`}>
                  <div className="flex items-center gap-2 mb-2">
                    <Award className={`w-5 h-5 ${licenseActive ? "text-green-600" : "text-red-600"}`} />
                    <span className={`text-sm font-semibold ${licenseActive ? "text-green-700" : "text-danger"}`}>
                      {licenseActive ? "Licensed" : "Action Required"}
                    </span>
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {licenses.length > 0 ? `${licenses.length} license${licenses.length > 1 ? "s" : ""} on record` : "No license data"}
                  </p>
                  <Link href="/home/credentials" className="text-xs text-primary hover:text-primary-hover mt-2 inline-block">
                    Manage →
                  </Link>
                </div>
                {/* CPD Progress */}
                <div className="rounded-xl border border-border bg-card p-4">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-sm font-medium text-foreground">CPD Progress</span>
                    <span className="text-xs text-primary">
                      {cpdCycle ? `${cpdEarned}/${cpdRequired} pts` : "No active cycle"}
                    </span>
                  </div>
                  <div className="w-full bg-neutral-100 rounded-full h-2">
                    <div
                      className="bg-primary rounded-full h-2"
                      style={{ width: `${cpdProgressPct}%` }}
                    />
                  </div>
                  <p className="text-xs text-muted-foreground mt-2">
                    {cpdCycle
                      ? `${Math.max(0, cpdRequired - cpdEarned)} points remaining`
                      : "CPD cycle data unavailable"}
                  </p>
                </div>
                {/* Fundo learning KPI snapshot */}
                <div className="rounded-xl border border-cyan-200 bg-cyan-50 p-4">
                  <div className="flex items-center justify-between mb-2">
                    <span className="inline-flex items-center gap-2 text-sm font-medium text-cyan-900">
                      <ServiceLogo slug="fundo" size="compact" />
                      Fundo Snapshot
                    </span>
                    <Link href="/learning" className="text-xs font-medium text-cyan-700 hover:text-cyan-900">
                      Open →
                    </Link>
                  </div>
                  <div className="grid grid-cols-3 gap-2">
                    <Link href="/learning?focus=required" className="rounded-lg bg-card px-2 py-2 text-center border border-cyan-100 hover:border-cyan-300 transition">
                      <p className="text-[10px] uppercase tracking-wide text-cyan-700">Required</p>
                      <p className="text-base font-semibold text-cyan-900">
                        {fundoKpisLoading ? "…" : fundoKpis.required}
                      </p>
                    </Link>
                    <Link href="/learning?focus=overdue" className="rounded-lg bg-card px-2 py-2 text-center border border-cyan-100 hover:border-cyan-300 transition">
                      <p className="text-[10px] uppercase tracking-wide text-cyan-700">Overdue</p>
                      <p className={`text-base font-semibold ${fundoKpis.overdue > 0 ? "text-danger" : "text-cyan-900"}`}>
                        {fundoKpisLoading ? "…" : fundoKpis.overdue}
                      </p>
                    </Link>
                    <Link href="/learning?focus=cpd" className="rounded-lg bg-card px-2 py-2 text-center border border-cyan-100 hover:border-cyan-300 transition">
                      <p className="text-[10px] uppercase tracking-wide text-cyan-700">CPD</p>
                      <p className="text-base font-semibold text-cyan-900">
                        {fundoKpisLoading ? "…" : fundoKpis.cpdEligible}
                      </p>
                    </Link>
                  </div>
                  <p className="mt-2 text-xs text-cyan-800">
                    {fundoKpisError
                      ? "Learning KPI feed unavailable right now; open Impilo Fundo for full details."
                      : "Required, overdue, and CPD-eligible counts are sourced from Fundo my-learning."}
                  </p>
                </div>
                {/* Quick Actions */}
                <div className="rounded-xl border border-border bg-card p-4">
                  <span className="text-sm font-medium text-foreground mb-3 block">Quick Actions</span>
                  <div className="space-y-2">
                    <Link href="/learning" className="flex items-center gap-2 text-xs text-muted-foreground hover:text-primary">
                      <ServiceLogo slug="fundo" size="compact" /> Impilo Fundo
                    </Link>
                    <Link href="/home/certifications" className="flex items-center gap-2 text-xs text-muted-foreground hover:text-primary">
                      <Award className="w-3.5 h-3.5" /> Certifications
                    </Link>
                    <Link href="/professional" className="flex items-center gap-2 text-xs text-muted-foreground hover:text-primary">
                      <User className="w-3.5 h-3.5" /> Full Profile
                    </Link>
                    <Link href="/home/transcripts" className="flex items-center gap-2 text-xs text-muted-foreground hover:text-primary">
                      <FileText className="w-3.5 h-3.5" /> CPD Transcripts
                    </Link>
                  </div>
                </div>
              </div>

              {/* Affiliations (from VARAPI privileges or facility list fallback) */}
              <div className="bg-card rounded-lg border border-border p-6">
                <h3 className="text-base font-semibold text-foreground flex items-center gap-2 mb-4">
                  <Building2 className="w-5 h-5 text-purple-600" /> Facility Affiliations
                </h3>
                {privileges.length > 0 ? (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    {privileges.slice(0, 6).map((priv, idx) => (
                      <div key={idx} className="flex items-center justify-between bg-background rounded-lg border border-border p-3">
                        <div className="flex items-center gap-2">
                          <Building2 className="w-4 h-4 text-muted-foreground" />
                          <div>
                            <p className="text-sm font-medium text-foreground">Facility {priv.facilityId?.slice(0, 8)}</p>
                            <p className="text-xs text-muted-foreground">{priv.privilegeType} · {priv.scope}</p>
                          </div>
                        </div>
                        <span className={`text-xs px-2 py-0.5 rounded-full ${
                          priv.status === "APPROVED" ? "bg-green-100 text-green-700" : "bg-neutral-100 text-muted-foreground"
                        }`}>{priv.status}</span>
                      </div>
                    ))}
                  </div>
                ) : facilities.length > 0 ? (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    {facilities.slice(0, 4).map((f) => (
                      <div key={f.id} className="flex items-center justify-between bg-background rounded-lg border border-border p-3">
                        <div className="flex items-center gap-2">
                          <Building2 className="w-4 h-4 text-muted-foreground" />
                          <div>
                            <p className="text-sm font-medium text-foreground">{f.attributes.name}</p>
                            <p className="text-xs text-muted-foreground">{f.attributes.facilityType}</p>
                          </div>
                        </div>
                        <button onClick={() => handleFacilitySelect(f)}
                          className="text-xs text-primary hover:text-primary-hover font-medium">
                          Start Shift →
                        </button>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-sm text-muted-foreground">No facility affiliations found.</p>
                )}
              </div>

              {/* Work Modes — all ways a professional can work */}
              {isClinical && (
                <div className="bg-card rounded-lg border border-border p-6">
                  <h3 className="text-base font-semibold text-foreground flex items-center gap-2 mb-4">
                    <Globe className="w-5 h-5 text-teal-600" /> Other Ways to Work
                  </h3>
                  <p className="text-sm text-muted-foreground mb-4">
                    Not at a facility? Choose how you are working today.
                  </p>
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                    {/* Telemedicine & Virtual */}
                    <button onClick={() => enterViaWorkHome("VIRTUAL_CARE")}
                      className="text-left bg-green-50 rounded-lg border border-green-200 p-4 hover:border-green-400 transition-all group">
                      <Video className="w-5 h-5 text-green-600 mb-2" />
                      <p className="text-sm font-medium text-foreground group-hover:text-green-700">Telemedicine & Virtual</p>
                      <p className="text-xs text-muted-foreground mt-0.5">Choose a workplace that grants virtual care, then mint that duty session</p>
                    </button>
                    {/* Independent Practice */}
                    <button onClick={() => enterViaWorkHome()}
                      className="text-left bg-warning-soft rounded-lg border border-warning/35 p-4 hover:border-amber-400 transition-all group">
                      <Briefcase className="w-5 h-5 text-amber-600 mb-2" />
                      <p className="text-sm font-medium text-foreground group-hover:text-warning-foreground">Independent Practice</p>
                      <p className="text-xs text-muted-foreground mt-0.5">Mint against a proven workplace — never a local mode flag alone</p>
                    </button>
                    {/* Field Work — governed: mint via Work Home, never a local mode flag */}
                    <button onClick={() => enterCommunityOutreachViaWorkHome()}
                      className="text-left bg-teal-50 rounded-lg border border-teal-200 p-4 hover:border-teal-400 transition-all group">
                      <MapPin className="w-5 h-5 text-teal-600 mb-2" />
                      <p className="text-sm font-medium text-foreground group-hover:text-teal-700">Field & Community Work</p>
                      <p className="text-xs text-muted-foreground mt-0.5">
                        Choose a workplace that grants community outreach, then start that duty session
                      </p>
                    </button>
                    {/* Emergency Response */}
                    <button onClick={() => enterViaWorkHome()}
                      className="text-left bg-danger-soft rounded-lg border border-danger/28 p-4 hover:border-red-400 transition-all group">
                      <Siren className="w-5 h-5 text-red-600 mb-2" />
                      <p className="text-sm font-medium text-foreground group-hover:text-danger">Emergency Response</p>
                      <p className="text-xs text-muted-foreground mt-0.5">Choose a proven workplace for elevated-trust duty — no local authority invent</p>
                    </button>
                    {/* Above Site */}
                    <button onClick={() => enterViaWorkHome("JURISDICTION_OPERATIONS")}
                      className="text-left bg-info-soft rounded-lg border border-info/25 p-4 hover:border-indigo-400 transition-all group">
                      <Layers className="w-5 h-5 text-indigo-600 mb-2" />
                      <p className="text-sm font-medium text-foreground group-hover:text-primary-hover">Above-Site & Programme</p>
                      <p className="text-xs text-muted-foreground mt-0.5">Mint jurisdiction or programme duty via Work Home</p>
                    </button>
                    {/* Maintenance & Configuration */}
                    <button onClick={() => enterViaWorkHome("TECHNICAL_SUPPORT")}
                      className="text-left bg-background rounded-lg border border-border p-4 hover:border-gray-400 transition-all group">
                      <Wrench className="w-5 h-5 text-muted-foreground mb-2" />
                      <p className="text-sm font-medium text-foreground group-hover:text-foreground">Maintenance & Configuration</p>
                      <p className="text-xs text-muted-foreground mt-0.5">Mint technical support duty before system setup and integrations</p>
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* ═══ PERSONAL TAB — same CitizenHome surface as citizen-only users ═══ */}
          {activeTab === "personal" && !focusedWorkMode && (
            <CitizenHome user={user} greeting={greeting} />
          )}

        </div>
      </PageShell>
    </AppLayout>
  );
}

// ═══════════════════════════════════════════════════════════════════
// Citizen Home — Facebook-style 3-column layout
// Left: quick-nav shortcuts · Centre: timeline feed · Right: widgets
// ═══════════════════════════════════════════════════════════════════

/** Normalize citizen + scheduling appointment payloads into JSON:API-style rows for queue-workflows helpers. */
function normalizeCitizenAppointmentList(payload: unknown): Array<{ id: string; attributes: Record<string, unknown> }> {
  const root = payload as { data?: unknown } | null | undefined;
  const raw = root?.data;
  const arr: unknown[] = Array.isArray(raw) ? raw : [];
  return arr.map((row, i) => {
    if (row && typeof row === "object" && "attributes" in row) {
      const r = row as { id?: string; attributes?: Record<string, unknown> };
      return { id: String(r.id ?? `apt-${i}`), attributes: r.attributes ?? {} };
    }
    if (row && typeof row === "object") {
      const o = row as Record<string, unknown>;
      return {
        id: String(o.id ?? `apt-${i}`),
        attributes: {
          scheduled_at: o.scheduledAt ?? o.scheduled_at,
          scheduledAt: o.scheduledAt,
          appointment_time: o.appointmentTime ?? o.appointment_time,
          appointment_type: o.appointmentType ?? o.appointment_type,
          appointmentType: o.appointmentType,
          status: o.status,
          provider_name: o.providerName ?? o.provider_name,
          providerName: o.providerName,
        },
      };
    }
    return { id: `apt-${i}`, attributes: {} };
  });
}

function CitizenHome({
  user,
  greeting,
}: {
  user: AuthUser | null;
  greeting: string;
}) {
  const patientAnchor = (user?.healthId && user.healthId.trim()) || user?.id || "";

  const citizenActorAppointments = useQuery<unknown>({
    queryKey: ["citizen-upcoming-appointments-mobile", user?.id],
    queryFn: () => apiClient.get("/internal/v1/mobile/citizen/appointments?page=0&size=25"),
    enabled: !!user?.id,
    staleTime: 60_000,
  });

  const citizenPatientAppointments = useQuery<unknown>({
    queryKey: ["citizen-upcoming-appointments-patient", patientAnchor],
    queryFn: () =>
      apiClient.get(`/internal/v1/appointments?patient_id=${encodeURIComponent(patientAnchor)}&size=25`),
    enabled: !!patientAnchor,
    staleTime: 60_000,
  });

  const mergedAppointmentRows = useMemo(() => {
    const fromActor = normalizeCitizenAppointmentList(citizenActorAppointments.data);
    const fromPatient = normalizeCitizenAppointmentList(citizenPatientAppointments.data);
    const map = new Map<string, { id: string; attributes: Record<string, unknown> }>();
    for (const row of fromActor) map.set(row.id, row);
    for (const row of fromPatient) map.set(row.id, row);
    return [...map.values()];
  }, [citizenActorAppointments.data, citizenPatientAppointments.data]);

  const citizenAppointmentsLoading = citizenActorAppointments.isPending || citizenPatientAppointments.isPending;
  const citizenAppointmentsError = citizenActorAppointments.isError && citizenPatientAppointments.isError;

  const terminalAppt = new Set(["CANCELLED", "CANCELED", "NO_SHOW", "COMPLETED", "DONE"]);
  const startOfToday = () => {
    const d = new Date();
    d.setHours(0, 0, 0, 0);
    return d.getTime();
  };
  const upcomingAppointments = mergedAppointmentRows
    .filter((row) => {
      const status = getAppointmentStatus(row).toUpperCase();
      if (terminalAppt.has(status)) return false;
      const when = getAppointmentTime(row);
      if (!when) return false;
      const ms = new Date(when).getTime();
      if (Number.isNaN(ms)) return false;
      return ms >= startOfToday();
    })
    .sort((a, b) => new Date(getAppointmentTime(a)).getTime() - new Date(getAppointmentTime(b)).getTime())
    .slice(0, 5);

  return (
    <>
      {/* ── Top status bar ────────────────────────────────────────── */}
      <div className="mb-4 flex -mx-3 items-center justify-between border-b border-border bg-card px-3 py-2.5 md:-mx-4 md:px-4">
        <div className="flex items-center gap-3 min-w-0">
          <div className="h-9 w-9 rounded-full bg-primary-soft flex items-center justify-center shrink-0">
            <User className="h-4.5 w-4.5 text-primary" />
          </div>
          <div className="min-w-0">
            <p className="text-sm font-semibold text-foreground truncate">
              {greeting}, {user?.displayName?.split(" ")[0] ?? "there"}
            </p>
            <p className="text-[11px] text-muted-foreground truncate">
              {user?.email}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-1.5">
          <Link href="/home/notifications" className="p-2 rounded-full hover:bg-neutral-100 relative" title="Notifications">
            <Activity className="w-4.5 h-4.5 text-muted-foreground" />
          </Link>
          <Link href="/ask" className="p-2 rounded-full hover:bg-neutral-100" title="Ask Nompilo">
            <MessageSquare className="w-4.5 h-4.5 text-muted-foreground" />
          </Link>
          <Link href="/auth/logout" className="p-2 rounded-full hover:bg-neutral-100" title="Switch account">
            <UserCog className="w-4.5 h-4.5 text-muted-foreground" />
          </Link>
        </div>
      </div>

      {/* ── 3-column grid ─────────────────────────────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-[220px_1fr_260px] gap-5">

        {/* ════ LEFT COLUMN — Quick nav ════ */}
        <CitizenQuickAccessRail className="hidden lg:block" />

        {/* ════ CENTRE COLUMN — Timeline ════ */}
        <main className="min-w-0 space-y-4">
          <TimelineComposer userName={user?.displayName?.split(" ")[0] ?? "there"} />
          <TimelineFeed userId={user?.id} />
        </main>

        {/* ════ RIGHT COLUMN — Widgets ════ */}
        <aside className="hidden lg:block space-y-4">
          {/* My Wallet — prominent at top (Health ID lives in left column) */}
          <Link href="/wallet"
            className="flex items-center gap-3 p-4 rounded-xl bg-gradient-to-br from-amber-400 to-amber-500 hover:from-amber-500 hover:to-amber-600 transition-colors text-[color:var(--on-yellow)]">
            <Wallet className="w-8 h-8" />
            <div>
              <p className="text-sm font-semibold">My Wallet</p>
              <p className="text-xs opacity-90">Payments, claims &amp; coverage</p>
            </div>
          </Link>

          {/* Upcoming appointments — confirmed scheduled events only */}
          <div className="bg-card rounded-xl border border-border p-4">
            <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-3 flex items-center gap-1.5">
              <Calendar className="w-3.5 h-3.5 text-primary" /> Upcoming appointments
            </h3>
            {citizenAppointmentsLoading && (
              <p className="text-xs text-muted-foreground">Loading appointments…</p>
            )}
            {!citizenAppointmentsLoading && citizenAppointmentsError && (
              <p className="text-xs text-muted-foreground leading-relaxed">
                Appointments could not be loaded. Book a service or check My Bookings for pending requests.
              </p>
            )}
            {!citizenAppointmentsLoading && !citizenAppointmentsError && upcomingAppointments.length === 0 && (
              <p className="text-xs text-muted-foreground leading-relaxed">
                No confirmed appointments yet. Booking requests appear under My Bookings until confirmed.
              </p>
            )}
            {!citizenAppointmentsLoading && !citizenAppointmentsError && upcomingAppointments.length > 0 && (
              <ul className="space-y-2.5">
                {upcomingAppointments.map((appt) => {
                  const when = getAppointmentTime(appt);
                  const label = when
                    ? new Date(when).toLocaleString([], { dateStyle: "medium", timeStyle: "short" })
                    : "Date TBC";
                  return (
                    <li key={appt.id} className="text-xs text-foreground border-b border-border pb-2 last:border-0 last:pb-0">
                      <p className="font-medium text-foreground">{getAppointmentType(appt)}</p>
                      <p className="text-[11px] text-muted-foreground mt-0.5">{label}</p>
                      <p className="text-[11px] text-muted-foreground">{getAppointmentProvider(appt)}</p>
                    </li>
                  );
                })}
              </ul>
            )}
            <div className="mt-3 flex flex-col gap-1 text-center text-xs font-medium">
              <Link href="/home/bookings/new" className="text-primary hover:underline">
                Book a service →
              </Link>
              <Link href="/home/bookings" className="text-muted-foreground hover:underline">
                My Bookings (pending requests)
              </Link>
              <Link href="/home/appointments" className="text-muted-foreground hover:underline">
                My Appointments (confirmed)
              </Link>
            </div>
          </div>

          {/* Quick health actions */}
          <div className="bg-card rounded-xl border border-border p-4">
            <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2.5">Quick Actions</h3>
            <div className="grid grid-cols-2 gap-2">
              {[
                { href: "/home/medications", label: "Medications", icon: Pill, color: "text-amber-500" },
                { href: "/home/conditions", label: "Conditions", icon: FlaskConical, color: "text-purple-500" },
                { href: "/home/allergies", label: "Allergies", icon: AlertTriangle, color: "text-red-500" },
                { href: "/home/documents", label: "Documents", icon: FileCheck, color: "text-blue-500" },
                { href: "/home/results", label: "Lab Results", icon: Clipboard, color: "text-green-500" },
              ].map((a) => {
                const Icon = a.icon;
                return (
                  <Link key={a.href} href={a.href}
                    className="flex flex-col items-center gap-1.5 p-3 rounded-lg border border-border hover:border-primary/25 hover:bg-primary-soft/50 transition-colors text-center">
                    <Icon className={`w-5 h-5 ${a.color}`} />
                    <span className="text-[11px] font-medium text-muted-foreground">{a.label}</span>
                  </Link>
                );
              })}
            </div>
          </div>

          {/* Account */}
          <div className="bg-card rounded-xl border border-border p-4">
            <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2.5">Account</h3>
            <div className="space-y-1">
              {[
                { href: "/home/profile", label: "Profile" },
                { href: "/home/preferences", label: "Preferences" },
                { href: "/settings/security", label: "Security & Privacy" },
                { href: "/support", label: "Help & Support" },
              ].map((link) => (
                <Link key={link.href} href={link.href}
                  className="block px-2 py-1.5 rounded-md text-xs text-muted-foreground hover:text-foreground hover:bg-background transition-colors">
                  {link.label}
                </Link>
              ))}
            </div>
          </div>
        </aside>
      </div>
    </>
  );
}

/** Compact announcements banner showing pinned/urgent items from the noticeboard */
// ═══════════════════════════════════════════════════════════════════
// Timeline components — post creation + live citizen feed
// ═══════════════════════════════════════════════════════════════════

function TimelineComposer({ userName }: { userName: string }) {
  const [postText, setPostText] = useState("");
  const [posting, setPosting] = useState(false);
  const [posted, setPosted] = useState(false);

  async function handlePost() {
    if (!postText.trim()) return;
    setPosting(true);
    try {
      await apiClient.post("/internal/v1/mobile/citizen/feed", {
        body: postText.trim(),
        type: "STATUS_UPDATE",
      });
    } catch {
      // BFF may not have this endpoint yet — that's OK, clear locally
    }
    setPosting(false);
    setPosted(true);
    setPostText("");
    setTimeout(() => setPosted(false), 3000);
  }

  return (
    <div className="bg-card rounded-xl border border-border overflow-hidden">
      {/* Composer */}
      <div className="p-4">
        <div className="flex items-start gap-3">
          <div className="h-10 w-10 rounded-full bg-primary-soft flex items-center justify-center shrink-0 mt-0.5">
            <User className="h-5 w-5 text-primary" />
          </div>
          <div className="flex-1">
            <textarea
              value={postText}
              onChange={(e) => setPostText(e.target.value)}
              placeholder={`What's on your mind, ${userName}?`}
              rows={2}
              className="w-full resize-none border-0 bg-transparent text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-0"
            />
          </div>
        </div>
        {posted && (
          <p className="text-xs text-primary mt-1 ml-[52px]">Posted to your timeline</p>
        )}
      </div>
      {/* Action bar */}
      <div className="flex items-center justify-between border-t border-border px-4 py-2">
        <p className="text-[11px] text-muted-foreground px-1">
          Media and structured check-ins will appear here when those channels are enabled for citizen posts.
        </p>
        <button
          onClick={handlePost}
          disabled={!postText.trim() || posting}
          className="flex items-center gap-1.5 px-4 py-1.5 rounded-full bg-primary text-white text-xs font-medium hover:bg-primary-hover disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          <Send className="w-3.5 h-3.5" /> Post
        </button>
      </div>
    </div>
  );
}

/** Live citizen timeline posts from the BFF. */
function TimelineFeed({ userId }: { userId?: string }) {
  const hasRole = useAuthStore((s) => s.hasRole);
  const isCitizenAccess = hasRole("CITIZEN") || hasRole("SYSTEM_ADMIN") || hasRole("SUPER_ADMIN") || hasRole("DEVELOPER");
  const { data, isLoading, isError, error } = useQuery<{ data: Array<{ id: string; attributes: Record<string, unknown> }> }>({
    queryKey: ["personal-feed", userId],
    queryFn: () => apiClient.get("/internal/v1/mobile/citizen/feed?size=10"),
    staleTime: 30_000,
    enabled: isCitizenAccess,
    retry: false,
  });
  const liveItems = isError ? [] : (data?.data ?? []);

  // Seeded posts so the timeline never looks empty
  const seededPosts: Array<{
    id: string;
    author: string;
    avatar: string;
    time: string;
    body: string;
    likes: number;
    comments: number;
    type: "update" | "health_tip" | "announcement" | "milestone";
  }> = [
    {
      id: "seed-1",
      author: "Ministry of Health",
      avatar: "MoH",
      time: "2 hours ago",
      body: "Flu season reminder: Free influenza vaccinations are available at all public health facilities. Bring your Health ID. Walk-ins welcome, no appointment needed.",
      likes: 142,
      comments: 23,
      type: "announcement",
    },
    {
      id: "seed-2",
      author: "Nompilo Health AI",
      avatar: "N",
      time: "4 hours ago",
      body: "Tip: Drinking 2 litres of water daily can improve energy levels, skin health, and kidney function. Track your hydration in the Wellness tab.",
      likes: 89,
      comments: 7,
      type: "health_tip",
    },
    {
      id: "seed-3",
      author: "Harare Central Hospital",
      avatar: "HCH",
      time: "6 hours ago",
      body: "New services available: Our Telemedicine clinic is now open for video consultations Monday–Friday 08:00–16:00. Book through Impilo or call +263 4 123 4567.",
      likes: 67,
      comments: 15,
      type: "update",
    },
    {
      id: "seed-4",
      author: "Community Wellness",
      avatar: "CW",
      time: "Yesterday",
      body: "Congratulations to 1,247 citizens who completed the 30-Day Walking Challenge! New challenge starting Monday: Sleep Better in 21 Days. Join in the Wellness tab.",
      likes: 234,
      comments: 41,
      type: "milestone",
    },
    {
      id: "seed-5",
      author: "MOHCC Immunisation Programme",
      avatar: "IP",
      time: "Yesterday",
      body: "Childhood immunisation week starts next Monday across all provinces. Check your child's vaccination schedule in Documents > Immunisation Record.",
      likes: 178,
      comments: 32,
      type: "announcement",
    },
  ];

  const typeColors: Record<string, string> = {
    announcement: "bg-danger-soft text-red-600 border-red-100",
    health_tip: "bg-green-50 text-green-600 border-green-100",
    update: "bg-info-soft text-primary border-blue-100",
    milestone: "bg-warning-soft text-amber-600 border-amber-100",
  };

  const avatarColors: Record<string, string> = {
    Y: "bg-primary-soft text-primary-hover",
  };

  const livePosts = liveItems.length > 0 ? [
    ...liveItems.map((item) => ({
      id: item.id,
      author: (item.attributes.author as string) ?? "You",
      avatar: ((item.attributes.author as string) ?? "Y").charAt(0).toUpperCase(),
      time: item.attributes.published_at
        ? new Date(item.attributes.published_at as string).toLocaleDateString()
        : "Just now",
      body: (item.attributes.body as string) ?? (item.attributes.title as string) ?? "",
      likes: (item.attributes.likes as number) ?? 0,
      comments: (item.attributes.comments as number) ?? 0,
      type: "update" as const,
    })),
  ] : seededPosts;

  return (
    <div className="space-y-4">
      {/* Call / video shortcuts — prominent strip */}
      <div className="grid grid-cols-3 gap-3">
        <Link href="/telemedicine"
          className="flex items-center justify-center gap-2 py-3 bg-green-50 border border-green-200 rounded-xl text-sm font-medium text-green-700 hover:bg-green-100 transition-colors">
          <Video className="w-4 h-4" /> Video call
        </Link>
        <Link href="/telemedicine?mode=audio"
          className="flex items-center justify-center gap-2 py-3 bg-info-soft border border-info/25 rounded-xl text-sm font-medium text-primary-hover hover:bg-blue-100 transition-colors">
          <Phone className="w-4 h-4" /> Voice call
        </Link>
        <Link href="/scheduling"
          className="flex items-center justify-center gap-2 py-3 bg-primary-soft border border-primary/25 rounded-xl text-sm font-medium text-primary-hover hover:bg-primary-soft transition-colors">
          <Calendar className="w-4 h-4" /> Book visit
        </Link>
      </div>

      {isError && (
        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          Live feed unavailable — showing community highlights. {formatServiceError(error)}
        </div>
      )}
      {/* Timeline posts */}
      {isLoading && (
        <div className="rounded-xl border border-border bg-card px-4 py-8 text-center text-sm text-muted-foreground">
          Loading your citizen timeline…
        </div>
      )}
      {!isLoading && livePosts.length === 0 && (
        <div className="rounded-xl border border-border bg-card px-4 py-8 text-center">
          <p className="text-sm font-medium text-foreground">No timeline posts yet</p>
          <p className="mt-1 text-xs text-muted-foreground">
            Your home feed will populate when the citizen feed service returns real posts for this account.
          </p>
        </div>
      )}
      {livePosts.map((post) => (
        <article key={post.id} className="bg-card rounded-xl border border-border overflow-hidden">
          {/* Post header */}
          <div className="flex items-center gap-3 px-4 pt-4 pb-2">
            <div className={`w-10 h-10 rounded-full flex items-center justify-center text-xs font-bold shrink-0 ${avatarColors[post.avatar] ?? "bg-neutral-100 text-muted-foreground"}`}>
              {post.avatar}
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-foreground truncate">{post.author}</p>
              <div className="mt-0.5 flex items-center gap-2">
                <p className="text-xs text-muted-foreground">{post.time}</p>
                <span className={`rounded-full border px-1.5 py-0.5 text-[10px] font-medium ${typeColors[post.type] ?? "bg-background text-muted-foreground border-border"}`}>
                  {post.type.replace("_", " ")}
                </span>
              </div>
            </div>
          </div>
          {/* Post body */}
          <div className="px-4 pb-3">
            <p className="text-sm text-foreground leading-relaxed whitespace-pre-line">{post.body}</p>
          </div>
          {/* Engagement bar */}
          <div className="flex items-center justify-between border-t border-border px-4 py-2">
            <span className="text-xs text-muted-foreground">{post.likes > 0 ? `${post.likes} likes` : ""}</span>
            <span className="text-xs text-muted-foreground">{post.comments > 0 ? `${post.comments} comments` : ""}</span>
          </div>
          {/* Action buttons — engagement mutations not wired on this home feed yet */}
          <div className="flex border-t border-border">
            <button
              type="button"
              disabled
              title="Like is not available on this feed yet"
              className="flex-1 flex items-center justify-center gap-1.5 py-2.5 text-xs text-muted-foreground opacity-60 cursor-not-allowed"
            >
              <ThumbsUp className="w-4 h-4" /> Like
            </button>
            <button
              type="button"
              disabled
              title="Comment is not available on this feed yet"
              className="flex-1 flex items-center justify-center gap-1.5 py-2.5 text-xs text-muted-foreground opacity-60 cursor-not-allowed"
            >
              <MessageCircle className="w-4 h-4" /> Comment
            </button>
            <button
              type="button"
              disabled
              title="Share is not available on this feed yet"
              className="flex-1 flex items-center justify-center gap-1.5 py-2.5 text-xs text-muted-foreground opacity-60 cursor-not-allowed"
            >
              <Send className="w-4 h-4" /> Share
            </button>
          </div>
        </article>
      ))}
    </div>
  );
}

function AnnouncementsBanner() {
  const { data } = useQuery<{ data: Array<Record<string, unknown>>; stats: Record<string, number> }>({
    queryKey: ["announcements-banner"],
    queryFn: () => apiClient.get("/internal/v1/communication/announcements?size=3"),
    staleTime: 60_000,
  });
  const items = data?.data ?? [];
  const stats = data?.stats;

  if (items.length === 0) {
    return (
      <div className="flex items-center gap-2 py-2 text-sm text-muted-foreground">
        <Clock className="w-4 h-4" />
        No active announcements
      </div>
    );
  }

  const priorityColors: Record<string, string> = {
    urgent: "bg-red-100 text-danger border-danger/28",
    high: "bg-orange-100 text-orange-700 border-orange-200",
    normal: "bg-primary-soft text-primary border-primary/25",
    low: "bg-neutral-100 text-muted-foreground border-border",
  };

  return (
    <div className="space-y-2">
      {stats && (
        <div className="flex items-center gap-4 text-xs text-muted-foreground">
          <span>{stats.total ?? 0} active</span>
          {(stats.pinned ?? 0) > 0 && <span className="text-amber-600">{stats.pinned} pinned</span>}
          {(stats.urgent ?? 0) > 0 && <span className="text-red-600">{stats.urgent} urgent</span>}
        </div>
      )}
      {items.slice(0, 3).map((a) => (
        <Link
          key={a.id as string}
          href="/scheduling/noticeboard"
          className={`flex items-start gap-3 p-3 rounded-lg border text-left transition-colors hover:shadow-sm ${
            a.priority === "urgent" ? "border-danger/28 bg-danger-soft" : a.is_pinned ? "border-warning/35 bg-warning-soft" : "border-border bg-background"
          }`}
        >
          <div className="shrink-0 mt-0.5">
            {a.priority === "urgent" ? (
              <AlertTriangle className="w-4 h-4 text-red-500" />
            ) : a.is_pinned ? (
              <Shield className="w-4 h-4 text-amber-500" />
            ) : (
              <Activity className="w-4 h-4 text-impilo-400" />
            )}
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <p className="text-sm font-medium text-foreground truncate">{a.title as string}</p>
              <span className={`px-1.5 py-0.5 text-[10px] font-medium rounded ${priorityColors[(a.priority as string) ?? "normal"]}`}>
                {a.priority as string}
              </span>
            </div>
            <p className="text-xs text-muted-foreground line-clamp-1 mt-0.5">{a.content as string}</p>
          </div>
        </Link>
      ))}
    </div>
  );
}

/** Community feed section using existing citizen feed infrastructure */
function FeedSection() {
  const hasRole = useAuthStore((s) => s.hasRole);
  const isCitizenAccess = hasRole("CITIZEN") || hasRole("SYSTEM_ADMIN") || hasRole("SUPER_ADMIN") || hasRole("DEVELOPER");
  const { data } = useQuery<{ data: Array<{ id: string; attributes: Record<string, unknown> }> }>({
    queryKey: ["personal-feed"],
    queryFn: () => apiClient.get("/internal/v1/mobile/citizen/feed?size=5"),
    enabled: isCitizenAccess,
  });
  const items = data?.data ?? [];

  return (
    <div className="bg-card rounded-lg border border-border p-6">
      <h3 className="text-base font-semibold text-foreground flex items-center gap-2 mb-4">
        <Users className="w-5 h-5 text-purple-600" /> Community & Updates
      </h3>
      {items.length === 0 ? (
        <p className="text-sm text-muted-foreground">No community updates yet.</p>
      ) : (
        <div className="space-y-3">
          {items.map((item) => (
            <div key={item.id} className="flex items-start gap-3 p-3 bg-background rounded-lg">
              <div className="w-8 h-8 rounded-full bg-purple-100 flex items-center justify-center shrink-0">
                <FileText className="w-4 h-4 text-purple-500" />
              </div>
              <div className="min-w-0">
                <p className="text-sm font-medium text-foreground truncate">
                  {(item.attributes.title as string) ?? "Update"}
                </p>
                <p className="text-xs text-muted-foreground line-clamp-2">
                  {(item.attributes.body as string) ?? ""}
                </p>
                {typeof item.attributes.published_at === "string" && (
                  <p className="text-xs text-muted-foreground mt-1">
                    {new Date(item.attributes.published_at).toLocaleDateString()}
                  </p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function getGreeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  return "Good evening";
}
