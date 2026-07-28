import { fileSystem, iconMap, getFilteredIconMap } from './fileSystem.js';
import { updateUIForPath } from './ui.js';
import { favorites } from './favorites.js';
import { currentSort, sortAsc } from './sort.js';
import { getFiles, getFilesPaged, clearFileCache, prefetchNextPage } from './getFiles.js';
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

// ========================================
// ESTADO DE PAGINAÇÃO
// ========================================
const PAGE_SIZE = 50;
let paginationState = {
    path: null,        // path atual para verificar se mudou
    offset: 0,
    total: 0,
    hasMore: false,
    allItems: [],      // todos os itens carregados até agora (já filtrados/buscados)
    isLoading: false,
};

function resetPagination(newPath) {
    paginationState = {
        path: newPath,
        offset: 0,
        total: 0,
        hasMore: false,
        allItems: [],
        isLoading: false,
    };
}

// ========================================
// HTML ESCAPE
// ========================================
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

// ========================================
// RENDERIZAÇÃO DOS ITENS DO ARQUIVO
// ========================================

/**
 * Renderiza um array de itens como HTML de file-item.
 */
function renderFileItemsHtml(items, showExt) {
    return items.map(item => {
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
}

/**
 * Aplica ordenação nos itens conforme currentSort/sortAsc.
 */
function applySort(items) {
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
    return items;
}

// ========================================
// LOAD MORE (scroll infinito)
// ========================================

export function loadMoreFiles() {
    if (paginationState.isLoading || !paginationState.hasMore) return;
    if (paginationState.path !== currentPath) return;

    paginationState.isLoading = true;
    showLoadingIndicator(true);

    try {
        const result = getFilesPaged(currentPath, paginationState.offset, PAGE_SIZE);
        if (!result || !result.items) {
            paginationState.isLoading = false;
            showLoadingIndicator(false);
            return;
        }

        // Aplicar ordenação nos novos itens
        const sortedNewItems = applySort(result.items);

        paginationState.allItems = paginationState.allItems.concat(sortedNewItems);
        paginationState.offset = paginationState.offset + (result.items.length || 0);
        paginationState.total = result.total;
        paginationState.hasMore = result.hasMore;

        // Renderizar os novos itens no grid
        appendItemsToGrid(sortedNewItems);

        // Pré-carregar próximo lote em background
        if (paginationState.hasMore) {
            prefetchNextPage(currentPath, paginationState.offset);
        }

        updateLoadMoreButton();
    } catch (e) {
        console.warn('[navigation] Erro em loadMoreFiles:', e);
    } finally {
        paginationState.isLoading = false;
        showLoadingIndicator(false);
    }
}

function showLoadingIndicator(show) {
    let indicator = document.getElementById('loadingIndicator');
    if (show) {
        if (!indicator) {
            indicator = document.createElement('div');
            indicator.id = 'loadingIndicator';
            indicator.style.cssText = 'text-align:center;padding:16px;color:var(--color-primary)';
            indicator.textContent = 'Carregando…';
            const grid = document.getElementById('fileGrid');
            if (grid) grid.after(indicator);
        }
        indicator.style.display = 'block';
    } else if (indicator) {
        indicator.style.display = 'none';
    }
}

function appendItemsToGrid(items) {
    const grid = document.getElementById('fileGrid');
    if (!grid) return;
    const showExt = getShowExtensions();
    grid.insertAdjacentHTML('beforeend', renderFileItemsHtml(items, showExt));
    // Atualizar dataset com todos os itens carregados até agora
    grid.dataset.items = JSON.stringify(paginationState.allItems);
}

function updateLoadMoreButton() {
    const existing = document.getElementById('loadMoreBtn');
    if (paginationState.hasMore) {
        if (!existing) {
            const btn = document.createElement('button');
            btn.id = 'loadMoreBtn';
            btn.textContent = 'Carregar mais';
            btn.className = 'load-more-btn';
            btn.onclick = loadMoreFiles;
            const grid = document.getElementById('fileGrid');
            if (grid) grid.after(btn);
        }
    } else if (existing) {
        existing.remove();
    }
}

function removeLoadMoreButton() {
    const btn = document.getElementById('loadMoreBtn');
    if (btn) btn.remove();
    const indicator = document.getElementById('loadingIndicator');
    if (indicator) indicator.remove();
}

// ========================================
// HOME RENDER
// ========================================

function renderHome() {
    const grid = document.getElementById('fileGrid');
    grid.innerHTML = `
        <div class="icon-grid">
            ${getFilteredIconMap().map(icon => {
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
    removeLoadMoreButton();
}

// ========================================
// RENDER FILES (principal)
// ========================================

export function renderFiles() {
    const grid = document.getElementById('fileGrid');
    const searchEl = document.getElementById('searchInput');
    const search = searchEl ? searchEl.value.toLowerCase().trim() : '';

    // Tela inicial (Home)
    if (currentPath === '/' && !search) {
        renderHome();
        return;
    }

    // Resetar estado de paginação ao mudar de diretório
    if (paginationState.path !== currentPath) {
        resetPagination(currentPath);
    }

    // Se já tem dados carregados, renderizar imediatamente do cache
    if (paginationState.allItems.length > 0 && !search) {
        const showExt = getShowExtensions();
        const html = renderFileItemsHtml(paginationState.allItems, showExt);
        grid.innerHTML = html;
        grid.dataset.items = JSON.stringify(paginationState.allItems);
        updateBreadcrumb();
        updateUIForPath(currentPath);
        updateLoadMoreButton();
        return;
    }

    // Primeiro carregamento (ou busca ativa)
    paginationState.isLoading = true;
    showLoadingIndicator(true);

    try {
        // Se há busca, carregar TUDO para filtrar (busca é exceção)
        if (search) {
            const allResult = getFilesPaged(currentPath, 0, -1);
            let filtered = allResult.items.filter(item =>
                item.name.toLowerCase().includes(search)
            );
            filtered = applySort(filtered);

            if (filtered.length === 0) {
                grid.innerHTML = getEmptyStateHtml();
                grid.dataset.items = '[]';
            } else {
                const showExt = getShowExtensions();
                grid.innerHTML = renderFileItemsHtml(filtered, showExt);
                grid.dataset.items = JSON.stringify(filtered);
            }
            removeLoadMoreButton();
        } else {
            // Carregar primeira página
            const result = getFilesPaged(currentPath, 0, PAGE_SIZE);

            if (!result || result.items.length === 0) {
                grid.innerHTML = getEmptyStateHtml();
                removeLoadMoreButton();
                paginationState.isLoading = false;
                showLoadingIndicator(false);
                updateBreadcrumb();
                updateUIForPath(currentPath);
                return;
            }

            const sortedItems = applySort(result.items);
            paginationState.allItems = sortedItems;
            paginationState.offset = sortedItems.length;
            paginationState.total = result.total;
            paginationState.hasMore = result.hasMore;

            const showExt = getShowExtensions();
            grid.innerHTML = renderFileItemsHtml(sortedItems, showExt);
            grid.dataset.items = JSON.stringify(sortedItems);

            // Pré-carregar próximo lote
            if (paginationState.hasMore) {
                prefetchNextPage(currentPath, paginationState.offset);
            }

            updateLoadMoreButton();
        }
    } catch (e) {
        console.warn('[navigation] Erro em renderFiles:', e);
        // Fallback: tenta o método antigo
        try {
            const allItems = getFiles(currentPath);
            if (allItems.length === 0) {
                grid.innerHTML = getEmptyStateHtml();
                grid.dataset.items = '[]';
            } else {
                const showExt = getShowExtensions();
                grid.innerHTML = renderFileItemsHtml(allItems, showExt);
                grid.dataset.items = JSON.stringify(allItems);
            }
        } catch (e2) {
            grid.innerHTML = getEmptyStateHtml();
        }
        removeLoadMoreButton();
    } finally {
        paginationState.isLoading = false;
        showLoadingIndicator(false);
    }

    updateBreadcrumb();
    updateUIForPath(currentPath);
}

function getEmptyStateHtml() {
    return `
        <div class="empty-state">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
            <div class="empty-state-text">Nenhum arquivo ou pasta encontrado.</div>
        </div>
    `;
}

function updateBreadcrumb() {
    const breadcrumb = document.getElementById('breadcrumb');
    const crumbs = getBreadcrumb(currentPath);
    breadcrumb.innerHTML = crumbs.map((c, i) => {
        const isLast = i === crumbs.length - 1;
        const safePath = escapeAttr(c.path);
        return `<span ${isLast ? `style="color:var(--color-primary);font-weight:700"` : ''} onclick="window.fmNavigateTo('${safePath}')">${c.name}</span>${!isLast ? '<span class="sep">›</span>' : ''}`;
    }).join('');
}

export function navigateTo(path) {
    if (isClipboardActive()) {
        // Durante clipboard, navega normalmente (escolher destino)
    } else if (isSelectionMode()) {
        clearSelection();
        return;
    }
    if (path === '/') {
        currentPath = '/';
        clearFileCache();
        resetPagination('/');
        renderFiles();
        document.getElementById('globalMenu').classList.remove('show');
        return;
    }

    // Verificar se o caminho existe (fallback para diretórios que não existem mais)
    const testItems = getFilesPaged(path, 0, 1);
    if (testItems.total === 0 && path !== '/') {
        // Tenta o pai
        if (path.startsWith('/') && path.split('/').length > 2) {
            const parent = path.substring(0, path.lastIndexOf('/'));
            const parentTest = getFilesPaged(parent, 0, 1);
            if (parentTest.total > 0 || fileSystem[parent]) {
                currentPath = parent;
                clearFileCache();
                resetPagination(parent);
                renderFiles();
                document.getElementById('globalMenu').classList.remove('show');
                return;
            }
        }
        // Fallback para raiz
        currentPath = '/';
        clearFileCache();
        resetPagination('/');
        console.warn('Caminho não encontrado, redirecionando para raiz:', path);
    } else {
        currentPath = path;
        clearFileCache();
        resetPagination(path);
    }
    renderFiles();
    document.getElementById('globalMenu').classList.remove('show');
}

// ========================================
// SCROLL INFINITO (auto-detecção)
// ========================================

let scrollListenerAttached = false;

export function enableInfiniteScroll() {
    if (scrollListenerAttached) return;
    scrollListenerAttached = true;

    const grid = document.getElementById('fileGrid');
    if (!grid) return;

    // Usar IntersectionObserver para detectar quando o "load more" está visível
    if (typeof IntersectionObserver !== 'undefined') {
        const observer = new IntersectionObserver((entries) => {
            for (const entry of entries) {
                if (entry.isIntersecting && paginationState.hasMore && !paginationState.isLoading) {
                    loadMoreFiles();
                }
            }
        }, { rootMargin: '200px' });

        // Observar o grid para mudanças — quando o botão "load more" for adicionado ao DOM,
        // começar a observá-lo para scroll infinito automático
        const mutationObserver = new MutationObserver(() => {
            const btn = document.getElementById('loadMoreBtn');
            if (btn) {
                observer.observe(btn);
            }
        });

        mutationObserver.observe(grid.parentElement || document.body, { childList: true, subtree: true });
    } else {
        // Fallback: scroll event listener
        window.addEventListener('scroll', () => {
            if (paginationState.isLoading || !paginationState.hasMore) return;
            const btn = document.getElementById('loadMoreBtn');
            if (!btn) return;
            const rect = btn.getBoundingClientRect();
            if (rect.top < window.innerHeight + 200) {
                loadMoreFiles();
            }
        }, { passive: true });
    }
}

// Inicializar scroll infinito após DOM ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', enableInfiniteScroll);
} else {
    enableInfiniteScroll();
}

