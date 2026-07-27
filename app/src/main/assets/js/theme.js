const THEME_KEY = 'fm_dark_theme';
const EXTENSIONS_KEY = 'fm_show_extensions';

export function loadThemePreference() {
    try {
        const stored = localStorage.getItem(THEME_KEY);
        const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
        const isDark = stored !== null ? JSON.parse(stored) : prefersDark;

        document.getElementById('appFrame').classList.toggle('dark', isDark);

        const themeToggle = document.querySelector('.settings-item .toggle[data-setting="theme"]');
        if (themeToggle) {
            themeToggle.classList.toggle('on', isDark);
            themeToggle.classList.toggle('off', !isDark);
        }

        const showExt = localStorage.getItem(EXTENSIONS_KEY);
        if (showExt !== null) {
            const extToggle = document.querySelector('.settings-item .toggle[data-setting="extensions"]');
            if (extToggle) {
                const isOn = JSON.parse(showExt);
                extToggle.classList.toggle('on', isOn);
                extToggle.classList.toggle('off', !isOn);
            }
        }
    } catch (e) {
        console.warn('Erro ao carregar preferências:', e.message);
    }
}

export function getShowExtensions() {
    try {
        const val = localStorage.getItem(EXTENSIONS_KEY);
        return val !== null ? JSON.parse(val) : true;
    } catch {
        return true;
    }
}

export function toggleTheme(el) {
    const isDark = !el.classList.contains('on');
    el.classList.toggle('on', isDark);
    el.classList.toggle('off', !isDark);
    document.getElementById('appFrame').classList.toggle('dark', isDark);
    localStorage.setItem(THEME_KEY, JSON.stringify(isDark));
}

export function toggleExtensions(el) {
    const isOn = !el.classList.contains('on');
    el.classList.toggle('on', isOn);
    el.classList.toggle('off', !isOn);
    localStorage.setItem(EXTENSIONS_KEY, JSON.stringify(isOn));
    if (typeof window.fmRenderFiles === 'function') {
        window.fmRenderFiles();
    }
}