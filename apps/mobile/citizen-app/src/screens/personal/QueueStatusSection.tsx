import React from "react";
import { View, Text, ScrollView, StyleSheet, Pressable } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery } from "@tanstack/react-query";
import { fetchQueueStatus } from "../../services/queueStatusService";
import { useAppStore } from "../../stores/appStore";
import { USE_SEED_DATA, SEED_QUEUE_TICKETS } from "../../data/seedHealthRecords";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT, APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

const STATUS_CFG: Record<string, { color: string; bg: string; label: string; icon: React.ComponentProps<typeof Ionicons>["name"] }> = {
  WAITING:   { color: APP_GOLD,       bg: APP_GOLD_LIGHT,   label: "Waiting",   icon: "time-outline"            },
  CALLED:    { color: APP_GREEN_DARK, bg: APP_GREEN_LIGHT,  label: "Called",    icon: "volume-medium-outline"   },
  COMPLETED: { color: APP_TEXT_2,     bg: "#EEF2F2",        label: "Completed", icon: "checkmark-circle-outline" },
  CANCELLED: { color: APP_RED,        bg: APP_RED_LIGHT,    label: "Cancelled", icon: "close-circle-outline"    },
};

export function QueueStatusSection() {
  const profile   = useAppStore((s) => s.profile);
  const patientId = profile?.cpid ?? "";

  const { data: tickets = [], isLoading, refetch } = useQuery({
    queryKey: ["queue-status", patientId, USE_SEED_DATA],
    queryFn: async () => {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 400));
        return SEED_QUEUE_TICKETS;
      }
      return fetchQueueStatus(patientId);
    },
    enabled:        USE_SEED_DATA || !!patientId,
    refetchInterval: USE_SEED_DATA ? false : 15_000,
  });

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  if (tickets.length === 0) {
    return (
      <View style={[s.root, s.centred]}>
        <Ionicons name="location-outline" size={52} color={APP_GREEN_LIGHT} />
        <Text style={s.emptyTitle}>Not in any queue</Text>
        <Text style={s.emptySub}>
          Join a queue remotely from the Services tab, or check in at a facility to get a ticket.
        </Text>
      </View>
    );
  }

  return (
    <ScrollView style={s.root} contentContainerStyle={s.content} showsVerticalScrollIndicator={false}>
      {tickets.map((ticket) => {
        const cfg = STATUS_CFG[ticket.status] ?? STATUS_CFG.WAITING;
        const isWaiting = ticket.status === "WAITING";
        const isCalled  = ticket.status === "CALLED";

        return (
          <View key={ticket.id} style={[s.ticketCard, isCalled && s.ticketCardCalled]}>
            {/* Called alert */}
            {isCalled ? (
              <View style={s.calledBanner}>
                <Ionicons name="volume-medium" size={16} color="#FFFFFF" />
                <Text style={s.calledBannerText}>You have been called — please proceed to the counter</Text>
              </View>
            ) : null}

            {/* Ticket number hero */}
            <View style={s.ticketHero}>
              <View>
                <Text style={s.ticketLabel}>Queue Ticket</Text>
                <Text style={[s.ticketNumber, { color: cfg.color }]}>{ticket.ticketNumber}</Text>
              </View>
              <View style={[s.statusBadge, { backgroundColor: cfg.bg }]}>
                <Ionicons name={cfg.icon} size={14} color={cfg.color} />
                <Text style={[s.statusBadgeText, { color: cfg.color }]}>{cfg.label}</Text>
              </View>
            </View>

            {/* Facility & service */}
            <View style={s.facilityBlock}>
              <View style={s.facilityRow}>
                <Ionicons name="business-outline" size={14} color={APP_TEXT_2} />
                <Text style={s.facilityName}>{ticket.facilityName}</Text>
              </View>
              <Text style={s.serviceType}>{ticket.serviceType}</Text>
            </View>

            {/* Position & wait (only when waiting) */}
            {isWaiting && ticket.position != null ? (
              <View style={s.waitBlock}>
                <View style={[s.waitCard, { backgroundColor: APP_GOLD_LIGHT }]}>
                  <Text style={[s.waitNum, { color: APP_GOLD }]}>{ticket.position}</Text>
                  <Text style={s.waitLbl}>Position</Text>
                </View>
                {ticket.estimatedWait != null ? (
                  <View style={[s.waitCard, { backgroundColor: APP_GREEN_XLIGHT }]}>
                    <Text style={[s.waitNum, { color: APP_GREEN }]}>{ticket.estimatedWait}</Text>
                    <Text style={s.waitLbl}>Est. minutes</Text>
                  </View>
                ) : null}
              </View>
            ) : null}

            {/* Times */}
            <View style={s.timesRow}>
              <View style={s.timeItem}>
                <Ionicons name="log-in-outline" size={13} color={APP_TEXT_3} />
                <Text style={s.timeText}>
                  Joined {new Date(ticket.joinedAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                </Text>
              </View>
              {ticket.calledAt ? (
                <View style={s.timeItem}>
                  <Ionicons name="volume-medium-outline" size={13} color={APP_GREEN} />
                  <Text style={[s.timeText, { color: APP_GREEN }]}>
                    Called {new Date(ticket.calledAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                  </Text>
                </View>
              ) : null}
            </View>

            {/* Refresh */}
            {isWaiting ? (
              <Pressable onPress={() => refetch()} style={s.refreshBtn}>
                <Ionicons name="refresh-outline" size={13} color={APP_TEXT_2} />
                <Text style={s.refreshText}>Refresh queue position</Text>
              </Pressable>
            ) : null}
          </View>
        );
      })}

      {!USE_SEED_DATA ? (
        <Text style={s.autoRefreshNote}>Auto-refreshing every 15 seconds</Text>
      ) : null}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  content: { padding: 16, gap: 12, paddingBottom: 40 },
  centred: { flex: 1, alignItems: "center", justifyContent: "center", gap: 14, padding: 32 },

  ticketCard: {
    backgroundColor: APP_SURFACE, borderRadius: 22, overflow: "hidden",
    shadowColor: "#000", shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.1, shadowRadius: 14, elevation: 6,
  },
  ticketCardCalled: {
    shadowColor: APP_GREEN, shadowOpacity: 0.25,
  },

  calledBanner: {
    flexDirection: "row", alignItems: "center", gap: 10,
    backgroundColor: APP_GREEN, paddingVertical: 12, paddingHorizontal: 18,
  },
  calledBannerText: { fontSize: 13, fontWeight: "700", color: "#FFFFFF", flex: 1 },

  ticketHero: {
    flexDirection: "row", justifyContent: "space-between", alignItems: "flex-start",
    paddingHorizontal: 20, paddingTop: 20, paddingBottom: 4,
  },
  ticketLabel: { fontSize: 11, fontWeight: "700", color: APP_TEXT_3, textTransform: "uppercase", letterSpacing: 1 },
  ticketNumber: { fontSize: 48, fontWeight: "900", lineHeight: 56 },
  statusBadge: {
    flexDirection: "row", alignItems: "center", gap: 6,
    paddingHorizontal: 12, paddingVertical: 7, borderRadius: 20, marginTop: 8,
  },
  statusBadgeText: { fontSize: 13, fontWeight: "700" },

  facilityBlock: { paddingHorizontal: 20, paddingBottom: 16 },
  facilityRow: { flexDirection: "row", alignItems: "center", gap: 6, marginBottom: 3 },
  facilityName: { fontSize: 15, fontWeight: "600", color: APP_TEXT },
  serviceType: { fontSize: 13, color: APP_TEXT_2 },

  waitBlock: {
    flexDirection: "row", gap: 12, paddingHorizontal: 20, paddingBottom: 16,
  },
  waitCard: { flex: 1, borderRadius: 16, padding: 16, alignItems: "center", gap: 4 },
  waitNum: { fontSize: 36, fontWeight: "900" },
  waitLbl: { fontSize: 11, fontWeight: "600", color: APP_TEXT_2 },

  timesRow: {
    flexDirection: "row", gap: 16, paddingHorizontal: 20, paddingBottom: 16,
    borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER, paddingTop: 12,
  },
  timeItem: { flexDirection: "row", alignItems: "center", gap: 5 },
  timeText: { fontSize: 12, color: APP_TEXT_3 },

  refreshBtn: {
    flexDirection: "row", alignItems: "center", justifyContent: "center",
    gap: 6, paddingVertical: 12,
    borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER,
  },
  refreshText: { fontSize: 13, fontWeight: "500", color: APP_TEXT_2 },

  emptyTitle: { fontSize: 17, fontWeight: "700", color: APP_TEXT, textAlign: "center" },
  emptySub: { fontSize: 13, color: APP_TEXT_3, textAlign: "center", paddingHorizontal: 24, lineHeight: 19 },
  autoRefreshNote: { fontSize: 11, color: APP_TEXT_3, textAlign: "center" },
});
