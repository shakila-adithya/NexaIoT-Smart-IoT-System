export default function PageHero({
  eyebrow,
  title,
  description,
  image,
  imageAlt,
  children,
}) {
  return (
    <section className="relative min-h-[150px] overflow-hidden rounded-[16px] bg-[#0aa6bd]">
      <div className="relative z-10 flex min-h-[150px] items-center px-7 py-6 lg:w-[65%]">
        <div>
          {eyebrow ? (
            <div className="flex items-center gap-2 text-[11px] font-semibold text-white">
              <span className="h-2 w-2 rounded-full bg-[#8cf0cf]" />
              {eyebrow}
            </div>
          ) : null}

          <h2 className="mt-3 text-[20px] font-semibold leading-tight text-white">
            {title}
          </h2>

          {description ? (
            <p className="mt-2 max-w-[560px] text-[11px] leading-[18px] text-white/85">
              {description}
            </p>
          ) : null}

          {children ? <div className="mt-4">{children}</div> : null}
        </div>
      </div>

      {image ? (
        <div className="absolute inset-y-0 right-0 hidden w-[43%] lg:block">
          <img
            src={image}
            alt={imageAlt || ""}
            className="h-full w-full object-cover"
          />

          <div className="absolute inset-0 bg-gradient-to-r from-[#0aa6bd] via-[#0aa6bd]/25 to-transparent" />
        </div>
      ) : null}
    </section>
  );
}