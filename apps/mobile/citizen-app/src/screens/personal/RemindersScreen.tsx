import React, { useState, useEffect, useCallback, useRef } from "react";
import {
  View, Text, ScrollView, Pressable, StyleSheet,
  RefreshControl, Switch, Alert,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import { getReminders, updateReminder, deleteReminder } from "../../services/remindersService";
import type { Reminder, ReminderType } from "../../types";
import { USE_SEED_DATA, SEED_REMINDERS } from "../../data/seedHealthRecords";
import { useToast } from "../../components/Toast";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT, APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

const TYPE_CFG: Record<ReminderType, { label: string; icon: React.ComponentProps<typeof Ionicons>["name"]; color: string; bg: string }> = {
  MEDICATION:  { label: "Medication",  icon: "medical",          color: APP_GREEN_DARK, bg: APP_GREEN_LIGHT  },
  APPOINTMENT: { label: "Appointment", icon: "calendar",         color: APP_GREEN,      bg: APP_GREEN_XLIGHT },
  LAB_CHECK:   { label: "Lab Check",   icon: "flask",            color: APP_GOLD,       bg: APP_GOLD_LIGHT   },
  FOLLOW_UP:   { label: "Follow-up",   icon: "refresh-circle",   color: APP_TEXT_2,     bg: "#EEF2F2"        },
};

const RECURRENCE_LABEL: Record<string, string> = {
  ONCE: "Once", DAILY: "Daily", WEEKLY: "Weekly",
};

function groupByType(reminders: Reminder[]): { type: ReminderType; items: Reminder[] }[] {
  const map = new Map<ReminderType, Reminder[]>();
  for (const r of reminders) {
    if (!map.has(r.type)) map.set(r.type, []);
    map.get(r.type)!.push(r);
  }
  return Array.from(map.entries()).map(([type, items]) => ({ type, items }));
}

export function RemindersScreen() {
  const toast    = useToast();
  const toastRef = useRef(toast);
  toastRef.current = toast;

  const [reminders, setReminders]       = useState<Reminder[]>([]);
  const [isLoading, setIsLoading]       = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [toggling, setToggling]         = useState<string | null>(null);

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    try {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 350));
        setReminders(SEED_REMINDERS);
      } else {
        const r = await getReminders({ size: 50 });
        setReminders(r.items);
      }
    } catch {
      toastRef.current.error("Could not load reminders.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  const onRefresh = useCallback(() => { setIsRefreshing(true); void load(true); }, [load]);

  const handleToggle = useCallback(async (rem: Reminder) => {
    if (USE_SEED_DATA) {
      setReminders((prev) => prev.map((r) => r.id === rem.id ? { ...r, enabled: !r.enabled } : r));
      return;
    }
    setToggling(rem.id);
    try {
      await updateReminder(rem.id, { enabled: !rem.enabled });
      void load(true);
    } catch {
      toastRef.current.error("Could not update reminder.");
    } finally {
      setToggling(null);
    }
  }, [load]);

  const handleDelete = useCallback((rem: Reminder) => {
    Alert.alert(
      "Delete Reminder",
      `Delete "${rem.title}"?`,
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Delete", style: "destructive",
          onPress: async () => {
            if (USE_SEED_DATA) {
              setReminders((prev) => prev.filter((r) => r.id !== rem.id));
              return;
            }
            try {
              await deleteReminder(rem.id);
              toastRef.current.success("Reminder deleted.");
              void load(true);
            } catch {
              toastRef.current.error("Could not delete reminder.");
            }
          },
        },
      ],
    );
  }, [load]);

  const active  = reminders.filter((r) => r.enabled).length;
  const groups  = groupByType(reminders);

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  return (
    <ScrollView
      style={s.root}
      contentContainerStyle={s.content}
      showsVerticalScrollIndicator={false}
      refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} tintColor={APP_GREEN} colors={[APP_GREEN]} />}
    >
      {/* Summary */}
      <View style={s.summaryStrip}>
        <View style={[s.summaryCard, { backgroundColor: APP_GREEN_LIGHT }]}>
          <Text style={[s.summaryNum, { color: APP_GREEN_DARK }]}>{active}</Text>
          <Text style={s.summaryLbl}>Active</Text>
        </View>
        <View style={[s.summaryCard, { backgroundColor: "#EEF2F2" }]}>
          <Text style={[s.summaryNum, { color: APP_TEXT_2 }]}>{reminders.length - active}</Text>
          <Text style={s.summaryLbl}>Paused</Text>
        </View>
        <View style={[s.summaryCard, { backgroundColor: APP_GREEN_XLIGHT }]}>
          <Text style={[s.summaryNum, { color: APP_GREEN }]}>{reminders.length}</Text>
          <Text style={s.summaryLbl}>Total</Text>
        </View>
      </View>

      {reminders.length === 0 ? (
        <View style={s.empty}>
          <Ionicons name="alarm-outline" size={52} color={APP_GREEN_LIGHT} />
          <Text style={s.emptyTitle}>No reminders set</Text>
          <Text style={s.emptySub}>Reminders for medications and appointments will appear here.</Text>
        </View>
      ) : (
        groups.map(({ type, items }) => {
          const cfg = TYPE_CFG[type] ?? TYPE_CFG.FOLLOW_UP;
          return (
            <View key={type}>
              {/* Group header */}
              <View style={s.groupHeader}>
                <View style={[s.groupHeaderIcon, { backgroundColor: cfg.bg }]}>
                  <Ionicons name={cfg.icon} size={13} color={cfg.color} />
                </View>
                <Text style={s.groupHeaderText}>{cfg.label}</Text>
                <Text style={s.groupHeaderCount}>{items.length}</Text>
              </View>

              {/* Group card */}
              <View style={s.groupCard}>
                {items.map((rem, idx) => (
                  <View key={rem.id}>
                    <View style={s.reminderRow}>
                      <View style={[s.reminderIcon, { backgroundColor: rem.enabled ? cfg.bg : "#F3F4F6" }]}>
                        <Ionicons name={cfg.icon} size={16} color={rem.enabled ? cfg.color : APP_TEXT_3} />
                      </View>
                      <View style={s.reminderInfo}>
                        <Text style={[s.reminderTitle, !rem.enabled && s.reminderTitleDisabled]}>
                          {rem.title}
                        </Text>
                        <View style={s.reminderMetaRow}>
                          <Text style={s.reminderTime}>
                            {new Date(rem.dateTime).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                          </Text>
                          <View style={s.recurrencePill}>
                            <Text style={s.recurrenceText}>{RECURRENCE_LABEL[rem.recurrence] ?? rem.recurrence}</Text>
                          </View>
                        </View>
                        {rem.description ? (
                          <Text style={s.reminderDesc} numberOfLines={1}>{rem.description}</Text>
                        ) : null}
                      </View>
                      <View style={s.reminderActions}>
                        <Switch
                          value={rem.enabled}
                          onValueChange={() => handleToggle(rem)}
                          trackColor={{ false: APP_BORDER, true: APP_GREEN_LIGHT }}
                          thumbColor={rem.enabled ? APP_GREEN : "#FFFFFF"}
                          disabled={toggling === rem.id}
                        />
                        <Pressable onPress={() => handleDelete(rem)} hitSlop={8} style={s.deleteBtn}>
                          <Ionicons name="trash-outline" size={16} color={APP_TEXT_3} />
                        </Pressable>
                      </View>
                    </View>
                    {idx < items.length - 1 ? <View style={s.divider} /> : null}
                  </View>
                ))}
              </View>
            </View>
          );
        })
      )}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  content: { padding: 16, gap: 14, paddingBottom: 40 },
  centred: { flex: 1, alignItems: "center", justifyContent: "center" },
  summaryStrip: { flexDirection: "row", gap: 10 },
  summaryCard: { flex: 1, borderRadius: 14, padding: 14, alignItems: "center", gap: 4 },
  summaryNum: { fontSize: 22, fontWeight: "800" },
  summaryLbl: { fontSize: 11, fontWeight: "600", color: APP_TEXT_2 },

  groupHeader: {
    flexDirection: "row", alignItems: "center", gap: 8, paddingHorizontal: 4, marginBottom: 6,
  },
  groupHeaderIcon: { width: 22, height: 22, borderRadius: 6, alignItems: "center", justifyContent: "center" },
  groupHeaderText: { fontSize: 12, fontWeight: "700", color: APP_TEXT_2, textTransform: "uppercase", letterSpacing: 0.8, flex: 1 },
  groupHeaderCount: { fontSize: 12, color: APP_TEXT_3 },

  groupCard: {
    backgroundColor: APP_SURFACE, borderRadius: 16, overflow: "hidden",
    shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.05, shadowRadius: 6, elevation: 2,
  },
  reminderRow: { flexDirection: "row", alignItems: "center", gap: 12, paddingHorizontal: 14, paddingVertical: 12 },
  reminderIcon: { width: 38, height: 38, borderRadius: 11, alignItems: "center", justifyContent: "center" },
  reminderInfo: { flex: 1 },
  reminderTitle: { fontSize: 14, fontWeight: "600", color: APP_TEXT },
  reminderTitleDisabled: { color: APP_TEXT_3 },
  reminderMetaRow: { flexDirection: "row", alignItems: "center", gap: 8, marginTop: 3 },
  reminderTime: { fontSize: 12, fontWeight: "600", color: APP_TEXT_2 },
  recurrencePill: { backgroundColor: APP_GREEN_XLIGHT, paddingHorizontal: 7, paddingVertical: 2, borderRadius: 8 },
  recurrenceText: { fontSize: 10, fontWeight: "700", color: APP_GREEN },
  reminderDesc: { fontSize: 12, color: APP_TEXT_3, marginTop: 2 },
  reminderActions: { flexDirection: "row", alignItems: "center", gap: 4 },
  deleteBtn: { padding: 4 },
  divider: { height: StyleSheet.hairlineWidth, backgroundColor: APP_BORDER, marginLeft: 64 },

  empty: { alignItems: "center", paddingVertical: 60, gap: 12 },
  emptyTitle: { fontSize: 17, fontWeight: "700", color: APP_TEXT },
  emptySub: { fontSize: 13, color: APP_TEXT_3, textAlign: "center", paddingHorizontal: 32, lineHeight: 19 },
});
