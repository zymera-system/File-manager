import { getFiles } from './getFiles.js';
import { showToast } from './toast.js';

const EXTENSIONS = [
    { value: '.html', label: '.html' },
    { value: '.txt', label: '.txt' },
    { value: '.css', label: '.css' },
    { value: '.js', label: '.js' },
    { value: '.json', label: '.json' },
    { value: '.xml', label: '.xml' },
    { value: '.md', label: '.md' },
    { value: '', label: 'Outro...' }
];

let selectedExt = '.html';

function populateExtensions() {
    const dropdown = document.getElementById('extDropdown');
    if (!dropdown) return;
    dropdown.innerHTML = EXTENSIONS.map(ext =>
        `<div class="create-ext-option" data-value="${ext.value}">${ext.label}</div>`
    ).join('');
    dropdown.querySelectorAll('.create-ext-option').forEach(opt => {
        opt.addEventListener('click', () => {
            selectExtension(opt.dataset.value, opt.textContent);
        });
    });
    const defaultExt = EXTENSIONS[0];
    selectExtension(defaultExt.value, defaultExt.label);
}

function selectExtension(value, label) {
    selectedExt = value;
    const labelEl = document.getElementById('extSelectedLabel');
    const extCustom = document.getElementById('createFileExtCustom');
    if (labelEl) labelEl.textContent = label;
    closeExtDropdown();
    if (value === '') {
        labelEl.style.display = 'none';
        extCustom.style.display = 'inline-block';
        extCustom.focus();
    } else {
        labelEl.style.display = '';
        extCustom.style.display = 'none';
    }
}

export function toggleExtDropdown() {
    const dropdown = document.getElementById('extDropdown');
    if (!dropdown) return;
    dropdown.classList.toggle('open');
}

function closeExtDropdown() {
    const dropdown = document.getElementById('extDropdown');
    if (dropdown) dropdown.classList.remove('open');
}

document.addEventListener('click', (e) => {
    const btn = document.getElementById('createFileExtBtn');
    const dropdown = document.getElementById('extDropdown');
    if (btn && dropdown && !btn.contains(e.target) && !dropdown.contains(e.target)) {
        closeExtDropdown();
    }
});

export function openCreateModal(currentPath) {
    if (currentPath === '/') return;
    const input = document.getElementById('createItemName');
    const extBtn = document.getElementById('createFileExtBtn');
    const extCustom = document.getElementById('createFileExtCustom');

    input.value = '';
    input.placeholder = 'Nome da pasta...';
    document.querySelector('input[name="createType"][value="folder"]').checked = true;
    extBtn.style.display = 'none';
    extCustom.style.display = 'none';
    populateExtensions();
    input.focus();
    document.getElementById('modalOverlay').classList.add('open');
    document.getElementById('globalMenu').classList.remove('show');
}

export function closeCreateModal() {
    document.getElementById('modalOverlay').classList.remove('open');
}

export function handleTypeChange() {
    const type = document.querySelector('input[name="createType"]:checked').value;
    const input = document.getElementById('createItemName');
    const extBtn = document.getElementById('createFileExtBtn');
    const extCustom = document.getElementById('createFileExtCustom');

    if (type === 'folder') {
        input.placeholder = 'Nome da pasta...';
        extBtn.style.display = 'none';
        extCustom.style.display = 'none';
    } else {
        input.placeholder = 'Nome do arquivo';
        extBtn.style.display = 'flex';
        const found = EXTENSIONS.find(e => e.value === selectedExt);
        if (found) selectExtension(found.value, found.label);
        else {
            const first = EXTENSIONS[0];
            selectExtension(first.value, first.label);
        }
    }
    input.focus();
}

/**
 * createItem — cria pasta ou arquivo real via FileBridge.
 */
export function createItem(currentPath) {
    if (currentPath === '/') return;
    const nameInput = document.getElementById('createItemName');
    const name = nameInput.value.trim();
    const type = document.querySelector('input[name="createType"]:checked').value;

    if (!name) {
        showToast('Digite um nome para o item.', 'error');
        nameInput.focus();
        return;
    }
    if (/[\\/:*?"<>|]/.test(name)) {
        showToast('Nome contém caracteres inválidos.', 'error');
        nameInput.focus();
        return;
    }

    // Verificar duplicata local
    const current = getFiles(currentPath);
    if (current.some(f => f.name === name)) {
        showToast('Já existe um item com esse nome.', 'error');
        nameInput.focus();
        return;
    }

    const hasBridge = typeof window.FileBridge !== 'undefined' && window.FileBridge !== null;

    if (type === 'folder') {
        if (hasBridge) {
            const devicePath = window.fmGetDevicePath ? window.fmGetDevicePath(currentPath) : currentPath;
            try {
                const raw = window.FileBridge.createFolder(devicePath, name);
                const result = JSON.parse(raw);
                if (result.error) {
                    showToast(result.message, 'error');
                    return;
                }
                closeCreateModal();
                return true;
            } catch (e) {
                showToast('Erro ao criar pasta: ' + e.message, 'error');
                return;
            }
        }
        // Fallback mock
        closeCreateModal();
        return true;
    } else {
        const extCustom = document.getElementById('createFileExtCustom');
        let fileName = name;

        if (selectedExt === '' && !extCustom.value) {
            showToast('Selecione ou digite uma extensão para o arquivo.', 'error');
            extCustom.focus();
            return;
        }

        fileName += selectedExt || '.' + extCustom.value.replace(/^\./, '');

        if (current.some(f => f.name === fileName)) {
            showToast('Já existe um arquivo com esse nome.', 'error');
            nameInput.focus();
            return;
        }

        if (hasBridge) {
            const devicePath = window.fmGetDevicePath ? window.fmGetDevicePath(currentPath) : currentPath;
            try {
                const raw = window.FileBridge.createFile(devicePath, fileName);
                const result = JSON.parse(raw);
                if (result.error) {
                    showToast(result.message, 'error');
                    return;
                }
                closeCreateModal();
                return true;
            } catch (e) {
                showToast('Erro ao criar arquivo: ' + e.message, 'error');
                return;
            }
        }
        // Fallback mock
        closeCreateModal();
        return true;
    }
}
