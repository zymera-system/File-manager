// ========================================
// CONFIGURAÇÃO DO MENU POR PÁGINA
// ========================================
// Ícones são importados de '../icons/icons.js'
// ========================================

import { 
    iconGear, 
    iconAnalyze, 
    iconRefresh, 
    iconTrash, 
    iconNewFile, 
    iconSort, 
    iconClearCache, 
    iconAbout, 
    iconRestore 
} from '../icons/icons.js';

// Ícone de diagnostic log (estilo relógio/aviso)
const iconDiagnostic = `<circle cx="12" cy="12" r="10"/><path d="M12 8v4"/><path d="M12 16h.01"/>`;

export const menuConfig = {
    // Itens que aparecem em TODAS as páginas (no final)
    global: [
        {
            id: 'diagnostic',
            label: 'Logs de Erro',
            icon: iconDiagnostic,
            action: 'fmOpenErrorLog'
        },
        {
            id: 'settings',
            label: 'Configurações',
            icon: iconGear,
            action: 'fmOpenSettings'
        }
    ],

    // Página: Files (home grid)
    files: [
        {
            id: 'analyze',
            label: 'Analisar dispositivo',
            icon: iconAnalyze,
            action: 'fmAnalyzeDevice'
        },
        {
            id: 'refresh',
            label: 'Atualizar',
            icon: iconRefresh,
            action: 'fmRefreshFiles'
        },
        { id: 'divider-1', divider: true },
        {
            id: 'trash',
            label: 'Lixeira',
            icon: iconTrash,
            action: 'fmOpenTrash'
        }
    ],

    // Página: Files (subpasta)
    fileFolder: [
        {
            id: 'new-file',
            label: 'Novo arquivo',
            icon: iconNewFile,
            action: 'fmOpenCreateModal'
        },
        {
            id: 'sort',
            label: 'Ordenar por',
            icon: iconSort,
            action: 'fmShowSortMenu'
        },
        {
            id: 'refresh',
            label: 'Atualizar',
            icon: iconRefresh,
            action: 'fmRefreshFiles'
        },
        { id: 'divider-1', divider: true },
        {
            id: 'trash',
            label: 'Lixeira',
            icon: iconTrash,
            action: 'fmOpenTrash'
        }
    ],

    // Página: Apps
    apps: [
        {
            id: 'sort-apps',
            label: 'Ordenar',
            icon: iconSort,
            action: 'fmOpenSortAppsModal'
        },
        { id: 'divider-1', divider: true },
        {
            id: 'clear-cache',
            label: 'Limpar cache',
            icon: iconClearCache,
            action: 'fmClearCache'
        }
    ],

    // Página: Configurações
    settings: [
        {
            id: 'about',
            label: 'Sobre',
            icon: iconAbout,
            action: 'fmShowAbout'
        }
    ],

    // Página: Lixeira
    trash: [
        {
            id: 'clear-trash',
            label: 'Limpar lixeira',
            icon: iconTrash,
            action: 'fmClearTrash'
        },
        {
            id: 'restore-all',
            label: 'Restaurar tudo',
            icon: iconRestore,
            action: 'fmRestoreAll'
        }
    ]
};
