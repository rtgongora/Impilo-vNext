/**
 * LoginScreen — Keycloak PKCE authentication for citizens.
 * Modern hero layout with green brand treatment.
 */

import { useState, useCallback, useEffect, useRef } from "react";
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

const GREEN = "#059669";
const GREEN_DARK = "#047857";
const GREEN_LIGHT = "#D1FAE5";

interface LoginScreenProps {
  onSignUp?: () => void;
}

export function LoginScreen({ onSignUp }: LoginScreenProps) {
  const auth = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [pendingState, setPendingState] = useState<string | null>(null);
  const [isSigningIn, setIsSigningIn] = useState(false);
  const callbackHandledRef = useRef(false);

  // Surface store-level auth errors (e.g. token-exchange failure after remount)
  const displayError = error ?? (auth.error?.message ?? null);

  const completeAuth = useCallback(async (url: string, expectedState: string) => {
    if (callbackHandledRef.current) {
      return;
    }

    const parsed = Linking.parse(url);
    const code = parsed.queryParams?.code as string | undefined;
    const returnedState = parsed.queryParams?.state as string | undefined;

    if (!code || !returnedState) {
      return;
    }

    callbackHandledRef.current = true;
    await auth.handleCallback(code, returnedState, expectedState);
    setPendingState(null);
  }, [auth]);

  const handleLogin = useCallback(async () => {
    if (isSigningIn) {
      return;
    }

    setError(null);
    setIsSigningIn(true);
    callbackHandledRef.current = false;

    try {
      const { authUrl, state } = await auth.login();
      setPendingState(state);

      const timeout = new Promise<never>((_, reject) =>
        setTimeout(() => reject(new Error("Authentication timed out. Please try again.")), 120_000)
      );

      const result = await Promise.race([
        WebBrowser.openAuthSessionAsync(authUrl, Linking.createURL("auth/callback")),
        timeout,
      ]);

      if (result.type === "success" && result.url) {
        await completeAuth(result.url, state);
      } else {
        setPendingState(null);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed. Please try again.");
      setPendingState(null);
    } finally {
      setIsSigningIn(false);
    }
  }, [auth, completeAuth, isSigningIn]);

  // Fallback: handle the callback URL if it arrives as a deep link rather than
  // being intercepted by openAuthSessionAsync (can happen on iOS when the system
  // browser dismisses before the session completes).
  useEffect(() => {
    const handleUrl = (event: { url: string }) => {
      if (pendingState) {
        completeAuth(event.url, pendingState).catch((err) => {
          setError(err instanceof Error ? err.message : "Authentication failed");
        });
      }
    };

    const subscription = Linking.addEventListener("url", handleUrl);
    Linking.getInitialURL().then((url) => {
      if (url) handleUrl({ url });
    });
    return () => subscription.remove();
  }, [completeAuth, pendingState]);

  return (
    <View testID="login-screen" style={styles.root}>
      <View style={styles.heroSection}>
        <View style={styles.logoCircle}>
          <Ionicons name="heart" size={44} color="#FFFFFF" />
        </View>
        <Text style={styles.appName}>Impilo Health</Text>
        <Text style={styles.tagline}>Your health, in your hands</Text>

        <View style={styles.pillRow}>
          {["Appointments", "Medications", "Results", "Telehealth"].map((label) => (
            <View key={label} style={styles.pill}>
              <Text style={styles.pillText}>{label}</Text>
            </View>
          ))}
        </View>
      </View>

      <View style={styles.bottomSheet}>
        <Text style={styles.sheetTitle}>Sign in to continue</Text>
        <Text style={styles.sheetSubtitle}>
          Access your personal health records, appointments, and more — all in one place.
        </Text>

        {displayError ? (
          <View style={styles.errorBox}>
            <Ionicons name="alert-circle" size={16} color="#DC2626" />
            <Text style={styles.errorText}>{displayError}</Text>
            <Pressable onPress={() => setError(null)} hitSlop={8}>
              <Ionicons name="close" size={16} color="#DC2626" />
            </Pressable>
          </View>
        ) : null}

        {auth.isLoading || isSigningIn ? (
          <View style={styles.loadingRow}>
            <LoadingSpinner size="md" />
            <Text style={styles.loadingText}>Signing you in…</Text>
          </View>
        ) : (
          <Button
            title="Sign in with Impilo"
            onPress={handleLogin}
            variant="primary"
            size="lg"
            fullWidth
            disabled={isSigningIn}
            testID="login-button"
            accessibilityLabel="Sign in to Impilo Citizen App"
            icon={<Ionicons name="log-in-outline" size={20} color="#FFFFFF" />}
          />
        )}

        <View style={styles.trustRow}>
          <Ionicons name="shield-checkmark" size={14} color="#6B7280" />
          <Text style={styles.trustText}>
            Secured by Impilo National Health Identity
          </Text>
        </View>

        {onSignUp ? (
          <Pressable onPress={onSignUp} testID="signup-link" style={styles.signUpRow}>
            <Text style={styles.signUpPrompt}>
              New to Impilo?{" "}
              <Text style={styles.footerLink}>Create an account</Text>
            </Text>
          </Pressable>
        ) : null}

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
    backgroundColor: GREEN,
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
    backgroundColor: GREEN_DARK,
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
    color: GREEN_LIGHT,
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
  },
  signUpRow: { alignItems: "center" },
  signUpPrompt: { fontSize: 14, color: "#374151", textAlign: "center" },
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
    color: GREEN,
    fontWeight: "600",
  },
});
