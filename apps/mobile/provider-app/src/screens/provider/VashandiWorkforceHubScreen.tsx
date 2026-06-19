/**
 * VashandiWorkforceHubScreen — contract-filtered entry to provider Vashandi self-service screens.
 */
import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from "react-native";
import { Screen, Header, LoadingSpinner, Button } from "@impilo/mobile-design-system";
import {
  resolveMobileVashandiNav,
  type MobileVashandiNavItem,
  type VashandiMobileScreen,
} from "@impilo/mobile-auth";
import { fetchSessionExperienceContract } from "../../services/sessionExperienceService";
import { VashandiRosterScreen } from "./VashandiRosterScreen";
import { VashandiAttendanceScreen } from "./VashandiAttendanceScreen";
import { VashandiAvailabilityScreen } from "./VashandiAvailabilityScreen";
import { VashandiFacilityStaffScreen } from "./VashandiFacilityStaffScreen";

function renderScreen(screen: VashandiMobileScreen) {
  switch (screen) {
    case "roster":
      return <VashandiRosterScreen />;
    case "attendance":
      return <VashandiAttendanceScreen />;
    case "availability":
      return <VashandiAvailabilityScreen />;
    case "facility_staff":
      return <VashandiFacilityStaffScreen />;
    default:
      return null;
  }
}

export function VashandiWorkforceHubScreen() {
  const [navItems, setNavItems] = useState<MobileVashandiNavItem[]>([]);
  const [blockedState, setBlockedState] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeScreen, setActiveScreen] = useState<VashandiMobileScreen | null>(null);

  const loadNav = useCallback(async () => {
    setLoading(true);
    try {
      const contract = await fetchSessionExperienceContract();
      const items = resolveMobileVashandiNav({
        visibleVashandiWorkspaces: contract.visibleVashandiWorkspaces,
        workTabVisible: contract.workTabVisible,
      });
      setNavItems(items);
      setBlockedState(
        items.length === 0 ? contract.vashandiFriendlyResolutionState ?? "vashandi_no_workspace_authority" : null,
      );
      if (items.length === 1) {
        setActiveScreen(items[0].screen);
      }
    } catch {
      setNavItems([]);
      setBlockedState("vashandi_upstream_unavailable");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadNav();
  }, [loadNav]);

  if (loading) {
    return (
      <Screen>
        <Header title="Vashandi Workforce" subtitle="Operational roster and attendance" />
        <LoadingSpinner size="lg" />
      </Screen>
    );
  }

  if (activeScreen) {
    return (
      <View style={styles.embedded} testID="vashandi-workforce-hub">
        {navItems.length > 1 ? (
          <View style={styles.backRow}>
            <Button title="← Vashandi menu" variant="outline" size="sm" onPress={() => setActiveScreen(null)} />
          </View>
        ) : null}
        {renderScreen(activeScreen)}
      </View>
    );
  }

  if (navItems.length === 0) {
    return (
      <Screen>
        <Header title="Vashandi Workforce" subtitle="Operational roster and attendance" />
        <View style={styles.blocked} testID="vashandi-workforce-blocked">
          <Text style={styles.blockedTitle}>Vashandi access not available</Text>
          <Text style={styles.blockedMessage}>
            {blockedState ?? "Your session does not include any visible Vashandi workspaces."}
          </Text>
          <Button title="Retry" variant="outline" onPress={() => void loadNav()} />
        </View>
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="Vashandi Workforce" subtitle="Choose a governed workforce workspace" />
      <ScrollView style={styles.menu} contentContainerStyle={styles.menuContent} testID="vashandi-workforce-hub">
        {navItems.map((item) => (
          <TouchableOpacity
            key={item.id}
            style={styles.menuCard}
            testID={`vashandi-nav-${item.id}`}
            onPress={() => setActiveScreen(item.screen)}
          >
            <Text style={styles.menuTitle}>{item.label}</Text>
            <Text style={styles.menuMeta}>{item.webHref}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  embedded: { flex: 1 },
  backRow: { paddingHorizontal: 16, paddingTop: 8 },
  menu: { flex: 1 },
  menuContent: { padding: 16, gap: 12, paddingBottom: 32 },
  menuCard: {
    backgroundColor: "#F9FAFB",
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: "#E5E7EB",
  },
  menuTitle: { fontSize: 16, fontWeight: "700", color: "#111827" },
  menuMeta: { fontSize: 12, color: "#6B7280", marginTop: 4 },
  blocked: { padding: 24, gap: 12 },
  blockedTitle: { fontSize: 16, fontWeight: "700", color: "#111827" },
  blockedMessage: { fontSize: 14, color: "#6B7280", lineHeight: 20 },
});
