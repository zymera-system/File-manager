package com.filemanager.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * FileBridge - Ponte entre JavaScript e o sistema de arquivos nativo
 * 
 * Expõe funções via @JavascriptInterface para o WebView.
 * Permite que o JavaScript realize operações de arquivo no sistema nativo.
 */
public class FileBridge {

    private final Activity activity;

    public FileBridge(Activity activity) {
        this.activity = activity;
    }

    // ========================
    //  LISTAR ARQUIVOS
    // ========================

    /**
     * Lista arquivos e pastas em um diretório.
     * @param path Caminho absoluto ou relativo
     * @return JSON array com os itens encontrados
     */
    @JavascriptInterface
    public String listFiles(String path) {
        try {
            File dir = resolvePath(path);
            if (dir == null || !dir.exists()) {
                return errorJson("Diretório não encontrado: " + path);
            }
            if (!dir.isDirectory()) {
                return errorJson("Não é um diretório: " + path);
            }

            File[] files = dir.listFiles();
            if (files == null) {
                return errorJson("Sem permissão para acessar: " + path);
            }

            JSONArray result = new JSONArray();

            // Ordena: pastas primeiro, depois por nome
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    if (a.isDirectory() && !b.isDirectory()) return -1;
                    if (!a.isDirectory() && b.isDirectory()) return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

            for (File file : files) {
                JSONObject item = new JSONObject();
                item.put("name", file.getName());
                item.put("type", file.isDirectory() ? "folder" : "file");
                item.put("path", file.getAbsolutePath());
                item.put("size", file.isFile() ? formatSize(file.length()) : null);
                item.put("date", sdf.format(new Date(file.lastModified())));
                item.put("canRead", file.canRead());
                item.put("canWrite", file.canWrite());
                item.put("hidden", file.isHidden());
                result.put(item);
            }

            return result.toString();

        } catch (Exception e) {
            return errorJson("Erro ao processar: " + e.getMessage());
        }
    }

    // ========================
    //  CRIAR PASTA
    // ========================

    @JavascriptInterface
    public String createFolder(String parentPath, String folderName) {
        try {
            File parent = resolvePath(parentPath);
            if (parent == null || !parent.exists()) {
                return errorJson("Diretório pai não encontrado");
            }

            File newFolder = new File(parent, folderName);
            if (newFolder.exists()) {
                return errorJson("Já existe uma pasta com esse nome");
            }

            boolean created = newFolder.mkdirs();
            if (created) {
                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("path", newFolder.getAbsolutePath());
                return result.toString();
            } else {
                return errorJson("Não foi possível criar a pasta (verifique permissões)");
            }

        } catch (Exception e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    // ========================
    //  CRIAR ARQUIVO
    // ========================

    @JavascriptInterface
    public String createFile(String parentPath, String fileName) {
        try {
            File parent = resolvePath(parentPath);
            if (parent == null || !parent.exists()) {
                return errorJson("Diretório pai não encontrado");
            }

            File newFile = new File(parent, fileName);
            if (newFile.exists()) {
                return errorJson("Já existe um arquivo com esse nome");
            }

            boolean created = newFile.createNewFile();
            if (created) {
                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("path", newFile.getAbsolutePath());
                return result.toString();
            } else {
                return errorJson("Não foi possível criar o arquivo (verifique permissões)");
            }

        } catch (Exception e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    // ========================
    //  EXCLUIR ARQUIVO/PASTA
    // ========================

    @JavascriptInterface
    public String deleteItem(String path) {
        try {
            File file = resolvePath(path);
            if (file == null || !file.exists()) {
                return errorJson("Arquivo não encontrado");
            }

            boolean deleted = deleteRecursive(file);
            JSONObject result = new JSONObject();
            result.put("success", deleted);
            if (!deleted) {
                result.put("error", "Falha ao excluir (verifique permissões)");
            }
            return result.toString();

        } catch (Exception e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    // ========================
    //  COPIAR ARQUIVO/PASTA
    // ========================

    @JavascriptInterface
    public String copyFile(String sourcePath, String destPath) {
        try {
            File source = resolvePath(sourcePath);
            if (source == null || !source.exists()) {
                return errorJson("Arquivo origem não encontrado: " + sourcePath);
            }

            File dest = resolvePath(destPath);
            // Se destino é uma pasta, copiar para dentro dela
            if (dest.exists() && dest.isDirectory()) {
                dest = new File(dest, source.getName());
            }

            if (dest.exists()) {
                return errorJson("Já existe um item com esse nome no destino");
            }

            boolean success;
            if (source.isDirectory()) {
                success = copyDirectoryRecursive(source, dest);
            } else {
                success = copyFileRecursive(source, dest);
            }

            JSONObject result = new JSONObject();
            result.put("success", success);
            if (success) {
                result.put("path", dest.getAbsolutePath());
            } else {
                result.put("error", "Falha ao copiar");
            }
            return result.toString();

        } catch (Exception e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    private boolean copyFileRecursive(File source, File dest) {
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(source);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(dest);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fos.flush();
            fos.close();
            fis.close();
            dest.setLastModified(source.lastModified());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean copyDirectoryRecursive(File source, File dest) {
        if (!dest.mkdirs()) {
            // Pode falhar se já existe
            if (!dest.exists()) return false;
        }

        File[] files = source.listFiles();
        if (files != null) {
            for (File file : files) {
                File newDest = new File(dest, file.getName());
                if (file.isDirectory()) {
                    if (!copyDirectoryRecursive(file, newDest)) return false;
                } else {
                    if (!copyFileRecursive(file, newDest)) return false;
                }
            }
        }
        dest.setLastModified(source.lastModified());
        return true;
    }

    // ========================
    //  MOVER ARQUIVO/PASTA
    // ========================

    @JavascriptInterface
    public String moveFile(String sourcePath, String destPath) {
        try {
            File source = resolvePath(sourcePath);
            if (source == null || !source.exists()) {
                return errorJson("Arquivo origem não encontrado: " + sourcePath);
            }

            File dest = resolvePath(destPath);
            // Se destino é uma pasta, mover para dentro dela
            if (dest.exists() && dest.isDirectory()) {
                dest = new File(dest, source.getName());
            }

            if (dest.exists()) {
                return errorJson("Já existe um item com esse nome no destino");
            }

            // Tentar rename primeiro (mesmo disco = instantâneo)
            boolean success = source.renameTo(dest);

            if (!success) {
                // Dispositivos diferentes: copiar + deletar
                if (source.isDirectory()) {
                    success = copyDirectoryRecursive(source, dest);
                } else {
                    success = copyFileRecursive(source, dest);
                }
                if (success) {
                    deleteRecursive(source);
                }
            }

            JSONObject result = new JSONObject();
            result.put("success", success);
            if (success) {
                result.put("path", dest.getAbsolutePath());
            } else {
                result.put("error", "Falha ao mover");
            }
            return result.toString();

        } catch (Exception e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    // ========================
    //  RENOMEAR
    // ========================

    @JavascriptInterface
    public String renameItem(String path, String newName) {
        try {
            File file = resolvePath(path);
            if (file == null || !file.exists()) {
                return errorJson("Arquivo não encontrado");
            }

            File newFile = new File(file.getParent(), newName);
            if (newFile.exists()) {
                return errorJson("Já existe um item com esse nome");
            }

            boolean renamed = file.renameTo(newFile);
            JSONObject result = new JSONObject();
            result.put("success", renamed);
            if (renamed) {
                result.put("path", newFile.getAbsolutePath());
            } else {
                result.put("error", "Falha ao renomear");
            }
            return result.toString();

        } catch (Exception e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    // ========================
    //  INFORMAÇÕES DE ARMAZENAMENTO
    // ========================

    @JavascriptInterface
    public String getStorageInfo() {
        try {
            JSONObject info = new JSONObject();

            // Armazenamento interno
            File internalDir = activity.getFilesDir();
            StatFs internalStat = new StatFs(internalDir.getAbsolutePath());

            long internalTotal = internalStat.getTotalBytes();
            long internalFree = internalStat.getAvailableBytes();
            long internalUsed = internalTotal - internalFree;

            info.put("internal", buildStoragePart(internalTotal, internalFree, internalUsed));

            // Armazenamento externo
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File externalDir = Environment.getExternalStorageDirectory();
                StatFs externalStat = new StatFs(externalDir.getAbsolutePath());

                long externalTotal = externalStat.getTotalBytes();
                long externalFree = externalStat.getAvailableBytes();
                long externalUsed = externalTotal - externalFree;

                info.put("external", buildStoragePart(externalTotal, externalFree, externalUsed));

                // Combinado
                long totalAll = internalTotal + externalTotal;
                long freeAll = internalFree + externalFree;
                long usedAll = internalUsed + externalUsed;

                info.put("total", buildStoragePart(totalAll, freeAll, usedAll));
            } else {
                info.put("total", buildStoragePart(internalTotal, internalFree, internalUsed));
            }

            // Contar apps instalados
            info.put("appsCount", getInstalledAppsCount());

            return info.toString();

        } catch (Exception e) {
            return errorJson("Erro ao obter informações: " + e.getMessage());
        }
    }

    private JSONObject buildStoragePart(long total, long free, long used) throws JSONException {
        JSONObject part = new JSONObject();
        part.put("total", total);
        part.put("free", free);
        part.put("used", used);
        part.put("totalFormatted", formatSize(total));
        part.put("freeFormatted", formatSize(free));
        part.put("usedFormatted", formatSize(used));
        part.put("percentUsed", total > 0 ? Math.round((used * 100.0) / total) : 0);
        return part;
    }

    private int getInstalledAppsCount() {
        try {
            return activity.getPackageManager().getInstalledApplications(0).size();
        } catch (Exception e) {
            return 0;
        }
    }

    // ========================
    //  OBTER RAÍZ DO DISPOSITIVO
    // ========================

    @JavascriptInterface
    public String getRootPath() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    // ========================
    //  OBTER CAMINHOS PADRÃO
    // ========================

    @JavascriptInterface
    public String getStandardPaths() {
        try {
            JSONObject paths = new JSONObject();
            paths.put("root", Environment.getExternalStorageDirectory().getAbsolutePath());
            paths.put("download", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
            paths.put("dcim", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath());
            paths.put("documents", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath());
            paths.put("pictures", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath());
            paths.put("music", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath());
            paths.put("movies", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath());
            paths.put("appFiles", activity.getFilesDir().getAbsolutePath());
            paths.put("appCache", activity.getCacheDir().getAbsolutePath());
            return paths.toString();
        } catch (Exception e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    // ========================
    //  VERIFICAR SE ARQUIVO EXISTE
    // ========================

    @JavascriptInterface
    public boolean fileExists(String path) {
        File file = resolvePath(path);
        return file != null && file.exists();
    }

    // ========================
    //  OBTER TAMANHO DE ARQUIVO/PASTA
    // ========================

    @JavascriptInterface
    public String getItemSize(String path) {
        try {
            File file = resolvePath(path);
            if (file == null || !file.exists()) {
                return errorJson("Item não encontrado");
            }

            long size = file.isDirectory() ? getDirSize(file) : file.length();
            JSONObject result = new JSONObject();
            result.put("bytes", size);
            result.put("formatted", formatSize(size));
            return result.toString();

        } catch (Exception e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    private long getDirSize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    size += getDirSize(file);
                } else {
                    size += file.length();
                }
            }
        }
        return size;
    }

    // ========================
    //  UTILITÁRIOS
    // ========================

    /**
     * Resolve um caminho para um objeto File.
     * Suporta caminhos relativos (baseados no root do dispositivo).
     */
    private File resolvePath(String path) {
        if (path == null || path.isEmpty()) {
            return Environment.getExternalStorageDirectory();
        }

        // Se começa com /, é absoluto
        if (path.startsWith("/")) {
            return new File(path);
        }

        // Senão, relativo ao root
        return new File(Environment.getExternalStorageDirectory(), path);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String errorJson(String message) {
        try {
            JSONObject err = new JSONObject();
            err.put("error", true);
            err.put("message", message);
            return err.toString();
        } catch (JSONException e) {
            return "{\"error\":true,\"message\":\"Erro desconhecido\"}";
        }
    }

    // ========================
    //  SELF-TEST: valida cada método e retorna status
    // ========================

    /**
     * Executa auto-teste de todos os métodos da bridge.
     * @return JSON com status de cada método
     */
    @JavascriptInterface
    public String selfTest() {
        JSONObject result = new JSONObject();
        try {
            // Test 1: getRootPath
            try {
                String root = getRootPath();
                result.put("getRootPath", root != null && !root.isEmpty() ? "OK:" + root : "FAIL:empty");
            } catch (Exception e) {
                result.put("getRootPath", "FAIL:" + e.getMessage());
            }

            // Test 2: listFiles on root
            try {
                String root = getRootPath();
                String listResult = listFiles(root);
                if (listResult != null && listResult.startsWith("[")) {
                    org.json.JSONArray arr = new org.json.JSONArray(listResult);
                    result.put("listFiles_root", "OK:" + arr.length() + " items");
                } else {
                    result.put("listFiles_root", "FAIL:" + listResult);
                }
            } catch (Exception e) {
                result.put("listFiles_root", "FAIL:" + e.getMessage());
            }

            // Test 3: getStorageInfo
            try {
                String storageResult = getStorageInfo();
                if (storageResult != null && !storageResult.contains("\"error\":true")) {
                    JSONObject info = new JSONObject(storageResult);
                    long total = info.optJSONObject("total") != null ? info.getJSONObject("total").optLong("total", 0) : 0;
                    result.put("getStorageInfo", "OK:total=" + total);
                } else {
                    result.put("getStorageInfo", "FAIL:" + storageResult);
                }
            } catch (Exception e) {
                result.put("getStorageInfo", "FAIL:" + e.getMessage());
            }

            // Test 4: fileExists on root
            try {
                String root = getRootPath();
                boolean exists = fileExists(root);
                result.put("fileExists", exists ? "OK:true" : "FAIL:false");
            } catch (Exception e) {
                result.put("fileExists", "FAIL:" + e.getMessage());
            }

            // Test 5: listFiles on /Download (note: Android uses "Download" not "Downloads")
            try {
                String root = getRootPath();
                String downloadPath = root + "/Download";
                File downloadDir = new File(downloadPath);
                if (!downloadDir.exists()) {
                    downloadPath = root + "/Downloads";
                }
                String dlResult = listFiles(downloadPath);
                if (dlResult != null && dlResult.startsWith("[")) {
                    org.json.JSONArray arr = new org.json.JSONArray(dlResult);
                    result.put("listFiles_download", "OK:" + arr.length() + " items at " + downloadPath);
                } else {
                    result.put("listFiles_download", "FAIL:" + dlResult + " path=" + downloadPath);
                }
            } catch (Exception e) {
                result.put("listFiles_download", "FAIL:" + e.getMessage());
            }

            // Test 6: createFolder (temp test)
            try {
                String root = getRootPath();
                String testDir = root + "/.fm_bridge_test";
                File testFile = new File(testDir);
                boolean created = testFile.mkdirs();
                if (created || testFile.exists()) {
                    result.put("createFolder", "OK");
                    testFile.delete(); // cleanup
                } else {
                    result.put("createFolder", "FAIL:mkdirs returned false");
                }
            } catch (Exception e) {
                result.put("createFolder", "FAIL:" + e.getMessage());
            }

            // Test 7: isExternalStorageManager (permission check)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    boolean managed = Environment.isExternalStorageManager();
                    result.put("permission_managed_storage", managed ? "OK:granted" : "WARN:not_granted");
                } else {
                    result.put("permission_managed_storage", "OK:not_needed(API<" + Build.VERSION_CODES.R + ")");
                }
            } catch (Exception e) {
                result.put("permission_managed_storage", "FAIL:" + e.getMessage());
            }

            // Test 8: check READ/WRITE permissions
            try {
                boolean canRead = Environment.getExternalStorageDirectory().canRead();
                boolean canWrite = Environment.getExternalStorageDirectory().canWrite();
                result.put("permission_read", canRead ? "OK" : "FAIL");
                result.put("permission_write", canWrite ? "OK" : "FAIL");
            } catch (Exception e) {
                result.put("permission_check", "FAIL:" + e.getMessage());
            }

        } catch (Exception e) {
            try { result.put("global_error", e.getMessage()); } catch (Exception ignored) {}
        }
        return result.toString();
    }

    // ========================
    //  DIAGNÓSTICO
    // ========================

    /**
     * Salva logs de diagnóstico em arquivo.
     * @param logContent Conteúdo do log em texto
     * @return JSON com resultado
     */
    @JavascriptInterface
    public String saveDiagnosticLog(String logContent) {
        try {
            // Salvar no diretório de Downloads
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String filename = "diagnostic_log_" + sdf.format(new Date()) + ".txt";
            File logFile = new File(downloadsDir, filename);

            FileWriter writer = new FileWriter(logFile);
            writer.write(logContent);
            writer.flush();
            writer.close();

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("path", logFile.getAbsolutePath());
            result.put("filename", filename);
            return result.toString();

        } catch (IOException e) {
            return errorJson("Falha ao salvar log: " + e.getMessage());
        } catch (JSONException e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    /**
     * Salva log e prepara para compartilhamento.
     * @param logContent Conteúdo do log em texto
     */
    @JavascriptInterface
    public void shareDiagnosticLog(String logContent) {
        try {
            // Salvar primeiro
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String filename = "diagnostic_log_" + sdf.format(new Date()) + ".txt";
            File logFile = new File(downloadsDir, filename);

            FileWriter writer = new FileWriter(logFile);
            writer.write(logContent);
            writer.flush();
            writer.close();

            // Compartilhar via Intent
            final Uri fileUri = Uri.fromFile(logFile);
            final Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico FileManager");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Log de diagnóstico do FileManager");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.startActivity(Intent.createChooser(shareIntent, "Compartilhar diagnóstico"));
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
