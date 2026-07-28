package com.filemanager.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.webkit.JavascriptInterface;

import androidx.core.content.FileProvider;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

/**
 * Gerencia atualizações do app.
 * - Verifica versão disponível em URL remota
 * - Baixa APK de atualização
 * - Instala APK via Intent
 */
public class UpdateManager {

    private static final String TAG = "UpdateManager";
    private final Activity activity;

    // URL do JSON de versão (configurável pelo JS)
    private String updateUrl = "https://raw.githubusercontent.com/seu-usuario/seu-repo/main/update.json";

    // ID do download em andamento
    private long downloadId = -1;

    public UpdateManager(Activity activity) {
        this.activity = activity;
    }

    // ========================================
    //  FILE PROVIDER HELPER
    // ========================================

    private Uri getFileUri(File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".fileprovider",
                file
            );
        }
        return Uri.fromFile(file);
    }

    // ========================
    //  OBTER VERSÃO ATUAL
    // ========================

    @JavascriptInterface
    public String getCurrentVersion() {
        try {
            PackageInfo pInfo = activity.getPackageManager()
                .getPackageInfo(activity.getPackageName(), 0);
            JSONObject info = new JSONObject();
            info.put("versionName", pInfo.versionName);
            info.put("versionCode", pInfo.versionCode);
            return info.toString();
        } catch (Exception e) {
            return "{\"versionName\":\"1.0.0\",\"versionCode\":1}";
        }
    }

    // ========================
    //  CONFIGURAR URL DE ATUALIZAÇÃO
    // ========================

    @JavascriptInterface
    public void setUpdateUrl(String url) {
        this.updateUrl = url;
        Log.d(TAG, "Update URL configurada: " + url);
    }

    // ========================
    //  OBTER URL DE ATUALIZAÇÃO
    // ========================

    @JavascriptInterface
    public String getUpdateUrl() {
        return this.updateUrl;
    }

    // ========================
    //  VERIFICAR ATUALIZAÇÃO
    // ========================

    /**
     * Verifica se há atualização disponível.
     * O JS deve chamar esta função e processar o JSON de resposta.
     *
     * O JSON retornado tem o formato:
     * {
     *   "updateAvailable": true/false,
     *   "latestVersion": "1.1.0",
     *   "latestVersionCode": 2,
     *   "downloadUrl": "https://...",
     *   "changelog": "..."
     * }
     *
     * IMPORTANTE: Esta função faz uma requisição HTTP síncrona.
     * Use em background thread no JS ou chame apenas quando necessário.
     */
    @JavascriptInterface
    public String checkForUpdate() {
        try {
            // Obtém versão atual
            PackageInfo pInfo = activity.getPackageManager()
                .getPackageInfo(activity.getPackageName(), 0);
            int currentVersionCode = pInfo.versionCode;

            // Faz request HTTP para verificar versão
            // Nota: Em produção, isso deveria ser feito em background
            java.net.URL url = new java.net.URL(updateUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return buildUpdateResult(false, pInfo.versionName, currentVersionCode,
                    null, null, "Servidor retornou código " + responseCode);
            }

            // Lê a resposta
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            conn.disconnect();

            // Parse do JSON
            JSONObject remote = new JSONObject(response.toString());
            int latestVersionCode = remote.getInt("versionCode");
            String latestVersion = remote.getString("versionName");
            String downloadUrl = remote.optString("downloadUrl", null);
            String changelog = remote.optString("changelog", "");

            boolean updateAvailable = latestVersionCode > currentVersionCode;

            return buildUpdateResult(updateAvailable, latestVersion, latestVersionCode,
                downloadUrl, changelog, null);

        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar atualização", e);
            try {
                PackageInfo pInfo = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0);
                return buildUpdateResult(false, pInfo.versionName, pInfo.versionCode,
                    null, null, "Erro de conexão: " + e.getMessage());
            } catch (Exception ex) {
                return "{\"updateAvailable\":false,\"error\":\"" + e.getMessage() + "\"}";
            }
        }
    }

    // ========================
    //  BAIXAR ATUALIZAÇÃO
    // ========================

    /**
     * Baixa o APK de atualização usando o DownloadManager do Android.
     * @param url URL do APK para baixar
     */
    @JavascriptInterface
    public void downloadUpdate(String url) {
        if (url == null || url.isEmpty()) {
            notifyJs("onDownloadError", "URL de download inválida");
            return;
        }

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle("Atualizando File Manager");
            request.setDescription("Baixando nova versão...");
            request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS, "filemanager-update.apk");

            // Permite download apenas via Wi-Fi (opcional)
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);

            DownloadManager downloadManager = (DownloadManager)
                activity.getSystemService(Context.DOWNLOAD_SERVICE);
            downloadId = downloadManager.enqueue(request);

            // Registra receiver para quando o download terminar
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            activity.registerReceiver(downloadReceiver, filter);

            notifyJs("onDownloadStart", "Download iniciado");
            Log.d(TAG, "Download iniciado, ID: " + downloadId);

        } catch (Exception e) {
            Log.e(TAG, "Erro ao iniciar download", e);
            notifyJs("onDownloadError", "Erro ao iniciar download: " + e.getMessage());
        }
    }

    // ========================
    //  INSTALAR APK
    // ========================

    /**
     * Instala um APK baixado.
     * @param apkPath Caminho completo do arquivo APK
     */
    @JavascriptInterface
    public void installApk(String apkPath) {
        try {
            File apkFile = new File(apkPath);
            if (!apkFile.exists()) {
                notifyJs("onInstallError", "Arquivo APK não encontrado");
                return;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Use FileProvider for Android 7+
            intent.setDataAndType(
                getFileUri(apkFile),
                "application/vnd.android.package-archive"
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            activity.startActivity(intent);
            Log.d(TAG, "Instalação iniciada: " + apkPath);

        } catch (Exception e) {
            Log.e(TAG, "Erro ao instalar APK", e);
            notifyJs("onInstallError", "Erro ao abrir instalação: " + e.getMessage());
        }
    }

    // ========================
    //  CANCELAR DOWNLOAD
    // ========================

    @JavascriptInterface
    public void cancelDownload() {
        if (downloadId != -1) {
            DownloadManager dm = (DownloadManager)
                activity.getSystemService(Context.DOWNLOAD_SERVICE);
            dm.remove(downloadId);
            downloadId = -1;
            notifyJs("onDownloadCancelled", "Download cancelado");
        }
    }

    // ========================
    //  OBTER STATUS DO DOWNLOAD
    // ========================

    @JavascriptInterface
    public String getDownloadStatus() {
        if (downloadId == -1) {
            return "{\"status\":\"none\"}";
        }

        try {
            DownloadManager dm = (DownloadManager)
                activity.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);
            Cursor cursor = dm.query(query);

            if (cursor.moveToFirst()) {
                int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int status = cursor.getInt(statusIdx);

                JSONObject result = new JSONObject();
                switch (status) {
                    case DownloadManager.STATUS_RUNNING:
                        int downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                        int totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                        long downloaded = cursor.getLong(downloadedIdx);
                        long total = cursor.getLong(totalIdx);

                        result.put("status", "running");
                        result.put("downloaded", downloaded);
                        result.put("total", total);
                        result.put("percent", total > 0 ? Math.round((downloaded * 100.0) / total) : 0);
                        break;
                    case DownloadManager.STATUS_SUCCESSFUL:
                        int pathIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                        result.put("status", "completed");
                        result.put("path", cursor.getString(pathIdx));
                        break;
                    case DownloadManager.STATUS_FAILED:
                        result.put("status", "failed");
                        break;
                    case DownloadManager.STATUS_PENDING:
                        result.put("status", "pending");
                        break;
                    case DownloadManager.STATUS_PAUSED:
                        result.put("status", "paused");
                        break;
                }
                cursor.close();
                return result.toString();
            }
            cursor.close();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar status", e);
        }

        return "{\"status\":\"unknown\"}";
    }

    // ========================
    //  ABRIR LOJA / SITE
    // ========================

    @JavascriptInterface
    public void openUpdatePage(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao abrir URL", e);
        }
    }

    // ========================
    //  UTILITÁRIOS INTERNOS
    // ========================

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id == downloadId) {
                DownloadManager dm = (DownloadManager)
                    context.getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(id);
                Cursor cursor = dm.query(query);

                if (cursor.moveToFirst()) {
                    int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int status = cursor.getInt(statusIdx);

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        int pathIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                        String uriString = cursor.getString(pathIdx);

                        if (uriString != null) {
                            Uri uri = Uri.parse(uriString);
                            String filePath = uri.getPath();

                            // Tenta corrigir caminho no Android 10+
                            if (filePath == null || !new File(filePath).exists()) {
                                filePath = Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_DOWNLOADS) + "/filemanager-update.apk";
                            }

                            notifyJs("onDownloadComplete", filePath);
                            Log.d(TAG, "Download completo: " + filePath);
                        }
                    } else {
                        notifyJs("onDownloadError", "Download falhou");
                    }
                }
                cursor.close();

                try {
                    activity.unregisterReceiver(this);
                } catch (Exception e) {
                    // Receiver já foi removido
                }
            }
        }
    };

    private String buildUpdateResult(boolean available, String version, int versionCode,
                                      String downloadUrl, String changelog, String error) {
        try {
            JSONObject result = new JSONObject();
            result.put("updateAvailable", available);
            result.put("latestVersion", version);
            result.put("latestVersionCode", versionCode);
            result.put("downloadUrl", downloadUrl);
            result.put("changelog", changelog);
            if (error != null) result.put("error", error);
            return result.toString();
        } catch (JSONException e) {
            return "{\"updateAvailable\":false,\"error\":\"JSON error\"}";
        }
    }

    private void notifyJs(String callback, String data) {
        try {
            final String js = "javascript:" + callback + "('" +
                data.replace("'", "\\'") + "')";
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Obtém o WebView da Activity
                    if (activity instanceof MainActivity) {
                        ((MainActivity) activity).evaluateJavascript(js);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Erro ao notificar JS", e);
        }
    }
}
