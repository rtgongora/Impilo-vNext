"use client";

/**
 * Notification Preferences — Toggle switches for email, SMS, push per category.
 * Route: /settings/notifications | pageTitle: "Notification Preferences"
 */

import { useState } from "react";
import Link from "next/link";
import {
  ArrowLeft,
  Loader2,
  AlertCircle,
  CheckCircle,
  Save,
  Mail,
  MessageSquare,
  Bell,
} from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface NotificationCategory {
  key: string;
  label: string;
  description: string;
  email: boolean;
  sms: boolean;
  push: boolean;
}

interface NotificationResource {
  id: string;
  type: "notification_settings";
  attributes: {
    categories: NotificationCategory[];
    [key: string]: unknown;
  };
}

type NotificationResponse = ApiResponse<NotificationResource>;

function useNotificationSettings() {
  return useQuery<NotificationResponse>({
    queryKey: ["settings-notifications"],
    queryFn: () =>
      apiClient.get<NotificationResponse>("/internal/v1/settings/notifications"),
  });
}

const DEFAULT_CATEGORIES: NotificationCategory[] = [
  {
    key: "security",
    label: "Security Alerts",
    description: "Login attempts, password changes, and suspicious activity",
    email: true,
    sms: true,
    push: true,
  },
  {
    key: "clinical",
    label: "Clinical Updates",
    description: "Lab results, appointment reminders, and care plan changes",
    email: true,
    sms: false,
    push: true,
  },
  {
    key: "billing",
    label: "Billing & Payments",
    description: "Invoice notifications, payment confirmations, and overdue alerts",
    email: true,
    sms: false,
    push: false,
  },
  {
    key: "system",
    label: "System Notifications",
    description: "Maintenance windows, feature updates, and system alerts",
    email: true,
    sms: false,
    push: true,
  },
  {
    key: "audit",
    label: "Audit & Compliance",
    description: "Compliance reports, audit findings, and policy changes",
    email: true,
    sms: false,
    push: false,
  },
];

const CHANNEL_ICONS = {
  email: Mail,
  sms: MessageSquare,
  push: Bell,
};

export default function NotificationSettingsPage() {
  const queryClient = useQueryClient();
  const { data, isLoading, error } = useNotificationSettings();

  const [categories, setCategories] = useState<NotificationCategory[]>(DEFAULT_CATEGORIES);
  const [initialized, setInitialized] = useState(false);

  if (data?.data && !initialized) {
    setCategories(data.data.attributes.categories);
    setInitialized(true);
  }

  const saveNotifications = useMutation({
    mutationFn: (payload: { categories: NotificationCategory[] }) =>
      apiClient.put("/internal/v1/settings/notifications", payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["settings-notifications"] });
    },
  });

  const toggleChannel = (categoryKey: string, channel: "email" | "sms" | "push") => {
    setCategories((prev) =>
      prev.map((cat) =>
        cat.key === categoryKey ? { ...cat, [channel]: !cat[channel] } : cat
      )
    );
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    saveNotifications.mutate({ categories });
  };

  return (
    <AppLayout>
      <PageShell
        title="Notification Preferences"
        subtitle="Configure how and when you receive notifications"
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
            <p className="text-red-600 text-sm">Failed to load notification settings</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading notification settings...</span>
          </div>
        ) : (
          <div className="max-w-3xl">
            {saveNotifications.isSuccess && (
              <div className="mb-4 flex items-center gap-2 px-4 py-3 bg-green-50 border border-green-200 rounded-lg">
                <CheckCircle className="w-4 h-4 text-green-600" />
                <p className="text-sm text-green-700">
                  Notification preferences saved successfully
                </p>
              </div>
            )}

            <form onSubmit={handleSubmit}>
              {/* Channel legend */}
              <div className="bg-white rounded-lg border border-gray-200 mb-4">
                <div className="px-5 py-3 border-b bg-gray-50 flex items-center justify-between">
                  <span className="text-sm font-medium text-gray-600">Category</span>
                  <div className="flex gap-8">
                    {(["email", "sms", "push"] as const).map((channel) => {
                      const Icon = CHANNEL_ICONS[channel];
                      return (
                        <div key={channel} className="flex items-center gap-1.5 w-16 justify-center">
                          <Icon className="w-3.5 h-3.5 text-gray-500" />
                          <span className="text-xs font-medium text-gray-500 uppercase">
                            {channel}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                </div>

                <div className="divide-y divide-gray-100">
                  {categories.map((category) => (
                    <div
                      key={category.key}
                      className="px-5 py-4 flex items-center justify-between"
                    >
                      <div>
                        <p className="text-sm font-medium text-gray-900">{category.label}</p>
                        <p className="text-xs text-gray-500 mt-0.5">{category.description}</p>
                      </div>
                      <div className="flex gap-8">
                        {(["email", "sms", "push"] as const).map((channel) => (
                          <div key={channel} className="w-16 flex justify-center">
                            <button
                              type="button"
                              onClick={() => toggleChannel(category.key, channel)}
                              className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
                                category[channel] ? "bg-impilo-500" : "bg-gray-300"
                              }`}
                            >
                              <span
                                className={`inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform ${
                                  category[channel] ? "translate-x-4.5" : "translate-x-0.5"
                                }`}
                              />
                            </button>
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              <button
                type="submit"
                disabled={saveNotifications.isPending}
                className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-medium text-white bg-impilo-500 rounded-lg hover:bg-impilo-600 transition-colors disabled:opacity-50"
              >
                {saveNotifications.isPending ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Save className="w-4 h-4" />
                )}
                Save Preferences
              </button>
            </form>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
