package com.filemanager.app.core;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * DatabaseManager — SQLite para bookmarks, histórico e favoritos.
 *
 * Estrutura inspirada no File Manager+:
 * - type 1: Favoritos
 * - type 2: Bookmark (atalho)
 * - type 3: Histórico (máximo 200)
 * - type 4: Lixeira (arquivos deletados)
 *
 * Tabela bookmarks:
 *   id INTEGER PRIMARY KEY
 *   path TEXT NOT NULL
 *   name TEXT
 *   type INTEGER DEFAULT 1
 *   created_at INTEGER
 *   extra TEXT (JSON)
 */
public class DatabaseManager extends SQLiteOpenHelper {

    private static final String DB_NAME = "filemanager.db";
    private static final int DB_VERSION = 1;
    private static final int MAX_HISTORY = 200;

    // Tipos de registro
    public static final int TYPE_FAVORITE = 1;
    public static final int TYPE_BOOKMARK = 2;
    public static final int TYPE_HISTORY = 3;
    public static final int TYPE_TRASH = 4;
    public static final int TYPE_CLOUD = 5;

    private static DatabaseManager instance;

    public static synchronized DatabaseManager getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseManager(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseManager(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE bookmarks (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "path TEXT NOT NULL, " +
            "name TEXT, " +
            "type INTEGER DEFAULT 1, " +
            "created_at INTEGER, " +
            "extra TEXT" +
            ")");

        db.execSQL("CREATE INDEX idx_bookmarks_type ON bookmarks(type)");
        db.execSQL("CREATE INDEX idx_bookmarks_path ON bookmarks(path)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Futuras migrações aqui
    }

    // ========================================
    //  FAVORITOS / BOOKMARKS
    // ========================================

    /**
     * Adiciona um favorito/bookmark.
     */
    public long addBookmark(String path, String name, int type, String extra) {
        SQLiteDatabase db = getWritableDatabase();

        // Verificar se já existe
        Cursor cursor = db.rawQuery(
            "SELECT id FROM bookmarks WHERE path = ? AND type = ?",
            new String[]{path, String.valueOf(type)});
        if (cursor.moveToFirst()) {
            long existingId = cursor.getLong(0);
            cursor.close();
            return existingId;
        }
        cursor.close();

        ContentValues values = new ContentValues();
        values.put("path", path);
        values.put("name", name);
        values.put("type", type);
        values.put("created_at", System.currentTimeMillis());
        values.put("extra", extra);

        return db.insert("bookmarks", null, values);
    }

    /**
     * Remove um bookmark por path e tipo.
     */
    public int removeBookmark(String path, int type) {
        return getWritableDatabase().delete("bookmarks",
            "path = ? AND type = ?",
            new String[]{path, String.valueOf(type)});
    }

    /**
     * Remove um bookmark por ID.
     */
    public int removeBookmarkById(long id) {
        return getWritableDatabase().delete("bookmarks",
            "id = ?", new String[]{String.valueOf(id)});
    }

    /**
     * Verifica se um path é bookmark de certo tipo.
     */
    public boolean isBookmark(String path, int type) {
        Cursor cursor = getReadableDatabase().rawQuery(
            "SELECT id FROM bookmarks WHERE path = ? AND type = ? LIMIT 1",
            new String[]{path, String.valueOf(type)});
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    /**
     * Retorna todos os bookmarks de um tipo como JSON.
     */
    public String getBookmarksJson(int type) {
        try {
            Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, path, name, type, created_at, extra FROM bookmarks " +
                "WHERE type = ? ORDER BY created_at DESC",
                new String[]{String.valueOf(type)});

            JSONArray arr = new JSONArray();
            while (cursor.moveToNext()) {
                JSONObject obj = new JSONObject();
                obj.put("id", cursor.getLong(0));
                obj.put("path", cursor.getString(1));
                obj.put("name", cursor.getString(2));
                obj.put("type", cursor.getInt(3));
                obj.put("createdAt", cursor.getLong(4));
                String extra = cursor.getString(5);
                if (extra != null) obj.put("extra", new JSONObject(extra));
                arr.put(obj);
            }
            cursor.close();

            JSONObject result = new JSONObject();
            result.put("items", arr);
            result.put("count", arr.length());
            return result.toString();
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    // ========================================
    //  HISTÓRICO (máximo 200)
    // ========================================

    /**
     * Adiciona uma entrada ao histórico.
     */
    public void addToHistory(String path, String name) {
        SQLiteDatabase db = getWritableDatabase();

        // Remover duplicata existente
        db.delete("bookmarks",
            "path = ? AND type = ?",
            new String[]{path, String.valueOf(TYPE_HISTORY)});

        // Inserir nova entrada
        ContentValues values = new ContentValues();
        values.put("path", path);
        values.put("name", name);
        values.put("type", TYPE_HISTORY);
        values.put("created_at", System.currentTimeMillis());
        db.insert("bookmarks", null, values);

        // Limpar excesso (manter últimos 200)
        db.execSQL(
            "DELETE FROM bookmarks WHERE id NOT IN " +
            "(SELECT id FROM bookmarks WHERE type = " + TYPE_HISTORY +
            " ORDER BY created_at DESC LIMIT " + MAX_HISTORY + ") " +
            "AND type = " + TYPE_HISTORY
        );
    }

    /**
     * Limpa todo o histórico.
     */
    public void clearHistory() {
        getWritableDatabase().delete("bookmarks",
            "type = ?", new String[]{String.valueOf(TYPE_HISTORY)});
    }

    // ========================================
    //  LIXEIRA (arquivos deletados)
    // ========================================

    /**
     * Registra um arquivo deletado na lixeira.
     */
    public void addToTrash(String path, String name, String originalParent, String extra) {
        ContentValues values = new ContentValues();
        values.put("path", path);
        values.put("name", name);
        values.put("type", TYPE_TRASH);
        values.put("created_at", System.currentTimeMillis());
        if (originalParent != null) {
            JSONObject json = new JSONObject();
            try {
                json.put("originalParent", originalParent);
                if (extra != null) json.put("extra", new JSONObject(extra));
            } catch (Exception ignored) {}
            values.put("extra", json.toString());
        }
        getWritableDatabase().insert("bookmarks", null, values);
    }

    /**
     * Retorna todos os itens na lixeira como JSON.
     */
    public String getTrashJson() {
        return getBookmarksJson(TYPE_TRASH);
    }

    /**
     * Limpa a lixeira.
     */
    public void emptyTrash() {
        getWritableDatabase().delete("bookmarks",
            "type = ?", new String[]{String.valueOf(TYPE_TRASH)});
    }

    // ========================================
    //  BUSCA
    // ========================================

    /**
     * Busca por nome em todos os bookmarks.
     */
    public String searchBookmarks(String query) {
        try {
            Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, path, name, type, created_at FROM bookmarks " +
                "WHERE name LIKE ? OR path LIKE ? ORDER BY created_at DESC LIMIT 100",
                new String[]{"%" + query + "%", "%" + query + "%"});

            JSONArray arr = new JSONArray();
            while (cursor.moveToNext()) {
                JSONObject obj = new JSONObject();
                obj.put("id", cursor.getLong(0));
                obj.put("path", cursor.getString(1));
                obj.put("name", cursor.getString(2));
                obj.put("type", cursor.getInt(3));
                obj.put("createdAt", cursor.getLong(4));
                arr.put(obj);
            }
            cursor.close();

            JSONObject result = new JSONObject();
            result.put("items", arr);
            result.put("count", arr.length());
            result.put("query", query);
            return result.toString();
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    // ========================================
    //  STATS
    // ========================================

    /**
     * Retorna estatísticas gerais do banco.
     */
    public String getStatsJson() {
        try {
            JSONObject stats = new JSONObject();

            String[] types = {"favorite", "bookmark", "history", "trash", "cloud"};
            int[] typeIds = {TYPE_FAVORITE, TYPE_BOOKMARK, TYPE_HISTORY, TYPE_TRASH, TYPE_CLOUD};

            for (int i = 0; i < types.length; i++) {
                Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT COUNT(*) FROM bookmarks WHERE type = ?",
                    new String[]{String.valueOf(typeIds[i])});
                cursor.moveToFirst();
                stats.put(types[i], cursor.getInt(0));
                cursor.close();
            }

            Cursor total = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM bookmarks", null);
            total.moveToFirst();
            stats.put("total", total.getInt(0));
            total.close();

            return stats.toString();
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    // ========================================
    //  LIMPEZA
    // ========================================

    /**
     * Remove todas as entradas de um tipo.
     */
    public void clearType(int type) {
        getWritableDatabase().delete("bookmarks",
            "type = ?", new String[]{String.valueOf(type)});
    }

    /**
     * Remove todas as entradas.
     */
    public void clearAll() {
        getWritableDatabase().delete("bookmarks", null, null);
    }
}
