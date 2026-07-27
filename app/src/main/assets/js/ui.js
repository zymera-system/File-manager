export function updateUIForPath(path) {
    const isHome = path === '/';
    document.getElementById('fileToolbar').classList.toggle('hidden', isHome);
    document.getElementById('breadcrumb').classList.toggle('hidden', isHome);
    document.getElementById('storageWidget').style.display = isHome ? 'block' : 'none';
}
