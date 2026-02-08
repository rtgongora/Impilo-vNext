import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/**/*.{js,ts,jsx,tsx}",
    "../shared-ui/**/*.{ts,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          primary: "#1F7A3A",
          "accent-yellow": "#F2C300",
          "accent-red": "#D62828",
          "accent-black": "#111111",
        },
        emerald: {
          50: "#ECFDF5",
          100: "#D1FAE5",
          200: "#A7F3D0",
          300: "#6EE7B7",
          400: "#34D399",
          500: "#10B981",
          600: "#059669",
          700: "#047857",
          800: "#065F46",
          900: "#064E3B",
        },
        amber: {
          50: "#FFFBEB",
          100: "#FEF3C7",
          200: "#FDE68A",
          300: "#FCD34D",
          400: "#FBBF24",
          500: "#F59E0B",
          600: "#D97706",
          700: "#B45309",
          800: "#92400E",
          900: "#78350F",
        },
        success: "#16A34A",
        warning: "#F59E0B",
        danger: "#DC2626",
        info: "#2563EB",
        neutral: {
          0: "#FFFFFF",
          50: "#F8FAFC",
          100: "#F1F5F9",
          200: "#E2E8F0",
          300: "#CBD5E1",
          400: "#94A3B8",
          500: "#64748B",
          600: "#475569",
          700: "#334155",
          800: "#1E293B",
          900: "#0F172A",
        },
      },
      fontFamily: {
        sans: ["Inter", "system-ui", "sans-serif"],
        serif: ["Source Serif 4", "Georgia", "serif"],
      },
      borderRadius: {
        DEFAULT: "12px",
        lg: "16px",
      },
      boxShadow: {
        subtle: "0 1px 2px rgba(0,0,0,0.06)",
      },
    },
  },
  plugins: [],
};

export default config;
