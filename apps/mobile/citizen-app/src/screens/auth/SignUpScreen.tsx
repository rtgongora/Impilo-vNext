/**
 * SignUpScreen — Citizen registration mirroring web /auth/register golden path.
 */

import { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, TextInput, Pressable, ScrollView } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Button, LoadingSpinner } from "@impilo/mobile-design-system";
import {
  fetchRegistrationReadiness,
  registerCitizen,
  type RegisterSuccess,
} from "../../services/citizenRegistrationService";
import { appStore } from "../../stores/appStore";
import { useAuth } from "@impilo/mobile-auth";

const GREEN = "#059669";

interface SignUpScreenProps {
  onBack: () => void;
}

export function SignUpScreen({ onBack }: SignUpScreenProps) {
  const auth = useAuth();
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [healthId, setHealthId] = useState("");
  const [hasHealthId, setHasHealthId] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [readinessMessage, setReadinessMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    fetchRegistrationReadiness()
      .then((r) => {
        if (!r.ready) {
          setReadinessMessage(r.message ?? "Registration is temporarily unavailable.");
        }
      })
      .catch(() => setReadinessMessage("Could not verify registration readiness."));
  }, []);

  const handleSubmit = useCallback(async () => {
    if (submitting || readinessMessage) return;
    setError(null);
    setSubmitting(true);
    try {
      const result = await registerCitizen({
        fullName,
        email,
        password,
        healthId: hasHealthId ? healthId : undefined,
      });

      if (result.kind === "session") {
        const session = result as RegisterSuccess;
        await auth.establishFromTokenResponse({
          access_token: session.accessToken,
          refresh_token: session.refreshToken,
          expires_in: session.expiresIn,
        });
        appStore.getState().setOnboardingScreen("assurance");
      } else {
        setError(result.message);
      }
    } catch (err) {
      const apiErr = err as { response?: { data?: { error?: { message?: string } } }; message?: string };
      setError(
        apiErr.response?.data?.error?.message ??
          (err instanceof Error ? err.message : "Registration failed. Please try again."),
      );
    } finally {
      setSubmitting(false);
    }
  }, [auth, email, fullName, hasHealthId, healthId, password, readinessMessage, submitting]);

  return (
    <ScrollView testID="signup-screen" contentContainerStyle={styles.container}>
      <Pressable onPress={onBack} style={styles.backRow} accessibilityLabel="Back to sign in">
        <Ionicons name="arrow-back" size={20} color={GREEN} />
        <Text style={styles.backText}>Back to sign in</Text>
      </Pressable>

      <Text style={styles.title}>Create your Impilo account</Text>
      <Text style={styles.subtitle}>
        Register as a person (CITIZEN). Professional roles are activated after sign-in.
      </Text>

      {readinessMessage ? (
        <View style={styles.warnBox}>
          <Text style={styles.warnText}>{readinessMessage}</Text>
        </View>
      ) : null}

      {error ? (
        <View style={styles.errorBox}>
          <Text style={styles.errorText}>{error}</Text>
        </View>
      ) : null}

      <Text style={styles.label}>Full name</Text>
      <TextInput
        testID="signup-full-name"
        style={styles.input}
        value={fullName}
        onChangeText={setFullName}
        autoCapitalize="words"
        placeholder="Your legal name"
      />

      <Text style={styles.label}>Email</Text>
      <TextInput
        testID="signup-email"
        style={styles.input}
        value={email}
        onChangeText={setEmail}
        autoCapitalize="none"
        keyboardType="email-address"
        placeholder="you@example.com"
      />

      <Text style={styles.label}>Password</Text>
      <TextInput
        testID="signup-password"
        style={styles.input}
        value={password}
        onChangeText={setPassword}
        secureTextEntry
        placeholder="At least 8 characters"
      />

      <Pressable
        onPress={() => setHasHealthId((v) => !v)}
        style={styles.checkboxRow}
        accessibilityRole="checkbox"
        accessibilityState={{ checked: hasHealthId }}
      >
        <Ionicons name={hasHealthId ? "checkbox" : "square-outline"} size={20} color={GREEN} />
        <Text style={styles.checkboxLabel}>I already have a Health ID</Text>
      </Pressable>

      {hasHealthId ? (
        <>
          <Text style={styles.label}>Health ID</Text>
          <TextInput
            testID="signup-health-id"
            style={styles.input}
            value={healthId}
            onChangeText={setHealthId}
            autoCapitalize="characters"
            placeholder="Optional link to existing ID"
          />
        </>
      ) : null}

      {submitting ? (
        <View style={styles.loadingRow}>
          <LoadingSpinner size="md" />
          <Text style={styles.loadingText}>Creating account…</Text>
        </View>
      ) : (
        <Button
          title="Create account"
          onPress={handleSubmit}
          variant="primary"
          size="lg"
          fullWidth
          disabled={!fullName.trim() || !email.trim() || password.length < 8 || Boolean(readinessMessage)}
          testID="signup-submit"
        />
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { padding: 24, paddingBottom: 48, backgroundColor: "#FFFFFF", flexGrow: 1 },
  backRow: { flexDirection: "row", alignItems: "center", gap: 6, marginBottom: 16 },
  backText: { color: GREEN, fontSize: 14, fontWeight: "600" },
  title: { fontSize: 24, fontWeight: "800", color: "#111827" },
  subtitle: { marginTop: 8, fontSize: 14, color: "#6B7280", lineHeight: 20, marginBottom: 20 },
  label: { fontSize: 12, fontWeight: "600", color: "#374151", marginTop: 12, marginBottom: 4 },
  input: {
    borderWidth: 1,
    borderColor: "#E5E7EB",
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 15,
    color: "#111827",
  },
  checkboxRow: { flexDirection: "row", alignItems: "center", gap: 8, marginTop: 16 },
  checkboxLabel: { fontSize: 14, color: "#374151" },
  errorBox: {
    backgroundColor: "#FEF2F2",
    borderRadius: 10,
    padding: 12,
    borderWidth: 1,
    borderColor: "#FECACA",
    marginBottom: 12,
  },
  errorText: { color: "#DC2626", fontSize: 13 },
  warnBox: {
    backgroundColor: "#FFFBEB",
    borderRadius: 10,
    padding: 12,
    borderWidth: 1,
    borderColor: "#FDE68A",
    marginBottom: 12,
  },
  warnText: { color: "#92400E", fontSize: 13 },
  loadingRow: { flexDirection: "row", alignItems: "center", gap: 12, paddingVertical: 16, justifyContent: "center" },
  loadingText: { fontSize: 15, color: "#6B7280" },
});
