/**
 * ProviderDashboardScreen — Worklist/task queue for the provider.
 *
 * Shows assigned tasks, today's encounters, overdue items, and quick actions.
 */

import React, { useState, useEffect, useCallback, useMemo } from "react";
import { View, Text, StyleSheet, ScrollView, Pressable, Alert } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useAuth } from "@impilo/mobile-auth";
import { Screen, Header, Card, CardBody, CardHeader, Button, Badge, LoadingSpinner, EmptyState, ErrorState, DashboardSection, ServiceCard, colors } from "@impilo/mobile-design-system";
import { buildProviderServiceCards } from "../../navigation/providerServiceNavigation";
import { useCommunicationDashboard } from "@impilo/mobile-messaging";
import { appStore, useAppStore } from "../../stores/appStore";
import { useEncounterStore } from "../../stores/encounterStore";
import { getMyTasks } from "../../services/taskService";
import { listEncounters } from "../../services/encounterService";
import { getFacilityMetrics } from "../../services/supportService";
import { getClinicalWorklist } from "../../services/clinicalWorklistService";
import { getProviderLearningSummary } from "../../services/learningService";
import type {
  Task,
  Encounter,
  FacilityMetrics,
  ProviderTabKey,
  ClinicalWorklistItem,
  ClinicalWorklistSummary,
  ProviderLearningSummary,
} from "../../types";

import { buildProviderCommsKpis } from "../../lib/providerCommsKpis";
import { useSwitchAppMode } from "../../hooks/useSwitchAppMode";
import type { AppMode } from "../../types";

export { buildProviderCommsKpis };

const BLUE = "#1E40AF";

const LAUNCH_ACTIONS: Array<{
  key: string;
  title: string;
  subtitle: string;
  target: ProviderTabKey;
  icon: string;
  color: string;
  bg: string;
}> = [
  { key: "fundo-learning", title: "Fundo Learning", subtitle: "Prioritize training, CPD and required modules", target: "apps", icon: "school-outline", color: "#0E7490", bg: "#CFFAFE" },
  { key: "find-patient", title: "Find Patient", subtitle: "Search and start a new encounter", target: "patients", icon: "search-outline", color: "#059669", bg: colors.ui.success.light },
  { key: "queue",        title: "Open Queue",   subtitle: "Pick up the next waiting patient",  target: "queue",    icon: "people-outline", color: BLUE, bg: "#DBEAFE" },
  { key: "results",      title: "Lab Results",  subtitle: "Check new lab and imaging output",  target: "results",  icon: "flask-outline",  color: "#7C3AED", bg: "#EDE9FE" },
  { key: "tools",        title: "Clinical Tools", subtitle: "Notes, plans, CDS, and handoff", target: "tools",    icon: "construct-outline", color: "#D97706", bg: colors.ui.warning.light },
];

const PRIORITY_VARIANT: Record<string, string> = {
  URGENT: "destructive",
  HIGH: "warning",
  MEDIUM: "secondary",
  LOW: "outline",
};

function badgeVariantForPriority(priority?: string): "destructive" | "secondary" | "outline" {
  const key = String(priority ?? "MEDIUM").toUpperCase();
  const mapped = PRIORITY_VARIANT[key];
  if (mapped === "destructive" || mapped === "secondary" || mapped === "outline") {
    return mapped;
  }
  return "secondary";
}

export function ProviderDashboardScreen() {
  const auth = useAuth();
  const { facilityId, facilityName, workspaceName, setProviderTab, setLearningSubject } = useAppStore();
  // Every mode entry from this screen goes through the minting switcher —
  // supervisor is a governed mode, so jumping straight there would leave the
  // clinician's CLINICAL_CARE token in place behind a supervisory workspace.
  const { switchAppMode } = useSwitchAppMode();
  const enterMode = useCallback((mode: AppMode) => void switchAppMode(mode), [switchAppMode]);
  const openSupervisorEscalations = useCallback(() => {
    appStore.getState().setSupervisorEntryTab("escalations");
    void switchAppMode("supervisor");
  }, [switchAppMode]);
  const { activeEncounter } = useEncounterStore();
  const { dashboard: commsDashboard } = useCommunicationDashboard();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [openEncounters, setOpenEncounters] = useState<Encounter[]>([]);
  const [metrics, setMetrics] = useState<FacilityMetrics | null>(null);
  const [clinicalWorklist, setClinicalWorklist] = useState<ClinicalWorklistItem[]>([]);
  const [worklistSummary, setWorklistSummary] = useState<ClinicalWorklistSummary | null>(null);
  const [learningSummary, setLearningSummary] = useState<ProviderLearningSummary | null>(null);
  const [learningError, setLearningError] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);

  const serviceCards = useMemo(
    () =>
      buildProviderServiceCards({
        setProviderTab,
        enterMode,
        openClinicalTool: (toolId) => appStore.getState().setClinicalToolsInitialTab(toolId),
      }),
    [setProviderTab, enterMode]
  );

  const loadDashboard = useCallback(async () => {
    if (!facilityId) {
      setTasks([]);
      setOpenEncounters([]);
      setMetrics(null);
      setClinicalWorklist([]);
      setWorklistSummary(null);
      setLearningSummary(null);
      setLearningError(false);
      setLoading(false);
      setError(null);
      return;
    }
    setLoading(true);
    setError(null);
    const authUser = auth.user as
      | {
          sub?: string;
          provider_id?: string;
          providerId?: string;
        }
      | undefined;
    const learningSubjectId =
      authUser?.provider_id ?? authUser?.providerId ?? authUser?.sub ?? null;
    if (learningSubjectId) {
      setLearningSubject("PROVIDER", learningSubjectId);
    }
    try {
      const [taskResult, encounterResult, metricsResult, worklistResult, learningResult] = await Promise.all([
        getMyTasks(undefined, 0, 50),
        listEncounters(undefined, 0, 20),
        getFacilityMetrics(facilityId).catch(() => null),
        getClinicalWorklist(facilityId).catch(() => ({ items: [], summary: null })),
        learningSubjectId
          ? getProviderLearningSummary({ subjectType: "PROVIDER", subjectId: learningSubjectId }).catch(() => null)
          : Promise.resolve(null),
      ]);
      setTasks(taskResult.tasks);
      setOpenEncounters(
        encounterResult.encounters.filter((e) => e.status === "IN_PROGRESS")
      );
      setMetrics(metricsResult);
      setClinicalWorklist(worklistResult.items ?? []);
      setWorklistSummary(worklistResult.summary ?? null);
      setLearningSummary(learningResult);
      setLearningError(learningSubjectId !== null && learningResult === null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load dashboard");
    } finally {
      setLoading(false);
    }
  }, [facilityId, auth.user, setLearningSubject]);

  useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);

  if (loading) {
    return (
      <Screen>
        <Header title="Worklist" />
        <LoadingSpinner size="lg" />
      </Screen>
    );
  }

  if (error) {
    return (
      <Screen>
        <Header title="Worklist" />
        <ErrorState title="Error" message={error} onRetry={loadDashboard} />
      </Screen>
    );
  }

  const pendingTasks = tasks.filter((t) => t.status === "PENDING" || t.status === "IN_PROGRESS");
  const overdueTasks = tasks.filter((t) => t.status === "OVERDUE");

  if (!facilityId) {
    return (
      <Screen>
        <Header title="Launch Care" />
        <EmptyState
          title="Select a facility first"
          message="Choose your facility and workspace before starting service delivery."
          action={{
            label: "Refresh",
            onPress: loadDashboard,
          }}
        />
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="Launch Care" accent={BLUE} />
      <ScrollView
        testID="provider-dashboard"
        style={styles.scroll}
        contentContainerStyle={styles.container}
        showsVerticalScrollIndicator={false}
      >
        {/* Hero banner */}
        <View style={styles.heroBanner}>
          <View style={styles.heroLeft}>
            <Text style={styles.heroEyebrow}>Provider Mode</Text>
            <Text style={styles.heroTitle}>
              {activeEncounter ? "Encounter in progress" : "Start service delivery"}
            </Text>
            <View style={styles.contextRow}>
              {facilityName ? (
                <View style={styles.contextChip}>
                  <Ionicons name="business-outline" size={11} color="rgba(255,255,255,0.8)" />
                  <Text style={styles.contextChipText}>{facilityName}</Text>
                </View>
              ) : null}
              {workspaceName ? (
                <View style={styles.contextChip}>
                  <Ionicons name="desktop-outline" size={11} color="rgba(255,255,255,0.8)" />
                  <Text style={styles.contextChipText}>{workspaceName}</Text>
                </View>
              ) : null}
              {activeEncounter ? (
                <View style={[styles.contextChip, styles.contextChipActive]}>
                  <Ionicons name="pulse" size={11} color="#FFFFFF" />
                  <Text style={styles.contextChipText}>{activeEncounter.encounterType}</Text>
                </View>
              ) : null}
            </View>
          </View>
          <Pressable
            style={({ pressed }) => [styles.heroCtaBtn, { opacity: pressed ? 0.85 : 1 }]}
            onPress={() => setProviderTab(activeEncounter ? "encounter" : "patients")}
            testID="launch-primary-action"
          >
            <Ionicons
              name={activeEncounter ? "pulse" : "add-circle"}
              size={20}
              color={BLUE}
            />
            <Text style={styles.heroCtaText}>
              {activeEncounter ? "Resume" : "New Encounter"}
            </Text>
          </Pressable>
        </View>

        {/* Summary metrics */}
        <View style={styles.metricsRow}>
          {[
            { label: "Pending", value: pendingTasks.length, color: colors.gray[900], icon: "list" },
            { label: "Seen Today", value: metrics?.patientsSeenToday ?? openEncounters.length, color: "#059669", icon: "checkmark-circle" },
            { label: "Open Visits", value: metrics?.encountersOpen ?? overdueTasks.length, color: overdueTasks.length > 0 ? colors.ui.error.main : colors.gray[900], icon: "medical" },
            { label: "Stock Alerts", value: metrics?.stockAlerts ?? 0, color: (metrics?.stockAlerts ?? 0) > 0 ? colors.ui.error.main : colors.gray[900], icon: "warning" },
          ].map((m) => (
            <Card key={m.label} variant="elevated" padding="sm" style={styles.metricCard}>
              <CardBody>
                <View style={styles.metricContent}>
                  <Ionicons name={m.icon as never} size={18} color={m.color} />
                  <Text style={[styles.metricValue, { color: m.color }]}>{String(m.value)}</Text>
                  <Text style={styles.metricLabel}>{m.label}</Text>
                </View>
              </CardBody>
            </Card>
          ))}
        </View>

        {commsDashboard ? (
          <>
            <Text style={styles.sectionLabel}>Comms Snapshot</Text>
            {commsDashboard.source_health && Object.values(commsDashboard.source_health).some((state) => state !== "UP") ? (
              <View style={styles.commsWarning}>
                <Text style={styles.commsWarningText}>
                  Some comms sources are degraded. Metrics may be partial; tap Refresh or open Comms for details.
                </Text>
              </View>
            ) : null}
            <View style={styles.metricsRow}>
              {buildProviderCommsKpis(commsDashboard, setProviderTab, openSupervisorEscalations).map((m) => (
                <Pressable
                  key={m.label}
                  onPress={m.onPress}
                  style={({ pressed }) => ({ opacity: pressed ? 0.85 : 1, flex: 1 })}
                  testID={`comms-kpi-${m.label.toLowerCase().replace(" ", "-")}`}
                  accessibilityLabel={`${m.label}. ${m.hint}`}
                >
                  <Card variant="elevated" padding="sm" style={styles.metricCard}>
                    <CardBody>
                      <View style={styles.metricContent}>
                        <Ionicons name={m.icon as never} size={18} color={m.color} />
                        <Text style={[styles.metricValue, { color: m.color }]}>{String(m.value ?? 0)}</Text>
                        <Text style={styles.metricLabel}>{m.label}</Text>
                        <Text style={styles.metricHint}>{m.hint}</Text>
                      </View>
                    </CardBody>
                  </Card>
                </Pressable>
              ))}
            </View>
          </>
        ) : null}

        {/* Quick launch actions */}
        <Text style={styles.sectionLabel}>Quick Launch</Text>
        <View style={styles.launchGrid}>
          {LAUNCH_ACTIONS.map((action) => (
            <Pressable
              key={action.key}
              style={({ pressed }) => [styles.launchCard, { opacity: pressed ? 0.88 : 1 }]}
              onPress={() => setProviderTab(action.target)}
              testID={`launch-${action.key}`}
            >
              <View style={[styles.launchIconCircle, { backgroundColor: action.bg }]}>
                <Ionicons name={action.icon as never} size={22} color={action.color} />
              </View>
              <Text style={[styles.launchTitle, { color: action.color }]}>{action.title}</Text>
              <Text style={styles.launchSubtitle}>{action.subtitle}</Text>
            </Pressable>
          ))}
          {activeEncounter ? (
            <Pressable
              style={({ pressed }) => [styles.launchCard, styles.launchCardActive, { opacity: pressed ? 0.88 : 1 }]}
              onPress={() => setProviderTab("encounter")}
              testID="launch-resume-encounter"
            >
              <View style={[styles.launchIconCircle, { backgroundColor: colors.ui.success.light }]}>
                <Ionicons name="pulse" size={22} color="#059669" />
              </View>
              <Text style={[styles.launchTitle, { color: "#059669" }]}>Resume</Text>
              <Text style={styles.launchSubtitle}>Continue live encounter</Text>
            </Pressable>
          ) : null}
        </View>

        <DashboardSection
          title="Work Services"
          subtitle="Clinical, orders, imaging, blood bank, inventory, training, and live events"
          testID="provider-service-hub"
        >
          {serviceCards.map((service) => (
            <ServiceCard
              key={service.slug}
              testID={`service-card-${service.slug}`}
              name={service.name}
              description={service.description}
              icon={
                service.icon ? (
                  <Ionicons name={service.icon as never} size={22} color={BLUE} />
                ) : undefined
              }
              wiringStatus={service.wiringStatus}
              onPress={service.onPress}
              disabled={service.disabled}
              accentColor={BLUE}
            />
          ))}
        </DashboardSection>

        <Text style={styles.sectionLabel}>Fundo Learning Snapshot</Text>
        <View style={styles.metricsRow}>
          {[
            {
              label: "Required",
              value: learningSummary?.required ?? 0,
              color: "#0E7490",
              icon: "school-outline",
              focus: "required" as const,
            },
            {
              label: "Overdue",
              value: learningSummary?.overdue ?? 0,
              color: (learningSummary?.overdue ?? 0) > 0 ? colors.ui.error.main : colors.gray[900],
              icon: "time-outline",
              focus: "overdue" as const,
            },
            {
              label: "CPD Eligible",
              value: learningSummary?.cpdEligible ?? 0,
              color: "#7C3AED",
              icon: "ribbon-outline",
              focus: "cpd" as const,
            },
          ].map((m) => (
            <Pressable
              key={m.label}
              onPress={() => {
                appStore.getState().setAppsFocus(m.focus);
                setProviderTab("apps");
              }}
              style={({ pressed }) => ({ opacity: pressed ? 0.85 : 1, flex: 1 })}
              testID={`learning-kpi-${m.label.toLowerCase().replace(" ", "-")}`}
            >
              <Card variant="elevated" padding="sm" style={styles.metricCard}>
                <CardBody>
                  <View style={styles.metricContent}>
                    <Ionicons name={m.icon as never} size={18} color={m.color} />
                    <Text style={[styles.metricValue, { color: m.color }]}>{String(m.value)}</Text>
                    <Text style={styles.metricLabel}>{m.label}</Text>
                  </View>
                </CardBody>
              </Card>
            </Pressable>
          ))}
        </View>
        {learningError ? (
          <View style={styles.commsWarning}>
            <Text style={styles.commsWarningText}>
              Fundo KPIs are temporarily unavailable. Open Apps for full learning details.
            </Text>
          </View>
        ) : null}

        <Text style={styles.sectionLabel}>Safety, support &amp; comms</Text>
        <View style={styles.supportRow}>
          <Pressable
            style={({ pressed }) => [styles.supportChip, { opacity: pressed ? 0.88 : 1 }]}
            onPress={() => setProviderTab("messaging")}
            testID="launch-comms-hub"
          >
            <Ionicons name="chatbubbles-outline" size={16} color={BLUE} />
            <Text style={styles.supportChipText}>Comms</Text>
          </Pressable>
          <Pressable
            style={({ pressed }) => [styles.supportChip, { opacity: pressed ? 0.88 : 1 }]}
            onPress={() => {
              appStore.getState().setClinicalToolsInitialTab("telemedicine");
              setProviderTab("tools");
            }}
            testID="launch-telehealth-tools"
          >
            <Ionicons name="videocam-outline" size={16} color="#0891B2" />
            <Text style={styles.supportChipText}>Telehealth</Text>
          </Pressable>
          <Pressable
            style={({ pressed }) => [styles.supportChip, { opacity: pressed ? 0.88 : 1 }]}
            onPress={() => {
              appStore.getState().setClinicalToolsInitialTab("learning");
              setProviderTab("tools");
            }}
            testID="launch-fundo-learning"
          >
            <Ionicons name="school-outline" size={16} color="#1E40AF" />
            <Text style={styles.supportChipText}>Fundo</Text>
          </Pressable>
          <Pressable
            style={({ pressed }) => [styles.supportChip, { opacity: pressed ? 0.88 : 1 }]}
            onPress={() => {
              openSupervisorEscalations();
            }}
            testID="launch-system-support"
          >
            <Ionicons name="construct-outline" size={16} color="#7C3AED" />
            <Text style={styles.supportChipText}>Support</Text>
          </Pressable>
          <Pressable
            style={({ pressed }) => [styles.supportChip, styles.supportChipSos, { opacity: pressed ? 0.88 : 1 }]}
            onPress={() =>
              Alert.alert(
                "SOS & escalations",
                "For immediate danger to life, call your local emergency number.\n\nFor facility incidents, outages, safeguarding, or security events, open Support to file a ticket or use your facility escalation protocol.",
                [
                  { text: "Cancel", style: "cancel" },
                  {
                    text: "Open support",
                    onPress: () => {
                      openSupervisorEscalations();
                    },
                  },
                ],
              )
            }
            testID="launch-sos-info"
          >
            <Ionicons name="warning-outline" size={16} color="#B91C1C" />
            <Text style={[styles.supportChipText, { color: "#B91C1C" }]}>SOS</Text>
          </Pressable>
        </View>

        {/* Unified clinical inbox */}
        <Text style={styles.sectionLabel}>Unified Clinical Inbox</Text>
        {clinicalWorklist.length === 0 ? (
          <EmptyState
            title="No high-priority inbox items"
            message="Queue, referrals, tasks, and orders are clear right now."
          />
        ) : (
          <>
            <View style={styles.worklistSummaryRow}>
              <Badge variant="secondary" size="sm">{`${worklistSummary?.total ?? clinicalWorklist.length} total`}</Badge>
              <Badge variant="warning" size="sm">{`${worklistSummary?.urgent ?? 0} urgent`}</Badge>
              <Badge variant="outline" size="sm">{`${worklistSummary?.overdue ?? 0} overdue`}</Badge>
            </View>
            {clinicalWorklist.slice(0, 5).map((item) => (
              <Card key={item.id} variant="elevated" padding="sm">
                <CardBody>
                  <View style={styles.worklistRow}>
                    <View style={styles.taskLeft}>
                      <Text style={styles.taskTitle}>{item.title}</Text>
                      <Text style={styles.taskPatient}>
                        {`${item.kind} · ${item.status ?? "PENDING"} · ${item.source}`}
                      </Text>
                      {item.description ? <Text style={styles.taskDue}>{item.description}</Text> : null}
                    </View>
                    <Badge variant={badgeVariantForPriority(item.priority)} size="sm">
                      {String(item.priority ?? "MEDIUM").toUpperCase()}
                    </Badge>
                  </View>
                </CardBody>
              </Card>
            ))}
          </>
        )}

        {/* Task list */}
        <Text style={styles.sectionLabel}>My Tasks</Text>
        {pendingTasks.length === 0 ? (
          <EmptyState title="No pending tasks" message="All caught up for this shift" />
        ) : (
          pendingTasks.map((task) => (
            <Card key={task.id} variant="elevated" padding="md">
              <CardBody>
                <Pressable
                  testID={`task-${task.id}`}
                  onPress={() => setSelectedTaskId(task.id === selectedTaskId ? null : task.id)}
                >
                  <View style={styles.taskRow}>
                    <View style={styles.taskLeft}>
                      <Text style={styles.taskTitle}>{task.title}</Text>
                      {task.patientName ? (
                        <View style={styles.taskMeta}>
                          <Ionicons name="person-outline" size={12} color={colors.gray[500]} />
                          <Text style={styles.taskPatient}>{task.patientName}</Text>
                        </View>
                      ) : null}
                      <View style={styles.taskMeta}>
                        <Badge variant="outline" size="sm">{task.taskType}</Badge>
                        {task.dueAt ? (
                          <Text style={styles.taskDue}>
                            {`Due ${new Date(task.dueAt).toLocaleDateString()}`}
                          </Text>
                        ) : null}
                      </View>
                    </View>
                    <Badge variant={badgeVariantForPriority(task.priority)} size="sm">
                      {task.priority}
                    </Badge>
                  </View>
                </Pressable>
              </CardBody>
            </Card>
          ))
        )}

        {/* Open encounters */}
        {openEncounters.length > 0 ? (
          <>
            <Text style={styles.sectionLabel}>Open Encounters</Text>
            {openEncounters.map((enc) => (
              <Card key={enc.id} variant="elevated" padding="md">
                <CardBody>
                  <View testID={`encounter-${enc.id}`} style={styles.encounterRow}>
                    <View style={styles.encounterIconWrap}>
                      <Ionicons name="pulse" size={20} color={BLUE} />
                    </View>
                    <View style={styles.encounterText}>
                      <Text style={styles.taskTitle}>{`${enc.encounterType} Visit`}</Text>
                      <Text style={styles.taskPatient}>
                        {`Started ${new Date(enc.startedAt).toLocaleTimeString()}`}
                      </Text>
                      {enc.chiefComplaint ? (
                        <Text style={styles.taskDue}>{`CC: ${enc.chiefComplaint}`}</Text>
                      ) : null}
                    </View>
                    <Badge variant="info" size="sm">Active</Badge>
                  </View>
                </CardBody>
              </Card>
            ))}
          </>
        ) : null}

        <View style={styles.refreshRow}>
          <Button
            title="Refresh"
            variant="outline"
            onPress={loadDashboard}
            testID="refresh-dashboard"
            icon={<Ionicons name="refresh-outline" size={16} color="#059669" />}
          />
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  scroll: {
    flex: 1,
  },
  container: {
    padding: 16,
    gap: 12,
    paddingBottom: 32,
  },
  heroBanner: {
    backgroundColor: BLUE,
    borderRadius: 20,
    padding: 20,
    flexDirection: "row",
    alignItems: "flex-start",
    justifyContent: "space-between",
    gap: 12,
    shadowColor: BLUE,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 12,
    elevation: 6,
  },
  heroLeft: {
    flex: 1,
    gap: 6,
  },
  heroEyebrow: {
    fontSize: 11,
    fontWeight: "700",
    color: "rgba(255,255,255,0.7)",
    textTransform: "uppercase",
    letterSpacing: 0.8,
  },
  heroTitle: {
    fontSize: 20,
    fontWeight: "800",
    color: "#FFFFFF",
    letterSpacing: -0.3,
  },
  contextRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 6,
    marginTop: 4,
  },
  contextChip: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    backgroundColor: "rgba(255,255,255,0.15)",
    borderRadius: 999,
    paddingVertical: 3,
    paddingHorizontal: 8,
  },
  contextChipActive: {
    backgroundColor: "rgba(255,255,255,0.25)",
  },
  contextChipText: {
    fontSize: 11,
    color: "rgba(255,255,255,0.9)",
    fontWeight: "500",
  },
  heroCtaBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    backgroundColor: "#FFFFFF",
    borderRadius: 12,
    paddingVertical: 10,
    paddingHorizontal: 14,
  },
  heroCtaText: {
    fontSize: 13,
    fontWeight: "700",
    color: BLUE,
  },
  metricsRow: {
    flexDirection: "row",
    gap: 8,
  },
  metricCard: {
    flex: 1,
  },
  metricContent: {
    alignItems: "center",
    gap: 2,
  },
  metricValue: {
    fontSize: 22,
    fontWeight: "800",
  },
  metricLabel: {
    fontSize: 10,
    color: colors.gray[500],
    textAlign: "center",
  },
  metricHint: {
    fontSize: 10,
    color: colors.gray[400],
    textAlign: "center",
  },
  commsWarning: {
    borderWidth: 1,
    borderColor: "#FCD34D",
    backgroundColor: colors.ui.warning.light,
    borderRadius: 10,
    paddingHorizontal: 10,
    paddingVertical: 8,
  },
  commsWarningText: {
    fontSize: 11,
    color: colors.ui.warning.text,
  },
  sectionLabel: {
    fontSize: 12,
    fontWeight: "700",
    color: colors.gray[500],
    letterSpacing: 0.8,
    textTransform: "uppercase",
    marginTop: 4,
  },
  launchGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 10,
  },
  launchCard: {
    flexBasis: "47%",
    flex: 1,
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    padding: 14,
    minHeight: 110,
    gap: 6,
    alignItems: "flex-start",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.06,
    shadowRadius: 4,
    elevation: 2,
  },
  launchCardActive: {
    borderWidth: 1.5,
    borderColor: "#A7F3D0",
  },
  launchIconCircle: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 2,
  },
  launchTitle: {
    fontSize: 14,
    fontWeight: "700",
  },
  launchSubtitle: {
    fontSize: 11,
    color: colors.gray[500],
    lineHeight: 16,
  },
  taskRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 12,
  },
  taskLeft: {
    flex: 1,
    gap: 4,
  },
  taskTitle: {
    fontSize: 14,
    fontWeight: "700",
    color: colors.gray[900],
  },
  taskMeta: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  taskPatient: {
    fontSize: 12,
    color: colors.gray[500],
  },
  taskDue: {
    fontSize: 11,
    color: colors.gray[400],
  },
  worklistSummaryRow: {
    flexDirection: "row",
    gap: 8,
    marginBottom: 4,
  },
  worklistRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 8,
  },
  encounterRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  encounterIconWrap: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: "#DBEAFE",
    alignItems: "center",
    justifyContent: "center",
  },
  encounterText: {
    flex: 1,
    gap: 2,
  },
  refreshRow: {
    alignItems: "center",
    marginTop: 8,
  },
  supportRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  supportChip: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderRadius: 12,
    backgroundColor: "#FFFFFF",
    borderWidth: 1,
    borderColor: colors.gray[200],
  },
  supportChipSos: {
    borderColor: "#FECACA",
    backgroundColor: "#FEF2F2",
  },
  supportChipText: {
    fontSize: 13,
    fontWeight: "700",
    color: colors.gray[800],
  },
});
