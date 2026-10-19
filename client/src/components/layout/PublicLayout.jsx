import { Outlet, useLocation } from "react-router-dom";
import Navbar from "./NavBar.jsx";
import Footer from "./Footer.jsx";

export default function PublicLayout() {
  const { pathname } = useLocation();

  return (
    <div className="min-h-screen flex flex-col overflow-x-hidden bg-[var(--bg-subtle)] text-ink ambient">
      <Navbar />
      <main className="flex-1">
        <div key={pathname} className="public-page-enter">
          <Outlet />
        </div>
      </main>
      <Footer />
    </div>
  );
}
