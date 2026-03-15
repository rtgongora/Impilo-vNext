/**
 * MarketplaceScreen — Service discovery, booking, and order tracking.
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  Screen,
  Header,
  Card,
  CardHeader,
  CardBody,
  Button,
  Badge,
  TextField,
  Select,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import {
  fetchServices,
  fetchServiceRequests,
  requestService,
  cancelServiceRequest,
  type MarketplaceService,
  type ServiceRequest,
} from "../../services/marketplaceService";
// re-import the frontend types (not the service fn return types)
import type { MarketplaceService as MktService, ServiceRequest as SvcReq } from "../../types";

type MarketplaceTab = "browse" | "requests";

const CATEGORIES = [
  { label: "All", value: "" },
  { label: "Consultation", value: "CONSULTATION" },
  { label: "Lab Tests", value: "LAB_TESTS" },
  { label: "Pharmacy", value: "PHARMACY" },
  { label: "Imaging", value: "IMAGING" },
  { label: "Wellness", value: "WELLNESS" },
  { label: "Home Care", value: "HOME_CARE" },
];

const REQUEST_STATUS_COLORS: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  PENDING: "outline",
  CONFIRMED: "default",
  IN_PROGRESS: "secondary",
  COMPLETED: "secondary",
  CANCELLED: "destructive",
};

export function MarketplaceScreen() {
  const [tab, setTab] = useState<MarketplaceTab>("browse");
  const [services, setServices] = useState<MktService[]>([]);
  const [requests, setRequests] = useState<SvcReq[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const [selectedService, setSelectedService] = useState<MktService | null>(null);
  const [preferredDate, setPreferredDate] = useState("");
  const [notes, setNotes] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const loadServices = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchServices({
        category: category || undefined,
        search: search || undefined,
        size: 50,
      });
      setServices(result.items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, [category, search]);

  const loadRequests = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchServiceRequests({ size: 50 });
      setRequests(result.items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (tab === "browse") loadServices();
    else loadRequests();
  }, [tab, loadServices, loadRequests]);

  const handleRequest = useCallback(async () => {
    if (!selectedService) return;
    setSubmitting(true);
    try {
      await requestService({
        serviceId: selectedService.id,
        preferredDate: preferredDate || undefined,
        notes: notes || undefined,
      });
      setSelectedService(null);
      setPreferredDate("");
      setNotes("");
      setTab("requests");
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setSubmitting(false);
    }
  }, [selectedService, preferredDate, notes]);

  const handleCancelRequest = useCallback(async (id: string) => {
    try {
      await cancelServiceRequest(id);
      loadRequests();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    }
  }, [loadRequests]);

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, { title: "Health Services" }),
    React.createElement(
      "div",
      { "data-testid": "marketplace-screen", style: { padding: "16px", display: "flex", flexDirection: "column", gap: "16px" } },

      // Tab toggle
      React.createElement(
        "div",
        { style: { display: "flex", gap: "8px" } },
        React.createElement(Button, {
          title: "Browse Services",
          variant: tab === "browse" ? "primary" : "ghost",
          onPress: () => setTab("browse"),
          testID: "marketplace-tab-browse",
        }),
        React.createElement(Button, {
          title: "My Requests",
          variant: tab === "requests" ? "primary" : "ghost",
          onPress: () => setTab("requests"),
          testID: "marketplace-tab-requests",
        })
      ),

      error
        ? React.createElement(ErrorState, { title: "Error", message: error.message, onRetry: () => tab === "browse" ? loadServices() : loadRequests() })
        : null,

      // Service booking modal
      selectedService
        ? React.createElement(
            Card,
            null,
            React.createElement(CardHeader, { title: `Book: ${selectedService.name}` }),
            React.createElement(
              CardBody,
              null,
              React.createElement("p", { style: { fontSize: "14px", color: "#374151", marginBottom: "12px" } }, selectedService.description),
              selectedService.price !== undefined
                ? React.createElement("p", { style: { fontSize: "14px", fontWeight: "600", marginBottom: "12px" } },
                    `Price: ${selectedService.currency} ${selectedService.price}`
                  )
                : null,
              React.createElement(
                "div",
                { style: { display: "flex", flexDirection: "column", gap: "12px" } },
                React.createElement(TextField, {
                  label: "Preferred Date",
                  value: preferredDate,
                  onChange: setPreferredDate,
                  placeholder: "YYYY-MM-DD",
                  testID: "booking-preferred-date",
                }),
                React.createElement(TextField, {
                  label: "Notes (optional)",
                  value: notes,
                  onChange: setNotes,
                  placeholder: "Any special requirements",
                  testID: "booking-notes",
                }),
                React.createElement(
                  "div",
                  { style: { display: "flex", gap: "8px" } },
                  React.createElement(Button, {
                    title: submitting ? "Submitting..." : "Confirm Booking",
                    variant: "primary",
                    onPress: handleRequest,
                    disabled: submitting,
                    testID: "confirm-booking",
                  }),
                  React.createElement(Button, {
                    title: "Cancel",
                    variant: "ghost",
                    onPress: () => setSelectedService(null),
                  })
                )
              )
            )
          )
        : null,

      // Browse tab
      tab === "browse"
        ? React.createElement(
            React.Fragment,
            null,
            React.createElement(TextField, {
              value: search,
              onChange: setSearch,
              placeholder: "Search services...",
              testID: "marketplace-search",
            }),
            React.createElement(
              "div",
              { style: { display: "flex", gap: "8px", overflowX: "auto", paddingBottom: "4px" } },
              CATEGORIES.map((cat) =>
                React.createElement(Button, {
                  key: cat.value,
                  title: cat.label,
                  variant: category === cat.value ? "primary" : "ghost",
                  size: "sm",
                  onPress: () => setCategory(cat.value),
                  testID: `category-${cat.value || "all"}`,
                })
              )
            ),
            isLoading
              ? React.createElement(LoadingSpinner, { size: "md" })
              : services.length === 0
                ? React.createElement(EmptyState, {
                    title: "No services found",
                    message: "Try adjusting your search or category filter",
                  })
                : services.map((svc) =>
                    React.createElement(
                      Card,
                      { key: svc.id },
                      React.createElement(
                        CardBody,
                        null,
                        React.createElement(
                          "div",
                          {
                            "data-testid": `service-${svc.id}`,
                            style: { display: "flex", justifyContent: "space-between", alignItems: "flex-start" },
                          },
                          React.createElement(
                            "div",
                            { style: { flex: 1 } },
                            React.createElement("strong", { style: { fontSize: "15px" } }, svc.name),
                            React.createElement("p", { style: { fontSize: "14px", color: "#374151", margin: "4px 0" } }, svc.description),
                            React.createElement("p", { style: { fontSize: "13px", color: "#6B7280", margin: "2px 0" } },
                              `${svc.facilityName} \u2022 ${svc.category}`
                            ),
                            svc.price !== undefined
                              ? React.createElement("p", { style: { fontSize: "14px", fontWeight: "600", margin: "4px 0" } },
                                  `${svc.currency} ${svc.price}`
                                )
                              : null,
                            svc.rating !== undefined
                              ? React.createElement("span", { style: { fontSize: "13px", color: "#D97706" } },
                                  `\u2605 ${svc.rating.toFixed(1)}`
                                )
                              : null
                          ),
                          svc.available
                            ? React.createElement(Button, {
                                title: "Book",
                                variant: "primary",
                                size: "sm",
                                onPress: () => setSelectedService(svc),
                                testID: `book-${svc.id}`,
                              })
                            : React.createElement(Badge, { variant: "outline", children: "Unavailable" })
                        )
                      )
                    )
                  )
          )
        : null,

      // Requests tab
      tab === "requests"
        ? React.createElement(
            React.Fragment,
            null,
            isLoading
              ? React.createElement(LoadingSpinner, { size: "md" })
              : requests.length === 0
                ? React.createElement(EmptyState, {
                    title: "No requests yet",
                    message: "Browse services and make your first booking",
                  })
                : requests.map((req) =>
                    React.createElement(
                      Card,
                      { key: req.id },
                      React.createElement(
                        CardBody,
                        null,
                        React.createElement(
                          "div",
                          {
                            "data-testid": `request-${req.id}`,
                            style: { display: "flex", justifyContent: "space-between", alignItems: "flex-start" },
                          },
                          React.createElement(
                            "div",
                            null,
                            React.createElement(
                              "div",
                              { style: { display: "flex", gap: "8px", alignItems: "center", marginBottom: "4px" } },
                              React.createElement("strong", null, req.serviceName),
                              React.createElement(Badge, {
                                variant: REQUEST_STATUS_COLORS[req.status] ?? "outline",
                                children: req.status,
                              })
                            ),
                            React.createElement("p", { style: { fontSize: "13px", color: "#6B7280", margin: "2px 0" } }, req.facilityName),
                            React.createElement("p", { style: { fontSize: "13px", color: "#9CA3AF", margin: "2px 0" } },
                              `Requested: ${new Date(req.requestedAt).toLocaleDateString()}`
                            ),
                            req.scheduledAt
                              ? React.createElement("p", { style: { fontSize: "13px", color: "#374151", margin: "2px 0" } },
                                  `Scheduled: ${new Date(req.scheduledAt).toLocaleString()}`
                                )
                              : null,
                            req.trackingNumber
                              ? React.createElement("p", { style: { fontSize: "12px", color: "#6B7280", margin: "2px 0" } },
                                  `Tracking: ${req.trackingNumber}`
                                )
                              : null
                          ),
                          req.status === "PENDING" || req.status === "CONFIRMED"
                            ? React.createElement(Button, {
                                title: "Cancel",
                                variant: "ghost",
                                size: "sm",
                                onPress: () => handleCancelRequest(req.id),
                                testID: `cancel-request-${req.id}`,
                              })
                            : null
                        )
                      )
                    )
                  )
          )
        : null
    )
  );
}
