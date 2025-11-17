package customerinfo.app;

import android.content.Context;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class HtmlActivity extends AppCompatActivity {

    private WebView webView;
    private static final String TAG = "HtmlActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.htmlactivity);

        webView = findViewById(R.id.webView);
        setupWebView();
    }

    private void setupWebView() {

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        // Required to render properly
        webView.setWebViewClient(new WebViewClient());

        // LINK Android ↔ JavaScript
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidInterface");

        // Load template
        try {
            webView.loadUrl("file:///android_asset/application_form.html");
            Log.d(TAG, "HTML Loaded Successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error loading HTML: " + e.getMessage());
            Toast.makeText(this, "Error loading form", Toast.LENGTH_SHORT).show();
        }
    }

    /* =============================
       ANDROID ↔ JAVASCRIPT INTERFACE
       ============================= */
    public class WebAppInterface {

        @JavascriptInterface
        public String fetchDataForApplication(String inputNumber, String type) {
            try {
                Log.d(TAG, "Fetch requested → " + inputNumber + " (" + type + ")");
                ApplicationFormHelper helper = new ApplicationFormHelper();
                java.util.Map<String, Object> result =
                        helper.fetchDataForApplicationForm(inputNumber, type);

                return new org.json.JSONObject(result).toString();

            } catch (Exception e) {
                Log.e(TAG, "fetch error: " + e.getMessage());
                return "{\"error\":\"Data fetch failed: " + e.getMessage() + "\"}";
            }
        }

        @JavascriptInterface
        public void saveAsPDF() {
            runOnUiThread(() -> {
                Log.d(TAG, "PDF save requested");
                createPdfFromWebView();
            });
        }

        @JavascriptInterface
        public void closeApplication() {
            finish();
        }

        @JavascriptInterface
        public void showToast(String msg) {
            Toast.makeText(HtmlActivity.this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    /* =============================
       PDF GENERATION
       ============================= */
    private void createPdfFromWebView() {
        try {
            PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);

            String jobName = "Application_Form_" + System.currentTimeMillis();

            PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter(jobName);

            PrintAttributes attributes = new PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build();

            printManager.print(jobName, adapter, attributes);

            Toast.makeText(this, "PDF Saving... Check Downloads folder", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Log.e(TAG, "PDF Error: " + e.getMessage());
            Toast.makeText(this, "PDF Save Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /* =============================
       BACK BUTTON HANDLER
       ============================= */
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}