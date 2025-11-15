package customerinfo.app;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.HostnameVerifier;
import android.graphics.Color;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.os.Build;
import android.os.Environment;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

// Application form imports
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AlertDialog;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.graphics.Rect;
import android.os.Handler;
import android.view.inputmethod.EditorInfo;

public class MainActivity extends AppCompatActivity {

    private EditText meterInput;
    private Button submitBtn;
    private TextView resultView;
    private RadioButton prepaidBtn, postpaidBtn, consumerNoOption, meterNoOption;
    private RadioGroup mainRadioGroup, postpaidRadioGroup;
    private LinearLayout postpaidOptionsLayout;
    private String selectedType = "prepaid";
    private String postpaidSubType = "consumer_no";
    private ExcelHelper excelHelper;
    private static final int STORAGE_PERMISSION_CODE = 100;
    private static final String TAG = "ExcelHelper";
    private UIHelper uiHelper;
    private LinearLayout startupLayout, mainLayout;
    private Button backBtn;
    private ApplicationFormHelper applicationFormHelper;
    private Map<String, Object> lastSearchResult;
    private String selectedMode = "";

    // Fix SSL certificate issues
    static {
        trustAllCertificates();
    }

    private static void trustAllCertificates() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }
                    }
            };

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check storage permission FIRST
        checkStoragePermission();

        // Initialize views FIRST
        initViews();

        // THEN setup click listeners
        setupClickListeners();
        
        // Show startup screen
        showStartupScreen();
        
        // Show Excel save status in result
        showResult("🔍 Enter meter/consumer number and search\n💾 Data auto-saves to Excel after each search");
    }

    private void initViews() {
        startupLayout = findViewById(R.id.startupLayout);
        mainLayout = findViewById(R.id.mainLayout);
        backBtn = findViewById(R.id.backBtn);
        
        meterInput = findViewById(R.id.meterInput);
        prepaidBtn = findViewById(R.id.prepaidBtn);
        postpaidBtn = findViewById(R.id.postpaidBtn);
        submitBtn = findViewById(R.id.submitBtn);
        resultView = findViewById(R.id.resultView);

        // Postpaid options
        postpaidOptionsLayout = findViewById(R.id.postpaidOptionsLayout);
        postpaidRadioGroup = findViewById(R.id.postpaidRadioGroup);
        consumerNoOption = findViewById(R.id.consumerNoOption);
        meterNoOption = findViewById(R.id.meterNoOption);

        resultView.setKeyListener(null);
        resultView.setTextIsSelectable(true);
        resultView.setFocusableInTouchMode(true);
        resultView.setLongClickable(true);

        // Initialize UI Helper
        uiHelper = new UIHelper(this, resultView, findViewById(R.id.tableContainer));
        excelHelper = new ExcelHelper(this);

        // Setup back button
        backBtn.setOnClickListener(v -> showStartupScreen());

        updateButtonStates();
        updatePostpaidSubOptions();
        updateInputHint();
    }

    private void showStartupScreen() {
        startupLayout.setVisibility(View.VISIBLE);
        mainLayout.setVisibility(View.GONE);
        
        Button lookupBtn = findViewById(R.id.lookupBtn);
        Button applicationBtn = findViewById(R.id.applicationBtn);
        
        lookupBtn.setOnClickListener(v -> {
            selectedMode = "lookup";
            showMainInterface();
            showResult("🔍 Lookup mode selected\nEnter meter/consumer number to search");
        });
        
        applicationBtn.setOnClickListener(v -> {
            selectedMode = "application";
            openApplicationForm();
        });
    }

    private void showMainInterface() {
        startupLayout.setVisibility(View.GONE);
        mainLayout.setVisibility(View.VISIBLE);
        updateButtonStates();
        updatePostpaidSubOptions();
        updateInputHint();
        
        // Show keyboard automatically
        new Handler().postDelayed(() -> {
            showKeyboard();
        }, 300);
    }

    private void setupClickListeners() {
        prepaidBtn.setOnClickListener(v -> {
            selectedType = "prepaid";
            postpaidOptionsLayout.setVisibility(View.GONE);
            updateButtonStates();
            updateInputHint();
            showResult("📱 Prepaid selected - Enter 12-digit meter number");
            showKeyboard();
        });

        postpaidBtn.setOnClickListener(v -> {
            selectedType = "postpaid";
            postpaidOptionsLayout.setVisibility(View.VISIBLE);
            updateButtonStates();
            updateInputHint();
            showResult("💡 Postpaid selected - Choose input type");
            showKeyboard();
        });

        // Postpaid sub-options listeners
        postpaidRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.consumerNoOption) {
                postpaidSubType = "consumer_no";
                updatePostpaidSubOptions();
                updateInputHint();
                showResult("👤 Consumer No selected - Enter consumer number");
                showKeyboard();
            } else if (checkedId == R.id.meterNoOption) {
                postpaidSubType = "meter_no";
                updatePostpaidSubOptions();
                updateInputHint();
                showResult("🔢 Meter No selected - Enter meter number");
                showKeyboard();
            }
        });

        submitBtn.setOnClickListener(v -> {
            String inputNumber = meterInput.getText().toString().trim();
            if (inputNumber.isEmpty()) {
                showResult("❌ Please enter number");
                return;
            }

            if (selectedType.equals("prepaid") && inputNumber.length() != 12) {
                showResult("❌ Prepaid meter must be 12 digits");
                return;
            }

            // Hide keyboard when searching
            hideKeyboard();
            fetchData(inputNumber);
        });

        // Add keyboard handling for EditText
        meterInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    // Hide keyboard and trigger search
                    hideKeyboard();
                    submitBtn.performClick();
                    return true;
                }
                return false;
            }
        });
    }

    // Keyboard methods
    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && meterInput != null) {
            meterInput.requestFocus();
            imm.showSoftInput(meterInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && meterInput != null) {
            imm.hideSoftInputFromWindow(meterInput.getWindowToken(), 0);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int)ev.getRawX(), (int)ev.getRawY())) {
                    v.clearFocus();
                    hideKeyboard();
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void updateButtonStates() {
        prepaidBtn.setBackgroundColor(selectedType.equals("prepaid") ? 0xFF3498DB : 0xFF95A5A6);
        postpaidBtn.setBackgroundColor(selectedType.equals("postpaid") ? 0xFF3498DB : 0xFF95A5A6);
    }

    private void updatePostpaidSubOptions() {
        consumerNoOption.setBackgroundColor(postpaidSubType.equals("consumer_no") ? 0xFF2196F3 : 0xFF90CAF9);
        meterNoOption.setBackgroundColor(postpaidSubType.equals("meter_no") ? 0xFF2196F3 : 0xFF90CAF9);
    }

    private void updateInputHint() {
        if (selectedType.equals("prepaid")) {
            meterInput.setHint("Enter 12-digit meter number");
        } else {
            if (postpaidSubType.equals("consumer_no")) {
                meterInput.setHint("Enter consumer number");
            } else {
                meterInput.setHint("Enter meter number");
            }
        }
    }

    private void fetchData(String inputNumber) {
        // Clear UI first
        uiHelper.clearAll();

        showResult("🔄 Fetching " + selectedType + " data...\nInput: " + inputNumber);

        new Thread(() -> {
            try {
                Map<String, Object> result = fetchDataBasedOnType(inputNumber);
                String output = displayResult(result, selectedType);

                // Save to Excel
                saveLookupToExcel(result, inputNumber);

                runOnUiThread(() -> {
                    uiHelper.displayTextResult(output);
                    displayTableIfAvailable(result);
                });

            } catch (Exception e) {
                runOnUiThread(() -> showResult("❌ Error: " + e.getMessage()));
            }
        }).start();
    }

    private Map<String, Object> fetchDataBasedOnType(String inputNumber) {
        if (selectedType.equals("prepaid")) {
            return fetchPrepaidData(inputNumber);
        } else {
            if (postpaidSubType.equals("consumer_no")) {
                return fetchPostpaidData(inputNumber);
            } else {
                return fetchMeterLookupData(inputNumber);
            }
        }
    }

private void openApplicationForm() {
    setContentView(R.layout.application_form);

    WebView webView = findViewById(R.id.webView);
    webView.getSettings().setJavaScriptEnabled(true);
    webView.setWebViewClient(new WebViewClient());

    webView.loadUrl("file:///android_asset/application_form.html");
}

    // API METHODS
    public static Map<String, Object> getCustomerNumbersByMeter(String meterNumber) {
        Map<String, Object> result = new HashMap<>();
        try {
            HttpsURLConnection conn = (HttpsURLConnection) new URL("https://billonwebapi.bpdb.gov.bd/api/BillInformation/GetCustomerMeterbyMeterNo/12/" + meterNumber).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                
                JSONObject meterData = new JSONObject(response.toString());
                if (meterData.getInt("status") == 1 && meterData.has("content")) {
                    JSONArray customers = meterData.getJSONArray("content");
                    List<String> customerNumbers = new ArrayList<>();
                    for (int i = 0; i < customers.length(); i++) {
                        JSONObject customer = customers.getJSONObject(i);
                        String custNum = customer.optString("CUSTOMER_NUM");
                        if (!custNum.isEmpty()) {
                            customerNumbers.add(custNum);
                        }
                    }
                    if (!customerNumbers.isEmpty()) {
                        result.put("customer_numbers", customerNumbers);
                        result.put("meter_api_data", meterData);
                    } else {
                        result.put("error", "No customer numbers found for this meter");
                    }
                } else {
                    result.put("error", "No customer data found for this meter");
                }
            } else {
                result.put("error", "HTTP Error: " + conn.getResponseCode());
            }
        } catch (Exception e) {
            result.put("error", "Meter API Error: " + e.getMessage());
        }
        return result;
    }

    private Map<String, Object> fetchMeterLookupData(String meterNumber) {
        Map<String, Object> result = new HashMap<>();
        result.put("meter_number", meterNumber);
        
        Map<String, Object> meterResult = getCustomerNumbersByMeter(meterNumber);
        if (meterResult.containsKey("error")) {
            result.put("error", meterResult.get("error"));
            return result;
        }
        
        if (!meterResult.containsKey("customer_numbers")) {
            result.put("error", "No customer numbers found for this meter");
            return result;
        }
        
        List<String> customerNumbers = (List<String>) meterResult.get("customer_numbers");
        result.put("customer_numbers", customerNumbers);
        result.put("meter_api_data", meterResult.get("meter_api_data"));
        
        List<Map<String, Object>> customerResults = new ArrayList<>();
        for (String custNum : customerNumbers) {
            customerResults.add(fetchPostpaidData(custNum));
        }
        result.put("customer_results", customerResults);
        return result;
    }

    public static Map<String, Object> SERVER1Lookup(String meterNumber) {
        Map<String, Object> result = new HashMap<>();
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL("http://web.bpdbprepaid.gov.bd/bn/token-check").openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Accept", "text/x-component");
            conn.setRequestProperty("Content-Type", "text/plain;charset=UTF-8");
            conn.setRequestProperty("Next-Action", "29e85b2c55c9142822fe8da82a577612d9e58bb2");
            conn.setRequestProperty("Origin", "http://web.bpdbprepaid.gov.bd");
            conn.setRequestProperty("Referer", "http://web.bpdbprepaid.gov.bd/bn/token-check");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            OutputStream os = conn.getOutputStream();
            os.write(("[{\"meterNo\":\"" + meterNumber + "\"}]").getBytes("UTF-8"));
            os.flush();
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                String responseBody = response.toString();
                result.put("consumer_number", extractConsumerNumber(responseBody));
                result.put("SERVER1_data", responseBody);
            } else {
                result.put("error", "HTTP Error: " + responseCode);
            }
        } catch (Exception e) {
            result.put("error", "SERVER 1 Error: " + e.getMessage());
        }
        return result;
    }

    private static String extractConsumerNumber(String responseBody) {
        try {
            Matcher matcher = Pattern.compile("\"customerNo\"\\s*:\\s*\"(\\d+)\"").matcher(responseBody);
            if (matcher.find()) {
                return matcher.group(1);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static Map<String, Object> SERVER2Lookup(String accountNumber) {
        Map<String, Object> result = new HashMap<>();
        try {
            HttpsURLConnection conn = (HttpsURLConnection) new URL("https://billonwebAPI.bpdb.gov.bd/API/CustomerInformation/" + accountNumber).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                
                JSONObject SERVER2Data = new JSONObject(response.toString());
                if (isValidSERVER2Data(SERVER2Data)) {
                    result.put("SERVER2_data", SERVER2Data);
                } else {
                    result.put("error", "SERVER 2 returned invalid data");
                }
            } else {
                result.put("error", "HTTP Error: " + conn.getResponseCode());
            }
        } catch (Exception e) {
            result.put("error", "SERVER 2 Error: " + e.getMessage());
        }
        return result;
    }

    private static boolean isValidSERVER2Data(JSONObject SERVER2Data) {
        if (SERVER2Data == null) return false;
        try {
            return (SERVER2Data.has("customerInfo") && SERVER2Data.getJSONArray("customerInfo").length() > 0) ||
                   SERVER2Data.has("finalBalanceInfo") || 
                   SERVER2Data.has("balanceInfo");
        } catch (Exception e) {
            return false;
        }
    }

    public static Map<String, Object> SERVER3Lookup(String customerNumber) {
        Map<String, Object> result = new HashMap<>();
        
        // First get SERVER2 data
        Map<String, Object> SERVER2Result = SERVER2Lookup(customerNumber);
        if (SERVER2Result != null && !SERVER2Result.containsKey("error")) {
            result.put("SERVER2_data", SERVER2Result.get("SERVER2_data"));
        }

        // Then get SERVER3 data
        try {
            HttpsURLConnection conn = (HttpsURLConnection) new URL("https://miscbillAPI.bpdb.gov.bd/API/v1/get-pre-customer_info/" + customerNumber).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                
                JSONObject SERVER3Data = new JSONObject(response.toString());
                if (isValidSERVER3Data(SERVER3Data)) {
                    result.put("SERVER3_data", SERVER3Data);
                    result.put("source", "SERVER3_with_SERVER2");
                } else {
                    result.put("source", "SERVER2_only");
                }
            } else {
                result.put("source", "SERVER2_only");
            }
        } catch (Exception e) {
            result.put("source", "SERVER2_only");
        }
        
        if (!result.containsKey("SERVER2_data") && !result.containsKey("SERVER3_data")) {
            result.put("error", "Both SERVER 3 and SERVER 2 failed to return valid data");
        }
        return result;
    }

    private static boolean isValidSERVER3Data(JSONObject SERVER3Data) {
        if (SERVER3Data == null) return false;
        try {
            String customerNumber = SERVER3Data.optString("customerNumber", "").trim();
            String customerName = SERVER3Data.optString("customerName", "").trim();
            return !customerNumber.isEmpty() && !customerName.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> fetchPrepaidData(String meterNumber) {
        Map<String, Object> SERVER1Result = SERVER1Lookup(meterNumber);
        Map<String, Object> result = new HashMap<>();
        result.put("meter_number", meterNumber);
        result.put("SERVER1_data", SERVER1Result.get("SERVER1_data"));
        result.put("consumer_number", SERVER1Result.get("consumer_number"));
        
        String consumerNumber = (String) SERVER1Result.get("consumer_number");
        if (consumerNumber != null && !SERVER1Result.containsKey("error")) {
            Map<String, Object> SERVER3Result = SERVER3Lookup(consumerNumber);
            if (SERVER3Result != null && !SERVER3Result.containsKey("error")) {
                String source = (String) SERVER3Result.getOrDefault("source", "unknown");
                if (SERVER3Result.containsKey("SERVER3_data")) {
                    result.put("SERVER3_data", SERVER3Result.get("SERVER3_data"));
                }
                if (SERVER3Result.containsKey("SERVER2_data")) {
                    result.put("SERVER2_data", SERVER3Result.get("SERVER2_data"));
                }
                result.put("data_source", source);
            } else {
                result.put("error", "All data sources failed");
            }
        }
        return result;
    }

    private Map<String, Object> fetchPostpaidData(String customerNumber) {
        Map<String, Object> result = new HashMap<>();
        result.put("customer_number", customerNumber);
        
        Map<String, Object> SERVER3Result = SERVER3Lookup(customerNumber);
        if (SERVER3Result != null && !SERVER3Result.containsKey("error")) {
            String source = (String) SERVER3Result.getOrDefault("source", "unknown");
            if (SERVER3Result.containsKey("SERVER3_data")) {
                result.put("SERVER3_data", SERVER3Result.get("SERVER3_data"));
            }
            if (SERVER3Result.containsKey("SERVER2_data")) {
                result.put("SERVER2_data", SERVER3Result.get("SERVER2_data"));
            }
            result.put("data_source", source);
        } else {
            result.put("error", "All data sources failed");
        }
        return result;
    }

    // DATA PROCESSING METHODS
    private void displayTableIfAvailable(Map<String, Object> result) {
        try {
            if (selectedType.equals("postpaid") && postpaidSubType.equals("meter_no") && result.containsKey("customer_results")) {
                List<Map<String, Object>> customerResults = (List<Map<String, Object>>) result.get("customer_results");
                for (Map<String, Object> customerResult : customerResults) {
                    showTableForCustomer(customerResult);
                }
            } else {
                showTableForCustomer(result);
            }
        } catch (Exception e) {
            // Ignore table errors
        }
    }

    private void showTableForCustomer(Map<String, Object> result) {
        try {
            Object server2DataObj = result.get("SERVER2_data");
            if (server2DataObj instanceof JSONObject) {
                JSONObject server2Data = (JSONObject) server2DataObj;
                if (server2Data.has("billInfo")) {
                    JSONArray billInfo = server2Data.getJSONArray("billInfo");
                    if (billInfo.length() > 0) {
                        uiHelper.displayBillTable(billInfo);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore table errors
        }
    }

    private String displayResult(Map<String, Object> result, String billType) {
        if (result == null) {
            return "❌ No result data available";
        }
        
        StringBuilder output = new StringBuilder();
        output.append("\n══════════════════════════════════════════════════\n");
        output.append("📊 ").append(billType.toUpperCase()).append(" METER INFO\n");
        output.append("══════════════════════════════════════════════════\n");
        
        if (result.containsKey("error")) {
            output.append("❌ Error: ").append(result.get("error")).append("\n");
            return output.toString();
        }
        
        output.append("🔢 Meter Number: ").append(result.getOrDefault("meter_number", "N/A")).append("\n");
        
        if ("prepaid".equals(billType)) {
            if (result.get("consumer_number") != null) {
                output.append("👤 Consumer Number: ").append(result.get("consumer_number")).append("\n");
            }
            
            // Process prepaid data
            Map<String, Object> mergedData = mergeSERVERData(result);
            if (mergedData != null && !mergedData.isEmpty()) {
                output.append(formatMergedDisplayWithoutTable(mergedData));
            }
        } else if ("postpaid".equals(billType)) {
            if (result.containsKey("customer_results")) {
                List<String> customerNumbers = (List<String>) result.get("customer_numbers");
                List<Map<String, Object>> customerResults = (List<Map<String, Object>>) result.get("customer_results");
                output.append("\n📊 Found ").append(customerNumbers.size()).append(" customer(s) for this meter\n\n");
                
                for (int i = 0; i < customerResults.size(); i++) {
                    output.append("=".repeat(40)).append("\n");
                    output.append("👤 CUSTOMER ").append(i + 1).append("/").append(customerNumbers.size()).append(": ").append(customerNumbers.get(i)).append("\n");
                    output.append("=".repeat(40)).append("\n");
                    
                    Map<String, Object> mergedData = mergeSERVERData(customerResults.get(i));
                    if (mergedData != null && !mergedData.isEmpty()) {
                        output.append(formatMergedDisplayWithoutTable(mergedData)).append("\n");
                    } else {
                        output.append("❌ No data available for this customer\n\n");
                    }
                }
            } else {
                output.append("👤 Consumer Number: ").append(result.getOrDefault("customer_number", "N/A")).append("\n");
                Map<String, Object> mergedData = mergeSERVERData(result);
                if (mergedData != null && !mergedData.isEmpty()) {
                    output.append("=".repeat(30)).append("\n");
                    output.append(formatMergedDisplayWithoutTable(mergedData));
                } else {
                    output.append("❌ No customer data found\n");
                }
            }
        }
        
        return output.toString();
    }

    // DATA MERGING AND CLEANING METHODS
    private Map<String, Object> mergeSERVERData(Map<String, Object> source) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, String> customerInfo = new HashMap<>();
            Map<String, String> balanceInfo = new HashMap<>();
            
            // Copy basic info
            if (source.containsKey("meter_number")) {
                result.put("meter_number", source.get("meter_number"));
            }
            if (source.containsKey("consumer_number")) {
                result.put("consumer_number", source.get("consumer_number"));
            }
            if (source.containsKey("customer_number")) {
                result.put("customer_number", source.get("customer_number"));
            }
            
            // Process SERVER1 data
            if (source.containsKey("SERVER1_data")) {
                Map<String, Object> cleanedServer1 = cleanSERVER1Data(source.get("SERVER1_data"));
                if (cleanedServer1.containsKey("customer_info")) {
                    customerInfo.putAll((Map<String, String>) cleanedServer1.get("customer_info"));
                }
            }
            
            // Process SERVER2 data
            if (source.containsKey("SERVER2_data") && source.get("SERVER2_data") instanceof JSONObject) {
                Map<String, Object> cleanedServer2 = cleanSERVER2Data((JSONObject) source.get("SERVER2_data"));
                if (cleanedServer2.containsKey("customer_info")) {
                    customerInfo.putAll((Map<String, String>) cleanedServer2.get("customer_info"));
                }
                if (cleanedServer2.containsKey("balance_info")) {
                    balanceInfo.putAll((Map<String, String>) cleanedServer2.get("balance_info"));
                }
            }
            
            // Process SERVER3 data
            if (source.containsKey("SERVER3_data") && source.get("SERVER3_data") instanceof JSONObject) {
                Map<String, Object> cleanedServer3 = cleanSERVER3Data((JSONObject) source.get("SERVER3_data"));
                if (cleanedServer3.containsKey("customer_info")) {
                    customerInfo.putAll((Map<String, String>) cleanedServer3.get("customer_info"));
                }
                if (cleanedServer3.containsKey("balance_info") && balanceInfo.isEmpty()) {
                    balanceInfo.putAll((Map<String, String>) cleanedServer3.get("balance_info"));
                }
            }
            
            if (!customerInfo.isEmpty()) {
                result.put("customer_info", customerInfo);
            }
            if (!balanceInfo.isEmpty()) {
                result.put("balance_info", balanceInfo);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in mergeSERVERData: " + e.getMessage());
        }
        return result;
    }

    // Add this missing method for ApplicationFormHelper
    public Map<String, Object> mergeSERVERDataForApplication(Map<String, Object> map) {
        // For application form, use the same merging logic as regular lookup
        return mergeSERVERData(map);
    }

    private Map<String, Object> cleanSERVER1Data(Object SERVER1DataObj) {
        Map<String, Object> result = new HashMap<>();
        // Basic SERVER1 data cleaning
        try {
            if (SERVER1DataObj instanceof String) {
                String response = (String) SERVER1DataObj;
                Map<String, String> customerInfo = new HashMap<>();
                
                String consumerNumber = extractConsumerNumber(response);
                if (consumerNumber != null) {
                    customerInfo.put("Consumer Number", consumerNumber);
                }
                
                if (!customerInfo.isEmpty()) {
                    result.put("customer_info", customerInfo);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning SERVER1 data: " + e.getMessage());
        }
        return result;
    }

    private Map<String, Object> cleanSERVER2Data(JSONObject SERVER2Data) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, String> customerInfo = new HashMap<>();
            Map<String, String> balanceInfo = new HashMap<>();
            
            // Extract customer info
            if (SERVER2Data.has("customerInfo")) {
                JSONArray customerInfoArray = SERVER2Data.getJSONArray("customerInfo");
                if (customerInfoArray.length() > 0 && customerInfoArray.getJSONArray(0).length() > 0) {
                    JSONObject firstCustomer = customerInfoArray.getJSONArray(0).getJSONObject(0);
                    customerInfo.put("Customer Number", firstCustomer.optString("CUSTOMER_NUMBER"));
                    customerInfo.put("Customer Name", firstCustomer.optString("CUSTOMER_NAME"));
                    customerInfo.put("Address", firstCustomer.optString("ADDRESS"));
                    customerInfo.put("Meter Number", firstCustomer.optString("METER_NUM"));
                }
            }
            
            // Extract balance info
            if (SERVER2Data.has("finalBalanceInfo")) {
                String balanceString = SERVER2Data.optString("finalBalanceInfo");
                if (!balanceString.isEmpty() && !balanceString.equals("null")) {
                    balanceInfo.put("Total Balance", balanceString);
                    balanceInfo.put("Arrear Amount", balanceString);
                }
            }
            
            if (!customerInfo.isEmpty()) {
                result.put("customer_info", customerInfo);
            }
            if (!balanceInfo.isEmpty()) {
                result.put("balance_info", balanceInfo);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning SERVER2 data: " + e.getMessage());
        }
        return result;
    }

    private Map<String, Object> cleanSERVER3Data(JSONObject SERVER3Data) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, String> customerInfo = new HashMap<>();
            Map<String, String> balanceInfo = new HashMap<>();
            
            customerInfo.put("Customer Number", SERVER3Data.optString("customerNumber"));
            customerInfo.put("Customer Name", SERVER3Data.optString("customerName"));
            customerInfo.put("Customer Address", SERVER3Data.optString("customerAddr"));
            customerInfo.put("Meter Number", SERVER3Data.optString("meterNum"));
            customerInfo.put("Tariff Description", SERVER3Data.optString("tariffDesc"));
            
            String arrearAmount = SERVER3Data.optString("arrearAmount");
            if (!arrearAmount.isEmpty() && !arrearAmount.equals("null")) {
                balanceInfo.put("Total Balance", arrearAmount);
                balanceInfo.put("Arrear Amount", arrearAmount);
            }
            
            if (!customerInfo.isEmpty()) {
                result.put("customer_info", customerInfo);
            }
            if (!balanceInfo.isEmpty()) {
                result.put("balance_info", balanceInfo);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning SERVER3 data: " + e.getMessage());
        }
        return result;
    }

    private String formatMergedDisplayWithoutTable(Map<String, Object> data) {
        StringBuilder output = new StringBuilder();
        
        if (data.containsKey("customer_info")) {
            output.append("👤 CUSTOMER INFORMATION\n");
            output.append("══════════════════════════════════════════════════\n");
            
            Map<String, String> customerInfo = (Map<String, String>) data.get("customer_info");
            for (Map.Entry<String, String> entry : customerInfo.entrySet()) {
                if (isValidValue(entry.getValue())) {
                    output.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            }
        }
        
        if (data.containsKey("balance_info")) {
            output.append("\n💰 BALANCE INFORMATION\n");
            output.append("══════════════════════════════════════════════════\n");
            
            Map<String, String> balanceInfo = (Map<String, String>) data.get("balance_info");
            for (Map.Entry<String, String> entry : balanceInfo.entrySet()) {
                if (isValidValue(entry.getValue())) {
                    output.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            }
        }
        
        return output.toString();
    }

    private boolean isValidValue(String value) {
        return value != null && 
               !value.trim().isEmpty() && 
               !value.equals("N/A") && 
               !value.equals("null") && 
               !value.equals("{}") && 
               !value.equals("undefined");
    }

    // EXCEL METHODS
    private void saveLookupToExcel(Map<String, Object> result, String inputNumber) {
        if (excelHelper == null) {
            excelHelper = new ExcelHelper(this);
        }
        try {
            if (selectedType.equals("prepaid")) {
                Map<String, String> excelData = extractDataForExcel(result, "prepaid");
                excelHelper.savePrepaidLookup("User", inputNumber, excelData);
            } else {
                if (result.containsKey("customer_results")) {
                    List<Map<String, String>> excelDataList = new ArrayList<>();
                    List<Map<String, Object>> customerResults = (List<Map<String, Object>>) result.get("customer_results");
                    for (Map<String, Object> customerResult : customerResults) {
                        excelDataList.add(extractDataForExcel(customerResult, "postpaid"));
                    }
                    excelHelper.saveMultiplePostpaidLookups("User", excelDataList);
                } else {
                    Map<String, String> excelData = extractDataForExcel(result, "postpaid");
                    excelHelper.savePostpaidLookup("User", excelData);
                }
            }
            Log.d(TAG, "Data automatically saved to Excel");
        } catch (Exception e) {
            Log.e(TAG, "Error auto-saving to Excel: " + e.getMessage());
        }
    }

    private Map<String, String> extractDataForExcel(Map<String, Object> result, String type) {
        Map<String, String> excelData = new HashMap<>();
        try {
            if ("prepaid".equals(type)) {
                excelData.put("Meter Number", getSafeString(result.get("meter_number")));
                excelData.put("Consumer Number", getSafeString(result.get("consumer_number")));
            } else {
                excelData.put("Customer Number", getSafeString(result.get("customer_number")));
            }
            
            Map<String, Object> mergedData = mergeSERVERData(result);
            if (mergedData != null) {
                if (mergedData.containsKey("customer_info")) {
                    excelData.putAll((Map<String, String>) mergedData.get("customer_info"));
                }
                if (mergedData.containsKey("balance_info")) {
                    Map<String, String> balanceInfo = (Map<String, String>) mergedData.get("balance_info");
                    excelData.put("Arrear Amount", balanceInfo.get("Arrear Amount"));
                    excelData.put("Total Balance", balanceInfo.get("Total Balance"));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting data for Excel: " + e.getMessage());
        }
        return excelData;
    }

    private String getSafeString(Object value) {
        if (value == null) return "N/A";
        String stringValue = value.toString();
        return (stringValue.equals("null") || stringValue.isEmpty()) ? "N/A" : stringValue;
    }

    public void saveAndShareExcel() {
        if (excelHelper != null) {
            boolean success = excelHelper.saveExcelFile();
            if (success) {
                String filePath = excelHelper.getFilePath();
                shareExcelFile(filePath);
            } else {
                Toast.makeText(this, "Failed to save Excel file", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void shareExcelFile(String filePath) {
        Toast.makeText(this, "Excel file saved: " + filePath, Toast.LENGTH_LONG).show();
    }

    // APPLICATION FORM METHODS
    private void openApplicationForm() {
        try {
            WebView applicationWebView = new WebView(this);
            
            // Configure WebView settings
            WebSettings webSettings = applicationWebView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webSettings.setDomStorageEnabled(true);
            webSettings.setAllowFileAccess(true);
            webSettings.setBuiltInZoomControls(true);
            webSettings.setDisplayZoomControls(false);
            
            applicationFormHelper = new ApplicationFormHelper(this, applicationWebView);
            applicationWebView.addJavascriptInterface(applicationFormHelper, "AndroidInterface");
            
            applicationWebView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    Log.d("ApplicationForm", "Page finished loading: " + url);
                    showKeyboardForWebView(applicationWebView);
                }
                
                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    super.onReceivedError(view, errorCode, description, failingUrl);
                    Log.e("ApplicationForm", "WebView error: " + description);
                    if (applicationFormHelper != null) {
                        applicationFormHelper.hideLoading();
                    }
                }
            });
            
            // Show dialog with WebView
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("আবেদনপত্র");
            builder.setView(applicationWebView);
            builder.setPositiveButton("প্রিন্ট", (dialog, which) -> {
                try {
                    applicationWebView.evaluateJavascript("window.print();", null);
                } catch (Exception e) {
                    Log.e("ApplicationForm", "Print error: " + e.getMessage());
                }
            });
            builder.setNegativeButton("বন্ধ", (dialog, which) -> {
                dialog.dismiss();
                showStartupScreen();
            });
            
            AlertDialog dialog = builder.create();
            dialog.setOnShowListener(dialogInterface -> {
                Log.d("ApplicationForm", "Dialog shown");
                showKeyboardForWebView(applicationWebView);
            });
            
            dialog.setOnCancelListener(d -> {
                Log.d("ApplicationForm", "Dialog cancelled");
                showStartupScreen();
            });
            
            dialog.show();
            
            // Load the HTML form
            try {
                applicationWebView.loadUrl("file:///android_asset/application_form.html");
                Log.d("ApplicationForm", "Loading HTML from assets");
            } catch (Exception e) {
                Log.e("ApplicationForm", "Error loading HTML: " + e.getMessage());
                if (applicationFormHelper != null) {
                    applicationFormHelper.showError("Failed to load application form: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            Log.e("ApplicationForm", "Error opening application form: " + e.getMessage());
            Toast.makeText(this, "Error opening application form", Toast.LENGTH_SHORT).show();
            showStartupScreen();
        }
    }

    private void showKeyboardForWebView(final WebView webView) {
        if (webView != null) {
            webView.postDelayed(() -> {
                try {
                    webView.evaluateJavascript("javascript:document.getElementById('searchInput').focus();", null);
                    webView.requestFocus();
                    
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT);
                    }
                } catch (Exception e) {
                    Log.e("Keyboard", "Error showing keyboard: " + e.getMessage());
                }
            }, 1000);
        }
    }

    public void fetchDataForApplicationForm(String inputNumber, String billType) {
        this.selectedType = billType;
        if (applicationFormHelper != null) {
            applicationFormHelper.showLoading();
        }
        
        new Thread(() -> {
            try {
                Map<String, Object> result = fetchDataBasedOnType(inputNumber);
                runOnUiThread(() -> {
                    if (applicationFormHelper != null) {
                        applicationFormHelper.fillApplicationForm(result, billType);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (applicationFormHelper != null) {
                        Map<String, Object> errorResult = new HashMap<>();
                        errorResult.put("error", "Error: " + e.getMessage());
                        applicationFormHelper.fillApplicationForm(errorResult, billType);
                    }
                });
            }
        }).start();
    }

    // PERMISSION METHODS
    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                        },
                        STORAGE_PERMISSION_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Storage permission denied - Excel saving disabled", Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    // UTILITY METHODS
    private void showResult(String message) {
        runOnUiThread(() -> {
            if (resultView != null) {
                resultView.setText(message);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (excelHelper != null) {
            excelHelper.close();
        }
    }
}