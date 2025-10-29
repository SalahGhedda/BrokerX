const STORAGE_KEYS = {
    session: 'brokerx.session',
    signupDraft: 'brokerx.signupDraft'
};

const currencyFormatter = new Intl.NumberFormat('fr-CA', { style: 'currency', currency: 'CAD' });
const toast = document.getElementById('toast');

const state = {
    accountId: null,
    email: null,
    followed: new Set(),
    currentStock: null
};

const storedSession = loadSession();
if (storedSession) {
    state.accountId = storedSession.accountId || null;
    state.email = storedSession.email || null;
}

const page = document.body ? document.body.dataset.page : null;
const initializers = {
    auth: initAuthPage,
    confirm: initConfirmPage,
    dashboard: initDashboardPage,
    stocks: initStocksPage,
    'stock-detail': initStockDetailPage
};

if (page && initializers[page]) {
    initializers[page]();
}

function loadSession() {
    try {
        const raw = localStorage.getItem(STORAGE_KEYS.session);
        return raw ? JSON.parse(raw) : null;
    } catch (_) {
        return null;
    }
}

function saveSession(session) {
    try {
        localStorage.setItem(STORAGE_KEYS.session, JSON.stringify(session));
    } catch (_) {
        /* ignore storage errors */
    }
}

function clearSession() {
    try {
        localStorage.removeItem(STORAGE_KEYS.session);
    } catch (_) {
        /* ignore storage errors */
    }
    state.accountId = null;
    state.email = null;
    state.followed = new Set();
    state.currentStock = null;
}

function loadSignupDraft() {
    try {
        const raw = localStorage.getItem(STORAGE_KEYS.signupDraft);
        return raw ? JSON.parse(raw) : null;
    } catch (_) {
        return null;
    }
}

function saveSignupDraft(draft) {
    try {
        localStorage.setItem(STORAGE_KEYS.signupDraft, JSON.stringify(draft));
    } catch (_) {
        /* ignore storage errors */
    }
}

function clearSignupDraft() {
    try {
        localStorage.removeItem(STORAGE_KEYS.signupDraft);
    } catch (_) {
        /* ignore storage errors */
    }
}

function showToast(message, type = 'info', duration = 3500) {
    if (!toast) {
        return;
    }
    toast.textContent = message;
    toast.className = 'toast visible' + (type === 'error' ? ' error' : type === 'success' ? ' success' : '');
    setTimeout(() => toast.classList.remove('visible'), duration);
}

async function callApi(url, options = {}) {
    const response = await fetch(url, {
        ...options,
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            ...(options.headers || {})
        }
    });
    const text = await response.text();
    let payload = {};
    try {
        payload = text ? JSON.parse(text) : {};
    } catch (_) {
        payload = { message: text };
    }
    if (!response.ok) {
        throw new Error(payload.message || response.statusText);
    }
    return payload;
}

async function fetchJson(url) {
    const response = await fetch(url);
    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || response.statusText);
    }
    return response.json();
}

function escapeHtml(value) {
    if (!value) {
        return '';
    }
    return value.replace(/[&<>"']/g, (char) => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    }[char]));
}

function redirect(url) {
    window.location.href = url;
}

function updateFollowButton(button, isFollowed) {
    button.className = isFollowed ? 'secondary is-following' : 'primary';
    button.textContent = isFollowed ? 'Suivi' : 'Suivre';
}

async function refreshFollowedSet(accountId) {
    try {
        const quotes = await fetchJson('/api/stocks/followed?accountId=' + encodeURIComponent(accountId));
        state.followed = new Set(quotes.map(q => q.id));
        return quotes;
    } catch (error) {
        showToast(error.message, 'error');
        return [];
    }
}

async function toggleFollowStock(accountId, stockId, shouldFollow) {
    const params = new URLSearchParams();
    params.set('accountId', accountId);
    params.set('stockId', stockId);
    await callApi(shouldFollow ? '/api/stocks/follow' : '/api/stocks/unfollow', { method: 'POST', body: params });
    if (shouldFollow) {
        state.followed.add(stockId);
    } else {
        state.followed.delete(stockId);
    }
    return shouldFollow;
}

function populateSession(result) {
    state.accountId = result.accountId;
    state.email = result.email;
    saveSession({ accountId: state.accountId, email: state.email });
}

function requireSession() {
    if (!state.accountId) {
        redirect('index.html');
        return null;
    }
    return { accountId: state.accountId, email: state.email };
}

function handleLogout() {
    clearSession();
    showToast('Vous etes deconnecte.', 'info');
    redirect('index.html');
}

function initAuthPage() {
    const tabButtons = Array.from(document.querySelectorAll('.tab-button'));
    const authPanels = Array.from(document.querySelectorAll('.auth-panel'));
    const loginForm = document.getElementById('login-form');
    const signupForm = document.getElementById('signup-form');
    const switchTabLinks = Array.from(document.querySelectorAll('.switch-tab'));

    function switchAuthPanel(targetId) {
        tabButtons.forEach(btn => btn.classList.toggle('active', btn.dataset.target === targetId));
        authPanels.forEach(panel => panel.classList.toggle('hidden', panel.id !== targetId));
    }

    tabButtons.forEach(button => {
        button.addEventListener('click', () => switchAuthPanel(button.dataset.target));
    });

    switchTabLinks.forEach(link => {
        link.addEventListener('click', (event) => {
            event.preventDefault();
            switchAuthPanel(link.dataset.target);
        });
    });

    const draft = loadSignupDraft();
    if (draft && draft.email) {
        const loginEmail = document.getElementById('login-email');
        if (loginEmail) {
            loginEmail.value = draft.email;
        }
    }

    if (loginForm) {
        loginForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            const params = new URLSearchParams(new FormData(loginForm));
            try {
                const result = await callApi('/api/auth/login', { method: 'POST', body: params });
                populateSession(result);
                showToast('Connexion reussie. Bienvenue !', 'success');
                loginForm.reset();
                redirect('dashboard.html');
            } catch (error) {
                showToast(error.message, 'error');
            }
        });
    }

    if (signupForm) {
        signupForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            const params = new URLSearchParams(new FormData(signupForm));
            try {
                const result = await callApi('/api/auth/signup', { method: 'POST', body: params });
                saveSignupDraft(result);
                showToast('Compte cree. Consultez votre courriel pour le code de verification.', 'success', 6000);
                signupForm.reset();
                const nextParams = new URLSearchParams();
                if (result.accountId) {
                    nextParams.set('accountId', result.accountId);
                }
                if (result.verificationCode) {
                    nextParams.set('code', result.verificationCode);
                }
                if (result.email) {
                    nextParams.set('email', result.email);
                }
                const queryString = nextParams.toString();
                redirect('confirm.html' + (queryString ? '?' + queryString : ''));
            } catch (error) {
                showToast(error.message, 'error');
            }
        });
    }

    switchAuthPanel('login-panel');
}

function initConfirmPage() {
    const confirmForm = document.getElementById('confirm-form');
    const accountInput = document.getElementById('confirm-accountId');
    const codeInput = document.getElementById('confirm-code');
    const urlParams = new URLSearchParams(window.location.search);
    const draft = loadSignupDraft() || {};
    const initialAccountId = urlParams.get('accountId') || draft.accountId || '';
    const initialCode = urlParams.get('code') || draft.verificationCode || '';
    const initialEmail = urlParams.get('email') || draft.email || '';

    if (accountInput && initialAccountId) {
        accountInput.value = initialAccountId;
    }
    if (codeInput && initialCode) {
        codeInput.value = initialCode;
    }

    if (initialAccountId || initialCode || initialEmail) {
        saveSignupDraft({
            email: initialEmail || draft.email || '',
            accountId: initialAccountId || draft.accountId || '',
            verificationCode: initialCode || draft.verificationCode || ''
        });
    }

    if (window.location.search && window.history && window.history.replaceState) {
        window.history.replaceState(null, '', 'confirm.html');
    }

    if (confirmForm) {
        confirmForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            const accountId = accountInput ? accountInput.value.trim() : '';
            const code = codeInput ? codeInput.value.trim() : '';
            if (!accountId || !code) {
                showToast('Identifiant et code requis.', 'error');
                return;
            }
            const params = new URLSearchParams();
            params.set('accountId', accountId);
            params.set('verificationCode', code);
            try {
                await callApi('/api/accounts/confirm', { method: 'POST', body: params });
                showToast('Compte active. Vous pouvez vous connecter.', 'success');
                const latestDraft = loadSignupDraft() || {};
                const emailForLogin = latestDraft.email || initialEmail || '';
                clearSignupDraft();
                if (emailForLogin) {
                    saveSignupDraft({ email: emailForLogin });
                }
                setTimeout(() => redirect('index.html'), 600);
            } catch (error) {
                showToast(error.message, 'error');
            }
        });
    }
}

async function initDashboardPage() {
    const session = requireSession();
    if (!session) {
        return;
    }

    const accountEmail = document.getElementById('account-email');
    const accountIdLabel = document.getElementById('account-id');
    const accountBalance = document.getElementById('account-balance');
    const followedList = document.getElementById('followed-stocks');
    const activityLog = document.getElementById('activity-log');
    const depositForm = document.getElementById('deposit-form');
    const openStocksBtn = document.getElementById('open-stocks');
    const logoutBtn = document.getElementById('logout-btn');

    if (logoutBtn) {
        logoutBtn.addEventListener('click', handleLogout);
    }
    if (openStocksBtn) {
        openStocksBtn.addEventListener('click', () => redirect('stocks.html'));
    }

    await loadSummary();
    await loadOrders();

    if (depositForm) {
        depositForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            const amount = depositForm.elements['amount'] ? depositForm.elements['amount'].value : '';
            if (!amount) {
                showToast('Montant requis.', 'error');
                return;
            }
            const params = new URLSearchParams();
            params.set('accountId', session.accountId);
            params.set('amount', amount);
            params.set('idempotencyKey', (window.crypto && window.crypto.randomUUID) ? window.crypto.randomUUID() : 'UI-' + Date.now());
            try {
                await callApi('/api/wallets/deposit', { method: 'POST', body: params });
                depositForm.reset();
                showToast('Depot enregistre avec succes.', 'success');
                await loadSummary();
                await loadOrders();
            } catch (error) {
                showToast(error.message, 'error');
            }
        });
    }

    async function loadSummary() {
        try {
            const summary = await fetchJson('/api/accounts/summary?accountId=' + encodeURIComponent(session.accountId));
            state.email = summary.email;
            if (accountEmail) {
                accountEmail.textContent = summary.email + ' - compte ' + summary.state;
            }
            if (accountIdLabel) {
                accountIdLabel.textContent = summary.accountId;
            }
            if (accountBalance) {
                accountBalance.textContent = currencyFormatter.format(Number(summary.balance));
            }
            renderFollowedStocks(summary.followedStocks || []);
        } catch (error) {
            showToast(error.message, 'error');
        }
    }

    async function loadOrders() {
        if (!activityLog) {
            return;
        }
        try {
            const orders = await fetchJson('/api/orders?accountId=' + encodeURIComponent(session.accountId));
            renderOrdersList(orders);
        } catch (error) {
            showToast(error.message, 'error');
        }
    }

    async function cancelOrderRequest(orderId) {
        if (!orderId) {
            return false;
        }
        const params = new URLSearchParams();
        params.set('accountId', session.accountId);
        params.set('orderId', orderId);
        try {
            await callApi('/api/orders/cancel', { method: 'POST', body: params });
            showToast('Ordre annule.', 'info');
            await loadOrders();
            await loadSummary();
            return true;
        } catch (error) {
            showToast(error.message, 'error');
            return false;
        }
    }

    function renderFollowedStocks(quotes) {
        state.followed = new Set(quotes.map(q => q.id));
        if (!followedList) {
            return;
        }
        if (!quotes.length) {
            followedList.innerHTML = '<li><span>Aucun titre suivi pour le moment.</span><span class="badge info">Info</span></li>';
            return;
        }
        followedList.innerHTML = '';
        quotes.forEach(quote => {
            const li = document.createElement('li');
            const updatedAt = quote.updatedAt ? new Date(quote.updatedAt).toLocaleTimeString('fr-FR') : '';
            li.innerHTML = '<span>' + quote.symbol + ' - ' + escapeHtml(quote.name) +
                (updatedAt ? '<br><small>' + updatedAt + '</small>' : '') +
                '</span><span class="badge success">' + currencyFormatter.format(Number(quote.price)) + '</span>';
            li.addEventListener('click', () => {
                redirect('stock.html?stockId=' + encodeURIComponent(quote.id));
            });
            followedList.appendChild(li);
        });
    }

    function renderOrdersList(orders) {
        if (!activityLog) {
            return;
        }
        if (!orders.length) {
            activityLog.innerHTML = '<li><span>Aucune operation pour le moment.</span><span class="badge info">Live</span></li>';
            return;
        }
        activityLog.innerHTML = '';
        orders.forEach(order => {
            const li = document.createElement('li');
            const createdAt = order.createdAt ? new Date(order.createdAt).toLocaleString('fr-CA', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '';
            const executedAt = order.executedAt ? new Date(order.executedAt).toLocaleString('fr-CA', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : null;
            const statusClass = order.status === 'COMPLETED'
                ? 'success'
                : order.status === 'FAILED'
                    ? 'danger'
                    : order.status === 'CANCELLED'
                        ? 'warning'
                        : 'info';
            const quantity = Number(order.quantity);
            const executedPrice = order.executedPrice != null ? currencyFormatter.format(Number(order.executedPrice)) : '--';
            const notional = order.notional != null ? currencyFormatter.format(Number(order.notional)) : '--';
            const limitPrice = order.limitPrice != null ? currencyFormatter.format(Number(order.limitPrice)) : '--';
            const typeLabel = order.type === 'LIMIT' ? 'Limite' : 'Marché';
            const statusText = order.status === 'COMPLETED' ? 'Termine'
                : order.status === 'FAILED' ? 'Echec'
                : order.status === 'CANCELLED' ? 'Annule'
                : 'En attente';
            const metaParts = [];
            if (createdAt) {
                metaParts.push('Cree le ' + createdAt);
            }
            if (order.status === 'PENDING') {
                metaParts.push('Limite ' + limitPrice);
            } else if (order.status === 'COMPLETED') {
                if (executedAt) {
                    metaParts.push('Execute le ' + executedAt);
                }
                metaParts.push(quantity + ' @ ' + executedPrice + (notional !== '--' ? ' (' + notional + ')' : ''));
            } else if (order.status === 'FAILED') {
                if (executedAt) {
                    metaParts.push('Echec le ' + executedAt);
                }
                if (order.failureReason) {
                    metaParts.push(escapeHtml(order.failureReason));
                }
            } else if (order.status === 'CANCELLED') {
                if (executedAt) {
                    metaParts.push('Annule le ' + executedAt);
                }
                if (order.failureReason) {
                    metaParts.push(escapeHtml(order.failureReason));
                }
            }
            const meta = metaParts.join(' - ');
            const info = document.createElement('span');
            let infoHtml = escapeHtml(order.symbol) + ' - ' + typeLabel;
            if (meta) {
                infoHtml += '<br><small>' + meta + '</small>';
            }
            info.innerHTML = infoHtml;

            const actions = document.createElement('span');
            actions.className = 'activity-actions';
            const badge = document.createElement('span');
            badge.className = 'badge ' + statusClass;
            badge.textContent = statusText;
            actions.appendChild(badge);

            if (order.status === 'PENDING') {
                const cancelBtn = document.createElement('button');
                cancelBtn.type = 'button';
                cancelBtn.className = 'secondary danger';
                cancelBtn.textContent = 'Annuler';
                cancelBtn.addEventListener('click', async (event) => {
                    event.preventDefault();
                    event.stopPropagation();
                    cancelBtn.disabled = true;
                    const success = await cancelOrderRequest(order.orderId);
                    if (!success) {
                        cancelBtn.disabled = false;
                    }
                });
                actions.appendChild(cancelBtn);
            }

            li.appendChild(info);
            li.appendChild(actions);
            activityLog.appendChild(li);
        });
    }
}

async function initStocksPage() {
    const session = requireSession();
    if (!session) {
        return;
    }

    const stocksListEl = document.getElementById('stocks-list');
    const backDashboardBtn = document.getElementById('back-dashboard');
    const logoutBtn = document.getElementById('logout-btn');

    const cleanup = () => {
        if (stocksRefreshTimer) {
            clearInterval(stocksRefreshTimer);
        }
    };

    if (logoutBtn) {
        logoutBtn.addEventListener('click', () => {
            cleanup();
            handleLogout();
        });
    }
    if (backDashboardBtn) {
        backDashboardBtn.addEventListener('click', () => {
            cleanup();
            redirect('dashboard.html');
        });
    }

    let stocksCache = [];
    let isRefreshing = false;
    const refreshIntervalMs = 5000;
    let stocksRefreshTimer;

    await refreshFollowedSet(session.accountId);
    await loadStocks();

    stocksRefreshTimer = window.setInterval(() => {
        loadStocks().catch(() => { /* handled in loadStocks */ });
    }, refreshIntervalMs);

    window.addEventListener('beforeunload', cleanup);

    async function loadStocks() {
        if (isRefreshing) {
            return;
        }
        isRefreshing = true;
        try {
            stocksCache = await fetchJson('/api/stocks');
            renderStocksList(stocksCache);
        } catch (error) {
            showToast(error.message, 'error');
        } finally {
            isRefreshing = false;
        }
    }

    function renderStocksList(stocks) {
        if (!stocksListEl) {
            return;
        }
        stocksListEl.innerHTML = '';
        stocks.forEach((quote) => {
            const row = document.createElement('div');
            row.className = 'stock-row';

            const info = document.createElement('div');
            info.className = 'stock-row-info';
            info.innerHTML = '<span class="stock-row-symbol">' + quote.symbol + '</span>' +
                '<span class="stock-row-name">' + escapeHtml(quote.name) + '</span>';

            const actions = document.createElement('div');
            actions.className = 'stock-row-actions';

            const price = document.createElement('span');
            price.className = 'stock-row-price';
            price.textContent = currencyFormatter.format(Number(quote.price));

            const followBtn = document.createElement('button');
            followBtn.type = 'button';
            updateFollowButton(followBtn, state.followed.has(quote.id));
            followBtn.addEventListener('click', async (event) => {
                event.stopPropagation();
                await handleFollowToggle(quote);
            });

            actions.appendChild(price);
            actions.appendChild(followBtn);

            row.appendChild(info);
            row.appendChild(actions);
            row.addEventListener('click', () => {
                redirect('stock.html?stockId=' + encodeURIComponent(quote.id));
            });

            stocksListEl.appendChild(row);
        });
    }

    async function handleFollowToggle(quote) {
        const shouldFollow = !state.followed.has(quote.id);
        try {
            await toggleFollowStock(session.accountId, quote.id, shouldFollow);
            await refreshFollowedSet(session.accountId);
            showToast(shouldFollow ? 'Titre ajoute aux suivis.' : 'Titre retire des suivis.', 'success');
            renderStocksList(stocksCache);
        } catch (error) {
            showToast(error.message, 'error');
        }
    }
}

async function initStockDetailPage() {
    const session = requireSession();
    if (!session) {
        return;
    }

    const urlParams = new URLSearchParams(window.location.search);
    const stockId = urlParams.get('stockId');
    if (!stockId) {
        redirect('stocks.html');
        return;
    }

    const symbolEl = document.getElementById('stock-detail-symbol');
    const nameEl = document.getElementById('stock-detail-name');
    const descriptionEl = document.getElementById('stock-detail-description');
    const priceEl = document.getElementById('stock-detail-price');
    const followBtn = document.getElementById('stock-detail-follow');
    const orderForm = document.getElementById('stock-order-form');
    const orderSymbolInput = document.getElementById('stock-order-symbol');
    const orderQuantityInput = document.getElementById('stock-order-quantity');
    const orderTypeSelect = document.getElementById('stock-order-type');
    const priceWrapper = document.getElementById('stock-order-price-wrapper');
    const priceInput = document.getElementById('stock-order-price');
    const backStocksBtn = document.getElementById('back-stocks');
    const backDashboardBtn = document.getElementById('back-dashboard');
    const logoutBtn = document.getElementById('logout-btn');
    const updatedEl = document.getElementById('stock-detail-updated');
    const clockEl = document.getElementById('stock-clock');

    let detailRefreshTimer;
    let clockTimer;
    let isDetailRefreshing = false;
    const detailRefreshIntervalMs = 5000;

    const cleanup = () => {
        if (detailRefreshTimer) {
            clearInterval(detailRefreshTimer);
        }
        if (clockTimer) {
            clearInterval(clockTimer);
        }
    };

    if (logoutBtn) {
        logoutBtn.addEventListener('click', () => {
            cleanup();
            handleLogout();
        });
    }
    if (backDashboardBtn) {
        backDashboardBtn.addEventListener('click', () => {
            cleanup();
            redirect('dashboard.html');
        });
    }
    if (backStocksBtn) {
        backStocksBtn.addEventListener('click', () => {
            cleanup();
            redirect('stocks.html');
        });
    }

    if (orderTypeSelect && priceWrapper) {
        orderTypeSelect.addEventListener('change', () => {
            const showPrice = orderTypeSelect.value === 'LIMIT';
            priceWrapper.classList.toggle('hidden', !showPrice);
        });
    }

    const updateClock = () => {
        if (!clockEl) {
            return;
        }
        const now = new Date();
        clockEl.textContent = now.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    };

    await refreshFollowedSet(session.accountId);
    await loadDetail();

    detailRefreshTimer = window.setInterval(() => {
        loadDetail().catch(() => { /* error handled */ });
    }, detailRefreshIntervalMs);

    updateClock();
    clockTimer = window.setInterval(updateClock, 1000);

    window.addEventListener('beforeunload', cleanup);

    async function loadDetail() {
        if (isDetailRefreshing) {
            return;
        }
        isDetailRefreshing = true;
        try {
            const quote = await fetchJson('/api/stocks/details?stockId=' + encodeURIComponent(stockId));
            const resolvedId = quote.id || stockId;
            const timestamp = quote.updatedAt ? new Date(quote.updatedAt) : new Date();
            state.currentStock = { ...quote, id: resolvedId };
            if (symbolEl) {
                symbolEl.textContent = quote.symbol;
            }
            if (nameEl) {
                nameEl.textContent = quote.name;
            }
            if (descriptionEl) {
                descriptionEl.textContent = quote.description || '';
            }
            if (priceEl) {
                priceEl.textContent = currencyFormatter.format(Number(quote.price));
            }
            if (orderSymbolInput) {
                orderSymbolInput.value = quote.symbol;
            }
            const isFollowed = state.followed.has(resolvedId);
            state.currentStock.followed = isFollowed;
            if (followBtn) {
                updateFollowButton(followBtn, isFollowed);
            }
            if (updatedEl) {
                updatedEl.textContent = 'MAJ ' + timestamp.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
            }
        } catch (error) {
            showToast(error.message, 'error');
            cleanup();
            redirect('stocks.html');
        } finally {
            isDetailRefreshing = false;
        }
    }

    if (followBtn) {
        followBtn.addEventListener('click', async () => {
            if (!state.currentStock || !state.currentStock.id) {
                return;
            }
            const shouldFollow = !state.followed.has(state.currentStock.id);
            try {
                await toggleFollowStock(session.accountId, state.currentStock.id, shouldFollow);
                await refreshFollowedSet(session.accountId);
                state.currentStock.followed = shouldFollow;
                updateFollowButton(followBtn, shouldFollow);
                showToast(shouldFollow ? 'Titre ajoute aux suivis.' : 'Titre retire des suivis.', 'success');
            } catch (error) {
                showToast(error.message, 'error');
            }
        });
    }

    if (orderForm) {
        orderForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (!state.currentStock) {
                showToast('Selectionnez un titre avant de passer un ordre.', 'error');
                return;
            }
            const formData = new URLSearchParams();
            formData.set('accountId', session.accountId);
            formData.set('symbol', orderSymbolInput ? orderSymbolInput.value : state.currentStock.symbol);
            formData.set('side', 'BUY');
            formData.set('type', orderTypeSelect ? orderTypeSelect.value : 'MARKET');
            formData.set('quantity', orderQuantityInput ? orderQuantityInput.value : '1');
            if (orderTypeSelect && orderTypeSelect.value === 'LIMIT') {
                formData.set('price', priceInput ? priceInput.value || '' : '');
            }
            formData.set('clientOrderId', (window.crypto && window.crypto.randomUUID) ? window.crypto.randomUUID() : 'ORD-' + Date.now());
            try {
                await callApi('/api/orders', { method: 'POST', body: formData });
                showToast('Ordre transmis au moteur.', 'success');
                orderForm.reset();
                if (priceWrapper) {
                    priceWrapper.classList.add('hidden');
                }
            } catch (error) {
                showToast(error.message, 'error');
            }
        });
    }
}
