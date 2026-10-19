export default function Card({ className = "", children, ...props }) {
  return (
    <div className={`glass rounded-2xl shadow-card ${className}`} {...props}>
      {children}
    </div>
  );
}


