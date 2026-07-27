import { menuConfig } from './menuConfig.js';

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
        toast.textContent = 'Analisando dispositivo...';
        toast.className = 'global-toast info show';
        setTimeout(() => toast.classList.remove('show'), 2000);
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
    const toast = document.getElementById('globalToast');
    if (toast) {
        toast.textContent = 'Cache limpo';
        toast.className = 'global-toast success show';
        setTimeout(() => toast.classList.remove('show'), 2000);
    }
}

export function showAbout() {
    closeMenu();
    const toast = document.getElementById('globalToast');
    if (toast) {
        toast.textContent = 'FileManager v1.0';
        toast.className = 'global-toast info show';
        setTimeout(() => toast.classList.remove('show'), 2500);
    }
}

export function restoreAll() {
    closeMenu();
    const toast = document.getElementById('globalToast');
    if (toast) {
        toast.textContent = 'Restaurando todos os itens...';
        toast.className = 'global-toast info show';
        setTimeout(() => toast.classList.remove('show'), 2000);
    }
}

export function setupMenuClose() {
    document.addEventListener('click', function(e) {
        if (!e.target.closest('.header-btn')) {
            closeMenu();
        }
    });
}