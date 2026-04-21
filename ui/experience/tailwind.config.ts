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
          50: "#E6F5EC",
          100: "#CDEDD9",
          200: "#9DDBB6",
          300: "#6CC992",
          400: "#33B56A",
          500: "#009739",   /* Brand Primary */
          600: "#008A34",
          700: "#006F2A",
          800: "#005420",
          900: "#003A16",
        },
        "brand-yellow": "#FCE300",
        "brand-red": "#EF3340",
        "brand-black": "#231F20",
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
