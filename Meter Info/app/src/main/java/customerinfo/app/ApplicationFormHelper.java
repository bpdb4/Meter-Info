package customerinfo.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public class ApplicationHelper {
    
    private MainActivity mainActivity;
    
    public ApplicationHelper(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }
    
    /**
     * Main method to fetch data for application form
     * Returns data in EXACT format expected by HTML
     */
    public Map<String, Object> fetchDataForApplicationForm(String inputNumber, String type) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            System.out.println("🔍 APPLICATION HELPER: Fetching " + type + " data for: " + inputNumber);
            
            if ("prepaid".equals(type)) {
                result = fetchPrepaidDataForApplication(inputNumber);
            } else {
                result = fetchPostpaidDataForApplication(inputNumber);
            }
            
        } catch (Exception e) {
            System.out.println("❌ APPLICATION HELPER ERROR: " + e.getMessage());
            result.put("error", "Data fetch failed: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Fetch prepaid data in exact HTML format
     */
    private Map<String, Object> fetchPrepaidDataForApplication(String meterNumber) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Step 1: Get consumer number from SERVER1
            Map<String, Object> server1Result = MainActivity.SERVER1Lookup(meterNumber);
            String consumerNumber = (String) server1Result.get("consumer_number");
            
            if (consumerNumber == null || server1Result.containsKey("error")) {
                result.put("error", "মিটার নং ভুল বা ডেটা পাওয়া যায়নি");
                return result;
            }
            
            // Step 2: Get customer data from SERVER3 (includes SERVER2)
            Map<String, Object> server3Result = MainActivity.SERVER3Lookup(consumerNumber);
            
            if (server3Result.containsKey("error")) {
                result.put("error", "গ্রাহক তথ্য পাওয়া যায়নি: " + server3Result.get("error"));
                return result;
            }
            
            // Step 3: Extract data in exact HTML format
            extractFormData(server3Result, server1Result, result, "prepaid");
            
        } catch (Exception e) {
            result.put("error", "ডেটা প্রসেসিং ব্যর্থ: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Fetch postpaid data in exact HTML format
     */
    private Map<String, Object> fetchPostpaidDataForApplication(String customerNumber) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get customer data from SERVER3 (includes SERVER2)
            Map<String, Object> server3Result = MainActivity.SERVER3Lookup(customerNumber);
            
            if (server3Result.containsKey("error")) {
                result.put("error", "গ্রাহক তথ্য পাওয়া যায়নি: " + server3Result.get("error"));
                return result;
            }
            
            // Extract data in exact HTML format
            extractFormData(server3Result, null, result, "postpaid");
            
        } catch (Exception e) {
            result.put("error", "ডেটা প্রসেসিং ব্যর্থ: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Extract data in EXACT format expected by HTML form
     */
    private void extractFormData(Map<String, Object> server3Result, 
                                Map<String, Object> server1Result, 
                                Map<String, Object> result, 
                                String type) {
        try {
            JSONObject server3Data = (JSONObject) server3Result.get("SERVER3_data");
            Object server2DataObj = server3Result.get("SERVER2_data");
            JSONObject server2Data = null;
            
            if (server2DataObj instanceof JSONObject) {
                server2Data = (JSONObject) server2DataObj;
            }
            
            // Extract customer information in EXACT HTML field names
            extractCustomerInfo(server3Data, server2Data, result);
            
            // Extract balance/arrear information
            extractBalanceInfo(server3Data, server2Data, result);
            
            // Extract recharge history for prepaid
            if ("prepaid".equals(type) && server1Result != null) {
                extractRechargeHistory(server1Result, result);
            } else {
                // For postpaid or if no recharge data, create empty recharge array
                result.put("recharges", new ArrayList<>());
            }
            
        } catch (Exception e) {
            System.out.println("❌ Error extracting form data: " + e.getMessage());
            result.put("error", "ফর্ম ডেটা এক্সট্রাক্ট ব্যর্থ: " + e.getMessage());
        }
    }
    
    /**
     * Extract customer info in EXACT HTML field names
     */
    private void extractCustomerInfo(JSONObject server3Data, JSONObject server2Data, Map<String, Object> result) {
        // These field names MUST match your HTML element IDs
        Map<String, String> customerInfo = new HashMap<>();
        
        // From SERVER3 (primary source)
        if (server3Data != null) {
            customerInfo.put("customer_name", server3Data.optString("customerName", ""));
            customerInfo.put("father_name", server3Data.optString("fatherName", ""));
            customerInfo.put("address", server3Data.optString("customerAddr", ""));
            customerInfo.put("meter_no", server3Data.optString("meterNum", ""));
            customerInfo.put("consumer_no", server3Data.optString("customerNumber", ""));
        }
        
        // Supplement with SERVER2 data
        if (server2Data != null && !server2Data.has("error")) {
            // Extract mobile number from SERVER2 balanceInfo
            if (server2Data.has("balanceInfo")) {
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
                    // Ignore mobile number extraction errors
                }
            }
            
            // Supplement missing fields from SERVER2 customerInfo
            if (server2Data.has("customerInfo") && server2Data.getJSONArray("customerInfo").length() > 0) {
                try {
                    JSONArray customerInfoArray = server2Data.getJSONArray("customerInfo");
                    if (customerInfoArray.length() > 0 && customerInfoArray.getJSONArray(0).length() > 0) {
                        JSONObject customer = customerInfoArray.getJSONArray(0).getJSONObject(0);
                        
                        if (customerInfo.get("customer_name").isEmpty()) {
                            customerInfo.put("customer_name", customer.optString("CUSTOMER_NAME", ""));
                        }
                        if (customerInfo.get("address").isEmpty()) {
                            customerInfo.put("address", customer.optString("ADDRESS", ""));
                        }
                        if (customerInfo.get("meter_no").isEmpty()) {
                            customerInfo.put("meter_no", customer.optString("METER_NUM", ""));
                        }
                        if (customerInfo.get("consumer_no").isEmpty()) {
                            customerInfo.put("consumer_no", customer.optString("CUSTOMER_NUMBER", ""));
                        }
                    }
                } catch (Exception e) {
                    // Ignore customer info extraction errors
                }
            }
        }
        
        // Clean and validate all fields
        for (Map.Entry<String, String> entry : customerInfo.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.equals("null") || value.isEmpty()) {
                customerInfo.put(entry.getKey(), "");
            }
        }
        
        // Put all customer info into result (EXACT field names for HTML)
        result.putAll(customerInfo);
    }
    
    /**
     * Extract balance/arrear information
     */
    private void extractBalanceInfo(JSONObject server3Data, JSONObject server2Data, Map<String, Object> result) {
        String arrearAmount = "";
        
        // Try SERVER2 finalBalanceInfo first
        if (server2Data != null && server2Data.has("finalBalanceInfo")) {
            String balanceString = server2Data.optString("finalBalanceInfo");
            if (isValidValue(balanceString)) {
                arrearAmount = extractAmountFromBalance(balanceString);
            }
        }
        
        // Try SERVER2 balanceInfo object
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
                // Ignore balance info errors
            }
        }
        
        // Fallback to SERVER3 arrearAmount
        if (arrearAmount.isEmpty() && server3Data != null && server3Data.has("arrearAmount")) {
            String server3Arrear = server3Data.optString("arrearAmount");
            if (isValidValue(server3Arrear) && !server3Arrear.equals("0") && !server3Arrear.equals("0.00")) {
                arrearAmount = server3Arrear;
            }
        }
        
        // Clean arrear amount
        if (arrearAmount.equals("0") || arrearAmount.equals("0.00") || arrearAmount.isEmpty()) {
            arrearAmount = "";
        }
        
        result.put("arrear", arrearAmount);
    }
    
    /**
     * Extract recharge history for prepaid
     */
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
        
        // Limit to last 4 recharges as per HTML table
        int maxRecharges = Math.min(recharges.size(), 4);
        result.put("recharges", recharges.subList(0, maxRecharges));
    }
    
    /**
     * Extract recharge transactions from SERVER1 response
     */
    private List<Map<String, String>> extractRechargeTransactions(String responseBody) {
        List<Map<String, String>> transactions = new ArrayList<>();
        
        try {
            int index = 0;
            int count = 0;
            
            while (index != -1 && count < 10) { // Limit to 10 for safety
                // Look for token pattern
                index = responseBody.indexOf("\"tokens\":{\"_text\":\"", index);
                if (index == -1) break;
                
                // Extract token
                int tokenStart = index + "\"tokens\":{\"_text\":\"".length();
                int tokenEnd = responseBody.indexOf("\"", tokenStart);
                
                if (tokenEnd != -1) {
                    // Extract transaction fields around this token
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
    
    /**
     * Extract transaction fields (Date and Amount only - as per HTML table)
     */
    private Map<String, String> extractTransactionFields(String response, int tokenPosition) {
        Map<String, String> transaction = new HashMap<>();
        
        try {
            int searchStart = Math.max(0, tokenPosition - 1000);
            int searchEnd = Math.min(response.length(), tokenPosition + 200);
            String searchArea = response.substring(searchStart, searchEnd);
            
            // Extract only Date and Amount (as per your HTML table columns)
            String date = extractExactValue(searchArea, "date");
            String amount = extractExactValue(searchArea, "grossAmount");
            
            // Format for HTML display
            transaction.put("Date", formatDateForDisplay(date));
            transaction.put("Amount", formatAmountForDisplay(amount));
            
        } catch (Exception e) {
            System.out.println("❌ Error extracting transaction fields: " + e.getMessage());
        }
        
        return transaction;
    }
    
    /**
     * Format date for display
     */
    private String formatDateForDisplay(String date) {
        if (date == null || date.equals("N/A") || date.isEmpty()) {
            return "";
        }
        // Simple date formatting - you can enhance this
        return date.replace("T", " ").split(" ")[0]; // Get only date part
    }
    
    /**
     * Format amount for display
     */
    private String formatAmountForDisplay(String amount) {
        if (amount == null || amount.equals("N/A") || amount.isEmpty()) {
            return "";
        }
        return "৳" + amount;
    }
    
    /**
     * Extract amount from balance string
     */
    private String extractAmountFromBalance(String balanceString) {
        if (balanceString == null || balanceString.isEmpty() || balanceString.equals("null")) {
            return "";
        }
        
        try {
            // If it's just a number, return it
            if (!balanceString.contains(",") && !balanceString.contains(":")) {
                return balanceString.trim();
            }
            
            // If it has breakdown, take the first part (total balance)
            String[] parts = balanceString.split(",");
            if (parts.length > 0) {
                return parts[0].trim();
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        
        return balanceString;
    }
    
    /**
     * Helper to extract exact value from JSON pattern
     */
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
    
    /**
     * Check if value is valid
     */
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