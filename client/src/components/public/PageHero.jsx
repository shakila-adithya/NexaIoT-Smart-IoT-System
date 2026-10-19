/* Framed photo hero with a giant frosted word — the shared page opener. */
export default function PageHero({ image, imageAlt = "", ghost, title, text, children, aside, compact = false }) {
  return (
    <section className="p-3 sm:p-4">
      <div className={`frame ${compact ? "min-h-[380px]" : "min-h-[560px]"} flex flex-col justify-end`}>
        {image && <img className="frame-photo" src={image} alt={imageAlt} fetchPriority="high" />}
        {!image && <div className="absolute inset-0 -z-[3] bg-[radial-gradient(70%_80%_at_80%_0%,rgba(34,199,218,0.3),transparent_60%),linear-gradient(180deg,#0f3045,#07131e)]" />}
        <div className="frame-grade" />

        <span
          aria-hidden="true"
          className="page-hero-ghost ghost-word enter-ghost absolute inset-x-4 top-[17%] text-[clamp(3.5rem,9vw,15rem)]"
        >
          {ghost}
        </span>

        <div className="relative z-10 mx-auto flex w-full max-w-7xl flex-col gap-8 px-6 pb-10 pt-40 text-white sm:px-10 lg:flex-row lg:items-end lg:justify-between">
          <div className="max-w-xl enter-rise enter-d1">
            <h1 className="text-3xl font-semibold leading-tight sm:text-4xl lg:text-5xl">{title}</h1>
            {text && <p className="mt-4 max-w-lg text-base leading-relaxed text-white/75 sm:text-lg">{text}</p>}
            {children && <div className="mt-7 flex flex-wrap items-center gap-3">{children}</div>}
          </div>
          {aside && <div className="enter-rise enter-d2 lg:w-72">{aside}</div>}
        </div>
      </div>
    </section>
  );
}


