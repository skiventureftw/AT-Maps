package com.example.capstone11;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.os.*;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

/**
 * @author Luke Winters winterlt@sunyit.edu
 * created Spring 2017, Suny Polytechnic Institute
 */
public class MainActivity extends AppCompatActivity {
    private static final String FOLDER_PATH = "/storage/emulated/0/Maps/";
    private static final int    GPS_CODE = 10,
                                NETWORK_CODE = 11,
                                FILE_CODE = 12;

    private Button bGPS, bNetwork, bOpen, bOK;
    private TextView t;
    private EditText txtLatitude, txtLongitude;
    private Spinner spinner;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private GeoDatabaseHandler gdbHandler;
    private boolean listenerRunning, haveQuad, queryExecuted;
    private Intent openIntent1, openIntent2;
    final static String MW = "Mount Washington", HF= "Harpers Ferry", MK = "Katahdin", SM = "Springer";
    Uri uri;
    File quad;
    String[] queryResult;

    /**
     * Class initialises layout of app and creates a new GeoDatabaseHandler
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initActivityScreenOrientPortrait();
        listenerRunning = false;
        haveQuad=false;
        bGPS = (Button) findViewById(R.id.bGPS);
        bNetwork = (Button) findViewById(R.id.bNetwork);
        bOpen = (Button) findViewById(R.id.bOpen);
        t = (TextView) findViewById(R.id.t);
        txtLatitude = (EditText) findViewById(R.id.txtLatitude);
        txtLongitude = (EditText) findViewById(R.id.txtLongitude);
        spinner = (Spinner) findViewById(R.id.spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.options_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        try{
            gdbHandler = new GeoDatabaseHandler(this);
        } catch (IOException e){
            e.printStackTrace();
        }
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                t.append("\nAccuracy: "+location.getAccuracy()+" meters\nFinding more accurate location...");
                if(location.getAccuracy()<100) {
                    String latitude = (location.getLatitude() + "");
                    String longitude = (location.getLongitude() + "");
                    locationManager.removeUpdates(this);
                    bNetwork.setText("Find Network");
                    bGPS.setText("Find GPS");
                    listenerRunning = false;
                    runPointInPolygon(latitude, longitude);
                }
            }
            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {}
            @Override
            public void onProviderEnabled(String s) {}
            @Override
            public void onProviderDisabled(String s) {
                //opens location settings if location is off
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        };
        configureGPS();
        configureNetwork();
        configureOpen();
        configureOK();
        configureSpinner();

    }//end of onCreate


    @Override
    public void onDestroy(){
        super.onDestroy();
        gdbHandler.cleanup();
    }

    /**
     * Method to handle permission requests. Calls respective onClick method
     * @param requestCode case code, uses constants
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case GPS_CODE:
                bGPS.callOnClick();
                break;
            case NETWORK_CODE:
                bNetwork.callOnClick();
                break;
            case FILE_CODE:
                bOpen.callOnClick();
                break;
            default:
                break;
        }
    }
    /**
     * method to check if the app has permissions
     *
     * @param  code uses constants, initialised at start of Class
     * @return returns true if permissions are granted, false otherwise.
     */
    boolean hasPermission (int code){
        switch (code){
            case GPS_CODE:
                if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.INTERNET}, GPS_CODE);
                    }
                    return false;
                }
                else return true;
            case NETWORK_CODE:
                if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.INTERNET}, NETWORK_CODE);
                    }
                    return false;
                }
                else return true;
            case FILE_CODE:
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, FILE_CODE);
                    }
                    return false;
                }else return true;
            default:
                return false;
        }
    }

    /**
     * Method to find which USGS quad a given point is in
     *
     * This method takes a point (lat/long) and calls GeoDatabaseHandler.queryPointInPolygon. It has added checks,
     * haveQuad and queryExecuted, which are used when a user tries to open a map. It writes the result of the query
     * to the TextView. Additionally, it finds the associated map and stores it in File quads (global). If it cannot
     * find a file matching the one in the database, haveQuad is kept at false.
     *
     * @param runLat latitude to be used
     * @param runLong longitude to be used
     */
    public void runPointInPolygon(String runLat, String runLong){
        queryResult = new String[4];
        haveQuad=false;
        queryExecuted=false;
        queryResult = gdbHandler.queryPointInPolygon(runLong, runLat);
        //t.setText("\n"+queryResult[0]);
        t.setText("\nLocation: " + runLat +", "+ runLong);
        txtLatitude.setText(runLat);
        txtLongitude.setText(runLong);
        t.append("\nRunning Query...\n\n");
        t.append("Cell Name: "+queryResult[1]+"\nState: "+queryResult[2]);
        t.append("\n\nDone");
        File dir = new File(FOLDER_PATH);
        File[] foundQuad = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(queryResult[3]);
            }
        });
         for(File file:foundQuad){
            quad = file;
             t.append("\n\n"+quad);
             haveQuad=true;
        }
        queryExecuted=true;
    }

    /**
     * Sets onClickListener, On click Checks permissions, finds location with GPS, calls runPointInPolygon
     */
    void configureGPS() {
        bGPS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!hasPermission(GPS_CODE))
                    return;
                if(!listenerRunning) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, locationListener);
                    t.setText("Please wait, finding location...");
                    listenerRunning = true;
                    bGPS.setText("Stop Location");
                    spinner.setSelection(((ArrayAdapter)spinner.getAdapter()).getPosition("Examples"));
                }else{
                    locationManager.removeUpdates(locationListener);
                    listenerRunning=false;
                    bGPS.setText("Find GPS");
                    bNetwork.setText("Find Network");
                    if(t.getText().toString().contains("Please wait,"))
                        t.setText("");
                }
            }
        });
    }
    /**
     * Sets onClickListener, On click Checks permissions, finds location with network, calls runPointInPolygon
     */
    void configureNetwork() {
        bNetwork.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!hasPermission(NETWORK_CODE))
                    return;
                if(!listenerRunning) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 500, 0, locationListener);
                    t.setText("Please wait, finding location...");
                    listenerRunning = true;
                    bNetwork.setText("Stop Location");
                    spinner.setSelection(((ArrayAdapter)spinner.getAdapter()).getPosition("Examples"));
                }else{
                    locationManager.removeUpdates(locationListener);
                    listenerRunning=false;
                    bNetwork.setText("Find Network");
                    bGPS.setText("Find GPS");
                    if(t.getText().toString().contains("Please wait,"))
                        t.setText("");
                }
            }
        });
    }

    /**
     * Sets onClickListener, on click checks for permissions, calls runPointInPolygon with user-inputted location
     */
    void configureOK(){
        bOK = (Button) findViewById(R.id.bOK);
        bOK.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                if(TextUtils.isEmpty(txtLatitude.getText().toString()) || TextUtils.isEmpty(txtLongitude.getText().toString())){
                    showToast(1);
                }else
                runPointInPolygon(txtLatitude.getText().toString(), txtLongitude.getText().toString());
            }
        });
    }
    void configureOpen(){
        bOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!hasPermission(FILE_CODE))
                    return;
                openFile();
            }
        });
    }

    /**
     * sets up spinner to have example locations, sets text of editText to the location.
     */
    void configureSpinner(){
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch(parent.getItemAtPosition(position).toString()){
                    case MW:
                        txtLatitude.setText("44.270659", TextView.BufferType.EDITABLE);
                        txtLongitude.setText("-71.302659", TextView.BufferType.EDITABLE);
                        break;
                    case MK:
                        txtLatitude.setText("45.904437", TextView.BufferType.EDITABLE);
                        txtLongitude.setText("-68.921999", TextView.BufferType.EDITABLE);
                        break;
                    case HF:
                        txtLatitude.setText("39.323504", TextView.BufferType.EDITABLE);
                        txtLongitude.setText("-77.728701", TextView.BufferType.EDITABLE);
                        break;
                    case SM:
                        txtLatitude.setText("34.626888", TextView.BufferType.EDITABLE);
                        txtLongitude.setText("-84.193932", TextView.BufferType.EDITABLE);
                        break;
                    default:
                        break;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    /**
     * Displays error message toasts
     * @param toastNo case number
     */
    void showToast(int toastNo){
        Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.TOP| Gravity.START, 0,0);
        switch(toastNo){
            case 1:
                toast.makeText(MainActivity.this, "Please enter a location first.", toast.LENGTH_SHORT).show();
                break;
            case 2:
                toast.makeText(MainActivity.this, "Please find or enter a location first.", toast.LENGTH_SHORT).show();
                break;
            case 3:
                toast.makeText(MainActivity.this, "You don't seem to have that map downloaded.", toast.LENGTH_LONG).show();
            default:
                break;
        }
    }

    /**
     * Opens a PDF with app of user's choice, if it the query has executed and the file exists.
     */
    void openFile(){
        if(!queryExecuted) {
            showToast(2);
            return;
        }
        if (!haveQuad){
            showToast(3);
            return;
        }
        openIntent1 = new Intent(Intent.ACTION_VIEW);
        openIntent1.setDataAndType(uri, "application/pdf");
        openIntent1.setDataAndType(Uri.fromFile(quad), "application/pdf");
        openIntent2 = openIntent1.createChooser(openIntent1, "Open With");
        startActivity(openIntent2);
    }

    /**
     * Method to keep activity in portrait mode
     * Adapter by Luke Winters
     * Written by Erwinus
     * http://stackoverflow.com/questions/3723823/i-want-my-android-application-to-be-only-run-in-portrait-mode
     */
    private void initActivityScreenOrientPortrait()
    {
        DisplayMetrics metrics = new DisplayMetrics();
        this.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        // Test if it is VISUAL in portrait mode by simply checking it's size
        boolean bIsVisualPortrait = ( metrics.heightPixels >= metrics.widthPixels );
        if( !bIsVisualPortrait )
        {
            // Swap the orientation to match the VISUAL portrait mode
            if( this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT )
            { this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE); }
            else { this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ); }
        }
        else { this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR); }

    }
}//end of MainActivity