const FAVORITES_KEY = 'fm_favorites';

export let favorites = [];

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

export function toggleFavorite(name) {
    const idx = favorites.indexOf(name);
    idx > -1 ? favorites.splice(idx, 1) : favorites.push(name);
    saveFavorites();
}

loadFavorites();
