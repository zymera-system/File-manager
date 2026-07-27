import * as logger from './logger.js';

export function initErrorHandler() {
    window.onerror = function(message, source, line, col, error) {
        const stack = error && error.stack ? error.stack : '';
        const msg = typeof message === 'object' ? (message.message || String(message)) : String(message);
        const type = error && error.name ? error.name : 'Error';
        logger.error(type, msg, source, line, col, stack);
        return false;
    };

    window.onunhandledrejection = function(event) {
        const reason = event.reason;
        let message = '';
        let stack = '';
        let type = 'PromiseRejection';

        if (reason instanceof Error) {
            message = reason.message;
            stack = reason.stack || '';
            type = reason.name || 'PromiseRejection';
        } else if (typeof reason === 'string') {
            message = reason;
        } else if (reason && typeof reason === 'object') {
            message = reason.message || JSON.stringify(reason);
            stack = reason.stack || '';
        } else {
            message = String(reason);
        }

        logger.error(type, message, '', 0, 0, stack);
    };

    console.log('ErrorHandler: initialized');
}
