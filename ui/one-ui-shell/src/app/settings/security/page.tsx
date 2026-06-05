"use client";

/**
 * Security Settings — Password change, MFA toggle, active sessions.
 * Route: /settings/security | pageTitle: "Security Settings"
 */

import { useState } from "react";
import Link from "next/link";
import {
  ArrowLeft,
  Loader2,
  AlertCircle,
  CheckCircle,
  ShieldCheck,
  Smartphone,
  Monitor,
  LogOut,
} from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { TrustGovernanceStrip } from "@/components/trust/TrustGovernanceStrip";

interface SessionResource {
  id: string;
  device: string;
  ipAddress: string;
  lastActive: string;
  current: boolean;
}

interface SecurityResource {
  id: string;
  type: "security_settings";
  attributes: {
    mfaEnabled: boolean;
    mfaMethod: string;
    sessions: SessionResource[];
    [key: string]: unknown;
  };
}

type SecurityResponse = ApiResponse<SecurityResource>;

function useSecuritySettings() {
  return useQuery<SecurityResponse>({
    queryKey: ["settings-security"],
    queryFn: () => apiClient.get<SecurityResponse>("/internal/v1/settings/security"),
  });
}

export default function SecuritySettingsPage() {
  const queryClient = useQueryClient();
  const { data, isLoading, error } = useSecuritySettings();
  const security = data?.data;

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const changePassword = useMutation({
    mutationFn: (payload: { currentPassword: string; newPassword: string }) =>
      apiClient.post("/internal/v1/settings/security/password", payload),
    onSuccess: () => {
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
    },
  });

  const toggleMfa = useMutation({
    mutationFn: (enabled: boolean) =>
      apiClient.put("/internal/v1/settings/security/mfa", { enabled }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["settings-security"] });
    },
  });

  const revokeSession = useMutation({
    mutationFn: (sessionId: string) =>
      apiClient.delete(`/internal/v1/settings/security/sessions/${sessionId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["settings-security"] });
    },
  });

  const handlePasswordSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (newPassword !== confirmPassword) return;
    changePassword.mutate({ currentPassword, newPassword });
  };

  return (
    <AppLayout>
      <PageShell
        title="Security Settings"
        subtitle="Manage your password, MFA, and active sessions"
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
            <p className="text-red-600 text-sm">Failed to load security settings</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading security settings...</span>
          </div>
        ) : (
          <div className="space-y-6 max-w-2xl">
            {/* Change Password */}
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <h3 className="text-sm font-medium text-gray-900 mb-4">Change Password</h3>

              {changePassword.isSuccess && (
                <div className="mb-4 flex items-center gap-2 px-4 py-3 bg-green-50 border border-green-200 rounded-lg">
                  <CheckCircle className="w-4 h-4 text-green-600" />
                  <p className="text-sm text-green-700">Password changed successfully</p>
                </div>
              )}

              {changePassword.isError && (
                <div className="mb-4 flex items-center gap-2 px-4 py-3 bg-red-50 border border-red-200 rounded-lg">
                  <AlertCircle className="w-4 h-4 text-red-600" />
                  <p className="text-sm text-red-700">Failed to change password</p>
                </div>
              )}

              <form onSubmit={handlePasswordSubmit} className="space-y-4">
                <div>
                  <label htmlFor="currentPassword" className="block text-sm font-medium text-gray-700 mb-1">
                    Current Password
                  </label>
                  <input
                    id="currentPassword"
                    type="password"
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    className="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 focus:border-impilo-400"
                    required
                  />
                </div>
                <div>
                  <label htmlFor="newPassword" className="block text-sm font-medium text-gray-700 mb-1">
                    New Password
                  </label>
                  <input
                    id="newPassword"
                    type="password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    className="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 focus:border-impilo-400"
                    required
                  />
                </div>
                <div>
                  <label htmlFor="confirmPassword" className="block text-sm font-medium text-gray-700 mb-1">
                    Confirm New Password
                  </label>
                  <input
                    id="confirmPassword"
                    type="password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    className="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 focus:border-impilo-400"
                    required
                  />
                  {confirmPassword && newPassword !== confirmPassword && (
                    <p className="text-xs text-red-600 mt-1">Passwords do not match</p>
                  )}
                </div>
                <button
                  type="submit"
                  disabled={changePassword.isPending || newPassword !== confirmPassword}
                  className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-medium text-white bg-impilo-500 rounded-lg hover:bg-impilo-600 transition-colors disabled:opacity-50"
                >
                  {changePassword.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
                  Change Password
                </button>
              </form>
            </div>

            {/* MFA Toggle */}
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-lg bg-indigo-100 text-indigo-600 flex items-center justify-center">
                    <ShieldCheck className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="text-sm font-medium text-gray-900">
                      Multi-Factor Authentication
                    </h3>
                    <p className="text-xs text-gray-500 mt-0.5">
                      {security?.attributes.mfaEnabled
                        ? `Enabled via ${security.attributes.mfaMethod}`
                        : "Add an extra layer of security to your account"}
                    </p>
                  </div>
                </div>
                <button
                  onClick={() => toggleMfa.mutate(!security?.attributes.mfaEnabled)}
                  disabled={toggleMfa.isPending}
                  className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                    security?.attributes.mfaEnabled ? "bg-impilo-500" : "bg-gray-300"
                  } disabled:opacity-50`}
                >
                  <span
                    className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                      security?.attributes.mfaEnabled ? "translate-x-6" : "translate-x-1"
                    }`}
                  />
                </button>
              </div>
            </div>

            {/* Active Sessions */}
            <div className="bg-white rounded-lg border border-gray-200">
              <div className="px-5 py-4 border-b">
                <h3 className="text-sm font-medium text-gray-900">Active Sessions</h3>
              </div>
              {!security?.attributes.sessions || security.attributes.sessions.length === 0 ? (
                <div className="p-8 text-center">
                  <p className="text-gray-400 text-sm">No active sessions</p>
                </div>
              ) : (
                <div className="divide-y divide-gray-100">
                  {security.attributes.sessions.map((session) => (
                    <div
                      key={session.id}
                      className="px-5 py-4 flex items-center justify-between"
                    >
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 rounded-lg bg-gray-100 text-gray-500 flex items-center justify-center">
                          {session.device.toLowerCase().includes("mobile") ? (
                            <Smartphone className="w-4 h-4" />
                          ) : (
                            <Monitor className="w-4 h-4" />
                          )}
                        </div>
                        <div>
                          <p className="text-sm text-gray-900">
                            {session.device}
                            {session.current && (
                              <span className="ml-2 inline-block px-1.5 py-0.5 text-xs rounded bg-green-100 text-green-700">
                                Current
                              </span>
                            )}
                          </p>
                          <p className="text-xs text-gray-500">
                            {session.ipAddress} &middot; Last active{" "}
                            {new Date(session.lastActive).toLocaleString()}
                          </p>
                        </div>
                      </div>
                      {!session.current && (
                        <button
                          onClick={() => revokeSession.mutate(session.id)}
                          disabled={revokeSession.isPending}
                          className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-red-700 bg-red-50 rounded-lg hover:bg-red-100 transition-colors disabled:opacity-50"
                        >
                          <LogOut className="w-3 h-3" />
                          Revoke
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>

            <TrustGovernanceStrip />
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
