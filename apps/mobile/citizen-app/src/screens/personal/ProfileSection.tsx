import React, { useState, useCallback } from "react";
import { View, Text, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Card, CardBody, Button, TextField, Avatar, LoadingSpinner, ErrorState, colors } from "@impilo/mobile-design-system";
import { useAppStore, appStore } from "../../stores/appStore";
import { updateProfile } from "../../services/profileService";

type IoniconsName = React.ComponentProps<typeof Ionicons>["name"];

function InfoRow({ icon, label, value }: { icon: IoniconsName; label: string; value: string }) {
  return (
    <View style={styles.infoRow}>
      <View style={styles.infoIconCircle}>
        <Ionicons name={icon} size={15} color="#059669" />
      </View>
      <View style={styles.infoContent}>
        <Text style={styles.infoLabel}>{label}</Text>
        <Text style={styles.infoValue}>{value}</Text>
      </View>
    </View>
  );
}

export function ProfileSection() {
  const { profile } = useAppStore();
  const [editing, setEditing] = useState(false);
  const [phone, setPhone] = useState(profile?.phone ?? "");
  const [email, setEmail] = useState(profile?.email ?? "");
  const [language, setLanguage] = useState(profile?.preferredLanguage ?? "en");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSave = useCallback(async () => {
    setSaving(true);
    setError(null);
    try {
      const updated = await updateProfile({ phone, email, preferredLanguage: language });
      appStore.getState().setProfile(updated);
      setEditing(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save profile");
    } finally {
      setSaving(false);
    }
  }, [phone, email, language]);

  if (!profile) {
    return <LoadingSpinner size="md" />;
  }

  return (
    <View testID="profile-section" style={styles.container}>
      <View style={styles.avatarCard}>
        <View style={styles.avatarRing}>
          <Avatar
            name={`${profile.givenName} ${profile.familyName}`}
            size="xl"
            src={profile.avatarUrl}
          />
        </View>
        <Text style={styles.profileName}>{`${profile.givenName} ${profile.familyName}`}</Text>
        <View style={styles.metaChips}>
          {profile.dateOfBirth ? (
            <View style={styles.metaChip}>
              <Ionicons name="calendar-outline" size={12} color={colors.gray[500]} />
              <Text style={styles.metaChipText}>{profile.dateOfBirth}</Text>
            </View>
          ) : null}
          {profile.sex ? (
            <View style={styles.metaChip}>
              <Ionicons name="body-outline" size={12} color={colors.gray[500]} />
              <Text style={styles.metaChipText}>{profile.sex}</Text>
            </View>
          ) : null}
          {profile.nationalId ? (
            <View style={styles.metaChip}>
              <Ionicons name="card-outline" size={12} color={colors.gray[500]} />
              <Text style={styles.metaChipText}>{profile.nationalId}</Text>
            </View>
          ) : null}
        </View>
      </View>

      {error ? (
        <ErrorState title="Error" message={error} onRetry={() => setError(null)} />
      ) : null}

      {editing ? (
        <View style={styles.formCard}>
          <Text style={styles.formSectionLabel}>EDIT PROFILE</Text>
          <View style={styles.formFields}>
            <TextField
              label="Phone"
              value={phone}
              onChange={setPhone}
              placeholder="+263..."
              testID="profile-phone"
            />
            <TextField
              label="Email"
              value={email}
              onChange={setEmail}
              placeholder="you@example.com"
              testID="profile-email"
            />
            <TextField
              label="Preferred Language"
              value={language}
              onChange={setLanguage}
              testID="profile-language"
            />
          </View>
          <View style={styles.buttonRow}>
            <View style={styles.buttonFlex}>
              <Button
                title={saving ? "Saving..." : "Save Changes"}
                variant="primary"
                onPress={handleSave}
                disabled={saving}
                testID="profile-save"
              />
            </View>
            <View style={styles.buttonFlex}>
              <Button
                title="Cancel"
                variant="ghost"
                onPress={() => setEditing(false)}
                testID="profile-cancel"
              />
            </View>
          </View>
        </View>
      ) : (
        <View style={styles.infoCard}>
          <Text style={styles.infoSectionLabel}>CONTACT & PREFERENCES</Text>
          <View style={styles.infoList}>
            <InfoRow icon="call-outline" label="Phone" value={profile.phone ?? "Not set"} />
            <InfoRow icon="mail-outline" label="Email" value={profile.email ?? "Not set"} />
            <InfoRow icon="language-outline" label="Language" value={profile.preferredLanguage} />
            {profile.facilityName ? (
              <InfoRow icon="business-outline" label="Primary Facility" value={profile.facilityName} />
            ) : null}
          </View>
          <View style={styles.editButtonWrapper}>
            <Button
              title="Edit Profile"
              variant="secondary"
              onPress={() => setEditing(true)}
              testID="profile-edit"
            />
          </View>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 16,
  },
  avatarCard: {
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    padding: 24,
    alignItems: "center",
    gap: 10,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
  },
  avatarRing: {
    borderWidth: 3,
    borderColor: colors.ui.success.light,
    borderRadius: 999,
    padding: 3,
  },
  profileName: {
    fontSize: 20,
    fontWeight: "700",
    color: colors.gray[900],
    marginTop: 4,
  },
  metaChips: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    justifyContent: "center",
    marginTop: 4,
  },
  metaChip: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    backgroundColor: colors.gray[100],
    borderRadius: 12,
    paddingVertical: 4,
    paddingHorizontal: 10,
  },
  metaChipText: {
    fontSize: 12,
    color: colors.gray[500],
    fontWeight: "500",
  },
  formCard: {
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    padding: 16,
    gap: 14,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
  },
  formSectionLabel: {
    fontSize: 13,
    fontWeight: "700",
    color: colors.gray[500],
    letterSpacing: 0.8,
    textTransform: "uppercase",
  },
  formFields: {
    gap: 12,
  },
  buttonRow: {
    flexDirection: "row",
    gap: 10,
    marginTop: 4,
  },
  buttonFlex: {
    flex: 1,
  },
  infoCard: {
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    padding: 16,
    gap: 12,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
  },
  infoSectionLabel: {
    fontSize: 13,
    fontWeight: "700",
    color: colors.gray[500],
    letterSpacing: 0.8,
    textTransform: "uppercase",
  },
  infoList: {
    gap: 2,
  },
  infoRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.gray[100],
  },
  infoIconCircle: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: colors.ui.success.light,
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
  },
  infoContent: {
    flex: 1,
  },
  infoLabel: {
    fontSize: 11,
    fontWeight: "600",
    color: colors.gray[400],
    textTransform: "uppercase",
    letterSpacing: 0.3,
  },
  infoValue: {
    fontSize: 14,
    color: colors.gray[900],
    fontWeight: "500",
    marginTop: 1,
  },
  editButtonWrapper: {
    marginTop: 4,
  },
});
