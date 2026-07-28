import { getFiles } from './getFiles.js';
import { fileSystem } from './fileSystem.js';
import { showToast } from './toast.js';
import { renderFiles, getCurrentPath } from './navigation.js';
import { showLoading, updateProgress, updateFile, hideLoading } from './loading.js';

export function uploadFile() {
    const hasBridge = typeof window.FileBridge !== 'undefined' && window.FileBridge !== null;
    if (!hasBridge) {
        showToast('Upload disponível apenas no dispositivo', 'warning');
        return;
    }
    try {
        window.FileBridge.pickAndUploadFile(getCurrentPath());
    } catch (e) {
        showToast('Erro ao iniciar upload: ' + e.message, 'error');
    }
}

export function uploadMultiple() {
    const hasBridge = typeof window.FileBridge !== 'undefined' && window.FileBridge !== null;
    if (!hasBridge) {
        showToast('Upload disponível apenas no dispositivo', 'warning');
        return;
    }
    try {
        window.FileBridge.pickAndUploadMultiple(getCurrentPath());
    } catch (e) {
        showToast('Erro ao iniciar upload: ' + e.message, 'error');
    }
}

export function onUploadComplete(result) {
    try {
        const data = typeof result === 'string' ? JSON.parse(result) : result;
        if (data.success) {
            showToast('Upload concluído: ' + (data.count || 1) + ' arquivo(s)', 'success');
            renderFiles();
        } else {
            showToast('Falha no upload: ' + (data.error || 'erro desconhecido'), 'error');
        }
    } catch (e) {
        showToast('Upload finalizado', 'info');
        renderFiles();
    }
}
