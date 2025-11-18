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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
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

        // Initialize views
        initViews();

        // THEN setup click listeners
        setupClickListeners();
        
        // Initialize ExcelHelper only if permission already granted
        if (isStoragePermissionGranted()) {
            excelHelper = new ExcelHelper(this);
            showResult("✅ Excel ready - Enter meter number to search");
        } else {
            showResult("🔍 Enter meter/consumer number and search\n💾 Excel will be ready after permission");
        }
    }

    private void initViews() {
        meterInput = findViewById(R.id.meterInput);
        prepaidBtn = findViewById(R.id.prepaidBtn);
        postpaidBtn = findViewById(R.id.postpaidBtn);
        submitBtn = findViewById(R.id.submitBtn);
        resultView = findViewById(R.id.resultView);
        //excelBtn = findViewById(R.id.excelBtn);

        // Postpaid options
        postpaidOptionsLayout = findViewById(R.id.postpaidOptionsLayout);
        postpaidRadioGroup = findViewById(R.id.postpaidRadioGroup);
        consumerNoOption = findViewById(R.id.consumerNoOption);
        meterNoOption = findViewById(R.id.meterNoOption);

        Button backBtn = findViewById(R.id.backBtn);

        resultView.setKeyListener(null);  // This completely disables keyboard
        resultView.setTextIsSelectable(true);
        resultView.setFocusableInTouchMode(true);
        resultView.setLongClickable(true);

        // Initialize UI Helper
        uiHelper = new UIHelper(this, resultView, findViewById(R.id.tableContainer));
        // excelHelper = new ExcelHelper(this); // REMOVED - will be created after permission

        updateButtonStates();
        updatePostpaidSubOptions();
        updateInputHint();
    }


    private void setupClickListeners() {
        prepaidBtn.setOnClickListener(v -> {
            selectedType = "prepaid";
            postpaidOptionsLayout.setVisibility(View.GONE);
            updateButtonStates();
            updateInputHint();
            showResult("📱 Prepaid selected - Enter 12-digit meter number");
        });

        postpaidBtn.setOnClickListener(v -> {
            selectedType = "postpaid";
            postpaidOptionsLayout.setVisibility(View.VISIBLE);
            updateButtonStates();
            updateInputHint();
            showResult("💡 Postpaid selected - Choose input type");
        });

        // Postpaid sub-options listeners
        postpaidRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.consumerNoOption) {
                postpaidSubType = "consumer_no";
                updatePostpaidSubOptions();
                updateInputHint();
                showResult("👤 Consumer No selected - Enter consumer number");
            } else if (checkedId == R.id.meterNoOption) {
                postpaidSubType = "meter_no";
                updatePostpaidSubOptions();
                updateInputHint();
                showResult("🔢 Meter No selected - Enter meter number");
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


            fetchData(inputNumber);
        });

        // ✅ CORRECT PLACE: Add back button listener HERE
        findViewById(R.id.backBtn).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, Home.class);
            startActivity(intent);
            finish();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show();
                // Initialize ExcelHelper AFTER permission is granted
                if (excelHelper == null) {
                    excelHelper = new ExcelHelper(this);
                }
                showResult("✅ Excel ready - Enter meter number to search");
            } else {
                Toast.makeText(this, "Storage permission denied - Excel saving disabled", Toast.LENGTH_LONG).show();
                showResult("❌ Storage permission denied - Excel saving disabled");
            }
        }
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

                // ADD THIS LINE to save to Excel
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
            return postpaidSubType.equals("consumer_no") ?
                    fetchPostpaidData(inputNumber) : fetchMeterLookupData(inputNumber);
        }
    }

    private void displayTableIfAvailable(Map<String, Object> result) {
        try {
            // Handle METER NO lookup (multiple customers)
            if (selectedType.equals("postpaid") && postpaidSubType.equals("meter_no") &&
                    result.containsKey("customer_results")) {

                // Show tables for ALL customers found in meter lookup
                List<Map<String, Object>> customerResults = (List<Map<String, Object>>) result.get("customer_results");
                for (Map<String, Object> customerResult : customerResults) {
                    showTableForCustomer(customerResult);
                }
            }
            // Handle SINGLE customer (consumer no or prepaid)
            else {
                showTableForCustomer(result);
            }
        } catch (Exception e) {
            // Table is optional, ignore errors
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
            // Ignore errors for individual customers
        }
    }

    private void showResult(String message) {
        runOnUiThread(() -> resultView.setText(message));
    }

    // NEW: Get customer numbers from meter number
    public static Map<String, Object> getCustomerNumbersByMeter(String meterNumber) {
        Map<String, Object> result = new HashMap<>();
        try {
            String url = "https://billonwebapi.bpdb.gov.bd/api/BillInformation/GetCustomerMeterbyMeterNo/12/" + meterNumber;
            HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();

            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            System.out.println("🔍 METER LOOKUP API: Fetching customers for meter: " + meterNumber);

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

                    System.out.println("✅ Found " + customers.length() + " customer(s) for meter " + meterNumber);

                    for (int i = 0; i < customers.length(); i++) {
                        JSONObject customer = customers.getJSONObject(i);
                        String custNum = customer.optString("CUSTOMER_NUM");
                        if (!custNum.isEmpty()) {
                            customerNumbers.add(custNum);
                            System.out.println("   👤 Customer: " + custNum + " - " + customer.optString("CUSTOMER_NAME", "N/A"));
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
            System.out.println("❌ METER LOOKUP API Error: " + e.getMessage());
            result.put("error", "Meter API Error: " + e.getMessage());
        }
        return result;
    }

    private Map<String, Object> fetchMeterLookupData(String meterNumber) {
        Map<String, Object> result = new HashMap<>();
        result.put("meter_number", meterNumber);
        result.put("customer_numbers", new ArrayList<String>());
        result.put("customer_results", new ArrayList<Map<String, Object>>());

        System.out.println("🔍 Starting meter lookup for: " + meterNumber);

        // Step 1: Get customer numbers from meter API
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

        System.out.println("🔄 Processing " + customerNumbers.size() + " customer(s)");

        // Step 2: Process each customer number with postpaid workflow
        List<Map<String, Object>> customerResults = new ArrayList<>();
        for (String custNum : customerNumbers) {
            System.out.println("🔄 Processing customer: " + custNum);
            Map<String, Object> customerResult = fetchPostpaidData(custNum);
            customerResults.add(customerResult);
        }

        result.put("customer_results", customerResults);
        return result;
    }

    // SERVER 1: Get consumer number from prepaid meter
    public static Map<String, Object> SERVER1Lookup(String meterNumber) {
        Map<String, Object> result = new HashMap<>();
        try {
            String url = "http://web.bpdbprepaid.gov.bd/bn/token-check";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();

            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Accept", "text/x-component");
            conn.setRequestProperty("Content-Type", "text/plain;charset=UTF-8");
            conn.setRequestProperty("Next-Action", "29e85b2c55c9142822fe8da82a577612d9e58bb2");
            conn.setRequestProperty("Origin", "http://web.bpdbprepaid.gov.bd");
            conn.setRequestProperty("Referer", "http://web.bpdbprepaid.gov.bd/bn/token-check");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            String requestData = "[{\"meterNo\":\"" + meterNumber + "\"}]";
            OutputStream os = conn.getOutputStream();
            os.write(requestData.getBytes("UTF-8"));
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
                String consumerNumber = extractConsumerNumber(responseBody);

                result.put("consumer_number", consumerNumber);
                result.put("SERVER1_data", responseBody);
            } else {
                result.put("error", "HTTP Error: " + responseCode);
            }
        } catch (Exception e) {
            result.put("error", "SERVER 1 Error: " + e.getMessage());
        }
        return result;
    }

    // Extract consumer number from SERVER 1 response
    private static String extractConsumerNumber(String responseBody) {
        try {
            Pattern pattern = Pattern.compile("\"customerNo\"\\s*:\\s*\"(\\d+)\"");
            Matcher matcher = pattern.matcher(responseBody);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            System.out.println("Error extracting consumer number: " + e.getMessage());
        }
        return null;
    }

    // CORRECTED: SERVER3Lookup to always fetch both servers
    public static Map<String, Object> SERVER3Lookup(String customerNumber) {
        Map<String, Object> result = new HashMap<>();

        System.out.println("🔍 SERVER 3: Starting data fetch for: " + customerNumber);

        // ALWAYS try to fetch SERVER 2 data first (it has bill info)
        System.out.println("🔄 SERVER 3: Fetching SERVER 2 data first...");
        Map<String, Object> SERVER2Result = SERVER2Lookup(customerNumber);

        if (SERVER2Result != null && !SERVER2Result.containsKey("error")) {
            result.put("SERVER2_data", SERVER2Result.get("SERVER2_data"));
            System.out.println("✅ SERVER 3: SERVER2 data fetched successfully");
        } else {
            System.out.println("❌ SERVER 3: SERVER2 data fetch failed");
        }

        // Then try SERVER 3
        try {
            String url = "https://miscbillAPI.bpdb.gov.bd/API/v1/get-pre-customer_info/" + customerNumber;
            HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();

            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            System.out.println("🔍 SERVER 3: Fetching SERVER3 data for: " + customerNumber);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                JSONObject SERVER3Data = new JSONObject(response.toString());

                // Check if SERVER 3 returned valid data
                if (isValidSERVER3Data(SERVER3Data)) {
                    System.out.println("✅ SERVER 3: Valid data received");
                    result.put("SERVER3_data", SERVER3Data);
                    result.put("source", "SERVER3_with_SERVER2");
                } else {
                    System.out.println("⚠️ SERVER 3: Invalid or empty data");
                    result.put("source", "SERVER2_only");
                }
            } else {
                System.out.println("⚠️ SERVER 3: HTTP " + conn.getResponseCode());
                result.put("source", "SERVER2_only");
            }
        } catch (Exception e) {
            System.out.println("❌ SERVER 3 Error: " + e.getMessage());
            result.put("source", "SERVER2_only");
        }

        // If we have at least one data source, return success
        if (result.containsKey("SERVER2_data") || result.containsKey("SERVER3_data")) {
            System.out.println("✅ SERVER 3: Data fetch completed with sources: " + result.get("source"));
        } else {
            System.out.println("❌ SERVER 3: All data sources failed");
            result.put("error", "Both SERVER 3 and SERVER 2 failed to return valid data");
        }

        return result;
    }

    // Helper method to validate SERVER 3 data
    private static boolean isValidSERVER3Data(JSONObject SERVER3Data) {
        if (SERVER3Data == null) return false;

        try {
            // Check if essential fields exist and are not empty
            String customerNumber = SERVER3Data.optString("customerNumber", "").trim();
            String customerName = SERVER3Data.optString("customerName", "").trim();

            boolean hasValidData = !customerNumber.isEmpty() && !customerName.isEmpty();

            if (!hasValidData) {
                System.out.println("❌ SERVER 3: Missing essential fields (customerNumber or customerName)");
            }

            return hasValidData;

        } catch (Exception e) {
            System.out.println("❌ SERVER 3: Data validation error: " + e.getMessage());
            return false;
        }
    }

    // Enhanced SERVER 2 lookup with better error handling
    public static Map<String, Object> SERVER2Lookup(String accountNumber) {
        Map<String, Object> result = new HashMap<>();
        try {
            String url = "https://billonwebAPI.bpdb.gov.bd/API/CustomerInformation/" + accountNumber;
            HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();

            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            System.out.println("🔍 SERVER 2: Fetching data for: " + accountNumber);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                JSONObject SERVER2Data = new JSONObject(response.toString());

                // Validate SERVER 2 data
                if (isValidSERVER2Data(SERVER2Data)) {
                    System.out.println("✅ SERVER 2: Valid data received");
                    result.put("SERVER2_data", SERVER2Data);
                } else {
                    System.out.println("❌ SERVER 2: Invalid data received");
                    result.put("error", "SERVER 2 returned invalid data");
                }
            } else {
                System.out.println("❌ SERVER 2: HTTP Error: " + conn.getResponseCode());
                result.put("error", "HTTP Error: " + conn.getResponseCode());
            }
        } catch (Exception e) {
            System.out.println("❌ SERVER 2 Error: " + e.getMessage());
            result.put("error", "SERVER 2 Error: " + e.getMessage());
        }
        return result;
    }

    // Helper method to validate SERVER 2 data
    private static boolean isValidSERVER2Data(JSONObject SERVER2Data) {
        if (SERVER2Data == null) return false;

        try {
            // Check if SERVER 2 has customerInfo array with data
            if (SERVER2Data.has("customerInfo")) {
                JSONArray customerInfoArray = SERVER2Data.getJSONArray("customerInfo");
                if (customerInfoArray.length() > 0) {
                    return true;
                }
            }

            // Check if it has balance info
            if (SERVER2Data.has("finalBalanceInfo") || SERVER2Data.has("balanceInfo")) {
                return true;
            }

            System.out.println("❌ SERVER 2: No customerInfo or balance data found");
            return false;

        } catch (Exception e) {
            System.out.println("❌ SERVER 2: Data validation error: " + e.getMessage());
            return false;
        }
    }

    private Map<String, Object> fetchPrepaidData(String meterNumber) {
        Map<String, Object> SERVER1Result = SERVER1Lookup(meterNumber);
        Map<String, Object> result = new HashMap<>();

        result.put("meter_number", meterNumber);
        result.put("SERVER1_data", SERVER1Result.get("SERVER1_data"));
        result.put("consumer_number", SERVER1Result.get("consumer_number"));
        result.put("SERVER3_data", null);
        result.put("SERVER2_data", null);

        String consumerNumber = (String) SERVER1Result.get("consumer_number");
        if (consumerNumber != null && !SERVER1Result.containsKey("error")) {
            // Use the corrected SERVER3Lookup which now fetches both servers
            Map<String, Object> SERVER3Result = SERVER3Lookup(consumerNumber);

            if (SERVER3Result != null && !SERVER3Result.containsKey("error")) {
                // Check which SERVER provided the data
                String source = (String) SERVER3Result.getOrDefault("source", "unknown");
                System.out.println("📊 Prepaid data source: " + source);

                if (SERVER3Result.containsKey("SERVER3_data")) {
                    result.put("SERVER3_data", SERVER3Result.get("SERVER3_data"));
                }
                if (SERVER3Result.containsKey("SERVER2_data")) {
                    result.put("SERVER2_data", SERVER3Result.get("SERVER2_data"));
                }

                result.put("data_source", source);
            } else {
                System.out.println("❌ All SERVERs failed for prepaid data");
                result.put("error", "All data sources failed");
            }
        }

        return result;
    }

    private Map<String, Object> fetchPostpaidData(String customerNumber) {
        Map<String, Object> result = new HashMap<>();
        result.put("customer_number", customerNumber);

        System.out.println("🔍 Fetching postpaid data for: " + customerNumber);

        // Use the corrected SERVER3Lookup which now fetches both servers
        Map<String, Object> SERVER3Result = SERVER3Lookup(customerNumber);

        if (SERVER3Result != null && !SERVER3Result.containsKey("error")) {
            String source = (String) SERVER3Result.getOrDefault("source", "unknown");
            System.out.println("📊 Postpaid data source: " + source);

            // Copy all data from SERVER3Result
            if (SERVER3Result.containsKey("SERVER3_data")) {
                result.put("SERVER3_data", SERVER3Result.get("SERVER3_data"));
            }
            if (SERVER3Result.containsKey("SERVER2_data")) {
                result.put("SERVER2_data", SERVER3Result.get("SERVER2_data"));
            }

            result.put("data_source", source);
            System.out.println("✅ Postpaid data fetch successful");
        } else {
            System.out.println("❌ All SERVERs failed for postpaid data");
            result.put("error", "All data sources failed");
        }

        return result;
    }

    private String extractDirectValue(JSONObject jsonObject, String key) {
        try {
            if (!jsonObject.has(key)) {
                return "N/A";
            }

            Object value = jsonObject.get(key);

            // Handle JSONObject with _text
            if (value instanceof JSONObject) {
                JSONObject obj = (JSONObject) value;
                if (obj.has("_text")) {
                    String textValue = obj.getString("_text");
                    return (textValue == null || textValue.isEmpty() || textValue.equals("{}")) ? "N/A" : textValue.trim();
                }
                return "N/A";
            }

            // Handle primitive types directly
            String stringValue = value.toString().trim();
            return (stringValue.isEmpty() || stringValue.equals("{}")) ? "N/A" : stringValue;

        } catch (Exception e) {
            return "N/A";
        }
    }

    // IMPROVED: Better handling of _text fields
    private String getText(Object field) {
        try {
            if (field == null) {
                return "N/A";
            }

            if (field instanceof JSONObject) {
                JSONObject obj = (JSONObject) field;

                // Handle _text field (XML-like structure)
                if (obj.has("_text")) {
                    Object textValue = obj.get("_text");
                    if (textValue != null && !textValue.toString().isEmpty() && !textValue.toString().equals("{}")) {
                        String text = textValue.toString().trim();
                        return text.isEmpty() ? "N/A" : text;
                    }
                }

                // If it's an empty JSON object, return N/A
                return "N/A";
            }

            // For empty JSONObject representations
            if (field.toString().equals("{}")) {
                return "N/A";
            }

            // For non-JSONObject, return string representation
            String text = field.toString().trim();
            return text.isEmpty() || text.equals("{}") ? "N/A" : text;

        } catch (Exception e) {
            return "N/A";
        }
    }

    // FIXED: Use exact pattern extraction that works (from JVDroid test)
    private Map<String, Object> cleanSERVER1Data(Object SERVER1DataObj) {
        System.out.println("=== DEBUG cleanSERVER1Data START ===");

        if (SERVER1DataObj == null) {
            System.out.println("❌ SERVER1DataObj is NULL");
            Map<String, Object> result = new HashMap<>();
            result.put("error", "No SERVER1 data available");
            return result;
        }

        String responseBody;
        if (SERVER1DataObj instanceof String) {
            responseBody = (String) SERVER1DataObj;
        } else if (SERVER1DataObj instanceof JSONObject) {
            responseBody = SERVER1DataObj.toString();
        } else {
            responseBody = SERVER1DataObj.toString();
        }

        System.out.println("📦 Raw response preview: " + responseBody.substring(0, Math.min(100, responseBody.length())) + "...");

        Map<String, Object> cleaned = new HashMap<>();

        try {
            // EXTRACT THE ACTUAL JSON PART - Remove the JSON-RPC prefix
            String jsonPart = extractActualJson(responseBody);
            System.out.println("🔍 Extracted JSON preview: " + jsonPart.substring(0, Math.min(100, jsonPart.length())) + "...");

            // Parse the clean JSON
            JSONObject SERVER1Data = new JSONObject(jsonPart);

            // Process mCustomerData
            if (SERVER1Data.has("mCustomerData")) {
                System.out.println("✅ Found mCustomerData");
                JSONObject mCustomerData = SERVER1Data.getJSONObject("mCustomerData");
                processCustomerDataDirect(mCustomerData, cleaned);
            }

            // Process mOrderData - USE EXACT PATTERN EXTRACTION (PROVEN TO WORK)
            if (SERVER1Data.has("mOrderData")) {
                System.out.println("✅ Found mOrderData - using exact pattern extraction");
                List<Map<String, String>> transactions = extractTransactionsWithExactPatterns(responseBody);
                if (!transactions.isEmpty()) {
                    cleaned.put("recent_transactions", transactions);
                    System.out.println("✅ Added " + transactions.size() + " transactions with tokens");
                }
            }

        } catch (Exception e) {
            System.out.println("❌ Exception in cleanSERVER1Data: " + e.getMessage());
            e.printStackTrace();
            cleaned.put("error", "Processing failed: " + e.getMessage());
        }

        System.out.println("🎯 Final cleaned data keys: " + cleaned.keySet());
        System.out.println("=== DEBUG cleanSERVER1Data END ===\n");

        return removeEmptyFields(cleaned);
    }

    // Extract the actual JSON from the JSON-RPC response
    private String extractActualJson(String responseBody) {
        try {
            // The format is: 0:["$@1",["nvIrYbiinJSZPzKb__tJP",null]]1:{ACTUAL_JSON}
            int jsonStart = responseBody.indexOf("1:{");
            if (jsonStart != -1) {
                return responseBody.substring(jsonStart + 2); // +2 to remove "1:"
            }
            return responseBody;
        } catch (Exception e) {
            return responseBody;
        }
    }

    // EXACT PATTERN EXTRACTION FOR TRANSACTIONS AND TOKENS (FROM WORKING JVDROID TEST)
    private List<Map<String, String>> extractTransactionsWithExactPatterns(String response) {
        List<Map<String, String>> transactions = new ArrayList<>();

        System.out.println("🔍 Looking for tokens with exact pattern...");

        // Exact pattern for tokens: "tokens":{"_text":"XXXX-XXXX-XXXX-XXXX-XXXX"}
        int index = 0;
        int count = 0;

        while (index != -1 && count < 3) {
            // Look for the exact tokens pattern
            index = response.indexOf("\"tokens\":{\"_text\":\"", index);
            if (index == -1) break;

            // Move to the start of the token value
            int valueStart = index + "\"tokens\":{\"_text\":\"".length();
            int valueEnd = response.indexOf("\"", valueStart);

            if (valueEnd != -1) {
                String token = response.substring(valueStart, valueEnd);
                System.out.println("🔑 FOUND TOKEN " + (count + 1) + ": " + token);

                // Extract other fields for this transaction
                Map<String, String> transaction = extractTransactionFields(response, index);
                transaction.put("Tokens", token);
                transactions.add(transaction);
                count++;
            }

            index = valueEnd + 1;
        }

        if (count == 0) {
            System.out.println("❌ No tokens found with exact pattern");
        }

        return transactions;
    }

    private Map<String, String> extractTransactionFields(String response, int tokenPosition) {
        Map<String, String> transaction = new HashMap<>();

        // Get a reasonable chunk around the token
        int searchStart = Math.max(0, tokenPosition - 1000);
        int searchEnd = Math.min(response.length(), tokenPosition + 200);
        String searchArea = response.substring(searchStart, searchEnd);

        // Extract each field with exact patterns
        transaction.put("Date", extractExactValue(searchArea, "date"));
        transaction.put("Order Number", extractExactValue(searchArea, "orderNo"));
        transaction.put("Amount", "৳" + extractExactValue(searchArea, "grossAmount"));
        transaction.put("Energy Cost", "৳" + extractExactValue(searchArea, "energyCost"));
        transaction.put("Operator", extractExactValue(searchArea, "operator"));
        transaction.put("Sequence", extractExactValue(searchArea, "sequence"));

        return transaction;
    }

    private String extractExactValue(String text, String fieldName) {
        try {
            // Exact pattern: "fieldName":{"_text":"value"}
            String pattern = "\"" + fieldName + "\":{\"_text\":\"";
            int start = text.indexOf(pattern);
            if (start != -1) {
                int valueStart = start + pattern.length();
                int valueEnd = text.indexOf("\"", valueStart);
                if (valueEnd != -1) {
                    return text.substring(valueStart, valueEnd);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return "N/A";
    }

    // Process customer data from JSON
    private void processCustomerDataDirect(JSONObject mCustomerData, Map<String, Object> cleaned) {
        try {
            if (mCustomerData.has("result")) {
                JSONObject result = mCustomerData.getJSONObject("result");

                Map<String, String> customerInfo = new HashMap<>();
                customerInfo.put("Consumer Number", extractDirectValue(result, "customerAccountNo"));
                customerInfo.put("Name", extractDirectValue(result, "customerName"));
                customerInfo.put("Address", extractDirectValue(result, "customerAddress"));
                customerInfo.put("Phone", extractDirectValue(result, "customerPhone"));
                customerInfo.put("Division", extractDirectValue(result, "division"));
                customerInfo.put("Sub Division", extractDirectValue(result, "sndDivision"));
                customerInfo.put("Tariff Category", extractDirectValue(result, "tariffCategory"));
                customerInfo.put("Connection Category", extractDirectValue(result, "connectionCategory"));
                customerInfo.put("Account Type", extractDirectValue(result, "accountType"));
                customerInfo.put("Meter Type", extractDirectValue(result, "meterType"));
                customerInfo.put("Sanctioned Load", extractDirectValue(result, "sanctionLoad"));
                customerInfo.put("Meter Number", extractDirectValue(result, "meterNumber"));
                customerInfo.put("Last Recharge Amount", extractDirectValue(result, "lastRechargeAmount"));
                customerInfo.put("Last Recharge Time", extractDirectValue(result, "lastRechargeTime"));
                customerInfo.put("Installation Date", extractDirectValue(result, "installationDate"));
                customerInfo.put("Lock Status", extractDirectValue(result, "lockStatus"));
                customerInfo.put("Total Recharge This Month", extractDirectValue(result, "totalRechargeThisMonth"));

                cleaned.put("customer_info", removeEmptyFields(customerInfo));
                System.out.println("✅ Added customer_info with " + customerInfo.size() + " fields");
            }
        } catch (Exception e) {
            System.out.println("❌ Error processing customer data: " + e.getMessage());
        }
    }

    // CORRECTED: cleanSERVER2Data with proper finalBalanceInfo, bill info, and unique value handling
    private Map<String, Object> cleanSERVER2Data(JSONObject SERVER2Data) {
        if (SERVER2Data == null || SERVER2Data.has("error")) {
            Map<String, Object> result = new HashMap<>();
            if (SERVER2Data != null && SERVER2Data.has("error")) {
                result.put("error", SERVER2Data.optString("error"));
            }
            return result;
        }

        Map<String, Object> cleaned = new HashMap<>();

        System.out.println("🔍 CLEAN SERVER2: Processing SERVER2 data");
        System.out.println("🔍 CLEAN SERVER2: Available keys: " + getJSONKeys(SERVER2Data));

        try {
            // 1. Extract Customer Information
            if (SERVER2Data.has("customerInfo") && SERVER2Data.getJSONArray("customerInfo").length() > 0) {
                JSONArray customerInfoArray = SERVER2Data.getJSONArray("customerInfo");
                if (customerInfoArray.length() > 0 && customerInfoArray.getJSONArray(0).length() > 0) {
                    JSONObject firstCustomer = customerInfoArray.getJSONArray(0).getJSONObject(0);
                    Map<String, String> customerInfo = new HashMap<>();

                    customerInfo.put("Customer Number", firstCustomer.optString("CUSTOMER_NUMBER"));
                    customerInfo.put("Customer Name", firstCustomer.optString("CUSTOMER_NAME"));
                    customerInfo.put("Address", firstCustomer.optString("ADDRESS"));
                    customerInfo.put("Tariff", firstCustomer.optString("TARIFF"));
                    customerInfo.put("Location Code", firstCustomer.optString("LOCATION_CODE"));
                    customerInfo.put("Bill Group", firstCustomer.optString("BILL_GROUP"));
                    customerInfo.put("Book", firstCustomer.optString("BOOK"));
                    customerInfo.put("Walking Sequence", firstCustomer.optString("WALKING_SEQUENCE"));
                    customerInfo.put("Meter Number", firstCustomer.optString("METER_NUM"));
                    customerInfo.put("Meter Status", getMeterStatus(firstCustomer.optString("METER_STATUS")));
                    customerInfo.put("Connection Date", formatDate(firstCustomer.optString("METER_CONNECT_DATE")));
                    customerInfo.put("Description", firstCustomer.optString("DESCR"));
                    customerInfo.put("Account_Number", firstCustomer.optString("CONS_EXTG_NUM"));
                    customerInfo.put("Usage Type", firstCustomer.optString("USAGE_TYPE"));
                    customerInfo.put("Start Bill Cycle", firstCustomer.optString("START_BILL_CYCLE"));

                    cleaned.put("customer_info", removeEmptyFields(customerInfo));
                    System.out.println("✅ CLEAN SERVER2: Customer info extracted");
                }
            } else {
                System.out.println("❌ CLEAN SERVER2: No customerInfo found");
            }

            // 2. Extract Balance Information - FIXED FINALBALANCEINFO PARSING
            Map<String, String> balanceInfo = new HashMap<>();

            // Method 1: Parse finalBalanceInfo with detailed breakdown
            if (SERVER2Data.has("finalBalanceInfo")) {
                String balanceString = SERVER2Data.optString("finalBalanceInfo");
                System.out.println("🔍 CLEAN SERVER2: finalBalanceInfo raw: " + balanceString);

                if (balanceString != null && !balanceString.equals("null") && !balanceString.isEmpty()) {
                    balanceInfo = parseFinalBalanceInfo(balanceString);
                    System.out.println("✅ CLEAN SERVER2: finalBalanceInfo parsed: " + balanceInfo);
                }
            }

            // Method 2: Fallback to balanceInfo object
            if (balanceInfo.isEmpty() && SERVER2Data.has("balanceInfo")) {
                try {
                    JSONObject balanceInfoObj = SERVER2Data.getJSONObject("balanceInfo");
                    if (balanceInfoObj.has("Result") && balanceInfoObj.getJSONArray("Result").length() > 0) {
                        JSONObject firstBalance = balanceInfoObj.getJSONArray("Result").getJSONObject(0);

                        double totalBalance = firstBalance.optDouble("BALANCE", 0);
                        double currentBill = firstBalance.optDouble("CURRENT_BILL", 0);
                        double arrearBill = firstBalance.optDouble("ARREAR_BILL", 0);
                        double paidAmount = firstBalance.optDouble("PAID_AMT", 0);

                        balanceInfo.put("Total Balance", String.format("%.2f", totalBalance));
                        balanceInfo.put("Current Bill", String.format("%.2f", currentBill));
                        balanceInfo.put("Arrear Amount", String.format("%.2f", arrearBill));
                        balanceInfo.put("Paid Amount", String.format("%.2f", paidAmount));

                        // Calculate components if available
                        if (totalBalance > 0 && currentBill + arrearBill > 0) {
                            double principal = currentBill + arrearBill - paidAmount;
                            double lps = Math.max(0, totalBalance - principal);
                            balanceInfo.put("PRN", String.format("%.2f", principal));
                            balanceInfo.put("LPS", String.format("%.2f", lps));
                        }

                        System.out.println("✅ CLEAN SERVER2: Balance info extracted from balanceInfo");
                    }
                } catch (Exception e) {
                    System.out.println("❌ CLEAN SERVER2: Error parsing balanceInfo: " + e.getMessage());
                }
            }

            if (!balanceInfo.isEmpty()) {
                cleaned.put("balance_info", balanceInfo);
                System.out.println("✅ CLEAN SERVER2: Balance info finalized: " + balanceInfo.keySet());
            } else {
                System.out.println("❌ CLEAN SERVER2: No balance information found");
            }

            // 3. EXTRACT BILL INFORMATION - FIXED!
            if (SERVER2Data.has("billInfo")) {
                try {
                    JSONArray billInfo = SERVER2Data.getJSONArray("billInfo");
                    System.out.println("✅ CLEAN SERVER2: billInfo found with " + billInfo.length() + " records");

                    if (billInfo.length() > 0) {
                        // Store the raw billInfo array
                        cleaned.put("bill_info_raw", billInfo);

                        // Create detailed bill summary
                        Map<String, Object> billSummary = new HashMap<>();
                        billSummary.put("total_bills", billInfo.length());

                        // Get latest bill details
                        JSONObject latestBill = billInfo.getJSONObject(0);
                        billSummary.put("latest_bill_date", formatBillMonth(latestBill.optString("BILL_MONTH")));
                        billSummary.put("latest_bill_number", latestBill.optString("BILL_NO"));
                        billSummary.put("latest_consumption", latestBill.optDouble("CONS_KWH_SR", 0));
                        billSummary.put("latest_total_amount", latestBill.optDouble("TOTAL_BILL", 0));
                        billSummary.put("latest_balance", latestBill.optDouble("BALANCE", 0));
                        billSummary.put("current_reading_sr", latestBill.optDouble("CONS_KWH_SR", 0));

                        // Extract all bills for detailed analysis
                        List<Map<String, Object>> allBills = new ArrayList<>();
                        double totalConsumption = 0;
                        double totalAmount = 0;

                        for (int i = 0; i < billInfo.length(); i++) {
                            JSONObject bill = billInfo.getJSONObject(i);
                            Map<String, Object> billDetail = new HashMap<>();

                            billDetail.put("bill_month", formatBillMonth(bill.optString("BILL_MONTH")));
                            billDetail.put("bill_number", bill.optString("BILL_NO"));
                            billDetail.put("consumption", bill.optDouble("CONS_KWH_SR", 0));
                            billDetail.put("total_amount", bill.optDouble("TOTAL_BILL", 0));
                            billDetail.put("balance", bill.optDouble("BALANCE", 0));
                            billDetail.put("due_date", formatDate(bill.optString("INVOICE_DUE_DATE")));

                            allBills.add(billDetail);

                            if (i < 3) { // Recent 3 months
                                totalConsumption += bill.optDouble("CONS_KWH_SR", 0);
                                totalAmount += bill.optDouble("TOTAL_BILL", 0);
                            }
                        }

                        billSummary.put("recent_consumption", totalConsumption);
                        billSummary.put("recent_amount", totalAmount);
                        billSummary.put("all_bills", allBills);
                        billSummary.put("bill_months_available", billInfo.length());

                        cleaned.put("bill_summary", billSummary);
                        System.out.println("✅ CLEAN SERVER2: Bill information extracted - " + allBills.size() + " bills");
                    }
                } catch (Exception e) {
                    System.out.println("❌ CLEAN SERVER2: Error processing billInfo: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("❌ CLEAN SERVER2: No billInfo found in SERVER2 data");
            }

            // 4. Store raw data for unique value analysis
            cleaned.put("SERVER2_raw_data", SERVER2Data);

        } catch (Exception e) {
            System.out.println("❌ CLEAN SERVER2 ERROR: " + e.getMessage());
            e.printStackTrace();
        }

        Map<String, Object> finalResult = removeEmptyFields(cleaned);
        System.out.println("🎯 CLEAN SERVER2 FINAL: " + finalResult.keySet());
        return finalResult;
    }

    // NEW: Improved finalBalanceInfo parser
    private Map<String, String> parseFinalBalanceInfo(String balanceString) {
        Map<String, String> balanceInfo = new HashMap<>();

        if (balanceString == null || balanceString.isEmpty() || balanceString.equals("null")) {
            return balanceInfo;
        }

        try {
            System.out.println("🔍 PARSING finalBalanceInfo: " + balanceString);

            // Handle different formats

            // Format 1: Simple total amount "1234.56"
            if (!balanceString.contains(",") && !balanceString.contains(":")) {
                balanceInfo.put("Total Balance", balanceString.trim());
                balanceInfo.put("Arrear Amount", balanceString.trim());
                System.out.println("✅ Parsed as simple total: " + balanceString);
                return balanceInfo;
            }

            // Format 2: Detailed breakdown "1234.56, PRN:1000.00, LPS:234.56"
            String[] parts = balanceString.split(",");

            // First part is always total balance
            String totalBalance = parts[0].trim();
            balanceInfo.put("Total Balance", totalBalance);
            balanceInfo.put("Arrear Amount", totalBalance);

            // Parse detailed components
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.startsWith("PRN:")) {
                    balanceInfo.put("PRN", part.substring(4).trim());
                } else if (part.startsWith("LPS:")) {
                    balanceInfo.put("LPS", part.substring(4).trim());
                } else if (part.startsWith("VAT:")) {
                    balanceInfo.put("VAT", part.substring(4).trim());
                } else if (part.startsWith("Current:")) {
                    balanceInfo.put("Current Bill", part.substring(8).trim());
                } else if (part.startsWith("Arrear:")) {
                    balanceInfo.put("Arrear Amount", part.substring(7).trim());
                }
            }

            System.out.println("✅ Parsed detailed balance: " + balanceInfo);

        } catch (Exception e) {
            System.out.println("❌ Error parsing finalBalanceInfo: " + e.getMessage());
            // Fallback: use the entire string as total balance
            balanceInfo.put("Total Balance", balanceString);
            balanceInfo.put("Arrear Amount", balanceString);
        }

        return balanceInfo;
    }

    private Map<String, Object> cleanSERVER3Data(JSONObject SERVER3Data) {
        if (SERVER3Data == null) {
            return new HashMap<>();
        }

        Map<String, Object> cleaned = new HashMap<>();
        Map<String, String> customerInfo = new HashMap<>();

        customerInfo.put("Customer Number", SERVER3Data.optString("customerNumber"));
        customerInfo.put("Customer Name", SERVER3Data.optString("customerName"));
        customerInfo.put("Customer Address", SERVER3Data.optString("customerAddr"));
        customerInfo.put("Father Name", SERVER3Data.optString("fatherName"));
        customerInfo.put("Location Code", SERVER3Data.optString("locationCode"));
        customerInfo.put("Area Code", SERVER3Data.optString("areaCode"));
        customerInfo.put("Book Number", SERVER3Data.optString("bookNumber"));
        customerInfo.put("Bill Group", SERVER3Data.optString("billGroup"));
        customerInfo.put("Meter Number", SERVER3Data.optString("meterNum"));
        customerInfo.put("Meter Condition", SERVER3Data.optString("meterConditionDesc"));
        customerInfo.put("Sanctioned Load", SERVER3Data.optString("sanctionedLoad"));
        customerInfo.put("Tariff Description", SERVER3Data.optString("tariffDesc"));
        customerInfo.put("Walk Order", SERVER3Data.optString("walkOrder"));
        customerInfo.put("Arrear Amount", SERVER3Data.optString("arrearAmount"));

        // Map SERVER3's lastBillReadingSr to match SERVER2's CONS_KWH_SR
        String lastReadingSr = SERVER3Data.optString("lastBillReadingSr");
        if (lastReadingSr != null && !lastReadingSr.equals("null") && !lastReadingSr.isEmpty()) {
            customerInfo.put("Last Bill Reading SR", lastReadingSr);
            // Also store for bill info merging
            cleaned.put("current_reading_sr", Double.parseDouble(lastReadingSr));
        }

        customerInfo.put("Last Bill Reading OF PK", SERVER3Data.optString("lastBillReadingOfPk"));
        customerInfo.put("Last Bill Reading PK", SERVER3Data.optString("lastBillReadingPk"));

        cleaned.put("customer_info", removeEmptyFields(customerInfo));

        if (SERVER3Data.has("arrearAmount")) {
            String arrearAmount = SERVER3Data.optString("arrearAmount");
            Map<String, String> balanceInfo = new HashMap<>();
            balanceInfo.put("Total Balance", arrearAmount);
            balanceInfo.put("Arrear Amount", arrearAmount);
            cleaned.put("balance_info", balanceInfo);
        }

        return removeEmptyFields(cleaned);
    }

    // CORRECTED: mergeSERVERData with proper unique value handling
    private Map<String, Object> mergeSERVERData(Map<String, Object> result) {
        JSONObject SERVER3Data = (JSONObject) result.get("SERVER3_data");
        Object SERVER2DataObj = result.get("SERVER2_data");
        JSONObject SERVER2Data = null;

        if (SERVER2DataObj instanceof JSONObject) {
            SERVER2Data = (JSONObject) SERVER2DataObj;
        }

        System.out.println("🔄 MERGE DEBUG: SERVER3Data = " + (SERVER3Data != null));
        System.out.println("🔄 MERGE DEBUG: SERVER2Data = " + (SERVER2Data != null));

        if (SERVER3Data == null && (SERVER2Data == null || SERVER2Data.has("error"))) {
            System.out.println("❌ MERGE: No valid data to merge");
            return null;
        }

        Map<String, Object> merged = new HashMap<>();
        Map<String, String> uniqueSERVER2Fields = new HashMap<>();
        Map<String, String> uniqueSERVER3Fields = new HashMap<>();
        Map<String, String> overlappingFields = new HashMap<>();

        try {
            Map<String, Object> cleanedSERVER3 = new HashMap<>();

            // PROCESS SERVER2 DATA FIRST (since it has bill info)
            if (SERVER2Data != null && !SERVER2Data.has("error")) {
                Map<String, Object> cleanedSERVER2 = cleanSERVER2Data(SERVER2Data);

                // PRESERVE ALL BILL INFORMATION FROM SERVER2 FIRST (MOST IMPORTANT)
                if (cleanedSERVER2.containsKey("bill_info_raw")) {
                    merged.put("bill_info_raw", cleanedSERVER2.get("bill_info_raw"));
                    System.out.println("✅ MERGE: Preserved bill_info_raw from SERVER2");
                }
                if (cleanedSERVER2.containsKey("bill_summary")) {
                    merged.put("bill_summary", cleanedSERVER2.get("bill_summary"));
                    System.out.println("✅ MERGE: Preserved bill_summary from SERVER2");
                }

                // Process customer info from SERVER2 as base
                if (cleanedSERVER2.containsKey("customer_info")) {
                    Map<String, String> customerInfo = new HashMap<>((Map<String, String>) cleanedSERVER2.get("customer_info"));
                    merged.put("customer_info", customerInfo);
                    uniqueSERVER2Fields.putAll(customerInfo);

                    // IMPORTANT: Add current reading from bill summary to customer info
                    if (cleanedSERVER2.containsKey("bill_summary")) {
                        Map<String, Object> billSummary = (Map<String, Object>) cleanedSERVER2.get("bill_summary");
                        if (billSummary.containsKey("current_reading_sr")) {
                            Double currentReading = (Double) billSummary.get("current_reading_sr");
                            String readingKey = "Current Reading SR";
                            if (!customerInfo.containsKey(readingKey)) {
                                customerInfo.put(readingKey, String.valueOf(currentReading));
                                uniqueSERVER2Fields.put(readingKey, String.valueOf(currentReading));
                                System.out.println("✅ MERGE: Added Current Reading SR from SERVER2 bill summary");
                            }
                        }
                    }
                }

                // Balance info: SERVER2 preferred (has breakdown)
                if (cleanedSERVER2.containsKey("balance_info")) {
                    Map<String, String> SERVER2Balance = (Map<String, String>) cleanedSERVER2.get("balance_info");
                    if (SERVER2Balance != null && !SERVER2Balance.isEmpty()) {
                        merged.put("balance_info", SERVER2Balance);
                        System.out.println("✅ MERGE: Using SERVER2 balance info with breakdown");
                    }
                }

                if (cleanedSERVER2.containsKey("SERVER2_raw_data")) {
                    merged.put("SERVER2_raw_data", cleanedSERVER2.get("SERVER2_raw_data"));
                }
            }

            // THEN supplement with SERVER 3 data
            if (SERVER3Data != null) {
                cleanedSERVER3 = cleanSERVER3Data(SERVER3Data);
                System.out.println("🔄 MERGE: SERVER3 cleaned keys: " + cleanedSERVER3.keySet());

                // Process customer info for unique values from SERVER3
                if (cleanedSERVER3.containsKey("customer_info")) {
                    if (!merged.containsKey("customer_info")) {
                        merged.put("customer_info", new HashMap<String, String>());
                    }

                    Map<String, String> customerInfo = (Map<String, String>) merged.get("customer_info");
                    Map<String, String> SERVER3Customer = (Map<String, String>) cleanedSERVER3.get("customer_info");

                    // Identify unique and overlapping fields
                    for (Map.Entry<String, String> entry : SERVER3Customer.entrySet()) {
                        if (entry.getValue() != null && !entry.getValue().equals("N/A") && !entry.getValue().isEmpty()) {
                            if (customerInfo.containsKey(entry.getKey())) {
                                // Overlapping field - SERVER2 value takes precedence (since we processed it first)
                                overlappingFields.put(entry.getKey(), customerInfo.get(entry.getKey()));
                            } else {
                                // Unique SERVER3 field
                                customerInfo.put(entry.getKey(), entry.getValue());
                                uniqueSERVER3Fields.put(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                }

                // Use SERVER3 balance info only if SERVER2 doesn't have it
                if (cleanedSERVER3.containsKey("balance_info") && !merged.containsKey("balance_info")) {
                    Map<String, String> balanceInfo = new HashMap<>((Map<String, String>) cleanedSERVER3.get("balance_info"));
                    merged.put("balance_info", balanceInfo);
                    System.out.println("✅ MERGE: Using SERVER3 balance info as fallback");
                }
            }
            // Store unique value analysis
            Map<String, Object> uniqueAnalysis = new HashMap<>();
            uniqueAnalysis.put("server2_unique", uniqueSERVER2Fields);
            uniqueAnalysis.put("server3_unique", uniqueSERVER3Fields);
            uniqueAnalysis.put("overlapping", overlappingFields);
            uniqueAnalysis.put("total_unique_server2", uniqueSERVER2Fields.size());
            uniqueAnalysis.put("total_unique_server3", uniqueSERVER3Fields.size());
            uniqueAnalysis.put("total_overlapping", overlappingFields.size());

            merged.put("unique_analysis", uniqueAnalysis);

            System.out.println("✅ MERGE COMPLETE: Final keys = " + merged.keySet());
            System.out.println("🔍 UNIQUE ANALYSIS: SERVER2=" + uniqueSERVER2Fields.size() +
                    ", SERVER3=" + uniqueSERVER3Fields.size() +
                    ", Overlapping=" + overlappingFields.size());

            return removeEmptyFields(merged);

        } catch (Exception e) {
            System.out.println("❌ MERGE ERROR: " + e.getMessage());
            e.printStackTrace();
            return merged;
        }
    }

    // formatMergedDisplay without the table
    private String formatMergedDisplayWithoutTable(Map<String, Object> mergedData) {
        if (mergedData == null || mergedData.isEmpty()) {
            return "No data available";
        }

        StringBuilder output = new StringBuilder();

        // Customer Info Section - Same as before
        if (mergedData.containsKey("customer_info")) {
            output.append("👤 CUSTOMER INFORMATION\n");
            output.append("══════════════════════════════════════════════════\n");

            Map<String, String> customerInfo = (Map<String, String>) mergedData.get("customer_info");

            // Group fields logically
            String[] personalInfo = {
                    "Customer Name", "Father Name", "Customer Address"
            };

            String[] meterInfo = {
                    "Meter Number", "Meter Condition", "Meter Status", "Connection Date"
            };

            String[] billingInfo = {
                    "Customer Number", "Location Code", "Area Code", "Bill Group", "Book Number",
                    "Tariff Description", "Sanctioned Load", "Walk Order", "Account_Number"
            };

            String[] technicalInfo = {
                    "Usage Type", "Description", "Start Bill Cycle"
            };

            String[] readingInfo = {
                    "Arrear Amount", "Current Reading SR", "Last Bill Reading SR",
                    "Last Bill Reading OF PK", "Last Bill Reading PK"
            };

            // Personal Information
            output.append("\n📋 PERSONAL INFORMATION\n");
            output.append("──────────────────────────────────────────────────\n");
            for (String key : personalInfo) {
                if (customerInfo.containsKey(key) && isValidValue(customerInfo.get(key))) {
                    output.append("• ").append(key).append(": ").append(customerInfo.get(key)).append("\n");
                }
            }

            // Meter Information
            output.append("\n🔧 METER INFORMATION\n");
            output.append("──────────────────────────────────────────────────\n");
            for (String key : meterInfo) {
                if (customerInfo.containsKey(key) && isValidValue(customerInfo.get(key))) {
                    output.append("• ").append(key).append(": ").append(customerInfo.get(key)).append("\n");
                }
            }

            // Billing Information
            output.append("\n💳 BILLING INFORMATION\n");
            output.append("──────────────────────────────────────────────────\n");
            for (String key : billingInfo) {
                if (customerInfo.containsKey(key) && isValidValue(customerInfo.get(key))) {
                    output.append("• ").append(key).append(": ").append(customerInfo.get(key)).append("\n");
                }
            }

            // Technical Information
            output.append("\n⚙️ TECHNICAL INFORMATION\n");
            output.append("──────────────────────────────────────────────────\n");
            for (String key : technicalInfo) {
                if (customerInfo.containsKey(key) && isValidValue(customerInfo.get(key))) {
                    output.append("• ").append(key).append(": ").append(customerInfo.get(key)).append("\n");
                }
            }

            // Reading Information
            output.append("\n📊 METER READINGS\n");
            output.append("──────────────────────────────────────────────────\n");
            for (String key : readingInfo) {
                if (customerInfo.containsKey(key) && isValidValue(customerInfo.get(key))) {
                    output.append("• ").append(key).append(": ").append(customerInfo.get(key)).append("\n");
                }
            }
        }

        // Combined Bill Information Section - BUT NO TABLE
        if (mergedData.containsKey("bill_summary") || mergedData.containsKey("bill_info_raw")) {
            output.append("\n📊 BILL SUMMARY\n");
            output.append("══════════════════════════════════════════════════\n");

            // NEW BILL SUMMARY FORMAT (keep this)
            if (mergedData.containsKey("bill_info_raw")) {
                try {
                    JSONArray billInfo = (JSONArray) mergedData.get("bill_info_raw");

                    if (billInfo.length() > 0) {
                        // Calculate first and last bill periods, totals
                        JSONObject firstBill = billInfo.getJSONObject(billInfo.length() - 1);
                        JSONObject lastBill = billInfo.getJSONObject(0);

                        String firstBillPeriod = formatBillMonth(firstBill.optString("BILL_MONTH"));
                        String lastBillPeriod = formatBillMonth(lastBill.optString("BILL_MONTH"));

                        double totalAmount = 0.0;
                        double totalPaid = 0.0;
                        double arrears = 0.0;

                        for (int i = 0; i < billInfo.length(); i++) {
                            JSONObject bill = billInfo.getJSONObject(i);
                            totalAmount += bill.optDouble("CURRENT_BILL", 0);  // Changed from CURRENT_BILL to TOTAL_BILL
                            totalPaid += bill.optDouble("PAID_AMT", 0);
                        }

// Calculate arrears as Total Amount minus Total Paid
                        arrears = totalAmount - totalPaid;

                        // Get total bills count
                        int totalBills = billInfo.length();
                        if (mergedData.containsKey("bill_summary")) {
                            Map<String, Object> billSummary = (Map<String, Object>) mergedData.get("bill_summary");
                            totalBills = (int) billSummary.getOrDefault("total_bills", billInfo.length());
                        }

                        // Display new bill summary format
                        output.append(String.format("%-25s: %s\n", "First Bill Period", firstBillPeriod));
                        output.append(String.format("%-25s: %s\n", "Last Bill Period", lastBillPeriod));
                        output.append(String.format("%-25s: %s\n", "Total Bills", totalBills));
                        output.append(String.format("%-25s: ৳%.0f\n", "Total Amount", totalAmount));
                        output.append(String.format("%-25s: ৳%.0f\n", "Total Paid", totalPaid));
                        output.append(String.format("%-25s: ৳%.0f\n", "Arrears", arrears));
                        output.append("\n");
                    }
                } catch (Exception e) {
                    output.append("❌ Error calculating bill summary: ").append(e.getMessage()).append("\n");
                }
            }

            // Final Balance Details - SHOW ZEROS (keep this)
            if (mergedData.containsKey("balance_info")) {
                Map<String, String> balanceInfo = (Map<String, String>) mergedData.get("balance_info");

                output.append("💰 FINAL BALANCE DETAILS\n");
                output.append("──────────────────────────────────────────────────\n");

                String[] balanceOrder = {"Total Balance", "Arrear Amount", "PRN", "LPS", "VAT"};

                for (String key : balanceOrder) {
                    if (balanceInfo.containsKey(key)) {
                        String value = balanceInfo.get(key);
                        if (value == null || value.isEmpty() || value.equals("0") || value.equals("0.00")) {
                            value = "0";
                        }
                        output.append("• ").append(key).append(": ").append(value).append("\n");
                    }
                }
            }
        }

        return output.toString();
    }


    // Create cell background with borders
    private Drawable createCellBackground(boolean isHeader) {
        GradientDrawable border = new GradientDrawable();
        border.setColor(isHeader ? Color.LTGRAY : Color.WHITE);
        border.setStroke(1, Color.GRAY);
        return border;
    }

    // Helper method to create bill summary from raw bill data
    private Map<String, Object> createBillSummary(JSONArray billInfo) {
        Map<String, Object> billSummary = new HashMap<>();

        try {
            if (billInfo.length() > 0) {
                billSummary.put("total_bills", billInfo.length());

                // Get latest bill details
                JSONObject latestBill = billInfo.getJSONObject(0);
                billSummary.put("latest_bill_date", formatBillMonth(latestBill.optString("BILL_MONTH")));
                billSummary.put("latest_bill_number", latestBill.optString("BILL_NO"));
                billSummary.put("latest_consumption", latestBill.optDouble("CONS_KWH_SR", 0));
                billSummary.put("latest_total_amount", latestBill.optDouble("TOTAL_BILL", 0));
                billSummary.put("latest_balance", latestBill.optDouble("BALANCE", 0));
                billSummary.put("current_reading_sr", latestBill.optDouble("CONS_KWH_SR", 0));

                // Calculate 3-month consumption
                double recentConsumption = 0;
                for (int i = 0; i < Math.min(billInfo.length(), 3); i++) {
                    recentConsumption += billInfo.getJSONObject(i).optDouble("CONS_KWH_SR", 0);
                }
                billSummary.put("recent_consumption", recentConsumption);
                billSummary.put("bill_months_available", billInfo.length());
            }
        } catch (Exception e) {
            System.out.println("❌ Error creating bill summary: " + e.getMessage());
        }

        return billSummary;
    }

    // Helper method to truncate text for table display
    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    // Helper method to pad text to the right
    private String padRight(String text, int length) {
        if (text == null) text = "";
        if (text.length() > length) {
            return text.substring(0, length - 3) + "...";
        }
        return String.format("%-" + length + "s", text);
    }

    // Helper method to center text
    private String centerText(String text, int length) {
        if (text == null) text = "";
        if (text.length() > length) {
            return text.substring(0, length - 3) + "...";
        }

        int padding = length - text.length();
        int leftPadding = padding / 2;
        int rightPadding = padding - leftPadding;

        return repeatString(" ", leftPadding) + text + repeatString(" ", rightPadding);
    }

    // Helper method to check if value is valid for display
    private boolean isValidValue(String value) {
        if (value == null) {
            return false;
        }

        String trimmedValue = value.trim();

        return !trimmedValue.isEmpty() &&
                !trimmedValue.equals("N/A") &&
                !trimmedValue.equals("null") &&
                !trimmedValue.equals("{}") &&
                !trimmedValue.equals("undefined");
    }

    private String formatPrepaidDisplay(Map<String, Object> cleanedData) {
        if (cleanedData == null || cleanedData.isEmpty()) {
            return "No prepaid data available";
        }

        StringBuilder output = new StringBuilder();

        // Customer Info Section
        if (cleanedData.containsKey("customer_info")) {
            output.append("👤 CUSTOMER INFORMATION\n");
            output.append(repeatString("=", 20)).append("\n");
            Map<String, String> customerInfo = (Map<String, String>) cleanedData.get("customer_info");
            for (Map.Entry<String, String> entry : customerInfo.entrySet()) {
                if (!entry.getValue().equals("N/A")) {
                    output.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            }
            output.append("\n");
        }

        // TOKENS SECTION - Most Important!
        if (cleanedData.containsKey("recent_transactions")) {
            output.append("🔑 LAST 3 RECHARGE TOKENS\n");
            output.append(repeatString("=", 20)).append("\n\n");

            List<Map<String, String>> transactions = (List<Map<String, String>>) cleanedData.get("recent_transactions");
            for (int i = 0; i < transactions.size(); i++) {
                Map<String, String> transaction = transactions.get(i);
                output.append("Order ").append(i + 1).append(":\n");
                output.append("  📅 Date: ").append(transaction.get("Date")).append("\n");
                output.append("  🧾 Order: ").append(transaction.get("Order Number")).append("\n");
                output.append("  👤 Operator: ").append(transaction.get("Operator")).append("\n");
                output.append("  🔢 Sequence: ").append(transaction.get("Sequence")).append("\n");
                output.append("  💰 Amount: ").append(transaction.get("Amount")).append("\n");
                output.append("  ⚡ Energy: ").append(transaction.get("Energy Cost")).append("\n");
                output.append("  🔑 TOKENS: ").append(transaction.get("Tokens")).append("\n\n");
            }
        }

        return output.toString();
    }

    private String getMeterStatus(String statusCode) {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("1", "Active");
        statusMap.put("2", "Inactive");
        statusMap.put("3", "Disconnected");
        return statusMap.getOrDefault(statusCode, "Unknown (" + statusCode + ")");
    }

    private String formatDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return "N/A";
        }
        try {
            if (dateString.contains("T")) {
                return dateString.split("T")[0];
            }
            return dateString;
        } catch (Exception e) {
            return dateString;
        }
    }

    private Map<String, String> extractBalanceInfo(String balanceString) {
        Map<String, String> balanceInfo = new HashMap<>();

        if (balanceString == null || balanceString.isEmpty()) {
            return balanceInfo;
        }

        try {
            // Handle case where we only get the total amount (your actual API response)
            if (!balanceString.contains(",") && !balanceString.contains(":")) {
                // Only total amount provided - this is your current API response
                balanceInfo.put("Total Balance", balanceString.trim());
                balanceInfo.put("Arrear Amount", balanceString.trim());
                return balanceInfo;
            }

            // Handle full breakdown format (your expected format)
            String[] parts = balanceString.split(",");

            // First part is always total balance
            String mainBalance = parts[0].trim();
            balanceInfo.put("Total Balance", mainBalance);
            balanceInfo.put("Arrear Amount", mainBalance);

            // Parse breakdown components
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.startsWith("PRN:")) {
                    balanceInfo.put("PRN", part.substring(4).trim());
                } else if (part.startsWith("LPS:")) {
                    balanceInfo.put("LPS", part.substring(4).trim());
                } else if (part.startsWith("VAT:")) {
                    balanceInfo.put("VAT", part.substring(4).trim());
                }
            }

        } catch (Exception e) {
            System.out.println("⚠️ Balance parsing warning: " + e.getMessage());
            balanceInfo.put("Total Balance", balanceString);
        }

        return balanceInfo;
    }

    @SuppressWarnings("unchecked")
    private <T> T removeEmptyFields(T data) {
        if (data instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) data;
            Map<Object, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                Object value = entry.getValue();

                // PRESERVE bill-related data even if it seems empty
                if (key.toString().contains("bill") || key.toString().contains("balance")) {
                    result.put(key, value);
                    continue;
                }

                Object cleaned = removeEmptyFields(value);
                if (cleaned != null &&
                        !cleaned.equals("") &&
                        !cleaned.equals("N/A") &&
                        !(cleaned instanceof Map && ((Map<?, ?>) cleaned).isEmpty()) &&
                        !(cleaned instanceof List && ((List<?>) cleaned).isEmpty())) {
                    result.put(key, cleaned);
                }
            }
            return (T) result;
        } else if (data instanceof List) {
            List<?> list = (List<?>) data;
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                Object cleaned = removeEmptyFields(item);
                if (cleaned != null &&
                        !cleaned.equals("") &&
                        !cleaned.equals("N/A") &&
                        !(cleaned instanceof Map && ((Map<?, ?>) cleaned).isEmpty()) &&
                        !(cleaned instanceof List && ((List<?>) cleaned).isEmpty())) {
                    result.add(cleaned);
                }
            }
            return (T) result;
        } else {
            return data;
        }
    }

    // IMPROVED: formatBillDisplay with better empty data handling
    private String formatBillDisplay(JSONObject SERVER2Data) {
        if (SERVER2Data == null) {
            return "No bill data available";
        }

        // Check for error first
        if (SERVER2Data.has("error")) {
            return "Error in bill data: " + SERVER2Data.optString("error");
        }

        StringBuilder output = new StringBuilder();

        try {
            // DEBUG: Check what's in SERVER2Data
            System.out.println("📊 BILL DEBUG: SERVER2Data keys: " + getJSONKeys(SERVER2Data));

            if (SERVER2Data.has("billInfo")) {
                JSONArray billInfo = SERVER2Data.getJSONArray("billInfo");
                System.out.println("📊 BILL DEBUG: billInfo length: " + billInfo.length());

                if (billInfo.length() > 0) {
                    int billCount = Math.min(billInfo.length(), 3);
                    output.append("📊 BILL HISTORY (LAST ").append(billCount).append(" MONTHS)\n\n");

                    // Define fields to display
                    String[][] fields = {
                            {"Bill Month", "BILL_MONTH"},
                            {"Bill No", "BILL_NO"},
                            {"Consumption", "CONS_KWH_SR"},
                            {"Current Bill", "CURRENT_BILL"},
                            {"Arrear Bill", "ARREAR_BILL"},
                            {"Total Bill", "TOTAL_BILL"},
                            {"Due Date", "INVOICE_DUE_DATE"},
                            {"Paid Amount", "PAID_AMT"},
                            {"Balance", "BALANCE"}
                    };

                    // Create header
                    output.append(String.format("%-20s", "FIELD"));
                    for (int i = 0; i < billCount; i++) {
                        output.append(String.format("%-20s", "BILL " + (i + 1)));
                    }
                    output.append("\n");
                    output.append(repeatString("─", 20 * (billCount + 1))).append("\n");

                    // Process each field
                    for (String[] field : fields) {
                        String fieldName = field[0];
                        String fieldKey = field[1];

                        output.append(String.format("%-20s", fieldName));

                        for (int i = 0; i < billCount; i++) {
                            JSONObject bill = billInfo.getJSONObject(i);
                            String value = getFormattedBillValue(bill, fieldKey);
                            output.append(String.format("%-20s", value));
                        }
                        output.append("\n");
                    }

                } else {
                    output.append("❌ No bill history records found\n");
                }
            } else {
                output.append("❌ No bill information available in response\n");
            }

            // Add final balance info if available
            if (SERVER2Data.has("finalBalanceInfo")) {
                String balanceString = SERVER2Data.optString("finalBalanceInfo");
                if (!balanceString.isEmpty() && !balanceString.equals("null") && !balanceString.equals("0")) {
                    output.append("\n💰 FINAL BALANCE DETAILS\n");
                    output.append(repeatString("─", 25)).append("\n");

                    Map<String, String> balanceInfo = extractBalanceInfo(balanceString);
                    boolean hasBalanceData = false;
                    for (Map.Entry<String, String> entry : balanceInfo.entrySet()) {
                        if (!entry.getValue().equals("0") && !entry.getValue().isEmpty()) {
                            output.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                            hasBalanceData = true;
                        }
                    }
                    if (!hasBalanceData) {
                        output.append("• No outstanding balance\n");
                    }
                }
            }

        } catch (Exception e) {
            output.append("❌ Error displaying bill data: ").append(e.getMessage()).append("\n");
            e.printStackTrace();
        }

        return output.toString();
    }

    // Helper method to format bill values
    private String getFormattedBillValue(JSONObject bill, String fieldKey) {
        try {
            if (!bill.has(fieldKey) || bill.isNull(fieldKey)) {
                return "N/A";
            }

            switch (fieldKey) {
                case "BILL_MONTH":
                    String dateStr = bill.getString(fieldKey);
                    return formatBillMonth(dateStr);

                case "INVOICE_DUE_DATE":
                case "RECEIPT_DATE":
                    return formatDate(bill.getString(fieldKey));

                case "CURRENT_BILL":
                case "ARREAR_BILL":
                case "TOTAL_BILL":
                case "PAID_AMT":
                case "BALANCE":
                    double amount = bill.getDouble(fieldKey);
                    return "৳" + String.format("%.0f", amount);

                case "CONS_KWH_SR":
                case "OPN_KWH_SR_RDNG":
                    double consumption = bill.getDouble(fieldKey);
                    return String.format("%.0f", consumption);

                default:
                    return bill.getString(fieldKey);
            }
        } catch (Exception e) {
            return "N/A";
        }
    }

    // Helper to format bill month
    private String formatBillMonth(String dateStr) {
        if (dateStr == null || dateStr.isEmpty() || dateStr.equals("null")) {
            return "N/A";
        }

        try {
            // Extract YYYY-MM from date string
            String[] parts = dateStr.substring(0, 10).split("-");
            if (parts.length >= 2) {
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);

                String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

                if (month >= 1 && month <= 12) {
                    return monthNames[month - 1] + "-" + year;
                }
            }
            return dateStr.substring(0, 7); // Fallback to YYYY-MM
        } catch (Exception e) {
            return dateStr.length() >= 7 ? dateStr.substring(0, 7) : dateStr;
        }
    }

    // Helper to debug JSON keys
    private String getJSONKeys(JSONObject json) {
        try {
            Iterator<String> keys = json.keys();
            List<String> keyList = new ArrayList<>();
            while (keys.hasNext()) {
                keyList.add(keys.next());
            }
            return String.join(", ", keyList);
        } catch (Exception e) {
            return "Error getting keys";
        }
    }


    // NEW: Format bill summary for display
    private String formatBillSummary(Map<String, Object> billSummary) {
        if (billSummary == null || billSummary.isEmpty()) {
            return "No bill summary available";
        }

        StringBuilder output = new StringBuilder();

        try {
            output.append("📅 Total Bills: ").append(billSummary.getOrDefault("total_bills", "N/A")).append("\n");

            if (billSummary.containsKey("latest_bill_date")) {
                output.append("📋 Latest Bill Date: ").append(billSummary.get("latest_bill_date")).append("\n");
            }

            if (billSummary.containsKey("latest_bill_number")) {
                output.append("🧾 Latest Bill No: ").append(billSummary.get("latest_bill_number")).append("\n");
            }

            if (billSummary.containsKey("latest_consumption")) {
                Object consumption = billSummary.get("latest_consumption");
                if (consumption instanceof Double) {
                    output.append("⚡ Latest Consumption: ").append(String.format("%.0f", (Double) consumption)).append(" kWh\n");
                } else {
                    output.append("⚡ Latest Consumption: ").append(consumption).append(" kWh\n");
                }
            }

            if (billSummary.containsKey("latest_total_amount")) {
                Object amount = billSummary.get("latest_total_amount");
                if (amount instanceof Double) {
                    output.append("💵 Latest Amount: ৳").append(String.format("%.0f", (Double) amount)).append("\n");
                } else {
                    output.append("💵 Latest Amount: ৳").append(amount).append("\n");
                }
            }

            if (billSummary.containsKey("recent_consumption")) {
                Object recentConsumption = billSummary.get("recent_consumption");
                if (recentConsumption instanceof Double) {
                    output.append("📈 Recent 3 Months Consumption: ").append(String.format("%.0f", (Double) recentConsumption)).append(" kWh\n");
                } else {
                    output.append("📈 Recent 3 Months Consumption: ").append(recentConsumption).append(" kWh\n");
                }
            }

            if (billSummary.containsKey("bill_months_available")) {
                output.append("📆 Bill History Available: ").append(billSummary.get("bill_months_available")).append(" months\n");
            }

        } catch (Exception e) {
            output.append("❌ Error formatting bill summary: ").append(e.getMessage()).append("\n");
        }

        return output.toString();
    }

    // IMPROVED: Helper to check if bill display is valid
    private boolean isValidBillDisplay(String billDisplay) {
        return billDisplay != null &&
                !billDisplay.trim().isEmpty() &&
                !billDisplay.contains("No bill data available") &&
                !billDisplay.contains("No bill information available") &&
                !billDisplay.contains("Error in bill data") &&
                !billDisplay.contains("No bill summary available") &&
                billDisplay.length() > 20; // Reduced threshold for summary display
    }

    private String repeatString(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    // POLISHED: displayResult with clean sections
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

            // Show prepaid details from SERVER 1
            Object SERVER1DataObj = result.get("SERVER1_data");
            if (SERVER1DataObj != null) {
                Map<String, Object> cleanedData = cleanSERVER1Data(SERVER1DataObj);

                boolean hasValidData = !cleanedData.isEmpty() &&
                        !cleanedData.containsKey("error") &&
                        (cleanedData.containsKey("customer_info") ||
                                cleanedData.containsKey("recent_transactions"));

                if (hasValidData) {
                    output.append("\n══════════════════════════════════════════════════\n");
                    output.append("📋 PREPAID CUSTOMER DETAILS\n");
                    output.append("══════════════════════════════════════════════════\n");

                    String prepaidDisplay = formatPrepaidDisplay(cleanedData);
                    if (!prepaidDisplay.trim().isEmpty() && !prepaidDisplay.equals("No data available")) {
                        output.append(prepaidDisplay);
                    }
                }
            }

            // Show merged SERVER 3 + SERVER 2 data
            Map<String, Object> mergedData = mergeSERVERData(result);
            if (mergedData != null && !mergedData.isEmpty()) {
                //output.append("\n══════════════════════════════════════════════════\n");
                //output.append("📋 CUSTOMER INFORMATION\n");
                //output.append("══════════════════════════════════════════════════\n");
                String displayText = formatMergedDisplayWithoutTable(mergedData);
                output.append(displayText);
            }

        } else if ("postpaid".equals(billType)) {
            // Handle meter lookup results
            if (result.containsKey("customer_results")) {
                List<String> customerNumbers = (List<String>) result.get("customer_numbers");
                List<Map<String, Object>> customerResults = (List<Map<String, Object>>) result.get("customer_results");

                output.append("\n📊 Found ").append(customerNumbers.size()).append(" customer(s) for this meter\n\n");

                for (int i = 0; i < customerResults.size(); i++) {
                    output.append(repeatString("=", 40)).append("\n");
                    output.append("👤 CUSTOMER ").append(i + 1).append("/").append(customerNumbers.size())
                            .append(": ").append(customerNumbers.get(i)).append("\n");
                    output.append(repeatString("=", 40)).append("\n");

                    Map<String, Object> customerResult = customerResults.get(i);
                    Map<String, Object> mergedData = mergeSERVERData(customerResult);

                    if (mergedData != null && !mergedData.isEmpty()) {
                        String displayText = formatMergedDisplayWithoutTable(mergedData);
                        output.append(displayText).append("\n");
                    } else {
                        output.append("❌ No data available for this customer\n\n");
                    }
                }
            } else {
                // Regular postpaid (consumer number)
                output.append("👤 Consumer Number: ").append(result.getOrDefault("customer_number", "N/A")).append("\n");

                // Show merged SERVER 3 + SERVER 2 data for postpaid
                Map<String, Object> mergedData = mergeSERVERData(result);
                if (mergedData != null && !mergedData.isEmpty()) {
                    //output.append("\n").append(repeatString("=", 30)).append("\n");
                    //output.append("📋 CUSTOMER INFORMATION\n");
                    output.append(repeatString("=", 30)).append("\n");
                    String displayText = formatMergedDisplayWithoutTable(mergedData);
                    output.append(displayText);
                } else {
                    output.append("❌ No customer data found\n");
                }
            }
        }

        return output.toString();
    }


  private void checkStoragePermission() {
        if (isStoragePermissionGranted()) {
            return;
        }
        
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                        },
                        STORAGE_PERMISSION_CODE);
            }
        }
    }

    private boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private Map<String, String> extractDataForExcel(Map<String, Object> result, String type) {
        java.util.Map<String, String> excelData = new java.util.HashMap<>();

        try {
            if ("prepaid".equals(type)) {
                excelData.put("Meter Number", getSafeString(result.get("meter_number")));
                excelData.put("Consumer Number", getSafeString(result.get("consumer_number")));

                Map<String, Object> mergedData = mergeSERVERData(result);
                if (mergedData != null) {
                    Map<String, String> customerInfo = (Map<String, String>) mergedData.get("customer_info");
                    Map<String, String> balanceInfo = (Map<String, String>) mergedData.get("balance_info");

                    if (customerInfo != null) {
                        excelData.putAll(customerInfo);
                    }
                    if (balanceInfo != null) {
                        excelData.put("Arrear Amount", balanceInfo.get("Arrear Amount"));
                        excelData.put("Total Balance", balanceInfo.get("Total Balance"));
                    }
                }

            } else {
                excelData.put("Customer Number", getSafeString(result.get("customer_number")));

                Map<String, Object> mergedData = mergeSERVERData(result);
                if (mergedData != null) {
                    Map<String, String> customerInfo = (Map<String, String>) mergedData.get("customer_info");
                    Map<String, String> balanceInfo = (Map<String, String>) mergedData.get("balance_info");

                    if (customerInfo != null) {
                        excelData.putAll(customerInfo);
                    }
                    if (balanceInfo != null) {
                        excelData.put("Arrear Amount", balanceInfo.get("Arrear Amount"));
                    }
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

    private void saveLookupToExcel(Map<String, Object> result, String inputNumber) {
        // Check if ExcelHelper is initialized
        if (excelHelper == null) {
            if (isStoragePermissionGranted()) {
                excelHelper = new ExcelHelper(this);
            } else {
                Log.e(TAG, "Cannot save - no storage permission");
                showResult("❌ Cannot save - storage permission required");
                return;
            }
        }
        
        try {
            if (selectedType.equals("prepaid")) {
                Map<String, String> excelData = extractDataForExcel(result, "prepaid");
                excelHelper.savePrepaidLookup("User", inputNumber, excelData);
            } else {
                if (result.containsKey("customer_results")) {
                    List<Map<String, Object>> customerResults = (List<Map<String, Object>>) result.get("customer_results");
                    List<Map<String, String>> excelDataList = new ArrayList<>();

                    for (Map<String, Object> customerResult : customerResults) {
                        Map<String, String> excelData = extractDataForExcel(customerResult, "postpaid");
                        excelDataList.add(excelData);
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
            showResult("❌ Error saving to Excel: " + e.getMessage());
        }
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
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (excelHelper != null) {
            excelHelper.close();
        }
    }
}
