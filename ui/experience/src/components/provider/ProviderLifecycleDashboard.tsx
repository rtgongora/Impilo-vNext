"use client";

import React, { useState } from "react";
import {
  Award,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  ChevronRight,
  Loader2,
} from "lucide-react";
import { EligibilityStatusCard } from "./EligibilityStatusCard";
import { ProviderLifecycleTimeline } from "./ProviderLifecycleTimeline";
import { useProviderLifecycleSummary } from "@/hooks/queries/useProviderLifecycle";

interface ProviderLifecycleDashboardProps {
  providerId: number;
}

type TabType = "overview" | "applications" | "qualifications" | "compliance" | "certificates";

const tabs: { key: TabType; label: string }[] = [
  { key: "overview", label: "Overview" },
  { key: "applications", label: "Applications" },
  { key: "qualifications", label: "Qualifications" },
  { key: "compliance", label: "Compliance" },
  { key: "certificates", label: "Certificates" },
];

export function ProviderLifecycleDashboard({
  providerId,
}: ProviderLifecycleDashboardProps) {
  const [activeTab, setActiveTab] = useState<TabType>("overview");
  const { data: summary, isLoading } = useProviderLifecycleSummary(providerId);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    );
  }

  if (!summary) {
    return (
      <div className="text-center py-12 text-muted-foreground">
        <p>Provider lifecycle data unavailable</p>
      </div>
    );
  }

  const hasIssues =
    !summary.eligibility?.eligible ||
    (summary.outstandingCompliance?.length ?? 0) > 0;
  const issueCount = (summary.outstandingCompliance?.length ?? 0) +
    (summary.eligibility?.blockReasons?.length ?? 0);

  const timelineEvents = [
    ...summary.applications.map((app) => ({
      id: `app-${app.id}`,
      type: "application" as const,
      title: `${app.applicationType} Application`,
      description: app.authorityRoute,
      date: new Date(app.createdAt),
      status: app.workflowState,
      statusLabel: app.workflowState,
    })),
    ...summary.affiliations.map((aff) => ({
      id: `aff-${aff.id}`,
      type: "affiliation" as const,
      title: `${aff.affiliationType} Affiliation`,
      description: aff.roleTitle ? `Role: ${aff.roleTitle}` : undefined,
      date: aff.startDate ? new Date(aff.startDate) : new Date(),
      status: aff.status,
      statusLabel: aff.status,
    })),
    ...summary.qualifications.map((qual) => ({
      id: `qual-${qual.id}`,
      type: "qualification" as const,
      title: qual.title,
      description: qual.institutionName,
      date: qual.awardDate ? new Date(qual.awardDate) : new Date(),
      status: qual.verificationStatus,
      statusLabel: qual.verificationStatus,
    })),
    ...(summary.currentCertificate
      ? [
          {
            id: `cert-${summary.currentCertificate.id}`,
            type: "certificate" as const,
            title: `${summary.currentCertificate.certificateType} Certificate`,
            date: new Date(),
            status: summary.currentCertificate.status,
            statusLabel: summary.currentCertificate.status,
          },
        ]
      : []),
    ...(summary.picAssignment
      ? [
          {
            id: `pic-${summary.picAssignment.id}`,
            type: "pic_assignment" as const,
            title: `${summary.picAssignment.assignmentType} Assignment`,
            date: summary.picAssignment.startDate
              ? new Date(summary.picAssignment.startDate)
              : new Date(),
            status: summary.picAssignment.status,
            statusLabel: summary.picAssignment.status,
          },
        ]
      : []),
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">Provider Lifecycle</h2>
        {hasIssues && (
          <div className="flex items-center gap-2 rounded-full bg-amber-50 px-3 py-1">
            <AlertTriangle className="h-4 w-4 text-amber-600" />
            <span className="text-sm font-medium text-amber-700">
              {issueCount} issue{issueCount !== 1 ? "s" : ""} to address
            </span>
          </div>
        )}
      </div>

      {summary.eligibility && (
        <EligibilityStatusCard
          eligible={summary.eligibility.eligible}
          hasValidLicense={summary.eligibility.hasValidLicense}
          hasActivePractisingCertificate={
            summary.eligibility.hasActivePractisingCertificate
          }
          hasNoOverdueCompliance={summary.eligibility.hasNoOverdueCompliance}
          hasActiveAffiliation={summary.eligibility.hasActiveAffiliation}
          canServeAsPic={summary.eligibility.canServeAsPic}
          licenseExpiryDate={summary.eligibility.licenseExpiryDate}
          certificateExpiryDate={summary.eligibility.certificateExpiryDate}
          blockReasons={summary.eligibility.blockReasons}
        />
      )}

      <div className="border-b">
        <nav className="flex gap-4 -mb-px">
          {tabs.map(({ key, label }) => (
            <button
              key={key}
              onClick={() => setActiveTab(key)}
              className={`px-3 py-2 text-sm font-medium border-b-2 transition-colors ${
                activeTab === key
                  ? "border-primary text-primary"
                  : "border-transparent text-muted-foreground hover:text-foreground"
              }`}
            >
              {label}
              {key === "compliance" &&
                (summary.outstandingCompliance?.length ?? 0) > 0 && (
                  <span className="ml-1.5 rounded-full bg-amber-100 px-1.5 py-0.5 text-xs text-amber-700">
                    {summary.outstandingCompliance?.length}
                  </span>
                )}
            </button>
          ))}
        </nav>
      </div>

      <div className="mt-6">
        {activeTab === "overview" && (
          <ProviderLifecycleTimeline events={timelineEvents} />
        )}

        {activeTab === "applications" && (
          <div className="space-y-3">
            {summary.applications.length === 0 ? (
              <p className="text-sm text-muted-foreground">No applications</p>
            ) : (
              summary.applications.map((app) => (
                <div
                  key={app.id}
                  className="flex items-center justify-between rounded-lg border p-3"
                >
                  <div className="flex items-center gap-3">
                    {app.workflowState === "DECIDED_APPROVED" ? (
                      <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                    ) : app.workflowState === "DECIDED_REJECTED" ? (
                      <XCircle className="h-4 w-4 text-red-600" />
                    ) : (
                      <Loader2 className="h-4 w-4 text-amber-600" />
                    )}
                    <div>
                      <p className="font-medium text-sm">{app.applicationType}</p>
                      <p className="text-xs text-muted-foreground">
                        {new Date(app.createdAt).toLocaleDateString()}
                      </p>
                    </div>
                  </div>
                  <ChevronRight className="h-4 w-4 text-muted-foreground" />
                </div>
              ))
            )}
          </div>
        )}

        {activeTab === "qualifications" && (
          <div className="space-y-3">
            {summary.qualifications.length === 0 ? (
              <p className="text-sm text-muted-foreground">No qualifications</p>
            ) : (
              summary.qualifications.map((qual) => (
                <div
                  key={qual.id}
                  className="flex items-center justify-between rounded-lg border p-3"
                >
                  <div className="flex items-center gap-3">
                    {qual.verificationStatus === "VERIFIED" ? (
                      <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                    ) : (
                      <Loader2 className="h-4 w-4 text-amber-600" />
                    )}
                    <div>
                      <p className="font-medium text-sm">{qual.title}</p>
                      <p className="text-xs text-muted-foreground">
                        {qual.institutionName}
                      </p>
                    </div>
                  </div>
                  <ChevronRight className="h-4 w-4 text-muted-foreground" />
                </div>
              ))
            )}
          </div>
        )}

        {activeTab === "compliance" && (
          <div className="space-y-3">
            {(summary.outstandingCompliance?.length ?? 0) === 0 ? (
              <div className="flex items-center gap-2 text-emerald-600">
                <CheckCircle2 className="h-4 w-4" />
                <p className="text-sm">All compliance requirements met</p>
              </div>
            ) : (
              summary.outstandingCompliance?.map((action) => (
                <div
                  key={action.id}
                  className="flex items-center justify-between rounded-lg border border-amber-200 bg-amber-50 p-3"
                >
                  <div className="flex items-center gap-3">
                    <AlertTriangle className="h-4 w-4 text-amber-600" />
                    <div>
                      <p className="font-medium text-sm">{action.actionType}</p>
                      <p className="text-xs text-muted-foreground">
                        {action.description}
                        {action.dueDate && ` - Due: ${new Date(action.dueDate).toLocaleDateString()}`}
                      </p>
                    </div>
                  </div>
                  <ChevronRight className="h-4 w-4 text-muted-foreground" />
                </div>
              ))
            )}
          </div>
        )}

        {activeTab === "certificates" && (
          <div className="space-y-3">
            {!summary.currentCertificate ? (
              <p className="text-sm text-muted-foreground">No current certificate</p>
            ) : (
              <div className="rounded-lg border p-4">
                <div className="flex items-center gap-3 mb-3">
                  <Award className="h-5 w-5 text-purple-600" />
                  <div>
                    <p className="font-semibold">
                      {summary.currentCertificate.certificateType}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      Status: {summary.currentCertificate.status}
                    </p>
                  </div>
                </div>
                {summary.currentCertificate.expiryDate && (
                  <p className="text-sm">
                    Expires:{" "}
                    <span className="font-medium">
                      {new Date(
                        summary.currentCertificate.expiryDate
                      ).toLocaleDateString()}
                    </span>
                  </p>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}