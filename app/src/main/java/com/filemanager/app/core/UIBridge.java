package com.filemanager.app.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/**
 * UIBridge — Componentes UI nativos para o File Manager.
 *
 * Fornece diálogos, bottom sheets, toasts e overlays nativos
 * que complementam a interface HTML existente.
 *
 * Componentes:
 * - Toast messages (rápidos)
 * - Diálogos de confirmação
 * - Input dialogs (renomear, criar pasta)
 * - Bottom sheets de opções
 * - Progress overlay
 * - Info dialogs
 */
public class UIBridge {

    private static final String TAG = "UIBridge";
    private final Activity activity;
    private Dialog progressDialog;
    private ProgressBar progressDialogBar;
    private TextView progressDialogPct;
    private TextView progressDialogMsg;
    private android.webkit.WebView webView;

    public UIBridge(Activity activity) {
        this.activity = activity;
    }

    /**
     * Define o WebView para execução de callbacks JS.
     */
    public void setWebView(android.webkit.WebView webView) {
        this.webView = webView;
    }

    // ========================================
    //  TOAST
    // ========================================

    /**
     * Exibe uma mensagem Toast curta.
     */
    public void showToast(String message) {
        activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
    }

    /**
     * Exibe uma mensagem Toast longa.
     */
    public void showToastLong(String message) {
        activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }

    // ========================================
    //  DIÁLOGO DE CONFIRMAÇÃO
    // ========================================

    /**
     * Exibe diálogo de confirmação Sim/Não.
     * Chama callback JS quando o usuário responder.
     *
     * @param title título do diálogo
     * @param message mensagem
     * @param positiveLabel texto do botão positivo
     * @param negativeLabel texto do botão negativo
     * @param callbackName nome da função JS a chamar com true/false
     */
    public void showConfirmDialog(String title, String message,
                                   String positiveLabel, String negativeLabel,
                                   String callbackName) {
        activity.runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(title);
            builder.setMessage(message);
            builder.setCancelable(true);

            builder.setPositiveButton(positiveLabel, (dialog, which) -> {
                dialog.dismiss();
                notifyJsCallback(callbackName, true);
            });

            builder.setNegativeButton(negativeLabel, (dialog, which) -> {
                dialog.dismiss();
                notifyJsCallback(callbackName, false);
            });

            builder.setOnCancelListener(dialog -> notifyJsCallback(callbackName, false));
            builder.show();
        });
    }

    // ========================================
    //  INPUT DIALOG
    // ========================================

    /**
     * Exibe diálogo com campo de texto.
     * Útil para renomear, criar pasta, etc.
     *
     * @param title título
     * @param hint placeholder
     * @param defaultValue valor inicial
     * @param inputType tipo do campo (0=text, 1=number)
     * @param callbackName nome da função JS
     */
    public void showInputDialog(String title, String hint, String defaultValue,
                                 int inputType, String callbackName) {
        activity.runOnUiThread(() -> {
            EditText input = new EditText(activity);
            input.setHint(hint);
            input.setText(defaultValue != null ? defaultValue : "");
            input.setSelectAllOnFocus(true);

            if (inputType == 1) {
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
            }

            // Padding
            int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
            input.setPadding(pad, pad, pad, pad);

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(title);
            builder.setView(input);
            builder.setCancelable(true);

            builder.setPositiveButton("OK", (dialog, which) -> {
                String value = input.getText().toString().trim();
                dialog.dismiss();
                notifyJsCallback(callbackName, value);
            });

            builder.setNegativeButton("Cancelar", (dialog, which) -> {
                dialog.dismiss();
                notifyJsCallback(callbackName, (String) null);
            });

            builder.setOnCancelListener(dialog -> notifyJsCallback(callbackName, (String) null));

            AlertDialog dialog = builder.create();
            dialog.show();

            // Focar no input e abrir teclado
            input.requestFocus();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        });
    }

    // ========================================
    //  BOTTOM SHEET DE OPÇÕES
    // ========================================

    /**
     * Exibe bottom sheet com lista de opções.
     *
     * @param title título
     * @param options array de opções [{label: "Opção 1", action: "action1"}, ...]
     * @param callbackName função JS chamada com a action selecionada
     */
    public void showOptionsSheet(String title, String optionsJson, String callbackName) {
        activity.runOnUiThread(() -> {
            try {
                org.json.JSONArray options = new org.json.JSONArray(optionsJson);

                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setTitle(title);

                String[] labels = new String[options.length()];
                String[] actions = new String[options.length()];

                for (int i = 0; i < options.length(); i++) {
                    JSONObject opt = options.getJSONObject(i);
                    labels[i] = opt.getString("label");
                    actions[i] = opt.getString("action");
                }

                builder.setItems(labels, (dialog, which) -> {
                    dialog.dismiss();
                    notifyJsCallback(callbackName, actions[which]);
                });

                builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss());
                builder.show();

            } catch (Exception e) {
                showToast("Erro ao mostrar opções");
            }
        });
    }

    // ========================================
    //  PROGRESS OVERLAY
    // ========================================

    /**
     * Exibe overlay de progresso sobre o conteúdo.
     * Útil para operações onde o JS já mostra a UI principal.
     */
    public void showProgressOverlay(String message, int progress) {
        activity.runOnUiThread(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                updateProgressOverlay(progress, message);
                return;
            }

            progressDialog = new Dialog(activity);
            progressDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            progressDialog.setCancelable(false);

            LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER);
            int pad = (int) (32 * activity.getResources().getDisplayMetrics().density);
            layout.setPadding(pad, pad, pad, pad);
            layout.setBackgroundColor(Color.parseColor("#E6222222"));

            // Mensagem
            progressDialogMsg = new TextView(activity);
            progressDialogMsg.setText(message);
            progressDialogMsg.setTextColor(Color.WHITE);
            progressDialogMsg.setTextSize(16);
            progressDialogMsg.setGravity(Gravity.CENTER);
            layout.addView(progressDialogMsg);

            // Progress bar
            progressDialogBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            progressDialogBar.setMax(100);
            progressDialogBar.setProgress(progress);
            int marginTop = (int) (16 * activity.getResources().getDisplayMetrics().density);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = marginTop;
            progressDialogBar.setLayoutParams(params);
            layout.addView(progressDialogBar);

            // Texto de porcentagem
            progressDialogPct = new TextView(activity);
            progressDialogPct.setText(progress + "%");
            progressDialogPct.setTextColor(Color.WHITE);
            progressDialogPct.setTextSize(14);
            progressDialogPct.setGravity(Gravity.CENTER);
            int marginTop2 = (int) (8 * activity.getResources().getDisplayMetrics().density);
            FrameLayout.LayoutParams params2 = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params2.topMargin = marginTop2;
            progressDialogPct.setLayoutParams(params2);
            layout.addView(progressDialogPct);

            progressDialog.setContentView(layout);

            Window window = progressDialog.getWindow();
            if (window != null) {
                window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                );
                window.setGravity(Gravity.CENTER);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

            progressDialog.show();
        });
    }

    /**
     * Atualiza progresso do overlay.
     */
    public void updateProgressOverlay(int progress, String message) {
        activity.runOnUiThread(() -> {
            if (progressDialog == null || !progressDialog.isShowing()) return;

            if (progressDialogBar != null) {
                progressDialogBar.setProgress(progress);
            }

            if (progressDialogPct != null) {
                progressDialogPct.setText(progress + "%");
            }

            if (message != null && progressDialogMsg != null) {
                progressDialogMsg.setText(message);
            }
        });
    }

    /**
     * Esconde o overlay de progresso.
     */
    public void hideProgressOverlay() {
        activity.runOnUiThread(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
            progressDialog = null;
        });
    }

    // ========================================
    //  INFO DIALOG
    // ========================================

    /**
     * Exibe diálogo de informações.
     */
    public void showInfoDialog(String title, String message) {
        activity.runOnUiThread(() -> {
            new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
        });
    }

    /**
     * Exibe diálogo de erro.
     */
    public void showErrorDialog(String title, String message) {
        activity.runOnUiThread(() -> {
            new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
        });
    }

    // ========================================
    //  CALLBACKS
    // ========================================

    /**
     * Notifica o JS com o resultado de uma interação.
     */
    private void notifyJsCallback(String callbackName, boolean value) {
        if (callbackName == null || callbackName.isEmpty()) return;
        String script = String.format("if(window.%s) window.%s(%s);",
            callbackName, callbackName, value);
        runJs(script);
    }

    private void notifyJsCallback(String callbackName, String value) {
        if (callbackName == null || callbackName.isEmpty()) return;
        String escaped = value != null ? value.replace("\\", "\\\\").replace("\"", "\\\"") : "null";
        String script = String.format("if(window.%s) window.%s(\"%s\");",
            callbackName, callbackName, escaped);
        runJs(script);
    }

    private void runJs(String script) {
        activity.runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript(script, null);
            }
        });
    }

    // ========================================
    //  CLEANUP
    // ========================================

    public void dismissAll() {
        hideProgressOverlay();
    }
}
