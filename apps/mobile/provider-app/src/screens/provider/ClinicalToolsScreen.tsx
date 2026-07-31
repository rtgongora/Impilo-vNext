/**
 * ClinicalToolsScreen — SOAP notes, drug interactions, order sets, care plans,
 * MAR, CDS, paging, barcode scanning, and specialty workspaces in one hub.
 */
import React, { useState, useEffect } from "react";
import { View, Text, TextInput, ScrollView, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { Screen, Header, Button, Badge, LoadingSpinner, DictationAssistButton, colors } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  applyOrderSet,
  checkDrugInteractions, fetchOrderSets, fetchCarePlans, createCarePlan,
  fetchMAR, administerMedication, evaluateCDS,
  fetchPages, sendPage,
  saveSoapNote,
} from "../../services/queueService";
import { useEncounterStore } from "../../stores/encounterStore";
import { SPECIALTY_WORKSPACES, getSpecialtyById } from "../../data/specialtyWorkspaces";

import { InpatientScreen } from "./InpatientScreen";
import { SpecialtyWorkspacePanel } from "./SpecialtyWorkspacePanel";
import { FacilityAdminScreen } from "./FacilityAdminScreen";
import { ControlTowerScreen } from "./ControlTowerScreen";
import { PlaceModeDashboardScreen } from "../outreach/PlaceModeDashboardScreen";
import { FacilityRegulatorsScreen } from "./FacilityRegulatorsScreen";
import { FacilitySetupScreen } from "./FacilitySetupScreen";
import { QueueDefinitionsScreen } from "./QueueDefinitionsScreen";
import { ReportsScreen } from "./ReportsScreen";
import { FinanceOverviewScreen } from "./FinanceOverviewScreen";
import { PharmacyHubScreen } from "./PharmacyHubScreen";
import { LabHubScreen } from "./LabHubScreen";
import { MarketplaceOpsScreen } from "./MarketplaceOpsScreen";
import { ScheduleScreen } from "./ScheduleScreen";
import { BookingRequestsScreen } from "./BookingRequestsScreen";
import { AdminRegistryHubScreen } from "./AdminRegistryHubScreen";
import { OpsReportsHubScreen } from "./OpsReportsHubScreen";
import { DeveloperHubScreen } from "./DeveloperHubScreen";
import { ProfessionalSettingsHubScreen } from "./ProfessionalSettingsHubScreen";
import { ProfessionalChannelsHubScreen } from "./ProfessionalChannelsHubScreen";
import { PublicHealthFieldTasksScreen } from "./PublicHealthFieldTasksScreen";
import { TelemedicineScreen } from "./TelemedicineScreen";
import { FundoLearningShellScreen } from "./FundoLearningShellScreen";
import { ProductionReadinessJourneyScreen } from "./ProductionReadinessJourneyScreen";
import { CoreTransactionJourneyShellScreen } from "./CoreTransactionJourneyShellScreen";
import { WorkflowDispatchOpsScreen } from "./WorkflowDispatchOpsScreen";
import { VashandiWorkforceHubScreen } from "./VashandiWorkforceHubScreen";
import { MadiOrdersScreen } from "../madi/MadiOrdersScreen";
import { MadiTransfusionScreen } from "../madi/MadiTransfusionScreen";
import { MadiDriveCaptureScreen } from "../madi/MadiDriveCaptureScreen";
import { MadiReactionReportScreen } from "../madi/MadiReactionReportScreen";
import { MadiCentralBankScreen } from "../madi/MadiCentralBankScreen";
import { ProviderLiveHubScreen } from "../live/ProviderLiveHubScreen";
import { TriageScreen } from "./TriageScreen";
import { PrehospitalEpcrScreen } from "./PrehospitalEpcrScreen";
import { EmergencyHubScreen } from "./EmergencyHubScreen";
import { BillingScreen } from "./BillingScreen";
import { PACSViewerScreen } from "./PACSViewerScreen";
import { DischargeScreen } from "./DischargeScreen";
import { ReportSafetyScreen } from "../rito/ReportSafetyScreen";
import { MySafetyCasesScreen } from "../rito/MySafetyCasesScreen";
import { appStore, useAppStore } from "../../stores/appStore";

type ToolTab =
  | "emergency"
  | "soap"
  | "triage"
  | "prehospital_epcr"
  | "drugs"
  | "orders"
  | "care"
  | "mar"
  | "cds"
  | "paging"
  | "barcode"
  | "workspaces"
  | "inpatient"
  | "facility"
  | "control_tower"
  | "place_mode"
  | "regulators"
  | "facility_setup"
  | "queue_definitions"
  | "reports"
  | "finance"
  | "schedule"
  | "booking_requests"
  | "pharmacy"
  | "lab"
  | "marketplace"
  | "admin"
  | "ops_reports"
  | "developer_hub"
  | "prof_settings"
  | "prof_channels"
  | "telemedicine"
  | "billing"
  | "pacs"
  | "discharge"
  | "learning"
  | "core_transaction"
  | "workflow_dispatch"
  | "ph_field_tasks"
  | "vashandi"
  | "rito_report"
  | "rito_my_cases"
  | "prod_ready"
  | "madi_orders"
  | "madi_transfusion"
  | "madi_drives"
  | "madi_reactions"
  | "madi_central_bank"
  | "impilo_live";

const TABS: { id: ToolTab; label: string }[] = [
  { id: "emergency", label: "Emergency" },
  { id: "soap", label: "SOAP" }, { id: "triage", label: "Triage" }, { id: "prehospital_epcr", label: "ePCR" }, { id: "telemedicine", label: "Telehealth" }, { id: "drugs", label: "Drug Check" }, { id: "orders", label: "Order Sets" },
  { id: "care", label: "Care Plan" }, { id: "mar", label: "MAR" }, { id: "cds", label: "CDS" },
  { id: "paging", label: "Paging" }, { id: "barcode", label: "Barcode" }, { id: "workspaces", label: "Specialty" },
  { id: "inpatient", label: "Inpatient" }, { id: "facility", label: "Facility" }, { id: "control_tower", label: "Control Tower" }, { id: "place_mode", label: "Place Mode" }, { id: "regulators", label: "Regulators" }, { id: "facility_setup", label: "Setup" }, { id: "queue_definitions", label: "Queues" }, { id: "reports", label: "Reports" },
  { id: "finance", label: "Finance" }, { id: "billing", label: "Billing" }, { id: "pacs", label: "PACS" },
  { id: "schedule", label: "Schedule" },
  { id: "booking_requests", label: "Bookings" },
  { id: "pharmacy", label: "Pharmacy" },
  { id: "lab", label: "Lab" },
  { id: "madi_orders", label: "Blood Orders" },
  { id: "madi_transfusion", label: "Transfusion" },
  { id: "madi_drives", label: "Blood Drives" },
  { id: "madi_reactions", label: "Haemovig." },
  { id: "madi_central_bank", label: "Central Bank" },
  { id: "impilo_live", label: "Impilo Live" },
  { id: "marketplace", label: "Market Ops" },
  { id: "admin", label: "Admin" },
  { id: "ops_reports", label: "Ops+" },
  { id: "developer_hub", label: "Dev" },
  { id: "prof_settings", label: "Prefs" },
  { id: "prof_channels", label: "CX+" },
  { id: "discharge", label: "Discharge" },
  { id: "learning", label: "Learning" },
  { id: "core_transaction", label: "Core Tx" },
  { id: "workflow_dispatch", label: "Flow/Ops" },
  { id: "ph_field_tasks", label: "PH Field" },
  { id: "vashandi", label: "Vashandi" },
  { id: "rito_report", label: "Report Safety" },
  { id: "rito_my_cases", label: "My Safety" },
  { id: "prod_ready", label: "Prod Ready" },
];

export function ClinicalToolsScreen() {
  const [tab, setTab] = useState<ToolTab>("soap");
  const { clinicalToolsInitialTab } = useAppStore();

  useEffect(() => {
    if (clinicalToolsInitialTab) {
      setTab(clinicalToolsInitialTab as ToolTab);
      appStore.getState().setClinicalToolsInitialTab(null);
    }
  }, [clinicalToolsInitialTab]);

  return (
    <Screen><Header title="Clinical Tools" />
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.tabBar} contentContainerStyle={styles.tabBarContent}>
        {TABS.map((t) => (
          <TouchableOpacity
            key={t.id}
            testID={`tools-tab-${t.id}`}
            onPress={() => setTab(t.id)}
            style={[styles.tab, tab === t.id && styles.activeTab]}
          >
            <Text style={[styles.tabText, tab === t.id && styles.activeTabText]}>{t.label}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
      <ScrollView style={styles.content} contentContainerStyle={styles.contentPad}>
        {tab === "emergency" && <EmergencyHubScreen />}
        {tab === "soap" && <SOAPPanel />}
        {tab === "triage" && <TriageScreen />}
        {tab === "prehospital_epcr" && <PrehospitalEpcrScreen />}
        {tab === "telemedicine" && <TelemedicineScreen />}
        {tab === "drugs" && <DrugInteractionPanel />}
        {tab === "orders" && <OrderSetsPanel />}
        {tab === "care" && <CarePlanPanel />}
        {tab === "mar" && <MARPanel />}
        {tab === "cds" && <CDSPanel />}
        {tab === "paging" && <PagingPanel />}
        {tab === "barcode" && <BarcodePanel />}
        {tab === "workspaces" && <SpecialtyPanel />}
        {tab === "inpatient" && <InpatientScreen />}
        {tab === "facility" && <FacilityAdminScreen />}
        {tab === "control_tower" && <ControlTowerScreen />}
        {tab === "place_mode" && <PlaceModeDashboardScreen />}
        {tab === "regulators" && <FacilityRegulatorsScreen />}
        {tab === "facility_setup" && <FacilitySetupScreen />}
        {tab === "queue_definitions" && <QueueDefinitionsScreen />}
        {tab === "reports" && <ReportsScreen />}
        {tab === "finance" && <FinanceOverviewScreen />}
        {tab === "billing" && <BillingScreen />}
        {tab === "pacs" && <PACSViewerScreen />}
        {tab === "schedule" && <ScheduleScreen />}
        {tab === "booking_requests" && <BookingRequestsScreen />}
        {tab === "pharmacy" && <PharmacyHubScreen />}
        {tab === "lab" && <LabHubScreen />}
        {tab === "madi_orders" && <MadiOrdersScreen />}
        {tab === "madi_transfusion" && <MadiTransfusionScreen />}
        {tab === "madi_drives" && <MadiDriveCaptureScreen />}
        {tab === "madi_reactions" && <MadiReactionReportScreen />}
        {tab === "madi_central_bank" && <MadiCentralBankScreen />}
        {tab === "impilo_live" && <ProviderLiveHubScreen />}
        {tab === "marketplace" && <MarketplaceOpsScreen />}
        {tab === "admin" && <AdminRegistryHubScreen />}
        {tab === "ops_reports" && <OpsReportsHubScreen />}
        {tab === "developer_hub" && <DeveloperHubScreen />}
        {tab === "prof_settings" && <ProfessionalSettingsHubScreen />}
        {tab === "prof_channels" && <ProfessionalChannelsHubScreen />}
        {tab === "discharge" && (
          <DischargePanel onBackToInpatient={() => setTab("inpatient")} />
        )}
        {tab === "learning" && <FundoLearningShellScreen />}
        {tab === "core_transaction" && <CoreTransactionJourneyShellScreen />}
        {tab === "workflow_dispatch" && <WorkflowDispatchOpsScreen />}
        {tab === "ph_field_tasks" && <PublicHealthFieldTasksScreen />}
        {tab === "vashandi" && <VashandiWorkforceHubScreen />}
        {tab === "rito_report" && <ReportSafetyScreen />}
        {tab === "rito_my_cases" && <MySafetyCasesScreen />}
        {tab === "prod_ready" && (
          <ProductionReadinessJourneyScreen onNavigateTab={(nextTab) => setTab(nextTab as ToolTab)} />
        )}
      </ScrollView>
    </Screen>
  );
}

function DischargePanel({ onBackToInpatient }: { onBackToInpatient: () => void }) {
  const { activeEncounter } = useEncounterStore();

  if (!activeEncounter?.id) {
    return (
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Discharge Workflow</Text>
        <Text style={styles.hint}>
          Select an active encounter first before submitting discharge details.
        </Text>
        <Button title="Open Inpatient" onPress={onBackToInpatient} />
      </View>
    );
  }

  return (
    <DischargeScreen
      encounterId={activeEncounter.id}
      onGoBack={onBackToInpatient}
      onDischargeComplete={onBackToInpatient}
    />
  );
}

function SOAPPanel() {
  const { activeEncounter } = useEncounterStore();
  const [soap, setSOAP] = useState({ subjective: "", objective: "", assessment: "", plan: "" });

  // This button used to fire Alert.alert("Saved", "SOAP note recorded") over local component
  // state. Nothing was written anywhere — and this is the DEFAULT tab of Clinical Tools, beside a
  // dictation button, so a clinician could dictate a full encounter note, see "Saved", navigate
  // away, and lose all of it with no indication anything was wrong.
  const mutation = useMutation({
    mutationFn: () =>
      saveSoapNote({
        patientId: activeEncounter?.patientId ?? "",
        encounterId: activeEncounter?.id ?? "",
        ...soap,
      }),
    onSuccess: () => {
      setSOAP({ subjective: "", objective: "", assessment: "", plan: "" });
      Alert.alert("Saved", "SOAP note recorded against this encounter.");
    },
    onError: (error: unknown) => {
      const detail = error instanceof Error ? error.message : "The note was not saved.";
      // The form is deliberately NOT cleared — the text is the only copy that exists.
      Alert.alert("NOT saved", `${detail}\n\nYour note is still on screen. Do not navigate away.`);
    },
  });

  const hasContent = Object.values(soap).some((v) => v.trim().length > 0);
  const canSave = hasContent && Boolean(activeEncounter?.id) && !mutation.isPending;

  return (
    <View style={styles.section}>
      <View style={styles.soapHeaderRow}>
        <Text style={styles.sectionTitle}>SOAP Note</Text>
        <DictationAssistButton fieldLabel="SOAP sections" testID="soap-dictation-assist" />
      </View>
      {(["subjective", "objective", "assessment", "plan"] as const).map((field) => (
        <View key={field}>
          <Text style={styles.label}>{field.charAt(0).toUpperCase() + field.slice(1)}</Text>
          <TextInput style={styles.textArea} value={soap[field]} onChangeText={(v) => setSOAP({ ...soap, [field]: v })} placeholder={`Enter ${field}...`} multiline />
        </View>
      ))}
      {!activeEncounter?.id && (
        <Text style={styles.result}>A note is written against an encounter. Open one to save.</Text>
      )}
      <Button
        testID="soap-save"
        title={mutation.isPending ? "Saving..." : "Save SOAP Note"}
        onPress={() => mutation.mutate()}
        disabled={!canSave}
      />
    </View>
  );
}

function DrugInteractionPanel() {
  const { activeEncounter } = useEncounterStore();
  const [meds, setMeds] = useState("");

  // This panel used to render "No significant interactions found for N medications" on ANY
  // successful response, ignoring what came back — and the endpoint behind it returned that same
  // reassurance without calling anything. Two independent fabrications of the same false all-clear.
  const mutation = useMutation({
    mutationFn: () =>
      checkDrugInteractions(
        meds.split(",").map((m) => m.trim()).filter(Boolean),
        { patientId: activeEncounter?.patientId, encounterId: activeEncounter?.id },
      ),
  });

  const alerts = mutation.data ?? [];
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Drug Interaction Check</Text>
      <TextInput style={styles.input} value={meds} onChangeText={setMeds} placeholder="Enter medications (comma-separated)" />
      <Button title="Check Interactions" onPress={() => mutation.mutate()} disabled={!meds || mutation.isPending} />

      {mutation.isError && (
        <Text testID="interaction-error" style={styles.result}>
          {"The interaction check did NOT run. This is not a finding of no interactions — "}
          {"do not prescribe on the basis of this screen."}
        </Text>
      )}

      {mutation.isSuccess && alerts.length === 0 && (
        <Text testID="interaction-none" style={styles.result}>
          No interactions returned by the clinical knowledge engine for these medications.
        </Text>
      )}

      {mutation.isSuccess &&
        alerts.map((alert, index) => (
          <View key={`${alert.rule_id ?? "alert"}-${index}`} style={styles.card}>
            <Badge label={alert.severity} variant={alert.severity === "INFO" ? "info" : "warning"} />
            <Text style={styles.cardMeta}>{alert.message}</Text>
            {alert.source ? <Text style={styles.cardMeta}>Source: {alert.source}</Text> : null}
          </View>
        ))}
    </View>
  );
}

function OrderSetsPanel() {
  const { data: sets = [], isLoading } = useQuery({ queryKey: ["order-sets"], queryFn: fetchOrderSets });
  if (isLoading) return <LoadingSpinner />;
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Order Set Protocols</Text>
      {(sets as Array<Record<string, unknown>>).map((s) => (
        <View key={String(s.id)} style={styles.card}>
          <Text style={styles.cardTitle}>{String(s.name)}</Text>
          <Badge label={String(s.category)} variant="info" />
          <Text style={styles.cardMeta}>Items: {((s.items as string[]) ?? []).join(", ")}</Text>
          <ApplyProtocolButton pathwayId={String(s.id)} name={String(s.name)} />
        </View>
      ))}
    </View>
  );
}

function CarePlanPanel() {
  const { activeEncounter } = useEncounterStore();
  const [title, setTitle] = useState("");
  const [goals, setGoals] = useState("");
  const mutation = useMutation({ mutationFn: () => createCarePlan({ title, goals: goals.split("\n").filter(Boolean), subject_cpid: activeEncounter?.patientId ?? "", journey_id: activeEncounter?.journeyId, encounter_id: activeEncounter?.id }) });
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Nursing Care Plan</Text>
      <TextInput style={styles.input} placeholder="Care plan title" value={title} onChangeText={setTitle} />
      <TextInput style={styles.textArea} placeholder="Goals (one per line)" value={goals} onChangeText={setGoals} multiline />
      <Button title="Create Care Plan" onPress={() => mutation.mutate()} disabled={!title || mutation.isPending} />
    </View>
  );
}

function MARPanel() {
  const { activeEncounter } = useEncounterStore();
  const { data: meds = [], isLoading } = useQuery({ queryKey: ["mar", activeEncounter?.patientId], queryFn: () => fetchMAR(activeEncounter?.patientId ?? ""), enabled: !!activeEncounter?.patientId });
  const administerMut = useMutation({ mutationFn: (prescriptionId: string) => administerMedication({ prescriptionId, administeredBy: "current-nurse" }) });
  if (isLoading) return <LoadingSpinner />;
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Medication Administration Record</Text>
      {(meds as Array<Record<string, unknown>>).length === 0 ? <Text style={styles.empty}>No active medications</Text> :
        (meds as Array<Record<string, unknown>>).map((m) => (
          <View key={String(m.id)} style={styles.card}>
            <Text style={styles.cardTitle}>{String(m.medication_name)}</Text>
            <Text style={styles.cardMeta}>{String(m.dosage)} · {String(m.frequency)}</Text>
            <Button title="Administer" size="sm" onPress={() => administerMut.mutate(String(m.id))} />
          </View>
        ))
      }
    </View>
  );
}

function CDSPanel() {
  const { activeEncounter, diagnoses } = useEncounterStore();
  const mutation = useMutation({ mutationFn: () => evaluateCDS({ context: "encounter", encounterId: activeEncounter?.id, diagnoses: diagnoses.map((d) => d.icdCode) }) });
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Clinical Decision Support</Text>
      <Button title="Evaluate CDS Rules" onPress={() => mutation.mutate()} disabled={mutation.isPending} />
      {mutation.isSuccess && (mutation.data as Array<Record<string, unknown>>).map((alert, i) => (
        <View key={i} style={[styles.card, { borderLeftColor: alert.level === "WARNING" ? colors.ui.warning.main : "#3B82F6", borderLeftWidth: 4 }]}>
          <Badge label={String(alert.level)} variant={alert.level === "WARNING" ? "warning" : "info"} />
          <Text style={styles.cardTitle}>{String(alert.message)}</Text>
          <Text style={styles.cardMeta}>Source: {String(alert.source)}</Text>
        </View>
      ))}
    </View>
  );
}

function PagingPanel() {
  const { data: pages = [], isLoading } = useQuery({ queryKey: ["provider-pages"], queryFn: () => fetchPages() });
  const [form, setForm] = useState({ recipientName: "", message: "", urgency: "normal" });
  const mutation = useMutation({ mutationFn: () => sendPage({ recipientId: form.recipientName, recipientName: form.recipientName, message: form.message, urgency: form.urgency }) });
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Clinical Paging</Text>
      <TextInput style={styles.input} placeholder="Recipient" value={form.recipientName} onChangeText={(v) => setForm({ ...form, recipientName: v })} />
      <TextInput style={styles.textArea} placeholder="Page message..." value={form.message} onChangeText={(v) => setForm({ ...form, message: v })} multiline />
      <Button title="Send Page" onPress={() => mutation.mutate()} disabled={!form.recipientName || !form.message || mutation.isPending} />
      {isLoading ? <LoadingSpinner /> : (pages as Array<Record<string, unknown>>).slice(0, 5).map((p) => (
        <View key={String(p.id)} style={styles.card}>
          <Text style={styles.cardMeta}>To: {String(p.recipient_name)} · {String(p.urgency)} · {String(p.status)}</Text>
          <Text style={styles.cardTitle}>{String(p.message)}</Text>
        </View>
      ))}
    </View>
  );
}

function BarcodePanel() {
  const [manualCode, setManualCode] = useState("");
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Barcode / QR Scanner</Text>
      <View style={styles.scannerPlaceholder}>
        <Text style={styles.scannerText}>Camera scanner requires device hardware</Text>
        <Text style={styles.scannerHint}>Use the native camera to scan medication barcodes</Text>
      </View>
      <Text style={styles.label}>Manual Entry</Text>
      <TextInput style={styles.input} placeholder="Enter barcode number" value={manualCode} onChangeText={setManualCode} />
      <Button title="Look Up" onPress={() => Alert.alert("Lookup", `Searching for barcode: ${manualCode}`)} disabled={!manualCode} />
    </View>
  );
}

function SpecialtyPanel() {
  const [activeId, setActiveId] = useState<string | null>(null);
  const active = activeId ? getSpecialtyById(activeId) : undefined;

  if (active) {
    return <SpecialtyWorkspacePanel workspace={active} onBack={() => setActiveId(null)} />;
  }

  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Specialty Workspaces</Text>
      <Text style={styles.hint}>18 specialty hubs with tools and quick actions</Text>
      {SPECIALTY_WORKSPACES.map((w) => (
        <View key={w.id} style={styles.card}>
          <Text style={styles.cardTitle}>{w.name}</Text>
          <Text style={styles.cardMeta}>{w.tools.length} tools · {w.icon}</Text>
          <Button title="Open workspace" size="sm" onPress={() => setActiveId(w.id)} />
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  tabBar: { borderBottomWidth: 1, borderBottomColor: colors.gray[200], paddingHorizontal: 8 },
  tabBarContent: { flexDirection: "row", gap: 4, paddingVertical: 4 },
  tab: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 14, backgroundColor: colors.gray[100] },
  activeTab: { backgroundColor: "#2563EB" },
  tabText: { fontSize: 12, color: colors.gray[500], fontWeight: "500" },
  activeTabText: { color: "#FFF" },
  content: { flex: 1 },
  contentPad: { padding: 16, paddingBottom: 32 },
  section: { gap: 12 },
  soapHeaderRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 8,
  },
  sectionTitle: { fontSize: 16, fontWeight: "700", color: colors.gray[900] },
  label: { fontSize: 13, fontWeight: "600", color: colors.gray[700] },
  input: { borderWidth: 1, borderColor: colors.gray[300], borderRadius: 8, padding: 10, fontSize: 14 },
  textArea: { borderWidth: 1, borderColor: colors.gray[300], borderRadius: 8, padding: 12, fontSize: 14, minHeight: 80, textAlignVertical: "top" },
  card: { backgroundColor: colors.gray[50], borderRadius: 12, padding: 12, gap: 4 },
  cardTitle: { fontSize: 14, fontWeight: "600", color: colors.gray[900] },
  cardMeta: { fontSize: 12, color: colors.gray[500] },
  result: { fontSize: 13, color: colors.ui.success.main, fontWeight: "500" },
  empty: { fontSize: 13, color: colors.gray[400], textAlign: "center", paddingVertical: 20 },
  hint: { fontSize: 13, color: colors.gray[500] },
  scannerPlaceholder: { backgroundColor: colors.gray[800], borderRadius: 12, padding: 24, alignItems: "center", gap: 4 },
  scannerText: { color: colors.gray[400], fontSize: 14 },
  scannerHint: { color: colors.gray[500], fontSize: 12 },
});

/**
 * Applies a protocol by starting a governed pathway session bound to the encounter.
 *
 * <p>The button used to fire Alert.alert("Applied", …) and do nothing else. The clinician moved on
 * believing a protocol was running on the patient.
 */
function ApplyProtocolButton({ pathwayId, name }: { pathwayId: string; name: string }) {
  const { activeEncounter } = useEncounterStore();
  const mutation = useMutation({
    mutationFn: () =>
      applyOrderSet(pathwayId, {
        patientId: activeEncounter?.patientId,
        encounterId: activeEncounter?.id ?? "",
      }),
    onSuccess: () => Alert.alert("Protocol applied", `${name} is now running on this encounter.`),
    onError: (error: unknown) => {
      const detail = error instanceof Error ? error.message : "The protocol was not applied.";
      Alert.alert("NOT applied", `${detail}\n\n${name} is not running on this encounter.`);
    },
  });

  if (!activeEncounter?.id) {
    return <Text style={styles.cardMeta}>Open an encounter to apply this protocol.</Text>;
  }
  return (
    <Button
      title={mutation.isPending ? "Applying..." : "Apply Protocol"}
      size="sm"
      onPress={() => mutation.mutate()}
      disabled={mutation.isPending}
    />
  );
}
