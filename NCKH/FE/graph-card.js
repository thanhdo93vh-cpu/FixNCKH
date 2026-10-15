(() => {
  const card = document.getElementById("knowledgeGraphData");
  if (!card) return;

  const emptyState = document.getElementById("kgEmptyState");
  const statusTitle = document.getElementById("kgStatusTitle");
  const statusMessage = document.getElementById("kgStatusMessage");
  const summary = document.getElementById("kgSummary");
  const detailLink = document.getElementById("kgDetailLink");

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

  window.addEventListener("kgdata", (event) => {
    const { detection, knowledgeGraph, error } = event.detail || {};
    const graph = knowledgeGraph && typeof knowledgeGraph === "object"
      ? knowledgeGraph
      : detection && typeof detection === "object"
        ? detection
        : null;
    const detail = graph?.detail || {};

    // Show the compact, user-facing parts of the Graph already present in the BE payload.
    const values = [
      ["Loại rác", firstValue(graph?.trashTypeCode, graph?.trashTypeName, graph?.trashType)],
      ["Chất liệu", firstValue(graph?.materials, graph?.material)],
      ["Thùng phù hợp", binName(firstValue(graph?.trashBinCode, graph?.binCode, graph?.trashBinName, graph?.binName))],
      ["Các bước xử lý", formatSteps(graph?.guidanceSteps, detail.trashSteps)],
      ["Xử lý", combineValues(graph?.guidance, graph?.guidanceDescription, graph?.action)],
      ["Lưu ý", combineValues(graph?.guidanceLuY, graph?.note)],
      ["Tác động", combineValues(graph?.impacts, graph?.impactDescriptions, detail.impact)],
      ["Mối nguy", combineValues(graph?.hazards, graph?.hazardDescriptions, detail.hazards, detail.hazardDescriptions, detail.toxicity)]
    ];
    const hasGraphData = values.some(([, value]) => hasValue(value)) || [
      graph?.guidance, graph?.guidanceDescription, graph?.guidanceSteps, graph?.guidanceLuY,
      graph?.action, graph?.note, graph?.impacts, graph?.impactDescriptions,
      graph?.hazards, graph?.hazardDescriptions, detail.impact, detail.hazards,
      detail.hazardDescriptions, detail.toxicity, detail.trashSteps
    ].some(hasValue);

    summary.replaceChildren();
    for (const [label, value] of values) {
      if (!hasValue(value)) continue;
      const row = document.createElement("div");
      const term = document.createElement("dt");
      const description = document.createElement("dd");
      term.textContent = label;
      if (label === "Các bước xử lý") description.className = "processing-steps-value";
      description.textContent = formatValue(value);
      row.append(term, description);
      summary.append(row);
    }
    summary.hidden = !hasGraphData;

    if (!hasGraphData) {
      emptyState.hidden = false;
      statusTitle.textContent = "Chưa có dữ liệu";
      statusMessage.textContent = error
        ? "Không thể hiển thị tri thức phân loại cho kết quả này."
        : detection
          ? "Không tìm thấy tri thức phân loại cho vật phẩm này."
          : "Đưa rác vào trước camera, chụp ảnh để xem tri thức phân loại.";
      detailLink.hidden = true;
      card.hidden = false;
      sessionStorage.removeItem("hauiTrash.graphDetail");
      return;
    }

    emptyState.hidden = true;
    card.hidden = false;

    try {
      sessionStorage.setItem("hauiTrash.graphDetail", JSON.stringify({ graph }));
      detailLink.href = "graph-detail.html";
      detailLink.hidden = false;
    } catch (_) {
      detailLink.hidden = true;
      emptyState.hidden = false;
      statusTitle.textContent = "Tri thức đã nhận được";
      statusMessage.textContent = "Không thể lưu dữ liệu để mở trang chi tiết trong phiên này.";
    }
  });
})();
