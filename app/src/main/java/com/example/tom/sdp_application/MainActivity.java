package com.example.tom.sdp_application;

import android.Manifest;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;


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
import android.view.View;
import android.widget.*;

import java.util.List;
import java.lang.Thread;


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



    //BluetoothLE mBluetoothLE = new BluetoothLE(mBluetoothAdapter, REQUEST_ENABLE_BT, mHandler, mLEScanner, settings, filters, mGatt);
    BluetoothLeUart uart;

    TextView messages;
    EditText input;
    Button   send;
    CheckBox newline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



        messages = (TextView) findViewById(R.id.messages);
        input = (EditText) findViewById(R.id.input);

        uart = new BluetoothLeUart(this);
        uart.registerCallback(this);

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

        super.onResume();
        writeLine("Scanning for devices ...");
        uart.registerCallback(this);
        uart.connectFirstAvailable();

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
                messages.append(text);
                messages.append("\n");
            }
        });
    }


    public void sendClick(View view) {
        StringBuilder stringBuilder = new StringBuilder();
        String message = input.getText().toString();

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
            uart.send(stringBuilder.toString());
        }
        // Terminate with a newline character if requests
        newline = (CheckBox) findViewById(R.id.newline);
        if (newline.isChecked()) {
            stringBuilder.setLength(0);
            stringBuilder.append("\n");
            uart.send(stringBuilder.toString());
        }
    }

    //UART CALLBACK HANDLERS
    public void onReceive(BluetoothLeUart uart, BluetoothGattCharacteristic rx) {
        // Called when data is received by the UART.
        Toast.makeText(this ,"Received: " + rx.getStringValue(0),
            Toast.LENGTH_LONG).show();
        System.out.print("MESSAGES");

    }

    public void onConnectFailed(BluetoothLeUart uart) {
        // Called when some error occured which prevented UART connection from completing.
        writeLine("Error connecting to device!");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                send = findViewById(R.id.send);
                send.setClickable(false);
                send.setEnabled(false);
            }
        });
    }

    public void onDeviceFound(BluetoothDevice device) {
        // Called when a UART device is discovered (after calling startScan).
        writeLine("Found device : " + device.getAddress());
        writeLine("Waiting for a connection ...");
    }

    @Override
    public void onDeviceInfoAvailable() {
        writeLine(uart.getDeviceInfo());
    }

    public void onDisconnected(BluetoothLeUart uart) {
        // Called when the UART device disconnected.
        writeLine("Disconnected!");
        // Disable the send button.
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                send = findViewById(R.id.send);
                send.setClickable(false);
                send.setEnabled(false);
            }
        });
    }

    public void onConnected(BluetoothLeUart uart) {
        // Called when UART device is connected and ready to send/receive data.
        System.out.println("Connected!");
        // Enable the send button
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                send = findViewById(R.id.send);
                send.setClickable(true);
                send.setEnabled(true);
            }
        });
    }

}
