"use client";

/**
 * User Profile — View and edit user profile information.
 * Route: /home/profile
 */

import { useState, useEffect } from "react";
import { Loader2, UserCircle, Save } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useMutation } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export default function ProfilePage() {
  const { user, setAuth, token } = useAuthStore();

  const [displayName, setDisplayName] = useState(user?.displayName ?? "");
  const [email, setEmail] = useState(user?.email ?? "");
  const [phone, setPhone] = useState("");
  const [role, setRole] = useState(user?.roles?.[0] ?? "");
  const [qualifications, setQualifications] = useState("");

  useEffect(() => {
    if (user) {
      setDisplayName(user.displayName);
      setEmail(user.email);
      setRole(user.roles?.[0] ?? "");
    }
  }, [user]);

  const updateProfile = useMutation({
    mutationFn: (payload: Record<string, unknown>) =>
      apiClient.put<ApiResponse<{ id: string; type: string; attributes: Record<string, unknown> }>>(
        "/internal/v1/users/profile",
        payload,
      ),
    onSuccess: (res) => {
      if (user && token) {
        setAuth(
          {
            ...user,
            displayName: (res.data.attributes.displayName as string) || user.displayName,
            email: (res.data.attributes.email as string) || user.email,
          },
          token,
        );
      }
    },
  });

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    updateProfile.mutate({
      displayName,
      email,
      phone,
      qualifications,
    });
  }

  return (
    <AppLayout>
      <PageShell title="My Profile" subtitle="View and update your profile information">
        <form onSubmit={handleSubmit} className="max-w-xl space-y-6">
          <div className="bg-white rounded-lg border border-gray-200 p-5">
            <div className="flex items-center gap-4 mb-6">
              <div className="w-16 h-16 rounded-full bg-blue-100 flex items-center justify-center">
                <UserCircle className="w-10 h-10 text-blue-600" />
              </div>
              <div>
                <h3 className="font-medium text-gray-900">{user?.displayName || "User"}</h3>
                <p className="text-sm text-gray-500">{user?.actorType || "Provider"}</p>
              </div>
            </div>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Display Name</label>
                <input
                  type="text"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Phone</label>
                <input
                  type="tel"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  placeholder="+263 7X XXX XXXX"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Role</label>
                <input
                  type="text"
                  value={role}
                  readOnly
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-gray-50 text-gray-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Qualifications</label>
                <textarea
                  value={qualifications}
                  onChange={(e) => setQualifications(e.target.value)}
                  rows={3}
                  placeholder="e.g. MBChB, MMED (Internal Medicine)"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                />
              </div>
            </div>
          </div>

          {updateProfile.isSuccess && (
            <div className="p-3 rounded-lg bg-green-50 border border-green-200 text-sm text-green-700">
              Profile updated successfully.
            </div>
          )}

          {updateProfile.isError && (
            <div className="p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700">
              Failed to update profile. Please try again.
            </div>
          )}

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={updateProfile.isPending}
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
            >
              {updateProfile.isPending ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Save className="w-4 h-4" />
              )}
              Save Changes
            </button>
          </div>
        </form>
      </PageShell>
    </AppLayout>
  );
}
