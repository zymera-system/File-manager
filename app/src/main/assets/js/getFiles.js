import { fileSystem } from './fileSystem.js';

// ========================================
// CAMINHOS PADRÃO (virtual → real)
// ========================================
let standardPaths = null;

/**
 * Carrega caminhos reais do dispositivo via FileBridge.getStandardPaths().
 * Cacheado para não chamar a bridge repetidamente.
 */
function loadStandardPaths() {
    if (standardPaths) return standardPaths;
    try {
        if (window.FileBridge && window.FileBridge.getStandardPaths) {
            const raw = window.FileBridge.getStandardPaths();
            if (raw) {
                standardPaths = JSON.parse(raw);
                console.log('[getFiles] Standard paths carregados:', standardPaths);
                return standardPaths;
            }
        }
    } catch (e) {
        console.warn('[getFiles] Erro ao carregar standard paths:', e);
    }
    return null;
}

/**
 * Traduz caminhos virtuais da Home para caminhos reais do dispositivo.
 * Ex: '/Armazenamento' → '/storage/emulated/0'
 *     '/Downloads' → '/storage/emulated/0/Download'
 * Se o caminho já é absoluto (começa com /storage), retorna como está.
 */
export function resolveVirtualPath(virtualPath) {
    if (!virtualPath) return virtualPath;

    // Se já é um caminho real do dispositivo, retornar direto
    if (virtualPath.startsWith('/storage/') || virtualPath.startsWith('/sdcard/')) {
        return virtualPath;
    }

    const paths = loadStandardPaths();
    if (!paths) return virtualPath; // Sem bridge, usar como está

    const root = paths.root || '/storage/emulated/0';

    // Tenta obter caminhos reais de armazenamento removível via StorageBridge
    let sdCardPath = null;
    let usbDrivePath = null;
    try {
        if (typeof window.StorageBridge !== 'undefined' && window.StorageBridge !== null) {
            if (typeof window.StorageBridge.getSDCardPath === 'function') {
                const sdRaw = window.StorageBridge.getSDCardPath();
                if (sdRaw) {
                    const sdInfo = JSON.parse(sdRaw);
                    if (sdInfo.path) sdCardPath = sdInfo.path;
                }
            }
            if (typeof window.StorageBridge.getUSBDrivePath === 'function') {
                const usbRaw = window.StorageBridge.getUSBDrivePath();
                if (usbRaw) {
                    const usbInfo = JSON.parse(usbRaw);
                    if (usbInfo.path) usbDrivePath = usbInfo.path;
                }
            }
        }
    } catch (e) {
        // StorageBridge pode não estar disponível — usar fallback
    }

    // Fallback: caminhos conhecidos comuns
    if (!sdCardPath) {
        const knownSD = ['/storage/sdcard1', '/storage/extSdCard', '/storage/external_SD',
                         '/storage/ext_sd', '/storage/removable/sdcard1', '/mnt/sdcard',
                         '/mnt/extSdCard', '/storage/emulated/0'];
        for (const p of knownSD) {
            if (p !== root) { sdCardPath = p; break; }
        }
    }
    if (!usbDrivePath) {
        const knownUSB = ['/storage/usbotg', '/storage/usb', '/storage/UsbDrive',
                          '/storage/USB', '/mnt/usb', '/storage/udisk'];
        for (const p of knownUSB) {
            if (p !== root) { usbDrivePath = p; break; }
        }
    }

    // Mapeamento: nome virtual → chave no standardPaths
    const pathMap = {
        '/Armazenamento': root,
        '/Downloads': paths.download || root + '/Download',
        '/Imagens': paths.pictures || root + '/Pictures',
        '/Audios': paths.music || root + '/Music',
        '/Videos': paths.movies || root + '/Movies',
        '/Documentos': paths.documents || root + '/Documents',
        '/DCIM': paths.dcim || root + '/DCIM',
        '/CartaoSD': sdCardPath,
        '/DriveUSB': usbDrivePath,
    };

    // Caminhos virtuais simples
    if (pathMap[virtualPath]) {
        return pathMap[virtualPath];
    }

    // Subcaminhos virtuais: ex: '/Armazenamento/Pasta' → '/storage/emulated/0/Pasta'
    for (const [virtual, real] of Object.entries(pathMap)) {
        if (virtualPath.startsWith(virtual + '/')) {
            const suffix = virtualPath.substring(virtual.length + 1);
            return real + '/' + suffix;
        }
    }

    return virtualPath;
}

/**
 * hasBridge — verifica se a bridge nativa está disponível.
 */
function hasBridge() {
    return typeof window.FileBridge !== 'undefined' && window.FileBridge !== null && typeof window.FileBridge.listFiles === 'function';
}

/**
 * hasPagedBridge — verifica se a bridge suporta paginação.
 */
function hasPagedBridge() {
    return hasBridge() && typeof window.FileBridge.listFilesPaged === 'function';
}

// ========================================
// CACHE DE ARQUIVOS COM PAGINAÇÃO
// ========================================

const PAGE_SIZE = 50;

/**
 * Cache de diretórios: path → { items, total, loadedAll }
 */
const dirCache = new Map();

/**
 * Invalida o cache para um diretório específico (após criar/excluir/renomear).
 */
export function invalidateDirCache(path) {
    const resolved = resolveVirtualPath(path);
    dirCache.delete(resolved);
    // Também limpar cache do pai
    const parent = resolved.substring(0, resolved.lastIndexOf('/'));
    if (parent) dirCache.delete(parent);
}

/**
 * Invalida todo o cache de arquivos (ao mudar de diretório).
 */
export function clearFileCache() {
    dirCache.clear();
}

/**
 * getFilesPaged — Versão paginada do getFiles.
 * 
 * Retorna um subconjunto dos arquivos, carregando sob demanda da bridge.
 * Itens já carregados ficam em cache para navegação fluida.
 * 
 * @param {string} path - Caminho virtual ou real
 * @param {number} offset - Índice inicial (0 = primeiro)
 * @param {number} [limit=PAGE_SIZE] - Quantos itens carregar
 * @returns {{ items: Array, total: number, hasMore: boolean }}
 */
export function getFilesPaged(path, offset = 0, limit = PAGE_SIZE) {
    const resolvedPath = resolveVirtualPath(path);

    // Fallback: sem bridge, retorna do fileSystem (dados mock, sem paginação real)
    if (!hasBridge()) {
        const allItems = fileSystem[path] || [];
        const total = allItems.length;
        const from = Math.max(0, offset);
        const to = Math.min(total, offset + limit);
        return {
            items: allItems.slice(from, to),
            total: total,
            hasMore: to < total,
        };
    }

    // Com bridge: usar paginação real
    if (hasPagedBridge()) {
        try {
            const json = window.FileBridge.listFilesPaged(resolvedPath, offset, limit);
            if (!json) return { items: [], total: 0, hasMore: false };
            if (json.startsWith('{"error"')) {
                const err = JSON.parse(json);
                console.warn('[getFiles] Bridge paginada retornou erro:', err.message);
                return { items: [], total: 0, hasMore: false };
            }
            const result = JSON.parse(json);
            const items = (result.items || []).map(item => ({
                name: item.name,
                type: item.type,
                size: item.size || undefined,
                date: item.date || undefined,
            }));
            return {
                items,
                total: result.total || 0,
                hasMore: result.hasMore || false,
            };
        } catch (e) {
            console.warn('[getFiles] Exceção na bridge paginada:', e);
        }
    }

    // Bridge antiga (sem paginação nativa) — carregar tudo e paginar no JS
    try {
        const json = window.FileBridge.listFiles(resolvedPath);
        if (json && !json.startsWith('{"error"')) {
            const allItems = JSON.parse(json);
            if (Array.isArray(allItems)) {
                const mapped = allItems.map(item => ({
                    name: item.name,
                    type: item.type,
                    size: item.size || undefined,
                    date: item.date || undefined,
                }));
                const total = mapped.length;
                const from = Math.max(0, offset);
                const to = Math.min(total, offset + limit);
                return {
                    items: mapped.slice(from, to),
                    total: total,
                    hasMore: to < total,
                };
            }
        }
    } catch (e) {
        console.warn('[getFiles] Exceção na bridge legada:', e);
    }

    return { items: [], total: 0, hasMore: false };
}

/**
 * getFiles — mantido para compatibilidade, carrega tudo de uma vez.
 * Internamente usa getFilesPaged para consistência.
 */
export function getFiles(path) {
    const result = getFilesPaged(path, 0, -1);
    return result.items;
}

/**
 * Pré-carrega o próximo lote de arquivos em background.
 * Usado pelo scroll infinito para manter a fluidez.
 */
export function prefetchNextPage(path, currentOffset) {
    const nextOffset = currentOffset + PAGE_SIZE;
    // Dispara o carregamento assíncrono via requestIdleCallback ou setTimeout
    const load = () => {
        getFilesPaged(path, nextOffset, PAGE_SIZE);
    };
    if (typeof requestIdleCallback === 'function') {
        requestIdleCallback(load, { timeout: 3000 });
    } else {
        setTimeout(load, 100);
    }
}
