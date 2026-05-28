import React, { useState, useCallback } from "react";
import {
  View, Text, ScrollView, Pressable, StyleSheet, Alert,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useAuth } from "@impilo/mobile-auth";
import { useAppStore } from "../../stores/appStore";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

const APP_VERSION = "1.0.0-beta";

interface SettingRow {
  id: string;
  label: string;
  description?: string;
  icon: React.ComponentProps<typeof Ionicons>["name"];
  color: string;
  bg: string;
  onPress: () => void;
  badge?: string;
  destructive?: boolean;
}

interface SettingGroup {
  title: string;
  items: SettingRow[];
}

export function SettingsSection() {
  const auth    = useAuth();
  const profile = useAppStore((s) => s.profile);
  const [signingOut, setSigningOut] = useState(false);

  const handleSignOut = useCallback(() => {
    Alert.alert(
      "Sign Out",
      "Are you sure you want to sign out of Impilo?",
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Sign Out",
          style: "destructive",
          onPress: async () => {
            setSigningOut(true);
            try { await auth.logout(); } catch { setSigningOut(false); }
          },
        },
      ],
    );
  }, [auth]);

  const handleDeleteAccount = useCallback(() => {
    Alert.alert(
      "Delete Account",
      "This will permanently delete your Impilo account and all associated data. This action cannot be undone.",
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Delete Account",
          style: "destructive",
          onPress: () => {
            Alert.alert("Confirm Deletion", "Please contact support@impilo.gov.zw or call 0800 IMPILO to complete account deletion.");
          },
        },
      ],
    );
  }, []);

  return (
    <ScrollView style={s.root} contentContainerStyle={s.content} showsVerticalScrollIndicator={false}>
      {/* Profile summary */}
      <View style={s.profileCard}>
        <View style={s.profileAvatar}>
          <Text style={s.profileAvatarText}>
            {profile ? `${profile.givenName[0]}${profile.familyName[0]}` : "?"}
          </Text>
        </View>
        <View style={s.profileInfo}>
          <Text style={s.profileName}>{profile ? `${profile.givenName} ${profile.familyName}` : "—"}</Text>
          {profile?.email ? <Text style={s.profileEmail}>{profile.email}</Text> : null}
          {profile?.cpid ? <Text style={s.profileCpid}>{profile.cpid}</Text> : null}
        </View>
        <Ionicons name="chevron-forward" size={16} color={APP_TEXT_3} />
      </View>

      {/* Settings groups */}
      {(([
        {
          title: "Account",
          items: [
            { id: "profile",   label: "My Profile",   description: "View and edit personal details", icon: "person-outline"   as const, color: APP_GREEN,      bg: APP_GREEN_LIGHT,  onPress: () => {} },
            { id: "security",  label: "Security",     description: "PIN, biometrics, sessions",      icon: "lock-closed-outline" as const, color: APP_GREEN_DARK, bg: APP_GREEN_XLIGHT, onPress: () => {} },
            { id: "language",  label: "Language",     description: "English (en)",                   icon: "language-outline" as const, color: APP_TEXT_2,     bg: "#EEF2F2",        onPress: () => {}, badge: "EN" },
          ],
        },
        {
          title: "Preferences",
          items: [
            { id: "notifs",    label: "Notifications",    description: "Manage push, SMS and email",       icon: "notifications-outline" as const,  color: APP_GREEN,      bg: APP_GREEN_LIGHT,  onPress: () => {} },
            { id: "consent",   label: "Data Consent",     description: "Manage data sharing permissions",  icon: "shield-outline"        as const,  color: APP_GREEN_DARK, bg: APP_GREEN_XLIGHT, onPress: () => {} },
            { id: "privacy",   label: "Privacy Policy",   description: "How we use your data",             icon: "document-text-outline" as const,  color: APP_TEXT_2,     bg: "#EEF2F2",        onPress: () => {} },
            { id: "terms",     label: "Terms of Use",     description: "Usage agreement",                  icon: "clipboard-outline"     as const,  color: APP_TEXT_2,     bg: "#EEF2F2",        onPress: () => {} },
          ],
        },
        {
          title: "Support",
          items: [
            { id: "help",      label: "Help & Support",   description: "FAQ and support tickets",          icon: "help-circle-outline"   as const,  color: APP_GREEN,      bg: APP_GREEN_LIGHT,  onPress: () => {} },
            { id: "feedback",  label: "Send Feedback",    description: "Rate or report an issue",          icon: "chatbubble-outline"    as const,  color: APP_TEXT_2,     bg: "#EEF2F2",        onPress: () => {} },
          ],
        },
      ] as { title: string; items: SettingRow[] }[])).map((group) => (
        <View key={group.title}>
          <Text style={s.groupTitle}>{group.title}</Text>
          <View style={s.groupCard}>
            {group.items.map((item, idx) => (
              <View key={item.id}>
                <Pressable
                  onPress={item.onPress}
                  style={({ pressed }) => [s.settingRow, pressed && s.settingRowPressed]}
                >
                  <View style={[s.settingIcon, { backgroundColor: item.bg }]}>
                    <Ionicons name={item.icon} size={18} color={item.color} />
                  </View>
                  <View style={s.settingBody}>
                    <Text style={s.settingLabel}>{item.label}</Text>
                    {item.description ? <Text style={s.settingDesc}>{item.description}</Text> : null}
                  </View>
                  {item.badge ? (
                    <View style={s.badgePill}>
                      <Text style={s.badgePillText}>{item.badge}</Text>
                    </View>
                  ) : null}
                  <Ionicons name="chevron-forward" size={15} color={APP_TEXT_3} />
                </Pressable>
                {idx < group.items.length - 1 ? <View style={s.divider} /> : null}
              </View>
            ))}
          </View>
        </View>
      ))}

      {/* Danger zone */}
      <Text style={s.groupTitle}>Account Actions</Text>
      <View style={s.groupCard}>
        <Pressable
          onPress={handleSignOut}
          disabled={signingOut}
          style={({ pressed }) => [s.settingRow, pressed && s.settingRowPressed]}
        >
          <View style={[s.settingIcon, { backgroundColor: APP_GREEN_LIGHT }]}>
            <Ionicons name="log-out-outline" size={18} color={APP_GREEN} />
          </View>
          <View style={s.settingBody}>
            <Text style={s.settingLabel}>{signingOut ? "Signing out…" : "Sign Out"}</Text>
            <Text style={s.settingDesc}>Sign out from this device</Text>
          </View>
          <Ionicons name="chevron-forward" size={15} color={APP_TEXT_3} />
        </Pressable>

        <View style={s.divider} />

        <Pressable
          onPress={handleDeleteAccount}
          style={({ pressed }) => [s.settingRow, pressed && s.settingRowPressed]}
        >
          <View style={[s.settingIcon, { backgroundColor: APP_RED_LIGHT }]}>
            <Ionicons name="trash-outline" size={18} color={APP_RED} />
          </View>
          <View style={s.settingBody}>
            <Text style={[s.settingLabel, { color: APP_RED }]}>Delete Account</Text>
            <Text style={s.settingDesc}>Permanently delete your Impilo account</Text>
          </View>
          <Ionicons name="chevron-forward" size={15} color={APP_TEXT_3} />
        </Pressable>
      </View>

      {/* App info */}
      <View style={s.appInfo}>
        <Text style={s.appInfoTitle}>Impilo Health</Text>
        <Text style={s.appInfoVersion}>Version {APP_VERSION}</Text>
        <Text style={s.appInfoCopyright}>© {new Date().getFullYear()} Ministry of Health & Child Care, Zimbabwe</Text>
      </View>
    </ScrollView>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  content: { padding: 16, gap: 16, paddingBottom: 48 },

  profileCard: {
    flexDirection: "row", alignItems: "center", gap: 14,
    backgroundColor: APP_SURFACE, borderRadius: 18, padding: 16,
    shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 8, elevation: 3,
  },
  profileAvatar: {
    width: 52, height: 52, borderRadius: 16,
    backgroundColor: APP_GREEN, alignItems: "center", justifyContent: "center",
  },
  profileAvatarText: { fontSize: 18, fontWeight: "800", color: "#FFFFFF" },
  profileInfo: { flex: 1 },
  profileName: { fontSize: 16, fontWeight: "700", color: APP_TEXT },
  profileEmail: { fontSize: 12, color: APP_TEXT_2, marginTop: 2 },
  profileCpid: { fontSize: 11, color: APP_TEXT_3, fontFamily: "monospace", marginTop: 2 },

  groupTitle: { fontSize: 12, fontWeight: "700", color: APP_TEXT_2, textTransform: "uppercase", letterSpacing: 0.8, paddingHorizontal: 4, marginBottom: -4 },
  groupCard: {
    backgroundColor: APP_SURFACE, borderRadius: 18, overflow: "hidden",
    shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 6, elevation: 3,
  },
  settingRow: { flexDirection: "row", alignItems: "center", gap: 14, paddingHorizontal: 16, paddingVertical: 14 },
  settingRowPressed: { backgroundColor: APP_GREEN_XLIGHT },
  settingIcon: { width: 40, height: 40, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  settingBody: { flex: 1 },
  settingLabel: { fontSize: 15, fontWeight: "600", color: APP_TEXT },
  settingDesc: { fontSize: 12, color: APP_TEXT_3, marginTop: 2 },
  badgePill: { backgroundColor: APP_GREEN_LIGHT, paddingHorizontal: 8, paddingVertical: 3, borderRadius: 8 },
  badgePillText: { fontSize: 11, fontWeight: "700", color: APP_GREEN_DARK },
  divider: { height: StyleSheet.hairlineWidth, backgroundColor: APP_BORDER, marginLeft: 70 },

  appInfo: { alignItems: "center", paddingVertical: 8, gap: 4 },
  appInfoTitle: { fontSize: 14, fontWeight: "700", color: APP_TEXT_2 },
  appInfoVersion: { fontSize: 12, color: APP_TEXT_3 },
  appInfoCopyright: { fontSize: 10, color: APP_TEXT_3, textAlign: "center" },
});
