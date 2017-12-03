package com.example.tom.sdp_application;

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
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;



public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter mBluetoothAdapter;
    private int REQUEST_ENABLE_BT = 1;
    private Handler mHandler;
    private static final long SCAN_PERIOD = 10000;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;

    BluetoothLE mBluetoothLE = new BluetoothLE(mBluetoothAdapter, REQUEST_ENABLE_BT, mHandler, mLEScanner, settings, filters, mGatt);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBluetoothLE.mHandler = new Handler();

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE Not Supported",
                    Toast.LENGTH_SHORT).show();
            finish();
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothLE.mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    @Override
    protected void onResume(){
        super.onResume();

        if(mBluetoothLE.mBluetoothAdapter == null || !mBluetoothLE.mBluetoothAdapter.isEnabled()){
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }else{
            if (Build.VERSION.SDK_INT >= 21) {
                mBluetoothLE.mLEScanner = mBluetoothLE.mBluetoothAdapter.getBluetoothLeScanner();
                mBluetoothLE.settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
                mBluetoothLE.filters = new ArrayList<ScanFilter>();
            }
            mBluetoothLE.scanLeDevice(true);
        }

    }

    protected void onPause(){
        super.onPause();
        if (mBluetoothAdapter != null && mBluetoothLE.mBluetoothAdapter.isEnabled()) {
            mBluetoothLE.scanLeDevice(false);
        }
    }

    @Override
    protected  void onDestroy(){
        if (mBluetoothLE.mGatt == null) {
            return;
        }
        mBluetoothLE.mGatt.close();
        mBluetoothLE.mGatt = null;
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

}
