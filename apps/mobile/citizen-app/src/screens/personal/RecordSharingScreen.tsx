import React, { useEffect, useState } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, Share } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { TextField, LoadingSpinner } from "@impilo/mobile-design-system";
import { appStore, useAppStore } from "../../stores/appStore";
import { enqueueCitizenLongtailPost, issueShareCode } from "../../services/citizenLongtailService";
import { ApiError, NetworkError } from "@impilo/mobile-api-client";
import { loadLastShareIssue, saveLastShareIssue, type CachedShareIssue } from "../../services/citizenLongtailLocalCache";
import { formatWhen } from "../../lib/formatWhen";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_GOLD, APP_GOLD_LIGHT, APP_RED, APP_RED_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

export function RecordSharingScreen() {
  const isOnline = useAppStore((s) => s.isOnline);
  const [purpose, setPurpose]           = useState("");
  const [shareCode, setShareCode]       = useState("");
  const [issued, setIssued]             = useState(false);
  const [queuedOffline, setQueuedOffline] = useState(false);
  const [loading, setLoading]           = useState(false);
  const [error, setError]               = useState<string | null>(null);
  const [lastSaved, setLastSaved]       = useState<CachedShareIssue | null>(null);

  useEffect(() => {
    void (async () => {
      const prev = await loadLastShareIssue();
      if (prev?.code) setLastSaved(prev);
    })();
  }, []);

  const shareOut = async () => {
    if (!shareCode.trim()) return;
    try {
      await Share.share({ message: `Impilo share code: ${shareCode.trim()}${purpose.trim() ? `\nPurpose: ${purpose.trim()}` : ""}` });
    } catch { /* dismissed */ }
  };

  const issue = async () => {
    setError(null); setLoading(true); setQueuedOffline(false);
    try {
      if (!appStore.getState().isOnline) {
        await enqueueCitizenLongtailPost("/internal/v1/mobile/citizen/share/issue", { purpose: purpose.trim() || null }, "citizen_longtail_share_issue");
        setQueuedOffline(true); setIssued(false); setShareCode("");
        return;
      }
      const data = await issueShareCode(purpose.trim() || null);
      setShareCode(data.code); setIssued(true);
      const entry = { code: data.code, issued_at: data.issued_at, purpose: data.purpose ?? (purpose.trim() || null) };
      await saveLastShareIssue(entry);
      setLastSaved(entry);
    } catch (e) {
      setError(e instanceof NetworkError ? "Network error — try again or wait until online." : e instanceof ApiError ? e.message : "Something went wrong. Please try again.");
    } finally { setLoading(false); }
  };

  return (
    <ScrollView style={s.root} contentContainerStyle={s.content} showsVerticalScrollIndicator={false}>
      {/* Hero */}
      <View style={s.hero}>
        <View style={s.heroIcon}><Ionicons name="share-social" size={28} color={APP_GREEN} /></View>
        <Text style={s.heroTitle}>Share My Record</Text>
        <Text style={s.heroDesc}>Generate a one-time share code to give to a provider or caregiver.</Text>
      </View>

      {/* Offline notice */}
      {!isOnline ? (
        <View style={s.offlineBanner}>
          <Ionicons name="cloud-offline-outline" size={15} color={APP_GOLD} />
          <Text style={s.offlineBannerText}>Offline — the request will queue and send when you reconnect.</Text>
        </View>
      ) : null}

      {/* Previous code */}
      {lastSaved && !issued && !queuedOffline ? (
        <View style={s.prevCard}>
          <View style={s.prevLeft}>
            <Text style={s.prevLabel}>Last issued code</Text>
            <Text style={s.prevCode}>{lastSaved.code}</Text>
            <Text style={s.prevTime}>{formatWhen(lastSaved.issued_at)}{lastSaved.purpose ? ` · ${lastSaved.purpose}` : ""}</Text>
          </View>
          <Pressable onPress={shareOut} style={s.shareSmallBtn} hitSlop={8}>
            <Ionicons name="share-outline" size={16} color={APP_GREEN} />
          </Pressable>
        </View>
      ) : null}

      {/* Form */}
      <View style={s.formCard}>
        <TextField label="Purpose (optional)" value={purpose} onChange={setPurpose} placeholder="e.g. Specialist visit at PAGH" testID="share-purpose" />

        {/* Result */}
        {issued && shareCode ? (
          <View style={s.codeCard}>
            <Text style={s.codeLabel}>Your Share Code</Text>
            <Text style={s.codeValue}>{shareCode}</Text>
            <Text style={s.codeHint}>Give this code to your provider. It expires in 24 hours.</Text>
            <Pressable onPress={shareOut} style={s.shareBtn}>
              <Ionicons name="share-outline" size={16} color="#FFFFFF" />
              <Text style={s.shareBtnText}>Share Code</Text>
            </Pressable>
          </View>
        ) : queuedOffline ? (
          <View style={[s.codeCard, { backgroundColor: APP_GOLD_LIGHT }]}>
            <Ionicons name="time-outline" size={24} color={APP_GOLD} />
            <Text style={[s.codeLabel, { color: APP_GOLD }]}>Queued for sync</Text>
            <Text style={s.codeHint}>Will send when you reconnect.</Text>
          </View>
        ) : null}

        {error ? (
          <View style={s.errorBanner}>
            <Ionicons name="alert-circle-outline" size={15} color={APP_RED} />
            <Text style={s.errorText}>{error}</Text>
          </View>
        ) : null}

        <Pressable onPress={() => void issue()} disabled={loading} style={[s.actionBtn, loading && s.actionBtnDisabled]} testID="issue-share-code">
          {loading ? <LoadingSpinner size="sm" /> : (
            <>
              <Ionicons name={issued ? "refresh-outline" : "key-outline"} size={17} color="#FFFFFF" />
              <Text style={s.actionBtnText}>{issued ? "Re-issue Code" : queuedOffline ? "Queue Again" : "Issue Share Code"}</Text>
            </>
          )}
        </Pressable>
      </View>
    </ScrollView>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  content: { padding: 16, gap: 14, paddingBottom: 40 },
  hero: { alignItems: "center", paddingVertical: 20, gap: 10 },
  heroIcon: { width: 64, height: 64, borderRadius: 20, backgroundColor: APP_GREEN_LIGHT, alignItems: "center", justifyContent: "center" },
  heroTitle: { fontSize: 20, fontWeight: "800", color: APP_TEXT },
  heroDesc: { fontSize: 13, color: APP_TEXT_2, textAlign: "center", paddingHorizontal: 24, lineHeight: 19 },
  offlineBanner: { flexDirection: "row", alignItems: "center", gap: 8, backgroundColor: APP_GOLD_LIGHT, borderRadius: 12, padding: 12, borderLeftWidth: 3, borderLeftColor: APP_GOLD },
  offlineBannerText: { fontSize: 13, color: APP_GOLD, flex: 1, lineHeight: 17 },
  prevCard: { flexDirection: "row", alignItems: "center", backgroundColor: APP_SURFACE, borderRadius: 16, padding: 14, borderLeftWidth: 4, borderLeftColor: APP_GREEN, shadowColor: "#000", shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.05, shadowRadius: 4, elevation: 2 },
  prevLeft: { flex: 1 },
  prevLabel: { fontSize: 11, fontWeight: "700", color: APP_TEXT_3, textTransform: "uppercase", letterSpacing: 0.5 },
  prevCode: { fontSize: 18, fontWeight: "800", color: APP_TEXT, fontFamily: "monospace", marginVertical: 3 },
  prevTime: { fontSize: 12, color: APP_TEXT_2 },
  shareSmallBtn: { width: 36, height: 36, borderRadius: 10, backgroundColor: APP_GREEN_LIGHT, alignItems: "center", justifyContent: "center" },
  formCard: { backgroundColor: APP_SURFACE, borderRadius: 18, padding: 16, gap: 14, shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 8, elevation: 3 },
  codeCard: { alignItems: "center", backgroundColor: APP_GREEN_XLIGHT, borderRadius: 16, padding: 20, gap: 8 },
  codeLabel: { fontSize: 11, fontWeight: "700", color: APP_GREEN_DARK, textTransform: "uppercase", letterSpacing: 0.8 },
  codeValue: { fontSize: 28, fontWeight: "900", color: APP_GREEN, fontFamily: "monospace", letterSpacing: 2 },
  codeHint: { fontSize: 12, color: APP_TEXT_2, textAlign: "center" },
  shareBtn: { flexDirection: "row", alignItems: "center", gap: 6, backgroundColor: APP_GREEN, paddingHorizontal: 20, paddingVertical: 10, borderRadius: 12, marginTop: 4 },
  shareBtnText: { fontSize: 13, fontWeight: "700", color: "#FFFFFF" },
  errorBanner: { flexDirection: "row", alignItems: "flex-start", gap: 8, backgroundColor: APP_RED_LIGHT, borderRadius: 12, padding: 12 },
  errorText: { fontSize: 13, color: APP_RED, flex: 1, lineHeight: 17 },
  actionBtn: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, backgroundColor: APP_GREEN, paddingVertical: 14, borderRadius: 14 },
  actionBtnDisabled: { opacity: 0.5 },
  actionBtnText: { fontSize: 15, fontWeight: "700", color: "#FFFFFF" },
});
