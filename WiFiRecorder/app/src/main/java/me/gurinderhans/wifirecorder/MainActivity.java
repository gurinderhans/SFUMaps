package me.gurinderhans.wifirecorder;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;


public class MainActivity extends Activity {

    public static final String KEY_SSID_SEND = "wifiSSID";
    private final String TAG = getClass().getSimpleName();
    boolean record;

    SimpleAdapter mSimpleAdapter;
    WifiManager service_WifiManager;
    WifiReceiver wifiReceiver;
    WiFiDatabaseManager mWiFiDatabaseManager;
    ArrayList<HashMap<String, String>> mSortedAPsList;
    Handler mHandler;
    Runnable scanner;
    Context context;
    String tableName;
    EditText recordDataTableName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //TODO: try merging OneWifiNetwork Activity into this - figure out a way to simulate back button

        getActionBar().setTitle("Recorder");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) getActionBar().setElevation(0);

        recordDataTableName = (EditText) findViewById(R.id.allAPsTableName);
        ListView lv_allWifiAPs = (ListView) findViewById(R.id.allWifiAPs_lv);

        context = getApplicationContext();
        mHandler = new Handler();
        mSortedAPsList = new ArrayList<>();
        wifiReceiver = new WifiReceiver();

        service_WifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mWiFiDatabaseManager = new WiFiDatabaseManager(context);

        //set things in place and display networks
        int[] ids = {R.id.ssid, R.id.bssid, R.id.freq, R.id.level};
        String[] keys = {WiFiDatabaseManager.KEY_SSID, WiFiDatabaseManager.KEY_BSSID, WiFiDatabaseManager.KEY_FREQ, WiFiDatabaseManager.KEY_RSSI};

        mSimpleAdapter = new SimpleAdapter(context, mSortedAPsList, R.layout.lv_item_wifiap, keys, ids);

        lv_allWifiAPs.setAdapter(mSimpleAdapter);

        lv_allWifiAPs.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                record = false;
                String ssid = ((TextView) view.findViewById(R.id.ssid)).getText().toString();
                Intent startoneWifiActivity = new Intent(MainActivity.this, OneWifiNetwork.class);
                startoneWifiActivity.putExtra(KEY_SSID_SEND, ssid);
                startActivity(startoneWifiActivity);
            }
        });

        scanner = new Runnable() {
            @Override
            public void run() {
                service_WifiManager.startScan();
            }
        };

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_record) {
            tableName = recordDataTableName.getText().toString();
            record = true;
            recordDataTableName.setEnabled(false);
        }
        if (id == R.id.action_record_stop) {
            record = false;
            recordDataTableName.setEnabled(true);
            recordDataTableName.setText("");
        }

        if (id == R.id.exportdb) {
            File file = new File(Environment.getExternalStorageDirectory() + WiFiDatabaseManager.DBPATH + WiFiDatabaseManager.DATABASE_NAME);
            boolean deleted = file.delete();

            Uri toShare = Uri.EMPTY;
            try {
                toShare = Uri.fromFile(mWiFiDatabaseManager.exportDB());
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (toShare != Uri.EMPTY && deleted) {
                Intent intent = new Intent(android.content.Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_STREAM, toShare);
                startActivity(Intent.createChooser(intent, ""));
            }
        }

        if (id == R.id.view_tables) {
            startActivity(new Intent(MainActivity.this, TablesListActivity.class));
        }
        if (id == R.id.custom_wifi_scan) {
            startActivity(new Intent(MainActivity.this, CustomWiFiScanActivity.class));
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        showData(service_WifiManager.getScanResults());
        mHandler.postDelayed(scanner, 0);
    }

    @Override
    public void onPause() {
        super.onPause();
        record = false;
        unregisterReceiver(wifiReceiver);
    }

    public void showData(List<ScanResult> wifiAPs) {
        mSortedAPsList.clear();

        //sort wifi results by rssi value
        Collections.sort(wifiAPs, new Comparator<ScanResult>() {
            @Override
            public int compare(ScanResult lhs, ScanResult rhs) {
                return (rhs.level < lhs.level ? -1 : (lhs.level == rhs.level ? 0 : 1));
            }
        });

        for (ScanResult result : wifiAPs) {
            HashMap<String, String> ap = new HashMap<>();
            ap.put(WiFiDatabaseManager.KEY_SSID, result.SSID);
            ap.put(WiFiDatabaseManager.KEY_BSSID, result.BSSID);
            ap.put(WiFiDatabaseManager.KEY_FREQ, result.frequency + " MHz");
            ap.put(WiFiDatabaseManager.KEY_RSSI, result.level + "");

            String rec_time = System.currentTimeMillis() + "";

            if (record)
                mWiFiDatabaseManager.addApData(result.SSID, result.BSSID, result.frequency + "", result.level + "", rec_time, tableName);

            mSortedAPsList.add(ap);
        }

        mSimpleAdapter.notifyDataSetChanged();
    }

    private class WifiReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent intent) {
            showData(service_WifiManager.getScanResults());
            mHandler.postDelayed(scanner, 0);
        }
    }
}
