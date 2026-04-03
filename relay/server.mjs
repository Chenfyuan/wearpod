import crypto from "node:crypto";
import path from "node:path";
import { fileURLToPath } from "node:url";
import express from "express";
import multer from "multer";
import { XMLParser } from "fast-xml-parser";

const app = express();
const port = Number(process.env.PORT || 8787);
const publicBaseUrl = (process.env.PUBLIC_BASE_URL || `http://localhost:${port}`).replace(/\/$/, "");
const relayDir = path.dirname(fileURLToPath(import.meta.url));
const docsAssetsDir = path.resolve(relayDir, "..", "docs", "assets");
const docsAssetBaseUrl = process.env.DOCS_ASSET_BASE_URL || "/assets";
const sessionTtlMs = 10 * 60 * 1000;
const sessions = new Map();
const shortCodeIndex = new Map();
const exportSessions = new Map();
const exportShortCodeIndex = new Map();
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 1024 * 1024 },
});
const xmlParser = new XMLParser({
  ignoreAttributes: false,
  attributeNamePrefix: "",
});
const defaultLocale = "en";
const companyName = "广州舜健科技有限公司";
const contactEmail = "chenfyuanl@gmail.com";
const privacyEffectiveDate = "2026-03-30";
const messages = {
  "zh-CN": {
    site_title: "WearPod | 独立 Wear OS 播客播放器",
    nav_home: "首页",
    nav_import: "导入",
    nav_export: "导出",
    nav_privacy: "隐私政策",
    lang_zh: "中文",
    lang_en: "EN",
    brand_eyebrow: "WearPod",
    preview_eyebrow: "预览",
    import_eyebrow: "导入",
    export_eyebrow: "导出",
    short_code_eyebrow: "短码",
    privacy_eyebrow: "隐私",
    notice_eyebrow: "提示",
    hero_title: "为圆形手表而生的独立播客播放器",
    hero_body:
      "一款专为 Wear OS 打磨的独立播客应用。支持 RSS、手机扫码导入导出、离线下载与手表端直接播放，不需要 companion app，也不需要依赖手机做主控。",
    hero_cta_primary: "开始导入",
    hero_cta_secondary: "看看界面",
    trust_playback: "独立播放",
    trust_offline: "离线优先",
    trust_phone_assist: "手机辅助导入",
    preview_title: "把主流程压缩进手表安全区",
    preview_body: "首页聚焦继续播放，播放器把主控制前置，导入则交给手机完成低频输入，整套体验都是围绕圆形屏幕重新组织的。",
    preview_home_title: "首页",
    preview_home_body: "继续播放、收藏与主入口",
    preview_player_title: "播放器",
    preview_player_body: "播放、切换、音量与输出都围绕核心控件组织",
    preview_subscriptions_title: "订阅与导出",
    preview_subscriptions_body: "把导入、导出和订阅管理收进同一个手表入口",
    import_title: "手机导入订阅，比在手表上打字更合理",
    import_body:
      "扫描手表上的导入二维码，或直接输入导入短码。你可以填一个 RSS，也可以上传一个 OPML 文件，导入结果会回到手表确认。",
    import_step_1_title: "进入导入会话",
    import_step_1_body: "扫描手表二维码或输入 6 位短码",
    import_step_2_title: "填写 RSS 或上传 OPML",
    import_step_2_body: "把低频重输入放到手机上完成",
    import_step_3_title: "回到手表确认导入",
    import_step_3_body: "保持 WearPod 的独立使用链路",
    import_code_title: "输入导入短码",
    import_code_body: "如果你已经在手表上生成了导入二维码，也可以直接在这里输入短码继续。",
    export_title: "把手表里的订阅导出成 OPML 备份",
    export_body:
      "导出流程同样通过手机完成。扫描导出二维码或输入短码后，就能下载一份标准 OPML 文件，用于迁移、备份或转移到其他客户端。",
    export_step_1_title: "进入导出会话",
    export_step_1_body: "扫描手表导出二维码或输入短码",
    export_step_2_title: "下载 OPML 文件",
    export_step_2_body: "保存到手机浏览器下载目录",
    export_step_3_title: "用于迁移或备份",
    export_step_3_body: "支持迁移到其他播客客户端或设备",
    export_code_title: "输入导出短码",
    export_code_body: "手表上点“手机导出”后，会显示导出二维码和 6 位短码。你可以直接在这里继续下载备份。",
    short_code_import_label: "输入导入短码",
    short_code_import_button: "继续到导入页",
    short_code_export_label: "输入导出短码",
    short_code_export_button: "继续到导出页",
    short_code_placeholder: "输入 6 位短码",
    footer_body: "WearPod 由 {companyName} 提供，为真正想在手表上独立听播客的人准备。若你需要使用帮助或售后支持，可以通过 {contactEmail} 联系我们。",
    footer_privacy: "查看隐私政策",
    footer_start: "开始使用",
    privacy_title: "WearPod 隐私政策",
    privacy_hero_title: "隐私政策",
    privacy_hero_body:
      "本页面说明 WearPod 当前的导入导出中转服务如何处理数据。经营主体为 {companyName}，联系邮箱与客服渠道均为 {contactEmail}，本政策自 {effectiveDate} 起生效。",
    privacy_company_title: "经营主体",
    privacy_contact_title: "联系邮箱",
    privacy_support_title: "客服渠道",
    privacy_effective_title: "生效日期",
    privacy_section_handled_title: "我们处理哪些数据",
    privacy_import_title: "导入数据",
    privacy_import_body: "当你使用手机导入时，服务会接收你提交的 RSS 地址或 OPML 文件内容，用于生成手表可读取的本次导入结果。",
    privacy_export_title: "导出数据",
    privacy_export_body: "当你使用手机导出时，服务会暂存手表生成的 OPML 内容，并提供一次性下载链接。",
    privacy_session_title: "会话信息",
    privacy_session_body: "系统会生成短码、会话编号和过期时间，用于让手机和手表在短时间内完成同一次操作。",
    privacy_retention_title: "我们如何保留这些数据",
    privacy_retention_short_title: "短时有效",
    privacy_retention_short_body: "导入和导出会话默认只保留约 10 分钟，过期后会失效，避免长期保留临时数据。",
    privacy_retention_usage_title: "仅用于本次操作",
    privacy_retention_usage_body: "这些数据用于完成当前二维码对应的导入或导出，不会被用作账号画像、推荐或广告用途。",
    privacy_notice_body: "如果你对 WearPod 的数据处理、订阅导入导出或售后支持有任何疑问，请发送邮件至 {contactEmail}。",
    back_home: "返回首页",
    import_page_title: "WearPod 导入订阅",
    import_page_heading: "导入到手表",
    badge_code: "短码 {shortCode}",
    import_page_body: "你可以贴一个 RSS 地址，或者直接上传 OPML。手表会在确认后完成真正导入。",
    strip_target: "目标设备",
    strip_target_value: "WearPod on Wear OS",
    strip_status: "会话状态",
    strip_status_waiting: "等待手机提交",
    import_single_title: "导入单个订阅地址",
    import_single_body: "适合快速导入一档播客",
    rss_label: "RSS 地址",
    rss_placeholder: "https://example.com/feed.xml",
    divider_or: "或",
    import_opml_title: "批量导入 OPML",
    import_opml_body: "适合从其他播客客户端迁移订阅",
    select_opml: "选择 OPML 文件",
    no_file_selected: "未选择文件",
    submit_to_watch: "提交到手表",
    import_footnote: "提交后，回到手表确认导入即可。",
    importing_title: "正在提交到手表...",
    importing_body: "请稍等片刻。",
    import_submitted_title: "已提交到手表",
    import_submitted_body: "新增候选 {acceptedCount} 个，重复 {duplicateCount} 个，无效 {invalidCount} 个。现在回到手表确认导入即可。",
    submit_failed: "提交失败",
    export_page_title: "WearPod 导出订阅",
    export_page_heading: "导出订阅备份",
    export_page_body: "你现在可以把手表里的订阅导出成一个 OPML 文件，保存到手机或分享到其他播客应用。",
    export_count_label: "订阅数量",
    export_count_value: "{count} 个",
    export_type_label: "文件类型",
    export_type_value: "OPML",
    export_download_title: "下载订阅备份",
    export_download_body: "文件会直接保存到手机浏览器的下载目录。",
    download_opml: "下载 OPML",
    export_footnote: "如果想迁移到其他客户端，通常直接导入这个 OPML 文件即可。",
    message_short_code_not_found_title: "短码不存在",
    message_short_code_not_found_body: "请重新扫描手表上的二维码。",
    message_session_not_found_title: "会话不存在",
    message_session_not_found_body: "请在手表上重新生成二维码。",
    message_qr_expired_title: "二维码已过期",
    message_qr_expired_body: "请在手表上重新生成二维码。",
    message_export_session_not_found_title: "导出会话不存在",
    message_export_session_not_found_body: "请在手表上重新生成导出二维码。",
    back_to_short_code: "返回输入短码",
  },
  en: {
    site_title: "WearPod | Standalone podcast player for Wear OS",
    nav_home: "Home",
    nav_import: "Import",
    nav_export: "Export",
    nav_privacy: "Privacy",
    lang_zh: "中文",
    lang_en: "EN",
    brand_eyebrow: "WearPod",
    preview_eyebrow: "Preview",
    import_eyebrow: "Import",
    export_eyebrow: "Export",
    short_code_eyebrow: "Short code",
    privacy_eyebrow: "Privacy",
    notice_eyebrow: "Notice",
    hero_title: "A standalone podcast player built for round watches",
    hero_body:
      "A Wear OS podcast app designed for round watch displays. It supports RSS, phone-assisted import and export, offline downloads, and direct playback on the watch without a companion app.",
    hero_cta_primary: "Start importing",
    hero_cta_secondary: "See the product",
    trust_playback: "Standalone playback",
    trust_offline: "Offline first",
    trust_phone_assist: "Phone-assisted import",
    preview_title: "The core flow is compressed into the watch-safe area",
    preview_body: "Home stays focused on resuming playback, the player keeps the primary controls front and center, and phone-assisted import handles the low-frequency typing work.",
    preview_home_title: "Home",
    preview_home_body: "Resume playback, favorites, and top-level entry points",
    preview_player_title: "Player",
    preview_player_body: "Playback, switching, volume, and routing are all organized around the core controls",
    preview_subscriptions_title: "Subscriptions & export",
    preview_subscriptions_body: "Import, export, and subscription management live inside one watch flow",
    import_title: "Phone import is much better than typing on a watch",
    import_body:
      "Scan the import QR code from your watch or enter the short code. You can paste a single RSS feed or upload an OPML file, then confirm the result back on the watch.",
    import_step_1_title: "Open an import session",
    import_step_1_body: "Scan the QR code on your watch or enter the 6-digit code",
    import_step_2_title: "Paste RSS or upload OPML",
    import_step_2_body: "Keep the heavy text input on the phone",
    import_step_3_title: "Confirm on the watch",
    import_step_3_body: "Keep the overall WearPod experience watch-first",
    import_code_title: "Enter import code",
    import_code_body: "If your watch has already generated an import QR code, you can continue here with the short code instead.",
    export_title: "Export your watch subscriptions as an OPML backup",
    export_body:
      "Export also runs through the phone. Scan the export QR code or enter the short code to download a standard OPML file for backup or migration.",
    export_step_1_title: "Open an export session",
    export_step_1_body: "Scan the export QR code on your watch or enter the code",
    export_step_2_title: "Download the OPML file",
    export_step_2_body: "Save it to your phone's download folder",
    export_step_3_title: "Use it for backup or migration",
    export_step_3_body: "Import it into another podcast app or device",
    export_code_title: "Enter export code",
    export_code_body: "After you tap Phone export on the watch, you'll see a QR code and a 6-digit short code. Continue here to download the backup.",
    short_code_import_label: "Enter import code",
    short_code_import_button: "Continue to import",
    short_code_export_label: "Enter export code",
    short_code_export_button: "Continue to export",
    short_code_placeholder: "Enter the 6-digit code",
    footer_body: "WearPod is provided by {companyName} for people who want a truly independent podcast experience on Wear OS. For support, contact {contactEmail}.",
    footer_privacy: "Read privacy policy",
    footer_start: "Get started",
    privacy_title: "WearPod Privacy Policy",
    privacy_hero_title: "Privacy Policy",
    privacy_hero_body:
      "This page explains how the current WearPod relay service handles import and export data. The service is operated by {companyName}. Both the contact and customer support email are {contactEmail}. This policy takes effect on {effectiveDate}.",
    privacy_company_title: "Operating entity",
    privacy_contact_title: "Contact email",
    privacy_support_title: "Support channel",
    privacy_effective_title: "Effective date",
    privacy_section_handled_title: "What data we handle",
    privacy_import_title: "Import data",
    privacy_import_body: "When you use phone import, the service receives the RSS URL or OPML file you submit in order to build the import result that the watch can read.",
    privacy_export_title: "Export data",
    privacy_export_body: "When you use phone export, the service temporarily stores the OPML content generated by the watch and provides a one-time download link.",
    privacy_session_title: "Session data",
    privacy_session_body: "The system generates a short code, session identifier, and expiration time so the phone and watch can complete the same operation together.",
    privacy_retention_title: "How long data is kept",
    privacy_retention_short_title: "Short-lived sessions",
    privacy_retention_short_body: "Import and export sessions are kept for about 10 minutes by default, then they expire to avoid long-term storage of temporary data.",
    privacy_retention_usage_title: "Used only for the current action",
    privacy_retention_usage_body: "This data is only used to complete the import or export associated with the current QR code. It is not used for profiling, recommendations, or advertising.",
    privacy_notice_body: "If you have any questions about WearPod data handling, subscription import/export, or support, email {contactEmail}.",
    back_home: "Back to home",
    import_page_title: "WearPod Import",
    import_page_heading: "Import to your watch",
    badge_code: "Code {shortCode}",
    import_page_body: "Paste an RSS feed or upload an OPML file. The watch will finish the actual import after you confirm it.",
    strip_target: "Target device",
    strip_target_value: "WearPod on Wear OS",
    strip_status: "Session status",
    strip_status_waiting: "Waiting for phone submission",
    import_single_title: "Import a single feed URL",
    import_single_body: "Best for bringing in one podcast quickly",
    rss_label: "RSS feed URL",
    rss_placeholder: "https://example.com/feed.xml",
    divider_or: "or",
    import_opml_title: "Import OPML in bulk",
    import_opml_body: "Best for migrating subscriptions from another app",
    select_opml: "Choose an OPML file",
    no_file_selected: "No file selected",
    submit_to_watch: "Send to watch",
    import_footnote: "After submitting, go back to the watch to confirm the import.",
    importing_title: "Sending to your watch...",
    importing_body: "This should only take a moment.",
    import_submitted_title: "Sent to your watch",
    import_submitted_body: "{acceptedCount} accepted, {duplicateCount} duplicates, {invalidCount} invalid. Go back to your watch to confirm the import.",
    submit_failed: "Submission failed",
    export_page_title: "WearPod Export",
    export_page_heading: "Export subscription backup",
    export_page_body: "You can export the subscriptions stored on your watch as an OPML file and save it on your phone or import it elsewhere.",
    export_count_label: "Subscriptions",
    export_count_value: "{count}",
    export_type_label: "File format",
    export_type_value: "OPML",
    export_download_title: "Download subscription backup",
    export_download_body: "The file will be saved to your phone browser's download directory.",
    download_opml: "Download OPML",
    export_footnote: "Most podcast apps can import this OPML file directly if you want to migrate later.",
    message_short_code_not_found_title: "Code not found",
    message_short_code_not_found_body: "Please scan the QR code from your watch again.",
    message_session_not_found_title: "Session not found",
    message_session_not_found_body: "Please generate a new QR code on your watch.",
    message_qr_expired_title: "QR code expired",
    message_qr_expired_body: "Please generate a new QR code on your watch.",
    message_export_session_not_found_title: "Export session not found",
    message_export_session_not_found_body: "Please generate a new export QR code on your watch.",
    back_to_short_code: "Back to code entry",
  },
};

app.use(express.json({ limit: "2mb" }));
app.use(express.urlencoded({ extended: true }));
app.use("/assets", express.static(docsAssetsDir, { maxAge: "1h" }));

app.get("/", (req, res) => {
  res.type("html").send(renderLandingPage(resolveLocale(req)));
});

app.get("/privacy", (req, res) => {
  res.type("html").send(renderPrivacyPage(resolveLocale(req)));
});

app.post("/enter-code", (req, res) => {
  const localeInfo = resolveLocale(req);
  const shortCode = String(req.body.shortCode || "").trim().toUpperCase();
  const sessionId = shortCodeIndex.get(shortCode);
  if (sessionId) {
    res.redirect(localizedPath(`/session/${sessionId}`, localeInfo));
    return;
  }
  const exportSessionId = exportShortCodeIndex.get(shortCode);
  if (exportSessionId) {
    res.redirect(localizedPath(`/export/${exportSessionId}`, localeInfo));
    return;
  }
  if (!sessionId) {
    res.status(404).type("html").send(renderMessagePage(
      t(localeInfo.locale, "message_short_code_not_found_title"),
      t(localeInfo.locale, "message_short_code_not_found_body"),
      localeInfo,
    ));
    return;
  }
});

app.get("/session/:sessionId", (req, res) => {
  const localeInfo = resolveLocale(req);
  const session = getSession(req.params.sessionId);
  if (!session) {
    res.status(404).type("html").send(renderMessagePage(
      t(localeInfo.locale, "message_session_not_found_title"),
      t(localeInfo.locale, "message_session_not_found_body"),
      localeInfo,
    ));
    return;
  }
  if (expireIfNeeded(session)) {
    res.status(410).type("html").send(renderMessagePage(
      t(localeInfo.locale, "message_qr_expired_title"),
      t(localeInfo.locale, "message_qr_expired_body"),
      localeInfo,
    ));
    return;
  }
  res.type("html").send(renderSessionPage(session, localeInfo));
});

app.post("/api/sessions", (_req, res) => {
  const session = createSession();
  res.json(watchSessionPayload(session));
});

app.post("/api/export-sessions", (req, res) => {
  const opmlContent = String(req.body.opmlContent || "").trim();
  const outlineCount = Number(req.body.outlineCount || 0);
  if (!opmlContent) {
    res.status(400).json({ message: "opml_required" });
    return;
  }
  const session = createExportSession({
    opmlContent,
    outlineCount: Number.isFinite(outlineCount) ? Math.max(0, outlineCount) : 0,
  });
  res.json(exportSessionPayload(session));
});

app.get("/api/sessions/:sessionId", (req, res) => {
  const session = getSession(req.params.sessionId);
  if (!session) {
    res.status(404).json({ message: "session_not_found" });
    return;
  }
  expireIfNeeded(session);
  res.json(watchSessionPayload(session));
});

app.get("/export/:sessionId", (req, res) => {
  const localeInfo = resolveLocale(req);
  const session = getExportSession(req.params.sessionId);
  if (!session) {
    res.status(404).type("html").send(renderMessagePage(
      t(localeInfo.locale, "message_export_session_not_found_title"),
      t(localeInfo.locale, "message_export_session_not_found_body"),
      localeInfo,
    ));
    return;
  }
  if (expireIfNeeded(session)) {
    res.status(410).type("html").send(renderMessagePage(
      t(localeInfo.locale, "message_qr_expired_title"),
      t(localeInfo.locale, "message_export_session_not_found_body"),
      localeInfo,
    ));
    return;
  }
  res.type("html").send(renderExportSessionPage(session, localeInfo));
});

app.get("/api/export-sessions/:sessionId/download", (req, res) => {
  const localeInfo = resolveLocale(req);
  const session = getExportSession(req.params.sessionId);
  if (!session) {
    res.status(404).type("html").send(renderMessagePage(
      t(localeInfo.locale, "message_export_session_not_found_title"),
      t(localeInfo.locale, "message_export_session_not_found_body"),
      localeInfo,
    ));
    return;
  }
  if (expireIfNeeded(session)) {
    res.status(410).type("html").send(renderMessagePage(
      t(localeInfo.locale, "message_qr_expired_title"),
      t(localeInfo.locale, "message_export_session_not_found_body"),
      localeInfo,
    ));
    return;
  }
  session.downloadCount += 1;
  res
    .status(200)
    .type("application/xml; charset=utf-8")
    .setHeader("Content-Disposition", `attachment; filename="wearpod-subscriptions.opml"`)
    .send(session.opmlContent);
});

app.post("/api/sessions/:sessionId/import", upload.single("opml"), async (req, res) => {
  const session = getSession(req.params.sessionId);
  if (!session) {
    res.status(404).json({ message: "session_not_found" });
    return;
  }
  if (expireIfNeeded(session)) {
    res.status(410).json({ message: "session_expired" });
    return;
  }

  const candidates = [];
  const rssUrl = String(req.body.rssUrl || "").trim();
  if (rssUrl) {
    candidates.push(rssUrl);
  }
  if (req.file?.buffer?.length) {
    candidates.push(...extractFeedUrlsFromOpml(req.file.buffer));
  }

  const normalized = summarizeCandidates(candidates);
  session.status = "SUBMITTED";
  session.feedUrls = normalized.feedUrls;
  session.invalidCount = normalized.invalidCount;
  session.duplicateCountWithinPayload = normalized.duplicateCountWithinPayload;
  session.submittedAtEpochMillis = Date.now();

  res.json({
    ok: true,
    acceptedCount: session.feedUrls.length,
    invalidCount: session.invalidCount,
    duplicateCountWithinPayload: session.duplicateCountWithinPayload,
  });
});

app.listen(port, () => {
  console.log(`WearPod relay listening on http://localhost:${port}`);
  console.log(`Public base URL: ${publicBaseUrl}`);
});

setInterval(() => {
  for (const session of sessions.values()) {
    expireIfNeeded(session);
  }
}, 30_000).unref();

function createSession() {
  const sessionId = crypto.randomBytes(12).toString("hex");
  const shortCode = crypto.randomBytes(3).toString("hex").toUpperCase();
  const session = {
    sessionId,
    shortCode,
    status: "PENDING",
    createdAtEpochMillis: Date.now(),
    expiresAtEpochMillis: Date.now() + sessionTtlMs,
    submittedAtEpochMillis: null,
    feedUrls: [],
    invalidCount: 0,
    duplicateCountWithinPayload: 0,
  };
  sessions.set(sessionId, session);
  shortCodeIndex.set(shortCode, sessionId);
  return session;
}

function createExportSession({ opmlContent, outlineCount }) {
  const sessionId = crypto.randomBytes(12).toString("hex");
  const shortCode = crypto.randomBytes(3).toString("hex").toUpperCase();
  const session = {
    sessionId,
    shortCode,
    status: "READY",
    createdAtEpochMillis: Date.now(),
    expiresAtEpochMillis: Date.now() + sessionTtlMs,
    outlineCount,
    downloadCount: 0,
    opmlContent,
  };
  exportSessions.set(sessionId, session);
  exportShortCodeIndex.set(shortCode, sessionId);
  return session;
}

function getSession(sessionId) {
  return sessions.get(sessionId);
}

function getExportSession(sessionId) {
  return exportSessions.get(sessionId);
}

function expireIfNeeded(session) {
  if (session.status === "EXPIRED") {
    return true;
  }
  if (Date.now() < session.expiresAtEpochMillis) {
    return false;
  }
  session.status = "EXPIRED";
  if ("feedUrls" in session) {
    session.feedUrls = [];
  }
  if ("opmlContent" in session) {
    session.opmlContent = "";
  }
  return true;
}

function watchSessionPayload(session) {
  return {
    sessionId: session.sessionId,
    shortCode: session.shortCode,
    mobileUrl: `${publicBaseUrl}/session/${session.sessionId}`,
    expiresAtEpochMillis: session.expiresAtEpochMillis,
    status: session.status,
    feedUrls: session.status === "SUBMITTED" ? session.feedUrls : [],
    invalidCount: session.status === "SUBMITTED" ? session.invalidCount : 0,
    duplicateCountWithinPayload: session.status === "SUBMITTED" ? session.duplicateCountWithinPayload : 0,
  };
}

function exportSessionPayload(session) {
  return {
    sessionId: session.sessionId,
    shortCode: session.shortCode,
    mobileUrl: `${publicBaseUrl}/export/${session.sessionId}`,
    expiresAtEpochMillis: session.expiresAtEpochMillis,
    outlineCount: session.outlineCount,
  };
}

function resolveLocale(req) {
  const requested = resolveRequestedLocale(req.query.lang);
  if (requested) {
    return { locale: requested, explicit: true };
  }
  const acceptLanguage = String(req.get("accept-language") || "");
  return { locale: resolveRequestedLocale(acceptLanguage) || defaultLocale, explicit: false };
}

function resolveRequestedLocale(value) {
  const normalized = String(value || "").trim().toLowerCase();
  if (!normalized) return null;
  if (normalized.startsWith("zh")) return "zh-CN";
  if (normalized.startsWith("en")) return "en";
  return null;
}

function t(locale, key, vars = {}) {
  const dictionary = messages[locale] || messages[defaultLocale];
  const template = dictionary[key] ?? messages[defaultLocale][key] ?? key;
  return template.replace(/\{(\w+)\}/g, (_, name) => String(vars[name] ?? ""));
}

function localizedPath(pathname, localeInfo, hash = "") {
  const query = localeInfo.explicit ? `?lang=${encodeURIComponent(localeInfo.locale)}` : "";
  return `${pathname}${query}${hash}`;
}

function forceLocalePath(pathname, locale, hash = "") {
  return `${pathname}?lang=${encodeURIComponent(locale)}${hash}`;
}

function renderLanguageSwitcher(pathname, activeLocale, hash = "") {
  return `
    <div class="lang-switch" aria-label="Language switcher">
      <a href="${forceLocalePath(pathname, "zh-CN", hash)}"${activeLocale === "zh-CN" ? ' aria-current="true"' : ""}>${t(activeLocale, "lang_zh")}</a>
      <a href="${forceLocalePath(pathname, "en", hash)}"${activeLocale === "en" ? ' aria-current="true"' : ""}>${t(activeLocale, "lang_en")}</a>
    </div>
  `;
}

function summarizeCandidates(candidates) {
  const seen = new Set();
  const feedUrls = [];
  let invalidCount = 0;
  let duplicateCountWithinPayload = 0;

  for (const candidate of candidates) {
    const normalized = normalizeFeedUrl(candidate);
    if (!normalized) {
      invalidCount += 1;
      continue;
    }
    if (seen.has(normalized)) {
      duplicateCountWithinPayload += 1;
      continue;
    }
    seen.add(normalized);
    feedUrls.push(normalized);
  }

  return { feedUrls, invalidCount, duplicateCountWithinPayload };
}

function normalizeFeedUrl(rawUrl) {
  const trimmed = String(rawUrl || "").trim();
  if (!trimmed) return null;
  const candidate = /^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`;
  try {
    const url = new URL(candidate);
    if (url.protocol !== "http:" && url.protocol !== "https:") {
      return null;
    }
    return url.toString();
  } catch {
    return null;
  }
}

function extractFeedUrlsFromOpml(buffer) {
  try {
    const document = xmlParser.parse(buffer.toString("utf8"));
    const body = document?.opml?.body;
    return collectOutlineUrls(body?.outline);
  } catch {
    return [];
  }
}

function collectOutlineUrls(node) {
  if (!node) return [];
  if (Array.isArray(node)) {
    return node.flatMap(collectOutlineUrls);
  }

  const urls = [];
  if (typeof node === "object") {
    if (node.xmlUrl) {
      urls.push(node.xmlUrl);
    }
    if (node.outline) {
      urls.push(...collectOutlineUrls(node.outline));
    }
  }
  return urls;
}

function renderLandingPage(localeInfo) {
  const locale = localeInfo.locale;
  return `<!doctype html>
<html lang="${locale}">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>${t(locale, "site_title")}</title>
  <style>${baseStyles()}</style>
</head>
<body>
  <header class="site-nav">
    <nav class="site-nav-inner">
      <a class="brand-link" href="#home">WearPod</a>
      <div class="nav-links">
        <a href="#home">${t(locale, "nav_home")}</a>
        <a href="#import">${t(locale, "nav_import")}</a>
        <a href="#export">${t(locale, "nav_export")}</a>
        <a href="${localizedPath("/privacy", localeInfo)}">${t(locale, "nav_privacy")}</a>
      </div>
      ${renderLanguageSwitcher("/", locale, "#home")}
    </nav>
  </header>

  <main class="shell shell-sections shell-wide">
    <section class="hero-layout" id="home">
      <div class="card card-hero hero-copy">
        <div class="eyebrow">${t(locale, "brand_eyebrow")}</div>
        <h1>${t(locale, "hero_title")}</h1>
        <p class="lede">${t(locale, "hero_body")}</p>
        <div class="hero-actions">
          <a class="button-link" href="#import">${t(locale, "hero_cta_primary")}</a>
          <a class="button-link button-link-secondary" href="#preview">${t(locale, "hero_cta_secondary")}</a>
        </div>
        <div class="trust-row">
          <div class="trust-pill">${t(locale, "trust_playback")}</div>
          <div class="trust-pill">${t(locale, "trust_offline")}</div>
          <div class="trust-pill">${t(locale, "trust_phone_assist")}</div>
        </div>
      </div>

      <div class="hero-visual">
        <div class="hero-device hero-device-primary">
          <div class="watch-frame watch-frame-large">
            <img src="${docsAssetBaseUrl}/wearpod-player.png" alt="${t(locale, "preview_player_title")}" loading="lazy" />
          </div>
        </div>
        <div class="hero-device hero-device-secondary">
          <div class="watch-frame watch-frame-medium">
            <img src="${docsAssetBaseUrl}/wearpod-home.png" alt="${t(locale, "preview_home_title")}" loading="lazy" />
          </div>
        </div>
      </div>
    </section>

    <section class="card section-card" id="preview">
      <div class="section-title-block">
        <div>
          <div class="eyebrow">${t(locale, "preview_eyebrow")}</div>
          <h2>${t(locale, "preview_title")}</h2>
        </div>
        <p class="section-copy">${t(locale, "preview_body")}</p>
      </div>
      <div class="preview-grid preview-grid-wide">
        <figure class="preview-card">
          <div class="watch-stage">
            <div class="watch-frame watch-frame-card">
              <img src="${docsAssetBaseUrl}/wearpod-home.png" alt="${t(locale, "preview_home_title")}" loading="lazy" />
            </div>
          </div>
          <figcaption>
            <strong>${t(locale, "preview_home_title")}</strong>
            <span>${t(locale, "preview_home_body")}</span>
          </figcaption>
        </figure>
        <figure class="preview-card">
          <div class="watch-stage">
            <div class="watch-frame watch-frame-card">
              <img src="${docsAssetBaseUrl}/wearpod-player.png" alt="${t(locale, "preview_player_title")}" loading="lazy" />
            </div>
          </div>
          <figcaption>
            <strong>${t(locale, "preview_player_title")}</strong>
            <span>${t(locale, "preview_player_body")}</span>
          </figcaption>
        </figure>
        <figure class="preview-card">
          <div class="watch-stage">
            <div class="watch-frame watch-frame-card">
              <img src="${docsAssetBaseUrl}/wearpod-subscriptions.png" alt="${t(locale, "preview_subscriptions_title")}" loading="lazy" />
            </div>
          </div>
          <figcaption>
            <strong>${t(locale, "preview_subscriptions_title")}</strong>
            <span>${t(locale, "preview_subscriptions_body")}</span>
          </figcaption>
        </figure>
      </div>
    </section>

    <section class="split-band" id="import">
      <div class="card card-hero split-copy">
        <div class="eyebrow">${t(locale, "import_eyebrow")}</div>
        <h2>${t(locale, "import_title")}</h2>
        <p class="lede">${t(locale, "import_body")}</p>
        <div class="hero-grid">
          <div class="hero-step">
            <div class="step-index">1</div>
            <div>
              <strong>${t(locale, "import_step_1_title")}</strong>
              <p>${t(locale, "import_step_1_body")}</p>
            </div>
          </div>
          <div class="hero-step">
            <div class="step-index">2</div>
            <div>
              <strong>${t(locale, "import_step_2_title")}</strong>
              <p>${t(locale, "import_step_2_body")}</p>
            </div>
          </div>
          <div class="hero-step">
            <div class="step-index">3</div>
            <div>
              <strong>${t(locale, "import_step_3_title")}</strong>
              <p>${t(locale, "import_step_3_body")}</p>
            </div>
          </div>
        </div>
      </div>
      <div class="card card-form split-action">
        <div class="section-head section-head-compact">
          <div class="eyebrow">${t(locale, "short_code_eyebrow")}</div>
          <h2>${t(locale, "import_code_title")}</h2>
        </div>
        <p class="section-copy">${t(locale, "import_code_body")}</p>
        ${renderShortCodeForm(localeInfo, "import")}
      </div>
    </section>

    <section class="split-band" id="export">
      <div class="card card-hero split-copy">
        <div class="eyebrow">${t(locale, "export_eyebrow")}</div>
        <h2>${t(locale, "export_title")}</h2>
        <p class="lede">${t(locale, "export_body")}</p>
        <div class="hero-grid">
          <div class="hero-step">
            <div class="step-index">1</div>
            <div>
              <strong>${t(locale, "export_step_1_title")}</strong>
              <p>${t(locale, "export_step_1_body")}</p>
            </div>
          </div>
          <div class="hero-step">
            <div class="step-index">2</div>
            <div>
              <strong>${t(locale, "export_step_2_title")}</strong>
              <p>${t(locale, "export_step_2_body")}</p>
            </div>
          </div>
          <div class="hero-step">
            <div class="step-index">3</div>
            <div>
              <strong>${t(locale, "export_step_3_title")}</strong>
              <p>${t(locale, "export_step_3_body")}</p>
            </div>
          </div>
        </div>
      </div>
      <div class="card card-form split-action">
        <div class="section-head section-head-compact">
          <div class="eyebrow">${t(locale, "short_code_eyebrow")}</div>
          <h2>${t(locale, "export_code_title")}</h2>
        </div>
        <p class="section-copy">${t(locale, "export_code_body")}</p>
        ${renderShortCodeForm(localeInfo, "export")}
      </div>
    </section>

    <section class="card footer-card commerce-footer">
      <div class="footer-grid footer-grid-wide">
        <div>
          <div class="eyebrow">WearPod</div>
          <p class="footer-copy">${t(locale, "footer_body", { companyName, contactEmail })}</p>
        </div>
        <div class="footer-actions">
          <a class="button-link button-link-secondary" href="${localizedPath("/privacy", localeInfo)}">${t(locale, "footer_privacy")}</a>
          <a class="button-link" href="#import">${t(locale, "footer_start")}</a>
        </div>
      </div>
    </section>
  </main>
</body>
</html>`;
}

function renderShortCodeForm(localeInfo, type) {
  const locale = localeInfo.locale;
  const isImport = type === "import";
  const label = isImport ? t(locale, "short_code_import_label") : t(locale, "short_code_export_label");
  const buttonText = isImport ? t(locale, "short_code_import_button") : t(locale, "short_code_export_button");
  return `
      <form method="post" action="${localizedPath("/enter-code", localeInfo)}" class="stack compact-stack short-code-form">
        <label>
          ${label}
          <input name="shortCode" maxlength="6" placeholder="${t(locale, "short_code_placeholder")}" autocomplete="off" />
        </label>
        <button type="submit">${buttonText}</button>
      </form>
  `;
}

function renderPrivacyPage(localeInfo) {
  const locale = localeInfo.locale;
  return `<!doctype html>
<html lang="${locale}">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>${t(locale, "privacy_title")}</title>
  <style>${baseStyles()}</style>
</head>
<body>
  <header class="site-nav">
    <nav class="site-nav-inner">
      <a class="brand-link" href="${localizedPath("/", localeInfo)}#home">WearPod</a>
      <div class="nav-links">
        <a href="${localizedPath("/", localeInfo)}#home">${t(locale, "nav_home")}</a>
        <a href="${localizedPath("/", localeInfo)}#import">${t(locale, "nav_import")}</a>
        <a href="${localizedPath("/", localeInfo)}#export">${t(locale, "nav_export")}</a>
        <a href="${localizedPath("/privacy", localeInfo)}" aria-current="page">${t(locale, "nav_privacy")}</a>
      </div>
      ${renderLanguageSwitcher("/privacy", locale)}
    </nav>
  </header>
  <main class="shell shell-sections">
    <section class="card card-hero">
      <div class="eyebrow">${t(locale, "privacy_eyebrow")}</div>
      <h1>${t(locale, "privacy_hero_title")}</h1>
      <p class="lede">${t(locale, "privacy_hero_body", { companyName, contactEmail, effectiveDate: privacyEffectiveDate })}</p>
    </section>

    <section class="card">
      <div class="section-head">
        <h2>${t(locale, "privacy_company_title")} &amp; ${t(locale, "privacy_contact_title")}</h2>
      </div>
      <div class="policy-stack">
        <div class="value-card">
          <strong>${t(locale, "privacy_company_title")}</strong>
          <p>${companyName}</p>
        </div>
        <div class="value-card">
          <strong>${t(locale, "privacy_contact_title")}</strong>
          <p>${contactEmail}</p>
        </div>
        <div class="value-card">
          <strong>${t(locale, "privacy_support_title")}</strong>
          <p>${contactEmail}</p>
        </div>
        <div class="value-card">
          <strong>${t(locale, "privacy_effective_title")}</strong>
          <p>${privacyEffectiveDate}</p>
        </div>
      </div>
    </section>

    <section class="card">
      <div class="section-head">
        <h2>${t(locale, "privacy_section_handled_title")}</h2>
      </div>
      <div class="policy-stack">
        <div class="value-card">
          <strong>${t(locale, "privacy_import_title")}</strong>
          <p>${t(locale, "privacy_import_body")}</p>
        </div>
        <div class="value-card">
          <strong>${t(locale, "privacy_export_title")}</strong>
          <p>${t(locale, "privacy_export_body")}</p>
        </div>
        <div class="value-card">
          <strong>${t(locale, "privacy_session_title")}</strong>
          <p>${t(locale, "privacy_session_body")}</p>
        </div>
      </div>
    </section>

    <section class="card">
      <div class="section-head">
        <h2>${t(locale, "privacy_retention_title")}</h2>
      </div>
      <div class="policy-stack">
        <div class="value-card">
          <strong>${t(locale, "privacy_retention_short_title")}</strong>
          <p>${t(locale, "privacy_retention_short_body")}</p>
        </div>
        <div class="value-card">
          <strong>${t(locale, "privacy_retention_usage_title")}</strong>
          <p>${t(locale, "privacy_retention_usage_body")}</p>
        </div>
      </div>
    </section>

    <section class="card footer-card">
      <div class="footer-grid">
        <div>
          <div class="eyebrow">${t(locale, "notice_eyebrow")}</div>
          <p class="footer-copy">${t(locale, "privacy_notice_body", { contactEmail })}</p>
        </div>
        <a class="button-link" href="${localizedPath("/", localeInfo)}">${t(locale, "back_home")}</a>
      </div>
    </section>
  </main>
</body>
</html>`;
}

function renderSessionPage(session, localeInfo) {
  const locale = localeInfo.locale;
  const jsCopy = JSON.stringify({
    noFileSelected: t(locale, "no_file_selected"),
    importingTitle: t(locale, "importing_title"),
    importingBody: t(locale, "importing_body"),
    importSubmittedTitle: t(locale, "import_submitted_title"),
    importSubmittedBodyTemplate: t(locale, "import_submitted_body", {
      acceptedCount: "__ACCEPTED__",
      duplicateCount: "__DUPLICATES__",
      invalidCount: "__INVALID__",
    }),
    submitFailed: t(locale, "submit_failed"),
  });
  return `<!doctype html>
<html lang="${locale}">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>${t(locale, "import_page_title")}</title>
  <style>${baseStyles()}</style>
</head>
<body>
  <main class="shell">
    <section class="card card-hero">
      <div class="eyebrow">${t(locale, "brand_eyebrow")}</div>
      <div class="hero-topline">
        <h1>${t(locale, "import_page_heading")}</h1>
        <div class="badge">${t(locale, "badge_code", { shortCode: session.shortCode })}</div>
      </div>
      <p class="lede">${t(locale, "import_page_body")}</p>
      <div class="session-strip">
        <div>
          <div class="strip-label">${t(locale, "strip_target")}</div>
          <div class="strip-value">${t(locale, "strip_target_value")}</div>
        </div>
        <div>
          <div class="strip-label">${t(locale, "strip_status")}</div>
          <div class="strip-value">${t(locale, "strip_status_waiting")}</div>
        </div>
      </div>
      ${renderLanguageSwitcher(`/session/${session.sessionId}`, locale)}
    </section>

    <section class="card card-form">
      <form id="import-form" class="stack">
        <div class="input-card">
          <div class="input-card-head">
            <div class="input-card-icon">RSS</div>
            <div>
              <strong>${t(locale, "import_single_title")}</strong>
              <p>${t(locale, "import_single_body")}</p>
            </div>
          </div>
          <label>
            ${t(locale, "rss_label")}
            <input name="rssUrl" placeholder="${t(locale, "rss_placeholder")}" autocomplete="off" />
          </label>
        </div>

        <div class="divider"><span>${t(locale, "divider_or")}</span></div>

        <div class="input-card">
          <div class="input-card-head">
            <div class="input-card-icon">OPML</div>
            <div>
              <strong>${t(locale, "import_opml_title")}</strong>
              <p>${t(locale, "import_opml_body")}</p>
            </div>
          </div>
          <label class="file-picker">
            <span>${t(locale, "select_opml")}</span>
            <input id="opml-input" name="opml" type="file" accept=".opml,.xml,text/xml,application/xml" />
            <em id="file-name">${t(locale, "no_file_selected")}</em>
          </label>
        </div>

        <button id="submit-button" type="submit">${t(locale, "submit_to_watch")}</button>
      </form>
      <div id="result" class="result"></div>
      <p class="footnote">${t(locale, "import_footnote")}</p>
    </section>
  </main>
  <script>
    const copy = ${jsCopy};
    const form = document.getElementById("import-form");
    const result = document.getElementById("result");
    const fileInput = document.getElementById("opml-input");
    const fileName = document.getElementById("file-name");
    const submitButton = document.getElementById("submit-button");

    fileInput.addEventListener("change", () => {
      fileName.textContent = fileInput.files?.[0]?.name || copy.noFileSelected;
    });

    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      result.className = "result is-visible";
      result.innerHTML = "<strong>" + copy.importingTitle + "</strong><br />" + copy.importingBody;
      submitButton.disabled = true;
      const data = new FormData(form);
      try {
        const response = await fetch("/api/sessions/${session.sessionId}/import", {
          method: "POST",
          body: data
        });
        const payload = await response.json();
        if (!response.ok) {
          throw new Error(payload.message || copy.submitFailed);
        }
        result.className = "result is-visible is-success";
        const summary = copy.importSubmittedBodyTemplate
          .replace("__ACCEPTED__", String(payload.acceptedCount))
          .replace("__DUPLICATES__", String(payload.duplicateCountWithinPayload))
          .replace("__INVALID__", String(payload.invalidCount));
        result.innerHTML = "<strong>" + copy.importSubmittedTitle + "</strong><br />" + summary;
        form.reset();
        fileName.textContent = copy.noFileSelected;
      } catch (error) {
        result.className = "result is-visible is-error";
        result.textContent = error.message || copy.submitFailed;
      } finally {
        submitButton.disabled = false;
      }
    });
  </script>
</body>
</html>`;
}

function renderExportSessionPage(session, localeInfo) {
  const locale = localeInfo.locale;
  return `<!doctype html>
<html lang="${locale}">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>${t(locale, "export_page_title")}</title>
  <style>${baseStyles()}</style>
</head>
<body>
  <main class="shell">
    <section class="card card-hero">
      <div class="eyebrow">${t(locale, "brand_eyebrow")}</div>
      <div class="hero-topline">
        <h1>${t(locale, "export_page_heading")}</h1>
        <div class="badge">${t(locale, "badge_code", { shortCode: session.shortCode })}</div>
      </div>
      <p class="lede">${t(locale, "export_page_body")}</p>
      <div class="session-strip">
        <div>
          <div class="strip-label">${t(locale, "export_count_label")}</div>
          <div class="strip-value">${t(locale, "export_count_value", { count: session.outlineCount })}</div>
        </div>
        <div>
          <div class="strip-label">${t(locale, "export_type_label")}</div>
          <div class="strip-value">${t(locale, "export_type_value")}</div>
        </div>
      </div>
      ${renderLanguageSwitcher(`/export/${session.sessionId}`, locale)}
    </section>

    <section class="card card-form">
      <div class="stack">
        <div class="input-card">
          <div class="input-card-head">
            <div class="input-card-icon">OPML</div>
            <div>
              <strong>${t(locale, "export_download_title")}</strong>
              <p>${t(locale, "export_download_body")}</p>
            </div>
          </div>
          <a class="button-link" href="/api/export-sessions/${session.sessionId}/download">${t(locale, "download_opml")}</a>
        </div>
        <p class="footnote">${t(locale, "export_footnote")}</p>
      </div>
    </section>
  </main>
</body>
</html>`;
}

function renderMessagePage(title, message, localeInfo) {
  const locale = localeInfo.locale;
  return `<!doctype html>
<html lang="${locale}">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>${escapeHtml(title)}</title>
  <style>${baseStyles()}</style>
</head>
<body>
  <main class="shell">
    <section class="card">
      <div class="eyebrow">${t(locale, "brand_eyebrow")}</div>
      <h1>${escapeHtml(title)}</h1>
      <p>${escapeHtml(message)}</p>
      ${renderLanguageSwitcher("/", locale)}
      <a class="link" href="${localizedPath("/", localeInfo)}">${t(locale, "back_to_short_code")}</a>
    </section>
  </main>
</body>
</html>`;
}

function baseStyles() {
  return `
    :root {
      color-scheme: dark;
      --bg: #09080d;
      --surface: #16121c;
      --surface-soft: #221b2a;
      --surface-softer: #2d2436;
      --text: #f5f1fa;
      --muted: #a598b3;
      --accent: #ff7b5b;
      --accent-soft: rgba(255, 123, 91, 0.14);
      --success: #9df0b6;
      --error: #ffb3b3;
      --border: rgba(255,255,255,0.08);
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      min-height: 100vh;
      font-family: ui-rounded, -apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif;
      background:
        radial-gradient(circle at top, rgba(255, 123, 91, 0.28), transparent 28%),
        radial-gradient(circle at bottom right, rgba(255,255,255,0.08), transparent 22%),
        var(--bg);
      color: var(--text);
    }
    html {
      scroll-behavior: smooth;
    }
    .site-nav {
      position: sticky;
      top: 0;
      z-index: 10;
      padding: 14px 16px 0;
      background: linear-gradient(180deg, rgba(9, 8, 13, 0.92), rgba(9, 8, 13, 0.38), transparent);
      backdrop-filter: blur(18px);
    }
    .site-nav-inner {
      width: min(100%, 940px);
      margin: 0 auto;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 10px 14px;
      border-radius: 999px;
      border: 1px solid var(--border);
      background: rgba(20, 16, 24, 0.72);
      box-shadow: 0 16px 32px rgba(0,0,0,0.24);
    }
    .brand-link {
      color: var(--text);
      text-decoration: none;
      font-weight: 700;
      letter-spacing: 0.02em;
    }
    .nav-links {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
      justify-content: flex-end;
    }
    .nav-links a {
      color: var(--muted);
      text-decoration: none;
      font-size: 14px;
      padding: 10px 12px;
      border-radius: 999px;
      transition: background 160ms ease, color 160ms ease;
    }
    .nav-links a:hover,
    .nav-links a[aria-current="page"] {
      color: var(--text);
      background: rgba(255,255,255,0.06);
    }
    .lang-switch {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      margin-top: 16px;
      padding: 6px;
      border-radius: 999px;
      background: rgba(255,255,255,0.04);
      border: 1px solid rgba(255,255,255,0.08);
      width: fit-content;
    }
    .site-nav .lang-switch {
      margin-top: 0;
      margin-left: auto;
    }
    .lang-switch a {
      color: var(--muted);
      text-decoration: none;
      font-size: 13px;
      line-height: 1;
      padding: 8px 10px;
      border-radius: 999px;
      transition: background 160ms ease, color 160ms ease;
    }
    .lang-switch a:hover,
    .lang-switch a[aria-current="true"] {
      color: var(--text);
      background: rgba(255,255,255,0.08);
    }
    .shell {
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding: 24px;
      gap: 16px;
    }
    .shell-sections {
      place-items: start center;
      padding-top: 18px;
      padding-bottom: 40px;
    }
    .shell-wide {
      gap: 22px;
    }
    .card {
      width: min(100%, 1040px);
      background: linear-gradient(180deg, rgba(255,255,255,0.04), rgba(255,255,255,0.02));
      border: 1px solid var(--border);
      border-radius: 28px;
      padding: 24px;
      box-shadow: 0 24px 60px rgba(0,0,0,0.35);
      backdrop-filter: blur(20px);
    }
    .hero-layout,
    .split-band {
      width: min(100%, 1040px);
      display: grid;
      gap: 18px;
    }
    .hero-layout {
      grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
      align-items: stretch;
    }
    .hero-copy,
    .split-copy {
      min-height: 100%;
    }
    .hero-visual {
      position: relative;
      min-height: 520px;
      display: grid;
      align-items: center;
      justify-items: center;
    }
    .hero-device {
      display: grid;
      place-items: center;
    }
    .hero-device-primary {
      width: min(100%, 420px);
      aspect-ratio: 1;
    }
    .hero-device-secondary {
      position: absolute;
      left: 0;
      bottom: 12px;
      width: 180px;
      aspect-ratio: 1;
      transform: rotate(-10deg);
      opacity: 0.92;
    }
    .watch-stage {
      display: grid;
      place-items: center;
      padding: 10px 8px 4px;
      min-height: 248px;
      background:
        radial-gradient(circle at 50% 30%, rgba(255,123,91,0.08), transparent 50%),
        linear-gradient(180deg, rgba(255,255,255,0.03), rgba(255,255,255,0));
    }
    .watch-frame {
      position: relative;
      border-radius: 50%;
      overflow: hidden;
      background: #19161f;
      box-shadow:
        0 26px 60px rgba(0,0,0,0.42),
        inset 0 0 0 1px rgba(255,255,255,0.08);
    }
    .watch-frame::before {
      content: "";
      position: absolute;
      inset: 0;
      border-radius: 50%;
      background:
        radial-gradient(circle at 50% 18%, rgba(255,255,255,0.14), transparent 30%),
        radial-gradient(circle at 50% 50%, rgba(0,0,0,0), rgba(0,0,0,0.18) 74%, rgba(0,0,0,0.38) 100%);
      pointer-events: none;
      z-index: 2;
    }
    .watch-frame::after {
      content: "";
      position: absolute;
      inset: 10px;
      border-radius: 50%;
      box-shadow:
        inset 0 0 0 10px #454545,
        inset 0 0 0 15px #2a2a2f,
        inset 0 0 0 18px rgba(255,255,255,0.05);
      pointer-events: none;
      z-index: 3;
    }
    .watch-frame img {
      display: block;
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .watch-frame-large {
      width: min(100%, 420px);
      aspect-ratio: 1;
    }
    .watch-frame-medium {
      width: 180px;
      aspect-ratio: 1;
    }
    .watch-frame-card {
      width: min(100%, 230px);
      aspect-ratio: 1;
    }
    .card-hero {
      position: relative;
      overflow: hidden;
    }
    .footer-card {
      padding-top: 18px;
      padding-bottom: 18px;
    }
    .card-hero::after {
      content: "";
      position: absolute;
      inset: auto -40px -80px auto;
      width: 180px;
      height: 180px;
      background: radial-gradient(circle, rgba(255,123,91,0.22), transparent 68%);
      pointer-events: none;
    }
    .eyebrow {
      color: var(--accent);
      font-size: 13px;
      margin-bottom: 8px;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }
    h1 {
      margin: 0 0 10px;
      font-size: 30px;
      line-height: 1.1;
    }
    h2 {
      margin: 0;
      font-size: 24px;
      line-height: 1.15;
    }
    p {
      margin: 0 0 18px;
      line-height: 1.5;
      color: var(--muted);
    }
    .section-head {
      margin-bottom: 16px;
    }
    .section-head-compact {
      margin-bottom: 12px;
    }
    .section-title-block {
      display: grid;
      grid-template-columns: minmax(0, 0.9fr) minmax(260px, 0.7fr);
      gap: 18px;
      align-items: end;
      margin-bottom: 18px;
    }
    .section-copy {
      margin: 0;
      font-size: 14px;
      line-height: 1.65;
    }
    .lede {
      font-size: 15px;
      margin-bottom: 20px;
    }
    .hero-actions {
      display: flex;
      gap: 10px;
      margin-bottom: 18px;
      flex-wrap: wrap;
    }
    .trust-row {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
      margin-top: 8px;
    }
    .trust-pill {
      padding: 10px 14px;
      border-radius: 999px;
      border: 1px solid rgba(255,255,255,0.08);
      background: rgba(255,255,255,0.04);
      color: var(--text);
      font-size: 13px;
      box-shadow: inset 0 1px 0 rgba(255,255,255,0.04);
    }
    .stack {
      display: grid;
      gap: 16px;
    }
    .compact-stack {
      margin-top: 12px;
    }
    label {
      display: grid;
      gap: 8px;
      font-size: 14px;
      color: var(--text);
    }
    input {
      width: 100%;
      border-radius: 16px;
      border: 1px solid var(--border);
      background: var(--surface-soft);
      color: var(--text);
      padding: 14px 16px;
      font-size: 16px;
      transition: border-color 160ms ease, transform 160ms ease;
    }
    input:focus {
      outline: none;
      border-color: rgba(255, 123, 91, 0.5);
    }
    button,
    .button-link {
      border: 0;
      border-radius: 999px;
      padding: 16px 18px;
      background: linear-gradient(135deg, #ff9a84, var(--accent));
      color: #140d14;
      font-size: 16px;
      font-weight: 700;
      cursor: pointer;
      box-shadow: 0 14px 28px rgba(255,123,91,0.28);
      transition: transform 160ms ease, box-shadow 160ms ease, opacity 160ms ease;
      text-decoration: none;
      text-align: center;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
    .button-link-secondary {
      background: rgba(255,255,255,0.06);
      color: var(--text);
      box-shadow: none;
    }
    button:hover,
    .button-link:hover {
      transform: translateY(-1px);
      box-shadow: 0 18px 34px rgba(255,123,91,0.32);
    }
    button:disabled {
      opacity: 0.7;
      cursor: progress;
    }
    .badge {
      display: inline-flex;
      align-items: center;
      padding: 8px 12px;
      border-radius: 999px;
      background: var(--accent-soft);
      color: var(--text);
      font-size: 13px;
      border: 1px solid rgba(255,123,91,0.22);
    }
    .result {
      display: none;
      margin-top: 18px;
      padding: 16px 18px;
      border-radius: 22px;
      background: rgba(255,255,255,0.03);
      border: 1px solid var(--border);
      font-size: 14px;
      line-height: 1.5;
      color: var(--muted);
    }
    .result.is-visible {
      display: block;
    }
    .result.is-success {
      background: rgba(157, 240, 182, 0.08);
      border-color: rgba(157, 240, 182, 0.2);
      color: var(--text);
    }
    .result.is-error {
      background: rgba(255, 123, 123, 0.08);
      border-color: rgba(255, 123, 123, 0.18);
      color: var(--error);
    }
    .link {
      color: var(--accent);
      text-decoration: none;
    }
    .hero-grid {
      display: grid;
      gap: 10px;
      margin-bottom: 22px;
    }
    .value-grid {
      display: grid;
      gap: 10px;
      margin-top: 8px;
    }
    .value-card {
      padding: 14px 16px;
      border-radius: 20px;
      background: rgba(255,255,255,0.035);
      border: 1px solid var(--border);
    }
    .value-card strong {
      display: block;
      font-size: 15px;
      margin-bottom: 6px;
    }
    .hero-step {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 12px;
      align-items: start;
      padding: 14px 16px;
      border-radius: 20px;
      background: rgba(255,255,255,0.035);
      border: 1px solid var(--border);
    }
    .hero-step strong,
    .input-card strong {
      display: block;
      font-size: 15px;
      margin-bottom: 4px;
    }
    .hero-step p,
    .input-card p {
      margin: 0;
      font-size: 13px;
    }
    .step-index,
    .input-card-icon {
      width: 34px;
      height: 34px;
      border-radius: 999px;
      display: grid;
      place-items: center;
      background: var(--accent-soft);
      color: var(--text);
      font-size: 12px;
      font-weight: 700;
      border: 1px solid rgba(255,123,91,0.18);
      flex-shrink: 0;
    }
    .preview-grid {
      display: grid;
      gap: 12px;
    }
    .preview-grid-wide {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }
    .preview-card {
      margin: 0;
      border-radius: 22px;
      overflow: hidden;
      background: rgba(255,255,255,0.04);
      border: 1px solid rgba(255,255,255,0.08);
    }
    .preview-card figcaption {
      display: grid;
      gap: 4px;
      padding: 14px 16px 16px;
    }
    .preview-card figcaption strong {
      font-size: 15px;
    }
    .preview-card figcaption span {
      color: var(--muted);
      font-size: 13px;
      line-height: 1.5;
    }
    .hero-topline {
      display: flex;
      gap: 12px;
      justify-content: space-between;
      align-items: start;
      margin-bottom: 8px;
    }
    .hero-topline h1 {
      margin-bottom: 0;
      max-width: 220px;
    }
    .session-strip {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 12px;
      margin-top: 8px;
      padding-top: 18px;
    }
    .session-strip > div {
      border-radius: 18px;
      background: rgba(255,255,255,0.03);
      border: 1px solid var(--border);
      padding: 14px;
    }
    .strip-label {
      font-size: 12px;
      color: var(--muted);
      margin-bottom: 6px;
    }
    .strip-value {
      font-size: 14px;
      color: var(--text);
      font-weight: 600;
    }
    .card-form {
      background: linear-gradient(180deg, rgba(255,255,255,0.035), rgba(255,255,255,0.018));
    }
    .split-action {
      align-self: stretch;
      display: grid;
      align-content: start;
    }
    .input-card {
      padding: 18px;
      border-radius: 24px;
      background: rgba(255,255,255,0.03);
      border: 1px solid var(--border);
    }
    .input-card-head {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 12px;
      align-items: center;
      margin-bottom: 14px;
    }
    .divider {
      position: relative;
      text-align: center;
      color: var(--muted);
      font-size: 12px;
    }
    .divider::before {
      content: "";
      position: absolute;
      top: 50%;
      left: 0;
      right: 0;
      height: 1px;
      background: var(--border);
    }
    .divider span {
      position: relative;
      padding: 0 10px;
      background: #17131c;
    }
    .file-picker {
      position: relative;
      display: grid;
      gap: 10px;
      padding: 14px 16px;
      border-radius: 18px;
      background: var(--surface-soft);
      border: 1px dashed rgba(255,255,255,0.12);
      overflow: hidden;
      cursor: pointer;
    }
    .file-picker input {
      position: absolute;
      inset: 0;
      opacity: 0;
      cursor: pointer;
    }
    .file-picker span {
      font-weight: 600;
    }
    .file-picker em {
      font-style: normal;
      color: var(--muted);
      font-size: 13px;
    }
    .footnote {
      margin-top: 14px;
      margin-bottom: 0;
      font-size: 13px;
      text-align: center;
    }
    .footer-grid {
      display: grid;
      gap: 14px;
    }
    .footer-grid-wide {
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: center;
    }
    .footer-actions {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
      justify-content: flex-end;
    }
    .commerce-footer {
      overflow: hidden;
    }
    .footer-copy {
      max-width: 46ch;
    }
    .policy-stack {
      display: grid;
      gap: 10px;
    }
    .footer-links {
      display: flex;
      flex-wrap: wrap;
      gap: 8px 14px;
    }
    @media (max-width: 480px) {
      .site-nav {
        padding: 10px 10px 0;
      }
      .site-nav-inner {
        align-items: start;
        flex-direction: column;
        border-radius: 24px;
      }
      .nav-links {
        width: 100%;
        justify-content: flex-start;
      }
      .shell {
        padding: 16px;
      }
      .hero-layout,
      .split-band,
      .section-title-block,
      .preview-grid-wide,
      .footer-grid-wide {
        grid-template-columns: 1fr;
      }
      .hero-visual {
        min-height: auto;
      }
      .hero-device-primary {
        width: 100%;
        max-width: 360px;
      }
      .hero-device-secondary {
        position: relative;
        left: auto;
        bottom: auto;
        width: 52%;
        justify-self: start;
        margin-top: -42px;
        transform: rotate(-8deg);
      }
      .card {
        padding: 20px;
      }
      h1 {
        font-size: 26px;
      }
      h2 {
        font-size: 22px;
      }
      .hero-actions {
        display: grid;
      }
      .hero-topline {
        flex-direction: column;
      }
      .hero-topline h1 {
        max-width: none;
      }
      .footer-actions {
        justify-content: flex-start;
      }
      .session-strip {
        grid-template-columns: 1fr;
      }
    }
  `;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}
