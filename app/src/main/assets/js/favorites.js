const FAVORITES_KEY = 'fm_favorites';
const BRIDGE_SYNC_KEY = 'fm_favorites_synced';

export let favorites = [];

function hasBridge() {
    return typeof window.FileBridge !== 'undefined' && window.FileBridge !== null;
}

function loadFavorites() {
    try {
        const stored = localStorage.getItem(FAVORITES_KEY);
        favorites = stored ? JSON.parse(stored) : [];
    } catch {
        favorites = [];
    }
}

function saveFavorites() {
    try {
        localStorage.setItem(FAVORITES_KEY, JSON.stringify(favorites));
    } catch (e) {
        console.warn('Erro ao salvar favoritos:', e.message);
    }
}

function syncToBridge() {
    if (!hasBridge()) return;
    try {
        const existing = JSON.parse(window.FileBridge.getFavorites() || '[]');
        const existingPaths = new Set(existing.map(f => f.path || f.name));
        favorites.forEach(name => {
            if (!existingPaths.has(name)) {
                window.FileBridge.addFavorite(name, name);
            }
        });
        localStorage.setItem(BRIDGE_SYNC_KEY, 'true');
    } catch (e) {
        console.warn('[favorites] Erro ao sincronizar com bridge:', e.message);
    }
}

function syncFromBridge() {
    if (!hasBridge()) return;
    try {
        const raw = window.FileBridge.getFavorites();
        if (raw) {
            const items = JSON.parse(raw);
            if (Array.isArray(items) && items.length > 0) {
                favorites = items.map(f => f.name || f.path || f);
                saveFavorites();
            }
        }
    } catch (e) {
        console.warn('[favorites] Erro ao carregar da bridge:', e.message);
    }
}

export function toggleFavorite(name) {
    const idx = favorites.indexOf(name);
    if (idx > -1) {
        favorites.splice(idx, 1);
        if (hasBridge()) {
            try { window.FileBridge.removeFavorite(name); } catch (e) {}
        }
    } else {
        favorites.push(name);
        if (hasBridge()) {
            try { window.FileBridge.addFavorite(name, name); } catch (e) {}
        }
    }
    saveFavorites();
}

loadFavorites();
if (hasBridge()) {
    if (localStorage.getItem(BRIDGE_SYNC_KEY)) {
        syncFromBridge();
    } else {
        syncToBridge();
    }
}
