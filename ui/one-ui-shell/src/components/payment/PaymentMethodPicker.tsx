"use client";

/**
 * PaymentMethodPicker — reusable payment method selector for all pay points.
 * Includes Mushe Wallet, Cash, EcoCash, InnBucks, OneMoney, Bank, Cards, Insurance.
 */

const PAYMENT_METHODS = [
  { id: "MUSHE_WALLET", label: "Mushe Wallet", color: "bg-[color:var(--primary-soft)] border-[color:var(--primary-muted)] text-[color:var(--primary-hover)]" },
  { id: "CASH", label: "Cash", color: "bg-[color:var(--success-soft)] border-[color:var(--success)]/30 text-[color:var(--success)]" },
  { id: "ECOCASH", label: "EcoCash", color: "bg-[color:var(--danger-soft)] border-[color:var(--danger)]/35 text-[color:var(--danger)]" },
  { id: "INNBUCKS", label: "InnBucks", color: "bg-[color:var(--info-soft)] border-[color:var(--info)]/30 text-[color:var(--info)]" },
  { id: "ONE_MONEY", label: "OneMoney", color: "bg-[color:var(--accent-yellow-soft)] border-[color:var(--accent-yellow)]/60 text-[#7f5600]" },
  { id: "BANK_TRANSFER", label: "Bank / ZIPIT", color: "bg-[color:var(--surface-soft)] border-[color:var(--border-strong)] text-[color:var(--text-secondary)]" },
  { id: "VISA", label: "Visa", color: "bg-[color:var(--info-soft)] border-[color:var(--info)]/30 text-[color:var(--info)]" },
  { id: "MASTERCARD", label: "Mastercard", color: "bg-[color:var(--warning-soft)] border-[color:var(--warning)]/30 text-[#7f5600]" },
  { id: "INSURANCE", label: "Medical Aid", color: "bg-[color:var(--nompilo-soft)] border-[color:var(--nompilo)]/30 text-[color:var(--nompilo)]" },
  { id: "GOVERNMENT_SUBSIDY", label: "Govt Subsidy", color: "bg-[color:var(--surface-soft)] border-[color:var(--primary-muted)] text-[color:var(--primary-hover)]" },
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
      <p className="text-[11px] font-medium text-[color:var(--text-muted)] mb-1.5">Payment Method</p>
      <div className={compact
        ? "flex flex-wrap gap-1.5"
        : "grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-1.5"
      }>
        {methods.map((m) => (
          <button
            key={m.id}
            type="button"
            onClick={() => onChange(m.id)}
            className={`px-2.5 py-1.5 text-[11px] font-medium rounded-full border transition-all ${m.color} ${
              value === m.id
                ? "ring-2 ring-offset-1 ring-[color:var(--primary)] shadow-sm"
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
