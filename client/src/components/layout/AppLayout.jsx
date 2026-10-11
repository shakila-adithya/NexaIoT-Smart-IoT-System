import { useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import {
  Bell,
  ChartNoAxesCombined,
  ChevronDown,
  Cpu,
  LayoutDashboard,
  LogOut,
  Menu,
  Satellite,
  Search,
  Settings,
  TriangleAlert,
  X,
} from "lucide-react";
import { useAuth } from "../../hooks/useAuth.js";
import { getAlerts } from "../../api/alertsApi.js";

const navigation = [
  { name: "Dashboard", path: "/dashboard", icon: LayoutDashboard },
  { name: "Devices", path: "/devices", icon: Cpu },
  { name: "Alerts", path: "/alerts", icon: TriangleAlert },
  { name: "Reports", path: "/reports", icon: ChartNoAxesCombined },
  { name: "Settings", path: "/settings", icon: Settings },
];

function SidebarContent({ onNavigate, unresolvedAlertCount }) {
  return (
    <>
      <div className="px-[18px] pt-7">
        <Link
        to="/dashboard"
        onClick={onNavigate}
        className="flex items-center gap-2 px-2.5 font-bold text-lg text-white"
        >
        <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-white">
            <Satellite size={18} />
        </span>

        NexaIoT
        </Link>

        <nav className="mt-6">
          <div className="px-3 pb-1.5 text-[10px] font-bold uppercase tracking-[0.08em] text-[#668b9e]">
            Workspace
          </div>

          <div className="space-y-1.5">
            {navigation.map((item) => {
              const Icon = item.icon;

              return (
                <NavLink
                  key={item.path}
                  to={item.path}
                  onClick={onNavigate}
                  className={({ isActive }) =>
                    `flex h-11 items-center gap-3 rounded-[10px] px-3 text-[13px] transition ${
                      isActive
                        ? "bg-[#123f52] text-white"
                        : "text-[#8fb0c1] hover:bg-[#103447] hover:text-white"
                    }`
                  }
                >
                  <Icon size={18} strokeWidth={1.9} />

                  <span>{item.name}</span>

                  {item.path === "/alerts" && unresolvedAlertCount > 0 ? (
                    <span className="ml-auto flex h-5 min-w-[22px] items-center justify-center rounded-full bg-[#e24e5a] px-1.5 text-[10px] font-bold text-white">
                      {unresolvedAlertCount}
                    </span>
                  ) : null}
                </NavLink>
              );
            })}
          </div>
        </nav>
      </div>

      <div className="mt-auto px-[18px] pb-6">
        <div className="rounded-[14px] border border-[#1d4a5e] bg-[#103447] p-3.5">
          <div className="flex items-center justify-between text-[11px]">
            <span className="text-white">System health</span>
            <span className="font-bold text-[#16a57a]">99.9%</span>
          </div>

          <div className="mt-2.5 h-[5px] overflow-hidden rounded-full bg-[#315366]">
            <div className="h-full w-[93%] rounded-full bg-[#16a57a]" />
          </div>

          <div className="mt-2 text-[9px] text-[#8fb0c1]">
            All services operational · 1m ago
          </div>
        </div>
      </div>
    </>
  );
}

export default function AppLayout() {
  const { user, logout, profileImageUrl } = useAuth();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [unresolvedAlertCount, setUnresolvedAlertCount] = useState(0);
  const [profileMenuOpen, setProfileMenuOpen] = useState(false);
  const profileMenuRef = useRef(null);

  useEffect(() => {
    let mounted = true;

    async function loadAlertCount() {
      try {
        const alerts = await getAlerts();

        if (!mounted) return;

        setUnresolvedAlertCount(
          Array.isArray(alerts)
            ? alerts.filter(
                (alert) =>
                  alert.status === "ACTIVE" ||
                  alert.status === "ACKNOWLEDGED",
              ).length
            : 0,
        );
      } catch (error) {
        if (!mounted) return;

        setUnresolvedAlertCount(0);
        console.error("Unable to load alert count:", error);
      }
    }

    loadAlertCount();

    const intervalId = window.setInterval(loadAlertCount, 5000);

    return () => {
      mounted = false;
      window.clearInterval(intervalId);
    };
  }, []);

  useEffect(() => {
    function closeProfileMenu(event) {
      if (profileMenuRef.current && !profileMenuRef.current.contains(event.target)) {
        setProfileMenuOpen(false);
      }
    }

    document.addEventListener("mousedown", closeProfileMenu);
    return () => document.removeEventListener("mousedown", closeProfileMenu);
  }, []);

  const handleLogout = () => {
    setProfileMenuOpen(false);
    logout();
    navigate("/login", { replace: true });
  };

  const displayName = user?.fullName || "NexaIoT User";
  const role =
    user?.role && user.role !== "USER"
      ? user.role
      : "System Administrator";

  return (
    <div
      className="min-h-screen bg-[#f4f8fb] text-[#102a3a]"
      style={{
        fontFamily:
          'Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
      }}
    >
      <aside className="fixed inset-y-0 left-0 z-40 hidden w-[236px] flex-col bg-[#0b2535] lg:flex">
        <SidebarContent unresolvedAlertCount={unresolvedAlertCount} />
      </aside>

      {mobileOpen ? (
        <div className="fixed inset-0 z-50 lg:hidden">
          <button
            type="button"
            aria-label="Close navigation"
            className="absolute inset-0 bg-slate-950/40"
            onClick={() => setMobileOpen(false)}
          />
          <aside className="relative flex h-full w-[236px] flex-col bg-[#0b2535] shadow-2xl">
            <button
              type="button"
              onClick={() => setMobileOpen(false)}
              className="absolute right-3 top-3 z-10 flex h-8 w-8 items-center justify-center rounded-lg bg-white/10 text-white"
            >
              <X size={18} />
            </button>
            <SidebarContent
              onNavigate={() => setMobileOpen(false)}
              unresolvedAlertCount={unresolvedAlertCount}
            />
          </aside>
        </div>
      ) : null}

      <div className="min-h-screen lg:pl-[236px]">
        <header className="sticky top-0 z-30 flex h-[72px] items-center border-b border-[#dce8ee] bg-white px-4 md:px-[30px]">
          <button
            type="button"
            onClick={() => setMobileOpen(true)}
            className="mr-3 flex h-10 w-10 items-center justify-center rounded-[10px] border border-[#dce8ee] text-[#6b8290] lg:hidden"
          >
            <Menu size={20} />
          </button>

          <div className="relative hidden h-10 w-[360px] items-center md:flex">
            <Search
              size={16}
              className="absolute left-[13px] text-[#9aaeba]"
            />
            <input
              type="text"
              placeholder="Search devices, alerts, reports..."
              className="h-full w-full rounded-[10px] border border-[#dce8ee] bg-[#f8fbfd] pl-[39px] pr-14 text-[12px] text-[#102a3a] outline-none placeholder:text-[#9aaeba] focus:border-[#08a9c4] focus:ring-2 focus:ring-[#08a9c4]/10"
            />
            <span className="absolute right-[13px] rounded-md border border-[#dce8ee] bg-white px-1.5 py-[3px] text-[9px] text-[#9aaeba]">
              ⌘ K
            </span>
          </div>

          <div className="ml-auto flex items-center gap-3">
            <button
              type="button"
              className="relative flex h-[38px] w-[38px] items-center justify-center rounded-[10px] text-[#6b8290] transition hover:bg-[#f4f8fb]"
              aria-label="Notifications"
            >
              <Bell size={17} />
              <span className="absolute right-[7px] top-[7px] h-[7px] w-[7px] rounded-full border border-white bg-[#e24e5a]" />
            </button>

            <div ref={profileMenuRef} className="relative">
              <button
                type="button"
                onClick={() => setProfileMenuOpen((open) => !open)}
                onKeyDown={(event) => event.key === "Escape" && setProfileMenuOpen(false)}
                aria-expanded={profileMenuOpen}
                aria-haspopup="menu"
                className="flex items-center gap-2 rounded-[10px] p-1 text-left transition hover:bg-[#f4f8fb]"
              >
                {profileImageUrl ? (
                  <img src={profileImageUrl} alt={`${displayName} profile`} className="h-9 w-9 rounded-full object-cover" />
                ) : (
                  <span className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-[#08a9c4] to-[#087b9c] text-xs font-bold text-white">
                    {displayName.charAt(0).toUpperCase()}
                  </span>
                )}
                <span className="hidden min-w-0 sm:block">
                  <span className="block max-w-[150px] truncate text-[12px] text-[#102a3a]">{displayName}</span>
                  <span className="block max-w-[150px] truncate text-[10px] text-[#6b8290]">{role}</span>
                </span>
                <ChevronDown size={14} className="hidden text-[#6b8290] sm:block" />
              </button>

              {profileMenuOpen ? (
                <div role="menu" className="absolute right-0 top-full z-50 mt-2 w-44 rounded-[10px] border border-[#dce8ee] bg-white p-1.5 shadow-[0_10px_24px_rgba(16,42,58,0.14)]">
                  <Link to="/profile" onClick={() => setProfileMenuOpen(false)} className="block rounded-[7px] px-3 py-2 text-[11px] text-[#526d7b] hover:bg-[#f4f8fb] hover:text-[#102a3a]">Profile</Link>
                  <Link to="/settings" onClick={() => setProfileMenuOpen(false)} className="block rounded-[7px] px-3 py-2 text-[11px] text-[#526d7b] hover:bg-[#f4f8fb] hover:text-[#102a3a]">Settings</Link>
                  <button type="button" onClick={handleLogout} className="flex w-full items-center gap-2 rounded-[7px] px-3 py-2 text-[11px] text-[#e24e5a] hover:bg-red-50"><LogOut size={13} />Logout</button>
                </div>
              ) : null}
            </div>

            <div className="hidden h-[26px] w-px bg-[#dce8ee] sm:block" />

            <button
              type="button"
              onClick={handleLogout}
              className="flex items-center gap-[7px] text-[11px] font-semibold text-[#6b8290] transition hover:text-[#e24e5a]"
            >
              <LogOut size={16} />
              <span className="hidden sm:inline">Logout</span>
            </button>
          </div>
        </header>

        <main className="min-h-[calc(100vh-72px)]">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
