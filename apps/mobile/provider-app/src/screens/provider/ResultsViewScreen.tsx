/**
 * ResultsViewScreen — Lab results review for providers.
 *
 * Displays pending and completed lab results with filtering, detail expansion,
 * reference ranges, abnormal flags, and result acknowledgement.
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Pressable,
} from "react-native";
import { Screen, Header, Card, CardBody, CardHeader, Badge, Button, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import { apiClient } from "@impilo/mobile-api-client";
import type { LabOrderStatus } from "../../types";

// ── Resource shapes (snake_case from API) ──────────────────────────

interface ResultItemResource {
  id: string;
  type: "LabResultItem";
  attributes: {
    lab_order_id: string;
    patient_id: string;
    patient_name: string;
    test_name: string;
    test_code: string;
    urgency: "ROUTINE" | "URGENT" | "STAT";
    status: LabOrderStatus;
    ordered_at: string;
    ordered_by: string;
    acknowledged: boolean;
    acknowledged_at?: string;
    results: ResultParameterResource[];
  };
}

interface ResultParameterResource {
  id: string;
  parameter: string;
  value: string;
  unit: string;
  reference_range: string;
  is_abnormal: boolean;
  completed_at: string;
}

// ── Domain types (camelCase for app) ───────────────────────────────

interface ResultItem {
  id: string;
  labOrderId: string;
  patientId: string;
  patientName: string;
  testName: string;
  testCode: string;
  urgency: "ROUTINE" | "URGENT" | "STAT";
  status: LabOrderStatus;
  orderedAt: string;
  orderedBy: string;
  acknowledged: boolean;
  acknowledgedAt?: string;
  results: ResultParameter[];
}

interface ResultParameter {
  id: string;
  parameter: string;
  value: string;
  unit: string;
  referenceRange: string;
  isAbnormal: boolean;
  completedAt: string;
}

// ── Mappers ────────────────────────────────────────────────────────

function mapResultParameter(r: ResultParameterResource): ResultParameter {
  return {
    id: r.id,
    parameter: r.parameter,
    value: r.value,
    unit: r.unit,
    referenceRange: r.reference_range,
    isAbnormal: r.is_abnormal,
    completedAt: r.completed_at,
  };
}

function mapResultItem(r: ResultItemResource): ResultItem {
  return {
    id: r.id,
    labOrderId: r.attributes.lab_order_id,
    patientId: r.attributes.patient_id,
    patientName: r.attributes.patient_name,
    testName: r.attributes.test_name,
    testCode: r.attributes.test_code,
    urgency: r.attributes.urgency,
    status: r.attributes.status,
    orderedAt: r.attributes.ordered_at,
    orderedBy: r.attributes.ordered_by,
    acknowledged: r.attributes.acknowledged,
    acknowledgedAt: r.attributes.acknowledged_at,
    results: r.attributes.results.map(mapResultParameter),
  };
}

// ── API calls ──────────────────────────────────────────────────────

async function fetchResults(statusFilter?: string): Promise<ResultItem[]> {
  const params = statusFilter && statusFilter !== "ALL" ? `?status=${statusFilter}` : "";
  const response = await apiClient.get<{ data: ResultItemResource[] }>(
    `/internal/v1/mobile/provider/labs/results${params}`
  );
  return response.data.data.map(mapResultItem);
}

async function acknowledgeResult(resultId: string): Promise<void> {
  await apiClient.post(`/internal/v1/mobile/provider/labs/results/${resultId}/acknowledge`);
}

// ── Filter options ─────────────────────────────────────────────────

type FilterOption = "ALL" | "PENDING" | "RESULTED";

const FILTER_OPTIONS: { key: FilterOption; label: string }[] = [
  { key: "ALL", label: "All" },
  { key: "PENDING", label: "Pending" },
  { key: "RESULTED", label: "Resulted" },
];

const URGENCY_VARIANT: Record<string, "destructive" | "warning" | "secondary"> = {
  STAT: "destructive",
  URGENT: "warning",
  ROUTINE: "secondary",
};

function formatDateTime(iso: string): string {
  const d = new Date(iso);
  return `${d.toLocaleDateString()} ${d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`;
}

// ── Component ──────────────────────────────────────────────────────

export function ResultsViewScreen() {
  const [results, setResults] = useState<ResultItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<FilterOption>("ALL");
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [acknowledgingId, setAcknowledgingId] = useState<string | null>(null);

  const loadResults = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchResults(filter === "ALL" ? undefined : filter);
      setResults(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load results");
    } finally {
      setLoading(false);
    }
  }, [filter]);

  useEffect(() => {
    loadResults();
  }, [loadResults]);

  const handleAcknowledge = useCallback(
    async (resultId: string) => {
      setAcknowledgingId(resultId);
      try {
        await acknowledgeResult(resultId);
        setResults((prev) =>
          prev.map((r) =>
            r.id === resultId
              ? { ...r, acknowledged: true, acknowledgedAt: new Date().toISOString() }
              : r
          )
        );
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to acknowledge result");
      } finally {
        setAcknowledgingId(null);
      }
    },
    []
  );

  if (loading) {
    return (
      <Screen>
        <Header title="Lab Results" />
        <LoadingSpinner size="lg" />
      </Screen>
    );
  }

  if (error && results.length === 0) {
    return (
      <Screen>
        <Header title="Lab Results" />
        <ErrorState title="Error" message={error} onRetry={loadResults} />
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="Lab Results" />
      <ScrollView testID="results-view-screen" style={styles.container}>
        {/* Filter bar */}
        <View style={styles.filterRow}>
          {FILTER_OPTIONS.map((opt) => (
            <Pressable
              key={opt.key}
              testID={`filter-${opt.key}`}
              style={[
                styles.filterButton,
                filter === opt.key ? styles.filterButtonActive : undefined,
              ]}
              onPress={() => setFilter(opt.key)}
            >
              <Text
                style={[
                  styles.filterText,
                  filter === opt.key ? styles.filterTextActive : undefined,
                ]}
              >
                {opt.label}
              </Text>
            </Pressable>
          ))}
        </View>

        {/* Summary */}
        <View style={styles.summaryRow}>
          <Card>
            <CardBody>
              <View style={styles.tileCenter}>
                <Text style={styles.tileNumber}>{String(results.length)}</Text>
                <Text style={styles.tileLabel}>Total</Text>
              </View>
            </CardBody>
          </Card>
          <Card>
            <CardBody>
              <View style={styles.tileCenter}>
                <Text style={[styles.tileNumber, { color: colors.ui.error.main }]}>
                  {String(results.filter((r) => r.results.some((p) => p.isAbnormal)).length)}
                </Text>
                <Text style={styles.tileLabel}>Abnormal</Text>
              </View>
            </CardBody>
          </Card>
          <Card>
            <CardBody>
              <View style={styles.tileCenter}>
                <Text style={styles.tileNumber}>
                  {String(results.filter((r) => !r.acknowledged && r.status === "COMPLETED").length)}
                </Text>
                <Text style={styles.tileLabel}>Unreviewed</Text>
              </View>
            </CardBody>
          </Card>
        </View>

        {/* Error banner for acknowledge failures */}
        {error ? (
          <View style={styles.errorBanner}>
            <Text style={styles.errorText}>{error}</Text>
          </View>
        ) : null}

        {/* Results list */}
        <CardHeader>Results</CardHeader>
        {results.length === 0 ? (
          <EmptyState title="No results" message="No lab results match the selected filter" />
        ) : (
          results.map((item) => {
            const hasAbnormal = item.results.some((p) => p.isAbnormal);
            const isExpanded = expandedId === item.id;

            return (
              <Card key={item.id}>
                <CardBody>
                  <Pressable
                    testID={`result-${item.id}`}
                    onPress={() =>
                      setExpandedId(isExpanded ? null : item.id)
                    }
                  >
                    <View style={styles.resultHeader}>
                      <View style={styles.resultTitleRow}>
                        <Text style={styles.testName}>{item.testName}</Text>
                        {hasAbnormal ? (
                          <Badge variant="destructive">ABNORMAL</Badge>
                        ) : null}
                      </View>
                      <Badge variant={URGENCY_VARIANT[item.urgency] ?? "secondary"}>
                        {item.urgency}
                      </Badge>
                    </View>

                    <Text style={styles.patientName}>
                      {`Patient: ${item.patientName}`}
                    </Text>
                    <View style={styles.metaRow}>
                      <Text style={styles.metaText}>
                        {`Ordered: ${formatDateTime(item.orderedAt)}`}
                      </Text>
                      <Badge variant={item.status === "COMPLETED" ? "secondary" : "outline"}>
                        {item.status}
                      </Badge>
                    </View>

                    {/* Expanded detail view */}
                    {isExpanded ? (
                      <View style={styles.detailContainer}>
                        <View style={styles.detailRow}>
                          <Text style={styles.detailLabel}>Test Code</Text>
                          <Text style={styles.detailValue}>{item.testCode}</Text>
                        </View>
                        <View style={styles.detailRow}>
                          <Text style={styles.detailLabel}>Ordered By</Text>
                          <Text style={styles.detailValue}>{item.orderedBy}</Text>
                        </View>
                        {item.acknowledged ? (
                          <View style={styles.detailRow}>
                            <Text style={styles.detailLabel}>Acknowledged</Text>
                            <Text style={styles.detailValue}>
                              {item.acknowledgedAt
                                ? formatDateTime(item.acknowledgedAt)
                                : "Yes"}
                            </Text>
                          </View>
                        ) : null}

                        {/* Result parameters table */}
                        {item.results.length > 0 ? (
                          <>
                            <View style={styles.paramHeader}>
                              <Text style={[styles.paramCol, styles.paramHeaderText]}>
                                Parameter
                              </Text>
                              <Text style={[styles.paramColNarrow, styles.paramHeaderText]}>
                                Value
                              </Text>
                              <Text style={[styles.paramColNarrow, styles.paramHeaderText]}>
                                Range
                              </Text>
                            </View>
                            {item.results.map((param) => (
                              <View
                                key={param.id}
                                testID={`param-${param.id}`}
                                style={[
                                  styles.paramRow,
                                  param.isAbnormal ? styles.paramRowAbnormal : undefined,
                                ]}
                              >
                                <Text style={styles.paramCol}>{param.parameter}</Text>
                                <Text
                                  style={[
                                    styles.paramColNarrow,
                                    param.isAbnormal ? styles.abnormalValue : undefined,
                                  ]}
                                >
                                  {`${param.value} ${param.unit}`}
                                </Text>
                                <Text style={styles.paramColNarrow}>
                                  {param.referenceRange}
                                </Text>
                              </View>
                            ))}
                          </>
                        ) : (
                          <Text style={styles.noParams}>
                            Results not yet available
                          </Text>
                        )}

                        {/* Acknowledge action */}
                        {item.status === "COMPLETED" && !item.acknowledged ? (
                          <View style={styles.acknowledgeContainer}>
                            <Button
                              title={
                                acknowledgingId === item.id
                                  ? "Acknowledging..."
                                  : "Acknowledge Result"
                              }
                              variant="default"
                              onPress={() => handleAcknowledge(item.id)}
                              testID={`acknowledge-${item.id}`}
                              disabled={acknowledgingId === item.id}
                            />
                          </View>
                        ) : null}
                      </View>
                    ) : null}
                  </Pressable>
                </CardBody>
              </Card>
            );
          })
        )}

        <View style={styles.refreshContainer}>
          <Button
            title="Refresh"
            variant="outline"
            onPress={loadResults}
            testID="refresh-results"
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
  filterRow: {
    flexDirection: "row",
    gap: 8,
    marginBottom: 16,
  },
  filterButton: {
    paddingVertical: 8,
    paddingHorizontal: 16,
    borderRadius: 20,
    backgroundColor: colors.gray[100],
  },
  filterButtonActive: {
    backgroundColor: "#2563EB",
  },
  filterText: {
    fontSize: 14,
    fontWeight: "500",
    color: colors.gray[700],
  },
  filterTextActive: {
    color: "#FFFFFF",
  },
  summaryRow: {
    flexDirection: "row",
    gap: 12,
    marginBottom: 16,
  },
  tileCenter: {
    alignItems: "center",
  },
  tileNumber: {
    fontSize: 24,
    fontWeight: "700",
  },
  tileLabel: {
    fontSize: 12,
    color: colors.gray[500],
  },
  errorBanner: {
    backgroundColor: "#FEF2F2",
    padding: 12,
    borderRadius: 8,
    marginBottom: 12,
  },
  errorText: {
    color: colors.ui.error.main,
    fontSize: 13,
  },
  resultHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  resultTitleRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    flex: 1,
  },
  testName: {
    fontSize: 15,
    fontWeight: "700",
  },
  patientName: {
    fontSize: 14,
    color: colors.gray[500],
    marginTop: 4,
  },
  metaRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginTop: 6,
  },
  metaText: {
    fontSize: 12,
    color: colors.gray[400],
  },
  detailContainer: {
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: colors.gray[200],
  },
  detailRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingVertical: 4,
  },
  detailLabel: {
    fontSize: 13,
    color: colors.gray[500],
    flex: 1,
  },
  detailValue: {
    fontSize: 13,
    fontWeight: "500",
    flex: 1,
    textAlign: "right",
  },
  paramHeader: {
    flexDirection: "row",
    marginTop: 12,
    paddingBottom: 6,
    borderBottomWidth: 1,
    borderBottomColor: colors.gray[200],
  },
  paramHeaderText: {
    fontSize: 12,
    fontWeight: "600",
    color: colors.gray[500],
  },
  paramRow: {
    flexDirection: "row",
    paddingVertical: 6,
    borderBottomWidth: 1,
    borderBottomColor: colors.gray[100],
  },
  paramRowAbnormal: {
    backgroundColor: "#FEF2F2",
  },
  paramCol: {
    flex: 2,
    fontSize: 13,
  },
  paramColNarrow: {
    flex: 1,
    fontSize: 13,
  },
  abnormalValue: {
    color: colors.ui.error.main,
    fontWeight: "700",
  },
  noParams: {
    fontSize: 13,
    color: colors.gray[400],
    marginTop: 8,
    fontStyle: "italic",
  },
  acknowledgeContainer: {
    marginTop: 12,
  },
  refreshContainer: {
    marginTop: 16,
    marginBottom: 32,
  },
});
