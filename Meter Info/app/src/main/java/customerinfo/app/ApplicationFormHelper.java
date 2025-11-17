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

    // ---------------- PREPAID ------------------
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

    // ---------------- POSTPAID ------------------
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

    // MASTER FORM EXTRACTOR (common)
    private void extractFormData(Map<String, Object> server3Result,
                                Map<String, Object> server1Result,
                                Map<String, Object> result,
                                String inputNumber,
                                String type) {
        try {
            JSONObject server3Data = (JSONObject) server3Result.get("SERVER3_data");
            Object server2Obj = server3Result.get("SERVER2_data");
            JSONObject server2Data = server2Obj instanceof JSONObject ? (JSONObject) server2Obj : null;

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

    // ---------------- CUSTOMER INFO -------------------
    private void extractCustomerInfo(JSONObject server3Data, JSONObject server2Data,
                                     Map<String, Object> server1Result,
                                     Map<String, Object> result,
                                     String inputNumber, String type) {

        Map<String, String> customerInfo = new HashMap<>();

        // ---------- PREPAID ----------
        if ("prepaid".equals(type) && server1Result != null) {
            try {
                Object s1obj = server1Result.get("SERVER1_data");
                if (s1obj instanceof String) {
                    String body = (String) s1obj;

                    // extract full fields
                    extractCustomerInfoFromSERVER1(body, customerInfo);

                    // consumer number from SERVER1
                    String consumerNumber = extractConsumerNumberFromSERVER1(body);
                    if (!consumerNumber.isEmpty()) {
                        customerInfo.put("consumer_no", consumerNumber);
                    }
                }
            } catch (Exception ignored) {}
        }

        // ---------- POSTPAID (simple version as requested) ----------
        if ("postpaid".equals(type) && server3Data != null) {

            customerInfo.put("customer_name", server3Data.optString("customerName", ""));
            customerInfo.put("father_name", server3Data.optString("fatherName", ""));
            customerInfo.put("address", server3Data.optString("customerAddr", ""));

            // No mobile number for postpaid
            customerInfo.put("mobile_no", "");

            // meter number (fallback to input)
            String meter = server3Data.optString("meterNum", "");
            if (meter == null || meter.isEmpty() || meter.equals("null")) {
                meter = inputNumber;
            }
            customerInfo.put("meter_no", meter);

            // always the input number
            customerInfo.put("consumer_no", inputNumber);
        }

        // PREPAID meter number override
        if ("prepaid".equals(type)) {
            customerInfo.put("meter_no", inputNumber);
        }

        // clean nulls
        for (String key : Arrays.asList("customer_name","father_name","address","mobile_no","meter_no","consumer_no")) {
            customerInfo.putIfAbsent(key, "");
            if (customerInfo.get(key) == null) customerInfo.put(key, "");
        }

        result.putAll(customerInfo);
    }

    // SERVER1 consumer number extractor
    private String extractConsumerNumberFromSERVER1(String responseBody) {
        try {
            String json = extractActualJson(responseBody);
            JSONObject data = new JSONObject(json);

            if (data.has("mCustomerData")) {
                JSONObject res = data.getJSONObject("mCustomerData").getJSONObject("result");
                return extractDirectValue(res, "customerAccountNo");
            }
        } catch (Exception ignored) {}
        return "";
    }

    // SERVER1 customer details extractor
    private void extractCustomerInfoFromSERVER1(String responseBody, Map<String, String> info) {
        try {
            String json = extractActualJson(responseBody);
            JSONObject data = new JSONObject(json);

            if (data.has("mCustomerData")) {
                JSONObject res = data.getJSONObject("mCustomerData").getJSONObject("result");

                info.put("customer_name", extractDirectValue(res, "customerName"));
                info.put("address", extractDirectValue(res, "customerAddress"));
                info.put("mobile_no", extractDirectValue(res, "customerPhone"));
                info.put("consumer_no", extractDirectValue(res, "customerAccountNo"));
                info.put("meter_no", extractDirectValue(res, "meterNumber"));
            }
        } catch (Exception ignored) {}
    }

    private String extractDirectValue(JSONObject json, String key) {
        try {
            if (!json.has(key)) return "";
            Object obj = json.get(key);

            if (obj instanceof JSONObject && ((JSONObject)obj).has("_text")) {
                return ((JSONObject)obj).getString("_text").trim();
            }
            return obj.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String extractActualJson(String s) {
        try {
            int i = s.indexOf("1:{");
            if (i != -1) return s.substring(i + 2);
            return s;
        } catch (Exception e) {
            return s;
        }
    }

    // ---------------- BALANCE -------------------
    private void extractBalanceInfo(JSONObject s3, JSONObject s2, Map<String, Object> result) {
        String arrear = "";

        try {
            if (s2 != null && s2.has("finalBalanceInfo")) {
                String val = s2.optString("finalBalanceInfo");
                if (isValidValue(val)) arrear = extractAmountFromBalance(val);
            }

            if (arrear.isEmpty() && s2 != null && s2.has("balanceInfo")) {
                JSONObject bi = s2.getJSONObject("balanceInfo");
                if (bi.has("Result") && bi.getJSONArray("Result").length() > 0) {
                    double bal = bi.getJSONArray("Result").getJSONObject(0).optDouble("BALANCE", 0);
                    if (bal > 0) arrear = String.format("%.0f", bal);
                }
            }

            if (arrear.isEmpty() && s3.has("arrearAmount")) {
                String a = s3.optString("arrearAmount");
                if (isValidValue(a) && !a.equals("0")) arrear = a;
            }
        } catch (Exception ignored) {}

        if (arrear.equals("0") || arrear.equals("0.00")) arrear = "";
        result.put("arrear", arrear);
    }

    private boolean isValidValue(String v) {
        return v != null && !v.trim().isEmpty() &&
               !v.equals("0") && !v.equals("0.00") &&
               !v.equals("null") && !v.equals("N/A");
    }

    private String extractAmountFromBalance(String s) {
        try {
            if (!s.contains(",") && !s.contains(":")) return s.trim();
            return s.split(",")[0].trim();
        } catch (Exception e) {
            return s;
        }
    }

    // ---------------- RECHARGE HISTORY -------------------
    private void extractRechargeHistory(Map<String, Object> server1Result, Map<String, Object> result) {
        List<Map<String, String>> list = new ArrayList<>();

        try {
            Object s1 = server1Result.get("SERVER1_data");
            if (s1 instanceof String) list = extractRechargeTransactions((String) s1);
        } catch (Exception ignored) {}

        result.put("recharges", list.subList(0, Math.min(list.size(), 4)));
    }

    private List<Map<String, String>> extractRechargeTransactions(String s) {
        List<Map<String, String>> out = new ArrayList<>();
        int idx = 0, count = 0;

        try {
            while ((idx = s.indexOf("\"tokens\":{\"_text\":\"", idx)) != -1 && count < 10) {

                int start = Math.max(0, idx - 800);
                int end = Math.min(s.length(), idx + 300);
                String area = s.substring(start, end);

                Map<String,String> tr = new HashMap<>();
                tr.put("Date", extractExactValue(area, "date"));
                tr.put("Amount", "৳" + extractExactValue(area, "grossAmount"));

                out.add(tr);
                count++;

                idx++;
            }
        } catch (Exception ignored) {}

        return out;
    }

    private String extractExactValue(String body, String field) {
        try {
            String p = "\"" + field + "\":{\"_text\":\"";
            int i = body.indexOf(p);
            if (i != -1) {
                int st = i + p.length();
                int en = body.indexOf("\"", st);
                return en > st ? body.substring(st, en) : "";
            }
        } catch (Exception ignored) {}
        return "";
    }
}
  
