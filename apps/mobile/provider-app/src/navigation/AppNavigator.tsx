/**
 * AppNavigator — Root navigator that wraps AuthGuard + ModeRouter.
 *
 * Sets up the app shell:
 *   - error boundary
 *   - network listener
 *   - notification setup
 *   - global Nompilo assistant launcher and conversation BottomSheet
 */

import React, { useState } from "react";
import { StyleSheet, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { BottomSheet, NompiloLauncher } from "@impilo/mobile-design-system";
import { AuthGuard } from "./AuthGuard";
import { ModeRouter } from "./ModeRouter";
import { useDeepLinkRouting } from "./deepLinks";
import { GlobalErrorBanner } from "../screens/GlobalErrorBanner";
import { NetworkStatusBar } from "../screens/NetworkStatusBar";
import { NompiloAssistantScreen } from "../screens/NompiloAssistantScreen";

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
          <ModeRouter />
          <NompiloLauncher
            onPress={() => setNompiloOpen(true)}
            accentColor="#009739"
            testID="provider-nompilo-launcher"
          />
        </View>
      </AuthGuard>
      <BottomSheet isOpen={nompiloOpen} onClose={() => setNompiloOpen(false)}>
        <NompiloAssistantScreen
          onClose={() => setNompiloOpen(false)}
          surface="provider-global"
        />
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
