"use client";

import { useState } from "react";
import Link from "next/link";
import { msikaFlowApi } from "@/lib/msikaFlowApi";

export default function CartPage() {
  const [orderId, setOrderId] = useState("");
  const [orderIdInput, setOrderIdInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [orderStatus, setOrderStatus] = useState<string | null>(null);
  const [orderDetails, setOrderDetails] = useState<{
    orderId: string;
    status: string;
    amountTotal: string;
    currency: string;
    lines: Array<{
      lineId: string;
      msikaCoreCode: string;
      kind: string;
      qty: number;
      unitPrice: string;
      lineTotal: string;
      state: string;
    }>;
  } | null>(null);
  const [paymentResult, setPaymentResult] = useState<string | null>(null);

  const lookupOrder = async () => {
    if (!orderIdInput.trim()) return;
    setLoading(true);
    setError(null);
    setOrderDetails(null);
    setPaymentResult(null);
    try {
      const order = await msikaFlowApi.getOrder(orderIdInput.trim());
      setOrderDetails(order);
      setOrderId(order.orderId);
      setOrderStatus(order.status);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Order not found");
    } finally {
      setLoading(false);
    }
  };

  const handleValidate = async () => {
    if (!orderId) return;
    setLoading(true);
    setError(null);
    try {
      const updated = await msikaFlowApi.validateOrder(orderId);
      setOrderStatus(updated.status);
      setOrderDetails(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Validation failed");
    } finally {
      setLoading(false);
    }
  };

  const handlePrice = async () => {
    if (!orderId) return;
    setLoading(true);
    setError(null);
    try {
      const updated = await msikaFlowApi.priceOrder(orderId);
      setOrderStatus(updated.status);
      setOrderDetails(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Pricing failed");
    } finally {
      setLoading(false);
    }
  };

  const handlePay = async () => {
    if (!orderId) return;
    setLoading(true);
    setError(null);
    try {
      const result = await msikaFlowApi.payOrder(orderId);
      setPaymentResult(`Payment initiated: ${result.mushexPaymentIntentId}`);
      setOrderStatus(result.status);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Payment failed");
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = async () => {
    if (!orderId) return;
    setLoading(true);
    setError(null);
    try {
      const updated = await msikaFlowApi.cancelOrder(orderId);
      setOrderStatus(updated.status);
      setOrderDetails(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Cancel failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <h1 className="text-2xl font-semibold mb-1">Checkout</h1>
      <p className="text-sm text-neutral-500 mb-6">
        Review your order, validate items, confirm pricing, and proceed to payment.
      </p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-red-800">
          {error}
        </div>
      )}

      {paymentResult && (
        <div className="mb-4 p-3 rounded-lg bg-success-soft border border-success/25 text-sm text-primary-hover">
          {paymentResult}
        </div>
      )}

      {!orderDetails ? (
        <div className="card p-6">
          <h2 className="text-lg font-semibold mb-4">Look Up Order</h2>
          <p className="text-sm text-neutral-500 mb-4">
            Enter an order ID to review and complete checkout, or{" "}
            <Link href="/browse" className="text-brand-primary hover:underline">
              browse the catalog
            </Link>{" "}
            to create a new order.
          </p>
          <div className="flex gap-3">
            <input
              type="text"
              value={orderIdInput}
              onChange={(e) => setOrderIdInput(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && lookupOrder()}
              placeholder="Enter order ID..."
              className="input-field flex-1 max-w-md"
            />
            <button onClick={lookupOrder} disabled={loading} className="btn-primary">
              {loading ? "Loading..." : "Look Up"}
            </button>
          </div>
        </div>
      ) : (
        <>
          <div className="card p-6 mb-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold">Order Summary</h2>
              <span className={`inline-block px-3 py-1 rounded-full text-xs font-medium ${
                orderStatus === "COMPLETED" ? "bg-emerald-100 text-primary-hover" :
                orderStatus === "CANCELLED" ? "bg-red-100 text-danger" :
                "bg-blue-100 text-primary-hover"
              }`}>
                {orderStatus}
              </span>
            </div>
            <div className="grid grid-cols-2 gap-4 text-sm mb-4">
              <div>
                <span className="text-neutral-500 block text-xs">Order ID</span>
                <span className="font-mono text-xs">{orderDetails.orderId}</span>
              </div>
              <div>
                <span className="text-neutral-500 block text-xs">Total</span>
                <span className="text-lg font-semibold">
                  {orderDetails.currency} {orderDetails.amountTotal}
                </span>
              </div>
            </div>

            {orderDetails.lines.length > 0 && (
              <div className="border-t border-neutral-100 pt-4">
                <h3 className="text-sm font-medium text-neutral-600 mb-2">Line Items</h3>
                <div className="space-y-2">
                  {orderDetails.lines.map((line) => (
                    <div key={line.lineId} className="flex justify-between items-center py-2 border-b border-neutral-50">
                      <div>
                        <span className="text-sm font-medium font-mono">{line.msikaCoreCode}</span>
                        <span className="text-xs text-neutral-500 ml-2">x{line.qty}</span>
                        <span className="text-xs text-neutral-400 ml-2">({line.kind})</span>
                      </div>
                      <div className="text-right">
                        <span className="text-sm font-semibold">{line.lineTotal}</span>
                        <span className={`ml-2 text-xs ${line.state === "PRICED" ? "text-primary" : "text-neutral-400"}`}>
                          {line.state}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>

          <div className="card p-6">
            <h3 className="text-sm font-semibold mb-4">Checkout Actions</h3>
            <div className="flex flex-wrap gap-3">
              {orderStatus === "CREATED" && (
                <button onClick={handleValidate} disabled={loading} className="btn-secondary">
                  {loading ? "Validating..." : "Validate Order"}
                </button>
              )}
              {orderStatus === "VALIDATED" && (
                <button onClick={handlePrice} disabled={loading} className="btn-secondary">
                  {loading ? "Pricing..." : "Calculate Price"}
                </button>
              )}
              {orderStatus === "PRICED" && (
                <button onClick={handlePay} disabled={loading} className="btn-primary">
                  {loading ? "Processing..." : "Pay Now"}
                </button>
              )}
              {orderStatus !== "COMPLETED" && orderStatus !== "CANCELLED" && (
                <button onClick={handleCancel} disabled={loading} className="btn-danger">
                  Cancel Order
                </button>
              )}
              <button
                onClick={() => { setOrderDetails(null); setOrderIdInput(""); setPaymentResult(null); }}
                className="btn-secondary"
              >
                New Order
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
