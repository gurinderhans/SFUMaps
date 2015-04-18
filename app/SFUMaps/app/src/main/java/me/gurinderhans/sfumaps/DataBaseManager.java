package me.gurinderhans.sfumaps;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by ghans on 1/24/15.
 */
public class DataBaseManager extends SQLiteOpenHelper {

    // TODO: class requires more work - [under construction]

    public static final String TAG = DataBaseManager.class.getSimpleName();

    public static final String DATABASE_NAME = "WIFI_DATA";
    public static final int DATABASE_VERSION = 1;
    public static final String ASSETS_DATABASE_PATH = "databases/" + DATABASE_NAME;

    private boolean createDb = false, upgradeDb = false;

    Context context;

    /**
     * @param ctx - application context
     */
    public DataBaseManager(Context ctx) {
        super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = ctx;
    }


    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        createDb = true;
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        Log.i(TAG, "Old version: " + oldVersion + " New version: " + newVersion);
        upgradeDb = true;
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);

        if (createDb) {
            createDb = false;
            copyDatabaseFromAssets(db);
        }

        if (upgradeDb) {
            upgradeDb = false;
//            copyDatabaseFromAssets(db);
        }
    }


    /**
     * Copies the database file stored in assets folder to the
     * application database location
     *
     * @param db - application database that we copy our database contents to
     */
    private void copyDatabaseFromAssets(SQLiteDatabase db) {

        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            // Open db packaged as asset as the input stream
            inputStream = context.getAssets().open(ASSETS_DATABASE_PATH);

            // Open the db in the application package context:
            outputStream = new FileOutputStream(db.getPath());

            // Transfer db file contents:
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.flush();

            // Set the version of the copied database to the current version
            SQLiteDatabase copiedDb = context.openOrCreateDatabase(
                    DATABASE_NAME, 0, null);
            copiedDb.execSQL("PRAGMA user_version = " + DATABASE_VERSION);
            copiedDb.close();

        } catch (IOException e) {
            e.printStackTrace();
            Log.i(TAG, "Error copying database");
        } finally {
            try { // Close the streams
                if (outputStream != null)
                    outputStream.close();

                if (inputStream != null)
                    inputStream.close();

            } catch (IOException e) {
                e.printStackTrace();
                Log.i(TAG, "Error closing streams");
            }
        }
    }

    /**
     * @return - list of all database tables
     */
    ArrayList<String> getTableNames() {

        SQLiteDatabase db = getReadableDatabase();
        ArrayList<String> tables = new ArrayList<>();

        Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);

        if (cursor.moveToFirst()) {
            do tables.add(cursor.getString(0));
            while (cursor.moveToNext() && !(cursor.getString(0).equals("android_metadata")));
        } else Log.i("ERROR", "Unable to move cursor!");

        cursor.close();
        db.close();

        return tables;
    }

    /**
     * @param tablename - the name of the table that we want the data from
     * @return - the table data
     */
    ArrayList<HashMap<String, Object>> getTableData(String tablename) {
        SQLiteDatabase db = getReadableDatabase();

        ArrayList<HashMap<String, Object>> data = new ArrayList<>();

        String GET_TABLE_DATA_QUERY = "SELECT * FROM " + tablename;

        Cursor cursor = db.rawQuery(GET_TABLE_DATA_QUERY, null);

        if (cursor.moveToFirst()) {
            do {
                HashMap<String, Object> tableRow = new HashMap<>();
                tableRow.put(Keys.KEY_ROWID, cursor.getString(0));
                tableRow.put(Keys.KEY_SSID, cursor.getString(1));
                tableRow.put(Keys.KEY_BSSID, cursor.getString(2));
                tableRow.put(Keys.KEY_FREQ, cursor.getString(3));
                tableRow.put(Keys.KEY_RSSI, cursor.getString(4));
                tableRow.put(Keys.KEY_TIME, cursor.getString(5));

                data.add(tableRow);

            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return data;
    }


    /*
     * get campus hierarchy
     */
    /*void getHierarchy() {

        SQLiteDatabase db = getReadableDatabase();

        Tree<HashMap<Integer, String>> t;

        String GET_HIERARCHY_QUERY = "SELECT * FROM hierarchy";
        Cursor cursor = db.rawQuery(GET_HIERARCHY_QUERY, null);

        if (cursor.moveToFirst()) {

            Log.i(TAG, "id: " + cursor.getInt(0));
            int id = cursor.getInt(0);
            int parentNode = cursor.getInt(2);
            String selfName = cursor.getString(1);
            int selfId = cursor.getInt(3);
            String value = cursor.getString(4);

            final int tmpId = id;
            final String tmpName = selfName;

            t= new Tree<HashMap<Integer, String>>(new HashMap<Integer, String>(){{
                put(tmpId, tmpName);
            }});

            Log.i(TAG, t.toString());

            // we read the first value manually so we need
            // to move cursor so we don't read the first row again
            cursor.moveToPosition(1);

            do {

                id = cursor.getInt(0);
                parentNode = cursor.getInt(2);
                selfName = cursor.getString(1);
                selfId = cursor.getInt(3);
                value = cursor.getString(4);

                // traverse through the tree and find the parent node
//                t.addLeaf(parent, this);

//                Log.i(TAG, "id: " + id);


            } while (cursor.moveToNext());
        }

//        Log.i(TAG, t.toString());

        cursor.close();
        db.close();
    }*/

}