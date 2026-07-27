import { getFiles } from './getFiles.js';

/**
 * deleteItemDirect — exclui um item real via FileBridge.
 * @param {string} name — nome do arquivo/pasta
 * @param {string} currentPath — caminho atual na interface
 * @returns {boolean} true se excluído com sucesso
 */
export function deleteItemDirect(name, currentPath) {
    if (currentPath === '/') return false;

    const hasBridge = typeof window.FileBridge !== 'undefined' && window.FileBridge !== null;

    if (hasBridge) {
        // Construir caminho completo com separador
        const fullPath = currentPath === '/' ? '/' + name : currentPath + '/' + name;
        const devicePath = window.fmGetDevicePath
            ? window.fmGetDevicePath(fullPath)
            : fullPath;

        try {
            const raw = window.FileBridge.deleteItem(devicePath);
            const result = JSON.parse(raw);
            return !result.error;
        } catch (e) {
            console.error('[delete] Erro ao excluir:', e.message);
            return false;
        }
    }

    // Fallback mock
    const current = getFiles(currentPath);
    const idx = current.findIndex(f => f.name === name);
    if (idx > -1) { current.splice(idx, 1); return true; }
    return false;
}
