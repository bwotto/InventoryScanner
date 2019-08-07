package com.sierracharter.inventoryscanner;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;

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
import java.util.HashSet;
import java.util.Set;

public class NetworkFragment extends Fragment {
    public static final String TAG = "NetworkFragment";

    private static String mRoomNumber = "";
    private static String mAssetNumber = "";

    private DownloadCallback mCallback;
    private DownloadTask downloadTask;
    private static NetworkFragment mNetworkFragment;

    /**
     * Static initializer for NetworkFragment that sets the URL of the host it will be downloading
     * from.
     */

    public static NetworkFragment getInstance(FragmentManager supportFragmentManager) {
        if(mNetworkFragment == null){
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
        mCallback = (DownloadCallback) context;
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

    public void updateSheet(String mRoomNumber, String assetNumber, String host, String share, String filename, String domain, String username, String password) {
        Log.e("BENJI", "start update sheet");
        downloadTask = new DownloadTask();
        downloadTask.execute(mRoomNumber, assetNumber, host, share, filename, domain, username, password);
    }

    private class DownloadTask extends AsyncTask<String, Void, Exception>{

        private boolean mRunning;

        @Override
        protected void onPreExecute() {
            Log.d("BENJI", "pre execute");
            if(!mRunning){
                mRunning = true;
            }
            mCallback.onStartedSheetUpdate(null);
        }

        @Override
        protected Exception doInBackground(String... strings) {
            Log.d("BENJI", "do in background");
            String roomNumber = strings[0];
            String assetNumber = strings[1];
            String host = strings[2];
            String share = strings[3];
            String filename = strings[4];
            String domain = strings[5];
            String username = strings[6];
            String password = strings[7];

            Exception e = handleupdate(roomNumber, assetNumber, host, share, filename, domain, username, password);

            return e;
        }

        @Override
        protected void onPostExecute(Exception e) {
            Log.d("BENJI", "On post execute");
            mRunning = false;
            mCallback.onFinishedSheetUpdate(e);
        }

        private Exception handleupdate(String roomNumber, String assetNumber, String host, String share, String filename, String domain, String username, String password){
            SMBClient client = new SMBClient();
            Log.d("BENJI", "created smb client");
            Exception exception = null;

            try {

                Connection connection = client.connect(host);
                Session session = connection.authenticate(new AuthenticationContext(username, password.toCharArray(), domain));

                DiskShare diskShare = (DiskShare)session.connectShare(share);
                Log.d("BENJI", "accessed disk share");

                Set<AccessMask> accessMasks = new HashSet<>();
                accessMasks.add(AccessMask.MAXIMUM_ALLOWED);

                Set<FileAttributes> attributes = new HashSet<>();
                attributes.add(FileAttributes.FILE_ATTRIBUTE_NORMAL);

                Set<SMB2ShareAccess> shareAccesses = new HashSet<>();
                shareAccesses.add(SMB2ShareAccess.FILE_SHARE_READ);
                shareAccesses.add(SMB2ShareAccess.FILE_SHARE_WRITE);
                SMB2CreateDisposition createDisposition = SMB2CreateDisposition.FILE_OPEN;

                File file = diskShare.openFile(filename, accessMasks, attributes, shareAccesses, createDisposition, null);

                BufferedInputStream in = new BufferedInputStream(file.getInputStream());
                Log.d("BENJI", "Buffered input stream opened");

                Workbook workbook = new HSSFWorkbook(in);
                Log.d("BENJI", "Workbook created");

                Sheet sheet = workbook.getSheetAt(0);

                for(Row row : sheet){
                    Cell cell = row.getCell(0, Row.RETURN_NULL_AND_BLANK);

                    if(cell.getCellType() == Cell.CELL_TYPE_NUMERIC){
                        double assetNumberInSheet = cell.getNumericCellValue();
                        double assetNumberFromScan =  Double.parseDouble(assetNumber);

                        //We found an matching asset number in the sheet.
                        if(assetNumberFromScan == assetNumberInSheet){
                            Log.d("BENJI", "Found matching asset number in sheet");
                            Cell cellRoom = row.getCell(2, Row.RETURN_NULL_AND_BLANK);
                            String roomNameInSheet = cellRoom.getStringCellValue();
                            //We scanned from the room number we are in.
                            CellStyle style = workbook.createCellStyle();
                            style.cloneStyleFrom(cell.getCellStyle());
                            style.setFillPattern(CellStyle.SOLID_FOREGROUND);

                            if(roomNumber.toUpperCase().equals(roomNameInSheet.toUpperCase())){
                                Log.d("BENJI", "We found the scanned item");
                                style.setFillForegroundColor(IndexedColors.GREEN.getIndex());
                            }
                            //The item we scanned must be out of place.
                            else{
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

            }catch(Exception e){
                exception = e;
                if(e != null) {
                    Log.e("BENJI", e.getMessage());
                }
            }

            return exception;
        }
    }
}