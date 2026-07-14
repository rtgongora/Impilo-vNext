import React, { useState } from "react";
import {
  View,
  Text,
  TouchableOpacity,
  TextInput,
  StyleSheet,
  Alert,
  Switch,
  Share,
  ActivityIndicator,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import QRCode from "react-native-qrcode-svg";
import * as LocalAuthentication from "expo-local-authentication";
import { Button, LoadingSpinner, ErrorState } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  fetchMyCards,
  setPhrCarry,
  payBiometric,
  createBillContribution,
  type SmartCard,
} from "../../services/smartCardService";
import {
  hasDeviceCardKey,
  ensureDeviceCardKey,
  signOfflineTransaction,
} from "../../services/smartCardKey";
import { useAppStore } from "../../stores/appStore";

/**
 * Unified SMART card — one card, three functions: Identity + Financial + Personal Health Record.
 * Renders the virtual card (a wallet pass on the phone), lets the citizen toggle the PHR-carry
 * function, pay from the card with a biometric (scan-to-pay), and raise a shareable "help pay my
 * bill" link. The device's P-256 key (smartCardKey) signs offline transactions the server verifies.
 */
export function SmartCardSection() {
  const profile = useAppStore((s) => s.profile);
  const queryClient = useQueryClient();
  const [flipped, setFlipped] = useState(false);
  const [payAmount, setPayAmount] = useState("");
  const [billTitle, setBillTitle] = useState("");
  const [billTarget, setBillTarget] = useState("");

  const { data: cards, isLoading, error } = useQuery({
    queryKey: ["smart-cards"],
    queryFn: fetchMyCards,
  });

  const { data: hasKey } = useQuery({
    queryKey: ["smart-card-device-key"],
    queryFn: hasDeviceCardKey,
  });

  const card: SmartCard | undefined = cards?.[0];

  const setupKeyMutation = useMutation({
    mutationFn: ensureDeviceCardKey,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["smart-card-device-key"] });
      Alert.alert(
        "Card ready on this device",
        "A secure card key was created on this phone. To use tap/scan/biometric pay, this device must be enrolled to your card.",
      );
    },
    onError: (e: unknown) => Alert.alert("Setup failed", errText(e)),
  });

  const phrMutation = useMutation({
    mutationFn: (enabled: boolean) => setPhrCarry(card!.cardId, enabled),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["smart-cards"] }),
    onError: (e: unknown) => Alert.alert("Could not update", errText(e)),
  });

  const payMutation = useMutation({
    mutationFn: async () => {
      if (!card?.vitoCardNumber) {
        throw new Error("This card is not yet set up for offline pay on this device.");
      }
      const amount = payAmount.trim();
      if (!amount || Number(amount) <= 0) throw new Error("Enter an amount greater than 0.");

      // 1) Biometric gate — the device biometric authorizes use of the card key (WebAuthn-style).
      const hasHw = await LocalAuthentication.hasHardwareAsync();
      const enrolled = await LocalAuthentication.isEnrolledAsync();
      if (!hasHw || !enrolled) {
        throw new Error("No biometric is set up on this device.");
      }
      const auth = await LocalAuthentication.authenticateAsync({
        promptMessage: `Pay ${amount} from your card`,
        disableDeviceFallback: false,
      });
      if (!auth.success) throw new Error("Biometric authorization was cancelled.");

      // 2) Sign the canonical payload asserting BIOMETRIC user-verification, then reconcile.
      const signed = await signOfflineTransaction({
        amount,
        currency: "USD",
        authMethod: "BIOMETRIC",
      });
      return payBiometric({
        vitoCardNumber: card.vitoCardNumber,
        payload: signed.payload,
        signature: signed.signature,
      });
    },
    onSuccess: () => {
      setPayAmount("");
      queryClient.invalidateQueries({ queryKey: ["smart-cards"] });
      Alert.alert("Paid", "Your card payment was authorized with your biometric.");
    },
    onError: (e: unknown) => Alert.alert("Payment not completed", errText(e)),
  });

  const billMutation = useMutation({
    mutationFn: () =>
      createBillContribution({
        title: billTitle.trim() || "Help with my bill",
        targetAmount: billTarget.trim() || undefined,
        currency: "USD",
      }),
    onSuccess: async (req) => {
      setBillTitle("");
      setBillTarget("");
      if (req?.shareToken) {
        const url = `https://impilo.mohcc.gov.zw/give/${req.shareToken}`;
        await Share.share({
          message: `Please help me with my health bill on Impilo: ${url}`,
        });
      }
    },
    onError: (e: unknown) => Alert.alert("Could not create link", errText(e)),
  });

  if (isLoading) return <LoadingSpinner />;
  if (error) {
    return (
      <ErrorState
        message="Failed to load your card"
        onRetry={() => queryClient.invalidateQueries({ queryKey: ["smart-cards"] })}
      />
    );
  }

  if (!card) {
    return (
      <View style={styles.container}>
        <View style={styles.emptyCard}>
          <Ionicons name="card-outline" size={40} color="#93C5FD" />
          <Text style={styles.emptyTitle}>No card yet</Text>
          <Text style={styles.emptyText}>
            Your unified SMART card carries your identity, wallet, and health record. It appears here
            once issued to your wallet.
          </Text>
        </View>
      </View>
    );
  }

  const qrValue =
    card.vitoCardNumber ?? `impilo-card:${card.cardId}`; // identity/pay reference encoded in the QR

  return (
    <View style={styles.container}>
      {/* ── Virtual card (front / back flip) ── */}
      <TouchableOpacity activeOpacity={0.9} onPress={() => setFlipped((f) => !f)}>
        {!flipped ? (
          <View style={styles.card}>
            <View style={styles.cardTopStrip}>
              <Text style={styles.brand}>IMPILO SMART CARD</Text>
              <View style={styles.statusPill}>
                <View style={styles.statusDot} />
                <Text style={styles.statusText}>{card.status}</Text>
              </View>
            </View>
            <View style={styles.chip} />
            <Text style={styles.cardNumber}>{card.maskedCardNumber}</Text>
            <Text style={styles.cardName}>
              {profile?.givenName} {profile?.familyName}
            </Text>
            <View style={styles.functionsRow}>
              <Function icon="finger-print" label="ID" on />
              <Function icon="wallet" label="Pay" on />
              <Function icon="pulse" label="Health" on={card.phrEnabled} />
            </View>
            <Text style={styles.flipHint}>Tap to show QR</Text>
          </View>
        ) : (
          <View style={[styles.card, styles.cardBack]}>
            <Text style={styles.brand}>SCAN TO VERIFY / PAY</Text>
            <View style={styles.qrWrap}>
              <QRCode value={qrValue} size={150} backgroundColor="#FFFFFF" color="#0F172A" />
            </View>
            <Text style={styles.qrCaption}>
              {card.vitoCardNumber ? "Present this to pay or verify your identity" : "Card identity reference"}
            </Text>
            <Text style={styles.flipHint}>Tap to flip back</Text>
          </View>
        )}
      </TouchableOpacity>

      {/* ── Device setup ── */}
      {!hasKey ? (
        <View style={styles.panel}>
          <View style={styles.panelHeader}>
            <Ionicons name="key-outline" size={16} color="#2563EB" />
            <Text style={styles.panelTitle}>Set up this device</Text>
          </View>
          <Text style={styles.panelText}>
            Create a secure card key on this phone to use scan-to-pay. Your private key never leaves
            the device.
          </Text>
          <Button
            title={setupKeyMutation.isPending ? "Setting up…" : "Set up card on this device"}
            onPress={() => setupKeyMutation.mutate()}
            disabled={setupKeyMutation.isPending}
          />
        </View>
      ) : null}

      {/* ── PHR-carry function ── */}
      <View style={styles.panel}>
        <View style={styles.rowBetween}>
          <View style={styles.panelHeader}>
            <Ionicons name="pulse" size={16} color="#059669" />
            <Text style={styles.panelTitle}>Carry my health record</Text>
          </View>
          <Switch
            value={card.phrEnabled}
            onValueChange={(v) => phrMutation.mutate(v)}
            disabled={phrMutation.isPending}
          />
        </View>
        <Text style={styles.panelText}>
          When on, your critical health summary (allergies, conditions) is kept on your card for
          offline and emergency access. Off by default — you control it.
        </Text>
      </View>

      {/* ── Biometric pay ── */}
      <View style={styles.panel}>
        <View style={styles.panelHeader}>
          <Ionicons name="finger-print" size={16} color="#7C3AED" />
          <Text style={styles.panelTitle}>Scan-biometrics to pay</Text>
        </View>
        <TextInput
          style={styles.input}
          placeholder="Amount (USD)"
          placeholderTextColor="#9CA3AF"
          keyboardType="decimal-pad"
          value={payAmount}
          onChangeText={setPayAmount}
        />
        <Button
          title={payMutation.isPending ? "Authorizing…" : "Pay with biometric"}
          onPress={() => payMutation.mutate()}
          disabled={payMutation.isPending || !hasKey}
        />
        {!hasKey ? <Text style={styles.hintMuted}>Set up your card on this device first.</Text> : null}
      </View>

      {/* ── Help pay my bill ── */}
      <View style={styles.panel}>
        <View style={styles.panelHeader}>
          <Ionicons name="people" size={16} color="#EA580C" />
          <Text style={styles.panelTitle}>Get help with a bill</Text>
        </View>
        <Text style={styles.panelText}>
          Create a link a family member or group can chip in to. Contributions go straight to your
          wallet.
        </Text>
        <TextInput
          style={styles.input}
          placeholder="What's it for? (e.g. Hospital bill)"
          placeholderTextColor="#9CA3AF"
          value={billTitle}
          onChangeText={setBillTitle}
        />
        <TextInput
          style={styles.input}
          placeholder="Target amount (optional)"
          placeholderTextColor="#9CA3AF"
          keyboardType="decimal-pad"
          value={billTarget}
          onChangeText={setBillTarget}
        />
        <Button
          title={billMutation.isPending ? "Creating…" : "Create & share link"}
          onPress={() => billMutation.mutate()}
          disabled={billMutation.isPending}
        />
      </View>
    </View>
  );
}

function Function({ icon, label, on }: { icon: React.ComponentProps<typeof Ionicons>["name"]; label: string; on: boolean }) {
  return (
    <View style={styles.function}>
      <Ionicons name={icon} size={16} color={on ? "#FFFFFF" : "rgba(255,255,255,0.35)"} />
      <Text style={[styles.functionLabel, !on && styles.functionLabelOff]}>{label}</Text>
    </View>
  );
}

function errText(e: unknown): string {
  if (e && typeof e === "object" && "message" in e) return String((e as { message: unknown }).message);
  return "Something went wrong. Please try again.";
}

const styles = StyleSheet.create({
  container: { gap: 14 },
  card: {
    backgroundColor: "#0F172A",
    borderRadius: 18,
    padding: 20,
    minHeight: 210,
    justifyContent: "space-between",
  },
  cardBack: { alignItems: "center", justifyContent: "center", gap: 12 },
  cardTopStrip: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  brand: { color: "#93C5FD", fontSize: 12, fontWeight: "700", letterSpacing: 1.2 },
  statusPill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    backgroundColor: "rgba(16,185,129,0.15)",
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 12,
  },
  statusDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: "#10B981" },
  statusText: { color: "#6EE7B7", fontSize: 10, fontWeight: "700" },
  chip: { width: 38, height: 28, borderRadius: 6, backgroundColor: "#CBA135", marginTop: 14 },
  cardNumber: { color: "#F8FAFC", fontSize: 18, letterSpacing: 2, marginTop: 10, fontWeight: "600" },
  cardName: { color: "#CBD5E1", fontSize: 13, marginTop: 4, textTransform: "uppercase", letterSpacing: 0.5 },
  functionsRow: { flexDirection: "row", gap: 20, marginTop: 14 },
  function: { flexDirection: "row", alignItems: "center", gap: 5 },
  functionLabel: { color: "#FFFFFF", fontSize: 12, fontWeight: "600" },
  functionLabelOff: { color: "rgba(255,255,255,0.35)" },
  flipHint: { color: "rgba(255,255,255,0.5)", fontSize: 10, marginTop: 12, textAlign: "center" },
  qrWrap: { backgroundColor: "#FFFFFF", padding: 12, borderRadius: 12 },
  qrCaption: { color: "#CBD5E1", fontSize: 12, textAlign: "center" },
  panel: {
    backgroundColor: "#FFFFFF",
    borderRadius: 14,
    padding: 16,
    gap: 10,
    borderWidth: 1,
    borderColor: "#F1F5F9",
  },
  panelHeader: { flexDirection: "row", alignItems: "center", gap: 6 },
  panelTitle: { fontSize: 14, fontWeight: "700", color: "#111827" },
  panelText: { fontSize: 12, color: "#6B7280", lineHeight: 17 },
  rowBetween: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  input: {
    borderWidth: 1,
    borderColor: "#E5E7EB",
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 14,
    color: "#111827",
  },
  hintMuted: { fontSize: 11, color: "#9CA3AF" },
  emptyCard: { alignItems: "center", gap: 8, padding: 24 },
  emptyTitle: { fontSize: 16, fontWeight: "700", color: "#111827" },
  emptyText: { fontSize: 13, color: "#6B7280", textAlign: "center", lineHeight: 19 },
});
