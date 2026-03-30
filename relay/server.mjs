import crypto from "node:crypto";
import express from "express";
import multer from "multer";
import { XMLParser } from "fast-xml-parser";

const app = express();
const port = Number(process.env.PORT || 8787);
const publicBaseUrl = (process.env.PUBLIC_BASE_URL || `http://localhost:${port}`).replace(/\/$/, "");
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

app.use(express.json({ limit: "2mb" }));
app.use(express.urlencoded({ extended: true }));

app.get("/", (_req, res) => {
  res.type("html").send(renderLandingPage());
});

app.post("/enter-code", (req, res) => {
  const shortCode = String(req.body.shortCode || "").trim().toUpperCase();
  const sessionId = shortCodeIndex.get(shortCode);
  if (sessionId) {
    res.redirect(`/session/${sessionId}`);
    return;
  }
  const exportSessionId = exportShortCodeIndex.get(shortCode);
  if (exportSessionId) {
    res.redirect(`/export/${exportSessionId}`);
    return;
  }
  if (!sessionId) {
    res.status(404).type("html").send(renderMessagePage("短码不存在", "请重新扫描手表上的二维码。"));
    return;
  }
});

app.get("/session/:sessionId", (req, res) => {
  const session = getSession(req.params.sessionId);
  if (!session) {
    res.status(404).type("html").send(renderMessagePage("会话不存在", "请在手表上重新生成二维码。"));
    return;
  }
  if (expireIfNeeded(session)) {
    res.status(410).type("html").send(renderMessagePage("二维码已过期", "请在手表上重新生成二维码。"));
    return;
  }
  res.type("html").send(renderSessionPage(session));
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
  const session = getExportSession(req.params.sessionId);
  if (!session) {
    res.status(404).type("html").send(renderMessagePage("导出会话不存在", "请在手表上重新生成导出二维码。"));
    return;
  }
  if (expireIfNeeded(session)) {
    res.status(410).type("html").send(renderMessagePage("二维码已过期", "请在手表上重新生成导出二维码。"));
    return;
  }
  res.type("html").send(renderExportSessionPage(session));
});

app.get("/api/export-sessions/:sessionId/download", (req, res) => {
  const session = getExportSession(req.params.sessionId);
  if (!session) {
    res.status(404).type("html").send(renderMessagePage("导出会话不存在", "请在手表上重新生成导出二维码。"));
    return;
  }
  if (expireIfNeeded(session)) {
    res.status(410).type("html").send(renderMessagePage("二维码已过期", "请在手表上重新生成导出二维码。"));
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

function renderLandingPage() {
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>WearPod 手机导入</title>
  <style>${baseStyles()}</style>
</head>
<body>
  <main class="shell">
    <section class="card card-hero">
      <div class="eyebrow">WearPod</div>
      <h1>在手机上完成订阅导入</h1>
      <p class="lede">手表负责播放和管理，手机只用来完成低频输入。</p>
      <div class="hero-grid">
        <div class="hero-step">
          <div class="step-index">1</div>
          <div>
            <strong>扫描手表二维码</strong>
            <p>进入本次导入会话</p>
          </div>
        </div>
        <div class="hero-step">
          <div class="step-index">2</div>
          <div>
            <strong>填写 RSS 或上传 OPML</strong>
            <p>手机输入更轻松</p>
          </div>
        </div>
        <div class="hero-step">
          <div class="step-index">3</div>
          <div>
            <strong>回到手表确认导入</strong>
            <p>真正导入仍由手表完成</p>
          </div>
        </div>
      </div>
      <form method="post" action="/enter-code" class="stack compact-stack">
        <label>
          短码进入
          <input name="shortCode" maxlength="6" placeholder="输入 6 位短码" autocomplete="off" />
        </label>
        <button type="submit">继续到导入页</button>
      </form>
    </section>
  </main>
</body>
</html>`;
}

function renderSessionPage(session) {
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>WearPod 导入订阅</title>
  <style>${baseStyles()}</style>
</head>
<body>
  <main class="shell">
    <section class="card card-hero">
      <div class="eyebrow">WearPod</div>
      <div class="hero-topline">
        <h1>导入到手表</h1>
        <div class="badge">短码 ${session.shortCode}</div>
      </div>
      <p class="lede">你可以贴一个 RSS 地址，或者直接上传 OPML。手表会在确认后完成真正导入。</p>
      <div class="session-strip">
        <div>
          <div class="strip-label">目标设备</div>
          <div class="strip-value">WearPod on Wear OS</div>
        </div>
        <div>
          <div class="strip-label">会话状态</div>
          <div class="strip-value">等待手机提交</div>
        </div>
      </div>
    </section>

    <section class="card card-form">
      <form id="import-form" class="stack">
        <div class="input-card">
          <div class="input-card-head">
            <div class="input-card-icon">RSS</div>
            <div>
              <strong>导入单个订阅地址</strong>
              <p>适合快速导入一档播客</p>
            </div>
          </div>
          <label>
            RSS 地址
            <input name="rssUrl" placeholder="https://example.com/feed.xml" autocomplete="off" />
          </label>
        </div>

        <div class="divider"><span>或</span></div>

        <div class="input-card">
          <div class="input-card-head">
            <div class="input-card-icon">OPML</div>
            <div>
              <strong>批量导入 OPML</strong>
              <p>适合从其他播客客户端迁移订阅</p>
            </div>
          </div>
          <label class="file-picker">
            <span>选择 OPML 文件</span>
            <input id="opml-input" name="opml" type="file" accept=".opml,.xml,text/xml,application/xml" />
            <em id="file-name">未选择文件</em>
          </label>
        </div>

        <button id="submit-button" type="submit">提交到手表</button>
      </form>
      <div id="result" class="result"></div>
      <p class="footnote">提交后，回到手表确认导入即可。</p>
    </section>
  </main>
  <script>
    const form = document.getElementById("import-form");
    const result = document.getElementById("result");
    const fileInput = document.getElementById("opml-input");
    const fileName = document.getElementById("file-name");
    const submitButton = document.getElementById("submit-button");

    fileInput.addEventListener("change", () => {
      fileName.textContent = fileInput.files?.[0]?.name || "未选择文件";
    });

    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      result.className = "result is-visible";
      result.innerHTML = "<strong>正在提交到手表...</strong><br />请稍等片刻。";
      submitButton.disabled = true;
      const data = new FormData(form);
      try {
        const response = await fetch("/api/sessions/${session.sessionId}/import", {
          method: "POST",
          body: data
        });
        const payload = await response.json();
        if (!response.ok) {
          throw new Error(payload.message || "提交失败");
        }
        result.className = "result is-visible is-success";
        result.innerHTML = \`
          <strong>已提交到手表</strong><br />
          新增候选 \${payload.acceptedCount} 个，重复 \${payload.duplicateCountWithinPayload} 个，无效 \${payload.invalidCount} 个。现在回到手表确认导入即可。
        \`;
        form.reset();
        fileName.textContent = "未选择文件";
      } catch (error) {
        result.className = "result is-visible is-error";
        result.textContent = error.message || "提交失败";
      } finally {
        submitButton.disabled = false;
      }
    });
  </script>
</body>
</html>`;
}

function renderExportSessionPage(session) {
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>WearPod 导出订阅</title>
  <style>${baseStyles()}</style>
</head>
<body>
  <main class="shell">
    <section class="card card-hero">
      <div class="eyebrow">WearPod</div>
      <div class="hero-topline">
        <h1>导出订阅备份</h1>
        <div class="badge">短码 ${session.shortCode}</div>
      </div>
      <p class="lede">你现在可以把手表里的订阅导出成一个 OPML 文件，保存到手机或分享到其他播客应用。</p>
      <div class="session-strip">
        <div>
          <div class="strip-label">订阅数量</div>
          <div class="strip-value">${session.outlineCount} 个</div>
        </div>
        <div>
          <div class="strip-label">文件类型</div>
          <div class="strip-value">OPML</div>
        </div>
      </div>
    </section>

    <section class="card card-form">
      <div class="stack">
        <div class="input-card">
          <div class="input-card-head">
            <div class="input-card-icon">OPML</div>
            <div>
              <strong>下载订阅备份</strong>
              <p>文件会直接保存到手机浏览器的下载目录。</p>
            </div>
          </div>
          <a class="button-link" href="/api/export-sessions/${session.sessionId}/download">下载 OPML</a>
        </div>
        <p class="footnote">如果想迁移到其他客户端，通常直接导入这个 OPML 文件即可。</p>
      </div>
    </section>
  </main>
</body>
</html>`;
}

function renderMessagePage(title, message) {
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>${escapeHtml(title)}</title>
  <style>${baseStyles()}</style>
</head>
<body>
  <main class="shell">
    <section class="card">
      <div class="eyebrow">WearPod</div>
      <h1>${escapeHtml(title)}</h1>
      <p>${escapeHtml(message)}</p>
      <a class="link" href="/">返回输入短码</a>
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
    .shell {
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding: 24px;
      gap: 16px;
    }
    .card {
      width: min(100%, 420px);
      background: linear-gradient(180deg, rgba(255,255,255,0.04), rgba(255,255,255,0.02));
      border: 1px solid var(--border);
      border-radius: 28px;
      padding: 24px;
      box-shadow: 0 24px 60px rgba(0,0,0,0.35);
      backdrop-filter: blur(20px);
    }
    .card-hero {
      position: relative;
      overflow: hidden;
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
    p {
      margin: 0 0 18px;
      line-height: 1.5;
      color: var(--muted);
    }
    .lede {
      font-size: 15px;
      margin-bottom: 20px;
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
    @media (max-width: 480px) {
      .shell {
        padding: 16px;
      }
      .card {
        padding: 20px;
      }
      h1 {
        font-size: 26px;
      }
      .hero-topline {
        flex-direction: column;
      }
      .hero-topline h1 {
        max-width: none;
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
