(() => {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const state = {
    token: localStorage.getItem("kemtiz_token") || "",
    me: null,
    authStage: "phone",
    pendingPhone: "",
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

  function setAuthStage(stage) {
    state.authStage = stage;
    const verifying = stage === "code";
    $("codeSection").classList.toggle("hidden", !verifying);
    $("changePhoneButton").classList.toggle("hidden", !verifying);
    $("phone").readOnly = verifying;
    $("authCode").required = verifying;
    $("authTitle").textContent = verifying ? "Проверь SMS" : "Вход и регистрация";
    $("authDescription").textContent = verifying
      ? "Введи шестизначный код из SMS, чтобы открыть Kemtiz."
      : "Введи номер телефона. Если ты здесь впервые, аккаунт создастся автоматически.";
    $("authSubmit").innerHTML = verifying
      ? 'Подтвердить и войти <span>↗</span>'
      : 'Получить код <span>↗</span>';
    $("authError").textContent = "";
    if (!verifying) {
      state.pendingPhone = "";
      $("authCode").value = "";
      $("codeHint").textContent = "Код действует 5 минут.";
      $("phone").focus();
    } else {
      $("authCode").focus();
    }
  }

  $("changePhoneButton").addEventListener("click", () => setAuthStage("phone"));

  $("authForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    $("authError").textContent = "";
    $("authSubmit").disabled = true;
    try {
      if (state.authStage === "phone") {
        const result = await api("/api/auth/request-code", {
          method: "POST",
          body: { phone: $("phone").value.trim() }
        });
        state.pendingPhone = result.phone;
        const digits = String(result.phone || "").replace(/\D/g, "");
        $("codeHint").textContent = "Введи код из SMS на номер, оканчивающийся на " + digits.slice(-4) + ". Код действует 5 минут.";
        setAuthStage("code");
      } else {
        const result = await api("/api/auth/verify-code", {
          method: "POST",
          body: {
            phone: state.pendingPhone || $("phone").value.trim(),
            code: $("authCode").value.trim()
          }
        });
        state.token = result.token;
        localStorage.setItem("kemtiz_token", state.token);
        state.manualLogout = false;
        state.pendingPhone = "";
        state.authStage = "phone";
        $("phone").value = "";
        $("authCode").value = "";
        await enterApp(result.user);
      }
    } catch (error) {
      $("authError").textContent = error.message || "Не удалось подтвердить номер.";
    } finally {
      $("authSubmit").disabled = false;
    }
  });

  async function enterApp(user) {
    state.me = user || await api("/api/me");
    $("authView").classList.add("hidden");
    $("appView").classList.remove("hidden");
    $("meName").textContent = state.me.display_name;
    $("meUsername").textContent = "@" + state.me.username;
    $("avatarMe").textContent = state.me.display_name.charAt(0).toUpperCase();
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
    $("phone").value = "";
    $("authCode").value = "";
    setAuthStage("phone");
    document.body.classList.remove("chat-open");
    if (showMessage) showToast("Ты вышел из аккаунта.");
  }

  $("logoutButton").addEventListener("click", () => logout(true));

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
