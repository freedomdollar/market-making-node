(() => {
    const FEE_ZANO = 0.01;
    const AUTO_REFRESH_MS = 5000;

    // -- Elements
    const els = {
        sidebar: document.getElementById('sidebar'),
        sidebarBackdrop: document.getElementById('sidebar-backdrop'),
        btnToggleSidebar: document.getElementById('btn-toggle-sidebar'),

        assetsStatus: document.getElementById('assets-status'),
        assetSelect: document.getElementById('asset-select'),
        assetBalance: document.getElementById('asset-balance'),
        availableHelp: document.getElementById('available-help'),
        zanoBalance: document.getElementById('zano-balance'),
        amountInput: document.getElementById('amount-input'),
        btnMax: document.getElementById('btn-max'),
        feeOverlay: document.getElementById('fee-overlay'),
        cardAsset: document.getElementById('card-asset'),

        addressInput: document.getElementById('address-input'),
        btnCheckAddress: document.getElementById('btn-check-address'),
        addressStatus: document.getElementById('address-status'),
        addressHelp: document.getElementById('address-help'),
        rowBaseAddress: document.getElementById('row-base-address'),
        baseAddress: document.getElementById('base-address'),
        rowPaymentId: document.getElementById('row-payment-id'),
        paymentId: document.getElementById('payment-id'),
        rowAlias: document.getElementById('row-alias'),
        aliasText: document.getElementById('alias-text'),

        commentInput: document.getElementById('comment-input'),
        twoFaInput: document.getElementById('2fa-input'),
        btnSend: document.getElementById('btn-send'),
        btnReset: document.getElementById('btn-reset'),
        sendAlert: document.getElementById('send-alert'),
    };

    // -- State
    let assets = []; // normalized: { assetId, symbol, name, balance, decimals, totalBalance? }
    let selected = null; // current asset object
    let zanoBal = 0;
    let addressOk = false;
    let lastQueryPayload = null;
    let autoTimer = null;

    // Sidebar toggling (mobile)
    if (els.btnToggleSidebar) {
        els.btnToggleSidebar.addEventListener('click', () => {
            els.sidebar.classList.toggle('show');
            els.sidebarBackdrop.classList.toggle('show');
        });
        els.sidebarBackdrop.addEventListener('click', () => {
            els.sidebar.classList.remove('show');
            els.sidebarBackdrop.classList.remove('show');
        });
    }

    // Helpers
    const pow10 = (d) => Math.pow(10, d);
    const roundDown = (n, d) => Math.floor(Number(n) * pow10(d)) / pow10(d);
    const fmt = (n, d = 12) => {
        if (n === null || n === undefined || isNaN(n)) return '—';
        const s = Number(n);
        if (!isFinite(s)) return '—';
        return s.toLocaleString(undefined, { maximumFractionDigits: Math.min(d, 12) });
    };
    const stepFor = (d) => (d > 0 ? '0.' + '0'.repeat(d - 1) + '1' : '1');
    const trimZeros = (s) => s.replace(/\.0+$/, '').replace(/(\.\d*?)0+$/, '$1');
    const hhmmss = () => {
        const d = new Date();
        const p = (x) => String(x).padStart(2, '0');
        return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
    };

    const showAlert = (type, html) => {
        els.sendAlert.className = 'alert alert-' + (type === 'success' ? 'success' : 'danger');
        els.sendAlert.innerHTML = html;
        els.sendAlert.classList.remove('d-none');
        setTimeout(() => { els.sendAlert.classList.add('d-none'); }, 8000);
    };

    const setSending = (flag) => {
        els.btnSend.disabled = flag;
        els.btnSend.querySelector('.default').classList.toggle('d-none', flag);
        els.btnSend.querySelector('.sending').classList.toggle('d-none', !flag);
    };

    const postJson = async (url, data) => {
        const res = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });
        const json = await res.json().catch(() => ({}));
        if (!res.ok) {
            const msg = (json && (json.message || json.error)) || ('HTTP ' + res.status);
            const err = new Error(msg);
            err.response = res;
            err.json = json;
            throw err;
        }
        return json;
    };

    const tryGet = async (url) => {
        try {
            const res = await fetch(url);
            if (!res.ok) throw new Error('HTTP ' + res.status);
            return await res.json();
        } catch (e) {
            return null;
        }
    };

    const parseWalletBalances = (raw) => {
        // Expecting /api/get-wallet-balances shape
        // Return array of { assetId, symbol, name, balance, decimals, totalBalance }
        try {
            const map = raw && raw.payload && raw.payload.main && raw.payload.main.assetBalanceMap;
            if (!map || typeof map !== 'object') return [];
            const out = [];
            for (const key of Object.keys(map)) {
                const entry = map[key];
                const info = entry.asset_info || {};
                const decimals = Number(info.decimal_point || 0);
                const unlockedAtomic = Number(entry.unlocked || 0);
                const totalAtomic = Number(entry.total || 0);
                const balance = unlockedAtomic / Math.pow(10, decimals);
                const totalBalance = totalAtomic / Math.pow(10, decimals);
                const assetId = String(info.asset_id || key);
                const symbol = String(info.ticker || info.full_name || assetId);
                const name = String(info.full_name || info.ticker || assetId);
                out.push({ assetId, symbol, name, balance, decimals, totalBalance });
            }
            // Sort: ZANO first, then by symbol
            out.sort((a, b) => {
                if (a.symbol.toUpperCase() === 'ZANO') return -1;
                if (b.symbol.toUpperCase() === 'ZANO') return 1;
                return a.symbol.localeCompare(b.symbol);
            });
            return out;
        } catch {
            return [];
        }
    };

    const findZanoBalance = (assetsArr) => {
        const z = assetsArr.find(a =>
            String(a.symbol).toUpperCase() === 'ZANO' ||
            String(a.assetId).toUpperCase() === 'ZANO'
        );
        return z ? Number(z.balance || 0) : 0;
    };

    const computeMaxSend = () => {
        if (!selected) return 0;
        const bal = Number(selected.balance || 0);
        const isZano = (String(selected.symbol).toUpperCase() === 'ZANO') ||
            (String(selected.assetId).toUpperCase() === 'ZANO');
        if (zanoBal < FEE_ZANO) return 0;
        const raw = isZano ? Math.max(0, bal - FEE_ZANO) : bal;
        return roundDown(raw, selected.decimals || 0);
    };

    const amountValid = () => {
        const val = Number(els.amountInput.value);
        if (!selected) return false;
        if (!isFinite(val) || val <= 0) return false;
        return val <= computeMaxSend();
    };

    const updateAvailableUI = () => {
        const bal = selected ? Number(selected.balance || 0) : 0;
        const dec = selected ? (selected.decimals || 0) : 12;
        els.assetBalance.textContent = 'Balance: ' + fmt(bal, dec) + ' ' + (selected ? selected.symbol : '');
        const max = computeMaxSend();
        els.availableHelp.textContent = 'Available to send: ' + fmt(max, dec) + ' ' + (selected ? selected.symbol : '');
        els.zanoBalance.textContent = fmt(zanoBal, 12) + ' ZANO';
        const feeBlocked = zanoBal < FEE_ZANO;
        els.cardAsset.classList.toggle('is-disabled', feeBlocked);
        els.btnSend.disabled = feeBlocked || !addressOk || !selected || !amountValid();
        // Update step for current asset
        if (selected) {
            els.amountInput.step = stepFor(selected.decimals || 0);
        }
    };

    const renderAssetSelect = (preserveAssetId = null) => {
        const currentId = preserveAssetId || (selected && selected.assetId) || null;
        const optionsHtml = assets.map((a, i) => {
            const label = a.name + ' • ' + fmt(a.balance, a.decimals) + ' ' + a.symbol + (typeof a.totalBalance !== 'undefined' ? (' (' + fmt(a.totalBalance, a.decimals) + ' total)') : '');
            return `<option value="${i}">${label}</option>`;
        }).join('');
        els.assetSelect.innerHTML = optionsHtml;
        let newIndex = 0;
        if (currentId) {
            const idx = assets.findIndex(a => a.assetId === currentId);
            if (idx >= 0) newIndex = idx;
        }
        els.assetSelect.selectedIndex = newIndex;
        setSelectedByIndex(newIndex);
    };

    const refreshBalances = async () => {
        const raw = await tryGet('/api/get-wallet-balances');
        const newAssets = parseWalletBalances(raw);
        if (!newAssets.length) return;
        const preserveId = selected && selected.assetId;
        assets = newAssets;
        zanoBal = findZanoBalance(assets);
        renderAssetSelect(preserveId);
        els.assetsStatus.textContent = 'Updated ' + hhmmss();
        updateAvailableUI();
    };

    const startAutoRefresh = () => {
        if (autoTimer) clearInterval(autoTimer);
        autoTimer = setInterval(refreshBalances, AUTO_REFRESH_MS);
    };

    const setSelectedByIndex = (idx) => {
        selected = assets[idx] || null;
        updateAvailableUI();
    };

    // Load assets from /api/get-wallet-balances
    const loadAssets = async () => {
        els.assetsStatus.textContent = 'Loading assets…';
        const raw = await tryGet('/api/get-wallet-balances');
        assets = parseWalletBalances(raw);
        if (!assets.length) {
            assets = [{ assetId: 'ZANO', symbol: 'ZANO', name: 'Zano', balance: 0, decimals: 12 }];
            els.assetsStatus.innerHTML = 'No balances found; using placeholder. Wire /api/get-wallet-balances to enable live data.';
        } else {
            els.assetsStatus.textContent = 'Loaded ' + assets.length + ' asset' + (assets.length === 1 ? '' : 's') + ' from wallet';
        }
        zanoBal = findZanoBalance(assets);
        renderAssetSelect();
    };

    // Address validation
    let debounceTimer;
    const triggerAddressCheck = async () => {
        const addr = (els.addressInput.value || '').trim();
        if (!addr) {
            addressOk = false;
            lastQueryPayload = null;
            els.addressStatus.textContent = '—';
            els.rowBaseAddress.classList.add('d-none');
            els.rowPaymentId.classList.add('d-none');
            els.rowAlias.classList.add('d-none');
            updateAvailableUI();
            return;
        }
        els.addressStatus.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Validating…';
        try {
            const res = await postJson('/api/wallet-query-address', { address: addr });
            if (Number(res.status) === 200) {
                addressOk = true;
                lastQueryPayload = res.payload || {};
                const base = lastQueryPayload.baseAddress || '';
                const pid = (lastQueryPayload.paymentId === null || lastQueryPayload.paymentId === undefined) ? null : String(lastQueryPayload.paymentId);
                const alias = lastQueryPayload.alias || null;
                const aliasComment = lastQueryPayload.aliasComment || null;

                // Base address
                els.baseAddress.value = base;
                els.rowBaseAddress.classList.remove('d-none');

                // Payment ID behavior:
                // - If API returned paymentId => show read-only field with value.
                // - If API returned null/undefined => show EDITABLE field (remove readonly).
                els.rowPaymentId.classList.remove('d-none');
                if (pid) {
                    els.paymentId.value = pid;
                    els.paymentId.readOnly = true;
                    els.paymentId.classList.remove('is-editable');
                } else {
                    els.paymentId.value = '';
                    els.paymentId.readOnly = false;
                    els.paymentId.placeholder = 'Enter payment ID / account number (if required)';
                    els.paymentId.classList.add('is-editable');
                }

                // Alias block
                if (alias || aliasComment) {
                    const aliasStr = (alias ? alias : '') + (alias && aliasComment ? ' — ' : '') + (aliasComment ? aliasComment : '');
                    els.aliasText.textContent = aliasStr;
                    els.rowAlias.classList.remove('d-none');
                } else {
                    els.rowAlias.classList.add('d-none');
                }

                els.addressStatus.innerHTML = '<span class="text-success"><i class="bi bi-check2-circle me-1"></i>Address valid</span>';
            } else {
                addressOk = false;
                lastQueryPayload = null;
                els.addressStatus.innerHTML = '<span class="text-danger"><i class="bi bi-x-circle me-1"></i>' + (res.message || 'Invalid address') + '</span>';
                els.rowBaseAddress.classList.add('d-none');
                els.rowPaymentId.classList.add('d-none');
                els.rowAlias.classList.add('d-none');
            }
        } catch (e) {
            addressOk = false;
            lastQueryPayload = null;
            els.addressStatus.innerHTML = '<span class="text-danger"><i class="bi bi-x-circle me-1"></i>' + (e.message || 'Validation failed') + '</span>';
            els.rowBaseAddress.classList.add('d-none');
            els.rowPaymentId.classList.add('d-none');
            els.rowAlias.classList.add('d-none');
        } finally {
            updateAvailableUI();
        }
    };

    const debounceCheck = () => {
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(triggerAddressCheck, 450);
    };

    // Copy buttons
    document.addEventListener('click', (e) => {
        const btn = e.target.closest('[data-copy]');
        if (!btn) return;
        const sel = btn.getAttribute('data-copy');
        const input = document.querySelector(sel);
        if (input) {
            navigator.clipboard.writeText(input.value || input.textContent || '').then(() => {
                btn.innerHTML = '<i class="bi bi-clipboard-check"></i>';
                setTimeout(() => { btn.innerHTML = '<i class="bi bi-clipboard"></i>'; }, 1200);
            });
        }
    });

    // Events
    els.assetSelect.addEventListener('change', (e) => {
        setSelectedByIndex(Number(e.target.value));
        // snap amount to valid precision when switching assets
        if (els.amountInput.value) {
            const d = selected ? (selected.decimals || 0) : 0;
            const v = roundDown(Number(els.amountInput.value), d);
            els.amountInput.value = trimZeros(v.toFixed(d));
        }
    });
    els.amountInput.addEventListener('input', updateAvailableUI);
    els.btnMax.addEventListener('click', () => {
        const max = computeMaxSend();
        const d = selected ? (selected.decimals || 0) : 0;
        els.amountInput.value = trimZeros(max.toFixed(d));
        updateAvailableUI();
    });

    els.addressInput.addEventListener('input', debounceCheck);
    els.btnCheckAddress.addEventListener('click', triggerAddressCheck);

    els.btnReset.addEventListener('click', () => {
        els.assetSelect.selectedIndex = 0;
        setSelectedByIndex(0);
        els.amountInput.value = '';
        els.addressInput.value = '';
        els.commentInput.value = '';
        els.twoFaInput.value = '';
        els.addressStatus.textContent = '—';
        els.rowBaseAddress.classList.add('d-none');
        els.rowPaymentId.classList.add('d-none');
        els.rowAlias.classList.add('d-none');
        els.paymentId.readOnly = true;
        els.paymentId.value = '';
        els.paymentId.classList.remove('is-editable');
        addressOk = false;
        lastQueryPayload = null;
        els.sendAlert.classList.add('d-none');
        updateAvailableUI();
    });

    els.btnSend.addEventListener('click', async () => {
        const amount = Number(els.amountInput.value);
        const addr = (els.addressInput.value || '').trim();
        const comment = els.commentInput.value || '';
        const twoFa = els.twoFaInput.value || '';

        // Validation
        if (zanoBal < FEE_ZANO) {
            showAlert('error', '<i class="bi bi-exclamation-triangle me-1"></i>Insufficient ZANO for network fee (need at least 0.01 ZANO).');
            return;
        }
        if (!selected) { showAlert('error', 'Please select an asset.'); return; }
        if (!addressOk || !addr) { showAlert('error', 'Please enter a valid destination address and try again.'); return; }
        if (!isFinite(amount) || amount <= 0) { showAlert('error', 'Please enter a valid amount greater than zero.'); return; }

        const max = computeMaxSend();
        if (amount > max) {
            showAlert('error', 'Amount exceeds the available limit (' + fmt(max, selected.decimals) + ' ' + selected.symbol + ').');
            return;
        }

        // Build paymentId rule:
        // - If address query returned a paymentId (integrated address), send paymentId: null
        // - Else (no paymentId from query), send the user-entered paymentId (possibly empty -> null)
        let paymentIdToSend = null;
        const apiPid = lastQueryPayload && lastQueryPayload.paymentId;
        if (apiPid) {
            paymentIdToSend = null;
        } else {
            const typed = (els.paymentId.value || '').trim();
            paymentIdToSend = typed ? typed : null;
        }

        // Send
        setSending(true);
        try {
            const d = selected.decimals || 0;
            const amountStr = trimZeros(roundDown(amount, d).toFixed(d));
            const payload = {
                address: addr,
                comment: comment,
                assetId: selected.assetId,
                amount: amountStr,
                paymentId: paymentIdToSend,
                twoFa: twoFa
            };
            const res = await postJson('/api/wallet-send', payload);
            const msg = (res && (res.message || res.statusText)) || 'Transfer sent';
            showAlert('success', '<i class="bi bi-check2-circle me-1"></i>' + msg);

            // Clear amount/comment (keep address)
            els.amountInput.value = '';
            els.commentInput.value = '';

            // Immediately refresh balances
            await refreshBalances();
        } catch (e) {
            showAlert('error', '<i class="bi bi-x-circle me-1"></i>' + (e.message || 'Send failed'));
        } finally {
            setSending(false);
        }
    });

    // init
    (async () => {
        await loadAssets();
        updateAvailableUI();
        startAutoRefresh();
    })();
})();