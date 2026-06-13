"use client";

/**
 * Role Management — RBAC role definitions table with create action.
 * Route: /admin/roles | pageTitle: "Role Management"
 */

import { useState } from "react";
import Link from "next/link";
import { ArrowLeft, Loader2, Shield, Plus, AlertCircle } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface RoleResource {
  id: string;
  type: "role";
  attributes: {
    name: string;
    description: string;
    permissionCount: number;
    status: string;
    [key: string]: unknown;
  };
}

type RolesResponse = ApiResponse<RoleResource[]>;

function useRoles() {
  return useQuery<RolesResponse>({
    queryKey: ["admin-roles"],
    queryFn: () => apiClient.get<RolesResponse>("/internal/v1/admin/roles"),
  });
}

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  INACTIVE: "bg-neutral-100 text-muted-foreground",
  DEPRECATED: "bg-yellow-100 text-yellow-700",
};

export default function RolesPage() {
  const { data, isLoading, error } = useRoles();
  const [showCreate, setShowCreate] = useState(false);

  const roles = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Role Management"
        subtitle="Define and manage RBAC role definitions"
      >
        <div className="mb-4">
          <Link
            href="/admin"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to administration
          </Link>
        </div>

        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-medium text-foreground">
            {roles.length > 0 ? `${roles.length} role(s)` : ""}
          </h2>
          <button
            onClick={() => setShowCreate(!showCreate)}
            className="inline-flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-white bg-primary rounded-lg hover:bg-primary-hover transition-colors"
          >
            <Plus className="w-4 h-4" />
            Create Role
          </button>
        </div>

        {showCreate && (
          <div className="mb-4 bg-card rounded-lg border border-primary/25 p-4">
            <h3 className="text-sm font-medium text-foreground mb-3">Create New Role</h3>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                const formData = new FormData(e.currentTarget);
                apiClient.post("/internal/v1/admin/roles", {
                  name: formData.get("name"),
                  description: formData.get("description"),
                });
                setShowCreate(false);
              }}
              className="flex flex-col gap-3"
            >
              <input
                name="name"
                placeholder="Role name"
                required
                className="px-3 py-2 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
              />
              <input
                name="description"
                placeholder="Description"
                className="px-3 py-2 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
              />
              <div className="flex gap-2">
                <button
                  type="submit"
                  className="px-4 py-2 text-sm font-medium text-white bg-primary rounded-lg hover:bg-primary-hover transition-colors"
                >
                  Create
                </button>
                <button
                  type="button"
                  onClick={() => setShowCreate(false)}
                  className="px-4 py-2 text-sm font-medium text-foreground bg-neutral-100 rounded-lg hover:bg-neutral-100 transition-colors"
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        )}

        {error ? (
          <div className="bg-card rounded-lg border border-danger/28 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load roles</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading roles...</span>
          </div>
        ) : roles.length === 0 ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <Shield className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">No roles configured</p>
          </div>
        ) : (
          <div className="bg-card rounded-lg border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-background">
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Name</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Description</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Permissions</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {roles.map((role) => {
                  const statusStyle =
                    STATUS_STYLES[role.attributes.status] ?? "bg-neutral-100 text-muted-foreground";
                  return (
                    <tr key={role.id} className="hover:bg-background transition-colors">
                      <td className="px-4 py-3 font-medium text-foreground">
                        {role.attributes.name}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {role.attributes.description}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-primary-soft text-primary">
                          {role.attributes.permissionCount} permissions
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block px-2 py-0.5 text-xs rounded-full ${statusStyle}`}
                        >
                          {role.attributes.status}
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
