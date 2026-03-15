/**
 * PersonalScreen — Hub for all personal health features.
 *
 * Sections: Profile, Appointments, Prescriptions, Results, Records, Coverage, Settings.
 */

import React, { useState } from "react";
import { Screen, Header, TabBar, type TabItem } from "@impilo/mobile-design-system";
import { ProfileSection } from "./ProfileSection";
import { AppointmentsSection } from "./AppointmentsSection";
import { PrescriptionsSection } from "./PrescriptionsSection";
import { ResultsSection } from "./ResultsSection";
import { CoverageSection } from "./CoverageSection";
import { SettingsSection } from "./SettingsSection";

type PersonalTab = "profile" | "appointments" | "prescriptions" | "results" | "coverage" | "settings";

const PERSONAL_TABS: TabItem[] = [
  { id: "profile", label: "Profile", icon: "user" },
  { id: "appointments", label: "Appointments", icon: "calendar" },
  { id: "prescriptions", label: "Rx", icon: "pill" },
  { id: "results", label: "Results", icon: "clipboard" },
  { id: "coverage", label: "Coverage", icon: "shield" },
  { id: "settings", label: "Settings", icon: "settings" },
];

const SECTIONS: Record<PersonalTab, React.FC> = {
  profile: ProfileSection,
  appointments: AppointmentsSection,
  prescriptions: PrescriptionsSection,
  results: ResultsSection,
  coverage: CoverageSection,
  settings: SettingsSection,
};

export function PersonalScreen() {
  const [activeSection, setActiveSection] = useState<PersonalTab>("profile");
  const SectionComponent = SECTIONS[activeSection];

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, { title: "My Health" }),
    React.createElement(
      "div",
      { "data-testid": "personal-screen", style: { display: "flex", flexDirection: "column", height: "100%" } },
      React.createElement(
        "div",
        { style: { overflowX: "auto", borderBottom: "1px solid #E5E7EB", padding: "0 16px" } },
        React.createElement(
          "div",
          { style: { display: "flex", gap: "4px", minWidth: "max-content" } },
          PERSONAL_TABS.map((tab) =>
            React.createElement(
              "button",
              {
                key: tab.id,
                onClick: () => setActiveSection(tab.id as PersonalTab),
                "data-testid": `personal-tab-${tab.id}`,
                style: {
                  padding: "10px 16px",
                  border: "none",
                  borderBottom: activeSection === tab.id ? "2px solid #2563EB" : "2px solid transparent",
                  background: "none",
                  fontSize: "13px",
                  fontWeight: activeSection === tab.id ? "600" : "400",
                  color: activeSection === tab.id ? "#2563EB" : "#6B7280",
                  cursor: "pointer",
                  whiteSpace: "nowrap",
                },
              },
              tab.label
            )
          )
        )
      ),
      React.createElement(
        "div",
        { style: { flex: 1, overflow: "auto", padding: "16px" } },
        React.createElement(SectionComponent, null)
      )
    )
  );
}
