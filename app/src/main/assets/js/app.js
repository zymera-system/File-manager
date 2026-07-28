import { renderFiles, navigateTo, getCurrentPath } from './navigation.js';
import { resolveVirtualPath } from './getFiles.js';
import { updateUIForPath } from './ui.js';
import { toggleFavorite, favorites } from './favorites.js';
import { sortFiles } from './sort.js';
import { openCreateModal, closeCreateModal, createItem, handleTypeChange, toggleExtDropdown } from './modal.js';
import { loadThemePreference, toggleTheme, toggleExtensions, getShowExtensions } from './theme.js';
import { toggleMenu, analyzeDevice, refreshFiles, openSettings, setupMenuClose, showSortMenu, refreshApps, sortApps, clearCache, showAbout, restoreAll, setupMenu, closeMenu } from './menu.js';
import { navigateToTab, getCurrentPage, onTabChange } from './tabs.js';
import { updateAllStorageData } from './storage.js';
import { deleteItemDirect } from './delete.js';
import { getFiles } from './getFiles.js';
import { fileSystem } from './fileSystem.js';
import { moveToTrash, getTrashItems, restoreFromTrash, permanentDelete, getDaysRemaining, loadTrash } from './trash.js';
import {
    renderAppsPage,
    loadRealApps,
    switchTab,
    appClick,
    appLongPressStart,
    appLongPressEnd,
    toggleFab,
    backupApk,
    uninstallApp,
    confirmUninstall,
    shareApp,
    shareVia,
    closeModal as closeAppsModal,
    clearAppSelection,
    filterApps,
    openSortAppsModal,
    closeSortAppsModal,
    openSortByModal,
    confirmSortBy,
    applySortApps
} from './apps.js';
import {
    isSelectionMode,
    longPressStart,
    longPressEnd,
    wasLongPressTriggered,
    resetLongPressFlag,
    handleFileClickForSelection,
    clearSelection,
    toggleMoreMenu,
    setupSelectionListeners,
    getSelectedFiles,
    enterSelectionMode,
    deselectAll,
    isClipboardActive,
    getClipboardMode,
    enterCopyMode,
    enterMoveMode,
    cancelClipboard,
    pasteFiles,
    moveFiles,
    setClipboardPathGetter,
    setClipboardFilesGetter,
    setClipboardFilesMapGetter,
    hideClipboardBar,
    showClipboardBarIfActive,
    hasNativeBridge
} from './selection.js';
import {
    showLoading,
    updateLoading,
    updateProgress,
    updateFile,
    hideLoading,
    isLoading,
    pauseLoading,
    resumeLoading,
    showReversalResult,
    hideReversalResult,
    getReversalChoice,
    showPopup,
    hidePopup,
    showScan,
    updateScan,
    hideScan,
    showConfirmation,
    showSurePopup,
    shouldSkipConfirmDelete
} from './loading.js';
import { deleteWithProgress, copyWithProgress, moveWithProgress, getActiveTasks, hasActiveTasks } from './progress.js';

function buildPath(base, name) {
    if (!name) return base || '/';
    const sep = base === '/' ? '' : '/';
    return (base === '/' ? '/' : base) + sep + name;
}

function escapeAttr(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/'/g, '&#39;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
import {
    startOperation,
    trackFileProcessed,
    trackKeyCreated,
    snapshotBeforeMove,
    snapshotArray,
    getProcessedCount,
    getProcessed,
    getOperationType,
    isCancelled,
    hasOperation,
    cancelOperation,
    revertOperation,
    clearLog
} from './cancellation.js';
import { showToast } from './toast.js';
import { initErrorHandler } from './errorHandler.js';
import { uploadFile, uploadMultiple } from './upload.js';

// ========================================
// BRIDGE: Resolver caminhos virtuais → reais
// ========================================
// Expõe resolveVirtualPath como global para que delete.js, modal.js,
// selection.js etc. possam traduzir caminhos da UI para caminhos reais.
window.fmGetDevicePath = resolveVirtualPath;

// ========================================
// BRIDGE: Helper de chamadas seguras ao FileBridge
// ========================================
/**
 * Executa uma chamada ao FileBridge de forma segura.
 * @param {string} method — nome do método
 * @param  {...any} args — argumentos
 * @returns {object|null} resultado parseado ou null em caso de erro
 */
window.bridgeCall = function(method, ...args) {
    try {
        if (!window.FileBridge || typeof window.FileBridge[method] !== 'function') {
            console.warn(`[bridge] Método "${method}" não disponível`);
            return { success: false, error: "Método não disponível: " + method };
        }
        const raw = window.FileBridge[method](...args);
        if (raw === null || raw === undefined) return { success: true };
        try {
            return JSON.parse(raw);
        } catch (e) {
            return { raw: raw };
        }
    } catch (e) {
        console.error(`[bridge] Erro em ${method}:`, e.message);
        return { success: false, error: e.message };
    }
};

setClipboardPathGetter(getCurrentPath);
setClipboardFilesGetter(getFiles);
setClipboardFilesMapGetter(() => fileSystem);

// Expor funções globais para onclick inline no HTML
window.fmNavigateTo = navigateTo;
window.fmToggleFavorite = (name) => { toggleFavorite(name); renderFiles(); };
window.fmDeleteItem = (name) => {
    showDeleteModal([name]);
};
window.fmOpenCreateModal = () => openCreateModal(getCurrentPath());
window.fmCreateItem = () => {
    if (createItem(getCurrentPath())) {
        renderFiles();
        showToast('Item criado', 'success');
    }
};
window.fmCloseCreateModal = closeCreateModal;
window.fmHandleTypeChange = handleTypeChange;
window.fmToggleExtDropdown = toggleExtDropdown;
window.fmToggleTheme = toggleTheme;
window.fmToggleExtensions = toggleExtensions;
window.fmToggleMenu = toggleMenu;
window.fmAnalyzeDevice = analyzeDevice;
window.fmRefreshFiles = () => refreshFiles(renderFiles);
window.fmOpenSettings = () => openSettings(navigateToTab);
window.fmSortFiles = (by) => { sortFiles(by); renderFiles(); };
window.fmRenderFiles = renderFiles;

let searchDebounceTimer = null;
window.fmDebouncedRender = function() {
    if (searchDebounceTimer) clearTimeout(searchDebounceTimer);
    searchDebounceTimer = setTimeout(() => {
        renderFiles();
        searchDebounceTimer = null;
    }, 250);
};

// Menu actions (new)
window.fmShowSortMenu = showSortMenu;
window.fmRefreshApps = refreshApps;
window.fmSortApps = sortApps;
window.fmClearCache = clearCache;
window.fmShowAbout = showAbout;
window.fmRestoreAll = restoreAll;

// ========================================
// DIAGNÓSTIC: Abrir painel de logs de erro
// ========================================
window.fmOpenErrorLog = function() {
    closeMenu();
    window.location.href = 'error-log.html';
};

// Selection bar
window.fmLongPressStart = longPressStart;
window.fmLongPressEnd = longPressEnd;
window.fmClearSelection = clearSelection;
window.fmToggleMoreMenu = toggleMoreMenu;

// Apps page
window.fmOpenApps = function() {
    closeMenu();
    loadRealApps();
    navigateToTab('apps');
    renderAppsPage();
    history.pushState({ page: 'apps' }, '');
};
window.fmSwitchTab = switchTab;
window.fmAppClick = function(name) {
    if (wasLongPressTriggered()) {
        resetLongPressFlag();
        return;
    }
    appClick(name);
};
window.fmAppLongPressStart = appLongPressStart;
window.fmAppLongPressEnd = appLongPressEnd;
window.fmToggleFab = toggleFab;
window.fmBackupApk = backupApk;
window.fmUninstallApp = uninstallApp;
window.fmConfirmUninstall = confirmUninstall;
window.fmShareApp = shareApp;
window.fmShareVia = shareVia;
window.fmCloseAppsModal = closeAppsModal;
window.fmClearAppSelection = clearAppSelection;
window.fmFilterApps = filterApps;
window.fmOpenSortAppsModal = openSortAppsModal;
window.fmCloseSortAppsModal = closeSortAppsModal;
window.fmOpenSortByModal = openSortByModal;
window.fmConfirmSortBy = function() {
    const appsPageActive = document.querySelector('[data-page="apps"]').classList.contains('active');
    if (appsPageActive) {
        confirmSortBy();
        return;
    }
    const checked = document.querySelector('input[name="sortBy"]:checked');
    if (checked) {
        const map = {
            'name-asc': 'name', 'name-desc': 'name',
            'date-asc': 'date', 'date-desc': 'date',
            'size-asc': 'size', 'size-desc': 'size'
        };
        const field = map[checked.value] || 'name';
        window.fmSortFiles(field);
    }
    document.getElementById('sortByModal').classList.remove('open');
};
window.fmApplySortApps = applySortApps;

window.fmResetPreferences = function() {
    try {
        localStorage.removeItem('fm_dark_theme');
        localStorage.removeItem('fm_show_extensions');
        localStorage.removeItem('fm_skip_confirm_delete');
    } catch (e) { /* ignore */ }
    showToast('Preferências redefinidas', 'success');
    setTimeout(() => location.reload(), 600);
};

// Loading system
window.fmShowLoading = showLoading;
window.fmUpdateLoading = updateLoading;
window.fmUpdateProgress = updateProgress;
window.fmUpdateFile = updateFile;
window.fmHideLoading = hideLoading;
window.fmIsLoading = isLoading;
window.fmCancelLoading = pauseLoading;
window.fmResumeOperation = resumeLoading;
window.fmConfirmCancel = function() {
    const modal = document.getElementById('cancelConfirmModal');
    if (modal) modal.classList.remove('open');
    cancelOperation();
    resumeLoading();
};
window.fmShowReversalResult = showReversalResult;
window.fmHideReversalResult = hideReversalResult;
window.fmUploadFile = uploadFile;
window.fmUploadMultiple = uploadMultiple;
window.fmConfirmReversal = function() {
    const choice = getReversalChoice();
    if (choice === 'revert') {
        const type = getOperationType();
        const processed = getProcessed();
        if (type && processed.length > 0) {
            revertOperation(type, processed);
        }
    }
    hideReversalResult();
};

// Cancellation system
window.fmStartOperation = startOperation;
window.fmTrackFile = trackFileProcessed;
window.fmTrackKey = trackKeyCreated;
window.fmSnapshotBeforeMove = snapshotBeforeMove;
window.fmSnapshotArray = snapshotArray;
window.fmGetProcessedCount = getProcessedCount;
window.fmGetProcessed = getProcessed;
window.fmIsCancelled = isCancelled;
window.fmHasOperation = hasOperation;
window.fmCancelOperation = cancelOperation;
window.fmRevertOperation = revertOperation;
window.fmClearLog = clearLog;

// Clipboard bar
window.fmCancelClipboard = cancelClipboard;
window.fmConfirmCancelClipboard = function(confirm) {
    document.getElementById('cancelClipboardModal').classList.remove('open');
    if (confirm) cancelClipboard();
};
window.fmClipboardConfirm = async function() {
    const path = getCurrentPath();
    if (!isClipboardActive()) return;
    if (path === '/') {
        showToast('Navegue até uma pasta para colar', 'warning');
        return;
    }
    let result = false;
    let modeLabel = '';
    if (getClipboardMode() === 'copy') {
        result = await pasteFiles(path);
        modeLabel = 'copiado(s)';
    } else if (getClipboardMode() === 'move') {
        result = await moveFiles(path);
        modeLabel = 'movido(s)';
    }
    renderFiles();
    if (result) {
        showToast('Arquivo(s) ' + modeLabel + ' com sucesso', 'success');
    }
};

/**
 * Detecta categoria MIME do arquivo pela extensão.
 * Retorna 'audio', 'video', 'image', 'zip', 'text', 'apk', 'pdf' ou 'other'.
 */
function getFileCategory(fileName) {
    const ext = fileName.split('.').pop().toLowerCase();
    if (['mp3','wav','ogg','flac','aac','m4a','wma','opus','mid','midi'].includes(ext)) return 'audio';
    if (['mp4','mkv','avi','mov','wmv','flv','webm','3gp','ts','m4v'].includes(ext)) return 'video';
    if (['jpg','jpeg','png','gif','bmp','webp','svg','heic','heif','tiff','ico'].includes(ext)) return 'image';
    if (['zip','7z','rar','tar','gz','bz2','xz','tgz','zst'].includes(ext)) return 'zip';
    if (['txt','md','json','xml','html','htm','css','js','ts','java','kt','py','c','cpp','h','sh','bat','log','ini','cfg','yml','yaml','toml','env','gitignore'].includes(ext)) return 'text';
    if (['pdf'].includes(ext)) return 'pdf';
    if (['apk','aab'].includes(ext)) return 'apk';
    return 'other';
}

window.fmFileClick = function(fileName) {
    if (wasLongPressTriggered()) {
        resetLongPressFlag();
        return;
    }

    const grid = document.getElementById('fileGrid');
    const itemsJSON = grid.dataset.items;
    const allFiles = itemsJSON ? JSON.parse(itemsJSON) : [];
    const currentPath = getCurrentPath();

    if (!isSelectionMode()) {
        const item = allFiles.find(f => f.name === fileName);
        if (!item) return;

        // Pasta → navegar
        if (item.type === 'folder') {
            const targetPath = currentPath === '/'
                ? '/' + fileName
                : currentPath + '/' + fileName;
            navigateTo(targetPath);
            return;
        }

        // Arquivo → abrir com player nativo (se tiver bridge)
        if (typeof window.FileBridge !== 'undefined' && window.FileBridge !== null) {
            const fullPath = currentPath === '/'
                ? '/' + fileName
                : currentPath + '/' + fileName;
            const devicePath = window.fmGetDevicePath
                ? window.fmGetDevicePath(fullPath)
                : fullPath;
            const cat = getFileCategory(fileName);

            // Áudio, vídeo, imagem → abrir com player nativo
            if (cat === 'audio' || cat === 'video' || cat === 'image') {
                try {
                    const result = window.FileBridge.openMedia(devicePath);
                    const data = result ? JSON.parse(result) : null;
                    if (data && data.error) {
                        showToast('Nenhum aplicativo encontrado para abrir este arquivo', 'error');
                    }
                } catch (e) {
                    console.warn('[app] Erro ao abrir mídia:', e);
                    showToast('Erro ao abrir arquivo', 'error');
                }
                return;
            }

            // APK → instalar
            if (cat === 'apk') {
                window.FileBridge.openMedia(devicePath);
                return;
            }

            // Outros tipos → tentar abrir com app padrão
            try {
                window.FileBridge.openMedia(devicePath);
            } catch (e) {
                showToast('Nenhum aplicativo encontrado para abrir este arquivo', 'error');
            }
        }
        return;
    }

    const handled = handleFileClickForSelection(fileName, allFiles);
    if (handled) return;
};

window.fmSelectionAction = async function(action) {
    const selected = getSelectedFiles();
    if (selected.length === 0) return;

    switch (action) {
        case 'copy':
            await enterCopyMode();
            break;
        case 'move':
            await enterMoveMode();
            break;
        case 'rename':
            if (selected.length === 1) {
                window.fmOpenRenameModal(selected[0]);
            } else {
                showToast('Selecione apenas um item para renomear', 'warning');
            }
            break;
        case 'delete':
            showDeleteModal(selected);
            break;
        case 'share':
            if (hasNativeBridge()) {
                try {
                    const currentPath = getCurrentPath();
                    const paths = selected.map(name => {
                        const full = currentPath === '/' ? '/' + name : currentPath + '/' + name;
                        return window.fmGetDevicePath ? window.fmGetDevicePath(full) : full;
                    });
                    if (selected.length === 1) {
                        window.FileBridge.shareFile(paths[0]);
                    } else {
                        window.FileBridge.shareMultiple(JSON.stringify(paths));
                    }
                } catch (e) { showToast('Erro ao compartilhar', 'error'); }
            } else {
                showToast(`Compartilhando: ${selected.join(', ')}`, 'info');
            }
            break;
        case 'favorite':
            selected.forEach(name => toggleFavorite(name));
            clearSelection();
            renderFiles();
            break;
        case 'pin':
            if (hasNativeBridge()) {
                try {
                    const curPath = getCurrentPath();
                    selected.forEach(name => {
                        const filePath = curPath === '/' ? '/' + name : curPath + '/' + name;
                        window.FileBridge.pinFile(filePath, name);
                    });
                    showToast(`${selected.length} arquivo(s) fixado(s)`, 'success');
                } catch (e) { showToast('Erro ao fixar arquivo', 'error'); }
            } else {
                showToast(`Fixando: ${selected.join(', ')}`, 'info');
            }
            break;
        case 'compress':
            if (hasNativeBridge()) {
                try {
                    const curPath = getCurrentPath();
                    const filesJson = JSON.stringify(selected.map(name =>
                        curPath === '/' ? '/' + name : curPath + '/' + name
                    ));
                    const outputZip = (curPath === '/' ? '/' : curPath + '/') + 'compactado_' + Date.now() + '.zip';
                    window.FileBridge.compressZip(filesJson, outputZip);
                    showToast('Compactando arquivos...', 'info');
                    setTimeout(() => { renderFiles(); }, 1500);
                } catch (e) { showToast('Erro ao compactar', 'error'); }
            } else {
                showToast(`Compactando: ${selected.join(', ')}`, 'info');
            }
            break;
        case 'extract':
            if (hasNativeBridge()) {
                try {
                    const curPath = getCurrentPath();
                    selected.forEach(name => {
                        const filePath = curPath === '/' ? '/' + name : curPath + '/' + name;
                        const destDir = curPath === '/' ? '/' + name.replace(/\.[^.]+$/, '') : curPath + '/' + name.replace(/\.[^.]+$/, '');
                        window.FileBridge.extractZip(filePath, destDir);
                    });
                    showToast('Extraindo arquivos...', 'info');
                    setTimeout(() => { renderFiles(); }, 1500);
                } catch (e) { showToast('Erro ao extrair', 'error'); }
            } else {
                showToast(`Extraindo: ${selected.join(', ')}`, 'info');
            }
            break;
        case 'properties':
            if (hasNativeBridge()) {
                try {
                    const curPath = getCurrentPath();
                    const fileName = selected[0];
                    const filePath = curPath === '/' ? '/' + fileName : curPath + '/' + fileName;
                    showProperties(filePath, fileName);
                } catch (e) { showToast('Erro ao obter propriedades', 'error'); }
            } else {
                showToast(`Propriedades de: ${selected.join(', ')}`, 'info');
            }
            break;
        case 'shortcut':
            if (hasNativeBridge()) {
                try {
                    const curPath = getCurrentPath();
                    selected.forEach(name => {
                        const filePath = curPath === '/' ? '/' + name : curPath + '/' + name;
                        window.FileBridge.createShortcut(filePath, name);
                    });
                    showToast(`Atalho(s) criado(s)`, 'success');
                } catch (e) { showToast('Erro ao criar atalho', 'error'); }
            } else {
                showToast(`Criando atalho: ${selected.join(', ')}`, 'info');
            }
            break;
        case 'duplicate':
            if (hasNativeBridge()) {
                try {
                    const curPath = getCurrentPath();
                    selected.forEach(name => {
                        const src = curPath === '/' ? '/' + name : curPath + '/' + name;
                        const baseName = name.replace(/\.[^.]+$/, '');
                        const ext = name.includes('.') ? '.' + name.split('.').pop() : '';
                        let dest = (curPath === '/' ? '/' : curPath + '/') + baseName + ' (cópia)' + ext;
                        let counter = 1;
                        while (window.FileBridge.fileExists(dest)) {
                            dest = (curPath === '/' ? '/' : curPath + '/') + baseName + ' (cópia ' + (counter + 1) + ')' + ext;
                            counter++;
                        }
                        window.FileBridge.copyFile(src, dest);
                    });
                    showToast('Arquivos duplicados', 'success');
                    clearSelection();
                    renderFiles();
                } catch (e) { showToast('Erro ao duplicar', 'error'); }
            } else {
                showToast(`Duplicando: ${selected.join(', ')}`, 'info');
            }
            break;
    }
};

// Rename state
let renameTarget = null;
let renameTargetPath = null;

window.fmOpenRenameModal = function(name) {
    const currentPath = getCurrentPath();
    if (currentPath === '/') return;
    renameTarget = name;
    renameTargetPath = currentPath;
    document.getElementById('renameInput').value = name;
    document.getElementById('renameModalOverlay').classList.add('open');
    setTimeout(() => document.getElementById('renameInput').focus(), 100);
};

window.fmCloseRenameModal = function() {
    document.getElementById('renameModalOverlay').classList.remove('open');
    renameTarget = null;
    renameTargetPath = null;
};

window.fmConfirmRename = function() {
    const newName = document.getElementById('renameInput').value.trim();
    if (!renameTarget || !renameTargetPath) {
        window.fmCloseRenameModal();
        return;
    }
    if (!newName) {
        showToast('Digite um nome', 'error');
        return;
    }
    if (/[\\/:*?"<>|]/.test(newName)) {
        showToast('Nome contém caracteres inválidos', 'error');
        return;
    }
    const current = getFiles(renameTargetPath);
    if (current.some(f => f.name === newName && f.name !== renameTarget)) {
        showToast('Já existe um arquivo com esse nome', 'error');
        return;
    }

    const hasBridge = typeof window.FileBridge !== 'undefined' && window.FileBridge !== null;
    if (hasBridge) {
        try {
            const fullPath = renameTargetPath === '/' ? '/' + renameTarget : renameTargetPath + '/' + renameTarget;
            const devicePath = window.fmGetDevicePath ? window.fmGetDevicePath(fullPath) : fullPath;
            const raw = window.FileBridge.renameItem(devicePath, newName);
            const result = JSON.parse(raw);
            if (result.error) {
                showToast(result.error, 'error');
                return;
            }
        } catch (e) {
            showToast('Erro ao renomear: ' + e.message, 'error');
            return;
        }
    }

    const item = current.find(f => f.name === renameTarget);
    if (item) {
        const wasFolder = item.type === 'folder';
        item.name = newName;
        if (wasFolder) {
            const oldPath = renameTargetPath + '/' + renameTarget;
            const newPath = renameTargetPath + '/' + newName;
            if (fileSystem[oldPath]) {
                fileSystem[newPath] = fileSystem[oldPath];
                delete fileSystem[oldPath];
            }
        }
    }
    window.fmCloseRenameModal();
    renderFiles();
    showToast('Renomeado com sucesso', 'success');
};

// Delete modal
let deleteTargets = [];

function showDeleteModal(selected) {
    const grid = document.getElementById('fileGrid');
    const itemsJSON = grid.dataset.items;
    const allFiles = itemsJSON ? JSON.parse(itemsJSON) : [];

    let folders = 0;
    let files = 0;
    selected.forEach(name => {
        const item = allFiles.find(f => f.name === name);
        if (item) {
            if (item.type === 'folder') folders++;
            else files++;
        }
    });

    document.getElementById('deleteFolderCount').textContent = folders;
    document.getElementById('deleteFileCount').textContent = files;
    deleteTargets = selected;
    document.getElementById('deleteModalOverlay').classList.add('open');
}

window.fmCloseDeleteModal = function() {
    document.getElementById('deleteModalOverlay').classList.remove('open');
    deleteTargets = [];
};

window.fmConfirmDelete = async function() {
    if (hasOperation()) return;
    const grid = document.getElementById('fileGrid');
    const itemsJSON = grid.dataset.items;
    const allFiles = itemsJSON ? JSON.parse(itemsJSON) : [];
    const currentPath = getCurrentPath();

    const itemsToTrash = deleteTargets.map(name => {
        const item = allFiles.find(f => f.name === name);
        return item || { name, type: 'file' };
    });

    const total = itemsToTrash.length;
    if (total === 0) {
        document.getElementById('deleteModalOverlay').classList.remove('open');
        deleteTargets = [];
        return;
    }

    document.getElementById('deleteModalOverlay').classList.remove('open');

    // Calcular resumo
    let folderCount = 0;
    let fileCount = 0;
    let totalBytes = 0;
    itemsToTrash.forEach(item => {
        if (item.type === 'folder') folderCount++;
        else fileCount++;
        if (item.size) totalBytes += Number(item.size);
    });
    const totalSize = totalBytes > 0
        ? (totalBytes / 1024 / 1024).toFixed(1) + ' MB'
        : total + ' itens';

    // ===== FASE 1: SCAN =====
    showScan({ firstPath: buildPath(currentPath, itemsToTrash[0]?.name) });

    let scanCancelled = false;
    let scanHidden = false;

    document.getElementById('scanCancelBtn').onclick = () => {
        scanCancelled = true;
        hideScan();
    };
    document.getElementById('scanOcultarBtn').onclick = () => {
        scanHidden = true;
        hideScan();
    };

    for (const item of itemsToTrash) {
        if (scanCancelled) {
            deleteTargets = [];
            clearSelection();
            return;
        }
        updateScan(buildPath(currentPath, item.name));
        await new Promise(r => setTimeout(r, 30));
    }

    if (scanCancelled) {
        deleteTargets = [];
        clearSelection();
        return;
    }

    if (!scanHidden) hideScan();

    // ===== FASE 2: CONFIRMAÇÃO =====
    let isPermanentDelete = false;
    if (!shouldSkipConfirmDelete()) {
        const confirmResult = await showConfirmation({
            folderCount,
            fileCount,
            totalSize,
            showPermanent: true
        });

        if (!confirmResult.confirmed) {
            deleteTargets = [];
            clearSelection();
            renderFiles();
            return;
        }

        isPermanentDelete = confirmResult.permanent;

        // ===== POPUP "TEM CERTEZA?" =====
        const sureResult = await showSurePopup();

        if (!sureResult.confirmed) {
            deleteTargets = [];
            clearSelection();
            renderFiles();
            return;
        }
    }

    // ===== FASE 3: OPERAÇÃO =====
    startOperation('delete', currentPath, null);
    for (const item of itemsToTrash) {
        snapshotBeforeMove(currentPath, item);
    }

    // Para exclusão permanente com bridge, usar asyncDelete em batch
    if (isPermanentDelete && hasNativeBridge() && itemsToTrash.length > 0) {
        document.getElementById('deleteModalOverlay').classList.remove('open');

        // Excluir cada item via asyncDelete (background thread c/ progresso)
        const deletePromises = itemsToTrash.map(item => {
            const fullPath = buildPath(currentPath, item.name);
            const devicePath = window.fmGetDevicePath
                ? window.fmGetDevicePath(fullPath)
                : fullPath;
            return deleteWithProgress(devicePath);
        });

        // Aguardar todas as exclusões em background
        Promise.allSettled(deletePromises).then(() => {
            deleteTargets = [];
            clearSelection();
            renderFiles();
            showToast(itemsToTrash.length + ' arquivo(s) excluído(s) permanentemente', 'success');
        });

        return;
    }

    showLoading({
        icon: 'delete',
        title: 'Excluindo arquivos',
        current: 0,
        total: total,
        percentage: 0,
        cancellable: true,
        onCancel: () => { pauseLoading(); }
    });

    let deleted = 0;

    for (let i = 0; i < itemsToTrash.length; i++) {
        await new Promise(r => setTimeout(r, 0));

        if (isCancelled()) {
            hideLoading();
            const processed = cancelOperation();
            if (processed.length > 0) {
                const reverted = revertOperation('delete', processed);
                showReversalResult(reverted, 'delete');
            }
            deleteTargets = [];
            clearSelection();
            renderFiles();
            return;
        }

        const item = itemsToTrash[i];
        if (isPermanentDelete) {
            deleteItemDirect(item.name, currentPath);
        } else {
            if (getFiles(currentPath).some(f => f.name === item.name)) {
                moveToTrash([item], currentPath);
            }
        }
        trackFileProcessed(item.name, currentPath, null, 'deleted');
        deleted++;

        updateProgress(Math.round(((i + 1) / total) * 100), i + 1, total);
        updateFile(item.name, buildPath(currentPath, item.name), null, item.size);
    }

    hideLoading();
    clearLog();
    deleteTargets = [];
    clearSelection();
    if (isClipboardActive()) cancelClipboard();
    renderFiles();
    if (isPermanentDelete) {
        showToast(deleted + ' arquivo(s) excluído(s) permanentemente', 'success');
    } else {
        showToast(deleted + ' arquivo(s) movido(s) para lixeira', 'success');
    }
};

// Trash functions
function renderTrashPage() {
    const items = getTrashItems();
    const countEl = document.getElementById('trashCount');
    const listEl = document.getElementById('trashList');
    const actionsEl = document.getElementById('trashActions');

    if (!countEl || !listEl || !actionsEl) return;

    countEl.textContent = `${items.length} item(ns)`;

    if (items.length === 0) {
        listEl.innerHTML = '<div class="trash-empty">🗑️ Lixeira vazia</div>';
        actionsEl.style.display = 'none';
        return;
    }

    actionsEl.style.display = 'block';
    listEl.innerHTML = items.map(item => {
        const icon = item.type === 'folder' ? '📁' : '📄';
        const days = getDaysRemaining(item);
        const dateStr = new Date(item.deletedAt).toLocaleDateString('pt-BR');
        return `
            <div class="trash-item" data-name="${encodeURIComponent(item.name)}" data-deleted-at="${item.deletedAt}">
                <div class="trash-item-icon">${icon}</div>
                <div class="trash-item-info">
                    <div class="trash-item-name">${escapeAttr(item.name)}</div>
                    <div class="trash-item-meta">Excluído em ${dateStr} • ${days} dia(s) restante(s)</div>
                </div>
                <div class="trash-item-actions">
                    <button class="trash-restore-btn" title="Restaurar">↩️</button>
                    <button class="trash-delete-btn" title="Excluir permanentemente">🗑️</button>
                </div>
            </div>
        `;
    }).join('');
}

window.fmRestoreFromTrash = async function(name, deletedAt) {
    const items = getTrashItems();
    const item = items.find(t => t.name === name && t.deletedAt === deletedAt);
    if (!item) return;

    showLoading({
        icon: 'restore',
        title: 'Restaurando arquivo',
        currentFile: item.name,
        origin: 'Lixeira',
        destination: item.originalPath,
        percentage: 50,
        cancellable: false
    });

    // Checkpoint
    await new Promise(r => setTimeout(r, 300));

    const result = restoreFromTrash(item);
    if (result) {
        const destFiles = getFiles(result.originalPath);
        if (result.type === 'folder') {
            if (!fileSystem[result.originalPath]) fileSystem[result.originalPath] = [];
        }
        destFiles.push({ name: result.name, type: result.type });
    }

    updateProgress(100, 1, 1);
    await new Promise(r => setTimeout(r, 200));
    hideLoading();
    renderTrashPage();
    showToast('Arquivo restaurado', 'success');
};

window.fmPermanentDelete = async function(name, deletedAt) {
    const items = getTrashItems();
    const item = items.find(t => t.name === name && t.deletedAt === deletedAt);
    if (!item) return;

    showLoading({
        icon: 'delete',
        title: 'Excluindo permanentemente',
        currentFile: item.name,
        percentage: 50,
        cancellable: false
    });

    await new Promise(r => setTimeout(r, 300));

    permanentDelete(item);

    updateProgress(100, 1, 1);
    await new Promise(r => setTimeout(r, 200));
    hideLoading();
    renderTrashPage();
};

window.fmClearTrash = async function() {
    const items = getTrashItems();
    const total = items.length;
    if (total === 0) return;

    startOperation('delete', '/Lixeira', null);

    showLoading({
        icon: 'delete',
        title: 'Limpando lixeira',
        current: 0,
        total: total,
        percentage: 0,
        cancellable: true,
        onCancel: () => { pauseLoading(); }
    });

    let cleared = 0;

    for (let i = 0; i < items.length; i++) {
        await new Promise(r => setTimeout(r, 0));

        if (isCancelled()) {
            hideLoading();
            cancelOperation();
            renderTrashPage();
            return;
        }

        permanentDelete(items[i]);
        trackFileProcessed(items[i].name, '/Lixeira', null, 'deleted');
        cleared++;

        updateProgress(Math.round(((i + 1) / total) * 100), i + 1, total);
        updateFile(items[i].name, null, null, null);
    }

    clearLog();
    hideLoading();
    renderTrashPage();
    showToast('Lixeira limpa', 'success');
};

window.fmOpenTrash = function() {
    closeMenu();
    renderTrashPage();
    navigateToTab('trash');
    history.pushState({ page: 'trash' }, '');
};

// Botão voltar estilo Android
let lastBackPress = 0;

window.fmGoBack = function() {
    // 0. Se loading ativo, ignorar
    if (isLoading()) return;

    // 0.2. Se popupOverlay aberto (scan/confirm/sure), fechar
    const popupOverlay = document.getElementById('popupOverlay');
    if (popupOverlay && popupOverlay.classList.contains('open')) {
        hidePopup();
        return;
    }

    // 0.5. Se modal de cancelamento/reversão aberto, fechar
    const cancelModal = document.getElementById('cancelConfirmModal');
    if (cancelModal && cancelModal.classList.contains('open')) {
        cancelModal.classList.remove('open');
        return;
    }
    const reversalModal = document.getElementById('reversalResultModal');
    if (reversalModal && reversalModal.classList.contains('open')) {
        hideReversalResult();
        return;
    }

    // 1. Se modal de apps aberto, fecha
    if (document.querySelector('#uninstallModal.open') || document.querySelector('#shareModal.open')) {
        closeAppsModal('uninstall');
        closeAppsModal('share');
        return;
    }
    // 2. Se FAB aberto, fecha
    if (document.getElementById('appsFab') && document.getElementById('appsFab').classList.contains('open')) {
        toggleFab();
        return;
    }
    // 3. Se seleção de apps ativa, limpa
    if (document.getElementById('appsSelectionBar') && document.getElementById('appsSelectionBar').classList.contains('active')) {
        clearAppSelection();
        return;
    }
    // 4. Se na página apps, volta para files
    if (document.querySelector('[data-page="apps"]').classList.contains('active')) {
        document.querySelector('[data-page="apps"]').classList.remove('active');
        document.querySelector('[data-page="files"]').classList.add('active');
        history.pushState({ page: 'files' }, '');
        return;
    }
    // 4.5. Se na página settings ou trash, usar history.back() para voltar à página anterior
    if (document.querySelector('[data-page="settings"]').classList.contains('active') ||
        document.querySelector('[data-page="trash"]').classList.contains('active')) {
        history.back();
        return;
    }
    // 5. Se clipboard ativo
    if (isClipboardActive()) {
        const mode = getClipboardMode();
        document.getElementById('cancelClipboardMode').textContent = mode === 'copy' ? 'cópia' : 'movimentação';
        document.getElementById('cancelClipboardModal').classList.add('open');
        return;
    }
    // 6. Se seleção de arquivos ativa
    if (isSelectionMode()) {
        clearSelection();
        return;
    }
    navigateToTab('files');
};

window.addEventListener('popstate', function(event) {
    if (isLoading()) return;

    const popupOverlay = document.getElementById('popupOverlay');
    if (popupOverlay && popupOverlay.classList.contains('open')) {
        hidePopup();
        return;
    }

    // Estado do historico: qual pagina o usuario veio
    const state = event.state;
    const targetPage = state && state.page ? state.page : null;

    // Verificar estado interno primeiro (modais, clipboard, selecao)
    // Esses estados tem prioridade sobre navegacao por historico

    // Se na pagina apps e modal/FAB/selecao ativo, fechar antes de navegar
    if (document.querySelector('[data-page="apps"]').classList.contains('active')) {
        if (document.querySelector('#uninstallModal.open') || document.querySelector('#shareModal.open')) {
            closeAppsModal('uninstall');
            closeAppsModal('share');
            return;
        }
        if (document.getElementById('appsFab') && document.getElementById('appsFab').classList.contains('open')) {
            toggleFab();
            return;
        }
        if (document.getElementById('appsSelectionBar') && document.getElementById('appsSelectionBar').classList.contains('active')) {
            clearAppSelection();
            return;
        }
    }

    if (isClipboardActive()) {
        const mode = getClipboardMode();
        document.getElementById('cancelClipboardMode').textContent = mode === 'copy' ? 'cópia' : 'movimentação';
        document.getElementById('cancelClipboardModal').classList.add('open');
        return;
    }

    if (isSelectionMode()) {
        clearSelection();
        return;
    }

    // Navegacao baseada no event.state
    // targetPage indica para onde o usuario deve voltar
    if (targetPage === 'apps') {
        navigateToTab('apps');
        return;
    }

    if (targetPage === 'trash') {
        renderTrashPage();
        navigateToTab('trash');
        return;
    }

    if (targetPage === 'settings') {
        navigateToTab('settings');
        return;
    }

    // Fallback: usar logica anterior baseada em currentPage e path
    const now = Date.now();
    const page = getCurrentPage();
    const path = getCurrentPath();

    if (now - lastBackPress < 2000) {
        lastBackPress = now;
        if (page !== 'files') {
            navigateToTab('files');
            clearSelection();
        } else if (path !== '/') {
            const parent = path.substring(0, path.lastIndexOf('/', path.length - 2) + 1) || '/';
            navigateTo(parent);
        }
        return;
    }
    lastBackPress = now;

    if (page !== 'files') {
        navigateToTab('files');
        clearSelection();
    } else if (path !== '/') {
        const parent = path.substring(0, path.lastIndexOf('/', path.length - 2) + 1) || '/';
        navigateTo(parent);
    } else {
        showToast('Pressione voltar novamente para sair', 'info');
    }
});

// ===== Propriedades detalhadas =====
function showProperties(path, name) {
    try {
        const devicePath = window.fmGetDevicePath ? window.fmGetDevicePath(path) : path;
        const raw = window.FileBridge.getFileInfo(devicePath);
        if (!raw) { showToast('Não foi possível obter informações', 'error'); return; }
        const info = JSON.parse(raw);
        if (info.error) { showToast(info.error, 'error'); return; }

        const title = document.getElementById('propModalTitle');
        const content = document.getElementById('propModalContent');
        if (!title || !content) return;

        title.textContent = info.name || name;
        const rows = [
            ['Tipo', info.isDirectory ? 'Pasta' : 'Arquivo'],
            ['Caminho', info.path || path],
            info.isDirectory ? ['Itens', String(info.childCount || 0)] : ['Tamanho', info.sizeFormatted || '—'],
            info.isDirectory ? null : ['Extensão', info.extension ? '.' + info.extension : '—'],
            info.isDirectory ? null : ['Tipo MIME', info.mimeType || '—'],
            ['Modificado', info.lastModifiedDate || '—'],
            ['Leitura', info.canRead ? '✅' : '❌'],
            ['Escrita', info.canWrite ? '✅' : '❌'],
            ['Execução', info.canExecute ? '✅' : '❌'],
            ['Oculto', info.isHidden ? 'Sim' : 'Não'],
        ].filter(Boolean);

        content.innerHTML = rows.map(([k, v]) =>
            `<div style="display:flex;justify-content:space-between;padding:3px 0;border-bottom:1px solid rgba(128,128,128,0.15)"><span style="color:var(--text-secondary,#888)">${k}</span><span style="text-align:right;word-break:break-all;max-width:55%">${v}</span></div>`
        ).join('');

        document.getElementById('propertiesModal').classList.add('open');
    } catch (e) {
        showToast('Erro ao obter propriedades', 'error');
    }
}

// ===== BRIDGE: Compatibilidade Android =====
// Mapeia window.Android (padrão antigo) para nossas bridges
if (window.FileBridge && !window.Android) {
    window.Android = {
        openApps: function() { window.fmOpenApps(); },
        deleteFile: function(path) { if (window.FileBridge.deleteFile) window.FileBridge.deleteFile(path); },
        showToast: function(msg) { showToast(msg, 'info'); }
    };
}

// Show eject instructions for storage devices
window.fmShowEjectInstructions = function(path) {
    showToast('Para ejetar, vá em Configurações > Armazenamento > Desmontar', 'info');
};

// Upload complete callback from Java
window.fmOnUploadComplete = function(result) {
    try {
        const data = typeof result === 'string' ? JSON.parse(result) : result;
        if (data.success) {
            showToast('Upload concluído: ' + (data.count || 1) + ' arquivo(s)', 'success');
            renderFiles();
        } else {
            showToast('Falha no upload: ' + (data.message || 'erro desconhecido'), 'error');
        }
    } catch (e) {
        showToast('Upload finalizado', 'info');
        renderFiles();
    }
};

// Bridge: Ação da Home (chamada pelo Java via evaluateJavascript)
window.fmHomeAction = function() {
    navigateTo('/');
};

// Bridge: Mostrar recentes
window.fmShowRecentes = function() {
    navigateTo('/Recentes');
};

// Bridge: Navegar para SD
window.fmNavigateToSD = function() {
    navigateTo('/CartaoSD');
};

// Bridge: Atualizar dados de armazenamento
window.fmRefreshStorage = function() {
    updateAllStorageData();
};

// Inicialização
document.addEventListener('DOMContentLoaded', function() {
    // Inicializar captura de erros
    initErrorHandler();

    // Bordas animadas durante carregamento
    const widget = document.getElementById('storageWidget');
    if (widget) widget.classList.add('loading');

    setupMenu(getCurrentPage, getCurrentPath);
    loadTrash();
    loadThemePreference();
    renderFiles();
    updateAllStorageData();
    setInterval(updateAllStorageData, 30000);
    setupMenuClose();
    setupSelectionListeners();
    onTabChange((page) => {
        if (page === 'files' && isClipboardActive()) {
            showClipboardBarIfActive();
        } else {
            hideClipboardBar();
        }
    });
    history.pushState({ page: 'files' }, '');

    // Delegation de eventos para lixeira (segurança)
    const trashList = document.getElementById('trashList');
    if (trashList) {
        trashList.addEventListener('click', function(e) {
            const btn = e.target.closest('button');
            if (!btn) return;
            const item = btn.closest('.trash-item');
            if (!item || !item.dataset.name) return;
            const name = decodeURIComponent(item.dataset.name);
            const deletedAt = Number(item.dataset.deletedAt);
            if (btn.classList.contains('trash-restore-btn')) {
                window.fmRestoreFromTrash(name, deletedAt);
            } else if (btn.classList.contains('trash-delete-btn')) {
                window.fmPermanentDelete(name, deletedAt);
            }
        });
    }

    // Bordas param de girar após 3s mínimos
    setTimeout(() => {
        if (widget) widget.classList.remove('loading');
    }, 3000);
});
