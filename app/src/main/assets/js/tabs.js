let currentPage = 'files';
let onPageChange = null;

export function getCurrentPage() {
    return currentPage;
}

export function onTabChange(fn) {
    onPageChange = fn;
}

export function navigateToTab(tabId) {
    if (tabId === currentPage) return;
    const old = document.querySelector(`.page[data-page="${currentPage}"]`);
    const neu = document.querySelector(`.page[data-page="${tabId}"]`);
    if (old) { old.classList.remove('active'); old.classList.add('exit'); setTimeout(() => old.classList.remove('exit'), 400); }
    if (neu) neu.classList.add('active');
    currentPage = tabId;
    if (onPageChange) onPageChange(tabId);
}
