package com.filemanager.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.filemanager.app.MainActivity;
import com.filemanager.app.R;
import com.filemanager.app.core.OperationManager;

/**
 * FileManagerService — Foreground Service para operações longas.
 *
 * Mantém o app vivo durante cópias, movimentações, compressão, etc.
 * Exibe notificação persistente com progresso.
 *
 * Uso:
 *   FileManagerService.start(context, "Copy", "Copying files...");
 *   FileManagerService.updateProgress(context, 45, "photo.jpg");
 *   FileManagerService.finish(context, true, "3 files copied");
 */
public class FileManagerService extends Service {

    private static final String TAG = "FileManagerService";
    private static final String CHANNEL_ID = "filemanager_operations";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START = "com.filemanager.app.ACTION_START";
    public static final String ACTION_UPDATE = "com.filemanager.app.ACTION_UPDATE";
    public static final String ACTION_FINISH = "com.filemanager.app.ACTION_FINISH";
    public static final String ACTION_CANCEL = "com.filemanager.app.ACTION_CANCEL";

    public static final String EXTRA_OPERATION_TYPE = "operation_type";
    public static final String EXTRA_DESCRIPTION = "description";
    public static final String EXTRA_PROGRESS = "progress";
    public static final String EXTRA_CURRENT_FILE = "current_file";
    public static final String EXTRA_SUCCESS = "success";
    public static final String EXTRA_RESULT_MESSAGE = "result_message";

    private NotificationManager notificationManager;
    private String currentOperation = "";
    private long startTime = 0;

    // Callback para notificar cancelamento ao JS
    public interface CancelCallback {
        void onCancelled();
    }
    private static CancelCallback cancelCallback;

    public static void setCancelCallback(CancelCallback callback) {
        cancelCallback = callback;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = getSystemService(NotificationManager.class);
        createNotificationChannel();
        Log.d(TAG, "Service criado");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        switch (action != null ? action : "") {
            case ACTION_START:
                handleStart(intent);
                break;
            case ACTION_UPDATE:
                handleUpdate(intent);
                break;
            case ACTION_FINISH:
                handleFinish(intent);
                break;
            case ACTION_CANCEL:
                handleCancel();
                break;
            default:
                stopSelf();
                break;
        }

        return START_STICKY;
    }

    // ========================================
    //  HANDLERS
    // ========================================

    private void handleStart(Intent intent) {
        currentOperation = intent.getStringExtra(EXTRA_OPERATION_TYPE);
        String description = intent.getStringExtra(EXTRA_DESCRIPTION);
        if (currentOperation == null) currentOperation = "operation";
        if (description == null) description = "Processing...";
        startTime = System.currentTimeMillis();

        startForeground(NOTIFICATION_ID, buildNotification(0, description, true));
        Log.d(TAG, "Operação iniciada: " + currentOperation + " — " + description);
    }

    private void handleUpdate(Intent intent) {
        int progress = intent.getIntExtra(EXTRA_PROGRESS, 0);
        String currentFile = intent.getStringExtra(EXTRA_CURRENT_FILE);
        if (currentFile == null) currentFile = "";

        String desc = buildDescription(progress, currentFile);
        notificationManager.notify(NOTIFICATION_ID, buildNotification(progress, desc, true));
    }

    private void handleFinish(Intent intent) {
        boolean success = intent.getBooleanExtra(EXTRA_SUCCESS, true);
        String message = intent.getStringExtra(EXTRA_RESULT_MESSAGE);
        if (message == null) message = success ? "Completed" : "Failed";

        long elapsed = System.currentTimeMillis() - startTime;
        String timeStr = formatElapsed(elapsed);

        String text = message + " (" + timeStr + ")";
        notificationManager.notify(NOTIFICATION_ID, buildNotification(100, text, false));

        // Auto-dismiss após 3 segundos
        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            stopForeground(true);
            stopSelf();
        }, 3000);

        Log.d(TAG, "Operação finalizada: " + currentOperation + " — " + text);
    }

    private void handleCancel() {
        if (cancelCallback != null) {
            cancelCallback.onCancelled();
        }
        notificationManager.cancel(NOTIFICATION_ID);
        stopForeground(true);
        stopSelf();
        Log.d(TAG, "Operação cancelada: " + currentOperation);
    }

    // ========================================
    //  NOTIFICATION
    // ========================================

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Operações de Arquivo",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Progresso de cópia, movimentação, compressão");
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(int progress, String description, boolean ongoing) {
        // Intent para abrir o app ao tocar na notificação
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingOpen = PendingIntent.getActivity(this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Intent para cancelar
        Intent cancelIntent = new Intent(this, FileManagerService.class);
        cancelIntent.setAction(ACTION_CANCEL);
        PendingIntent pendingCancel = PendingIntent.getService(this, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("FileManager — " + capitalize(currentOperation))
            .setContentText(description)
            .setContentIntent(pendingOpen)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true);

        if (ongoing && progress >= 0 && progress < 100) {
            builder.setProgress(100, progress, false);
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar", pendingCancel);
        }

        if (!ongoing) {
            builder.setAutoCancel(true);
        }

        return builder.build();
    }

    private String buildDescription(int progress, String currentFile) {
        StringBuilder sb = new StringBuilder();
        sb.append(progress).append("%");
        if (!currentFile.isEmpty()) {
            sb.append(" — ").append(truncate(currentFile, 30));
        }
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > 1000) {
            sb.append(" (").append(formatElapsed(elapsed)).append(")");
        }
        return sb.toString();
    }

    // ========================================
    //  HELPERS
    // ========================================

    private String formatElapsed(long ms) {
        long seconds = ms / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return minutes + "m " + seconds + "s";
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destruído");
        super.onDestroy();
    }

    // ========================================
    //  STATIC METHODS (conveniência)
    // ========================================

    public static void start(Context context, String operationType, String description) {
        Intent intent = new Intent(context, FileManagerService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_OPERATION_TYPE, operationType);
        intent.putExtra(EXTRA_DESCRIPTION, description);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void updateProgress(Context context, int progress, String currentFile) {
        Intent intent = new Intent(context, FileManagerService.class);
        intent.setAction(ACTION_UPDATE);
        intent.putExtra(EXTRA_PROGRESS, progress);
        intent.putExtra(EXTRA_CURRENT_FILE, currentFile);
        context.startService(intent);
    }

    public static void finish(Context context, boolean success, String message) {
        Intent intent = new Intent(context, FileManagerService.class);
        intent.setAction(ACTION_FINISH);
        intent.putExtra(EXTRA_SUCCESS, success);
        intent.putExtra(EXTRA_RESULT_MESSAGE, message);
        context.startService(intent);
    }

    public static void cancel(Context context) {
        Intent intent = new Intent(context, FileManagerService.class);
        intent.setAction(ACTION_CANCEL);
        context.startService(intent);
    }
}
