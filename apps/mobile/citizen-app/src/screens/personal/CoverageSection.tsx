/**
 * CoverageSection — View insurance/coverage information.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet, TextInput } from "react-native";
import { Card, CardHeader, CardBody, Badge, Button, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import { fetchCoverage } from "../../services/coverageService";
import {
  fetchCoveragePayerJourney,
  flushCoverageCommandQueue,
  runCoverageCommand,
  subscribeCoverageCommandQueue,
  type CoverageCommandKind,
  type CoverageCommandQueueEntry,
  type CoveragePayerJourney,
} from "../../services/coverageCommandService";
import type { CoverageInfo } from "../../types";

const COMMAND_LABELS: Record<CoverageCommandKind, string> = {
  "eligibility-check": "Eligibility",
  "claim-submission": "Claim",
  "preauth-request": "Pre-auth",
  "appeal-submission": "Appeal",
};

function buildPayload(command: CoverageCommandKind, form: Record<string, string>) {
  if (command === "eligibility-check") {
    return { memberCpid: form.memberCpid, coverageId: form.coverageId, serviceCode: form.serviceCode };
  }
  if (command === "claim-submission") {
    return { coverageId: form.coverageId, claimType: form.claimType, facilityId: form.facilityId, totalAmount: form.totalAmount };
  }
  if (command === "preauth-request") {
    return {
      coverageId: form.coverageId,
      requestType: form.requestType,
      facilityId: form.facilityId,
      providerId: form.providerId,
      clinicalInfo: form.clinicalInfo,
    };
  }
  return {
    claimId: form.claimId,
    appellantId: form.memberCpid,
    reason: form.reason,
    evidence: { summary: form.evidenceSummary },
  };
}

export function CoverageSection() {
  const [coverage, setCoverage] = useState<CoverageInfo[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [command, setCommand] = useState<CoverageCommandKind>("eligibility-check");
  const [commandForm, setCommandForm] = useState({
    memberCpid: "",
    coverageId: "",
    serviceCode: "CONSULTATION",
    claimType: "OUTPATIENT",
    facilityId: "",
    totalAmount: "",
    requestType: "SPECIALIST",
    providerId: "",
    clinicalInfo: "",
    claimId: "",
    reason: "",
    evidenceSummary: "",
  });
  const [queue, setQueue] = useState<CoverageCommandQueueEntry[]>([]);
  const [commandStatus, setCommandStatus] = useState<string | null>(null);
  const [isSubmittingCommand, setSubmittingCommand] = useState(false);
  const [journey, setJourney] = useState<CoveragePayerJourney | null>(null);
  const [isLoadingJourney, setLoadingJourney] = useState(false);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchCoverage();
      setCoverage(result);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => subscribeCoverageCommandQueue(setQueue), []);

  async function submitCommand() {
    setSubmittingCommand(true);
    setCommandStatus(null);
    try {
      const result = await runCoverageCommand(command, buildPayload(command, commandForm));
      setCommandStatus(
        result.syncStatus === "SYNCED"
          ? `${COMMAND_LABELS[command]} synced.`
          : `${COMMAND_LABELS[command]} queued provisionally for retry.`,
      );
    } finally {
      setSubmittingCommand(false);
    }
  }

  async function syncQueuedCommands() {
    const result = await flushCoverageCommandQueue();
    setCommandStatus(`Sync complete: ${result.synced} synced, ${result.remaining} remaining.`);
  }

  async function loadPayerJourney() {
    setLoadingJourney(true);
    try {
      const result = await fetchCoveragePayerJourney({
        coverageId: commandForm.coverageId || coverage[0]?.id,
        memberCpid: commandForm.memberCpid || coverage[0]?.memberId,
        appellantId: commandForm.memberCpid || coverage[0]?.memberId,
      });
      setJourney(result);
    } finally {
      setLoadingJourney(false);
    }
  }

  if (isLoading) return <LoadingSpinner size="md" />;
  if (error) return <ErrorState title="Error" message={error.message} onRetry={load} />;

  return (
    <View testID="coverage-section" style={styles.container}>
      <Text style={styles.heading}>Coverage & Insurance</Text>
      <Card>
        <CardHeader title="Coverage Commands" />
        <CardBody>
          <Text style={styles.commandHelp}>
            Run live coverage commands from mobile. Failed submissions are held as provisional queue entries until sync.
          </Text>
          <View style={styles.commandTabs}>
            {(Object.keys(COMMAND_LABELS) as CoverageCommandKind[]).map((item) => (
              <Text
                key={item}
                style={[styles.commandTab, command === item ? styles.commandTabActive : undefined]}
                onPress={() => setCommand(item)}
              >
                {COMMAND_LABELS[item]}
              </Text>
            ))}
          </View>
          <TextInput
            style={styles.input}
            placeholder="Member CPID / appellant id"
            value={commandForm.memberCpid}
            onChangeText={(value) => setCommandForm((current) => ({ ...current, memberCpid: value }))}
          />
          <TextInput
            style={styles.input}
            placeholder="Coverage ID"
            value={commandForm.coverageId}
            onChangeText={(value) => setCommandForm((current) => ({ ...current, coverageId: value }))}
          />
          {command === "eligibility-check" ? (
            <TextInput
              style={styles.input}
              placeholder="Service code"
              value={commandForm.serviceCode}
              onChangeText={(value) => setCommandForm((current) => ({ ...current, serviceCode: value }))}
            />
          ) : null}
          {command === "claim-submission" ? (
            <>
              <TextInput
                style={styles.input}
                placeholder="Facility ID"
                value={commandForm.facilityId}
                onChangeText={(value) => setCommandForm((current) => ({ ...current, facilityId: value }))}
              />
              <TextInput
                style={styles.input}
                placeholder="Total amount"
                keyboardType="numeric"
                value={commandForm.totalAmount}
                onChangeText={(value) => setCommandForm((current) => ({ ...current, totalAmount: value }))}
              />
            </>
          ) : null}
          {command === "preauth-request" ? (
            <>
              <TextInput
                style={styles.input}
                placeholder="Facility ID"
                value={commandForm.facilityId}
                onChangeText={(value) => setCommandForm((current) => ({ ...current, facilityId: value }))}
              />
              <TextInput
                style={styles.input}
                placeholder="Provider ID"
                value={commandForm.providerId}
                onChangeText={(value) => setCommandForm((current) => ({ ...current, providerId: value }))}
              />
              <TextInput
                style={styles.input}
                placeholder="Clinical info"
                value={commandForm.clinicalInfo}
                onChangeText={(value) => setCommandForm((current) => ({ ...current, clinicalInfo: value }))}
              />
            </>
          ) : null}
          {command === "appeal-submission" ? (
            <>
              <TextInput
                style={styles.input}
                placeholder="Claim ID"
                value={commandForm.claimId}
                onChangeText={(value) => setCommandForm((current) => ({ ...current, claimId: value }))}
              />
              <TextInput
                style={styles.input}
                placeholder="Appeal reason"
                value={commandForm.reason}
                onChangeText={(value) => setCommandForm((current) => ({ ...current, reason: value }))}
              />
            </>
          ) : null}
          <Button
            title={isSubmittingCommand ? "Submitting..." : `Run ${COMMAND_LABELS[command]}`}
            onPress={submitCommand}
            disabled={
              isSubmittingCommand ||
              (command !== "appeal-submission" && !commandForm.coverageId) ||
              (command === "appeal-submission" &&
                (!commandForm.claimId || !commandForm.memberCpid || !commandForm.reason))
            }
          />
          <Button
            title={`Sync queued (${queue.length})`}
            variant="outline"
            onPress={syncQueuedCommands}
            disabled={queue.length === 0}
          />
          {commandStatus ? <Text style={styles.commandStatus}>{commandStatus}</Text> : null}
          {queue.length > 0 ? (
            <View style={styles.queuePanel}>
              <Text style={styles.queueTitle}>Provisional queue and retry history</Text>
              {queue.slice(0, 4).map((entry) => (
                <View key={entry.localId} style={styles.queueItem}>
                  <Text style={styles.queueText}>{`${COMMAND_LABELS[entry.command]} · ${entry.status} · retry ${entry.retryCount}`}</Text>
                  <Text style={styles.queueMeta}>{entry.lastError ?? entry.history[0]?.message ?? "Queued"}</Text>
                </View>
              ))}
            </View>
          ) : null}
        </CardBody>
      </Card>
      <Card>
        <CardHeader title="Mobile Payer Ops Workspace" />
        <CardBody>
          <Text style={styles.commandHelp}>
            One journey view for coverage claims, remittance, appeals, settlements, and reconciliation status.
          </Text>
          <Button
            title={isLoadingJourney ? "Loading journey..." : "Load payer journey"}
            onPress={loadPayerJourney}
            disabled={isLoadingJourney}
          />
          {journey ? (
            <View style={styles.journeyGrid}>
              {journey.reconciliation.map((item) => (
                <View key={item.stage} style={styles.journeyItem}>
                  <Text style={styles.journeyStage}>{item.stage}</Text>
                  <Text style={styles.journeyStatus}>{item.status}</Text>
                  <Text style={styles.queueMeta}>{item.detail}</Text>
                </View>
              ))}
            </View>
          ) : null}
        </CardBody>
      </Card>
      {coverage.length === 0 ? (
        <EmptyState
          title="No coverage information"
          message="Your insurance and coverage details will appear here when available"
        />
      ) : null}
      {coverage.map((plan) => (
        <Card key={plan.id}>
          <CardHeader title={plan.planName} />
          <CardBody>
            <View testID={`coverage-${plan.id}`}>
              <View style={styles.badgeRow}>
                <Badge variant={plan.status === "ACTIVE" ? "default" : "outline"}>
                  {plan.status}
                </Badge>
                <Text style={styles.planType}>{plan.planType}</Text>
              </View>
              <Text style={styles.infoText}>{`Member ID: ${plan.memberId}`}</Text>
              <Text style={styles.infoText}>
                {`Effective: ${new Date(plan.effectiveFrom).toLocaleDateString()}${plan.effectiveTo ? ` - ${new Date(plan.effectiveTo).toLocaleDateString()}` : " (ongoing)"}`}
              </Text>
              {plan.copay !== undefined ? (
                <Text style={styles.infoText}>{`Copay: ${plan.currency} ${plan.copay}`}</Text>
              ) : null}
              {plan.deductible !== undefined ? (
                <Text style={styles.infoText}>{`Deductible: ${plan.currency} ${plan.deductible}`}</Text>
              ) : null}
              {plan.outOfPocketMax !== undefined ? (
                <Text style={styles.infoText}>{`Out-of-pocket max: ${plan.currency} ${plan.outOfPocketMax}`}</Text>
              ) : null}
            </View>
          </CardBody>
        </Card>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 12,
  },
  heading: {
    fontSize: 18,
    fontWeight: "600",
  },
  badgeRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    marginBottom: 8,
  },
  planType: {
    fontSize: 13,
    color: colors.gray[500],
  },
  infoText: {
    fontSize: 14,
    marginVertical: 4,
  },
  commandHelp: {
    fontSize: 12,
    color: colors.gray[500],
    marginBottom: 8,
  },
  commandTabs: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginBottom: 8,
  },
  commandTab: {
    borderWidth: 1,
    borderColor: colors.gray[300],
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 6,
    fontSize: 12,
    color: colors.gray[700],
  },
  commandTabActive: {
    borderColor: "#4F46E5",
    color: "#3730A3",
    backgroundColor: "#EEF2FF",
  },
  input: {
    borderWidth: 1,
    borderColor: colors.gray[300],
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 8,
    marginBottom: 8,
    fontSize: 14,
  },
  commandStatus: {
    marginTop: 8,
    fontSize: 12,
    color: "#047857",
  },
  queuePanel: {
    marginTop: 10,
    borderWidth: 1,
    borderColor: "#FCD34D",
    borderRadius: 8,
    padding: 8,
    backgroundColor: "#FFFBEB",
  },
  queueTitle: {
    fontSize: 12,
    fontWeight: "700",
    color: colors.ui.warning.text,
    marginBottom: 4,
  },
  queueItem: {
    paddingVertical: 4,
    borderTopWidth: 1,
    borderTopColor: "#FDE68A",
  },
  queueText: {
    fontSize: 12,
    fontWeight: "600",
    color: "#78350F",
  },
  queueMeta: {
    fontSize: 11,
    color: colors.gray[500],
  },
  journeyGrid: {
    marginTop: 10,
    gap: 8,
  },
  journeyItem: {
    borderWidth: 1,
    borderColor: colors.gray[300],
    borderRadius: 8,
    padding: 10,
  },
  journeyStage: {
    fontSize: 13,
    fontWeight: "700",
    color: colors.gray[900],
  },
  journeyStatus: {
    marginTop: 2,
    fontSize: 12,
    fontWeight: "600",
    color: "#3730A3",
  },
});
