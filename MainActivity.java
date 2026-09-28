package com.ayush.smartbulbir;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    private WebView webView;
    private ConsumerIrManager irManager;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        irManager = (ConsumerIrManager) getSystemService(CONSUMER_IR_SERVICE);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new IrBridge(), "AndroidIR");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private boolean hasEmitter() {
        return irManager != null && irManager.hasIrEmitter();
    }

    private int[] necPattern(long value) {
        int[] pattern = new int[2 + (32 * 2) + 1];
        int p = 0;
        pattern[p++] = 9000;
        pattern[p++] = 4500;

        for (int i = 31; i >= 0; i--) {
            int bit = (int)((value >>> i) & 1L);
            pattern[p++] = 560;
            pattern[p++] = bit == 1 ? 1690 : 560;
        }

        pattern[p] = 560;
        return pattern;
    }

    private boolean transmit(long value, int requestedFrequency) {
        if (!hasEmitter()) return false;

        int frequency = requestedFrequency > 0 ? requestedFrequency : 38000;
        int[] pattern = necPattern(value);

        // ConsumerIrManager requires a pattern shorter than 2 seconds.
        irManager.transmit(frequency, pattern);
        return true;
    }

    private class IrBridge {
        @JavascriptInterface
        public boolean hasIrEmitter() {
            return hasEmitter();
        }

        @JavascriptInterface
        public boolean transmit(String hexOrUnsignedDecimal, int frequency) {
            try {
                long value;
                String s = hexOrUnsignedDecimal.trim();
                if (s.startsWith("0x") || s.startsWith("0X")) {
                    value = Long.parseLong(s.substring(2), 16);
                } else {
                    value = Long.parseLong(s);
                }

                boolean sent = transmit(value & 0xFFFFFFFFL, frequency);
                if (!sent) {
                    runOnUiThread(() -> Toast.makeText(
                            MainActivity.this,
                            "No IR emitter detected",
                            Toast.LENGTH_SHORT
                    ).show());
                }
                return sent;
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        "Invalid IR code",
                        Toast.LENGTH_SHORT
                ).show());
                return false;
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidIR");
            webView.destroy();
        }
        super.onDestroy();
    }
}
