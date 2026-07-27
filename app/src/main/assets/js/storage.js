const BYTES_PER_GB = 1024 * 1024 * 1024;

let cachedStorageData = null;
let cachedAppsCount = null;

function hasBridge() {
    return typeof window.FileBridge !== 'undefined' && window.FileBridge !== null;
}

/**
 * fetchRealStorageData — obtém dados de armazenamento reais via FileBridge.
 * FileBridge.getStorageInfo() retorna JSON com:
 *   { internal: { total, free, used }, external: {...}, total: { total, free, used }, appsCount }
 */
function fetchRealStorageData() {
    try {
        if (!hasBridge()) {
            console.warn('[storage] ⚠️ Bridge não disponível');
            return null;
        }
        console.log('[storage] 🔵 Chamando FileBridge.getStorageInfo()...');
        const raw = window.FileBridge.getStorageInfo();
        if (!raw) {
            console.warn('[storage] ⚠️ getStorageInfo retornou null');
            return null;
        }
        console.log('[storage] 📦 Resposta bruta (200 chars):', raw.substring(0, 200));
        const info = JSON.parse(raw);
        if (info.error) {
            console.warn('[storage] ❌ getStorageInfo ERRO:', info.message);
            return null;
        }

        // Usar o objeto 'total' (combinado) se disponível
        const storage = info.total || info.external || info.internal;
        if (storage && typeof storage.total === 'number' && storage.total > 0) {
            console.log('[storage] ✅ Dados reais: total=' + storage.totalFormatted + ' livre=' + storage.freeFormatted);
            return {
                totalBytes: storage.total,
                usedBytes: storage.used,
                freeBytes: storage.free
            };
        }
        console.warn('[storage] ⚠️ Dados de storage inválidos:', storage);
    } catch (e) {
        console.warn('[storage] 💥 Exceção ao obter dados reais:', e.message);
    }
    return null;
}

/**
 * fetchRealAppsCount — obtém contagem real de apps via FileBridge.
 * FileBridge.getStorageInfo() já inclui 'appsCount'.
 */
function fetchRealAppsCount() {
    try {
        if (!hasBridge()) return null;
        const raw = window.FileBridge.getStorageInfo();
        if (!raw) return null;
        const info = JSON.parse(raw);
        if (info.error) return null;
        if (typeof info.appsCount === 'number') {
            console.log('[storage] ✅ Apps count real:', info.appsCount);
            return info.appsCount;
        }
    } catch (e) {
        console.warn('[storage] 💥 Exceção ao obter contagem de apps:', e.message);
    }
    return null;
}

export function formatStorage(bytes) {
    if (bytes >= BYTES_PER_GB) return (bytes / BYTES_PER_GB).toFixed(2).replace('.', ',') + ' GB';
    if (bytes >= 1024 * 1024) return (bytes / 1048576).toFixed(2).replace('.', ',') + ' MB';
    if (bytes >= 1024) return (bytes / 1024).toFixed(2).replace('.', ',') + ' KB';
    return bytes + ' B';
}

export function updateGaugeArc(percent) {
    const fill = document.getElementById('gauge-fill');
    if (!fill) return;
    const totalLength = 251;
    fill.setAttribute('stroke-dasharray', `${(percent / 100) * totalLength} ${totalLength}`);
}

export function updateAllStorageData() {
    // Dados reais do dispositivo
    if (!cachedStorageData) {
        cachedStorageData = fetchRealStorageData();
    }
    if (cachedAppsCount === null) {
        cachedAppsCount = fetchRealAppsCount();
    }

    // Se não conseguiu dados reais, usar fallbacks razoáveis
    const data = cachedStorageData || { totalBytes: 0, usedBytes: 0, freeBytes: 0 };
    const appsCount = cachedAppsCount || 0;

    document.getElementById('total-value').textContent = formatStorage(data.totalBytes);
    document.getElementById('usado-value').textContent = formatStorage(data.usedBytes);
    document.getElementById('livre-value').textContent = formatStorage(data.freeBytes);
    document.getElementById('disponivel-value').textContent = formatStorage(data.freeBytes);
    document.getElementById('apps-count').textContent = appsCount;

    const percent = data.totalBytes
        ? Math.round((data.usedBytes / data.totalBytes) * 100)
        : 0;
    updateGaugeArc(percent);
}

/**
 * refreshStorage — força atualização dos dados de armazenamento.
 * Chamado após operações que alteram o filesystem.
 */
export function refreshStorage() {
    cachedStorageData = null;
    cachedAppsCount = null;
    updateAllStorageData();
}
