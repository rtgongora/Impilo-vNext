import React, { useEffect, useState } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, Share } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { TextField, LoadingSpinner } from "@impilo/mobile-design-system";
import { appStore } from "../../stores/appStore";
import { claimSharedDocuments, enqueueCitizenLongtailPost } from "../../services/citizenLongtailService";
import { ApiError, NetworkError } from "@impilo/mobile-api-client";
import { loadLastShareClaim, saveLastShareClaim, type CachedShareClaim } from "../../services/citizenLongtailLocalCache";
import { formatWhen } from "../../lib/formatWhen";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_GOLD, APP_GOLD_LIGHT, APP_RED, APP_RED_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3,
} from "../../lib/colors";

export function ClaimSharedDocumentsScreen() {
  const [code, setCode]                   = useState("");
  const [claimed, setClaimed]             = useState(false);
  const [queuedOffline, setQueuedOffline] = useState(false);
  const [loading, setLoading]             = useState(false);
  const [error, setError]                 = useState<string | null>(null);
  const [lastClaim, setLastClaim]         = useState<CachedShareClaim | null>(null);

  useEffect(() => {
    void (async () => {
      const prev = await loadLastShareClaim();
      if (prev?.code) setLastClaim(prev);
    })();
  }, []);

  const shareClaimMeta = async () => {
    if (!lastClaim?.code) return;
    try { await Share.share({ message: `Claimed share code ${lastClaim.code} on ${formatWhen(lastClaim.claimed_at)}` }); } catch { /* dismissed */ }
  };

  const claim = async () => {
    if (!code.trim()) return;
    setError(null); setLoading(true); setQueuedOffline(false);
    try {
      if (!appStore.getState().isOnline) {
        await enqueueCitizenLongtailPost("/internal/v1/mobile/citizen/share/claim", { code: code.trim() }, "citizen_longtail_share_claim");
        setQueuedOffline(true); setClaimed(false);
        return;
      }
      await claimSharedDocuments(code.trim());
      setClaimed(true);
      const entry = { code: code.trim(), claimed_at: new Date().toISOString() };
      await saveLastShareClaim(entry);
      setLastClaim(entry);
    } catch (e) {
      setError(e instanceof NetworkError ? "Network error — try again or wait until online." : e instanceof ApiError ? e.message : "Something went wrong.");
    } finally { setLoading(false); }
  };

  return (
    <ScrollView style={s.root} contentContainerStyle={s.content} showsVerticalScrollIndicator={false}>
      <View style={s.hero}>
        <View style={s.heroIcon}><Ionicons name="download" size={28} color={APP_GREEN} /></View>
        <Text style={s.heroTitle}>Claim Documents</Text>
        <Text style={s.heroDesc}>Enter a share code from a provider or patient to claim shared medical documents.</Text>
      </View>

      {lastClaim && !claimed && !queuedOffline ? (
        <View style={s.prevCard}>
          <View style={s.prevLeft}>
            <Text style={s.prevLabel}>Last claimed</Text>
            <Text style={s.prevCode}>{lastClaim.code}</Text>
            <Text style={s.prevTime}>{formatWhen(lastClaim.claimed_at)}</Text>
          </View>
          <Pressable onPress={shareClaimMeta} style={s.shareSmallBtn} hitSlop={8}>
            <Ionicons name="share-outline" size={16} color={APP_GREEN} />
          </Pressable>
        </View>
      ) : null}

      <View style={s.formCard}>
        <TextField label="Share Code" value={code} onChange={setCode} placeholder="Enter code from provider" testID="claim-code" />

        {claimed ? (
          <View style={s.successCard}>
            <Ionicons name="checkmark-circle" size={32} color={APP_GREEN} />
            <Text style={s.successTitle}>Documents claimed!</Text>
            <Text style={s.successSub}>The shared documents have been added to your records.</Text>
          </View>
        ) : queuedOffline ? (
          <View style={[s.successCard, { backgroundColor: APP_GOLD_LIGHT }]}>
            <Ionicons name="time-outline" size={28} color={APP_GOLD} />
            <Text style={[s.successTitle, { color: APP_GOLD }]}>Queued for sync</Text>
          </View>
        ) : null}

        {error ? (
          <View style={s.errorBanner}>
            <Ionicons name="alert-circle-outline" size={15} color={APP_RED} />
            <Text style={s.errorText}>{error}</Text>
          </View>
        ) : null}

        <Pressable onPress={() => void claim()} disabled={loading || !code.trim()} style={[s.actionBtn, (loading || !code.trim()) && s.actionBtnDisabled]} testID="claim-documents">
          {loading ? <LoadingSpinner size="sm" /> : (
            <>
              <Ionicons name="download-outline" size={17} color="#FFFFFF" />
              <Text style={s.actionBtnText}>{queuedOffline ? "Queue Again" : "Claim Documents"}</Text>
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
  prevCard: { flexDirection: "row", alignItems: "center", backgroundColor: APP_SURFACE, borderRadius: 16, padding: 14, borderLeftWidth: 4, borderLeftColor: APP_GREEN, shadowColor: "#000", shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.05, shadowRadius: 4, elevation: 2 },
  prevLeft: { flex: 1 },
  prevLabel: { fontSize: 11, fontWeight: "700", color: APP_TEXT_3, textTransform: "uppercase", letterSpacing: 0.5 },
  prevCode: { fontSize: 18, fontWeight: "800", color: APP_TEXT, fontFamily: "monospace", marginVertical: 3 },
  prevTime: { fontSize: 12, color: APP_TEXT_2 },
  shareSmallBtn: { width: 36, height: 36, borderRadius: 10, backgroundColor: APP_GREEN_LIGHT, alignItems: "center", justifyContent: "center" },
  formCard: { backgroundColor: APP_SURFACE, borderRadius: 18, padding: 16, gap: 14, shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 8, elevation: 3 },
  successCard: { alignItems: "center", backgroundColor: APP_GREEN_XLIGHT, borderRadius: 16, padding: 20, gap: 8 },
  successTitle: { fontSize: 16, fontWeight: "800", color: APP_GREEN_DARK },
  successSub: { fontSize: 13, color: APP_TEXT_2, textAlign: "center" },
  errorBanner: { flexDirection: "row", alignItems: "flex-start", gap: 8, backgroundColor: APP_RED_LIGHT, borderRadius: 12, padding: 12 },
  errorText: { fontSize: 13, color: APP_RED, flex: 1, lineHeight: 17 },
  actionBtn: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, backgroundColor: APP_GREEN, paddingVertical: 14, borderRadius: 14 },
  actionBtnDisabled: { opacity: 0.5 },
  actionBtnText: { fontSize: 15, fontWeight: "700", color: "#FFFFFF" },
});
