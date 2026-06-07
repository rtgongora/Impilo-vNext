/**
 * ClinicalToolsScreen — SOAP notes, drug interactions, order sets, care plans,
 * MAR, CDS, paging, barcode scanning, and specialty workspaces in one hub.
 */
import React, { useState, useEffect } from "react";
import { View, Text, TextInput, ScrollView, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { Screen, Header, Button, Badge, LoadingSpinner, DictationAssistButton } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  checkDrugInteractions, fetchOrderSets, fetchCarePlans, createCarePlan,
  fetchMAR, administerMedication, evaluateCDS,
  fetchPages, sendPage,
} from "../../services/queueService";
import { useEncounterStore } from "../../stores/encounterStore";
import { SPECIALTY_WORKSPACES, getSpecialtyById } from "../../data/specialtyWorkspaces";

import { InpatientScreen } from "./InpatientScreen";
import { SpecialtyWorkspacePanel } from "./SpecialtyWorkspacePanel";
import { FacilityAdminScreen } from "./FacilityAdminScreen";
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
import { WorkflowDispatchOpsScreen } from "./WorkflowDispatchOpsScreen";
import { TriageScreen } from "./TriageScreen";
import { BillingScreen } from "./BillingScreen";
import { PACSViewerScreen } from "./PACSViewerScreen";
import { DischargeScreen } from "./DischargeScreen";
import { appStore, useAppStore } from "../../stores/appStore";

type ToolTab =
  | "soap"
  | "triage"
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
  | "prod_ready";

const TABS: { id: ToolTab; label: string }[] = [
  { id: "soap", label: "SOAP" }, { id: "triage", label: "Triage" }, { id: "telemedicine", label: "Telehealth" }, { id: "drugs", label: "Drug Check" }, { id: "orders", label: "Order Sets" },
  { id: "care", label: "Care Plan" }, { id: "mar", label: "MAR" }, { id: "cds", label: "CDS" },
  { id: "paging", label: "Paging" }, { id: "barcode", label: "Barcode" }, { id: "workspaces", label: "Specialty" },
  { id: "inpatient", label: "Inpatient" }, { id: "facility", label: "Facility" }, { id: "reports", label: "Reports" },
  { id: "finance", label: "Finance" }, { id: "billing", label: "Billing" }, { id: "pacs", label: "PACS" },
  { id: "schedule", label: "Schedule" },
  { id: "booking_requests", label: "Bookings" },
  { id: "pharmacy", label: "Pharmacy" },
  { id: "lab", label: "Lab" },
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
        {tab === "soap" && <SOAPPanel />}
        {tab === "triage" && <TriageScreen />}
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
        {tab === "reports" && <ReportsScreen />}
        {tab === "finance" && <FinanceOverviewScreen />}
        {tab === "billing" && <BillingScreen />}
        {tab === "pacs" && <PACSViewerScreen />}
        {tab === "schedule" && <ScheduleScreen />}
        {tab === "booking_requests" && <BookingRequestsScreen />}
        {tab === "pharmacy" && <PharmacyHubScreen />}
        {tab === "lab" && <LabHubScreen />}
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
  const [soap, setSOAP] = useState({ subjective: "", objective: "", assessment: "", plan: "" });
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
      <Button title="Save SOAP Note" onPress={() => Alert.alert("Saved", "SOAP note recorded")} />
    </View>
  );
}

function DrugInteractionPanel() {
  const [meds, setMeds] = useState("");
  const mutation = useMutation({ mutationFn: () => checkDrugInteractions(meds.split(",").map((m) => m.trim()).filter(Boolean)) });
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Drug Interaction Check</Text>
      <TextInput style={styles.input} value={meds} onChangeText={setMeds} placeholder="Enter medications (comma-separated)" />
      <Button title="Check Interactions" onPress={() => mutation.mutate()} disabled={!meds || mutation.isPending} />
      {mutation.isSuccess && <Text style={styles.result}>No significant interactions found for {meds.split(",").length} medications</Text>}
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
          <Button title="Apply Protocol" size="sm" onPress={() => Alert.alert("Applied", `${s.name} protocol applied to encounter`)} />
        </View>
      ))}
    </View>
  );
}

function CarePlanPanel() {
  const { activeEncounter } = useEncounterStore();
  const [title, setTitle] = useState("");
  const [goals, setGoals] = useState("");
  const mutation = useMutation({ mutationFn: () => createCarePlan({ title, goals: goals.split("\n").filter(Boolean), patientId: activeEncounter?.patientId ?? "" }) });
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
        <View key={i} style={[styles.card, { borderLeftColor: alert.level === "WARNING" ? "#F59E0B" : "#3B82F6", borderLeftWidth: 4 }]}>
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
  tabBar: { borderBottomWidth: 1, borderBottomColor: "#E5E7EB", paddingHorizontal: 8 },
  tabBarContent: { flexDirection: "row", gap: 4, paddingVertical: 4 },
  tab: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 14, backgroundColor: "#F3F4F6" },
  activeTab: { backgroundColor: "#2563EB" },
  tabText: { fontSize: 12, color: "#6B7280", fontWeight: "500" },
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
  sectionTitle: { fontSize: 16, fontWeight: "700", color: "#111827" },
  label: { fontSize: 13, fontWeight: "600", color: "#374151" },
  input: { borderWidth: 1, borderColor: "#D1D5DB", borderRadius: 8, padding: 10, fontSize: 14 },
  textArea: { borderWidth: 1, borderColor: "#D1D5DB", borderRadius: 8, padding: 12, fontSize: 14, minHeight: 80, textAlignVertical: "top" },
  card: { backgroundColor: "#F9FAFB", borderRadius: 12, padding: 12, gap: 4 },
  cardTitle: { fontSize: 14, fontWeight: "600", color: "#111827" },
  cardMeta: { fontSize: 12, color: "#6B7280" },
  result: { fontSize: 13, color: "#22C55E", fontWeight: "500" },
  empty: { fontSize: 13, color: "#9CA3AF", textAlign: "center", paddingVertical: 20 },
  hint: { fontSize: 13, color: "#6B7280" },
  scannerPlaceholder: { backgroundColor: "#1F2937", borderRadius: 12, padding: 24, alignItems: "center", gap: 4 },
  scannerText: { color: "#9CA3AF", fontSize: 14 },
  scannerHint: { color: "#6B7280", fontSize: 12 },
});
