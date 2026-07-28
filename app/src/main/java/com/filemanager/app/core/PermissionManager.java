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
 * Estratégia em 3 camadas (inspirada no File Manager+):
 * 
 * Camada 1 — Android ≤ 10 (SDK ≤ 29):
 *   READ_EXTERNAL_STORAGE + WRITE_EXTERNAL_STORAGE
 *   + requestLegacyExternalStorage="true"
 * 
 * Camada 2 — Android 11-12 (SDK 30-32):
 *   MANAGE_EXTERNAL_STORAGE (All Files Access)
 *   Usuário é redirecionado para as configurações do sistema.
 * 
 * Camada 3 — Android 13+ (SDK ≥ 33):
 *   Permissões granulares de mídia:
 *   READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_AUDIO
 *   + POST_NOTIFICATIONS (se necessário)
 */
public class PermissionManager {

    public static final int REQUEST_STORAGE_LEGACY = 1001;
    public static final int REQUEST_MANAGE_STORAGE = 1002;
    public static final int REQUEST_MEDIA_GRANULAR = 1003;
    public static final int REQUEST_NOTIFICATIONS = 1004;

    private final Activity activity;
    private PermissionCallback callback;

    public interface PermissionCallback {
        void onPermissionGranted(String permission);
        void onPermissionDenied(String permission);
        void onRequiresManualGrant(); // Para MANAGE_EXTERNAL_STORAGE
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

    /**
     * Verifica se tem permissão de leitura/escrita no storage externo.
     * Adapta a verificação conforme a versão do Android.
     */
    public boolean hasStoragePermission() {
        int sdk = Build.VERSION.SDK_INT;

        if (sdk >= Build.VERSION_CODES.R) {
            // Android 11+: verificar MANAGE_EXTERNAL_STORAGE
            return Environment.isExternalStorageManager();
        } else if (sdk >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: permissões granulares
            return hasPermission(android.Manifest.permission.READ_MEDIA_IMAGES)
                && hasPermission(android.Manifest.permission.READ_MEDIA_VIDEO)
                && hasPermission(android.Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            // Android ≤ 12: permissões legadas
            return hasPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                && hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    /**
     * Verifica se tem permissão específica.
     */
    public boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(activity, permission) 
            == PackageManager.PERMISSION_GRANTED;
    }

    // ========================================
    //  SOLICITAÇÃO DE PERMISSÕES
    // ========================================

    /**
     * Solicita permissões de storage de forma adaptativa.
     * Chamar no onCreate ou quando a permissão for necessária.
     */
    public void requestStoragePermissions() {
        int sdk = Build.VERSION.SDK_INT;

        if (sdk >= Build.VERSION_CODES.R) {
            // Android 11+: redirecionar para configurações
            requestManageStoragePermission();
        } else if (sdk >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: permissões granulares de mídia
            requestMediaPermissions();
        } else {
            // Android ≤ 12: permissões legadas
            requestLegacyPermissions();
        }
    }

    /**
     * Camada 1 — Permissões legadas (Android ≤ 12)
     */
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

    /**
     * Camada 2 — MANAGE_EXTERNAL_STORAGE (Android 11+)
     * Redireciona o usuário para as configurações do sistema.
     */
    private void requestManageStoragePermission() {
        if (!Environment.isExternalStorageManager()) {
            if (callback != null) {
                callback.onRequiresManualGrant();
            }
            // O app deve chamar openStorageSettings() quando o usuário clicar
        } else if (callback != null) {
            callback.onPermissionGranted("manage_storage");
        }
    }

    /**
     * Abre a tela de configurações de "Todos os arquivos" (Android 11+).
     * Chamar quando o usuário clicar no botão de conceder permissão.
     */
    public void openStorageSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                android.content.Intent intent = new android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                );
                android.net.Uri uri = android.net.Uri.fromParts("package", 
                    activity.getPackageName(), null);
                intent.setData(uri);
                activity.startActivity(intent);
            } catch (Exception e) {
                // Fallback: abrir todas as configurações
                android.content.Intent intent = new android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                );
                activity.startActivity(intent);
            }
        }
    }

    /**
     * Camada 3 — Permissões granulares de mídia (Android 13+)
     */
    private void requestMediaPermissions() {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+: com leitura parcial
            permissions = new String[]{
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_AUDIO,
                android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            };
        } else {
            // Android 13
            permissions = new String[]{
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_AUDIO
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

    /**
     * Solicita permissão de notificações (Android 13+).
     */
    public void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (!hasPermission(android.Manifest.permission.POST_NOTIFICATIONS)) {
                ActivityCompat.requestPermissions(activity,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
            }
        }
    }

    // ========================================
    //  TRATAMENTO DE RESULTADO
    // ========================================

    /**
     * Chamar dentro de onRequestPermissionsResult da Activity.
     */
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
            // Identificar qual foi negada
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    callback.onPermissionDenied(permissions[i]);
                    break;
                }
            }
        }
    }

    /**
     * Retorna status das permissões em JSON.
     */
    public String getStatusJson() {
        try {
            org.json.JSONObject status = new org.json.JSONObject();
            int sdk = Build.VERSION.SDK_INT;

            status.put("sdkVersion", sdk);
            status.put("hasStoragePermission", hasStoragePermission());

            if (sdk >= Build.VERSION_CODES.R) {
                status.put("strategy", "manage_storage");
                status.put("isManager", Environment.isExternalStorageManager());
            } else if (sdk >= Build.VERSION_CODES.TIRAMISU) {
                status.put("strategy", "media_granular");
                status.put("hasReadImages", hasPermission(android.Manifest.permission.READ_MEDIA_IMAGES));
                status.put("hasReadVideo", hasPermission(android.Manifest.permission.READ_MEDIA_VIDEO));
                status.put("hasReadAudio", hasPermission(android.Manifest.permission.READ_MEDIA_AUDIO));
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
