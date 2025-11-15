package customerinfo.app;

import android.content.Context;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;
import java.util.HashMap;
import java.util.Map;

public class ApplicationFormHelper {
    private Context context;
    private WebView webView;

    public ApplicationFormHelper(Context context2, WebView webView2) {
        this.context = context2;
        this.webView = webView2;
    }

    @JavascriptInterface
    public void showToast(String message) {
        Toast.makeText(this.context, message, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void closeApplication() {
        // This will be handled by the dialog's close button
    }

    @JavascriptInterface
    public void fetchDataForApplication(String inputNumber, String billType) {
        Context context2 = this.context;
        if (context2 instanceof MainActivity) {
            ((MainActivity) context2).fetchDataForApplicationForm(inputNumber, billType);
        }
    }

    public void fillApplicationForm(Map<String, Object> result, String billType) {
        try {
            this.webView.post(new Runnable() {
                @Override
                public void run() {
                    String javascriptCode = generateApplicationJavascript(result, billType);
                    webView.evaluateJavascript(javascriptCode, null);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error filling form: " + e.getMessage());
        }
    }

    private String generateApplicationJavascript(Map<String, Object> result, String billType) {
        StringBuilder js = new StringBuilder();
        js.append("javascript:(function() {");
        js.append("hideLoading();");

        if (result == null || result.containsKey("error")) {
            String errorMessage = "No data available";
            if (result != null && result.get("error") != null) {
                errorMessage = result.get("error").toString();
            }
            js.append("showError('").append(escapeJavaScript(errorMessage)).append("');");
            js.append("})();");
            return js.toString();
        }

        // Extract data for form filling
        Map<String, Object> mergedData = mergeSERVERData(result);

        js.append("var data = {");

        // Meter number
        if (result.get("meter_number") != null) {
            js.append("meter_no: '").append(escapeJavaScript(result.get("meter_number").toString())).append("',");
        }

        // Consumer/Customer number
        if (result.get("consumer_number") != null) {
            js.append("consumer_no: '").append(escapeJavaScript(result.get("consumer_number").toString())).append("',");
        } else if (result.get("customer_number") != null) {
            js.append("consumer_no: '").append(escapeJavaScript(result.get("customer_number").toString())).append("',");
        }

        // Customer information from merged data
        if (mergedData != null && !mergedData.isEmpty()) {
            if (mergedData.containsKey("customer_info")) {
                Map<String, String> customerInfo = (Map) mergedData.get("customer_info");

                // Required fields for the form
                js.append("customer_name: '").append(escapeJavaScript(
                    customerInfo.getOrDefault("Customer Name", 
                    customerInfo.getOrDefault("Name", "")))).append("',");

                js.append("father_name: '").append(escapeJavaScript(
                    customerInfo.getOrDefault("Father Name", ""))).append("',");

                js.append("address: '").append(escapeJavaScript(
                    customerInfo.getOrDefault("Customer Address", 
                    customerInfo.getOrDefault("Address", "")))).append("',");

                js.append("mobile_no: '").append(escapeJavaScript(
                    customerInfo.getOrDefault("Phone", 
                    customerInfo.getOrDefault("Mobile", "")))).append("',");
            }

            // Balance information for arrear
            if (mergedData.containsKey("balance_info")) {
                Map<String, String> balanceInfo = (Map) mergedData.get("balance_info");
                js.append("arrear: '").append(escapeJavaScript(
                    formatArrearAmount(balanceInfo.getOrDefault("Arrear Amount", 
                    balanceInfo.getOrDefault("Total Balance", ""))))).append("',");
            }
        }

        // Remove trailing comma if exists
        if (js.charAt(js.length() - 1) == ',') {
            js.setLength(js.length() - 1);
        }

        js.append("};");
        js.append("console.log('Filling form with data:', data);");
        js.append("fillFormWithData(data);");
        js.append("})();");

        return js.toString();
    }

    public void showError(String message) {
        this.webView.post(new Runnable() {
            @Override
            public void run() {
                webView.evaluateJavascript("javascript:showError('" + escapeJavaScript(message) + "');", null);
            }
        });
    }

    public void hideLoading() {
        this.webView.post(new Runnable() {
            @Override
            public void run() {
                webView.evaluateJavascript("javascript:hideLoading();", null);
            }
        });
    }

    public void showLoading() {
        this.webView.post(new Runnable() {
            @Override
            public void run() {
                webView.evaluateJavascript("javascript:showLoading();", null);
            }
        });
    }

    private String formatArrearAmount(String arrear) {
        try {
            if (arrear == null || arrear.equals("N/A") || arrear.isEmpty()) {
                return "...........";
            }

            String cleanArrear = arrear.replaceAll("[^\\d.]", "");
            if (cleanArrear.isEmpty() || cleanArrear.equals("0") || cleanArrear.equals("0.0") || cleanArrear.equals("0.00")) {
                return "...........";
            }

            double amount = Double.parseDouble(cleanArrear);
            if (amount == 0.0d) {
                return "...........";
            }

            return "৳" + String.format("%.0f", amount);
        } catch (Exception e) {
            return "...........";
        }
    }

    private String escapeJavaScript(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("'", "\\'")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private Map<String, Object> mergeSERVERData(Map<String, Object> map) {
        // This should call the MainActivity's mergeSERVERData method
        if (context instanceof MainActivity) {
            return ((MainActivity) context).mergeSERVERDataForApplication(map);
        }
        return new HashMap<>();
    }
}