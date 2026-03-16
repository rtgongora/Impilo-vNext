"use client";

/**
 * Device Management — Trusted device registry table.
 * Route: /admin/devices | pageTitle: "Device Management"
 */

import Link from "next/link";
import { ArrowLeft, Loader2, Smartphone, AlertCircle } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface DeviceResource {
  id: string;
  type: "device";
  attributes: {
    name: string;
    deviceType: string;
    registeredUser: string;
    lastActive: string;
    status: string;
    [key: string]: unknown;
  };
}

type DevicesResponse = ApiResponse<DeviceResource[]>;

function useDevices() {
  return useQuery<DevicesResponse>({
    queryKey: ["admin-devices"],
    queryFn: () => apiClient.get<DevicesResponse>("/internal/v1/admin/devices"),
  });
}

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  REVOKED: "bg-red-100 text-red-700",
  PENDING: "bg-yellow-100 text-yellow-700",
};

export default function DevicesPage() {
  const { data, isLoading, error } = useDevices();

  const devices = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Device Management"
        subtitle="Register and manage trusted devices"
      >
        <div className="mb-4">
          <Link
            href="/admin"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to administration
          </Link>
        </div>

        {error ? (
          <div className="bg-white rounded-lg border border-red-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load devices</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading devices...</span>
          </div>
        ) : devices.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Smartphone className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No devices registered</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Device Name</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Type</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Registered User</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Last Active</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {devices.map((device) => {
                  const statusStyle =
                    STATUS_STYLES[device.attributes.status] ?? "bg-gray-100 text-gray-600";
                  return (
                    <tr key={device.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {device.attributes.name}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-cyan-100 text-cyan-700">
                          {device.attributes.deviceType}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {device.attributes.registeredUser}
                      </td>
                      <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
                        {new Date(device.attributes.lastActive).toLocaleString()}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block px-2 py-0.5 text-xs rounded-full ${statusStyle}`}
                        >
                          {device.attributes.status}
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
