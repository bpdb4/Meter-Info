package customerinfo.app;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import android.os.Build.VERSION_CODES;  

public class ExcelHelper {
    private static final String TAG = "ExcelHelper";
    private static final String EXCEL_FILE_NAME = "BPDB_Meter_Lookups.xlsx";

    private Context context;
    private Workbook workbook;
    private Sheet prepaidSheet;
    private Sheet postpaidSheet;
    private String filePath;

    public ExcelHelper(Context context) {
        this.context = context;
        initializeWorkbook();
    }

    private void initializeWorkbook() {
        try {
            filePath = getExcelFilePath();
            File file = new File(filePath);

            Log.d(TAG, "Excel file path: " + filePath);
            Log.d(TAG, "File exists: " + file.exists() + ", size: " + (file.exists() ? file.length() : 0));

            if (file.exists() && file.length() > 0) {
                try {
                    // Load existing workbook
                    FileInputStream inputStream = new FileInputStream(file);
                    workbook = new XSSFWorkbook(inputStream);
                    inputStream.close();

                    Log.d(TAG, "✅ Successfully loaded existing workbook");

                    // Get existing sheets
                    prepaidSheet = workbook.getSheet("PrepaidLookups");
                    postpaidSheet = workbook.getSheet("PostpaidLookups");

                    if (prepaidSheet == null) {
                        Log.d(TAG, "Prepaid sheet not found, creating new one");
                        prepaidSheet = workbook.createSheet("PrepaidLookups");
                        createPrepaidHeaders();
                    } else {
                        Log.d(TAG, "Prepaid sheet found with " + prepaidSheet.getPhysicalNumberOfRows() + " rows");
                    }

                    if (postpaidSheet == null) {
                        Log.d(TAG, "Postpaid sheet not found, creating new one");
                        postpaidSheet = workbook.createSheet("PostpaidLookups");
                        createPostpaidHeaders();
                    } else {
                        Log.d(TAG, "Postpaid sheet found with " + postpaidSheet.getPhysicalNumberOfRows() + " rows");
                    }

                } catch (Exception e) {
                    Log.e(TAG, "❌ Error loading existing workbook: " + e.getMessage());
                    // Fallback: create new workbook
                    createNewWorkbook();
                }
            } else {
                Log.d(TAG, "📝 Creating new workbook - file doesn't exist or is empty");
                createNewWorkbook();
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Critical error in initializeWorkbook: " + e.getMessage());
            createNewWorkbook();
        }
    }

    private void createNewWorkbook() {
        workbook = new XSSFWorkbook();
        prepaidSheet = workbook.createSheet("PrepaidLookups");
        postpaidSheet = workbook.createSheet("PostpaidLookups");
        createPrepaidHeaders();
        createPostpaidHeaders();

        Log.d(TAG, "✅ New workbook created and saved");
    }

    private String getExcelFilePath() {
        File directory = getStorageDir();
        return new File(directory, EXCEL_FILE_NAME).getAbsolutePath();
    }

    private static File getStorageDir() {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File dir = new File(downloadsDir, "BPDB_Records");
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            Log.d(TAG, "Directory created: " + created + " at " + dir.getAbsolutePath());
        }
        return dir;
    }

    private void createPrepaidHeaders() {
        try {
            Row headerRow = prepaidSheet.createRow(0);
            String[] prepaidHeaders = {
                    "Timestamp", "User", "Meter Number", "Consumer Number", "Lock Status",
                    "Account Type", "Customer Name", "Customer Address", "Phone", "Division",
                    "Sub Division", "Location Code", "Area Code", "Description", "Tariff Category",
                    "Sanctioned Load", "Installation Date", "Connection Date", "Bill Group",
                    "Book Number", "Walk Order", "Account_Number", "Last Recharge Time",
                    "Last Recharge Amount", "Arrear Amount", "Total Balance"
            };

            CellStyle headerStyle = createHeaderStyle();
            for (int i = 0; i < prepaidHeaders.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(prepaidHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            autoSizeColumns(prepaidSheet);
            Log.d(TAG, "✅ Prepaid headers created");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error creating prepaid headers: " + e.getMessage());
        }
    }

    private void createPostpaidHeaders() {
        try {
            Row headerRow = postpaidSheet.createRow(0);
            String[] postpaidHeaders = {
                    "Timestamp", "User", "Customer Name", "Customer Address", "Meter Number",
                    "Meter Condition", "Meter Status", "Connection Date", "Customer Number",
                    "Location Code", "Area Code", "Bill Group", "Book Number", "Tariff Description",
                    "Sanctioned Load", "Walk Order", "Account_Number", "Usage Type", "Description",
                    "Start Bill Cycle", "Arrear Amount"
            };

            CellStyle headerStyle = createHeaderStyle();
            for (int i = 0; i < postpaidHeaders.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(postpaidHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            autoSizeColumns(postpaidSheet);
            Log.d(TAG, "✅ Postpaid headers created");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error creating postpaid headers: " + e.getMessage());
        }
    }

    private CellStyle createHeaderStyle() {
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        return headerStyle;
    }
    private void showToast(final String message) {
        android.os.Handler handler = new android.os.Handler(context.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void savePrepaidLookup(String user, String meterNumber, Map<String, String> customerData) {
        try {
            int currentRows = prepaidSheet.getPhysicalNumberOfRows();
            Row row = prepaidSheet.createRow(currentRows);

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());

            int col = 0;
            row.createCell(col++).setCellValue(timestamp);
            row.createCell(col++).setCellValue(user);
            row.createCell(col++).setCellValue(getSafeString(meterNumber));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Consumer Number")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Lock Status")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Account Type")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Customer Name")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Customer Address")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Phone")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Division")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Sub Division")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Location Code")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Area Code")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Description")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Tariff Category")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Sanctioned Load")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Installation Date")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Connection Date")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Bill Group")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Book Number")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Walk Order")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Account_Number")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Last Recharge Time")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Last Recharge Amount")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Arrear Amount")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Total Balance")));

            // Save the workbook
            boolean saved = saveWorkbook();
            if (saved) {
                showToast("✅ Prepaid data saved to Excel");
            } else {
                showToast("❌ Failed to save prepaid data");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error saving prepaid lookup: " + e.getMessage());
        }
    }

    public void savePostpaidLookup(String user, Map<String, String> customerData) {
        try {
            int currentRows = postpaidSheet.getPhysicalNumberOfRows();
            Row row = postpaidSheet.createRow(currentRows);

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());

            int col = 0;
            row.createCell(col++).setCellValue(timestamp);
            row.createCell(col++).setCellValue(user);
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Customer Name")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Customer Address")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Meter Number")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Meter Condition")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Meter Status")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Connection Date")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Customer Number")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Location Code")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Area Code")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Bill Group")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Book Number")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Tariff Description")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Sanctioned Load")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Walk Order")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Account_Number")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Usage Type")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Description")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Start Bill Cycle")));
            row.createCell(col++).setCellValue(getSafeString(customerData.get("Arrear Amount")));

            // Save the workbook
            boolean saved = saveWorkbook();
            if (saved) {
                showToast("✅ Postpaid data saved to Excel");
            } else {
                showToast("❌ Failed to save postpaid data");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error saving postpaid lookup: " + e.getMessage());
        }
    }

    public void saveMultiplePostpaidLookups(String user, List<Map<String, String>> customerDataList) {
        for (Map<String, String> customerData : customerDataList) {
            savePostpaidLookup(user, customerData);
        }
    }

    private String getSafeString(String value) {
        if (value == null || value.equals("null") || value.isEmpty()) {
            return "N/A";
        }
        return value;
    }

    private boolean saveWorkbook() {
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(filePath);
            workbook.write(outputStream);
            Log.d(TAG, "💾 Workbook saved successfully: " + filePath);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "❌ Error saving workbook: " + e.getMessage());
            return false;
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "❌ Error closing output stream: " + e.getMessage());
                }
            }
        }
    }

    public boolean saveExcelFile() {
        // For compatibility - just save the current workbook
        return saveWorkbook();
    }

    public void autoSizeColumns(Sheet sheet) {
        try {
            for (int i = 0; i < sheet.getRow(0).getLastCellNum(); i++) {
                sheet.setColumnWidth(i, 15 * 256);
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error setting column widths: " + e.getMessage());
        }
    }

    public String getFilePath() {
    String fileName = "MeterLookups.xlsx";
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Android 10+ - Save to Downloads folder with timestamp to prevent overwrite
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String uniqueFileName = "MeterLookups_" + timeStamp + ".xlsx";
        return new File(downloadsDir, uniqueFileName).getAbsolutePath();
    } else {
        // Android 9 and below - Save to Downloads with timestamp
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String uniqueFileName = "MeterLookups_" + timeStamp + ".xlsx";
        return new File(downloadsDir, uniqueFileName).getAbsolutePath();
    }
}

    public int getPrepaidRecordCount() {
        return Math.max(0, prepaidSheet.getPhysicalNumberOfRows() - 1);
    }

    public int getPostpaidRecordCount() {
        return Math.max(0, postpaidSheet.getPhysicalNumberOfRows() - 1);
    }

    public void close() {
        try {
            if (workbook != null) {
                saveWorkbook(); // Save before closing
                workbook.close();
                Log.d(TAG, "✅ Workbook closed successfully");
            }
        } catch (IOException e) {
            Log.e(TAG, "❌ Error closing workbook: " + e.getMessage());
        }
    }
}