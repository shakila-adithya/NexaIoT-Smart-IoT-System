import { BellRing, CircleCheck, Cpu, WifiOff } from "lucide-react";

const toneMap = {
  cyan: "bg-[#e8f8fb] text-[#08a9c4]",
  amber: "bg-[#fff4df] text-[#e59a22]",
  green: "bg-[#e8f7f2] text-[#16a57a]",
  purple: "bg-[#f2edff] text-[#8066d6]",
};

function formatRelativeTime(value) {
  if (!value) return "Unknown time";

  const elapsedSeconds = Math.max(
    0,
    Math.floor((Date.now() - new Date(value).getTime()) / 1000),
  );

  if (elapsedSeconds < 60) return "Just now";
  if (elapsedSeconds < 3600) return `${Math.floor(elapsedSeconds / 60)} min ago`;
  if (elapsedSeconds < 86400) return `${Math.floor(elapsedSeconds / 3600)} hr ago`;
  return `${Math.floor(elapsedSeconds / 86400)} day${Math.floor(elapsedSeconds / 86400) === 1 ? "" : "s"} ago`;
}

function formatAlertType(type) {
  return (type || "Alert")
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

function buildActivity(devices, alerts) {
  const activities = [];

  alerts.forEach((alert) => {
    const deviceName = alert.deviceName || "Unknown device";
    const alertTitle = formatAlertType(alert.type);

    activities.push({
      title: `${alertTitle} alert created`,
      meta: `${deviceName} · ${formatRelativeTime(alert.createdAt)}`,
      timestamp: alert.createdAt,
      icon: BellRing,
      tone: "amber",
    });

    if (alert.acknowledgedAt) {
      activities.push({
        title: `${alertTitle} acknowledged`,
        meta: `${deviceName} · ${formatRelativeTime(alert.acknowledgedAt)}`,
        timestamp: alert.acknowledgedAt,
        icon: CircleCheck,
        tone: "cyan",
      });
    }

    if (alert.resolvedAt) {
      activities.push({
        title: `${alertTitle} resolved`,
        meta: `${deviceName} · ${formatRelativeTime(alert.resolvedAt)}`,
        timestamp: alert.resolvedAt,
        icon: CircleCheck,
        tone: "green",
      });
    }
  });

  devices.forEach((device) => {
    if (device.createdAt) {
      activities.push({
        title: `${device.name || "Device"} registered`,
        meta: `${device.location || "Unknown location"} · ${formatRelativeTime(device.createdAt)}`,
        timestamp: device.createdAt,
        icon: Cpu,
        tone: "purple",
      });
    }
  });

  return activities
    .filter((activity) => !Number.isNaN(new Date(activity.timestamp).getTime()))
    .sort((first, second) => new Date(second.timestamp) - new Date(first.timestamp))
    .slice(0, 5);
}

export default function RecentActivity({ devices = [], alerts = [] }) {
  const activities = buildActivity(devices, alerts);

  return (
    <section className="rounded-[14px] border border-[#dce8ee] bg-white p-[18px] shadow-[0_6px_20px_rgba(10,48,72,0.07)]">
      <div className="flex items-start justify-between">
        <div>
          <h2 className="text-[15px] font-semibold text-[#102a3a]">
            Recent activity
          </h2>
          <p className="mt-1 text-[11px] text-[#6b8290]">
            Latest events from your workspace
          </p>
        </div>

      </div>

      <div className="mt-3.5 space-y-[14px]">
        {activities.length === 0 ? (
          <div className="rounded-[10px] bg-[#f8fbfd] p-4">
            <p className="text-[11px] font-medium text-[#102a3a]">
              No recent activity yet
            </p>
            <p className="mt-1 text-[10px] leading-4 text-[#6b8290]">
              Activity will appear after devices or alerts are recorded.
            </p>
          </div>
        ) : activities.map((item) => {
          const Icon = item.icon || WifiOff;

          return (
            <div key={item.title} className="flex items-center">
              <div
                className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-[9px] ${
                  toneMap[item.tone] || toneMap.cyan
                }`}
              >
                <Icon size={14} strokeWidth={1.9} />
              </div>

              <div className="ml-[11px] min-w-0">
                <div className="truncate text-[11px] font-medium text-[#102a3a]">
                  {item.title}
                </div>
                <div className="mt-1 truncate text-[9px] text-[#8397a2]">
                  {item.meta}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}
