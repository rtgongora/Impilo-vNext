"use client";

/**
 * User Detail — Individual user info, roles, audit history, status toggle.
 * Route: /admin/users/[id] | pageTitle: "User Details"
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Loader2, User, AlertCircle, Shield, Clock } from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAdminUser } from "@/hooks/queries/useAdminUsers";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface AuditEntryResource {
  id: string;
  type: "audit_entry";
  attributes: {
    action: string;
    timestamp: string;
    resourceType: string;
    details: Record<string, unknown>;
    [key: string]: unknown;
  };
}

type UserAuditResponse = ApiResponse<AuditEntryResource[]>;

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  INACTIVE: "bg-gray-100 text-gray-600",
  SUSPENDED: "bg-red-100 text-red-700",
  PENDING: "bg-yellow-100 text-yellow-700",
};

export default function UserDetailPage() {
  const params = useParams();
  const id = params.id as string;
  const queryClient = useQueryClient();

  const { data, isLoading, error } = useAdminUser(id);
  const user = data?.data;

  const { data: auditData, isLoading: auditLoading } = useQuery<UserAuditResponse>({
    queryKey: ["admin-users", id, "audit"],
    queryFn: () =>
      apiClient.get<UserAuditResponse>(`/internal/v1/admin/users/${id}/audit`),
    enabled: !!id,
  });

  const auditEntries = auditData?.data ?? [];

  const toggleStatus = useMutation({
    mutationFn: (newStatus: string) =>
      apiClient.patch(`/internal/v1/admin/users/${id}`, { status: newStatus }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-users", id] });
    },
  });

  return (
    <AppLayout>
      <PageShell
        title="User Details"
        subtitle={user ? user.attributes.displayName : "Loading user..."}
      >
        <div className="mb-4">
          <Link
            href="/admin/users"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to users
          </Link>
        </div>

        {error ? (
          <div className="bg-white rounded-lg border border-red-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load user details</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading user details...</span>
          </div>
        ) : !user ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <User className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">User not found</p>
          </div>
        ) : (
          <div className="space-y-6">
            {/* User Info Card */}
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-4">
                  <div className="w-12 h-12 rounded-full bg-blue-100 text-blue-600 flex items-center justify-center">
                    <User className="w-6 h-6" />
                  </div>
                  <div>
                    <h2 className="text-lg font-semibold text-gray-900">
                      {user.attributes.displayName}
                    </h2>
                    <p className="text-sm text-gray-500">{user.attributes.email}</p>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <span
                    className={`inline-block px-2.5 py-1 text-xs rounded-full ${
                      STATUS_STYLES[user.attributes.status] ?? "bg-gray-100 text-gray-600"
                    }`}
                  >
                    {user.attributes.status}
                  </span>
                  <button
                    onClick={() =>
                      toggleStatus.mutate(
                        user.attributes.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE"
                      )
                    }
                    disabled={toggleStatus.isPending}
                    className={`px-3 py-1.5 text-xs font-medium rounded-lg transition-colors ${
                      user.attributes.status === "ACTIVE"
                        ? "bg-red-50 text-red-700 hover:bg-red-100"
                        : "bg-green-50 text-green-700 hover:bg-green-100"
                    } disabled:opacity-50`}
                  >
                    {user.attributes.status === "ACTIVE" ? "Suspend" : "Activate"}
                  </button>
                </div>
              </div>
            </div>

            {/* User Details Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center gap-2 mb-3">
                  <Shield className="w-4 h-4 text-indigo-500" />
                  <h3 className="text-sm font-medium text-gray-900">Role</h3>
                </div>
                <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-blue-100 text-blue-700">
                  {user.attributes.role}
                </span>
              </div>
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center gap-2 mb-3">
                  <Clock className="w-4 h-4 text-gray-500" />
                  <h3 className="text-sm font-medium text-gray-900">User ID</h3>
                </div>
                <p className="text-sm text-gray-600 font-mono">{user.id}</p>
              </div>
            </div>

            {/* Audit History */}
            <div className="bg-white rounded-lg border border-gray-200">
              <div className="px-5 py-4 border-b">
                <h3 className="text-sm font-medium text-gray-900">Audit History</h3>
              </div>
              {auditLoading ? (
                <div className="flex items-center justify-center py-8">
                  <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
                  <span className="ml-2 text-sm text-gray-500">Loading audit history...</span>
                </div>
              ) : auditEntries.length === 0 ? (
                <div className="p-8 text-center">
                  <p className="text-gray-400 text-sm">No audit entries for this user</p>
                </div>
              ) : (
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b bg-gray-50">
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Timestamp</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Action</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Resource</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {auditEntries.map((entry) => (
                      <tr key={entry.id} className="hover:bg-gray-50 transition-colors">
                        <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
                          {new Date(entry.attributes.timestamp).toLocaleString()}
                        </td>
                        <td className="px-4 py-3">
                          <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-blue-100 text-blue-700">
                            {entry.attributes.action}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-gray-600">
                          {entry.attributes.resourceType}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
