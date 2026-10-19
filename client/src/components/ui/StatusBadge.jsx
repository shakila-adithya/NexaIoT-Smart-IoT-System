import Badge from "./Badge.jsx";

export default function StatusBadge({ tone = "neutral", children, ...props }) {
  return <Badge tone={tone} {...props}>{children}</Badge>;
}
