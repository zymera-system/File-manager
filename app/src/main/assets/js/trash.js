import { fileSystem } from './fileSystem.js';

const TRASH_KEY = 'fm_trash';
const RETENTION_DAYS = 30;
const MAX_TRASH_ITEMS = 500;

let trashItems = [];

export function loadTrash() {
    try {
        const stored = localStorage.getItem(TRASH_KEY);
        trashItems = stored ? JSON.parse(stored) : [];
    } catch {
        trashItems = [];
        console.warn('Dados da lixeira corrompidos. Lixeira redefinida.');
    }
}

function saveTrash() {
    try {
        if (trashItems.length > MAX_TRASH_ITEMS) {
            trashItems = trashItems.slice(-MAX_TRASH_ITEMS);
        }
        localStorage.setItem(TRASH_KEY, JSON.stringify(trashItems));
    } catch (e) {
        console.warn('Erro ao salvar lixeira:', e.message);
    }
}

/**
 * Move itens para a lixeira.
 * Se houver FileBridge, move o arquivo fisicamente para .trash/.
 * Caso contrário, apenas registra no localStorage.
 */
export function moveToTrash(items, currentPath) {
    const hasBridge = typeof window.FileBridge !== 'undefined' && window.FileBridge !== null;
    const now = Date.now();

    items.forEach(item => {
        if (hasBridge && currentPath !== '/') {
            // Mover fisicamente para .trash/
            const fullPath = currentPath === '/' ? '/' + item.name : currentPath + '/' + item.name;
            const devicePath = window.fmGetDevicePath ? window.fmGetDevicePath(fullPath) : fullPath;
            try {
                const raw = window.FileBridge.trashItem(devicePath);
                const result = JSON.parse(raw);
                trashItems.push({
                    name: item.name,
                    type: item.type,
                    size: item.size || '—',
                    date: item.date || '—',
                    originalPath: currentPath,
                    trashPath: result.trashPath || null,
                    deletedAt: now,
                    expiryDate: now + RETENTION_DAYS * 24 * 60 * 60 * 1000,
                    physical: true
                });
            } catch (e) {
                console.warn('[trash] Erro ao mover para lixeira:', e.message);
                // Fallback: só registra metadata
                trashItems.push({
                    name: item.name,
                    type: item.type,
                    size: item.size || '—',
                    date: item.date || '—',
                    originalPath: currentPath,
                    deletedAt: now,
                    expiryDate: now + RETENTION_DAYS * 24 * 60 * 60 * 1000,
                    physical: false
                });
            }
        } else {
            // Sem bridge — apenas metadata
            trashItems.push({
                name: item.name,
                type: item.type,
                size: item.size || '—',
                date: item.date || '—',
                originalPath: currentPath,
                deletedAt: now,
                expiryDate: now + RETENTION_DAYS * 24 * 60 * 60 * 1000,
                physical: false
            });
        }
    });
    saveTrash();
}

/**
 * Restaura um item da lixeira para o caminho original.
 */
export function restoreFromTrash(item) {
    const idx = trashItems.findIndex(t => t.name === item.name && t.deletedAt === item.deletedAt);
    if (idx === -1) return false;

    const hasBridge = typeof window.FileBridge !== 'undefined' && window.FileBridge !== null;

    if (hasBridge && item.trashPath && item.physical) {
        // Restaurar fisicamente do .trash/
        const originalPath = item.originalPath === '/' ? '/' + item.name : item.originalPath + '/' + item.name;
        const deviceOriginal = window.fmGetDevicePath ? window.fmGetDevicePath(originalPath) : originalPath;
        try {
            const raw = window.FileBridge.restoreTrashItem(item.trashPath, deviceOriginal);
            const result = JSON.parse(raw);
            if (result.error) {
                console.warn('[trash] Erro ao restaurar:', result.error);
                return false;
            }
        } catch (e) {
            console.warn('[trash] Erro ao restaurar:', e.message);
            return false;
        }
    } else {
        // Fallback: apenas limpa da lista
        if (!fileSystem[item.originalPath]) {
            fileSystem[item.originalPath] = [];
        }
    }

    trashItems.splice(idx, 1);
    saveTrash();
    return { name: item.name, type: item.type, originalPath: item.originalPath };
}

/**
 * Exclui permanentemente um item da lixeira.
 */
export function permanentDelete(item) {
    const idx = trashItems.findIndex(t => t.name === item.name && t.deletedAt === item.deletedAt);
    if (idx === -1) return false;

    const hasBridge = typeof window.FileBridge !== 'undefined' && window.FileBridge !== null;

    if (hasBridge && item.trashPath && item.physical) {
        try {
            window.FileBridge.permanentDeleteTrash(item.trashPath);
        } catch (e) {
            console.warn('[trash] Erro ao excluir permanentemente:', e.message);
        }
    }

    trashItems.splice(idx, 1);
    saveTrash();
    return true;
}

export function getTrashItems() {
    return [...trashItems];
}

export function getTrashCount() {
    return trashItems.length;
}

export function getDaysRemaining(item) {
    if (!item || !item.expiryDate) return 0;
    const now = Date.now();
    const remaining = item.expiryDate - now;
    return Math.max(0, Math.ceil(remaining / (24 * 60 * 60 * 1000)));
}

export function cleanExpiredTrash() {
    const now = Date.now();
    const expired = trashItems.filter(t => t.expiryDate <= now);

    // Excluir fisicamente itens expirados
    const hasBridge = typeof window.FileBridge !== 'undefined' && window.FileBridge !== null;
    if (hasBridge) {
        expired.forEach(item => {
            if (item.trashPath && item.physical) {
                try {
                    window.FileBridge.permanentDeleteTrash(item.trashPath);
                } catch (e) { /* ignora */ }
            }
        });
    }

    trashItems = trashItems.filter(t => t.expiryDate > now);
    if (expired.length > 0) saveTrash();
    return expired.length;
}

export function clearTrash() {
    const hasBridge = typeof window.FileBridge !== 'undefined' && window.FileBridge !== null;
    if (hasBridge) {
        trashItems.forEach(item => {
            if (item.trashPath && item.physical) {
                try {
                    window.FileBridge.permanentDeleteTrash(item.trashPath);
                } catch (e) { /* ignora */ }
            }
        });
    }
    trashItems = [];
    saveTrash();
}
