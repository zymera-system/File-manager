/**
 * progress.js — Gerenciamento de operações assíncronas com progresso.
 *
 * Inspirado no ZArchiver:
 * - Operações em background com até 5 slots
 * - Progresso via polling da bridge
 * - Cancelamento por taskId
 * - UI de progresso com overlay
 */

// ========================================
// ESTADO DAS OPERAÇÕES ATIVAS
// ========================================

const activeTasks = new Map(); // taskId -> { type, status, progress, ... }

/**
 * Inicia uma operação assíncrona com monitoramento de progresso.
 *
 * @param {string} operationType - 'copy', 'move', 'delete', 'compress', 'extract'
 * @param {Function} startFn - Função que inicia a operação e retorna taskId
 * @param {string} label - Descrição amigável (ex: "Copiando arquivos...")
 * @returns {Promise<string>} Promise que resolve com o taskId quando concluído
 */
export function startAsyncOperation(operationType, startFn, label) {
    return new Promise((resolve, reject) => {
        try {
            const taskId = startFn();
            if (!taskId) {
                reject(new Error('Falha ao iniciar operação'));
                return;
            }

            const taskInfo = {
                taskId: taskId,
                type: operationType,
                label: label || operationType,
                status: 'running',
                progress: 0,
                errorMessage: '',
                _resolve: resolve,
                _reject: reject,
            };

            activeTasks.set(taskId, taskInfo);
            showProgressOverlay(taskInfo);
            updateTaskQueueBar();

            // Iniciar polling
            startPolling(taskId);
        } catch (e) {
            reject(e);
        }
    });
}

// ========================================
// POLLING DE PROGRESSO
// ========================================

const POLL_INTERVAL = 300; // ms

function startPolling(taskId) {
    const poll = () => {
        const taskInfo = activeTasks.get(taskId);
        if (!taskInfo || taskInfo.status !== 'running') return;

        try {
            const raw = window.FileBridge.pollProgress(taskId);
            if (!raw) {
                // Tarefa finalizou sem resposta
                markCompleted(taskId);
                return;
            }

            const data = JSON.parse(raw);
            const progress = data.progress || 0;
            const currentFile = data.currentFile || '';
            const completed = data.completed || false;
            const success = data.success !== undefined ? data.success : true;
            const errorMsg = data.errorMessage || '';

            taskInfo.progress = progress;

            if (completed) {
                if (success) {
                    markCompleted(taskId);
                } else {
                    markFailed(taskId, errorMsg || 'Operação falhou');
                }
                return;
            }

            // Atualizar UI
            updateProgressUI(taskInfo, currentFile);

            // Continuar polling
            setTimeout(poll, POLL_INTERVAL);
        } catch (e) {
            console.warn('[progress] Erro no polling:', e);
            // Tentar novamente
            setTimeout(poll, POLL_INTERVAL * 2);
        }
    };

    setTimeout(poll, POLL_INTERVAL);
}

// ========================================
// FUNÇÕES DE OPERAÇÃO
// ========================================

/**
 * Copia arquivo/pasta em background com progresso.
 */
/**
 * Interpreta o retorno de asyncCopy/asyncMove/asyncDelete.
 * O Java retorna o taskId como string pura (ex: "task_a1b2c3d4").
 */
function parseAsyncResult(raw) {
    if (!raw || raw === 'null') throw new Error('Resposta vazia da bridge');
    // Se for JSON (começa com '{'), fazer parse completo
    if (raw.startsWith('{')) {
        const data = JSON.parse(raw);
        if (data.error) throw new Error(data.error);
        return data.taskId || raw;
    }
    // Se for string pura, é o próprio taskId
    return raw;
}

export function copyWithProgress(sourcePath, destPath) {
    return startAsyncOperation(
        'copy',
        () => {
            const raw = window.FileBridge.asyncCopy(sourcePath, destPath);
            return parseAsyncResult(raw);
        },
        'Copiando...'
    );
}

/**
 * Move arquivo/pasta em background com progresso.
 */
export function moveWithProgress(sourcePath, destPath) {
    return startAsyncOperation(
        'move',
        () => {
            const raw = window.FileBridge.asyncMove(sourcePath, destPath);
            return parseAsyncResult(raw);
        },
        'Movendo...'
    );
}

/**
 * Exclui arquivo/pasta em background com progresso.
 */
export function deleteWithProgress(path) {
    return startAsyncOperation(
        'delete',
        () => {
            const raw = window.FileBridge.asyncDelete(path);
            return parseAsyncResult(raw);
        },
        'Excluindo...'
    );
}

/**
 * Cancela uma operação em andamento.
 */
export function cancelOperation(taskId) {
    if (!taskId) return;
    try {
        const result = window.FileBridge.cancelOperation(taskId);
        // cancelOperation retorna boolean, não JSON
        const success = (result === true || result === 'true');
        if (success) {
            const taskInfo = activeTasks.get(taskId);
            if (taskInfo) {
                taskInfo.status = 'cancelled';
                taskInfo.progress = 0;
                activeTasks.delete(taskId);
                if (taskInfo._reject) taskInfo._reject(new Error('Operação cancelada'));
            }
            hideProgressOverlay();
            updateTaskQueueBar();
            showToast('Operação cancelada', 'warning');
        }
    } catch (e) {
        console.error('[progress] Erro ao cancelar:', e);
    }
}

// ========================================
// CALLBACKS DE ESTADO
// ========================================

function markCompleted(taskId) {
    const taskInfo = activeTasks.get(taskId);
    if (!taskInfo) return;

    taskInfo.status = 'completed';
    taskInfo.progress = 100;
    activeTasks.delete(taskId);

    updateProgressUI(taskInfo, '');
    hideProgressOverlay();
    updateTaskQueueBar();
    // Remover após 2s se não houver mais tarefas
    setTimeout(() => {
        if (!hasActiveTasks()) hideTaskQueueBar();
    }, 2000);

    if (taskInfo._resolve) taskInfo._resolve(taskId);
    showToast('Operação concluída', 'success');
}

function markFailed(taskId, errorMessage) {
    const taskInfo = activeTasks.get(taskId);
    if (!taskInfo) return;

    taskInfo.status = 'failed';
    taskInfo.errorMessage = errorMessage;
    activeTasks.delete(taskId);

    hideProgressOverlay();
    updateTaskQueueBar();

    if (taskInfo._reject) taskInfo._reject(new Error(errorMessage || 'Operação falhou'));
    showToast('Erro: ' + (errorMessage || 'Operação falhou'), 'error');
}

// ========================================
// UI — OVERLAY DE PROGRESSO
// ========================================

function showProgressOverlay(taskInfo) {
    const overlay = document.getElementById('loadingOverlay');
    if (!overlay) return;

    const dots = document.getElementById('loadingDots');
    const icon = document.getElementById('loadingIcon');
    const title = document.getElementById('loadingTitle');
    const info = document.getElementById('loadingInfo');
    const fill = document.getElementById('loadingFill');
    const percentage = document.getElementById('loadingPercentage');
    const count = document.getElementById('loadingCount');
    const cancelBtn = document.getElementById('loadingCancelBtn');

    if (dots) dots.style.display = '';
    if (icon) icon.style.display = 'none';
    if (title) title.textContent = taskInfo.label || 'Operação em andamento';
    if (info) info.textContent = 'Preparando...';
    if (fill) fill.style.width = '0%';
    if (percentage) percentage.textContent = '0%';
    if (count) count.textContent = '';

    const typeIcon = {
        'copy': '📋',
        'move': '📦',
        'delete': '🗑️',
        'compress': '📚',
        'extract': '📂',
    }[taskInfo.type] || '⚙️';

    if (title) title.textContent = `${typeIcon} ${taskInfo.label}`;

    if (cancelBtn) {
        cancelBtn.style.display = '';
        cancelBtn.onclick = () => cancelOperation(taskInfo.taskId);
    }

    overlay.classList.add('open');
}

function updateProgressUI(taskInfo, currentFile) {
    const fill = document.getElementById('loadingFill');
    const percentage = document.getElementById('loadingPercentage');
    const info = document.getElementById('loadingInfo');
    const count = document.getElementById('loadingCount');

    if (fill) fill.style.width = Math.min(taskInfo.progress, 100) + '%';
    if (percentage) percentage.textContent = Math.round(taskInfo.progress) + '%';
    if (info && currentFile) {
        // Mostrar apenas o nome do arquivo atual (não o caminho completo)
        const fileName = currentFile.split('/').pop() || currentFile;
        info.textContent = fileName;
    }
    if (count) {
        const files = taskInfo.processedCount || 0;
        const total = taskInfo.totalCount || 0;
        if (total > 0) {
            count.textContent = `${files} / ${total} arquivos`;
        }
    }
}

function hideProgressOverlay() {
    const overlay = document.getElementById('loadingOverlay');
    if (overlay) overlay.classList.remove('open');

    const cancelBtn = document.getElementById('loadingCancelBtn');
    if (cancelBtn) cancelBtn.onclick = null;
}

// ========================================
// TOAST HELPER (local para evitar dependência circular)
// ========================================

function showToast(message, type) {
    const toast = document.getElementById('globalToast');
    if (!toast) return;

    toast.textContent = message;
    toast.className = 'global-toast ' + (type || '');
    toast.classList.add('show');

    clearTimeout(toast._hideTimer);
    toast._hideTimer = setTimeout(() => {
        toast.classList.remove('show');
    }, 3000);
}

// ========================================
// TASK QUEUE BAR — UI
// ========================================

function updateTaskQueueBar() {
    const bar = document.getElementById('taskQueueBar');
    const badge = document.getElementById('taskQueueBadge');
    const text = document.getElementById('taskQueueText');
    const list = document.getElementById('taskQueueList');

    const count = activeTasks.size;
    const tasks = Array.from(activeTasks.values());
    const running = tasks.filter(t => t.status === 'running');

    if (bar) {
        if (running.length > 0) {
            bar.classList.add('active');
        } else {
            bar.classList.remove('active');
        }
    }

    if (badge) badge.textContent = running.length;

    if (text) {
        if (running.length === 0) {
            // Deixar o texto existente para transição suave
        } else if (running.length === 1) {
            const t = running[0];
            const labels = { 'copy': 'Copiando', 'move': 'Movendo', 'delete': 'Excluindo',
                'compress': 'Compactando', 'extract': 'Extraindo' };
            text.textContent = (labels[t.type] || t.type) + '...';
        } else {
            text.textContent = running.length + ' operações em andamento';
        }
    }

    // Atualizar lista no painel
    if (list && document.getElementById('taskQueuePanel').classList.contains('open')) {
        renderTaskQueueList(list, tasks);
    }
}

function renderTaskQueueList(list, tasks) {
    if (tasks.length === 0) {
        list.innerHTML = '<div style="text-align:center;padding:20px;color:var(--color-text-muted);font-size:13px">Nenhuma operação ativa</div>';
        return;
    }

    const typeIcons = { 'copy': '📋', 'move': '📦', 'delete': '🗑️', 'compress': '📚', 'extract': '📂' };
    const statusLabels = { 'running': '', 'completed': '✓ Concluído', 'failed': '✕ Falhou', 'cancelled': '— Cancelado' };

    list.innerHTML = tasks.map(task => {
        const icon = typeIcons[task.type] || '⚙️';
        const statusClass = task.status !== 'running' ? 'status-' + task.status : '';
        const statusLabel = statusLabels[task.status] || '';
        const showCancel = task.status === 'running' ? '' : 'style="display:none"';

        return `
            <div class="task-item ${statusClass}">
                <div class="task-item-icon">${icon}</div>
                <div class="task-item-info">
                    <div class="task-item-label">${task.label || task.type}</div>
                    <div class="task-item-file">${statusLabel}</div>
                </div>
                <div class="task-item-progress">
                    <div class="task-item-progress-bar">
                        <div class="task-item-progress-fill" style="width:${Math.min(task.progress, 100)}%"></div>
                    </div>
                    <div class="task-item-progress-text">${Math.round(task.progress)}%</div>
                </div>
                <button class="task-item-cancel" onclick="window.fmCancelTask('${task.taskId}')" ${showCancel}>✕</button>
            </div>
        `;
    }).join('');
}

function hideTaskQueueBar() {
    const bar = document.getElementById('taskQueueBar');
    if (bar) bar.classList.remove('active');
}

export function toggleTaskQueuePanel() {
    const panel = document.getElementById('taskQueuePanel');
    const list = document.getElementById('taskQueueList');
    if (!panel) return;

    const isOpen = panel.classList.contains('open');
    if (isOpen) {
        panel.classList.remove('open');
    } else {
        panel.classList.add('open');
        if (list) renderTaskQueueList(list, Array.from(activeTasks.values()));
    }
}

export function hideTaskQueuePanel() {
    const panel = document.getElementById('taskQueuePanel');
    if (panel) panel.classList.remove('open');
}

// Expor funções para onclick inline
if (typeof window !== 'undefined') {
    window.fmToggleTaskQueue = toggleTaskQueuePanel;
    window.fmHideTaskQueue = hideTaskQueuePanel;
    window.fmCancelTask = cancelOperation;
}

// ========================================
// EXPORTS
// ========================================

export function getActiveTasks() {
    return Array.from(activeTasks.values());
}

export function hasActiveTasks() {
    return activeTasks.size > 0;
}

export function updateTaskQueueUI() {
    updateTaskQueueBar();
}
