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
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Workspaces
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading workspace...</span>
          </div>
        ) : error || !workspace ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load workspace</p>
          </div>
        ) : (
          <div className="max-w-2xl space-y-6">
            {/* Header */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <div className="flex items-start gap-4 mb-4">
                <div className="w-12 h-12 rounded-lg bg-green-100 flex items-center justify-center shrink-0">
                  <LayoutGrid className="w-6 h-6 text-green-600" />
                </div>
                <div>
                  <h3 className="text-lg font-medium text-gray-900">{workspace.attributes.name}</h3>
                  <p className="text-sm text-gray-500">
                    {workspace.attributes.workspaceType}
                    {workspace.attributes.facilityName && ` at ${workspace.attributes.facilityName}`}
                  </p>
                </div>
              </div>
              <dl className="grid grid-cols-3 gap-4 text-sm">
                <div>
                  <dt className="text-gray-500">Status</dt>
                  <dd className="mt-0.5">
                    <span
                      className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${
                        workspace.attributes.status === "ACTIVE"
                          ? "bg-green-100 text-green-700"
                          : "bg-gray-100 text-gray-700"
                      }`}
                    >
                      {workspace.attributes.status}
                    </span>
                  </dd>
                </div>
                <div>
                  <dt className="text-gray-500">Capacity</dt>
                  <dd className="font-medium text-gray-900 mt-0.5">
                    {workspace.attributes.capacity ?? "\u2014"}
                  </dd>
                </div>
                <div>
                  <dt className="text-gray-500">Queue</dt>
                  <dd className="font-medium text-gray-900 mt-0.5 flex items-center gap-1">
                    <Clock className="w-4 h-4 text-gray-400" />
                    {workspace.attributes.queueCount ?? 0} waiting
                  </dd>
                </div>
              </dl>
            </div>

            {/* Stats */}
            <div className="grid grid-cols-2 gap-4">
              <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
                <Users className="w-6 h-6 text-blue-500 mx-auto mb-1" />
                <p className="text-xl font-bold text-gray-900">
                  {workspace.attributes.assignedStaff?.length ?? 0}
                </p>
                <p className="text-xs text-gray-500">Assigned Staff</p>
              </div>
              <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
                <Activity className="w-6 h-6 text-green-500 mx-auto mb-1" />
                <p className="text-xl font-bold text-gray-900">
                  {workspace.attributes.currentPatients?.length ?? 0}
                </p>
                <p className="text-xs text-gray-500">Current Patients</p>
              </div>
            </div>

            {/* Assigned Staff */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="font-medium text-gray-900 mb-3 flex items-center gap-2">
                <Users className="w-4 h-4" /> Assigned Staff
              </h3>
              {(workspace.attributes.assignedStaff?.length ?? 0) > 0 ? (
                <div className="space-y-2">
                  {workspace.attributes.assignedStaff.map((staff) => (
                    <div
                      key={staff.id}
                      className="flex items-center justify-between p-3 bg-gray-50 rounded-lg"
                    >
                      <span className="text-sm font-medium text-gray-900">
                        {staff.displayName}
                      </span>
                      <span className="text-xs text-gray-500">{staff.role}</span>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-gray-400">No staff currently assigned</p>
              )}
            </div>

            {/* Current Patients */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="font-medium text-gray-900 mb-3 flex items-center gap-2">
                <Activity className="w-4 h-4" /> Current Patients
              </h3>
              {(workspace.attributes.currentPatients?.length ?? 0) > 0 ? (
                <div className="space-y-2">
                  {workspace.attributes.currentPatients.map((patient) => (
                    <div
                      key={patient.id}
                      className="flex items-center justify-between p-3 bg-gray-50 rounded-lg"
                    >
                      <span className="text-sm font-medium text-gray-900">
                        {patient.patientName}
                      </span>
                      <span className="inline-block px-2 py-0.5 text-xs rounded-full font-medium bg-blue-100 text-blue-700">
                        {patient.status}
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-gray-400">No patients currently in workspace</p>
              )}
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
