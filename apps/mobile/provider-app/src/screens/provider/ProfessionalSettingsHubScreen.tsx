/**
 * ProfessionalSettingsHubScreen — Tier-3 wave 6 parity for web `/settings*` professional landings.
 */
import React, { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { ProfessionalHubBody } from "../../components/ProfessionalHubBody";
import { resolveHubSections } from "../../lib/hubUi";
import {
  fetchProfessionalSettingsHub,
  type ProfessionalSettingsSection,
} from "../../services/professionalSettingsHubService";

const FALLBACK_SECTIONS: ProfessionalSettingsSection[] = [
  { id: "settings", title: "Settings", web_path: "/settings", hint: "Professional preferences overview." },
  { id: "settings_account", title: "Account Settings", web_path: "/settings/account", hint: "Profile, language, and sign-in identity." },
  { id: "settings_security", title: "Security Settings", web_path: "/settings/security", hint: "MFA, sessions, and device posture." },
  { id: "settings_notifications", title: "Notification Preferences", web_path: "/settings/notifications", hint: "Channels and alert rules." },
  { id: "settings_display", title: "Display Settings", web_path: "/settings/display", hint: "Density, contrast, and accessibility." },
  { id: "settings_integrations", title: "Integrations", web_path: "/settings/integrations", hint: "Connected apps and API tokens." },
  { id: "settings_privacy", title: "Privacy & Data", web_path: "/settings/privacy", hint: "Retention, export, and consent mirrors." },
];

export function ProfessionalSettingsHubScreen() {
  const { data, isPending, isError, refetch, isRefetching } = useQuery({
    queryKey: ["professional-settings-hub"],
    queryFn: fetchProfessionalSettingsHub,
    retry: 1,
  });

  // An empty answer from the BFF is a real answer — see resolveHubSections.
  const { sections, isOfflineLayout } = useMemo(
    () => resolveHubSections(data?.sections, FALLBACK_SECTIONS),
    [data],
  );

  return (
    <ProfessionalHubBody
      rootTestID="professional-settings-hub-screen"
      heading="Professional settings"
      description="Mirrors the web settings sidebar for the professional context; full editors open in workspace when available."
      sections={sections}
      isPending={isPending}
      isError={isError}
      isOfflineLayout={isOfflineLayout}
      refreshedAt={data?.refreshed_at}
      isRefetching={isRefetching}
      onRefresh={() => refetch()}
      getSectionTestId={(id) => `professional-settings-hub-section-${id}`}
    />
  );
}
