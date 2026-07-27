let toastTimer = null;

export function showToast(message, type) {
    const toast = document.getElementById('globalToast');
    if (!toast) return;

    if (toastTimer) {
        clearTimeout(toastTimer);
        toastTimer = null;
    }

    toast.textContent = message;
    toast.className = 'global-toast';

    if (type === 'success') toast.classList.add('success');
    else if (type === 'error') toast.classList.add('error');
    else if (type === 'warning') toast.classList.add('warning');
    else toast.classList.add('info');

    toast.classList.add('show');

    toastTimer = setTimeout(() => {
        toast.classList.remove('show');
        toastTimer = null;
    }, 2500);
}