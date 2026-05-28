import React, {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  Animated,
  Pressable,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useSafeAreaInsets } from "react-native-safe-area-context";

export type ToastVariant = "success" | "error" | "warning" | "info";

export interface ToastConfig {
  message: string;
  variant?: ToastVariant;
  duration?: number;
  action?: { label: string; onPress: () => void };
}

interface ToastItem extends ToastConfig {
  id: string;
  translateY: Animated.Value;
  opacity: Animated.Value;
}

interface ToastContextValue {
  show: (config: ToastConfig) => void;
  success: (message: string, action?: ToastConfig["action"]) => void;
  error: (message: string, action?: ToastConfig["action"]) => void;
  warning: (message: string, action?: ToastConfig["action"]) => void;
  info: (message: string, action?: ToastConfig["action"]) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

const VARIANT_CONFIG: Record<
  ToastVariant,
  { bg: string; icon: string; iconColor: string; textColor: string; borderColor: string }
> = {
  success: {
    bg: "#ECFDF5",
    icon: "checkmark-circle",
    iconColor: "#059669",
    textColor: "#065F46",
    borderColor: "#A7F3D0",
  },
  error: {
    bg: "#FEF2F2",
    icon: "alert-circle",
    iconColor: "#DC2626",
    textColor: "#991B1B",
    borderColor: "#FECACA",
  },
  warning: {
    bg: "#FFFBEB",
    icon: "warning",
    iconColor: "#D97706",
    textColor: "#92400E",
    borderColor: "#FDE68A",
  },
  info: {
    bg: "#EFF6FF",
    icon: "information-circle",
    iconColor: "#2563EB",
    textColor: "#1E40AF",
    borderColor: "#BFDBFE",
  },
};

function ToastItem({
  item,
  onDismiss,
}: {
  item: ToastItem;
  onDismiss: (id: string) => void;
}) {
  const cfg = VARIANT_CONFIG[item.variant ?? "info"];

  return (
    <Animated.View
      style={[
        styles.toast,
        {
          backgroundColor: cfg.bg,
          borderColor: cfg.borderColor,
          transform: [{ translateY: item.translateY }],
          opacity: item.opacity,
        },
      ]}
    >
      <Ionicons name={cfg.icon as never} size={20} color={cfg.iconColor} />
      <Text style={[styles.message, { color: cfg.textColor }]} numberOfLines={3}>
        {item.message}
      </Text>
      {item.action ? (
        <Pressable
          onPress={() => {
            item.action?.onPress();
            onDismiss(item.id);
          }}
          style={[styles.actionBtn, { borderColor: cfg.borderColor }]}
          hitSlop={8}
        >
          <Text style={[styles.actionText, { color: cfg.iconColor }]}>
            {item.action.label}
          </Text>
        </Pressable>
      ) : null}
      <Pressable onPress={() => onDismiss(item.id)} hitSlop={12} style={styles.closeBtn}>
        <Ionicons name="close" size={16} color={cfg.iconColor} />
      </Pressable>
    </Animated.View>
  );
}

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const insets = useSafeAreaInsets();

  // Map ref: id → toast item (holds animated values). Avoids stale-closure deps.
  const toastMapRef = useRef<Map<string, ToastItem>>(new Map());
  const timers = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

  const dismiss = useCallback((id: string) => {
    const item = toastMapRef.current.get(id);
    if (!item) return;

    clearTimeout(timers.current.get(id));
    timers.current.delete(id);

    Animated.parallel([
      Animated.timing(item.opacity, { toValue: 0, duration: 200, useNativeDriver: true }),
      Animated.timing(item.translateY, { toValue: -20, duration: 200, useNativeDriver: true }),
    ]).start(() => {
      toastMapRef.current.delete(id);
      setToasts((prev) => prev.filter((t) => t.id !== id));
    });
  }, []); // stable — no state deps, uses ref

  const show = useCallback((config: ToastConfig) => {
    const id = `toast-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    const translateY = new Animated.Value(-60);
    const opacity = new Animated.Value(0);
    const duration = config.duration ?? 3500;

    const item: ToastItem = {
      ...config,
      id,
      translateY,
      opacity,
      variant: config.variant ?? "info",
      duration,
    };

    toastMapRef.current.set(id, item);
    setToasts((prev) => [...prev.slice(-2), item]);

    Animated.parallel([
      Animated.spring(translateY, { toValue: 0, tension: 100, friction: 10, useNativeDriver: true }),
      Animated.timing(opacity, { toValue: 1, duration: 200, useNativeDriver: true }),
    ]).start();

    const timer = setTimeout(() => {
      Animated.parallel([
        Animated.timing(opacity, { toValue: 0, duration: 250, useNativeDriver: true }),
        Animated.timing(translateY, { toValue: -40, duration: 250, useNativeDriver: true }),
      ]).start(() => {
        toastMapRef.current.delete(id);
        timers.current.delete(id);
        setToasts((prev) => prev.filter((t) => t.id !== id));
      });
    }, duration);

    timers.current.set(id, timer);
  }, []); // stable — no state deps, uses functional setToasts + refs

  const success = useCallback(
    (message: string, action?: ToastConfig["action"]) => show({ message, variant: "success", action }),
    [show],
  );
  const error = useCallback(
    (message: string, action?: ToastConfig["action"]) => show({ message, variant: "error", action }),
    [show],
  );
  const warning = useCallback(
    (message: string, action?: ToastConfig["action"]) => show({ message, variant: "warning", action }),
    [show],
  );
  const info = useCallback(
    (message: string, action?: ToastConfig["action"]) => show({ message, variant: "info", action }),
    [show],
  );

  // Memoize context value so consumers never get a new object reference on re-render.
  // All 5 functions are stable (empty or [show] deps), so this memo never invalidates.
  const contextValue = useMemo<ToastContextValue>(
    () => ({ show, success, error, warning, info }),
    [show, success, error, warning, info],
  );

  return (
    <ToastContext.Provider value={contextValue}>
      {children}
      <View
        style={[styles.container, { top: insets.top + 8 }]}
        pointerEvents="box-none"
      >
        {toasts.map((item) => (
          <ToastItem key={item.id} item={item} onDismiss={dismiss} />
        ))}
      </View>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used inside ToastProvider");
  return ctx;
}

const styles = StyleSheet.create({
  container: {
    position: "absolute",
    left: 16,
    right: 16,
    zIndex: 9999,
    gap: 8,
    pointerEvents: "box-none",
  } as never,
  toast: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    borderRadius: 14,
    borderWidth: 1,
    paddingVertical: 12,
    paddingHorizontal: 14,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.12,
    shadowRadius: 12,
    elevation: 8,
  },
  message: {
    flex: 1,
    fontSize: 14,
    fontWeight: "500",
    lineHeight: 19,
  },
  actionBtn: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 8,
    borderWidth: 1,
  },
  actionText: {
    fontSize: 13,
    fontWeight: "700",
  },
  closeBtn: {
    padding: 2,
  },
});
