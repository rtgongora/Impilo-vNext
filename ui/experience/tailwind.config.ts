import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        /* Impilo brand palette — mirrors ui/shared-ui/tokens.css */
        impilo: {
          50: "#E8F5EC",
          100: "#C8E6CE",
          200: "#A0D4A8",
          300: "#6DBF7B",
          400: "#43A854",
          500: "#1F7A3A",   /* Brand Primary */
          600: "#1A6831",
          700: "#155628",
          800: "#10441F",
          900: "#0B3216",
        },
        "brand-yellow": "#F2C300",
        "brand-red": "#D62828",
        "brand-black": "#111111",
      },
      fontFamily: {
        sans: ['"Inter"', "system-ui", "sans-serif"],
        serif: ['"Source Serif 4"', "Georgia", "serif"],
      },
    },
  },
  plugins: [],
};

export default config;
