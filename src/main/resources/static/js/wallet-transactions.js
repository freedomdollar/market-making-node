/* Wallet Transactions page logic
   GET /api/get-wallet-transactions?wallet=main&page=0&limit=20
*/
(function () {
    const API = '/api/get-wallet-transactions';
    const WALLET = 'main'; // single wallet, fixed
    const els = {
        alert: $('#tx-alert'),
        status: $('#tx-status'),
        rangePill: $('#range-pill'),
        tbody: $('#tx-tbody'),
        pagination: $('#tx-pagination'),
        pageHuman: $('#page-human'),
        pagesTotal: $('#pages-total'),
        itemsTotal: $('#items-total'),
        limitSelect: $('#limit-select'),
        refreshBtn: $('#btn-refresh'),
    };
    var blockchainHeight = 0;

    // ---- state ----
    const params = new URLSearchParams(window.location.search);
    const initialPage = clampInt(params.get('page'), 0, 0);
    const initialLimit = clampInt(params.get('limit'), 20, 10);

    const state = {
        page: initialPage,        // 0-based
        limit: initialLimit,      // 10/20/50/100
        totalItems: 0,
        totalPages: 0,
        isLoading: false
    };

    // Initialize UI
    els.limitSelect.val(String(state.limit));

    // Events
    els.limitSelect.on('change', () => {
        state.limit = parseInt(els.limitSelect.val(), 10) || 20;
        state.page = 0;
        pushUrl();
        load();
    });

    els.refreshBtn.on('click', () => load());

    els.pagination.on('click', 'a.page-link[data-page]', (e) => {
        e.preventDefault();
        const p = parseInt($(e.currentTarget).data('page'), 10);
        if (!Number.isNaN(p) && p >= 0 && p !== state.page) {
            state.page = p;
            pushUrl();
            load();
        }
    });

    // Copy buttons (tx hash / address)
    $(document).on('click', '[data-copy]', async (e) => {
        const text = e.currentTarget.getAttribute('data-copy');
        try {
            await navigator.clipboard.writeText(text);
            const btn = e.currentTarget;
            const original = btn.innerHTML;
            btn.innerHTML = '<i class="bi bi-clipboard-check"></i>';
            setTimeout(() => (btn.innerHTML = original), 900);
        } catch { /* ignore */ }
    });

    // Initial load
    load();

    // ---- functions ----

    function toIsoLocal(dLike){
        const d = (dLike instanceof Date) ? dLike : new Date(dLike);
        if (isNaN(d.getTime())) return "—";
        const pad = (n) => String(n).padStart(2, '0');
        const y = d.getFullYear(), m = pad(d.getMonth()+1), day = pad(d.getDate());
        const h = pad(d.getHours()), min = pad(d.getMinutes()), s = pad(d.getSeconds());
        const off = -d.getTimezoneOffset(); // minutes; positive = east of UTC
        const sign = off >= 0 ? "+" : "-";
        const oh = pad(Math.floor(Math.abs(off) / 60));
        const om = pad(Math.abs(off) % 60);
        return `${y}-${m}-${day} ${h}:${min}:${s}`;
    }

    function load() {
        if (state.isLoading) return;
        state.isLoading = true;
        setStatus('Loading…');
        hideAlert();

        const url = `${API}?wallet=${encodeURIComponent(WALLET)}&page=${state.page}&limit=${state.limit}`;
        $.getJSON(url).done((res) => {
            // Expected shape:
            // { status, message, type, totalItems, totalPages, currentPage, data: [...] }
            const currentPage = isFiniteInt(res?.currentPage) ? res.currentPage : state.page;
            state.page = currentPage;

            state.totalItems = isFiniteInt(res?.totalItems) ? res.totalItems : (Array.isArray(res?.data) ? res.data.length : 0);

            // Prefer API totalPages when it looks sane; otherwise compute fallback
            const fromApiPages = isFiniteInt(res?.totalPages) ? res.totalPages : null;
            const fallbackPages = Math.max(1, Math.ceil((state.totalItems || 0) / Math.max(1, state.limit)));
            state.totalPages = (fromApiPages && fromApiPages > 0) ? fromApiPages : fallbackPages;

            if (res.walletInfo) {
                blockchainHeight = res.walletInfo.height;
            }

            renderRows(Array.isArray(res?.data) ? res.data : []);
            renderPagination();
            renderMeta();

            const now = new Date();
            setStatus(`Updated ${toIsoLocal(now)}`);
        }).fail((xhr) => {
            showAlert(`Failed to load transactions (HTTP ${xhr?.status || 'error'}).`);
            setStatus('Error');
            els.tbody.html(`
        <tr><td colspan="8" class="text-muted">Could not load data.</td></tr>
      `);
            renderPagination(true);
            renderMeta();
        }).always(() => {
            state.isLoading = false;
        });
    }

    function renderRows(items) {
        if (!items.length) {
            els.tbody.html(`
        <tr><td colspan="8" class="text-muted">No transactions found.</td></tr>
      `);
            els.rangePill.text('Showing 0–0 of 0');
            return;
        }

        const startItem = state.page * state.limit + 1;
        const endItem = startItem + items.length - 1;
        els.rangePill.text(`Showing ${startItem.toLocaleString()}–${endItem.toLocaleString()} of ${state.totalItems.toLocaleString()}`);

        const html = items.map(rowHtml).join('');
        els.tbody.html(html);
    }

    function rowHtml(t) {

        const swap = t.swapId > 0;
        const income = !!t.income;
        const mining = !!(t.mining);

        var confirmations = 0;

        if (t.height > 0 && blockchainHeight > 0) {
            confirmations = blockchainHeight - t.height;
        }

        const dirBadge = income
            ? `<span class="badge bg-success"><i class="bi bi-arrow-down-right"></i> Incoming${mining ? ' • Mining' : ''}${swap ? ' swap' : ''}</span>`
            : `<span class="badge bg-danger"><i class="bi bi-arrow-up-right"></i> Outgoing${mining ? ' • Mining' : ''}${swap ? ' swap' : ''}</span>`;

        const amtClass = income ? 'amount-in' : 'amount-out';
        const sign = income ? '+' : '−';
        const amountStr = formatAmount(t.amount);

        const ticker = t.ticker || '';
        const fullName = t.fullName || '';
        const asset = [ticker, fullName].filter(Boolean).join(' · ');

        const alias = (t.remoteAlias || '').trim();
        const remote = alias ? `<span title="${escapeHtml(alias)}">${escapeHtml(alias)}</span>`
            : (t.remoteAddress ? `<span class="mono" title="${escapeHtml(t.remoteAddress)}">${shorten(t.remoteAddress)}</span>` : '—');

        const hashShort = t.txId ? shorten(t.txId, 18, 18) : '—';
        const ts = t.timestamp ? new Date(t.timestamp) : null;

        return `
      <tr>
          <td>${ts ? toIsoLocal(ts) : '—'}</td>
          <td>${dirBadge}</td>
          <td class="${amtClass}">${sign}${amountStr} ${escapeHtml(ticker || '')}</td>
          <td>${asset ? escapeHtml(asset) : '—'}</td>
          <td><div class="truncate">${remote}</div></td>
          <td>
            ${t.txId ? `
              <span class="mono" title="${escapeHtml(t.txId)}">${hashShort}</span>
              <button class="btn btn-sm btn-outline-secondary ms-2" title="Copy tx hash" data-copy="${escapeAttr(t.txId)}">
                <i class="bi bi-clipboard"></i>
              </button>` : '—'}
          </td>
          <td>${isFiniteInt(confirmations) ? Number(confirmations).toLocaleString() : '—'}</td>
      </tr>
    `;
    }

    function renderPagination(disableAll = false) {
        const total = Math.max(1, state.totalPages);
        const current = clampInt(state.page, 0, 0);
        const disabled = !!disableAll;

        const items = [];
        const mk = (label, page, disabledBtn = false, active = false, title = '') =>
            `<li class="page-item ${disabled || disabledBtn ? 'disabled' : ''} ${active ? 'active' : ''}">
         <a class="page-link" href="#" data-page="${page}" title="${escapeAttr(title || '')}">${label}</a>
       </li>`;

        // First / Prev
        items.push(mk('&laquo;', 0, current === 0, false, 'First page'));
        items.push(mk('&lsaquo;', Math.max(0, current - 1), current === 0, false, 'Previous page'));

        // Window of pages (up to 5)
        const win = 5;
        let start = Math.max(0, current - Math.floor(win / 2));
        let end = Math.min(total - 1, start + win - 1);
        if (end - start + 1 < win) start = Math.max(0, end - win + 1);

        for (let p = start; p <= end; p++) {
            items.push(mk(String(p + 1), p, false, p === current, `Page ${p + 1}`));
        }

        // Next / Last
        items.push(mk('&rsaquo;', Math.min(total - 1, current + 1), current >= total - 1, false, 'Next page'));
        items.push(mk('&raquo;', total - 1, current >= total - 1, false, 'Last page'));

        els.pagination.html(items.join(''));
    }

    function renderMeta() {
        els.pageHuman.text(String(state.page + 1));
        els.pagesTotal.text(String(Math.max(1, state.totalPages)));
        els.itemsTotal.text((state.totalItems || 0).toLocaleString());
    }

    function pushUrl() {
        const sp = new URLSearchParams(window.location.search);
        sp.set('page', String(state.page));
        sp.set('limit', String(state.limit));
        const newUrl = `${window.location.pathname}?${sp.toString()}`;
        window.history.replaceState({}, '', newUrl);
    }

    // ---- utils ----
    function setStatus(text) { els.status.text(text); }
    function showAlert(message, type = 'warning') {
        els.alert.removeClass('d-none').removeClass('alert-danger alert-warning alert-info')
            .addClass('alert-' + type).html(message);
    }
    function hideAlert(){ els.alert.addClass('d-none').empty(); }

    function clampInt(v, fallback, min) {
        const n = parseInt(v, 10);
        if (Number.isNaN(n) || !Number.isFinite(n)) return fallback;
        return Math.max(min, n);
    }
    function isFiniteInt(v){ return Number.isFinite(parseInt(v, 10)); }

    function shorten(s, left = 10, right = 10) {
        if (!s || s.length <= left + right + 1) return s || '';
        return `${s.slice(0, left)}…${s.slice(-right)}`;
    }

    function formatAmount(x) {
        if (typeof x !== 'number') x = Number(x);
        if (!Number.isFinite(x)) return '0';
        // keep up to 12 fraction digits but trim trailing zeros
        return new Intl.NumberFormat(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 12 }).format(x);
    }

    function escapeHtml(s) {
        return String(s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }
    function escapeAttr(s){ return escapeHtml(s).replace(/"/g, '&quot;'); }
})();