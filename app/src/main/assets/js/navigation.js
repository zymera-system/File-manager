import { fileSystem, iconMap } from './fileSystem.js';
import { updateUIForPath } from './ui.js';
import { favorites } from './favorites.js';
import { currentSort, sortAsc } from './sort.js';
import { getFiles } from './getFiles.js';
import { getShowExtensions } from './theme.js';
import {
    isSelectionMode,
    isClipboardActive,
    isSelected,
    handleFileClickForSelection,
    longPressStart,
    longPressEnd,
    wasLongPressTriggered,
    resetLongPressFlag,
    clearSelection
} from './selection.js';

let currentPath = '/';

function escapeHtml(str) {
    if (!str) return '';
    return str
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function escapeAttr(str) {
    if (!str) return '';
    return str
        .replace(/&/g, '&amp;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
}

function normalizeSvgViewBoxes() {
    requestAnimationFrame(() => {
        const svgs = document.querySelectorAll('.icon-grid .icon-svg svg');
        svgs.forEach(svg => {
            try {
                const bbox = svg.getBBox();
                if (bbox.width === 0 || bbox.height === 0) return;
                const pad = 0.15;
                const pw = bbox.width * pad;
                const ph = bbox.height * pad;
                const vw = bbox.width + pw * 2;
                const vh = bbox.height + ph * 2;
                const cx = bbox.x + bbox.width / 2;
                const cy = bbox.y + bbox.height / 2;
                const side = Math.max(vw, vh);
                const vx = cx - side / 2;
                const vy = cy - side / 2;
                svg.setAttribute('viewBox', `${vx} ${vy} ${side} ${side}`);
            } catch (e) {}
        });
    });
}

function stripExtension(filename) {
    if (!filename || filename.startsWith('.')) return filename;
    const dot = filename.lastIndexOf('.');
    if (dot > 0 && dot < filename.length - 1) {
        return filename.substring(0, dot);
    }
    return filename;
}

export function getCurrentPath() {
    return currentPath;
}

export function getBreadcrumb(path) {
    if (path === '/') return [{ name: 'Raiz', path: '/' }];
    const parts = path.split('/').filter(p => p);
    const crumbs = [{ name: 'Raiz', path: '/' }];
    let acc = '/';
    for (const p of parts) {
        acc += p + '/';
        crumbs.push({ name: p, path: acc });
    }
    return crumbs;
}

export function renderFiles() {
    const grid = document.getElementById('fileGrid');
    const searchEl = document.getElementById('searchInput');
    const search = searchEl ? searchEl.value.toLowerCase().trim() : '';

    if (currentPath === '/' && !search) {
        grid.innerHTML = `
            <div class="icon-grid">
                ${iconMap.map(icon => {
                    const onclick = icon.path === '/Aplicativos'
                        ? 'window.fmOpenApps()'
                        : `window.fmNavigateTo('${icon.path}')`;
                    return `
                    <div class="icon-item" onclick="${onclick}">
                        <div class="icon-svg">${icon.svg}</div>
                        <div class="icon-label">${icon.label}</div>
                    </div>
                `;}).join('')}
            </div>
        `;
        document.getElementById('breadcrumb').innerHTML = `<span style="color:var(--color-primary);font-weight:700">📁 Raiz</span>`;
        updateUIForPath(currentPath);
        normalizeSvgViewBoxes();
        return;
    }

    let items = getFiles(currentPath).filter(item => item.name.toLowerCase().includes(search));

    if (currentSort === 'name') {
        items.sort((a, b) => (sortAsc ? 1 : -1) * a.name.localeCompare(b.name));
    } else if (currentSort === 'size') {
        items.sort((a, b) => {
            const sa = parseFloat(a.size) || 0;
            const sb = parseFloat(b.size) || 0;
            return (sortAsc ? 1 : -1) * (sa - sb);
        });
    } else if (currentSort === 'date') {
        items.sort((a, b) => (sortAsc ? 1 : -1) * (a.date || '').localeCompare(b.date || ''));
    }

    if (items.length === 0) {
        grid.innerHTML = `
            <div class="empty-state">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
                <div class="empty-state-text">Nenhum arquivo ou pasta encontrado.</div>
            </div>
        `;
    } else {
        const itemsJSON = JSON.stringify(items);
        const showExt = getShowExtensions();
        grid.innerHTML = items.map(item => {
            const isFolder = item.type === 'folder';
            const iconEmoji = isFolder ? '📁' : '📄';
            const size = isFolder ? '—' : (item.size || '—');
            const date = item.date || '—';
            const selected = isSelected(item.name);
            const displayName = isFolder || showExt ? item.name : stripExtension(item.name);
            const safeName = escapeHtml(displayName);
            const safeAttr = escapeAttr(item.name);
            const safePath = escapeAttr(currentPath);
            return `
                <div class="file-item ${selected ? 'selected' : ''}"
                     data-file-name="${safeAttr}"
                     onclick="window.fmFileClick('${safeAttr}')"
                     onmousedown="window.fmLongPressStart(event, '${safeAttr}', '${safePath}')"
                     onmouseup="window.fmLongPressEnd(event)"
                     onmouseleave="window.fmLongPressEnd(event)"
                     ontouchstart="window.fmLongPressStart(event, '${safeAttr}', '${safePath}')"
                     ontouchend="window.fmLongPressEnd(event)">
                    <div class="file-icon ${isFolder ? 'folder' : 'file'}">${iconEmoji}</div>
                    <div class="file-info">
                        <div class="file-name">${safeName}</div>
                        <div class="file-meta">
                            <span>${isFolder ? 'Pasta' : 'Arquivo'}</span>
                            ${!isFolder ? `<span>• ${size}</span>` : ''}
                            <span>• ${date}</span>
                            ${favorites.includes(item.name) ? '<span>⭐</span>' : ''}
                        </div>
                    </div>
                    <div class="file-actions" style="display: none;">
                        <button onclick="event.stopPropagation(); window.fmToggleFavorite('${safeAttr}')" aria-label="Favorito">
                            <svg viewBox="0 0 24 24" fill="${favorites.includes(item.name) ? 'var(--color-primary)' : 'none'}" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
                        </button>
                        <button onclick="event.stopPropagation(); window.fmDeleteItem('${safeAttr}')" aria-label="Excluir">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                        </button>
                    </div>
                </div>
            `;
        }).join('');

        grid.dataset.items = itemsJSON;
    }

    const breadcrumb = document.getElementById('breadcrumb');
    const crumbs = getBreadcrumb(currentPath);
    breadcrumb.innerHTML = crumbs.map((c, i) => {
        const isLast = i === crumbs.length - 1;
        const safePath = escapeAttr(c.path);
        return `<span ${isLast ? `style="color:var(--color-primary);font-weight:700"` : ''} onclick="window.fmNavigateTo('${safePath}')">${c.name}</span>${!isLast ? '<span class="sep">›</span>' : ''}`;
    }).join('');

    updateUIForPath(currentPath);
}

export function navigateTo(path) {
    if (isClipboardActive()) {
        // Durante clipboard, navega normalmente (escolher destino)
    } else if (isSelectionMode()) {
        clearSelection();
        return;
    }
    if (path === '/') { currentPath = '/'; renderFiles(); document.getElementById('globalMenu').classList.remove('show'); return; }
    if (!getFiles(path).length && path !== '/') {
        if (path.startsWith('/') && path.split('/').length > 2) {
            const parent = path.substring(0, path.lastIndexOf('/'));
            if (getFiles(parent).length || fileSystem[parent]) { currentPath = parent; renderFiles(); document.getElementById('globalMenu').classList.remove('show'); return; }
        }
        currentPath = '/';
        console.warn('Caminho não encontrado, redirecionando para raiz:', path);
    } else {
        currentPath = path;
    }
    renderFiles();
    document.getElementById('globalMenu').classList.remove('show');
}
