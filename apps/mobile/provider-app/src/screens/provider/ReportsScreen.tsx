/**
 * ReportsScreen — Browse available reports by category, view report data.
 *
 * Features:
 * - Available reports list with name, category badge, last-run date
 * - Tappable cards to show report data (key-value summary cards)
 * - Filter by category: Clinical, Operational, Financial
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from "react-native";
import { Screen, Header, Card, CardHeader, CardBody, Badge, Button, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import { fetchReportList, fetchReportData } from "../../services/reportService";
import type { ReportSummary, ReportData, ReportCategory } from "../../types";

const CATEGORIES: (ReportCategory | "All")[] = [
  "All",
  "Clinical",
  "Operational",
  "Financial",
];

const CATEGORY_BADGE_VARIANT: Record<
  ReportCategory,
  "primary" | "secondary" | "destructive"
> = {
  Clinical: "primary",
  Operational: "secondary",
  Financial: "destructive",
};

export function ReportsScreen() {
  const [reports, setReports] = useState<ReportSummary[]>([]);
  const [filter, setFilter] = useState<ReportCategory | "All">("All");
  const [selectedReport, setSelectedReport] = useState<ReportData | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadReports = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await fetchReportList();
      setReports(list);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load reports"
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadReports();
  }, [loadReports]);

  const handleSelectReport = useCallback(async (reportId: string) => {
    setLoadingDetail(true);
    try {
      const data = await fetchReportData(reportId);
      setSelectedReport(data);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load report data"
      );
    } finally {
      setLoadingDetail(false);
    }
  }, []);

  const filteredReports =
    filter === "All"
      ? reports
      : reports.filter((r) => r.category === filter);

  // Report detail view
  if (selectedReport) {
    return (
      <Screen>
        <Header title={selectedReport.reportName} />
        <View testID="report-detail-screen" style={styles.container}>
          <Button
            title="Back to Reports"
            variant="outline"
            onPress={() => setSelectedReport(null)}
            testID="back-to-reports"
          />
          <Text style={styles.generatedAt}>
            Generated: {new Date(selectedReport.generatedAt).toLocaleString()}
          </Text>
          <ScrollView
            style={styles.scrollArea}
            contentContainerStyle={styles.scrollContent}
          >
            {selectedReport.entries.map((entry, idx) => (
              <Card key={`${entry.key}-${idx}`}>
                <CardBody>
                  <View
                    testID={`report-entry-${idx}`}
                    style={styles.entryRow}
                  >
                    <Text style={styles.entryKey}>{entry.key}</Text>
                    <Text style={styles.entryValue}>
                      {entry.value}
                      {entry.unit ? ` ${entry.unit}` : ""}
                    </Text>
                  </View>
                </CardBody>
              </Card>
            ))}
          </ScrollView>
        </View>
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="Reports & Analytics" />
      <View testID="reports-screen" style={styles.container}>
        {/* Category filter */}
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          style={styles.filterBar}
          contentContainerStyle={styles.filterBarContent}
        >
          {CATEGORIES.map((cat) => (
            <TouchableOpacity
              key={cat}
              onPress={() => setFilter(cat)}
              style={[
                styles.filterChip,
                filter === cat && styles.filterChipActive,
              ]}
              testID={`filter-${cat}`}
            >
              <Text
                style={[
                  styles.filterChipText,
                  filter === cat && styles.filterChipTextActive,
                ]}
              >
                {cat}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>

        {loading ? (
          <LoadingSpinner size="md" />
        ) : error ? (
          <ErrorState title="Error" message={error} onRetry={loadReports} />
        ) : filteredReports.length === 0 ? (
          <EmptyState
            title="No reports"
            message={`No ${filter === "All" ? "" : filter + " "}reports available`}
          />
        ) : (
          <ScrollView
            style={styles.scrollArea}
            contentContainerStyle={styles.scrollContent}
          >
            {filteredReports.map((report) => (
              <TouchableOpacity
                key={report.id}
                onPress={() => handleSelectReport(report.id)}
                testID={`report-${report.id}`}
                activeOpacity={0.7}
              >
                <Card>
                  <CardBody>
                    <View style={styles.reportRow}>
                      <View style={styles.reportInfo}>
                        <Text style={styles.reportName}>{report.name}</Text>
                        {report.description && (
                          <Text style={styles.reportDesc}>
                            {report.description}
                          </Text>
                        )}
                        {report.lastRunAt && (
                          <Text style={styles.reportMeta}>
                            Last run:{" "}
                            {new Date(report.lastRunAt).toLocaleDateString()}
                          </Text>
                        )}
                      </View>
                      <Badge
                        variant={CATEGORY_BADGE_VARIANT[report.category]}
                      >
                        {report.category}
                      </Badge>
                    </View>
                  </CardBody>
                </Card>
              </TouchableOpacity>
            ))}
          </ScrollView>
        )}

        {loadingDetail && (
          <View style={styles.loadingOverlay}>
            <LoadingSpinner size="md" />
          </View>
        )}

        <View style={styles.refreshContainer}>
          <Button
            title="Refresh"
            variant="outline"
            onPress={loadReports}
            testID="refresh-reports"
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
  filterBar: {
    marginBottom: 12,
    maxHeight: 40,
  },
  filterBarContent: {
    flexDirection: "row",
    gap: 8,
  },
  filterChip: {
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: colors.gray[100],
  },
  filterChipActive: {
    backgroundColor: "#2563EB",
  },
  filterChipText: {
    fontSize: 13,
    fontWeight: "500",
    color: colors.gray[500],
  },
  filterChipTextActive: {
    color: "#FFFFFF",
  },
  scrollArea: {
    flex: 1,
  },
  scrollContent: {
    gap: 12,
    paddingBottom: 16,
  },
  reportRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
  },
  reportInfo: {
    flex: 1,
    gap: 4,
    marginRight: 12,
  },
  reportName: {
    fontSize: 15,
    fontWeight: "700",
    color: colors.gray[900],
  },
  reportDesc: {
    fontSize: 13,
    color: colors.gray[500],
  },
  reportMeta: {
    fontSize: 12,
    color: colors.gray[400],
  },
  generatedAt: {
    fontSize: 12,
    color: colors.gray[400],
    marginVertical: 8,
  },
  entryRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  entryKey: {
    fontSize: 14,
    fontWeight: "600",
    color: colors.gray[700],
    flex: 1,
  },
  entryValue: {
    fontSize: 16,
    fontWeight: "800",
    color: colors.gray[900],
  },
  loadingOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "rgba(255,255,255,0.7)",
    justifyContent: "center",
    alignItems: "center",
  },
  refreshContainer: {
    marginTop: 8,
  },
});
