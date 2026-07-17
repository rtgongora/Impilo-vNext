/**
 * AppNavigator — Root navigator wrapping AuthGuard + CitizenTabs.
 *
 * Also hosts:
 *   - the global Nompilo launcher (floating, bottom right)
 *   - the global NhumeTracking overlay (deep-link target from Home and Personal)
 *   - the Nompilo conversation BottomSheet
 */

import React, { useState } from "react";
import { StyleSheet, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { BottomSheet, NompiloLauncher } from "@impilo/mobile-design-system";
import { AuthGuard } from "./AuthGuard";
import { CitizenTabs } from "./CitizenTabs";
import { useDeepLinkRouting } from "./deepLinks";
import { GlobalErrorBanner } from "../screens/GlobalErrorBanner";
import { NetworkStatusBar } from "../screens/NetworkStatusBar";
import { NompiloAssistantScreen } from "../screens/NompiloAssistantScreen";
import { FloatingSosButton } from "../components/sos/FloatingSosButton";

export function AppNavigator() {
  const [nompiloOpen, setNompiloOpen] = useState(false);

  // Route inbound universal links / custom-scheme deep links into the app store.
  useDeepLinkRouting();

  return (
    <SafeAreaView style={styles.container}>
      <NetworkStatusBar />
      <GlobalErrorBanner />
      <AuthGuard>
        <View style={styles.guarded}>
          <CitizenTabs />
          <FloatingSosButton />
          <NompiloLauncher
            onPress={() => setNompiloOpen(true)}
            accentColor="#059669"
            testID="citizen-nompilo-launcher"
          />
        </View>
      </AuthGuard>
      <BottomSheet isOpen={nompiloOpen} onClose={() => setNompiloOpen(false)}>
        <NompiloAssistantScreen onClose={() => setNompiloOpen(false)} surface="citizen-global" />
      </BottomSheet>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#FFFFFF",
  },
  guarded: {
    flex: 1,
  },
});
