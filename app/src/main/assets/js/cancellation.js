// ========================================
// CANCELAMENTO COM REVERSÃO SEGURA
// ========================================

import { fileSystem } from './fileSystem.js';

let operationLog = null;

// ========================================
// INICIAR OPERAÇÃO
// ========================================

export function startOperation(type, sourcePath, destPath) {
    operationLog = {
        type: type,
        sourcePath: sourcePath,
        destPath: destPath,
        processed: [],
        sourceSnapshots: {},
        destKeysCreated: [],
        cancelled: false
    };
}

// ========================================
// RASTREAR ARQUIVOS PROCESSADOS
// ========================================

export function trackFileProcessed(name, sourcePath, destPath, action) {
    if (!operationLog) return;
    operationLog.processed.push({ name, sourcePath, destPath, action });
}

export function trackKeyCreated(key) {
    if (!operationLog) return;
    if (!operationLog.destKeysCreated.includes(key)) {
        operationLog.destKeysCreated.push(key);
    }
}

// ========================================
// SNAPSHOT (salvar estado antes de mutar)
// ========================================

export function snapshotBeforeMove(path, fileObj) {
    if (!operationLog) return;
    const normalizedPath = path.endsWith('/') ? path : path + '/';
    const key = normalizedPath + fileObj.name;
    if (!operationLog.sourceSnapshots[key]) {
        operationLog.sourceSnapshots[key] = { ...fileObj };
    }
}

export function snapshotArray(path) {
    if (!operationLog) return;
    const arr = fileSystem[path];
    if (arr && !operationLog.sourceSnapshots['__array__' + path]) {
        operationLog.sourceSnapshots['__array__' + path] = arr.map(item => ({ ...item }));
    }
}

// ========================================
// INFORMAÇÕES DA OPERAÇÃO
// ========================================

export function getProcessedCount() {
    if (!operationLog) return 0;
    return operationLog.processed.length;
}

export function getProcessed() {
    if (!operationLog) return [];
    return [...operationLog.processed];
}

export function getOperationType() {
    if (!operationLog) return null;
    return operationLog.type;
}

export function isCancelled() {
    if (!operationLog) return false;
    return operationLog.cancelled;
}

export function hasOperation() {
    return operationLog !== null;
}

// ========================================
// CANCELAR OPERAÇÃO
// ========================================

export function cancelOperation() {
    if (!operationLog) return [];
    operationLog.cancelled = true;
    return [...operationLog.processed];
}

// ========================================
// REVERSÃO — COPY
// Remove arquivos copiados do destino
// ========================================

function revertCopy(processed) {
    let reverted = 0;

    for (const item of processed) {
        if (item.action !== 'copied') continue;

        const destArr = fileSystem[item.destPath];
        if (destArr) {
            const idx = destArr.findIndex(f => f.name === item.name);
            if (idx !== -1) {
                destArr.splice(idx, 1);
                reverted++;
            }
        }
    }

    // Remover chaves de pastas criadas
    if (operationLog) {
        for (const key of operationLog.destKeysCreated) {
            if (fileSystem[key]) {
                delete fileSystem[key];
            }
        }
    }

    return reverted;
}

// ========================================
// REVERSÃO — MOVE
// Move arquivos de volta ao origem
// ========================================

function revertMove(processed) {
    let reverted = 0;

    for (const item of processed) {
        if (item.action !== 'moved') continue;

        const destArr = fileSystem[item.destPath];
        if (destArr) {
            const idx = destArr.findIndex(f => f.name === item.name);
            if (idx !== -1) {
                destArr.splice(idx, 1);
            }
        }

        const snapKey = (item.sourcePath.endsWith('/') ? item.sourcePath : item.sourcePath + '/') + item.name;
        const snapshot = operationLog ? operationLog.sourceSnapshots[snapKey] : null;

        let sourceArr = fileSystem[item.sourcePath];
        if (!sourceArr) {
            fileSystem[item.sourcePath] = [];
            sourceArr = fileSystem[item.sourcePath];
        }

        const exists = sourceArr.some(f => f.name === item.name);
        if (!exists) {
            if (snapshot) {
                sourceArr.push({ ...snapshot });
            } else {
                sourceArr.push({ name: item.name, type: 'file' });
            }
            reverted++;
        }
    }

    // Restaurar arrays de pastas deletados
    if (operationLog) {
        for (const key of Object.keys(operationLog.sourceSnapshots)) {
            if (key.startsWith('__array__')) {
                const path = key.replace('__array__', '');
                const snapshotArr = operationLog.sourceSnapshots[key];
                if (snapshotArr && !fileSystem[path]) {
                    fileSystem[path] = snapshotArr.map(item => ({ ...item }));
                }
            }
        }
    }

    return reverted;
}

// ========================================
// REVERSÃO — DELETE
// Re-insere arquivos no fileSystem
// ========================================

function revertDelete(processed) {
    let reverted = 0;

    for (const item of processed) {
        if (item.action !== 'deleted') continue;

        const snapKey = item.sourcePath + '/' + item.name;
        const snapshot = operationLog ? operationLog.sourceSnapshots[snapKey] : null;

        let sourceArr = fileSystem[item.sourcePath];
        if (!sourceArr) {
            fileSystem[item.sourcePath] = [];
            sourceArr = fileSystem[item.sourcePath];
        }

        // Verificar se já existe
        const exists = sourceArr.some(f => f.name === item.name);
        if (!exists) {
            if (snapshot) {
                sourceArr.push({ ...snapshot });
            } else {
                sourceArr.push({ name: item.name, type: 'file' });
            }
            reverted++;
        }
    }

    return reverted;
}

// ========================================
// REVERSÃO — DESPACHO
// ========================================

export function revertOperation(type, processed) {
    if (!processed || processed.length === 0) return 0;

    let reverted = 0;

    switch (type) {
        case 'copy':
            reverted = revertCopy(processed);
            break;
        case 'move':
            reverted = revertMove(processed);
            break;
        case 'delete':
            reverted = revertDelete(processed);
            break;
        default:
            break;
    }

    clearLog();
    return reverted;
}

// ========================================
// LIMPAR LOG
// ========================================

export function clearLog() {
    operationLog = null;
}
