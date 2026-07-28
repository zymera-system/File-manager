package com.filemanager.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.webkit.JavascriptInterface;

import com.filemanager.app.core.ArchiveManager;
import com.filemanager.app.core.DatabaseManager;
import com.filemanager.app.core.MediaPlayerManager;
import com.filemanager.app.core.ObserverManager;
import com.filemanager.app.core.OperationManager;
import com.filemanager.app.core.PermissionManager;
import com.filemanager.app.core.StorageDetector;
import com.filemanager.app.core.UIBridge;
import com.filemanager.app.service.FileManagerService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
