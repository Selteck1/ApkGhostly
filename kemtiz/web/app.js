(() => {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const state = {
    token: localStorage.getItem("kemtiz_token") || "",
    me: null,
    googleClientId: "",
    googleCredential: "",
    googleProfile: null,
    chats: [],
    friends: [],
    incoming: [],
    outgoing: [],
    currentChat: null,
    socket: null,
    reconnectTimer: null,
    manualLogout: false,
    lastTypingSent: 0,
    toastTimer: null
  };

  async function api(path, options = {}) {
    const headers = { ...(options.headers || {}) };
    if (state.token) headers.Authorization = "Bearer " + state.token;
    if (options.body && typeof options.body !== "string") {
      headers["Content-Type"] = "application/json";
      options.body = JSON.stringify(options.body);
    }
    const response = await fetch(path, { ...options, headers });
    const raw = await response.text();
    let data = {};
    try { data = raw ? JSON.parse(raw) : {}; } catch (_) { data = { detail: raw }; }
    if (!response.ok) {
      if (response.status === 401 && state.token && !path.includes("/auth/")) {
        logout(false);
      }
      throw new Error(data.detail || data.message || ("Ошибка " + response.status));
    }
    return data;
  }

  function showToast(message, isError = false) {
    const toast = $("toast");
    toast.textContent = message;
    toast.classList.toggle("error", isError);
    toast.classList.add("visible");
    clearTimeout(state.toastTimer);
    state.toastTimer = setTimeout(() => toast.classList.remove("visible"), 3000);
  }

  function textNode(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  function avatar(label, className = "avatar") {
    const node = textNode("div", className, (label || "?").trim().charAt(0).toUpperCase());
    return node;
  }

  function setProfileAvatar(element, label, url) {
    element.replaceChildren();
    if (url && /^https:\/\//i.test(url) && /(^|\.)googleusercontent\.com$/i.test(new URL(url).hostname)) {
      const image = document.createElement("img");
      image.src = url;
      image.alt = "";
      image.referrerPolicy = "no-referrer";
      element.append(image);
      return;
    }
    element.textContent = (label || "?").trim().charAt(0).toUpperCase();
  }

  function showGoogleView() {
    $("googleLoginView").classList.remove("hidden");
    $("profileForm").classList.add("hidden");
    $("authTitle").textContent = "Добро пожаловать";
    $("authDescription").textContent = "Продолжи с Google — быстро, удобно и без отдельного пароля.";
    $("authError").textContent = "";
    $("profileError").textContent = "";
    state.googleCredential = "";
    state.googleProfile = null;
  }

  function suggestedUsername(email) {
    const local = (email.split("@")[0] || "kemtiz_user")
      .toLowerCase().replace(/[^a-z0-9_]/g, "_").replace(/^[^a-z0-9]+/, "").slice(0, 15);
    const base = local || "kemtiz_user";
    const suffix = Math.random().toString(36).slice(2, 6);
    return (base + "_" + suffix).slice(0, 24);
  }

  function showProfileForm(profile, credential) {
    state.googleCredential = credential;
    state.googleProfile = profile;
    $("googleLoginView").classList.add("hidden");
    $("profileForm").classList.remove("hidden");
    $("googleDisplayName").textContent = profile.name || "Google аккаунт";
    $("googleProfileEmail").textContent = profile.email || "";
    $("profileUsername").value = suggestedUsername(profile.email || "");
    $("profileCountry").value = "";
    $("profileAbout").value = "";
    setProfileAvatar($("googleAvatar"), profile.name || "G", profile.picture);
    $("profileError").textContent = "";
    $("profileUsername").focus();
  }

  async function finishGoogleLogin(result) {
    state.token = result.token;
    localStorage.setItem("kemtiz_token", state.token);
    state.manualLogout = false;
    state.googleCredential = "";
    state.googleProfile = null;
    await enterApp(result.user);
  }

  async function handleGoogleCredential(credential) {
    $("authError").textContent = "";
    try {
      const result = await api("/api/auth/google/start", {
        method: "POST",
        body: { credential }
      });
      if (result.needs_profile) {
        showProfileForm(result.profile || {}, credential);
      } else {
        await finishGoogleLogin(result);
      }
    } catch (error) {
      $("authError").textContent = error.message || "Не удалось войти через Google.";
    }
  }

  function initializeGoogleButton() {
    if (window.KemtizNativeGoogle && typeof window.KemtizNativeGoogle.signIn === "function") {
      $("googleSetupButton").classList.remove("hidden");
      return;
    }
    let attempts = 0;
    const tryRender = () => {
      if (!window.google || !window.google.accounts || !window.google.accounts.id) {
        attempts += 1;
        if (attempts < 60) {
          window.setTimeout(tryRender, 100);
        } else {
          $("authError").textContent = "Не удалось загрузить Google Sign-In. Проверь подключение к интернету.";
        }
        return;
      }
      if (!state.googleClientId) {
        $("authError").textContent = "Google-вход ещё не настроен на сервере. Сначала добавь KEMTIZ_GOOGLE_CLIENT_ID.";
        return;
      }
      window.google.accounts.id.initialize({
        client_id: state.googleClientId,
        callback: (response) => {
          if (!response || !response.credential) {
            $("authError").textContent = "Google не вернул подтверждение аккаунта. Попробуй ещё раз.";
            return;
          }
          handleGoogleCredential(response.credential);
        },
        auto_select: false,
        cancel_on_tap_outside: true,
        ux_mode: "popup"
      });
      const container = $("googleButton");
      container.replaceChildren();
      window.google.accounts.id.renderButton(container, {
        type: "standard",
        theme: "filled_black",
        size: "large",
        text: "continue_with",
        shape: "rectangular",
        logo_alignment: "left",
        width: Math.min(360, Math.max(240, container.clientWidth || 360)),
        locale: "ru"
      });
      $("authError").textContent = "";
    };
    tryRender();
  }

  window.KemtizNativeGoogleCredential = (credential) => {
    if (typeof credential === "string" && credential) {
      handleGoogleCredential(credential);
    } else {
      $("authError").textContent = "Не удалось получить подтверждение Google-аккаунта.";
    }
  };

  window.KemtizNativeGoogleError = (message) => {
    $("authError").textContent = String(message || "Не удалось войти через Google.");
  };

  window.KemtizNativeQrScanned = async (rawValue) => {
    const match = String(rawValue || "").trim().match(/^kemtiz:\/\/desktop-login\/([A-Za-z0-9_-]{20,100})$/);
    if (!match) {
      showToast("Это не QR-код входа в Kemtiz Desktop.", true);
      return;
    }
    if (!state.token || !state.me) {
      showToast("Сначала войди в Kemtiz на телефоне.", true);
      return;
    }
    const sessionId = match[1];
    try {
      const session = await api("/api/auth/desktop/qr/" + encodeURIComponent(sessionId));
      if (session.status !== "pending" || session.expires_in <= 0) {
        showToast("Этот QR-код уже истёк или использован. Обнови его на компьютере.", true);
        return;
      }
      const deviceName = String(session.device_name || "компьютере").slice(0, 80);
      const confirmed = window.confirm(
        "Разрешить вход в Kemtiz на устройстве:\n\n" + deviceName +
        "\n\nПодтверждай только если этот QR-код открыт на твоём компьютере."
      );
      if (!confirmed) {
        await api("/api/auth/desktop/qr/" + encodeURIComponent(sessionId) + "/deny", {
          method: "POST", body: {}
        });
        showToast("Вход на компьютер отклонён.");
        return;
      }
      await api("/api/auth/desktop/qr/" + encodeURIComponent(sessionId) + "/approve", {
        method: "POST", body: {}
      });
      showToast("Вход подтверждён. Вернись в Kemtiz на компьютере.");
    } catch (error) {
      showToast(error.message || "Не удалось подтвердить вход на компьютере.", true);
    }
  };

  window.KemtizNativeQrError = (message) => {
    const text = String(message || "Не удалось отсканировать QR-код.");
    if (text.includes("отменено")) return;
    showToast(text, true);
  };

  $("googleSetupButton").addEventListener("click", () => {
    if (!state.googleClientId) {
      $("authError").textContent = "Для входа настрой KEMTIZ_GOOGLE_CLIENT_ID в конфигурации сервера. Google не позволяет вход без OAuth Client ID.";
    } else if (window.KemtizNativeGoogle && typeof window.KemtizNativeGoogle.signIn === "function") {
      $("authError").textContent = "";
      window.KemtizNativeGoogle.signIn(state.googleClientId);
    } else {
      $("authError").textContent = "Google Sign-In загружается. Подожди пару секунд и попробуй снова.";
      initializeGoogleButton();
    }
  });

  $("profileForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!state.googleCredential) {
      $("profileError").textContent = "Подтверждение Google истекло. Вернись назад и выбери аккаунт ещё раз.";
      return;
    }
    $("profileError").textContent = "";
    $("finishProfileButton").disabled = true;
    try {
      const result = await api("/api/auth/google/finish", {
        method: "POST",
        body: {
          credential: state.googleCredential,
          username: $("profileUsername").value.trim(),
          country: $("profileCountry").value,
          about: $("profileAbout").value.trim()
        }
      });
      await finishGoogleLogin(result);
    } catch (error) {
      $("profileError").textContent = error.message || "Не удалось создать аккаунт.";
    } finally {
      $("finishProfileButton").disabled = false;
    }
  });

  $("cancelProfileButton").addEventListener("click", () => showGoogleView());

  async function loadGoogleConfiguration() {
    try {
      const config = await api("/api/config");
      state.googleClientId = String(config.google_client_id || "").trim();
      initializeGoogleButton();
    } catch (error) {
      $("authError").textContent = error.message || "Не удалось загрузить настройки входа.";
    }
  }

  loadGoogleConfiguration();

  async function enterApp(user) {
    state.me = user || await api("/api/me");
    $("authView").classList.add("hidden");
    $("appView").classList.remove("hidden");
    $("meName").textContent = state.me.display_name;
    $("meUsername").textContent = "@" + state.me.username;
    setProfileAvatar($("avatarMe"), state.me.display_name, state.me.avatar_url);
    await refreshAll();
    connectSocket();
  }

  function logout(showMessage = true) {
    state.manualLogout = true;
    clearTimeout(state.reconnectTimer);
    if (state.socket) {
      try { state.socket.close(); } catch (_) {}
    }
    state.socket = null;
    state.token = "";
    state.me = null;
    state.currentChat = null;
    localStorage.removeItem("kemtiz_token");
    $("appView").classList.add("hidden");
    $("authView").classList.remove("hidden");
    showGoogleView();
    document.body.classList.remove("chat-open");
    if (showMessage) showToast("Ты вышел из аккаунта.");
  }

  $("logoutButton").addEventListener("click", () => logout(true));

  $("connectPcButton").addEventListener("click", () => {
    if (!state.token || !state.me) {
      showToast("Сначала войди в Kemtiz на телефоне.", true);
      return;
    }
    if (window.KemtizNativeGoogle && typeof window.KemtizNativeGoogle.scanQrCode === "function") {
      window.KemtizNativeGoogle.scanQrCode();
    } else {
      showToast("Сканирование QR-кода доступно в Android-приложении Kemtiz.", true);
    }
  });

  async function refreshAll() {
    await Promise.allSettled([refreshFriends(), refreshRequests(), refreshChats()]);
  }

  async function refreshFriends() {
    try {
      state.friends = await api("/api/friends");
      renderFriends();
      renderGroupFriendChoices();
    } catch (error) {
      showToast(error.message, true);
    }
  }

  async function refreshRequests() {
    try {
      const data = await api("/api/friends/requests");
      state.incoming = data.incoming || [];
      state.outgoing = data.outgoing || [];
      renderRequests();
    } catch (error) {
      showToast(error.message, true);
    }
  }

  async function refreshChats() {
    try {
      state.chats = await api("/api/chats");
      renderChats();
      if (state.currentChat) {
        const updated = state.chats.find((chat) => chat.id === state.currentChat.id);
        if (updated) {
          state.currentChat = updated;
          renderChatHeader(updated);
        } else {
          state.currentChat = null;
          showEmptyState();
        }
      }
    } catch (error) {
      showToast(error.message, true);
    }
  }

  function renderFriends() {
    const container = $("friendsList");
    container.replaceChildren();
    $("friendsCount").textContent = String(state.friends.length);
    if (!state.friends.length) {
      container.append(textNode("div", "list-empty", "Пока нет друзей"));
      return;
    }
    state.friends.forEach((friend) => {
      const button = textNode("button", "person-row");
      button.type = "button";
      button.append(avatar(friend.display_name, "avatar small-avatar"));
      const copy = textNode("span", "person-copy");
      copy.append(textNode("strong", "", friend.display_name));
      copy.append(textNode("span", "person-subtitle", "@" + friend.username));
      button.append(copy);
      button.append(textNode("span", friend.online ? "presence online" : "presence", ""));
      button.addEventListener("click", () => openDirectChat(friend));
      container.append(button);
    });
  }

  function renderRequests() {
    const container = $("requestsList");
    container.replaceChildren();
    $("requestCount").textContent = String(state.incoming.length);
    if (!state.incoming.length && !state.outgoing.length) {
      container.append(textNode("div", "list-empty", "Новых заявок нет"));
      return;
    }
    state.incoming.forEach((person) => {
      const card = textNode("div", "request-row");
      card.append(avatar(person.display_name, "avatar tiny-avatar"));
      const copy = textNode("div", "person-copy");
      copy.append(textNode("strong", "", person.display_name));
      copy.append(textNode("span", "person-subtitle", "@" + person.username));
      card.append(copy);
      const actions = textNode("div", "request-actions");
      const yes = textNode("button", "mini-action accept", "✓");
      yes.type = "button";
      yes.title = "Принять";
      yes.addEventListener("click", () => answerRequest(person.request_id, "accept"));
      const no = textNode("button", "mini-action decline", "×");
      no.type = "button";
      no.title = "Отклонить";
      no.addEventListener("click", () => answerRequest(person.request_id, "decline"));
      actions.append(yes, no);
      card.append(actions);
      container.append(card);
    });
    state.outgoing.forEach((person) => {
      const row = textNode("div", "list-empty outgoing-row", "Ожидает ответа: @" + person.username);
      container.append(row);
    });
  }

  async function answerRequest(id, action) {
    try {
      await api("/api/friends/requests/" + id + "/" + action, { method: "POST" });
      showToast(action === "accept" ? "Вы теперь друзья." : "Заявка отклонена.");
      await refreshAll();
    } catch (error) {
      showToast(error.message, true);
    }
  }

  $("searchForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const query = $("searchInput").value.trim();
    const container = $("searchResults");
    container.replaceChildren();
    if (query.length < 2) {
      showToast("Введи хотя бы 2 символа.");
      return;
    }
    try {
      const users = await api("/api/users/search?q=" + encodeURIComponent(query));
      if (!users.length) {
        container.append(textNode("div", "list-empty", "Никого не нашли."));
        return;
      }
      users.forEach((person) => {
        const row = textNode("div", "search-result");
        row.append(avatar(person.display_name, "avatar tiny-avatar"));
        const copy = textNode("div", "person-copy");
        copy.append(textNode("strong", "", person.display_name));
        copy.append(textNode("span", "person-subtitle", "@" + person.username));
        row.append(copy);
        const isFriend = state.friends.some((friend) => friend.id === person.id);
        const button = textNode("button", "mini-action add-friend", isFriend ? "✓" : "+");
        button.type = "button";
        button.disabled = isFriend;
        button.title = isFriend ? "Уже друг" : "Добавить в друзья";
        button.addEventListener("click", async () => {
          try {
            await api("/api/friends/requests", { method: "POST", body: { username: person.username } });
            button.textContent = "…";
            button.disabled = true;
            showToast("Заявка отправлена.");
            await refreshRequests();
          } catch (error) {
            showToast(error.message, true);
          }
        });
        row.append(button);
        container.append(row);
      });
    } catch (error) {
      showToast(error.message, true);
    }
  });

  function renderGroupFriendChoices() {
    const container = $("groupFriends");
    container.replaceChildren();
    if (!state.friends.length) {
      container.append(textNode("div", "list-empty", "Сначала добавь друзей."));
      return;
    }
    state.friends.forEach((friend) => {
      const label = textNode("label", "check-row");
      const checkbox = document.createElement("input");
      checkbox.type = "checkbox";
      checkbox.value = String(friend.id);
      const name = textNode("span", "", friend.display_name + "  @" + friend.username);
      label.append(checkbox, name);
      container.append(label);
    });
  }

  $("toggleGroupButton").addEventListener("click", () => {
    $("groupForm").classList.toggle("hidden");
  });

  $("groupForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const title = $("groupTitle").value.trim();
    const memberIds = Array.from($("groupFriends").querySelectorAll('input[type="checkbox"]:checked'))
      .map((checkbox) => Number(checkbox.value));
    if (!title || !memberIds.length) {
      showToast("Укажи название и выбери хотя бы одного друга.", true);
      return;
    }
    try {
      const chat = await api("/api/chats/group", { method: "POST", body: { title, member_ids: memberIds } });
      $("groupTitle").value = "";
      $("groupForm").classList.add("hidden");
      await refreshChats();
      await openChat(chat);
      showToast("Группа создана.");
    } catch (error) {
      showToast(error.message, true);
    }
  });

  function renderChats() {
    const container = $("chatsList");
    container.replaceChildren();
    if (!state.chats.length) {
      container.append(textNode("div", "list-empty", "Здесь появятся твои чаты"));
      return;
    }
    state.chats.forEach((chat) => {
      const button = textNode("button", "chat-row" + (state.currentChat && state.currentChat.id === chat.id ? " selected" : ""));
      button.type = "button";
      const title = chat.title || "Чат";
      button.append(avatar(title, "avatar small-avatar"));
      const copy = textNode("span", "chat-copy");
      const top = textNode("span", "chat-row-top");
      top.append(textNode("strong", "", title));
      if (chat.last_message_at) top.append(textNode("time", "chat-time", formatTime(chat.last_message_at)));
      copy.append(top);
      copy.append(textNode("span", "chat-preview", chat.last_message || (chat.kind === "group" ? "Группа создана" : "Начни разговор")));
      button.append(copy);
      button.addEventListener("click", () => openChat(chat));
      container.append(button);
    });
  }

  function renderChatHeader(chat) {
    $("chatTitle").textContent = chat.title || "Чат";
    $("chatAvatar").textContent = (chat.title || "K").charAt(0).toUpperCase();
    if (chat.kind === "group") {
      $("chatSubtitle").textContent = (chat.members || []).length + " участника · группа";
    } else {
      const other = (chat.members || []).find((member) => member.id !== state.me.id);
      const online = other && other.online;
      $("chatSubtitle").textContent = online ? "● В сети" : ("@" + ((other && other.username) || ""));
      $("chatSubtitle").classList.toggle("subtitle-online", !!online);
    }
  }

  async function openDirectChat(friend) {
    try {
      const chat = await api("/api/chats/direct/" + friend.id, { method: "POST" });
      await refreshChats();
      await openChat(chat);
    } catch (error) {
      showToast(error.message, true);
    }
  }

  async function openChat(chat) {
    state.currentChat = chat;
    renderChatHeader(chat);
    $("emptyState").classList.add("hidden");
    $("messagePanel").classList.remove("hidden");
    document.body.classList.add("chat-open");
    $("mobileBackButton").classList.remove("hidden");
    renderChats();
    $("messagesList").replaceChildren(textNode("div", "loading-messages", "Загружаем сообщения…"));
    try {
      const messages = await api("/api/chats/" + chat.id + "/messages?limit=100");
      if (!state.currentChat || state.currentChat.id !== chat.id) return;
      renderMessages(messages);
      const last = messages.length ? messages[messages.length - 1].id : 0;
      await api("/api/chats/" + chat.id + "/read", { method: "POST", body: { last_read_message_id: last } });
      $("messageInput").focus({ preventScroll: true });
    } catch (error) {
      showToast(error.message, true);
    }
  }

  function showEmptyState() {
    $("emptyState").classList.remove("hidden");
    $("messagePanel").classList.add("hidden");
    $("mobileBackButton").classList.add("hidden");
    document.body.classList.remove("chat-open");
    $("chatTitle").textContent = "Добро пожаловать в Kemtiz";
    $("chatSubtitle").textContent = "Выбери друга или начни новый чат";
    $("chatAvatar").textContent = "K";
    renderChats();
  }

  $("mobileBackButton").addEventListener("click", () => {
    document.body.classList.remove("chat-open");
  });

  function renderMessages(messages) {
    const container = $("messagesList");
    container.replaceChildren();
    if (!messages.length) {
      container.append(textNode("div", "message-empty", "Здесь пока тихо. Отправь первое сообщение 👋"));
      return;
    }
    messages.forEach((message) => appendMessage(message, false));
    container.scrollTop = container.scrollHeight;
  }

  function appendMessage(message, scroll = true) {
    const container = $("messagesList");
    if (container.querySelector('[data-message-id="' + message.id + '"]')) return;
    const empty = container.querySelector(".message-empty, .loading-messages");
    if (empty) empty.remove();

    const mine = Number(message.sender_id) === Number(state.me.id);
    const row = textNode("div", "message-row" + (mine ? " mine" : ""));
    row.dataset.messageId = String(message.id);
    if (!mine) row.append(avatar(message.sender_display_name || message.sender_username, "avatar message-avatar"));
    const bubble = textNode("div", "message-bubble");
    if (!mine) bubble.append(textNode("div", "message-author", message.sender_display_name || message.sender_username || "Пользователь"));
    bubble.append(textNode("div", "message-body", message.body));
    const meta = textNode("div", "message-meta", formatTime(message.created_at) + (mine ? "  ✓" : ""));
    bubble.append(meta);
    row.append(bubble);
    container.append(row);
    if (scroll) container.scrollTop = container.scrollHeight;
  }

  function formatTime(value) {
    if (!value) return "";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "";
    return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  }

  $("messageForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const text = $("messageInput").value.trim();
    if (!text || !state.currentChat) return;
    const chatId = state.currentChat.id;
    $("sendButton").disabled = true;
    try {
      const message = await api("/api/chats/" + chatId + "/messages", { method: "POST", body: { body: text } });
      if (state.currentChat && state.currentChat.id === chatId) appendMessage(message);
      $("messageInput").value = "";
      resizeTextarea();
      refreshChats();
    } catch (error) {
      showToast(error.message, true);
    } finally {
      $("sendButton").disabled = false;
      $("messageInput").focus();
    }
  });

  function resizeTextarea() {
    const field = $("messageInput");
    field.style.height = "auto";
    field.style.height = Math.min(field.scrollHeight, 150) + "px";
  }

  $("messageInput").addEventListener("input", () => {
    resizeTextarea();
    const now = Date.now();
    if (state.socket && state.socket.readyState === WebSocket.OPEN && state.currentChat && now - state.lastTypingSent > 1200) {
      state.socket.send(JSON.stringify({ type: "typing", chat_id: state.currentChat.id }));
      state.lastTypingSent = now;
    }
  });

  $("messageInput").addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      $("messageForm").requestSubmit();
    }
  });

  function connectSocket() {
    if (!state.token || state.manualLogout) return;
    clearTimeout(state.reconnectTimer);
    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    const socket = new WebSocket(protocol + "//" + location.host + "/ws");
    state.socket = socket;
    socket.addEventListener("open", () => {
      socket.send(JSON.stringify({ type: "auth", token: state.token }));
      $("connectionState").classList.add("connected");
      $("connectionState").title = "Соединение активно";
    });
    socket.addEventListener("message", async (event) => {
      let data;
      try { data = JSON.parse(event.data); } catch (_) { return; }
      if (data.type === "message.new") {
        if (state.currentChat && state.currentChat.id === data.message.chat_id) {
          appendMessage(data.message);
          if (Number(data.message.sender_id) !== Number(state.me.id)) {
            const all = $("messagesList").querySelectorAll("[data-message-id]");
            const last = all.length ? Number(all[all.length - 1].dataset.messageId) : 0;
            api("/api/chats/" + state.currentChat.id + "/read", { method: "POST", body: { last_read_message_id: last } }).catch(() => {});
          }
        }
        refreshChats();
      } else if (data.type === "chat_list_changed") {
        refreshChats();
      } else if (data.type === "friend_request") {
        refreshRequests();
        showToast("Пришла новая заявка в друзья.");
      } else if (data.type === "friend_list_changed") {
        refreshFriends();
        refreshRequests();
        refreshChats();
      } else if (data.type === "presence") {
        const person = state.friends.find((friend) => friend.id === data.user_id);
        if (person) person.online = data.online;
        renderFriends();
        if (state.currentChat && state.currentChat.kind === "direct") {
          const other = (state.currentChat.members || []).find((member) => member.id !== state.me.id);
          if (other && other.id === data.user_id) {
            other.online = data.online;
            renderChatHeader(state.currentChat);
          }
        }
      } else if (data.type === "typing") {
        if (state.currentChat && state.currentChat.id === data.chat_id && data.user_id !== state.me.id) {
          $("typingLabel").classList.remove("hidden");
          clearTimeout(window.kemtizTypingTimer);
          window.kemtizTypingTimer = setTimeout(() => $("typingLabel").classList.add("hidden"), 1800);
        }
      } else if (data.type === "message.deleted") {
        if (state.currentChat && state.currentChat.id === data.chat_id) {
          const item = $("messagesList").querySelector('[data-message-id="' + data.message_id + '"]');
          if (item) item.querySelector(".message-body").textContent = "Сообщение удалено";
        }
      }
    });
    socket.addEventListener("close", () => {
      $("connectionState").classList.remove("connected");
      $("connectionState").title = "Переподключение…";
      if (state.token && !state.manualLogout) {
        state.reconnectTimer = setTimeout(connectSocket, 2500);
      }
    });
    socket.addEventListener("error", () => $("connectionState").classList.remove("connected"));
  }

  // Restore a previous session when the page is reopened.
  if (state.token) {
    api("/api/me").then((user) => enterApp(user)).catch(() => {
      localStorage.removeItem("kemtiz_token");
      state.token = "";
    });
  }

  if ("serviceWorker" in navigator && location.protocol === "https:") {
    navigator.serviceWorker.register("/sw.js").catch(() => {});
  }
})();
