"use client";

/**
 * Report Detail — View report metadata, status, and download.
 * Route: /reports/[id]
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { Loader2, AlertTriangle, FileText, Download, ArrowLeft, Clock, CheckCircle2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import type { ReportResource } from "@/hooks/queries/useReports";

const STATUS_CONFIG: Record<string, { label: string; className: string; icon: typeof Clock }> = {
  GENERATING: { label: "Generating", className: "bg-amber-100 text-amber-700", icon: Clock },
  COMPLETED: { label: "Completed", className: "bg-green-100 text-green-700", icon: CheckCircle2 },
  FAILED: { label: "Failed", className: "bg-red-100 text-red-700", icon: AlertTriangle },
};

export default function ReportDetailPage() {
  const params = useParams();
  const id = params.id as string;

  const { data, isLoading, error } = useQuery<ApiResponse<ReportResource>>({
    queryKey: ["reports", id],
    queryFn: () => apiClient.get<ApiResponse<ReportResource>>(`/internal/v1/reports/${id}`),
    enabled: !!id,
    refetchInterval: (query) => {
      const status = query.state.data?.data?.attributes?.status;
      return status === "GENERATING" ? 3000 : false;
    },
  });

  const report = data?.data;
  const statusConfig = report ? STATUS_CONFIG[report.attributes.status] ?? STATUS_CONFIG.GENERATING : null;

  return (
    <AppLayout>
      <PageShell title="Report Details" subtitle="View report status and download">
        <div className="mb-4">
          <Link
            href="/reports"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Reports
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading report...</span>
          </div>
        ) : error || !report ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load report</p>
          </div>
        ) : (
          <div className="max-w-2xl space-y-6">
            {/* Report Info Card */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <div className="flex items-start justify-between">
                <div className="flex items-start gap-3">
                  <div className="w-10 h-10 rounded-lg bg-blue-100 flex items-center justify-center shrink-0">
                    <FileText className="w-5 h-5 text-blue-600" />
                  </div>
                  <div>
                    <h3 className="font-medium text-gray-900">{report.attributes.reportType.replace(/_/g, " ")}</h3>
                    <p className="text-xs text-gray-500 mt-0.5">Report ID: {report.id}</p>
                  </div>
                </div>
                {statusConfig && (
                  <span className={`inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded-full font-medium ${statusConfig.className}`}>
                    <statusConfig.icon className="w-3 h-3" />
                    {statusConfig.label}
                  </span>
                )}
              </div>
            </div>

            {/* Metadata */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="font-medium text-gray-900 mb-3">Details</h3>
              <dl className="space-y-3 text-sm">
                <div className="flex justify-between">
                  <dt className="text-gray-500">Status</dt>
                  <dd className="text-gray-900 font-medium">{report.attributes.status}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-gray-500">Generated At</dt>
                  <dd className="text-gray-900">
                    {report.attributes.generatedAt
                      ? new Date(report.attributes.generatedAt).toLocaleString()
                      : "Pending"}
                  </dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-gray-500">Report Type</dt>
                  <dd className="text-gray-900">{report.attributes.reportType}</dd>
                </div>
              </dl>
            </div>

            {/* Download */}
            {report.attributes.status === "COMPLETED" && report.attributes.downloadUrl && (
              <div className="flex justify-end">
                <a
                  href={report.attributes.downloadUrl}
                  download
                  className="inline-flex items-center gap-2 px-5 py-2.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700"
                >
                  <Download className="w-4 h-4" />
                  Download Report
                </a>
              </div>
            )}

            {report.attributes.status === "GENERATING" && (
              <div className="flex items-center justify-center gap-2 py-4 text-sm text-amber-600">
                <Loader2 className="w-4 h-4 animate-spin" />
                Report is being generated. This page will update automatically.
              </div>
            )}

            {report.attributes.status === "FAILED" && (
              <div className="p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700">
                Report generation failed. Please try generating a new report.
              </div>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
