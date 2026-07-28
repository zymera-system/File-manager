package com.filemanager.app.core;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * PermissionManager — Gerencia permissões de armazenamento de forma adaptativa.
 * 
 * Constantes de permissão usadas como strings literais para compatibilidade
 * com compileSdkVersion 30 (constantes nativas exigem SDK 33/34).
 */
public class PermissionManager {

    // Strings de permissão (compatíveis com qualquer SDK)
    private static final String PERM_READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES";
    private static final String PERM_READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO";
    private static final String PERM_READ_MEDIA_AUDIO = "android.permission.READ_MEDIA_AUDIO";
    private static final String PERM_READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED";
    private static final String PERM_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";

    public static final int REQUEST_STORAGE_LEGACY = 1001;
    public static final int REQUEST_MANAGE_STORAGE = 1002;
    public static final int REQUEST_MEDIA_GRANULAR = 1003;
    public static final int REQUEST_NOTIFICATIONS = 1004;

    private final Activity activity;
    private PermissionCallback callback;

    public interface PermissionCallback {
        void onPermissionGranted(String permission);
        void onPermissionDenied(String permission);
        void onRequiresManualGrant();
    }

    public PermissionManager(Activity activity) {
        this.activity = activity;
    }

    public void setCallback(PermissionCallback callback) {
        this.callback = callback;
    }

    // ========================================
    //  VERIFICAÇÃO DE PERMISSÕES
    // ========================================

    public boolean hasStoragePermission() {
        int sdk = Build.VERSION.SDK_INT;

        if (sdk >= 33) {
            // Android 13+: permissões granulares de mídia
            return hasPermission(PERM_READ_MEDIA_IMAGES)
                && hasPermission(PERM_READ_MEDIA_VIDEO)
                && hasPermission(PERM_READ_MEDIA_AUDIO);
        } else if (sdk >= 30) {
            // Android 11-12: MANAGE_EXTERNAL_STORAGE
            return Environment.isExternalStorageManager();
        } else {
            // Android ≤ 10: permissões legadas
            return hasPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                && hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    public boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(activity, permission) 
            == PackageManager.PERMISSION_GRANTED;
    }

    // ========================================
    //  SOLICITAÇÃO DE PERMISSÕES
    // ========================================

    public void requestStoragePermissions() {
        int sdk = Build.VERSION.SDK_INT;

        if (sdk >= 33) {
            requestMediaPermissions();
        } else if (sdk >= 30) {
            requestManageStoragePermission();
        } else {
            requestLegacyPermissions();
        }
    }

    private void requestLegacyPermissions() {
        String[] permissions = {
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        boolean needRequest = false;
        for (String perm : permissions) {
            if (!hasPermission(perm)) {
                needRequest = true;
                break;
            }
        }

        if (needRequest) {
            ActivityCompat.requestPermissions(activity, permissions, REQUEST_STORAGE_LEGACY);
        } else if (callback != null) {
            callback.onPermissionGranted("storage_legacy");
        }
    }

    private void requestManageStoragePermission() {
        if (!Environment.isExternalStorageManager()) {
            if (callback != null) {
                callback.onRequiresManualGrant();
            }
        } else if (callback != null) {
            callback.onPermissionGranted("manage_storage");
        }
    }

    public void openStorageSettings() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                android.content.Intent intent = new android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                );
                android.net.Uri uri = android.net.Uri.fromParts("package", 
                    activity.getPackageName(), null);
                intent.setData(uri);
                activity.startActivity(intent);
            } catch (Exception e) {
                android.content.Intent intent = new android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                );
                activity.startActivity(intent);
            }
        }
    }

    private void requestMediaPermissions() {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= 34) {
            permissions = new String[]{
                PERM_READ_MEDIA_IMAGES,
                PERM_READ_MEDIA_VIDEO,
                PERM_READ_MEDIA_AUDIO,
                PERM_READ_MEDIA_VISUAL_USER_SELECTED
            };
        } else {
            permissions = new String[]{
                PERM_READ_MEDIA_IMAGES,
                PERM_READ_MEDIA_VIDEO,
                PERM_READ_MEDIA_AUDIO
            };
        }

        boolean needRequest = false;
        for (String perm : permissions) {
            if (!hasPermission(perm)) {
                needRequest = true;
                break;
            }
        }

        if (needRequest) {
            ActivityCompat.requestPermissions(activity, permissions, REQUEST_MEDIA_GRANULAR);
        } else if (callback != null) {
            callback.onPermissionGranted("media_granular");
        }
    }

    public void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (!hasPermission(PERM_POST_NOTIFICATIONS)) {
                ActivityCompat.requestPermissions(activity,
                    new String[]{PERM_POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
            }
        }
    }

    // ========================================
    //  TRATAMENTO DE RESULTADO
    // ========================================

    public void handlePermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (callback == null) return;

        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            String type;
            switch (requestCode) {
                case REQUEST_STORAGE_LEGACY: type = "storage_legacy"; break;
                case REQUEST_MEDIA_GRANULAR: type = "media_granular"; break;
                case REQUEST_NOTIFICATIONS: type = "notifications"; break;
                default: type = "unknown"; break;
            }
            callback.onPermissionGranted(type);
        } else {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    callback.onPermissionDenied(permissions[i]);
                    break;
                }
            }
        }
    }

    public String getStatusJson() {
        try {
            org.json.JSONObject status = new org.json.JSONObject();
            int sdk = Build.VERSION.SDK_INT;

            status.put("sdkVersion", sdk);
            status.put("hasStoragePermission", hasStoragePermission());

            if (sdk >= 33) {
                status.put("strategy", "media_granular");
                status.put("hasReadImages", hasPermission(PERM_READ_MEDIA_IMAGES));
                status.put("hasReadVideo", hasPermission(PERM_READ_MEDIA_VIDEO));
                status.put("hasReadAudio", hasPermission(PERM_READ_MEDIA_AUDIO));
            } else if (sdk >= 30) {
                status.put("strategy", "manage_storage");
                status.put("isManager", Environment.isExternalStorageManager());
            } else {
                status.put("strategy", "legacy");
                status.put("hasRead", hasPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE));
                status.put("hasWrite", hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE));
            }

            return status.toString();
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + e.getMessage() + "\"}";
        }
    }
}
