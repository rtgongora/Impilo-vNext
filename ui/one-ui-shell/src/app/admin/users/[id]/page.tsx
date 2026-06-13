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
  INACTIVE: "bg-neutral-100 text-muted-foreground",
  SUSPENDED: "bg-red-100 text-danger",
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
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to users
          </Link>
        </div>

        {error ? (
          <div className="bg-card rounded-lg border border-danger/28 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load user details</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading user details...</span>
          </div>
        ) : !user ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <User className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">User not found</p>
          </div>
        ) : (
          <div className="space-y-6">
            {/* User Info Card */}
            <div className="bg-card rounded-lg border border-border p-6">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-4">
                  <div className="w-12 h-12 rounded-full bg-primary-soft text-primary flex items-center justify-center">
                    <User className="w-6 h-6" />
                  </div>
                  <div>
                    <h2 className="text-lg font-semibold text-foreground">
                      {user.attributes.displayName}
                    </h2>
                    <p className="text-sm text-muted-foreground">{user.attributes.email}</p>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <span
                    className={`inline-block px-2.5 py-1 text-xs rounded-full ${
                      STATUS_STYLES[user.attributes.status] ?? "bg-neutral-100 text-muted-foreground"
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
                        ? "bg-danger-soft text-danger hover:bg-red-100"
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
              <div className="bg-card rounded-lg border border-border p-5">
                <div className="flex items-center gap-2 mb-3">
                  <Shield className="w-4 h-4 text-indigo-500" />
                  <h3 className="text-sm font-medium text-foreground">Role</h3>
                </div>
                <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-primary-soft text-primary">
                  {user.attributes.role}
                </span>
              </div>
              <div className="bg-card rounded-lg border border-border p-5">
                <div className="flex items-center gap-2 mb-3">
                  <Clock className="w-4 h-4 text-muted-foreground" />
                  <h3 className="text-sm font-medium text-foreground">User ID</h3>
                </div>
                <p className="text-sm text-muted-foreground font-mono">{user.id}</p>
              </div>
            </div>

            {/* Audit History */}
            <div className="bg-card rounded-lg border border-border">
              <div className="px-5 py-4 border-b">
                <h3 className="text-sm font-medium text-foreground">Audit History</h3>
              </div>
              {auditLoading ? (
                <div className="flex items-center justify-center py-8">
                  <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
                  <span className="ml-2 text-sm text-muted-foreground">Loading audit history...</span>
                </div>
              ) : auditEntries.length === 0 ? (
                <div className="p-8 text-center">
                  <p className="text-muted-foreground text-sm">No audit entries for this user</p>
                </div>
              ) : (
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b bg-background">
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Timestamp</th>
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Action</th>
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Resource</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {auditEntries.map((entry) => (
                      <tr key={entry.id} className="hover:bg-background transition-colors">
                        <td className="px-4 py-3 text-muted-foreground whitespace-nowrap">
                          {new Date(entry.attributes.timestamp).toLocaleString()}
                        </td>
                        <td className="px-4 py-3">
                          <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-primary-soft text-primary">
                            {entry.attributes.action}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">
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
