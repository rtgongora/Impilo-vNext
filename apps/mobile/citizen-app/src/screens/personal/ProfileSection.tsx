/**
 * ProfileSection — View and edit citizen profile.
 */

import React, { useState, useCallback } from "react";
import { View, Text, StyleSheet } from "react-native";
import {
  Card,
  CardHeader,
  CardBody,
  Button,
  TextField,
  Avatar,
  LoadingSpinner,
  ErrorState,
} from "@impilo/mobile-design-system";
import { useAppStore, appStore } from "../../stores/appStore";
import { updateProfile } from "../../services/profileService";

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
      <Card>
        <CardHeader title="Personal Information" />
        <CardBody>
          <View style={styles.profileHeader}>
            <Avatar
              name={`${profile.givenName} ${profile.familyName}`}
              size="xl"
              src={profile.avatarUrl}
            />
            <View>
              <Text style={styles.profileName}>{`${profile.givenName} ${profile.familyName}`}</Text>
              <Text style={styles.profileDetail}>{`DOB: ${profile.dateOfBirth}`}</Text>
              <Text style={styles.profileDetailSmall}>{`Sex: ${profile.sex}`}</Text>
              {profile.nationalId ? (
                <Text style={styles.profileDetailSmall}>{`ID: ${profile.nationalId}`}</Text>
              ) : null}
            </View>
          </View>

          {error ? (
            <ErrorState title="Error" message={error} onRetry={() => setError(null)} />
          ) : null}

          {editing ? (
            <View style={styles.formContainer}>
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
              <View style={styles.buttonRow}>
                <Button
                  title={saving ? "Saving..." : "Save"}
                  variant="primary"
                  onPress={handleSave}
                  disabled={saving}
                  testID="profile-save"
                />
                <Button
                  title="Cancel"
                  variant="ghost"
                  onPress={() => setEditing(false)}
                  testID="profile-cancel"
                />
              </View>
            </View>
          ) : (
            <View style={styles.infoContainer}>
              <Text style={styles.infoText}>{`Phone: ${profile.phone ?? "Not set"}`}</Text>
              <Text style={styles.infoText}>{`Email: ${profile.email ?? "Not set"}`}</Text>
              <Text style={styles.infoText}>{`Language: ${profile.preferredLanguage}`}</Text>
              {profile.facilityName ? (
                <Text style={styles.infoText}>{`Primary Facility: ${profile.facilityName}`}</Text>
              ) : null}
              <Button
                title="Edit Profile"
                variant="secondary"
                onPress={() => setEditing(true)}
                testID="profile-edit"
              />
            </View>
          )}
        </CardBody>
      </Card>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 16,
  },
  profileHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 16,
    marginBottom: 16,
  },
  profileName: {
    fontSize: 18,
    fontWeight: "600",
  },
  profileDetail: {
    marginTop: 4,
    color: "#6B7280",
    fontSize: 14,
  },
  profileDetailSmall: {
    marginTop: 2,
    color: "#6B7280",
    fontSize: 14,
  },
  formContainer: {
    gap: 12,
  },
  buttonRow: {
    flexDirection: "row",
    gap: 8,
  },
  infoContainer: {
    gap: 8,
  },
  infoText: {
    fontSize: 14,
  },
});
