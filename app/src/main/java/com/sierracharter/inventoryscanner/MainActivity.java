package com.sierracharter.inventoryscanner;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.sierracharter.smbfilechooser.FileChooserActivity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ProgressBar;


public class MainActivity extends AppCompatActivity implements DownloadCallback{

    private static final String TAG = "MainActivity";

    private String mRoomNumber = "";
    private NetworkFragment networkFragment;
    private String filePathToSheet = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if(savedInstanceState != null){
            mRoomNumber = savedInstanceState.getString("room_number");
            filePathToSheet = savedInstanceState.getString("url");
            ProgressBar progressBar = findViewById(R.id.progressBar);
            if(savedInstanceState.getBoolean("in_progress")){
                progressBar.setVisibility(View.VISIBLE);
            }
        }

        networkFragment = NetworkFragment.getInstance(getSupportFragmentManager());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("room_number", mRoomNumber);
        super.onSaveInstanceState(outState);

        ProgressBar progressBar = findViewById(R.id.progressBar);
        boolean isVisible = progressBar.getVisibility() == View.VISIBLE;
        outState.putBoolean("in_progress", isVisible);

        outState.putString("url", filePathToSheet);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onScanButtonPressed(View view){
        IntentIntegrator intentIntegrator = new IntentIntegrator(this);
        intentIntegrator.setOrientationLocked(false);
        intentIntegrator.initiateScan();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(data == null){
            return;
        }

        //Process result of file choice.
        if(resultCode == RESULT_OK && requestCode == FileChooserActivity.ACTION_PICK_FILE){
            filePathToSheet = data.getStringExtra(FileChooserActivity.INTENT_RESULT);
            Log.d(TAG, "chosen file: " + filePathToSheet);
        }

        //Process result of scan.
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        if(result != null){
            String scanContents = result.getContents();

            if(!scanContents.equals("")){

                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

//                String host = preferences.getString("host", "");
//                String share = preferences.getString("share", "");
//                String filePath = preferences.getString("file_path", "");
                String domain = preferences.getString("domain", "");
                String username = preferences.getString("username", "");
                String password = preferences.getString("password", "");

                //The contents is a number.
                if(scanContents.matches("[0-9]+")){
                    if(mRoomNumber.equals("")){
                        showMessage("Scan a room number first");
                    }
                    //Time to update sheet.
                    startUpdateSheet(mRoomNumber, scanContents, filePathToSheet, domain, username, password);
                }

                //The contents is a room number.
                else{
                    mRoomNumber = scanContents.replace("+", "");
                }

            }
        }
    }

    private void startUpdateSheet(String mRoomNumber, String assetNumber,String filename, String domain, String username, String password){
        if(networkFragment != null){
            if(!assetNumber.equals("") && !mRoomNumber.equals("")){
                networkFragment.updateSheet(mRoomNumber, assetNumber, filename, domain, username, password);
            }
        }
    }

    @Override
    public void onStartedSheetUpdate(String result){
        ProgressBar bar = findViewById(R.id.progressBar);
        bar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onFinishedSheetUpdate(String result){
        ProgressBar bar = findViewById(R.id.progressBar);
        bar.setVisibility(View.INVISIBLE);

        if(result != null && !equals("")){
            showMessage(result);
        }
    }

    private void showMessage(String message){
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show();
    }

    public void onChooseFile(View view){
        Intent intent = new Intent(this, FileChooserActivity.class);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String host = preferences.getString("host", "");
        String share = preferences.getString("share", "");

        if(host.equals("") || share.equals("")){
            showMessage("You must set host and share in settings");
            return;
        }

        String fullRootPath = "smb://" + host + "/" + share + "/";
        intent.putExtra(FileChooserActivity.INTENT_ARGS, fullRootPath);
        startActivityForResult(intent, FileChooserActivity.ACTION_PICK_FILE);
    }
}
