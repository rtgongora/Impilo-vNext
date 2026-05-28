"use client";

/**
 * Key Management — Cryptographic keys and certificates table.
 * Route: /admin/keys | pageTitle: "Key Management"
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Loader2, Key, AlertCircle } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { RegistryPlaneContextBar } from "@/components/experience/RegistryPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import type { ApiResponse } from "@/lib/api-client";

interface KeyResource {
  id: string;
  type: "key";
  attributes: {
    keyId: string;
    algorithm: string;
    purpose: string;
    createdDate: string;
    expiry: string;
    status: string;
    [key: string]: unknown;
  };
}

type KeysResponse = ApiResponse<KeyResource[]>;

function useKeys() {
  return useQuery<KeysResponse>({
    queryKey: ["admin-keys"],
    queryFn: () => Promise.reject(new Error("Typed key-management BFF unavailable")),
  });
}

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  EXPIRED: "bg-red-100 text-red-700",
  REVOKED: "bg-gray-100 text-gray-600",
  PENDING_ACTIVATION: "bg-yellow-100 text-yellow-700",
};

export default function KeysPage() {
  const searchParams = useSearchParams();
  const fromRegistryAdmin = searchParams.get("from") === "registry-admin";
  const { data, isLoading, error } = useKeys();

  const keys = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Key Management"
        subtitle="Manage cryptographic keys and certificates"
      >
        <RegistryPlaneContextBar />
        <div className="mb-4">
          <Link
            href={fromRegistryAdmin ? "/registry/trust" : "/admin"}
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            {fromRegistryAdmin ? "Back to trust hub" : "Back to administration"}
          </Link>
        </div>

        {error ? (
          <div className="bg-white rounded-lg border border-red-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load keys</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading keys...</span>
          </div>
        ) : keys.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Key className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No keys configured</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Key ID</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Algorithm</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Purpose</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Created</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Expiry</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {keys.map((k) => {
                  const statusStyle =
                    STATUS_STYLES[k.attributes.status] ?? "bg-gray-100 text-gray-600";
                  return (
                    <tr key={k.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 font-mono text-xs text-gray-900">
                        {k.attributes.keyId}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-yellow-100 text-yellow-700">
                          {k.attributes.algorithm}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {k.attributes.purpose}
                      </td>
                      <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
                        {new Date(k.attributes.createdDate).toLocaleDateString()}
                      </td>
                      <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
                        {new Date(k.attributes.expiry).toLocaleDateString()}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block px-2 py-0.5 text-xs rounded-full ${statusStyle}`}
                        >
                          {k.attributes.status}
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
