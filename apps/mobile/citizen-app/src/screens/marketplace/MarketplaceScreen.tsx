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
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  Badge,
  TextField,
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
  APP_SURFACE,
  APP_BG,
  APP_TEXT,
  APP_TEXT_2,
  APP_TEXT_3,
  APP_BORDER,
  APP_ERROR,
} from "../../lib/colors";

const { height: SCREEN_HEIGHT } = Dimensions.get("window");

type MarketplaceTab = "browse" | "requests" | "cart";

const CATEGORIES = [
  { label: "All",          value: "",             icon: "grid-outline" },
  { label: "Consultation", value: "CONSULTATION", icon: "person-outline" },
  { label: "Lab Tests",    value: "LAB_TESTS",    icon: "flask-outline" },
  { label: "Pharmacy",     value: "PHARMACY",     icon: "medkit-outline" },
  { label: "Imaging",      value: "IMAGING",      icon: "scan-outline" },
  { label: "Wellness",     value: "WELLNESS",     icon: "fitness-outline" },
  { label: "Home Care",    value: "HOME_CARE",    icon: "home-outline" },
] as const;

const STATUS_VARIANT: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  PENDING:     "outline",
  CONFIRMED:   "default",
  IN_PROGRESS: "secondary",
  COMPLETED:   "secondary",
  CANCELLED:   "destructive",
};

const TABS: { id: MarketplaceTab; label: string; icon: React.ComponentProps<typeof Ionicons>["name"] }[] = [
  { id: "browse",   label: "Browse",   icon: "search-outline" },
  { id: "requests", label: "Requests", icon: "receipt-outline" },
  { id: "cart",     label: "Cart",     icon: "cart-outline" },
];

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
  const slideAnim = useRef(new Animated.Value(SCREEN_HEIGHT)).current;
  const backdropAnim = useRef(new Animated.Value(0)).current;
  const [preferredDate, setPreferredDate] = useState("");
  const [notes, setNotes] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    Animated.parallel([
      Animated.spring(slideAnim, { toValue: 0, tension: 80, friction: 12, useNativeDriver: true }),
      Animated.timing(backdropAnim, { toValue: 1, duration: 250, useNativeDriver: true }),
    ]).start();
  }, [slideAnim, backdropAnim]);

  const dismiss = useCallback(() => {
    Animated.parallel([
      Animated.timing(slideAnim, { toValue: SCREEN_HEIGHT, duration: 250, useNativeDriver: true }),
      Animated.timing(backdropAnim, { toValue: 0, duration: 200, useNativeDriver: true }),
    ]).start(onClose);
  }, [slideAnim, backdropAnim, onClose]);

  const handleConfirm = useCallback(async () => {
    setSubmitting(true);
    try {
      await requestService({
        serviceId: service.id,
        preferredDate: preferredDate || undefined,
        notes: notes || undefined,
      });
      dismiss();
      onSuccess();
      sheetToastRef.current.success(`"${service.name}" booked successfully!`);
    } catch (err) {
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
          {/* Handle */}
          <View style={styles.sheetHandle} />

          {/* Header */}
          <View style={styles.sheetHeader}>
            <View style={styles.sheetTitleBlock}>
              <Text style={styles.sheetTitle}>{service.name}</Text>
              <Text style={styles.sheetSubtitle}>{service.facilityName}</Text>
            </View>
            <Pressable onPress={dismiss} hitSlop={12} style={styles.sheetClose}>
              <Ionicons name="close" size={22} color={APP_TEXT_2} />
            </Pressable>
          </View>

          <View style={styles.sheetDivider} />

          <ScrollView contentContainerStyle={styles.sheetBody} keyboardShouldPersistTaps="handled">
            {/* Description */}
            {service.description ? (
              <Text style={styles.sheetServiceDesc}>{service.description}</Text>
            ) : null}

            {/* Price */}
            {service.price !== undefined ? (
              <View style={styles.pricePill}>
                <Ionicons name="pricetag" size={14} color={APP_GREEN} />
                <Text style={styles.priceText}>
                  {`${service.currency ?? ""} ${service.price}`}
                </Text>
              </View>
            ) : null}

            {/* Rating */}
            {service.rating !== undefined ? (
              <View style={styles.ratingRow}>
                {Array.from({ length: 5 }).map((_, i) => (
                  <Ionicons
                    key={i}
                    name={i < Math.round(service.rating!) ? "star" : "star-outline"}
                    size={16}
                    color="#D97706"
                  />
                ))}
                <Text style={styles.ratingLabel}>{service.rating.toFixed(1)}</Text>
              </View>
            ) : null}

            <View style={styles.formSection}>
              <Text style={styles.formSectionLabel}>Booking Details</Text>
              <TextField
                label="Preferred Date (optional)"
                value={preferredDate}
                onChange={setPreferredDate}
                placeholder="e.g. 2026-06-15"
                testID="booking-preferred-date"
              />
              <TextField
                label="Notes (optional)"
                value={notes}
                onChange={setNotes}
                placeholder="Any special requirements or instructions"
                testID="booking-notes"
              />
            </View>
          </ScrollView>

          <View style={styles.sheetFooter}>
            <Pressable
              onPress={dismiss}
              style={styles.cancelSheetBtn}
            >
              <Text style={styles.cancelSheetBtnText}>Cancel</Text>
            </Pressable>
            <Pressable
              onPress={handleConfirm}
              disabled={submitting}
              style={[styles.confirmSheetBtn, submitting && styles.btnDisabled]}
              testID="confirm-booking"
            >
              {submitting ? (
                <LoadingSpinner size="sm" />
              ) : (
                <>
                  <Ionicons name="checkmark-circle" size={18} color="#FFFFFF" />
                  <Text style={styles.confirmSheetBtnText}>Confirm Booking</Text>
                </>
              )}
            </Pressable>
          </View>
        </Animated.View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

export function MarketplaceScreen() {
  const toast = useToast();
  const toastRef = useRef(toast);
  toastRef.current = toast;

  const [tab, setTab] = useState<MarketplaceTab>("browse");
  const [services, setServices] = useState<MktService[]>([]);
  const [requests, setRequests] = useState<SvcReq[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const [selectedService, setSelectedService] = useState<MktService | null>(null);

  const loadServices = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    try {
      const result = await fetchServices({
        category: category || undefined,
        search: search || undefined,
        size: 50,
      });
      setServices(result.items);
    } catch {
      toastRef.current.error("Could not load services. Pull down to retry.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [category, search]); // toast via ref

  const loadRequests = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    try {
      const result = await fetchServiceRequests({ size: 50 });
      setRequests(result.items);
    } catch {
      toastRef.current.error("Could not load your requests. Pull down to retry.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []); // toast via ref

  useEffect(() => {
    if (tab === "browse") void loadServices();
    else if (tab === "requests") void loadRequests();
    else setIsLoading(false);
  }, [tab, loadServices, loadRequests]);

  const onRefresh = useCallback(() => {
    setIsRefreshing(true);
    if (tab === "browse") void loadServices(true);
    else void loadRequests(true);
  }, [tab, loadServices, loadRequests]);

  const handleCancelRequest = useCallback((req: SvcReq) => {
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
              toastRef.current.error("Could not cancel request. Please try again.");
            }
          },
        },
      ],
    );
  }, [loadRequests]); // toast via ref

  return (
    <Screen>
      <Header title="Health Services" />

      {/* Tab Bar */}
      <View style={styles.tabBar}>
        {TABS.map((t) => (
          <Pressable
            key={t.id}
            onPress={() => setTab(t.id)}
            style={({ pressed }) => [
              styles.tabPill,
              tab === t.id && styles.tabPillActive,
              pressed && { opacity: 0.8 },
            ]}
            testID={`marketplace-tab-${t.id}`}
          >
            <Ionicons
              name={t.icon}
              size={15}
              color={tab === t.id ? APP_GREEN : APP_TEXT_2}
            />
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
            <RefreshControl
              refreshing={isRefreshing}
              onRefresh={onRefresh}
              tintColor={APP_GREEN}
              colors={[APP_GREEN]}
            />
          ) : undefined
        }
      >
        {/* BROWSE TAB */}
        {tab === "browse" ? (
          <>
            {/* Search */}
            <View style={styles.searchBar}>
              <Ionicons name="search-outline" size={18} color={APP_TEXT_3} style={styles.searchIcon} />
              <TextField
                value={search}
                onChange={setSearch}
                placeholder="Search services..."
                testID="marketplace-search"
              />
            </View>

            {/* Category chips */}
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              contentContainerStyle={styles.categoryRow}
            >
              {CATEGORIES.map((cat) => (
                <Pressable
                  key={cat.value}
                  onPress={() => setCategory(cat.value)}
                  style={[
                    styles.categoryChip,
                    category === cat.value && styles.categoryChipActive,
                  ]}
                  testID={`category-${cat.value || "all"}`}
                >
                  <Ionicons
                    name={cat.icon as never}
                    size={14}
                    color={category === cat.value ? APP_GREEN : APP_TEXT_2}
                  />
                  <Text
                    style={[
                      styles.categoryChipText,
                      category === cat.value && styles.categoryChipTextActive,
                    ]}
                  >
                    {cat.label}
                  </Text>
                </Pressable>
              ))}
            </ScrollView>

            {/* Service list */}
            {isLoading ? (
              <SkeletonServiceList />
            ) : services.length === 0 ? (
              <View style={styles.emptyBox}>
                <View style={styles.emptyIconWrap}>
                  <Ionicons name="storefront-outline" size={32} color={APP_TEXT_3} />
                </View>
                <Text style={styles.emptyTitle}>No services found</Text>
                <Text style={styles.emptyMessage}>Try adjusting your search or category</Text>
              </View>
            ) : (
              services.map((svc) => (
                <Pressable
                  key={svc.id}
                  testID={`service-${svc.id}`}
                  style={({ pressed }) => [styles.serviceCard, pressed && { opacity: 0.95 }]}
                  onPress={() => svc.available && setSelectedService(svc)}
                >
                  <View style={styles.serviceTop}>
                    <View style={styles.serviceInfo}>
                      <Text style={styles.serviceName}>{svc.name}</Text>
                      <View style={styles.serviceBadgeRow}>
                        <View style={styles.categoryBadge}>
                          <Text style={styles.categoryBadgeText}>{svc.category}</Text>
                        </View>
                        {svc.price !== undefined ? (
                          <View style={styles.priceBadge}>
                            <Text style={styles.priceBadgeText}>
                              {`${svc.currency ?? ""} ${svc.price}`}
                            </Text>
                          </View>
                        ) : null}
                      </View>
                    </View>
                    {svc.available ? (
                      <Pressable
                        style={({ pressed }) => [styles.bookBtn, pressed && { opacity: 0.85 }]}
                        onPress={() => setSelectedService(svc)}
                        testID={`book-${svc.id}`}
                        hitSlop={4}
                      >
                        <Text style={styles.bookBtnText}>Book</Text>
                      </Pressable>
                    ) : (
                      <Badge variant="outline">Unavailable</Badge>
                    )}
                  </View>
                  <Text style={styles.serviceDesc} numberOfLines={2}>
                    {svc.description}
                  </Text>
                  <View style={styles.serviceMeta}>
                    <Ionicons name="business-outline" size={12} color={APP_TEXT_3} />
                    <Text style={styles.facilityText} numberOfLines={1}>{svc.facilityName}</Text>
                    {svc.rating !== undefined ? (
                      <>
                        <Ionicons name="star" size={12} color="#D97706" />
                        <Text style={styles.ratingVal}>{svc.rating.toFixed(1)}</Text>
                      </>
                    ) : null}
                  </View>
                </Pressable>
              ))
            )}
          </>
        ) : null}

        {/* REQUESTS TAB */}
        {tab === "requests" ? (
          <>
            {isLoading ? (
              <LoadingSpinner size="md" />
            ) : requests.length === 0 ? (
              <View style={styles.emptyBox}>
                <View style={styles.emptyIconWrap}>
                  <Ionicons name="receipt-outline" size={32} color={APP_TEXT_3} />
                </View>
                <Text style={styles.emptyTitle}>No requests yet</Text>
                <Text style={styles.emptyMessage}>
                  Browse services and make your first booking
                </Text>
                <Pressable
                  onPress={() => setTab("browse")}
                  style={styles.emptyAction}
                >
                  <Text style={styles.emptyActionText}>Browse Services</Text>
                </Pressable>
              </View>
            ) : (
              requests.map((req) => (
                <View key={req.id} style={styles.requestCard} testID={`request-${req.id}`}>
                  <View style={styles.requestTop}>
                    <View style={styles.requestInfo}>
                      <Text style={styles.requestName}>{req.serviceName}</Text>
                      <View style={styles.requestMeta}>
                        <Ionicons name="business-outline" size={12} color={APP_TEXT_3} />
                        <Text style={styles.requestFacility}>{req.facilityName}</Text>
                      </View>
                      <View style={styles.requestMeta}>
                        <Ionicons name="calendar-outline" size={12} color={APP_TEXT_3} />
                        <Text style={styles.requestDate}>
                          {new Date(req.requestedAt).toLocaleDateString()}
                        </Text>
                      </View>
                      {req.scheduledAt ? (
                        <View style={styles.requestMeta}>
                          <Ionicons name="time-outline" size={12} color={APP_GREEN} />
                          <Text style={styles.scheduledText}>
                            {new Date(req.scheduledAt).toLocaleString()}
                          </Text>
                        </View>
                      ) : null}
                      {req.trackingNumber ? (
                        <View style={[styles.requestMeta, styles.trackingRow]}>
                          <Ionicons name="barcode-outline" size={12} color={APP_TEXT_2} />
                          <Text style={styles.trackingText}>{req.trackingNumber}</Text>
                        </View>
                      ) : null}
                    </View>
                    <View style={styles.requestActions}>
                      <Badge variant={STATUS_VARIANT[req.status] ?? "outline"}>
                        {req.status}
                      </Badge>
                      {(req.status === "PENDING" || req.status === "CONFIRMED") ? (
                        <Pressable
                          style={({ pressed }) => [
                            styles.cancelRequestBtn,
                            pressed && { opacity: 0.75 },
                          ]}
                          onPress={() => handleCancelRequest(req)}
                          testID={`cancel-request-${req.id}`}
                        >
                          <Ionicons name="close-circle-outline" size={14} color={APP_ERROR} />
                          <Text style={styles.cancelRequestText}>Cancel</Text>
                        </Pressable>
                      ) : null}
                    </View>
                  </View>
                </View>
              ))
            )}
          </>
        ) : null}

        {tab === "cart" ? <CartScreen /> : null}
      </ScrollView>

      {/* Booking Bottom Sheet */}
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
    backgroundColor: "#F3F4F6",
  },
  tabPillActive: { backgroundColor: APP_GREEN_LIGHT },
  tabPillText: { fontSize: 13, fontWeight: "500", color: APP_TEXT_2 },
  tabPillTextActive: { color: APP_GREEN, fontWeight: "700" },

  scroll: { flex: 1, backgroundColor: APP_BG },
  container: { padding: 16, gap: 12, paddingBottom: 40 },

  searchBar: { position: "relative" },
  searchIcon: { position: "absolute", left: 14, top: 13, zIndex: 1 },

  categoryRow: { gap: 8, paddingBottom: 4, paddingTop: 2 },
  categoryChip: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: "#F3F4F6",
  },
  categoryChipActive: { backgroundColor: APP_GREEN_XLIGHT, borderWidth: 1, borderColor: APP_GREEN_LIGHT },
  categoryChipText: { fontSize: 13, fontWeight: "500", color: APP_TEXT_2 },
  categoryChipTextActive: { color: APP_GREEN, fontWeight: "600" },

  emptyBox: { alignItems: "center", paddingVertical: 56, gap: 10 },
  emptyIconWrap: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: "#F3F4F6",
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 4,
  },
  emptyTitle: { fontSize: 16, fontWeight: "700", color: APP_TEXT },
  emptyMessage: { fontSize: 14, color: APP_TEXT_3, textAlign: "center", paddingHorizontal: 24 },
  emptyAction: {
    marginTop: 4,
    paddingHorizontal: 20,
    paddingVertical: 10,
    backgroundColor: APP_GREEN,
    borderRadius: 12,
  },
  emptyActionText: { color: "#FFFFFF", fontWeight: "700", fontSize: 14 },

  serviceCard: {
    backgroundColor: APP_SURFACE,
    borderRadius: 16,
    padding: 16,
    gap: 10,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
  },
  serviceTop: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
    gap: 10,
  },
  serviceInfo: { flex: 1, gap: 6 },
  serviceName: { fontSize: 15, fontWeight: "700", color: APP_TEXT },
  serviceBadgeRow: { flexDirection: "row", gap: 6, flexWrap: "wrap" },
  categoryBadge: {
    backgroundColor: "#EFF6FF",
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 10,
  },
  categoryBadgeText: { fontSize: 11, fontWeight: "600", color: "#1E40AF" },
  priceBadge: {
    backgroundColor: APP_GREEN_LIGHT,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 10,
  },
  priceBadgeText: { fontSize: 11, fontWeight: "600", color: APP_GREEN },
  bookBtn: {
    backgroundColor: APP_GREEN,
    paddingHorizontal: 18,
    paddingVertical: 8,
    borderRadius: 14,
  },
  bookBtnText: { fontSize: 13, fontWeight: "700", color: "#FFFFFF" },
  serviceDesc: { fontSize: 13, color: APP_TEXT_2, lineHeight: 19 },
  serviceMeta: { flexDirection: "row", alignItems: "center", gap: 5 },
  facilityText: { fontSize: 12, color: APP_TEXT_3, flex: 1 },
  ratingVal: { fontSize: 12, color: "#D97706", fontWeight: "600" },

  requestCard: {
    backgroundColor: APP_SURFACE,
    borderRadius: 16,
    padding: 16,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  requestTop: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
    gap: 12,
  },
  requestInfo: { flex: 1, gap: 5 },
  requestName: { fontSize: 15, fontWeight: "700", color: APP_TEXT },
  requestMeta: { flexDirection: "row", alignItems: "center", gap: 5 },
  requestFacility: { fontSize: 13, color: APP_TEXT_2 },
  requestDate: { fontSize: 12, color: APP_TEXT_3 },
  scheduledText: { fontSize: 12, color: APP_GREEN, fontWeight: "600" },
  trackingRow: {
    backgroundColor: "#F9FAFB",
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 8,
    alignSelf: "flex-start",
  },
  trackingText: { fontSize: 12, color: APP_TEXT_2, fontFamily: "monospace" },
  requestActions: { alignItems: "flex-end", gap: 10 },
  cancelRequestBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: "#FECACA",
    backgroundColor: "#FEF2F2",
  },
  cancelRequestText: { fontSize: 12, fontWeight: "600", color: APP_ERROR },

  /* Booking Sheet */
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "rgba(0,0,0,0.45)",
  },
  sheetWrapper: {
    position: "absolute",
    bottom: 0,
    left: 0,
    right: 0,
    justifyContent: "flex-end",
  },
  sheet: {
    backgroundColor: APP_SURFACE,
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    maxHeight: SCREEN_HEIGHT * 0.85,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.15,
    shadowRadius: 20,
    elevation: 24,
  },
  sheetHandle: {
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: APP_BORDER,
    alignSelf: "center",
    marginTop: 12,
    marginBottom: 6,
  },
  sheetHeader: {
    flexDirection: "row",
    alignItems: "flex-start",
    paddingHorizontal: 20,
    paddingVertical: 14,
    gap: 12,
  },
  sheetTitleBlock: { flex: 1 },
  sheetTitle: { fontSize: 18, fontWeight: "800", color: APP_TEXT },
  sheetSubtitle: { fontSize: 13, color: APP_TEXT_2, marginTop: 2 },
  sheetClose: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: "#F3F4F6",
    alignItems: "center",
    justifyContent: "center",
  },
  sheetDivider: { height: StyleSheet.hairlineWidth, backgroundColor: APP_BORDER },
  sheetBody: { padding: 20, gap: 16, paddingBottom: 8 },
  sheetServiceDesc: { fontSize: 14, color: APP_TEXT_2, lineHeight: 20 },
  pricePill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    alignSelf: "flex-start",
    backgroundColor: APP_GREEN_XLIGHT,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: APP_GREEN_LIGHT,
  },
  priceText: { fontSize: 15, fontWeight: "700", color: APP_GREEN },
  ratingRow: { flexDirection: "row", alignItems: "center", gap: 3 },
  ratingLabel: { fontSize: 13, color: APP_TEXT_2, marginLeft: 4 },
  formSection: { gap: 14 },
  formSectionLabel: {
    fontSize: 13,
    fontWeight: "700",
    color: APP_TEXT_2,
    textTransform: "uppercase",
    letterSpacing: 0.8,
  },
  sheetFooter: {
    flexDirection: "row",
    gap: 10,
    padding: 20,
    paddingBottom: 32,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: APP_BORDER,
  },
  cancelSheetBtn: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    paddingVertical: 14,
    borderRadius: 14,
    backgroundColor: "#F3F4F6",
  },
  cancelSheetBtnText: { fontSize: 15, fontWeight: "600", color: APP_TEXT_2 },
  confirmSheetBtn: {
    flex: 2,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    paddingVertical: 14,
    borderRadius: 14,
    backgroundColor: APP_GREEN,
    shadowColor: APP_GREEN,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 10,
    elevation: 6,
  },
  confirmSheetBtnText: { fontSize: 15, fontWeight: "700", color: "#FFFFFF" },
  btnDisabled: { opacity: 0.5 },
});
