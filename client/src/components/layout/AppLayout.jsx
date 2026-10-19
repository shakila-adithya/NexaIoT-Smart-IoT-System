import { useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import {
  Bell,
  ChartNoAxesCombined,
  ChevronDown,
  ClipboardCheck,
  Cpu,
  LayoutDashboard,
  LogOut,
  Menu,
  Search,
  Settings,
  ShieldCheck,
  Satellite,
  TriangleAlert,
  UserRound,
  X,
} from "lucide-react";
import { useAuth } from "../../hooks/useAuth.js";
import { getAlerts, markAlertRead, markAllAlertsRead } from "../../api/alertsApi.js";
import { getDevices } from "../../api/devicesApi.js";
import { getHealth } from "../../api/healthApi.js";
import PopoverSurface from "../ui/PopoverSurface.jsx";

const navigationGroups = [
  {
    label: "Main",
    items: [
      { name: "Dashboard", path: "/dashboard", icon: LayoutDashboard, end: true },
      { name: "Devices", path: "/devices", icon: Cpu },
      { name: "Alerts", path: "/alerts", icon: TriangleAlert, end: true },
      { name: "Alert Rules", path: "/alerts/rules", icon: ShieldCheck, end: true },
      { name: "Tasks", path: "/tasks", icon: ClipboardCheck },
      { name: "Reports", path: "/reports", icon: ChartNoAxesCombined },
    ],
  },
  {
    label: "Account",
    items: [
      { name: "Profile", path: "/profile", icon: UserRound, end: true },
      { name: "Settings", path: "/settings", icon: Settings, end: true },
    ],
  },
];

function formatAlertType(type = "") {
  return type.toLowerCase().split("_").map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(" ") || "Alert";
}

function matchesSearch(value, query) {
  return String(value || "").toLowerCase().includes(query);
}

function IconButton({ label, className = "", children, ...props }) {
  return (
    <button
      type="button"
      aria-label={label}
      className={`flex h-10 w-10 items-center justify-center rounded-[var(--radius-md)] text-[var(--color-text-muted)] transition-colors duration-200 hover:bg-[var(--color-surface-subtle)] hover:text-[var(--color-text-primary)] focus-visible:ring-2 focus-visible:ring-[var(--color-primary)]/35 ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}

function SidebarContent({ onNavigate, unresolvedAlertCount }) {
  return (
    <div className="flex min-h-full flex-col">
      <div className="px-5 pt-7">
        <Link to="/dashboard" onClick={onNavigate} className="flex items-center gap-2.5 px-2 text-lg font-semibold tracking-[-0.02em] text-white">
          <span className="flex h-9 w-9 items-center justify-center rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white shadow-[0_8px_20px_rgba(8,169,196,0.2)]">
            <Satellite size={19} />
          </span>
          NexaIoT
        </Link>

        <nav className="mt-9 space-y-7" aria-label="Application navigation">
          {navigationGroups.map((group) => (
            <div key={group.label}>
              <div className="px-3 text-[10px] font-semibold uppercase tracking-[0.14em] text-[#668b9e]">{group.label}</div>
              <div className="mt-2 space-y-1">
                {group.items.map((item) => {
                  const Icon = item.icon;
                  return (
                    <NavLink
                      key={item.path}
                      to={item.path}
                      end={item.end}
                      onClick={onNavigate}
                      className={({ isActive }) => `group relative flex h-11 items-center gap-3 rounded-[var(--radius-md)] px-3 text-[13px] ${isActive ? "active-nav-item bg-[var(--app-canvas)] font-semibold text-[var(--color-text-primary)]" : "text-[#8fb0c1]"}`}
                    >
                      {({ isActive }) => (
                        <>
                          <span className={`flex h-7 w-7 items-center justify-center rounded-[var(--radius-sm)] ${isActive ? "bg-[rgba(8,169,196,0.17)] text-[var(--color-primary)]" : "text-[#8fb0c1]"}`}>
                            <Icon size={18} strokeWidth={1.9} />
                          </span>
                          <span>{item.name}</span>
                          {item.path === "/alerts" && unresolvedAlertCount > 0 ? (
                            <span className="ml-auto flex h-5 min-w-[22px] items-center justify-center rounded-[var(--radius-pill)] bg-[var(--color-danger)] px-1.5 text-[10px] font-bold text-white">
                              {unresolvedAlertCount}
                            </span>
                          ) : null}
                        </>
                      )}
                    </NavLink>
                  );
                })}
              </div>
            </div>
          ))}
        </nav>
      </div>

    </div>
  );
}

function SearchResults({
  searchOpen,
  normalizedSearch,
  searchLoading,
  searchError,
  searchItems,
  deviceResults,
  alertResults,
  activeSearchIndex,
  onOpenItem,
}) {
  if (!searchOpen) return null;

  return (
    <PopoverSurface role="listbox" aria-label="Search results" className="absolute left-0 right-0 top-full z-[60] mt-2 max-h-[420px] overflow-y-auto p-2">
      {searchLoading ? <div className="px-3 py-5 text-xs text-[var(--color-text-muted)]">Searching...</div> : null}
      {!searchLoading && searchError ? <div className="px-3 py-5 text-xs text-[var(--color-danger)]">{searchError}</div> : null}
      {!searchLoading && !searchError && normalizedSearch.length < 2 ? <div className="px-3 py-5 text-xs text-[var(--color-text-muted)]">Type at least 2 characters.</div> : null}
      {!searchLoading && !searchError && normalizedSearch.length >= 2 && searchItems.length === 0 ? <div className="px-3 py-5 text-xs text-[var(--color-text-muted)]">No devices or alerts found.</div> : null}
      {!searchLoading && !searchError && normalizedSearch.length >= 2 && searchItems.length > 0 ? (
        <>
          {deviceResults.length > 0 ? (
            <div>
              <p className="px-2.5 pb-1.5 pt-1 text-[10px] font-semibold uppercase tracking-[0.1em] text-[var(--color-text-muted)]">Devices ({deviceResults.length})</p>
              {deviceResults.map((device, index) => (
                <button key={device.id} type="button" role="option" aria-selected={activeSearchIndex === index} onClick={() => onOpenItem({ type: "device", id: device.id })} className={`dropdown-action flex w-full items-start gap-2.5 rounded-[var(--radius-sm)] px-2.5 py-2.5 text-left text-[var(--color-text-primary)] transition-colors ${activeSearchIndex === index ? "bg-[var(--color-primary-soft)]" : "hover:bg-[var(--color-surface-subtle)]"}`}>
                  <Cpu size={16} className="mt-0.5 shrink-0 text-[var(--color-primary)]" />
                  <span className="min-w-0"><span className="block truncate text-xs font-semibold">{device.name}</span><span className="mt-0.5 block truncate text-[11px] text-[var(--color-text-muted)]">{device.status} · {device.location || device.deviceKey}</span></span>
                </button>
              ))}
            </div>
          ) : null}
          {alertResults.length > 0 ? (
            <div className="mt-2 border-t border-[var(--color-border)] pt-1">
              <p className="px-2.5 pb-1.5 pt-1 text-[10px] font-semibold uppercase tracking-[0.1em] text-[var(--color-text-muted)]">Alerts ({alertResults.length})</p>
              {alertResults.map((alert, index) => {
                const itemIndex = deviceResults.length + index;
                return <button key={alert.id} type="button" role="option" aria-selected={activeSearchIndex === itemIndex} onClick={() => onOpenItem({ type: "alert", id: alert.id })} className={`dropdown-action flex w-full items-start gap-2.5 rounded-[var(--radius-sm)] px-2.5 py-2.5 text-left transition-colors ${activeSearchIndex === itemIndex ? "bg-[var(--color-primary-soft)]" : "hover:bg-[var(--color-surface-subtle)]"}`}><Bell size={16} className="mt-0.5 shrink-0 text-[var(--color-danger)]" /><span className="min-w-0"><span className="block truncate text-xs font-semibold text-[var(--color-text-primary)]">{formatAlertType(alert.type)}</span><span className="mt-0.5 block truncate text-[11px] text-[var(--color-text-muted)]">{alert.severity || "INFO"} · {alert.deviceName || alert.deviceKey || "Device alert"} · {alert.status}</span><span className="mt-0.5 block truncate text-[11px] text-[var(--color-text-muted)]">{alert.message || "No alert message"}</span></span></button>;
              })}
            </div>
          ) : null}
        </>
      ) : null}
    </PopoverSurface>
  );
}

export default function AppLayout() {
  const { user, logout, profileImageUrl } = useAuth();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [unresolvedAlertCount, setUnresolvedAlertCount] = useState(0);
  const [alerts, setAlerts] = useState([]);
  const [profileMenuOpen, setProfileMenuOpen] = useState(false);
  const [notificationOpen, setNotificationOpen] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [unreadNotificationCount, setUnreadNotificationCount] = useState(0);
  const [notificationLoading, setNotificationLoading] = useState(true);
  const [notificationError, setNotificationError] = useState("");
  const [globalSearch, setGlobalSearch] = useState("");
  const [searchOpen, setSearchOpen] = useState(false);
  const [mobileSearchOpen, setMobileSearchOpen] = useState(false);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchError, setSearchError] = useState("");
  const [searchData, setSearchData] = useState(null);
  const [activeSearchIndex, setActiveSearchIndex] = useState(-1);
  const profileMenuRef = useRef(null);
  const notificationRef = useRef(null);
  const searchRef = useRef(null);
  const mobileSearchRef = useRef(null);
  const mobileSearchTriggerRef = useRef(null);
  const [health, setHealth] = useState({ loading: true, error: false });

  useEffect(() => {
    let mounted = true;

    async function loadAlertCount() {
      try {
        const alerts = await getAlerts();
        if (!mounted) return;

        setUnresolvedAlertCount(Array.isArray(alerts) ? alerts.filter((alert) => alert.status === "ACTIVE" || alert.status === "ACKNOWLEDGED").length : 0);
        setAlerts(Array.isArray(alerts) ? alerts : []);
        setNotifications(Array.isArray(alerts) ? alerts.slice(0, 5) : []);
        setUnreadNotificationCount(Array.isArray(alerts) ? alerts.filter((alert) => !alert.readAt).length : 0);
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
    const checkHealth = () => {
      getHealth()
        .then(() => mounted && setHealth({ loading: false, error: false }))
        .catch(() => mounted && setHealth({ loading: false, error: true }));
    };

    checkHealth();
    const healthInterval = window.setInterval(checkHealth, 10000);

    return () => {
      mounted = false;
      window.clearInterval(healthInterval);
    };
  }, []);

  useEffect(() => {
    function closeSearch(event) {
      const insideDesktop = searchRef.current?.contains(event.target);
      const insideMobile = mobileSearchRef.current?.contains(event.target) || mobileSearchTriggerRef.current?.contains(event.target);
      if (!insideDesktop) setSearchOpen(false);
      if (!insideMobile) setMobileSearchOpen(false);
    }
    document.addEventListener("mousedown", closeSearch);
    return () => document.removeEventListener("mousedown", closeSearch);
  }, []);

  const normalizedSearch = globalSearch.trim().toLowerCase();
  const deviceResults = normalizedSearch.length >= 2 && searchData ? searchData.devices.filter((device) => [device.name, device.type, device.deviceKey, device.location, device.status].some((field) => matchesSearch(field, normalizedSearch))).slice(0, 5) : [];
  const alertResults = normalizedSearch.length >= 2 && searchData ? searchData.alerts.filter((alert) => [alert.type, alert.message, alert.severity, alert.status, alert.deviceName, alert.deviceKey].some((field) => matchesSearch(field, normalizedSearch))).slice(0, 5) : [];
  const searchItems = [...deviceResults.map((device) => ({ type: "device", id: device.id })), ...alertResults.map((alert) => ({ type: "alert", id: alert.id }))];

  useEffect(() => {
    if (normalizedSearch.length < 2 || searchData) return undefined;
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

  useEffect(() => {
    function closeMenus(event) {
      if (profileMenuRef.current && !profileMenuRef.current.contains(event.target)) setProfileMenuOpen(false);
      if (notificationRef.current && !notificationRef.current.contains(event.target)) setNotificationOpen(false);
    }
    document.addEventListener("mousedown", closeMenus);
    return () => document.removeEventListener("mousedown", closeMenus);
  }, []);

  useEffect(() => {
    const closeOnEscape = (event) => {
      if (event.key === "Escape") {
        setNotificationOpen(false);
        setProfileMenuOpen(false);
        setMobileOpen(false);
        setMobileSearchOpen(false);
        setSearchOpen(false);
      }
    };
    document.addEventListener("keydown", closeOnEscape);
    return () => document.removeEventListener("keydown", closeOnEscape);
  }, []);

  const clearSearch = () => {
    setGlobalSearch("");
    setSearchOpen(false);
    setMobileSearchOpen(false);
    setSearchData(null);
    setActiveSearchIndex(-1);
  };

  const handleSearchChange = (event) => {
    const value = event.target.value;
    setGlobalSearch(value);
    setSearchOpen(value.trim().length >= 2);
    setMobileSearchOpen(true);
    setActiveSearchIndex(-1);
    if (value.trim().length < 2) setSearchError("");
  };

  const openSearchItem = (item) => {
    clearSearch();
    navigate(item.type === "device" ? `/devices/${item.id}` : `/alerts?alertId=${encodeURIComponent(item.id)}`);
  };

  const handleSearchKeyDown = (event) => {
    if (event.key === "Escape") {
      setSearchOpen(false);
      setMobileSearchOpen(false);
      return;
    }
    if (!searchOpen && !mobileSearchOpen) return;
    if (event.key === "ArrowDown" && searchItems.length > 0) {
      event.preventDefault();
      setActiveSearchIndex((index) => (index + 1) % searchItems.length);
    } else if (event.key === "ArrowUp" && searchItems.length > 0) {
      event.preventDefault();
      setActiveSearchIndex((index) => (index - 1 + searchItems.length) % searchItems.length);
    } else if (event.key === "Enter" && activeSearchIndex >= 0) {
      event.preventDefault();
      openSearchItem(searchItems[activeSearchIndex]);
    }
  };

  const handleLogout = () => {
    setProfileMenuOpen(false);
    logout();
    navigate("/login", { replace: true });
  };

  const handleNotificationClick = async (alert) => {
    if (!alert.readAt) {
      try {
        const updatedAlert = await markAlertRead(alert.id);
        setAlerts((current) => current.map((item) => item.id === updatedAlert.id ? updatedAlert : item));
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
      setAlerts((current) => {
        const updatedById = new Map(updatedAlerts.map((alert) => [alert.id, alert]));
        return current.map((alert) => updatedById.get(alert.id) || alert);
      });
      setUnreadNotificationCount(0);
    } catch {
      setNotificationError("Unable to mark notifications as read.");
    }
  };

  const displayName = user?.fullName || "NexaIoT User";
  const role = user?.role && user.role !== "USER" ? user.role : "System Administrator";

  return (
    <div className="authenticated-app-shell app-glass-background min-h-screen w-full max-w-full overflow-x-hidden text-[var(--color-text-primary)]">
      <aside className="app-sidebar-scroll fixed inset-y-0 left-0 z-40 hidden w-[248px] flex-col overflow-y-auto bg-[var(--color-sidebar)] lg:flex">
        <SidebarContent unresolvedAlertCount={unresolvedAlertCount} />
      </aside>

      {mobileOpen ? (
        <div className="fixed inset-0 z-50 lg:hidden">
          <button type="button" aria-label="Close navigation" className="absolute inset-0 bg-[var(--color-overlay)] backdrop-blur-[2px]" onClick={() => setMobileOpen(false)} />
          <aside role="dialog" aria-label="Mobile navigation" className="app-sidebar-scroll relative flex h-full w-[280px] max-w-[86vw] flex-col overflow-y-auto bg-[var(--color-sidebar)] shadow-[var(--shadow-dialog)]">
            <IconButton label="Close navigation" onClick={() => setMobileOpen(false)} className="absolute right-4 top-4 z-10 text-white hover:bg-white/10 hover:text-white"><X size={19} /></IconButton>
            <SidebarContent onNavigate={() => setMobileOpen(false)} unresolvedAlertCount={unresolvedAlertCount} />
          </aside>
        </div>
      ) : null}

      <div className="min-h-screen min-w-0 max-w-full lg:ml-[248px] lg:w-auto">
        <header className="glass-topbar sticky top-0 z-30 flex h-[72px] items-center px-4 md:px-7">
          <IconButton label="Open navigation" onClick={() => setMobileOpen(true)} className="mr-2 lg:hidden"><Menu size={20} /></IconButton>

          <div ref={searchRef} className="relative hidden h-10 w-full max-w-[420px] items-center md:flex">
            <Search size={16} className="pointer-events-none absolute left-3.5 text-[var(--color-text-muted)]" />
            <input type="text" value={globalSearch} onChange={handleSearchChange} onFocus={() => normalizedSearch.length >= 2 && setSearchOpen(true)} onKeyDown={handleSearchKeyDown} placeholder="Search devices and alerts..." aria-label="Search devices and alerts" className="glass-input h-full w-full rounded-[var(--radius-md)] pl-10 pr-3 text-sm text-[var(--color-text-primary)] outline-none transition focus:border-[var(--color-primary)] focus:bg-white focus:ring-2 focus:ring-[var(--color-primary)]/12" />
            <SearchResults searchOpen={searchOpen} normalizedSearch={normalizedSearch} searchLoading={searchLoading} searchError={searchError} searchItems={searchItems} deviceResults={deviceResults} alertResults={alertResults} activeSearchIndex={activeSearchIndex} onOpenItem={openSearchItem} />
          </div>

          <div className="relative md:hidden" ref={mobileSearchTriggerRef}>
            <IconButton label="Open search" aria-expanded={mobileSearchOpen} onClick={() => setMobileSearchOpen((open) => !open)}><Search size={19} /></IconButton>
            {mobileSearchOpen ? (
              <div ref={mobileSearchRef} className="glass-popover fixed left-3 right-3 top-[68px] z-[60] rounded-[var(--radius-card)] p-3">
            <div className="relative flex h-10 items-center">
                  <Search size={16} className="pointer-events-none absolute left-3.5 text-[var(--color-text-muted)]" />
                  <input autoFocus type="text" value={globalSearch} onChange={handleSearchChange} onKeyDown={handleSearchKeyDown} placeholder="Search devices and alerts..." aria-label="Search devices and alerts" className="glass-input h-full w-full rounded-[var(--radius-md)] pl-10 pr-3 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-primary)] focus:bg-white focus:ring-2 focus:ring-[var(--color-primary)]/12" />
                  <SearchResults searchOpen={mobileSearchOpen} normalizedSearch={normalizedSearch} searchLoading={searchLoading} searchError={searchError} searchItems={searchItems} deviceResults={deviceResults} alertResults={alertResults} activeSearchIndex={activeSearchIndex} onOpenItem={openSearchItem} />
                </div>
              </div>
            ) : null}
          </div>

          <div className="ml-auto flex items-center gap-1.5 sm:gap-2">
            <div ref={notificationRef} className="relative">
              <IconButton label="Notifications" aria-expanded={notificationOpen} onClick={() => setNotificationOpen((open) => !open)} className="relative">
                <Bell size={18} />
                {unreadNotificationCount > 0 ? <span className="absolute right-0.5 top-0.5 flex h-4 min-w-4 items-center justify-center rounded-[var(--radius-pill)] bg-[var(--color-danger)] px-1 text-[9px] font-bold text-white">{unreadNotificationCount > 9 ? "9+" : unreadNotificationCount}</span> : null}
              </IconButton>
              {notificationOpen ? (
                <PopoverSurface role="dialog" aria-label="Notifications" className="absolute right-0 top-full z-[60] mt-3 w-[min(360px,calc(100vw-2rem))]">
                  <div className="flex items-center justify-between border-b border-[var(--color-border)] px-2.5 pb-2.5 pt-1"><p className="text-sm font-semibold text-[var(--color-text-primary)]">Notifications</p>{unreadNotificationCount > 0 ? <button type="button" onClick={handleMarkAllNotificationsRead} className="dropdown-action rounded-[var(--radius-sm)] px-2 py-1 text-[11px] font-semibold text-[var(--color-primary-hover)] hover:bg-[var(--color-primary-soft)] focus-visible:ring-2 focus-visible:ring-[var(--color-primary)]/35">Mark all as read</button> : null}</div>
                  {notificationLoading ? <p className="px-2.5 py-5 text-xs text-[var(--color-text-muted)]">Loading notifications...</p> : null}
                  {!notificationLoading && notificationError ? <p className="px-2.5 py-5 text-xs text-[var(--color-danger)]">{notificationError}</p> : null}
                  {!notificationLoading && !notificationError && notifications.length === 0 ? <p className="px-2.5 py-5 text-xs text-[var(--color-text-muted)]">No new notifications</p> : null}
                  {!notificationLoading && !notificationError && notifications.length > 0 ? <div className="mt-1 max-h-[360px] overflow-y-auto"><div className="space-y-1">{notifications.map((alert) => <button key={alert.id} type="button" onClick={() => handleNotificationClick(alert)} className={`dropdown-action flex w-full items-start gap-2.5 rounded-[var(--radius-sm)] px-2.5 py-2.5 text-left transition-colors hover:bg-[var(--color-surface-subtle)] ${alert.readAt ? "" : "bg-[var(--color-primary-soft)]/55"}`}><span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${alert.readAt ? "bg-[var(--color-border-strong)]" : "bg-[var(--color-danger)]"}`} /><span className="min-w-0"><span className={`block truncate text-xs text-[var(--color-text-primary)] ${alert.readAt ? "font-medium" : "font-semibold"}`}>{formatAlertType(alert.type)}</span><span className="mt-0.5 block truncate text-[11px] text-[var(--color-text-muted)]">{alert.deviceName || alert.deviceKey || "Device alert"} · {alert.severity || "INFO"}</span><span className="mt-0.5 block truncate text-[11px] text-[var(--color-text-muted)]">{alert.message || "Review alert details"}</span></span></button>)}</div></div> : null}
                </PopoverSurface>
              ) : null}
            </div>

            <div ref={profileMenuRef} className="relative">
              <button type="button" onClick={() => setProfileMenuOpen((open) => !open)} aria-expanded={profileMenuOpen} aria-haspopup="menu" className="flex min-h-10 items-center gap-2 rounded-[var(--radius-md)] p-1 transition-colors hover:bg-[var(--color-surface-subtle)] focus-visible:ring-2 focus-visible:ring-[var(--color-primary)]/35">
                {profileImageUrl ? <img src={profileImageUrl} alt={`${displayName} profile`} className="h-9 w-9 rounded-full object-cover" /> : <span className="flex h-9 w-9 items-center justify-center rounded-full bg-[var(--color-primary)] text-xs font-bold text-white">{displayName.charAt(0).toUpperCase()}</span>}
                <span className="hidden min-w-0 text-left sm:block"><span className="block max-w-[150px] truncate text-xs font-semibold text-[var(--color-text-primary)]">{displayName}</span><span className="block max-w-[150px] truncate text-[11px] text-[var(--color-text-muted)]">{role}</span></span>
                <ChevronDown size={15} className="hidden text-[var(--color-text-muted)] sm:block" />
              </button>
              {profileMenuOpen ? <PopoverSurface role="menu" aria-label="User menu" className="absolute right-0 top-full z-[60] mt-3 w-48"><div className="border-b border-[var(--color-border)] px-3 py-2"><p className="truncate text-xs font-semibold text-[var(--color-text-primary)]">{displayName}</p><p className="mt-0.5 truncate text-[11px] text-[var(--color-text-muted)]">{role}</p></div><div className="mt-1 space-y-0.5"><Link role="menuitem" to="/profile" onClick={() => setProfileMenuOpen(false)} className="dropdown-action block rounded-[var(--radius-sm)] px-3 py-2.5 text-xs text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-subtle)] hover:text-[var(--color-text-primary)]">Profile</Link><Link role="menuitem" to="/settings" onClick={() => setProfileMenuOpen(false)} className="dropdown-action block rounded-[var(--radius-sm)] px-3 py-2.5 text-xs text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-subtle)] hover:text-[var(--color-text-primary)]">Settings</Link><button role="menuitem" type="button" onClick={handleLogout} className="dropdown-action flex w-full items-center gap-2 rounded-[var(--radius-sm)] px-3 py-2.5 text-left text-xs text-[var(--color-danger)] hover:bg-[var(--color-danger-soft)]"><LogOut size={14} />Logout</button></div></PopoverSurface> : null}
            </div>

            <div className="hidden h-7 w-px bg-[var(--color-border)] sm:block" />
            <IconButton label="Logout" onClick={handleLogout} className="hidden sm:flex"><LogOut size={17} /></IconButton>
          </div>
        </header>

        <main className="min-h-[calc(100vh-72px)] min-w-0 w-full max-w-full">
          {health.error ? (
            <div className="backend-unavailable-banner" role="alert" aria-live="assertive">
              <TriangleAlert size={17} aria-hidden="true" />
              <div>
                <strong>Backend unavailable</strong>
                <span>Some NexaIoT features may not work until the server reconnects.</span>
              </div>
            </div>
          ) : null}
          <Outlet context={{ alerts, alertsLoading: notificationLoading, alertsError: notificationError }} />
        </main>
      </div>
    </div>
  );
}
