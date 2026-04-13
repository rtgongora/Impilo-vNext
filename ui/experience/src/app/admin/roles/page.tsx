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
  INACTIVE: "bg-gray-100 text-gray-600",
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
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to administration
          </Link>
        </div>

        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-medium text-gray-700">
            {roles.length > 0 ? `${roles.length} role(s)` : ""}
          </h2>
          <button
            onClick={() => setShowCreate(!showCreate)}
            className="inline-flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-white bg-impilo-500 rounded-lg hover:bg-impilo-600 transition-colors"
          >
            <Plus className="w-4 h-4" />
            Create Role
          </button>
        </div>

        {showCreate && (
          <div className="mb-4 bg-white rounded-lg border border-impilo-200 p-4">
            <h3 className="text-sm font-medium text-gray-900 mb-3">Create New Role</h3>
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
                className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
              />
              <input
                name="description"
                placeholder="Description"
                className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
              />
              <div className="flex gap-2">
                <button
                  type="submit"
                  className="px-4 py-2 text-sm font-medium text-white bg-impilo-500 rounded-lg hover:bg-impilo-600 transition-colors"
                >
                  Create
                </button>
                <button
                  type="button"
                  onClick={() => setShowCreate(false)}
                  className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        )}

        {error ? (
          <div className="bg-white rounded-lg border border-red-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load roles</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading roles...</span>
          </div>
        ) : roles.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Shield className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No roles configured</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Name</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Description</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Permissions</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {roles.map((role) => {
                  const statusStyle =
                    STATUS_STYLES[role.attributes.status] ?? "bg-gray-100 text-gray-600";
                  return (
                    <tr key={role.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {role.attributes.name}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {role.attributes.description}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-impilo-100 text-impilo-600">
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
