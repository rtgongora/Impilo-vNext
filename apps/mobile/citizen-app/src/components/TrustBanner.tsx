import React, { useEffect, useState } from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { fetchAssuranceStatus } from "../services/assuranceService";

/**
 * Dashboard trust banner (G-CZO-05) — the mobile counterpart of the web
 * IdentityAssuranceBanner. Surfaces the citizen's current identity-assurance level so the
 * mobile dashboard reflects trust state (previously absent). Tapping routes to where the
 * citizen can continue verification.
 *
 * Shows for account-only (LOA1) and temporary (LOA2). At verified levels (LOA3+) it renders
 * nothing — mirroring the web banner hiding at FULLY_VERIFIED. Fail-safe: while loading or if
 * the level can't be resolved, it renders nothing rather than blocking the dashboard.
 */
interface BannerCopy {
  title: string;
  subtitle: string;
  border: string;
  bg: string;
  fg: string;
  icon: string;
}

const COPY_BY_LEVEL: Record<string, BannerCopy> = {
  LOA1: {
    title: "Account only",
    subtitle: "Verify your identity to unlock your health records.",
    border: "#FCD34D",
    bg: "#FFFBEB",
    fg: "#92400E",
    icon: "shield-outline",
  },
  LOA2: {
    title: "Temporary Health ID",
    subtitle: "Continue verification for full access to your records.",
    border: "#93C5FD",
    bg: "#EFF6FF",
    fg: "#1E40AF",
    icon: "shield-half-outline",
  },
};

export function TrustBanner({ onPress }: { onPress?: () => void }) {
  const [level, setLevel] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    fetchAssuranceStatus()
      .then((s) => {
        if (active) setLevel(s.currentLevel);
      })
      .catch(() => {
        if (active) setLevel(null);
      });
    return () => {
      active = false;
    };
  }, []);

  if (!level) return null; // loading or unresolved → fail-safe, render nothing
  const copy = COPY_BY_LEVEL[level];
  if (!copy) return null; // verified (LOA3+) → no banner

  return (
    <Pressable
      testID="trust-banner"
      accessibilityRole="button"
      accessibilityLabel={`${copy.title}. ${copy.subtitle}`}
      onPress={onPress}
      style={[styles.banner, { borderColor: copy.border, backgroundColor: copy.bg }]}
    >
      <Ionicons name={copy.icon as never} size={22} color={copy.fg} />
      <View style={styles.textWrap}>
        <Text style={[styles.title, { color: copy.fg }]}>{copy.title}</Text>
        <Text style={styles.subtitle}>{copy.subtitle}</Text>
      </View>
      <Ionicons name="chevron-forward" size={18} color={copy.fg} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  banner: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    borderWidth: 1,
    borderRadius: 12,
    padding: 14,
    marginBottom: 12,
  },
  textWrap: { flex: 1 },
  title: { fontSize: 15, fontWeight: "600" },
  subtitle: { fontSize: 13, color: "#475569", marginTop: 2 },
});
