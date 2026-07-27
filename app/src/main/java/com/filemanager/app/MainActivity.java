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

/**
 * MainActivity - Wrapper Android para o File Manager Web
 * 
 * Este arquivo é o ponto de entrada do aplicativo Android.
 * Ele carrega o file manager web em um WebView e fornece
 * uma ponte (bridge) para operações nativas do sistema.
 */
public class MainActivity extends Activity {

    private static final String TAG = "FileManager";
    private WebView webView;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int MANAGE_STORAGE_REQUEST_CODE = 101;

    private FileBridge fileBridge;
    private StorageBridge storageBridge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializa as bridges
        fileBridge = new FileBridge(this);
        storageBridge = new StorageBridge(this);

        // Configura o WebView
        setupWebView();

        // Solicita permissões
        requestPermissions();
    }

    /**
     * Configura o WebView com as opções necessárias
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

        // Permite que ES modules carreguem imports de file:// URLs (assets)
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // Adiciona as bridges JavaScript
        webView.addJavascriptInterface(fileBridge, "FileBridge");
        webView.addJavascriptInterface(storageBridge, "StorageBridge");

        // Configura o cliente WebView com logging completo
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
                // Verificar bridges disponíveis após carregamento
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
                handler.proceed();
            }
        });

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

    /**
     * Solicita as permissões necessárias
     */
    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.d(TAG, "Android 11+ detectado, verificando MANAGE_EXTERNAL_STORAGE");
            if (!Environment.isExternalStorageManager()) {
                Log.d(TAG, "Permissão MANAGE_EXTERNAL_STORAGE não concedida, solicitando...");
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
            } else {
                Log.d(TAG, "Permissão MANAGE_EXTERNAL_STORAGE já concedida");
            }
        } else {
            Log.d(TAG, "Android " + Build.VERSION.SDK_INT + " detectado, verificando permissões legacy");
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Solicitando READ/WRITE_EXTERNAL_STORAGE...");
                requestPermissions(
                    new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    PERMISSION_REQUEST_CODE);
            } else {
                Log.d(TAG, "Permissões de armazenamento já concedidas");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permissão de armazenamento CONCEDIDA, recarregando WebView...");
                webView.reload();
            } else {
                Log.w(TAG, "Permissão de armazenamento NEGADA pelo usuário");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                Log.d(TAG, "MANAGE_EXTERNAL_STORAGE CONCEDIDO, recarregando WebView...");
                webView.reload();
            } else {
                Log.w(TAG, "MANAGE_EXTERNAL_STORAGE não concedido");
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Executa JavaScript no WebView
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
     * Verifica se as bridges JavaScript estão disponíveis
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

    @Override
    protected void onDestroy() {
        Log.d(TAG, "=== onDestroy ===");
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
