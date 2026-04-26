"use client";

/**
 * Integration Settings — Connected services list with status and disconnect.
 * Route: /settings/integrations | pageTitle: "Integrations"
 */

import Link from "next/link";
import {
  ArrowLeft,
  Loader2,
  AlertCircle,
  Plug,
  Unplug,
  CheckCircle,
  XCircle,
} from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface IntegrationResource {
  id: string;
  type: "integration";
  attributes: {
    name: string;
    description: string;
    status: string;
    connectedAt: string;
    provider: string;
    [key: string]: unknown;
  };
}

type IntegrationsResponse = ApiResponse<IntegrationResource[]>;

function useIntegrations() {
  return useQuery<IntegrationsResponse>({
    queryKey: ["settings-integrations"],
    queryFn: () =>
      apiClient.get<IntegrationsResponse>("/internal/v1/settings/integrations"),
  });
}

const STATUS_STYLES: Record<string, string> = {
  CONNECTED: "bg-green-100 text-green-700",
  DISCONNECTED: "bg-gray-100 text-gray-600",
  ERROR: "bg-red-100 text-red-700",
};

const STATUS_ICONS: Record<string, typeof CheckCircle> = {
  CONNECTED: CheckCircle,
  DISCONNECTED: XCircle,
  ERROR: AlertCircle,
};

export default function IntegrationsPage() {
  const queryClient = useQueryClient();
  const { data, isLoading, error } = useIntegrations();

  const integrations = data?.data ?? [];

  const disconnectIntegration = useMutation({
    mutationFn: (integrationId: string) =>
      apiClient.delete(`/internal/v1/settings/integrations/${integrationId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["settings-integrations"] });
    },
  });

  return (
    <AppLayout>
      <PageShell
        title="Integrations"
        subtitle="Manage connected services and API integrations"
      >
        <div className="mb-4">
          <Link
            href="/settings"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to settings
          </Link>
        </div>

        {error ? (
          <div className="bg-white rounded-lg border border-red-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load integrations</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading integrations...</span>
          </div>
        ) : integrations.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Plug className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No integrations configured</p>
          </div>
        ) : (
          <div className="space-y-4 max-w-3xl">
            {integrations.map((integration) => {
              const statusStyle =
                STATUS_STYLES[integration.attributes.status] ?? "bg-gray-100 text-gray-600";
              const StatusIcon =
                STATUS_ICONS[integration.attributes.status] ?? AlertCircle;
              const isConnected = integration.attributes.status === "CONNECTED";

              return (
                <div
                  key={integration.id}
                  className="bg-white rounded-lg border border-gray-200 p-5"
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-4">
                      <div className="w-10 h-10 rounded-lg bg-gray-100 text-gray-500 flex items-center justify-center">
                        <Plug className="w-5 h-5" />
                      </div>
                      <div>
                        <h3 className="text-sm font-medium text-gray-900">
                          {integration.attributes.name}
                        </h3>
                        <p className="text-xs text-gray-500 mt-0.5">
                          {integration.attributes.description}
                        </p>
                        <div className="flex items-center gap-2 mt-1.5">
                          <span
                            className={`inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded-full ${statusStyle}`}
                          >
                            <StatusIcon className="w-3 h-3" />
                            {integration.attributes.status}
                          </span>
                          {isConnected && integration.attributes.connectedAt && (
                            <span className="text-xs text-gray-400">
                              Connected{" "}
                              {new Date(integration.attributes.connectedAt).toLocaleDateString()}
                            </span>
                          )}
                          <span className="text-xs text-gray-400">
                            {integration.attributes.provider}
                          </span>
                        </div>
                      </div>
                    </div>
                    {isConnected && (
                      <button
                        onClick={() => disconnectIntegration.mutate(integration.id)}
                        disabled={disconnectIntegration.isPending}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-red-700 bg-red-50 rounded-lg hover:bg-red-100 transition-colors disabled:opacity-50"
                      >
                        <Unplug className="w-3.5 h-3.5" />
                        Disconnect
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
