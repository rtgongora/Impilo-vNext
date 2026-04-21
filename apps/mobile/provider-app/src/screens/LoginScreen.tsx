/**
 * LoginScreen — Keycloak PKCE authentication entry point.
 * Modern hero layout with blue brand treatment for providers.
 */

import React, { useState, useCallback, useEffect } from "react";
import {
  View,
  Text,
  StyleSheet,
  Platform,
  Pressable,
} from "react-native";
import * as WebBrowser from "expo-web-browser";
import * as Linking from "expo-linking";
import { Ionicons } from "@expo/vector-icons";
import { useAuth } from "@impilo/mobile-auth";
import { Button, LoadingSpinner } from "@impilo/mobile-design-system";

WebBrowser.maybeCompleteAuthSession();

const BLUE = "#1E40AF";
const BLUE_DARK = "#1E3A8A";
const BLUE_LIGHT = "#DBEAFE";

const FEATURE_PILLS = ["Worklist", "Encounters", "Lab Results", "Outreach"];

export function LoginScreen() {
  const auth = useAuth();
  const [pendingState, setPendingState] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleLogin = useCallback(async () => {
    setError(null);
    try {
      const { authUrl, state } = await auth.login();
      setPendingState(state);
      const result = await WebBrowser.openAuthSessionAsync(
        authUrl,
        Linking.createURL("auth/callback")
      );
      if (result.type === "success" && result.url) {
        const parsed = Linking.parse(result.url);
        const code = parsed.queryParams?.code as string | undefined;
        const returnedState = parsed.queryParams?.state as string | undefined;
        if (code && returnedState) {
          await auth.handleCallback(code, returnedState, state);
        }
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed. Please try again.");
    }
  }, [auth]);

  useEffect(() => {
    const handleUrl = (event: { url: string }) => {
      const parsed = Linking.parse(event.url);
      const code = parsed.queryParams?.code as string | undefined;
      const state = parsed.queryParams?.state as string | undefined;
      if (code && state && pendingState) {
        auth.handleCallback(code, state, pendingState).catch((err) => {
          setError(err instanceof Error ? err.message : "Authentication failed");
        });
      }
    };

    const subscription = Linking.addEventListener("url", handleUrl);
    Linking.getInitialURL().then((url) => {
      if (url) handleUrl({ url });
    });
    return () => subscription.remove();
  }, [auth, pendingState]);

  return (
<<<<<<< HEAD
    <View testID="login-screen" style={styles.root}>
      <View style={styles.heroSection}>
        <View style={styles.logoCircle}>
          <Ionicons name="medkit" size={44} color="#FFFFFF" />
        </View>
        <Text style={styles.appName}>Impilo Provider</Text>
        <Text style={styles.tagline}>Clinical workflow management</Text>

        <View style={styles.pillRow}>
          {FEATURE_PILLS.map((label) => (
            <View key={label} style={styles.pill}>
              <Text style={styles.pillText}>{label}</Text>
            </View>
          ))}
        </View>
      </View>

      <View style={styles.bottomSheet}>
        <Text style={styles.sheetTitle}>Provider Sign In</Text>
        <Text style={styles.sheetSubtitle}>
          Access patient queues, clinical workflows, and facility tools. Your provider identity activates upon login.
        </Text>

        {error ? (
          <View style={styles.errorBox}>
            <Ionicons name="alert-circle" size={16} color="#DC2626" />
            <Text style={styles.errorText}>{error}</Text>
            <Pressable onPress={() => setError(null)} hitSlop={8}>
              <Ionicons name="close" size={16} color="#DC2626" />
            </Pressable>
          </View>
        ) : null}

        {auth.isLoading ? (
          <View style={styles.loadingRow}>
            <LoadingSpinner size="md" />
            <Text style={styles.loadingText}>Authenticating…</Text>
          </View>
        ) : (
          <Button
            title="Sign in with Impilo"
            onPress={handleLogin}
            variant="secondary"
            size="lg"
            fullWidth
            testID="login-button"
            accessibilityLabel="Sign in to Impilo Provider App"
            icon={<Ionicons name="log-in-outline" size={20} color="#FFFFFF" />}
          />
        )}

        <View style={styles.trustRow}>
          <Ionicons name="shield-checkmark" size={14} color="#6B7280" />
          <Text style={styles.trustText}>
            Professional identity verified by Impilo Health System
          </Text>
        </View>

        <View style={styles.modeRow}>
          <View style={styles.modeChip}>
            <Ionicons name="medkit-outline" size={12} color={BLUE} />
            <Text style={styles.modeChipText}>Provider</Text>
          </View>
          <View style={styles.modeChip}>
            <Ionicons name="map-outline" size={12} color={BLUE} />
            <Text style={styles.modeChipText}>Outreach</Text>
          </View>
          <View style={styles.modeChip}>
            <Ionicons name="bar-chart-outline" size={12} color={BLUE} />
            <Text style={styles.modeChipText}>Supervisor</Text>
          </View>
          <View style={styles.modeChip}>
            <Ionicons name="cloud-offline-outline" size={12} color={BLUE} />
            <Text style={styles.modeChipText}>Offline</Text>
          </View>
        </View>

        <View style={styles.divider} />

        <Text style={styles.footerText}>
          By continuing you agree to the{" "}
          <Text style={styles.footerLink}>Terms of Service</Text>
          {" and "}
          <Text style={styles.footerLink}>Privacy Policy</Text>
        </Text>
      </View>
    </View>
  );
}

const STATUS_TOP = Platform.OS === "ios" ? 44 : 24;

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: BLUE,
  },
  heroSection: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    paddingTop: STATUS_TOP + 24,
    paddingBottom: 32,
    paddingHorizontal: 32,
    gap: 12,
  },
  logoCircle: {
    width: 88,
    height: 88,
    borderRadius: 44,
    backgroundColor: BLUE_DARK,
    alignItems: "center",
    justifyContent: "center",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 12,
    elevation: 8,
    marginBottom: 8,
  },
  appName: {
    fontSize: 34,
    fontWeight: "800",
    color: "#FFFFFF",
    letterSpacing: -0.5,
  },
  tagline: {
    fontSize: 16,
    color: BLUE_LIGHT,
    opacity: 0.9,
  },
  pillRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    justifyContent: "center",
    marginTop: 8,
  },
  pill: {
    backgroundColor: "rgba(255,255,255,0.15)",
    borderRadius: 999,
    paddingVertical: 5,
    paddingHorizontal: 14,
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.25)",
  },
  pillText: {
    fontSize: 12,
    color: "#FFFFFF",
    fontWeight: "500",
  },
  bottomSheet: {
    backgroundColor: "#FFFFFF",
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    paddingHorizontal: 28,
    paddingTop: 32,
    paddingBottom: 40,
    gap: 16,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.12,
    shadowRadius: 16,
    elevation: 12,
  },
  sheetTitle: {
    fontSize: 22,
    fontWeight: "800",
    color: "#111827",
    letterSpacing: -0.3,
  },
  sheetSubtitle: {
    fontSize: 14,
    color: "#6B7280",
    lineHeight: 20,
    marginBottom: 4,
  },
  errorBox: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    backgroundColor: "#FEF2F2",
    borderRadius: 10,
    padding: 12,
    borderWidth: 1,
    borderColor: "#FECACA",
  },
  errorText: {
    flex: 1,
    fontSize: 13,
    color: "#DC2626",
  },
  loadingRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 12,
    paddingVertical: 16,
  },
  loadingText: {
    fontSize: 15,
    color: "#6B7280",
  },
  trustRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
  },
  trustText: {
    fontSize: 12,
    color: "#6B7280",
    flex: 1,
    textAlign: "center",
  },
  modeRow: {
    flexDirection: "row",
    justifyContent: "center",
    gap: 8,
    flexWrap: "wrap",
  },
  modeChip: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    backgroundColor: BLUE_LIGHT,
    borderRadius: 999,
    paddingVertical: 4,
    paddingHorizontal: 10,
  },
  modeChipText: {
    fontSize: 11,
    color: BLUE,
    fontWeight: "600",
  },
  divider: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: "#E5E7EB",
  },
  footerText: {
    fontSize: 12,
    color: "#9CA3AF",
    textAlign: "center",
    lineHeight: 18,
  },
  footerLink: {
    color: BLUE,
    fontWeight: "600",
  },
});
