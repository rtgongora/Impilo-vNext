import React, { useState, useEffect, useCallback, useRef } from "react";
import {
  View,
  Text,
  ScrollView,
  Pressable,
  StyleSheet,
  Modal,
  Animated,
  Dimensions,
  RefreshControl,
  Alert,
  KeyboardAvoidingView,
  Platform,
  TextInput,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import {
  Screen,
  Header,
  LoadingSpinner,
} from "@impilo/mobile-design-system";
import {
  fetchServices,
  fetchServiceRequests,
  requestService,
  cancelServiceRequest,
} from "../../services/marketplaceService";
import type { MarketplaceService as MktService, ServiceRequest as SvcReq } from "../../types";
import { CartScreen } from "./CartScreen";
import { useToast } from "../../components/Toast";
import { SkeletonServiceList } from "../../components/SkeletonLoader";
import {
  APP_GREEN,
  APP_GREEN_LIGHT,
  APP_GREEN_XLIGHT,
  APP_GREEN_DARK,
  APP_SURFACE,
  APP_BG,
  APP_TEXT,
  APP_TEXT_2,
  APP_TEXT_3,
  APP_BORDER,
  APP_ERROR,
  APP_RED_LIGHT,
} from "../../lib/colors";

const { height: SCREEN_HEIGHT } = Dimensions.get("window");

type MarketplaceTab = "browse" | "requests" | "cart";

const CATEGORY_ICON: Record<string, React.ComponentProps<typeof Ionicons>["name"]> = {
  CONSULTATION: "person-outline",
  LAB_TESTS:    "flask-outline",
  PHARMACY:     "medkit-outline",
  IMAGING:      "scan-outline",
  WELLNESS:     "heart-outline",
  HOME_CARE:    "home-outline",
};

const CATEGORY_LABEL: Record<string, string> = {
  CONSULTATION: "Consultation",
  LAB_TESTS:    "Lab Tests",
  PHARMACY:     "Pharmacy",
  IMAGING:      "Imaging",
  WELLNESS:     "Wellness",
  HOME_CARE:    "Home Care",
};

const CATEGORIES = [
  { label: "All",          value: "",             icon: "grid-outline" as const },
  { label: "Consultation", value: "CONSULTATION", icon: "person-outline" as const },
  { label: "Lab Tests",    value: "LAB_TESTS",    icon: "flask-outline" as const },
  { label: "Pharmacy",     value: "PHARMACY",     icon: "medkit-outline" as const },
  { label: "Imaging",      value: "IMAGING",      icon: "scan-outline" as const },
  { label: "Wellness",     value: "WELLNESS",     icon: "heart-outline" as const },
  { label: "Home Care",    value: "HOME_CARE",    icon: "home-outline" as const },
];

const STATUS_STYLE: Record<string, { text: string; bg: string }> = {
  PENDING:     { text: APP_TEXT_2, bg: APP_BG },
  CONFIRMED:   { text: APP_GREEN_DARK, bg: APP_GREEN_XLIGHT },
  IN_PROGRESS: { text: APP_GREEN,      bg: APP_GREEN_XLIGHT },
  COMPLETED:   { text: APP_TEXT_3,     bg: APP_BG },
  CANCELLED:   { text: APP_ERROR,      bg: APP_RED_LIGHT },
};

const TABS: { id: MarketplaceTab; label: string; icon: React.ComponentProps<typeof Ionicons>["name"] }[] = [
  { id: "browse",   label: "Browse",   icon: "search-outline" },
  { id: "requests", label: "Requests", icon: "receipt-outline" },
  { id: "cart",     label: "Cart",     icon: "cart-outline" },
];

// ─── Booking sheet ─────────────────────────────────────────────────────────
function BookingSheet({
  service,
  onClose,
  onSuccess,
}: {
  service: MktService;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const toastCtx = useToast();
  const sheetToastRef = useRef(toastCtx);
  sheetToastRef.current = toastCtx;

  const slideAnim  = useRef(new Animated.Value(SCREEN_HEIGHT)).current;
  const backdropAnim = useRef(new Animated.Value(0)).current;
  const [preferredDate, setPreferredDate] = useState("");
  const [notes, setNotes]       = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    Animated.parallel([
      Animated.spring(slideAnim,   { toValue: 0, tension: 80, friction: 12, useNativeDriver: true }),
      Animated.timing(backdropAnim, { toValue: 1, duration: 250, useNativeDriver: true }),
    ]).start();
  }, [slideAnim, backdropAnim]);

  const dismiss = useCallback(() => {
    Animated.parallel([
      Animated.timing(slideAnim,   { toValue: SCREEN_HEIGHT, duration: 250, useNativeDriver: true }),
      Animated.timing(backdropAnim, { toValue: 0,            duration: 200, useNativeDriver: true }),
    ]).start(onClose);
  }, [slideAnim, backdropAnim, onClose]);

  const handleConfirm = useCallback(async () => {
    setSubmitting(true);
    try {
      await requestService({ serviceId: service.id, preferredDate: preferredDate || undefined, notes: notes || undefined });
      dismiss();
      onSuccess();
      sheetToastRef.current.success(`"${service.name}" booked!`);
    } catch {
      sheetToastRef.current.error("Booking failed. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }, [service, preferredDate, notes, dismiss, onSuccess]);

  return (
    <Modal transparent animationType="none" onRequestClose={dismiss}>
      <Animated.View style={[styles.backdrop, { opacity: backdropAnim }]}>
        <Pressable style={StyleSheet.absoluteFill} onPress={dismiss} />
      </Animated.View>

      <KeyboardAvoidingView
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        style={styles.sheetWrapper}
        pointerEvents="box-none"
      >
        <Animated.View style={[styles.sheet, { transform: [{ translateY: slideAnim }] }]}>
          <View style={styles.sheetHandle} />

          {/* Header */}
          <View style={styles.sheetHeader}>
            <View style={styles.sheetIconWrap}>
              <Ionicons
                name={CATEGORY_ICON[service.category] ?? "medical-outline"}
                size={20}
                color={APP_GREEN}
              />
            </View>
            <View style={styles.sheetTitleBlock}>
              <Text style={styles.sheetTitle}>{service.name}</Text>
              <Text style={styles.sheetSubtitle}>{service.facilityName}</Text>
            </View>
            <Pressable onPress={dismiss} hitSlop={12} style={styles.sheetCloseBtn}>
              <Ionicons name="close" size={18} color={APP_TEXT_2} />
            </Pressable>
          </View>

          <View style={styles.divider} />

          <ScrollView
            contentContainerStyle={styles.sheetBody}
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}
          >
            {service.description ? (
              <Text style={styles.sheetDesc}>{service.description}</Text>
            ) : null}

            {service.price !== undefined ? (
              <View style={styles.pricePill}>
                <Text style={styles.pricePillText}>
                  {`${service.currency ?? "USD"} ${service.price}`}
                </Text>
                {service.rating !== undefined ? (
                  <>
                    <View style={styles.pricePillDot} />
                    <Ionicons name="star" size={13} color="#D97706" />
                    <Text style={styles.ratingText}>{service.rating.toFixed(1)}</Text>
                  </>
                ) : null}
              </View>
            ) : null}

            <Text style={styles.fieldLabel}>Preferred Date</Text>
            <View style={styles.inputWrap}>
              <Ionicons name="calendar-outline" size={15} color={APP_TEXT_3} />
              <TextInput
                style={styles.input}
                value={preferredDate}
                onChangeText={setPreferredDate}
                placeholder="e.g. 2026-06-20"
                placeholderTextColor={APP_TEXT_3}
                testID="booking-preferred-date"
              />
            </View>

            <Text style={styles.fieldLabel}>Notes</Text>
            <View style={[styles.inputWrap, { alignItems: "flex-start", paddingTop: 12 }]}>
              <TextInput
                style={[styles.input, { minHeight: 72, textAlignVertical: "top" }]}
                value={notes}
                onChangeText={setNotes}
                placeholder="Any special requirements"
                placeholderTextColor={APP_TEXT_3}
                multiline
                testID="booking-notes"
              />
            </View>
          </ScrollView>

          <View style={styles.sheetFooter}>
            <Pressable onPress={dismiss} style={styles.btnSecondary}>
              <Text style={styles.btnSecondaryText}>Cancel</Text>
            </Pressable>
            <Pressable
              onPress={handleConfirm}
              disabled={submitting}
              style={[styles.btnPrimary, submitting && { opacity: 0.5 }]}
              testID="confirm-booking"
            >
              {submitting ? (
                <LoadingSpinner size="sm" />
              ) : (
                <>
                  <Ionicons name="checkmark-circle" size={17} color="#fff" />
                  <Text style={styles.btnPrimaryText}>Confirm Booking</Text>
                </>
              )}
            </Pressable>
          </View>
        </Animated.View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

// ─── Main screen ──────────────────────────────────────────────────────────
export function MarketplaceScreen() {
  const toast    = useToast();
  const toastRef = useRef(toast);
  toastRef.current = toast;

  const [tab,      setTab]      = useState<MarketplaceTab>("browse");
  const [services, setServices] = useState<MktService[]>([]);
  const [requests, setRequests] = useState<SvcReq[]>([]);
  const [isLoading,    setIsLoading]    = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [search,   setSearch]   = useState("");
  const [category, setCategory] = useState("");
  const [selectedService, setSelectedService] = useState<MktService | null>(null);

  const loadServices = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    try {
      const result = await fetchServices({ category: category || undefined, search: search || undefined, size: 50 });
      setServices(result.items);
    } catch {
      toastRef.current.error("Could not load services.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [category, search]);

  const loadRequests = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    try {
      const result = await fetchServiceRequests({ size: 50 });
      setRequests(result.items);
    } catch {
      toastRef.current.error("Could not load your requests.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    if (tab === "browse")       void loadServices();
    else if (tab === "requests") void loadRequests();
    else                         setIsLoading(false);
  }, [tab, loadServices, loadRequests]);

  const onRefresh = useCallback(() => {
    setIsRefreshing(true);
    if (tab === "browse") void loadServices(true);
    else                  void loadRequests(true);
  }, [tab, loadServices, loadRequests]);

  const handleCancel = useCallback((req: SvcReq) => {
    Alert.alert(
      "Cancel Request",
      `Cancel your request for "${req.serviceName}"?`,
      [
        { text: "Keep it", style: "cancel" },
        {
          text: "Cancel Request",
          style: "destructive",
          onPress: async () => {
            try {
              await cancelServiceRequest(req.id);
              toastRef.current.success("Request cancelled.");
              void loadRequests(true);
            } catch {
              toastRef.current.error("Could not cancel request.");
            }
          },
        },
      ],
    );
  }, [loadRequests]);

  return (
    <Screen>
      <Header title="Health Services" />

      {/* Tab bar */}
      <View style={styles.tabBar}>
        {TABS.map((t) => (
          <Pressable
            key={t.id}
            onPress={() => setTab(t.id)}
            style={[styles.tabPill, tab === t.id && styles.tabPillActive]}
            testID={`marketplace-tab-${t.id}`}
          >
            <Ionicons name={t.icon} size={14} color={tab === t.id ? APP_GREEN : APP_TEXT_3} />
            <Text style={[styles.tabPillText, tab === t.id && styles.tabPillTextActive]}>
              {t.label}
            </Text>
          </Pressable>
        ))}
      </View>

      <ScrollView
        testID="marketplace-screen"
        style={styles.scroll}
        contentContainerStyle={styles.container}
        showsVerticalScrollIndicator={false}
        refreshControl={
          tab !== "cart" ? (
            <RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} tintColor={APP_GREEN} colors={[APP_GREEN]} />
          ) : undefined
        }
      >
        {/* ── BROWSE ───────────────────────────────────────────────────── */}
        {tab === "browse" ? (
          <>
            {/* Search */}
            <View style={styles.searchWrap}>
              <Ionicons name="search-outline" size={16} color={APP_TEXT_3} />
              <TextInput
                style={styles.searchInput}
                value={search}
                onChangeText={setSearch}
                placeholder="Search services..."
                placeholderTextColor={APP_TEXT_3}
                returnKeyType="search"
                testID="marketplace-search"
              />
              {search.length > 0 ? (
                <Pressable onPress={() => setSearch("")} hitSlop={10}>
                  <Ionicons name="close-circle" size={16} color={APP_TEXT_3} />
                </Pressable>
              ) : null}
            </View>

            {/* Category chips */}
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipRow}>
              {CATEGORIES.map((cat) => (
                <Pressable
                  key={cat.value}
                  onPress={() => setCategory(cat.value)}
                  style={[styles.chip, category === cat.value && styles.chipActive]}
                  testID={`category-${cat.value || "all"}`}
                >
                  <Ionicons name={cat.icon} size={13} color={category === cat.value ? APP_GREEN : APP_TEXT_3} />
                  <Text style={[styles.chipText, category === cat.value && styles.chipTextActive]}>
                    {cat.label}
                  </Text>
                </Pressable>
              ))}
            </ScrollView>

            {/* List */}
            {isLoading ? (
              <SkeletonServiceList />
            ) : services.length === 0 ? (
              <View style={styles.emptyWrap}>
                <Ionicons name="storefront-outline" size={36} color={APP_TEXT_3} />
                <Text style={styles.emptyTitle}>No services found</Text>
                <Text style={styles.emptyMsg}>Try adjusting your search or category</Text>
              </View>
            ) : (
              services.map((svc) => (
                <Pressable
                  key={svc.id}
                  testID={`service-${svc.id}`}
                  style={({ pressed }) => [styles.serviceCard, pressed && { opacity: 0.94 }]}
                  onPress={() => svc.available && setSelectedService(svc)}
                >
                  {/* Icon */}
                  <View style={styles.serviceIconWrap}>
                    <Ionicons
                      name={CATEGORY_ICON[svc.category] ?? "medical-outline"}
                      size={20}
                      color={APP_GREEN}
                    />
                  </View>

                  {/* Body */}
                  <View style={styles.serviceBody}>
                    <View style={styles.serviceRow}>
                      <Text style={styles.serviceName}>{svc.name}</Text>
                      {svc.available ? (
                        <Pressable
                          style={styles.bookBtn}
                          onPress={() => setSelectedService(svc)}
                          testID={`book-${svc.id}`}
                          hitSlop={4}
                        >
                          <Text style={styles.bookBtnText}>Book</Text>
                        </Pressable>
                      ) : (
                        <Text style={styles.unavailableText}>Unavailable</Text>
                      )}
                    </View>

                    <Text style={styles.facilityText} numberOfLines={1}>{svc.facilityName}</Text>
                    <Text style={styles.serviceDesc} numberOfLines={2}>{svc.description}</Text>

                    <View style={styles.serviceMeta}>
                      {svc.price !== undefined ? (
                        <Text style={styles.priceText}>{`${svc.currency ?? "USD"} ${svc.price}`}</Text>
                      ) : null}
                      {svc.rating !== undefined ? (
                        <View style={styles.ratingWrap}>
                          <Ionicons name="star" size={11} color="#D97706" />
                          <Text style={styles.ratingVal}>{svc.rating.toFixed(1)}</Text>
                        </View>
                      ) : null}
                      <Text style={styles.categoryLabel}>
                        {CATEGORY_LABEL[svc.category] ?? svc.category}
                      </Text>
                    </View>
                  </View>
                </Pressable>
              ))
            )}
          </>
        ) : null}

        {/* ── REQUESTS ─────────────────────────────────────────────────── */}
        {tab === "requests" ? (
          <>
            {isLoading ? (
              <LoadingSpinner size="md" />
            ) : requests.length === 0 ? (
              <View style={styles.emptyWrap}>
                <Ionicons name="receipt-outline" size={36} color={APP_TEXT_3} />
                <Text style={styles.emptyTitle}>No requests yet</Text>
                <Text style={styles.emptyMsg}>Browse services and make your first booking</Text>
                <Pressable onPress={() => setTab("browse")} style={styles.emptyAction}>
                  <Text style={styles.emptyActionText}>Browse Services</Text>
                </Pressable>
              </View>
            ) : (
              requests.map((req) => {
                const sc = STATUS_STYLE[req.status] ?? STATUS_STYLE.PENDING;
                return (
                  <View key={req.id} style={styles.requestCard} testID={`request-${req.id}`}>
                    <View style={styles.requestTop}>
                      <View style={{ flex: 1 }}>
                        <Text style={styles.requestName}>{req.serviceName}</Text>
                        <Text style={styles.requestFacility}>{req.facilityName}</Text>
                      </View>
                      <View style={[styles.statusPill, { backgroundColor: sc.bg }]}>
                        <Text style={[styles.statusText, { color: sc.text }]}>
                          {req.status.replace("_", " ")}
                        </Text>
                      </View>
                    </View>

                    <View style={styles.requestMeta}>
                      <Ionicons name="calendar-outline" size={12} color={APP_TEXT_3} />
                      <Text style={styles.requestDate}>
                        {new Date(req.requestedAt).toLocaleDateString()}
                      </Text>
                      {req.scheduledAt ? (
                        <>
                          <Text style={styles.metaDot}>·</Text>
                          <Ionicons name="time-outline" size={12} color={APP_GREEN} />
                          <Text style={styles.scheduledText}>
                            {new Date(req.scheduledAt).toLocaleDateString()}
                          </Text>
                        </>
                      ) : null}
                    </View>

                    {req.trackingNumber ? (
                      <Text style={styles.trackingText}>{req.trackingNumber}</Text>
                    ) : null}

                    {(req.status === "PENDING" || req.status === "CONFIRMED") ? (
                      <Pressable
                        style={styles.cancelBtn}
                        onPress={() => handleCancel(req)}
                        testID={`cancel-request-${req.id}`}
                      >
                        <Text style={styles.cancelBtnText}>Cancel Request</Text>
                      </Pressable>
                    ) : null}
                  </View>
                );
              })
            )}
          </>
        ) : null}

        {tab === "cart" ? <CartScreen /> : null}
      </ScrollView>

      {selectedService ? (
        <BookingSheet
          service={selectedService}
          onClose={() => setSelectedService(null)}
          onSuccess={() => {
            setSelectedService(null);
            setTab("requests");
            void loadRequests();
          }}
        />
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  // ── Tab bar ───────────────────────────────────────────────────────────────
  tabBar: {
    flexDirection: "row",
    gap: 8,
    paddingHorizontal: 16,
    paddingVertical: 10,
    backgroundColor: APP_SURFACE,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: APP_BORDER,
  },
  tabPill: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 5,
    paddingVertical: 9,
    borderRadius: 20,
    backgroundColor: APP_BG,
  },
  tabPillActive: { backgroundColor: APP_GREEN_XLIGHT },
  tabPillText: { fontSize: 13, fontWeight: "500", color: APP_TEXT_3 },
  tabPillTextActive: { color: APP_GREEN, fontWeight: "700" },

  // ── Layout ────────────────────────────────────────────────────────────────
  scroll: { flex: 1, backgroundColor: APP_BG },
  container: { padding: 16, paddingBottom: 48, gap: 10 },

  // ── Search ────────────────────────────────────────────────────────────────
  searchWrap: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    backgroundColor: APP_SURFACE,
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: APP_BORDER,
  },
  searchInput: { flex: 1, fontSize: 14, color: APP_TEXT, padding: 0 },

  // ── Category chips ────────────────────────────────────────────────────────
  chipRow: { gap: 8, paddingBottom: 2 },
  chip: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: APP_SURFACE,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: APP_BORDER,
  },
  chipActive: {
    backgroundColor: APP_GREEN_XLIGHT,
    borderColor: APP_GREEN_LIGHT,
  },
  chipText: { fontSize: 13, fontWeight: "500", color: APP_TEXT_2 },
  chipTextActive: { color: APP_GREEN, fontWeight: "600" },

  // ── Service card ──────────────────────────────────────────────────────────
  serviceCard: {
    flexDirection: "row",
    gap: 12,
    backgroundColor: APP_SURFACE,
    borderRadius: 16,
    padding: 14,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: APP_BORDER,
  },
  serviceIconWrap: {
    width: 40,
    height: 40,
    borderRadius: 12,
    backgroundColor: APP_GREEN_XLIGHT,
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
    marginTop: 2,
  },
  serviceBody: { flex: 1, gap: 4 },
  serviceRow: { flexDirection: "row", alignItems: "flex-start", gap: 8 },
  serviceName: { flex: 1, fontSize: 14, fontWeight: "700", color: APP_TEXT, lineHeight: 20 },
  bookBtn: {
    backgroundColor: APP_GREEN,
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 10,
    flexShrink: 0,
  },
  bookBtnText: { fontSize: 12, fontWeight: "700", color: "#fff" },
  unavailableText: { fontSize: 12, color: APP_TEXT_3, paddingTop: 4 },
  facilityText: { fontSize: 12, color: APP_TEXT_3 },
  serviceDesc: { fontSize: 13, color: APP_TEXT_2, lineHeight: 18 },
  serviceMeta: { flexDirection: "row", alignItems: "center", gap: 8, marginTop: 2 },
  priceText: { fontSize: 13, fontWeight: "700", color: APP_TEXT },
  ratingWrap: { flexDirection: "row", alignItems: "center", gap: 3 },
  ratingVal: { fontSize: 12, fontWeight: "600", color: "#D97706" },
  categoryLabel: { fontSize: 12, color: APP_TEXT_3, marginLeft: "auto" },

  // ── Request card ──────────────────────────────────────────────────────────
  requestCard: {
    backgroundColor: APP_SURFACE,
    borderRadius: 16,
    padding: 14,
    gap: 8,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: APP_BORDER,
  },
  requestTop: { flexDirection: "row", alignItems: "flex-start", gap: 10 },
  requestName: { fontSize: 14, fontWeight: "700", color: APP_TEXT },
  requestFacility: { fontSize: 12, color: APP_TEXT_3, marginTop: 2 },
  statusPill: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 20,
    flexShrink: 0,
  },
  statusText: { fontSize: 11, fontWeight: "700", textTransform: "uppercase", letterSpacing: 0.5 },
  requestMeta: { flexDirection: "row", alignItems: "center", gap: 5 },
  requestDate: { fontSize: 12, color: APP_TEXT_3 },
  metaDot: { color: APP_TEXT_3, fontSize: 12 },
  scheduledText: { fontSize: 12, color: APP_GREEN, fontWeight: "600" },
  trackingText: { fontSize: 12, color: APP_TEXT_3, fontVariant: ["tabular-nums"] },
  cancelBtn: {
    alignSelf: "flex-start",
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: "#FECACA",
    backgroundColor: APP_RED_LIGHT,
  },
  cancelBtnText: { fontSize: 12, fontWeight: "600", color: APP_ERROR },

  // ── Empty state ───────────────────────────────────────────────────────────
  emptyWrap: { alignItems: "center", paddingVertical: 60, gap: 8 },
  emptyTitle: { fontSize: 16, fontWeight: "700", color: APP_TEXT, marginTop: 4 },
  emptyMsg: { fontSize: 14, color: APP_TEXT_3, textAlign: "center", paddingHorizontal: 40, lineHeight: 20 },
  emptyAction: {
    marginTop: 8,
    paddingHorizontal: 20,
    paddingVertical: 10,
    backgroundColor: APP_GREEN,
    borderRadius: 12,
  },
  emptyActionText: { color: "#fff", fontWeight: "700", fontSize: 13 },

  // ── Booking sheet ─────────────────────────────────────────────────────────
  backdrop: { ...StyleSheet.absoluteFillObject, backgroundColor: "rgba(0,0,0,0.45)" },
  sheetWrapper: { position: "absolute", bottom: 0, left: 0, right: 0, justifyContent: "flex-end" },
  sheet: {
    backgroundColor: APP_SURFACE,
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    maxHeight: SCREEN_HEIGHT * 0.85,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.12,
    shadowRadius: 16,
    elevation: 20,
  },
  sheetHandle: {
    width: 36,
    height: 4,
    borderRadius: 2,
    backgroundColor: APP_BORDER,
    alignSelf: "center",
    marginTop: 12,
    marginBottom: 4,
  },
  sheetHeader: {
    flexDirection: "row",
    alignItems: "flex-start",
    paddingHorizontal: 20,
    paddingVertical: 16,
    gap: 12,
  },
  sheetIconWrap: {
    width: 44,
    height: 44,
    borderRadius: 14,
    backgroundColor: APP_GREEN_XLIGHT,
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
  },
  sheetTitleBlock: { flex: 1 },
  sheetTitle: { fontSize: 16, fontWeight: "800", color: APP_TEXT },
  sheetSubtitle: { fontSize: 13, color: APP_TEXT_3, marginTop: 2 },
  sheetCloseBtn: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: APP_BG,
    alignItems: "center",
    justifyContent: "center",
  },
  divider: { height: StyleSheet.hairlineWidth, backgroundColor: APP_BORDER },
  sheetBody: { padding: 20, gap: 14, paddingBottom: 8 },
  sheetDesc: { fontSize: 14, color: APP_TEXT_2, lineHeight: 21 },
  pricePill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    alignSelf: "flex-start",
    backgroundColor: APP_GREEN_XLIGHT,
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 12,
  },
  pricePillText: { fontSize: 14, fontWeight: "700", color: APP_GREEN },
  pricePillDot: { width: 3, height: 3, borderRadius: 1.5, backgroundColor: APP_GREEN_LIGHT },
  ratingText: { fontSize: 13, fontWeight: "600", color: "#D97706" },
  fieldLabel: { fontSize: 12, fontWeight: "600", color: APP_TEXT_3, textTransform: "uppercase", letterSpacing: 0.7 },
  inputWrap: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    backgroundColor: APP_BG,
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: APP_BORDER,
  },
  input: { flex: 1, fontSize: 14, color: APP_TEXT, padding: 0 },
  sheetFooter: {
    flexDirection: "row",
    gap: 10,
    padding: 20,
    paddingBottom: 36,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: APP_BORDER,
  },
  btnSecondary: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    paddingVertical: 14,
    borderRadius: 14,
    backgroundColor: APP_BG,
  },
  btnSecondaryText: { fontSize: 15, fontWeight: "600", color: APP_TEXT_2 },
  btnPrimary: {
    flex: 2,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    paddingVertical: 14,
    borderRadius: 14,
    backgroundColor: APP_GREEN,
  },
  btnPrimaryText: { fontSize: 15, fontWeight: "700", color: "#fff" },
});
