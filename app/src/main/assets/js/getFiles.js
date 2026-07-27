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

    // Mapeamento: nome virtual → chave no standardPaths
    const pathMap = {
        '/Armazenamento': root,
        '/Downloads': paths.download || root + '/Download',
        '/Imagens': paths.pictures || root + '/Pictures',
        '/Audios': paths.music || root + '/Music',
        '/Videos': paths.movies || root + '/Movies',
        '/Documentos': paths.documents || root + '/Documents',
        '/DCIM': paths.dcim || root + '/DCIM',
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
 * getFiles — obtém lista de arquivos/pastas de um diretório.
 * 
 * Quando a bridge está disponível, chama FileBridge.listFiles() com o caminho
 * resolvido (virtual → real). Caso contrário, usa o fallback mock.
 * 
 * A bridge retorna JSON com propriedades:
 *   { name, type ("folder"|"file"), path, size (string formatada), date (string), canRead, canWrite, hidden }
 * 
 * Esses dados já vêm no formato correto para a UI, então passamos direto.
 */
export function getFiles(path) {
    // 1. Resolver caminho virtual → real
    const resolvedPath = resolveVirtualPath(path);

    // 2. Tentar usar a bridge real (Android)
    if (hasBridge()) {
        try {
            const json = window.FileBridge.listFiles(resolvedPath);
            if (json) {
                // Verificar se é erro
                if (json.startsWith('{"error"')) {
                    const err = JSON.parse(json);
                    console.warn('[getFiles] Bridge retornou erro:', err.message);
                    // Fallback para mock
                } else {
                    const result = JSON.parse(json);
                    if (Array.isArray(result)) {
                        console.log('[getFiles] Bridge retornou', result.length, 'itens para', resolvedPath);
                        // A bridge já retorna no formato correto:
                        // { name, type: "folder"|"file", path, size: "1.8 MB", date: "2025-03-10" }
                        // Apenas mapeamos para o formato que a UI espera
                        return result.map(item => ({
                            name: item.name,
                            type: item.type,         // "folder" ou "file" — já correto
                            size: item.size || undefined, // Já formatado pela bridge
                            date: item.date || undefined, // Já no formato yyyy-MM-dd
                        }));
                    }
                }
            }
        } catch (e) {
            console.warn('[getFiles] Exceção na bridge:', e);
        }
    }

    // 3. Fallback: dados mock (somente quando bridge não está disponível)
    return fileSystem[path] || [];
}
