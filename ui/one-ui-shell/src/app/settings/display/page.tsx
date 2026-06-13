"use client";

/**
 * Display Settings — Theme, font size, and density preferences.
 * Route: /settings/display | pageTitle: "Display Settings"
 */

import { useState } from "react";
import Link from "next/link";
import {
  ArrowLeft,
  Loader2,
  AlertCircle,
  CheckCircle,
  Save,
  Sun,
  Moon,
  Monitor,
} from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface DisplayResource {
  id: string;
  type: "display_settings";
  attributes: {
    theme: string;
    fontSize: string;
    density: string;
    [key: string]: unknown;
  };
}

type DisplayResponse = ApiResponse<DisplayResource>;

function useDisplaySettings() {
  return useQuery<DisplayResponse>({
    queryKey: ["settings-display"],
    queryFn: () => apiClient.get<DisplayResponse>("/internal/v1/settings/display"),
  });
}

const THEMES = [
  { value: "light", label: "Light", icon: Sun },
  { value: "dark", label: "Dark", icon: Moon },
  { value: "system", label: "System", icon: Monitor },
];

const FONT_SIZES = [
  { value: "small", label: "Small" },
  { value: "medium", label: "Medium" },
  { value: "large", label: "Large" },
];

const DENSITIES = [
  { value: "comfortable", label: "Comfortable" },
  { value: "compact", label: "Compact" },
];

export default function DisplaySettingsPage() {
  const queryClient = useQueryClient();
  const { data, isLoading, error } = useDisplaySettings();
  const display = data?.data;

  const [theme, setTheme] = useState("system");
  const [fontSize, setFontSize] = useState("medium");
  const [density, setDensity] = useState("comfortable");
  const [initialized, setInitialized] = useState(false);

  if (display && !initialized) {
    setTheme(display.attributes.theme);
    setFontSize(display.attributes.fontSize);
    setDensity(display.attributes.density);
    setInitialized(true);
  }

  const saveDisplay = useMutation({
    mutationFn: (payload: { theme: string; fontSize: string; density: string }) =>
      apiClient.put("/internal/v1/settings/display", payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["settings-display"] });
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    saveDisplay.mutate({ theme, fontSize, density });
  };

  return (
    <AppLayout>
      <PageShell
        title="Display Settings"
        subtitle="Customize theme, font size, and layout density"
      >
        <div className="mb-4">
          <Link
            href="/settings"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to settings
          </Link>
        </div>

        {error ? (
          <div className="bg-card rounded-lg border border-danger/28 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load display settings</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading display settings...</span>
          </div>
        ) : (
          <div className="max-w-2xl">
            {saveDisplay.isSuccess && (
              <div className="mb-4 flex items-center gap-2 px-4 py-3 bg-green-50 border border-green-200 rounded-lg">
                <CheckCircle className="w-4 h-4 text-green-600" />
                <p className="text-sm text-green-700">Display settings saved successfully</p>
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-6">
              {/* Theme */}
              <div className="bg-card rounded-lg border border-border p-6">
                <h3 className="text-sm font-medium text-foreground mb-4">Theme</h3>
                <div className="grid grid-cols-3 gap-3">
                  {THEMES.map((t) => {
                    const Icon = t.icon;
                    return (
                      <button
                        key={t.value}
                        type="button"
                        onClick={() => setTheme(t.value)}
                        className={`flex flex-col items-center gap-2 p-4 rounded-lg border-2 transition-colors ${
                          theme === t.value
                            ? "border-impilo-400 bg-primary-soft"
                            : "border-border hover:border-border"
                        }`}
                      >
                        <Icon
                          className={`w-6 h-6 ${
                            theme === t.value ? "text-primary" : "text-muted-foreground"
                          }`}
                        />
                        <span
                          className={`text-sm font-medium ${
                            theme === t.value ? "text-primary" : "text-muted-foreground"
                          }`}
                        >
                          {t.label}
                        </span>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Font Size */}
              <div className="bg-card rounded-lg border border-border p-6">
                <h3 className="text-sm font-medium text-foreground mb-4">Font Size</h3>
                <div className="flex gap-3">
                  {FONT_SIZES.map((fs) => (
                    <button
                      key={fs.value}
                      type="button"
                      onClick={() => setFontSize(fs.value)}
                      className={`px-4 py-2.5 rounded-lg border-2 text-sm font-medium transition-colors ${
                        fontSize === fs.value
                          ? "border-impilo-400 bg-primary-soft text-primary"
                          : "border-border text-muted-foreground hover:border-border"
                      }`}
                    >
                      {fs.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* Density */}
              <div className="bg-card rounded-lg border border-border p-6">
                <h3 className="text-sm font-medium text-foreground mb-4">Density</h3>
                <div className="flex gap-3">
                  {DENSITIES.map((d) => (
                    <button
                      key={d.value}
                      type="button"
                      onClick={() => setDensity(d.value)}
                      className={`px-4 py-2.5 rounded-lg border-2 text-sm font-medium transition-colors ${
                        density === d.value
                          ? "border-impilo-400 bg-primary-soft text-primary"
                          : "border-border text-muted-foreground hover:border-border"
                      }`}
                    >
                      {d.label}
                    </button>
                  ))}
                </div>
              </div>

              <button
                type="submit"
                disabled={saveDisplay.isPending}
                className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-medium text-white bg-primary rounded-lg hover:bg-primary-hover transition-colors disabled:opacity-50"
              >
                {saveDisplay.isPending ? (
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
