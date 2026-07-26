/**
 * ContactSignUpScreen — R1 "Reachable" contact-first OTP onboarding (citizen mobile mirror
 * of the web gateway flow). Runs pre-authentication over the anonymous public lane.
 *
 * Flow (mirrors web AuthContactOtpController):
 *   1. Name + phone/email  → POST /internal/v1/auth/contact/otp/request
 *   2. OTP code + password  → POST /internal/v1/auth/contact/otp/verify {purpose:"REGISTER"}
 *      → on the auth_token envelope, establish a live session and continue to assurance.
 *
 * "Create account with phone" — OTP-login for EXISTING users does not exist; this is
 * registration only. Care before coverage; help before identity.
 */
import React, { useCallback, useState } from "react";
import { View, Text, StyleSheet, ScrollView, Pressable } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Button, TextField, OtpCodeInput, isOtpComplete, colors } from "@impilo/mobile-design-system";
import { useAuth } from "@impilo/mobile-auth";
import {
  requestContactOtp,
  verifyContactRegister,
  type OtpChannel,
} from "../../services/contactOtpService";
import { appStore } from "../../stores/appStore";

const GREEN = "#059669";

interface ContactSignUpScreenProps {
  onBack: () => void;
}

type Step = "contact" | "verify";

function looksLikeEmail(value: string): boolean {
  return /@/.test(value);
}

export function ContactSignUpScreen({ onBack }: ContactSignUpScreenProps) {
  const auth = useAuth();
  const [step, setStep] = useState<Step>("contact");
  const [channel, setChannel] = useState<OtpChannel>("PHONE");
  const [contact, setContact] = useState("");
  const [fullName, setFullName] = useState("");
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [maskedValue, setMaskedValue] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const sendCode = useCallback(async () => {
    if (submitting) return;
    setError(null);
    setNotice(null);
    if (!fullName.trim()) {
      setError("Enter your name so we can create your account.");
      return;
    }
    if (!contact.trim()) {
      setError(channel === "PHONE" ? "Enter your phone number." : "Enter your email address.");
      return;
    }
    setSubmitting(true);
    try {
      const result = await requestContactOtp(channel, contact);
      setMaskedValue(result.maskedValue);
      setStep("verify");
      setNotice(`We sent a verification code to ${result.maskedValue}.`);
    } catch (err) {
      setError(errorMessage(err, "We could not send a code just now. Please try again shortly."));
    } finally {
      setSubmitting(false);
    }
  }, [channel, contact, fullName, submitting]);

  const verifyAndRegister = useCallback(async () => {
    if (submitting) return;
    setError(null);
    if (!isOtpComplete(code)) {
      setError("Enter the 6-digit code we sent you.");
      return;
    }
    if (password.length < 12) {
      setError("Choose a password of at least 12 characters.");
      return;
    }
    setSubmitting(true);
    try {
      const result = await verifyContactRegister({ value: contact, code, password, fullName });
      if (result.kind === "session") {
        await auth.establishFromTokenResponse({
          access_token: result.accessToken,
          refresh_token: result.refreshToken,
          expires_in: result.expiresIn,
        });
        appStore.getState().setOnboardingScreen("assurance");
      } else {
        setNotice(result.message);
        setError(null);
      }
    } catch (err) {
      setError(errorMessage(err, "That code did not work. Request a new code and try again."));
    } finally {
      setSubmitting(false);
    }
  }, [auth, code, contact, fullName, password, submitting]);

  return (
    <ScrollView testID="contact-signup-screen" contentContainerStyle={styles.container}>
      <Pressable
        onPress={() => (step === "verify" ? setStep("contact") : onBack())}
        style={styles.backRow}
        accessibilityLabel="Back"
      >
        <Ionicons name="arrow-back" size={20} color={GREEN} />
        <Text style={styles.backText}>{step === "verify" ? "Change details" : "Back to sign in"}</Text>
      </Pressable>

      <Text style={styles.title}>Create account with phone</Text>
      <Text style={styles.subtitle}>
        Register as a person (CITIZEN). We only need a way to reach you — you can add more later.
      </Text>

      {notice ? (
        <View style={styles.noticeBox}>
          <Text style={styles.noticeText}>{notice}</Text>
        </View>
      ) : null}
      {error ? (
        <View style={styles.errorBox}>
          <Text style={styles.errorText}>{error}</Text>
        </View>
      ) : null}

      {step === "contact" ? (
        <>
          <Text style={styles.label}>Full name</Text>
          <TextField
            testID="contact-signup-name"
            value={fullName}
            onChangeText={setFullName}
            autoCapitalize="words"
            placeholder="Your legal name"
          />

          <View style={styles.channelRow}>
            <Button
              title="Phone"
              size="sm"
              variant={channel === "PHONE" ? "primary" : "outline"}
              onPress={() => setChannel("PHONE")}
              testID="contact-signup-channel-phone"
            />
            <Button
              title="Email"
              size="sm"
              variant={channel === "EMAIL" ? "primary" : "outline"}
              onPress={() => setChannel("EMAIL")}
              testID="contact-signup-channel-email"
            />
          </View>

          <Text style={styles.label}>{channel === "PHONE" ? "Phone number" : "Email address"}</Text>
          <TextField
            testID="contact-signup-contact"
            value={contact}
            onChangeText={setContact}
            autoCapitalize="none"
            keyboardType={channel === "PHONE" ? "phone-pad" : "email-address"}
            placeholder={channel === "PHONE" ? "e.g. 0771 234 567" : "you@example.com"}
          />
          {channel === "EMAIL" && contact.length > 0 && !looksLikeEmail(contact) ? (
            <Text style={styles.hintText}>That does not look like an email address.</Text>
          ) : null}

          <View style={styles.actionRow}>
            <Button
              title={submitting ? "Sending code…" : "Send verification code"}
              onPress={sendCode}
              variant="primary"
              size="lg"
              fullWidth
              loading={submitting}
              disabled={submitting || !fullName.trim() || !contact.trim()}
              testID="contact-signup-send"
            />
          </View>
        </>
      ) : (
        <>
          <Text style={styles.label}>Enter the code sent to {maskedValue}</Text>
          <OtpCodeInput
            testID="contact-signup-otp"
            value={code}
            onChangeText={setCode}
            autoFocus
            error={undefined}
          />

          <Pressable onPress={sendCode} disabled={submitting} style={styles.resendRow}>
            <Text style={styles.resendText}>Didn&apos;t get it? Resend code</Text>
          </Pressable>

          <Text style={styles.label}>Choose a password</Text>
          <TextField
            testID="contact-signup-password"
            value={password}
            onChangeText={setPassword}
            secureTextEntry
            placeholder="At least 12 characters"
            helperText="Use upper and lower case, a number, and a symbol."
          />

          <View style={styles.actionRow}>
            <Button
              title={submitting ? "Creating account…" : "Verify & create account"}
              onPress={verifyAndRegister}
              variant="primary"
              size="lg"
              fullWidth
              loading={submitting}
              disabled={submitting || !isOtpComplete(code) || password.length < 12}
              testID="contact-signup-verify"
            />
          </View>
        </>
      )}
    </ScrollView>
  );
}

function errorMessage(err: unknown, fallback: string): string {
  const apiErr = err as { message?: string; code?: string };
  if (apiErr?.message && typeof apiErr.message === "string") return apiErr.message;
  return fallback;
}

const styles = StyleSheet.create({
  container: { padding: 24, paddingBottom: 48, backgroundColor: "#FFFFFF", flexGrow: 1 },
  backRow: { flexDirection: "row", alignItems: "center", gap: 6, marginBottom: 16 },
  backText: { color: GREEN, fontSize: 14, fontWeight: "600" },
  title: { fontSize: 24, fontWeight: "800", color: colors.gray[900] },
  subtitle: { marginTop: 8, fontSize: 14, color: colors.gray[500], lineHeight: 20, marginBottom: 12 },
  label: { fontSize: 12, fontWeight: "600", color: colors.gray[700], marginTop: 16, marginBottom: 6 },
  hintText: { fontSize: 12, color: "#B45309", marginTop: 4 },
  channelRow: { flexDirection: "row", gap: 8, marginTop: 16 },
  actionRow: { marginTop: 24 },
  resendRow: { marginTop: 12, alignItems: "center" },
  resendText: { color: GREEN, fontSize: 13, fontWeight: "600" },
  errorBox: {
    backgroundColor: "#FEF2F2",
    borderRadius: 10,
    padding: 12,
    borderWidth: 1,
    borderColor: "#FECACA",
    marginTop: 12,
  },
  errorText: { color: colors.ui.error.main, fontSize: 13 },
  noticeBox: {
    backgroundColor: "#ECFDF5",
    borderRadius: 10,
    padding: 12,
    borderWidth: 1,
    borderColor: "#A7F3D0",
    marginTop: 12,
  },
  noticeText: { color: colors.ui.success.text, fontSize: 13 },
});
