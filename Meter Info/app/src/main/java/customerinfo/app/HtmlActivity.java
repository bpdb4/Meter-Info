package customerinfo.app;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

public class HtmlActivity extends AppCompatActivity {
    private WebView webView;

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
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidInterface");
        webView.loadUrl("file:///android_asset/application_form.html");
    }

    public class WebAppInterface {
        @android.webkit.JavascriptInterface
        public String fetchDataForApplication(String inputNumber, String type) {
            try {
                // FIXED: Use HtmlActivity.this instead of just HtmlActivity.this
                ApplicationFormHelper helper = new ApplicationFormHelper(HtmlActivity.this);
                java.util.Map<String, Object> result = helper.fetchDataForApplicationForm(inputNumber, type);
                org.json.JSONObject jsonResult = new org.json.JSONObject(result);
                return jsonResult.toString();
            } catch (Exception e) {
                return "{\"error\":\"Data fetch failed: " + e.getMessage() + "\"}";
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
}