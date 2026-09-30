import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { ToastProvider } from "./context/ToastContext.jsx";
import { AuthProvider } from "./context/AuthContext.jsx";
import App from "./App.jsx";
import "./styles/styles.css";

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider><App /></AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  </StrictMode>
);
