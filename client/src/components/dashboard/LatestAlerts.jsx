import { useNavigate } from "react-router-dom";
import { latestAlerts } from "../../data/dashboardMockData.js";

const styles = {
  Critical: {
    container: "border-[#f7d4d7] bg-[#fff7f8]",
    badge: "bg-[#fdecee] text-[#d83f4d]",
    dot: "bg-[#e24e5a]",
  },
  Warning: {
    container: "border-[#f5e1bd] bg-[#fffaf2]",
    badge: "bg-[#fff1d7] text-[#c67b08]",
    dot: "bg-[#e59a22]",
  },
  Info: {
    container: "border-[#d7ebf1] bg-[#f7fcfd]",
    badge: "bg-[#e8f8fb] text-[#087f98]",
    dot: "bg-[#08a9c4]",
  },
};

export default function LatestAlerts() {
  const navigate = useNavigate();

  return (
    <section className="rounded-[14px] border border-[#dce8ee] bg-white p-[18px] shadow-[0_6px_20px_rgba(10,48,72,0.07)]">
      <div className="flex items-center justify-between">
        <h2 className="text-[15px] font-semibold text-[#102a3a]">
          Latest alerts
        </h2>

        <button
          type="button"
          onClick={() => navigate("/alerts")}
          className="text-[11px] font-medium text-[#08a9c4]"
        >
          See all 7
        </button>
      </div>

      <div className="mt-[13px] space-y-3">
        {latestAlerts.map((alert) => {
          const style = styles[alert.severity] || styles.Info;

          return (
            <div
              key={alert.title}
              className={`rounded-[10px] border p-3 ${style.container}`}
            >
              <div className="flex items-center justify-between">
                <span
                  className={`inline-flex h-[22px] items-center gap-1.5 rounded-full px-2 text-[10px] font-semibold ${style.badge}`}
                >
                  <span className={`h-1.5 w-1.5 rounded-full ${style.dot}`} />
                  {alert.severity}
                </span>

                <span className="text-[9px] text-[#8397a2]">{alert.status}</span>
              </div>

              <div className="mt-2 text-[11px] font-medium text-[#102a3a]">
                {alert.title}
              </div>

              <div className="mt-1 text-[9px] text-[#8397a2]">
                {alert.meta}
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}
