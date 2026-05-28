import React, { useState, useCallback } from "react";
import {
  View, Text, ScrollView, Pressable, StyleSheet, Switch,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useToast } from "../../components/Toast";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

interface ChannelPref { push: boolean; sms: boolean; email: boolean }
interface PreferenceGroup {
  id: string;
  label: string;
  description: string;
  icon: React.ComponentProps<typeof Ionicons>["name"];
  color: string;
  bg: string;
  prefs: ChannelPref;
}

const INITIAL_PREFS: PreferenceGroup[] = [
  { id: "appointments",  label: "Appointments",        description: "Reminders for upcoming visits and check-ups",     icon: "calendar-outline",    color: APP_GREEN,      bg: APP_GREEN_LIGHT,  prefs: { push: true,  sms: true,  email: true  } },
  { id: "medications",   label: "Medications",          description: "Dose reminders and refill alerts",                icon: "medical-outline",     color: APP_GREEN_DARK, bg: APP_GREEN_XLIGHT, prefs: { push: true,  sms: true,  email: false } },
  { id: "results",       label: "Lab Results",          description: "Notifications when new results are available",    icon: "flask-outline",       color: APP_GOLD,       bg: APP_GOLD_LIGHT,   prefs: { push: true,  sms: false, email: true  } },
  { id: "emergencies",   label: "Emergency Alerts",     description: "Critical health and SOS alerts",                 icon: "warning-outline",     color: "#DC2626",      bg: "#FEF2F2",        prefs: { push: true,  sms: true,  email: false } },
  { id: "queue",         label: "Queue Updates",        description: "Queue position and call notifications",           icon: "location-outline",    color: APP_GREEN,      bg: APP_GREEN_LIGHT,  prefs: { push: true,  sms: false, email: false } },
  { id: "wellness",      label: "Wellness Tips",        description: "Daily health tips and wellness nudges",           icon: "heart-outline",       color: APP_GOLD,       bg: APP_GOLD_LIGHT,   prefs: { push: false, sms: false, email: false } },
  { id: "news",          label: "News & Announcements", description: "Health news and service announcements",           icon: "newspaper-outline",   color: APP_TEXT_2,     bg: "#EEF2F2",        prefs: { push: false, sms: false, email: false } },
];

const CHANNELS: { key: keyof ChannelPref; label: string; icon: React.ComponentProps<typeof Ionicons>["name"] }[] = [
  { key: "push",  label: "Push",  icon: "phone-portrait-outline" },
  { key: "sms",   label: "SMS",   icon: "chatbubble-outline"     },
  { key: "email", label: "Email", icon: "mail-outline"           },
];

export function CommunicationPreferencesScreen() {
  const toast = useToast();
  const [groups, setGroups] = useState<PreferenceGroup[]>(INITIAL_PREFS);
  const [saving, setSaving] = useState(false);

  const toggle = useCallback((id: string, channel: keyof ChannelPref) => {
    setGroups((prev) =>
      prev.map((g) =>
        g.id === id ? { ...g, prefs: { ...g.prefs, [channel]: !g.prefs[channel] } } : g,
      ),
    );
  }, []);

  const handleSave = useCallback(async () => {
    setSaving(true);
    await new Promise((r) => setTimeout(r, 600));
    setSaving(false);
    toast.success("Communication preferences saved.");
  }, [toast]);

  const pushEnabled  = groups.filter((g) => g.prefs.push).length;
  const smsEnabled   = groups.filter((g) => g.prefs.sms).length;
  const emailEnabled = groups.filter((g) => g.prefs.email).length;

  return (
    <View style={s.root}>
      <ScrollView contentContainerStyle={s.content} showsVerticalScrollIndicator={false}>

        {/* Hero */}
        <View style={s.hero}>
          <View style={s.heroIcon}><Ionicons name="notifications" size={28} color={APP_GREEN} /></View>
          <Text style={s.heroTitle}>Notification Preferences</Text>
          <Text style={s.heroDesc}>Choose how and when Impilo contacts you. You can turn off any channel or category at any time.</Text>
        </View>

        {/* Channel summary */}
        <View style={s.channelRow}>
          {[
            { label: "Push", count: pushEnabled,  icon: "phone-portrait-outline" as const },
            { label: "SMS",  count: smsEnabled,   icon: "chatbubble-outline"     as const },
            { label: "Email",count: emailEnabled, icon: "mail-outline"           as const },
          ].map((ch) => (
            <View key={ch.label} style={s.channelCard}>
              <View style={[s.channelIcon, { backgroundColor: ch.count > 0 ? APP_GREEN_LIGHT : "#EEF2F2" }]}>
                <Ionicons name={ch.icon} size={16} color={ch.count > 0 ? APP_GREEN : APP_TEXT_3} />
              </View>
              <Text style={[s.channelCount, { color: ch.count > 0 ? APP_GREEN : APP_TEXT_3 }]}>{ch.count}</Text>
              <Text style={s.channelLabel}>{ch.label}</Text>
            </View>
          ))}
        </View>

        {/* Column headers */}
        <View style={s.tableHeader}>
          <Text style={s.tableHeaderCategory}>Category</Text>
          {CHANNELS.map((ch) => (
            <View key={ch.key} style={s.tableHeaderChannel}>
              <Ionicons name={ch.icon} size={13} color={APP_TEXT_3} />
              <Text style={s.tableHeaderChannelText}>{ch.label}</Text>
            </View>
          ))}
        </View>

        {/* Preference rows */}
        <View style={s.prefCard}>
          {groups.map((group, idx) => (
            <View key={group.id} style={[s.prefRow, idx < groups.length - 1 && s.rowDivider]}>
              {/* Category */}
              <View style={s.prefCategory}>
                <View style={[s.prefCatIcon, { backgroundColor: group.bg }]}>
                  <Ionicons name={group.icon} size={15} color={group.color} />
                </View>
                <View style={s.prefCatText}>
                  <Text style={s.prefCatLabel}>{group.label}</Text>
                  <Text style={s.prefCatDesc} numberOfLines={1}>{group.description}</Text>
                </View>
              </View>
              {/* Toggles */}
              {CHANNELS.map((ch) => (
                <View key={ch.key} style={s.toggleCell}>
                  <Switch
                    value={group.prefs[ch.key]}
                    onValueChange={() => toggle(group.id, ch.key)}
                    trackColor={{ false: APP_BORDER, true: APP_GREEN_LIGHT }}
                    thumbColor={group.prefs[ch.key] ? APP_GREEN : "#FFFFFF"}
                    style={s.toggleSwitch}
                  />
                </View>
              ))}
            </View>
          ))}
        </View>

        {/* Quiet hours note */}
        <View style={s.quietNote}>
          <Ionicons name="moon-outline" size={14} color={APP_TEXT_3} />
          <Text style={s.quietNoteText}>
            Quiet hours (10 PM – 6 AM) are applied to all non-emergency notifications automatically.
          </Text>
        </View>
      </ScrollView>

      {/* Save button */}
      <View style={s.footer}>
        <Pressable onPress={handleSave} disabled={saving} style={[s.saveBtn, saving && s.saveBtnDisabled]}>
          {saving ? (
            <Text style={s.saveBtnText}>Saving…</Text>
          ) : (
            <>
              <Ionicons name="checkmark-circle-outline" size={16} color="#FFFFFF" />
              <Text style={s.saveBtnText}>Save Preferences</Text>
            </>
          )}
        </Pressable>
      </View>
    </View>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  content: { padding: 16, gap: 14, paddingBottom: 24 },
  hero: { alignItems: "center", paddingVertical: 16, gap: 10 },
  heroIcon: { width: 64, height: 64, borderRadius: 20, backgroundColor: APP_GREEN_LIGHT, alignItems: "center", justifyContent: "center" },
  heroTitle: { fontSize: 20, fontWeight: "800", color: APP_TEXT },
  heroDesc: { fontSize: 13, color: APP_TEXT_2, textAlign: "center", paddingHorizontal: 20, lineHeight: 19 },
  channelRow: { flexDirection: "row", gap: 10 },
  channelCard: { flex: 1, backgroundColor: APP_SURFACE, borderRadius: 14, padding: 12, alignItems: "center", gap: 5, shadowColor: "#000", shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.04, shadowRadius: 3, elevation: 2 },
  channelIcon: { width: 34, height: 34, borderRadius: 10, alignItems: "center", justifyContent: "center" },
  channelCount: { fontSize: 18, fontWeight: "800" },
  channelLabel: { fontSize: 11, fontWeight: "600", color: APP_TEXT_2 },
  tableHeader: { flexDirection: "row", alignItems: "center", paddingHorizontal: 14, paddingVertical: 8 },
  tableHeaderCategory: { flex: 1, fontSize: 11, fontWeight: "700", color: APP_TEXT_2, textTransform: "uppercase", letterSpacing: 0.5 },
  tableHeaderChannel: { width: 56, alignItems: "center", gap: 2 },
  tableHeaderChannelText: { fontSize: 9, fontWeight: "700", color: APP_TEXT_3 },
  prefCard: { backgroundColor: APP_SURFACE, borderRadius: 18, overflow: "hidden", shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 8, elevation: 3 },
  prefRow: { flexDirection: "row", alignItems: "center", paddingHorizontal: 14, paddingVertical: 12 },
  rowDivider: { borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: APP_BORDER },
  prefCategory: { flex: 1, flexDirection: "row", alignItems: "center", gap: 10 },
  prefCatIcon: { width: 34, height: 34, borderRadius: 10, alignItems: "center", justifyContent: "center" },
  prefCatText: { flex: 1 },
  prefCatLabel: { fontSize: 13, fontWeight: "600", color: APP_TEXT },
  prefCatDesc: { fontSize: 10, color: APP_TEXT_3, marginTop: 1 },
  toggleCell: { width: 56, alignItems: "center" },
  toggleSwitch: { transform: [{ scaleX: 0.8 }, { scaleY: 0.8 }] },
  quietNote: { flexDirection: "row", alignItems: "flex-start", gap: 8, backgroundColor: APP_GREEN_XLIGHT, borderRadius: 12, padding: 12 },
  quietNoteText: { fontSize: 12, color: APP_TEXT_2, flex: 1, lineHeight: 17 },
  footer: { padding: 16, paddingBottom: 24, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER, backgroundColor: APP_SURFACE },
  saveBtn: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, backgroundColor: APP_GREEN, paddingVertical: 14, borderRadius: 14 },
  saveBtnDisabled: { opacity: 0.5 },
  saveBtnText: { fontSize: 15, fontWeight: "700", color: "#FFFFFF" },
});
