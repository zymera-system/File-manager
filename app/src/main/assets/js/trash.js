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

export function moveToTrash(items, currentPath) {
    const now = Date.now();
    items.forEach(item => {
        trashItems.push({
            name: item.name,
            type: item.type,
            size: item.size || '—',
            date: item.date || '—',
            originalPath: currentPath,
            deletedAt: now,
            expiryDate: now + RETENTION_DAYS * 24 * 60 * 60 * 1000
        });
    });
    saveTrash();
}

export function restoreFromTrash(item) {
    const idx = trashItems.findIndex(t => t.name === item.name && t.deletedAt === item.deletedAt);
    if (idx === -1) return false;

    if (!fileSystem[item.originalPath]) {
        fileSystem[item.originalPath] = [];
    }

    trashItems.splice(idx, 1);
    saveTrash();
    return { name: item.name, type: item.type, originalPath: item.originalPath };
}

export function permanentDelete(item) {
    const idx = trashItems.findIndex(t => t.name === item.name && t.deletedAt === item.deletedAt);
    if (idx === -1) return false;
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
    trashItems = trashItems.filter(t => t.expiryDate > now);
    if (expired.length > 0) saveTrash();
    return expired.length;
}

export function clearTrash() {
    trashItems = [];
    saveTrash();
}
