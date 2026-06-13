"use client";

/**
 * Workspace Detail — View workspace info, staff, patients, queue status.
 * Route: /workspace/[id]
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { Loader2, AlertTriangle, ArrowLeft, LayoutGrid, Users, Activity, Clock } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface WorkspaceDetail {
  id: string;
  type: "workspace";
  attributes: {
    name: string;
    workspaceType: string;
    facilityId: string;
    facilityName: string;
    status: string;
    capacity: number;
    assignedStaff: Array<{
      id: string;
      displayName: string;
      role: string;
    }>;
    currentPatients: Array<{
      id: string;
      patientName: string;
      status: string;
    }>;
    queueCount: number;
    [key: string]: unknown;
  };
}

export default function WorkspaceDetailPage() {
  const params = useParams();
  const id = params.id as string;

  const { data, isLoading, error } = useQuery<ApiResponse<WorkspaceDetail>>({
    queryKey: ["workspace-detail", id],
    queryFn: () => apiClient.get<ApiResponse<WorkspaceDetail>>(`/internal/v1/workspaces/${id}`),
    enabled: !!id,
  });

  const workspace = data?.data;

  return (
    <AppLayout>
      <PageShell title="Workspace Details" subtitle="Workspace information and current activity">
        <div className="mb-4">
          <Link
            href="/workspace"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Workspaces
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading workspace...</span>
          </div>
        ) : error || !workspace ? (
          <div className="bg-danger-soft rounded-lg border border-danger/28 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load workspace</p>
          </div>
        ) : (
          <div className="max-w-2xl space-y-6">
            {/* Header */}
            <div className="bg-card rounded-lg border border-border p-5">
              <div className="flex items-start gap-4 mb-4">
                <div className="w-12 h-12 rounded-lg bg-green-100 flex items-center justify-center shrink-0">
                  <LayoutGrid className="w-6 h-6 text-green-600" />
                </div>
                <div>
                  <h3 className="text-lg font-medium text-foreground">{workspace.attributes.name}</h3>
                  <p className="text-sm text-muted-foreground">
                    {workspace.attributes.workspaceType}
                    {workspace.attributes.facilityName && ` at ${workspace.attributes.facilityName}`}
                  </p>
                </div>
              </div>
              <dl className="grid grid-cols-3 gap-4 text-sm">
                <div>
                  <dt className="text-muted-foreground">Status</dt>
                  <dd className="mt-0.5">
                    <span
                      className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${
                        workspace.attributes.status === "ACTIVE"
                          ? "bg-green-100 text-green-700"
                          : "bg-neutral-100 text-foreground"
                      }`}
                    >
                      {workspace.attributes.status}
                    </span>
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Capacity</dt>
                  <dd className="font-medium text-foreground mt-0.5">
                    {workspace.attributes.capacity ?? "\u2014"}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Queue</dt>
                  <dd className="font-medium text-foreground mt-0.5 flex items-center gap-1">
                    <Clock className="w-4 h-4 text-muted-foreground" />
                    {workspace.attributes.queueCount ?? 0} waiting
                  </dd>
                </div>
              </dl>
            </div>

            {/* Stats */}
            <div className="grid grid-cols-2 gap-4">
              <div className="bg-card rounded-lg border border-border p-4 text-center">
                <Users className="w-6 h-6 text-impilo-400 mx-auto mb-1" />
                <p className="text-xl font-bold text-foreground">
                  {workspace.attributes.assignedStaff?.length ?? 0}
                </p>
                <p className="text-xs text-muted-foreground">Assigned Staff</p>
              </div>
              <div className="bg-card rounded-lg border border-border p-4 text-center">
                <Activity className="w-6 h-6 text-green-500 mx-auto mb-1" />
                <p className="text-xl font-bold text-foreground">
                  {workspace.attributes.currentPatients?.length ?? 0}
                </p>
                <p className="text-xs text-muted-foreground">Current Patients</p>
              </div>
            </div>

            {/* Assigned Staff */}
            <div className="bg-card rounded-lg border border-border p-5">
              <h3 className="font-medium text-foreground mb-3 flex items-center gap-2">
                <Users className="w-4 h-4" /> Assigned Staff
              </h3>
              {(workspace.attributes.assignedStaff?.length ?? 0) > 0 ? (
                <div className="space-y-2">
                  {workspace.attributes.assignedStaff.map((staff) => (
                    <div
                      key={staff.id}
                      className="flex items-center justify-between p-3 bg-background rounded-lg"
                    >
                      <span className="text-sm font-medium text-foreground">
                        {staff.displayName}
                      </span>
                      <span className="text-xs text-muted-foreground">{staff.role}</span>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">No staff currently assigned</p>
              )}
            </div>

            {/* Current Patients */}
            <div className="bg-card rounded-lg border border-border p-5">
              <h3 className="font-medium text-foreground mb-3 flex items-center gap-2">
                <Activity className="w-4 h-4" /> Current Patients
              </h3>
              {(workspace.attributes.currentPatients?.length ?? 0) > 0 ? (
                <div className="space-y-2">
                  {workspace.attributes.currentPatients.map((patient) => (
                    <div
                      key={patient.id}
                      className="flex items-center justify-between p-3 bg-background rounded-lg"
                    >
                      <span className="text-sm font-medium text-foreground">
                        {patient.patientName}
                      </span>
                      <span className="inline-block px-2 py-0.5 text-xs rounded-full font-medium bg-primary-soft text-primary">
                        {patient.status}
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">No patients currently in workspace</p>
              )}
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
