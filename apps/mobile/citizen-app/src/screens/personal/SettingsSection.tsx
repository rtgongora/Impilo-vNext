import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import {
  Button,
  Switch,
  LoadingSpinner,
  ErrorState,
} from "@impilo/mobile-design-system";
import { useAuth } from "@impilo/mobile-auth";
import { getPreferences, updatePreference, type NotificationPreference } from "@impilo/mobile-messaging";
import { fetchConsents, updateConsent, deleteAccount } from "../../services/profileService";
import type { ConsentPreference } from "../../types";

export function SettingsSection() {
  const auth = useAuth();
  const [consents, setConsents] = useState<ConsentPreference[]>([]);
  const [notifPrefs, setNotifPrefs] = useState<NotificationPreference[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [c, n] = await Promise.all([
        fetchConsents(),
        getPreferences(),
      ]);
      setConsents(c);
      setNotifPrefs(n);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleConsentToggle = useCallback(async (id: string, granted: boolean) => {
    try {
      const updated = await updateConsent(id, granted);
      setConsents((prev) => prev.map((c) => (c.id === id ? updated : c)));
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    }
  }, []);

  const handleNotifPrefToggle = useCallback(async (category: string, field: "pushEnabled" | "inAppEnabled", value: boolean) => {
    try {
      await updatePreference({ category: category as NotificationPreference["category"], [field]: value });
      setNotifPrefs((prev) =>
        prev.map((p) => (p.category === category ? { ...p, [field]: value } : p))
      );
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    }
  }, []);

  const handleDeleteAccount = useCallback(async () => {
    try {
      await deleteAccount();
      auth.logout();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    }
  }, [auth]);

  if (isLoading) return <LoadingSpinner size="md" />;
  if (error) return <ErrorState title="Error" message={error.message} onRetry={load} />;

  return (
    <View testID="settings-section" style={styles.container}>
      <View style={styles.section}>
        <Text style={styles.sectionLabel}>PRIVACY & CONSENT</Text>
        <View style={styles.card}>
          {consents.length === 0 ? (
            <View style={styles.emptyRow}>
              <Text style={styles.emptyText}>No consent preferences configured</Text>
            </View>
          ) : (
            consents.map((consent, index) => (
              <View
                key={consent.id}
                testID={`consent-${consent.id}`}
                style={[
                  styles.consentRow,
                  index === consents.length - 1 ? styles.lastRow : null,
                ]}
              >
                <View style={styles.consentIconCircle}>
                  <Ionicons name="lock-closed" size={16} color="#059669" />
                </View>
                <View style={styles.consentContent}>
                  <Text style={styles.consentCategory}>{consent.category}</Text>
                  <Text style={styles.consentDescription}>{consent.description}</Text>
                </View>
                <Switch
                  checked={consent.granted}
                  onChange={(v: boolean) => handleConsentToggle(consent.id, v)}
                  accessibilityLabel={`Toggle ${consent.category} consent`}
                />
              </View>
            ))
          )}
        </View>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionLabel}>NOTIFICATIONS</Text>
        <View style={styles.card}>
          {notifPrefs.length === 0 ? (
            <View style={styles.emptyRow}>
              <Text style={styles.emptyText}>No notification preferences available</Text>
            </View>
          ) : (
            notifPrefs.map((pref, index) => (
              <View
                key={pref.category}
                testID={`notif-pref-${pref.category}`}
                style={[
                  styles.notifRow,
                  index === notifPrefs.length - 1 ? styles.lastRow : null,
                ]}
              >
                <View style={styles.notifTop}>
                  <View style={styles.notifIconCircle}>
                    <Ionicons name="notifications" size={16} color="#1E40AF" />
                  </View>
                  <Text style={styles.notifCategory}>{pref.category}</Text>
                </View>
                <View style={styles.notifToggles}>
                  <View style={styles.togglePill}>
                    <Switch
                      checked={pref.pushEnabled}
                      onChange={(v: boolean) => handleNotifPrefToggle(pref.category, "pushEnabled", v)}
                    />
                    <Text style={styles.toggleLabel}>Push</Text>
                  </View>
                  <View style={styles.togglePill}>
                    <Switch
                      checked={pref.inAppEnabled}
                      onChange={(v: boolean) => handleNotifPrefToggle(pref.category, "inAppEnabled", v)}
                    />
                    <Text style={styles.toggleLabel}>In-App</Text>
                  </View>
                </View>
              </View>
            ))
          )}
        </View>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionLabel}>ACCOUNT</Text>
        <View style={styles.card}>
          <View style={styles.accountRow}>
            <View style={styles.accountIconCircle}>
              <Ionicons name="log-out-outline" size={18} color="#6B7280" />
            </View>
            <View style={styles.accountContent}>
              <Text style={styles.accountActionLabel}>Sign Out</Text>
              <Text style={styles.accountActionSub}>End your current session</Text>
            </View>
            <Button
              title="Sign Out"
              variant="secondary"
              size="sm"
              onPress={() => auth.logout()}
              testID="sign-out"
            />
          </View>

          <View style={[styles.accountRow, styles.lastRow]}>
            <View style={[styles.accountIconCircle, styles.dangerIconCircle]}>
              <Ionicons name="trash-outline" size={18} color="#EF4444" />
            </View>
            <View style={styles.accountContent}>
              <Text style={[styles.accountActionLabel, styles.dangerLabel]}>Delete Account</Text>
              <Text style={styles.accountActionSub}>Permanently remove your data</Text>
            </View>
            {!showDeleteConfirm ? (
              <Button
                title="Delete"
                variant="ghost"
                size="sm"
                onPress={() => setShowDeleteConfirm(true)}
                testID="delete-account"
              />
            ) : null}
          </View>

          {showDeleteConfirm ? (
            <View style={styles.deleteConfirmBox}>
              <View style={styles.deleteWarningHeader}>
                <Ionicons name="warning" size={18} color="#991B1B" />
                <Text style={styles.deleteWarningTitle}>Are you sure?</Text>
              </View>
              <Text style={styles.deleteWarningText}>
                This will permanently delete your account and all associated data. This action cannot be undone.
              </Text>
              <View style={styles.deleteButtonRow}>
                <View style={styles.buttonFlex}>
                  <Button
                    title="Confirm Delete"
                    variant="primary"
                    onPress={handleDeleteAccount}
                    testID="confirm-delete-account"
                  />
                </View>
                <View style={styles.buttonFlex}>
                  <Button
                    title="Cancel"
                    variant="ghost"
                    onPress={() => setShowDeleteConfirm(false)}
                  />
                </View>
              </View>
            </View>
          ) : null}
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 20,
  },
  section: {
    gap: 8,
  },
  sectionLabel: {
    fontSize: 13,
    fontWeight: "700",
    color: "#6B7280",
    letterSpacing: 0.8,
    textTransform: "uppercase",
  },
  card: {
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
    overflow: "hidden",
  },
  emptyRow: {
    padding: 16,
    alignItems: "center",
  },
  emptyText: {
    color: "#9CA3AF",
    fontSize: 14,
  },
  consentRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: "#F3F4F6",
  },
  lastRow: {
    borderBottomWidth: 0,
  },
  consentIconCircle: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: "#D1FAE5",
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
  },
  consentContent: {
    flex: 1,
  },
  consentCategory: {
    fontSize: 14,
    fontWeight: "700",
    color: "#111827",
  },
  consentDescription: {
    fontSize: 12,
    color: "#9CA3AF",
    marginTop: 2,
  },
  notifRow: {
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: "#F3F4F6",
    gap: 10,
  },
  notifTop: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  notifIconCircle: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: "#DBEAFE",
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
  },
  notifCategory: {
    fontSize: 14,
    fontWeight: "700",
    color: "#111827",
  },
  notifToggles: {
    flexDirection: "row",
    gap: 16,
    paddingLeft: 46,
  },
  togglePill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    backgroundColor: "#F9FAFB",
    borderRadius: 10,
    paddingVertical: 6,
    paddingHorizontal: 10,
  },
  toggleLabel: {
    fontSize: 12,
    color: "#6B7280",
    fontWeight: "500",
  },
  accountRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingVertical: 14,
    paddingHorizontal: 16,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: "#F3F4F6",
  },
  accountIconCircle: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: "#F3F4F6",
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
  },
  dangerIconCircle: {
    backgroundColor: "#FEE2E2",
  },
  accountContent: {
    flex: 1,
  },
  accountActionLabel: {
    fontSize: 14,
    fontWeight: "700",
    color: "#111827",
  },
  dangerLabel: {
    color: "#EF4444",
  },
  accountActionSub: {
    fontSize: 12,
    color: "#9CA3AF",
    marginTop: 1,
  },
  deleteConfirmBox: {
    margin: 12,
    padding: 14,
    backgroundColor: "#FEF2F2",
    borderRadius: 12,
    borderWidth: 1,
    borderColor: "#FECACA",
    gap: 10,
  },
  deleteWarningHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  deleteWarningTitle: {
    fontSize: 15,
    fontWeight: "700",
    color: "#991B1B",
  },
  deleteWarningText: {
    color: "#B91C1C",
    fontSize: 13,
    lineHeight: 19,
  },
  deleteButtonRow: {
    flexDirection: "row",
    gap: 10,
    marginTop: 4,
  },
  buttonFlex: {
    flex: 1,
  },
});
