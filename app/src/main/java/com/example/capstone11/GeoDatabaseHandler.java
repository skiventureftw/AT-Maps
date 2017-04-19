package com.example.capstone11;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import jsqlite.*;

/**
 * Adapted by Luke Winters
 * @author kristina on 9/2/15.
 */
public class GeoDatabaseHandler {

    private static final String TAG = "GEODBH";
    private static final String TAG_SL = TAG + "_JSQLITE";
    //db column name for cell name and state name
    private static final String CELL_NAME = "all_stat_3";
    private static final String STATE_NAME = "all_stat_4";

    //default android path to app database internal storage
    private static String DB_PATH = "/data/data/com.example.capstone11/databases";

    //see below for explanation of SRID constants and source of database
    //https://github.com/kristina-hager/spatialite-tools-docker
    //the name of the db, also in res/raw
    private static String DB_NAME = "quads.sqlite";

    //constants related to source database and GPS SRID
    private static final int GPS_SRID = 4326;
    private static final int SOURCE_DATA_SRID = 4326;

    private Database spatialiteDb;


    public GeoDatabaseHandler(Context context) throws IOException {

        File cacheDatabase = new File(DB_PATH, DB_NAME);
        if (!cacheDatabase.getParentFile().exists()) {
            File dirDb = cacheDatabase.getParentFile();
            Log.i(TAG,"making directory: " + cacheDatabase.getParentFile());
            if (!dirDb.mkdir()) {
                throw new IOException(TAG_SL + "Could not create dirDb: " + dirDb.getAbsolutePath());
            }
        }

        //can only read data from raw or assets, so need to copy database to an internal file for further work
        //source: http://stackoverflow.com/questions/513084/how-to-ship-an-android-application-with-a-database
        InputStream inputStream = context.getResources().openRawResource(R.raw.quads);
        copyDatabase(inputStream, DB_PATH + File.separator + DB_NAME);

        spatialiteDb = new Database();
        try {
            spatialiteDb.open(cacheDatabase.getAbsolutePath(),
                    jsqlite.Constants.SQLITE_OPEN_READWRITE | jsqlite.Constants.SQLITE_OPEN_CREATE);
        } catch (jsqlite.Exception e) {
            Log.e(TAG_SL,e.getMessage());
        }

    }


    public void cleanup() {
        try {
            spatialiteDb.close();
        } catch (jsqlite.Exception e) {
            e.printStackTrace();
        }
    }

    private void copyDatabase(InputStream inputStream, String dbFilename) throws IOException {

        OutputStream outputStream = new FileOutputStream(dbFilename);

        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer,0,length);
        }

        outputStream.flush();
        outputStream.close();
        inputStream.close();
        Log.i(TAG,"Copied database to " + dbFilename);
    }


    public String[] queryPointInPolygon(String lat, String lon) {

        String gpsPoint = "POINT(" + lat +" " + lon + ")";
        String query = "select " + CELL_NAME + ", " + STATE_NAME +", filestart from Quad_index_clean where within"
               + "(GeomFromText('"  + gpsPoint + "'), Geometry);";

        String cellName = "nothing";
        String state = "nothing";
        String fileStart = "nothing";
        String[] returnString;
        StringBuilder stringBuilder = new StringBuilder();
        //stringBuilder.append("issue point in polygon query on " + gpsPoint + " ..\n");
        //stringBuilder.append("Execute query: ").append(query).append("\n\n");

        try {
            Stmt stmt = spatialiteDb.prepare(query);

            Log.i(TAG, "result column count: " + stmt.column_count());


            int maxColumns = stmt.column_count();

            for (int i = 0; i < maxColumns; i++) {
                stringBuilder.append(stmt.column_name(i)).append(" | ");
            }
            stringBuilder.append("\n--------------------------------------------\n");


            int rowIndex = 0;
            while (stmt.step()) {
                stringBuilder.append("\t");
                for (int i = 0; i < maxColumns; i++) {
                    if(cellName.equals("nothing"))
                        cellName = stmt.column_string(i);
                    else if (state.equals("nothing"))
                        state = stmt.column_string(i);
                    else fileStart = stmt.column_string(i);
                    stringBuilder.append(stmt.column_string(i)).append(" | ");
                }
                stringBuilder.append("\n");

                if (rowIndex++ > 10) break;
            }
            stringBuilder.append("\t...");
            stmt.close();
        } catch (jsqlite.Exception e) {
            Log.e(TAG_SL,e.getMessage());
        }

        stringBuilder.append("\ndone\n");

        returnString = new String[]{stringBuilder.toString(), cellName, state, fileStart};
        return returnString;
    }
}