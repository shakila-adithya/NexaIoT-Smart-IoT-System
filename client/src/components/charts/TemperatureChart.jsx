import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

function ChartTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;

  return (
    <div className="rounded-[10px] border border-[#dce8ee] bg-white px-3 py-2 shadow-lg">
      <div className="text-[9px] text-[#8397a2]">{label}</div>
      <div className="mt-1 text-[11px] font-semibold text-[#102a3a]">
        {payload[0].value}°C
      </div>
    </div>
  );
}

export default function TemperatureChart({ data = [] }) {
  return (
    <div className="h-[190px] w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart
          data={data}
          margin={{ top: 8, right: 8, left: -24, bottom: 0 }}
        >
          <CartesianGrid
            vertical={false}
            stroke="#e8eff3"
            strokeDasharray="0"
          />

          <XAxis
            dataKey="time"
            axisLine={false}
            tickLine={false}
            tick={{ fill: "#8aa0ac", fontSize: 9 }}
            dy={9}
          />

          <YAxis
            hide
            domain={["dataMin - 1", "dataMax + 1"]}
          />

          <Tooltip
            cursor={{ stroke: "#dce8ee", strokeWidth: 1 }}
            content={<ChartTooltip />}
          />

          <Line
            type="monotone"
            dataKey="temperature"
            stroke="#08a9c4"
            strokeWidth={3}
            dot={{
              r: 4,
              fill: "#ffffff",
              stroke: "#08a9c4",
              strokeWidth: 2,
            }}
            activeDot={{
              r: 5,
              fill: "#08a9c4",
              stroke: "#ffffff",
              strokeWidth: 2,
            }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
