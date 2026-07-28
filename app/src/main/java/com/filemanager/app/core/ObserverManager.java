package com.filemanager.app.core;

import android.os.Handler;
import android.os.Looper;
import android.os.FileObserver;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ObserverManager — Monitoramento de mudanças no sistema de arquivos.
 *
 * Inspirado no File Manager+ (FileObserverService):
 * - Observa diretórios em tempo real
 * - Notifica o JS via pollEvents() quando algo muda
 * - Suporta múltiplos diretórios simultaneamente
 *
 * Eventos monitorados:
 *   CREATE, DELETE, MOVED_TO, MOVED_FROM, MODIFY, CLOSE_WRITE
 */
public class ObserverManager {

    private final Map<String, DirectoryObserver> observers = new ConcurrentHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final EventCollector eventCollector = new EventCollector();

    /**
     * Evento de mudança no filesystem.
     */
    public static class FsEvent {
        public final int eventType;
        public final String path;
        public final String directory;
        public final long timestamp;
        public final String type;

        public FsEvent(int eventType, String path, String directory) {
            this.eventType = eventType;
            this.path = path;
            this.directory = directory;
            this.timestamp = System.currentTimeMillis();
            this.type = resolveType(eventType);
        }

        private static String resolveType(int event) {
            if ((event & FileObserver.CREATE) != 0) return "create";
            if ((event & FileObserver.DELETE) != 0) return "delete";
            if ((event & FileObserver.MODIFY) != 0) return "modify";
            if ((event & FileObserver.MOVED_TO) != 0) return "move_to";
            if ((event & FileObserver.MOVED_FROM) != 0) return "move_from";
            if ((event & FileObserver.CLOSE_WRITE) != 0) return "close_write";
            return "unknown";
        }
    }

    /**
     * Collector que acumula eventos para polling pelo JS.
     */
    public static class EventCollector {
        private final java.util.concurrent.LinkedBlockingQueue<FsEvent> events
            = new java.util.concurrent.LinkedBlockingQueue<>();
        private static final int MAX_EVENTS = 100;

        public void addEvent(FsEvent event) {
            if (events.size() >= MAX_EVENTS) {
                events.poll();
            }
            events.offer(event);
        }

        public String drainEventsJson() {
            try {
                JSONArray arr = new JSONArray();
                FsEvent event;
                while ((event = events.poll()) != null) {
                    JSONObject obj = new JSONObject();
                    obj.put("type", event.type);
                    obj.put("path", event.path);
                    obj.put("directory", event.directory);
                    obj.put("timestamp", event.timestamp);
                    arr.put(obj);
                }

                JSONObject result = new JSONObject();
                result.put("events", arr);
                result.put("count", arr.length());
                return result.toString();
            } catch (Exception e) {
                return "{\"events\":[],\"count\":0,\"error\":true}";
            }
        }

        public int size() {
            return events.size();
        }

        public void clear() {
            events.clear();
        }
    }

    // ========================================
    //  GERENCIAMENTO DE OBSERVERS
    // ========================================

    private class DirectoryObserver extends FileObserver {
        final String watchPath;

        @SuppressWarnings("deprecation")
        DirectoryObserver(String path) {
            super(path, FileObserver.CREATE | FileObserver.DELETE
                 | FileObserver.MOVED_TO | FileObserver.MOVED_FROM
                 | FileObserver.MODIFY | FileObserver.CLOSE_WRITE);
            this.watchPath = path;
        }

        @Override
        public void onEvent(int event, String path) {
            if (path == null) return;
            String fullPath = watchPath + "/" + path;
            eventCollector.addEvent(new FsEvent(event, fullPath, watchPath));
        }
    }

    public boolean startWatching(String directory) {
        if (observers.containsKey(directory)) {
            return true;
        }

        File dir = new File(directory);
        if (!dir.exists() || !dir.isDirectory()) {
            return false;
        }

        try {
            DirectoryObserver observer = new DirectoryObserver(directory);
            observer.startWatching();
            observers.put(directory, observer);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean stopWatching(String directory) {
        DirectoryObserver observer = observers.remove(directory);
        if (observer != null) {
            observer.stopWatching();
            return true;
        }
        return false;
    }

    public void stopAll() {
        for (DirectoryObserver observer : observers.values()) {
            observer.stopWatching();
        }
        observers.clear();
    }

    public boolean isWatching(String directory) {
        return observers.containsKey(directory);
    }

    // ========================================
    //  POLLING DE EVENTOS
    // ========================================

    public String pollEvents() {
        return eventCollector.drainEventsJson();
    }

    public int getPendingEventCount() {
        return eventCollector.size();
    }

    // ========================================
    //  STATUS
    // ========================================

    public String getStatusJson() {
        try {
            JSONObject status = new JSONObject();
            status.put("observedCount", observers.size());
            status.put("pendingEvents", eventCollector.size());

            JSONArray watched = new JSONArray();
            for (String dir : observers.keySet()) {
                watched.put(dir);
            }
            status.put("watchedDirectories", watched);

            return status.toString();
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + e.getMessage() + "\"}";
        }
    }
}
