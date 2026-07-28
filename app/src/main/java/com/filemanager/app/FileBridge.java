package com.filemanager.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.webkit.JavascriptInterface;

import androidx.core.content.FileProvider;

import com.filemanager.app.core.ArchiveManager;
import com.filemanager.app.core.DatabaseManager;
import com.filemanager.app.core.MediaPlayerManager;
import com.filemanager.app.core.ObserverManager;
import com.filemanager.app.core.OperationManager;
import com.filemanager.app.core.PermissionManager;
import com.filemanager.app.core.StorageDetector;
import com.filemanager.app.core.TestManager;
import com.filemanager.app.core.UIBridge;
import com.filemanager.app.service.FileManagerService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * FileBridge — Ponte entre JavaScript e o sistema de arquivos nativo.
 *
 * Arquitetura modular (Fase 1):
 * - Delega permissões ao PermissionManager
 * - Delega operações async ao OperationManager
 * - Delega detecção de storage ao StorageDetector
 * - Delega persistência ao DatabaseManager
 * - Delega file watching ao ObserverManager
 *
 * TODOS os métodos públicos existentes permanecem intactos (backward compat).
 * Novos métodos são adicionados com prefixo novo_ ou gerenciados pelo nome.
 */
public class FileBridge {

    private static final String TAG = "FileBridge";
    public static final int REQUEST_UPLOAD_FILE = 2001;
    public static final int REQUEST_UPLOAD_MULTIPLE = 2002;

    private final Activity activity;

    // Core managers (inicializados lazy via init())
    private PermissionManager permissionManager;
    private OperationManager operationManager;
    private StorageDetector storageDetector;
    private DatabaseManager databaseManager;
    private ObserverManager observerManager;
    private ArchiveManager archiveManager;
    private MediaPlayerManager mediaPlayerManager;
    private UIBridge uiBridge;
    private TestManager testManager;
    private boolean initialized = false;

    public FileBridge(Activity activity) {
        this.activity = activity;
    }

    /**
     * Inicializa todos os core managers.
     * Chamar APÓS obter permissões iniciais.
     */
    public void init() {
        if (initialized) return;

        permissionManager = new PermissionManager(activity);
        operationManager = new OperationManager();
        storageDetector = new StorageDetector(activity.getApplicationContext());
        databaseManager = DatabaseManager.getInstance(activity.getApplicationContext());
        observerManager = new ObserverManager();
        archiveManager = new ArchiveManager(activity.getApplicationContext());
        mediaPlayerManager = new MediaPlayerManager(activity);
        uiBridge = new UIBridge(activity);
        testManager = new TestManager(activity.getApplicationContext(), activity);

        // Configurar cancelamento via Service
        FileManagerService.setCancelCallback(() -> operationManager.cancelAll());

        initialized = true;
    }

    /**
     * Libera recursos dos managers.
     * Chamar no onDestroy da Activity.
     */
    public void destroy() {
        if (operationManager != null) operationManager.shutdown();
        if (observerManager != null) observerManager.stopAll();
        if (uiBridge != null) uiBridge.dismissAll();
        initialized = false;
    }

    // ========================================
    //  ACESSO AOS MANAGERS (para MainActivity)
    // ========================================

    public PermissionManager permissions() { return permissionManager; }
    public OperationManager operations() { return operationManager; }
    public StorageDetector storage() { return storageDetector; }
    public DatabaseManager database() { return databaseManager; }
    public ObserverManager observers() { return observerManager; }
    public ArchiveManager archives() { return archiveManager; }
    public MediaPlayerManager media() { return mediaPlayerManager; }
    public UIBridge ui() { return uiBridge; }

    // ========================
    //  LISTAR ARQUIVOS
    // ========================

    @JavascriptInterface
    public String listFiles(String path) {
        return listFilesPaged(path, 0, -1);
    }

    /**
     * listFilesPaged — Retorna uma página de arquivos do diretório.
     *
     * @param path   Caminho do diretório
     * @param offset Índice inicial (0 = primeiro item)
     * @param limit  Número máximo de itens (-1 = todos)
     * @return JSON: { items: [...], total: N, hasMore: bool }
     */
    @JavascriptInterface
    public String listFilesPaged(String path, int offset, int limit) {
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

            // Ordenar: pastas primeiro, depois por nome
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    if (a.isDirectory() && !b.isDirectory()) return -1;
                    if (!a.isDirectory() && b.isDirectory()) return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });

            int total = files.length;
            boolean loadAll = (limit <= 0);
            int from = Math.max(0, offset);
            int to = loadAll ? total : Math.min(total, offset + limit);
            if (from >= total) {
                // Offset além do total — retornar vazio
                JSONObject result = new JSONObject();
                result.put("items", new JSONArray());
                result.put("total", total);
                result.put("hasMore", false);
                result.put("offset", offset);
                return result.toString();
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            JSONArray arr = new JSONArray();

            for (int i = from; i < to; i++) {
                File file = files[i];
                JSONObject item = new JSONObject();
                item.put("name", file.getName());
                item.put("type", file.isDirectory() ? "folder" : "file");
                item.put("path", file.getAbsolutePath());
                item.put("size", file.isFile() ? formatSize(file.length()) : null);
                item.put("date", sdf.format(new Date(file.lastModified())));
                item.put("canRead", file.canRead());
                item.put("canWrite", file.canWrite());
                item.put("hidden", file.isHidden());
                arr.put(item);
            }

            JSONObject result = new JSONObject();
            result.put("items", arr);
            result.put("total", total);
            result.put("hasMore", to < total);
            result.put("offset", offset);
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
            if (dest.exists() && dest.isDirectory()) {
                dest = new File(dest, source.getName());
            }

            if (dest.exists()) {
                return errorJson("Já existe um item com esse nome no destino");
            }

            boolean success = source.renameTo(dest);

            if (!success) {
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
        // Delegar ao StorageDetector se disponível
        if (storageDetector != null) {
            return storageDetector.getPrimaryVolumeJson();
        }

        // Fallback: implementação original
        try {
            JSONObject info = new JSONObject();

            File internalDir = activity.getFilesDir();
            StatFs internalStat = new StatFs(internalDir.getAbsolutePath());

            long internalTotal = internalStat.getTotalBytes();
            long internalFree = internalStat.getAvailableBytes();
            long internalUsed = internalTotal - internalFree;

            info.put("internal", buildStoragePart(internalTotal, internalFree, internalUsed));

            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File externalDir = Environment.getExternalStorageDirectory();
                StatFs externalStat = new StatFs(externalDir.getAbsolutePath());

                long externalTotal = externalStat.getTotalBytes();
                long externalFree = externalStat.getAvailableBytes();
                long externalUsed = externalTotal - externalFree;

                info.put("external", buildStoragePart(externalTotal, externalFree, externalUsed));

                long totalAll = internalTotal + externalTotal;
                long freeAll = internalFree + externalFree;
                long usedAll = internalUsed + externalUsed;

                info.put("total", buildStoragePart(totalAll, freeAll, usedAll));
            } else {
                info.put("total", buildStoragePart(internalTotal, internalFree, internalUsed));
            }

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

    /**
     * Retorna contagem de apps sem iterar detalhes completos.
     */
    private int getInstalledAppsCountFast() {
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

    // ========================================
    //  INFO DETALHADA DE ARQUIVO/PASTA
    // ========================================

    /**
     * Retorna informações detalhadas de um arquivo ou pasta.
     */
    @JavascriptInterface
    public String getFileInfo(String path) {
        try {
            File file = resolvePath(path);
            if (file == null || !file.exists()) {
                return errorJson("Item não encontrado");
            }

            JSONObject info = new JSONObject();
            info.put("name", file.getName());
            info.put("path", file.getAbsolutePath());
            info.put("isDirectory", file.isDirectory());
            info.put("isFile", file.isFile());
            info.put("isHidden", file.isHidden());
            info.put("canRead", file.canRead());
            info.put("canWrite", file.canWrite());
            info.put("canExecute", file.canExecute());
            info.put("lastModified", file.lastModified());
            info.put("lastModifiedDate", new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(file.lastModified())));

            if (file.isFile()) {
                info.put("size", file.length());
                info.put("sizeFormatted", formatSize(file.length()));
                String ext = getExtension(file.getName()).toLowerCase();
                info.put("extension", ext);
                info.put("mimeType", java.net.URLConnection.guessContentTypeFromName(file.getName()));
            } else {
                long size = getDirSize(file);
                int childCount = 0;
                File[] children = file.listFiles();
                if (children != null) childCount = children.length;
                info.put("size", size);
                info.put("sizeFormatted", formatSize(size));
                info.put("childCount", childCount);
                info.put("extension", "");
            }

            return info.toString();
        } catch (Exception e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    private String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return (dot >= 0) ? name.substring(dot + 1) : "";
    }

    // ========================================
    //  NOVOS MÉTODOS — PERMISSÕES
    // ========================================

    /**
     * Verifica se tem permissão de storage.
     */
    @JavascriptInterface
    public String checkPermission() {
        if (permissionManager == null) init();
        return permissionManager.getStatusJson();
    }

    /**
     * Solicita permissões de storage (adaptativo por versão).
     */
    @JavascriptInterface
    public void requestPermission() {
        if (permissionManager == null) init();
        permissionManager.requestStoragePermissions();
    }

    /**
     * Abre configurações de "Todos os arquivos" (Android 11+).
     */
    @JavascriptInterface
    public void openStorageSettings() {
        if (permissionManager == null) init();
        permissionManager.openStorageSettings();
    }

    // ========================================
    //  NOVOS MÉTODOS — OPERAÇÕES ASSÍNCRONAS
    // ========================================

    /**
     * Copia arquivo/pasta em background com progresso.
     * @return taskId para polling
     */
    @JavascriptInterface
    public String asyncCopy(String sourcePath, String destPath) {
        if (operationManager == null) init();

        File sourceFile = new File(sourcePath);
        String desc = "Copiando " + sourceFile.getName() + "...";
        FileManagerService.start(activity, "copy", desc);

        return operationManager.submit("copy", (taskInfo) -> {
            File source = new File(sourcePath);
            File dest = new File(destPath);
            if (dest.isDirectory()) {
                dest = new File(dest, source.getName());
            }

            boolean result;
            if (source.isDirectory()) {
                result = copyDirWithProgress(source, dest, taskInfo);
            } else {
                result = copyFileWithProgress(source, dest, taskInfo);
            }

            FileManagerService.finish(activity, result,
                result ? source.getName() + " copiado" : "Falha ao copiar");
            return result;
        });
    }

    /**
     * Move arquivo/pasta em background com progresso.
     */
    @JavascriptInterface
    public String asyncMove(String sourcePath, String destPath) {
        if (operationManager == null) init();

        File sourceFile = new File(sourcePath);
        String desc = "Movendo " + sourceFile.getName() + "...";
        FileManagerService.start(activity, "move", desc);

        return operationManager.submit("move", (taskInfo) -> {
            File source = new File(sourcePath);
            File dest = new File(destPath);
            if (dest.isDirectory()) {
                dest = new File(dest, source.getName());
            }

            // Tentar rename primeiro
            if (source.renameTo(dest)) {
                OperationManager.updateProgress(taskInfo, 1, 1, source.getName());
                FileManagerService.finish(activity, true, source.getName() + " movido");
                return true;
            }

            // Copiar + deletar
            boolean success;
            if (source.isDirectory()) {
                success = copyDirWithProgress(source, dest, taskInfo);
            } else {
                success = copyFileWithProgress(source, dest, taskInfo);
            }
            if (success) {
                OperationManager.checkCancellation(taskInfo);
                deleteRecursive(source);
            }
            FileManagerService.finish(activity, success,
                success ? source.getName() + " movido" : "Falha ao mover");
            return success;
        });
    }

    @JavascriptInterface
    public String asyncDelete(String path) {
        if (operationManager == null) init();

        File deleteFile = new File(path);
        String desc = "Excluindo " + deleteFile.getName() + "...";
        FileManagerService.start(activity, "delete", desc);

        return operationManager.submit("delete", (taskInfo) -> {
            File file = new File(path);
            if (!file.exists()) {
                taskInfo.errorMessage = "File not found";
                return false;
            }
            return deleteWithProgress(file, taskInfo);
        });
    }

    /**
     * Cancela uma operação assíncrona pelo taskId.
     */
    @JavascriptInterface
    public boolean cancelOperation(String taskId) {
        if (operationManager == null) return false;
        return operationManager.cancel(taskId);
    }

    /**
     * Cancela todas as operações ativas.
     */
    @JavascriptInterface
    public void cancelAllOperations() {
        if (operationManager == null) return;
        operationManager.cancelAll();
    }

    /**
     * Poll de progresso de uma operação assíncrona.
     */
    @JavascriptInterface
    public String pollProgress(String taskId) {
        if (operationManager == null) init();
        return operationManager.getProgressJson(taskId);
    }

    /**
     * Lista operações ativas.
     */
    @JavascriptInterface
    public String getActiveOperations() {
        if (operationManager == null) init();
        return operationManager.getActiveTasksJson();
    }

    // ========================================
    //  NOVOS MÉTODOS — DETECÇÃO DE STORAGE
    // ========================================

    /**
     * Lista todos os volumes de armazenamento.
     */
    @JavascriptInterface
    public String getStorageVolumes() {
        if (storageDetector == null) init();
        return storageDetector.getAllVolumesJson();
    }

    /**
     * Retorna espaço de um path específico.
     */
    @JavascriptInterface
    public String getPathSpace(String path) {
        if (storageDetector == null) init();
        return storageDetector.getSpaceJson(path);
    }

    // ========================================
    //  NOVOS MÉTODOS — DATABASE (BOOKMARKS)
    // ========================================

    /**
     * Adiciona um favorito.
     */
    @JavascriptInterface
    public String addFavorite(String path, String name) {
        if (databaseManager == null) init();
        try {
            long id = databaseManager.addBookmark(path, name, DatabaseManager.TYPE_FAVORITE, null);
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("id", id);
            return result.toString();
        } catch (Exception e) {
            return errorJson(e.getMessage());
        }
    }

    /**
     * Remove um favorito.
     */
    @JavascriptInterface
    public String removeFavorite(String path) {
        if (databaseManager == null) init();
        int deleted = databaseManager.removeBookmark(path, DatabaseManager.TYPE_FAVORITE);
        try {
            JSONObject result = new JSONObject();
            result.put("success", deleted > 0);
            return result.toString();
        } catch (Exception e) {
            return errorJson(e.getMessage());
        }
    }

    /**
     * Lista todos os favoritos.
     */
    @JavascriptInterface
    public String getFavorites() {
        if (databaseManager == null) init();
        return databaseManager.getBookmarksJson(DatabaseManager.TYPE_FAVORITE);
    }

    /**
     * Verifica se um path é favorito.
     */
    @JavascriptInterface
    public boolean isFavorite(String path) {
        if (databaseManager == null) init();
        return databaseManager.isBookmark(path, DatabaseManager.TYPE_FAVORITE);
    }

    /**
     * Adiciona ao histórico.
     */
    @JavascriptInterface
    public void addToHistory(String path, String name) {
        if (databaseManager == null) init();
        databaseManager.addToHistory(path, name);
    }

    /**
     * Retorna histórico.
     */
    @JavascriptInterface
    public String getHistory() {
        if (databaseManager == null) init();
        return databaseManager.getBookmarksJson(DatabaseManager.TYPE_HISTORY);
    }

    /**
     * Limpa histórico.
     */
    @JavascriptInterface
    public void clearHistory() {
        if (databaseManager == null) init();
        databaseManager.clearHistory();
    }

    // ========================================
    //  NOVOS MÉTODOS — PIN (FIXAR)
    // ========================================

    /**
     * Fixa (pin) um arquivo/pasta no DatabaseManager (TYPE_BOOKMARK).
     */
    @JavascriptInterface
    public String pinFile(String path, String name) {
        try {
            if (databaseManager == null) init();
            long id = databaseManager.addBookmark(path, name, DatabaseManager.TYPE_BOOKMARK, null);
            JSONObject result = new JSONObject();
            result.put("success", id > 0);
            result.put("id", id);
            result.put("pinned", true);
            return result.toString();
        } catch (Exception e) {
            return errorJson("Erro ao fixar: " + e.getMessage());
        }
    }

    /**
     * Remove fixação (unpin) de um arquivo.
     */
    @JavascriptInterface
    public String unpinFile(String path) {
        try {
            if (databaseManager == null) init();
            int deleted = databaseManager.removeBookmark(path, DatabaseManager.TYPE_BOOKMARK);
            JSONObject result = new JSONObject();
            result.put("success", deleted > 0);
            result.put("pinned", false);
            return result.toString();
        } catch (Exception e) {
            return errorJson("Erro ao desafixar: " + e.getMessage());
        }
    }

    /**
     * Verifica se um arquivo está fixado (pinned).
     */
    @JavascriptInterface
    public boolean isPinned(String path) {
        if (databaseManager == null) init();
        return databaseManager.isBookmark(path, DatabaseManager.TYPE_BOOKMARK);
    }

    /**
     * Retorna todos os arquivos fixados.
     */
    @JavascriptInterface
    public String getPinnedFiles() {
        if (databaseManager == null) init();
        return databaseManager.getBookmarksJson(DatabaseManager.TYPE_BOOKMARK);
    }

    // ========================================
    //  NOVOS MÉTODOS — SHORTCUT (ATALHO)
    // ========================================

    /**
     * Cria atalho na tela inicial do Android (7.1+).
     * Para versões anteriores, cria um marcador no banco.
     */
    @JavascriptInterface
    public String createShortcut(String path, String name) {
        try {
            File file = resolvePath(path);
            if (file == null || !file.exists()) {
                return errorJson("Arquivo não encontrado");
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                // Android 7.1+: ShortcutManager
                android.content.pm.ShortcutManager sm =
                    activity.getSystemService(android.content.pm.ShortcutManager.class);
                if (sm != null && sm.isRequestPinShortcutSupported()) {
                    String shortcutId = "fm_" + name.replaceAll("[^a-zA-Z0-9]", "_");

                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(
                        FileProvider.getUriForFile(activity, activity.getPackageName() + ".provider", file),
                        "*/*"
                    );
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    android.content.pm.ShortcutInfo shortcut = new android.content.pm.ShortcutInfo.Builder(activity, shortcutId)
                        .setShortLabel(name)
                        .setLongLabel(path)
                        .setIntent(intent)
                        .build();

                    sm.requestPinShortcut(shortcut, null);

                    JSONObject result = new JSONObject();
                    result.put("success", true);
                    result.put("shortcutId", shortcutId);
                    result.put("method", "ShortcutManager");
                    return result.toString();
                }
            }

            // Fallback: salvar como bookmark TYPE_BOOKMARK
            if (databaseManager == null) init();
            long id = databaseManager.addBookmark(path, name, DatabaseManager.TYPE_BOOKMARK,
                "{\"type\":\"shortcut\"}");
            JSONObject result = new JSONObject();
            result.put("success", id > 0);
            result.put("id", id);
            result.put("method", "bookmark");
            return result.toString();

        } catch (Exception e) {
            return errorJson("Erro ao criar atalho: " + e.getMessage());
        }
    }

    // ========================================
    //  NOVOS MÉTODOS — FILE OBSERVER
    // ========================================

    /**
     * Inicia monitoramento de um diretório.
     */
    @JavascriptInterface
    public boolean watchDirectory(String path) {
        if (observerManager == null) init();
        return observerManager.startWatching(path);
    }

    /**
     * Para monitoramento de um diretório.
     */
    @JavascriptInterface
    public boolean unwatchDirectory(String path) {
        if (observerManager == null) init();
        return observerManager.stopWatching(path);
    }

    /**
     * Poll de eventos de mudança no filesystem.
     */
    @JavascriptInterface
    public String pollFsEvents() {
        if (observerManager == null) init();
        return observerManager.pollEvents();
    }

    /**
     * Status do file observer.
     */
    @JavascriptInterface
    public String getObserverStatus() {
        if (observerManager == null) init();
        return observerManager.getStatusJson();
    }

    // ========================================
    //  NOVOS MÉTODOS — COMPRESSÃO/DESCOMPRESSÃO
    // ========================================

    /**
     * Comprime arquivos em ZIP.
     */
    @JavascriptInterface
    public String compressZip(String filesJson, String outputZip) {
        if (archiveManager == null) init();

        try {
            JSONArray paths = new JSONArray(filesJson);
            File[] files = new File[paths.length()];
            for (int i = 0; i < paths.length(); i++) {
                files[i] = new File(paths.getString(i));
            }

            // Iniciar Service para notificação
            FileManagerService.start(activity, "compress", "Comprimindo " + paths.length() + " arquivos...");

            return operationManager.submit("compress", (taskInfo) -> {
                boolean result = archiveManager.compressToZip(files, new File(outputZip), null);
                OperationManager.updateProgress(taskInfo, 100, 100, "done");
                return result;
            });

        } catch (Exception e) {
            return errorJson(e.getMessage());
        }
    }

    /**
     * Descomprime um ZIP.
     */
    @JavascriptInterface
    public String extractZip(String zipPath, String destDir) {
        if (archiveManager == null) init();

        FileManagerService.start(activity, "extract", "Extraindo " + ArchiveManager.getExtension(zipPath).toUpperCase() + "...");

        return operationManager.submit("extract", (taskInfo) -> {
            boolean result = archiveManager.extractZip(new File(zipPath), new File(destDir), null);
            return result;
        });
    }

    /**
     * Lista conteúdo de um ZIP.
     */
    @JavascriptInterface
    public String listArchiveContents(String archivePath) {
        if (archiveManager == null) init();
        return archiveManager.listZipContents(archivePath);
    }

    /**
     * Informações de um arquivo compactado.
     */
    @JavascriptInterface
    public String getArchiveInfo(String archivePath) {
        if (archiveManager == null) init();
        return archiveManager.getArchiveInfo(archivePath);
    }

    // ========================================
    //  NOVOS MÉTODOS — LIXEIRA
    // ========================================

    /**
     * Move arquivo/pasta para lixeira (.trash/).
     * Não exclui permanentemente — permite restauração.
     * Registra no DatabaseManager para persistência entre sessões.
     */
    @JavascriptInterface
    public String trashItem(String path) {
        try {
            File file = resolvePath(path);
            if (file == null || !file.exists()) {
                return errorJson("Arquivo não encontrado");
            }

            File root = new File(getRootPath());
            File trashDir = new File(root, ".trash");
            if (!trashDir.exists()) trashDir.mkdirs();

            // Timestamp prefix para evitar colisões de nome
            String safeName = System.currentTimeMillis() + "_" + file.getName();
            File dest = new File(trashDir, safeName);

            boolean moved = file.renameTo(dest);
            if (!moved) {
                // Fallback: copiar + deletar
                moved = copyFileRecursive(file, dest);
                if (moved) file.delete();
            }

            if (moved) {
                // Registrar no DatabaseManager para persistência
                if (databaseManager != null) {
                    try {
                        databaseManager.addBookmark(
                            dest.getAbsolutePath(),
                            file.getName(),
                            DatabaseManager.TYPE_TRASH,
                            "{\"originalPath\":\"" + file.getAbsolutePath() + "\",\"trashPath\":\"" + dest.getAbsolutePath() + "\"}"
                        );
                    } catch (Exception dbErr) {
                        Log.w(TAG, "Erro ao registrar lixeira no DB: " + dbErr.getMessage());
                    }
                }

                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("trashPath", dest.getAbsolutePath());
                result.put("originalPath", file.getAbsolutePath());
                return result.toString();
            } else {
                return errorJson("Não foi possível mover para lixeira");
            }
        } catch (Exception e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    /**
     * Restaura arquivo da lixeira para o caminho original.
     */
    @JavascriptInterface
    public String restoreTrashItem(String trashPath, String originalPath) {
        try {
            File trashFile = new File(trashPath);
            if (!trashFile.exists()) {
                return errorJson("Arquivo não encontrado na lixeira");
            }

            File dest = new File(originalPath);
            File parentDir = dest.getParentFile();
            if (parentDir != null && !parentDir.exists()) parentDir.mkdirs();

            // Se já existe no destino, adicionar suffix
            if (dest.exists()) {
                String baseName = dest.getName().replaceFirst("\\.[^.]+$", "");
                String ext = dest.getName().contains(".") ? "." + dest.getName().substring(dest.getName().lastIndexOf('.') + 1) : "";
                int counter = 1;
                while (dest.exists()) {
                    dest = new File(parentDir, baseName + " (" + counter + ")" + ext);
                    counter++;
                }
            }

            boolean moved = trashFile.renameTo(dest);
            if (!moved) {
                moved = copyFileRecursive(trashFile, dest);
                if (moved) trashFile.delete();
            }

            if (moved) {
                // Remover do DatabaseManager
                if (databaseManager != null) {
                    try {
                        databaseManager.removeBookmark(trashPath, DatabaseManager.TYPE_TRASH);
                    } catch (Exception dbErr) {
                        Log.w(TAG, "Erro ao remover lixeira do DB: " + dbErr.getMessage());
                    }
                }

                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("restoredPath", dest.getAbsolutePath());
                return result.toString();
            } else {
                return errorJson("Não foi possível restaurar");
            }
        } catch (Exception e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    /**
     * Exclui permanentemente da lixeira.
     */
    @JavascriptInterface
    public String permanentDeleteTrash(String trashPath) {
        try {
            File file = new File(trashPath);
            if (!file.exists()) {
                return errorJson("Arquivo não encontrado");
            }
            boolean deleted = file.isDirectory() ? deleteRecursive(file) : file.delete();
            if (deleted) {
                // Remover do DatabaseManager
                if (databaseManager != null) {
                    try {
                        databaseManager.removeBookmark(trashPath, DatabaseManager.TYPE_TRASH);
                    } catch (Exception dbErr) {
                        Log.w(TAG, "Erro ao remover lixeira do DB: " + dbErr.getMessage());
                    }
                }

                JSONObject result = new JSONObject();
                result.put("success", true);
                return result.toString();
            } else {
                return errorJson("Não foi possível excluir");
            }
        } catch (Exception e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    /**
     * Lista arquivos na lixeira.
     */
    @JavascriptInterface
    public String listTrashItems() {
        try {
            File root = new File(getRootPath());
            File trashDir = new File(root, ".trash");
            if (!trashDir.exists()) {
                return "[]";
            }

            File[] files = trashDir.listFiles();
            if (files == null || files.length == 0) {
                return "[]";
            }

            JSONArray arr = new JSONArray();
            for (File file : files) {
                JSONObject item = new JSONObject();
                item.put("name", file.getName());
                item.put("path", file.getAbsolutePath());
                item.put("isDirectory", file.isDirectory());
                item.put("size", file.isDirectory() ? getDirSize(file) : file.length());
                item.put("lastModified", file.lastModified());
                arr.put(item);
            }
            return arr.toString();
        } catch (Exception e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    // ========================================
    //  NOVOS MÉTODOS — MÍDIA
    // ========================================

    /**
     * Abre um arquivo de mídia com player nativo.
     */
    @JavascriptInterface
    public String openMedia(String filePath) {
        if (mediaPlayerManager == null) init();
        return mediaPlayerManager.openMedia(filePath);
    }

    /**
     * Abre arquivo com app específico.
     */
    @JavascriptInterface
    public String openWith(String filePath, String packageName) {
        if (mediaPlayerManager == null) init();
        return mediaPlayerManager.openWith(filePath, packageName);
    }

    /**
     * Informações de mídia.
     */
    @JavascriptInterface
    public String getMediaInfo(String filePath) {
        if (mediaPlayerManager == null) init();
        return mediaPlayerManager.getMediaInfo(filePath);
    }

    /**
     * Scan de mídia para galeria.
     */
    @JavascriptInterface
    public String scanMedia(String filePath) {
        if (mediaPlayerManager == null) init();
        return mediaPlayerManager.scanMedia(filePath);
    }

    /**
     * Compartilha arquivo.
     */
    @JavascriptInterface
    public String shareFile(String filePath) {
        if (mediaPlayerManager == null) init();
        return mediaPlayerManager.shareFile(filePath);
    }

    /**
     * Compartilha múltiplos arquivos.
     */
    @JavascriptInterface
    public String shareMultiple(String pathsJson) {
        if (mediaPlayerManager == null) init();
        return mediaPlayerManager.shareMultiple(pathsJson);
    }

    // ========================================
    //  NOVOS MÉTODOS — APPS INSTALADOS
    // ========================================

    /**
     * Retorna lista de apps instalados.
     * @param includeSystem true para incluir apps do sistema
     */
    @JavascriptInterface
    public String getInstalledApps(boolean includeSystem) {
        try {
            android.content.pm.PackageManager pm = activity.getPackageManager();
            java.util.List<PackageInfo> packages = pm.getInstalledPackages(0);
            JSONArray apps = new JSONArray();

            for (PackageInfo pkg : packages) {
                boolean isSystem = (pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (isSystem && !includeSystem) continue;

                JSONObject app = new JSONObject();
                app.put("name", pkg.applicationInfo.loadLabel(pm).toString());
                app.put("packageName", pkg.packageName);
                app.put("versionName", pkg.versionName != null ? pkg.versionName : "—");
                app.put("versionCode", pkg.versionCode);
                app.put("isSystem", isSystem);

                // Tamanho do APK
                try {
                    String apkPath = pkg.applicationInfo.sourceDir;
                    if (apkPath != null) {
                        File apkFile = new File(apkPath);
                        app.put("size", apkFile.length());
                        app.put("sizeFormatted", formatSize(apkFile.length()));
                        app.put("apkPath", apkPath);
                    }
                } catch (Exception ignored) {}

                // Data de instalação
                try {
                    app.put("firstInstall", pkg.firstInstallTime);
                    app.put("lastUpdate", pkg.lastUpdateTime);
                } catch (Exception ignored) {}

                apps.put(app);
            }

            return apps.toString();
        } catch (Exception e) {
            return errorJson("Erro ao listar apps: " + e.getMessage());
        }
    }

    /**
     * Desinstala um app pelo package name.
     */
    @JavascriptInterface
    public String uninstallApp(String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
            intent.setData(Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("packageName", packageName);
            return result.toString();
        } catch (Exception e) {
            return errorJson("Erro ao desinstalar: " + e.getMessage());
        }
    }

    /**
     * Backup do APK de um app.
     */
    @JavascriptInterface
    public String backupApk(String packageName, String destDir) {
        try {
            android.content.pm.PackageManager pm = activity.getPackageManager();
            PackageInfo pkg = pm.getPackageInfo(packageName, 0);
            String apkPath = pkg.applicationInfo.sourceDir;
            if (apkPath == null) return errorJson("APK não encontrado");

            File source = new File(apkPath);
            File destDirFile = new File(destDir);
            if (!destDirFile.exists()) destDirFile.mkdirs();
            File dest = new File(destDirFile, packageName + ".apk");

            java.io.InputStream in = new java.io.FileInputStream(source);
            java.io.OutputStream out = new java.io.FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("backupPath", dest.getAbsolutePath());
            result.put("size", dest.length());
            return result.toString();
        } catch (Exception e) {
            return errorJson("Erro ao fazer backup: " + e.getMessage());
        }
    }

    // ========================================
    //  UPLOAD — Seletor de arquivos
    // ========================================

    private String pendingUploadPath;

    /**
     * Abre o seletor de arquivos para upload de um único arquivo.
     * @param destPath caminho de destino na interface
     */
    @JavascriptInterface
    public void pickAndUploadFile(String destPath) {
        this.pendingUploadPath = destPath;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        activity.startActivityForResult(intent, REQUEST_UPLOAD_FILE);
    }

    /**
     * Abre o seletor de arquivos para upload de múltiplos arquivos.
     * @param destPath caminho de destino na interface
     */
    @JavascriptInterface
    public void pickAndUploadMultiple(String destPath) {
        this.pendingUploadPath = destPath;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        activity.startActivityForResult(intent, REQUEST_UPLOAD_MULTIPLE);
    }

    /**
     * Processa o resultado do upload. Chamado de MainActivity.onActivityResult.
     */
    public void handleUploadResult(int requestCode, int resultCode, Intent data) {
        if (pendingUploadPath == null) return;
        if (resultCode != Activity.RESULT_OK || data == null) {
            pendingUploadPath = null;
            notifyUploadResult(false, "Operação cancelada", 0);
            return;
        }

        try {
            int count = 0;
            if (requestCode == REQUEST_UPLOAD_MULTIPLE && data.getClipData() != null) {
                int clipCount = data.getClipData().getItemCount();
                for (int i = 0; i < clipCount; i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    if (copyUriToPath(uri, pendingUploadPath)) count++;
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                if (copyUriToPath(uri, pendingUploadPath)) count++;
            }

            pendingUploadPath = null;
            notifyUploadResult(true, count + " arquivo(s) enviado(s)", count);
        } catch (Exception e) {
            pendingUploadPath = null;
            notifyUploadResult(false, "Erro: " + e.getMessage(), 0);
        }
    }

    private boolean copyUriToPath(Uri uri, String destPath) {
        try {
            java.io.InputStream is = activity.getContentResolver().openInputStream(uri);
            if (is == null) return false;

            String fileName = getFileNameFromUri(uri);
            File destFile = new File(resolvePath(destPath), fileName);
            if (destFile.exists()) {
                String base = fileName.replaceFirst("\\.[^.]+$", "");
                String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";
                int counter = 1;
                while (destFile.exists()) {
                    destFile = new File(destFile.getParent(), base + " (" + counter + ")" + ext);
                    counter++;
                }
            }

            java.io.OutputStream os = new java.io.FileOutputStream(destFile);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.close();
            is.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao copiar URI para destino: " + e.getMessage());
            return false;
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String displayName = "uploaded_file";
        try (android.database.Cursor cursor = activity.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex);
                }
            }
        } catch (Exception ignored) {}
        return displayName;
    }

    private void notifyUploadResult(boolean success, String message, int count) {
        String json = "{success:" + success + ",message:\"" + message.replace("\"", "\\\"") + "\",count:" + count + "}";
        final String script = "if (window.fmOnUploadComplete) window.fmOnUploadComplete('" + json.replace("'", "\\'") + "');";
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).evaluateJavascript(script);
                }
            }
        });
    }

    // ========================================
    //  NOVOS MÉTODOS — UI NATIVA
    // ========================================

    /**
     * Toast rápido.
     */
    @JavascriptInterface
    public void showToast(String message) {
        if (uiBridge == null) init();
        uiBridge.showToast(message);
    }

    /**
     * Diálogo de confirmação.
     */
    @JavascriptInterface
    public void showConfirmDialog(String title, String message, String positiveLabel,
                                   String negativeLabel, String callbackName) {
        if (uiBridge == null) init();
        uiBridge.showConfirmDialog(title, message, positiveLabel, negativeLabel, callbackName);
    }

    /**
     * Input dialog (renomear, criar pasta).
     */
    @JavascriptInterface
    public void showInputDialog(String title, String hint, String defaultValue,
                                 int inputType, String callbackName) {
        if (uiBridge == null) init();
        uiBridge.showInputDialog(title, hint, defaultValue, inputType, callbackName);
    }

    /**
     * Bottom sheet de opções.
     */
    @JavascriptInterface
    public void showOptionsSheet(String title, String optionsJson, String callbackName) {
        if (uiBridge == null) init();
        uiBridge.showOptionsSheet(title, optionsJson, callbackName);
    }

    /**
     * Overlay de progresso.
     */
    @JavascriptInterface
    public void showProgressOverlay(String message, int progress) {
        if (uiBridge == null) init();
        uiBridge.showProgressOverlay(message, progress);
    }

    /**
     * Atualiza overlay de progresso.
     */
    @JavascriptInterface
    public void updateProgressOverlay(int progress, String message) {
        if (uiBridge == null) init();
        uiBridge.updateProgressOverlay(progress, message);
    }

    /**
     * Esconde overlay de progresso.
     */
    @JavascriptInterface
    public void hideProgressOverlay() {
        if (uiBridge == null) init();
        uiBridge.hideProgressOverlay();
    }

    /**
     * Diálogo de informação.
     */
    @JavascriptInterface
    public void showInfoDialog(String title, String message) {
        if (uiBridge == null) init();
        uiBridge.showInfoDialog(title, message);
    }

    // ========================================
    //  NOVOS MÉTODOS — TESTES
    // ========================================

    /**
     * Executa suite completa de testes.
     */
    @JavascriptInterface
    public String runTests() {
        if (testManager == null) init();
        return testManager.runAllTests();
    }

    // ========================================
    //  NOVOS MÉTODOS — SERVICE
    // ========================================

    /**
     * Inicia foreground service para operação longa.
     */
    @JavascriptInterface
    public void startService(String operationType, String description) {
        FileManagerService.start(activity, operationType, description);
    }

    /**
     * Atualiza progresso do service.
     */
    @JavascriptInterface
    public void updateServiceProgress(int progress, String currentFile) {
        FileManagerService.updateProgress(activity, progress, currentFile);
    }

    /**
     * Finaliza o service.
     */
    @JavascriptInterface
    public void finishService(boolean success, String message) {
        FileManagerService.finish(activity, success, message);
    }

    // ========================================
    //  NOVOS MÉTODOS — STATUS GERAL
    // ========================================

    /**
     * Status completo da aplicação (todos os managers).
     */
    @JavascriptInterface
    public String getSystemStatus() {
        if (!initialized) init();
        try {
            JSONObject status = new JSONObject();
            status.put("permissions", new JSONObject(permissionManager != null ? permissionManager.getStatusJson() : "{}"));
            status.put("activeOperations", operationManager != null ? operationManager.getActiveTaskCount() : 0);
            status.put("observers", new JSONObject(observerManager != null ? observerManager.getStatusJson() : "{}"));
            status.put("databaseStats", new JSONObject(databaseManager != null ? databaseManager.getStatsJson() : "{}"));
            status.put("archiveManager", archiveManager != null ? "ready" : "null");
            status.put("mediaPlayer", mediaPlayerManager != null ? "ready" : "null");
            status.put("uiBridge", uiBridge != null ? "ready" : "null");
            status.put("version", "2.0.0");
            return status.toString();
        } catch (Exception e) {
            return errorJson(e.getMessage());
        }
    }

    // ========================================
    //  HELPERS DE PROGRESSO (para operações async)
    // ========================================

    /**
     * Copia arquivo com progresso e cancelamento.
     */
    private boolean copyFileWithProgress(File source, File dest, OperationManager.TaskInfo taskInfo) throws OperationManager.CancellationException {
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(source);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(dest);

            long totalSize = source.length();
            long copied = 0;
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                OperationManager.checkCancellation(taskInfo);
                fos.write(buffer, 0, bytesRead);
                copied += bytesRead;
                OperationManager.updateProgress(taskInfo, (int)(copied), (int)totalSize, source.getName());
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

    /**
     * Copia diretório com progresso.
     */
    private boolean copyDirWithProgress(File source, File dest, OperationManager.TaskInfo taskInfo) throws OperationManager.CancellationException {
        // Contar arquivos totais para progresso
        int totalFiles = countFiles(source);
        int[] processed = {0};

        return copyDirWithProgressInner(source, dest, taskInfo, totalFiles, processed);
    }

    private boolean copyDirWithProgressInner(File source, File dest, OperationManager.TaskInfo taskInfo, int totalFiles, int[] processed) throws OperationManager.CancellationException {
        if (!dest.mkdirs() && !dest.exists()) return false;

        File[] files = source.listFiles();
        if (files != null) {
            for (File file : files) {
                OperationManager.checkCancellation(taskInfo);
                File newDest = new File(dest, file.getName());
                boolean ok;
                if (file.isDirectory()) {
                    ok = copyDirWithProgressInner(file, newDest, taskInfo, totalFiles, processed);
                } else {
                    ok = copyFileWithProgress(file, newDest, taskInfo);
                }
                if (!ok) return false;
                processed[0]++;
                OperationManager.updateProgress(taskInfo, processed[0], totalFiles, file.getName());
            }
        }
        dest.setLastModified(source.lastModified());
        return true;
    }

    /**
     * Deleta com progresso (conta itens deletados).
     */
    private boolean deleteWithProgress(File file, OperationManager.TaskInfo taskInfo) throws OperationManager.CancellationException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                int total = children.length;
                int processed = 0;
                for (File child : children) {
                    OperationManager.checkCancellation(taskInfo);
                    deleteWithProgress(child, taskInfo);
                    processed++;
                    OperationManager.updateProgress(taskInfo, processed, total, child.getName());
                }
            }
        }
        return file.delete();
    }

    private int countFiles(File dir) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    count += countFiles(file);
                } else {
                    count++;
                }
            }
        }
        return count > 0 ? count : 1;
    }

    // ========================
    //  UTILITÁRIOS
    // ========================

    private File resolvePath(String path) {
        // Usar StorageDetector se disponível
        if (storageDetector != null && path != null && !path.startsWith("/")) {
            String resolved = storageDetector.resolveVirtualPath(path);
            if (resolved != null) return new File(resolved);
        }

        if (path == null || path.isEmpty()) {
            return Environment.getExternalStorageDirectory();
        }

        if (path.startsWith("/")) {
            return new File(path);
        }

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
    //  SELF-TEST
    // ========================

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
                    JSONArray arr = new JSONArray(listResult);
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
                    long total = info.optJSONObject("total") != null
                        ? info.getJSONObject("total").optLong("total", 0)
                        : info.optLong("totalBytes", 0);
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

            // Test 5: listFiles on /Download
            try {
                String root = getRootPath();
                String downloadPath = root + "/Download";
                File downloadDir = new File(downloadPath);
                if (!downloadDir.exists()) {
                    downloadPath = root + "/Downloads";
                }
                String dlResult = listFiles(downloadPath);
                if (dlResult != null && dlResult.startsWith("[")) {
                    JSONArray arr = new JSONArray(dlResult);
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
                    testFile.delete();
                } else {
                    result.put("createFolder", "FAIL:mkdirs returned false");
                }
            } catch (Exception e) {
                result.put("createFolder", "FAIL:" + e.getMessage());
            }

            // Test 7: permission check
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

            // Test 8: read/write check
            try {
                boolean canRead = Environment.getExternalStorageDirectory().canRead();
                boolean canWrite = Environment.getExternalStorageDirectory().canWrite();
                result.put("permission_read", canRead ? "OK" : "FAIL");
                result.put("permission_write", canWrite ? "OK" : "FAIL");
            } catch (Exception e) {
                result.put("permission_check", "FAIL:" + e.getMessage());
            }

            // Test 9: managers
            if (initialized) {
                result.put("permissionManager", "OK");
                result.put("operationManager", "OK:active=" + operationManager.getActiveTaskCount());
                result.put("storageDetector", "OK");
                result.put("databaseManager", "OK");
                result.put("observerManager", "OK");
                result.put("archiveManager", archiveManager != null ? "OK" : "FAIL");
                result.put("mediaPlayerManager", mediaPlayerManager != null ? "OK" : "FAIL");
                result.put("uiBridge", uiBridge != null ? "OK" : "FAIL");
                result.put("testManager", testManager != null ? "OK" : "FAIL");
            } else {
                result.put("managers", "WARN:not_initialized");
            }

        } catch (Exception e) {
            try { result.put("global_error", e.getMessage()); } catch (Exception ignored) {}
        }
        return result.toString();
    }

    // ========================
    //  DIAGNÓSTICO
    // ========================

    @JavascriptInterface
    public String saveDiagnosticLog(String logContent) {
        try {
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

    @JavascriptInterface
    public void shareDiagnosticLog(String logContent) {
        try {
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

            final Uri fileUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                fileUri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    logFile
                );
            } else {
                fileUri = Uri.fromFile(logFile);
            }
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
