"use client";

/**
 * Account Settings — Profile form with display name, email, phone, language.
 * Route: /settings/account | pageTitle: "Account Settings"
 */

import { useState } from "react";
import Link from "next/link";
import { ArrowLeft, Loader2, Save, AlertCircle, CheckCircle } from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface AccountResource {
  id: string;
  type: "account";
  attributes: {
    displayName: string;
    email: string;
    phone: string;
    language: string;
    [key: string]: unknown;
  };
}

type AccountResponse = ApiResponse<AccountResource>;

function useAccount() {
  return useQuery<AccountResponse>({
    queryKey: ["settings-account"],
    queryFn: () => apiClient.get<AccountResponse>("/internal/v1/settings/account"),
  });
}

const LANGUAGES = [
  { value: "en", label: "English" },
  { value: "sn", label: "Shona" },
  { value: "nd", label: "Ndebele" },
  { value: "fr", label: "French" },
  { value: "pt", label: "Portuguese" },
];

export default function AccountSettingsPage() {
  const queryClient = useQueryClient();
  const { data, isLoading, error } = useAccount();
  const account = data?.data;

  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [language, setLanguage] = useState("en");
  const [initialized, setInitialized] = useState(false);

  if (account && !initialized) {
    setDisplayName(account.attributes.displayName);
    setEmail(account.attributes.email);
    setPhone(account.attributes.phone);
    setLanguage(account.attributes.language);
    setInitialized(true);
  }

  const saveAccount = useMutation({
    mutationFn: (payload: { displayName: string; email: string; phone: string; language: string }) =>
      apiClient.put("/internal/v1/settings/account", payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["settings-account"] });
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    saveAccount.mutate({ displayName, email, phone, language });
  };

  return (
    <AppLayout>
      <PageShell
        title="Account Settings"
        subtitle="Manage your profile and contact information"
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
            <p className="text-red-600 text-sm">Failed to load account settings</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading account settings...</span>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 p-6 max-w-2xl">
            {saveAccount.isSuccess && (
              <div className="mb-4 flex items-center gap-2 px-4 py-3 bg-green-50 border border-green-200 rounded-lg">
                <CheckCircle className="w-4 h-4 text-green-600" />
                <p className="text-sm text-green-700">Account settings saved successfully</p>
              </div>
            )}

            {saveAccount.isError && (
              <div className="mb-4 flex items-center gap-2 px-4 py-3 bg-red-50 border border-red-200 rounded-lg">
                <AlertCircle className="w-4 h-4 text-red-600" />
                <p className="text-sm text-red-700">Failed to save account settings</p>
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-5">
              <div>
                <label htmlFor="displayName" className="block text-sm font-medium text-gray-700 mb-1">
                  Display Name
                </label>
                <input
                  id="displayName"
                  type="text"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  className="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 focus:border-impilo-400"
                  required
                />
              </div>

              <div>
                <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-1">
                  Email Address
                </label>
                <input
                  id="email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 focus:border-impilo-400"
                  required
                />
              </div>

              <div>
                <label htmlFor="phone" className="block text-sm font-medium text-gray-700 mb-1">
                  Phone Number
                </label>
                <input
                  id="phone"
                  type="tel"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  className="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 focus:border-impilo-400"
                />
              </div>

              <div>
                <label htmlFor="language" className="block text-sm font-medium text-gray-700 mb-1">
                  Language
                </label>
                <select
                  id="language"
                  value={language}
                  onChange={(e) => setLanguage(e.target.value)}
                  className="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 focus:border-impilo-400 bg-white"
                >
                  {LANGUAGES.map((lang) => (
                    <option key={lang.value} value={lang.value}>
                      {lang.label}
                    </option>
                  ))}
                </select>
              </div>

              <div className="pt-2">
                <button
                  type="submit"
                  disabled={saveAccount.isPending}
                  className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-medium text-white bg-impilo-500 rounded-lg hover:bg-impilo-600 transition-colors disabled:opacity-50"
                >
                  {saveAccount.isPending ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <Save className="w-4 h-4" />
                  )}
                  Save Changes
                </button>
              </div>
            </form>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
