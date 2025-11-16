package customerinfo.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.*;

public class ApplicationFormHelper {

    private String currentType;

    public ApplicationFormHelper() {
        // Empty constructor
    }

    public Map<String, Object> fetchDataForApplicationForm(String inputNumber, String type) {
        Map<String, Object> result = new HashMap<>();
        this.currentType = type;

        try {
            System.out.println("🔍 APPLICATION FORM HELPER: Fetching " + type + " data for: " + inputNumber);

            if ("prepaid".equals(type)) {
                result = fetchPrepaidDataForApplication(inputNumber);
            } else {
                result = fetchPostpaidDataForApplication(inputNumber);
            }

        } catch (Exception e) {
            System.out.println("❌ APPLICATION FORM HELPER ERROR: " + e.getMessage());
            result.put("error", "Data fetch failed: " + e.getMessage());
        }

        return result;
    }

    private Map<String, Object> fetchPrepaidDataForApplication(String meterNumber) {
        Map<String, Object> result = new HashMap<>();

        try {
            Map<String, Object> server1Result = MainActivity.SERVER1Lookup(meterNumber);
            String consumerNumber = (String) server1Result.get("consumer_number");

            if (consumerNumber == null || server1Result.containsKey("error")) {
                result.put("error", "মিটার নং ভুল বা ডেটা পাওয়া যায়নি");
                return result;
            }

            Map<String, Object> server3Result = MainActivity.SERVER3Lookup(consumerNumber);

            if (server3Result.containsKey("error")) {
                result.put("error", "গ্রাহক তথ্য পাওয়া যায়নি: " + server3Result.get("error"));
                return result;
            }

            extractFormData(server3Result, server1Result, result, meterNumber, "prepaid");

        } catch (Exception e) {
            result.put("error", "ডেটা প্রসেসিং ব্যর্থ: " + e.getMessage());
        }

        return result;
    }

    private Map<String, Object> fetchPostpaidDataForApplication(String customerNumber) {
        Map<String, Object> result = new HashMap<>();

        try {
            Map<String, Object> server3Result = MainActivity.SERVER3Lookup(customerNumber);

            if (server3Result.containsKey("error")) {
                result.put("error", "গ্রাহক তথ্য পাওয়া যায়নি: " + server3Result.get("error"));
                return result;
            }

            extractFormData(server3Result, null, result, customerNumber, "postpaid");

        } catch (Exception e) {
            result.put("error", "ডেটা প্রসেসিং ব্যর্থ: " + e.getMessage());
        }

        return result;
    }

    private void extractFormData(Map<String, Object> server3Result, 
                                Map<String, Object> server1Result, 
                                Map<String, Object> result, 
                                String inputNumber,
                                String type) {
        try {
            JSONObject server3Data = (JSONObject) server3Result.get("SERVER3_data");
            Object server2DataObj = server3Result.get("SERVER2_data");
            JSONObject server2Data = null;

            if (server2DataObj instanceof JSONObject) {
                server2Data = (JSONObject) server2DataObj;
            }

            extractCustomerInfo(server3Data, server2Data, server1Result, result, inputNumber, type);
            extractBalanceInfo(server3Data, server2Data, result);

            if ("prepaid".equals(type) && server1Result != null) {
                extractRechargeHistory(server1Result, result);
            } else {
                result.put("recharges", new ArrayList<>());
            }

        } catch (Exception e) {
            System.out.println("❌ Error extracting form data: " + e.getMessage());
            result.put("error", "ফর্ম ডেটা এক্সট্রাক্ট ব্যর্থ: " + e.getMessage());
        }
    }

    private void extractCustomerInfo(JSONObject server3Data, JSONObject server2Data, 
                                   Map<String, Object> server1Result, 
                                   Map<String, Object> result, 
                                   String inputNumber, String type) {
        Map<String, String> customerInfo = new HashMap<>();

        // FOR PREPAID: Always get consumer number from SERVER1 first
        String consumerNumber = "";
        if ("prepaid".equals(type) && server1Result != null) {
            try {
                Object server1DataObj = server1Result.get("SERVER1_data");
                if (server1DataObj instanceof String) {
                    String responseBody = (String) server1DataObj;
                    consumerNumber = extractConsumerNumberFromSERVER1(responseBody);
                    
                    // Extract ALL customer info from SERVER1 first
                    extractCustomerInfoFromSERVER1(responseBody, customerInfo);
                    
                    // Only use SERVER2/SERVER3 if consumer number starts with "44"
                    if (consumerNumber.startsWith("44") && server3Data != null) {
                        // Supplement with SERVER3 data for "44" series
                        if (customerInfo.get("customer_name").isEmpty()) {
                            customerInfo.put("customer_name", server3Data.optString("customerName", ""));
                        }
                        if (customerInfo.get("father_name").isEmpty()) {
                            customerInfo.put("father_name", server3Data.optString("fatherName", ""));
                        }
                        if (customerInfo.get("address").isEmpty()) {
                            customerInfo.put("address", server3Data.optString("customerAddr", ""));
                        }
                        if (customerInfo.get("consumer_no").isEmpty()) {
                            customerInfo.put("consumer_no", server3Data.optString("customerNumber", ""));
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("❌ Error extracting customer info from SERVER1: " + e.getMessage());
            }
        }

        // FOR POSTPAID: Use SERVER3 data
        if ("postpaid".equals(type) && server3Data != null) {
            customerInfo.put("customer_name", server3Data.optString("customerName", ""));
            customerInfo.put("father_name", server3Data.optString("fatherName", ""));
            customerInfo.put("address", server3Data.optString("customerAddr", ""));
            customerInfo.put("consumer_no", server3Data.optString("customerNumber", ""));
            customerInfo.put("meter_no", server3Data.optString("meterNum", ""));
        }

        // Always set meter number for prepaid
        if ("prepaid".equals(type)) {
            customerInfo.put("meter_no", inputNumber);
        }

        // Mobile number extraction
        if ("prepaid".equals(type) && server1Result != null) {
            try {
                Object server1DataObj = server1Result.get("SERVER1_data");
                if (server1DataObj instanceof String) {
                    String responseBody = (String) server1DataObj;
                    String mobileNo = extractMobileFromSERVER1(responseBody);
                    if (!mobileNo.isEmpty()) {
                        customerInfo.put("mobile_no", mobileNo);
                    }
                }
            } catch (Exception e) {
                System.out.println("❌ Error extracting mobile from SERVER1: " + e.getMessage());
            }
        }

        // SERVER2 mobile number for postpaid
        if (server2Data != null && !server2Data.has("error")) {
            if ("postpaid".equals(type) && server2Data.has("balanceInfo")) {
                try {
                    JSONObject balanceInfo = server2Data.getJSONObject("balanceInfo");
                    if (balanceInfo.has("Result") && balanceInfo.getJSONArray("Result").length() > 0) {
                        JSONObject balanceResult = balanceInfo.getJSONArray("Result").getJSONObject(0);
                        String mobileNo = balanceResult.optString("MOBILE_NO", "");
                        if (!mobileNo.isEmpty() && !mobileNo.equals("null")) {
                            customerInfo.put("mobile_no", mobileNo);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("❌ Error extracting mobile from SERVER2: " + e.getMessage());
                }
            }
        }

        // Clean empty fields
        for (Map.Entry<String, String> entry : customerInfo.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.equals("null") || value.isEmpty()) {
                customerInfo.put(entry.getKey(), "");
            }
        }

        result.putAll(customerInfo);
    }

    // Extract consumer number from SERVER1
    private String extractConsumerNumberFromSERVER1(String responseBody) {
        try {
            String jsonPart = extractActualJson(responseBody);
            JSONObject SERVER1Data = new JSONObject(jsonPart);

            if (SERVER1Data.has("mCustomerData")) {
                JSONObject mCustomerData = SERVER1Data.getJSONObject("mCustomerData");
                if (mCustomerData.has("result")) {
                    JSONObject result = mCustomerData.getJSONObject("result");
                    return extractDirectValue(result, "customerAccountNo");
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Error extracting consumer number from SERVER1: " + e.getMessage());
        }
        return "";
    }

    // Extract customer info from SERVER1 response
    private void extractCustomerInfoFromSERVER1(String responseBody, Map<String, String> customerInfo) {
        try {
            String jsonPart = extractActualJson(responseBody);
            JSONObject SERVER1Data = new JSONObject(jsonPart);

            if (SERVER1Data.has("mCustomerData")) {
                JSONObject mCustomerData = SERVER1Data.getJSONObject("mCustomerData");
                if (mCustomerData.has("result")) {
                    JSONObject result = mCustomerData.getJSONObject("result");

                    // Map SERVER1 fields to form fields
                    customerInfo.put("customer_name", extractDirectValue(result, "customerName"));
                    customerInfo.put("address", extractDirectValue(result, "customerAddress"));
                    customerInfo.put("mobile_no", extractDirectValue(result, "customerPhone"));
                    customerInfo.put("consumer_no", extractDirectValue(result, "customerAccountNo"));
                    customerInfo.put("meter_no", extractDirectValue(result, "meterNumber"));
                    
                    System.out.println("✅ Extracted customer info from SERVER1");
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Error in extractCustomerInfoFromSERVER1: " + e.getMessage());
        }
    }

    // Helper method to extract direct values
    private String extractDirectValue(JSONObject jsonObject, String key) {
        try {
            if (!jsonObject.has(key)) {
                return "";
            }

            Object value = jsonObject.get(key);

            // Handle JSONObject with _text
            if (value instanceof JSONObject) {
                JSONObject obj = (JSONObject) value;
                if (obj.has("_text")) {
                    String textValue = obj.getString("_text");
                    return (textValue == null || textValue.isEmpty() || textValue.equals("{}")) ? "" : textValue.trim();
                }
                return "";
            }

            // Handle primitive types directly
            String stringValue = value.toString().trim();
            return (stringValue.isEmpty() || stringValue.equals("{}")) ? "" : stringValue;

        } catch (Exception e) {
            return "";
        }
    }

    // Extract the actual JSON from the JSON-RPC response
    private String extractActualJson(String responseBody) {
        try {
            int jsonStart = responseBody.indexOf("1:{");
            if (jsonStart != -1) {
                return responseBody.substring(jsonStart + 2);
            }
            return responseBody;
        } catch (Exception e) {
            return responseBody;
        }
    }

    private String extractMobileFromSERVER1(String responseBody) {
        try {
            int phoneIndex = responseBody.indexOf("\"customerPhone\":{\"_text\":\"");
            if (phoneIndex != -1) {
                int valueStart = phoneIndex + "\"customerPhone\":{\"_text\":\"".length();
                int valueEnd = responseBody.indexOf("\"", valueStart);
                if (valueEnd != -1) {
                    String mobileNo = responseBody.substring(valueStart, valueEnd);
                    if (!mobileNo.isEmpty() && !mobileNo.equals("null")) {
                        return mobileNo;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Error extracting mobile from SERVER1: " + e.getMessage());
        }
        return "";
    }

    private void extractBalanceInfo(JSONObject server3Data, JSONObject server2Data, Map<String, Object> result) {
        String arrearAmount = "";

        if (server2Data != null && server2Data.has("finalBalanceInfo")) {
            String balanceString = server2Data.optString("finalBalanceInfo");
            if (isValidValue(balanceString)) {
                arrearAmount = extractAmountFromBalance(balanceString);
            }
        }

        if (arrearAmount.isEmpty() && server2Data != null && server2Data.has("balanceInfo")) {
            try {
                JSONObject balanceInfo = server2Data.getJSONObject("balanceInfo");
                if (balanceInfo.has("Result") && balanceInfo.getJSONArray("Result").length() > 0) {
                    JSONObject balanceResult = balanceInfo.getJSONArray("Result").getJSONObject(0);
                    double totalBalance = balanceResult.optDouble("BALANCE", 0);
                    if (totalBalance > 0) {
                        arrearAmount = String.format("%.0f", totalBalance);
                    }
                }
            } catch (Exception e) {
                System.out.println("❌ Error parsing balanceInfo: " + e.getMessage());
            }
        }

        if (arrearAmount.isEmpty() && server3Data != null && server3Data.has("arrearAmount")) {
            String server3Arrear = server3Data.optString("arrearAmount");
            if (isValidValue(server3Arrear) && !server3Arrear.equals("0") && !server3Arrear.equals("0.00")) {
                arrearAmount = server3Arrear;
            }
        }

        if (arrearAmount.equals("0") || arrearAmount.equals("0.00") || arrearAmount.isEmpty()) {
            arrearAmount = "";
        }

        result.put("arrear", arrearAmount);
    }

    private void extractRechargeHistory(Map<String, Object> server1Result, Map<String, Object> result) {
        List<Map<String, String>> recharges = new ArrayList<>();

        try {
            Object server1DataObj = server1Result.get("SERVER1_data");
            if (server1DataObj instanceof String) {
                String responseBody = (String) server1DataObj;
                recharges = extractRechargeTransactions(responseBody);
            }
        } catch (Exception e) {
            System.out.println("❌ Error extracting recharge history: " + e.getMessage());
        }

        int maxRecharges = Math.min(recharges.size(), 4);
        result.put("recharges", recharges.subList(0, maxRecharges));
    }

    private List<Map<String, String>> extractRechargeTransactions(String responseBody) {
        List<Map<String, String>> transactions = new ArrayList<>();

        try {
            int index = 0;
            int count = 0;

            while (index != -1 && count < 10) {
                index = responseBody.indexOf("\"tokens\":{\"_text\":\"", index);
                if (index == -1) break;

                int tokenStart = index + "\"tokens\":{\"_text\":\"".length();
                int tokenEnd = responseBody.indexOf("\"", tokenStart);

                if (tokenEnd != -1) {
                    Map<String, String> transaction = extractTransactionFields(responseBody, index);
                    transactions.add(transaction);
                    count++;
                }

                index = tokenEnd + 1;
            }

        } catch (Exception e) {
            System.out.println("❌ Error extracting recharge transactions: " + e.getMessage());
        }

        return transactions;
    }

    private Map<String, String> extractTransactionFields(String response, int tokenPosition) {
        Map<String, String> transaction = new HashMap<>();

        try {
            int searchStart = Math.max(0, tokenPosition - 1000);
            int searchEnd = Math.min(response.length(), tokenPosition + 200);
            String searchArea = response.substring(searchStart, searchEnd);

            String date = extractExactValue(searchArea, "date");
            String amount = extractExactValue(searchArea, "grossAmount");

            transaction.put("Date", formatDateForDisplay(date));
            transaction.put("Amount", formatAmountForDisplay(amount));

        } catch (Exception e) {
            System.out.println("❌ Error extracting transaction fields: " + e.getMessage());
        }

        return transaction;
    }

    private String formatDateForDisplay(String date) {
        if (date == null || date.equals("N/A") || date.isEmpty()) {
            return "";
        }
        return date.replace("T", " ").split(" ")[0];
    }

    private String formatAmountForDisplay(String amount) {
        if (amount == null || amount.equals("N/A") || amount.isEmpty()) {
            return "";
        }
        return "৳" + amount;
    }

    private String extractAmountFromBalance(String balanceString) {
        if (balanceString == null || balanceString.isEmpty() || balanceString.equals("null")) {
            return "";
        }

        try {
            if (!balanceString.contains(",") && !balanceString.contains(":")) {
                return balanceString.trim();
            }

            String[] parts = balanceString.split(",");
            if (parts.length > 0) {
                return parts[0].trim();
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }

        return balanceString;
    }

    private String extractExactValue(String text, String fieldName) {
        try {
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
        return "";
    }

    private boolean isValidValue(String value) {
        if (value == null) return false;

        String trimmed = value.trim();
        return !trimmed.isEmpty() &&
               !trimmed.equals("N/A") &&
               !trimmed.equals("null") &&
               !trimmed.equals("{}") &&
               !trimmed.equals("undefined") &&
               !trimmed.equals("0");
    }
}
