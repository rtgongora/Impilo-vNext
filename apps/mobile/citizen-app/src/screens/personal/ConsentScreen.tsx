import React, { useState, useEffect, useCallback, useRef } from "react";
import {
  View, Text, ScrollView, Pressable, StyleSheet,
  Switch, RefreshControl, Alert,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import { getConsents, updateConsent, type Consent } from "../../services/consentService";
import { USE_SEED_DATA } from "../../data/seedHealthRecords";
import { useToast } from "../../components/Toast";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT, APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

const SEED_CONSENTS: Consent[] = [
  { id: "c-001", category: "Medical Records Sharing",     description: "Allow sharing of your medical records with authorised healthcare providers within the Impilo network.",        granted: true,  updatedAt: new Date(Date.now() - 365 * 86_400_000).toISOString() },
  { id: "c-002", category: "Research Participation",      description: "Permit anonymised data to be used in approved medical research studies approved by the MoHCC research ethics board.", granted: false, updatedAt: new Date(Date.now() - 180 * 86_400_000).toISOString() },
  { id: "c-003", category: "Emergency Access",            description: "Grant emergency responders access to critical health information (blood type, allergies, medications) in life-threatening situations.", granted: true,  updatedAt: new Date(Date.now() - 365 * 86_400_000).toISOString() },
  { id: "c-004", category: "Third-Party Provider Access", description: "Allow third-party private healthcare providers, pharmacies, and specialists to view your health data when treating you.", granted: true,  updatedAt: new Date(Date.now() - 90 * 86_400_000).toISOString() },
  { id: "c-005", category: "Telehealth Recording",        description: "Permit recording of telehealth consultations for quality assurance, medical records, and clinical audit purposes.", granted: false, updatedAt: new Date(Date.now() - 30 * 86_400_000).toISOString() },
];

const CATEGORY_ICON: Record<string, React.ComponentProps<typeof Ionicons>["name"]> = {
  "Medical Records Sharing":     "folder-open-outline",
  "Research Participation":      "flask-outline",
  "Emergency Access":            "warning-outline",
  "Third-Party Provider Access": "people-outline",
  "Telehealth Recording":        "videocam-outline",
};

export function ConsentScreen() {
  const toast    = useToast();
  const toastRef = useRef(toast);
  toastRef.current = toast;

  const [consents, setConsents]           = useState<Consent[]>([]);
  const [isLoading, setIsLoading]         = useState(true);
  const [isRefreshing, setIsRefreshing]   = useState(false);
  const [isSaving, setIsSaving]           = useState(false);
  const [pendingChanges, setPendingChanges] = useState<Record<string, boolean>>({});

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    try {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 350));
        setConsents(SEED_CONSENTS);
      } else {
        const data = await getConsents();
        setConsents(data);
      }
      setPendingChanges({});
    } catch {
      toastRef.current.error("Could not load consent preferences.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  const onRefresh = useCallback(() => { setIsRefreshing(true); void load(true); }, [load]);

  const handleToggle = useCallback((id: string, newValue: boolean) => {
    setPendingChanges((prev) => ({ ...prev, [id]: newValue }));
  }, []);

  const effectiveValue = useCallback((c: Consent) =>
    pendingChanges[c.id] !== undefined ? pendingChanges[c.id] : c.granted,
  [pendingChanges]);

  const hasPending = Object.keys(pendingChanges).length > 0;

  const handleSave = useCallback(async () => {
    if (!hasPending) return;
    setIsSaving(true);
    try {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 600));
        setConsents((prev) => prev.map((c) =>
          pendingChanges[c.id] !== undefined ? { ...c, granted: pendingChanges[c.id]!, updatedAt: new Date().toISOString() } : c,
        ));
        setPendingChanges({});
        toastRef.current.success("Consent preferences saved.");
        return;
      }
      const updated = await Promise.all(
        Object.entries(pendingChanges).map(([id, val]) => updateConsent(id, val)),
      );
      setConsents((prev) => prev.map((c) => updated.find((u) => u.id === c.id) ?? c));
      setPendingChanges({});
      toastRef.current.success("Consent preferences saved.");
    } catch {
      toastRef.current.error("Could not save changes. Please try again.");
    } finally {
      setIsSaving(false);
    }
  }, [hasPending, pendingChanges]);

  const handleDiscard = useCallback(() => {
    Alert.alert("Discard Changes", "Discard unsaved changes?", [
      { text: "Keep editing", style: "cancel" },
      { text: "Discard", style: "destructive", onPress: () => setPendingChanges({}) },
    ]);
  }, []);

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  const grantedCount = consents.filter((c) => effectiveValue(c)).length;

  return (
    <View style={s.root}>
      <ScrollView
        contentContainerStyle={s.content}
        showsVerticalScrollIndicator={false}
        refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} tintColor={APP_GREEN} colors={[APP_GREEN]} />}
      >
        {/* Header */}
        <View style={s.hero}>
          <View style={s.heroIcon}><Ionicons name="shield-checkmark" size={28} color={APP_GREEN} /></View>
          <Text style={s.heroTitle}>Data Consent</Text>
          <Text style={s.heroDesc}>Control how your health data is shared within the Impilo network. You can change these at any time.</Text>
        </View>

        {/* Summary */}
        <View style={s.summaryRow}>
          <View style={[s.summaryCard, { backgroundColor: APP_GREEN_LIGHT }]}>
            <Text style={[s.summaryNum, { color: APP_GREEN_DARK }]}>{grantedCount}</Text>
            <Text style={s.summaryLbl}>Consented</Text>
          </View>
          <View style={[s.summaryCard, { backgroundColor: "#EEF2F2" }]}>
            <Text style={[s.summaryNum, { color: APP_TEXT_2 }]}>{consents.length - grantedCount}</Text>
            <Text style={s.summaryLbl}>Declined</Text>
          </View>
          <View style={[s.summaryCard, { backgroundColor: hasPending ? APP_GOLD_LIGHT : APP_GREEN_XLIGHT }]}>
            <Text style={[s.summaryNum, { color: hasPending ? APP_GOLD : APP_GREEN }]}>{Object.keys(pendingChanges).length}</Text>
            <Text style={s.summaryLbl}>Unsaved</Text>
          </View>
        </View>

        {/* Pending changes banner */}
        {hasPending ? (
          <View style={s.pendingBanner}>
            <Ionicons name="time-outline" size={15} color={APP_GOLD} />
            <Text style={s.pendingText}>{Object.keys(pendingChanges).length} unsaved change{Object.keys(pendingChanges).length > 1 ? "s" : ""}. Review and save below.</Text>
          </View>
        ) : null}

        {/* Consent items */}
        <View style={s.listCard}>
          {consents.map((c, idx) => {
            const value    = effectiveValue(c);
            const changed  = pendingChanges[c.id] !== undefined;
            const icon     = CATEGORY_ICON[c.category] ?? "lock-closed-outline";
            return (
              <View key={c.id} style={[s.consentRow, idx < consents.length - 1 && s.rowDivider]}>
                <View style={[s.consentIcon, { backgroundColor: value ? APP_GREEN_LIGHT : "#EEF2F2" }]}>
                  <Ionicons name={icon} size={18} color={value ? APP_GREEN : APP_TEXT_3} />
                </View>
                <View style={s.consentBody}>
                  <View style={s.consentTitleRow}>
                    <Text style={s.consentCategory}>{c.category}</Text>
                    {changed ? (
                      <View style={s.changedPill}>
                        <Text style={s.changedText}>changed</Text>
                      </View>
                    ) : null}
                  </View>
                  <Text style={s.consentDesc}>{c.description}</Text>
                  <Text style={s.consentDate}>
                    Last updated {new Date(c.updatedAt).toLocaleDateString()}
                  </Text>
                </View>
                <Switch
                  value={value}
                  onValueChange={(v) => handleToggle(c.id, v)}
                  trackColor={{ false: APP_BORDER, true: APP_GREEN_LIGHT }}
                  thumbColor={value ? APP_GREEN : "#FFFFFF"}
                />
              </View>
            );
          })}
        </View>

        {/* Legal note */}
        <View style={s.legalNote}>
          <Ionicons name="information-circle-outline" size={14} color={APP_TEXT_3} />
          <Text style={s.legalNoteText}>
            Your data is protected under the Zimbabwe Cyber and Data Protection Act (2021) and Impilo Data Governance Policy. Consent changes are logged and audited.
          </Text>
        </View>
      </ScrollView>

      {/* Save footer */}
      {hasPending ? (
        <View style={s.footer}>
          <Pressable onPress={handleDiscard} style={s.discardBtn}>
            <Text style={s.discardBtnText}>Discard</Text>
          </Pressable>
          <Pressable onPress={handleSave} disabled={isSaving} style={[s.saveBtn, isSaving && s.saveBtnDisabled]}>
            {isSaving ? <LoadingSpinner size="sm" /> : (
              <>
                <Ionicons name="checkmark-circle-outline" size={16} color="#FFFFFF" />
                <Text style={s.saveBtnText}>Save Changes</Text>
              </>
            )}
          </Pressable>
        </View>
      ) : null}
    </View>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  centred: { flex: 1, alignItems: "center", justifyContent: "center" },
  content: { padding: 16, gap: 14, paddingBottom: 32 },
  hero: { alignItems: "center", paddingVertical: 16, gap: 10 },
  heroIcon: { width: 64, height: 64, borderRadius: 20, backgroundColor: APP_GREEN_LIGHT, alignItems: "center", justifyContent: "center" },
  heroTitle: { fontSize: 20, fontWeight: "800", color: APP_TEXT },
  heroDesc: { fontSize: 13, color: APP_TEXT_2, textAlign: "center", paddingHorizontal: 20, lineHeight: 19 },
  summaryRow: { flexDirection: "row", gap: 10 },
  summaryCard: { flex: 1, borderRadius: 14, padding: 14, alignItems: "center", gap: 4 },
  summaryNum: { fontSize: 22, fontWeight: "800" },
  summaryLbl: { fontSize: 11, fontWeight: "600", color: APP_TEXT_2 },
  pendingBanner: { flexDirection: "row", alignItems: "center", gap: 8, backgroundColor: APP_GOLD_LIGHT, borderRadius: 12, padding: 12, borderLeftWidth: 3, borderLeftColor: APP_GOLD },
  pendingText: { fontSize: 13, color: APP_GOLD, flex: 1 },
  listCard: { backgroundColor: APP_SURFACE, borderRadius: 18, overflow: "hidden", shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 8, elevation: 3 },
  consentRow: { flexDirection: "row", alignItems: "flex-start", gap: 12, paddingHorizontal: 16, paddingVertical: 16 },
  rowDivider: { borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: APP_BORDER },
  consentIcon: { width: 42, height: 42, borderRadius: 12, alignItems: "center", justifyContent: "center", marginTop: 2 },
  consentBody: { flex: 1 },
  consentTitleRow: { flexDirection: "row", alignItems: "center", gap: 8, marginBottom: 4 },
  consentCategory: { fontSize: 14, fontWeight: "700", color: APP_TEXT, flex: 1 },
  changedPill: { backgroundColor: APP_GOLD_LIGHT, paddingHorizontal: 7, paddingVertical: 2, borderRadius: 8 },
  changedText: { fontSize: 10, fontWeight: "700", color: APP_GOLD },
  consentDesc: { fontSize: 12, color: APP_TEXT_2, lineHeight: 17, marginBottom: 4 },
  consentDate: { fontSize: 10, color: APP_TEXT_3 },
  legalNote: { flexDirection: "row", alignItems: "flex-start", gap: 8, backgroundColor: APP_GREEN_XLIGHT, borderRadius: 12, padding: 12 },
  legalNoteText: { fontSize: 12, color: APP_TEXT_2, flex: 1, lineHeight: 17 },
  footer: { flexDirection: "row", gap: 10, padding: 16, paddingBottom: 24, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER, backgroundColor: APP_SURFACE },
  discardBtn: { paddingHorizontal: 20, paddingVertical: 14, borderRadius: 14, backgroundColor: "#EEF2F2" },
  discardBtnText: { fontSize: 14, fontWeight: "600", color: APP_TEXT_2 },
  saveBtn: { flex: 1, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, backgroundColor: APP_GREEN, paddingVertical: 14, borderRadius: 14 },
  saveBtnDisabled: { opacity: 0.5 },
  saveBtnText: { fontSize: 15, fontWeight: "700", color: "#FFFFFF" },
});
