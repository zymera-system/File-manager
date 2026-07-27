// ========================================
// LOADING OVERLAY — Sistema Padrão
// ========================================

let isOpen = false;
let isPaused = false;
let cancelCallback = null;
let resumeCallback = null;
let currentConfig = {};

// ========================================
// ÍCONES SVG POR TIPO DE OPERAÇÃO
// ========================================

const iconSVGs = {
    copy: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>`,
    move: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M5 12h14"/><path d="M12 5l7 7-7 7"/></svg>`,
    delete: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>`,
    compress: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M21 8v13H3V8"/><path d="M1 3h22v5H1z"/><path d="M10 12h4"/><path d="M12 10v4"/></svg>`,
    extract: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M21 8v13H3V8"/><path d="M1 3h22v5H1z"/><path d="M12 12v5"/><path d="M9 15l3 3 3-3"/></svg>`,
    calculate: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>`,
    backup: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>`,
    restore: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>`,
    uninstall: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>`,
    share: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></svg>`
};

// ========================================
// FUNÇÕES AUXILIARES
// ========================================

function getIconSVG(type) {
    return iconSVGs[type] || null;
}

function renderInfo(config) {
    const parts = [];
    if (config.currentFile) parts.push(`Arquivo: ${config.currentFile}`);
    if (config.origin) parts.push(`Origem: ${config.origin}`);
    if (config.destination) parts.push(`Destino: ${config.destination}`);
    if (config.size) parts.push(`Tamanho: ${config.size}`);
    return parts.join('\n');
}

// ========================================
// SHOW / UPDATE / HIDE
// ========================================

export function showLoading(config = {}) {
    if (isOpen) return;
    currentConfig = { ...config };
    isOpen = true;
    isPaused = false;

    const overlay = document.getElementById('loadingOverlay');
    const dots = document.getElementById('loadingDots');
    const icon = document.getElementById('loadingIcon');
    const title = document.getElementById('loadingTitle');
    const info = document.getElementById('loadingInfo');
    const fill = document.getElementById('loadingFill');
    const percentage = document.getElementById('loadingPercentage');
    const count = document.getElementById('loadingCount');
    const cancelBtn = document.getElementById('loadingCancelBtn');

    if (!overlay) return;

    // Ícone ou dots
    const svg = getIconSVG(config.icon);
    if (svg) {
        dots.style.display = 'none';
        icon.style.display = 'block';
        icon.innerHTML = svg;
    } else {
        dots.style.display = 'flex';
        icon.style.display = 'none';
    }

    // Texto
    title.textContent = config.title || 'Processando...';
    info.textContent = config.info || renderInfo(config);

    // Progresso
    const pct = config.percentage || 0;
    fill.style.width = pct + '%';
    percentage.textContent = pct + '%';

    // Contagem
    if (config.current !== undefined && config.total !== undefined) {
        count.textContent = `${config.current} de ${config.total} itens`;
    } else {
        count.textContent = '';
    }

    // Botão cancelar
    if (config.cancellable === false) {
        cancelBtn.style.display = 'none';
    } else {
        cancelBtn.style.display = 'block';
    }

    // Callbacks
    cancelCallback = config.onCancel || null;
    resumeCallback = config.onResume || null;

    // Abrir
    overlay.classList.add('open');

    // Evento do botão cancelar
    cancelBtn.onclick = () => {
        if (cancelCallback) cancelCallback();
    };
}

export function updateLoading(config = {}) {
    if (!isOpen) return;

    Object.assign(currentConfig, config);

    const title = document.getElementById('loadingTitle');
    const info = document.getElementById('loadingInfo');
    const fill = document.getElementById('loadingFill');
    const percentage = document.getElementById('loadingPercentage');
    const count = document.getElementById('loadingCount');

    if (config.title && title) title.textContent = config.title;

    if (info) {
        const infoText = config.info || renderInfo(currentConfig);
        info.textContent = infoText;
    }

    if (config.percentage !== undefined && fill) {
        fill.style.width = config.percentage + '%';
    }
    if (config.percentage !== undefined && percentage) {
        percentage.textContent = config.percentage + '%';
    }

    if (count) {
        if (config.current !== undefined && config.total !== undefined) {
            count.textContent = `${config.current} de ${config.total} itens`;
        }
    }
}

export function updateProgress(percentage, current, total) {
    if (!isOpen) return;

    const fill = document.getElementById('loadingFill');
    const pctEl = document.getElementById('loadingPercentage');
    const count = document.getElementById('loadingCount');

    if (fill) fill.style.width = percentage + '%';
    if (pctEl) pctEl.textContent = percentage + '%';
    if (count && current !== undefined && total !== undefined) {
        count.textContent = `${current} de ${total} itens`;
    }

    currentConfig.percentage = percentage;
    currentConfig.current = current;
    currentConfig.total = total;
}

export function updateFile(name, origin, destination, size) {
    if (!isOpen) return;

    if (name !== undefined) currentConfig.currentFile = name;
    if (origin !== undefined) currentConfig.origin = origin;
    if (destination !== undefined) currentConfig.destination = destination;
    if (size !== undefined) currentConfig.size = size;

    const info = document.getElementById('loadingInfo');
    if (info) {
        info.textContent = renderInfo(currentConfig);
    }
}

export function hideLoading() {
    if (!isOpen) return;
    const overlay = document.getElementById('loadingOverlay');
    if (overlay) overlay.classList.remove('open');

    isOpen = false;
    isPaused = false;
    cancelCallback = null;
    resumeCallback = null;
    currentConfig = {};
}

export function isLoading() {
    return isOpen;
}

// ========================================
// PAUSE / RESUME (Cancelamento)
// ========================================

export function pauseLoading() {
    if (!isOpen) return;

    const modal = document.getElementById('cancelConfirmModal');
    if (modal) modal.classList.add('open');
}

export function resumeLoading() {
    const modal = document.getElementById('cancelConfirmModal');
    if (modal) modal.classList.remove('open');

    if (resumeCallback) resumeCallback();
}

// ========================================
// REVERSAL (Resultado da reversão)
// ========================================

export function showReversalResult(processedCount, type) {
    const modal = document.getElementById('reversalResultModal');
    const title = document.getElementById('reversalTitle');
    const info = document.getElementById('reversalInfo');

    if (!modal) return;

    const typeLabels = {
        copy: 'copiados',
        move: 'movidos',
        delete: 'excluídos'
    };
    const typeTitles = {
        copy: 'Arquivos copiados',
        move: 'Arquivos movidos',
        delete: 'Arquivos excluídos'
    };
    const label = typeLabels[type] || 'processados';

    if (title) title.textContent = typeTitles[type] || 'Arquivos transferidos';
    if (info) info.textContent = `${processedCount} arquivo(s) já ${label}.`;

    // Reset radio para "voltar"
    const radios = modal.querySelectorAll('input[name="reversal"]');
    radios.forEach(r => { r.checked = r.value === 'revert'; });

    modal.classList.add('open');
}

export function hideReversalResult() {
    const modal = document.getElementById('reversalResultModal');
    if (modal) modal.classList.remove('open');
}

export function getReversalChoice() {
    const checked = document.querySelector('input[name="reversal"]:checked');
    return checked ? checked.value : 'revert';
}

// ========================================
// POPUP — Scan / Confirmação / "Tem certeza?"
// ========================================

export function showPopup(html) {
    const overlay = document.getElementById('popupOverlay');
    const card = document.getElementById('popupCard');
    if (!overlay) return;
    overlay.classList.remove('hiding');
    card.innerHTML = html;
    overlay.classList.add('open');
    card.classList.remove('phase-change');
    requestAnimationFrame(() => {
        card.classList.add('phase-change');
    });
}

export function hidePopup(callback) {
    const overlay = document.getElementById('popupOverlay');
    if (!overlay || !overlay.classList.contains('open')) {
        if (callback) callback();
        return;
    }
    overlay.classList.add('hiding');
    setTimeout(() => {
        overlay.classList.remove('open', 'hiding');
        if (callback) callback();
    }, 300);
}

// ========================================
// SCAN (Fase 1)
// ========================================

export function showScan(config = {}) {
    const pulse = `
        <div class="scan-pulse">
            <div class="ring"></div>
            <div class="ring"></div>
            <div class="ring"></div>
            <div class="center"></div>
        </div>`;

    const html = `
        <div class="scan-card">
            ${pulse}
            <div class="scan-title">Analisando...</div>
            <div class="scan-path" id="scanPathDisplay">${config.firstPath || '...'}</div>
            <div class="popup-actions">
                <button class="popup-btn ocultar" id="scanOcultarBtn">Ocultar</button>
                <button class="popup-btn cancelar" id="scanCancelBtn">Cancelar</button>
            </div>
        </div>`;

    showPopup(html);
}

export function updateScan(path) {
    const el = document.getElementById('scanPathDisplay');
    if (el) el.textContent = path;
}

export function hideScan(callback) {
    hidePopup(callback);
}

// ========================================
// CONFIRMAÇÃO (Fase 2) — retorna Promise
// ========================================

export function showConfirmation(config = {}) {
    return new Promise((resolve) => {
        let html = `
            <div class="confirm-card">
                <div class="confirm-summary">
                    <div class="confirm-summary-item">📁 Pastas: <strong>${config.folderCount || 0}</strong></div>
                    <div class="confirm-summary-item">📄 Arquivos: <strong>${config.fileCount || 0}</strong></div>
                    <div class="confirm-total">Total: ${config.totalSize || '0 MB'}</div>
                </div>`;

        if (config.showPermanent) {
            html += `
                <label class="confirm-checkbox">
                    <input type="checkbox" id="chkPermanent">
                    Excluir permanentemente
                </label>`;
        }

        html += `
                <div class="popup-actions">
                    <button class="popup-btn cancelar" id="confirmCancelBtn">Cancelar</button>
                    <button class="popup-btn confirmar" id="confirmOkBtn">Confirmar</button>
                </div>
            </div>`;

        showPopup(html);

        document.getElementById('confirmCancelBtn').onclick = () => {
            hidePopup(() => resolve({ confirmed: false }));
        };
        document.getElementById('confirmOkBtn').onclick = () => {
            const permanent = document.getElementById('chkPermanent')?.checked || false;
            hidePopup(() => resolve({ confirmed: true, permanent }));
        };
    });
}

export function hideConfirmation(callback) {
    hidePopup(callback);
}

// ========================================
// "TEM CERTEZA?" — retorna Promise
// ========================================

const SKIP_CONFIRM_KEY = 'fm_skip_confirm_delete';

export function showSurePopup() {
    return new Promise((resolve) => {
        const html = `
            <div class="sure-card">
                <div class="sure-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                        <polyline points="3 6 5 6 21 6"/>
                        <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                    </svg>
                </div>
                <div class="sure-title">Tem certeza?</div>
                <div class="sure-warning">⚠️ Pastas podem conter arquivos internos.</div>
                <label class="sure-checkbox">
                    <input type="checkbox" id="chkDontShowAgain">
                    Não mostrar novamente
                </label>
                <div class="popup-actions">
                    <button class="popup-btn cancelar" id="sureCancelBtn">Cancelar</button>
                    <button class="popup-btn confirmar" id="sureOkBtn">Confirmar</button>
                </div>
            </div>`;

        showPopup(html);

        document.getElementById('sureCancelBtn').onclick = () => {
            hidePopup(() => resolve({ confirmed: false }));
        };
        document.getElementById('sureOkBtn').onclick = () => {
            const checked = document.getElementById('chkDontShowAgain')?.checked || false;
            if (checked) {
                localStorage.setItem(SKIP_CONFIRM_KEY, 'true');
            }
            hidePopup(() => resolve({ confirmed: true }));
        };
    });
}

export function shouldSkipConfirmDelete() {
    return localStorage.getItem(SKIP_CONFIRM_KEY) === 'true';
}

export function hideSurePopup(callback) {
    hidePopup(callback);
}
