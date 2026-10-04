import { useEffect } from "react";
import { Routes, Route, Navigate, useLocation } from "react-router-dom";
import PublicLayout from "./components/layout/PublicLayout.jsx";
import ProtectedRoute from "./components/auth/ProtectedRoute.jsx";

import Home from "./pages/public/Home.jsx";
import Features from "./pages/public/Features.jsx";
import Solutions from "./pages/public/Solutions.jsx";
import About from "./pages/public/About.jsx";
import Contact from "./pages/public/Contact.jsx";
import Privacy from "./pages/public/Privacy.jsx";
import Terms from "./pages/public/Terms.jsx";
import Login from "./pages/auth/Login.jsx";
import Register from "./pages/auth/Register.jsx";

import Dashboard from "./pages/app/Dashboard.jsx";
import Devices from "./pages/app/Devices.jsx";
import RegisterDevice from "./pages/app/RegisterDevice.jsx";
import AppLayout from "./components/layout/AppLayout.jsx";
import DeviceDetails from "./pages/app/DeviceDetails.jsx";

export default function App() {
  const { pathname } = useLocation();
  useEffect(() => { window.scrollTo({ top: 0, left: 0, behavior: "instant" }); }, [pathname]);
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route path="/" element={<Home />} />
        <Route path="/features" element={<Features />} />
        <Route path="/solutions" element={<Solutions />} />
        <Route path="/about" element={<About />} />
        <Route path="/contact" element={<Contact />} />
        <Route path="/privacy" element={<Privacy />} />
        <Route path="/terms" element={<Terms />} />
      </Route>
      <Route path="/login" element={<div className="auth-page-motion"><Login /></div>} />
      <Route path="/register" element={<div className="auth-page-motion"><Register /></div>} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/devices" element={<Devices />} />
          <Route path="/devices/register" element={<RegisterDevice />} />
          <Route path="/devices/:id" element={<DeviceDetails />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

