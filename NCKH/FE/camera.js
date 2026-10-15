const API_BASE = "http://localhost:8080";

(() => {
  const $ = (id) => document.getElementById(id);

  const state = {
    stream: null,
    busy: false,
    streamingInterval: null,
    lastStreamTime: 0,
    isStreaming: false,
    lastRequestId: null
  };
  const graphReasonCache = new Map();
  const iotOpenState = { busy: false, nextAllowedAt: 0, attempted: new Set() };
  const IOT_OPEN_COOLDOWN_MS = 5000;

  const isCameraPage = document.body.dataset.page === "camera";
  const STREAM_INTERVAL = 70; // ms - minimum delay giữa mỗi request để tránh quá tải server

  const api = {
    openBin: () => `${API_BASE}/api/v1/iot/open`,
    upload: () => `${API_BASE}/api/v1/ai_request`,
    predict: (id) =>
      `${API_BASE}/api/v1/ai_requests/${encodeURIComponent(id)}/predict`,
    graphReason: (label) =>
      `${API_BASE}/api/v1/kg/reason?label=${encodeURIComponent(label)}`,
  };

  async function openFinalBin(data, requestId) {
    const detections = Array.isArray(data?.detections) ? data.detections : [];
    if (!detections.length || data.requiresConfirmation || detections.some(d =>
      d.needsConfirm || ["NEEDS_CONFIRM", "REJECTED"].includes(d.status) ||
      !/^BIN_0[123]$/.test(d.binCode || "")
    )) return;
    // Multiple objects may share a bin; never choose between conflicting bins.
    const bins = new Set(detections.map(d => d.binCode));
    if (bins.size !== 1 || requestId == null) return;
    const keys = detections.map((d, index) => `${requestId}:${d.id ?? index}`);
    if (iotOpenState.busy || performance.now() < iotOpenState.nextAllowedAt ||
        keys.some(key => iotOpenState.attempted.has(key))) return;
    const binCode = detections[0].binCode;
    iotOpenState.busy = true;
    // Record before sending: a lost HTTP response must not trigger an automatic retry.
    keys.forEach(key => iotOpenState.attempted.add(key));
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);
    try {
      const response = await fetch(api.openBin(), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ binCode }),
        signal: controller.signal
      });
      const result = await response.json().catch(() => null);
      if (!response.ok || result?.commandSent !== true ||
          result.binCode !== binCode || result.status !== "COMMAND_SENT") {
        throw new Error("Serial command was not confirmed");
      }
      toast(`Đã gửi lệnh mở thùng (${binCode})`, "success");
    } catch (error) {
      toast("Chưa xác nhận gửi được lệnh mở thùng; không tự gửi lại.", "error");
    } finally {
      clearTimeout(timeout);
      iotOpenState.nextAllowedAt = performance.now() + IOT_OPEN_COOLDOWN_MS;
      iotOpenState.busy = false;
    }
  }


  function toast(message, type = "info") {
    const item = document.createElement("div");
    item.className = `toast ${type}`;
    item.textContent = message;
    $("toastRegion")?.append(item);
    setTimeout(() => item.remove(), 3000);
  }


  function setCameraState(mode, text) {
    if ($("cameraStateDot")) $("cameraStateDot").className = mode;
    if ($("cameraStateText")) $("cameraStateText").textContent = text;
  }


  function setBusy(busy) {
    state.busy = busy;

    if ($("btnCapture")) {
      $("btnCapture").disabled = busy || !state.stream;
    }

    if (busy) {
      setCameraState("busy", "Đang phân tích...");
    }
  }


  function clearBoundingBox() {
    const canvas = $("photoCanvas");
    if (!canvas) return;

    const ctx = canvas.getContext("2d");
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    canvas.hidden = true;
  }


  function resetResult() {
    $("resultDone").hidden = true;
    $("resultIdle").hidden = false;
    $("resultStatusText").textContent = "Đang chờ nhận diện...";
    if ($("guideDataText")) $("guideDataText").textContent = "Chưa có dữ liệu.";
    clearBoundingBox();
  }


  function mapBin(label) {
    const text = String(label || "").toLowerCase();

    if (
      /(hữu cơ|huu co|thức ăn|rau|lá cây|thuc an)/.test(text)
    ) {
      return {
        name: "Hữu cơ",
        key: "organic"
      };
    }

    if (
      /(tái chế|tai che|recycl|nhựa|nhua|giấy|giay|kim loại|kim loai|thủy tinh|thuy tinh|lon|carton|plastic|paper|metal|glass)/.test(text)
    ) {
      return {
        name: "Tái chế",
        key: "recycle"
      };
    }

    return {
      name: "Còn lại",
      key: "other"
    };
  }


  // Backend trả Knowledge Graph trực tiếp trong detection sau khi predict.
  function receiveKnowledgeGraphData(data, detection) {
    if (!detection) {
      window.dispatchEvent(new CustomEvent("kgdata", {
        detail: { response: data, detection: null, knowledgeGraph: null }
      }));
      return {
        bin: null,
        summary: ""
      };
    }

    const kg =
      detection.knowledgeGraph ||
      detection.trashKnowledge ||
      detection.kg ||
      detection;

    const detail = kg.detail || {};

    const binByCode = {
      BIN_01: {
        name: "Thùng rác tái chế",
        key: "recycle"
      },

      BIN_02: {
        name: "Thùng rác hữu cơ",
        key: "organic"
      },

      BIN_03: {
        name: "Thùng rác khác",
        key: "other"
      },
    };

    const bin =
      binByCode[kg.binCode] ||
      (
        kg.binName
          ? {
              name: kg.binName,
              key: mapBin(kg.binName).key
            }
          : null
      );

    const safeSteps =
      Array.isArray(detail.safeSteps)
        ? detail.safeSteps
        : [];

    const graphSteps =
      Array.isArray(detail.trashSteps)
        ? detail.trashSteps
            .map(
              (step) =>
                step.labelDisplay || step.label
            )
            .filter(Boolean)
        : [];

    const summary = [
      kg.trashType &&
        `Loại rác: ${kg.trashType}`,

      kg.material &&
        `Chất liệu: ${kg.material}`,

      bin &&
        `Thùng KG đề xuất: ${bin.name}`,

      kg.action &&
        `Hướng xử lý: ${kg.action}`,

      kg.note &&
        `Lưu ý: ${kg.note}`,

      detail.impact &&
        `Tác động: ${detail.impact}`,

      detail.toxicity &&
        `Độc tính: ${detail.toxicity}`,

      [...safeSteps, ...graphSteps].length &&
        `Bước an toàn: ${[
          ...safeSteps,
          ...graphSteps
        ].join("; ")}`,

    ]
      .filter(Boolean)
      .join(". ");


    window.dispatchEvent(
      new CustomEvent("kgdata", {
        detail: {
          response: data,
          detection,
          knowledgeGraph: kg,
          bin
        }
      })
    );

    return {
      bin,
      summary
    };
  }


  async function receiveGraphProcessingSteps(data, detection, requestId) {
    if (!detection?.label) return;

    const initialGraph =
      detection.knowledgeGraph ||
      detection.trashKnowledge ||
      detection.kg ||
      detection;
    const existingDetail = initialGraph.detail || {};
    const alreadyHasSteps =
      (Array.isArray(initialGraph.guidanceSteps) && initialGraph.guidanceSteps.length > 0) ||
      (Array.isArray(existingDetail.trashSteps) && existingDetail.trashSteps.length > 0);
    if (alreadyHasSteps) return;

    const cacheKey = String(detection.label).trim().toLowerCase();
    let request = graphReasonCache.get(cacheKey);
    if (!request) {
      request = fetch(api.graphReason(detection.label))
        .then(async (response) => {
          if (!response.ok) throw new Error(`Graph request failed: ${response.status}`);
          return response.json();
        });
      graphReasonCache.set(cacheKey, request);
      request.catch(() => {
        if (graphReasonCache.get(cacheKey) === request) graphReasonCache.delete(cacheKey);
      });
    }

    try {
      const graphFromApi = await request;
      if (requestId !== state.lastRequestId || !graphFromApi || typeof graphFromApi !== "object") return;

      const graph = { ...initialGraph, ...graphFromApi };
      window.dispatchEvent(new CustomEvent("kgdata", {
        detail: { response: data, detection, knowledgeGraph: graph }
      }));
    } catch (error) {
      console.warn("Could not load Graph processing steps", error);
    }
  }


  function lightBin(binKey) {
    document
      .querySelectorAll(".bin-card")
      .forEach((card) =>
        card.classList.toggle(
          "is-lit",
          card.dataset.bin === binKey
        )
      );
  }


  // --- HÀM VẼ BOUNDING BOX TỰ ĐỘNG CHUẨN HÓA TỌA ĐỘ LÊN LIVE CAMERA ---

  function drawBoundingBoxes(detections) {
    const video = $("camVideo");
    const canvas = $("photoCanvas");

    if (!canvas || !video) return;

    const ctx = canvas.getContext("2d");

    // Lấy kích thước thực tế hiển thị trên CSS
    const displayWidth =
      video.clientWidth || 640;

    const displayHeight =
      video.clientHeight || 480;

    // Lấy kích thước nguồn Video gốc
    const sourceWidth =
      video.videoWidth || displayWidth;

    const sourceHeight =
      video.videoHeight || displayHeight;

    canvas.width = displayWidth;
    canvas.height = displayHeight;

    ctx.clearRect(
      0,
      0,
      canvas.width,
      canvas.height
    );

    if (
      !Array.isArray(detections) ||
      detections.length === 0
    ) {
      canvas.hidden = true;
      return;
    }

    const scaleX =
      displayWidth / sourceWidth;

    const scaleY =
      displayHeight / sourceHeight;


    detections.forEach((det) => {
      let rawX,
        rawY,
        rawW,
        rawH;


      // Trích xuất tọa độ Bounding Box linh hoạt từ backend

      if (
        det.x1 !== undefined &&
        det.y1 !== undefined &&
        det.x2 !== undefined &&
        det.y2 !== undefined
      ) {

        // Format thực tế backend trả về: tọa độ phẳng ngay trên object detection

        rawX = Number(det.x1);
        rawY = Number(det.y1);

        rawW =
          Number(det.x2) - rawX;

        rawH =
          Number(det.y2) - rawY;


      } else if (
        Array.isArray(det.bbox)
      ) {

        [rawX, rawY, rawW, rawH] =
          det.bbox;

        if (
          rawW > rawX &&
          rawH > rawY &&
          det.bbox.length === 4
        ) {
          rawW =
            rawW - rawX;

          rawH =
            rawH - rawY;
        }


      } else if (det.box) {

        rawX =
          det.box.xmin ??
          det.box.x1 ??
          det.box.x;

        rawY =
          det.box.ymin ??
          det.box.y1 ??
          det.box.y;

        const xMax =
          det.box.xmax ??
          det.box.x2;

        const yMax =
          det.box.ymax ??
          det.box.y2;

        rawW =
          xMax !== undefined
            ? xMax - rawX
            : det.box.w;

        rawH =
          yMax !== undefined
            ? yMax - rawY
            : det.box.h;
      }


      if (
        rawX !== undefined &&
        rawY !== undefined &&
        rawW !== undefined &&
        rawH !== undefined
      ) {

        // Tự động scale nếu AI trả về tọa độ tỉ lệ normalized (0 -> 1)

        if (
          rawX <= 1 &&
          rawY <= 1 &&
          rawW <= 1 &&
          rawH <= 1
        ) {
          rawX *= sourceWidth;
          rawY *= sourceHeight;
          rawW *= sourceWidth;
          rawH *= sourceHeight;
        }


        // Quy đổi sang khung hiển thị thực tế

        const x =
          rawX * scaleX;

        const y =
          rawY * scaleY;

        const w =
          rawW * scaleX;

        const h =
          rawH * scaleY;


        const label =
          det.label ||
          "—";

        const confScore =
          Number(
            det.confidence ??
            det.yoloConfidence ??
            0
          );

        const confText =
          confScore
            ? `${(
                confScore * 100
              ).toFixed(1)}%`
            : "";


        // 1. Vẽ khung chữ nhật Bounding Box

        ctx.strokeStyle = "#dee9e2";
        ctx.lineWidth = 3;

        ctx.strokeRect(
          x,
          y,
          w,
          h
        );


        // 2. Vẽ Nhãn tên + Tỉ lệ %

        const labelText =
          `${label} ${confText}`.trim();

        const fontSize = 14;

        ctx.font =
          `bold ${fontSize}px sans-serif`;

        const textWidth =
          ctx.measureText(
            labelText
          ).width;


        ctx.save();

        // Lật chữ lại đúng chiều do CSS Canvas có transform: scaleX(-1)

        const backgroundY =
          (
            y - fontSize - 8 > 0
          )
            ? y - fontSize - 8
            : y;

        ctx.translate(
          x + textWidth + 10,
          backgroundY
        );

        ctx.scale(-1, 1);


        ctx.fillStyle = "#00FF66";

        ctx.fillRect(
          0,
          0,
          textWidth + 10,
          fontSize + 6
        );


        ctx.fillStyle = "#000000";

        ctx.fillText(
          labelText,
          5,
          fontSize
        );

        ctx.restore();
      }
    });

    canvas.hidden = false;
  }


  function showResult(data, requestId) {
    // Leave loading/placeholder state as soon as the BE response is rendered.
    $("resultIdle").hidden = true;
    $("resultDone").hidden = false;

    const detections =
      Array.isArray(data?.detections)
        ? data.detections
        : [];


    const best =
      detections.reduce(
        (winner, item) => {

          const currentConf =
            Number(
              item.confidence ??
              item.yoloConfidence ??
              0
            );

          const winnerConf =
            Number(
              winner?.confidence ??
              winner?.yoloConfidence ??
              0
            );

          return !winner ||
            currentConf > winnerConf
            ? item
            : winner;

        },
        null
      );


    const label =
      best?.label ||
      "Không nhận diện được";

    const confidence =
      best?.confidence ??
      best?.yoloConfidence;

    const knowledgeGraph =
      receiveKnowledgeGraphData(
        data,
        best
      );

    void receiveGraphProcessingSteps(data, best, requestId);

    const bin =
      knowledgeGraph.bin ||
      mapBin(label);


    // Vẽ Bounding Box trực tiếp lên Live Camera

    drawBoundingBoxes(
      detections
    );


    // Cập nhật khung thông tin bên phải

    $("resultLabel").textContent =
      label;

    $("resultBinHint").textContent =
      `✓ Nên bỏ vào thùng ${bin.name}`;

    $("resultConfidence").textContent =
      Number.isFinite(
        Number(confidence)
      )
        ? `${(
            Number(confidence) * 100
          ).toFixed(1)}%`
        : "Chưa có dữ liệu";


    if ($("guideDataText")) {
      $("guideDataText").textContent = [
        `AI xác định đây là “${label}”. Vui lòng bỏ vào thùng ${bin.name}.`,
        knowledgeGraph.summary
      ]
        .filter(Boolean)
        .join(" ");
    }


    lightBin(bin.key);


    const link =
      $("resultDetailLink");

    if (link) {
      link.href =
        `result_analys.html?id=${encodeURIComponent(
          requestId
        )}`;

      link.hidden = false;
    }
  }


  async function startCamera() {

    if ($("welcomeView"))
      $("welcomeView").hidden = true;

    if ($("cameraView"))
      $("cameraView").hidden = false;


    try {

      state.stream =
        await navigator.mediaDevices.getUserMedia({
          video: {
            facingMode: {
              ideal: "environment"
            }
          },
          audio: false
        });


      const video =
        $("camVideo");

      video.srcObject =
        state.stream;


      await new Promise(
        (resolve) =>
          video.addEventListener(
            "playing",
            resolve,
            { once: true }
          )
      );


      if ($("cameraIdleState")) {

        $("cameraIdleState").style.display =
          "none";

        $("cameraIdleState").hidden =
          true;
      }


      if ($("btnCapture"))
        $("btnCapture").disabled = false;

      setCameraState(
        "ready",
        "Camera đang bật"
      );

      resetResult();


    } catch (error) {

      console.error(error);

      if ($("cameraIdleState")) {

        $("cameraIdleState").style.display =
          "grid";

        $("cameraIdleState").hidden =
          false;
      }

      setCameraState(
        "",
        "Không thể mở camera"
      );

      toast(
        "Không thể mở camera. Hãy cấp quyền camera và dùng HTTPS hoặc localhost.",
        "error"
      );
    }
  }


  function stopCamera() {

    stopStreaming();

    state.stream
      ?.getTracks()
      .forEach(
        (track) => track.stop()
      );

    state.stream = null;


    if ($("camVideo"))
      $("camVideo").srcObject = null;

    window.location.href =
      "camera-trangchu.html";
  }


  function captureFrame() {

    const video =
      $("camVideo");

    const canvas =
      document.createElement(
        "canvas"
      );

    canvas.width =
      video.videoWidth || 640;

    canvas.height =
      video.videoHeight || 480;


    const ctx =
      canvas.getContext("2d");

    ctx.drawImage(
      video,
      0,
      0,
      canvas.width,
      canvas.height
    );


    return new Promise(
      (resolve) => {

        canvas.toBlob(
          (blob) =>
            resolve(blob),
          "image/jpeg",
          0.9
        );

      }
    );
  }


  // --- STREAM FRAME LIÊN TỤC ĐỂ DETECT REAL-TIME (CHAINING - NHƯ CŨ NHẤT) ---

  async function streamFrames() {

    if (
      !state.stream ||
      !state.isStreaming
    ) {
      return;
    }


    try {

      const imageBlob =
        await captureFrame();


      if (!imageBlob) {

        if (state.isStreaming) {

          setTimeout(
            () => streamFrames(),
            STREAM_INTERVAL
          );
        }

        return;
      }


      const form =
        new FormData();

      form.append(
        "file",
        new File(
          [imageBlob],
          `stream-${Date.now()}.jpg`,
          {
            type: "image/jpeg"
          }
        )
      );

      const headers = {};


      const uploadResponse =
        await fetch(
          api.upload(),
          {
            method: "POST",
            headers,
            body: form
          }
        );


      const uploadJson =
        await uploadResponse
          .json()
          .catch(() => null);


      if (!uploadResponse.ok) {

        if (state.isStreaming) {

          setTimeout(
            () => streamFrames(),
            STREAM_INTERVAL
          );
        }

        return;
      }


      const upload =
        uploadJson?.data ??
        uploadJson;


      if (
        !upload?.id ||
        !upload?.cloudinaryUrl
      ) {

        if (state.isStreaming) {

          setTimeout(
            () => streamFrames(),
            STREAM_INTERVAL
          );
        }

        return;
      }


      state.lastRequestId =
        upload.id;


      const predictForm =
        new FormData();


      predictForm.append(
        "file",
        new File(
          [imageBlob],
          `stream-predict-${Date.now()}.jpg`,
          {
            type: "image/jpeg"
          }
        )
      );


      predictForm.append(
        "conf",
        "0.25"
      );

      predictForm.append(
        "iou",
        "0.6"
      );


      const predictResponse =
        await fetch(
          api.predict(upload.id),
          {
            method: "POST",
            body: predictForm
          }
        );


      const predictJson =
        await predictResponse
          .json()
          .catch(() => null);


      if (!predictResponse.ok) {

        if (state.isStreaming) {

          setTimeout(
            () => streamFrames(),
            STREAM_INTERVAL
          );
        }

        return;
      }


      const resultData =
        predictJson?.data ??
        predictJson;


      // Vẽ Bounding Box ngay lập tức
      void openFinalBin(resultData, upload.id);

      if (resultData?.detections) {

        drawBoundingBoxes(
          resultData.detections
        );
      }


      // Nếu chưa có kết quả nào, hiển thị trạng thái "Đang chờ..."

      if (
        !(
          $("resultDone")?.hidden ===
          false
        )
      ) {

        if (
          !(
            $("resultIdle")?.hidden ===
            false
          )
        ) {

          $("resultStatusText").textContent =
            "Đang nhận diện live...";
        }
      }


    } catch (error) {

      console.error(
        "Stream error:",
        error
      );

    } finally {

      // Ngay sau khi xong, gửi frame tiếp theo luôn (chaining) - nhanh nhất có thể

      if (state.isStreaming) {

        setTimeout(
          () => streamFrames(),
          STREAM_INTERVAL
        );
      }
    }
  }


  function startStreaming() {

    if (state.isStreaming)
      return;

    state.isStreaming = true;

    $("resultStatusText").textContent =
      "Đang nhận diện live...";

    streamFrames();
  }


  function stopStreaming() {

    state.isStreaming = false;

    if (state.streamingInterval) {

      clearTimeout(
        state.streamingInterval
      );

      state.streamingInterval = null;
    }
  }


  // --- CHỤP ẢNH CHÍNH THỨC & LƯU VÀO LỊCH SỬ ---

  async function captureAndSave() {

    if (
      !state.stream ||
      state.busy
    ) {
      return;
    }


    setBusy(true);

    $("resultDone").hidden =
      true;

    $("resultIdle").hidden =
      false;

    $("resultStatusText").textContent =
      "Đang lưu ảnh...";


    try {

      const imageBlob =
        await captureFrame();


      if (!imageBlob) {

        toast(
          "Không thể chụp ảnh. Vui lòng thử lại.",
          "error"
        );

        return;
      }


      const form =
        new FormData();


      form.append(
        "file",
        new File(
          [imageBlob],
          `capture-${Date.now()}.jpg`,
          {
            type: "image/jpeg"
          }
        )
      );


      const headers = {};


      const uploadResponse =
        await fetch(
          api.upload(),
          {
            method: "POST",
            headers,
            body: form
          }
        );


      const uploadJson =
        await uploadResponse
          .json()
          .catch(() => null);


      if (!uploadResponse.ok)
        throw new Error(
          uploadJson?.message ||
          "Tải ảnh lên thất bại."
        );


      const upload =
        uploadJson?.data ??
        uploadJson;


      if (
        !upload?.id ||
        !upload?.cloudinaryUrl
      ) {
        throw new Error(
          "Máy chủ không trả về thông tin ảnh."
        );
      }


      state.lastRequestId =
        upload.id;


      const predictForm =
        new FormData();


      predictForm.append(
        "file",
        new File(
          [imageBlob],
          `capture-predict-${Date.now()}.jpg`,
          {
            type: "image/jpeg"
          }
        )
      );


      predictForm.append(
        "conf",
        "0.25"
      );

      predictForm.append(
        "iou",
        "0.6"
      );


      const predictResponse =
        await fetch(
          api.predict(upload.id),
          {
            method: "POST",
            body: predictForm
          }
        );


      const predictJson =
        await predictResponse
          .json()
          .catch(() => null);


      if (!predictResponse.ok) {

        throw new Error(
          predictJson?.message ||
          predictJson?.error ||
          "AI nhận diện thất bại."
        );
      }


      const resultData =
        predictJson?.data ??
        predictJson;


      console.log(
        "=== CAPTURE RESULT (SAVED) ===",
        resultData
      );


      // Hiển thị kết quả chính thức
      void openFinalBin(resultData, upload.id);

      showResult(
        resultData,
        upload.id
      );

      setCameraState(
        "ready",
        "Camera sẵn sàng"
      );

      toast(
        "Ảnh đã lưu vào lịch sử!",
        "success"
      );


    } catch (error) {

      console.error(error);

      window.dispatchEvent(new CustomEvent("kgdata", {
        detail: {
          detection: null,
          knowledgeGraph: null,
          error: true
        }
      }));

      toast(
        error.message ||
        "Không thể kết nối máy chủ.",
        "error"
      );

      $("resultStatusText").textContent =
        "Chưa thể nhận diện. Hãy thử lại.";

      setCameraState(
        "ready",
        state.stream
          ? "Camera sẵn sàng"
          : "Camera đã tắt"
      );

    } finally {

      setBusy(false);
    }
  }


  if ($("btnStartCamera")) {

    $("btnStartCamera").addEventListener(
      "click",
      () => {
        window.location.href =
          "camera-hoatdong.html";
      }
    );
  }


  $("btnStopCamera")
    ?.addEventListener(
      "click",
      stopCamera
    );


  // Nút Capture lưu ảnh vào lịch sử (thay vì chỉ nhận diện tạm)

  $("btnCapture")
    ?.addEventListener(
      "click",
      captureAndSave
    );


  window.addEventListener(
    "beforeunload",
    () => {

      stopStreaming();

      state.stream
        ?.getTracks()
        .forEach(
          (track) =>
            track.stop()
        );
    }
  );


  if (isCameraPage)
    startCamera();

})();

