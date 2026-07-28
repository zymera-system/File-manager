package com.filemanager.app.core;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.content.Intent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * StorageManager — Detecção unificada de volumes de armazenamento.
 *
 * Inspirado no File Manager+ e ZArchiver:
 * - Detecta automaticamente armazenamento interno, SD card, USB OTG
 * - Informações de espaço (total, livre, usado)
 * - Mapeamento de caminhos virtuais → reais
 * - Suporte a SAF (Storage Access Framework) para SD/USB no Android 11+
 */
public class StorageDetector {

    private final Context context;

    // Mapeamento de IDs virtuais para caminhos reais
    private static final java.util.Map<String, String> VIRTUAL_PATHS = new java.util.HashMap<>();

    static {
        VIRTUAL_PATHS.put("internal", "/storage/emulated/0");
        VIRTUAL_PATHS.put("sdcard", "/storage/"); // Será detectado dinamicamente
        VIRTUAL_PATHS.put("usb", "/storage/");    // Será detectado dinamicamente
    }

    public StorageDetector(Context context) {
        this.context = context;
    }

    // ========================================
    //  DETECÇÃO DE VOLUMES
    // ========================================

    /**
     * Classe simples representando um volume de armazenamento.
     */
    public static class StorageVolumeInfo {
        public String id;           // "internal", "sdcard", "usb_01", etc.
        public String displayName;
        public String path;
        public boolean isPrimary;
        public boolean isRemovable;
        public boolean isEmulated;
        public long totalBytes;
        public long freeBytes;
        public long usedBytes;

        public String toJson() throws Exception {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("id", id);
            json.put("displayName", displayName);
            json.put("path", path);
            json.put("isPrimary", isPrimary);
            json.put("isRemovable", isRemovable);
            json.put("isEmulated", isEmulated);
            json.put("totalBytes", totalBytes);
            json.put("freeBytes", freeBytes);
            json.put("usedBytes", usedBytes);
            json.put("totalFormatted", OperationManager.formatSize(totalBytes));
            json.put("freeFormatted", OperationManager.formatSize(freeBytes));
            json.put("usedFormatted", OperationManager.formatSize(usedBytes));
            json.put("percentUsed", totalBytes > 0 ? (int)((usedBytes * 100) / totalBytes) : 0);
            return json.toString();
        }
    }

    /**
     * Detecta todos os volumes de armazenamento disponíveis.
     */
    public List<StorageVolumeInfo> detectAllVolumes() {
        List<StorageVolumeInfo> volumes = new ArrayList<>();

        // 1. Armazenamento interno sempre existe
        StorageVolumeInfo internal = getInternalStorage();
        if (internal != null) {
            volumes.add(internal);
        }

        // 2. Detectar volumes extras via StorageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            StorageManager sm = context.getSystemService(StorageManager.class);
            if (sm != null) {
                for (StorageVolume volume : sm.getStorageVolumes()) {
                    // Pular o volume primário (já detectado)
                    if (volume.isPrimary()) continue;

                    StorageVolumeInfo info = new StorageVolumeInfo();
                    info.displayName = volume.getDescription(context);
                    info.isRemovable = volume.isRemovable();
                    info.isEmulated = volume.isEmulated();
                    info.isPrimary = false;

                    // Tentar obter o caminho real
                    String path = getVolumePath(volume);
                    if (path != null) {
                        info.path = path;
                        info.id = volume.isRemovable() ? "sdcard" : "usb_" + volumes.size();
                        getSpaceInfo(info);
                        volumes.add(info);
                    }
                }
            }
        }

        // 3. Fallback: verificar diretórios comuns de SD e USB
        if (volumes.size() <= 1) {
            addFallbackVolumes(volumes);
        }

        return volumes;
    }

    /**
     * Obtém informações do armazenamento interno.
     */
    private StorageVolumeInfo getInternalStorage() {
        StorageVolumeInfo info = new StorageVolumeInfo();
        info.id = "internal";
        info.displayName = "Armazenamento Interno";
        info.path = "/storage/emulated/0";
        info.isPrimary = true;
        info.isRemovable = false;
        info.isEmulated = true;
        getSpaceInfo(info);
        return info;
    }

    /**
     * Obtém o caminho de um StorageVolume (Android N+).
     */
    private String getVolumePath(StorageVolume volume) {
        try {
            //反射 para obter o path (não exposto publicamente)
            java.lang.reflect.Method method = volume.getClass().getMethod("getPath");
            return (String) method.invoke(volume);
        } catch (Exception e) {
            // Fallback: tentar campos conhecidos
            try {
                java.lang.reflect.Field field = volume.getClass().getDeclaredField("mPath");
                field.setAccessible(true);
                return (String) field.get(volume);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    /**
     * Adiciona volumes por fallback (verificar diretórios comuns).
     */
    private void addFallbackVolumes(List<StorageVolumeInfo> volumes) {
        // SD Card — caminhos comuns
        String[] sdPaths = {
            "/storage/sdcard1",
            "/storage/extSdCard",
            "/storage/external_SD"
        };
        for (String path : sdPaths) {
            File dir = new File(path);
            if (dir.exists() && dir.canRead() && !pathExists(volumes, path)) {
                StorageVolumeInfo info = new StorageVolumeInfo();
                info.id = "sdcard";
                info.displayName = "SD Card";
                info.path = path;
                info.isPrimary = false;
                info.isRemovable = true;
                info.isEmulated = false;
                getSpaceInfo(info);
                volumes.add(info);
                break;
            }
        }

        // USB OTG — caminhos comuns
        String[] usbPaths = {
            "/storage/usb",
            "/storage/usbotg",
            "/mnt/usb_storage"
        };
        for (String path : usbPaths) {
            File dir = new File(path);
            if (dir.exists() && dir.canRead() && !pathExists(volumes, path)) {
                StorageVolumeInfo info = new StorageVolumeInfo();
                info.id = "usb_01";
                info.displayName = "USB OTG";
                info.path = path;
                info.isPrimary = false;
                info.isRemovable = true;
                info.isEmulated = false;
                getSpaceInfo(info);
                volumes.add(info);
                break;
            }
        }
    }

    private boolean pathExists(List<StorageVolumeInfo> volumes, String path) {
        for (StorageVolumeInfo v : volumes) {
            if (path.equals(v.path)) return true;
        }
        return false;
    }

    // ========================================
    //  INFORMAÇÕES DE ESPAÇO
    // ========================================

    /**
     * Preenche informações de espaço de um volume.
     */
    private void getSpaceInfo(StorageVolumeInfo info) {
        try {
            StatFs stat = new StatFs(info.path);
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            long freeBlocks = stat.getAvailableBlocksLong();

            info.totalBytes = totalBlocks * blockSize;
            info.freeBytes = freeBlocks * blockSize;
            info.usedBytes = info.totalBytes - info.freeBytes;
        } catch (Exception e) {
            info.totalBytes = 0;
            info.freeBytes = 0;
            info.usedBytes = 0;
        }
    }

    /**
     * Retorna informações de espaço de um caminho específico.
     */
    public String getSpaceJson(String path) {
        try {
            File dir = new File(path);
            if (!dir.exists()) {
                return "{\"error\":true,\"message\":\"Path not found\"}";
            }

            StatFs stat = new StatFs(path);
            long blockSize = stat.getBlockSizeLong();
            long total = stat.getBlockCountLong() * blockSize;
            long free = stat.getAvailableBlocksLong() * blockSize;
            long used = total - free;

            org.json.JSONObject json = new org.json.JSONObject();
            json.put("path", path);
            json.put("totalBytes", total);
            json.put("freeBytes", free);
            json.put("usedBytes", used);
            json.put("totalFormatted", OperationManager.formatSize(total));
            json.put("freeFormatted", OperationManager.formatSize(free));
            json.put("usedFormatted", OperationManager.formatSize(used));
            json.put("percentUsed", total > 0 ? (int)((used * 100) / total) : 0);

            return json.toString();
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    // ========================================
    //  MAPEAMENTO DE CAMINHOS VIRTUAIS
    // ========================================

    /**
     * Resolve um caminho virtual para o caminho real.
     *
     * Virtual:  "internal:/DCIM"
     * Real:     "/storage/emulated/0/DCIM"
     *
     * @param virtualPath caminho virtual com prefixo do volume
     * @return caminho real, ou null se não encontrado
     */
    public String resolveVirtualPath(String virtualPath) {
        if (virtualPath == null || virtualPath.isEmpty()) return null;

        // Se já começa com /, é caminho real
        if (virtualPath.startsWith("/")) {
            return virtualPath;
        }

        // Formato esperado: "volumeId:/resto/do/caminho"
        int colonIndex = virtualPath.indexOf(':');
        if (colonIndex > 0) {
            String volumeId = virtualPath.substring(0, colonIndex);
            String subPath = virtualPath.substring(colonIndex + 1);

            // Buscar o volume
            List<StorageVolumeInfo> volumes = detectAllVolumes();
            for (StorageVolumeInfo vol : volumes) {
                if (vol.id.equals(volumeId)) {
                    String fullPath = vol.path + subPath;
                    // Garantir que não há barras duplas
                    return fullPath.replaceAll("//", "/");
                }
            }
        }

        return virtualPath;
    }

    /**
     * Converte um caminho real em virtual.
     *
     * Real:     "/storage/emulated/0/DCIM"
     * Virtual:  "internal:/DCIM"
     */
    public String toVirtualPath(String realPath) {
        if (realPath == null) return null;

        List<StorageVolumeInfo> volumes = detectAllVolumes();
        for (StorageVolumeInfo vol : volumes) {
            if (realPath.startsWith(vol.path)) {
                String subPath = realPath.substring(vol.path.length());
                return vol.id + ":" + subPath;
            }
        }

        return realPath;
    }

    // ========================================
    //  VOLUME JSON COMPLETO
    // ========================================

    /**
     * Retorna todos os volumes detectados como JSON array.
     */
    public String getAllVolumesJson() {
        try {
            List<StorageVolumeInfo> volumes = detectAllVolumes();
            org.json.JSONArray arr = new org.json.JSONArray();
            for (StorageVolumeInfo vol : volumes) {
                arr.put(new org.json.JSONObject(vol.toJson()));
            }

            org.json.JSONObject result = new org.json.JSONObject();
            result.put("volumes", arr);
            result.put("count", volumes.size());
            return result.toString();
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    /**
     * Retorna informações do volume primário como JSON.
     */
    public String getPrimaryVolumeJson() {
        StorageVolumeInfo internal = getInternalStorage();
        try {
            return internal.toJson();
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + e.getMessage() + "\"}";
        }
    }
}
