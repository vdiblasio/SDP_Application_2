package com.example.tom.sdp_application;

/**
 * Created by Vincent DiBlasio on 12/3/2017.
 */
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



public class BluetoothLE extends AppCompatActivity {
    BluetoothAdapter mBluetoothAdapter;
    int RequestEnableBT = 1;
    Handler mHandler;
    static final long ScanPeriod = 10000;

    BluetoothLeScanner mLEScanner;

    public ScanSettings settings;
    List<ScanFilter> filters;
    BluetoothGatt mGatt;

    public void scanLeDevice(final boolean enable) {
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT < 21) {
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    } else {
                        mLEScanner.stopScan(mScanCallback);
                    }
                }
            }, ScanPeriod);
            if(Build.VERSION.SDK_INT<21){
                mBluetoothAdapter.startLeScan(mLeScanCallback);
            } else{
                mLEScanner.startScan(filters,settings,mScanCallback);
            }

        }
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    Log.i("onScan", device.toString());
                    connectToDevice(device);

                }
            });
        }
    };

    public ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
        }
    };

    public void connectToDevice(BluetoothDevice device) {
        if (mGatt == null) {
            mGatt = device.connectGatt(this, false, gattCallback);
            scanLeDevice(false);
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i("onConnectionStateChange", "Status: " + status);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i("gattCallback", "STATE_CONNECTED");
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e("gattCallback", "STATE_DISCONNECTED");
                    break;
                default:
                    Log.e("gattCallback", "STATE_OTHER");
            }

        }

    };

    public BluetoothLE(BluetoothAdapter BluetoothAdapter, int RequestEnableBT, Handler Handler, BluetoothLeScanner LEScanner, ScanSettings settings, List<ScanFilter> filters, BluetoothGatt mGatt){
        this.mBluetoothAdapter = BluetoothAdapter;
        this.RequestEnableBT = RequestEnableBT;
        this.mHandler = Handler;
        this.mLEScanner = LEScanner;
        this.settings = settings;
        this.filters = filters;
        this.mGatt = mGatt;
    }


}
