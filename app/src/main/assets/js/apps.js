// ========================================
// DADOS — Carregados do FileBridge ou fallback mock
// ========================================

import { showLoading, updateProgress, updateFile, hideLoading } from './loading.js';

let downloadedApps = [];
let systemApps = [];

/**
 * Carrega apps reais do FileBridge.
 * Fallback para dados mock se não houver bridge.
 */
export function loadRealApps() {
    const hasBridge = typeof window.FileBridge !== 'undefined' && window.FileBridge !== null;
    if (!hasBridge) {
        // Fallback mock
        downloadedApps = [
            { name: 'WhatsApp', developer: 'WhatsApp Inc.', size: '120 MB', icon: '📱' },
            { name: 'Chrome', developer: 'Google', size: '250 MB', icon: '🌐' },
            { name: 'Telegram', developer: 'Telegram FZ-LLC', size: '95 MB', icon: '✈️' },
            { name: 'Instagram', developer: 'Meta', size: '180 MB', icon: '📷' },
            { name: 'Spotify', developer: 'Spotify AB', size: '140 MB', icon: '🎵' },
            { name: 'Netflix', developer: 'Netflix Inc.', size: '200 MB', icon: '🎬' },
        ];
        systemApps = [
            { name: 'Configurações', developer: 'Android System', size: '80 MB', icon: '⚙️' },
            { name: 'Serviços Google', developer: 'Google', size: '300 MB', icon: '🔧' },
            { name: 'Telefone', developer: 'Android System', size: '45 MB', icon: '📞' },
            { name: 'Mensagens', developer: 'Android System', size: '60 MB', icon: '💬' },
            { name: 'Câmera', developer: 'Android System', size: '120 MB', icon: '📸' },
            { name: 'Relógio', developer: 'Android System', size: '35 MB', icon: '⏰' },
        ];
        return;
    }

    try {
        // Apps do usuário (não-sistema)
        const userRaw = window.FileBridge.getInstalledApps(false);
        const userList = JSON.parse(userRaw);
        downloadedApps = Array.isArray(userList) ? userList.map(app => ({
            name: app.name || app.packageName,
            packageName: app.packageName,
            developer: app.packageName,
            size: app.sizeFormatted || '? MB',
            sizeBytes: app.size || 0,
            icon: '📱',
            versionName: app.versionName,
            apkPath: app.apkPath,
            isSystem: false
        })) : [];

        // Apps do sistema
        const sysRaw = window.FileBridge.getInstalledApps(true);
        const sysList = JSON.parse(sysRaw);
        if (Array.isArray(sysList)) {
            systemApps = sysList.filter(a => a.isSystem).map(app => ({
                name: app.name || app.packageName,
                packageName: app.packageName,
                developer: app.packageName,
                size: app.sizeFormatted || '? MB',
                sizeBytes: app.size || 0,
                icon: '⚙️',
                versionName: app.versionName,
                apkPath: app.apkPath,
                isSystem: true
            }));
        }
    } catch (e) {
        console.warn('[apps] Erro ao carregar apps reais:', e.message);
    }
}

// ========================================
// ESTADO DO MÓDULO
// ========================================

let currentTab = 'downloaded';
let selectionMode = false;
let selectedApps = new Set();
let fabOpen = false;
let longPressTimer = null;
let touchStartX = 0;
let touchStartY = 0;
let isAnimating = false;
let searchQuery = '';
let currentViewMode = 'detailed';
let currentSortBy = 'date-asc';
let renderedCount = 0;
const RENDER_BATCH = 50;

const sortLabels = {
    'date-asc': 'Data <i class="sort-arrow">▼</i>',
    'date-desc': 'Data <i class="sort-arrow">▲</i>',
    'name-asc': 'Nome A-Z',
    'name-desc': 'Nome Z-A',
    'size-asc': 'Tamanho <i class="sort-arrow">▼</i>',
    'size-desc': 'Tamanho <i class="sort-arrow">▲</i>'
};

// ========================================
// BUSCA
// ========================================

export function filterApps(query) {
    searchQuery = (query || '').toLowerCase().trim();
    renderAppList();
}

export function getSearchQuery() {
    return searchQuery;
}

// ========================================
// RENDERIZAÇÃO
// ========================================

export function renderAppsPage() {
    renderAppList();
    updateAppsSelectionUI();
}

function renderAppList() {
    const list = document.getElementById('appsList');
    if (!list) return;
    
    let apps = currentTab === 'downloaded' ? [...downloadedApps] : [...systemApps];
    
    // Filtrar por busca
    if (searchQuery) {
        apps = apps.filter(app => 
            app.name.toLowerCase().includes(searchQuery) ||
            app.developer.toLowerCase().includes(searchQuery)
        );
    }
    
    // Renderização em lotes (lazy loading)
    renderedCount = RENDER_BATCH;
    const visibleApps = apps.slice(0, renderedCount);
    const hasMore = apps.length > renderedCount;
    
    const iconClass = currentTab === 'downloaded' ? 'downloaded' : 'system';
    
    list.innerHTML = visibleApps.map(app => `
        <div class="app-card ${selectedApps.has(app.name) ? 'selected' : ''}"
             data-app-name="${app.name}"
             onclick="window.fmAppClick('${app.name}')"
             onmousedown="window.fmAppLongPressStart('${app.name}')"
             onmouseup="window.fmAppLongPressEnd()"
             onmouseleave="window.fmAppLongPressEnd()"
             ontouchstart="window.fmAppLongPressStart('${app.name}')"
             ontouchend="window.fmAppLongPressEnd()">
            <div class="app-checkbox ${selectionMode ? 'visible' : ''}"></div>
            <div class="app-icon ${iconClass}">${app.icon}</div>
            <div class="app-info">
                <div class="app-name">${app.name}</div>
                <div class="app-developer">${app.developer}</div>
                <div class="app-size">${app.size}</div>
            </div>
        </div>
    `).join('');
    
    setupSwipe(list);
    setupInfiniteScroll(list, apps, visibleApps.length, iconClass);
}

// ========================================
// INFINITE SCROLL (lazy loading)
// ========================================
let scrollListener = null;

function setupInfiniteScroll(list, allApps, initialCount, iconClass) {
    // Remover listener anterior se existir
    if (scrollListener) {
        list.removeEventListener('scroll', scrollListener);
        scrollListener = null;
    }

    if (allApps.length <= initialCount) return; // Não precisa de scroll

    let loading = false;
    scrollListener = () => {
        if (loading) return;
        const nearBottom = list.scrollTop + list.clientHeight >= list.scrollHeight - 100;
        if (!nearBottom) return;

        loading = true;
        const nextBatch = allApps.slice(renderedCount, renderedCount + RENDER_BATCH);
        if (nextBatch.length === 0) { loading = false; return; }

        const fragment = document.createDocumentFragment();
        nextBatch.forEach(app => {
            const div = document.createElement('div');
            div.className = `app-card ${selectedApps.has(app.name) ? 'selected' : ''}`;
            div.dataset.appName = app.name;
            div.setAttribute('onclick', `window.fmAppClick('${app.name.replace(/'/g, "\\'")}')`);
            div.setAttribute('onmousedown', `window.fmAppLongPressStart('${app.name.replace(/'/g, "\\'")}')`);
            div.setAttribute('onmouseup', 'window.fmAppLongPressEnd()');
            div.setAttribute('onmouseleave', 'window.fmAppLongPressEnd()');
            div.setAttribute('ontouchstart', `window.fmAppLongPressStart('${app.name.replace(/'/g, "\\'")}')`);
            div.setAttribute('ontouchend', 'window.fmAppLongPressEnd()');
            div.innerHTML = `
                <div class="app-checkbox ${selectionMode ? 'visible' : ''}"></div>
                <div class="app-icon ${iconClass}">${app.icon || '📱'}</div>
                <div class="app-info">
                    <div class="app-name">${app.name}</div>
                    <div class="app-developer">${app.developer || ''}</div>
                    <div class="app-size">${app.size || ''}</div>
                </div>
            `;
            fragment.appendChild(div);
        });

        list.appendChild(fragment);
        renderedCount += nextBatch.length;
        loading = false;
    };

    list.addEventListener('scroll', scrollListener);
}

let swipeInitialized = false;

function setupSwipe(element) {
    if (swipeInitialized) return;
    swipeInitialized = true;

    element.addEventListener('touchstart', (e) => {
        touchStartX = e.touches[0].clientX;
        touchStartY = e.touches[0].clientY;
    }, { passive: true });
    
    element.addEventListener('touchend', (e) => {
        if (isAnimating) return;
        
        const touchEndX = e.changedTouches[0].clientX;
        const touchEndY = e.changedTouches[0].clientY;
        const deltaX = touchEndX - touchStartX;
        const deltaY = touchEndY - touchStartY;
        
        if (Math.abs(deltaX) > 50 && Math.abs(deltaX) > Math.abs(deltaY)) {
            if (deltaX < 0 && currentTab === 'downloaded') {
                switchTab('system');
            } else if (deltaX > 0 && currentTab === 'system') {
                switchTab('downloaded');
            }
        }
    }, { passive: true });
}

// ========================================
// NAVEGAÇÃO ENTRE ABAS
// ========================================

export function switchTab(tab) {
    if (tab === currentTab || isAnimating) return;
    
    isAnimating = true;
    const list = document.getElementById('appsList');
    const tabs = document.querySelectorAll('.apps-tab');
    
    const direction = tab === 'system' ? 'slide-left' : 'slide-right';
    list.classList.add(direction);
    
    setTimeout(() => {
        currentTab = tab;
        
        tabs.forEach(t => {
            t.classList.toggle('active', t.dataset.tab === tab);
        });
        
        list.classList.remove(direction);
        renderAppList();
        
        const inDirection = tab === 'system' ? 'appsSlideInFromRight' : 'appsSlideInFromLeft';
        list.style.animation = `${inDirection} 0.3s cubic-bezier(0.4, 0, 0.2, 1)`;
        
        setTimeout(() => {
            list.style.animation = '';
            isAnimating = false;
        }, 300);
    }, 300);
}

// ========================================
// SELEÇÃO
// ========================================

export function enterSelectionMode(appName) {
    selectionMode = true;
    selectedApps.add(appName);
    updateAppsSelectionUI();
}

export function toggleAppSelection(appName) {
    if (selectedApps.has(appName)) {
        selectedApps.delete(appName);
    } else {
        selectedApps.add(appName);
    }
    
    if (selectedApps.size === 0) {
        clearAppSelection();
    } else {
        updateAppsSelectionUI();
    }
}

export function clearAppSelection() {
    selectionMode = false;
    selectedApps.clear();
    fabOpen = false;
    updateAppsSelectionUI();
}

function updateAppsSelectionUI() {
    const selBar = document.getElementById('appsSelectionBar');
    if (selBar) {
        selBar.classList.toggle('active', selectionMode);
        const count = selBar.querySelector('.sel-count');
        if (count) count.textContent = `${selectedApps.size} selecionados`;
    }
    
    const tabs = document.getElementById('appsTabs');
    if (tabs) {
        tabs.classList.toggle('hidden', selectionMode);
    }

    const searchBar = document.getElementById('appsSearchBar');
    if (searchBar) {
        searchBar.classList.toggle('hidden', selectionMode);
    }
    
    const fab = document.getElementById('appsFab');
    if (fab) {
        fab.classList.toggle('visible', selectionMode);
        fab.classList.toggle('open', fabOpen);
    }
    
    const fabBtn = document.getElementById('fabBtn');
    if (fabBtn) {
        fabBtn.classList.toggle('open', fabOpen);
    }
    
    document.querySelectorAll('.app-card').forEach(card => {
        const cb = card.querySelector('.app-checkbox');
        if (cb) cb.classList.toggle('visible', selectionMode);
        card.classList.toggle('selected', selectedApps.has(card.dataset.appName));
    });
}

// ========================================
// FAB
// ========================================

export function toggleFab() {
    fabOpen = !fabOpen;
    updateAppsSelectionUI();
}

// ========================================
// AÇÕES
// ========================================

export async function backupApk() {
    const apps = Array.from(selectedApps);
    toggleFab();

    if (apps.length === 0) return;

    const hasBridge = typeof window.FileBridge !== 'undefined' && window.FileBridge !== null;

    showLoading({
        icon: 'backup',
        title: 'Criando backup APK',
        current: 0,
        total: apps.length,
        percentage: 0,
        cancellable: false
    });

    let backed = 0;
    for (let i = 0; i < apps.length; i++) {
        await new Promise(r => setTimeout(r, 300));

        if (hasBridge) {
            try {
                const appData = [...downloadedApps, ...systemApps].find(a => a.name === apps[i]);
                if (appData && appData.packageName) {
                    const destDir = window.FileBridge.getRootPath() + '/Download/Backups';
                    const raw = window.FileBridge.backupApk(appData.packageName, destDir);
                    const result = JSON.parse(raw);
                    if (result.success) backed++;
                }
            } catch (e) {
                console.warn('[apps] Erro ao fazer backup:', e.message);
            }
        }

        updateProgress(Math.round(((i + 1) / apps.length) * 100), i + 1, apps.length);
        updateFile(apps[i] + '.apk', null, null, null);
    }

    await new Promise(r => setTimeout(r, 200));
    hideLoading();
    showToast(`✅ ${backed} backup(s) salvo(s)`, 'success');
}

export function uninstallApp() {
    toggleFab();
    
    const list = document.getElementById('uninstallAppsList');
    if (list) {
        list.innerHTML = Array.from(selectedApps)
            .map(app => `<div class="modal-app-item">• ${app}</div>`)
            .join('');
    }
    
    const modal = document.getElementById('uninstallModal');
    if (modal) modal.classList.add('open');
}

export async function confirmUninstall() {
    const apps = Array.from(selectedApps);
    const total = apps.length;

    if (total === 0) {
        closeModal('uninstall');
        return;
    }

    const hasBridge = typeof window.FileBridge !== 'undefined' && window.FileBridge !== null;

    showLoading({
        icon: 'uninstall',
        title: 'Desinstalando apps',
        current: 0,
        total: total,
        percentage: 0,
        cancellable: false
    });

    for (let i = 0; i < apps.length; i++) {
        await new Promise(r => setTimeout(r, 300));

        const appName = apps[i];

        if (hasBridge) {
            try {
                const appData = [...downloadedApps, ...systemApps].find(a => a.name === appName);
                if (appData && appData.packageName) {
                    window.FileBridge.uninstallApp(appData.packageName);
                }
            } catch (e) {
                console.warn('[apps] Erro ao desinstalar:', e.message);
            }
        }

        const idxDownloaded = downloadedApps.findIndex(a => a.name === appName);
        if (idxDownloaded !== -1) downloadedApps.splice(idxDownloaded, 1);

        const idxSystem = systemApps.findIndex(a => a.name === appName);
        if (idxSystem !== -1) systemApps.splice(idxSystem, 1);

        updateProgress(Math.round(((i + 1) / total) * 100), i + 1, total);
        updateFile(appName, null, null, null);
    }

    await new Promise(r => setTimeout(r, 200));
    hideLoading();
    closeModal('uninstall');
    clearAppSelection();
    renderAppList();
    const uninstalled = [...downloadedApps, ...systemApps].filter(a => !apps.includes(a.name));
    const confirmed = total - (downloadedApps.length + systemApps.length - apps.length);
    showToast(`🗑️ ${confirmed} app(s) desinstalado(s)`, 'success');
}

export function shareApp() {
    toggleFab();
    
    const modal = document.getElementById('shareModal');
    if (modal) modal.classList.add('open');
}

export function shareVia(platform) {
    const apps = Array.from(selectedApps);
    const hasBridge = typeof window.FileBridge !== 'undefined' && window.FileBridge !== null;

    if (hasBridge && apps.length > 0) {
        try {
            const appData = [...downloadedApps, ...systemApps].find(a => a.name === apps[0]);
            if (appData && appData.apkPath) {
                // Todos os métodos de compartilhamento usam shareFile da bridge
                // (shareViaBluetooth/WhatsApp/Drive foram unificados em shareFile)
                window.FileBridge.shareFile(appData.apkPath);
            }
        } catch (e) {
            console.warn('[apps] Erro ao compartilhar:', e.message);
        }
    }

    closeModal('share');
    clearAppSelection();
    showToast(`📤 Compartilhando via ${platform}`, 'success');
}

// ========================================
// MODALS
// ========================================

export function closeModal(type) {
    const modalId = type === 'uninstall' ? 'uninstallModal' : 'shareModal';
    const modal = document.getElementById(modalId);
    if (modal) modal.classList.remove('open');
}

// ========================================
// TOAST
// ========================================

function showToast(message, type = 'success') {
    const toast = document.getElementById('appsToast');
    if (!toast) return;
    
    toast.textContent = message;
    toast.className = `apps-toast ${type} show`;
    
    setTimeout(() => {
        toast.classList.remove('show');
    }, 2000);
}

// ========================================
// LONG PRESS
// ========================================

export function appLongPressStart(appName) {
    if (longPressTimer) clearTimeout(longPressTimer);
    longPressTimer = setTimeout(() => {
        enterSelectionMode(appName);
    }, 500);
}

export function appLongPressEnd() {
    if (longPressTimer) {
        clearTimeout(longPressTimer);
        longPressTimer = null;
    }
}

// ========================================
// CLIQUE NO APP
// ========================================

export function appClick(appName) {
    if (selectionMode) {
        toggleAppSelection(appName);
    }
}

// ========================================
// MODAL ORDENAR
// ========================================

export function openSortAppsModal() {
    const modal = document.getElementById('sortAppsModal');
    if (modal) modal.classList.add('open');
}

export function closeSortAppsModal() {
    const modal = document.getElementById('sortAppsModal');
    if (modal) modal.classList.remove('open');
}

export function openSortByModal() {
    const modal = document.getElementById('sortByModal');
    if (modal) modal.classList.add('open');
}

export function confirmSortBy() {
    const checked = document.querySelector('input[name="sortBy"]:checked');
    if (checked) {
        currentSortBy = checked.value;
        const label = document.getElementById('sortAppsCurrentLabel');
        if (label) label.innerHTML = sortLabels[currentSortBy] || currentSortBy;
    }
    const modal = document.getElementById('sortByModal');
    if (modal) modal.classList.remove('open');
}

export function applySortApps() {
    const viewChecked = document.querySelector('input[name="viewMode"]:checked');
    if (viewChecked) currentViewMode = viewChecked.value;

    sortAppList();
    closeSortAppsModal();
    renderAppList();
}

function sortAppList() {
    const comparator = {
        'date-asc': (a, b) => (a.name || '').localeCompare(b.name || ''),
        'date-desc': (a, b) => (b.name || '').localeCompare(a.name || ''),
        'name-asc': (a, b) => a.name.localeCompare(b.name),
        'name-desc': (a, b) => b.name.localeCompare(a.name),
        'size-asc': (a, b) => parseSize(a.size) - parseSize(b.size),
        'size-desc': (a, b) => parseSize(b.size) - parseSize(a.size)
    };

    const compare = comparator[currentSortBy] || comparator['name-asc'];

    downloadedApps.sort(compare);
    systemApps.sort(compare);
}

function parseSize(sizeStr) {
    if (!sizeStr) return 0;
    const match = sizeStr.match(/([\d.,]+)/);
    if (!match) return 0;
    const num = parseFloat(match[1].replace(',', '.'));
    if (sizeStr.includes('GB')) return num * 1024;
    if (sizeStr.includes('MB')) return num;
    if (sizeStr.includes('KB')) return num / 1024;
    return num;
}
