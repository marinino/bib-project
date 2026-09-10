import { createRoot } from "react-dom/client";
import { App } from "./App";
import "./styles.css";

// Bewusst ohne <StrictMode>: dessen doppelter Effekt-Durchlauf im Dev-Modus wuerde jeden
// Ladevorgang zweimal ins Request-Log schreiben - und genau dieses Log soll hier die
// tatsaechliche Aufruffolge gegen das Backend zeigen.
createRoot(document.getElementById("root")!).render(<App />);
