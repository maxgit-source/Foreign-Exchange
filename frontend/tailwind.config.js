/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        background: "#09090b", // Zinc 950
        surface: "#18181b",    // Zinc 900
        border: "#27272a",     // Zinc 800
        primary: "#3b82f6",    // Blue 500
        accent: "#10b981",     // Emerald 500 (Bullish)
        danger: "#ef4444",     // Red 500 (Bearish)
        text: {
          DEFAULT: "#e4e4e7",  // Zinc 200
          muted: "#a1a1aa",    // Zinc 400
        }
      },
      fontFamily: {
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'Monaco', 'Consolas', "monospace"],
        sans: ['"Inter"', 'ui-sans-serif', 'system-ui', 'sans-serif'],
      }
    },
  },
  plugins: [],
}
