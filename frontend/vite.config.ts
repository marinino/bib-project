import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Der Dev-Server proxyt /api und /actuator auf das Spring-Backend. Damit laeuft alles
// unter derselben Origin - das Backend braucht keine CORS-Konfiguration, nur um dieses
// Demo-UI zu bedienen. Kein Code im Backend musste fuer das Frontend geaendert werden.
const BACKEND = "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": { target: BACKEND, changeOrigin: true },
      "/actuator": { target: BACKEND, changeOrigin: true },
    },
  },
});
