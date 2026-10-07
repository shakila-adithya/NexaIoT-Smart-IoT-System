import {
  CAPABILITY_GROUPS,
  normalizeCapabilities,
} from "../../config/deviceCapabilities.js";

export default function CapabilitySelector({ capabilities, onChange }) {
  const selectedCapabilities = normalizeCapabilities(capabilities);
  const selectedCount = Object.values(selectedCapabilities).reduce(
    (total, values) => total + values.length,
    0,
  );

  const toggleCapability = (groupKey, value) => {
    const currentValues = selectedCapabilities[groupKey];
    const nextValues = currentValues.includes(value)
      ? currentValues.filter((item) => item !== value)
      : [...currentValues, value];

    onChange({
      ...selectedCapabilities,
      [groupKey]: nextValues,
    });
  };

  return (
    <section className="rounded-[14px] border border-[#dce8ee] bg-white p-5">
      <div>
        <h2 className="text-[15px] font-semibold text-[#102a3a]">
          Device capabilities
        </h2>
        <p className="mt-1 text-[10px] text-[#6b8290]">
          Select the measurements and controls supported by this device.
        </p>
      </div>

      <div className="mt-5 space-y-5">
        {CAPABILITY_GROUPS.map((group) => (
          <fieldset key={group.key}>
            <legend className="text-[10px] font-semibold uppercase tracking-[0.08em] text-[#6b8290]">
              {group.label}
            </legend>
            <div className="mt-2 grid gap-2 sm:grid-cols-2">
              {group.options.map(([value, label]) => {
                const selected = selectedCapabilities[group.key].includes(value);

                return (
                  <label
                    key={value}
                    className={`flex cursor-pointer items-center gap-2.5 rounded-[10px] border px-3 py-2.5 transition ${
                      selected
                        ? "border-[#8bd8e3] bg-[#eef9fb]"
                        : "border-[#dce8ee] bg-[#f8fbfd] hover:border-[#b9d7df]"
                    }`}
                  >
                    <input
                      type="checkbox"
                      checked={selected}
                      onChange={() => toggleCapability(group.key, value)}
                      className="h-3.5 w-3.5 accent-[#08a9c4]"
                    />
                    <span className="text-[10px] font-medium text-[#102a3a]">
                      {label}
                    </span>
                  </label>
                );
              })}
            </div>
          </fieldset>
        ))}
      </div>

      <p className="mt-4 text-[9px] text-[#8397a2]">
        {selectedCount} {selectedCount === 1 ? "capability" : "capabilities"} selected
      </p>
    </section>
  );
}
