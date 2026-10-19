import { Link } from "react-router-dom";
import { Satellite } from "lucide-react";

export default function AuthShell({
  image,
  imageAlt = "",
  title,
  description,
  visualContent,
  formContent,
  ghost = "Sign in",
  headline,
  blurb,
  imageTone = "dark",
}) {
  return (
    <div className={`auth-shell auth-shell-${imageTone} min-h-screen p-3 sm:p-4 bg-[var(--bg-subtle)]`}>
      <div className="frame grid min-h-[calc(100svh-1.5rem)] sm:min-h-[calc(100svh-2rem)] lg:grid-cols-[1.1fr_0.9fr]">
        <img className="frame-photo" src={image} alt={imageAlt} aria-hidden="true" />
        <div className="frame-grade" />

        <aside className="relative hidden flex-col justify-between p-10 text-white lg:flex">
          <Link to="/" className="relative z-10 inline-flex items-center gap-2.5 text-lg font-semibold text-white" aria-label="NexaIoT home">
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-gradient-to-br from-[#5EE0EE] to-[#14A0B6] text-[#04202c] shadow-[0_6px_18px_-6px_rgb(34_199_218)]">
              <Satellite size={16} />
            </span>
            NexaIoT
          </Link>
          <span aria-hidden="true" className="ghost-word enter-ghost absolute left-8 top-[26%] text-[clamp(5rem,13vw,12rem)]">{ghost}</span>
          <div className="relative z-10 enter-rise enter-d2 max-w-sm">
            <p className="text-3xl font-semibold leading-tight">{headline || title}</p>
            <p className="mt-3 text-white/70">{blurb || description}</p>
            {visualContent}
          </div>
        </aside>

        <main className="relative flex items-center justify-center p-5 sm:p-10">
          <div className="dark glass-dark w-full max-w-md rounded-3xl bg-[#06141f]/70 p-7 sm:p-9 text-white">
            <Link to="/" className="mb-8 inline-flex items-center gap-2.5 text-lg font-semibold text-white lg:hidden" aria-label="NexaIoT home">
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-gradient-to-br from-[#5EE0EE] to-[#14A0B6] text-[#04202c]"><Satellite size={16} /></span>
              NexaIoT
            </Link>
            {formContent}
          </div>
        </main>
      </div>
    </div>
  );
}
