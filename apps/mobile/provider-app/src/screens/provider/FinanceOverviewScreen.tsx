/**
 * FinanceOverviewScreen — Revenue summary, pending claims, and recent payments.
 *
 * Provides a financial dashboard for the provider with:
 * - Revenue summary card (today, week, month)
 * - Pending claims count and total value
 * - Recent payments list with status badges
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, StyleSheet, TextInput } from "react-native";
import { Screen, Header, Card, CardHeader, CardBody, Badge, Button, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import {
  fetchRevenueSummary,
  fetchPendingClaims,
  fetchRecentPayments,
} from "../../services/financeService";
import {
  fetchCoveragePayerJourney,
  flushCoverageCommandQueue,
  runCoverageCommand,
  subscribeCoverageCommandQueue,
  type CoverageCommandKind,
  type CoverageCommandQueueEntry,
  type CoveragePayerJourney,
} from "../../services/coverageCommandService";
import type { RevenueSummary, Claim, Payment } from "../../types";

const PAYMENT_BADGE_VARIANT: Record<string, "primary" | "secondary" | "destructive"> = {
  PAID: "primary",
  PENDING: "secondary",
  REJECTED: "destructive",
};

const COVERAGE_COMMAND_LABELS: Record<CoverageCommandKind, string> = {
  "eligibility-check": "Eligibility",
  "claim-submission": "Claim",
  "preauth-request": "Pre-auth",
  "appeal-submission": "Appeal",
};

function providerCoveragePayload(command: CoverageCommandKind, form: Record<string, string>) {
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

function formatCurrency(amount: number, currency: string): string {
  return `${currency} ${amount.toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

export function FinanceOverviewScreen() {
  const [revenue, setRevenue] = useState<RevenueSummary | null>(null);
  const [claims, setClaims] = useState<Claim[]>([]);
  const [payments, setPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [coverageCommand, setCoverageCommand] = useState<CoverageCommandKind>("eligibility-check");
  const [coverageForm, setCoverageForm] = useState({
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
  const [coverageQueue, setCoverageQueue] = useState<CoverageCommandQueueEntry[]>([]);
  const [coverageStatus, setCoverageStatus] = useState<string | null>(null);
  const [coverageSubmitting, setCoverageSubmitting] = useState(false);
  const [payerIntentId, setPayerIntentId] = useState("");
  const [payerJourney, setPayerJourney] = useState<CoveragePayerJourney | null>(null);
  const [payerJourneyLoading, setPayerJourneyLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [rev, cl, pay] = await Promise.all([
        fetchRevenueSummary(),
        fetchPendingClaims(),
        fetchRecentPayments(),
      ]);
      setRevenue(rev);
      setClaims(cl);
      setPayments(pay);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load finance data"
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => subscribeCoverageCommandQueue(setCoverageQueue), []);

  const pendingTotal = claims.reduce((sum, c) => sum + c.amount, 0);

  async function submitCoverageCommand() {
    setCoverageSubmitting(true);
    setCoverageStatus(null);
    try {
      const result = await runCoverageCommand(
        coverageCommand,
        providerCoveragePayload(coverageCommand, coverageForm)
      );
      setCoverageStatus(
        result.syncStatus === "SYNCED"
          ? `${COVERAGE_COMMAND_LABELS[coverageCommand]} synced.`
          : `${COVERAGE_COMMAND_LABELS[coverageCommand]} queued provisionally.`
      );
    } finally {
      setCoverageSubmitting(false);
    }
  }

  async function syncCoverageQueue() {
    const result = await flushCoverageCommandQueue();
    setCoverageStatus(
      `Coverage sync: ${result.synced} synced, ${result.remaining} remaining.`
    );
  }

  async function loadPayerOpsJourney() {
    setPayerJourneyLoading(true);
    try {
      const result = await fetchCoveragePayerJourney({
        coverageId: coverageForm.coverageId,
        memberCpid: coverageForm.memberCpid,
        appellantId: coverageForm.memberCpid,
        intentId: payerIntentId,
      });
      setPayerJourney(result);
    } finally {
      setPayerJourneyLoading(false);
    }
  }

  return (
    <Screen>
      <Header title="Finance Overview" />
      <View testID="finance-overview-screen" style={styles.container}>
        {loading ? (
          <LoadingSpinner size="md" />
        ) : error ? (
          <ErrorState title="Error" message={error} onRetry={load} />
        ) : (
          <ScrollView
            style={styles.scrollArea}
            contentContainerStyle={styles.scrollContent}
          >
            {/* ── Revenue Summary ────────────────────────────────── */}
            {revenue && (
              <Card>
                <CardHeader title="Revenue Summary" />
                <CardBody>
                  <View style={styles.revenueGrid}>
                    <View style={styles.revenueItem}>
                      <Text style={[styles.revenueValue, { color: colors.ui.success.main }]}>
                        {formatCurrency(revenue.todayRevenue, revenue.currency)}
                      </Text>
                      <Text style={styles.revenueLabel}>Today</Text>
                    </View>
                    <View style={styles.revenueItem}>
                      <Text style={[styles.revenueValue, { color: "#3B82F6" }]}>
                        {formatCurrency(revenue.weekTotal, revenue.currency)}
                      </Text>
                      <Text style={styles.revenueLabel}>This Week</Text>
                    </View>
                    <View style={styles.revenueItem}>
                      <Text style={[styles.revenueValue, { color: "#8B5CF6" }]}>
                        {formatCurrency(revenue.monthTotal, revenue.currency)}
                      </Text>
                      <Text style={styles.revenueLabel}>This Month</Text>
                    </View>
                  </View>
                </CardBody>
              </Card>
            )}

            {/* ── Pending Claims ─────────────────────────────────── */}
            <Card>
              <CardHeader title="Pending Claims" />
              <CardBody>
                <View style={styles.claimsSummary}>
                  <View style={styles.claimsStat}>
                    <Text style={styles.claimsCount}>{claims.length}</Text>
                    <Text style={styles.claimsLabel}>Claims Pending</Text>
                  </View>
                  <View style={styles.claimsStat}>
                    <Text style={styles.claimsTotal}>
                      {formatCurrency(
                        pendingTotal,
                        revenue?.currency ?? "ZAR"
                      )}
                    </Text>
                    <Text style={styles.claimsLabel}>Total Value</Text>
                  </View>
                </View>
              </CardBody>
            </Card>

            <Card>
              <CardHeader title="Coverage Commands" />
              <CardBody>
                <Text style={styles.commandHelp}>
                  Eligibility, claim, pre-auth, and appeal commands use live BFF paths. Failed mobile submissions are queued provisionally.
                </Text>
                <View style={styles.commandTabs}>
                  {(Object.keys(COVERAGE_COMMAND_LABELS) as CoverageCommandKind[]).map((item) => (
                    <Text
                      key={item}
                      style={[
                        styles.commandTab,
                        coverageCommand === item ? styles.commandTabActive : undefined,
                      ]}
                      onPress={() => setCoverageCommand(item)}
                    >
                      {COVERAGE_COMMAND_LABELS[item]}
                    </Text>
                  ))}
                </View>
                <TextInput
                  style={styles.input}
                  placeholder="Member CPID / appellant id"
                  value={coverageForm.memberCpid}
                  onChangeText={(value) => setCoverageForm({ ...coverageForm, memberCpid: value })}
                />
                <TextInput
                  style={styles.input}
                  placeholder="Coverage ID"
                  value={coverageForm.coverageId}
                  onChangeText={(value) => setCoverageForm({ ...coverageForm, coverageId: value })}
                />
                {coverageCommand === "eligibility-check" ? (
                  <TextInput
                    style={styles.input}
                    placeholder="Service code"
                    value={coverageForm.serviceCode}
                    onChangeText={(value) => setCoverageForm({ ...coverageForm, serviceCode: value })}
                  />
                ) : null}
                {coverageCommand === "claim-submission" ? (
                  <>
                    <TextInput
                      style={styles.input}
                      placeholder="Facility ID"
                      value={coverageForm.facilityId}
                      onChangeText={(value) => setCoverageForm({ ...coverageForm, facilityId: value })}
                    />
                    <TextInput
                      style={styles.input}
                      placeholder="Total amount"
                      keyboardType="numeric"
                      value={coverageForm.totalAmount}
                      onChangeText={(value) => setCoverageForm({ ...coverageForm, totalAmount: value })}
                    />
                  </>
                ) : null}
                {coverageCommand === "preauth-request" ? (
                  <>
                    <TextInput
                      style={styles.input}
                      placeholder="Facility ID"
                      value={coverageForm.facilityId}
                      onChangeText={(value) => setCoverageForm({ ...coverageForm, facilityId: value })}
                    />
                    <TextInput
                      style={styles.input}
                      placeholder="Provider ID"
                      value={coverageForm.providerId}
                      onChangeText={(value) => setCoverageForm({ ...coverageForm, providerId: value })}
                    />
                    <TextInput
                      style={styles.input}
                      placeholder="Clinical info"
                      value={coverageForm.clinicalInfo}
                      onChangeText={(value) => setCoverageForm({ ...coverageForm, clinicalInfo: value })}
                    />
                  </>
                ) : null}
                {coverageCommand === "appeal-submission" ? (
                  <>
                    <TextInput
                      style={styles.input}
                      placeholder="Claim ID"
                      value={coverageForm.claimId}
                      onChangeText={(value) => setCoverageForm({ ...coverageForm, claimId: value })}
                    />
                    <TextInput
                      style={styles.input}
                      placeholder="Appeal reason"
                      value={coverageForm.reason}
                      onChangeText={(value) => setCoverageForm({ ...coverageForm, reason: value })}
                    />
                  </>
                ) : null}
                <Button
                  title={coverageSubmitting ? "Submitting..." : `Run ${COVERAGE_COMMAND_LABELS[coverageCommand]}`}
                  onPress={submitCoverageCommand}
                  disabled={
                    coverageSubmitting ||
                    (coverageCommand !== "appeal-submission" && !coverageForm.coverageId) ||
                    (coverageCommand === "appeal-submission" &&
                      (!coverageForm.claimId || !coverageForm.memberCpid || !coverageForm.reason))
                  }
                />
                <Button
                  title={`Sync queued (${coverageQueue.length})`}
                  variant="outline"
                  onPress={syncCoverageQueue}
                  disabled={coverageQueue.length === 0}
                />
                {coverageStatus ? <Text style={styles.commandStatus}>{coverageStatus}</Text> : null}
                {coverageQueue.length > 0 ? (
                  <View style={styles.queuePanel}>
                    <Text style={styles.queueTitle}>Failed/provisional command review</Text>
                    {coverageQueue.slice(0, 5).map((entry) => (
                      <View key={entry.localId} style={styles.queueItem}>
                        <Text style={styles.queueText}>
                          {`${COVERAGE_COMMAND_LABELS[entry.command]} · ${entry.status} · retry ${entry.retryCount}`}
                        </Text>
                        <Text style={styles.queueMeta}>{entry.lastError ?? entry.history[0]?.message ?? "Queued"}</Text>
                      </View>
                    ))}
                  </View>
                ) : null}
              </CardBody>
            </Card>

            <Card>
              <CardHeader title="Payer Ops Workspace" />
              <CardBody>
                <Text style={styles.commandHelp}>
                  Unified mobile journey for claims, remittance, appeals, settlement lookup, and reconciliation status.
                </Text>
                <TextInput
                  style={styles.input}
                  placeholder="Payment intent ID (optional)"
                  value={payerIntentId}
                  onChangeText={setPayerIntentId}
                />
                <Button
                  title={payerJourneyLoading ? "Loading payer journey..." : "Load payer ops journey"}
                  onPress={loadPayerOpsJourney}
                  disabled={payerJourneyLoading}
                />
                {payerJourney ? (
                  <View style={styles.journeyGrid}>
                    {payerJourney.reconciliation.map((item) => (
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

            {/* ── Recent Payments ────────────────────────────────── */}
            <Text style={styles.sectionTitle}>Recent Payments</Text>
            {payments.length === 0 ? (
              <EmptyState
                title="No payments"
                message="No recent payment records"
              />
            ) : (
              payments.map((payment) => (
                <Card key={payment.id}>
                  <CardBody>
                    <View
                      testID={`payment-${payment.id}`}
                      style={styles.paymentRow}
                    >
                      <View style={styles.paymentInfo}>
                        <Text style={styles.paymentPatient}>
                          {payment.patientName}
                        </Text>
                        <Text style={styles.paymentDate}>
                          {new Date(payment.paidAt).toLocaleDateString()}
                        </Text>
                        {payment.reference && (
                          <Text style={styles.paymentRef}>
                            Ref: {payment.reference}
                          </Text>
                        )}
                      </View>
                      <View style={styles.paymentRight}>
                        <Text style={styles.paymentAmount}>
                          {formatCurrency(
                            payment.amount,
                            payment.currency
                          )}
                        </Text>
                        <Badge
                          variant={
                            PAYMENT_BADGE_VARIANT[payment.status] ?? "secondary"
                          }
                        >
                          {payment.status}
                        </Badge>
                      </View>
                    </View>
                  </CardBody>
                </Card>
              ))
            )}
          </ScrollView>
        )}

        <View style={styles.refreshContainer}>
          <Button
            title="Refresh"
            variant="outline"
            onPress={load}
            testID="refresh-finance"
          />
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
  },
  scrollArea: {
    flex: 1,
  },
  scrollContent: {
    gap: 12,
    paddingBottom: 16,
  },
  revenueGrid: {
    flexDirection: "row",
    justifyContent: "space-between",
    gap: 8,
  },
  revenueItem: {
    flex: 1,
    alignItems: "center",
    paddingVertical: 8,
  },
  revenueValue: {
    fontSize: 18,
    fontWeight: "900",
  },
  revenueLabel: {
    fontSize: 12,
    color: colors.gray[500],
    marginTop: 4,
  },
  claimsSummary: {
    flexDirection: "row",
    justifyContent: "space-around",
    paddingVertical: 8,
  },
  claimsStat: {
    alignItems: "center",
  },
  claimsCount: {
    fontSize: 32,
    fontWeight: "900",
    color: colors.ui.warning.main,
  },
  claimsTotal: {
    fontSize: 18,
    fontWeight: "800",
    color: colors.ui.warning.main,
  },
  claimsLabel: {
    fontSize: 12,
    color: colors.gray[500],
    marginTop: 4,
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
  sectionTitle: {
    fontSize: 16,
    fontWeight: "700",
    color: colors.gray[900],
    marginTop: 8,
  },
  paymentRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  paymentInfo: {
    flex: 1,
    gap: 2,
  },
  paymentPatient: {
    fontSize: 14,
    fontWeight: "700",
    color: colors.gray[900],
  },
  paymentDate: {
    fontSize: 12,
    color: colors.gray[500],
  },
  paymentRef: {
    fontSize: 11,
    color: colors.gray[400],
  },
  paymentRight: {
    alignItems: "flex-end",
    gap: 4,
  },
  paymentAmount: {
    fontSize: 15,
    fontWeight: "800",
    color: colors.gray[900],
  },
  refreshContainer: {
    marginTop: 8,
  },
});
