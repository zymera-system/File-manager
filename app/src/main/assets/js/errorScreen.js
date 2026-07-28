import * as logger from './logger.js';

export function renderErrorLog() {
    const container = document.getElementById('errorLogContainer');
    if (!container) return;

    const logs = logger.getLogs();

    if (logs.length === 0) {
        container.innerHTML = `
            <div class="error-empty">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="width:48px;height:48px;opacity:0.3">
                    <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
                </svg>
                <div class="error-empty-text">Nenhum erro registrado</div>
            </div>
        `;
        return;
    }

    const reversed = [...logs].reverse();

    container.innerHTML = reversed.map(entry => {
        const badgeColor = getBadgeColor(entry.level);
        const badgeIcon = getBadgeIcon(entry.level);
        const date = formatDate(entry.timestamp);
        const typeLabel = getTypeLabel(entry.type);
        const truncatedMsg = truncate(entry.message, 120);
        const showExpand = entry.message.length > 120 || entry.stack.length > 0;

        return `
            <div class="error-card" data-id="${entry.id}">
                <div class="error-card-header">
                    <span class="error-card-num">ERROR #${String(entry.num).padStart(3, '0')}</span>
                    <span class="error-badge" style="background:${badgeColor}">${badgeIcon} ${entry.level}</span>
                </div>
                <div class="error-card-body">
                    <div class="error-info-line"><span class="error-info-label">Tipo:</span> ${escapeHtml(typeLabel)}</div>
                    <div class="error-info-line"><span class="error-info-label">Arquivo:</span> ${escapeHtml(entry.source || 'N/A')}</div>
                    <div class="error-info-line"><span class="error-info-label">Linha:</span> ${entry.line || 'N/A'}</div>
                    <div class="error-info-line"><span class="error-info-label">Data:</span> ${date}</div>
                    <div class="error-msg-box">${escapeHtml(truncatedMsg)}
                        ${showExpand ? '<span class="error-expand-btn" onclick="toggleErrorExpand(this)">expandir</span>' : ''}
                    </div>
                    ${entry.stack ? `<pre class="error-stack hidden">${escapeHtml(entry.stack)}</pre>` : ''}
                </div>
                <div class="error-card-footer">
                    <button class="error-copy-btn" onclick="copySingleError('${entry.id}')">Copiar</button>
                </div>
            </div>
        `;
    }).join('');
}

export function exportLogs() {
    const logs = logger.getLogs();
    if (logs.length === 0) {
        showToast('Nenhum erro para exportar.');
        return;
    }

    let text = '=== Log de Erros - File Manager ===\n';
    text += 'Exportado em: ' + new Date().toLocaleString('pt-BR') + '\n';
    text += 'Total de erros: ' + logs.length + '\n\n';

    logs.forEach(entry => {
        text += `ERROR #${String(entry.num).padStart(3, '0')}\n`;
        text += `  Data:    ${formatDate(entry.timestamp)}\n`;
        text += `  Nivel:   ${entry.level}\n`;
        text += `  Tipo:    ${getTypeLabel(entry.type)}\n`;
        text += `  Arquivo: ${entry.source || 'N/A'}\n`;
        text += `  Linha:   ${entry.line || 'N/A'}\n`;
        text += `  Mensagem: ${entry.message}\n`;
        if (entry.stack) {
            text += `  Stack:\n${entry.stack.split('\n').map(l => '    ' + l).join('\n')}\n`;
        }
        text += '\n';
    });

    text += '=== Fim do Log ===\n';

    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'fm-error-log-' + new Date().toISOString().slice(0, 10) + '.txt';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}

export function shareLogs() {
    const logs = logger.getLogs();
    if (logs.length === 0) {
        showToast('Nenhum erro para compartilhar.');
        return;
    }
    const text = buildExportText(logs);
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'fm-error-log-' + new Date().toISOString().slice(0, 10) + '.txt';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}

export function copyAllLogs() {
    const logs = logger.getLogs();
    if (logs.length === 0) {
        showToast('Nenhum erro para copiar.');
        return;
    }
    const text = buildExportText(logs);
    copyToClipboard(text);
    showToast('Log copiado para a clipboard!');
}

export function clearAllLogs() {
    const modal = document.getElementById('confirmModal');
    const msg = document.getElementById('confirmMessage');
    if (modal && msg) {
        msg.textContent = 'Tem certeza que deseja limpar todos os logs de erro?';
        modal.classList.add('open');
        window.fmConfirmClearLogs = function() {
            logger.clearLogs();
            renderErrorLog();
            modal.classList.remove('open');
            showToast('Logs limpos');
        };
        window.fmCancelClearLogs = function() {
            modal.classList.remove('open');
        };
    } else {
        logger.clearLogs();
        renderErrorLog();
    }
}

window.toggleErrorExpand = function(btn) {
    const box = btn.parentElement;
    const stack = box.nextElementSibling;
    if (stack && stack.classList.contains('error-stack')) {
        const isHidden = stack.classList.contains('hidden');
        stack.classList.toggle('hidden', !isHidden);
        btn.textContent = isHidden ? 'recolher' : 'expandir';
    }
};

window.copySingleError = function(id) {
    const logs = logger.getLogs();
    const entry = logs.find(e => e.id === id);
    if (!entry) return;

    let text = `ERROR #${String(entry.num).padStart(3, '0')}\n`;
    text += `Data: ${formatDate(entry.timestamp)}\n`;
    text += `Nivel: ${entry.level}\n`;
    text += `Tipo: ${getTypeLabel(entry.type)}\n`;
    text += `Arquivo: ${entry.source || 'N/A'}\n`;
    text += `Linha: ${entry.line || 'N/A'}\n`;
    text += `Mensagem: ${entry.message}\n`;
    if (entry.stack) {
        text += `Stack:\n${entry.stack}\n`;
    }
    copyToClipboard(text);
    showToast('Erro copiado!');
};

function buildExportText(logs) {
    let text = '=== Log de Erros - File Manager ===\n';
    text += 'Exportado em: ' + new Date().toLocaleString('pt-BR') + '\n';
    text += 'Total de erros: ' + logs.length + '\n\n';
    logs.forEach(entry => {
        text += `ERROR #${String(entry.num).padStart(3, '0')}\n`;
        text += `  Data:    ${formatDate(entry.timestamp)}\n`;
        text += `  Nivel:   ${entry.level}\n`;
        text += `  Tipo:    ${getTypeLabel(entry.type)}\n`;
        text += `  Arquivo: ${entry.source || 'N/A'}\n`;
        text += `  Linha:   ${entry.line || 'N/A'}\n`;
        text += `  Mensagem: ${entry.message}\n`;
        if (entry.stack) {
            text += `  Stack:\n${entry.stack.split('\n').map(l => '    ' + l).join('\n')}\n`;
        }
        text += '\n';
    });
    text += '=== Fim do Log ===\n';
    return text;
}

function copyToClipboard(text) {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.select();
    document.execCommand('copy');
    document.body.removeChild(ta);
}

function showToast(msg) {
    const t = document.createElement('div');
    t.textContent = msg;
    Object.assign(t.style, {
        position: 'fixed', bottom: '100px', left: '50%', transform: 'translateX(-50%)',
        background: 'var(--color-text)', color: 'var(--color-bg)',
        padding: '10px 20px', borderRadius: '12px', fontSize: '14px',
        fontWeight: '600', zIndex: '99999', fontFamily: 'var(--font-family)',
        transition: 'opacity 0.3s', opacity: '1'
    });
    document.body.appendChild(t);
    setTimeout(() => { t.style.opacity = '0'; setTimeout(() => t.remove(), 300); }, 2000);
}

function getBadgeColor(level) {
    switch (level) {
        case 'ERROR': return '#E74C3C';
        case 'WARN': return '#F2994A';
        case 'INFO': return '#2D9CDB';
        case 'LOG': return '#6E6E73';
        default: return '#6E6E73';
    }
}

function getBadgeIcon(level) {
    switch (level) {
        case 'ERROR': return '\u{1F534}';
        case 'WARN': return '\u{1F7E0}';
        case 'INFO': return '\u{1F535}';
        case 'LOG': return '\u{26AA}';
        default: return '\u{26AA}';
    }
}

function getTypeLabel(type) {
    const map = {
        'TypeError': 'Erro de Tipo',
        'ReferenceError': 'Erro de Refer\u00EAncia',
        'SyntaxError': 'Erro de Sintaxe',
        'RangeError': 'Erro de Intervalo',
        'URIError': 'Erro de URI',
        'EvalError': 'Erro de Eval',
        'PromiseRejection': 'Rejei\u00E7\u00E3o de Promise',
        'Error': 'Erro JavaScript',
        'JavaCrash': 'Crash Java',
    };
    return map[type] || type;
}

function formatDate(isoString) {
    try {
        const d = new Date(isoString);
        return d.toLocaleString('pt-BR', {
            day: '2-digit', month: '2-digit',
            hour: '2-digit', minute: '2-digit', second: '2-digit'
        });
    } catch (e) {
        return isoString;
    }
}

function truncate(str, max) {
    if (!str || str.length <= max) return str || '';
    return str.slice(0, max) + '...';
}

function escapeHtml(str) {
    if (!str) return '';
    return str
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
