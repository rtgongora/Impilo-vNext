"use client";

import React, { useState } from "react";
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
} from "lucide-react";

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

/* ---------- mock data ---------- */
const MOCK_LAB_RESULTS: LabResult[] = [
  // Full Blood Count (FBC)
  {
    id: "lab-001",
    test_name: "Haemoglobin",
    test_code: "HGB",
    category: "Full Blood Count",
    status: "completed",
    result_value: "8.2",
    result_unit: "g/dL",
    reference_range: "12.0-17.5",
    is_abnormal: true,
    is_critical: true,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: "2026-04-07T08:30:00Z",
  },
  {
    id: "lab-002",
    test_name: "White Cell Count",
    test_code: "WBC",
    category: "Full Blood Count",
    status: "completed",
    result_value: "14.8",
    result_unit: "x10^9/L",
    reference_range: "4.0-11.0",
    is_abnormal: true,
    is_critical: false,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: "2026-04-07T08:30:00Z",
  },
  {
    id: "lab-003",
    test_name: "Platelets",
    test_code: "PLT",
    category: "Full Blood Count",
    status: "completed",
    result_value: "245",
    result_unit: "x10^9/L",
    reference_range: "150-400",
    is_abnormal: false,
    is_critical: false,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: "2026-04-07T08:30:00Z",
  },
  {
    id: "lab-004",
    test_name: "Haematocrit",
    test_code: "HCT",
    category: "Full Blood Count",
    status: "completed",
    result_value: "25.1",
    result_unit: "%",
    reference_range: "36-46",
    is_abnormal: true,
    is_critical: true,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: "2026-04-07T08:30:00Z",
  },
  {
    id: "lab-005",
    test_name: "MCV",
    test_code: "MCV",
    category: "Full Blood Count",
    status: "completed",
    result_value: "82",
    result_unit: "fL",
    reference_range: "80-100",
    is_abnormal: false,
    is_critical: false,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: "2026-04-07T08:30:00Z",
  },
  // Urea & Electrolytes (U&E)
  {
    id: "lab-010",
    test_name: "Sodium",
    test_code: "NA",
    category: "Urea & Electrolytes",
    status: "completed",
    result_value: "141",
    result_unit: "mmol/L",
    reference_range: "136-145",
    is_abnormal: false,
    is_critical: false,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: "2026-04-07T08:45:00Z",
  },
  {
    id: "lab-011",
    test_name: "Potassium",
    test_code: "K",
    category: "Urea & Electrolytes",
    status: "completed",
    result_value: "5.8",
    result_unit: "mmol/L",
    reference_range: "3.5-5.1",
    is_abnormal: true,
    is_critical: true,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: "2026-04-07T08:45:00Z",
  },
  {
    id: "lab-012",
    test_name: "Urea",
    test_code: "UREA",
    category: "Urea & Electrolytes",
    status: "completed",
    result_value: "12.4",
    result_unit: "mmol/L",
    reference_range: "2.5-7.8",
    is_abnormal: true,
    is_critical: false,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: "2026-04-07T08:45:00Z",
  },
  {
    id: "lab-013",
    test_name: "Creatinine",
    test_code: "CREAT",
    category: "Urea & Electrolytes",
    status: "completed",
    result_value: "156",
    result_unit: "umol/L",
    reference_range: "60-110",
    is_abnormal: true,
    is_critical: false,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: "2026-04-07T08:45:00Z",
  },
  {
    id: "lab-014",
    test_name: "eGFR",
    test_code: "EGFR",
    category: "Urea & Electrolytes",
    status: "completed",
    result_value: "38",
    result_unit: "mL/min",
    reference_range: ">60",
    is_abnormal: true,
    is_critical: true,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: "2026-04-07T08:45:00Z",
  },
  // Liver Function Tests (LFTs)
  {
    id: "lab-020",
    test_name: "ALT",
    test_code: "ALT",
    category: "Liver Function Tests",
    status: "completed",
    result_value: "42",
    result_unit: "U/L",
    reference_range: "7-56",
    is_abnormal: false,
    is_critical: false,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: "2026-04-07T09:00:00Z",
  },
  {
    id: "lab-021",
    test_name: "AST",
    test_code: "AST",
    category: "Liver Function Tests",
    status: "completed",
    result_value: "68",
    result_unit: "U/L",
    reference_range: "10-40",
    is_abnormal: true,
    is_critical: false,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: "2026-04-07T09:00:00Z",
  },
  {
    id: "lab-022",
    test_name: "ALP",
    test_code: "ALP",
    category: "Liver Function Tests",
    status: "completed",
    result_value: "95",
    result_unit: "U/L",
    reference_range: "44-147",
    is_abnormal: false,
    is_critical: false,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: "2026-04-07T09:00:00Z",
  },
  {
    id: "lab-023",
    test_name: "Total Bilirubin",
    test_code: "TBIL",
    category: "Liver Function Tests",
    status: "completed",
    result_value: "18",
    result_unit: "umol/L",
    reference_range: "5-21",
    is_abnormal: false,
    is_critical: false,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: "2026-04-07T09:00:00Z",
  },
  {
    id: "lab-024",
    test_name: "Albumin",
    test_code: "ALB",
    category: "Liver Function Tests",
    status: "completed",
    result_value: "28",
    result_unit: "g/L",
    reference_range: "35-52",
    is_abnormal: true,
    is_critical: false,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: "2026-04-07T09:00:00Z",
  },
  {
    id: "lab-025",
    test_name: "GGT",
    test_code: "GGT",
    category: "Liver Function Tests",
    status: "completed",
    result_value: "52",
    result_unit: "U/L",
    reference_range: "9-48",
    is_abnormal: true,
    is_critical: false,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: "2026-04-07T09:00:00Z",
  },
  // Cardiac markers
  {
    id: "lab-030",
    test_name: "Troponin I",
    test_code: "TROP",
    category: "Cardiac Markers",
    status: "completed",
    result_value: "2.4",
    result_unit: "ng/mL",
    reference_range: "<0.04",
    is_abnormal: true,
    is_critical: true,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: "2026-04-07T07:30:00Z",
  },
  {
    id: "lab-031",
    test_name: "CRP",
    test_code: "CRP",
    category: "Cardiac Markers",
    status: "completed",
    result_value: "48",
    result_unit: "mg/L",
    reference_range: "<5",
    is_abnormal: true,
    is_critical: false,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: "2026-04-07T07:30:00Z",
  },
  {
    id: "lab-032",
    test_name: "D-Dimer",
    test_code: "DDIM",
    category: "Cardiac Markers",
    status: "completed",
    result_value: "0.42",
    result_unit: "mg/L FEU",
    reference_range: "<0.50",
    is_abnormal: false,
    is_critical: false,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: "2026-04-07T07:30:00Z",
  },
  // Pending tests
  {
    id: "lab-040",
    test_name: "Blood Culture",
    test_code: "BCULT",
    category: "Microbiology",
    status: "processing",
    result_value: null,
    result_unit: null,
    reference_range: null,
    is_abnormal: false,
    is_critical: false,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: null,
  },
  {
    id: "lab-041",
    test_name: "HbA1c",
    test_code: "HBA1C",
    category: "Endocrine",
    status: "collected",
    result_value: null,
    result_unit: null,
    reference_range: null,
    is_abnormal: false,
    is_critical: false,
    ordered_at: "2026-04-07T06:00:00Z",
    resulted_at: null,
  },
  {
    id: "lab-042",
    test_name: "TSH",
    test_code: "TSH",
    category: "Endocrine",
    status: "pending",
    result_value: null,
    result_unit: null,
    reference_range: null,
    is_abnormal: false,
    is_critical: false,
    ordered_at: "2026-04-07T06:30:00Z",
    resulted_at: null,
  },
];

/* ---------- status configs ---------- */
const statusConfig = {
  normal: {
    bg: "bg-emerald-50 text-emerald-700 border border-emerald-200",
    icon: Minus,
    label: "Normal",
  },
  high: {
    bg: "bg-amber-50 text-amber-700 border border-amber-200",
    icon: TrendingUp,
    label: "High",
  },
  low: {
    bg: "bg-blue-50 text-blue-700 border border-blue-200",
    icon: TrendingDown,
    label: "Low",
  },
  critical: {
    bg: "bg-red-50 text-red-700 border border-red-200",
    icon: AlertTriangle,
    label: "Critical",
  },
};

const orderStatusConfig: Record<
  string,
  { bg: string; icon: React.ElementType; label: string }
> = {
  pending: { bg: "bg-gray-100 text-gray-600", icon: Clock, label: "Pending" },
  collected: { bg: "bg-blue-50 text-blue-600", icon: TestTube, label: "Collected" },
  processing: { bg: "bg-amber-50 text-amber-600", icon: Beaker, label: "Processing" },
  resulted: { bg: "bg-emerald-50 text-emerald-600", icon: CheckCircle, label: "Resulted" },
  completed: { bg: "bg-emerald-50 text-emerald-600", icon: CheckCircle, label: "Completed" },
};

/* ---------- component ---------- */
export function LabResultsSystem() {
  const [searchQuery, setSearchQuery] = useState("");
  const [activeTab, setActiveTab] = useState<"all" | "pending" | "abnormal">("all");

  const results = MOCK_LAB_RESULTS;

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

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold">Laboratory Results</h2>
          <p className="text-sm text-gray-500">View and track lab orders and results</p>
        </div>
        <div className="flex gap-2">
          <button className="inline-flex items-center px-3 py-1.5 text-sm font-medium rounded-md border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 transition-colors">
            <Filter className="h-4 w-4 mr-2" />
            Filter
          </button>
          <button className="inline-flex items-center px-3 py-1.5 text-sm font-medium rounded-md border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 transition-colors">
            <Download className="h-4 w-4 mr-2" />
            Export
          </button>
        </div>
      </div>

      {/* Search */}
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
        <input
          placeholder="Search tests..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="w-full pl-10 pr-4 py-2 rounded-md border border-gray-300 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
        />
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-gray-200">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
              activeTab === tab.key
                ? "border-blue-500 text-blue-600"
                : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
            } ${tab.key === "abnormal" ? "text-red-600" : ""}`}
          >
            {tab.label} ({tab.count})
          </button>
        ))}
      </div>

      {/* Results grouped by category */}
      <div className="overflow-y-auto max-h-[500px] space-y-6">
        {Object.keys(groupedResults).length === 0 ? (
          <div className="text-center py-12 text-gray-400">
            <TestTube className="w-12 h-12 mx-auto mb-4 opacity-50" />
            <p>No lab results found</p>
          </div>
        ) : (
          Object.entries(groupedResults).map(([category, categoryResults]) => (
            <div key={category}>
              <h3 className="text-sm font-semibold text-gray-700 mb-2 flex items-center gap-2">
                <TestTube className="h-4 w-4 text-blue-500" />
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
                      className="rounded-lg border border-gray-200 bg-white p-4 hover:shadow-md transition-shadow"
                    >
                      <div className="flex items-start justify-between">
                        <div className="flex-1">
                          <div className="flex items-center gap-3">
                            <div className="h-10 w-10 rounded-lg bg-blue-50 flex items-center justify-center flex-shrink-0">
                              <TestTube className="h-5 w-5 text-blue-600" />
                            </div>
                            <div>
                              <h4 className="font-medium text-gray-900">{result.test_name}</h4>
                              <p className="text-sm text-gray-500">{result.category}</p>
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
                              <span className="text-gray-400 text-xs">
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
                                    : "text-gray-900"
                                }`}
                              >
                                {result.result_value}
                              </span>
                              <span className="text-sm text-gray-500 ml-1">
                                {result.result_unit}
                              </span>
                            </div>
                          )}
                          {result.reference_range && (
                            <p className="text-xs text-gray-400">
                              Ref: {result.reference_range} {result.result_unit}
                            </p>
                          )}
                          <button className="mt-2 inline-flex items-center px-2 py-1 text-xs text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded transition-colors">
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
