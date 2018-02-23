package com.example.tom.sdp_application;

import android.Manifest;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;


import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;



public class MainActivity extends AppCompatActivity implements BluetoothLeUart.Callback {
    private BluetoothAdapter mBluetoothAdapter;
    private int REQUEST_ENABLE_BT = 1;
    private Handler mHandler;
    private static final long SCAN_PERIOD = 10000;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;
    private BluetoothAdapter.LeScanCallback mLeScanCallback;

    @Override
    public void onDeviceInfoAvailable() {
        //writeLine(uart.getDeviceInfo());
    }

    public void onConnected(BluetoothLeUart mBluetoothLeUart) {
        // Called when UART device is connected and ready to send/receive data.
        System.out.println("Connected!");
        // Enable the send button
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //send = (Button)findViewById(R.id.send);
                //send.setClickable(true);
                //send.setEnabled(true);
            }
        });
    }

    public void onConnectFailed(BluetoothLeUart mBluetoothLeUart) {
        // Called when some error occured which prevented UART connection from completing.
        writeLine("Error connecting to device!");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //send = (Button)findViewById(R.id.send);
                //send.setClickable(false);
                //send.setEnabled(false);
            }
        });
    }

    public void onDeviceFound(BluetoothDevice device) {
        // Called when a UART device is discovered (after calling startScan).
        writeLine("Found device : " + device.getAddress());
        writeLine("Waiting for a connection ...");
    }

    //BluetoothLE mBluetoothLE = new BluetoothLE(mBluetoothAdapter, REQUEST_ENABLE_BT, mHandler, mLEScanner, settings, filters, mGatt);
    BluetoothLeUart mBluetoothLeUart = new BluetoothLeUart(this);

    public ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
        }
    };

    public void onDisconnected(BluetoothLeUart mBluetoothLeUart) {
        // Called when the UART device disconnected.
        writeLine("Disconnected!");
        // Disable the send button.
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //send = (Button)findViewById(R.id.send);
                //send.setClickable(false);
                //send.setEnabled(false);
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final SmsManager smsManagger = SmsManager.getDefault();
        //TODO DON'T PUSH WITH A PHONE NUMBER
        final String num = "";


        // Here, thisActivity is the current activity
        if (this.getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            if (this.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {

                // Show an explanation to the user asynchronously -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed; request the permission
                this.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        1);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            // Permission has already been granted
        }
        // Here, thisActivity is the current activity
        if (this.getApplicationContext().checkSelfPermission(Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            if (this.shouldShowRequestPermissionRationale(Manifest.permission.SEND_SMS)) {

                // Show an explanation to the user asynchronously -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed; request the permission
                this.requestPermissions(new String[]{Manifest.permission.SEND_SMS},
                        1);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            // Permission has already been granted
        }

        //mBluetoothLE.mHandler = new Handler();


        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE Not Supported",
                    Toast.LENGTH_SHORT).show();
            finish();
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        //mBluetoothLE.mBluetoothAdapter = bluetoothManager.getAdapter();

        final Button scanButton = findViewById(R.id.scanButton);
        scanButton.setOnClickListener(new View.OnClickListener(){
          public void onClick(View v){
              //mBluetoothLE.mLEScanner.(mBluetoothLE.filters,mBluetoothLE.settings,mScanCallback);
              //mBluetoothLE.scanLeDevice(true);
              mBluetoothLeUart.connectFirstAvailable();

              //smsManagger.sendTextMessage(num, null, "test", null, null);


          }
        });


        //Toast.makeText(this, "scanning",
          //      Toast.LENGTH_SHORT).show();
        //finish();
    }

    @Override
    protected void onResume(){
        super.onResume();

        /*if(mBluetoothLE.mBluetoothAdapter == null || !mBluetoothLE.mBluetoothAdapter.isEnabled()){
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }else{
            if (Build.VERSION.SDK_INT >= 21) {
                mBluetoothLE.mLEScanner = mBluetoothLE.mBluetoothAdapter.getBluetoothLeScanner();
                mBluetoothLE.settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
                mBluetoothLE.filters = new ArrayList<ScanFilter>();
            }
            mBluetoothLE.scanLeDevice(true);
        }*/

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
        if(requestCode == REQUEST_ENABLE_BT) {
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

    public void onReceive(BluetoothLeUart mBluetoothLeUart, BluetoothGattCharacteristic rx) {
        // Called when data is received by the UART.
        Toast.makeText(this ,"Received: " + rx.getStringValue(0),
            Toast.LENGTH_LONG).show();
        System.out.print("MESSAGES");

    }
}
