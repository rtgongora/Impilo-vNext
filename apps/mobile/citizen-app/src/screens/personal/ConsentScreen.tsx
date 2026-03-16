/**
 * ConsentScreen — View and manage data sharing consent preferences.
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  View,
  Text,
  ScrollView,
  Pressable,
  StyleSheet,
  ActivityIndicator,
  Switch,
  Alert,
} from "react-native";
import {
  getConsents,
  updateConsent,
  type Consent,
} from "../../services/consentService";

const CONSENT_CATEGORIES: { category: string; description: string }[] = [
  { category: "Medical Records Sharing", description: "Allow sharing of your medical records with authorised healthcare providers." },
  { category: "Research Participation", description: "Permit anonymised data to be used in approved medical research studies." },
  { category: "Emergency Access", description: "Grant emergency responders access to critical health information." },
  { category: "Third-Party Provider Access", description: "Allow third-party healthcare providers to view your health data." },
  { category: "Telehealth Recording", description: "Permit recording of telehealth consultations for quality and records." },
];

export function ConsentScreen({ onGoBack }: { onGoBack?: () => void }) {
  const [consents, setConsents] = useState<Consent[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [pendingChanges, setPendingChanges] = useState<Record<string, boolean>>({});

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getConsents();
      setConsents(data);
      setPendingChanges({});
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleToggle = useCallback(
    (consentId: string, newValue: boolean) => {
      setPendingChanges((prev) => ({ ...prev, [consentId]: newValue }));
    },
    []
  );

  const getEffectiveValue = useCallback(
    (consent: Consent): boolean => {
      if (pendingChanges[consent.id] !== undefined) {
        return pendingChanges[consent.id];
      }
      return consent.granted;
    },
    [pendingChanges]
  );

  const handleSave = useCallback(async () => {
    const changedIds = Object.keys(pendingChanges);
    if (changedIds.length === 0) {
      Alert.alert("No Changes", "You have not made any changes to save.");
      return;
    }

    setIsSaving(true);
    try {
      const updates = await Promise.all(
        changedIds.map((id) => updateConsent(id, pendingChanges[id]))
      );
      setConsents((prev) =>
        prev.map((c) => {
          const updated = updates.find((u) => u.id === c.id);
          return updated ?? c;
        })
      );
      setPendingChanges({});
      Alert.alert("Saved", "Your consent preferences have been updated.");
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      Alert.alert("Error", `Failed to save preferences: ${message}`);
    } finally {
      setIsSaving(false);
    }
  }, [pendingChanges]);

  const hasPendingChanges = Object.keys(pendingChanges).length > 0;

  if (isLoading) {
    return (
      <View testID="consent-screen-loading" style={styles.centered}>
        <ActivityIndicator size="large" color="#2563EB" />
        <Text style={styles.loadingText}>Loading consent preferences…</Text>
      </View>
    );
  }

  if (error) {
    return (
      <View testID="consent-screen-error" style={styles.centered}>
        <Text style={styles.errorText}>{error.message}</Text>
        <Pressable testID="retry-button" onPress={load} style={styles.retryButton}>
          <Text style={styles.retryButtonText}>Retry</Text>
        </Pressable>
      </View>
    );
  }

  return (
    <View testID="consent-screen" style={styles.container}>
      {/* Go Back */}
      <Pressable testID="go-back" onPress={onGoBack} style={styles.goBack}>
        <Text style={styles.goBackText}>← Go Back</Text>
      </Pressable>

      <Text style={styles.heading}>Consent Management</Text>
      <Text style={styles.subheading}>
        Manage how your health data is shared and used.
      </Text>

      <ScrollView style={styles.scrollArea} showsVerticalScrollIndicator={false}>
        <View style={styles.consentList}>
          {consents.map((consent) => {
            const meta = CONSENT_CATEGORIES.find(
              (c) => c.category === consent.category
            );
            const granted = getEffectiveValue(consent);

            return (
              <View
                key={consent.id}
                testID={`consent-${consent.id}`}
                style={styles.consentCard}
              >
                <View style={styles.consentHeader}>
                  <View style={styles.consentTitleRow}>
                    <Text style={styles.consentTitle}>{consent.category}</Text>
                    <Text
                      style={[
                        styles.statusBadge,
                        granted ? styles.statusGranted : styles.statusDenied,
                      ]}
                    >
                      {granted ? "Granted" : "Denied"}
                    </Text>
                  </View>
                  <Switch
                    testID={`toggle-${consent.id}`}
                    value={granted}
                    onValueChange={(value: boolean) =>
                      handleToggle(consent.id, value)
                    }
                    trackColor={{ false: "#D1D5DB", true: "#93C5FD" }}
                    thumbColor={granted ? "#2563EB" : "#9CA3AF"}
                    accessibilityLabel={`Toggle ${consent.category} consent`}
                  />
                </View>
                <Text style={styles.consentDescription}>
                  {meta?.description ?? consent.description}
                </Text>
                <Text style={styles.updatedAt}>
                  Last updated: {new Date(consent.updatedAt).toLocaleDateString()}
                </Text>
              </View>
            );
          })}
        </View>
      </ScrollView>

      {/* Save Preferences */}
      <Pressable
        testID="save-preferences"
        onPress={handleSave}
        disabled={isSaving}
        style={[
          styles.saveButton,
          !hasPendingChanges && styles.saveButtonDisabled,
        ]}
      >
        {isSaving ? (
          <ActivityIndicator size="small" color="#FFFFFF" />
        ) : (
          <Text style={styles.saveButtonText}>Save Preferences</Text>
        )}
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    gap: 12,
  },
  centered: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    gap: 12,
  },
  loadingText: {
    fontSize: 14,
    color: "#6B7280",
    marginTop: 8,
  },
  errorText: {
    fontSize: 14,
    color: "#991B1B",
    textAlign: "center",
  },
  retryButton: {
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 8,
    backgroundColor: "#2563EB",
  },
  retryButtonText: {
    color: "#FFFFFF",
    fontWeight: "600",
    fontSize: 14,
  },
  goBack: {
    paddingVertical: 8,
  },
  goBackText: {
    fontSize: 14,
    fontWeight: "600",
    color: "#2563EB",
  },
  heading: {
    fontSize: 18,
    fontWeight: "600",
  },
  subheading: {
    fontSize: 14,
    color: "#6B7280",
  },
  scrollArea: {
    flex: 1,
  },
  consentList: {
    gap: 12,
  },
  consentCard: {
    padding: 16,
    backgroundColor: "#FFFFFF",
    borderRadius: 8,
    borderWidth: 1,
    borderColor: "#F3F4F6",
    gap: 8,
  },
  consentHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  consentTitleRow: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    flexWrap: "wrap",
    marginRight: 12,
  },
  consentTitle: {
    fontWeight: "700",
    fontSize: 15,
  },
  statusBadge: {
    fontSize: 11,
    fontWeight: "600",
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 10,
    overflow: "hidden",
  },
  statusGranted: {
    backgroundColor: "#D1FAE5",
    color: "#065F46",
  },
  statusDenied: {
    backgroundColor: "#FEE2E2",
    color: "#991B1B",
  },
  consentDescription: {
    fontSize: 13,
    color: "#6B7280",
  },
  updatedAt: {
    fontSize: 12,
    color: "#9CA3AF",
  },
  saveButton: {
    backgroundColor: "#2563EB",
    paddingVertical: 14,
    borderRadius: 8,
    alignItems: "center",
    justifyContent: "center",
    minHeight: 48,
  },
  saveButtonDisabled: {
    opacity: 0.5,
  },
  saveButtonText: {
    color: "#FFFFFF",
    fontWeight: "600",
    fontSize: 16,
  },
});
