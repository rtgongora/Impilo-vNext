import type { Config } from "tailwindcss";

/**
 * Impilo vNext — shared Tailwind preset.
 * All workspaces should use: presets: [impiloPreset]
 * and import shared-ui/tokens.css in their global stylesheet.
 */
const impiloPreset: Partial<Config> = {
  theme: {
    extend: {
      colors: {
        impilo: {
          green: "var(--impilo-green)",
          greenDark: "var(--impilo-green-dark)",
          greenSoft: "var(--impilo-green-soft)",
          yellow: "var(--impilo-yellow)",
          yellowSoft: "var(--impilo-yellow-soft)",
          red: "var(--impilo-red)",
          redSoft: "var(--impilo-red-soft)",
          charcoal: "var(--impilo-charcoal)",
          surface: "var(--surface)",
          background: "var(--background)",
          border: "var(--border-soft)",
          nompilo: "var(--nompilo)",
          nompiloSoft: "var(--nompilo-soft)",
          50: "#E6F5EC",
          100: "#CFEEDD",
          200: "#B8E4CE",
          300: "#85D3AA",
          400: "#4CBC7D",
          500: "#009739",
          600: "#008A33",
          700: "#006B2D",
          800: "#005523",
          900: "#003D19",
        },
        brand: {
          primary: "var(--primary)",
          "accent-yellow": "var(--accent-yellow)",
          "accent-red": "var(--accent-red)",
          "accent-black": "var(--impilo-charcoal)",
        },
        background: "var(--background)",
        backgroundDeep: "var(--background-deep)",
        foreground: "var(--text-primary)",
        card: "var(--surface)",
        border: "var(--border-soft)",
        primary: {
          DEFAULT: "var(--primary)",
          hover: "var(--primary-hover)",
          soft: "var(--primary-soft)",
          muted: "var(--primary-muted)",
          foreground: "var(--on-green)",
        },
        success: {
          DEFAULT: "var(--success)",
          soft: "var(--success-soft)",
        },
        warning: {
          DEFAULT: "var(--warning)",
          soft: "var(--warning-soft)",
          foreground: "var(--on-yellow)",
        },
        danger: {
          DEFAULT: "var(--danger)",
          soft: "var(--danger-soft)",
          foreground: "var(--on-red)",
        },
        info: {
          DEFAULT: "var(--info)",
          soft: "var(--info-soft)",
        },
        muted: {
          DEFAULT: "var(--text-muted)",
          foreground: "var(--text-secondary)",
        },
        neutral: {
          0: "var(--impilo-white)",
          50: "var(--background)",
          100: "var(--surface-soft)",
          200: "var(--border-soft)",
          500: "var(--text-muted)",
          700: "var(--text-secondary)",
          900: "var(--impilo-charcoal)",
        },
        assistant: "var(--nompilo)",
      },
      fontFamily: {
        sans: ['"Inter"', "system-ui", "sans-serif"],
        serif: ['"Source Serif 4"', "Georgia", "serif"],
      },
      borderRadius: {
        DEFAULT: "var(--radius-default)",
        sm: "0.5rem",
        md: "0.875rem",
        lg: "1.25rem",
        xl: "1.5rem",
        "2xl": "1.75rem",
        "3xl": "2rem",
      },
      boxShadow: {
        subtle: "var(--shadow-subtle)",
        "impilo-soft": "var(--shadow-soft)",
        "impilo-card": "var(--shadow-card)",
        "impilo-floating": "var(--shadow-floating)",
        "impilo-nompilo": "var(--shadow-nompilo)",
      },
    },
  },
};

export default impiloPreset;
