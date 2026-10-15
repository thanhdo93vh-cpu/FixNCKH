(() => {
  const status = document.getElementById("graphDetailStatus");
  const fields = document.getElementById("graphDetailFields");
  let graph = null;

  try {
    graph = JSON.parse(sessionStorage.getItem("hauiTrash.graphDetail") || "null")?.graph ?? null;
  } catch (_) {
    graph = null;
  }

  if (!graph || typeof graph !== "object") return;

  const hasValue = (value) => {
    if (Array.isArray(value)) return value.some(hasValue);
    if (value && typeof value === "object") return Object.values(value).some(hasValue);
    return value !== null && value !== undefined && String(value).trim() !== "";
  };
  const firstValue = (...values) => values.find(hasValue);
  const binName = (value) => {
    if (typeof value !== "string") return value;
    const names = {
      BIN_01: "Thùng rác tái chế",
      BIN_02: "Thùng rác hữu cơ",
      BIN_03: "Thùng rác còn lại"
    };
    return names[value.trim().toUpperCase()] || value;
  };
  const formatValue = (value) => {
    if (Array.isArray(value)) {
      return value.map((item) => {
        if (!item || typeof item !== "object") return String(item);
        return firstValue(item.labelDisplay, item.label, ...Object.values(item)) ?? "";
      }).filter(Boolean).join(", ");
    }
    return value === undefined || value === null ? "" : String(value);
  };
  const combineValues = (...values) => values
    .filter(hasValue)
    .map(formatValue)
    .filter(Boolean)
    .filter((value, index, all) => all.indexOf(value) === index)
    .join("; ");
  const formatSteps = (...values) => values
    .flatMap((value) => Array.isArray(value) ? value : hasValue(value) ? [value] : [])
    .map(formatValue)
    .map((step) => step.trim())
    .filter(Boolean)
    .filter((step, index, all) => all.indexOf(step) === index)
    .map((step, index) => /^bước\s*\d+/i.test(step) ? step : `Bước ${index + 1}: ${step}`)
    .join("\n");

  const detail = graph.detail || {};
  const groups = [
    ["Loại rác", formatValue(firstValue(graph.trashTypeCode, graph.trashTypeName, graph.trashType))],
    ["Chất liệu", formatValue(firstValue(graph.materials, graph.material))],
    ["Thùng phù hợp", formatValue(binName(firstValue(graph.trashBinCode, graph.binCode, graph.trashBinName, graph.binName)))],
    ["Các bước xử lý", formatSteps(graph.guidanceSteps, detail.trashSteps)],
    ["Xử lý", combineValues(graph.guidance, graph.guidanceDescription, graph.action)],
    ["Lưu ý", combineValues(graph.guidanceLuY, graph.note)],
    ["Tác động", combineValues(graph.impacts, graph.impactDescriptions, detail.impact)],
    ["Mối nguy", combineValues(graph.hazards, graph.hazardDescriptions, detail.hazards, detail.hazardDescriptions, detail.toxicity)]
  ];

  fields.replaceChildren();
  for (const [label, value] of groups) {
    const term = document.createElement("dt");
    const description = document.createElement("dd");
    term.textContent = label;
    if (label === "Các bước xử lý") description.className = "processing-steps-value";
    description.textContent = value || "—";
    fields.append(term, description);
  }

  status.hidden = true;
  fields.hidden = false;
})();
