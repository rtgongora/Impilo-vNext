/**
 * ProfessionalProfileScreen — Provider's professional profile view.
 *
 * Displays professional details, license/registration info, contact details,
 * and professional notices. Contact details are editable inline.
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Pressable,
  TextInput,
} from "react-native";
import { Screen, Header, Card, CardBody, CardHeader, Badge, Button, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import {
  getProfile,
  getNotices,
  updateProfile,
  type ProviderProfile,
  type Notice,
  type UpdateProfileInput,
} from "../../services/profileService";

const SEVERITY_VARIANT: Record<string, "destructive" | "warning" | "secondary"> = {
  CRITICAL: "destructive",
  WARNING: "warning",
  INFO: "secondary",
};

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString();
}

export function ProfessionalProfileScreen() {
  const [profile, setProfile] = useState<ProviderProfile | null>(null);
  const [notices, setNotices] = useState<Notice[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);

  // Editable contact fields
  const [editEmail, setEditEmail] = useState("");
  const [editPhone, setEditPhone] = useState("");
  const [editOfficePhone, setEditOfficePhone] = useState("");

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [profileResult, noticesResult] = await Promise.all([
        getProfile(),
        getNotices(),
      ]);
      setProfile(profileResult);
      setNotices(noticesResult);
      setEditEmail(profileResult.email);
      setEditPhone(profileResult.phone);
      setEditOfficePhone(profileResult.officePhone ?? "");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load profile");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleSave = useCallback(async () => {
    setSaving(true);
    try {
      const payload: UpdateProfileInput = {};
      if (editEmail !== profile?.email) payload.email = editEmail;
      if (editPhone !== profile?.phone) payload.phone = editPhone;
      if (editOfficePhone !== (profile?.officePhone ?? ""))
        payload.office_phone = editOfficePhone || undefined;

      const updated = await updateProfile(payload);
      setProfile(updated);
      setEditing(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update profile");
    } finally {
      setSaving(false);
    }
  }, [editEmail, editPhone, editOfficePhone, profile]);

  const handleCancelEdit = useCallback(() => {
    if (profile) {
      setEditEmail(profile.email);
      setEditPhone(profile.phone);
      setEditOfficePhone(profile.officePhone ?? "");
    }
    setEditing(false);
  }, [profile]);

  if (loading) {
    return (
      <Screen>
        <Header title="My Profile" />
        <LoadingSpinner size="lg" />
      </Screen>
    );
  }

  if (error && !profile) {
    return (
      <Screen>
        <Header title="My Profile" />
        <ErrorState title="Error" message={error} onRetry={loadData} />
      </Screen>
    );
  }

  if (!profile) {
    return (
      <Screen>
        <Header title="My Profile" />
        <EmptyState title="No profile" message="Profile data is not available" />
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="My Profile" />
      <ScrollView testID="professional-profile" style={styles.container}>
        {/* Professional Details */}
        <CardHeader>Professional Details</CardHeader>
        <Card>
          <CardBody>
            <Text style={styles.name}>
              {`${profile.givenName} ${profile.familyName}`}
            </Text>
            <View style={styles.detailRow}>
              <Text style={styles.label}>Qualification</Text>
              <Text style={styles.value}>{profile.qualification}</Text>
            </View>
            <View style={styles.detailRow}>
              <Text style={styles.label}>Specialty</Text>
              <Text style={styles.value}>{profile.specialty}</Text>
            </View>
            <View style={styles.detailRow}>
              <Text style={styles.label}>Provider Number</Text>
              <Text style={styles.value}>{profile.providerNumber}</Text>
            </View>
            <View style={styles.detailRow}>
              <Text style={styles.label}>Facility</Text>
              <Text style={styles.value}>{profile.facilityName}</Text>
            </View>
            <View style={styles.detailRow}>
              <Text style={styles.label}>Status</Text>
              <Badge variant={profile.status === "ACTIVE" ? "secondary" : "outline"}>
                {profile.status}
              </Badge>
            </View>
          </CardBody>
        </Card>

        {/* License & Registration */}
        <CardHeader>License &amp; Registration</CardHeader>
        <Card>
          <CardBody>
            <View style={styles.detailRow}>
              <Text style={styles.label}>License Number</Text>
              <Text style={styles.value}>{profile.licenseNumber}</Text>
            </View>
            <View style={styles.detailRow}>
              <Text style={styles.label}>Authority</Text>
              <Text style={styles.value}>{profile.licenseAuthority}</Text>
            </View>
            <View style={styles.detailRow}>
              <Text style={styles.label}>Expiry</Text>
              <Text
                style={[
                  styles.value,
                  new Date(profile.licenseExpiry) < new Date()
                    ? { color: colors.ui.error.main }
                    : undefined,
                ]}
              >
                {formatDate(profile.licenseExpiry)}
              </Text>
            </View>
            <View style={styles.detailRow}>
              <Text style={styles.label}>Registration Body</Text>
              <Text style={styles.value}>{profile.registrationBody}</Text>
            </View>
            <View style={styles.detailRow}>
              <Text style={styles.label}>Registration No.</Text>
              <Text style={styles.value}>{profile.registrationNumber}</Text>
            </View>
          </CardBody>
        </Card>

        {/* Contact Details */}
        <CardHeader>Contact Details</CardHeader>
        <Card>
          <CardBody>
            {editing ? (
              <>
                <View style={styles.editRow}>
                  <Text style={styles.label}>Email</Text>
                  <TextInput
                    testID="edit-email"
                    style={styles.input}
                    value={editEmail}
                    onChangeText={setEditEmail}
                    keyboardType="email-address"
                    autoCapitalize="none"
                  />
                </View>
                <View style={styles.editRow}>
                  <Text style={styles.label}>Phone</Text>
                  <TextInput
                    testID="edit-phone"
                    style={styles.input}
                    value={editPhone}
                    onChangeText={setEditPhone}
                    keyboardType="phone-pad"
                  />
                </View>
                <View style={styles.editRow}>
                  <Text style={styles.label}>Office Phone</Text>
                  <TextInput
                    testID="edit-office-phone"
                    style={styles.input}
                    value={editOfficePhone}
                    onChangeText={setEditOfficePhone}
                    keyboardType="phone-pad"
                  />
                </View>
                {error ? (
                  <Text style={styles.errorText}>{error}</Text>
                ) : null}
                <View style={styles.editActions}>
                  <Button
                    title={saving ? "Saving..." : "Save"}
                    variant="default"
                    onPress={handleSave}
                    testID="save-contact"
                    disabled={saving}
                  />
                  <Button
                    title="Cancel"
                    variant="outline"
                    onPress={handleCancelEdit}
                    testID="cancel-edit"
                    disabled={saving}
                  />
                </View>
              </>
            ) : (
              <>
                <View style={styles.detailRow}>
                  <Text style={styles.label}>Email</Text>
                  <Text style={styles.value}>{profile.email}</Text>
                </View>
                <View style={styles.detailRow}>
                  <Text style={styles.label}>Phone</Text>
                  <Text style={styles.value}>{profile.phone}</Text>
                </View>
                {profile.officePhone ? (
                  <View style={styles.detailRow}>
                    <Text style={styles.label}>Office Phone</Text>
                    <Text style={styles.value}>{profile.officePhone}</Text>
                  </View>
                ) : null}
                <View style={styles.editButtonContainer}>
                  <Pressable
                    testID="edit-contact-btn"
                    onPress={() => setEditing(true)}
                    style={styles.editButton}
                  >
                    <Text style={styles.editButtonText}>Edit Contact Details</Text>
                  </Pressable>
                </View>
              </>
            )}
          </CardBody>
        </Card>

        {/* Notices */}
        <CardHeader>Professional Notices</CardHeader>
        {notices.length === 0 ? (
          <EmptyState title="No notices" message="No active professional notices" />
        ) : (
          notices.map((notice) => (
            <Card key={notice.id}>
              <CardBody>
                <View testID={`notice-${notice.id}`}>
                  <View style={styles.noticeHeader}>
                    <Badge variant={SEVERITY_VARIANT[notice.severity] ?? "secondary"}>
                      {notice.severity}
                    </Badge>
                    <Text style={styles.noticeDate}>
                      {formatDate(notice.issuedAt)}
                    </Text>
                  </View>
                  <Text style={styles.noticeTitle}>{notice.title}</Text>
                  <Text style={styles.noticeBody}>{notice.body}</Text>
                  {notice.expiresAt ? (
                    <Text style={styles.noticeExpiry}>
                      {`Expires: ${formatDate(notice.expiresAt)}`}
                    </Text>
                  ) : null}
                </View>
              </CardBody>
            </Card>
          ))
        )}

        <View style={styles.refreshContainer}>
          <Button
            title="Refresh"
            variant="outline"
            onPress={loadData}
            testID="refresh-profile"
          />
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  name: {
    fontSize: 20,
    fontWeight: "700",
    marginBottom: 12,
  },
  detailRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingVertical: 6,
  },
  label: {
    fontSize: 14,
    color: colors.gray[500],
    flex: 1,
  },
  value: {
    fontSize: 14,
    fontWeight: "500",
    flex: 1,
    textAlign: "right",
  },
  editRow: {
    marginBottom: 12,
  },
  input: {
    borderWidth: 1,
    borderColor: colors.gray[300],
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    fontSize: 14,
    marginTop: 4,
  },
  editActions: {
    flexDirection: "row",
    gap: 12,
    marginTop: 8,
  },
  editButtonContainer: {
    marginTop: 12,
  },
  editButton: {
    alignSelf: "flex-start",
    paddingVertical: 8,
    paddingHorizontal: 16,
    borderRadius: 8,
    backgroundColor: "#EFF6FF",
  },
  editButtonText: {
    color: "#2563EB",
    fontSize: 14,
    fontWeight: "600",
  },
  errorText: {
    color: colors.ui.error.main,
    fontSize: 13,
    marginBottom: 8,
  },
  noticeHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  noticeDate: {
    fontSize: 12,
    color: colors.gray[400],
  },
  noticeTitle: {
    fontSize: 15,
    fontWeight: "600",
    marginTop: 6,
  },
  noticeBody: {
    fontSize: 14,
    color: colors.gray[700],
    marginTop: 4,
  },
  noticeExpiry: {
    fontSize: 12,
    color: colors.gray[400],
    marginTop: 4,
  },
  refreshContainer: {
    marginTop: 16,
    marginBottom: 32,
  },
});
