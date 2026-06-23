/**
 * FloatingSOSButton — Persistent SOS trigger always visible above the tab bar.
 * Tap to open the hold-to-activate sheet. Hold 3 s to dispatch the alert.
 */
import React, { useState, useRef } from "react";
import {
  View,
  Text,
  Pressable,
  StyleSheet,
  Modal,
  Animated,
  Alert,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useMutation } from "@tanstack/react-query";
import { triggerSOS } from "../services/sosService";
import { useAppStore } from "../stores/appStore";
import { APP_RED, APP_RED_LIGHT, APP_SURFACE, APP_BORDER, APP_TEXT_3 } from "../lib/colors";

const HOLD_MS = 3000;

export function FloatingSOSButton() {
  const profile   = useAppStore((s) => s.profile);
  const patientId = profile?.cpid ?? "";

  const [open, setOpen]         = useState(false);
  const [holding, setHolding]   = useState(false);
  const [countdown, setCountdown] = useState(3);

  const holdProgress = useRef(new Animated.Value(0)).current;
  const holdAnim     = useRef<Animated.CompositeAnimation | null>(null);
  const timer        = useRef<ReturnType<typeof setInterval> | null>(null);

  const sosMutation = useMutation({
    mutationFn: () => triggerSOS({ patientId, alertType: "MEDICAL" }),
    onSuccess:  (data) => {
      setOpen(false);
      Alert.alert("SOS Activated", data.message);
    },
    onError: () => {
      Alert.alert("SOS Failed", "Could not reach emergency services. Call 999 directly.");
    },
  });

  const startHold = () => {
    setHolding(true);
    setCountdown(3);
    holdProgress.setValue(0);

    timer.current = setInterval(() => {
      setCountdown((c) => (c > 1 ? c - 1 : c));
    }, 1000);

    holdAnim.current = Animated.timing(holdProgress, {
      toValue: 1,
      duration: HOLD_MS,
      useNativeDriver: false,
    });

    holdAnim.current.start(({ finished }) => {
      if (timer.current) clearInterval(timer.current);
      if (finished) sosMutation.mutate();
      setHolding(false);
      holdProgress.setValue(0);
      setCountdown(3);
    });
  };

  const cancelHold = () => {
    holdAnim.current?.stop();
    if (timer.current) clearInterval(timer.current);
    holdProgress.setValue(0);
    setHolding(false);
    setCountdown(3);
  };

  const dismiss = () => {
    cancelHold();
    setOpen(false);
  };

  const progressWidth = holdProgress.interpolate({
    inputRange: [0, 1],
    outputRange: ["0%", "100%"],
  });

  const buttonScale = holdProgress.interpolate({
    inputRange: [0, 1],
    outputRange: [1, 1.05],
  });

  return (
    <>
      {/* Floating pill */}
      <Pressable
        onPress={() => setOpen(true)}
        style={({ pressed }) => [styles.fab, pressed && { opacity: 0.88 }]}
        testID="sos-fab"
        accessibilityLabel="Emergency SOS"
        accessibilityRole="button"
      >
        <Ionicons name="warning" size={14} color="#fff" />
        <Text style={styles.fabText}>SOS</Text>
      </Pressable>

      {/* Activation sheet */}
      <Modal visible={open} transparent animationType="fade" onRequestClose={dismiss}>
        <Pressable style={styles.overlay} onPress={dismiss}>
          {/* Stop tap-through on the sheet itself */}
          <Pressable style={styles.sheet} onPress={() => {}}>
            <View style={styles.sheetHandle} />

            <Text style={styles.sheetTitle}>Emergency SOS</Text>
            <Text style={styles.sheetSubtitle}>
              Alerts emergency services and your contacts with your location.
            </Text>

            {/* Hold button */}
            <Animated.View style={{ transform: [{ scale: buttonScale }], alignItems: "center" }}>
              <Pressable
                onPressIn={startHold}
                onPressOut={cancelHold}
                style={[styles.sosBtn, holding && styles.sosBtnActive]}
                testID="sos-hold-btn"
              >
                <View style={styles.sosPulse} />
                <Ionicons name="call" size={36} color="#fff" />
                <Text style={styles.sosLabel}>SOS</Text>
                <Text style={styles.sosHint}>
                  {holding ? `Activating in ${countdown}s…` : "Hold 3 sec"}
                </Text>
              </Pressable>
            </Animated.View>

            {/* Progress bar */}
            <View style={styles.track}>
              <Animated.View style={[styles.fill, { width: progressWidth }]} />
            </View>

            <Pressable onPress={dismiss} style={styles.dismissBtn}>
              <Text style={styles.dismissText}>Cancel</Text>
            </Pressable>
          </Pressable>
        </Pressable>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  // ── Floating pill ──────────────────────────────────────────────────────────
  fab: {
    position: "absolute",
    bottom: 84,          // sits just above the 68 px tab bar + safe padding
    right: 16,
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    backgroundColor: APP_RED,
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderRadius: 24,
    shadowColor: APP_RED,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.4,
    shadowRadius: 10,
    elevation: 8,
  },
  fabText: { fontSize: 13, fontWeight: "800", color: "#fff", letterSpacing: 1 },

  // ── Sheet ──────────────────────────────────────────────────────────────────
  overlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.5)",
    justifyContent: "flex-end",
  },
  sheet: {
    backgroundColor: APP_SURFACE,
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    paddingHorizontal: 24,
    paddingBottom: 40,
    paddingTop: 12,
    alignItems: "center",
    gap: 16,
  },
  sheetHandle: {
    width: 36,
    height: 4,
    borderRadius: 2,
    backgroundColor: APP_BORDER,
    marginBottom: 4,
  },
  sheetTitle: { fontSize: 18, fontWeight: "800", color: "#1A1A1A" },
  sheetSubtitle: {
    fontSize: 13,
    color: APP_TEXT_3,
    textAlign: "center",
    lineHeight: 19,
    paddingHorizontal: 8,
  },

  // ── SOS hold button ────────────────────────────────────────────────────────
  sosBtn: {
    width: 156,
    height: 156,
    borderRadius: 78,
    backgroundColor: APP_RED,
    alignItems: "center",
    justifyContent: "center",
    gap: 2,
    shadowColor: APP_RED,
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.4,
    shadowRadius: 20,
    elevation: 12,
  },
  sosBtnActive: {
    shadowOpacity: 0.6,
    shadowRadius: 28,
    elevation: 18,
  },
  sosPulse: {
    position: "absolute",
    width: 178,
    height: 178,
    borderRadius: 89,
    borderWidth: 2,
    borderColor: APP_RED + "33",
  },
  sosLabel: { color: "#fff", fontSize: 28, fontWeight: "900", letterSpacing: 5, marginTop: 4 },
  sosHint:  { color: "rgba(255,255,255,0.75)", fontSize: 11 },

  // ── Progress ───────────────────────────────────────────────────────────────
  track: {
    width: "70%",
    height: 4,
    backgroundColor: APP_RED_LIGHT,
    borderRadius: 2,
    overflow: "hidden",
  },
  fill: { height: "100%", backgroundColor: APP_RED, borderRadius: 2 },

  // ── Dismiss ────────────────────────────────────────────────────────────────
  dismissBtn: {
    paddingHorizontal: 28,
    paddingVertical: 11,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: APP_BORDER,
  },
  dismissText: { fontSize: 14, fontWeight: "600", color: APP_TEXT_3 },
});
