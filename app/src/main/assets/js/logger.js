const STORAGE_KEY = 'fm_error_log';
const MAX_ENTRIES = 500;
let entryCounter = 0;

function loadEntries() {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        const entries = raw ? JSON.parse(raw) : [];
        entryCounter = entries.length;
        return entries;
    } catch (e) {
        return [];
    }
}

function saveEntries(entries) {
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
    } catch (e) {
        console.warn('JSLogger: localStorage full, clearing oldest entries');
        entries.splice(0, Math.floor(MAX_ENTRIES / 2));
        localStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
    }
}

function addEntry(level, type, message, source, line, col, stack) {
    const entries = loadEntries();
    const entry = {
        id: Date.now() + '-' + Math.random().toString(36).substr(2, 4),
        num: ++entryCounter,
        timestamp: new Date().toISOString(),
        level: level,
        type: type,
        message: message,
        source: source || '',
        line: line || 0,
        col: col || 0,
        stack: stack || ''
    };
    entries.push(entry);
    if (entries.length > MAX_ENTRIES) {
        entries.splice(0, entries.length - MAX_ENTRIES);
    }
    saveEntries(entries);
    return entry;
}

export function log(message, source) {
    addEntry('LOG', 'Info', message, source);
}

export function info(message, source) {
    addEntry('INFO', 'Info', message, source);
}

export function warn(message, source) {
    addEntry('WARN', 'Warning', message, source);
}

export function error(type, message, source, line, col, stack) {
    const entry = addEntry('ERROR', type, message, source, line, col, stack);
    if (typeof FM !== 'undefined' && FM.reportError) {
        try {
            FM.reportError(type, message, source || '', line || 0, stack || '');
        } catch (e) {
            console.warn('JSLogger: FM.reportError failed', e);
        }
    }
    return entry;
}

export function getLogs() {
    return loadEntries();
}

export function clearLogs() {
    localStorage.removeItem(STORAGE_KEY);
    entryCounter = 0;
}

export function getLogCount() {
    return loadEntries().length;
}
