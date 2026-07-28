package com.filemanager.app.core;

import android.os.Handler;
import android.os.Looper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OperationManager — Executa operações de arquivo em background com progresso e cancelamento.
 *
 * Padrão inspirado no ZArchiver:
 * - Cada operação recebe um taskId único
 * - Progresso é comunicado via callback (pollProgress no JS)
 * - Cancelamento por taskId (interrompe em boundary checks)
 *
 * Uso:
 *   OperationManager mgr = new OperationManager();
 *   String taskId = mgr.submit(() -> { ... });
 *   mgr.cancel(taskId);
 *   mgr.getProgress(taskId);
 */
public class OperationManager {

    // Pool dedicado — até 5 operações simultâneas (como ZArchiver)
    private final ExecutorService executor = Executors.newFixedThreadPool(5);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Registro ativo de tarefas
    private final ConcurrentHashMap<String, TaskInfo> tasks = new ConcurrentHashMap<>();

    /**
     * Informações de uma tarefa em execução.
     */
    public static class TaskInfo {
        public final String taskId;
        public final AtomicBoolean cancelled = new AtomicBoolean(false);
        public volatile boolean completed = false;
        public volatile boolean success = false;
        public volatile String errorMessage = "";
        public volatile int progress = 0;        // 0-100
        public volatile int processedCount = 0;
        public volatile int totalCount = 0;
        public volatile String currentFile = "";
        public volatile String operationType = ""; // copy, move, delete, compress, extract
        public long startTime = 0;

        public TaskInfo(String taskId, String operationType) {
            this.taskId = taskId;
            this.operationType = operationType;
            this.startTime = System.currentTimeMillis();
        }
    }

    /**
     * Interface para operações que reportam progresso.
     */
    public interface ProgressOperation {
        /**
         * Executa a operação.
         * Deve chamar checkCancellation() periodicamente e reportar progresso.
         *
         * @param taskInfo informações da tarefa (para progresso/cancelamento)
         * @return true se concluiu com sucesso
         * @throws CancellationException se a operação foi cancelada
         * @throws Exception em caso de erro
         */
        boolean execute(TaskInfo taskInfo) throws CancellationException, Exception;
    }

    /**
     * Exceção lançada quando uma tarefa é cancelada.
     */
    public static class CancellationException extends Exception {
        public CancellationException() {
            super("Operation cancelled by user");
        }
    }

    /**
     * Interface para callback de conclusão (operação na main thread).
     */
    public interface TaskCallback {
        void onCompleted(TaskInfo taskInfo);
    }

    // ========================================
    //  SUBMISSÃO DE OPERAÇÕES
    // ========================================

    /**
     * Submete uma operação para execução em background.
     *
     * @param operationType nome da operação (copy, move, delete, compress, extract, rename)
     * @param operation a operação a ser executada
     * @param callback callback chamado na main thread quando concluir
     * @return taskId único para rastreamento
     */
    public String submit(String operationType, ProgressOperation operation, TaskCallback callback) {
        String taskId = generateTaskId();
        TaskInfo info = new TaskInfo(taskId, operationType);
        tasks.put(taskId, info);

        executor.execute(() -> {
            try {
                info.success = operation.execute(info);
            } catch (CancellationException e) {
                info.success = false;
                info.errorMessage = "cancelled";
            } catch (Exception e) {
                info.success = false;
                info.errorMessage = e.getMessage() != null ? e.getMessage() : "unknown error";
            } finally {
                info.completed = true;
            }

            // Callback na main thread
            if (callback != null) {
                mainHandler.post(() -> callback.onCompleted(info));
            }
        });

        return taskId;
    }

    /**
     * Submete operação sem callback (fire and forget).
     */
    public String submit(String operationType, ProgressOperation operation) {
        return submit(operationType, operation, null);
    }

    // ========================================
    //  CANCELAMENTO
    // ========================================

    /**
     * Cancela uma operação pelo taskId.
     * O cancelamento é cooperativo — a operação deve checar isCancelled().
     */
    public boolean cancel(String taskId) {
        TaskInfo info = tasks.get(taskId);
        if (info != null && !info.completed) {
            info.cancelled.set(true);
            return true;
        }
        return false;
    }

    /**
     * Cancela todas as operações ativas.
     */
    public void cancelAll() {
        for (TaskInfo info : tasks.values()) {
            if (!info.completed) {
                info.cancelled.set(true);
            }
        }
    }

    /**
     * Verifica se uma tarefa foi cancelada.
     * Usar dentro das operações (chamado pelo ProgressOperation).
     */
    public static void checkCancellation(TaskInfo taskInfo) throws CancellationException {
        if (taskInfo.cancelled.get()) {
            throw new CancellationException();
        }
    }

    /**
     * Verifica se uma tarefa foi cancelada (boolean, sem exceção).
     */
    public boolean isCancelled(String taskId) {
        TaskInfo info = tasks.get(taskId);
        return info != null && info.cancelled.get();
    }

    // ========================================
    //  PROGRESSO
    // ========================================

    /**
     * Atualiza o progresso de uma operação.
     */
    public static void updateProgress(TaskInfo taskInfo, int processed, int total, String currentFile) {
        taskInfo.processedCount = processed;
        taskInfo.totalCount = total;
        taskInfo.progress = total > 0 ? (processed * 100) / total : 0;
        taskInfo.currentFile = currentFile != null ? currentFile : "";
    }

    /**
     * Obtém informações de progresso de uma tarefa em formato JSON.
     * Usado pelo JS via pollProgress(taskId).
     */
    public String getProgressJson(String taskId) {
        TaskInfo info = tasks.get(taskId);
        if (info == null) {
            return "{\"error\":true,\"message\":\"Task not found\"}";
        }

        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("taskId", info.taskId);
            json.put("operationType", info.operationType);
            json.put("completed", info.completed);
            json.put("cancelled", info.cancelled.get());
            json.put("success", info.completed && info.success);
            json.put("progress", info.progress);
            json.put("processedCount", info.processedCount);
            json.put("totalCount", info.totalCount);
            json.put("currentFile", info.currentFile);

            if (info.completed && !info.success) {
                json.put("error", true);
                json.put("errorMessage", info.errorMessage);
            }

            // Tempo decorrido
            long elapsed = System.currentTimeMillis() - info.startTime;
            json.put("elapsedMs", elapsed);

            return json.toString();
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    // ========================================
    //  STATUS
    // ========================================

    /**
     * Retorna número de operações ativas.
     */
    public int getActiveTaskCount() {
        int count = 0;
        for (TaskInfo info : tasks.values()) {
            if (!info.completed) count++;
        }
        return count;
    }

    /**
     * Retorna IDs de todas as tarefas ativas.
     */
    public String getActiveTasksJson() {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (TaskInfo info : tasks.values()) {
                if (!info.completed) {
                    org.json.JSONObject obj = new org.json.JSONObject();
                    obj.put("taskId", info.taskId);
                    obj.put("operationType", info.operationType);
                    obj.put("progress", info.progress);
                    obj.put("currentFile", info.currentFile);
                    arr.put(obj);
                }
            }
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * Limpa tarefas concluídas do registro (libera memória).
     */
    public void cleanup() {
        tasks.entrySet().removeIf(entry -> entry.getValue().completed);
    }

    // ========================================
    //  LIFECYCLE
    // ========================================

    /**
     * Cancela tudo e desliga o executor.
     * Chamar no onDestroy da Activity.
     */
    public void shutdown() {
        cancelAll();
        executor.shutdownNow();
        tasks.clear();
    }

    // ========================================
    //  HELPERS
    // ========================================

    private String generateTaskId() {
        return "task_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Formata bytes para tamanho legível.
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
