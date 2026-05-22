const state = {
  token: localStorage.getItem("novahub.token") || "",
  user: JSON.parse(localStorage.getItem("novahub.user") || "null"),
  authMode: "login",
  feedType: "latest",
  activeContent: null
};

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => Array.from(document.querySelectorAll(selector));

function saveSession(auth) {
  state.token = auth.token;
  state.user = {
    id: auth.userId,
    username: auth.username,
    nickname: auth.nickname || auth.username
  };
  localStorage.setItem("novahub.token", state.token);
  localStorage.setItem("novahub.user", JSON.stringify(state.user));
  renderAuth();
}

function clearSession() {
  state.token = "";
  state.user = null;
  localStorage.removeItem("novahub.token");
  localStorage.removeItem("novahub.user");
  renderAuth();
}

function toast(message) {
  const el = $("#toast");
  el.textContent = message;
  el.classList.add("show");
  window.clearTimeout(toast.timer);
  toast.timer = window.setTimeout(() => el.classList.remove("show"), 2800);
}

async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (!headers.has("Content-Type") && options.body) {
    headers.set("Content-Type", "application/json");
  }
  if (state.token) {
    headers.set("Authorization", `Bearer ${state.token}`);
  }

  const response = await fetch(path, { ...options, headers });
  const contentType = response.headers.get("content-type") || "";
  const payload = contentType.includes("application/json") ? await response.json() : await response.text();

  if (!response.ok) {
    const message = payload && payload.message ? payload.message : `请求失败 ${response.status}`;
    throw new Error(message);
  }
  if (payload && typeof payload === "object" && "code" in payload && payload.code !== 200) {
    throw new Error(payload.message || "请求失败");
  }
  return payload && typeof payload === "object" && "data" in payload ? payload.data : payload;
}

function escapeHtml(value) {
  return text(value, "").replace(/[&<>"']/g, (ch) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;"
  }[ch]));
}

function text(value, fallback = "未命名") {
  return value === null || value === undefined || value === "" ? fallback : String(value);
}

function formatTime(value) {
  if (!value) return "刚刚";
  if (typeof value === "number") {
    return new Date(value).toLocaleString();
  }
  return value;
}

function itemId(item) {
  return item.id || item.contentId;
}

function statusText(status) {
  return ({
    0: "草稿",
    1: "待审核",
    2: "已发布",
    3: "已下架"
  })[status] || "未知状态";
}

function reviewText(status) {
  return ({
    0: "审核中",
    1: "审核通过",
    2: "审核拒绝"
  })[status] || "未提交";
}

function renderTags(tags = []) {
  return tags.length
    ? `<div class="card-tags">${tags.map((tag) => `<span>${escapeHtml(tag.name)}</span>`).join("")}</div>`
    : "";
}

function renderCard(item) {
  const id = itemId(item);
  const title = escapeHtml(item.title || "未命名内容");
  const summary = escapeHtml(item.summary || item.content || "暂无摘要");
  const author = escapeHtml(item.authorNickname || "匿名作者");
  const cover = item.coverUrl ? `<img class="cover" src="${escapeHtml(item.coverUrl)}" alt="${title}">` : "";
  const type = (item.type || item.contentType) === 2 ? "视频" : "帖子";

  return `
    <article class="content-card">
      ${cover}
      <div class="meta-line">
        <span>${type}</span>
        <span>${author}</span>
        <span>${formatTime(item.createTime || item.publishTimestamp)}</span>
        ${item.status !== undefined ? `<span class="status-badge">${statusText(item.status)}</span>` : ""}
      </div>
      <h2>${title}</h2>
      <p>${summary}</p>
      ${renderTags(item.tags)}
      <div class="metrics">
        <span class="metric">赞 ${item.likeCount || 0}</span>
        <span class="metric">藏 ${item.collectCount || 0}</span>
        <span class="metric">评 ${item.commentCount || 0}</span>
        <span class="metric">看 ${item.viewCount || 0}</span>
      </div>
      ${id ? `<button class="ghost-action" type="button" data-open="${id}">查看详情</button>` : ""}
    </article>
  `;
}

function empty(message) {
  return `<div class="empty-state">${escapeHtml(message)}</div>`;
}

async function loadHealth() {
  try {
    const data = await api("/actuator/health");
    $("#healthState").textContent = data.status || "UP";
  } catch {
    $("#healthState").textContent = "不可用";
  }
}

async function loadMe() {
  if (!state.token) {
    renderProfile();
    return;
  }
  try {
    const user = await api("/api/users/me");
    state.user = {
      id: user.id,
      username: user.username,
      nickname: user.nickname || user.username,
      followCount: user.followCount || 0,
      fansCount: user.fansCount || 0,
      worksCount: user.worksCount || 0,
      bio: user.bio || ""
    };
    localStorage.setItem("novahub.user", JSON.stringify(state.user));
  } catch (error) {
    toast(error.message);
  }
  renderAuth();
  renderProfile();
}

function renderAuth() {
  const signedIn = Boolean(state.token && state.user);
  $("#signedOut").classList.toggle("hidden", signedIn);
  $("#signedIn").classList.toggle("hidden", !signedIn);

  if (signedIn) {
    const display = state.user.nickname || state.user.username;
    $("#profileName").textContent = display;
    $("#profileMeta").textContent = `@${state.user.username || state.user.id}`;
    $("#profileAvatar").textContent = display.slice(0, 1).toUpperCase();
  }
}

function renderProfile() {
  const target = $("#profileDetail");
  if (!state.user) {
    target.innerHTML = empty("登录后可以查看个人资料、作品数和账号状态。");
    return;
  }
  target.innerHTML = `
    <div class="profile-tile"><span>昵称</span><strong>${escapeHtml(state.user.nickname)}</strong></div>
    <div class="profile-tile"><span>用户名</span><strong>${escapeHtml(state.user.username)}</strong></div>
    <div class="profile-tile"><span>作品</span><strong>${state.user.worksCount || 0}</strong></div>
    <div class="profile-tile"><span>关注</span><strong>${state.user.followCount || 0}</strong></div>
    <div class="profile-tile"><span>粉丝</span><strong>${state.user.fansCount || 0}</strong></div>
    <div class="profile-tile wide"><span>简介</span><strong>${escapeHtml(state.user.bio || "暂无")}</strong></div>
  `;
}

async function loadFeed() {
  const target = $("#feedList");
  target.innerHTML = empty("正在加载内容流...");
  try {
    let items = [];
    if (state.feedType === "latest") {
      const data = await api("/api/contents?page=1&pageSize=20");
      items = data.records || [];
    } else if (state.feedType === "hot") {
      items = await api("/api/feed/hot?pageSize=20");
    } else {
      items = await api("/api/feed/recommend?pageSize=20");
    }
    target.innerHTML = items.length ? items.map(renderCard).join("") : empty("还没有公开内容。导入演示数据或发布审核通过后会显示在这里。");
  } catch (error) {
    target.innerHTML = empty(error.message);
  }
}

async function loadTags() {
  try {
    const tags = await api("/api/tags/hot?limit=12");
    $("#tagCloud").innerHTML = tags.length
      ? tags.map((tag) => `<span class="tag-pill" data-tag-id="${tag.id}">${escapeHtml(tag.name)}</span>`).join("")
      : empty("暂无标签");
    $("#tagSelect").innerHTML = tags.map((tag) => `<option value="${tag.id}">${escapeHtml(tag.name)}</option>`).join("");
  } catch (error) {
    $("#tagCloud").innerHTML = empty(error.message);
  }
}

async function loadHotRank() {
  try {
    const items = await api("/api/hotrank/all?limit=8");
    $("#hotList").innerHTML = items.length
      ? items.map((item) => {
        const id = item.contentId || item.id;
        return `
          <button class="mini-item mini-button" type="button" data-open="${id}">
            <strong>#${item.rank || "-"} 内容 ${id}</strong>
            <span>热度 ${Number(item.heatScore || 0).toFixed(1)} · 赞 ${item.likeCount || 0} · 评 ${item.commentCount || 0}</span>
          </button>
        `;
      }).join("")
      : empty("暂无热榜数据");
  } catch (error) {
    $("#hotList").innerHTML = empty(error.message);
  }
}

async function search(keyword) {
  const target = $("#searchResults");
  if (!keyword.trim()) {
    target.innerHTML = "";
    return;
  }
  target.innerHTML = empty("搜索中...");
  try {
    const data = await api(`/api/search?keyword=${encodeURIComponent(keyword)}&page=1&pageSize=8`);
    const records = data.records || [];
    target.innerHTML = records.length
      ? records.map((item) => {
        const id = itemId(item);
        return `
          <button class="mini-item mini-button" type="button" ${id ? `data-open="${id}"` : ""}>
            <strong>${escapeHtml(item.title || "未命名内容")}</strong>
            <span>${escapeHtml(item.summary || item.content || "暂无摘要")}</span>
          </button>
        `;
      }).join("")
      : empty("没有搜到相关内容");
  } catch (error) {
    target.innerHTML = empty(error.message);
  }
}

async function loadDrafts() {
  const target = $("#draftList");
  if (!state.token) {
    target.innerHTML = empty("请先登录再查看草稿。");
    return;
  }
  target.innerHTML = empty("正在加载草稿...");
  try {
    const data = await api("/api/contents/drafts");
    const records = data.records || [];
    target.innerHTML = records.length ? records.map(renderCard).join("") : empty("暂时没有草稿。");
  } catch (error) {
    target.innerHTML = empty(error.message);
  }
}

function renderComments(comments = []) {
  if (!comments.length) {
    return empty("还没有评论，登录后可以抢沙发。");
  }
  return comments.map((comment) => `
    <article class="comment-item">
      <div>
        <strong>${escapeHtml(comment.nickname || comment.username || `用户 ${comment.userId}`)}</strong>
        <span>${formatTime(comment.createTime)}</span>
      </div>
      <p>${escapeHtml(comment.content)}</p>
      ${(comment.replies || []).map((reply) => `
        <div class="comment-reply">
          <strong>${escapeHtml(reply.nickname || reply.username || `用户 ${reply.userId}`)}</strong>
          <p>${escapeHtml(reply.content)}</p>
        </div>
      `).join("")}
    </article>
  `).join("");
}

async function refreshDetail(contentId) {
  const content = await api(`/api/contents/${contentId}`);
  state.activeContent = content;
  const [comments, liked, collected] = await Promise.all([
    api(`/api/contents/${contentId}/comments?limit=20`),
    api(`/api/contents/${contentId}/like-status`).catch(() => false),
    state.token ? api(`/api/contents/${contentId}/collect-status`).catch(() => false) : false
  ]);
  content.isLiked = Boolean(liked);
  content.isCollected = Boolean(collected);
  $("#detailTitle").textContent = content.title || "未命名内容";
  $("#detailMeta").textContent = `${content.authorNickname || "匿名作者"} · ${formatTime(content.publishTime || content.createTime)}`;
  $("#detailBody").innerHTML = `
    ${content.coverUrl ? `<img class="cover" src="${escapeHtml(content.coverUrl)}" alt="${escapeHtml(content.title)}">` : ""}
    <div class="detail-status">
      <span class="status-badge">${statusText(content.status)}</span>
      <span class="status-badge soft">${reviewText(content.reviewStatus)}</span>
      ${content.reviewRemark ? `<span>${escapeHtml(content.reviewRemark)}</span>` : ""}
    </div>
    <p>${escapeHtml(content.content || "暂无正文")}</p>
    ${renderTags(content.tags)}
    <div class="metrics">
      <span class="metric">赞 ${content.likeCount || 0}</span>
      <span class="metric">藏 ${content.collectCount || 0}</span>
      <span class="metric">评 ${content.commentCount || 0}</span>
      <span class="metric">看 ${content.viewCount || 0}</span>
    </div>
  `;
  $("#likeBtn").textContent = content.isLiked ? "取消点赞" : "点赞";
  $("#collectBtn").textContent = content.isCollected ? "取消收藏" : "收藏";
  $("#commentList").innerHTML = renderComments(comments);
}

async function openDetail(contentId) {
  $("#detailDialog").classList.remove("hidden");
  $("#detailBody").innerHTML = empty("正在加载详情...");
  $("#commentList").innerHTML = "";
  try {
    await refreshDetail(contentId);
  } catch (error) {
    $("#detailBody").innerHTML = empty(error.message);
  }
}

function closeDetail() {
  $("#detailDialog").classList.add("hidden");
  state.activeContent = null;
}

function requireLogin(actionName) {
  if (state.token) return true;
  toast(`请先登录再${actionName}`);
  return false;
}

function switchView(view) {
  $$(".nav-tab").forEach((btn) => btn.classList.toggle("active", btn.dataset.view === view));
  $$(".view-section").forEach((section) => section.classList.remove("active"));
  $(`#${view}View`).classList.add("active");
  if (view === "profile") loadMe();
  if (view === "publish") loadDrafts();
}

function bindEvents() {
  $$(".nav-tab").forEach((btn) => {
    btn.addEventListener("click", () => switchView(btn.dataset.view));
  });

  $("#loginMode").addEventListener("click", () => setAuthMode("login"));
  $("#registerMode").addEventListener("click", () => setAuthMode("register"));
  $("#demoFillBtn").addEventListener("click", () => {
    setAuthMode("login");
    $("#username").value = "demo_user";
    $("#password").value = "123456";
    toast("已填入演示账号");
  });

  $("#authForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const payload = {
      username: $("#username").value.trim(),
      password: $("#password").value
    };
    const endpoint = state.authMode === "register" ? "/api/auth/register" : "/api/auth/login";
    if (state.authMode === "register") {
      payload.nickname = $("#nickname").value.trim() || payload.username;
      payload.email = $("#email").value.trim();
    }

    try {
      const auth = await api(endpoint, { method: "POST", body: JSON.stringify(payload) });
      saveSession(auth);
      toast(state.authMode === "register" ? "注册成功，已登录" : "登录成功");
      await Promise.all([loadMe(), loadFeed(), loadDrafts()]);
    } catch (error) {
      toast(error.message);
    }
  });

  $("#logoutBtn").addEventListener("click", async () => {
    try {
      if (state.token) await api("/api/auth/logout", { method: "POST" });
    } catch {
      // Local logout should still work if the token has already expired.
    }
    clearSession();
    renderProfile();
    toast("已退出登录");
  });

  $$(".chip").forEach((btn) => {
    btn.addEventListener("click", () => {
      $$(".chip").forEach((chip) => chip.classList.remove("active"));
      btn.classList.add("active");
      state.feedType = btn.dataset.feedType;
      loadFeed();
    });
  });

  $("#publishForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!requireLogin("发布内容")) return;

    const submitter = event.submitter;
    const tagIds = Array.from($("#tagSelect").selectedOptions).map((option) => Number(option.value));
    const payload = {
      title: $("#postTitle").value.trim(),
      content: $("#postContent").value.trim(),
      type: Number($("#postType").value),
      coverUrl: $("#coverUrl").value.trim(),
      tagIds,
      status: Number(submitter.dataset.status)
    };

    try {
      await api("/api/contents", { method: "POST", body: JSON.stringify(payload) });
      $("#publishForm").reset();
      toast(payload.status === 0 ? "草稿已保存" : "已提交审核");
      await Promise.all([loadDrafts(), loadFeed()]);
    } catch (error) {
      toast(error.message);
    }
  });

  $("#loadDraftsBtn").addEventListener("click", loadDrafts);
  $("#searchForm").addEventListener("submit", (event) => {
    event.preventDefault();
    search($("#searchInput").value);
  });

  $("#closeDetailBtn").addEventListener("click", closeDetail);
  $("#detailDialog").addEventListener("click", (event) => {
    if (event.target.id === "detailDialog") closeDetail();
  });

  $("#likeBtn").addEventListener("click", async () => {
    if (!state.activeContent || !requireLogin("点赞")) return;
    const method = state.activeContent.isLiked ? "DELETE" : "POST";
    try {
      await api(`/api/contents/${state.activeContent.id}/like`, { method });
      toast(method === "POST" ? "已点赞" : "已取消点赞");
      await Promise.all([refreshDetail(state.activeContent.id), loadFeed(), loadHotRank()]);
    } catch (error) {
      toast(error.message);
    }
  });

  $("#collectBtn").addEventListener("click", async () => {
    if (!state.activeContent || !requireLogin("收藏")) return;
    const method = state.activeContent.isCollected ? "DELETE" : "POST";
    try {
      await api(`/api/contents/${state.activeContent.id}/collect`, { method, body: method === "POST" ? "{}" : undefined });
      toast(method === "POST" ? "已收藏" : "已取消收藏");
      await Promise.all([refreshDetail(state.activeContent.id), loadFeed(), loadHotRank()]);
    } catch (error) {
      toast(error.message);
    }
  });

  $("#commentForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!state.activeContent || !requireLogin("评论")) return;
    const input = $("#commentInput");
    const content = input.value.trim();
    if (!content) return;
    try {
      await api(`/api/contents/${state.activeContent.id}/comments`, {
        method: "POST",
        body: JSON.stringify({ content })
      });
      input.value = "";
      toast("评论已发布");
      await Promise.all([refreshDetail(state.activeContent.id), loadFeed()]);
    } catch (error) {
      toast(error.message);
    }
  });

  document.addEventListener("click", (event) => {
    const trigger = event.target.closest("[data-open]");
    if (!trigger) return;
    openDetail(trigger.dataset.open);
  });
}

function setAuthMode(mode) {
  state.authMode = mode;
  $("#authTitle").textContent = mode === "register" ? "注册" : "登录";
  $("#authSubmit").textContent = mode === "register" ? "注册并登录" : "登录";
  $("#loginMode").classList.toggle("active", mode === "login");
  $("#registerMode").classList.toggle("active", mode === "register");
  $$(".register-only").forEach((el) => el.classList.toggle("hidden", mode !== "register"));
}

async function boot() {
  bindEvents();
  renderAuth();
  renderProfile();
  await Promise.allSettled([loadHealth(), loadTags(), loadHotRank(), loadFeed(), loadMe()]);
}

boot();
