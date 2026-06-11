/**
 * ReferralsSection — View and manage patient referrals.
 * Maps to web: /ehr/[patientId]/referrals
 * Priority: HIGH (care continuity)
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, Pressable, StyleSheet, ScrollView } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardHeader,
  CardBody,
  Button,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import type { Referral } from "../../types";
import { appStore } from "../../stores/appStore";

const STATUS_VARIANT: Record<string, "default" | "warning" | "success" | "destructive"> = {
  PENDING: "warning",
  ACCEPTED: "success",
  COMPLETED: "default",
  DECLINED: "destructive",
  EXPIRED: "destructive",
};

interface ReferralsSectionProps {
  patientId?: string;
}

export function ReferralsSection({ patientId }: ReferralsSectionProps) {
  const [referrals, setReferrals] = useState<Referral[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      // TODO: Wire to backend service
      // const result = await fetchReferrals({ patientId });
      // setReferrals(result.items);
      setReferrals([]);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, [patientId]);

  useEffect(() => {
    load();
  }, [load]);

  const pendingCount = referrals.filter(r => r.status === "PENDING").length;
  const activeCount = referrals.filter(r => r.status === "ACCEPTED" || r.status === "PENDING").length;

  return (
    <Screen>
      <Header
        title="Referrals"
        rightElement={
          pendingCount > 0 ? (
            <Badge variant="warning">{pendingCount} pending</Badge>
          ) : null
        }
      />
      <ScrollView style={styles.content} contentContainerStyle={styles.contentContainer}>
        {/* Active Referrals */}
        {activeCount > 0 && (
          <Card style={styles.activeCard}>
            <CardHeader title="Active Referrals" rightElement={<Badge>{activeCount}</Badge>} />
            <CardBody>
              {referrals.filter(r => r.status === "PENDING" || r.status === "ACCEPTED").map((referral) => (
                <View key={referral.id} style={styles.referralItem}>
                  <View style={styles.referralInfo}>
                    <Text style={styles.referralService}>{referral.serviceName}</Text>
                    <Text style={styles.referralFrom}>From: {referral.referringFacility}</Text>
                    {referral.validUntil && (
                      <Text style={styles.referralExpiry}>
                        Expires: {new Date(referral.validUntil).toLocaleDateString()}
                      </Text>
                    )}
                  </View>
                  <Badge variant={STATUS_VARIANT[referral.status] || "default"}>
                    {referral.status}
                  </Badge>
                </View>
              ))}
            </CardBody>
          </Card>
        )}

        {/* Referrals History */}
        {isLoading ? (
          <LoadingSpinner size="sm" />
        ) : error ? (
          <ErrorState message={error.message} onRetry={load} />
        ) : referrals.length === 0 ? (
          <EmptyState
            title="No referrals"
            description="Your referral history will appear here. Referrals are needed for specialist appointments."
          />
        ) : (
          <>
            <Text style={styles.sectionTitle}>History</Text>
            {referrals.filter(r => r.status !== "PENDING" && r.status !== "ACCEPTED").map((referral) => (
              <Card key={referral.id}>
                <CardHeader
                  title={referral.serviceName}
                  rightElement={
                    <Badge variant={STATUS_VARIANT[referral.status] || "default"}>
                      {referral.status}
                    </Badge>
                  }
                />
                <CardBody>
                  <View style={styles.detailRow}>
                    <Text style={styles.detailLabel}>Referred To</Text>
                    <Text style={styles.detailValue}>{referral.referralFacility}</Text>
                  </View>
                  <View style={styles.detailRow}>
                    <Text style={styles.detailLabel}>Date</Text>
                    <Text style={styles.detailValue}>
                      {referral.referralDate 
                        ? new Date(referral.referralDate).toLocaleDateString()
                        : "Unknown"}
                    </Text>
                  </View>
                  {referral.notes && (
                    <View style={styles.detailRow}>
                      <Text style={styles.detailLabel}>Notes</Text>
                      <Text style={styles.detailValue}>{referral.notes}</Text>
                    </View>
                  )}
                </CardBody>
              </Card>
            ))}
          </>
        )}

        {/* Request Referral */}
        <Button
          title="Request New Referral"
          variant="secondary"
          onPress={() => {
            appStore.getState().setPersonalSectionRequest("discover-providers");
            appStore.getState().setActiveTab("personal");
          }}
        />
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: {
    flex: 1,
  },
  contentContainer: {
    padding: 16,
    gap: 16,
  },
  activeCard: {
    borderColor: "#4F46E5",
    borderWidth: 1,
  },
  referralItem: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingVertical: 8,
  },
  referralInfo: {
    flex: 1,
  },
  referralService: {
    fontSize: 14,
    fontWeight: "600",
    color: "#1F2937",
  },
  referralFrom: {
    fontSize: 12,
    color: "#6B7280",
  },
  referralExpiry: {
    fontSize: 11,
    color: "#F59E0B",
    marginTop: 2,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: "600",
    color: "#6B7280",
    marginTop: 8,
  },
  detailRow: {
    paddingVertical: 4,
  },
  detailLabel: {
    fontSize: 12,
    color: "#6B7280",
  },
  detailValue: {
    fontSize: 14,
    color: "#1F2937",
  },
});
