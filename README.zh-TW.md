<div align="center">

<img src="assets/opendesqueeze-banner.svg" width="780" alt="OpenDeSqueeze — Android 本機變形寬銀幕 Desqueeze">

# OpenDeSqueeze

**Android 本機批次 Anamorphic Desqueeze：照片與影片去擠壓校正工具。**

[English](README.md) · [架構](docs/ARCHITECTURE.md) · [建置](BUILDING.md) · [貢獻指南](CONTRIBUTING.md)

</div>

OpenDeSqueeze 是一個用途非常集中的 Android 小工具：把變形寬銀幕鏡頭拍出的 squeezed 照片與影片，在手機上直接做幾何校正。

工作流刻意保持簡單：

```text
選照片 / 影片
      ↓
選 1.33× / 1.55× / 1.8× / 2.0× / 自訂
      ↓
確認輸出尺寸與編碼
      ↓
批次 Export
```

媒體不需要上傳雲端，也不打包 FFmpeg。

## 現有功能

- Android SAF 多選照片與影片。
- 1.33×、1.50×、1.55×、1.80×、2.00× 與 1.01–3.00× 自訂倍率。
- Preserve Width：維持寬度、縮短高度，預設選項。
- Preserve Height：維持高度、增加寬度。
- JPEG / PNG / WebP Lossless 批次照片輸出。
- H.264 / AVC、H.265 / HEVC，以及平台允許時的 AV1。
- 透過 `MediaCodecList` 探測手機實際可用 Encoder。
- 不硬寫 Snapdragon / Dimensity / Exynos codec 名稱。
- 硬體編碼優先，實際 configure/start 失敗時繼續 fallback。
- 可保留原始壓縮音訊，不重新編碼。
- Foreground media-processing service 支援長時間批次工作。
- Manifest 不申請 `INTERNET`，目前沒有帳號、分析 SDK 或雲端上傳。

## 為什麼預設 Preserve Width

假設來源是：

```text
3840 × 2160
1.33× anamorphic
```

Preserve Width：

```text
3840 × 2160
      ↓
3840 × 1624
≈ 2.36:1
```

Preserve Height 則會得到約：

```text
5108 × 2160
```

兩者幾何校正方向相同，但 5108px 寬度容易超過手機硬體 Encoder 的尺寸能力，因此手機端預設 Preserve Width。

## 影片處理鏈

```text
MediaExtractor
      ↓
MediaCodec Decoder
      ↓
SurfaceTexture
      ↓
OpenGL ES Transform
      ↓
MediaCodec Encoder
      ↓
MediaMuxer
      ↓
MediaStore
```

照片走獨立的 `PhotoProcessor`；影片則由 `VideoTranscoder` 負責。

完整說明見 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 編碼器策略

Auto：

```text
硬體 HEVC
   ↓
硬體 AVC
   ↓
軟體 HEVC
   ↓
軟體 AVC
```

手機廠商回報 codec capability 不一定等同實際可啟動，因此 OpenDeSqueeze 還會在 runtime 真正 configure/start；失敗就換下一個候選。

NPU 並沒有一套跨 Android 廠商通用的影片編碼 API，因此本專案不做假的「NPU Encoder」按鈕，而是交給 Android MediaCodec 與 SoC vendor 的媒體堆疊處理。

## HDR

v0.1.x 的 HDR 是實驗功能。

預設會辨識常見 HDR metadata 並阻止直接處理，避免無聲地破壞 HDR。實驗模式可嘗試轉送 metadata，但目前 OpenGL ES path 還不能宣稱是完整驗證的 10-bit HDR pipeline。

## 安裝與建置

目前提供 GitHub Actions 建出的 **debug APK artifact**，還沒有正式的固定 release signing。

- 想直接測：到 Actions 的成功 Build 下載 Artifact。
- 想自行建置：看 [BUILDING.md](BUILDING.md)。

## 開源與 Fork

歡迎：

- Fork
- Issue
- 裝置相容性回報
- Pull Request
- 新 codec / 新格式 / UI 改進提案

請看 [CONTRIBUTING.md](CONTRIBUTING.md)。

### GPL 要講清楚

這個專案採用 **GNU GPL v3.0**。

GPL 不是「禁止別人使用」。它允許使用、研究、修改、Fork 與再散布，但當你對外散布 GPL 涵蓋的修改版本或 binary 時，需要遵守 GPL 的對應源碼與授權義務。

如果目標是「公開原始碼，但禁止別人使用」，那就不屬於一般 Open Source License 的方向。OpenDeSqueeze 現在選擇的是 GPL copyleft 路線。

## Credits

目前專案由 `juhjuhx` 發起、整合與實機驗證。

開發過程使用 AI 工具協助架構、程式生成、除錯與文件整理；AI 工具的使用會在 [CONTRIBUTORS.md](CONTRIBUTORS.md) 中透明記錄，但合併進 repository 的程式碼責任仍由人類 maintainer 承擔。

研究過的外部開源專案與授權關係見 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
