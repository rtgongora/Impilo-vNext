"use client";

import React, { useCallback, useEffect, useState } from "react";
import { vitoApi, type SmartCard } from "@/lib/vitoApi";

type CardTab = "REQUESTED" | "PRINTED" | "ACTIVE";

/**
 * Card Management Interface — print, activate, inactivate.
 * Operators manage the SMART Card lifecycle from this dashboard.
 */
export default function CardsPage() {
  const [activeTab, setActiveTab] = useState<CardTab>("REQUESTED");
  const [cards, setCards] = useState<SmartCard[]>([]);
  const [loading, setLoading] = useState(true);

  const loadCards = useCallback(async () => {
    setLoading(true);
    try {
      const data = await vitoApi.listCardsByStatus(activeTab, 0, 50);
      setCards(data.items ?? []);
    } catch {
      setCards([]);
    } finally {
      setLoading(false);
    }
  }, [activeTab]);

  useEffect(() => {
    loadCards();
  }, [loadCards]);

  const performAction = async (cardId: number, action: "print" | "activate" | "inactivate") => {
    try {
      if (action === "print") await vitoApi.printCard(cardId);
      else if (action === "activate") await vitoApi.activateCard(cardId);
      else await vitoApi.inactivateCard(cardId);
      await loadCards();
    } catch {
      // Error handling via toast in production
    }
  };

  const tabs: { key: CardTab; label: string }[] = [
    { key: "REQUESTED", label: "Requested" },
    { key: "PRINTED", label: "Printed" },
    { key: "ACTIVE", label: "Active" },
  ];

  return (
    <div className="max-w-7xl mx-auto px-6 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">
          SMART Card Management
        </h1>
        <p className="text-sm text-neutral-500 mt-1">
          Manage card lifecycle: print, activate, inactivate, revoke
        </p>
      </div>

      {/* Tab bar */}
      <div className="flex gap-1 mb-4 bg-neutral-100 p-1 rounded-[12px] w-fit">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`px-4 py-2 text-sm font-medium rounded-[8px] transition-colors ${
              activeTab === tab.key
                ? "bg-card text-neutral-900 shadow-subtle"
                : "text-neutral-500 hover:text-neutral-900"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-100 bg-neutral-50">
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Card Number</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Client</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">DID</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Requested</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Expires</th>
              <th className="px-4 py-3 text-right font-medium text-neutral-500">Action</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-neutral-500">Loading...</td>
              </tr>
            ) : cards.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-neutral-500">
                  No cards in {activeTab.toLowerCase()} state
                </td>
              </tr>
            ) : (
              cards.map((card) => (
                <tr key={card.id} className="border-b border-neutral-50 hover:bg-neutral-50">
                  <td className="px-4 py-3 font-mono text-xs">{card.cardNumber}</td>
                  <td className="px-4 py-3 font-mono text-xs">{card.healthId.substring(0, 8)}...</td>
                  <td className="px-4 py-3 font-mono text-xs">{card.didUri.substring(0, 20)}...</td>
                  <td className="px-4 py-3 text-neutral-500">
                    {new Date(card.requestedAt).toLocaleDateString()}
                  </td>
                  <td className="px-4 py-3 text-neutral-500">
                    {new Date(card.expiresAt).toLocaleDateString()}
                  </td>
                  <td className="px-4 py-3 text-right">
                    {activeTab === "REQUESTED" && (
                      <button
                        onClick={() => performAction(card.id, "print")}
                        className="px-3 py-1 text-xs font-medium text-white bg-brand-primary rounded-[8px] hover:bg-brand-primary/90"
                      >
                        Mark Printed
                      </button>
                    )}
                    {activeTab === "PRINTED" && (
                      <button
                        onClick={() => performAction(card.id, "activate")}
                        className="px-3 py-1 text-xs font-medium text-white bg-success rounded-[8px] hover:bg-success/90"
                      >
                        Activate
                      </button>
                    )}
                    {activeTab === "ACTIVE" && (
                      <button
                        onClick={() => performAction(card.id, "inactivate")}
                        className="px-3 py-1 text-xs font-medium text-danger bg-danger/10 rounded-[8px] hover:bg-danger/20"
                      >
                        Inactivate
                      </button>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
