package com.filemanager.app.core;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * TestManager — Suite de testes automatizados para todos os managers.
 *
 * Executa testes de integração e validação de cada módulo:
 * - PermissionManager: verificação de permissões
 * - OperationManager: submissão, cancelamento, progresso
 * - StorageDetector: detecção de volumes
 * - DatabaseManager: CRUD de bookmarks
 * - ObserverManager: start/stop/poll
 * - ArchiveManager: compressão/descompressão
 * - FileManagerService: lifecycle
 *
 * Uso:
 *   TestManager tm = new TestManager(context, activity);
 *   String results = tm.runAllTests();
 */
public class TestManager {

    private static final String TAG = "TestManager";
    private final Context context;
    private final Activity activity;
    private int passed = 0;
    private int failed = 0;
    private int total = 0;

    public TestManager(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;
    }

    /**
     * Executa todos os testes e retorna resultados em JSON.
     */
    public String runAllTests() {
        passed = 0;
        failed = 0;
        total = 0;

        try {
            JSONObject results = new JSONObject();

            // Testes de cada manager
            results.put("permissionManager", testPermissionManager());
            results.put("operationManager", testOperationManager());
            results.put("storageDetector", testStorageDetector());
            results.put("databaseManager", testDatabaseManager());
            results.put("observerManager", testObserverManager());
            results.put("archiveManager", testArchiveManager());
            results.put("fileBridge", testFileBridge());

            // Resumo
            results.put("summary", buildSummary());

            return results.toString();

        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    // ========================================
    //  TESTES POR MANAGER
    // ========================================

    private JSONObject testPermissionManager() throws Exception {
        JSONObject r = new JSONObject();
        PermissionManager pm = new PermissionManager(activity);

        // Test 1: getStatusJson
        test("PM_getStatus", () -> {
            String json = pm.getStatusJson();
            assert json != null && !json.isEmpty() : "Status vazio";
            assert json.contains("sdkVersion") : "Sem sdkVersion";
        });
        r.put("getStatus", "OK");

        // Test 2: hasStoragePermission
        test("PM_hasStorage", () -> {
            boolean has = pm.hasStoragePermission();
            // Não podemos afirmar true/false sem saber o estado do dispositivo
            Log.d(TAG, "  hasStoragePermission=" + has);
        });
        r.put("hasStoragePermission", "OK");

        // Test 3: getStatusJson contém campos corretos
        test("PM_statusFields", () -> {
            String json = pm.getStatusJson();
            JSONObject obj = new JSONObject(json);
            assert obj.has("sdkVersion") : "Falta sdkVersion";
            assert obj.has("hasStoragePermission") : "Falta hasStoragePermission";
        });
        r.put("statusFields", "OK");

        return r;
    }

    private JSONObject testOperationManager() throws Exception {
        JSONObject r = new JSONObject();
        OperationManager om = new OperationManager();

        // Test 1: Submissão simples
        test("OM_submit", () -> {
            String taskId = om.submit("test", (taskInfo) -> {
                OperationManager.updateProgress(taskInfo, 50, 100, "test.txt");
                return true;
            });
            assert taskId != null && !taskId.isEmpty() : "TaskId vazio";
            assert taskId.startsWith("task_") : "TaskId formato inválido: " + taskId;
        });
        r.put("submit", "OK");

        // Test 2: Progresso
        test("OM_progress", () -> {
            String taskId = om.submit("test_progress", (taskInfo) -> {
                for (int i = 0; i <= 100; i += 10) {
                    OperationManager.updateProgress(taskInfo, i, 100, "file_" + i + ".txt");
                    Thread.sleep(10);
                }
                return true;
            });

            Thread.sleep(50); // Esperar um pouco

            String json = om.getProgressJson(taskId);
            JSONObject obj = new JSONObject(json);
            assert obj.has("progress") : "Falta progress";
            assert obj.has("taskId") : "Falta taskId";
        });
        r.put("progress", "OK");

        // Test 3: Cancelamento
        test("OM_cancel", () -> {
            String taskId = om.submit("test_cancel", (taskInfo) -> {
                for (int i = 0; i < 1000; i++) {
                    OperationManager.checkCancellation(taskInfo);
                    Thread.sleep(1);
                }
                return true;
            });

            Thread.sleep(20);
            boolean cancelled = om.cancel(taskId);
            assert cancelled : "Não foi possível cancelar";

            Thread.sleep(100);
            String json = om.getProgressJson(taskId);
            JSONObject obj = new JSONObject(json);
            assert obj.getBoolean("cancelled") || obj.getBoolean("completed") :
                "Operação não foi cancelada nem completada";
        });
        r.put("cancel", "OK");

        // Test 4: Active tasks
        test("OM_activeTasks", () -> {
            int active = om.getActiveTaskCount();
            assert active >= 0 : "Contagem negativa: " + active;
        });
        r.put("activeTasks", "OK");

        // Cleanup
        om.shutdown();

        return r;
    }

    private JSONObject testStorageDetector() throws Exception {
        JSONObject r = new JSONObject();
        StorageDetector sd = new StorageDetector(context);

        // Test 1: Detectar volumes
        test("SD_detectVolumes", () -> {
            var volumes = sd.detectAllVolumes();
            assert volumes != null && !volumes.isEmpty() : "Nenhum volume detectado";
            assert volumes.get(0).id.equals("internal") : "Primeiro volume não é internal";
        });
        r.put("detectVolumes", "OK");

        // Test 2: Primary volume
        test("SD_primaryVolume", () -> {
            String json = sd.getPrimaryVolumeJson();
            assert json != null && !json.isEmpty() : "Primary volume JSON vazio";
            JSONObject obj = new JSONObject(json);
            assert obj.has("totalBytes") : "Falta totalBytes";
            assert obj.getLong("totalBytes") > 0 : "totalBytes zero";
        });
        r.put("primaryVolume", "OK");

        // Test 3: Space info
        test("SD_spaceInfo", () -> {
            String root = Environment.getExternalStorageDirectory().getAbsolutePath();
            String json = sd.getSpaceJson(root);
            assert json != null : "Space JSON nulo";
            JSONObject obj = new JSONObject(json);
            assert obj.has("totalBytes") : "Falta totalBytes";
            assert obj.has("freeBytes") : "Falta freeBytes";
            assert obj.getLong("totalBytes") > obj.getLong("freeBytes") : "total < free";
        });
        r.put("spaceInfo", "OK");

        // Test 4: Virtual path resolution
        test("SD_virtualPath", () -> {
            String real = sd.resolveVirtualPath("internal:/Download");
            assert real != null : "Resolve retornou null";
            assert real.contains("/Download") : "Resolve não contém /Download: " + real;
        });
        r.put("virtualPath", "OK");

        // Test 5: All volumes JSON
        test("SD_allVolumes", () -> {
            String json = sd.getAllVolumesJson();
            JSONObject obj = new JSONObject(json);
            assert obj.has("count") : "Falta count";
            assert obj.getInt("count") >= 1 : "Menos de 1 volume";
        });
        r.put("allVolumes", "OK");

        return r;
    }

    private JSONObject testDatabaseManager() throws Exception {
        JSONObject r = new JSONObject();
        DatabaseManager db = DatabaseManager.getInstance(context);

        // Limpar dados de teste anteriores
        db.clearAll();

        // Test 1: Adicionar favorito
        test("DB_addFavorite", () -> {
            long id = db.addBookmark("/storage/emulated/0/Download", "Download",
                DatabaseManager.TYPE_FAVORITE, null);
            assert id > 0 : "ID inválido: " + id;
        });
        r.put("addFavorite", "OK");

        // Test 2: Verificar favorito
        test("DB_isFavorite", () -> {
            boolean isFav = db.isBookmark("/storage/emulated/0/Download", DatabaseManager.TYPE_FAVORITE);
            assert isFav : "Favorito não encontrado";
        });
        r.put("isFavorite", "OK");

        // Test 3: Listar favoritos
        test("DB_listFavorites", () -> {
            String json = db.getBookmarksJson(DatabaseManager.TYPE_FAVORITE);
            JSONObject obj = new JSONObject(json);
            assert obj.getInt("count") >= 1 : "Nenhum favorito listado";
        });
        r.put("listFavorites", "OK");

        // Test 4: Adicionar ao histórico
        test("DB_addHistory", () -> {
            db.addToHistory("/storage/emulated/0/DCIM", "DCIM");
            db.addToHistory("/storage/emulated/0/Download", "Download");
            String json = db.getBookmarksJson(DatabaseManager.TYPE_HISTORY);
            JSONObject obj = new JSONObject(json);
            assert obj.getInt("count") >= 2 : "Histórico incompleto";
        });
        r.put("addHistory", "OK");

        // Test 5: Limpar favoritos
        test("DB_removeFavorite", () -> {
            int deleted = db.removeBookmark("/storage/emulated/0/Download", DatabaseManager.TYPE_FAVORITE);
            assert deleted > 0 : "Nenhum registro removido";
            boolean isFav = db.isBookmark("/storage/emulated/0/Download", DatabaseManager.TYPE_FAVORITE);
            assert !isFav : "Favorito ainda existe após remoção";
        });
        r.put("removeFavorite", "OK");

        // Test 6: Stats
        test("DB_stats", () -> {
            String json = db.getStatsJson();
            JSONObject obj = new JSONObject(json);
            assert obj.has("total") : "Falta total";
        });
        r.put("stats", "OK");

        // Cleanup
        db.clearAll();

        return r;
    }

    private JSONObject testObserverManager() throws Exception {
        JSONObject r = new JSONObject();
        ObserverManager om = new ObserverManager();

        // Test 1: Status inicial
        test("OM_statusInitial", () -> {
            String json = om.getStatusJson();
            JSONObject obj = new JSONObject(json);
            assert obj.getInt("observedCount") == 0 : "Count inicial não zero";
        });
        r.put("statusInitial", "OK");

        // Test 2: Start watching
        test("OM_startWatching", () -> {
            String dir = Environment.getExternalStorageDirectory().getAbsolutePath();
            boolean started = om.startWatching(dir);
            assert started : "Não foi possível iniciar observer";
            assert om.isWatching(dir) : "isWatching retorna false após start";
        });
        r.put("startWatching", "OK");

        // Test 3: Poll events
        test("OM_pollEvents", () -> {
            String json = om.pollEvents();
            assert json != null : "Poll retornou null";
            JSONObject obj = new JSONObject(json);
            assert obj.has("count") : "Falta count";
        });
        r.put("pollEvents", "OK");

        // Test 4: Stop watching
        test("OM_stopWatching", () -> {
            String dir = Environment.getExternalStorageDirectory().getAbsolutePath();
            boolean stopped = om.stopWatching(dir);
            assert stopped : "Não foi possível parar observer";
            assert !om.isWatching(dir) : "isWatching retorna true após stop";
        });
        r.put("stopWatching", "OK");

        // Cleanup
        om.stopAll();

        return r;
    }

    private JSONObject testArchiveManager() throws Exception {
        JSONObject r = new JSONObject();
        ArchiveManager am = new ArchiveManager(context);

        // Test 1: isSupportedArchive
        test("AM_isSupported", () -> {
            assert ArchiveManager.isSupportedArchive("test.zip") : "ZIP não suportado";
            assert ArchiveManager.isSupportedArchive("test.tar.gz") : "TAR.GZ não suportado";
            assert ArchiveManager.isSupportedArchive("test.rar") : "RAR não listado";
            assert !ArchiveManager.isSupportedArchive("test.txt") : "TXT listado como archive";
        });
        r.put("isSupported", "OK");

        // Test 2: getExtension
        test("AM_getExtension", () -> {
            assert "zip".equals(ArchiveManager.getExtension("test.zip")) : "Extensão incorreta";
            assert "".equals(ArchiveManager.getExtension("noext")) : "Sem extensão deveria retornar vazio";
            assert "gz".equals(ArchiveManager.getExtension("test.tar.gz")) : "Extensão .gz incorreta";
        });
        r.put("getExtension", "OK");

        // Test 3: canCreate
        test("AM_canCreate", () -> {
            assert ArchiveManager.canCreate("zip") : "ZIP não criável";
            assert ArchiveManager.canCreate("gz") : "GZ não criável";
            assert !ArchiveManager.canCreate("rar") : "RAR listado como criável";
        });
        r.put("canCreate", "OK");

        // Test 4: Compressão/descompressão real
        test("AM_zipExtract", () -> {
            File testDir = new File(context.getCacheDir(), "archive_test");
            testDir.mkdirs();
            File testFile = new File(testDir, "test.txt");
            FileWriter writer = new FileWriter(testFile);
            writer.write("Teste de compressão ZIP");
            writer.close();

            File zipFile = new File(context.getCacheDir(), "test_output.zip");
            boolean compressed = am.compressToZip(new File[]{testFile}, zipFile, null);
            assert compressed : "Compressão falhou";
            assert zipFile.exists() && zipFile.length() > 0 : "ZIP vazio ou não criado";

            // Extrair
            File extractDir = new File(context.getCacheDir(), "archive_extract");
            boolean extracted = am.extractZip(zipFile, extractDir, null);
            assert extracted : "Extração falhou";

            File extractedFile = new File(extractDir, "test.txt");
            assert extractedFile.exists() : "Arquivo extraído não existe";

            // Cleanup
            testDir.delete();
            zipFile.delete();
            extractDir.delete();
        });
        r.put("zipExtract", "OK");

        return r;
    }

    private JSONObject testFileBridge() throws Exception {
        JSONObject r = new JSONObject();

        // Test 1: selfTest
        test("FB_selfTest", () -> {
            FileBridge fb = new FileBridge(activity);
            fb.init();
            String result = fb.selfTest();
            JSONObject obj = new JSONObject(result);
            assert obj.has("getRootPath") : "selfTest sem getRootPath";
            assert obj.getString("getRootPath").startsWith("OK") : "getRootPath falhou";
        });
        r.put("selfTest", "OK");

        return r;
    }

    // ========================================
    //  FRAMEWORK DE TESTE
    // ========================================

    private void test(String name, TestRunnable runnable) {
        total++;
        try {
            runnable.run();
            passed++;
            Log.d(TAG, "  ✅ " + name);
        } catch (AssertionError e) {
            failed++;
            Log.e(TAG, "  ❌ " + name + ": " + e.getMessage());
        } catch (Exception e) {
            failed++;
            Log.e(TAG, "  ❌ " + name + ": EXCEPTION " + e.getMessage());
        }
    }

    private interface TestRunnable {
        void run() throws Exception;
    }

    private class AssertionError extends Exception {
        AssertionError(String msg) { super(msg); }
    }

    private void assert(boolean condition, String message) throws AssertionError {
        if (!condition) throw new AssertionError(message);
    }

    private JSONObject buildSummary() throws Exception {
        JSONObject summary = new JSONObject();
        summary.put("total", total);
        summary.put("passed", passed);
        summary.put("failed", failed);
        summary.put("passRate", total > 0 ? (passed * 100.0 / total) : 0);
        summary.put("sdk", Build.VERSION.SDK_INT);
        summary.put("device", Build.MANUFACTURER + " " + Build.MODEL);
        summary.put("android", Build.VERSION.RELEASE);
        return summary;
    }
}
