function formatLabel(value) {
  return value
    .toLowerCase()
    .replaceAll("_", " ")
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export default function TemplateCapabilityPreview({ template }) {
  const capabilities = template?.capabilityManifest?.capabilities || [];

  return (
    <div className="mt-4 rounded-[12px] border border-[#dce8ee] bg-[#f8fbfd] p-3.5">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-[11px] font-semibold text-[#102a3a]">
            Supported capabilities
          </p>
          <p className="mt-0.5 text-[9px] text-[#6b8290]">
            Preview from the selected backend template.
          </p>
        </div>
        <span className="rounded-full bg-[#e7f7f9] px-2 py-1 text-[9px] font-medium text-[#087b9c]">
          {capabilities.length} {capabilities.length === 1 ? "capability" : "capabilities"}
        </span>
      </div>

      {capabilities.length > 0 ? (
        <div className="mt-3 grid gap-2 sm:grid-cols-2">
          {capabilities.map((capability) => (
            <div
              key={capability.key}
              className="rounded-[9px] border border-[#dce8ee] bg-white px-3 py-2.5"
            >
              <p className="text-[10px] font-semibold text-[#102a3a]">
                {capability.name}
              </p>
              <p className="mt-1 text-[9px] text-[#6b8290]">
                {formatLabel(capability.category)}
                {capability.control?.type
                  ? ` · ${formatLabel(capability.control.type)}`
                  : ""}
                {capability.unit ? ` · ${capability.unit}` : ""}
              </p>
            </div>
          ))}
        </div>
      ) : (
        <p className="mt-3 text-[10px] text-[#6b8290]">
          This template does not declare any capabilities.
        </p>
      )}
    </div>
  );
}
