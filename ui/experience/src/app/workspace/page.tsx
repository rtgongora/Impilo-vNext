"use client";

/**
 * Select Workspace — Workspace selection page.
 * Route: /workspace | pageTitle: "Select Workspace"
 */

import { useRouter } from "next/navigation";
import { LayoutGrid, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface WorkspaceResource {
  id: string;
  type: "workspace";
  attributes: {
    name: string;
    workspaceType: string;
    facilityId: string;
    status: string;
    [key: string]: unknown;
  };
}

export default function WorkspacePage() {
  const router = useRouter();
  const facility = useFacilityStore((s) => s.facility);
  const setWorkspace = useWorkspaceStore((s) => s.setWorkspace);

  const { data, isLoading } = useQuery<ApiResponse<WorkspaceResource[]>>({
    queryKey: ["workspaces", facility?.id],
    queryFn: () =>
      apiClient.get<ApiResponse<WorkspaceResource[]>>(
        `/internal/v1/workspaces?facility_id=${encodeURIComponent(facility?.id ?? "")}`,
      ),
    enabled: !!facility?.id,
  });

  const workspaces = data?.data ?? [];

  function handleSelect(ws: WorkspaceResource) {
    setWorkspace({
      id: ws.id,
      name: ws.attributes.name,
      workspaceType: ws.attributes.workspaceType,
      facilityId: ws.attributes.facilityId,
    });
    router.push("/shift");
  }

  const WORKSPACE_COLORS: Record<string, string> = {
    OPD: "bg-blue-100 text-blue-600",
    IPD: "bg-purple-100 text-purple-600",
    EMERGENCY: "bg-red-100 text-red-600",
    THEATRE: "bg-orange-100 text-orange-600",
    LABORATORY: "bg-teal-100 text-teal-600",
    PHARMACY: "bg-green-100 text-green-600",
  };

  return (
    <AppLayout>
      <PageShell
        title="Select Workspace"
        subtitle={facility ? `At ${facility.name}` : "Choose your workspace for this session"}
      >
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading workspaces...</span>
          </div>
        ) : workspaces.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <LayoutGrid className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No workspaces available at this facility</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {workspaces.map((ws) => {
              const colorClass =
                WORKSPACE_COLORS[ws.attributes.workspaceType] ?? "bg-gray-100 text-gray-600";
              return (
                <button
                  key={ws.id}
                  onClick={() => handleSelect(ws)}
                  className="bg-white rounded-lg border border-gray-200 p-5 text-left hover:border-blue-300 hover:shadow-md transition-all group"
                >
                  <div className="flex items-start gap-3">
                    <div
                      className={`w-10 h-10 rounded-lg flex items-center justify-center shrink-0 ${colorClass}`}
                    >
                      <LayoutGrid className="w-5 h-5" />
                    </div>
                    <div className="min-w-0">
                      <h3 className="font-medium text-gray-900 text-sm truncate">
                        {ws.attributes.name}
                      </h3>
                      <p className="text-xs text-gray-500 mt-0.5">
                        {ws.attributes.workspaceType}
                      </p>
                      <div className="mt-2">
                        <span
                          className={`inline-block px-2 py-0.5 text-xs rounded-full ${
                            ws.attributes.status === "ACTIVE"
                              ? "bg-green-100 text-green-700"
                              : "bg-gray-100 text-gray-600"
                          }`}
                        >
                          {ws.attributes.status}
                        </span>
                      </div>
                    </div>
                  </div>
                </button>
              );
            })}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
