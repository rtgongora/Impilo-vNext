"use client";

/**
 * PaymentMethodPicker — reusable payment method selector for all pay points.
 * Includes Mushe Wallet, Cash, EcoCash, InnBucks, OneMoney, Bank, Cards, Insurance.
 */

const PAYMENT_METHODS = [
  { id: "MUSHE_WALLET", label: "Mushe Wallet", color: "bg-impilo-50 border-impilo-300 text-impilo-700" },
  { id: "CASH", label: "Cash", color: "bg-green-50 border-green-300 text-green-700" },
  { id: "ECOCASH", label: "EcoCash", color: "bg-red-50 border-red-300 text-red-700" },
  { id: "INNBUCKS", label: "InnBucks", color: "bg-blue-50 border-blue-300 text-blue-700" },
  { id: "ONE_MONEY", label: "OneMoney", color: "bg-yellow-50 border-yellow-300 text-yellow-700" },
  { id: "BANK_TRANSFER", label: "Bank / ZIPIT", color: "bg-slate-50 border-slate-300 text-slate-700" },
  { id: "VISA", label: "Visa", color: "bg-indigo-50 border-indigo-300 text-indigo-700" },
  { id: "MASTERCARD", label: "Mastercard", color: "bg-orange-50 border-orange-300 text-orange-700" },
  { id: "INSURANCE", label: "Medical Aid", color: "bg-purple-50 border-purple-300 text-purple-700" },
  { id: "GOVERNMENT_SUBSIDY", label: "Govt Subsidy", color: "bg-emerald-50 border-emerald-300 text-emerald-700" },
] as const;

export type PaymentMethodId = (typeof PAYMENT_METHODS)[number]["id"];

interface PaymentMethodPickerProps {
  value: string;
  onChange: (method: string) => void;
  /** Show only specific methods */
  allowedMethods?: string[];
  /** Compact single-row layout */
  compact?: boolean;
}

export function PaymentMethodPicker({ value, onChange, allowedMethods, compact }: PaymentMethodPickerProps) {
  const methods = allowedMethods
    ? PAYMENT_METHODS.filter((m) => allowedMethods.includes(m.id))
    : PAYMENT_METHODS;

  return (
    <div>
      <p className="text-[11px] font-medium text-gray-500 mb-1.5">Payment Method</p>
      <div className={compact
        ? "flex flex-wrap gap-1.5"
        : "grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-1.5"
      }>
        {methods.map((m) => (
          <button
            key={m.id}
            type="button"
            onClick={() => onChange(m.id)}
            className={`px-2.5 py-1.5 text-[11px] font-medium rounded-md border transition-all ${m.color} ${
              value === m.id
                ? "ring-2 ring-offset-1 ring-gray-400 shadow-sm"
                : "opacity-60 hover:opacity-100"
            }`}
          >
            {m.label}
          </button>
        ))}
      </div>
    </div>
  );
}

export { PAYMENT_METHODS };
