import { menuConfig } from './menuConfig.js';
import { cleanExpiredTrash, getTrashItems, restoreFromTrash } from './trash.js';
import { loadRealApps } from './apps.js';

let menuOpen = false;
let getCurrentPageFn = null;
let getCurrentPathFn = null;

export function setupMenu(getPageFn, getPathFn) {
    getCurrentPageFn = getPageFn;
    getCurrentPathFn = getPathFn;
}

function getPageKey() {
    if (!getCurrentPageFn) return 'files';

    const page = getCurrentPageFn();
    const path = getCurrentPathFn ? getCurrentPathFn() : '/';

    if (page === 'apps') return 'apps';
    if (page === 'settings') return 'settings';
    if (page === 'trash') return 'trash';
    if (page === 'files') {
        return path === '/' ? 'files' : 'fileFolder';
    }
    return 'files';
}

function renderMenu() {
    const menu = document.getElementById('globalMenu');
    if (!menu) return;

    const pageKey = getPageKey();
    const pageItems = menuConfig[pageKey] || [];
    const globalItems = menuConfig.global || [];

    const allItems = [
        ...pageItems,
        ...(globalItems.length > 0 && pageItems.length > 0
            ? [{ id: 'global-divider', divider: true }]
            : []),
        ...globalItems
    ];

    menu.innerHTML = allItems.map(item => {
        if (item.divider) {
            return '<div class="menu-divider"></div>';
        }

        const actionName = item.action || '';
        if (!actionName || typeof window[actionName] !== 'function') {
            if (actionName) console.warn(`Menu action "${actionName}" not found on window`);
            return '';
        }

        return `
            <div class="menu-item" onclick="window.${actionName}()">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                    ${item.icon}
                </svg>
                ${item.label}
            </div>
        `;
    }).join('');
}

export function toggleMenu() {
    menuOpen = !menuOpen;
    if (menuOpen) {
        renderMenu();
    }
    document.getElementById('globalMenu').classList.toggle('show', menuOpen);
}

export function closeMenu() {
    const menu = document.getElementById('globalMenu');
    if (menu) menu.classList.remove('show');
    menuOpen = false;
}

export function refreshMenu() {
    if (menuOpen) {
        renderMenu();
    }
}

export function analyzeDevice() {
    closeMenu();
    const toast = document.getElementById('globalToast');
    if (toast) {
        if (window.FileBridge && typeof window.FileBridge.getStorageInfo === 'function') {
            try {
                const raw = window.FileBridge.getStorageInfo();
                const info = JSON.parse(raw);
                const total = info.total || {};
                const used = total.usedFormatted || '?';
                const totalSize = total.totalFormatted || '?';
                const pct = total.percentUsed || 0;
                toast.textContent = `Uso: ${used}/${totalSize} (${pct}%)`;
            } catch (e) {
                toast.textContent = 'Analisando dispositivo...';
            }
        } else {
            toast.textContent = 'Analisando dispositivo...';
        }
        toast.className = 'global-toast info show';
        setTimeout(() => toast.classList.remove('show'), 3000);
    }
}

export function refreshFiles(renderCallback) {
    renderCallback();
    closeMenu();
}

export function openSettings(navigateToTabCallback) {
    navigateToTabCallback('settings');
    history.pushState({ page: 'settings' }, '');
    closeMenu();
}

export function showSortMenu() {
    closeMenu();
    document.getElementById('sortByModal').classList.add('open');
}

export function refreshApps() {
    closeMenu();
    try {
        loadRealApps();
    } catch (e) { /* fallback silencioso */ }
    const toast = document.getElementById('globalToast');
    if (toast) {
        toast.textContent = 'Apps atualizados';
        toast.className = 'global-toast success show';
        setTimeout(() => toast.classList.remove('show'), 2000);
    }
}

export function sortApps() {
    closeMenu();
    document.getElementById('sortAppsModal').classList.add('open');
}

export function clearCache() {
    closeMenu();
    let cleared = 0;
    try {
        // Limpar cache do WebView
        if (window.caches) {
            caches.keys().then(names => {
                names.forEach(name => caches.delete(name));
            });
        }
        // Limpar itens temporários da lixeira expirados
        cleared = cleanExpiredTrash();
    } catch (e) { /* fallback */ }
    const toast = document.getElementById('globalToast');
    if (toast) {
        toast.textContent = cleared > 0 ? `${cleared} item(ns) limpo(s)` : 'Cache limpo';
        toast.className = 'global-toast success show';
        setTimeout(() => toast.classList.remove('show'), 2000);
    }
}

export function showAbout() {
    closeMenu();
    let version = 'v1.0';
    try {
        if (window.UpdateManager && typeof window.UpdateManager.getCurrentVersion === 'function') {
            const raw = window.UpdateManager.getCurrentVersion();
            const info = JSON.parse(raw);
            if (info.versionName) version = 'v' + info.versionName;
        }
    } catch (e) { /* mantém fallback */ }
    const toast = document.getElementById('globalToast');
    if (toast) {
        toast.textContent = `FileManager ${version}`;
        toast.className = 'global-toast info show';
        setTimeout(() => toast.classList.remove('show'), 2500);
    }
}

export function restoreAll() {
    closeMenu();
    const items = getTrashItems();
    if (items.length === 0) {
        const toast = document.getElementById('globalToast');
        if (toast) {
            toast.textContent = 'Lixeira vazia';
            toast.className = 'global-toast info show';
            setTimeout(() => toast.classList.remove('show'), 2000);
        }
        return;
    }
    let restored = 0;
    items.forEach(item => {
        const result = restoreFromTrash(item);
        if (result) restored++;
    });
    const toast = document.getElementById('globalToast');
    if (toast) {
        toast.textContent = `${restored} item(ns) restaurado(s)`;
        toast.className = 'global-toast success show';
        setTimeout(() => toast.classList.remove('show'), 2500);
    }
}

export function setupMenuClose() {
    document.addEventListener('click', function(e) {
        if (!e.target.closest('.header-btn')) {
            closeMenu();
        }
    });
}