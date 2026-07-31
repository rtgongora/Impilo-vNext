/**
 * EncounterScreen — Active encounter workflow.
 *
 * Displays the current encounter with tabs for Vitals, Diagnosis, Rx, Labs, Referrals, Notes.
 */

import React, { useState, useCallback, useMemo } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Screen, Header, Card, CardBody, CardHeader, Button, TabBar, Badge, LoadingSpinner, ErrorState, colors } from "@impilo/mobile-design-system";
import { useEncounterStore, encounterStore } from "../../stores/encounterStore";
import { closeEncounter, addEncounterNotes } from "../../services/encounterService";
import { applyCoreTransactionAction, listCoreTransactions } from "../../services/coreTransactionService";
import { AdaptiveEncounterCockpit } from "./AdaptiveEncounterCockpit";
import { EncounterFormsPanel } from "./EncounterFormsPanel";
import { VitalsPanel } from "./VitalsPanel";
import { DiagnosisPanel } from "./DiagnosisPanel";
import { PrescriptionPanel } from "./PrescriptionPanel";
import { LabOrderPanel } from "./LabOrderPanel";
import { ReferralPanel } from "./ReferralPanel";
import { NotesPanel } from "./NotesPanel";
import { TriageScreen } from "./TriageScreen";
import { MedicineWorkspaceScreen } from "./MedicineWorkspaceScreen";
import { MedicineCdsEvaluateScreen } from "./MedicineCdsEvaluateScreen";
import { ClerkingContinuityScreen } from "./ClerkingContinuityScreen";

const TABS = [
  { key: "cockpit", label: "Cockpit" },
  { key: "forms", label: "Forms" },
  { key: "vitals", label: "Vitals" },
  { key: "diagnosis", label: "Diagnosis" },
  { key: "rx", label: "Rx" },
  { key: "labs", label: "Labs" },
  { key: "referrals", label: "Referrals" },
  { key: "notes", label: "Notes" },
  { key: "medicine", label: "Medicine" },
  { key: "clerking", label: "Clerking" },
  { key: "triage", label: "Triage" },
  { key: "billing", label: "Billing" },
];

export function EncounterScreen() {
  const { activeEncounter, vitals, diagnoses, prescriptions, labOrders, referrals, isDirty } =
    useEncounterStore();
  const [activeTab, setActiveTab] = useState("vitals");
  const [closing, setClosing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const encounterTransactionQuery = useQuery({
    queryKey: ["provider-encounter-core-transaction", activeEncounter?.id],
    queryFn: () =>
      listCoreTransactions({
        type: "FACILITY_WALK_IN",
        encounterId: activeEncounter!.id,
      }),
    enabled: Boolean(activeEncounter?.id),
  });

  const queryClient = useQueryClient();

  const encounterTransaction = useMemo(() => {
    const row = encounterTransactionQuery.data?.[0];
    if (!row || typeof row !== "object") return null;
    const envelope = row as Record<string, unknown>;
    const tx = (envelope.transaction as Record<string, unknown> | undefined) ?? envelope;
    const nextActions = Array.isArray(envelope.nextActions) ? envelope.nextActions : [];
    return {
      id: String(tx.id ?? ""),
      state: String(tx.currentState ?? ""),
      providerStage: String(
        ((envelope.journeys as Record<string, unknown> | undefined)?.provider as Record<string, unknown> | undefined)
          ?.currentStage ?? "",
      ),
      nextActions: nextActions
        .map((action) => action as Record<string, unknown>)
        .filter((action) => typeof action.code === "string" && typeof action.label === "string")
        .map((action) => ({ code: String(action.code), label: String(action.label) })),
    };
  }, [encounterTransactionQuery.data]);

  const applyTransactionAction = useMutation({
    mutationFn: ({ transactionId, actionCode }: { transactionId: string; actionCode: string }) =>
      applyCoreTransactionAction(transactionId, actionCode, {
        encounterId: activeEncounter?.id,
        patientId: activeEncounter?.patientId,
        source: "mobile-encounter-screen",
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ["provider-encounter-core-transaction", activeEncounter?.id],
      });
    },
  });

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
      case "cockpit": {
        const et = String(activeEncounter.encounterType ?? "").toUpperCase();
        const inpatient = et.includes("INPATIENT") || et.includes("ADMISSION") || et.includes("WARD");
        return (
          <AdaptiveEncounterCockpit
            request={{
              cadre: "CLINICIAN",
              visitType: activeEncounter.encounterType,
              context: inpatient ? "inpatient" : "outpatient",
              encounterId: activeEncounter.id,
            }}
          />
        );
      }
      case "forms": {
        const et = String(activeEncounter.encounterType ?? "").toUpperCase();
        const inpatient = et.includes("INPATIENT") || et.includes("ADMISSION") || et.includes("WARD");
        return (
          <EncounterFormsPanel
            encounterId={activeEncounter.id}
            resolveParams={{
              cadre: "CLINICIAN",
              careSetting: inpatient ? "INPATIENT" : "OUTPATIENT",
              context: inpatient ? "inpatient" : "outpatient",
              cpid: activeEncounter.patientId,
            }}
          />
        );
      }
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
      case "medicine":
        return (
          <View style={{ gap: 16 }}>
            <MedicineWorkspaceScreen patientId={activeEncounter.patientId} embedded />
            <MedicineCdsEvaluateScreen patientId={activeEncounter.patientId} embedded />
          </View>
        );
      case "clerking":
        return (
          <ClerkingContinuityScreen
            patientId={activeEncounter.patientId}
            encounterId={activeEncounter.id}
            embedded
          />
        );
      case "triage":
        return <TriageScreen embedded />;
      case "billing":
        return <View style={{ padding: 16 }}><Text style={{ fontSize: 14, color: colors.gray[500] }}>Charge capture available from the Billing screen in Clinical Tools</Text></View>;
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

        {encounterTransactionQuery.isLoading ? (
          <Card>
            <CardBody>
              <Text style={styles.transactionHint}>Loading encounter transaction context…</Text>
            </CardBody>
          </Card>
        ) : encounterTransaction ? (
          <Card testID="encounter-transaction-context">
            <CardHeader title="Encounter transaction" />
            <CardBody>
              <Text style={styles.transactionState}>{`State: ${encounterTransaction.state}`}</Text>
              {encounterTransaction.providerStage ? (
                <Text style={styles.transactionHint}>
                  {`Provider stage: ${encounterTransaction.providerStage}`}
                </Text>
              ) : null}
              {encounterTransaction.nextActions.length > 0 ? (
                <View style={styles.transactionActions}>
                  {encounterTransaction.nextActions.map((action) => (
                    <Button
                      key={action.code}
                      title={action.label}
                      variant="outline"
                      loading={applyTransactionAction.isPending}
                      onPress={() => {
                        applyTransactionAction.mutate({
                          transactionId: encounterTransaction.id,
                          actionCode: action.code,
                        });
                      }}
                    />
                  ))}
                </View>
              ) : null}
            </CardBody>
          </Card>
        ) : (
          <Card>
            <CardBody>
              <Text style={styles.transactionHint}>
                No FACILITY_WALK_IN transaction linked to this encounter yet.
              </Text>
            </CardBody>
          </Card>
        )}

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
    color: colors.gray[500],
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
  transactionState: {
    fontSize: 14,
    fontWeight: "600",
    color: colors.gray[900],
  },
  transactionHint: {
    fontSize: 13,
    color: colors.gray[500],
  },
  transactionNext: {
    marginTop: 4,
    fontSize: 13,
    color: "#2563EB",
  },
  transactionActions: {
    marginTop: 12,
    gap: 8,
  },
});
