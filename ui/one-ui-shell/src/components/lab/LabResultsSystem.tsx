"use client";

import React, { useState, useMemo } from "react";
import {
  TestTube,
  Clock,
  CheckCircle,
  AlertTriangle,
  Search,
  Filter,
  Download,
  TrendingUp,
  TrendingDown,
  Minus,
  FileText,
  Beaker,
  Loader2,
} from "lucide-react";
import { useLabOrders, type LabOrderResource } from "@/hooks/queries/useLabOrders";

/* ---------- types ---------- */
interface LabResult {
  id: string;
  test_name: string;
  test_code: string | null;
  category: string;
  status: "pending" | "collected" | "processing" | "resulted" | "completed";
  result_value: string | null;
  result_unit: string | null;
  reference_range: string | null;
  is_abnormal: boolean;
  is_critical: boolean;
  ordered_at: string;
  resulted_at: string | null;
}

/* ---------- mapper ---------- */
function mapLabOrderToResult(order: LabOrderResource): LabResult {
  return {
    id: order.id,
    test_name: order.attributes.testName,
    test_code: order.attributes.testCode || null,
    category: order.attributes.category || "General",
    status: (order.attributes.status as LabResult["status"]) || "pending",
    result_value: null,
    result_unit: null,
    reference_range: null,
    is_abnormal: false,
    is_critical: false,
    ordered_at: order.attributes.createdAt,
    resulted_at: order.attributes.resultedAt || null,
  };
}

/* ---------- status configs ---------- */
const statusConfig = {
  normal: {
    bg: "bg-success-soft text-primary-hover border border-success/25",
    icon: Minus,
    label: "Normal",
  },
  high: {
    bg: "bg-warning-soft text-warning-foreground border border-warning/35",
    icon: TrendingUp,
    label: "High",
  },
  low: {
    bg: "bg-primary-soft text-primary border border-primary/25",
    icon: TrendingDown,
    label: "Low",
  },
  critical: {
    bg: "bg-danger-soft text-danger border border-danger/28",
    icon: AlertTriangle,
    label: "Critical",
  },
};

const orderStatusConfig: Record<
  string,
  { bg: string; icon: React.ElementType; label: string }
> = {
  pending: { bg: "bg-neutral-100 text-muted-foreground", icon: Clock, label: "Pending" },
  collected: { bg: "bg-primary-soft text-primary", icon: TestTube, label: "Collected" },
  processing: { bg: "bg-warning-soft text-amber-600", icon: Beaker, label: "Processing" },
  resulted: { bg: "bg-success-soft text-primary", icon: CheckCircle, label: "Resulted" },
  completed: { bg: "bg-success-soft text-primary", icon: CheckCircle, label: "Completed" },
};

/* ---------- component ---------- */
interface LabResultsSystemProps {
  patientId: string;
}

export function LabResultsSystem({ patientId }: LabResultsSystemProps) {
  const { data, isLoading, error } = useLabOrders(patientId);
  const [searchQuery, setSearchQuery] = useState("");
  const [activeTab, setActiveTab] = useState<"all" | "pending" | "abnormal">("all");

  const results: LabResult[] = useMemo(
    () => (data?.data ?? []).map(mapLabOrderToResult),
    [data]
  );

  const getResultStatus = (result: LabResult): keyof typeof statusConfig => {
    if (result.is_critical) return "critical";
    if (result.is_abnormal) return "high";
    return "normal";
  };

  const filteredResults = results.filter((result) => {
    const matchesSearch =
      result.test_name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (result.category || "").toLowerCase().includes(searchQuery.toLowerCase());

    if (activeTab === "all") return matchesSearch;
    if (activeTab === "pending") return matchesSearch && result.status !== "completed";
    if (activeTab === "abnormal")
      return matchesSearch && (result.is_abnormal || result.is_critical);
    return matchesSearch;
  });

  // Group filtered results by category
  const groupedResults = filteredResults.reduce(
    (acc, result) => {
      const cat = result.category || "General";
      if (!acc[cat]) acc[cat] = [];
      acc[cat].push(result);
      return acc;
    },
    {} as Record<string, LabResult[]>
  );

  const pendingCount = results.filter((r) => r.status !== "completed").length;
  const abnormalCount = results.filter((r) => r.is_abnormal || r.is_critical).length;

  const tabs: { key: "all" | "pending" | "abnormal"; label: string; count: number }[] = [
    { key: "all", label: "All Results", count: results.length },
    { key: "pending", label: "Pending", count: pendingCount },
    { key: "abnormal", label: "Abnormal", count: abnormalCount },
  ];

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-6 w-6 animate-spin text-primary" />
        <span className="ml-2 text-sm text-muted-foreground">Loading lab results...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center py-12 text-red-600">
        <AlertTriangle className="h-5 w-5 mr-2" />
        <span className="text-sm">Failed to load lab results. Please try again.</span>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold">Laboratory Results</h2>
          <p className="text-sm text-muted-foreground">View and track lab orders and results</p>
        </div>
        <div className="flex gap-2">
          <button className="inline-flex items-center px-3 py-1.5 text-sm font-medium rounded-md border border-border bg-card text-foreground hover:bg-background transition-colors">
            <Filter className="h-4 w-4 mr-2" />
            Filter
          </button>
          <button className="inline-flex items-center px-3 py-1.5 text-sm font-medium rounded-md border border-border bg-card text-foreground hover:bg-background transition-colors">
            <Download className="h-4 w-4 mr-2" />
            Export
          </button>
        </div>
      </div>

      {/* Search */}
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
        <input
          placeholder="Search tests..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="w-full pl-10 pr-4 py-2 rounded-md border border-border text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
        />
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-border">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
              activeTab === tab.key
                ? "border-impilo-400 text-primary"
                : "border-transparent text-muted-foreground hover:text-foreground hover:border-border"
            } ${tab.key === "abnormal" ? "text-red-600" : ""}`}
          >
            {tab.label} ({tab.count})
          </button>
        ))}
      </div>

      {/* Results grouped by category */}
      <div className="overflow-y-auto max-h-[500px] space-y-6">
        {Object.keys(groupedResults).length === 0 ? (
          <div className="text-center py-12 text-muted-foreground">
            <TestTube className="w-12 h-12 mx-auto mb-4 opacity-50" />
            <p>No lab results found</p>
          </div>
        ) : (
          Object.entries(groupedResults).map(([category, categoryResults]) => (
            <div key={category}>
              <h3 className="text-sm font-semibold text-foreground mb-2 flex items-center gap-2">
                <TestTube className="h-4 w-4 text-impilo-400" />
                {category}
              </h3>
              <div className="space-y-2">
                {categoryResults.map((result) => {
                  const resultStatus = getResultStatus(result);
                  const StatusIcon = statusConfig[resultStatus].icon;
                  const orderStatus =
                    orderStatusConfig[result.status] || orderStatusConfig.pending;
                  const OrderIcon = orderStatus.icon;

                  return (
                    <div
                      key={result.id}
                      className="rounded-lg border border-border bg-card p-4 hover:shadow-md transition-shadow"
                    >
                      <div className="flex items-start justify-between">
                        <div className="flex-1">
                          <div className="flex items-center gap-3">
                            <div className="h-10 w-10 rounded-lg bg-primary-soft flex items-center justify-center flex-shrink-0">
                              <TestTube className="h-5 w-5 text-primary" />
                            </div>
                            <div>
                              <h4 className="font-medium text-foreground">{result.test_name}</h4>
                              <p className="text-sm text-muted-foreground">{result.category}</p>
                            </div>
                          </div>

                          <div className="mt-3 flex items-center gap-4 text-sm flex-wrap">
                            {/* Order status badge */}
                            <span
                              className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${orderStatus.bg}`}
                            >
                              <OrderIcon className="h-3 w-3 mr-1" />
                              {orderStatus.label}
                            </span>

                            {/* Result status badge */}
                            {result.status === "completed" && result.result_value && (
                              <span
                                className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${statusConfig[resultStatus].bg}`}
                              >
                                <StatusIcon className="h-3 w-3 mr-1" />
                                {statusConfig[resultStatus].label}
                              </span>
                            )}

                            {/* Test code */}
                            {result.test_code && (
                              <span className="text-muted-foreground text-xs">
                                Code: {result.test_code}
                              </span>
                            )}
                          </div>
                        </div>

                        <div className="text-right flex-shrink-0 ml-4">
                          {result.result_value && (
                            <div className="mb-2">
                              <span
                                className={`text-2xl font-bold ${
                                  result.is_critical
                                    ? "text-red-600"
                                    : result.is_abnormal
                                    ? "text-amber-600"
                                    : "text-foreground"
                                }`}
                              >
                                {result.result_value}
                              </span>
                              <span className="text-sm text-muted-foreground ml-1">
                                {result.result_unit}
                              </span>
                            </div>
                          )}
                          {result.reference_range && (
                            <p className="text-xs text-muted-foreground">
                              Ref: {result.reference_range} {result.result_unit}
                            </p>
                          )}
                          <button className="mt-2 inline-flex items-center px-2 py-1 text-xs text-muted-foreground hover:text-foreground hover:bg-neutral-100 rounded transition-colors">
                            <FileText className="h-4 w-4 mr-1" />
                            Details
                          </button>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
