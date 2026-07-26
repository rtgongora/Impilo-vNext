/**
 * ProviderActivationScreen — Health OS §6 (mobile)
 *
 * "Sign in as a person; practice as a provider only under activated Provider ID."
 *
 * Minimal activation flow: allow the user to set an activated Provider ID
 * that is then persisted in secure storage and emitted as `x-provider-id`
 * on subsequent API calls via @impilo/mobile-trust.
 */

import React, { useMemo, useState } from "react";
import { View, Text, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useAuth } from "@impilo/mobile-auth";
import { Screen, Header, Button, TextField, Badge, colors } from "@impilo/mobile-design-system";

export function ProviderActivationScreen() {
  const auth = useAuth();
  const [providerId, setProviderId] = useState("");

  const canContinue = useMemo(() => providerId.trim().length > 3, [providerId]);

  return (
    <Screen>
      <Header title="Activate Provider" />
      <View testID="provider-activation-screen" style={styles.container}>
        <View style={styles.banner}>
          <Ionicons name="shield-checkmark" size={18} color="#1E40AF" />
          <Text style={styles.bannerTitle}>Provider role activation required</Text>
        </View>

        <Text style={styles.sub}>
          You are signed in as a person. To perform regulated clinical work, activate a Provider ID for this session.
        </Text>

        <Badge variant="outline">Header: x-provider-id</Badge>

        <View style={styles.card}>
          <TextField
            label="Provider ID"
            value={providerId}
            onChange={setProviderId}
            placeholder="e.g. PRV-2024-00147"
            testID="provider-id-input"
          />

          <Button
            title="Activate"
            onPress={() => auth.activateProvider(providerId.trim(), "Provider")}
            disabled={!canContinue}
            fullWidth
            testID="activate-provider-button"
          />

          <Button
            title="Sign out"
            variant="outline"
            onPress={() => void auth.logout()}
            fullWidth
            testID="activation-logout"
          />
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16, gap: 12 },
  banner: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    backgroundColor: "#EFF6FF",
    borderColor: "#BFDBFE",
    borderWidth: 1,
    borderRadius: 12,
    padding: 12,
  },
  bannerTitle: { fontSize: 14, fontWeight: "800", color: "#1E3A8A" },
  sub: { fontSize: 13, color: colors.gray[600], lineHeight: 18 },
  card: {
    marginTop: 6,
    gap: 12,
    backgroundColor: "#FFFFFF",
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colors.gray[200],
    padding: 14,
  },
});

