package com.vos.tvlauncher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int FILE_CHOOSER_REQUEST = 4101;

    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        web.setBackgroundColor(0xFF06080C);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        web.addJavascriptInterface(new Bridge(), "NativeBridge");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(MainActivity.this,
                            "No file picker found on this TV", Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });

        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.loadUrl("file:///android_asset/index.html");
        web.requestFocus();

        hideSystemUi();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void callJs(String js) {
        if (web != null) {
            web.evaluateJavascript(js, null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        web.onResume();
        callJs("window.onNativeResume && window.onNativeResume()");
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home button pressed while the launcher is already running
        callJs("window.onNativeResume && window.onNativeResume()");
    }

    @Override
    public void onBackPressed() {
        // The Home screen never exits; Back just acts like Escape inside the launcher UI.
        callJs("window.dispatchEvent(new KeyboardEvent('keydown',"
                + "{key:'Escape',keyCode:27,bubbles:true}))");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void toast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Exposed to the page as window.NativeBridge */
    private class Bridge {
        @JavascriptInterface
        public boolean launchApp(String pkg) {
            final Intent intent;
            PackageManager pm = getPackageManager();

            if ("com.android.settings".equals(pkg)) {
                intent = new Intent(Settings.ACTION_SETTINGS);
            } else {
                Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (i == null) {
                    i = pm.getLaunchIntentForPackage(pkg);
                }
                intent = i;
            }

            if (intent == null) {
                toast("App not installed");
                return false;
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this,
                                "Couldn't open app", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            return true;
        }
    }
}
