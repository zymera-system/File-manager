const FAVORITES_KEY = 'fm_favorites';
const BRIDGE_SYNC_KEY = 'fm_favorites_synced';

export let favorites = [];

function hasBridge() {
    return typeof window.FileBridge !== 'undefined' && window.FileBridge !== null;
}

/** Normaliza um item para { name, path }, aceitando string ou objeto */
function normalizeItem(item) {
    if (typeof item === 'string') {
        return { name: item, path: item };
    }
    return {
        name: item.name || item.path || '',
        path: item.path || item.name || ''
    };
}

function loadFavorites() {
    try {
        const stored = localStorage.getItem(FAVORITES_KEY);
        const raw = stored ? JSON.parse(stored) : [];
        // Normalizar tudo para { name, path }
        favorites = raw.map(normalizeItem);
    } catch {
        favorites = [];
    }
}

function saveFavorites() {
    try {
        // Salvar como objetos para preservar path
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
        favorites.forEach(item => {
            if (!existingPaths.has(item.name) && !existingPaths.has(item.path)) {
                window.FileBridge.addFavorite(item.path || item.name, item.name);
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
                favorites = items.map(f => ({
                    name: f.name || f.path || String(f),
                    path: f.path || f.name || String(f)
                }));
                saveFavorites();
            }
        }
    } catch (e) {
        console.warn('[favorites] Erro ao carregar da bridge:', e.message);
    }
}

export function toggleFavorite(name, fullPath) {
    const entry = normalizeItem({ name, path: fullPath || name });

    // Procurar por nome
    const idx = favorites.findIndex(f => f.name === entry.name);
    if (idx > -1) {
        favorites.splice(idx, 1);
        if (hasBridge()) {
            try { window.FileBridge.removeFavorite(entry.name); } catch (e) {}
        }
    } else {
        favorites.push(entry);
        if (hasBridge()) {
            try { window.FileBridge.addFavorite(entry.path, entry.name); } catch (e) {}
        }
    }
    saveFavorites();
}

/**
 * Retorna se um nome de arquivo está nos favoritos.
 */
export function isFavorite(name) {
    return favorites.some(f => f.name === name);
}

export function getFavoritePath(name) {
    const found = favorites.find(f => f.name === name);
    return found ? found.path : name;
}

loadFavorites();
if (hasBridge()) {
    if (localStorage.getItem(BRIDGE_SYNC_KEY)) {
        syncFromBridge();
    } else {
        syncToBridge();
    }
}
