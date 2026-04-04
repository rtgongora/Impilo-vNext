"use client";

/**
 * User Management — Admin users list with create user form.
 * Route: /admin/users | pageTitle: "User Management"
 */

import { useState } from "react";
import Link from "next/link";
import {
  Search,
  Loader2,
  Users,
  Plus,
  UserPlus,
  Save,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAdminUsers } from "@/hooks/queries/useAdminUsers";
import { apiClient } from "@/lib/api-client";
import { useQueryClient } from "@tanstack/react-query";

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  INACTIVE: "bg-gray-100 text-gray-600",
  SUSPENDED: "bg-red-100 text-red-700",
  PENDING: "bg-yellow-100 text-yellow-700",
};

const ROLE_STYLES: Record<string, string> = {
  SYSTEM_ADMIN: "bg-red-100 text-red-700",
  FACILITY_ADMIN: "bg-orange-100 text-orange-700",
  CLINICIAN: "bg-blue-100 text-blue-700",
  NURSE: "bg-teal-100 text-teal-700",
  PHARMACIST: "bg-green-100 text-green-700",
  CITIZEN: "bg-purple-100 text-purple-700",
  SUPPORT_AGENT: "bg-amber-100 text-amber-700",
  DEVELOPER: "bg-gray-100 text-gray-700",
};

const ALL_ROLES = [
  "SYSTEM_ADMIN", "FACILITY_ADMIN", "CLINICIAN", "NURSE",
  "PHARMACIST", "CITIZEN", "SUPPORT_AGENT", "DEVELOPER",
];

export default function AdminUsersPage() {
  const [search, setSearch] = useState("");
  const { data, isLoading } = useAdminUsers(search ? { search } : undefined);
  const queryClient = useQueryClient();

  const users = data?.data ?? [];

  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({
    email: "", firstName: "", lastName: "", password: "", role: "CLINICIAN", facilityId: "",
  });
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setCreating(true);
    setCreateError(null);
    try {
      await apiClient.post("/internal/v1/admin/users", {
        email: form.email.trim(),
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        password: form.password,
        role: form.role,
        facilityId: form.facilityId || null,
      });
      queryClient.invalidateQueries({ queryKey: ["admin-users"] });
      setForm({ email: "", firstName: "", lastName: "", password: "", role: "CLINICIAN", facilityId: "" });
      setShowCreate(false);
    } catch (err: unknown) {
      const apiErr = err as { error?: { message?: string; code?: string } };
      setCreateError(apiErr?.error?.message ?? "Failed to create user");
    } finally {
      setCreating(false);
    }
  }

  return (
    <AppLayout>
      <PageShell title="User Management" subtitle="Create and manage platform user accounts">

        <div className="flex items-center justify-between mb-5">
          <div className="relative max-w-md flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input type="text" value={search} onChange={(e) => setSearch(e.target.value)}
              placeholder="Search users by name, email, or role..."
              className="w-full pl-10 pr-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <button onClick={() => setShowCreate((v) => !v)}
            className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors ml-4">
            <Plus className="w-4 h-4" /> Create User
          </button>
        </div>

        {showCreate && (
          <form onSubmit={handleCreate} className="bg-white rounded-lg border border-gray-200 p-5 mb-5 space-y-4">
            <h3 className="text-sm font-medium text-gray-900 flex items-center gap-2">
              <UserPlus className="w-4 h-4 text-blue-500" /> Create Platform User
            </h3>
            {createError && (
              <div className="p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700">{createError}</div>
            )}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">First Name *</label>
                <input type="text" required value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Last Name *</label>
                <input type="text" required value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Email *</label>
                <input type="email" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Password *</label>
                <input type="password" required minLength={8} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })}
                  placeholder="Min 8 characters"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Role *</label>
                <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  {ALL_ROLES.map((r) => <option key={r} value={r}>{r.replace(/_/g, " ")}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Facility ID (optional)</label>
                <input type="text" value={form.facilityId} onChange={(e) => setForm({ ...form, facilityId: e.target.value })}
                  placeholder="UUID or leave empty"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
            </div>
            <div className="flex gap-3">
              <button type="button" onClick={() => setShowCreate(false)}
                className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-200 transition-colors">Cancel</button>
              <button type="submit" disabled={creating}
                className="flex-1 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors">
                {creating ? <><Loader2 className="w-4 h-4 animate-spin" /> Creating...</> : <><Save className="w-4 h-4" /> Create User</>}
              </button>
            </div>
          </form>
        )}

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
          </div>
        ) : users.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Users className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">{search ? "No users match your search" : "No users found"}</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Name</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Email</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Role</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Created</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {users.map((user) => {
                  const a = user.attributes;
                  const roleStyle = ROLE_STYLES[a.role] ?? "bg-gray-100 text-gray-600";
                  const statusStyle = STATUS_STYLES[a.status] ?? "bg-gray-100 text-gray-600";
                  return (
                    <tr key={user.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3">
                        <Link href={`/admin/users/${user.id}`} className="font-medium text-blue-600 hover:text-blue-800">
                          {a.displayName || (a as Record<string, unknown>).display_name as string || user.id}
                        </Link>
                      </td>
                      <td className="px-4 py-3 text-gray-600">{a.email}</td>
                      <td className="px-4 py-3">
                        <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${roleStyle}`}>
                          {(a.role || "").replace(/_/g, " ")}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${statusStyle}`}>
                          {a.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-500 text-xs">
                        {(a as Record<string, unknown>).created_at ? new Date(String((a as Record<string, unknown>).created_at)).toLocaleDateString() : "—"}
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
