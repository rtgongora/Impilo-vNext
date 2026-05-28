import React, { useEffect, useState } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, Share } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { TextField, LoadingSpinner } from "@impilo/mobile-design-system";
import { appStore } from "../../stores/appStore";
import { enqueueCitizenLongtailPost, verifyCredential } from "../../services/citizenLongtailService";
import { ApiError, NetworkError } from "@impilo/mobile-api-client";
import { loadLastCredentialVerify, saveLastCredentialVerify, type CachedCredentialVerify } from "../../services/citizenLongtailLocalCache";
import { formatWhen } from "../../lib/formatWhen";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_GOLD, APP_GOLD_LIGHT, APP_RED, APP_RED_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3,
} from "../../lib/colors";

export function VerifyCredentialScreen() {
  const [payload, setPayload]           = useState("");
  const [verified, setVerified]         = useState<null | boolean>(null);
  const [queuedOffline, setQueuedOffline] = useState(false);
  const [loading, setLoading]           = useState(false);
  const [error, setError]               = useState<string | null>(null);
  const [lastCheck, setLastCheck]       = useState<CachedCredentialVerify | null>(null);

  useEffect(() => {
    void (async () => {
      const prev = await loadLastCredentialVerify();
      if (prev) setLastCheck(prev);
    })();
  }, []);

  const shareSummary = async () => {
    if (!lastCheck) return;
    try { await Share.share({ message: `Credential check: ${lastCheck.verified ? "valid" : "not valid"} at ${formatWhen(lastCheck.checked_at)}` }); } catch { /* dismissed */ }
  };

  const verify = async () => {
    if (!payload.trim()) return;
    setError(null); setLoading(true); setVerified(null); setQueuedOffline(false);
    try {
      if (!appStore.getState().isOnline) {
        await enqueueCitizenLongtailPost("/internal/v1/mobile/citizen/credential/verify", { payload: payload.trim() }, "citizen_longtail_credential_verify");
        setQueuedOffline(true);
        return;
      }
      const result = await verifyCredential(payload.trim());
      setVerified(result.verified);
      const entry = { payload: payload.trim(), verified: result.verified, checked_at: new Date().toISOString() };
      await saveLastCredentialVerify(entry);
      setLastCheck(entry);
    } catch (e) {
      setError(e instanceof NetworkError ? "Network error — try again or wait until online." : e instanceof ApiError ? e.message : "Something went wrong.");
    } finally { setLoading(false); }
  };

  return (
    <ScrollView style={s.root} contentContainerStyle={s.content} showsVerticalScrollIndicator={false}>
      <View style={s.hero}>
        <View style={s.heroIcon}><Ionicons name="shield-checkmark" size={28} color={APP_GREEN} /></View>
        <Text style={s.heroTitle}>Verify Credential</Text>
        <Text style={s.heroDesc}>Paste a credential payload to verify its authenticity on the Impilo network.</Text>
      </View>

      {lastCheck && verified === null && !queuedOffline ? (
        <View style={[s.prevCard, { borderLeftColor: lastCheck.verified ? APP_GREEN : APP_RED }]}>
          <View style={s.prevLeft}>
            <Text style={s.prevLabel}>Last verification</Text>
            <View style={s.prevResultRow}>
              <Ionicons name={lastCheck.verified ? "checkmark-circle" : "close-circle"} size={16} color={lastCheck.verified ? APP_GREEN : APP_RED} />
              <Text style={[s.prevResult, { color: lastCheck.verified ? APP_GREEN_DARK : APP_RED }]}>
                {lastCheck.verified ? "Valid credential" : "Not valid"}
              </Text>
            </View>
            <Text style={s.prevTime}>{formatWhen(lastCheck.checked_at)}</Text>
          </View>
          <Pressable onPress={shareSummary} style={s.shareSmallBtn} hitSlop={8}>
            <Ionicons name="share-outline" size={16} color={APP_GREEN} />
          </Pressable>
        </View>
      ) : null}

      <View style={s.formCard}>
        <TextField label="Credential Payload" value={payload} onChange={setPayload} placeholder="Paste credential data here" testID="credential-payload" />

        {verified !== null ? (
          <View style={[s.resultCard, { backgroundColor: verified ? APP_GREEN_XLIGHT : APP_RED_LIGHT }]}>
            <Ionicons name={verified ? "checkmark-circle" : "close-circle"} size={32} color={verified ? APP_GREEN : APP_RED} />
            <Text style={[s.resultTitle, { color: verified ? APP_GREEN_DARK : APP_RED }]}>
              {verified ? "Credential is valid" : "Credential is NOT valid"}
            </Text>
            <Text style={s.resultSub}>{verified ? "This credential was verified on the Impilo network." : "This credential could not be verified."}</Text>
          </View>
        ) : queuedOffline ? (
          <View style={[s.resultCard, { backgroundColor: APP_GOLD_LIGHT }]}>
            <Ionicons name="time-outline" size={28} color={APP_GOLD} />
            <Text style={[s.resultTitle, { color: APP_GOLD }]}>Queued for sync</Text>
          </View>
        ) : null}

        {error ? (
          <View style={s.errorBanner}>
            <Ionicons name="alert-circle-outline" size={15} color={APP_RED} />
            <Text style={s.errorText}>{error}</Text>
          </View>
        ) : null}

        <Pressable onPress={() => void verify()} disabled={loading || !payload.trim()} style={[s.actionBtn, (loading || !payload.trim()) && s.actionBtnDisabled]} testID="verify-credential">
          {loading ? <LoadingSpinner size="sm" /> : (
            <>
              <Ionicons name="shield-checkmark-outline" size={17} color="#FFFFFF" />
              <Text style={s.actionBtnText}>{verified !== null ? "Verify Again" : "Verify Credential"}</Text>
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
  prevCard: { flexDirection: "row", alignItems: "center", backgroundColor: APP_SURFACE, borderRadius: 16, padding: 14, borderLeftWidth: 4, shadowColor: "#000", shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.05, shadowRadius: 4, elevation: 2 },
  prevLeft: { flex: 1 },
  prevLabel: { fontSize: 11, fontWeight: "700", color: APP_TEXT_3, textTransform: "uppercase", letterSpacing: 0.5, marginBottom: 4 },
  prevResultRow: { flexDirection: "row", alignItems: "center", gap: 6 },
  prevResult: { fontSize: 14, fontWeight: "700" },
  prevTime: { fontSize: 12, color: APP_TEXT_2, marginTop: 3 },
  shareSmallBtn: { width: 36, height: 36, borderRadius: 10, backgroundColor: APP_GREEN_LIGHT, alignItems: "center", justifyContent: "center" },
  formCard: { backgroundColor: APP_SURFACE, borderRadius: 18, padding: 16, gap: 14, shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 8, elevation: 3 },
  resultCard: { alignItems: "center", borderRadius: 16, padding: 20, gap: 8 },
  resultTitle: { fontSize: 16, fontWeight: "800" },
  resultSub: { fontSize: 13, color: APP_TEXT_2, textAlign: "center" },
  errorBanner: { flexDirection: "row", alignItems: "flex-start", gap: 8, backgroundColor: APP_RED_LIGHT, borderRadius: 12, padding: 12 },
  errorText: { fontSize: 13, color: APP_RED, flex: 1, lineHeight: 17 },
  actionBtn: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, backgroundColor: APP_GREEN, paddingVertical: 14, borderRadius: 14 },
  actionBtnDisabled: { opacity: 0.5 },
  actionBtnText: { fontSize: 15, fontWeight: "700", color: "#FFFFFF" },
});
