package com.filemanager.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * StorageBridge - Ponte para detecção e gerenciamento de armazenamento
 * 
 * Fornece funcionalidades de detecção de SD card e USB drive.
 * Expõe funções via @JavascriptInterface para o WebView.
 */
public class StorageBridge {

    private final Activity activity;
    private StorageManager storageManager;
    private BroadcastReceiver usbReceiver;

    // Callbacks para notificar o JavaScript
    private String storageChangeCallback;

    public StorageBridge(Activity activity) {
        this.activity = activity;
        this.storageManager = (StorageManager) activity.getSystemService(Context.STORAGE_SERVICE);
        setupUSBReceiver();
    }

    // ========================
    //  DETECÇÃO DE SD CARD
    // ========================

    /**
     * Verifica se há SD card conectado
     * @return true se SD card estiver presente
     */
    @JavascriptInterface
    public boolean isSDCardConnected() {
        // Método 1: Verificar caminhos conhecidos
        String[] sdPaths = {
            "/storage/sdcard1",
            "/storage/extSdCard",
            "/storage/external_SD",
            "/storage/ext_sd",
            "/storage/removable/sdcard1",
            "/storage/external_sd",
            "/mnt/sdcard1",
            "/mnt/extSdCard",
            "/mnt/external_SD"
        };

        for (String path : sdPaths) {
            File sdDir = new File(path);
            if (sdDir.exists() && sdDir.isDirectory() && sdDir.canRead()) {
                return true;
            }
        }

        // Método 2: Verificar /storage (Android 6+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            File storageDir = new File("/storage");
            File[] files = storageDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    if (!name.equals("emulated") && !name.equals("self") && 
                        !name.equals("udisk") && !name.equals("usb")) {
                        if (file.exists() && file.isDirectory() && file.canRead()) {
                            return true;
                        }
                    }
                }
            }
        }

        // Método 3: Usar StorageManager (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            List<StorageVolume> volumes = storageManager.getStorageVolumes();
            for (StorageVolume volume : volumes) {
                if (volume.isRemovable() && volume.getState() != null && 
                    volume.getState().equals(Environment.MEDIA_MOUNTED)) {
                    // Verificar se não é o armazenamento interno
                    if (volume.getDirectory() != null) {
                        String uuid = volume.getUuid();
                        if (uuid != null && !uuid.equals("primary")) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Obtém o caminho do SD card
     * @return Caminho do SD card ou null
     */
    @JavascriptInterface
    public String getSDCardPath() {
        // Verificar caminhos conhecidos
        String[] sdPaths = {
            "/storage/sdcard1",
            "/storage/extSdCard",
            "/storage/external_SD",
            "/storage/ext_sd",
            "/storage/removable/sdcard1",
            "/storage/external_sd",
            "/mnt/sdcard1",
            "/mnt/extSdCard",
            "/mnt/external_SD"
        };

        for (String path : sdPaths) {
            File sdDir = new File(path);
            if (sdDir.exists() && sdDir.isDirectory() && sdDir.canRead()) {
                return path;
            }
        }

        // Verificar /storage
        File storageDir = new File("/storage");
        File[] files = storageDir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (!name.equals("emulated") && !name.equals("self") && 
                    !name.equals("udisk") && !name.equals("usb")) {
                    if (file.exists() && file.isDirectory() && file.canRead()) {
                        return file.getAbsolutePath();
                    }
                }
            }
        }

        return null;
    }

    // ========================
    //  DETECÇÃO DE USB
    // ========================

    /**
     * Verifica se há USB drive conectado
     * @return true se USB estiver conectado
     */
    @JavascriptInterface
    public boolean isUSBDriveConnected() {
        // Método 1: Verificar caminhos USB conhecidos
        String[] usbPaths = {
            "/storage/usbotg",
            "/storage/usb",
            "/mnt/usb_storage",
            "/mnt/usb",
            "/mnt/usbotg",
            "/storage/UDiskA",
            "/storage/udisk"
        };

        for (String path : usbPaths) {
            File usbDir = new File(path);
            if (usbDir.exists() && usbDir.isDirectory() && usbDir.canRead()) {
                return true;
            }
        }

        // Método 2: Verificar /storage para dispositivos USB
        File storageDir = new File("/storage");
        File[] files = storageDir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName().toLowerCase();
                if (name.contains("usb") || name.contains("udisk") || name.contains("otg")) {
                    if (file.exists() && file.isDirectory() && file.canRead()) {
                        return true;
                    }
                }
            }
        }

        // Método 3: Usar UsbManager (Android 6+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            UsbManager usbManager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
            if (usbManager != null && !usbManager.getDeviceList().isEmpty()) {
                // Há dispositivos USB conectados
                // Verificar se há armazenamento USB
                return hasUSBStorage();
            }
        }

        return false;
    }

    /**
     * Obtém o caminho do USB drive
     * @return Caminho do USB drive ou null
     */
    @JavascriptInterface
    public String getUSBDrivePath() {
        // Verificar caminhos USB conhecidos
        String[] usbPaths = {
            "/storage/usbotg",
            "/storage/usb",
            "/mnt/usb_storage",
            "/mnt/usb",
            "/mnt/usbotg",
            "/storage/UDiskA",
            "/storage/udisk"
        };

        for (String path : usbPaths) {
            File usbDir = new File(path);
            if (usbDir.exists() && usbDir.isDirectory() && usbDir.canRead()) {
                return path;
            }
        }

        // Verificar /storage para dispositivos USB
        File storageDir = new File("/storage");
        File[] files = storageDir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName().toLowerCase();
                if (name.contains("usb") || name.contains("udisk") || name.contains("otg")) {
                    if (file.exists() && file.isDirectory() && file.canRead()) {
                        return file.getAbsolutePath();
                    }
                }
            }
        }

        return null;
    }

    // ========================
    //  LISTAR DISPOSITIVOS
    // ========================

    /**
     * Lista todos os dispositivos de armazenamento conectados
     * @return JSON array com os dispositivos
     */
    @JavascriptInterface
    public String getStorageDevices() {
        try {
            JSONArray devices = new JSONArray();

            // Adicionar armazenamento interno
            JSONObject internal = new JSONObject();
            internal.put("name", "Armazenamento Interno");
            internal.put("path", Environment.getExternalStorageDirectory().getAbsolutePath());
            internal.put("type", "internal");
            internal.put("removable", false);
            internal.put("mounted", Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED));
            devices.put(internal);

            // Adicionar SD card se presente
            if (isSDCardConnected()) {
                JSONObject sdCard = new JSONObject();
                sdCard.put("name", "Cartão SD");
                sdCard.put("path", getSDCardPath());
                sdCard.put("type", "sdcard");
                sdCard.put("removable", true);
                sdCard.put("mounted", true);
                devices.put(sdCard);
            }

            // Adicionar USB se presente
            if (isUSBDriveConnected()) {
                JSONObject usb = new JSONObject();
                usb.put("name", "Drive USB");
                usb.put("path", getUSBDrivePath());
                usb.put("type", "usb");
                usb.put("removable", true);
                usb.put("mounted", true);
                devices.put(usb);
            }

            return devices.toString();

        } catch (Exception e) {
            return errorJson("Erro ao listar dispositivos: " + e.getMessage());
        }
    }

    // ========================
    //  INFORMAÇÕES DE ARMAZENAMENTO
    // ========================

    /**
     * Obtém informações de armazenamento de um dispositivo específico
     * @param path Caminho do dispositivo
     * @return JSON com informações de armazenamento
     */
    @JavascriptInterface
    public String getStorageInfo(String path) {
        try {
            File dir = resolvePath(path);
            if (dir == null || !dir.exists()) {
                return errorJson("Dispositivo não encontrado");
            }

            StatFs stat = new StatFs(dir.getAbsolutePath());
            long total = stat.getTotalBytes();
            long free = stat.getAvailableBytes();
            long used = total - free;

            JSONObject info = new JSONObject();
            info.put("path", dir.getAbsolutePath());
            info.put("total", total);
            info.put("free", free);
            info.put("used", used);
            info.put("totalFormatted", formatSize(total));
            info.put("freeFormatted", formatSize(free));
            info.put("usedFormatted", formatSize(used));
            info.put("percentUsed", total > 0 ? Math.round((used * 100.0) / total) : 0);

            return info.toString();

        } catch (Exception e) {
            return errorJson("Erro ao obter informações: " + e.getMessage());
        }
    }

    // ========================
    //  EJECT (DESATIVAR)
    // ========================

    /**
     * Ejeta (desmonta) um dispositivo de armazenamento
     * @param path Caminho do dispositivo
     * @return true se bem-sucedido
     */
    @JavascriptInterface
    public boolean ejectStorage(String path) {
        try {
            // Em Android, não é possível ejetar programaticamente
            // O usuário precisa usar as configurações do sistema
            // Retornar false para indicar que não é possível
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // ========================
    //  CALLBACKS
    // ========================

    /**
     * Registra callback para mudanças de armazenamento
     * @param callback Nome da função JavaScript
     */
    @JavascriptInterface
    public void onStorageChange(String callback) {
        this.storageChangeCallback = callback;
    }

    // ========================
    //  MÉTODOS PRIVADOS
    // ========================

    private void setupUSBReceiver() {
        usbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                
                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    // USB conectado
                    notifyStorageChange("usb_connected");
                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    // USB desconectado
                    notifyStorageChange("usb_disconnected");
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        activity.registerReceiver(usbReceiver, filter);
    }

    private void notifyStorageChange(String event) {
        if (storageChangeCallback != null && !storageChangeCallback.isEmpty()) {
            try {
                JSONObject data = new JSONObject();
                data.put("event", event);
                data.put("timestamp", System.currentTimeMillis());
                
                String script = String.format(
                    "if (window.%s) window.%s(%s);",
                    storageChangeCallback,
                    storageChangeCallback,
                    data.toString()
                );
                
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).evaluateJavascript(script);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private boolean hasUSBStorage() {
        File storageDir = new File("/storage");
        File[] files = storageDir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName().toLowerCase();
                if (name.contains("usb") || name.contains("udisk") || name.contains("otg")) {
                    return true;
                }
            }
        }
        return false;
    }

    private File resolvePath(String path) {
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
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
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
}
