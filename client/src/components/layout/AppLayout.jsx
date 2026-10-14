import { useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import {
  Bell,
  ClipboardCheck,
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
import { getAlerts, markAlertRead, markAllAlertsRead } from "../../api/alertsApi.js";
import { getDevices } from "../../api/devicesApi.js";
import { getHealth } from "../../api/healthApi.js";

const navigation = [
  { name: "Dashboard", path: "/dashboard", icon: LayoutDashboard },
  { name: "Devices", path: "/devices", icon: Cpu },
  { name: "Alerts", path: "/alerts", icon: TriangleAlert },
  { name: "Reports", path: "/reports", icon: ChartNoAxesCombined },
  { name: "Tasks", path: "/tasks", icon: ClipboardCheck },
  { name: "Settings", path: "/settings", icon: Settings },
];

function SidebarContent({ onNavigate, unresolvedAlertCount, health }) {
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
            <span className="text-white">Backend health</span>
            <span className={`font-bold ${health.loading ? "text-[#f0c36a]" : health.error ? "text-[#f08b95]" : "text-[#16a57a]"}`}>
              {health.loading ? "Checking..." : health.error ? "Unavailable" : "Operational"}
            </span>
          </div>

          <div className="mt-2 text-[9px] text-[#8fb0c1]">
            {health.loading ? "Checking the NexaIoT backend..." : health.error ? "Backend health could not be confirmed." : "The NexaIoT backend is responding."}
          </div>
        </div>
      </div>
    </>
  );
}

function formatAlertType(type = "") {
  return type.toLowerCase().split("_").map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(" ") || "Alert";
}

function matchesSearch(value, query) {
  return String(value || "").toLowerCase().includes(query);
}

export default function AppLayout() {
  const { user, logout, profileImageUrl } = useAuth();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [unresolvedAlertCount, setUnresolvedAlertCount] = useState(0);
  const [profileMenuOpen, setProfileMenuOpen] = useState(false);
  const [notificationOpen, setNotificationOpen] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [unreadNotificationCount, setUnreadNotificationCount] = useState(0);
  const [notificationLoading, setNotificationLoading] = useState(true);
  const [notificationError, setNotificationError] = useState("");
  const [globalSearch, setGlobalSearch] = useState("");
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchError, setSearchError] = useState("");
  const [searchData, setSearchData] = useState(null);
  const [activeSearchIndex, setActiveSearchIndex] = useState(-1);
  const profileMenuRef = useRef(null);
  const notificationRef = useRef(null);
  const searchRef = useRef(null);
  const [health, setHealth] = useState({ loading: true, error: false });

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
        setNotifications(Array.isArray(alerts) ? alerts.slice(0, 5) : []);
        setUnreadNotificationCount(
          Array.isArray(alerts) ? alerts.filter((alert) => !alert.readAt).length : 0,
        );
        setNotificationError("");
      } catch (error) {
        if (!mounted) return;

        setUnresolvedAlertCount(0);
        setUnreadNotificationCount(0);
        setNotificationError("Unable to load notifications.");
        console.error("Unable to load alert count:", error);
      } finally {
        if (mounted) setNotificationLoading(false);
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
    let mounted = true;
    getHealth()
      .then(() => mounted && setHealth({ loading: false, error: false }))
      .catch(() => mounted && setHealth({ loading: false, error: true }));

    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    function closeSearch(event) {
      if (searchRef.current && !searchRef.current.contains(event.target)) setSearchOpen(false);
    }

    document.addEventListener("mousedown", closeSearch);
    return () => document.removeEventListener("mousedown", closeSearch);
  }, []);

  const normalizedSearch = globalSearch.trim().toLowerCase();
  const deviceResults = normalizedSearch.length >= 2 && searchData
    ? searchData.devices.filter((device) => [device.name, device.type, device.deviceKey, device.location, device.status].some((field) => matchesSearch(field, normalizedSearch))).slice(0, 5)
    : [];
  const alertResults = normalizedSearch.length >= 2 && searchData
    ? searchData.alerts.filter((alert) => [alert.type, alert.message, alert.severity, alert.status, alert.deviceName, alert.deviceKey].some((field) => matchesSearch(field, normalizedSearch))).slice(0, 5)
    : [];
  const searchItems = [
    ...deviceResults.map((device) => ({ type: "device", id: device.id })),
    ...alertResults.map((alert) => ({ type: "alert", id: alert.id })),
  ];

  useEffect(() => {
    if (normalizedSearch.length < 2) {
      return undefined;
    }

    if (searchData) return undefined;

    let cancelled = false;
    const timeoutId = window.setTimeout(async () => {
      try {
        setSearchLoading(true);
        setSearchError("");
        const [devices, alerts] = await Promise.all([getDevices(), getAlerts()]);
        if (!cancelled) setSearchData({ devices: Array.isArray(devices) ? devices : [], alerts: Array.isArray(alerts) ? alerts : [] });
      } catch {
        if (!cancelled) setSearchError("Unable to search devices and alerts right now.");
      } finally {
        if (!cancelled) setSearchLoading(false);
      }
    }, 300);

    return () => {
      cancelled = true;
      window.clearTimeout(timeoutId);
    };
  }, [normalizedSearch, searchData]);

  const clearSearch = () => {
    setGlobalSearch("");
    setSearchOpen(false);
    setSearchData(null);
    setActiveSearchIndex(-1);
  };

  const openSearchItem = (item) => {
    clearSearch();
    navigate(item.type === "device" ? `/devices/${item.id}` : `/alerts?alertId=${encodeURIComponent(item.id)}`);
  };

  const handleSearchKeyDown = (event) => {
    if (event.key === "Escape") {
      setSearchOpen(false);
      return;
    }
    if (!searchOpen || searchItems.length === 0) return;
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActiveSearchIndex((index) => (index + 1) % searchItems.length);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActiveSearchIndex((index) => (index - 1 + searchItems.length) % searchItems.length);
    } else if (event.key === "Enter" && activeSearchIndex >= 0) {
      event.preventDefault();
      openSearchItem(searchItems[activeSearchIndex]);
    }
  };

  useEffect(() => {
    function closeProfileMenu(event) {
      if (profileMenuRef.current && !profileMenuRef.current.contains(event.target)) {
        setProfileMenuOpen(false);
      }
      if (notificationRef.current && !notificationRef.current.contains(event.target)) {
        setNotificationOpen(false);
      }
    }

    document.addEventListener("mousedown", closeProfileMenu);
    return () => document.removeEventListener("mousedown", closeProfileMenu);
  }, []);

  useEffect(() => {
    const closeNotificationsOnEscape = (event) => {
      if (event.key === "Escape") setNotificationOpen(false);
    };
    document.addEventListener("keydown", closeNotificationsOnEscape);
    return () => document.removeEventListener("keydown", closeNotificationsOnEscape);
  }, []);

  const handleLogout = () => {
    setProfileMenuOpen(false);
    logout();
    navigate("/login", { replace: true });
  };

  const handleNotificationClick = async (alert) => {
    if (!alert.readAt) {
      try {
        const updatedAlert = await markAlertRead(alert.id);
        setNotifications((current) => current.map((item) => item.id === updatedAlert.id ? updatedAlert : item));
        setUnreadNotificationCount((count) => Math.max(0, count - 1));
      } catch {
        setNotificationError("Unable to mark notification as read.");
      }
    }
    setNotificationOpen(false);
    navigate(`/alerts?alertId=${encodeURIComponent(alert.id)}`);
  };

  const handleMarkAllNotificationsRead = async () => {
    if (unreadNotificationCount === 0) return;
    try {
      const updatedAlerts = await markAllAlertsRead();
      setNotifications((current) => {
        const updatedById = new Map(updatedAlerts.map((alert) => [alert.id, alert]));
        return current.map((alert) => updatedById.get(alert.id) || alert);
      });
      setUnreadNotificationCount(0);
    } catch {
      setNotificationError("Unable to mark notifications as read.");
    }
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
        <SidebarContent unresolvedAlertCount={unresolvedAlertCount} health={health} />
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
              health={health}
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

          <div ref={searchRef} className="relative hidden h-10 w-[360px] items-center md:flex">
            <Search
              size={16}
              className="absolute left-[13px] text-[#9aaeba]"
            />
            <input
              type="text"
              value={globalSearch}
              onChange={(event) => {
                const value = event.target.value;
                setGlobalSearch(value);
                setSearchOpen(value.trim().length >= 2);
                setActiveSearchIndex(-1);
                if (value.trim().length < 2) setSearchError("");
              }}
              onFocus={() => normalizedSearch.length >= 2 && setSearchOpen(true)}
              onKeyDown={handleSearchKeyDown}
              placeholder="Search devices and alerts..."
              className="h-full w-full rounded-[10px] border border-[#dce8ee] bg-[#f8fbfd] pl-[39px] pr-3 text-[12px] text-[#102a3a] outline-none placeholder:text-[#9aaeba] focus:border-[#08a9c4] focus:ring-2 focus:ring-[#08a9c4]/10"
            />
            {searchOpen ? (
              <div className="absolute left-0 right-0 top-full z-50 mt-2 max-h-[420px] overflow-y-auto rounded-[12px] border border-[#dce8ee] bg-white p-2 shadow-[0_14px_30px_rgba(16,42,58,0.16)]">
                {searchLoading ? <div className="px-3 py-4 text-[11px] text-[#6b8290]">Searching...</div> : searchError ? <div className="px-3 py-4 text-[11px] text-red-600">{searchError}</div> : normalizedSearch.length < 2 ? <div className="px-3 py-4 text-[11px] text-[#6b8290]">Type at least 2 characters.</div> : searchItems.length === 0 ? <div className="px-3 py-4 text-[11px] text-[#6b8290]">No devices or alerts found.</div> : <>
                  {deviceResults.length > 0 ? <div><p className="px-2.5 pb-1.5 pt-1 text-[9px] font-semibold uppercase tracking-wide text-[#8ba0ab]">Devices ({deviceResults.length})</p>{deviceResults.map((device, index) => <button key={device.id} type="button" onClick={() => openSearchItem({ type: "device", id: device.id })} className={`flex w-full items-start gap-2.5 rounded-[8px] px-2.5 py-2 text-left ${activeSearchIndex === index ? "bg-[#eef9fb]" : "hover:bg-[#f8fbfd]"}`}><Cpu size={14} className="mt-0.5 text-[#08a9c4]" /><span className="min-w-0"><span className="block truncate text-[11px] font-medium text-[#102a3a]">{device.name}</span><span className="mt-0.5 block truncate text-[9px] text-[#6b8290]">{device.status} · {device.location || device.deviceKey}</span></span></button>)}</div> : null}
                  {alertResults.length > 0 ? <div className="mt-2 border-t border-[#edf3f6] pt-1"><p className="px-2.5 pb-1.5 pt-1 text-[9px] font-semibold uppercase tracking-wide text-[#8ba0ab]">Alerts ({alertResults.length})</p>{alertResults.map((alert, index) => { const itemIndex = deviceResults.length + index; return <button key={alert.id} type="button" onClick={() => openSearchItem({ type: "alert", id: alert.id })} className={`flex w-full items-start gap-2.5 rounded-[8px] px-2.5 py-2 text-left ${activeSearchIndex === itemIndex ? "bg-[#eef9fb]" : "hover:bg-[#f8fbfd]"}`}><Bell size={14} className="mt-0.5 text-[#e24e5a]" /><span className="min-w-0"><span className="block truncate text-[11px] font-medium text-[#102a3a]">{formatAlertType(alert.type)}</span><span className="mt-0.5 block truncate text-[9px] text-[#6b8290]">{alert.severity || "INFO"} · {alert.deviceName || alert.deviceKey || "Device alert"} · {alert.status}</span><span className="mt-0.5 block truncate text-[9px] text-[#8ba0ab]">{alert.message || "No alert message"}</span></span></button>; })}</div> : null}
                </>}
              </div>
            ) : null}
          </div>

          <div className="ml-auto flex items-center gap-3">
            <div ref={notificationRef} className="relative">
              <button type="button" onClick={() => setNotificationOpen((open) => !open)} className="relative flex h-[38px] w-[38px] items-center justify-center rounded-[10px] text-[#6b8290] transition hover:bg-[#f4f8fb]" aria-label="Notifications" aria-expanded={notificationOpen}>
                <Bell size={17} />
                {unreadNotificationCount > 0 ? <span className="absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-[#e24e5a] px-1 text-[8px] font-bold text-white">{unreadNotificationCount > 9 ? "9+" : unreadNotificationCount}</span> : null}
              </button>
              {notificationOpen ? (
                <div className="absolute right-0 top-full z-50 mt-2 w-[320px] rounded-[12px] border border-[#dce8ee] bg-white p-2 shadow-[0_14px_30px_rgba(16,42,58,0.16)]">
                  <div className="flex items-center justify-between border-b border-[#edf3f6] px-2.5 pb-2 pt-1"><p className="text-[12px] font-semibold text-[#102a3a]">Notifications</p>{unreadNotificationCount > 0 ? <button type="button" onClick={handleMarkAllNotificationsRead} className="text-[9px] font-medium text-[#087f98] hover:text-[#08a9c4]">Mark all as read</button> : null}</div>
                  {notificationLoading ? <p className="px-2.5 py-5 text-[10px] text-[#6b8290]">Loading notifications...</p> : notificationError ? <p className="px-2.5 py-5 text-[10px] text-red-600">{notificationError}</p> : notifications.length === 0 ? <p className="px-2.5 py-5 text-[10px] text-[#6b8290]">No new notifications</p> : <div className="mt-1 max-h-[360px] overflow-x-hidden overflow-y-auto"><div className="space-y-1">{notifications.map((alert) => <button key={alert.id} type="button" onClick={() => handleNotificationClick(alert)} className={`flex w-full items-start gap-2.5 rounded-[8px] px-2.5 py-2 text-left hover:bg-[#f8fbfd] ${alert.readAt ? "" : "bg-[#f7fcfd]"}`}><span className={`mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full ${alert.readAt ? "bg-[#cbd8de]" : "bg-[#e24e5a]"}`} /><span className="min-w-0"><span className={`block truncate text-[10px] ${alert.readAt ? "font-medium" : "font-semibold"} text-[#102a3a]`}>{formatAlertType(alert.type)}</span><span className="mt-0.5 block truncate text-[9px] text-[#6b8290]">{alert.deviceName || alert.deviceKey || "Device alert"} · {alert.severity || "INFO"}</span><span className="mt-0.5 block truncate text-[9px] text-[#8ba0ab]">{alert.message || "Review alert details"}</span></span></button>)}</div></div>}
                </div>
              ) : null}
            </div>

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
