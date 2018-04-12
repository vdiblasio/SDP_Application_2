package com.example.tom.sdp_application;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.telephony.SmsManager;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.lang.Thread;
import java.util.Locale;
import java.util.UUID;


public class MainActivity extends AppCompatActivity implements BleManager.BleManagerListener, BleUtils.ResetBluetoothAdapterListener {

    private BleManager mBleManager;
    private boolean mIsScanPaused = true;
    private BleDevicesScanner mScanner;

    private PeripheralList mPeripheralList;

    private ArrayList<BluetoothDeviceData> mScannedDevices;
    private BluetoothDeviceData mSelectedDeviceData;
    private Class<?> mComponentToStartWhenConnected;
    private boolean mShouldEnableWifiOnQuit = false;
    private String mLatestCheckedDeviceAddress;

    //private DataFragment mRetainedDataFragment;

    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;

    private final static String kPreferences = "MainActivity_prefs";
    private final static String kPreferences_filtersPanelOpen = "filtersPanelOpen";

    // Components
    private final static int kComponentsNameIds[] = {
            R.string.scan_connectservice_info,
            R.string.scan_connectservice_uart,
            R.string.scan_connectservice_pinio,
            R.string.scan_connectservice_controller,
            R.string.scan_connectservice_beacon,
            R.string.scan_connectservice_neopixel,
    };

    // Activity request codes (used for onActivityResult)
    private static final int kActivityRequestCode_EnableBluetooth = 1;
    private static final int kActivityRequestCode_Settings = 2;
    private static final int kActivityRequestCode_ConnectedActivity = 3;



    private final static String TAG = MainActivity.class.getSimpleName();
    private final static long kMinDelayToUpdateUI = 200;    // in milliseconds
    private static final String kGenericAttributeService = "00001801-0000-1000-8000-00805F9B34FB";
    private static final String kServiceChangedCharacteristic = "00002A05-0000-1000-8000-00805F9B34FB";
//------------------------UI----------------------//
    //TextView messages;
    //EditText input;
    //Button   send;
    //CheckBox newline;
    private ExpandableHeightExpandableListView mScannedDevicesListView;
    private ExpandableListAdapter mScannedDevicesAdapter;
    private Button mScanButton;
    private long mLastUpdateMillis;
    private TextView mNoDevicesTextView;
    private ScrollView mDevicesScrollView;
    private SwipeRefreshLayout mSwipeRefreshLayout;

    private AlertDialog mConnectingDialog;
    private View mFiltersPanelView;
    private ImageView mFiltersExpandImageView;
    private ImageButton mFiltersClearButton;
    private TextView mFiltersTitleTextView;
    private EditText mFiltersNameEditText;
    private SeekBar mFiltersRssiSeekBar;
    private TextView mFiltersRssiValueTextView;
    private CheckBox mFiltersUnnamedCheckBox;
    private CheckBox mFiltersUartCheckBox;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mScanButton = (Button) findViewById(R.id.scanButton);
        mBleManager = BleManager.getInstance(this);
        mPeripheralList = new PeripheralList();

        mScannedDevicesListView = (ExpandableHeightExpandableListView) findViewById(R.id.scannedDevicesListView);
        mScannedDevicesAdapter = new ExpandableListAdapter();
        mScannedDevicesListView.setAdapter(mScannedDevicesAdapter);
        mScannedDevicesListView.setExpanded(true);

        mScannedDevicesListView.setOnGroupExpandListener(new ExpandableListView.OnGroupExpandListener() {
            @Override
            public void onGroupExpand(int groupPosition) {
            }
        });




        mNoDevicesTextView = (TextView) findViewById(R.id.nodevicesTextView);
        mDevicesScrollView = (ScrollView) findViewById(R.id.devicesScrollView);
        mDevicesScrollView.setVisibility(View.GONE);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefreshLayout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                mScannedDevices.clear();
                startScan(null);

                mSwipeRefreshLayout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                }, 500);
            }
        });


        if (savedInstanceState == null) {
            // Read preferences
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            boolean autoResetBluetoothOnStart = sharedPreferences.getBoolean("pref_resetble", false);
            boolean disableWifi = sharedPreferences.getBoolean("pref_disableWifi", false);
            boolean updatesEnabled = sharedPreferences.getBoolean("pref_updatesenabled", true);

            // Update SoftwareUpdateManager

            // Turn off wifi
            if (disableWifi) {
                final boolean isWifiEnabled = BleUtils.isWifiEnabled(this);
                if (isWifiEnabled) {
                    BleUtils.enableWifi(false, this);
                    mShouldEnableWifiOnQuit = true;
                }
            }

            // Check if bluetooth adapter is available
            final boolean wasBluetoothEnabled = manageBluetoothAvailability();
            final boolean areLocationServicesReadyForScanning = manageLocationServiceAvailabilityForScanning();

            // Reset bluetooth
            if (autoResetBluetoothOnStart && wasBluetoothEnabled && areLocationServicesReadyForScanning) {
                BleUtils.resetBluetoothAdapter(this, this);
            }
        }







        // Request Bluetooth scanning permissions
        requestLocationPermissionIfNeeded();


        //UI
        mScannedDevicesListView = (ExpandableHeightExpandableListView) findViewById(R.id.scannedDevicesListView);
        mScannedDevicesAdapter = new ExpandableListAdapter();


        final SmsManager smsManagger = SmsManager.getDefault();
        //TODO DON'T PUSH WITH A PHONE NUMBER
        final String num = "";

        //Permissions
        if (this.getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (this.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            } else {
                this.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},1);
            }
        } else {
        }

        if (this.getApplicationContext().checkSelfPermission(Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            if (this.shouldShowRequestPermissionRationale(Manifest.permission.SEND_SMS)) {
            } else {
                this.requestPermissions(new String[]{Manifest.permission.SEND_SMS}, 1);
            }
        } else {
        }


        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE Not Supported",
                    Toast.LENGTH_SHORT).show();
            finish();
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        //mBluetoothLE.mBluetoothAdapter = bluetoothManager.getAdapter();

        /*final Button scanButton = findViewById(R.id.scanButton);
        scanButton.setOnClickListener(new View.OnClickListener(){
          public void onClick(View v){
              uart.connectFirstAvailable();
              //smsManagger.sendTextMessage(num, null, "test", null, null);
          }
        });*/

    }

    @Override
    protected void onResume(){
        super.onResume();


        // Set listener
        mBleManager.setBleListener(this);

        // Autostart scan
        autostartScan();

        // Update UI
        updateUI();

        writeLine("Scanning for devices ...");
    }

    protected void onPause(){
        super.onPause();
        /*if (mBluetoothAdapter != null && mBluetoothLE.mBluetoothAdapter.isEnabled()) {
            mBluetoothLE.scanLeDevice(false);
        }*/
    }

    @Override
    protected  void onDestroy(){
        /*if (mBluetoothLE.mGatt == null) {
            return;
        }
        mBluetoothLE.mGatt.close();
        mBluetoothLE.mGatt = null;*/
        super.onDestroy();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if(requestCode == PERMISSION_REQUEST_FINE_LOCATION) {
            if (resultCode == Activity.RESULT_CANCELED) {
                //Bluetooth not enabled
                finish();
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void writeLine(final CharSequence text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //messages.append(text);
                //messages.append("\n");
            }
        });
    }


/*    public void sendClick(View view) {
        StringBuilder stringBuilder = new StringBuilder();
        //String message = input.getText().toString();

        // We can only send 20 bytes per packet, so break longer messages
        // up into 20 byte payloads
        int len = message.length();
        int pos = 0;
        while(len != 0) {
            stringBuilder.setLength(0);
            if (len>=20) {
                stringBuilder.append(message.toCharArray(), pos, 20 );
                len-=20;
                pos+=20;
            }
            else {
                stringBuilder.append(message.toCharArray(), pos, len);
                len = 0;
            }
            //uart.send(stringBuilder.toString());
        }
        // Terminate with a newline character if requests
        *//*newline = (CheckBox) findViewById(R.id.newline);
        if (newline.isChecked()) {
            stringBuilder.setLength(0);
            stringBuilder.append("\n");
            //uart.send(stringBuilder.toString());
        }*//*
    }*/

    private class BluetoothDeviceData {
        BluetoothDevice device;
        public int rssi;
        byte[] scanRecord;
        private String advertisedName;           // Advertised name
        private String cachedNiceName;
        private String cachedName;

        // Decoded scan record (update R.array.scan_devicetypes if this list is modified)
        static final int kType_Unknown = 0;
        static final int kType_Uart = 1;
        static final int kType_Beacon = 2;
        static final int kType_UriBeacon = 3;

        public int type;
        int txPower;
        ArrayList<UUID> uuids;

        String getName() {
            if (cachedName == null) {
                cachedName = device.getName();
                if (cachedName == null) {
                    cachedName = advertisedName;      // Try to get a name (but it seems that if device.getName() is null, this is also null)
                }
            }

            return cachedName;
        }

        String getNiceName() {
            if (cachedNiceName == null) {
                cachedNiceName = getName();
                if (cachedNiceName == null) {
                    cachedNiceName = device.getAddress();
                }
            }

            return cachedNiceName;
        }
    }


    private class ExpandableListAdapter extends BaseExpandableListAdapter {
        // Data
        private ArrayList<BluetoothDeviceData> mFilteredPeripherals;

        private class GroupViewHolder {
            TextView nameTextView;
            TextView descriptionTextView;
            ImageView rssiImageView;
            TextView rssiTextView;
            Button connectButton;
        }

        @Override
        public int getGroupCount() {
            mFilteredPeripherals = mPeripheralList.filteredPeripherals(true);
            return mFilteredPeripherals.size();
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return 1;
        }

        @Override
        public Object getGroup(int groupPosition) {
            return mFilteredPeripherals.get(groupPosition);
        }

        @Override
        public Spanned getChild(int groupPosition, int childPosition) {
            BluetoothDeviceData deviceData = mFilteredPeripherals.get(groupPosition);

            String text;
            switch (deviceData.type) {
                case BluetoothDeviceData.kType_Beacon:
                    text = getChildBeacon(deviceData);
                    break;

                case BluetoothDeviceData.kType_UriBeacon:
                    text = getChildUriBeacon(deviceData);
                    break;

                default:
                    text = getChildCommon(deviceData);
                    break;
            }

            Spanned result;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                result = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY);
            } else {
                result = Html.fromHtml(text);
            }
            return result;
        }


        private String getChildUriBeacon(BluetoothDeviceData deviceData) {
            StringBuilder result = new StringBuilder();

            String name = deviceData.getName();
            if (name != null) {
                result.append(getString(R.string.scan_device_localname)).append(": <b>").append(name).append("</b><br>");
            }

            String address = deviceData.device.getAddress();
            result.append(getString(R.string.scan_device_address) + ": <b>" + (address == null ? "" : address) + "</b><br>");

            String uri = UriBeaconUtils.getUriFromAdvertisingPacket(deviceData.scanRecord) + "</b><br>";
            result.append(getString(R.string.scan_device_uribeacon_uri)).append(": <b>").append(uri);

            result.append(getString(R.string.scan_device_txpower)).append(": <b>").append(deviceData.txPower).append("</b>");

            return result.toString();
        }


        private String getChildCommon(BluetoothDeviceData deviceData) {
            StringBuilder result = new StringBuilder();

            String name = deviceData.getName();
            if (name != null) {
                result.append(getString(R.string.scan_device_localname)).append(": <b>").append(name).append("</b><br>");
            }
            String address = deviceData.device.getAddress();
            result.append(getString(R.string.scan_device_address)).append(": <b>").append(address == null ? "" : address).append("</b><br>");

            StringBuilder serviceText = new StringBuilder();
            if (deviceData.uuids != null) {
                int i = 0;
                for (UUID uuid : deviceData.uuids) {
                    if (i > 0) serviceText.append(", ");
                    serviceText.append(uuid.toString().toUpperCase());
                    i++;
                }
            }
            if (!serviceText.toString().isEmpty()) {
                result.append(getString(R.string.scan_device_services)).append(": <b>").append(serviceText).append("</b><br>");
            }
            result.append(getString(R.string.scan_device_txpower)).append(": <b>").append(deviceData.txPower).append("</b>");

            return result.toString();
        }

        private String getChildBeacon(BluetoothDeviceData deviceData) {
            StringBuilder result = new StringBuilder();

            String name = deviceData.getName();
            if (name != null) {
                result.append(getString(R.string.scan_device_localname)).append(": <b>").append(name).append("</b><br>");
            }
            String address = deviceData.device.getAddress();
            result.append(getString(R.string.scan_device_address)).append(": <b>").append(address == null ? "" : address).append("</b><br>");

            final byte[] manufacturerBytes = {deviceData.scanRecord[6], deviceData.scanRecord[5]};      // Little endian
            String manufacturer = BleUtils.bytesToHex(manufacturerBytes);

            // Check if the manufacturer is known, and replace the id for a name
            String kKnownManufacturers[] = getResources().getStringArray(R.array.beacon_manufacturers_ids);
            int knownIndex = Arrays.asList(kKnownManufacturers).indexOf(manufacturer);
            if (knownIndex >= 0) {
                String kManufacturerNames[] = getResources().getStringArray(R.array.beacon_manufacturers_names);
                manufacturer = kManufacturerNames[knownIndex];
            }

            result.append(getString(R.string.scan_device_beacon_manufacturer)).append(": <b>").append(manufacturer == null ? "" : manufacturer).append("</b><br>");

            StringBuilder text = new StringBuilder();
            if (deviceData.uuids != null && deviceData.uuids.size() == 1) {
                UUID uuid = deviceData.uuids.get(0);
                text.append(uuid.toString().toUpperCase());
            }
            result.append(getString(R.string.scan_device_uuid)).append(": <b>").append(text).append("</b><br>");

            final byte[] majorBytes = {deviceData.scanRecord[25], deviceData.scanRecord[26]};           // Big endian
            String major = BleUtils.bytesToHex(majorBytes);
            result.append(getString(R.string.scan_device_beacon_major)).append(": <b>").append(major).append("</b><br>");

            final byte[] minorBytes = {deviceData.scanRecord[27], deviceData.scanRecord[28]};           // Big endian
            String minor = BleUtils.bytesToHex(minorBytes);
            result.append(getString(R.string.scan_device_beacon_minor)).append(": <b>").append(minor).append("</b><br>");

            result.append(getString(R.string.scan_device_txpower)).append(": <b>").append(deviceData.txPower).append("</b>");

            return result.toString();
        }


        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getGroupView(final int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            GroupViewHolder holder;

            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.layout_scan_item_title, parent, false);

                holder = new GroupViewHolder();

                holder.nameTextView = (TextView) convertView.findViewById(R.id.nameTextView);
                holder.descriptionTextView = (TextView) convertView.findViewById(R.id.descriptionTextView);
                holder.rssiImageView = (ImageView) convertView.findViewById(R.id.rssiImageView);
                holder.rssiTextView = (TextView) convertView.findViewById(R.id.rssiTextView);
                holder.connectButton = (Button) convertView.findViewById(R.id.connectButton);

                convertView.setTag(R.string.scan_tag_id, holder);

            } else {
                holder = (GroupViewHolder) convertView.getTag(R.string.scan_tag_id);
            }

            convertView.setTag(groupPosition);
            holder.connectButton.setTag(groupPosition);

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onClickScannedDevice(v);
                }
            });

            /*
            holder.connectButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onClickDeviceConnect(groupPosition);
                }
            });

            convertView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN ) {
                        onClickScannedDevice(v);
                        return true;
                    }
                    return false;
                }
            });
            */

            holder.connectButton.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        //onClickDeviceConnect(groupPosition);
                        return true;
                    }
                    return false;
                }
            });


            BluetoothDeviceData deviceData = mFilteredPeripherals.get(groupPosition);
            holder.nameTextView.setText(deviceData.getNiceName());

            holder.descriptionTextView.setVisibility(deviceData.type != BluetoothDeviceData.kType_Unknown ? View.VISIBLE : View.INVISIBLE);
            holder.descriptionTextView.setText(getResources().getStringArray(R.array.scan_devicetypes)[deviceData.type]);
            holder.rssiTextView.setText(deviceData.rssi == 127 ? getString(R.string.scan_device_rssi_notavailable) : String.valueOf(deviceData.rssi));

            int rrsiDrawableResource = getDrawableIdForRssi(deviceData.rssi);
            holder.rssiImageView.setImageResource(rrsiDrawableResource);

            return convertView;
        }

        private int getDrawableIdForRssi(int rssi) {
            int index;
            if (rssi == 127 || rssi <= -84) {       // 127 reserved for RSSI not available
                index = 0;
            } else if (rssi <= -72) {
                index = 1;
            } else if (rssi <= -60) {
                index = 2;
            } else if (rssi <= -48) {
                index = 3;
            } else {
                index = 4;
            }

            final int kSignalDrawables[] = {
                    R.drawable.signalstrength0,
                    R.drawable.signalstrength1,
                    R.drawable.signalstrength2,
                    R.drawable.signalstrength3,
                    R.drawable.signalstrength4};
            return kSignalDrawables[index];
        }

        @Override
        public View getChildView(final int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.layout_scan_item_child, parent, false);
            }

            // We don't expect many items so for clarity just find the views each time instead of using a ViewHolder
            TextView textView = (TextView) convertView.findViewById(R.id.dataTextView);
            Spanned text = getChild(groupPosition, childPosition);
            textView.setText(text);

            Button rawDataButton = (Button) convertView.findViewById(R.id.rawDataButton);
            rawDataButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ArrayList<BluetoothDeviceData> filteredPeripherals = mPeripheralList.filteredPeripherals(false);
                    if (groupPosition < filteredPeripherals.size()) {
                        final BluetoothDeviceData deviceData = filteredPeripherals.get(groupPosition);
                        final byte[] scanRecord = deviceData.scanRecord;
                        final String packetText = BleUtils.bytesToHexWithSpaces(scanRecord);
                        final String clipboardLabel = getString(R.string.scan_device_advertising_title);

                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle(R.string.scan_device_advertising_title)
                                .setMessage(packetText)
                                .setPositiveButton(android.R.string.ok, null)
                                .setNeutralButton(android.R.string.copy, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                        ClipData clip = ClipData.newPlainText(clipboardLabel, packetText);
                                        clipboard.setPrimaryClip(clip);
                                    }
                                })
                                .show();
                    }

                }
            });

            return convertView;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return false;
        }
    }

    public void onClickDeviceConnect(BluetoothDeviceData mBluetoothDeviceData) {
        stopScanning();

        ArrayList<BluetoothDeviceData> filteredPeripherals = mPeripheralList.filteredPeripherals(false);
        if (mBluetoothDeviceData != null) {
           // mSelectedDeviceData = filteredPeripherals.get(scannedDeviceIndex);
            mSelectedDeviceData = mBluetoothDeviceData;
            BluetoothDevice device = mSelectedDeviceData.device;


            mBleManager.setBleListener(MainActivity.this);           // Force set listener (could be still checking for updates...)


            if (mSelectedDeviceData.type == BluetoothDeviceData.kType_Uart) {      // if is uart, show all the available activities
                showChooseDeviceServiceDialog(mSelectedDeviceData);

                /*mComponentToStartWhenConnected = UartActivity.class;
                launchComponentActivity();*/
            } else {                          // if no uart, then go directly to info
                Log.d(TAG, "No UART service found. Go to InfoActivity");
            }
        } else {
            Log.w(TAG, "onClickDeviceConnect index does not exist: ");
        }
    }

    private void connect(BluetoothDevice device) {
        boolean isConnecting = mBleManager.connect(this, device.getAddress());
        if (isConnecting) {
            showConnectionStatus(true);
        }
        Toast toast = Toast.makeText(this, "CONNECTED :D", Toast.LENGTH_SHORT);
        toast.show();
       // stopScanning();

    }

    private void showConnectionStatus(boolean enable) {
        showStatusDialog(enable, R.string.scan_connecting);
    }

    private void showStatusDialog(boolean show, int stringId) {
        if (show) {

            // Remove if a previous dialog was open (maybe because was clicked 2 times really quick)
            if (mConnectingDialog != null) {
                mConnectingDialog.cancel();
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(stringId);

            // Show dialog
            mConnectingDialog = builder.create();
            mConnectingDialog.setCanceledOnTouchOutside(false);

            mConnectingDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface arg0, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        mBleManager.disconnect();
                        mConnectingDialog.cancel();
                    }
                    return true;
                }
            });
            mConnectingDialog.show();
        } else {
            if (mConnectingDialog != null) {
                mConnectingDialog.cancel();
            }
        }
    }

    private void stopScanning() {
        // Stop scanning
        if (mScanner != null) {
            mScanner.stop();
            mScanner = null;
        }

        updateUI();
    }

    private void updateUI() {
        // Scan button
        boolean isScanning = mScanner != null && mScanner.isScanning();
        mScanButton.setText(getString(isScanning ? R.string.scan_scanbutton_scanning : R.string.scan_scanbutton_scan));

        // Show list and hide "no devices" label
        final boolean isListEmpty = mScannedDevices == null || mScannedDevices.size() == 0;
        mNoDevicesTextView.setVisibility(isListEmpty ? View.VISIBLE : View.GONE);
        mDevicesScrollView.setVisibility(isListEmpty ? View.GONE : View.VISIBLE);

        // devices list
        mScannedDevicesAdapter.notifyDataSetChanged();
    }
    //TODO CHANGE THIS TO ONLY CONNECT VIA UART
    private void showChooseDeviceServiceDialog(final BluetoothDeviceData deviceData) {
        mComponentToStartWhenConnected = UartActivity.class;
        connect(deviceData.device);            // First connect to the device, and when connected go to selected activity
        launchComponentActivity();
        // Prepare dialog
       /* AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String title = String.format(getString(R.string.scan_connectto_dialog_title_format), deviceData.getNiceName());
        String[] items = new String[kComponentsNameIds.length];
        for (int i = 0; i < kComponentsNameIds.length; i++)
            items[i] = getString(kComponentsNameIds[i]);

        builder.setTitle(title)
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        switch (kComponentsNameIds[which]) {
                            case R.string.scan_connectservice_uart: {           // Uart
                                mComponentToStartWhenConnected = UartActivity.class;
                                launchComponentActivity();
                                break;
                            }
                        }

                        if (mComponentToStartWhenConnected != null) {
                            connect(deviceData.device);            // First connect to the device, and when connected go to selected activity
                        }
                    }
                });

        // Show dialog
        AlertDialog dialog = builder.create();
        dialog.show();*/
    }

    private class PeripheralList {
        // Constants
        private final static int kMaxRssiValue = -100;

        private final static String kPreferences = "PeripheralList_prefs";
        private final static String kPreferences_filtersName = "filtersName";
        private final static String kPreferences_filtersIsNameExact = "filtersIsNameExact";
        private final static String kPreferences_filtersIsNameCaseInsensitive = "filtersIsNameCaseInsensitive";
        private final static String kPreferences_filtersRssi = "filtersRssi";
        private final static String kPreferences_filtersUnnamedEnabled = "filtersUnnamedEnabled";
        private final static String kPreferences_filtersUartEnabled = "filtersUartEnabled";

        // Data
        private String mFilterName;
        private boolean mIsFilterNameExact;
        private boolean mIsFilterNameCaseInsensitive;
        private int mRssiFilterValue;
        private boolean mIsUnnamedEnabled;
        private boolean mIsOnlyUartEnabled;
        private ArrayList<BluetoothDeviceData> mCachedFilteredPeripheralList;
        private boolean mIsFilterDirty;

        private SharedPreferences.Editor preferencesEditor = getSharedPreferences(kPreferences, MODE_PRIVATE).edit();

        PeripheralList() {
            mIsFilterDirty = true;
            mCachedFilteredPeripheralList = new ArrayList<>();

            SharedPreferences preferences = getSharedPreferences(kPreferences, MODE_PRIVATE);
            mFilterName = preferences.getString(kPreferences_filtersName, null);
            mIsFilterNameExact = preferences.getBoolean(kPreferences_filtersIsNameExact, false);
            mIsFilterNameCaseInsensitive = preferences.getBoolean(kPreferences_filtersIsNameCaseInsensitive, true);
            mRssiFilterValue = preferences.getInt(kPreferences_filtersRssi, kMaxRssiValue);
            mIsUnnamedEnabled = preferences.getBoolean(kPreferences_filtersUnnamedEnabled, true);
            mIsOnlyUartEnabled = preferences.getBoolean(kPreferences_filtersUartEnabled, false);
        }

        String getFilterName() {
            return mFilterName;
        }

        void setFilterName(String name) {
            mFilterName = name;
            mIsFilterDirty = true;

            preferencesEditor.putString(kPreferences_filtersName, name);
            preferencesEditor.apply();
        }

        boolean isFilterNameExact() {
            return mIsFilterNameExact;
        }

        void setFilterNameExact(boolean exact) {
            mIsFilterNameExact = exact;
            mIsFilterDirty = true;

            preferencesEditor.putBoolean(kPreferences_filtersIsNameExact, exact);
            preferencesEditor.apply();
        }

        boolean isFilterNameCaseInsensitive() {
            return mIsFilterNameCaseInsensitive;
        }

        void setFilterNameCaseInsensitive(boolean caseInsensitive) {
            mIsFilterNameCaseInsensitive = caseInsensitive;
            mIsFilterDirty = true;

            preferencesEditor.putBoolean(kPreferences_filtersIsNameCaseInsensitive, caseInsensitive);
            preferencesEditor.apply();
        }

        int getFilterRssiValue() {
            return mRssiFilterValue;
        }

        void setFilterRssiValue(int value) {
            mRssiFilterValue = value;
            mIsFilterDirty = true;

            preferencesEditor.putInt(kPreferences_filtersRssi, value);
            preferencesEditor.apply();
        }

        boolean isFilterUnnamedEnabled() {
            return mIsUnnamedEnabled;
        }

        void setFilterUnnamedEnabled(boolean enabled) {
            mIsUnnamedEnabled = enabled;
            mIsFilterDirty = true;

            preferencesEditor.putBoolean(kPreferences_filtersUnnamedEnabled, enabled);
            preferencesEditor.apply();
        }


        boolean isFilterOnlyUartEnabled() {
            return mIsOnlyUartEnabled;
        }

        void setFilterOnlyUartEnabled(boolean enabled) {
            mIsOnlyUartEnabled = enabled;
            mIsFilterDirty = true;

            preferencesEditor.putBoolean(kPreferences_filtersUartEnabled, enabled);
            preferencesEditor.apply();
        }


        void setDefaultFilters() {
            mFilterName = null;
            mIsFilterNameExact = false;
            mIsFilterNameCaseInsensitive = true;
            mRssiFilterValue = kMaxRssiValue;
            mIsUnnamedEnabled = true;
            mIsOnlyUartEnabled = false;
        }

        boolean isAnyFilterEnabled() {
            return (mFilterName != null && !mFilterName.isEmpty()) || mRssiFilterValue > kMaxRssiValue || mIsOnlyUartEnabled || !mIsUnnamedEnabled;
        }

        ArrayList<BluetoothDeviceData> filteredPeripherals(boolean forceUpdate) {
            if (mIsFilterDirty || forceUpdate) {
                //mCachedFilteredPeripheralList = calculateFilteredPeripherals();
                mIsFilterDirty = false;
            }

            return mCachedFilteredPeripheralList;
        }

        private ArrayList<BluetoothDeviceData> calculateFilteredPeripherals() {

            ArrayList<BluetoothDeviceData> peripherals = (ArrayList<BluetoothDeviceData>) mScannedDevices.clone();

            // Sort devices alphabetically
            Collections.sort(peripherals, new Comparator<BluetoothDeviceData>() {
                @Override
                public int compare(BluetoothDeviceData o1, BluetoothDeviceData o2) {
                    return o1.getNiceName().compareToIgnoreCase(o2.getNiceName());
                }
            });

            // Apply filters
            if (mIsOnlyUartEnabled) {
                for (Iterator<BluetoothDeviceData> it = peripherals.iterator(); it.hasNext(); ) {
                    if (it.next().type != BluetoothDeviceData.kType_Uart) {
                        it.remove();
                    }
                }
            }

            if (!mIsUnnamedEnabled) {
                for (Iterator<BluetoothDeviceData> it = peripherals.iterator(); it.hasNext(); ) {
                    if (it.next().getName() == null) {
                        it.remove();
                    }
                }
            }

            if (mFilterName != null && !mFilterName.isEmpty()) {
                for (Iterator<BluetoothDeviceData> it = peripherals.iterator(); it.hasNext(); ) {
                    String name = it.next().getName();
                    boolean testPassed = false;
                    if (name != null) {
                        if (mIsFilterNameExact) {
                            if (mIsFilterNameCaseInsensitive) {
                                testPassed = name.compareToIgnoreCase(mFilterName) == 0;
                            } else {
                                testPassed = name.compareTo(mFilterName) == 0;
                            }
                        } else {
                            if (mIsFilterNameCaseInsensitive) {
                                testPassed = name.toLowerCase().contains(mFilterName.toLowerCase());
                            } else {
                                testPassed = name.contains(mFilterName);
                            }
                        }
                    }
                    if (!testPassed) {
                        it.remove();
                    }
                }
            }

            for (Iterator<BluetoothDeviceData> it = peripherals.iterator(); it.hasNext(); ) {
                if (it.next().rssi < mRssiFilterValue) {
                    it.remove();
                }
            }

            return peripherals;
        }

        String filtersDescription() {
            String filtersTitle = null;

            if (mFilterName != null && !mFilterName.isEmpty()) {
                filtersTitle = mFilterName;
            }

            if (mRssiFilterValue > kMaxRssiValue) {
                String rssiString = String.format(Locale.ENGLISH, getString(R.string.scan_filters_name_rssi_format), mRssiFilterValue);
                if (filtersTitle != null && !filtersTitle.isEmpty()) {
                    filtersTitle = filtersTitle + ", " + rssiString;
                } else {
                    filtersTitle = rssiString;
                }
            }

            if (!mIsUnnamedEnabled) {
                String namedString = getString(R.string.scan_filters_name_named);
                if (filtersTitle != null && !filtersTitle.isEmpty()) {
                    filtersTitle = filtersTitle + ", " + namedString;
                } else {
                    filtersTitle = namedString;
                }
            }

            if (mIsOnlyUartEnabled) {
                String uartString = getString(R.string.scan_filters_name_uart);
                if (filtersTitle != null && !filtersTitle.isEmpty()) {
                    filtersTitle = filtersTitle + ", " + uartString;
                } else {
                    filtersTitle = uartString;
                }
            }

            return filtersTitle;
        }
    }


    public void onClickScannedDevice(final View view) {
        final int groupPosition = (Integer) view.getTag();

        if (mScannedDevicesListView.isGroupExpanded(groupPosition)) {
            mScannedDevicesListView.collapseGroup(groupPosition);
        } else {
            mScannedDevicesListView.expandGroup(groupPosition, true);

            // Force scrolling to view the children
            mDevicesScrollView.post(new Runnable() {
                @Override
                public void run() {
                    mScannedDevicesListView.scrollToGroup(groupPosition, view, mDevicesScrollView);
                }
            });
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void requestLocationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check 
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can scan for Bluetooth peripherals");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
                    }
                });
                builder.show();
            }
        }
    }


    private boolean manageBluetoothAvailability() {
        boolean isEnabled = true;

        // Check Bluetooth HW status
        int errorMessageId = 0;
        final int bleStatus = BleUtils.getBleStatus(getBaseContext());
        switch (bleStatus) {
            case BleUtils.STATUS_BLE_NOT_AVAILABLE:
                errorMessageId = R.string.dialog_error_no_ble;
                isEnabled = false;
                break;
            case BleUtils.STATUS_BLUETOOTH_NOT_AVAILABLE: {
                errorMessageId = R.string.dialog_error_no_bluetooth;
                isEnabled = false;      // it was already off
                break;
            }
            case BleUtils.STATUS_BLUETOOTH_DISABLED: {
                isEnabled = false;      // it was already off
                // if no enabled, launch settings dialog to enable it (user should always be prompted before automatically enabling bluetooth)
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, kActivityRequestCode_EnableBluetooth);
                // execution will continue at onActivityResult()
                break;
            }
        }
        if (errorMessageId != 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            AlertDialog dialog = builder.setMessage(errorMessageId)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            DialogUtils.keepDialogOnOrientationChanges(dialog);
        }

        return isEnabled;
    }

    private boolean manageLocationServiceAvailabilityForScanning() {

        boolean areLocationServiceReady = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {        // Location services are only needed to be enabled from Android 6.0
            int locationMode = Settings.Secure.LOCATION_MODE_OFF;
            try {
                locationMode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);

            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
            }
            areLocationServiceReady = locationMode != Settings.Secure.LOCATION_MODE_OFF;

            if (!areLocationServiceReady) {

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                AlertDialog dialog = builder.setMessage(R.string.dialog_error_nolocationservices_requiredforscan_marshmallow)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                DialogUtils.keepDialogOnOrientationChanges(dialog);
            }
        }

        return areLocationServiceReady;
    }

    private void autostartScan() {
        if (BleUtils.getBleStatus(this) == BleUtils.STATUS_BLE_ENABLED) {
            // If was connected, disconnect
            mBleManager.disconnect();

            // Force restart scanning
            if (mScannedDevices != null) {      // Fixed a weird bug when resuming the app (this was null on very rare occasions even if it should not be)
                mScannedDevices.clear();
            }
            startScan(null);
        }
    }

    private void startScan(final UUID[] servicesToScan) {
        Log.d(TAG, "startScan");

        // Stop current scanning (if needed)
        stopScanning();

        // Configure scanning
        BluetoothAdapter bluetoothAdapter = BleUtils.getBluetoothAdapter(getApplicationContext());
        if (BleUtils.getBleStatus(this) != BleUtils.STATUS_BLE_ENABLED) {   //if BLE not enabled
            Log.w(TAG, "startScan: BluetoothAdapter not initialized or unspecified address.");
        } else {    //BLE enabled
            mScanner = new BleDevicesScanner(bluetoothAdapter, servicesToScan, new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
                    //not sure why this was commented
                    final String deviceName = device.getName();
                    Log.d(TAG, "Discovered device: " + (deviceName != null ? deviceName : "<unknown>"));

                    BluetoothDeviceData previouslyScannedDeviceData = null;
                    if (mScannedDevices == null)
                        mScannedDevices = new ArrayList<>();       // Safeguard

                    // Check that the device was not previously found
                    for (BluetoothDeviceData deviceData : mScannedDevices) {
                        if (deviceData.device.getAddress().equals(device.getAddress())) {
                            previouslyScannedDeviceData = deviceData;
                            break;
                        }
                    }

                    BluetoothDeviceData deviceData;
                    if (previouslyScannedDeviceData == null) {
                        Log.d(TAG, "ADDED TO LIST " + (deviceName != null ? deviceName : "<unknown>"));
                        // Add it to the mScannedDevice list
                        deviceData = new BluetoothDeviceData();
                        mScannedDevices.add(deviceData);
                    } else {
                        deviceData = previouslyScannedDeviceData;
                    }

                    deviceData.device = device;
                    deviceData.rssi = rssi;
                    deviceData.scanRecord = scanRecord;
                    decodeScanRecords(deviceData);

                    if(deviceName != null && deviceName.equals("Adafruit Bluefruit LE")) onClickDeviceConnect(deviceData);

                    // Update device data
                    long currentMillis = SystemClock.uptimeMillis();
                    if (previouslyScannedDeviceData == null || currentMillis - mLastUpdateMillis > kMinDelayToUpdateUI) {          // Avoid updating when not a new device has been found and the time from the last update is really short to avoid updating UI so fast that it will become unresponsive
                        mLastUpdateMillis = currentMillis;

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateUI();
                            }
                        });
                    }

                }
            });

            // Start scanning
            mScanner.start();
        }

        // Update UI
        updateUI();
    }

    private void decodeScanRecords(BluetoothDeviceData deviceData) {
        // based on http://stackoverflow.com/questions/24003777/read-advertisement-packet-in-android
        final byte[] scanRecord = deviceData.scanRecord;

        ArrayList<UUID> uuids = new ArrayList<>();
        byte[] advertisedData = Arrays.copyOf(scanRecord, scanRecord.length);
        int offset = 0;
        deviceData.type = BluetoothDeviceData.kType_Unknown;

        // Check if is an iBeacon ( 0x02, 0x0x1, a flag byte, 0x1A, 0xFF, manufacturer (2bytes), 0x02, 0x15)
        final boolean isBeacon = advertisedData[0] == 0x02 && advertisedData[1] == 0x01 && advertisedData[3] == 0x1A && advertisedData[4] == (byte) 0xFF && advertisedData[7] == 0x02 && advertisedData[8] == 0x15;

        // Check if is an URIBeacon
        final byte[] kUriBeaconPrefix = {0x03, 0x03, (byte) 0xD8, (byte) 0xFE};
        final boolean isUriBeacon = Arrays.equals(Arrays.copyOf(scanRecord, kUriBeaconPrefix.length), kUriBeaconPrefix) && advertisedData[5] == 0x16 && advertisedData[6] == kUriBeaconPrefix[2] && advertisedData[7] == kUriBeaconPrefix[3];

        if (isBeacon) {
            deviceData.type = BluetoothDeviceData.kType_Beacon;

            // Read uuid
            offset = 9;
            UUID uuid = BleUtils.getUuidFromByteArrayBigEndian(Arrays.copyOfRange(scanRecord, offset, offset + 16));
            uuids.add(uuid);
            offset += 16;

            // Skip major minor
            offset += 2 * 2;   // major, minor

            // Read txpower
            final int txPower = advertisedData[offset++];
            deviceData.txPower = txPower;
        } else if (isUriBeacon) {
            deviceData.type = BluetoothDeviceData.kType_UriBeacon;

            // Read txpower
            final int txPower = advertisedData[9];
            deviceData.txPower = txPower;
        } else {
            // Read standard advertising packet
            while (offset < advertisedData.length - 2) {
                // Length
                int len = advertisedData[offset++];
                if (len == 0) break;

                // Type
                int type = advertisedData[offset++];
                if (type == 0) break;

                // Data
//            Log.d(TAG, "record -> lenght: " + length + " type:" + type + " data" + data);

                switch (type) {
                    case 0x02:          // Partial list of 16-bit UUIDs
                    case 0x03: {        // Complete list of 16-bit UUIDs
                        while (len > 1) {
                            int uuid16 = advertisedData[offset++] & 0xFF;
                            uuid16 |= (advertisedData[offset++] << 8);
                            len -= 2;
                            uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)));
                        }
                        break;
                    }

                    case 0x06:          // Partial list of 128-bit UUIDs
                    case 0x07: {        // Complete list of 128-bit UUIDs
                        while (len >= 16) {
                            try {
                                // Wrap the advertised bits and order them.
                                UUID uuid = BleUtils.getUuidFromByteArraLittleEndian(Arrays.copyOfRange(advertisedData, offset, offset + 16));
                                uuids.add(uuid);

                            } catch (IndexOutOfBoundsException e) {
                                Log.e(TAG, "BlueToothDeviceFilter.parseUUID: " + e.toString());
                            } finally {
                                // Move the offset to read the next uuid.
                                offset += 16;
                                len -= 16;
                            }
                        }
                        break;
                    }

                    case 0x09: {
                        byte[] nameBytes = new byte[len - 1];
                        for (int i = 0; i < len - 1; i++) {
                            nameBytes[i] = advertisedData[offset++];
                        }

                        String name = null;
                        try {
                            name = new String(nameBytes, "utf-8");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        deviceData.advertisedName = name;
                        break;
                    }

                    case 0x0A: {        // TX Power
                        final int txPower = advertisedData[offset++];
                        deviceData.txPower = txPower;
                        break;
                    }

                    default: {
                        offset += (len - 1);
                        break;
                    }
                }
            }

            // Check if Uart is contained in the uuids
            boolean isUart = false;
            for (UUID uuid : uuids) {
                if (uuid.toString().equalsIgnoreCase(UartInterfaceActivity.UUID_SERVICE)) {
                    isUart = true;
                    break;
                }
            }
            if (isUart) {
                deviceData.type = BluetoothDeviceData.kType_Uart;
            }
        }

        deviceData.uuids = uuids;
    }

    public void onClickScan(View view) {
        boolean isScanning = mScanner != null && mScanner.isScanning();
        if (isScanning) {
            stopScanning();
        } else {
            startScan(null);
        }
    }

    private void launchComponentActivity() {
        // Enable generic attribute service
        final BluetoothGattService genericAttributeService = mBleManager.getGattService(kGenericAttributeService);
        if (genericAttributeService != null) {
            Log.d(TAG, "kGenericAttributeService found. Check if kServiceChangedCharacteristic exists");

            final UUID characteristicUuid = UUID.fromString(kServiceChangedCharacteristic);
            final BluetoothGattCharacteristic dataCharacteristic = genericAttributeService.getCharacteristic(characteristicUuid);
            if (dataCharacteristic != null) {
                Log.d(TAG, "kServiceChangedCharacteristic exists. Enable indication");
                mBleManager.enableIndication(genericAttributeService, kServiceChangedCharacteristic, true);
            } else {
                Log.d(TAG, "Skip enable indications for kServiceChangedCharacteristic. Characteristic not found");
            }
        } else {
            Log.d(TAG, "Skip enable indications for kServiceChangedCharacteristic. kGenericAttributeService not found");
        }

        // Launch activity
        showConnectionStatus(false);
        if (mComponentToStartWhenConnected != null) {
            Log.d(TAG, "Start component:" + mComponentToStartWhenConnected);
            Intent intent = new Intent(MainActivity.this, mComponentToStartWhenConnected);
            startActivityForResult(intent, kActivityRequestCode_ConnectedActivity);
        }
    }

    //--------------------------CALLBACK METHODS--------------------------//
    @Override
    public void onConnected() {

    }

    @Override
    public void onConnecting() {

    }

    @Override
    public void onDisconnected() {

    }

    @Override
    public void onServicesDiscovered() {
        Log.d(TAG, "services discovered");
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {

    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {

    }

    @Override
    public void onReadRemoteRssi(int rssi) {

    }

    @Override
    public void resetBluetoothCompleted() {

    }
}