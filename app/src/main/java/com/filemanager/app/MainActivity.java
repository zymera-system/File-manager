package com.filemanager.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.filemanager.app.core.PermissionManager;

/**
 * MainActivity — Wrapper Android para o File Manager Web.
 *
 * Ponto de entrada do app Android. Carrega o WebView e gerencia:
 * - Inicialização dos core managers via FileBridge.init()
 * - Permissões de armazenamento via PermissionManager
 * - Lifecycle completo (onCreate/onResume/onDestroy)
 */
public class MainActivity extends Activity implements PermissionManager.PermissionCallback {

    private static final String TAG = "FileManager";
    private WebView webView;

    private FileBridge fileBridge;
    private StorageBridge storageBridge;
    private PermissionManager permissionManager;
    private UpdateManager updateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializa as bridges
        fileBridge = new FileBridge(this);
        storageBridge = new StorageBridge(this);
        updateManager = new UpdateManager(this);

        // Configura o WebView
        setupWebView();

        // Solicita permissões via PermissionManager
        requestStoragePermissions();
    }

    /**
     * Configura o WebView com as opções necessárias.
     */
    private void setupWebView() {
        webView = findViewById(R.id.webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // Registra as bridges JavaScript
        webView.addJavascriptInterface(fileBridge, "FileBridge");
        webView.addJavascriptInterface(storageBridge, "StorageBridge");
        webView.addJavascriptInterface(updateManager, "UpdateManager");

        // WebViewClient — tratamento de carregamento e erros
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.d(TAG, "shouldOverrideUrlLoading: " + url);
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Log.d(TAG, "=== onPageStarted: " + url + " ===");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "=== onPageFinished: " + url + " ===");
                checkBridgesAvailability();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                Log.e(TAG, "=== onReceivedError ===");
                Log.e(TAG, "  URL: " + request.getUrl());
                Log.e(TAG, "  IsForMainFrame: " + request.isForMainFrame());
                Log.e(TAG, "  ErrorCode: " + error.getErrorCode());
                Log.e(TAG, "  Description: " + error.getDescription());
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                Log.e(TAG, "=== onReceivedSslError ===");
                Log.e(TAG, "  Error: " + error);
                handler.cancel();
            }
        });

        // WebChromeClient — console logging
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                String level;
                switch (consoleMessage.messageLevel()) {
                    case ERROR:
                        level = "ERROR";
                        Log.e(TAG, "[JS " + level + "] " + consoleMessage.message()
                            + " (line " + consoleMessage.lineNumber()
                            + " in " + consoleMessage.sourceId() + ")");
                        break;
                    case WARNING:
                        level = "WARN";
                        Log.w(TAG, "[JS " + level + "] " + consoleMessage.message()
                            + " (line " + consoleMessage.lineNumber()
                            + " in " + consoleMessage.sourceId() + ")");
                        break;
                    case DEBUG:
                        level = "DEBUG";
                        Log.d(TAG, "[JS " + level + "] " + consoleMessage.message());
                        break;
                    default:
                        level = "INFO";
                        Log.i(TAG, "[JS " + level + "] " + consoleMessage.message());
                        break;
                }
                return true;
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress % 25 == 0 || newProgress == 100) {
                    Log.d(TAG, "WebView progress: " + newProgress + "%");
                }
            }
        });

        // Carrega o index.html dos assets
        Log.d(TAG, "=== Carregando index.html ===");
        Log.d(TAG, "FileBridge registrado como: FileBridge");
        Log.d(TAG, "StorageBridge registrado como: StorageBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    // ========================================
    //  PERMISSÕES (via PermissionManager)
    // ========================================

    /**
     * Solicita permissões de armazenamento de forma adaptativa.
     * Delega ao PermissionManager que detecta a versão do Android.
     */
    private void requestStoragePermissions() {
        permissionManager = new PermissionManager(this);
        permissionManager.setCallback(this);

        // Verificar se já tem permissão
        if (permissionManager.hasStoragePermission()) {
            Log.d(TAG, "Permissões de storage já concedidas");
            onPermissionGranted("already_granted");
        } else {
            Log.d(TAG, "Solicitando permissões de storage...");
            permissionManager.requestStoragePermissions();
        }
    }

    /**
     * Callback: permissão concedida.
     */
    @Override
    public void onPermissionGranted(String permission) {
        Log.d(TAG, "Permissão concedida: " + permission);

        // Inicializar os managers agora que temos permissão
        if (!isFileBridgeInitialized()) {
            fileBridge.init();
            fileBridge.ui().setWebView(webView);
            Log.d(TAG, "FileBridge managers inicializados");
        }

        // Recarregar WebView se necessário
        if (webView != null) {
            webView.reload();
        }
    }

    /**
     * Callback: permissão negada.
     */
    @Override
    public void onPermissionDenied(String permission) {
        Log.w(TAG, "Permissão negada: " + permission);
        // O app pode funcionar parcialmente sem permissão
        // O JS irá mostrar aviso ao tentar acessar arquivos
    }

    /**
     * Callback: requer grant manual (Android 11+ MANAGE_EXTERNAL_STORAGE).
     */
    @Override
    public void onRequiresManualGrant() {
        Log.d(TAG, "Redirecionando para configurações de All Files Access...");
        permissionManager.openStorageSettings();
    }

    // ========================================
    //  LIFECYCLE
    // ========================================

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissionManager != null) {
            permissionManager.handlePermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Verificar se permissão foi concedida após retorno das configurações
        if (permissionManager != null && permissionManager.hasStoragePermission()) {
            if (!isFileBridgeInitialized()) {
                fileBridge.init();
                fileBridge.ui().setWebView(webView);
                Log.d(TAG, "FileBridge managers inicializados (onResume)");
            }
            if (webView != null) {
                webView.reload();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "=== onDestroy ===");

        // Liberar resources dos managers
        if (fileBridge != null) {
            fileBridge.destroy();
        }

        if (webView != null) {
            webView.destroy();
        }

        super.onDestroy();
    }

    // ========================================
    //  HELPERS
    // ========================================

    private boolean isFileBridgeInitialized() {
        return fileBridge.operations() != null;
    }

    /**
     * Executa JavaScript no WebView.
     */
    public void evaluateJavascript(String script) {
        if (webView != null) {
            webView.evaluateJavascript(script, new android.webkit.ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    Log.d(TAG, "evaluateJavascript result: " + value);
                }
            });
        }
    }

    /**
     * Verifica se as bridges JavaScript estão disponíveis.
     */
    private void checkBridgesAvailability() {
        String checkScript =
            "(function() {" +
            "  var bridges = {" +
            "    FileBridge: typeof window.FileBridge," +
            "    StorageBridge: typeof window.StorageBridge," +
            "    FM: typeof window.fmNavigateTo" +
            "  };" +
            "  console.log('[BridgeCheck] ' + JSON.stringify(bridges));" +
            "  return JSON.stringify(bridges);" +
            "})()";
        webView.evaluateJavascript(checkScript, new android.webkit.ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                Log.d(TAG, "=== Bridge Availability ===");
                Log.d(TAG, "  " + value);
            }
        });
    }
}
