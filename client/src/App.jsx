import { lazy, Suspense, useEffect } from "react";
import { Routes, Route, Navigate, useLocation } from "react-router-dom";
import PublicLayout from "./components/layout/PublicLayout.jsx";
import ProtectedRoute from "./components/auth/ProtectedRoute.jsx";
import AdminRoute from "./components/auth/AdminRoute.jsx";

import AppLayout from "./components/layout/AppLayout.jsx";

const Home = lazy(() => import("./pages/public/Home.jsx"));
const Features = lazy(() => import("./pages/public/Features.jsx"));
const Solutions = lazy(() => import("./pages/public/Solutions.jsx"));
const About = lazy(() => import("./pages/public/About.jsx"));
const Contact = lazy(() => import("./pages/public/Contact.jsx"));
const Privacy = lazy(() => import("./pages/public/Privacy.jsx"));
const Terms = lazy(() => import("./pages/public/Terms.jsx"));
const Login = lazy(() => import("./pages/auth/Login.jsx"));
const Register = lazy(() => import("./pages/auth/Register.jsx"));
const ForgotPassword = lazy(() => import("./pages/auth/ForgotPassword.jsx"));
const ResetPassword = lazy(() => import("./pages/auth/ResetPassword.jsx"));
const Dashboard = lazy(() => import("./pages/app/Dashboard.jsx"));
const Devices = lazy(() => import("./pages/app/Devices.jsx"));
const RegisterDevice = lazy(() => import("./pages/app/RegisterDevice.jsx"));
const DeviceDetails = lazy(() => import("./pages/app/DeviceDetails.jsx"));
const Alerts = lazy(() => import("./pages/app/Alerts.jsx"));
const AlertRules = lazy(() => import("./pages/app/AlertRules.jsx"));
const Reports = lazy(() => import("./pages/app/Reports.jsx"));
const Profile = lazy(() => import("./pages/app/Profile.jsx"));
const SettingsPage = lazy(() => import("./pages/app/Settings.jsx"));
const Tasks = lazy(() => import("./pages/app/Tasks.jsx"));
const ReportDetails = lazy(() => import("./pages/app/ReportDetails.jsx"));
const AdminConsole = lazy(() => import("./pages/app/AdminConsole.jsx"));
const FirmwareManagement = lazy(() => import("./pages/app/FirmwareManagement.jsx"));

export default function App() {
  const { pathname } = useLocation();
  useEffect(() => { window.scrollTo({ top: 0, left: 0, behavior: "instant" }); }, [pathname]);
  return (
    <Suspense fallback={<div className="min-h-screen bg-[var(--app-canvas)]" aria-busy="true" />}>
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
        <Route path="/forgot-password" element={<div className="auth-page-motion"><ForgotPassword /></div>} />
        <Route path="/reset-password" element={<div className="auth-page-motion"><ResetPassword /></div>} />
        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/devices" element={<Devices />} />
          <Route path="/devices/register" element={<RegisterDevice />} />
          <Route path="/devices/:id" element={<DeviceDetails />} />
          <Route path="/alerts" element={<Alerts />} />
          <Route path="/alerts/rules" element={<AlertRules />} />
          <Route path="/reports" element={<Reports />} />
          <Route path="/reports/:id" element={<ReportDetails />} />
          <Route path="/profile" element={<Profile />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/tasks" element={<Tasks />} />
          <Route element={<AdminRoute />}>
            <Route path="/admin" element={<AdminConsole />} />
            <Route path="/admin/firmware" element={<FirmwareManagement />} />
          </Route>
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}

