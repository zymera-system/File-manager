import { showLoading, updateProgress, updateFile, hideLoading, isLoading, pauseLoading, showReversalResult } from './loading.js';
import {
    startOperation,
    trackFileProcessed,
    trackKeyCreated,
    snapshotBeforeMove,
    snapshotArray,
    isCancelled,
    cancelOperation,
    revertOperation,
    clearLog
} from './cancellation.js';

// ========================
//  BRIDGE HELPERS (file operations via Android)
// ========================

export function hasNativeBridge() {
    return typeof window.FileBridge !== 'undefined' && window.FileBridge !== null;
}

function bridgeCopyFile(sourceInterfacePath, destInterfacePath) {
    if (!hasNativeBridge()) return false;
    try {
        const src = window.fmGetDevicePath ? window.fmGetDevicePath(sourceInterfacePath) : sourceInterfacePath;
        const dst = window.fmGetDevicePath ? window.fmGetDevicePath(destInterfacePath) : destInterfacePath;
        const raw = window.FileBridge.copyFile(src, dst);
        const result = JSON.parse(raw);
        return !result.error;
    } catch (e) { console.error('[bridge] copyFile error:', e.message); return false; }
}

function bridgeMoveFile(sourceInterfacePath, destInterfacePath) {
    if (!hasNativeBridge()) return false;
    try {
        const src = window.fmGetDevicePath ? window.fmGetDevicePath(sourceInterfacePath) : sourceInterfacePath;
        const dst = window.fmGetDevicePath ? window.fmGetDevicePath(destInterfacePath) : destInterfacePath;
        const raw = window.FileBridge.moveFile(src, dst);
        const result = JSON.parse(raw);
        return !result.error;
    } catch (e) { console.error('[bridge] moveFile error:', e.message); return false; }
}

function bridgeCreateFolder(parentInterfacePath, folderName) {
    if (!hasNativeBridge()) return false;
    try {
        const parent = window.fmGetDevicePath ? window.fmGetDevicePath(parentInterfacePath) : parentInterfacePath;
        const raw = window.FileBridge.createFolder(parent, folderName);
        const result = JSON.parse(raw);
        return !result.error;
    } catch (e) { return false; }
}

function buildInterfacePath(base, name) {
    if (!name) return base || '/';
    const sep = base === '/' ? '' : '/';
    return (base === '/' ? '/' : base) + sep + name;
}

let selectionMode = false;
let selectedFiles = new Set();
let rangeStart = null;
let rangeMode = false;
let longPressTimer = null;
let longPressTriggered = false;

let clipboard = { mode: null, files: [], sourcePath: '/' };

export function isSelectionMode() {
    return selectionMode;
}

export function isClipboardActive() {
    return clipboard.mode !== null;
}

export function getClipboardMode() {
    return clipboard.mode;
}

export function getClipboardFiles() {
    return clipboard.files;
}

export function isSelected(fileName) {
    return selectedFiles.has(fileName);
}

export function getSelectedCount() {
    return selectedFiles.size;
}

export function getSelectedFiles() {
    return [...selectedFiles];
}

export function enterSelectionMode(fileName) {
    selectionMode = true;
    selectedFiles.clear();
    if (fileName) selectedFiles.add(fileName);
    longPressTriggered = false;
    updateSelectionUI();
}

export function toggleSelect(fileName) {
    if (!selectionMode) return;
    if (selectedFiles.has(fileName)) {
        selectedFiles.delete(fileName);
    } else {
        selectedFiles.add(fileName);
    }
    if (selectedFiles.size === 0) {
        clearSelection();
        return;
    }
    updateSelectionUI();
}

export function selectAll(currentFiles) {
    if (!selectionMode) return;
    currentFiles.forEach(f => selectedFiles.add(f.name));
    updateSelectionUI();
}

export function deselectAll() {
    selectedFiles.clear();
    updateSelectionUI();
}

export function startRangeSelect() {
    rangeMode = true;
    rangeStart = null;
    const btn = document.getElementById('rangeSelectBtn');
    if (btn) btn.classList.add('active');
    document.addEventListener('click', cancelRangeOnOutsideClick);
}

function cancelRangeOnOutsideClick(e) {
    if (!e.target.closest('.file-item') && !e.target.closest('.selection-bar')) {
        rangeMode = false;
        rangeStart = null;
        const btn = document.getElementById('rangeSelectBtn');
        if (btn) btn.classList.remove('active');
        document.removeEventListener('click', cancelRangeOnOutsideClick);
    }
}

export function handleFileClickForSelection(fileName, allVisibleFiles) {
    if (!selectionMode) return false;

    if (rangeMode) {
        if (rangeStart === null) {
            rangeStart = fileName;
            const el = document.querySelector(`[data-file-name="${fileName}"]`);
            if (el) el.classList.add('range-preview');
            return true;
        }
        const startIdx = allVisibleFiles.findIndex(f => f.name === rangeStart);
        const endIdx = allVisibleFiles.findIndex(f => f.name === fileName);
        if (startIdx === -1 || endIdx === -1) return true;
        const minIdx = Math.min(startIdx, endIdx);
        const maxIdx = Math.max(startIdx, endIdx);
        for (let i = minIdx; i <= maxIdx; i++) {
            selectedFiles.add(allVisibleFiles[i].name);
        }
        document.querySelectorAll('.file-item.range-preview').forEach(el => el.classList.remove('range-preview'));
        rangeMode = false;
        rangeStart = null;
        const btn = document.getElementById('rangeSelectBtn');
        if (btn) btn.classList.remove('active');
        document.removeEventListener('click', cancelRangeOnOutsideClick);
        updateSelectionUI();
        return true;
    }

    toggleSelect(fileName);
    return true;
}

export function clearSelection() {
    selectionMode = false;
    selectedFiles.clear();
    rangeStart = null;
    rangeMode = false;
    const btn = document.getElementById('rangeSelectBtn');
    if (btn) btn.classList.remove('active');
    const menu = document.getElementById('selectionMoreMenu');
    if (menu) menu.classList.remove('show');
    document.removeEventListener('click', cancelRangeOnOutsideClick);
    updateSelectionUI();
}

function showDuplicationWarning(msg) {
    return new Promise((resolve) => {
        document.getElementById('duplicationMessage').textContent = msg;
        window.fmCloseDuplication = (proceed) => {
            document.getElementById('duplicationModal').classList.remove('open');
            resolve(proceed);
        };
        document.getElementById('duplicationModal').classList.add('open');
    });
}

export async function enterCopyMode() {
    const files = [...selectedFiles];
    if (files.length === 0) return;
    const warnings = checkInternalDuplication(files);
    if (warnings.length > 0) {
        const msg = warnings.map(w => `"${w.file}" já está dentro da pasta "${w.folder}"`).join('\n');
        const proceed = await showDuplicationWarning(msg);
        if (!proceed) return;
    }
    clipboard = { mode: 'copy', files, sourcePath: getCurrentPathForClipboard() };
    clearSelection();
    showClipboardBar();
}

export async function enterMoveMode() {
    const files = [...selectedFiles];
    if (files.length === 0) return;
    const warnings = checkInternalDuplication(files);
    if (warnings.length > 0) {
        const msg = warnings.map(w => `"${w.file}" já está dentro da pasta "${w.folder}"`).join('\n');
        const proceed = await showDuplicationWarning(msg);
        if (!proceed) return;
    }
    clipboard = { mode: 'move', files, sourcePath: getCurrentPathForClipboard() };
    clearSelection();
    showClipboardBar();
}

function checkInternalDuplication(selectedItems) {
    const warnings = [];
    const grid = document.getElementById('fileGrid');
    const itemsJSON = grid?.dataset?.items;
    let allFiles = [];

    try {
        allFiles = itemsJSON ? JSON.parse(itemsJSON) : [];
    } catch(e) {
        console.warn('checkInternalDuplication: falha ao parsear items', e);
    }

    selectedItems.forEach(name => {
        const item = allFiles.find(f => f.name === name);
        if (item && item.type === 'folder') {
            const folderPath = getCurrentPathForClipboard() + name + '/';
            selectedItems.forEach(otherName => {
                if (otherName !== name) {
                    const otherItem = allFiles.find(f => f.name === otherName);
                    if (otherItem && otherItem.type === 'file') {
                        const destFiles = getFilesForClipboard(folderPath);
                        if (destFiles.some(f => f.name === otherName)) {
                            warnings.push({ file: otherName, folder: name });
                        }
                    }
                }
            });
        }
    });
    return warnings;
}

export function cancelClipboard() {
    clipboard = { mode: null, files: [], sourcePath: '/' };
    hideClipboardBar();
}

// ========================================
// CONFLICT RESOLUTION
// ========================================

let conflictApplyAll = null;
let conflictResolve = null;

function showConflictModal(fileName, sourcePath, destPath) {
    return new Promise((resolve) => {
        document.getElementById('conflictFileName').textContent = fileName;
        document.getElementById('conflictSourcePath').textContent = sourcePath;
        document.getElementById('conflictDestPath').textContent = destPath;
        document.getElementById('conflictApplyAll').checked = false;
        const skipRadio = document.querySelector('input[name="conflictAction"][value="skip"]');
        if (skipRadio) skipRadio.checked = true;

        conflictResolve = resolve;
        document.getElementById('conflictModal').classList.add('open');
    });
}

window.fmConfirmConflict = function() {
    const checked = document.querySelector('input[name="conflictAction"]:checked');
    const action = checked ? checked.value : 'skip';
    const applyAll = document.getElementById('conflictApplyAll').checked;

    if (applyAll) {
        conflictApplyAll = action;
    }

    document.getElementById('conflictModal').classList.remove('open');

    if (conflictResolve) {
        conflictResolve({ action });
        conflictResolve = null;
    }
};

window.fmCancelConflict = function() {
    document.getElementById('conflictModal').classList.remove('open');
    conflictApplyAll = null;
    if (conflictResolve) {
        conflictResolve({ action: 'cancel' });
        conflictResolve = null;
    }
};

function generateUniqueName(originalName, existingFiles) {
    const dot = originalName.lastIndexOf('.');
    const baseName = (dot > 0) ? originalName.substring(0, dot) : originalName;
    const ext = (dot > 0) ? originalName.substring(dot) : '';
    let counter = 1;
    let newName;
    do {
        newName = baseName + ' (' + counter + ')' + ext;
        counter++;
    } while (existingFiles.some(f => f.name === newName));
    return newName;
}

async function resolveConflicts(toProcess, sourcePath, destPath, destFiles) {
    const resolved = [];
    for (const name of toProcess) {
        const destExists = destFiles.some(f => f.name === name);
        if (!destExists) {
            resolved.push({ name, destName: name });
            continue;
        }

        let action;
        if (conflictApplyAll !== null) {
            action = conflictApplyAll;
        } else {
            const choice = await showConflictModal(name, sourcePath, destPath);
            action = choice.action;
        }

        if (action === 'cancel') {
            return [];
        }

        switch (action) {
            case 'replace':
                const idx = destFiles.findIndex(f => f.name === name);
                if (idx > -1) destFiles.splice(idx, 1);
                resolved.push({ name, destName: name });
                break;
            case 'rename':
                const newName = generateUniqueName(name, destFiles);
                resolved.push({ name, destName: newName });
                break;
            case 'skip':
            default:
                break;
        }
    }
    return resolved;
}

export async function pasteFiles(destPath) {
    if (!clipboard.files.length || !destPath || destPath === '/') return false;
    if (clipboard.sourcePath === destPath) {
        cancelClipboard();
        return false;
    }

    const files = [...clipboard.files];
    const sourcePath = clipboard.sourcePath;
    const sourceFiles = getFilesForClipboard(sourcePath);
    const destFiles = getFilesForClipboard(destPath);

    // Filtrar apenas arquivos que existem na origem
    let toCopy = files.filter(name => {
        return sourceFiles.some(f => f.name === name);
    });

    if (toCopy.length === 0) {
        cancelClipboard();
        return false;
    }

    // Resolver conflitos com o destino
    conflictApplyAll = null;
    const resolved = await resolveConflicts(toCopy, sourcePath, destPath, destFiles);

    if (resolved.length === 0) {
        cancelClipboard();
        return false;
    }

    // Iniciar operação
    startOperation('copy', sourcePath, destPath);
    snapshotArray(sourcePath);

    showLoading({
        icon: 'copy',
        title: 'Copiando arquivos',
        current: 0,
        total: resolved.length,
        percentage: 0,
        cancellable: true,
        onCancel: () => { pauseLoading(); }
    });

    let copied = 0;

    for (let i = 0; i < resolved.length; i++) {
        await new Promise(r => setTimeout(r, 0));

        if (isCancelled()) {
            hideLoading();
            const processed = cancelOperation();
            if (processed.length > 0) {
                const reverted = revertOperation('copy', processed);
                showReversalResult(reverted, 'copy');
            }
            cancelClipboard();
            return false;
        }

        const { name, destName } = resolved[i];
        const file = sourceFiles.find(f => f.name === name);
        if (!file) continue;

        snapshotBeforeMove(sourcePath, file);

        const srcInterface = buildInterfacePath(sourcePath, name);
        const dstInterface = buildInterfacePath(destPath, destName);

        if (hasNativeBridge()) {
            // Bridge real: copiar arquivo/pasta via Android
            if (file.type === 'folder') {
                // Para pastas, criar a pasta destino e copiar conteúdo
                bridgeCreateFolder(destPath, destName);
                await copyFolderRecursiveBridge(sourcePath, name, destPath, destName);
            } else {
                const ok = bridgeCopyFile(srcInterface, dstInterface);
                if (!ok) {
                    console.warn('[copy] Falha ao copiar:', name);
                }
            }
        } else {
            // Fallback mock: copiar via array em memória
            if (file.type === 'folder') {
                await copyFolderRecursive(sourcePath + name + '/', destPath + destName + '/');
            }
            destFiles.push({ ...file, name: destName });
        }

        trackFileProcessed(name, sourcePath, destPath, 'copied');
        copied++;

        updateProgress(Math.round(((i + 1) / resolved.length) * 100), i + 1, resolved.length);
        updateFile(destName, sourcePath + name, destPath + destName, file.size);
    }

    clearLog();
    hideLoading();
    cancelClipboard();
    return copied > 0;
}

export async function moveFiles(destPath) {
    if (!clipboard.files.length || !destPath || destPath === '/') return false;
    if (clipboard.sourcePath === destPath) {
        cancelClipboard();
        return false;
    }

    const files = [...clipboard.files];
    const sourcePath = clipboard.sourcePath;
    const sourceFiles = getFilesForClipboard(sourcePath);
    const destFiles = getFilesForClipboard(destPath);

    // Filtrar apenas arquivos que existem na origem
    let toMove = files.filter(name => {
        return sourceFiles.some(f => f.name === name);
    });

    if (toMove.length === 0) {
        cancelClipboard();
        return false;
    }

    // Resolver conflitos com o destino
    conflictApplyAll = null;
    const resolved = await resolveConflicts(toMove, sourcePath, destPath, destFiles);

    if (resolved.length === 0) {
        cancelClipboard();
        return false;
    }

    // Iniciar operação
    startOperation('move', sourcePath, destPath);
    snapshotArray(sourcePath);
    for (const { name } of resolved) {
        const file = sourceFiles.find(f => f.name === name);
        if (file) snapshotBeforeMove(sourcePath, file);
    }

    showLoading({
        icon: 'move',
        title: 'Movendo arquivos',
        current: 0,
        total: resolved.length,
        percentage: 0,
        cancellable: true,
        onCancel: () => { pauseLoading(); }
    });

    let moved = 0;

    for (let i = 0; i < resolved.length; i++) {
        await new Promise(r => setTimeout(r, 0));

        if (isCancelled()) {
            hideLoading();
            const processed = cancelOperation();
            if (processed.length > 0) {
                const reverted = revertOperation('move', processed);
                showReversalResult(reverted, 'move');
            }
            cancelClipboard();
            return false;
        }

        const { name, destName } = resolved[i];
        const file = sourceFiles.find(f => f.name === name);
        if (!file) continue;

        const srcInterface = buildInterfacePath(sourcePath, name);
        const dstInterface = buildInterfacePath(destPath, destName);

        if (hasNativeBridge()) {
            // Bridge real: mover arquivo/pasta via Android
            if (file.type === 'folder') {
                await moveFolderRecursiveBridge(sourcePath, name, destPath, destName);
            } else {
                const ok = bridgeMoveFile(srcInterface, dstInterface);
                if (!ok) {
                    console.warn('[move] Falha ao mover:', name);
                }
            }
        } else {
            // Fallback mock: mover via array em memória
            if (file.type === 'folder') {
                await moveFolderRecursive(sourcePath + name + '/', destPath + destName + '/');
            }
            const idx = sourceFiles.findIndex(f => f.name === name);
            if (idx > -1) sourceFiles.splice(idx, 1);
            destFiles.push({ ...file, name: destName });
        }

        trackFileProcessed(name, sourcePath, destPath, 'moved');
        moved++;

        updateProgress(Math.round(((i + 1) / resolved.length) * 100), i + 1, resolved.length);
        updateFile(destName, sourcePath + name, destPath + destName, file.size);
    }

    clearLog();
    hideLoading();
    cancelClipboard();
    return moved > 0;
}

async function copyFolderRecursive(sourcePath, destPath, visited = new Set()) {
    if (visited.has(sourcePath)) return;
    visited.add(sourcePath);

    const sourceItems = getFilesForClipboard(sourcePath);
    const destMap = getFilesMapForClipboard();

    if (!destMap[sourcePath]) destMap[sourcePath] = [];
    if (!destMap[destPath]) destMap[destPath] = [];

    trackKeyCreated(destPath);

    for (const item of sourceItems) {
        // Checkpoint
        await new Promise(r => setTimeout(r, 0));

        if (isCancelled()) return;

        if (item.type === 'folder') {
            await copyFolderRecursive(sourcePath + item.name + '/', destPath + item.name + '/', visited);
            destMap[destPath].push({ ...item });
        } else {
            destMap[destPath].push({ ...item });
        }
    }
}

async function moveFolderRecursive(sourcePath, destPath, visited = new Set()) {
    if (visited.has(sourcePath)) return;
    visited.add(sourcePath);

    const sourceItems = getFilesForClipboard(sourcePath);
    const destMap = getFilesMapForClipboard();

    if (!destMap[sourcePath]) destMap[sourcePath] = [];
    if (!destMap[destPath]) destMap[destPath] = [];

    trackKeyCreated(destPath);

    for (const item of sourceItems) {
        // Checkpoint
        await new Promise(r => setTimeout(r, 0));

        if (isCancelled()) return;

        if (item.type === 'folder') {
            await moveFolderRecursive(sourcePath + item.name + '/', destPath + item.name + '/', visited);
            destMap[destPath].push({ ...item });
        } else {
            destMap[destPath].push({ ...item });
        }
    }
}

// ========================
//  BRIDGE RECURSIVE OPERATIONS (pastas via Android)
// ========================

async function copyFolderRecursiveBridge(sourceBase, folderName, destBase, destFolderName, visited = new Set()) {
    const srcPath = buildInterfacePath(sourceBase, folderName);
    const dstPath = buildInterfacePath(destBase, destFolderName);
    if (visited.has(srcPath)) return;
    visited.add(srcPath);

    let items = [];
    if (hasNativeBridge()) {
        try {
            const srcDevice = window.fmGetDevicePath ? window.fmGetDevicePath(srcPath) : srcPath;
            const raw = window.FileBridge.listFiles(srcDevice);
            items = JSON.parse(raw);
            if (items.error) items = [];
        } catch (e) { console.error('[copy-bridge] listFiles error:', e.message); return; }
    }

    for (const item of items) {
        await new Promise(r => setTimeout(r, 0));
        if (isCancelled()) return;

        const itemSrcInterface = buildInterfacePath(srcPath, item.name);
        const itemDstInterface = buildInterfacePath(dstPath, item.name);

        if (item.type === 'folder') {
            bridgeCreateFolder(dstPath, item.name);
            await copyFolderRecursiveBridge(srcPath, item.name, dstPath, item.name, visited);
        } else {
            bridgeCopyFile(itemSrcInterface, itemDstInterface);
        }
    }
}

async function moveFolderRecursiveBridge(sourceBase, folderName, destBase, destFolderName, visited = new Set()) {
    const srcPath = buildInterfacePath(sourceBase, folderName);
    const dstPath = buildInterfacePath(destBase, destFolderName);
    if (visited.has(srcPath)) return;
    visited.add(srcPath);

    let items = [];
    if (hasNativeBridge()) {
        try {
            const srcDevice = window.fmGetDevicePath ? window.fmGetDevicePath(srcPath) : srcPath;
            const raw = window.FileBridge.listFiles(srcDevice);
            items = JSON.parse(raw);
            if (items.error) items = [];
        } catch (e) { console.error('[move-bridge] listFiles error:', e.message); return; }
    }

    for (const item of items) {
        await new Promise(r => setTimeout(r, 0));
        if (isCancelled()) return;

        const itemSrcInterface = buildInterfacePath(srcPath, item.name);
        const itemDstInterface = buildInterfacePath(dstPath, item.name);

        if (item.type === 'folder') {
            bridgeCreateFolder(dstPath, item.name);
            await moveFolderRecursiveBridge(srcPath, item.name, dstPath, item.name, visited);
        } else {
            bridgeMoveFile(itemSrcInterface, itemDstInterface);
        }
    }

    // Após mover filhos, remover pasta origem vazia
    try {
        const srcDevice = window.fmGetDevicePath ? window.fmGetDevicePath(srcPath) : srcPath;
        window.FileBridge.deleteItem(srcDevice);
    } catch (e) { /* pasta pode já ter sido removida */ }
}

function showClipboardBar() {
    const selBar = document.getElementById('selectionBar');
    const clipBar = document.getElementById('clipboardBar');
    const clipCount = document.getElementById('clipboardCount');
    const clipConfirm = document.getElementById('clipboardConfirmBtn');
    const clipOrigin = document.getElementById('clipboardOrigin');

    if (!selBar || !clipBar) return;

    selBar.classList.remove('active');
    clipBar.classList.add('active');

    const qty = clipboard.files.length;
    const itemWord = qty === 1 ? 'item' : 'itens';
    const actionWord = clipboard.mode === 'copy' ? 'Copiando' : 'Movendo';
    clipCount.textContent = `${actionWord} ${qty} ${itemWord}`;
    clipOrigin.textContent = 'Origem: ' + clipboard.sourcePath;

    if (clipboard.mode === 'copy') {
        clipConfirm.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg><span class="sel-btn-label">Colar</span>`;
    } else {
        clipConfirm.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M5 12h14"/><path d="M12 5l7 7-7 7"/></svg><span class="sel-btn-label">Mover</span>`;
    }
}

export function showClipboardBarIfActive() {
    if (isClipboardActive()) {
        showClipboardBar();
    }
}

export function hideClipboardBar() {
    const clipBar = document.getElementById('clipboardBar');
    if (clipBar) clipBar.classList.remove('active');
}

let _getCurrentPathFn = null;
export function setClipboardPathGetter(fn) { _getCurrentPathFn = fn; }
function getCurrentPathForClipboard() { return _getCurrentPathFn ? _getCurrentPathFn() : '/'; }

let _getFilesFn = null;
export function setClipboardFilesGetter(fn) { _getFilesFn = fn; }
function getFilesForClipboard(path) { return _getFilesFn ? _getFilesFn(path) : []; }

let _getFilesMapFn = null;
export function setClipboardFilesMapGetter(fn) { _getFilesMapFn = fn; }
function getFilesMapForClipboard() { return _getFilesMapFn ? _getFilesMapFn() : {}; }

function updateSelectionUI() {
    const bar = document.getElementById('selectionBar');
    const countEl = document.getElementById('selectionCount');
    const moreMenu = document.getElementById('selectionMoreMenu');

    if (selectionMode && selectedFiles.size > 0) {
        if (bar) bar.classList.add('active');
        const summary = getSelectionSummary();
        if (countEl) {
            if (summary.folders > 0 && summary.files > 0) {
                countEl.textContent = `${summary.folders} pasta${summary.folders > 1 ? 's' : ''} + ${summary.files} arquivo${summary.files > 1 ? 's' : ''}`;
            } else if (summary.folders > 0) {
                countEl.textContent = `${summary.folders} pasta${summary.folders > 1 ? 's' : ''}`;
            } else {
                countEl.textContent = `${summary.files} arquivo${summary.files > 1 ? 's' : ''}`;
            }
        }
    } else {
        if (bar) bar.classList.remove('active');
        if (moreMenu) moreMenu.classList.remove('show');
    }

    document.querySelectorAll('.file-item').forEach(el => {
        const name = el.dataset.fileName;
        if (name) {
            el.classList.toggle('selected', selectedFiles.has(name));
        }
        const actions = el.querySelector('.file-actions');
        if (actions) {
            actions.style.display = selectionMode ? 'flex' : 'none';
        }
    });

    const selectAllBtn = document.getElementById('selectAllBtn');
    if (selectAllBtn) {
        selectAllBtn.classList.toggle('active', selectionMode && selectedFiles.size > 0);
    }
}

export function getSelectionSummary() {
    let folders = 0;
    let files = 0;
    const items = document.querySelectorAll('.file-item[data-file-name]');
    items.forEach(el => {
        const name = el.dataset.fileName;
        if (selectedFiles.has(name)) {
            const icon = el.querySelector('.file-icon');
            if (icon && icon.classList.contains('folder')) {
                folders++;
            } else {
                files++;
            }
        }
    });
    return { folders, files };
}

export function longPressStart(e, fileName, currentPath) {
    if (selectionMode) return;
    if (currentPath === '/') return;
    longPressTriggered = false;
    longPressTimer = setTimeout(() => {
        longPressTriggered = true;
        enterSelectionMode(fileName);
    }, 500);
}

export function longPressEnd(e) {
    if (longPressTimer) {
        clearTimeout(longPressTimer);
        longPressTimer = null;
    }
}

export function wasLongPressTriggered() {
    return longPressTriggered;
}

export function resetLongPressFlag() {
    longPressTriggered = false;
}

export function toggleMoreMenu() {
    const menu = document.getElementById('selectionMoreMenu');
    menu.classList.toggle('show');
}

export function setupSelectionListeners() {
    document.addEventListener('click', function(e) {
        const moreMenu = document.getElementById('selectionMoreMenu');
        if (moreMenu && moreMenu.classList.contains('show')) {
            if (!e.target.closest('#moreActionsBtn') && !e.target.closest('#selectionMoreMenu')) {
                moreMenu.classList.remove('show');
            }
        }
    });

    const selectAllBtn = document.getElementById('selectAllBtn');
    if (selectAllBtn) {
        selectAllBtn.addEventListener('click', function() {
            const visibleFiles = getVisibleFiles();
            if (visibleFiles.length === 0) return;
            if (selectedFiles.size === visibleFiles.length) {
                deselectAll();
            } else {
                if (!selectionMode) enterSelectionMode();
                selectAll(visibleFiles);
            }
        });
    }

    const rangeBtn = document.getElementById('rangeSelectBtn');
    if (rangeBtn) {
        rangeBtn.addEventListener('click', function() {
            if (rangeMode) {
                rangeMode = false;
                rangeStart = null;
                this.classList.remove('active');
            } else {
                startRangeSelect();
            }
        });
    }
}

function getVisibleFiles() {
    const items = document.querySelectorAll('.file-item[data-file-name]');
    return Array.from(items).map(el => ({ name: el.dataset.fileName }));
}
