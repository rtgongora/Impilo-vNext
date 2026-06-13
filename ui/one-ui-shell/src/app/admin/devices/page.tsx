"use client";

/**
 * Device Management — Trusted device registry table.
 * Route: /admin/devices | pageTitle: "Device Management"
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Loader2, Smartphone, AlertCircle } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface DeviceRow {
  deviceId: string;
  deviceName: string;
  deviceType: string;
  os: string;
  trustLevel: string;
  status: string;
  lastActivity: string;
  currentDevice?: boolean;
}

type DevicesResponse = ApiResponse<DeviceRow[]>;

function useDevices() {
  return useQuery<DevicesResponse>({
    queryKey: ["admin-devices"],
    queryFn: () => apiClient.get<DevicesResponse>("/internal/v1/devices"),
  });
}

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  REVOKED: "bg-red-100 text-danger",
  PENDING: "bg-yellow-100 text-yellow-700",
};

export default function DevicesPage() {
  const searchParams = useSearchParams();
  const fromOrgAdmin = searchParams.get("from") === "organization-admin";
  const { data, isLoading, error } = useDevices();

  const devices = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Device Management"
        subtitle="Register and manage trusted devices"
      >
        <OrganizationPlaneContextBar />
        <div className="mb-4">
          <Link
            href={fromOrgAdmin ? "/organization-admin" : "/admin"}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            {fromOrgAdmin ? "Back to organization administration" : "Back to administration"}
          </Link>
        </div>

        {error ? (
          <div className="bg-card rounded-lg border border-danger/28 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load devices</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading devices...</span>
          </div>
        ) : devices.length === 0 ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <Smartphone className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">No devices registered</p>
          </div>
        ) : (
          <div className="bg-card rounded-lg border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-background">
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Device Name</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Type</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Trust Level</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Last Active</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {devices.map((device) => {
                  const statusStyle =
                    STATUS_STYLES[device.status] ?? "bg-neutral-100 text-muted-foreground";
                  return (
                    <tr key={device.deviceId} className="hover:bg-background transition-colors">
                      <td className="px-4 py-3 font-medium text-foreground">
                        {device.deviceName}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-cyan-100 text-cyan-700">
                          {device.deviceType}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {device.trustLevel}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground whitespace-nowrap">
                        {device.lastActivity ? new Date(device.lastActivity).toLocaleString() : "—"}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block px-2 py-0.5 text-xs rounded-full ${statusStyle}`}
                        >
                          {device.status}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
