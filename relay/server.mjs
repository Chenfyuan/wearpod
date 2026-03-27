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
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 1024 * 1024 },
});
const xmlParser = new XMLParser({
  ignoreAttributes: false,
  attributeNamePrefix: "",
});

app.use(express.json());
app.use(express.urlencoded({ extended: true }));

app.get("/", (_req, res) => {
  res.type("html").send(renderLandingPage());
});

app.post("/enter-code", (req, res) => {
  const shortCode = String(req.body.shortCode || "").trim().toUpperCase();
  const sessionId = shortCodeIndex.get(shortCode);
  if (!sessionId) {
    res.status(404).type("html").send(renderMessagePage("短码不存在", "请重新扫描手表上的二维码。"));
    return;
  }
  res.redirect(`/session/${sessionId}`);
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

app.get("/api/sessions/:sessionId", (req, res) => {
  const session = getSession(req.params.sessionId);
  if (!session) {
    res.status(404).json({ message: "session_not_found" });
    return;
  }
  expireIfNeeded(session);
  res.json(watchSessionPayload(session));
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

function getSession(sessionId) {
  return sessions.get(sessionId);
}

function expireIfNeeded(session) {
  if (session.status === "EXPIRED") {
    return true;
  }
  if (Date.now() < session.expiresAtEpochMillis) {
    return false;
  }
  session.status = "EXPIRED";
  session.feedUrls = [];
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
    <section class="card">
      <div class="eyebrow">WearPod</div>
      <h1>在手机上继续导入订阅</h1>
      <p>扫描手表上的二维码，或输入手表显示的 6 位短码。</p>
      <form method="post" action="/enter-code" class="stack">
        <input name="shortCode" maxlength="6" placeholder="输入短码" autocomplete="off" />
        <button type="submit">继续</button>
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
    <section class="card">
      <div class="eyebrow">WearPod</div>
      <h1>导入到手表</h1>
      <p>你也可以上传 OPML。手表会在确认后完成真正导入。</p>
      <div class="badge">短码 ${session.shortCode}</div>
      <form id="import-form" class="stack">
        <label>
          RSS 地址
          <input name="rssUrl" placeholder="https://example.com/feed.xml" autocomplete="off" />
        </label>
        <label>
          OPML 文件
          <input name="opml" type="file" accept=".opml,.xml,text/xml,application/xml" />
        </label>
        <button type="submit">提交到手表</button>
      </form>
      <div id="result" class="result"></div>
    </section>
  </main>
  <script>
    const form = document.getElementById("import-form");
    const result = document.getElementById("result");
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      result.textContent = "正在提交...";
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
        result.innerHTML = \`<strong>已提交到手表</strong><br />新增候选 \${payload.acceptedCount} 个，重复 \${payload.duplicateCountWithinPayload} 个，无效 \${payload.invalidCount} 个。现在回到手表确认导入即可。\`;
        form.reset();
      } catch (error) {
        result.textContent = error.message || "提交失败";
      }
    });
  </script>
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
      --text: #f5f1fa;
      --muted: #a598b3;
      --accent: #ff7b5b;
      --border: rgba(255,255,255,0.08);
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      min-height: 100vh;
      font-family: ui-rounded, -apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif;
      background:
        radial-gradient(circle at top, rgba(255, 123, 91, 0.22), transparent 30%),
        var(--bg);
      color: var(--text);
    }
    .shell {
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding: 24px;
    }
    .card {
      width: min(100%, 420px);
      background: linear-gradient(180deg, rgba(255,255,255,0.04), rgba(255,255,255,0.02));
      border: 1px solid var(--border);
      border-radius: 28px;
      padding: 24px;
      box-shadow: 0 24px 60px rgba(0,0,0,0.35);
    }
    .eyebrow {
      color: var(--accent);
      font-size: 13px;
      margin-bottom: 8px;
    }
    h1 {
      margin: 0 0 10px;
      font-size: 28px;
      line-height: 1.1;
    }
    p {
      margin: 0 0 18px;
      line-height: 1.5;
      color: var(--muted);
    }
    .stack {
      display: grid;
      gap: 14px;
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
    }
    button {
      border: 0;
      border-radius: 999px;
      padding: 14px 18px;
      background: var(--accent);
      color: #140d14;
      font-size: 16px;
      font-weight: 700;
      cursor: pointer;
    }
    .badge {
      display: inline-flex;
      align-items: center;
      padding: 8px 12px;
      margin-bottom: 18px;
      border-radius: 999px;
      background: rgba(255,123,91,0.16);
      color: var(--text);
      font-size: 13px;
    }
    .result {
      margin-top: 16px;
      font-size: 14px;
      line-height: 1.5;
      color: var(--muted);
    }
    .link {
      color: var(--accent);
      text-decoration: none;
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
