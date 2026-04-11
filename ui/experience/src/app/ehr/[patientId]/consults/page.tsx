"use client";

/**
 * Consults & Referrals — Unified in-encounter workspace with 3 tabs:
 *   Consultations, Referrals, Teleconsults
 *
 * Lovable-aligned layout from ConsultsSection with real API integration
 * from existing Wave 2–5 hooks. This is the primary in-encounter surface
 * for all consultation, referral, and teleconsult activities.
 *
 * Route: /ehr/[patientId]/consults
 */

import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  Users,
  Send,
  Video,
  Phone,
  MessageSquare,
  Plus,
  Clock,
  CheckCircle2,
  Calendar,
  Loader2,
  Building2,
  Activity,
  FileText,
  AlertTriangle,
  Sparkles,
  Workflow,
  ClipboardCheck,
  ArrowRightLeft,
} from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { usePatient } from "@/hooks/queries/usePatients";
import { useEncounters } from "@/hooks/queries/useEncounters";
import {
  useReferrals,
  useCreateReferral,
  useCompleteReferral,
  useAcceptReferral,
  useRespondReferral,
  type ReferralResource } from "@/hooks/queries/useReferrals";
import {
  useTelemedicineSessions,
  useCreateTelemedicineSession,
  type TelemedicineSession } from "@/hooks/queries/useTelemedicine";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient } from "@/lib/api-client";
import {
  buildReferralTeleconsultBrief,
  COORDINATION_COPY,
  getReferralFacilityName,
  getReferralStageCopy,
  isReferralReceivingHere,
  parseConsultationCoordinationMeta,
  toDateTimeLocalValue,
} from "@/lib/consult-workflows";
import { usePrivacyDisplayStore } from "@/hooks/usePrivacyDisplayStore";
import { maskName, maskDob } from "@/lib/pii-mask";
import { ReferralPackageBuilder } from "@/components/ReferralPackageBuilder";
import { useQuery } from "@tanstack/react-query";

// ────────────────────────────────────────────────────────────
// Status / Priority badge helpers
// ────────────────────────────────────────────────────────────

const STATUS_STYLE: Record<string, string> = {
  PENDING: "bg-yellow-100 text-yellow-700",
  ACCEPTED: "bg-blue-100 text-blue-700",
  RESPONDED: "bg-purple-100 text-purple-700",
  COMPLETED: "bg-green-100 text-green-700",
  CANCELLED: "bg-gray-100 text-gray-600",
  IN_PROGRESS: "bg-green-100 text-green-700",
  SCHEDULED: "bg-blue-100 text-blue-700" };

const URGENCY_STYLE: Record<string, string> = {
  EMERGENCY: "bg-red-100 text-red-700",
  URGENT: "bg-orange-100 text-orange-700",
  ROUTINE: "bg-blue-100 text-blue-700" };

const TAB_META: Record<
  ActiveTab,
  { emptyLabel: string; emptyAction: string; subtitle: string }
> = {
  consultations: {
    emptyLabel: "No consultations for this patient",
    emptyAction: "Request first consultation",
    subtitle: "Internal and specialist consult coordination within the live encounter.",
  },
  referrals: {
    emptyLabel: "No referrals found",
    emptyAction: "Create first referral",
    subtitle: "Cross-facility referral tracking, response loops, and package handoff.",
  },
  teleconsults: {
    emptyLabel: "No teleconsultations for this patient",
    emptyAction: "Schedule first teleconsult",
    subtitle: "Virtual sessions, handoffs, and follow-up activity linked to this chart.",
  },
};

type ActiveTab = "consultations" | "referrals" | "teleconsults";
type TeleconsultMode = "VIDEO" | "AUDIO" | "CHAT" | "ASYNC_REVIEW" | "CASE_REVIEW";
type ReferralActionType = "accept" | "respond" | "complete";
type ReferralOutcome =
  | ""
  | "TREATED"
  | "ADMITTED"
  | "FURTHER_REFERRAL"
  | "RETURNED"
  | "FOLLOW_UP_REQUIRED";

// ────────────────────────────────────────────────────────────
// Main component
// ────────────────────────────────────────────────────────────

export default function ConsultsPage() {
  const params = useParams<{ patientId: string }>();
  const router = useRouter();
  const { patientId } = params;
  const { user } = useAuthStore();
  const facility = useFacilityStore((s) => s.facility);

  const { data: patientData } = usePatient(patientId);
  const { data: encountersData } = useEncounters(patientId);
  const { data: referralsData, isLoading: loadingReferrals } = useReferrals(patientId);
  const { data: teleData, isLoading: loadingTele } = useTelemedicineSessions({
    patientId,
    facilityId: facility?.id,
  });

  // Clinical data for ReferralPackageBuilder
  const { data: allergiesData } = useQuery<{ data: Array<{ attributes: Record<string, unknown> }> }>({
    queryKey: ["consult-allergies", patientId],
    queryFn: () => apiClient.get(`/internal/v1/allergies?patient_id=${patientId}`),
    enabled: !!patientId,
  });
  const { data: conditionsData } = useQuery<{ data: Array<{ attributes: Record<string, unknown> }> }>({
    queryKey: ["consult-conditions", patientId],
    queryFn: () => apiClient.get(`/internal/v1/conditions?patient_id=${patientId}`),
    enabled: !!patientId,
  });
  const { data: medsData } = useQuery<{ data: Array<{ attributes: Record<string, unknown> }> }>({
    queryKey: ["consult-meds", patientId],
    queryFn: () => apiClient.get(`/internal/v1/pharmacy/prescriptions?patient_id=${patientId}`),
    enabled: !!patientId,
  });
  const patientAllergies = (allergiesData?.data ?? []).map((a) => (a.attributes.allergen as string) ?? "").filter(Boolean);
  const patientConditions = (conditionsData?.data ?? []).map((c) => (c.attributes.condition_name as string) ?? "").filter(Boolean);
  const patientMedications = (medsData?.data ?? []).map((m) => (m.attributes.medication_name as string) ?? "").filter(Boolean);

  const patient = patientData?.data;
  const privacyLevel = usePrivacyDisplayStore((s) => s.level);
  const patientName = maskName(
    ((patient?.attributes as Record<string, unknown>)?.displayName as string)
    ?? ((patient?.attributes as Record<string, unknown>)?.givenName as string) ?? "Patient",
    privacyLevel,
  );
  const patientDob = maskDob((patient?.attributes as Record<string, unknown>)?.dateOfBirth as string | undefined, privacyLevel);
  const encounters = encountersData?.data ?? [];
  const activeEncounter = encounters.find(
    (e) => e.attributes.status === "IN_PROGRESS" || e.attributes.status === "ACTIVE"
  );
  const allReferrals = referralsData?.data ?? [];
  const allSessions: TelemedicineSession[] = teleData?.data ?? [];

  // Filter by patient
  const patientSessions = allSessions.filter(
    (s) => s.attributes.patient_id === patientId
  );

  // Split referrals: consultations (type=CONSULTATION) vs referrals (everything else)
  const consultations = allReferrals.filter(
    (r) =>
      r.attributes.referralType === "CONSULTATION" ||
      (r.attributes as Record<string, unknown>).referral_type === "CONSULTATION"
  );
  const referrals = allReferrals.filter(
    (r) =>
      r.attributes.referralType !== "CONSULTATION" &&
      (r.attributes as Record<string, unknown>).referral_type !== "CONSULTATION"
  );

  const createReferral = useCreateReferral();
  const completeReferral = useCompleteReferral();
  const acceptReferral = useAcceptReferral();
  const respondReferral = useRespondReferral();
  const createSession = useCreateTelemedicineSession();

  const searchParams = useSearchParams();
  const initialTab = (searchParams.get("tab") as ActiveTab) ?? "referrals";
  const [activeTab, setActiveTab] = useState<ActiveTab>(initialTab);
  const [showNewReferral, setShowNewReferral] = useState(false);
  const [showNewConsult, setShowNewConsult] = useState(false);
  const [showTeleconsultComposer, setShowTeleconsultComposer] = useState(false);
  const [teleconsultMode, setTeleconsultMode] = useState<TeleconsultMode>("VIDEO");
  const [teleconsultScheduledAt, setTeleconsultScheduledAt] = useState("");
  const [teleconsultNotes, setTeleconsultNotes] = useState("");
  const [teleconsultReferralId, setTeleconsultReferralId] = useState("");
  const [activeReferralAction, setActiveReferralAction] = useState<{
    id: string;
    type: ReferralActionType;
  } | null>(null);
  const [referralScheduledAt, setReferralScheduledAt] = useState("");
  const [referralHandoffNote, setReferralHandoffNote] = useState("");
  const [referralResponseNotes, setReferralResponseNotes] = useState("");
  const [referralOutcome, setReferralOutcome] = useState<ReferralOutcome>("");

  useEffect(() => {
    const next = searchParams.get("tab") as ActiveTab | null;
    if (next && next !== activeTab) {
      setActiveTab(next);
    }
  }, [activeTab, searchParams]);

  useEffect(() => {
    const params = new URLSearchParams(searchParams.toString());
    if (params.get("tab") === activeTab) return;
    params.set("tab", activeTab);
    router.replace(`/ehr/${patientId}/consults?${params.toString()}`);
  }, [activeTab, patientId, router, searchParams]);

  useEffect(() => {
    if (activeTab !== "referrals") {
      resetReferralAction();
    }
  }, [activeTab]);

  function resetReferralAction() {
    setActiveReferralAction(null);
    setReferralScheduledAt("");
    setReferralHandoffNote("");
    setReferralResponseNotes("");
    setReferralOutcome("");
  }

  function openTeleconsultComposer(mode: TeleconsultMode, referral?: ReferralResource) {
    setActiveTab("teleconsults");
    setTeleconsultMode(mode);
    if (referral) {
      setTeleconsultReferralId(referral.id);
      setTeleconsultNotes(buildReferralTeleconsultBrief(referral));
    }
    setShowTeleconsultComposer(true);
  }

  function resetTeleconsultComposer() {
    setShowTeleconsultComposer(false);
    setTeleconsultMode("VIDEO");
    setTeleconsultScheduledAt("");
    setTeleconsultNotes("");
    setTeleconsultReferralId("");
  }

  function toggleReferralAction(referral: ReferralResource, type: ReferralActionType) {
    if (activeReferralAction?.id === referral.id && activeReferralAction.type === type) {
      resetReferralAction();
      return;
    }

    setActiveReferralAction({ id: referral.id, type });
    setReferralScheduledAt(toDateTimeLocalValue(referral.attributes.scheduledAt));
    setReferralHandoffNote("");
    setReferralResponseNotes(referral.attributes.response_notes ?? referral.attributes.responseNotes ?? "");
    setReferralOutcome((referral.attributes.outcome ?? "") as ReferralOutcome);
  }

  function handleAcceptReferral(referral: ReferralResource) {
    if (!facility) return;

    acceptReferral.mutate({
      id: referral.id,
      receiving_facility_id: facility.id,
      receiving_facility_name: facility.name,
      scheduled_at: referralScheduledAt || undefined,
      notes: referralHandoffNote.trim() || undefined,
    }, {
      onSuccess: () => {
        resetReferralAction();
      },
    });
  }

  function handleRespondReferral(referral: ReferralResource) {
    const notes = referralResponseNotes.trim();
    if (!notes) return;

    respondReferral.mutate({
      id: referral.id,
      response_notes: notes,
      outcome: referralOutcome || undefined,
    }, {
      onSuccess: () => {
        resetReferralAction();
      },
    });
  }

  function handleCompleteReferralLoop(referral: ReferralResource) {
    completeReferral.mutate({
      id: referral.id,
      outcome: referralOutcome || referral.attributes.outcome || undefined,
    }, {
      onSuccess: () => {
        resetReferralAction();
      },
    });
  }

  function handleScheduleTeleconsult(mode: TeleconsultMode) {
    if (!facility) return;
    createSession.mutate({
      patient_id: patientId,
      provider_id: user?.id,
      facility_id: facility.id,
      session_type: mode,
      encounter_id: activeEncounter?.id,
      referral_id: teleconsultReferralId || undefined,
      scheduled_at: teleconsultScheduledAt || undefined,
      notes: teleconsultNotes || undefined,
    }, {
      onSuccess: () => {
        resetTeleconsultComposer();
      },
    });
  }

  // Tabs definition
  const tabs: { key: ActiveTab; label: string; icon: React.ReactNode; count: number }[] = [
    { key: "consultations", label: "Consultations", icon: <Users className="w-4 h-4" />, count: consultations.length },
    { key: "referrals", label: "Referrals", icon: <Send className="w-4 h-4" />, count: referrals.length },
    { key: "teleconsults", label: "Teleconsults", icon: <Video className="w-4 h-4" />, count: patientSessions.length },
  ];

  const isLoading = loadingReferrals || loadingTele;
  const activeTeleconsult = patientSessions.find((session) => session.attributes.status === "IN_PROGRESS");
  const scheduledTeleconsult = patientSessions.find((session) => session.attributes.status === "SCHEDULED");
  const pendingReferrals = referrals.filter((referral) => referral.attributes.status === "PENDING");
  const pendingConsultations = consultations.filter((consultation) => consultation.attributes.status === "PENDING");
  const receivableReferrals = referrals.filter(
    (referral) =>
      referral.attributes.status === "PENDING" && isReferralReceivingHere(referral, facility),
  );
  const acceptedInboundReferrals = referrals.filter(
    (referral) =>
      referral.attributes.status === "ACCEPTED" && isReferralReceivingHere(referral, facility),
  );
  const closureReadyReferrals = referrals.filter(
    (referral) =>
      referral.attributes.status === "RESPONDED" && !isReferralReceivingHere(referral, facility),
  );
  const awaitingExternalResponseReferrals = referrals.filter(
    (referral) =>
      referral.attributes.status === "PENDING" && !isReferralReceivingHere(referral, facility),
  );
  const closedReferrals = referrals.filter(
    (referral) =>
      referral.attributes.status === "COMPLETED" || referral.attributes.status === "CANCELLED",
  );
  const activeReferralIds = new Set(
    [...receivableReferrals, ...acceptedInboundReferrals, ...closureReadyReferrals].map((referral) => referral.id),
  );
  const monitoringReferrals = referrals.filter(
    (referral) =>
      !activeReferralIds.has(referral.id) &&
      referral.attributes.status !== "COMPLETED" &&
      referral.attributes.status !== "CANCELLED",
  );
  const referralWorkflowSections = [
    {
      key: "action-now",
      title: COORDINATION_COPY.needsActionNow,
      description: "The next best referral steps that can be completed directly in this encounter workspace.",
      referrals: [...receivableReferrals, ...acceptedInboundReferrals, ...closureReadyReferrals],
    },
    {
      key: "tracking",
      title: COORDINATION_COPY.trackingInProgress,
      description: "Referrals already moving through the handoff, review, or response cycle.",
      referrals: monitoringReferrals,
    },
    {
      key: "closed",
      title: COORDINATION_COPY.closedLoops,
      description: "Referral handoffs that already have a documented final state.",
      referrals: closedReferrals,
    },
  ].filter((section) => section.referrals.length > 0);
  const teleconsultReferralOptions = allReferrals.filter(
    (referral) =>
      referral.attributes.status !== "COMPLETED" &&
      referral.attributes.status !== "CANCELLED",
  );
  const openWorkCount =
    pendingConsultations.length +
    pendingReferrals.length +
    patientSessions.filter((session) =>
      session.attributes.status === "SCHEDULED" || session.attributes.status === "IN_PROGRESS",
    ).length;
  const dashboardCards = useMemo(
    () => [
      {
        label: "Open Work",
        value: openWorkCount,
        note: activeEncounter ? "Tied to the active encounter." : "Chart-level coordination.",
      },
      {
        label: "Awaiting Response",
        value: pendingConsultations.length + pendingReferrals.length,
        note: "Requests still waiting on another team or facility.",
      },
      {
        label: "Receiving Actions",
        value: receivableReferrals.length + acceptedInboundReferrals.length,
        note: "Handoffs or specialist responses the current facility can action here.",
      },
      {
        label: "Teleconsult Sessions",
        value: patientSessions.length,
        note: activeTeleconsult ? "A live virtual session is in progress." : "Includes scheduled and completed sessions.",
      },
    ],
    [
      activeEncounter,
      activeTeleconsult,
      openWorkCount,
      patientSessions.length,
      pendingConsultations.length,
      pendingReferrals.length,
      receivableReferrals.length,
      acceptedInboundReferrals.length,
    ],
  );
  const nextAction = useMemo(() => {
    if (activeTeleconsult) {
      return {
        title: "Resume the live teleconsultation",
        detail: `${activeTeleconsult.attributes.session_type} session is currently in progress for this patient.`,
        actionLabel: "Open session",
        onAction: () => router.push(`/telemedicine/session/${activeTeleconsult.id}`),
        tone: "emerald" as const,
      };
    }

    if (receivableReferrals.length > 0) {
      return {
        title: "Receive the referral handoff",
        detail: `${receivableReferrals.length} referral${receivableReferrals.length === 1 ? "" : "s"} can be accepted directly from this encounter workspace.`,
        actionLabel: "Open receiving flow",
        onAction: () => {
          setActiveTab("referrals");
          toggleReferralAction(receivableReferrals[0], "accept");
        },
        tone: "amber" as const,
      };
    }

    if (acceptedInboundReferrals.length > 0) {
      return {
        title: "Send back the specialist response",
        detail: `${acceptedInboundReferrals.length} accepted referral${acceptedInboundReferrals.length === 1 ? "" : "s"} still need a response or next-step recommendation.`,
        actionLabel: "Document response",
        onAction: () => {
          setActiveTab("referrals");
          toggleReferralAction(acceptedInboundReferrals[0], "respond");
        },
        tone: "blue" as const,
      };
    }

    if (closureReadyReferrals.length > 0) {
      return {
        title: "Close the referral loop",
        detail: `${closureReadyReferrals.length} referral response${closureReadyReferrals.length === 1 ? "" : "s"} need clinical review and completion.`,
        actionLabel: "Review referrals",
        onAction: () => {
          setActiveTab("referrals");
          toggleReferralAction(closureReadyReferrals[0], "complete");
        },
        tone: "purple" as const,
      };
    }

    if (scheduledTeleconsult) {
      return {
        title: "Prepare the next teleconsult",
        detail: `A ${scheduledTeleconsult.attributes.session_type} session is scheduled ${formatDateTime(scheduledTeleconsult.attributes.scheduled_at)}.`,
        actionLabel: "Open teleconsults",
        onAction: () => setActiveTab("teleconsults"),
        tone: "blue" as const,
      };
    }

    if (pendingConsultations.length > 0) {
      return {
        title: "Track pending consultations",
        detail: `${pendingConsultations.length} consultation request${pendingConsultations.length === 1 ? "" : "s"} are still awaiting action.`,
        actionLabel: "View consultations",
        onAction: () => setActiveTab("consultations"),
        tone: "amber" as const,
      };
    }

    return {
      title: "Start the next coordinated action",
      detail: "Create a consultation, referral, or teleconsult directly from this encounter workspace.",
      actionLabel: "Open referrals",
      onAction: () => setActiveTab("referrals"),
      tone: "slate" as const,
    };
  }, [
    activeTeleconsult,
    acceptedInboundReferrals,
    closureReadyReferrals,
    pendingConsultations.length,
    receivableReferrals,
    router,
    scheduledTeleconsult,
  ]);
  const telemetryLine = useMemo(() => {
    if (activeTeleconsult) {
      return "Live teleconsult session is active right now.";
    }
    if (scheduledTeleconsult) {
      return `Next teleconsult is scheduled ${formatDateTime(scheduledTeleconsult.attributes.scheduled_at)}.`;
    }
    if (openWorkCount > 0) {
      return `${openWorkCount} coordinated consult task${openWorkCount === 1 ? "" : "s"} remain open.`;
    }
    return "No open consult coordination tasks right now.";
  }, [activeTeleconsult, openWorkCount, scheduledTeleconsult]);

  return (
    <EHRLayout>
      <PageShell title="Consults & Referrals">

        <div className="space-y-5">
          {/* Patient Context Header — Lovable-aligned */}
          {patient && (
            <div className="rounded-3xl border border-slate-200 bg-[linear-gradient(135deg,#f8fbff_0%,#eef6ff_50%,#f8fafc_100%)] p-5 shadow-sm">
              <div className="flex flex-col gap-5 xl:flex-row xl:items-start xl:justify-between">
                <div className="flex items-start gap-3">
                  <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-blue-100 text-blue-700">
                    <Workflow className="w-5 h-5" />
                  </div>
                  <div>
                    <div className="inline-flex items-center gap-2 rounded-full bg-white/80 px-3 py-1 text-xs font-medium text-slate-600">
                      <Sparkles className="h-3.5 w-3.5 text-blue-600" />
                      Unified consults workspace
                    </div>
                    <h3 className="mt-3 text-xl font-semibold text-slate-900">{patientName}</h3>
                    <p className="mt-1 text-sm text-slate-600">
                      Consultations, referrals, and teleconsults now run as one coordinated encounter surface.
                    </p>
                    <div className="mt-3 flex flex-wrap items-center gap-2 text-xs">
                      <span className="rounded-full bg-white px-3 py-1 font-medium text-slate-700 shadow-sm">
                        {String(patient.attributes.cpid ?? patientId)}
                      </span>
                      {activeEncounter ? (
                        <span className="rounded-full bg-emerald-100 px-3 py-1 font-medium text-emerald-700">
                          Active {activeEncounter.attributes.encounterType} encounter
                        </span>
                      ) : (
                        <span className="rounded-full bg-slate-100 px-3 py-1 font-medium text-slate-600">
                          No active encounter
                        </span>
                      )}
                      {facility && (
                        <span className="rounded-full bg-blue-50 px-3 py-1 font-medium text-blue-700">
                          {facility.name}
                        </span>
                      )}
                    </div>
                  </div>
                </div>

                <div className="grid gap-3 sm:grid-cols-2 xl:w-[28rem]">
                  {dashboardCards.map((card) => (
                    <div key={card.label} className="rounded-2xl border border-white/70 bg-white/80 p-4 shadow-sm">
                      <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">
                        {card.label}
                      </p>
                      <p className="mt-2 text-2xl font-semibold text-slate-900">{card.value}</p>
                      <p className="mt-1 text-xs text-slate-500">{card.note}</p>
                    </div>
                  ))}
                </div>
              </div>

              <div className="mt-4 grid gap-3 lg:grid-cols-[minmax(0,1.4fr)_minmax(280px,1fr)]">
                <div className="rounded-2xl border border-white/70 bg-white/80 p-4 shadow-sm">
                  <div className="flex items-start gap-3">
                    <div className={`mt-0.5 flex h-9 w-9 items-center justify-center rounded-2xl ${
                      nextAction.tone === "emerald"
                        ? "bg-emerald-100 text-emerald-700"
                        : nextAction.tone === "purple"
                          ? "bg-purple-100 text-purple-700"
                          : nextAction.tone === "blue"
                            ? "bg-blue-100 text-blue-700"
                            : nextAction.tone === "amber"
                              ? "bg-amber-100 text-amber-700"
                              : "bg-slate-100 text-slate-700"
                    }`}>
                      {nextAction.tone === "purple" ? (
                        <CheckCircle2 className="h-4 w-4" />
                      ) : nextAction.tone === "amber" ? (
                        <AlertTriangle className="h-4 w-4" />
                      ) : nextAction.tone === "blue" ? (
                        <Calendar className="h-4 w-4" />
                      ) : (
                        <Video className="h-4 w-4" />
                      )}
                    </div>
                    <div className="flex-1">
                      <p className="text-sm font-semibold text-slate-900">{nextAction.title}</p>
                      <p className="mt-1 text-sm text-slate-600">{nextAction.detail}</p>
                      <button
                        onClick={nextAction.onAction}
                        className="mt-3 inline-flex items-center gap-1.5 rounded-xl bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800"
                      >
                        {nextAction.actionLabel}
                      </button>
                    </div>
                  </div>
                </div>

                <div className="rounded-2xl border border-white/70 bg-white/80 p-4 shadow-sm">
                  <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">
                    Orchestration Pulse
                  </p>
                  <p className="mt-2 text-sm font-medium text-slate-900">{telemetryLine}</p>
                  <p className="mt-2 text-xs leading-5 text-slate-500">
                    Router state, encounter context, and downstream consult activity are all being surfaced from the same patient workspace instead of splitting across unrelated pages.
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* Quick Access — Lovable: Worklist + Telemedicine Hub */}
          <div className="flex justify-end gap-2">
            <Link
              href="/queue/incoming-referrals"
              className="inline-flex items-center gap-2 px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 hover:bg-gray-50 transition-colors"
            >
              <Activity className="w-4 h-4" />
              Incoming Worklist
            </Link>
            <Link
              href="/telemedicine"
              className="inline-flex items-center gap-2 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
            >
              <Video className="w-4 h-4" />
              Telemedicine Hub
            </Link>
          </div>

          {/* Tab Navigation — Lovable 3-tab layout */}
          <div className="border-b border-gray-200">
            <div className="grid grid-cols-3 gap-0">
              {tabs.map((tab) => (
                <button
                  key={tab.key}
                  onClick={() => setActiveTab(tab.key)}
                  className={`flex items-center justify-center gap-2 px-4 py-3 text-sm font-medium border-b-2 transition-colors ${
                    activeTab === tab.key
                      ? "border-blue-600 text-blue-600"
                      : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
                  }`}
                >
                  {tab.icon}
                  {tab.label}
                  {tab.count > 0 && (
                    <span className={`px-1.5 py-0.5 text-xs rounded-full ${
                      activeTab === tab.key ? "bg-blue-100 text-blue-700" : "bg-gray-100 text-gray-600"
                    }`}>
                      {tab.count}
                    </span>
                  )}
                </button>
              ))}
            </div>
            <div className="flex items-center justify-between gap-3 px-1 py-3">
              <p className="text-sm text-slate-600">{TAB_META[activeTab].subtitle}</p>
              <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600">
                {tabs.find((tab) => tab.key === activeTab)?.count ?? 0} in view
              </span>
            </div>
          </div>

          {isLoading ? (
            <div className="flex items-center justify-center py-16">
              <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
              <span className="ml-2 text-sm text-gray-500">Loading...</span>
            </div>
          ) : (
            <>
              {/* ═══════════ CONSULTATIONS TAB ═══════════ */}
              {activeTab === "consultations" && (
                <div className="space-y-4">
                  <div className="flex items-center justify-between">
                    <h3 className="text-base font-semibold text-gray-900">Consultation Requests</h3>
                    <button
                      onClick={() => setShowNewConsult((v) => !v)}
                      className="inline-flex items-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
                    >
                      <Plus className="w-4 h-4" />
                      Request Consult
                    </button>
                  </div>

                  {showNewConsult && (
                    <ReferralPackageBuilder
                      patientName={patientName}
                      patientDob={patientDob}
                      conditions={patientConditions}
                      allergies={patientAllergies}
                      medications={patientMedications}
                      referralType="CONSULTATION"
                      onSubmit={(data) => {
                        createReferral.mutate({
                          patientId,
                          encounterId: activeEncounter?.id ?? "",
                          referralType: "CONSULTATION",
                          specialty: data.specialty,
                          referredTo: data.referred_to,
                          referredToFacility: data.referred_to_facility,
                          reason: data.reason,
                          urgency: data.urgency,
                          clinicalSummary: data.clinical_summary,
                        }, { onSuccess: () => setShowNewConsult(false) });
                      }}
                      onCancel={() => setShowNewConsult(false)}
                      isPending={createReferral.isPending}
                    />
                  )}

                  {consultations.length === 0 ? (
                    <EmptyState
                      icon={<Users className="w-12 h-12" />}
                      label={TAB_META.consultations.emptyLabel}
                      actionLabel={TAB_META.consultations.emptyAction}
                      onAction={() => setShowNewConsult(true)}
                    />
                  ) : (
                    consultations.map((c) => (
                      <ReferralCard
                        key={c.id}
                        referral={c}
                        patientId={patientId}
                        onJoin={() => openTeleconsultComposer("VIDEO", c)}
                        onComplete={() => completeReferral.mutate({ id: c.id })}
                      />
                    ))
                  )}
                </div>
              )}

              {/* ═══════════ REFERRALS TAB ═══════════ */}
              {activeTab === "referrals" && (
                <div className="space-y-4">
                  <div className="flex items-center justify-between">
                    <h3 className="text-base font-semibold text-gray-900">Referral Tracking</h3>
                    <button
                      onClick={() => setShowNewReferral((v) => !v)}
                      className="inline-flex items-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
                    >
                      <Plus className="w-4 h-4" />
                      New Referral
                    </button>
                  </div>

                  <div className="grid gap-3 xl:grid-cols-[minmax(0,1.4fr)_repeat(3,minmax(0,1fr))]">
                    <div className="rounded-2xl border border-slate-200 bg-[linear-gradient(135deg,#fffaf0_0%,#ffffff_50%,#eff6ff_100%)] p-4 shadow-sm">
                      <div className="flex items-start gap-3">
                        <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-amber-100 text-amber-700">
                          <ArrowRightLeft className="h-4 w-4" />
                        </div>
                        <div className="flex-1">
                          <p className="text-sm font-semibold text-slate-900">Referral orchestration</p>
                          <p className="mt-1 text-sm text-slate-600">
                            Receive the handoff, return specialist guidance, and close the loop without leaving this encounter.
                          </p>
                          <div className="mt-3 flex flex-wrap gap-2 text-xs">
                            {receivableReferrals.length > 0 && (
                              <button
                                type="button"
                                onClick={() => toggleReferralAction(receivableReferrals[0], "accept")}
                                className="rounded-full bg-amber-100 px-3 py-1 font-medium text-amber-800 transition-colors hover:bg-amber-200"
                              >
                                Accept next handoff
                              </button>
                            )}
                            {acceptedInboundReferrals.length > 0 && (
                              <button
                                type="button"
                                onClick={() => toggleReferralAction(acceptedInboundReferrals[0], "respond")}
                                className="rounded-full bg-blue-100 px-3 py-1 font-medium text-blue-800 transition-colors hover:bg-blue-200"
                              >
                                Draft response
                              </button>
                            )}
                            {closureReadyReferrals.length > 0 && (
                              <button
                                type="button"
                                onClick={() => toggleReferralAction(closureReadyReferrals[0], "complete")}
                                className="rounded-full bg-purple-100 px-3 py-1 font-medium text-purple-800 transition-colors hover:bg-purple-200"
                              >
                                Close next loop
                              </button>
                            )}
                          </div>
                        </div>
                      </div>
                    </div>

                    {[
                      {
                        label: "Receiving handoffs",
                        value: receivableReferrals.length,
                        note: "Pending referrals that match the current facility context.",
                        tone: "amber",
                      },
                      {
                        label: "Specialist responses",
                        value: acceptedInboundReferrals.length,
                        note: "Accepted referrals still awaiting findings or recommendations.",
                        tone: "blue",
                      },
                      {
                        label: "Closure review",
                        value: closureReadyReferrals.length,
                        note: "Returned referrals ready for the referring team to close out.",
                        tone: "purple",
                      },
                    ].map((item) => (
                      <div key={item.label} className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                        <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">{item.label}</p>
                        <p className={`mt-2 text-2xl font-semibold ${
                          item.tone === "amber"
                            ? "text-amber-700"
                            : item.tone === "blue"
                              ? "text-blue-700"
                              : "text-purple-700"
                        }`}>{item.value}</p>
                        <p className="mt-1 text-xs leading-5 text-slate-500">{item.note}</p>
                      </div>
                    ))}
                  </div>

                  {awaitingExternalResponseReferrals.length > 0 && (
                    <div className="rounded-2xl border border-slate-200 bg-slate-50/80 p-4">
                      <div className="flex items-start gap-3">
                        <div className="flex h-9 w-9 items-center justify-center rounded-2xl bg-slate-200 text-slate-700">
                          <ClipboardCheck className="h-4 w-4" />
                        </div>
                        <div>
                          <p className="text-sm font-semibold text-slate-900">Waiting on external response</p>
                          <p className="mt-1 text-sm text-slate-600">
                            {awaitingExternalResponseReferrals.length} outbound referral{awaitingExternalResponseReferrals.length === 1 ? "" : "s"} are still with the receiving team. You can still launch a teleconsult from the card if the handoff needs synchronous support.
                          </p>
                        </div>
                      </div>
                    </div>
                  )}

                  {showNewReferral && (
                    <ReferralPackageBuilder
                      patientName={patientName}
                      patientDob={patientDob}
                      conditions={patientConditions}
                      allergies={patientAllergies}
                      medications={patientMedications}
                      referralType="SPECIALIST"
                      onSubmit={(data) => {
                        createReferral.mutate({
                          patientId,
                          encounterId: activeEncounter?.id ?? "",
                          referralType: data.referral_type,
                          specialty: data.specialty,
                          referredTo: data.referred_to,
                          referredToFacility: data.referred_to_facility,
                          reason: data.reason,
                          urgency: data.urgency,
                          clinicalSummary: data.clinical_summary,
                        }, { onSuccess: () => setShowNewReferral(false) });
                      }}
                      onCancel={() => setShowNewReferral(false)}
                      isPending={createReferral.isPending}
                    />
                  )}

                  {referrals.length === 0 ? (
                    <EmptyState
                      icon={<Building2 className="w-12 h-12" />}
                      label={TAB_META.referrals.emptyLabel}
                      actionLabel={TAB_META.referrals.emptyAction}
                      onAction={() => setShowNewReferral(true)}
                    />
                  ) : (
                    <div className="space-y-5">
                      {referralWorkflowSections.map((section) => (
                        <div key={section.key} className="space-y-3">
                          <div className="flex flex-col gap-1 rounded-2xl border border-slate-200 bg-slate-50/70 px-4 py-3">
                            <div className="flex items-center justify-between gap-3">
                              <h4 className="text-sm font-semibold text-slate-900">{section.title}</h4>
                              <span className="rounded-full bg-white px-3 py-1 text-xs font-medium text-slate-600">
                                {section.referrals.length}
                              </span>
                            </div>
                            <p className="text-sm text-slate-600">{section.description}</p>
                          </div>

                          {section.referrals.map((r) => (
                            <div key={r.id} className="space-y-3">
                              <ReferralCard
                                referral={r}
                                patientId={patientId}
                                onJoin={() => openTeleconsultComposer("VIDEO", r)}
                                onAccept={() => toggleReferralAction(r, "accept")}
                                onRespond={() => toggleReferralAction(r, "respond")}
                                onComplete={() => toggleReferralAction(r, "complete")}
                                showWorkflowStage
                                isReceivingContext={isReferralReceivingHere(r, facility)}
                                isActionOpen={activeReferralAction?.id === r.id}
                              />

                              {activeReferralAction?.id === r.id && (
                                <ReferralWorkflowPanel
                                  referral={r}
                                  actionType={activeReferralAction.type}
                                  facilityName={facility?.name}
                                  scheduledAt={referralScheduledAt}
                                  handoffNote={referralHandoffNote}
                                  responseNotes={referralResponseNotes}
                                  outcome={referralOutcome}
                                  isAccepting={acceptReferral.isPending}
                                  isResponding={respondReferral.isPending}
                                  isCompleting={completeReferral.isPending}
                                  onScheduledAtChange={setReferralScheduledAt}
                                  onHandoffNoteChange={setReferralHandoffNote}
                                  onResponseNotesChange={setReferralResponseNotes}
                                  onOutcomeChange={(value) => setReferralOutcome(value as ReferralOutcome)}
                                  onCancel={resetReferralAction}
                                  onAccept={() => handleAcceptReferral(r)}
                                  onRespond={() => handleRespondReferral(r)}
                                  onComplete={() => handleCompleteReferralLoop(r)}
                                />
                              )}
                            </div>
                          ))}
                        </div>
                      ))}
                    </div>
                  )}

                  {/* Create new dashed card — Lovable pattern */}
                  <button
                    onClick={() => setShowNewReferral(true)}
                    className="w-full p-4 border-2 border-dashed border-gray-300 rounded-lg text-center text-gray-400 hover:bg-gray-50 hover:border-gray-400 transition-colors"
                  >
                    <Plus className="w-5 h-5 mx-auto mb-1" />
                    <span className="text-sm">Create new referral</span>
                  </button>
                </div>
              )}

              {/* ═══════════ TELECONSULTS TAB ═══════════ */}
              {activeTab === "teleconsults" && (
                <div className="space-y-4">
                  {/* Active Session Alert — Lovable pattern */}
                  {patientSessions.some((s) => s.attributes.status === "IN_PROGRESS") && (
                    <div className="bg-green-50 border border-green-200 rounded-lg p-4">
                      {patientSessions
                        .filter((s) => s.attributes.status === "IN_PROGRESS")
                        .map((s) => (
                          <div key={s.id} className="flex items-center justify-between">
                            <div className="flex items-center gap-3">
                              <div className="p-3 rounded-full bg-green-200 animate-pulse">
                                <Video className="w-4 h-4 text-green-700" />
                              </div>
                              <div>
                                <p className="font-semibold text-green-800">Active Teleconsultation</p>
                                <p className="text-sm text-gray-600">
                                  {s.attributes.session_type} session
                                  {s.attributes.started_at && ` — started ${new Date(s.attributes.started_at).toLocaleTimeString()}`}
                                </p>
                              </div>
                            </div>
                            <Link
                              href={`/telemedicine/session/${s.id}`}
                              className="px-4 py-2 bg-green-600 text-white text-sm font-medium rounded-lg hover:bg-green-700 transition-colors flex items-center gap-2"
                            >
                              <Video className="w-4 h-4" /> Rejoin Session
                            </Link>
                          </div>
                        ))}
                    </div>
                  )}

                  {/* Upcoming Sessions — Lovable pattern */}
                  {patientSessions.filter((s) => s.attributes.status === "SCHEDULED").length > 0 && (
                    <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 space-y-3">
                      <h4 className="text-sm font-medium text-gray-900 flex items-center gap-2">
                        <Clock className="w-4 h-4 text-blue-600" />
                        Upcoming Sessions
                      </h4>
                      {patientSessions
                        .filter((s) => s.attributes.status === "SCHEDULED")
                        .map((s) => (
                          <div key={s.id} className="flex items-center justify-between p-3 bg-white rounded-lg border">
                            <div className="flex items-center gap-3">
                              <div className="p-2 rounded-full bg-blue-100">
                                <Video className="w-4 h-4 text-blue-600" />
                              </div>
                              <div>
                                <p className="text-sm font-medium">{s.attributes.session_type} Teleconsult</p>
                                <p className="text-xs text-gray-500">
                                  {s.attributes.scheduled_at && new Date(s.attributes.scheduled_at).toLocaleString()}
                                </p>
                              </div>
                            </div>
                            <Link
                              href={`/telemedicine/session/${s.id}`}
                              className="px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700 transition-colors"
                            >
                              Join
                            </Link>
                          </div>
                        ))}
                    </div>
                  )}

                  {/* All Teleconsultations */}
                  <div className="flex items-center justify-between">
                    <h3 className="text-base font-semibold text-gray-900">Teleconsultation Sessions</h3>
                    <button
                      onClick={() => openTeleconsultComposer("VIDEO")}
                      disabled={createSession.isPending || !facility}
                      className="inline-flex items-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
                    >
                      <Video className="w-4 h-4" />
                      Schedule Teleconsult
                    </button>
                  </div>

                  {showTeleconsultComposer && (
                    <div className="rounded-2xl border border-blue-200 bg-blue-50/70 p-4">
                      <div className="flex flex-col gap-4">
                        <div>
                          <h4 className="text-sm font-semibold text-slate-900">Teleconsult launch</h4>
                          <p className="mt-1 text-sm text-slate-600">
                            Choose the mode, optionally schedule it, and link the session back to an open referral when needed.
                          </p>
                        </div>

                        <div className="grid gap-4 md:grid-cols-2">
                          <div>
                            <label className="mb-1 block text-xs font-medium uppercase tracking-[0.14em] text-slate-500">
                              Mode
                            </label>
                            <div className="grid grid-cols-2 gap-2">
                              {([
                                "VIDEO",
                                "AUDIO",
                                "CHAT",
                                "ASYNC_REVIEW",
                                "CASE_REVIEW",
                              ] as TeleconsultMode[]).map((mode) => (
                                <button
                                  key={mode}
                                  type="button"
                                  onClick={() => setTeleconsultMode(mode)}
                                  className={`rounded-xl border px-3 py-2 text-sm font-medium transition-colors ${
                                    teleconsultMode === mode
                                      ? "border-blue-500 bg-blue-600 text-white"
                                      : "border-slate-200 bg-white text-slate-700 hover:bg-slate-50"
                                  }`}
                                >
                                  {mode.replace("_", " ")}
                                </button>
                              ))}
                            </div>
                          </div>

                          <div>
                            <label htmlFor="teleconsultScheduledAt" className="mb-1 block text-xs font-medium uppercase tracking-[0.14em] text-slate-500">
                              Scheduled time
                            </label>
                            <input
                              id="teleconsultScheduledAt"
                              type="datetime-local"
                              value={teleconsultScheduledAt}
                              onChange={(event) => setTeleconsultScheduledAt(event.target.value)}
                              className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                            />
                            <p className="mt-1 text-xs text-slate-500">
                              Leave blank to create a ready-to-start teleconsult now.
                            </p>
                          </div>
                        </div>

                        <div className="grid gap-4 md:grid-cols-2">
                          <div>
                            <label htmlFor="teleconsultReferralId" className="mb-1 block text-xs font-medium uppercase tracking-[0.14em] text-slate-500">
                              Linked referral
                            </label>
                            <select
                              id="teleconsultReferralId"
                              value={teleconsultReferralId}
                              onChange={(event) => setTeleconsultReferralId(event.target.value)}
                              className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                            >
                              <option value="">No linked referral</option>
                              {teleconsultReferralOptions.map((referral) => (
                                <option key={referral.id} value={referral.id}>
                                  {(referral.attributes.specialty || referral.attributes.referralType)} - {referral.attributes.status}
                                </option>
                              ))}
                            </select>
                          </div>

                          <div>
                            <label htmlFor="teleconsultNotes" className="mb-1 block text-xs font-medium uppercase tracking-[0.14em] text-slate-500">
                              Session brief
                            </label>
                            <textarea
                              id="teleconsultNotes"
                              value={teleconsultNotes}
                              onChange={(event) => setTeleconsultNotes(event.target.value)}
                              rows={3}
                              placeholder="Clinical question, objectives, or prep notes..."
                              className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                            />
                          </div>
                        </div>

                        <div className="flex flex-wrap items-center gap-3">
                          <button
                            type="button"
                            onClick={() => handleScheduleTeleconsult(teleconsultMode)}
                            disabled={createSession.isPending || !facility}
                            className="inline-flex items-center gap-2 rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-blue-700 disabled:opacity-50"
                          >
                            {createSession.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Video className="h-4 w-4" />}
                            {teleconsultScheduledAt ? "Schedule session" : "Create session"}
                          </button>
                          <button
                            type="button"
                            onClick={resetTeleconsultComposer}
                            className="rounded-xl px-4 py-2.5 text-sm font-medium text-slate-600 transition-colors hover:bg-white hover:text-slate-900"
                          >
                            Cancel
                          </button>
                        </div>
                      </div>
                    </div>
                  )}

                  {patientSessions.length === 0 ? (
                    <EmptyState
                      icon={<Video className="w-12 h-12" />}
                      label={TAB_META.teleconsults.emptyLabel}
                      actionLabel={TAB_META.teleconsults.emptyAction}
                      onAction={() => openTeleconsultComposer("VIDEO")}
                    />
                  ) : (
                    patientSessions.map((s) => (
                      <TeleconsultCard key={s.id} session={s} />
                    ))
                  )}

                  {/* Quick Connect — Lovable pattern */}
                  <div className="pt-4 border-t border-gray-200">
                    <p className="text-sm font-medium text-gray-900 mb-3">Quick Connect</p>
                    <div className="grid grid-cols-5 gap-2">
                      <button onClick={() => openTeleconsultComposer("VIDEO")} disabled={createSession.isPending || !facility}
                        className="flex flex-col items-center justify-center gap-1 py-3 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 disabled:opacity-50 transition-colors">
                        <Video className="w-5 h-5" />
                        <span className="text-xs">Video</span>
                      </button>
                      <button onClick={() => openTeleconsultComposer("AUDIO")} disabled={createSession.isPending || !facility}
                        className="flex flex-col items-center justify-center gap-1 py-3 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 disabled:opacity-50 transition-colors">
                        <Phone className="w-5 h-5" />
                        <span className="text-xs">Audio</span>
                      </button>
                      <button onClick={() => openTeleconsultComposer("CHAT")} disabled={createSession.isPending || !facility}
                        className="flex flex-col items-center justify-center gap-1 py-3 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 disabled:opacity-50 transition-colors">
                        <MessageSquare className="w-5 h-5" />
                        <span className="text-xs">Chat</span>
                      </button>
                      <button onClick={() => openTeleconsultComposer("ASYNC_REVIEW")} disabled={createSession.isPending || !facility}
                        className="flex flex-col items-center justify-center gap-1 py-3 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 disabled:opacity-50 transition-colors">
                        <FileText className="w-5 h-5" />
                        <span className="text-xs">Async</span>
                      </button>
                      <button onClick={() => openTeleconsultComposer("CASE_REVIEW")} disabled={createSession.isPending || !facility}
                        className="flex flex-col items-center justify-center gap-1 py-3 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 disabled:opacity-50 transition-colors">
                        <Users className="w-5 h-5" />
                        <span className="text-xs">Board</span>
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </PageShell>
    </EHRLayout>
  );
}

// ────────────────────────────────────────────────────────────
// Sub-components
// ────────────────────────────────────────────────────────────

function EmptyState({ icon, label, actionLabel, onAction }: {
  icon: React.ReactNode; label: string; actionLabel: string; onAction: () => void;
}) {
  return (
    <div className="bg-white rounded-lg border border-gray-200 p-10 text-center">
      <div className="text-gray-300 mx-auto mb-4 w-12 h-12 flex items-center justify-center">{icon}</div>
      <p className="text-gray-400 text-sm">{label}</p>
      <button onClick={onAction} className="mt-3 inline-flex items-center gap-1 px-3 py-1.5 border border-gray-300 text-sm text-gray-600 rounded-lg hover:bg-gray-50 transition-colors">
        <Plus className="w-4 h-4" /> {actionLabel}
      </button>
    </div>
  );
}

function getWorkflowStage(status: string): { stage: number; label: string } {
  const map: Record<string, { stage: number; label: string }> = {
    PENDING: { stage: 2, label: "Consent & Submit" },
    ACCEPTED: { stage: 4, label: "Under Review" },
    RESPONDED: { stage: 6, label: "Actions Documented" },
    COMPLETED: { stage: 7, label: "Loop Closed" } };
  return map[status] ?? { stage: 1, label: "Building Package" };
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return "soon";
  return new Date(value).toLocaleString();
}

function ReferralCard({
  referral,
  patientId,
  onJoin,
  onAccept,
  onRespond,
  onComplete,
  showWorkflowStage,
  isReceivingContext = false,
  isActionOpen = false,
}: {
  referral: ReferralResource;
  patientId: string;
  onJoin: () => void;
  onAccept?: () => void;
  onRespond?: () => void;
  onComplete: () => void;
  showWorkflowStage?: boolean;
  isReceivingContext?: boolean;
  isActionOpen?: boolean;
}) {
  const a = referral.attributes;
  const statusStyle = STATUS_STYLE[a.status] ?? "bg-gray-100 text-gray-600";
  const urgencyStyle = URGENCY_STYLE[a.urgency] ?? "bg-gray-100 text-gray-600";
  const wf = getWorkflowStage(a.status);
  const hasResponse = a.response_notes || a.responseNotes;
  const coordinationMeta = parseConsultationCoordinationMeta(a.response_notes ?? a.responseNotes);
  const stageCopy = getReferralStageCopy(a.status, isReceivingContext);
  const referralFacility = getReferralFacilityName(referral);
  const canAccept = a.status === "PENDING" && isReceivingContext && !!onAccept;
  const canRespond = a.status === "ACCEPTED" && isReceivingContext && !!onRespond;
  const canComplete = a.status === "RESPONDED";

  return (
    <div className={`rounded-2xl border p-4 shadow-sm transition-colors ${
      isActionOpen ? "border-blue-300 bg-blue-50/40" : "border-gray-200 bg-white hover:bg-gray-50"
    }`}>
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1 space-y-2">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-full bg-blue-50">
              <Building2 className="w-4 h-4 text-blue-600" />
            </div>
            <div className="flex-1">
              <h4 className="font-semibold text-sm text-gray-900">{a.specialty || a.referralType}</h4>
              <p className="text-xs text-gray-500">{a.referredTo || "Pending assignment"}</p>
            </div>
            <div className="flex items-center gap-2">
              <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${urgencyStyle}`}>{a.urgency}</span>
              <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${statusStyle}`}>{a.status}</span>
            </div>
          </div>

          <div className={`ml-11 rounded-2xl border px-3 py-3 ${
            stageCopy.tone === "amber"
              ? "border-amber-200 bg-amber-50"
              : stageCopy.tone === "blue"
                ? "border-blue-200 bg-blue-50"
                : stageCopy.tone === "purple"
                  ? "border-purple-200 bg-purple-50"
                  : stageCopy.tone === "green"
                    ? "border-emerald-200 bg-emerald-50"
                    : "border-slate-200 bg-slate-50"
          }`}>
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm font-semibold text-slate-900">{stageCopy.title}</span>
              <span className={`rounded-full px-2.5 py-0.5 text-[11px] font-medium ${
                isReceivingContext ? "bg-white text-blue-700" : "bg-white text-slate-700"
              }`}>
                {isReceivingContext ? "Receiving side" : "Referring side"}
              </span>
            </div>
            <p className="mt-1 text-sm text-slate-600">{stageCopy.detail}</p>
          </div>

          {showWorkflowStage && (
            <div className="pl-11">
              <div className="flex items-center gap-2 mb-1">
                <span className="text-xs text-gray-500">Workflow:</span>
                <span className="text-xs font-medium text-gray-700">{wf.stage}/7 - {wf.label}</span>
              </div>
              <div className="flex gap-0.5">
                {[1, 2, 3, 4, 5, 6, 7].map((s) => (
                  <div key={s} className={`h-1.5 flex-1 rounded-full ${
                    s <= wf.stage ? (s === wf.stage ? "bg-blue-600" : "bg-green-500") : "bg-gray-200"
                  }`} />
                ))}
              </div>
            </div>
          )}

          <div className="pl-11 space-y-1">
            <p className="text-sm text-gray-600">{a.reason}</p>
            <div className="flex flex-wrap items-center gap-2 text-xs text-slate-500">
              <span className="rounded-full bg-slate-100 px-2.5 py-1 font-medium text-slate-700">
                Destination: {referralFacility}
              </span>
              {a.referredByName && (
                <span className="rounded-full bg-slate-100 px-2.5 py-1 font-medium text-slate-700">
                  Requested by {a.referredByName}
                </span>
              )}
              {patientId && (
                <span className="rounded-full bg-slate-100 px-2.5 py-1 font-medium text-slate-700">
                  Patient {patientId.slice(0, 8)}
                </span>
              )}
            </div>
            {a.clinicalSummary && (
              <div className="p-2.5 bg-gray-50 rounded-md border border-gray-100">
                <p className="text-xs text-gray-500 mb-0.5">Clinical Summary:</p>
                <p className="text-sm italic text-gray-700">{a.clinicalSummary}</p>
              </div>
            )}
            {hasResponse && (
              <div className="p-2.5 bg-purple-50 rounded-md border border-purple-200">
                <div className="mb-1 flex flex-wrap items-center gap-2">
                  <p className="text-xs font-semibold text-purple-700">
                    Specialist Response
                    {(a.receivingFacilityName || a.receiving_facility_name) && (
                      <span className="font-normal"> ({a.receivingFacilityName ?? a.receiving_facility_name})</span>
                    )}
                  </p>
                  {coordinationMeta.responseSentFromTeleconsult && (
                    <span className="rounded-full bg-white px-2 py-0.5 text-[11px] font-medium text-purple-700">
                      {COORDINATION_COPY.returnedFromTeleconsult}
                    </span>
                  )}
                </div>
                <p className="text-sm text-purple-900">{a.response_notes ?? a.responseNotes}</p>
                {a.outcome && <p className="text-xs text-purple-700 mt-1">Outcome: {a.outcome}</p>}
                {coordinationMeta.nextWorkspaceAction && (
                  <p className="mt-1 text-xs text-purple-700">
                    Next: {coordinationMeta.nextWorkspaceAction}
                  </p>
                )}
              </div>
            )}
            <div className="flex items-center gap-4 text-xs text-gray-400">
              <span>Created: {new Date(a.createdAt).toLocaleDateString()}</span>
              {(a.acceptedAt || a.accepted_at) && (
                <span className="text-blue-600">Accepted: {new Date((a.acceptedAt ?? a.accepted_at)!).toLocaleDateString()}</span>
              )}
              {(a.respondedAt || a.responded_at) && (
                <span className="text-purple-600">Responded: {new Date((a.respondedAt ?? a.responded_at)!).toLocaleDateString()}</span>
              )}
              {a.completedAt && (
                <span className="text-emerald-600">Closed: {new Date(a.completedAt).toLocaleDateString()}</span>
              )}
            </div>
          </div>
        </div>
      </div>

      {a.status !== "COMPLETED" && a.status !== "CANCELLED" && (
        <div className="mt-3 flex flex-wrap items-center gap-2 border-t border-gray-100 pt-3">
          {(a.status === "ACCEPTED" || a.status === "PENDING") && (
            <button
              onClick={onJoin}
              className="flex items-center gap-1 rounded-lg bg-green-600 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-green-700"
            >
              <Video className="w-3 h-3" /> Teleconsult
            </button>
          )}
          {canAccept && (
            <button
              onClick={onAccept}
              className="flex items-center gap-1 rounded-lg bg-amber-500 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-amber-600"
            >
              <ArrowRightLeft className="w-3 h-3" /> Accept Handoff
            </button>
          )}
          {canRespond && (
            <button
              onClick={onRespond}
              className="flex items-center gap-1 rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-blue-700"
            >
              <MessageSquare className="w-3 h-3" /> Add Response
            </button>
          )}
          {canComplete && (
            <button
              onClick={onComplete}
              className="flex items-center gap-1 rounded-lg bg-green-600 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-green-700"
            >
              <CheckCircle2 className="w-3 h-3" /> Complete Loop
            </button>
          )}
        </div>
      )}
    </div>
  );
}

function ReferralWorkflowPanel({
  referral,
  actionType,
  facilityName,
  scheduledAt,
  handoffNote,
  responseNotes,
  outcome,
  isAccepting,
  isResponding,
  isCompleting,
  onScheduledAtChange,
  onHandoffNoteChange,
  onResponseNotesChange,
  onOutcomeChange,
  onCancel,
  onAccept,
  onRespond,
  onComplete,
}: {
  referral: ReferralResource;
  actionType: ReferralActionType;
  facilityName?: string;
  scheduledAt: string;
  handoffNote: string;
  responseNotes: string;
  outcome: ReferralOutcome;
  isAccepting: boolean;
  isResponding: boolean;
  isCompleting: boolean;
  onScheduledAtChange: (value: string) => void;
  onHandoffNoteChange: (value: string) => void;
  onResponseNotesChange: (value: string) => void;
  onOutcomeChange: (value: ReferralOutcome) => void;
  onCancel: () => void;
  onAccept: () => void;
  onRespond: () => void;
  onComplete: () => void;
}) {
  const isAccept = actionType === "accept";
  const isRespond = actionType === "respond";
  const isComplete = actionType === "complete";

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-start gap-3">
        <div className={`flex h-10 w-10 items-center justify-center rounded-2xl ${
          isAccept
            ? "bg-amber-100 text-amber-700"
            : isRespond
              ? "bg-blue-100 text-blue-700"
              : "bg-purple-100 text-purple-700"
        }`}>
          {isAccept ? (
            <ArrowRightLeft className="h-4 w-4" />
          ) : isRespond ? (
            <MessageSquare className="h-4 w-4" />
          ) : (
            <ClipboardCheck className="h-4 w-4" />
          )}
        </div>
        <div className="flex-1">
          <p className="text-sm font-semibold text-slate-900">
            {isAccept
              ? "Confirm receiving handoff"
              : isRespond
                ? "Document the specialist response"
                : "Close the referral loop"}
          </p>
          <p className="mt-1 text-sm text-slate-600">
            {isAccept
              ? `Mark ${facilityName ?? "the current facility"} as the receiving team and optionally set the expected review time.`
              : isRespond
                ? "Capture the receiving team's findings, recommendations, and disposition for the referring clinician."
                : "Record that the referring side has reviewed the response and the care loop is closed."}
          </p>
        </div>
      </div>

      <div className="mt-4 space-y-4">
        {isAccept && (
          <>
            <div className="grid gap-4 md:grid-cols-2">
              <div>
                <label htmlFor={`referral-scheduled-${referral.id}`} className="mb-1 block text-xs font-medium uppercase tracking-[0.14em] text-slate-500">
                  Planned review time
                </label>
                <input
                  id={`referral-scheduled-${referral.id}`}
                  type="datetime-local"
                  value={scheduledAt}
                  onChange={(event) => onScheduledAtChange(event.target.value)}
                  className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm focus:border-amber-500 focus:outline-none focus:ring-2 focus:ring-amber-500"
                />
              </div>
              <div>
                <label htmlFor={`referral-handoff-${referral.id}`} className="mb-1 block text-xs font-medium uppercase tracking-[0.14em] text-slate-500">
                  Receiving note
                </label>
                <textarea
                  id={`referral-handoff-${referral.id}`}
                  rows={3}
                  value={handoffNote}
                  onChange={(event) => onHandoffNoteChange(event.target.value)}
                  placeholder="Triage note, expected specialist, or preparation instructions..."
                  className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm focus:border-amber-500 focus:outline-none focus:ring-2 focus:ring-amber-500"
                />
              </div>
            </div>
            <div className="rounded-xl bg-amber-50 px-3 py-2 text-xs text-amber-800">
              Accepting here keeps the referral inside the active encounter workspace while updating the receiving facility context.
            </div>
          </>
        )}

        {(isRespond || isComplete) && (
          <div>
            <label htmlFor={`referral-outcome-${referral.id}`} className="mb-1 block text-xs font-medium uppercase tracking-[0.14em] text-slate-500">
              Outcome
            </label>
            <select
              id={`referral-outcome-${referral.id}`}
              value={outcome}
              onChange={(event) => onOutcomeChange(event.target.value as ReferralOutcome)}
              className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">Select outcome...</option>
              <option value="TREATED">Treated - patient managed</option>
              <option value="ADMITTED">Admitted - ongoing care</option>
              <option value="FURTHER_REFERRAL">Further referral needed</option>
              <option value="RETURNED">Returned to referring facility</option>
              <option value="FOLLOW_UP_REQUIRED">Follow-up required</option>
            </select>
          </div>
        )}

        {isRespond && (
          <div>
            <label htmlFor={`referral-response-${referral.id}`} className="mb-1 block text-xs font-medium uppercase tracking-[0.14em] text-slate-500">
              Response notes
            </label>
            <textarea
              id={`referral-response-${referral.id}`}
              rows={4}
              value={responseNotes}
              onChange={(event) => onResponseNotesChange(event.target.value)}
              placeholder="Assessment findings, treatment provided, follow-up, or recommendations..."
              className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        )}

        {isComplete && (
          <div className="rounded-xl bg-purple-50 px-3 py-3 text-sm text-purple-900">
            This marks the referral as clinically reviewed on the referring side so the loop is visibly closed in the chart.
          </div>
        )}

        <div className="flex flex-wrap items-center gap-3">
          {isAccept && (
            <button
              type="button"
              onClick={onAccept}
              disabled={isAccepting}
              className="inline-flex items-center gap-2 rounded-xl bg-amber-500 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-amber-600 disabled:opacity-50"
            >
              {isAccepting ? <Loader2 className="h-4 w-4 animate-spin" /> : <ArrowRightLeft className="h-4 w-4" />}
              Confirm receiving handoff
            </button>
          )}
          {isRespond && (
            <button
              type="button"
              onClick={onRespond}
              disabled={isResponding || !responseNotes.trim()}
              className="inline-flex items-center gap-2 rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-blue-700 disabled:opacity-50"
            >
              {isResponding ? <Loader2 className="h-4 w-4 animate-spin" /> : <MessageSquare className="h-4 w-4" />}
              Send specialist response
            </button>
          )}
          {isComplete && (
            <button
              type="button"
              onClick={onComplete}
              disabled={isCompleting}
              className="inline-flex items-center gap-2 rounded-xl bg-purple-600 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-purple-700 disabled:opacity-50"
            >
              {isCompleting ? <Loader2 className="h-4 w-4 animate-spin" /> : <CheckCircle2 className="h-4 w-4" />}
              Mark loop closed
            </button>
          )}
          <button
            type="button"
            onClick={onCancel}
            className="rounded-xl px-4 py-2.5 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900"
          >
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
}

function TeleconsultCard({ session }: { session: TelemedicineSession }) {
  const a = session.attributes;
  const statusStyle = STATUS_STYLE[a.status] ?? "bg-gray-100 text-gray-600";
  const isJoinable = a.status === "SCHEDULED" || a.status === "IN_PROGRESS";

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-4 hover:bg-gray-50 transition-colors">
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-center gap-3 flex-1">
          <div className={`p-2 rounded-full ${a.status === "IN_PROGRESS" ? "bg-green-100" : "bg-blue-50"}`}>
            <Video className={`w-4 h-4 ${a.status === "IN_PROGRESS" ? "text-green-600" : "text-blue-600"}`} />
          </div>
          <div className="flex-1">
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-gray-900">{a.session_type} Teleconsult</span>
              <span className="px-2 py-0.5 text-xs rounded-full bg-gray-100 text-gray-600">{a.session_type}</span>
              <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${statusStyle}`}>{a.status}</span>
            </div>
            <div className="flex items-center gap-3 mt-0.5 text-xs text-gray-500">
              {a.scheduled_at && (
                <span className="flex items-center gap-1">
                  <Calendar className="w-3 h-3" />
                  {new Date(a.scheduled_at).toLocaleString()}
                </span>
              )}
              {a.referral_id && <span className="text-blue-600">Linked to referral</span>}
              {a.duration_seconds != null && a.duration_seconds > 0 && (
                <span>{Math.round(a.duration_seconds / 60)} min</span>
              )}
            </div>
            {a.notes && <p className="text-xs text-gray-500 mt-1">{a.notes}</p>}
          </div>
        </div>
        {isJoinable && (
          <Link href={`/telemedicine/session/${session.id}`}
            className="px-3 py-1.5 bg-green-600 text-white text-xs font-medium rounded-lg hover:bg-green-700 transition-colors flex items-center gap-1">
            <Video className="w-3 h-3" /> {a.status === "IN_PROGRESS" ? "Rejoin" : "Join"}
          </Link>
        )}
      </div>
    </div>
  );
}

