/**
 * VibeSync Web Showcase - Interactive JavaScript
 */

// 1. Sample Interactive Data for Phone Simulator
const contactsData = [
  {
    id: 1,
    name: "Sarah Connor",
    avatar: "SC",
    color: "linear-gradient(135deg, #FF6B6B, #FF8E53)",
    lastMsg: "Let's test the voice call on the new build! 🚀",
    time: "10:42 AM",
    unread: 2,
    category: "Friends",
    online: true,
    messages: [
      { text: "Hey! Did you update to the new VibeSync light theme?", sender: "them", time: "10:40 AM" },
      { text: "Yes! The glassmorphism and tabs look amazing! 🔥", sender: "me", time: "10:41 AM" },
      { text: "Let's test the voice call on the new build! 🚀", sender: "them", time: "10:42 AM" }
    ]
  },
  {
    id: 2,
    name: "Security Service",
    avatar: "🛡️",
    color: "linear-gradient(135deg, #6C3AEB, #3A6BEB)",
    lastMsg: "End-to-end encryption & privacy shield active.",
    time: "09:15 AM",
    unread: 0,
    category: "Admin",
    online: true,
    messages: [
      { text: "Welcome to VibeSync v2.0.", sender: "them", time: "09:10 AM" },
      { text: "Your connection is fully protected with end-to-end encryption.", sender: "them", time: "09:12 AM" },
      { text: "End-to-end encryption & privacy shield active.", sender: "them", time: "09:15 AM" }
    ]
  },
  {
    id: 3,
    name: "Alex Rivera",
    avatar: "AR",
    color: "linear-gradient(135deg, #4E65FF, #92EFFD)",
    lastMsg: "Sent over the encrypted files and photos.",
    time: "Yesterday",
    unread: 1,
    category: "Friends",
    online: false,
    messages: [
      { text: "Did you receive the files securely?", sender: "me", time: "Yesterday" },
      { text: "Sent over the encrypted files and photos.", sender: "them", time: "Yesterday" }
    ]
  },
  {
    id: 4,
    name: "VibeSync Team",
    avatar: "VT",
    color: "linear-gradient(135deg, #10B981, #059669)",
    lastMsg: "New APK build is available for download.",
    time: "Yesterday",
    unread: 0,
    category: "All",
    online: true,
    messages: [
      { text: "Check the showcase web page for direct APK link!", sender: "them", time: "Yesterday" }
    ]
  },
  {
    id: 5,
    name: "David Kim",
    avatar: "DK",
    color: "linear-gradient(135deg, #F59E0B, #D97706)",
    lastMsg: "Voice call is crystal clear and encrypted!",
    time: "Sep 24",
    unread: 0,
    category: "Friends",
    online: true,
    messages: [
      { text: "Testing high-definition private voice call.", sender: "them", time: "Sep 24" },
      { text: "Voice call is crystal clear and encrypted!", sender: "them", time: "Sep 24" }
    ]
  }
];

const callHistoryData = [
  { name: "Sarah Connor", type: "video", incoming: false, time: "Today, 10:30 AM", status: "Completed (4m 12s)" },
  { name: "David Kim", type: "voice", incoming: true, time: "Yesterday, 4:15 PM", status: "Missed Call" },
  { name: "Alex Rivera", type: "voice", incoming: false, time: "Sep 23, 8:40 PM", status: "Completed (12m 05s)" },
  { name: "System Admin", type: "video", incoming: true, time: "Sep 22, 11:20 AM", status: "Completed (1m 45s)" }
];

// State variables
let currentCategory = "All";
let currentSearchQuery = "";
let currentTab = "chats";
let activeChatContact = null;

// DOM Elements
document.addEventListener("DOMContentLoaded", () => {
  initClock();
  setupDownloadLinks();
  setupThemeToggle();
  setupPhoneSimulation();
  setupCopyLinkButton();
});

// Real-time Phone Status Clock
function initClock() {
  const clockEl = document.getElementById("phone-clock");
  if (!clockEl) return;

  function update() {
    const now = new Date();
    let hours = now.getHours();
    const minutes = String(now.getMinutes()).padStart(2, '0');
    hours = hours % 12 || 12;
    clockEl.textContent = `${hours}:${minutes}`;
  }
  update();
  setInterval(update, 30000);
}

// Download URL & QR Code Configuration
function setupDownloadLinks() {
  const currentOrigin = window.location.origin && !window.location.origin.startsWith("file") 
    ? window.location.origin 
    : "http://127.0.0.1:3001";
  
  // Download URL for browser click
  const downloadUrl = `${currentOrigin}/download`;

  // QR Code URL: If viewed on localhost, convert host to LAN IP so phone cameras can reach it
  const lanDownloadUrl = (window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1")
    ? `http://192.168.18.78:${window.location.port || "3001"}/download`
    : downloadUrl;

  // Update Download Buttons
  const downloadBtns = document.querySelectorAll(".btn-download-trigger");
  downloadBtns.forEach(btn => {
    btn.setAttribute("href", downloadUrl);
  });

  // Generate dynamic QR Code for phone scanning
  const qrImg = document.getElementById("download-qr-img");
  if (qrImg) {
    const qrApiUrl = `https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${encodeURIComponent(lanDownloadUrl)}&color=6C3AEB&bgcolor=FFFFFF&margin=1`;
    qrImg.src = qrApiUrl;
  }

  // Update display text
  const linkDisplay = document.getElementById("download-url-display");
  if (linkDisplay) {
    linkDisplay.textContent = downloadUrl;
  }
}

// Theme Toggle (Light / Dark)
function setupThemeToggle() {
  const toggleBtn = document.getElementById("theme-toggle-btn");
  if (!toggleBtn) return;

  const currentTheme = localStorage.getItem("vibesync-theme") || "light";
  document.documentElement.setAttribute("data-theme", currentTheme);
  updateThemeIcon(currentTheme);

  toggleBtn.addEventListener("click", () => {
    const active = document.documentElement.getAttribute("data-theme");
    const next = active === "dark" ? "light" : "dark";
    document.documentElement.setAttribute("data-theme", next);
    localStorage.setItem("vibesync-theme", next);
    updateThemeIcon(next);
  });
}

function updateThemeIcon(theme) {
  const icon = document.getElementById("theme-icon");
  if (!icon) return;
  if (theme === "dark") {
    icon.innerHTML = `<path d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>`;
  } else {
    icon.innerHTML = `<path d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>`;
  }
}

// Copy APK Download Link
function setupCopyLinkButton() {
  const copyBtn = document.getElementById("btn-copy-apk-link");
  if (!copyBtn) return;

  copyBtn.addEventListener("click", () => {
    const downloadUrl = document.querySelector(".btn-download-trigger")?.getAttribute("href") || window.location.href;
    navigator.clipboard.writeText(downloadUrl).then(() => {
      showToast("Download link copied to clipboard! 📋");
    }).catch(() => {
      showToast("Link: " + downloadUrl);
    });
  });
}

function showToast(message) {
  let toast = document.getElementById("app-toast");
  if (!toast) {
    toast = document.createElement("div");
    toast.id = "app-toast";
    toast.className = "toast";
    document.body.appendChild(toast);
  }
  toast.textContent = message;
  toast.classList.add("show");
  setTimeout(() => {
    toast.classList.remove("show");
  }, 2600);
}

// Phone Simulator Core Logic
function setupPhoneSimulation() {
  renderChatList();

  // Search input filter
  const searchInput = document.getElementById("phone-search-input");
  if (searchInput) {
    searchInput.addEventListener("input", (e) => {
      currentSearchQuery = e.target.value.toLowerCase().trim();
      renderChatList();
    });
  }

  // Filter chips
  const chips = document.querySelectorAll(".chip-btn");
  chips.forEach(chip => {
    chip.addEventListener("click", () => {
      chips.forEach(c => c.classList.remove("active"));
      chip.classList.add("active");
      currentCategory = chip.getAttribute("data-category");
      renderChatList();
    });
  });

  // Bottom navigation tabs
  const tabBtns = document.querySelectorAll(".nav-tab-btn");
  tabBtns.forEach(btn => {
    btn.addEventListener("click", () => {
      tabBtns.forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      currentTab = btn.getAttribute("data-tab");
      switchPhoneTab(currentTab);
    });
  });

  // Chat Room Send Message
  const sendBtn = document.getElementById("chat-send-btn");
  const msgInput = document.getElementById("chat-msg-input");
  const backBtn = document.getElementById("chat-back-btn");

  if (sendBtn && msgInput) {
    sendBtn.addEventListener("click", () => sendChatMessage(msgInput));
    msgInput.addEventListener("keydown", (e) => {
      if (e.key === "Enter") sendChatMessage(msgInput);
    });
  }

  if (backBtn) {
    backBtn.addEventListener("click", () => {
      const chatScreen = document.getElementById("phone-chat-screen");
      if (chatScreen) chatScreen.classList.remove("open");
    });
  }
}

// Render filtered chats in simulator
function renderChatList() {
  const container = document.getElementById("phone-chats-container");
  if (!container) return;

  const filtered = contactsData.filter(contact => {
    const matchCategory = (currentCategory === "All") 
      ? true 
      : (currentCategory === "Unread" ? contact.unread > 0 : contact.category === currentCategory);

    const matchSearch = currentSearchQuery === "" ||
      contact.name.toLowerCase().includes(currentSearchQuery) ||
      contact.lastMsg.toLowerCase().includes(currentSearchQuery);

    return matchCategory && matchSearch;
  });

  if (filtered.length === 0) {
    container.innerHTML = `
      <div style="text-align: center; padding: 40px 10px; color: #94A3B8;">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="margin: 0 auto 10px; display:block;">
          <circle cx="11" cy="11" r="8"></circle>
          <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
        </svg>
        <p style="font-size: 0.85rem; font-weight:600;">No conversations found</p>
      </div>
    `;
    return;
  }

  container.innerHTML = filtered.map(contact => `
    <div class="chat-card" onclick="openChatRoom(${contact.id})">
      <div class="avatar" style="background: ${contact.color};">
        ${contact.avatar}
        ${contact.online ? '<div class="status-online"></div>' : ''}
      </div>
      <div class="chat-details">
        <div class="chat-row-top">
          <span class="chat-name">${contact.name}</span>
          <span class="chat-time">${contact.time}</span>
        </div>
        <div class="chat-row-bottom">
          <span class="chat-snippet">${contact.lastMsg}</span>
          ${contact.unread > 0 ? `<span class="badge-unread">${contact.unread}</span>` : ''}
        </div>
      </div>
    </div>
  `).join("");
}

// Switch Bottom Tabs inside Phone
function switchPhoneTab(tab) {
  const chatsView = document.getElementById("phone-chats-view");
  const friendsView = document.getElementById("phone-friends-view");
  const callsView = document.getElementById("phone-calls-view");

  if (!chatsView || !friendsView || !callsView) return;

  chatsView.style.display = "none";
  friendsView.style.display = "none";
  callsView.style.display = "none";

  if (tab === "chats") {
    chatsView.style.display = "block";
    renderChatList();
  } else if (tab === "friends") {
    friendsView.style.display = "block";
    renderFriendsList();
  } else if (tab === "calls") {
    callsView.style.display = "block";
    renderCallsList();
  }
}

// Render Friends Tab
function renderFriendsList() {
  const container = document.getElementById("phone-friends-container");
  if (!container) return;

  container.innerHTML = contactsData.map(c => `
    <div class="chat-card">
      <div class="avatar" style="background: ${c.color}">
        ${c.avatar}
        ${c.online ? '<div class="status-online"></div>' : ''}
      </div>
      <div class="chat-details">
        <div class="chat-row-top">
          <span class="chat-name">${c.name}</span>
          <span style="font-size: 0.72rem; color: ${c.online ? '#10B981' : '#94A3B8'}; font-weight:600;">
            ${c.online ? 'Online' : 'Offline'}
          </span>
        </div>
        <div class="chat-row-bottom">
          <span class="chat-snippet">${c.category} • VibeSync Contact</span>
          <button style="border:none; background:rgba(108,58,235,0.1); color:#6C3AEB; padding:3px 10px; border-radius:12px; font-size:0.75rem; font-weight:700; cursor:pointer;" onclick="openChatRoom(${c.id})">Message</button>
        </div>
      </div>
    </div>
  `).join("");
}

// Render Calls Tab
function renderCallsList() {
  const container = document.getElementById("phone-calls-container");
  if (!container) return;

  container.innerHTML = callHistoryData.map(c => `
    <div class="chat-card" style="align-items: center;">
      <div class="avatar" style="background: ${c.type === 'video' ? 'linear-gradient(135deg, #6C3AEB, #3A6BEB)' : 'linear-gradient(135deg, #10B981, #059669)'}; font-size: 0.85rem;">
        ${c.type === 'video' ? '🎥' : '📞'}
      </div>
      <div class="chat-details">
        <div class="chat-row-top">
          <span class="chat-name">${c.name}</span>
          <span class="chat-time">${c.time}</span>
        </div>
        <div class="chat-row-bottom">
          <span class="chat-snippet" style="color: ${c.status.includes('Missed') ? '#EF4444' : '#64748B'}">${c.status}</span>
          <button onclick="simulateCall('${c.name}', '${c.type}')" style="border:none; background:transparent; color:#6C3AEB; cursor:pointer; font-size:1.1rem;">
            ${c.type === 'video' ? '📹' : '📞'}
          </button>
        </div>
      </div>
    </div>
  `).join("");
}

// Open interactive Chat Screen
window.openChatRoom = function(contactId) {
  const contact = contactsData.find(c => c.id === contactId);
  if (!contact) return;
  activeChatContact = contact;

  // Clear unread
  contact.unread = 0;
  renderChatList();

  const headerName = document.getElementById("chat-screen-contact-name");
  const headerAvatar = document.getElementById("chat-screen-contact-avatar");
  const messagesArea = document.getElementById("chat-screen-messages");
  const chatScreen = document.getElementById("phone-chat-screen");

  if (headerName) headerName.textContent = contact.name;
  if (headerAvatar) {
    headerAvatar.textContent = contact.avatar;
    headerAvatar.style.background = contact.color;
  }

  if (messagesArea) {
    messagesArea.innerHTML = contact.messages.map(m => `
      <div class="msg-bubble ${m.sender === 'me' ? 'msg-outgoing' : 'msg-incoming'}">
        ${m.text}
        <span class="msg-time">${m.time}</span>
      </div>
    `).join("");
    messagesArea.scrollTop = messagesArea.scrollHeight;
  }

  if (chatScreen) {
    chatScreen.classList.add("open");
  }
};

// Send message inside simulated phone
function sendChatMessage(input) {
  const text = input.value.trim();
  if (!text || !activeChatContact) return;

  const now = new Date();
  const timeStr = `${now.getHours() % 12 || 12}:${String(now.getMinutes()).padStart(2, '0')} ${now.getHours() >= 12 ? 'PM' : 'AM'}`;

  // Append user message
  activeChatContact.messages.push({ text: text, sender: "me", time: timeStr });
  activeChatContact.lastMsg = text;
  input.value = "";

  const messagesArea = document.getElementById("chat-screen-messages");
  if (messagesArea) {
    const userMsgEl = document.createElement("div");
    userMsgEl.className = "msg-bubble msg-outgoing";
    userMsgEl.innerHTML = `${text}<span class="msg-time">${timeStr}</span>`;
    messagesArea.appendChild(userMsgEl);
    messagesArea.scrollTop = messagesArea.scrollHeight;
  }

  // Auto simulated reply
  setTimeout(() => {
    const replies = [
      "Awesome! Testing in real-time.",
      "The glassmorphic design feels super fluid!",
      "Connected securely with end-to-end encryption.",
      "Got your message! Everything synced smoothly."
    ];
    const replyText = replies[Math.floor(Math.random() * replies.length)];
    activeChatContact.messages.push({ text: replyText, sender: "them", time: timeStr });
    activeChatContact.lastMsg = replyText;

    if (messagesArea && document.getElementById("phone-chat-screen").classList.contains("open")) {
      const replyEl = document.createElement("div");
      replyEl.className = "msg-bubble msg-incoming";
      replyEl.innerHTML = `${replyText}<span class="msg-time">${timeStr}</span>`;
      messagesArea.appendChild(replyEl);
      messagesArea.scrollTop = messagesArea.scrollHeight;
    }
  }, 900);
}

// Call simulation modal
window.simulateCall = function(name, type) {
  showToast(`Initiating ${type} call with ${name}... 📞`);
};
