// ===================== API HELPER =====================
const api = {
  async req(method, path, body) {
    const opts = { method, headers: {}, credentials: 'include' };
    if (body !== undefined) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
    const res = await fetch('/api' + path, opts);
    let data = null;
    try { data = await res.json(); } catch (e) { data = null; }
    if (res.status === 401) {
      state.user = null;
      showAuthScreen();
      throw new Error((data && data.error) || 'Not authenticated');
    }
    if (!res.ok) throw new Error((data && data.error) || 'Request failed');
    return data;
  },
  get(path) { return this.req('GET', path); },
  post(path, body) { return this.req('POST', path, body); },
  put(path, body) { return this.req('PUT', path, body); },
  del(path) { return this.req('DELETE', path); },
};

const state = { user: null, route: 'dashboard', booksCache: [], membersCache: [] };

// ===================== TOASTS =====================
function toast(message, type) {
  const root = document.getElementById('toast-root');
  const el = document.createElement('div');
  el.className = 'toast' + (type ? ' ' + type : '');
  el.textContent = message;
  root.appendChild(el);
  setTimeout(() => el.remove(), 3200);
}

// ===================== AUTH SCREEN =====================
function showAuthScreen() {
  document.getElementById('auth-screen').hidden = false;
  document.getElementById('app-shell').hidden = true;
}
function showAppShell() {
  document.getElementById('auth-screen').hidden = true;
  document.getElementById('app-shell').hidden = false;
}

document.querySelectorAll('[data-switch]').forEach(link => {
  link.addEventListener('click', (e) => {
    e.preventDefault();
    const target = link.dataset.switch;
    document.querySelectorAll('.auth-form').forEach(f => f.classList.remove('active'));
    document.getElementById(target + '-form').classList.add('active');
  });
});

document.getElementById('login-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const btn = document.getElementById('login-submit');
  const errBox = document.getElementById('login-error');
  errBox.hidden = true;
  btn.disabled = true;
  try {
    const res = await api.post('/login', {
      username: document.getElementById('login-username').value.trim(),
      password: document.getElementById('login-password').value,
    });
    state.user = res.user;
    onAuthed();
  } catch (err) {
    errBox.textContent = err.message;
    errBox.hidden = false;
  } finally {
    btn.disabled = false;
  }
});

document.getElementById('signup-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const btn = document.getElementById('signup-submit');
  const errBox = document.getElementById('signup-error');
  errBox.hidden = true;
  btn.disabled = true;
  try {
    const res = await api.post('/signup', {
      fullName: document.getElementById('signup-fullname').value.trim(),
      username: document.getElementById('signup-username').value.trim(),
      email: document.getElementById('signup-email').value.trim(),
      password: document.getElementById('signup-password').value,
    });
    state.user = res.user;
    onAuthed();
  } catch (err) {
    errBox.textContent = err.message;
    errBox.hidden = false;
  } finally {
    btn.disabled = false;
  }
});

document.getElementById('logout-btn').addEventListener('click', async () => {
  try { await api.post('/logout'); } catch (e) {}
  state.user = null;
  showAuthScreen();
  window.location.hash = '';
});

function onAuthed() {
  document.getElementById('topbar-avatar').textContent = initials(state.user.fullName);
  document.getElementById('topbar-user-name').textContent = state.user.fullName;
  showAppShell();
  if (!window.location.hash || window.location.hash === '#/') window.location.hash = '#/dashboard';
  else render();
}

function initials(name) {
  if (!name) return '?';
  return name.trim().split(/\s+/).slice(0, 2).map(w => w[0].toUpperCase()).join('');
}

// ===================== BOOT: check existing session =====================
(async function boot() {
  try {
    const user = await api.get('/me');
    state.user = user;
    onAuthed();
  } catch (e) {
    showAuthScreen();
  }
})();

// ===================== SIDEBAR / MOBILE =====================
document.getElementById('sidebar-toggle').addEventListener('click', () => {
  document.getElementById('app-shell').classList.toggle('collapsed');
});
document.getElementById('mobile-menu-btn').addEventListener('click', () => {
  document.getElementById('app-shell').classList.add('mobile-open');
});
document.getElementById('sidebar-scrim').addEventListener('click', () => {
  document.getElementById('app-shell').classList.remove('mobile-open');
});

// ===================== ROUTER =====================
const ROUTE_TITLES = {
  dashboard: 'Dashboard', books: 'Books', members: 'Members',
  issue: 'Issue & Return', overdue: 'Overdue & Fines',
  transactions: 'Transactions', reports: 'Reports', profile: 'Profile & Settings',
};

window.addEventListener('hashchange', render);

function currentRouteParts() {
  const hash = window.location.hash.replace(/^#\/?/, '');
  return hash.split('/').filter(Boolean);
}

async function render() {
  if (!state.user) return;
  document.getElementById('app-shell').classList.remove('mobile-open');
  const parts = currentRouteParts();
  const route = parts[0] || 'dashboard';
  state.route = route;

  document.querySelectorAll('.nav-item[data-route]').forEach(a => {
    a.classList.toggle('active', a.dataset.route === route);
  });
  document.getElementById('topbar-title').textContent = ROUTE_TITLES[route] || 'Stacks';

  const content = document.getElementById('content');
  content.innerHTML = '<div class="empty-state">Loading…</div>';

  try {
    switch (route) {
      case 'dashboard': return renderDashboard(content);
      case 'books': return parts[1] ? renderBookDetail(content, parts[1]) : renderBooks(content);
      case 'members': return parts[1] ? renderMemberDetail(content, parts[1]) : renderMembers(content);
      case 'issue': return renderIssue(content);
      case 'overdue': return renderOverdue(content);
      case 'transactions': return renderTransactions(content);
      case 'reports': return renderReports(content);
      case 'profile': return renderProfile(content);
      default: return renderDashboard(content);
    }
  } catch (err) {
    content.innerHTML = `<div class="empty-state"><div class="empty-title">Something went wrong</div>${escapeHtml(err.message)}</div>`;
  }
}

function escapeHtml(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}
function fmtDate(s) {
  if (!s) return '—';
  const d = new Date(s);
  if (isNaN(d)) return s;
  return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

// ===================== DASHBOARD =====================
async function renderDashboard(content) {
  const [stats, overdue, books] = await Promise.all([
    api.get('/dashboard'), api.get('/transactions/overdue'), api.get('/books'),
  ]);

  const lowStock = books.filter(b => b.availableCopies === 0).length;

  content.innerHTML = `
    <div class="page-head">
      <div>
        <h2>Welcome back, ${escapeHtml(firstName(state.user.fullName))} 👋</h2>
        <p>Here's what's happening in your library today.</p>
      </div>
      <div class="page-actions">
        <a href="#/books" class="btn btn-secondary btn-sm">Manage books</a>
        <a href="#/issue" class="btn btn-primary btn-sm">Issue a book</a>
      </div>
    </div>

    <div class="stat-grid">
      ${statCard('Total Books', stats.totalBooks, 'var(--info-soft)', 'var(--info)', iconBook())}
      ${statCard('Total Members', stats.totalMembers, 'var(--brand-soft)', 'var(--brand)', iconUsers())}
      ${statCard('Currently Issued', stats.issuedCount, 'var(--warn-soft)', 'var(--warn)', iconArrow())}
      ${statCard('Overdue', stats.overdueCount, 'var(--danger-soft)', 'var(--danger)', iconClock())}
    </div>

    <div class="grid-2">
      <div class="card">
        <div class="card-head"><h3>Overdue loans</h3><a href="#/overdue" class="btn btn-ghost btn-sm">View all</a></div>
        <div class="table-wrap">
          ${overdue.length === 0 ? emptyState('No overdue books right now', 'Nice — everything is on time.') : `
          <table><thead><tr><th>Book</th><th>Member</th><th>Due</th><th>Est. fine</th></tr></thead>
          <tbody>
            ${overdue.slice(0, 6).map(t => `
              <tr>
                <td class="cell-primary">${escapeHtml(t.bookTitle)}</td>
                <td>${escapeHtml(t.memberName)}</td>
                <td>${fmtDate(t.dueDate)}</td>
                <td><span class="badge badge-danger">₹${Number(t.projectedFine).toFixed(2)}</span></td>
              </tr>`).join('')}
          </tbody></table>`}
        </div>
      </div>

      <div class="card">
        <div class="card-head"><h3>Snapshot</h3></div>
        <div class="card-body">
          <div class="kv-list">
            <div class="kv-row"><span class="k">Books out of stock</span><span class="v">${lowStock}</span></div>
            <div class="kv-row"><span class="k">Active loans</span><span class="v">${stats.issuedCount}</span></div>
            <div class="kv-row"><span class="k">Overdue loans</span><span class="v">${stats.overdueCount}</span></div>
            <div class="kv-row"><span class="k">Registered members</span><span class="v">${stats.totalMembers}</span></div>
          </div>
          <div style="margin-top:18px; display:flex; flex-direction:column; gap:10px;">
            <a href="#/reports" class="btn btn-secondary btn-block">Open reports</a>
            <a href="#/members" class="btn btn-ghost btn-block">Manage members</a>
          </div>
        </div>
      </div>
    </div>
  `;
}

function firstName(full) { return (full || '').split(' ')[0]; }

function statCard(label, value, bg, fg, icon) {
  return `<div class="stat-card">
    <div class="stat-top">
      <div class="stat-icon" style="background:${bg}; color:${fg}">${icon}</div>
    </div>
    <div class="stat-value">${value}</div>
    <div class="stat-label">${label}</div>
  </div>`;
}

function emptyState(title, sub) {
  return `<div class="empty-state">
    ${iconEmptyBox()}
    <div class="empty-title">${escapeHtml(title)}</div>
    <div>${escapeHtml(sub || '')}</div>
  </div>`;
}

// ===================== ICONS =====================
function iconBook() { return `<svg viewBox="0 0 24 24" fill="none"><path d="M4 4.5A1.5 1.5 0 0 1 5.5 3H12v18H5.5A1.5 1.5 0 0 1 4 19.5v-15Z" stroke="currentColor" stroke-width="1.8"/><path d="M12 3h6.5A1.5 1.5 0 0 1 20 4.5v15a1.5 1.5 0 0 1-1.5 1.5H12" stroke="currentColor" stroke-width="1.8"/></svg>`; }
function iconUsers() { return `<svg viewBox="0 0 24 24" fill="none"><circle cx="9" cy="8" r="3.2" stroke="currentColor" stroke-width="1.8"/><path d="M3.5 20c.7-3.6 3.3-5.5 5.5-5.5s4.8 1.9 5.5 5.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>`; }
function iconArrow() { return `<svg viewBox="0 0 24 24" fill="none"><path d="M4 12h11M11 7l5 5-5 5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>`; }
function iconClock() { return `<svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="8.5" stroke="currentColor" stroke-width="1.8"/><path d="M12 7.5V12l3 2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>`; }
function iconEmptyBox() { return `<svg width="42" height="42" viewBox="0 0 24 24" fill="none"><path d="M3 8l9-5 9 5-9 5-9-5Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M3 8v8l9 5 9-5V8" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/></svg>`; }
function iconSearch() { return `<svg viewBox="0 0 24 24" fill="none"><circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="1.8"/><path d="M20 20l-3.5-3.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>`; }
function iconEdit() { return `<svg viewBox="0 0 24 24" fill="none"><path d="M4 16.5V20h3.5L18 9.5l-3.5-3.5L4 16.5Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M14 4.5l3.5 3.5" stroke="currentColor" stroke-width="1.6"/></svg>`; }
function iconTrash() { return `<svg viewBox="0 0 24 24" fill="none"><path d="M5 7h14M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2m-9 0 1 12.5A1.5 1.5 0 0 0 8.5 21h7a1.5 1.5 0 0 0 1.5-1.5L18 7" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>`; }
function iconChevron() { return `<svg viewBox="0 0 24 24" fill="none"><path d="M9 6l6 6-6 6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>`; }

// ===================== BOOKS =====================
async function renderBooks(content, keyword) {
  const books = await api.get('/books' + (keyword ? '?q=' + encodeURIComponent(keyword) : ''));
  state.booksCache = books;

  content.innerHTML = `
    <div class="page-head">
      <div><h2>Books</h2><p>${books.length} title${books.length === 1 ? '' : 's'} in the catalog.</p></div>
      <div class="page-actions">
        <div class="search-box">${iconSearch()}<input id="book-search" placeholder="Search title, author, ISBN…" value="${escapeHtml(keyword || '')}"/></div>
        <button class="btn btn-primary btn-sm" id="add-book-btn">+ Add book</button>
      </div>
    </div>
    <div class="card">
      <div class="table-wrap">
        ${books.length === 0 ? emptyState('No books found', 'Try a different search, or add your first book.') : `
        <table>
          <thead><tr><th>Title</th><th>Category</th><th>ISBN</th><th>Availability</th><th></th></tr></thead>
          <tbody>
            ${books.map(b => `
              <tr class="clickable" data-book-id="${b.bookId}">
                <td><div class="cell-primary">${escapeHtml(b.title)}</div><div class="cell-sub">${escapeHtml(b.author)}</div></td>
                <td>${b.category ? `<span class="badge badge-neutral">${escapeHtml(b.category)}</span>` : '—'}</td>
                <td>${escapeHtml(b.isbn || '—')}</td>
                <td>
                  <div style="display:flex; align-items:center; gap:8px; min-width:120px;">
                    <div class="progress-track" style="flex:1"><div class="progress-fill" style="width:${b.totalCopies ? (b.availableCopies / b.totalCopies * 100) : 0}%"></div></div>
                    <span class="cell-sub" style="white-space:nowrap">${b.availableCopies}/${b.totalCopies}</span>
                  </div>
                </td>
                <td>${iconChevron()}</td>
              </tr>`).join('')}
          </tbody>
        </table>`}
      </div>
    </div>
  `;

  document.querySelectorAll('tr[data-book-id]').forEach(row => {
    row.addEventListener('click', () => { window.location.hash = '#/books/' + row.dataset.bookId; });
  });
  document.getElementById('add-book-btn').addEventListener('click', openAddBookWizard);

  let debounceTimer;
  document.getElementById('book-search').addEventListener('input', (e) => {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => renderBooks(content, e.target.value), 300);
  });
}

async function renderBookDetail(content, id) {
  const books = await api.get('/books');
  const book = books.find(b => String(b.bookId) === String(id));
  if (!book) { content.innerHTML = emptyState('Book not found', 'It may have been deleted.'); return; }

  content.innerHTML = `
    <a href="#/books" class="btn btn-ghost btn-sm" style="margin-bottom:14px">&larr; Back to books</a>
    <div class="page-head">
      <div><h2>${escapeHtml(book.title)}</h2><p>${escapeHtml(book.author)}</p></div>
      <div class="page-actions">
        <button class="btn btn-secondary btn-sm" id="edit-book-btn">${iconEdit()} Edit</button>
        <button class="btn btn-danger btn-sm" id="delete-book-btn">${iconTrash()} Delete</button>
      </div>
    </div>
    <div class="grid-2">
      <div class="card"><div class="card-body">
        <div class="kv-list">
          <div class="kv-row"><span class="k">ISBN</span><span class="v">${escapeHtml(book.isbn || '—')}</span></div>
          <div class="kv-row"><span class="k">Category</span><span class="v">${escapeHtml(book.category || '—')}</span></div>
          <div class="kv-row"><span class="k">Total copies</span><span class="v">${book.totalCopies}</span></div>
          <div class="kv-row"><span class="k">Available now</span><span class="v">${book.availableCopies}</span></div>
        </div>
      </div></div>
      <div class="card"><div class="card-body">
        <div class="stat-label" style="margin-bottom:10px">Copies available</div>
        <div class="progress-track" style="height:10px"><div class="progress-fill" style="width:${book.totalCopies ? (book.availableCopies / book.totalCopies * 100) : 0}%"></div></div>
        <div style="margin-top:18px">
          <a href="#/issue" class="btn btn-primary btn-block">Issue this book</a>
        </div>
      </div></div>
    </div>
  `;

  document.getElementById('delete-book-btn').addEventListener('click', async () => {
    if (!confirm('Delete "' + book.title + '"? This can\'t be undone.')) return;
    try {
      await api.del('/books/' + book.bookId);
      toast('Book deleted', 'success');
      window.location.hash = '#/books';
    } catch (err) { toast(err.message, 'error'); }
  });
  document.getElementById('edit-book-btn').addEventListener('click', () => openEditBookModal(book));
}

function openEditBookModal(book) {
  const root = document.getElementById('modal-root');
  root.innerHTML = `
    <div class="modal-overlay">
      <div class="modal">
        <div class="modal-header"><h3>Edit book</h3><button class="icon-btn" id="modal-close">✕</button></div>
        <div class="modal-body">
          <label class="field"><span>Title</span><input id="ef-title" value="${escapeHtml(book.title)}"/></label>
          <label class="field"><span>Author</span><input id="ef-author" value="${escapeHtml(book.author)}"/></label>
          <div class="field-row">
            <label class="field"><span>ISBN</span><input id="ef-isbn" value="${escapeHtml(book.isbn || '')}"/></label>
            <label class="field"><span>Category</span><input id="ef-category" value="${escapeHtml(book.category || '')}"/></label>
          </div>
          <label class="field"><span>Total copies</span><input id="ef-total" type="number" min="1" value="${book.totalCopies}"/></label>
        </div>
        <div class="modal-footer"><div class="modal-footer-right">
          <button class="btn btn-secondary" id="modal-cancel">Cancel</button>
          <button class="btn btn-primary" id="modal-save">Save changes</button>
        </div></div>
      </div>
    </div>`;
  const close = () => { root.innerHTML = ''; };
  root.querySelector('#modal-close').addEventListener('click', close);
  root.querySelector('#modal-cancel').addEventListener('click', close);
  root.querySelector('#modal-save').addEventListener('click', async () => {
    try {
      await api.put('/books/' + book.bookId, {
        title: document.getElementById('ef-title').value.trim(),
        author: document.getElementById('ef-author').value.trim(),
        isbn: document.getElementById('ef-isbn').value.trim(),
        category: document.getElementById('ef-category').value.trim(),
        totalCopies: document.getElementById('ef-total').value,
      });
      toast('Book updated', 'success');
      close();
      render();
    } catch (err) { toast(err.message, 'error'); }
  });
}

// ---- Add Book wizard (3 steps: Details -> Inventory -> Review) ----
function openAddBookWizard() {
  const wiz = { step: 1, data: { title: '', author: '', isbn: '', category: '', totalCopies: 1 } };
  renderWizard();

  function renderWizard() {
    const root = document.getElementById('modal-root');
    const total = 3;
    root.innerHTML = `
      <div class="modal-overlay">
        <div class="modal">
          <div class="modal-header"><h3>Add a new book</h3><button class="icon-btn" id="modal-close">✕</button></div>
          <div class="wizard-steps">
            ${[1,2,3].map(i => `<div class="wizard-step-dot ${i < wiz.step ? 'done' : i === wiz.step ? 'current' : ''}"></div>`).join('')}
          </div>
          <div class="wizard-step-label">Step ${wiz.step} of ${total}</div>
          <div class="modal-body" id="wizard-body"></div>
          <div class="modal-footer">
            <button class="btn btn-ghost" id="wiz-back" ${wiz.step === 1 ? 'style="visibility:hidden"' : ''}>&larr; Back</button>
            <div class="modal-footer-right">
              <button class="btn btn-secondary" id="modal-cancel">Cancel</button>
              <button class="btn btn-primary" id="wiz-next">${wiz.step === total ? 'Save book' : 'Next'}</button>
            </div>
          </div>
        </div>
      </div>`;

    const body = document.getElementById('wizard-body');
    if (wiz.step === 1) {
      body.innerHTML = `
        <label class="field"><span>Title *</span><input id="wf-title" value="${escapeHtml(wiz.data.title)}" placeholder="The Pragmatic Programmer"/></label>
        <label class="field"><span>Author *</span><input id="wf-author" value="${escapeHtml(wiz.data.author)}" placeholder="David Thomas"/></label>
      `;
    } else if (wiz.step === 2) {
      body.innerHTML = `
        <label class="field"><span>ISBN</span><input id="wf-isbn" value="${escapeHtml(wiz.data.isbn)}" placeholder="978-0-13-595705-9"/></label>
        <label class="field"><span>Category</span><input id="wf-category" value="${escapeHtml(wiz.data.category)}" placeholder="Software Engineering"/></label>
        <label class="field"><span>Total copies</span><input id="wf-total" type="number" min="1" value="${wiz.data.totalCopies}"/></label>
      `;
    } else {
      body.innerHTML = `
        <p style="font-size:13.5px; color:var(--text-dim); margin-bottom:14px;">Review the details before saving.</p>
        <div class="kv-list">
          <div class="kv-row"><span class="k">Title</span><span class="v">${escapeHtml(wiz.data.title)}</span></div>
          <div class="kv-row"><span class="k">Author</span><span class="v">${escapeHtml(wiz.data.author)}</span></div>
          <div class="kv-row"><span class="k">ISBN</span><span class="v">${escapeHtml(wiz.data.isbn || '—')}</span></div>
          <div class="kv-row"><span class="k">Category</span><span class="v">${escapeHtml(wiz.data.category || '—')}</span></div>
          <div class="kv-row"><span class="k">Total copies</span><span class="v">${wiz.data.totalCopies}</span></div>
        </div>
      `;
    }

    document.getElementById('modal-close').addEventListener('click', () => root.innerHTML = '');
    document.getElementById('modal-cancel').addEventListener('click', () => root.innerHTML = '');
    document.getElementById('wiz-back').addEventListener('click', () => { wiz.step--; renderWizard(); });
    document.getElementById('wiz-next').addEventListener('click', async () => {
      if (wiz.step === 1) {
        const title = document.getElementById('wf-title').value.trim();
        const author = document.getElementById('wf-author').value.trim();
        if (!title || !author) { toast('Title and author are required', 'error'); return; }
        wiz.data.title = title; wiz.data.author = author;
        wiz.step = 2; renderWizard();
      } else if (wiz.step === 2) {
        wiz.data.isbn = document.getElementById('wf-isbn').value.trim();
        wiz.data.category = document.getElementById('wf-category').value.trim();
        wiz.data.totalCopies = document.getElementById('wf-total').value || 1;
        wiz.step = 3; renderWizard();
      } else {
        try {
          await api.post('/books', wiz.data);
          toast('Book added to catalog', 'success');
          root.innerHTML = '';
          render();
        } catch (err) { toast(err.message, 'error'); }
      }
    });
  }
}

// ===================== MEMBERS =====================
async function renderMembers(content, filterState) {
  const members = await api.get('/members');
  state.membersCache = members;
  const f = filterState || { q: '', status: 'all' };

  const filtered = members.filter(m => {
    const q = f.q.trim().toLowerCase();
    const matchesQ = !q ||
      (m.name || '').toLowerCase().includes(q) ||
      (m.email || '').toLowerCase().includes(q) ||
      (m.phone || '').toLowerCase().includes(q);
    if (!matchesQ) return false;
    if (f.status === 'overdue') return m.overdueCount > 0;
    if (f.status === 'dues') return m.duesOwed > 0;
    if (f.status === 'issued') return m.issuedCount > 0;
    if (f.status === 'clear') return m.issuedCount === 0;
    return true;
  });

  content.innerHTML = `
    <div class="page-head">
      <div><h2>Members</h2><p>${members.length} registered member${members.length === 1 ? '' : 's'}.</p></div>
      <div class="page-actions"><button class="btn btn-primary btn-sm" id="add-member-btn">+ Add member</button></div>
    </div>
    <div class="card">
      <div class="card-head" style="flex-wrap:wrap; gap:10px;">
        <div class="search-box" style="min-width:260px">${iconSearch()}<input id="member-search" placeholder="Search by name, phone, or email…" value="${escapeHtml(f.q)}"/></div>
        <select id="member-status-filter" class="field" style="width:auto; margin:0; border:1.5px solid var(--border); border-radius:10px; padding:8px 12px; background:var(--surface-soft); font-size:13.5px;">
          <option value="all" ${f.status === 'all' ? 'selected' : ''}>All members</option>
          <option value="issued" ${f.status === 'issued' ? 'selected' : ''}>Currently has books</option>
          <option value="overdue" ${f.status === 'overdue' ? 'selected' : ''}>Overdue</option>
          <option value="dues" ${f.status === 'dues' ? 'selected' : ''}>Has dues</option>
          <option value="clear" ${f.status === 'clear' ? 'selected' : ''}>Nothing out / clear</option>
        </select>
      </div>
      <div class="table-wrap">
        ${filtered.length === 0 ? emptyState('No matching members', 'Try a different search or filter.') : `
        <table>
          <thead><tr><th>Name</th><th>Contact</th><th>Joined</th><th>Account</th><th>Borrowing status</th><th></th></tr></thead>
          <tbody>
            ${filtered.map(m => `
              <tr class="clickable" data-member-id="${m.memberId}">
                <td class="cell-primary">${escapeHtml(m.name)}</td>
                <td>${escapeHtml(m.email)}<div class="cell-sub">${escapeHtml(m.phone || '')}</div></td>
                <td>${fmtDate(m.joinDate)}</td>
                <td>${statusBadge(m.status)}</td>
                <td>${borrowingStatusBadge(m)}</td>
                <td>${iconChevron()}</td>
              </tr>`).join('')}
          </tbody>
        </table>`}
      </div>
    </div>
  `;
  document.querySelectorAll('tr[data-member-id]').forEach(row => {
    row.addEventListener('click', () => { window.location.hash = '#/members/' + row.dataset.memberId; });
  });
  document.getElementById('add-member-btn').addEventListener('click', openAddMemberWizard);

  let debounceTimer;
  document.getElementById('member-search').addEventListener('input', (e) => {
    clearTimeout(debounceTimer);
    const nextQ = e.target.value;
    debounceTimer = setTimeout(() => renderMembers(content, { q: nextQ, status: f.status }), 250);
  });
  document.getElementById('member-status-filter').addEventListener('change', (e) => {
    renderMembers(content, { q: f.q, status: e.target.value });
  });
}

function borrowingStatusBadge(m) {
  if (m.overdueCount > 0) {
    return `<span class="badge badge-danger">${m.overdueCount} overdue · ₹${Number(m.duesOwed).toFixed(2)} due</span>`;
  }
  if (m.issuedCount > 0) {
    return `<span class="badge badge-info">${m.issuedCount} book${m.issuedCount === 1 ? '' : 's'} out</span>`;
  }
  return `<span class="badge badge-success">Clear</span>`;
}

function statusBadge(status) {
  const s = (status || 'active').toLowerCase();
  if (s === 'active') return `<span class="badge badge-success">Active</span>`;
  if (s === 'suspended') return `<span class="badge badge-danger">Suspended</span>`;
  return `<span class="badge badge-neutral">${escapeHtml(status)}</span>`;
}

async function renderMemberDetail(content, id) {
  const members = await api.get('/members');
  const member = members.find(m => String(m.memberId) === String(id));
  if (!member) { content.innerHTML = emptyState('Member not found', 'It may have been deleted.'); return; }
  const history = await api.get('/members/' + id + '/history');

  content.innerHTML = `
    <a href="#/members" class="btn btn-ghost btn-sm" style="margin-bottom:14px">&larr; Back to members</a>
    <div class="page-head">
      <div><h2>${escapeHtml(member.name)}</h2><p>${escapeHtml(member.email)}</p></div>
      <div class="page-actions">
        <button class="btn btn-secondary btn-sm" id="edit-member-btn">${iconEdit()} Edit</button>
        <button class="btn btn-danger btn-sm" id="delete-member-btn">${iconTrash()} Delete</button>
      </div>
    </div>
    <div class="grid-2">
      <div class="card">
        <div class="card-head"><h3>Borrowing history</h3></div>
        <div class="table-wrap">
          ${history.length === 0 ? emptyState('No history yet', 'This member hasn\'t borrowed any books.') : `
          <table><thead><tr><th>Book</th><th>Issued</th><th>Due</th><th>Status</th></tr></thead>
          <tbody>${history.map(t => `
            <tr>
              <td class="cell-primary">${escapeHtml(t.bookTitle)}</td>
              <td>${fmtDate(t.issueDate)}</td>
              <td>${fmtDate(t.dueDate)}</td>
              <td>${transactionStatusBadge(t.status)}</td>
            </tr>`).join('')}</tbody></table>`}
        </div>
      </div>
      <div class="card"><div class="card-body">
        <div class="kv-list">
          <div class="kv-row"><span class="k">Borrowing status</span><span class="v">${borrowingStatusBadge(member)}</span></div>
          <div class="kv-row"><span class="k">Phone</span><span class="v">${escapeHtml(member.phone || '—')}</span></div>
          <div class="kv-row"><span class="k">Address</span><span class="v">${escapeHtml(member.address || '—')}</span></div>
          <div class="kv-row"><span class="k">Joined</span><span class="v">${fmtDate(member.joinDate)}</span></div>
          <div class="kv-row"><span class="k">Status</span><span class="v">${statusBadge(member.status)}</span></div>
        </div>
        <div style="margin-top:18px"><a href="#/issue" class="btn btn-primary btn-block">Issue a book to this member</a></div>
      </div></div>
    </div>
  `;
  document.getElementById('delete-member-btn').addEventListener('click', async () => {
    if (!confirm('Delete "' + member.name + '"? This can\'t be undone.')) return;
    try { await api.del('/members/' + member.memberId); toast('Member deleted', 'success'); window.location.hash = '#/members'; }
    catch (err) { toast(err.message, 'error'); }
  });
  document.getElementById('edit-member-btn').addEventListener('click', () => openEditMemberModal(member));
}

function transactionStatusBadge(status) {
  const s = (status || '').toLowerCase();
  if (s === 'returned') return `<span class="badge badge-success">Returned</span>`;
  if (s === 'overdue') return `<span class="badge badge-danger">Overdue</span>`;
  return `<span class="badge badge-warn">Issued</span>`;
}

function openEditMemberModal(member) {
  const root = document.getElementById('modal-root');
  root.innerHTML = `
    <div class="modal-overlay"><div class="modal">
      <div class="modal-header"><h3>Edit member</h3><button class="icon-btn" id="modal-close">✕</button></div>
      <div class="modal-body">
        <label class="field"><span>Name</span><input id="mf-name" value="${escapeHtml(member.name)}"/></label>
        <label class="field"><span>Email</span><input id="mf-email" value="${escapeHtml(member.email)}"/></label>
        <div class="field-row">
          <label class="field"><span>Phone</span><input id="mf-phone" value="${escapeHtml(member.phone || '')}"/></label>
          <label class="field"><span>Status</span>
            <select id="mf-status">
              <option value="active" ${member.status === 'active' ? 'selected' : ''}>Active</option>
              <option value="suspended" ${member.status === 'suspended' ? 'selected' : ''}>Suspended</option>
            </select>
          </label>
        </div>
        <label class="field"><span>Address</span><input id="mf-address" value="${escapeHtml(member.address || '')}"/></label>
      </div>
      <div class="modal-footer"><div class="modal-footer-right">
        <button class="btn btn-secondary" id="modal-cancel">Cancel</button>
        <button class="btn btn-primary" id="modal-save">Save changes</button>
      </div></div>
    </div></div>`;
  const close = () => root.innerHTML = '';
  root.querySelector('#modal-close').addEventListener('click', close);
  root.querySelector('#modal-cancel').addEventListener('click', close);
  root.querySelector('#modal-save').addEventListener('click', async () => {
    try {
      await api.put('/members/' + member.memberId, {
        name: document.getElementById('mf-name').value.trim(),
        email: document.getElementById('mf-email').value.trim(),
        phone: document.getElementById('mf-phone').value.trim(),
        address: document.getElementById('mf-address').value.trim(),
        status: document.getElementById('mf-status').value,
      });
      toast('Member updated', 'success');
      close(); render();
    } catch (err) { toast(err.message, 'error'); }
  });
}

// ---- Add Member wizard (2 steps: Personal -> Contact & Review) ----
function openAddMemberWizard() {
  const wiz = { step: 1, data: { name: '', email: '', phone: '', address: '' } };
  renderWizard();
  function renderWizard() {
    const root = document.getElementById('modal-root');
    const total = 2;
    root.innerHTML = `
      <div class="modal-overlay"><div class="modal">
        <div class="modal-header"><h3>Add a new member</h3><button class="icon-btn" id="modal-close">✕</button></div>
        <div class="wizard-steps">${[1,2].map(i => `<div class="wizard-step-dot ${i < wiz.step ? 'done' : i === wiz.step ? 'current' : ''}"></div>`).join('')}</div>
        <div class="wizard-step-label">Step ${wiz.step} of ${total}</div>
        <div class="modal-body" id="wizard-body"></div>
        <div class="modal-footer">
          <button class="btn btn-ghost" id="wiz-back" ${wiz.step === 1 ? 'style="visibility:hidden"' : ''}>&larr; Back</button>
          <div class="modal-footer-right">
            <button class="btn btn-secondary" id="modal-cancel">Cancel</button>
            <button class="btn btn-primary" id="wiz-next">${wiz.step === total ? 'Save member' : 'Next'}</button>
          </div>
        </div>
      </div></div>`;
    const body = document.getElementById('wizard-body');
    if (wiz.step === 1) {
      body.innerHTML = `
        <label class="field"><span>Full name *</span><input id="wm-name" value="${escapeHtml(wiz.data.name)}" placeholder="Alex Rivera"/></label>
        <label class="field"><span>Email *</span><input id="wm-email" type="email" value="${escapeHtml(wiz.data.email)}" placeholder="alex@example.com"/></label>
      `;
    } else {
      body.innerHTML = `
        <label class="field"><span>Phone</span><input id="wm-phone" value="${escapeHtml(wiz.data.phone)}" placeholder="(555) 123-4567"/></label>
        <label class="field"><span>Address</span><input id="wm-address" value="${escapeHtml(wiz.data.address)}" placeholder="221B Baker Street"/></label>
        <div class="kv-list" style="margin-top:14px; padding-top:14px; border-top:1px solid var(--border)">
          <div class="kv-row"><span class="k">Name</span><span class="v">${escapeHtml(wiz.data.name)}</span></div>
          <div class="kv-row"><span class="k">Email</span><span class="v">${escapeHtml(wiz.data.email)}</span></div>
        </div>
      `;
    }
    document.getElementById('modal-close').addEventListener('click', () => root.innerHTML = '');
    document.getElementById('modal-cancel').addEventListener('click', () => root.innerHTML = '');
    document.getElementById('wiz-back').addEventListener('click', () => { wiz.step--; renderWizard(); });
    document.getElementById('wiz-next').addEventListener('click', async () => {
      if (wiz.step === 1) {
        const name = document.getElementById('wm-name').value.trim();
        const email = document.getElementById('wm-email').value.trim();
        if (!name || !email) { toast('Name and email are required', 'error'); return; }
        wiz.data.name = name; wiz.data.email = email;
        wiz.step = 2; renderWizard();
      } else {
        wiz.data.phone = document.getElementById('wm-phone').value.trim();
        wiz.data.address = document.getElementById('wm-address').value.trim();
        try {
          await api.post('/members', wiz.data);
          toast('Member added', 'success');
          root.innerHTML = ''; render();
        } catch (err) { toast(err.message, 'error'); }
      }
    });
  }
}

// ===================== ISSUE / RETURN =====================
async function renderIssue(content) {
  const [books, members, issued] = await Promise.all([
    api.get('/books'), api.get('/members'), api.get('/transactions/issued'),
  ]);
  const availableBooks = books.filter(b => b.availableCopies > 0);

  content.innerHTML = `
    <div class="page-head"><div><h2>Issue &amp; Return</h2><p>Hand out books and process returns.</p></div></div>
    <div class="grid-2">
      <div class="card">
        <div class="card-head"><h3>Issue a book</h3></div>
        <div class="card-body">
          <label class="field"><span>Book</span>
            <select id="issue-book">
              <option value="">Select a book…</option>
              ${availableBooks.map(b => `<option value="${b.bookId}">${escapeHtml(b.title)} (${b.availableCopies} available)</option>`).join('')}
            </select>
          </label>
          <label class="field"><span>Member</span>
            <select id="issue-member">
              <option value="">Select a member…</option>
              ${members.map(m => `<option value="${m.memberId}">${escapeHtml(m.name)}</option>`).join('')}
            </select>
          </label>
          <button class="btn btn-primary btn-block" id="issue-submit">Issue book</button>
        </div>
      </div>
      <div class="card">
        <div class="card-head"><h3>Currently issued</h3></div>
        <div class="table-wrap">
          ${issued.length === 0 ? emptyState('Nothing issued right now', '') : `
          <table><thead><tr><th>Book</th><th>Member</th><th>Due</th><th></th></tr></thead>
          <tbody>${issued.map(t => `
            <tr>
              <td class="cell-primary">${escapeHtml(t.bookTitle)}</td>
              <td>${escapeHtml(t.memberName)}</td>
              <td>${fmtDate(t.dueDate)}</td>
              <td><button class="btn btn-secondary btn-sm return-btn" data-tid="${t.transactionId}">Return</button></td>
            </tr>`).join('')}</tbody></table>`}
        </div>
      </div>
    </div>
  `;

  document.getElementById('issue-submit').addEventListener('click', async () => {
    const bookId = document.getElementById('issue-book').value;
    const memberId = document.getElementById('issue-member').value;
    if (!bookId || !memberId) { toast('Pick a book and a member first', 'error'); return; }
    try {
      const res = await api.post('/issue', { bookId, memberId });
      toast(res.message && res.message.includes('SUCCESS') ? 'Book issued successfully' : res.message, 'success');
      renderIssue(content);
    } catch (err) { toast(err.message, 'error'); }
  });

  document.querySelectorAll('.return-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      try {
        await api.post('/return/' + btn.dataset.tid);
        toast('Book returned', 'success');
        renderIssue(content);
      } catch (err) { toast(err.message, 'error'); }
    });
  });
}

// ===================== OVERDUE =====================
async function renderOverdue(content) {
  const overdue = await api.get('/transactions/overdue');
  content.innerHTML = `
    <div class="page-head"><div><h2>Overdue &amp; Fines</h2><p>${overdue.length} loan${overdue.length === 1 ? '' : 's'} past due.</p></div></div>
    <div class="card">
      <div class="table-wrap">
        ${overdue.length === 0 ? emptyState('Nothing overdue', 'All loans are within their due date.') : `
        <table><thead><tr><th>Book</th><th>Member</th><th>Issued</th><th>Due</th><th>Est. fine</th><th></th></tr></thead>
        <tbody>${overdue.map(t => `
          <tr>
            <td class="cell-primary">${escapeHtml(t.bookTitle)}</td>
            <td>${escapeHtml(t.memberName)}</td>
            <td>${fmtDate(t.issueDate)}</td>
            <td>${fmtDate(t.dueDate)}</td>
            <td><span class="badge badge-danger">₹${Number(t.projectedFine).toFixed(2)}</span></td>
            <td><button class="btn btn-secondary btn-sm return-btn" data-tid="${t.transactionId}">Mark returned</button></td>
          </tr>`).join('')}</tbody></table>`}
      </div>
    </div>
  `;
  document.querySelectorAll('.return-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      try { await api.post('/return/' + btn.dataset.tid); toast('Book returned', 'success'); renderOverdue(content); }
      catch (err) { toast(err.message, 'error'); }
    });
  });
}

// ===================== TRANSACTIONS =====================
async function renderTransactions(content) {
  const list = await api.get('/transactions');
  content.innerHTML = `
    <div class="page-head"><div><h2>Transactions</h2><p>Full loan ledger, ${list.length} record${list.length === 1 ? '' : 's'}.</p></div></div>
    <div class="card">
      <div class="table-wrap">
        ${list.length === 0 ? emptyState('No transactions yet', '') : `
        <table><thead><tr><th>Book</th><th>Member</th><th>Issued</th><th>Due</th><th>Returned</th><th>Fine</th><th>Status</th></tr></thead>
        <tbody>${list.map(t => `
          <tr>
            <td class="cell-primary">${escapeHtml(t.bookTitle)}</td>
            <td>${escapeHtml(t.memberName)}</td>
            <td>${fmtDate(t.issueDate)}</td>
            <td>${fmtDate(t.dueDate)}</td>
            <td>${t.returnDate ? fmtDate(t.returnDate) : '—'}</td>
            <td>${Number(t.fineAmount) > 0 ? '₹' + Number(t.fineAmount).toFixed(2) : '—'}</td>
            <td>${transactionStatusBadge(t.status)}</td>
          </tr>`).join('')}</tbody></table>`}
      </div>
    </div>
  `;
}

// ===================== REPORTS =====================
async function renderReports(content) {
  const r = await api.get('/reports');
  const maxCat = Math.max(1, ...r.categoryBreakdown.map(c => c.count));
  const maxTop = Math.max(1, ...r.topBooks.map(b => b.count));

  content.innerHTML = `
    <div class="page-head"><div><h2>Reports</h2><p>A quick pulse on how the library is being used.</p></div></div>
    <div class="stat-grid" style="grid-template-columns:repeat(2,1fr)">
      ${statCard('Fines collected', '₹' + Number(r.finesCollected).toFixed(2), 'var(--success-soft)', 'var(--success)', iconArrow())}
      ${statCard('Fines outstanding', '₹' + Number(r.finesOutstanding).toFixed(2), 'var(--danger-soft)', 'var(--danger)', iconClock())}
    </div>
    <div class="grid-2">
      <div class="card">
        <div class="card-head"><h3>Books by category</h3></div>
        <div class="card-body">
          ${r.categoryBreakdown.length === 0 ? emptyState('No data yet', '') : `
          <div class="bar-chart">
            ${r.categoryBreakdown.map(c => `
              <div class="bar-col">
                <div class="bar-value">${c.count}</div>
                <div class="bar" style="height:${(c.count / maxCat * 120) || 2}px"></div>
                <div class="bar-label">${escapeHtml(c.category || 'Uncategorized')}</div>
              </div>`).join('')}
          </div>`}
        </div>
      </div>
      <div class="card">
        <div class="card-head"><h3>Most borrowed books</h3></div>
        <div class="card-body">
          ${r.topBooks.length === 0 ? emptyState('No data yet', '') : `
          <div class="kv-list">
            ${r.topBooks.map(b => `
              <div>
                <div class="kv-row"><span class="k">${escapeHtml(b.title)}</span><span class="v">${b.count}×</span></div>
                <div class="progress-track" style="margin-top:6px"><div class="progress-fill" style="width:${(b.count / maxTop * 100)}%"></div></div>
              </div>`).join('')}
          </div>`}
        </div>
      </div>
    </div>
  `;
}

// ===================== PROFILE =====================
function renderProfile(content) {
  const u = state.user;
  content.innerHTML = `
    <div class="page-head"><div><h2>Profile &amp; Settings</h2><p>Manage your account details.</p></div></div>
    <div class="grid-2">
      <div class="card">
        <div class="card-head"><h3>Account details</h3></div>
        <div class="card-body">
          <div id="profile-error" class="form-error" hidden></div>
          <label class="field"><span>Full name</span><input id="pf-name" value="${escapeHtml(u.fullName)}"/></label>
          <label class="field"><span>Email</span><input id="pf-email" value="${escapeHtml(u.email)}"/></label>
          <label class="field"><span>Username</span><input value="${escapeHtml(u.username)}" disabled/></label>
          <button class="btn btn-primary" id="profile-save">Save changes</button>
        </div>
      </div>
      <div class="card">
        <div class="card-head"><h3>Change password</h3></div>
        <div class="card-body">
          <div id="pw-error" class="form-error" hidden></div>
          <label class="field"><span>Current password</span><input type="password" id="pw-current"/></label>
          <label class="field"><span>New password</span><input type="password" id="pw-new"/></label>
          <button class="btn btn-secondary" id="pw-save">Update password</button>
        </div>
      </div>
    </div>
  `;

  document.getElementById('profile-save').addEventListener('click', async () => {
    const errBox = document.getElementById('profile-error');
    errBox.hidden = true;
    try {
      const res = await api.put('/profile', {
        fullName: document.getElementById('pf-name').value.trim(),
        email: document.getElementById('pf-email').value.trim(),
      });
      state.user = res.user;
      document.getElementById('topbar-avatar').textContent = initials(state.user.fullName);
      document.getElementById('topbar-user-name').textContent = state.user.fullName;
      toast('Profile updated', 'success');
    } catch (err) { errBox.textContent = err.message; errBox.hidden = false; }
  });

  document.getElementById('pw-save').addEventListener('click', async () => {
    const errBox = document.getElementById('pw-error');
    errBox.hidden = true;
    try {
      await api.post('/change-password', {
        currentPassword: document.getElementById('pw-current').value,
        newPassword: document.getElementById('pw-new').value,
      });
      toast('Password updated', 'success');
      document.getElementById('pw-current').value = '';
      document.getElementById('pw-new').value = '';
    } catch (err) { errBox.textContent = err.message; errBox.hidden = false; }
  });
}
