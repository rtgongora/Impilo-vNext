import React, { useEffect, useState } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, Share } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { TextField, LoadingSpinner } from "@impilo/mobile-design-system";
import { appStore } from "../../stores/appStore";
import { enqueueCitizenLongtailPost, issueDelegatedPickup } from "../../services/citizenLongtailService";
import { ApiError, NetworkError } from "@impilo/mobile-api-client";
import { loadLastDelegatedPickup, saveLastDelegatedPickup, type CachedDelegatedPickup } from "../../services/citizenLongtailLocalCache";
import { formatWhen } from "../../lib/formatWhen";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_GOLD, APP_GOLD_LIGHT, APP_RED, APP_RED_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3,
} from "../../lib/colors";

export function DelegatedPickupScreen() {
  const [name, setName]                 = useState("");
  const [phone, setPhone]               = useState("");
  const [token, setToken]               = useState("");
  const [issued, setIssued]             = useState(false);
  const [queuedOffline, setQueuedOffline] = useState(false);
  const [loading, setLoading]           = useState(false);
  const [error, setError]               = useState<string | null>(null);
  const [lastAuth, setLastAuth]         = useState<CachedDelegatedPickup | null>(null);

  useEffect(() => {
    void (async () => {
      const prev = await loadLastDelegatedPickup();
      if (prev?.token) { setLastAuth(prev); setName(prev.delegate_name); setPhone(prev.delegate_phone); }
    })();
  }, []);

  const shareToken = async () => {
    if (!token.trim()) return;
    try {
      await Share.share({ message: `Impilo delegation token: ${token}\nDelegate: ${name}${phone ? ` (${phone})` : ""}` });
    } catch { /* dismissed */ }
  };

  const issue = async () => {
    if (!name.trim()) return;
    setError(null); setLoading(true); setQueuedOffline(false);
    try {
      if (!appStore.getState().isOnline) {
        await enqueueCitizenLongtailPost("/internal/v1/mobile/citizen/delegated-pickup/issue", { delegate_name: name.trim(), delegate_phone: phone.trim() || null }, "citizen_longtail_delegated_pickup");
        setQueuedOffline(true); setIssued(false); setToken("");
        return;
      }
      const data = await issueDelegatedPickup(name.trim(), phone.trim() || "");
      setToken(data.token); setIssued(true);
      const entry = { token: data.token, issued_at: data.issued_at, delegate_name: name.trim(), delegate_phone: phone.trim() };
      await saveLastDelegatedPickup(entry);
      setLastAuth(entry);
    } catch (e) {
      setError(e instanceof NetworkError ? "Network error — try again or wait until online." : e instanceof ApiError ? e.message : "Something went wrong.");
    } finally { setLoading(false); }
  };

  return (
    <ScrollView style={s.root} contentContainerStyle={s.content} showsVerticalScrollIndicator={false}>
      <View style={s.hero}>
        <View style={s.heroIcon}><Ionicons name="car" size={28} color={APP_GREEN} /></View>
        <Text style={s.heroTitle}>Delegated Pickup</Text>
        <Text style={s.heroDesc}>Authorise someone to collect your medication or documents on your behalf.</Text>
      </View>

      {lastAuth && !issued && !queuedOffline ? (
        <View style={s.prevCard}>
          <View style={s.prevLeft}>
            <Text style={s.prevLabel}>Last delegation</Text>
            <Text style={s.prevName}>{lastAuth.delegate_name}</Text>
            {lastAuth.delegate_phone ? <Text style={s.prevPhone}>{lastAuth.delegate_phone}</Text> : null}
            <Text style={s.prevTime}>{formatWhen(lastAuth.issued_at)}</Text>
          </View>
          {lastAuth.token ? (
            <Pressable onPress={shareToken} style={s.shareSmallBtn} hitSlop={8}>
              <Ionicons name="share-outline" size={16} color={APP_GREEN} />
            </Pressable>
          ) : null}
        </View>
      ) : null}

      <View style={s.formCard}>
        <TextField label="Delegate Full Name" value={name} onChange={setName} placeholder="e.g. Grace Citizen" testID="delegate-name" />
        <TextField label="Delegate Phone (optional)" value={phone} onChange={setPhone} placeholder="+263 7X XXX XXXX" testID="delegate-phone" />

        {issued && token ? (
          <View style={s.tokenCard}>
            <Text style={s.tokenLabel}>Delegation Token</Text>
            <Text style={s.tokenValue}>{token}</Text>
            <Text style={s.tokenHint}>{name} can present this token to collect on your behalf.</Text>
            <Pressable onPress={shareToken} style={s.shareBtn}>
              <Ionicons name="share-outline" size={16} color="#FFFFFF" />
              <Text style={s.shareBtnText}>Share Token</Text>
            </Pressable>
          </View>
        ) : queuedOffline ? (
          <View style={[s.tokenCard, { backgroundColor: APP_GOLD_LIGHT }]}>
            <Ionicons name="time-outline" size={28} color={APP_GOLD} />
            <Text style={[s.tokenLabel, { color: APP_GOLD }]}>Queued for sync</Text>
          </View>
        ) : null}

        {error ? (
          <View style={s.errorBanner}>
            <Ionicons name="alert-circle-outline" size={15} color={APP_RED} />
            <Text style={s.errorText}>{error}</Text>
          </View>
        ) : null}

        <Pressable onPress={() => void issue()} disabled={loading || !name.trim()} style={[s.actionBtn, (loading || !name.trim()) && s.actionBtnDisabled]} testID="issue-delegation">
          {loading ? <LoadingSpinner size="sm" /> : (
            <>
              <Ionicons name="car-outline" size={17} color="#FFFFFF" />
              <Text style={s.actionBtnText}>{issued ? "Re-issue Token" : queuedOffline ? "Queue Again" : "Issue Delegation"}</Text>
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
  prevLabel: { fontSize: 11, fontWeight: "700", color: APP_TEXT_3, textTransform: "uppercase", letterSpacing: 0.5, marginBottom: 4 },
  prevName: { fontSize: 16, fontWeight: "700", color: APP_TEXT },
  prevPhone: { fontSize: 13, color: APP_TEXT_2 },
  prevTime: { fontSize: 12, color: APP_TEXT_3, marginTop: 3 },
  shareSmallBtn: { width: 36, height: 36, borderRadius: 10, backgroundColor: APP_GREEN_LIGHT, alignItems: "center", justifyContent: "center" },
  formCard: { backgroundColor: APP_SURFACE, borderRadius: 18, padding: 16, gap: 14, shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 8, elevation: 3 },
  tokenCard: { alignItems: "center", backgroundColor: APP_GREEN_XLIGHT, borderRadius: 16, padding: 20, gap: 8 },
  tokenLabel: { fontSize: 11, fontWeight: "700", color: APP_GREEN_DARK, textTransform: "uppercase", letterSpacing: 0.8 },
  tokenValue: { fontSize: 22, fontWeight: "800", color: APP_GREEN, fontFamily: "monospace", letterSpacing: 1.5, textAlign: "center" },
  tokenHint: { fontSize: 12, color: APP_TEXT_2, textAlign: "center" },
  shareBtn: { flexDirection: "row", alignItems: "center", gap: 6, backgroundColor: APP_GREEN, paddingHorizontal: 20, paddingVertical: 10, borderRadius: 12, marginTop: 4 },
  shareBtnText: { fontSize: 13, fontWeight: "700", color: "#FFFFFF" },
  errorBanner: { flexDirection: "row", alignItems: "flex-start", gap: 8, backgroundColor: APP_RED_LIGHT, borderRadius: 12, padding: 12 },
  errorText: { fontSize: 13, color: APP_RED, flex: 1, lineHeight: 17 },
  actionBtn: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, backgroundColor: APP_GREEN, paddingVertical: 14, borderRadius: 14 },
  actionBtnDisabled: { opacity: 0.5 },
  actionBtnText: { fontSize: 15, fontWeight: "700", color: "#FFFFFF" },
});
