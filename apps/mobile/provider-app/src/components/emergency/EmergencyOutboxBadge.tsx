import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet } from "react-native";
import { Button, Badge, colors } from "@impilo/mobile-design-system";
import { useAppStore } from "../../stores/appStore";
import { countPendingEmergencyWrites, flushEmergencyOutbox } from "../../services/emergencyOutboxService";

export function EmergencyOutboxBadge({ onFlushComplete }: { onFlushComplete?: () => void }) {
  const { isOnline, pendingSyncCount } = useAppStore();
  const [flushing, setFlushing] = useState(false);

  const refresh = useCallback(async () => {
    await countPendingEmergencyWrites().then((n) => useAppStore.getState().setPendingSyncCount(n));
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  if (pendingSyncCount === 0) return null;

  const flush = async () => {
    setFlushing(true);
    try {
      await flushEmergencyOutbox();
      onFlushComplete?.();
    } finally {
      setFlushing(false);
      await refresh();
    }
  };

  return (
    <View style={s.banner} testID="emergency-outbox-badge">
      <Badge label={`${pendingSyncCount} pending`} variant="warning" />
      <Text style={s.text}>
        {isOnline
          ? "Emergency writes queued offline — flush when ready."
          : "Emergency writes will sync when you reconnect."}
      </Text>
      {isOnline ? (
        <Button title={flushing ? "Flushing…" : "Flush queue"} size="sm" variant="outline" onPress={flush} disabled={flushing} />
      ) : null}
    </View>
  );
}

const s = StyleSheet.create({
  banner: {
    backgroundColor: colors.ui.warning.light,
    borderRadius: 8,
    padding: 10,
    gap: 6,
    borderWidth: 1,
    borderColor: colors.ui.warning.main,
  },
  text: { fontSize: 12, color: colors.gray[700] },
});
