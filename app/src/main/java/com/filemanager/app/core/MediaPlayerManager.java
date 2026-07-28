package com.filemanager.app.core;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * MediaPlayerManager — Players de mídia nativos via Intent.
 *
 * Abre arquivos de mídia com os apps nativos do sistema:
 * - Imagens: Galeria, Google Photos, etc.
 * - Áudio: Player de música, Spotify, etc.
 * - Vídeo: MX Player, VLC, etc.
 *
 * Também fornece:
 * - Informações de mídia (duração, formato, etc.)
 * - Scan de mídia para atualizar a galeria
 * - Compartilhamento de arquivos
 *
 * Tipos MIME suportados:
 *   image/*, audio/*, video/*
 */
public class MediaPlayerManager {

    private static final String TAG = "MediaPlayerManager";
    private final Activity activity;

    // Cache de tipos MIME
    private static final Map<String, String> MIME_CACHE = new HashMap<>();

    static {
        // Imagens
        MIME_CACHE.put("jpg", "image/jpeg");
        MIME_CACHE.put("jpeg", "image/jpeg");
        MIME_CACHE.put("png", "image/png");
        MIME_CACHE.put("gif", "image/gif");
        MIME_CACHE.put("bmp", "image/bmp");
        MIME_CACHE.put("webp", "image/webp");
        MIME_CACHE.put("svg", "image/svg+xml");
        MIME_CACHE.put("heic", "image/heic");
        MIME_CACHE.put("heif", "image/heif");
        MIME_CACHE.put("tiff", "image/tiff");
        MIME_CACHE.put("ico", "image/x-icon");

        // Áudio
        MIME_CACHE.put("mp3", "audio/mpeg");
        MIME_CACHE.put("wav", "audio/wav");
        MIME_CACHE.put("ogg", "audio/ogg");
        MIME_CACHE.put("flac", "audio/flac");
        MIME_CACHE.put("aac", "audio/aac");
        MIME_CACHE.put("m4a", "audio/mp4");
        MIME_CACHE.put("wma", "audio/x-ms-wma");
        MIME_CACHE.put("opus", "audio/opus");

        // Vídeo
        MIME_CACHE.put("mp4", "video/mp4");
        MIME_CACHE.put("mkv", "video/x-matroska");
        MIME_CACHE.put("avi", "video/x-msvideo");
        MIME_CACHE.put("mov", "video/quicktime");
        MIME_CACHE.put("wmv", "video/x-ms-wmv");
        MIME_CACHE.put("flv", "video/x-flv");
        MIME_CACHE.put("webm", "video/webm");
        MIME_CACHE.put("3gp", "video/3gpp");
        MIME_CACHE.put("ts", "video/mp2t");
    }

    public MediaPlayerManager(Activity activity) {
        this.activity = activity;
    }

    // ========================================
    //  ABRIR MÍDIA
    // ========================================

    /**
     * Abre um arquivo de mídia com o app nativo do sistema.
     */
    @JavascriptInterface
    public String openMedia(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return errorJson("Arquivo não encontrado");
            }

            String mimeType = getMimeType(filePath);
            Uri uri = Uri.fromFile(file);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Verificar se há app que pode abrir
            if (intent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivity(intent);
                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("mimeType", mimeType);
                result.put("filePath", filePath);
                return result.toString();
            } else {
                return errorJson("Nenhum app encontrado para abrir este tipo de arquivo: " + mimeType);
            }

        } catch (Exception e) {
            return errorJson("Erro ao abrir: " + e.getMessage());
        }
    }

    /**
     * Abre um arquivo com um app específico.
     */
    @JavascriptInterface
    public String openWith(String filePath, String packageName) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return errorJson("Arquivo não encontrado");
            }

            String mimeType = getMimeType(filePath);
            Uri uri = Uri.fromFile(file);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.setPackage(packageName);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            activity.startActivity(intent);

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("package", packageName);
            return result.toString();

        } catch (Exception e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    // ========================================
    //  INFORMAÇÕES DE MÍDIA
    // ========================================

    /**
     * Retorna informações de um arquivo de mídia.
     */
    @JavascriptInterface
    public String getMediaInfo(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return errorJson("Arquivo não encontrado");
            }

            String ext = ArchiveManager.getExtension(filePath).toLowerCase();
            String mimeType = getMimeType(filePath);
            String category = getMediaCategory(ext);

            JSONObject info = new JSONObject();
            info.put("path", filePath);
            info.put("name", file.getName());
            info.put("size", file.length());
            info.put("sizeFormatted", OperationManager.formatSize(file.length()));
            info.put("extension", ext);
            info.put("mimeType", mimeType);
            info.put("category", category);
            info.put("lastModified", file.lastModified());
            info.put("canRead", file.canRead());

            // Para imagens, obter dimensões
            if ("image".equals(category)) {
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(filePath, options);
                    info.put("width", options.outWidth);
                    info.put("height", options.outHeight);
                } catch (Exception e) {
                    // Ignorar se não conseguir ler dimensões
                }
            }

            return info.toString();

        } catch (Exception e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    /**
     * Retorna informações de múltiplos arquivos de mídia.
     */
    @JavascriptInterface
    public String getMediaInfoBatch(String pathsJson) {
        try {
            JSONArray paths = new JSONArray(pathsJson);
            JSONArray results = new JSONArray();

            for (int i = 0; i < paths.length(); i++) {
                String path = paths.getString(i);
                results.put(new JSONObject(getMediaInfo(path)));
            }

            JSONObject result = new JSONObject();
            result.put("items", results);
            result.put("count", results.length());
            return result.toString();

        } catch (Exception e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    // ========================================
    //  SCAN DE MÍDIA
    // ========================================

    /**
     * Escaneia um arquivo para atualizar a galeria do sistema.
     */
    @JavascriptInterface
    public String scanMedia(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return errorJson("Arquivo não encontrado");
            }

            MediaScannerConnection.scanFile(
                activity,
                new String[]{filePath},
                null,
                (path, uri) -> {
                    Log.d(TAG, "Scan completo: " + path + " → " + uri);
                }
            );

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("message", "Scan iniciado");
            return result.toString();

        } catch (Exception e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    /**
     * Escaneia um diretório inteiro.
     */
    @JavascriptInterface
    public String scanDirectory(String dirPath) {
        try {
            File dir = new File(dirPath);
            if (!dir.exists() || !dir.isDirectory()) {
                return errorJson("Diretório não encontrado");
            }

            java.util.List<String> mediaFiles = new java.util.ArrayList<>();
            collectMediaFiles(dir, mediaFiles);

            if (mediaFiles.isEmpty()) {
                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("message", "Nenhum arquivo de mídia encontrado");
                return result.toString();
            }

            String[] paths = mediaFiles.toArray(new String[0]);
            MediaScannerConnection.scanFile(activity, paths, null, null);

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("scannedCount", paths.length);
            return result.toString();

        } catch (Exception e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    private void collectMediaFiles(File dir, java.util.List<String> result) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    collectMediaFiles(file, result);
                } else if (isMediaFile(file.getName())) {
                    result.add(file.getAbsolutePath());
                }
            }
        }
    }

    // ========================================
    //  COMPARTILHAR
    // ========================================

    /**
     * Compartilha um arquivo via Intent chooser.
     */
    @JavascriptInterface
    public String shareFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return errorJson("Arquivo não encontrado");
            }

            String mimeType = getMimeType(filePath);
            Uri uri = Uri.fromFile(file);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, file.getName());
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            activity.startActivity(Intent.createChooser(shareIntent, "Compartilhar " + file.getName()));

            JSONObject result = new JSONObject();
            result.put("success", true);
            return result.toString();

        } catch (Exception e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    /**
     * Compartilha múltiplos arquivos.
     */
    @JavascriptInterface
    public String shareMultiple(String pathsJson) {
        try {
            JSONArray paths = new JSONArray(pathsJson);
            if (paths.length() == 0) {
                return errorJson("Nenhum arquivo selecionado");
            }

            java.util.ArrayList<Uri> uris = new java.util.ArrayList<>();
            String mimeType = "*/*";

            for (int i = 0; i < paths.length(); i++) {
                String path = paths.getString(i);
                File file = new File(path);
                if (file.exists()) {
                    uris.add(Uri.fromFile(file));
                    if (i == 0) {
                        mimeType = getMimeType(path);
                    }
                }
            }

            Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            shareIntent.setType(mimeType);
            shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            activity.startActivity(Intent.createChooser(shareIntent, "Compartilhar " + paths.length() + " arquivos"));

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("count", uris.size());
            return result.toString();

        } catch (Exception e) {
            return errorJson("Erro: " + e.getMessage());
        }
    }

    // ========================================
    //  UTILITÁRIOS
    // ========================================

    /**
     * Retorna o tipo MIME de um arquivo.
     */
    public static String getMimeType(String path) {
        String ext = ArchiveManager.getExtension(path).toLowerCase();
        String mime = MIME_CACHE.get(ext);
        if (mime != null) return mime;

        // Fallback: tentar pelo content type do sistema
        return "application/octet-stream";
    }

    /**
     * Retorna a categoria de mídia (image, audio, video, document, archive, other).
     */
    public static String getMediaCategory(String ext) {
        if (ext == null) return "other";
        ext = ext.toLowerCase();

        if (ext.matches("jpg|jpeg|png|gif|bmp|webp|svg|heic|heif|tiff|ico")) return "image";
        if (ext.matches("mp3|wav|ogg|flac|aac|m4a|wma|opus")) return "audio";
        if (ext.matches("mp4|mkv|avi|mov|wmv|flv|webm|3gp|ts")) return "video";
        if (ext.matches("pdf|doc|docx|xls|xlsx|ppt|pptx|txt|rtf|csv")) return "document";
        if (ext.matches("zip|tar|gz|bz2|xz|7z|rar|iso")) return "archive";
        if (ext.matches("apk|xap")) return "app";

        return "other";
    }

    /**
     * Verifica se um arquivo é de mídia.
     */
    public static boolean isMediaFile(String filename) {
        String ext = ArchiveManager.getExtension(filename).toLowerCase();
        String category = getMediaCategory(ext);
        return "image".equals(category) || "audio".equals(category) || "video".equals(category);
    }

    /**
     * Verifica se um arquivo é imagem.
     */
    public static boolean isImage(String path) {
        return "image".equals(getMediaCategory(ArchiveManager.getExtension(path)));
    }

    /**
     * Verifica se um arquivo é áudio.
     */
    public static boolean isAudio(String path) {
        return "audio".equals(getMediaCategory(ArchiveManager.getExtension(path)));
    }

    /**
     * Verifica se um arquivo é vídeo.
     */
    public static boolean isVideo(String path) {
        return "video".equals(getMediaCategory(ArchiveManager.getExtension(path)));
    }

    private String errorJson(String message) {
        try {
            JSONObject err = new JSONObject();
            err.put("error", true);
            err.put("message", message);
            return err.toString();
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"Unknown error\"}";
        }
    }
}
