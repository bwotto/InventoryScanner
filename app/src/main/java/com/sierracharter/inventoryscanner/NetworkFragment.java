package com.sierracharter.inventoryscanner;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;

import jcifs.CIFSContext;
import jcifs.context.SingletonContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

public class NetworkFragment extends Fragment {
    private static final String TAG = "NetworkFragment";

    private static DownloadCallback mCallback;
    private static NetworkFragment mNetworkFragment;

    /**
     * Static initializer for NetworkFragment that sets the URL of the host it will be downloading
     * from.
     */

    static NetworkFragment getInstance(FragmentManager supportFragmentManager) {
        if (mNetworkFragment == null) {
            mNetworkFragment = new NetworkFragment();
            supportFragmentManager.beginTransaction().add(mNetworkFragment, TAG).commit();
        }

        return mNetworkFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if(context instanceof MainActivity) {
            mCallback = (DownloadCallback) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        // Clear reference to host Activity to avoid memory leak.
        mCallback = null;
    }

    @Override
    public void onDestroy() {
        // Cancel task when Fragment is destroyed.
        super.onDestroy();
    }

    void updateSheet(String mRoomNumber, String assetNumber, String filename, String domain, String username, String password) {
        Log.e("BENJI", "start update sheet");
        DownloadTask downloadTask = new DownloadTask();
        downloadTask.execute(mRoomNumber, assetNumber, filename, domain, username, password);
    }

    private static class DownloadTask extends AsyncTask<String, Void, String> {

        private boolean mRunning;

        @Override
        protected void onPreExecute() {
            Log.d("BENJI", "pre execute");
            if (!mRunning) {
                mRunning = true;
            }

            if(mCallback != null) {
                mCallback.onStartedSheetUpdate(null);
            }
        }

        @Override
        protected String doInBackground(String... strings) {
            Log.d("BENJI", "do in background");
            String roomNumber = strings[0];
            String assetNumber = strings[1];
            String filename = strings[2];
            String domain = strings[3];
            String username = strings[4];
            String password = strings[5];

            return handleupdate(roomNumber, assetNumber, filename, domain, username, password);
        }

        @Override
        protected void onPostExecute(String result) {
            mRunning = false;
            if(mCallback != null) {
                mCallback.onFinishedSheetUpdate(result);
            }
        }

        private String handleupdate(String roomNumber, String assetNumber, String filename, String domain, String username, String password) {

            CIFSContext baseContext = SingletonContext.getInstance();
            CIFSContext context = baseContext.withCredentials(new NtlmPasswordAuthenticator(domain, username, password));

            String message = "";

            try (SmbFile file = new SmbFile(filename, context)) {

                BufferedInputStream in = new BufferedInputStream(file.getInputStream());

                try (Workbook workbook = new HSSFWorkbook(in)) {
                    Log.d("BENJI", "Workbook created");

                    Sheet sheet = workbook.getSheetAt(0);

                    for (Row row : sheet) {
                        Cell cell = row.getCell(0, Row.RETURN_NULL_AND_BLANK);

                        if (cell.getCellType() == Cell.CELL_TYPE_NUMERIC) {
                            double assetNumberInSheet = cell.getNumericCellValue();
                            double assetNumberFromScan = Double.parseDouble(assetNumber);

                            //We found an matching asset number in the sheet.
                            if (assetNumberFromScan == assetNumberInSheet) {
                                Log.d("BENJI", "Found matching asset number in sheet");
                                Cell cellRoom = row.getCell(2, Row.RETURN_NULL_AND_BLANK);
                                String roomNameInSheet = cellRoom.getStringCellValue();
                                //We scanned from the room number we are in.
                                CellStyle style = workbook.createCellStyle();
                                style.cloneStyleFrom(cell.getCellStyle());
                                style.setFillPattern(CellStyle.SOLID_FOREGROUND);

                                if (roomNumber.toUpperCase().equals(roomNameInSheet.toUpperCase())) {
                                    Log.d("BENJI", "We found the scanned item");
                                    style.setFillForegroundColor(IndexedColors.GREEN.getIndex());
                                }
                                //The item we scanned must be out of place.
                                else {
                                    Log.d("BENJI", "The item we scanned is out of place");
                                    style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
                                }
                                //Set the new cell color.
                                cell.setCellStyle(style);

                                //Time to save the sheet.
                                BufferedOutputStream out = new BufferedOutputStream(file.getOutputStream());
                                workbook.write(out);
                                Log.d("BENJI", "Workbook written");
                                workbook.close();
                                out.close();
                                in.close();
                                file.close();
                                break;
                            }
                        }
                    }
                }catch(IOException e){
                    e.printStackTrace();
                }
            }catch(MalformedURLException e){
                message = "Wrong filename." + " malformed url exception";
            }catch(SmbException e){
                switch(e.getNtStatus()) {
                    case SmbException.ERROR_ACCESS_DENIED:
                    case SmbException.NT_STATUS_ACCESS_DENIED:
                    case SmbException.NT_STATUS_CONNECTION_REFUSED:
                        message = "Connection refused";
                        break;
                    case SmbException.NT_STATUS_FILE_IS_A_DIRECTORY:
                        message = "Not a file";
                        break;
                    case SmbException.NT_STATUS_NOT_FOUND:
                        message = "File not found";
                        break;
                    case SmbException.NT_STATUS_WRONG_PASSWORD:
                        message = "Wrong password";
                        break;
                    case SmbException.NT_STATUS_NO_SUCH_DOMAIN:
                        message = "No such domain";
                        break;
                    case SmbException.NT_STATUS_UNSUCCESSFUL:
                        message = "Unsuccessful network connection" + " smb exception" + "Are you connected to WIFI?";
                        break;
                    default:
                        message = e.getMessage() + " smb exception" + e.getNtStatus();
                }
            }
            catch(IOException e){
                message = e.getMessage() + " Could not create data stream";
            }

            return message;
        }
    }
}