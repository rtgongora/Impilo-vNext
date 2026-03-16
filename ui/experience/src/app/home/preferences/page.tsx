"use client";

/**
 * User Preferences — Language, theme, and notification settings.
 * Route: /home/preferences
 */

import { useState } from "react";
import { Loader2, Save, Globe, Palette, Bell } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useMutation } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export default function PreferencesPage() {
  const [language, setLanguage] = useState("en");
  const [theme, setTheme] = useState("light");
  const [emailNotifications, setEmailNotifications] = useState(true);
  const [pushNotifications, setPushNotifications] = useState(true);
  const [smsNotifications, setSmsNotifications] = useState(false);

  const savePreferences = useMutation({
    mutationFn: (payload: Record<string, unknown>) =>
      apiClient.put<ApiResponse<unknown>>("/internal/v1/users/preferences", payload),
  });

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    savePreferences.mutate({
      language,
      theme,
      notifications: {
        email: emailNotifications,
        push: pushNotifications,
        sms: smsNotifications,
      },
    });
  }

  return (
    <AppLayout>
      <PageShell title="Preferences" subtitle="Configure your language, theme, and notification settings">
        <form onSubmit={handleSubmit} className="max-w-xl space-y-6">
          {/* Language */}
          <div className="bg-white rounded-lg border border-gray-200 p-5">
            <h3 className="font-medium text-gray-900 mb-3 flex items-center gap-2">
              <Globe className="w-4 h-4" /> Language
            </h3>
            <select
              value={language}
              onChange={(e) => setLanguage(e.target.value)}
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="en">English</option>
              <option value="sn">Shona</option>
              <option value="nd">Ndebele</option>
              <option value="fr">French</option>
              <option value="pt">Portuguese</option>
            </select>
          </div>

          {/* Theme */}
          <div className="bg-white rounded-lg border border-gray-200 p-5">
            <h3 className="font-medium text-gray-900 mb-3 flex items-center gap-2">
              <Palette className="w-4 h-4" /> Theme
            </h3>
            <div className="flex gap-3">
              {[
                { value: "light", label: "Light" },
                { value: "dark", label: "Dark" },
                { value: "system", label: "System" },
              ].map((opt) => (
                <label
                  key={opt.value}
                  className={`flex-1 text-center p-3 rounded-lg border cursor-pointer text-sm transition-colors ${
                    theme === opt.value
                      ? "border-blue-400 bg-blue-50 text-blue-700 font-medium"
                      : "border-gray-200 text-gray-600 hover:border-gray-300"
                  }`}
                >
                  <input
                    type="radio"
                    name="theme"
                    value={opt.value}
                    checked={theme === opt.value}
                    onChange={(e) => setTheme(e.target.value)}
                    className="sr-only"
                  />
                  {opt.label}
                </label>
              ))}
            </div>
          </div>

          {/* Notifications */}
          <div className="bg-white rounded-lg border border-gray-200 p-5">
            <h3 className="font-medium text-gray-900 mb-3 flex items-center gap-2">
              <Bell className="w-4 h-4" /> Notification Preferences
            </h3>
            <div className="space-y-3">
              <label className="flex items-center justify-between cursor-pointer">
                <span className="text-sm text-gray-700">Email Notifications</span>
                <input
                  type="checkbox"
                  checked={emailNotifications}
                  onChange={(e) => setEmailNotifications(e.target.checked)}
                  className="rounded"
                />
              </label>
              <label className="flex items-center justify-between cursor-pointer">
                <span className="text-sm text-gray-700">Push Notifications</span>
                <input
                  type="checkbox"
                  checked={pushNotifications}
                  onChange={(e) => setPushNotifications(e.target.checked)}
                  className="rounded"
                />
              </label>
              <label className="flex items-center justify-between cursor-pointer">
                <span className="text-sm text-gray-700">SMS Notifications</span>
                <input
                  type="checkbox"
                  checked={smsNotifications}
                  onChange={(e) => setSmsNotifications(e.target.checked)}
                  className="rounded"
                />
              </label>
            </div>
          </div>

          {savePreferences.isSuccess && (
            <div className="p-3 rounded-lg bg-green-50 border border-green-200 text-sm text-green-700">
              Preferences saved successfully.
            </div>
          )}

          {savePreferences.isError && (
            <div className="p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700">
              Failed to save preferences. Please try again.
            </div>
          )}

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={savePreferences.isPending}
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
            >
              {savePreferences.isPending ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Save className="w-4 h-4" />
              )}
              Save Preferences
            </button>
          </div>
        </form>
      </PageShell>
    </AppLayout>
  );
}
