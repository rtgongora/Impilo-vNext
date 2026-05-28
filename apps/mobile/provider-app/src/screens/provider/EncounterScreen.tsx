/**
 * EncounterScreen — Active encounter workflow.
 *
 * Displays the current encounter with tabs for Vitals, Diagnosis, Rx, Labs, Referrals, Notes.
 */

import React, { useState, useCallback } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  CardHeader,
  Button,
  TabBar,
  Badge,
  LoadingSpinner,
  ErrorState,
} from "@impilo/mobile-design-system";
import { useEncounterStore, encounterStore } from "../../stores/encounterStore";
import { closeEncounter, addEncounterNotes } from "../../services/encounterService";
import { VitalsPanel } from "./VitalsPanel";
import { DiagnosisPanel } from "./DiagnosisPanel";
import { PrescriptionPanel } from "./PrescriptionPanel";
import { LabOrderPanel } from "./LabOrderPanel";
import { ReferralPanel } from "./ReferralPanel";
import { NotesPanel } from "./NotesPanel";
import { TriageScreen } from "./TriageScreen";

const TABS = [
  { key: "vitals", label: "Vitals" },
  { key: "diagnosis", label: "Diagnosis" },
  { key: "rx", label: "Rx" },
  { key: "labs", label: "Labs" },
  { key: "referrals", label: "Referrals" },
  { key: "notes", label: "Notes" },
  { key: "triage", label: "Triage" },
  { key: "billing", label: "Billing" },
];

export function EncounterScreen() {
  const { activeEncounter, vitals, diagnoses, prescriptions, labOrders, referrals, isDirty } =
    useEncounterStore();
  const [activeTab, setActiveTab] = useState("vitals");
  const [closing, setClosing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleCloseEncounter = useCallback(async () => {
    if (!activeEncounter) return;
    setClosing(true);
    setError(null);
    try {
      const primaryDx = diagnoses.find((d) => d.isPrimary);
      await closeEncounter(activeEncounter.id, primaryDx?.icdDescription);
      encounterStore.getState().clearEncounter();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to close encounter");
    } finally {
      setClosing(false);
    }
  }, [activeEncounter, diagnoses]);

  if (!activeEncounter) {
    return (
      <Screen>
        <Header title="Encounter" />
        <View style={styles.emptyContainer}>
          <Text>No active encounter. Search for a patient to start a visit.</Text>
        </View>
      </Screen>
    );
  }

  const renderPanel = () => {
    switch (activeTab) {
      case "vitals":
        return <VitalsPanel encounterId={activeEncounter.id} patientId={activeEncounter.patientId} />;
      case "diagnosis":
        return <DiagnosisPanel encounterId={activeEncounter.id} />;
      case "rx":
        return (
          <PrescriptionPanel
            encounterId={activeEncounter.id}
            patientId={activeEncounter.patientId}
          />
        );
      case "labs":
        return (
          <LabOrderPanel
            encounterId={activeEncounter.id}
            patientId={activeEncounter.patientId}
          />
        );
      case "referrals":
        return (
          <ReferralPanel
            encounterId={activeEncounter.id}
            patientId={activeEncounter.patientId}
          />
        );
      case "notes":
        return <NotesPanel encounterId={activeEncounter.id} />;
      case "triage":
        return <TriageScreen embedded />;
      case "billing":
        return <View style={{ padding: 16 }}><Text style={{ fontSize: 14, color: "#6B7280" }}>Charge capture available from the Billing screen in Clinical Tools</Text></View>;
      default:
        return null;
    }
  };

  return (
    <Screen>
      <Header title={`Encounter — ${activeEncounter.encounterType}`} />
      <ScrollView testID="encounter-screen" style={styles.container}>
        {/* Encounter summary */}
        <Card>
          <CardBody>
            <View style={styles.summaryRow}>
              <View>
                <Badge variant="primary">{activeEncounter.status}</Badge>
                <Text style={styles.startedText}>
                  {`Started: ${new Date(activeEncounter.startedAt).toLocaleTimeString()}`}
                </Text>
              </View>
              <View style={styles.badgeRow}>
                <Badge variant="outline">{`${vitals.length} vitals`}</Badge>
                <Badge variant="outline">{`${diagnoses.length} Dx`}</Badge>
                <Badge variant="outline">{`${prescriptions.length} Rx`}</Badge>
                <Badge variant="outline">{`${labOrders.length} labs`}</Badge>
                <Badge variant="outline">{`${referrals.length} refs`}</Badge>
              </View>
            </View>
            {activeEncounter.chiefComplaint ? (
              <Text style={styles.chiefComplaint}>
                {`Chief Complaint: ${activeEncounter.chiefComplaint}`}
              </Text>
            ) : null}
          </CardBody>
        </Card>

        {/* Encounter tabs */}
        <View style={styles.tabContainer}>
          <TabBar
            items={TABS}
            activeKey={activeTab}
            onSelect={setActiveTab}
          />
        </View>

        {/* Active panel */}
        {renderPanel()}

        {/* Close encounter */}
        {error ? <ErrorState title="Error" message={error} /> : null}
        <View style={styles.closeContainer}>
          <Button
            title="Close Encounter"
            variant="primary"
            onPress={handleCloseEncounter}
            loading={closing}
            disabled={diagnoses.length === 0}
            testID="close-encounter-btn"
            accessibilityLabel="Close this encounter"
          />
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  emptyContainer: {
    padding: 16,
    alignItems: "center",
  },
  summaryRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  startedText: {
    marginLeft: 8,
    fontSize: 14,
    color: "#6B7280",
  },
  badgeRow: {
    flexDirection: "row",
    gap: 8,
  },
  chiefComplaint: {
    marginTop: 8,
    fontSize: 14,
  },
  tabContainer: {
    marginVertical: 16,
  },
  closeContainer: {
    marginTop: 24,
    flexDirection: "row",
    gap: 12,
  },
});
