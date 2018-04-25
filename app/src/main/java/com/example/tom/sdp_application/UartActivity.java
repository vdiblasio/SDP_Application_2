package com.example.tom.sdp_application;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

//import com.adafruit.bluefruit.le.connect.app.settings.ConnectedSettingsActivity;
//import com.adafruit.bluefruit.le.connect.app.settings.MqttUartSettingsActivity;
//import com.adafruit.bluefruit.le.connect.app.settings.PreferencesFragment;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

public class UartActivity extends UartInterfaceActivity implements MqttManager.MqttManagerListener {
    // Log
    private final static String TAG = UartActivity.class.getSimpleName();

    // Configuration
    private final static boolean kUseColorsForData = true;
    public final static int kDefaultMaxPacketsToPaintAsText = 500;
    private final static int kInfoColor = Color.parseColor("#F21625");

    // Activity request codes (used for onActivityResult)
    private static final int kActivityRequestCode_ConnectedSettingsActivity = 0;
    private static final int kActivityRequestCode_MqttSettingsActivity = 1;


    // Constants
    private final static String kPreferences = "UartActivity_prefs";
    private final static String kPreferences_eol = "eol";
    private final static String kPreferences_eolCharactersId = "eolCharactersId";
    private final static String kPreferences_echo = "echo";
    private final static String kPreferences_asciiMode = "ascii";
    private final static String kPreferences_timestampDisplayMode = "timestampdisplaymode";
    PowerManager mgr;
    PowerManager.WakeLock wakeLock;
    // Colors
    private int mTxColor;
    private int mRxColor;

    // UI
    private EditText mBufferTextView;
    private ListView mBufferListView;
    private TimestampListAdapter mBufferListAdapter;
    private EditText mSendEditText;
    private MenuItem mMqttMenuItem;
    private Handler mMqttMenuItemAnimationHandler;
    private TextView mSentBytesTextView;
    private TextView mReceivedBytesTextView;
    private TextView mActivePhoneNumber;                //textview for number display
    private RelativeLayout mPhoneNumberDisplay;         //holds textview for active number and title
    private Button mViewMessaageLog;

    // UI TextBuffer (refreshing the text buffer is managed with a timer because a lot of changes can arrive really fast and could stall the main thread)
    private Handler mUIRefreshTimerHandler = new Handler();
    private Runnable mUIRefreshTimerRunnable = new Runnable() {
        @Override
        public void run() {

            if (isUITimerRunning) {
                updateTextDataUI();
                // Log.d(TAG, "updateDataUI");
                mUIRefreshTimerHandler.postDelayed(this, 200);
            }
        }
    };
    private boolean isUITimerRunning = false;

    // Data
    private boolean mShowDataInHexFormat;
    private boolean mIsTimestampDisplayMode;
    private boolean mIsEchoEnabled;
    private boolean mIsEolEnabled;
    private int mEolCharactersId;
    private String phoneNumber = "";

    private volatile SpannableStringBuilder mTextSpanBuffer;
    private volatile ArrayList<UartDataChunk> mDataBuffer;
    private volatile int mSentBytes;
    private volatile int mReceivedBytes;

    private DataFragment mRetainedDataFragment;

    private MqttManager mMqttManager;

    private int maxPacketsToPaintAsText;

    //SMS
    int smsType;

    //UI
    int uiOptions;
    int newUiOptions;
    int check;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_uart);



        File fileDir = getFilesDir();
        File file = new File(fileDir,"contactNumber");



        mBleManager = BleManager.getInstance(this);
        restoreRetainedDataFragment();

        mgr = (PowerManager)this.getApplicationContext().getSystemService(Context.POWER_SERVICE);
        wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakeLock");
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = openFileInput("contactNumber");
            int content;
            while ((content = fileInputStream.read()) != -1){
                phoneNumber += (char)content;
            }
        }catch (FileNotFoundException e){

        }catch (IOException ex){
            ex.printStackTrace();
        }finally {
            try{
                if(fileInputStream != null){
                    fileInputStream.close();
                }
            }catch (IOException ex){
                ex.printStackTrace();
            }
        }
    //test
        //My UI stuff

        //setting up the 'display log' button
        mViewMessaageLog = (Button) findViewById(R.id.viewMessageLog);
        mViewMessaageLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mViewMessaageLog.getVisibility() == View.VISIBLE){
                    mViewMessaageLog.setVisibility(View.INVISIBLE);
                }
                else{
                    mViewMessaageLog.setVisibility(View.VISIBLE);
                }
            }
        });

        mActivePhoneNumber = (TextView) findViewById(R.id.activePhoneNumber);
        mPhoneNumberDisplay = (RelativeLayout) findViewById(R.id.phoneNumberDisplay);

        //set up action listener for phone number display
        mActivePhoneNumber.setText(phoneNumber);


        AlertDialog.Builder builder = new AlertDialog.Builder(UartActivity.this);

        final EditText input = new EditText(UartActivity.this);
        input.setText(phoneNumber);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        builder.setView(input);

        //TODO replace text with string resources (R.string.xx)
        builder.setMessage("Enter number of selected contact")
                .setTitle("Change Emergency Contact")
                .setPositiveButton(android.R.string.ok, null)
                .setCancelable(false);

        final AlertDialog dialog = builder.create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface dialog) {
                Button b = ((AlertDialog)dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                b.setOnClickListener(new View.OnClickListener(){
                    @Override
                    public void onClick(View view){
                        String newNum = input.getText().toString();
                        if(newNum.length() != 10){
                            Toast.makeText(UartActivity.this, "Entered number is invalid.",
                                    Toast.LENGTH_SHORT).show();
                            //DONT DISMISS IF INVALID
                        }
                        else {
                            //valid length number, we will accept this
                            if (newNum != phoneNumber) {
                                //update textviews
                                input.setText(newNum);
                                mActivePhoneNumber.setText(newNum);
                                FileOutputStream outputStream;
                                try {
                                    outputStream = openFileOutput("contactNumber", Context.MODE_PRIVATE);
                                    outputStream.write(newNum.getBytes());
                                    outputStream.close();

                                } catch (Exception e) {
                                    Toast.makeText(UartActivity.this, "You should never see this",
                                            Toast.LENGTH_SHORT).show();
                                }

                                //make sure that this is legit

                                phoneNumber = newNum;
                            }
                            dialog.dismiss();
                        }
                    }
                });
            }
        });

        //set listener for phone number display area
        mPhoneNumberDisplay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.show();
            }
        });


/*
        //set action on POSITIVE (ok) button press
        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //collect phone number string, check for validity
                String newNum = input.getText().toString();
                if(newNum != phoneNumber) {
                    FileOutputStream outputStream;
                    try {
                        outputStream = openFileOutput("contactNumber", Context.MODE_PRIVATE);
                        outputStream.write(newNum.getBytes());
                        outputStream.close();

                    } catch (Exception e) {
                        Toast.makeText(UartActivity.this, "You should never see this",
                                Toast.LENGTH_SHORT).show();
                    }

                    //make sure that this is legit

                    phoneNumber = newNum;
                }
            }
        });
        builder.show();
*/


        // Get default theme colors
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = getTheme();
        theme.resolveAttribute(R.attr.colorPrimaryDark, typedValue, true);
        mTxColor = typedValue.data;
        theme.resolveAttribute(R.attr.colorControlActivated, typedValue, true);
        mRxColor = typedValue.data;

        // UI
        mBufferListView = (ListView) findViewById(R.id.bufferListView);
        mBufferListAdapter = new TimestampListAdapter(this, R.layout.layout_uart_datachunkitem);
        mBufferListView.setAdapter(mBufferListAdapter);
        mBufferListView.setDivider(null);

        mBufferTextView = (EditText) findViewById(R.id.bufferTextView);
        if (mBufferTextView != null) {
            mBufferTextView.setKeyListener(null);     // make it not editable
        }

        mSendEditText = (EditText) findViewById(R.id.sendEditText);
        mSendEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    onClickSend(null);
                    return true;
                }

                return false;
            }
        });
        mSendEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus) {
                    // Dismiss keyboard when sendEditText loses focus
                    dismissKeyboard(view);
                }
            }
        });

        mSentBytesTextView = (TextView) findViewById(R.id.sentBytesTextView);
        mReceivedBytesTextView = (TextView) findViewById(R.id.receivedBytesTextView);

        // Read shared preferences
        maxPacketsToPaintAsText = PreferencesFragment.getUartTextMaxPackets(this);
        //Log.d(TAG, "maxPacketsToPaintAsText: "+maxPacketsToPaintAsText);

        // Read local preferences
        SharedPreferences preferences = getSharedPreferences(kPreferences, MODE_PRIVATE);
        mShowDataInHexFormat = !preferences.getBoolean(kPreferences_asciiMode, true);
        final boolean isTimestampDisplayMode = preferences.getBoolean(kPreferences_timestampDisplayMode, false);
        setDisplayFormatToTimestamp(isTimestampDisplayMode);
        mIsEchoEnabled = preferences.getBoolean(kPreferences_echo, true);
        mIsEolEnabled = preferences.getBoolean(kPreferences_eol, true);
        mEolCharactersId = preferences.getInt(kPreferences_eolCharactersId, 0);
        invalidateOptionsMenu();        // udpate options menu with current values

        // Continue
        onServicesDiscovered();

        // Mqtt init
        mMqttManager = MqttManager.getInstance(this);
        if (MqttSettings.getInstance(this).isConnected()) {
            mMqttManager.connectFromSavedSettings(this);
        }

        if(phoneNumber.equals("")){
            dialog.show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        //wakeLock.release();
        // Setup listeners
        mBleManager.setBleListener(this);

        mMqttManager.setListener(this);
        updateMqttStatus();

        // Start UI refresh
        //Log.d(TAG, "add ui timer");
        updateUI();

        isUITimerRunning = true;
        mUIRefreshTimerHandler.postDelayed(mUIRefreshTimerRunnable, 0);

    }

    @Override
    public void onPause() {
        super.onPause();
        wakeLock.acquire(1000000000);

        //Log.d(TAG, "remove ui timer");
        isUITimerRunning = false;
        mUIRefreshTimerHandler.removeCallbacksAndMessages(mUIRefreshTimerRunnable);

        // Save preferences
        SharedPreferences preferences = getSharedPreferences(kPreferences, MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(kPreferences_echo, mIsEchoEnabled);
        editor.putBoolean(kPreferences_eol, mIsEolEnabled);
        editor.putInt(kPreferences_eolCharactersId, mEolCharactersId);
        editor.putBoolean(kPreferences_asciiMode, !mShowDataInHexFormat);
        editor.putBoolean(kPreferences_timestampDisplayMode, mIsTimestampDisplayMode);

        editor.apply();

    }

    @Override
    public void onDestroy() {
        // Disconnect mqtt
        if (mMqttManager != null) {
            mMqttManager.disconnect();
        }

        // Retain data
        saveRetainedDataFragment();

        super.onDestroy();
    }

    public void dismissKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public void onClickSend(View view) {
        String data = mSendEditText.getText().toString();
        mSendEditText.setText("");       // Clear editText

        uartSendData(data, false);
    }

    private void uartSendData(String data, boolean wasReceivedFromMqtt) {
        // MQTT publish to TX
        MqttSettings settings = MqttSettings.getInstance(UartActivity.this);
        if (!wasReceivedFromMqtt) {
            if (settings.isPublishEnabled()) {
                String topic = settings.getPublishTopic(MqttUartSettingsActivity.kPublishFeed_TX);
                final int qos = settings.getPublishQos(MqttUartSettingsActivity.kPublishFeed_TX);
                mMqttManager.publish(topic, data, qos);
            }
        }

        // Add eol
        if (mIsEolEnabled) {
            // Add newline character if checked
            data += getEolCharacters();//"\n";
        }

        // Send to uart
        if (!wasReceivedFromMqtt || settings.getSubscribeBehaviour() == MqttSettings.kSubscribeBehaviour_Transmit) {
            sendData(data);
            mSentBytes += data.length();
        }

        // Add to current buffer
        byte[] bytes = new byte[0];
        try {
            bytes = data.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        UartDataChunk dataChunk = new UartDataChunk(System.currentTimeMillis(), UartDataChunk.TRANSFERMODE_TX, bytes);
        mDataBuffer.add(dataChunk);

        final String formattedData = mShowDataInHexFormat ? BleUtils.bytesToHex2(bytes) : BleUtils.bytesToText(bytes, true);
        if (mIsTimestampDisplayMode) {
            final String currentDateTimeString = DateFormat.getTimeInstance().format(new Date(dataChunk.getTimestamp()));
            mBufferListAdapter.add(new TimestampData("[" + currentDateTimeString + "] TX: " + formattedData, mTxColor));
            mBufferListView.setSelection(mBufferListAdapter.getCount());
        }

        // Update UI
        updateUI();
    }

    private String getEolCharacters() {
        switch (mEolCharactersId) {
            case 1:
                return "\r";
            case 2:
                return "\n\r";
            case 3:
                return "\r\n";
            default:
                return "\n";
        }
    }

    private int getEolCharactersStringId() {
        switch (mEolCharactersId) {
            case 1:
                return R.string.uart_eolmode_r;
            case 2:
                return R.string.uart_eolmode_nr;
            case 3:
                return R.string.uart_eolmode_rn;
            default:
                return R.string.uart_eolmode_n;
        }
    }

    public void onClickCopy(View view) {
        String text = mBufferTextView.getText().toString(); // mShowDataInHexFormat ? mHexSpanBuffer.toString() : mAsciiSpanBuffer.toString();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("UART", text);
            clipboard.setPrimaryClip(clip);
        }
    }

    public void onClickClear(View view) {
        mTextSpanBuffer.clear();
        mDataBufferLastSize = 0;
        mBufferListAdapter.clear();
        mBufferTextView.setText("");

        mDataBuffer.clear();
        mSentBytes = 0;
        mReceivedBytes = 0;
        updateUI();
    }

    public void onClickShare(View view) {
        String textToSend = mBufferTextView.getText().toString(); // (mShowDataInHexFormat ? mHexSpanBuffer : mAsciiSpanBuffer).toString();

        if (textToSend.length() > 0) {

            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, textToSend);
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.uart_share_subject));     // subject will be used if sent to an email app
            sendIntent.setType("text/*");       // Note: don't use text/plain because dropbox will not appear as destination
            // startActivity(sendIntent);
            startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.uart_sharechooser_title)));      // Always show the app-chooser
        } else {
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.uart_share_empty))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    private void setDisplayFormatToTimestamp(boolean enabled) {
        mIsTimestampDisplayMode = enabled;
        mBufferTextView.setVisibility(enabled ? View.GONE : View.VISIBLE);
        mBufferListView.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }

    // region Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        check = 0;
        // Inflate the menu; this adds items to the action bar if it is present.

       // AlertDialog.Builder builder = new AlertDialog.Builder(this);
        getMenuInflater().inflate(R.menu.menu_uart, menu);

        // Mqtt
        //mMqttMenuItem = menu.findItem(R.id.action_mqttsettings);
        mMqttMenuItemAnimationHandler = new Handler();
        mMqttMenuItemAnimationRunnable.run();

        // DisplayMode
        MenuItem displayModeMenuItem = menu.findItem(R.id.action_displaymode);
        displayModeMenuItem.setTitle(String.format(getString(R.string.uart_action_displaymode_format), getString(mIsTimestampDisplayMode ? R.string.uart_displaymode_timestamp : R.string.uart_displaymode_text)));
        SubMenu displayModeSubMenu = displayModeMenuItem.getSubMenu();
        if (mIsTimestampDisplayMode) {
            MenuItem displayModeTimestampMenuItem = displayModeSubMenu.findItem(R.id.action_displaymode_timestamp);
            displayModeTimestampMenuItem.setChecked(true);
        } else {
            MenuItem displayModeTextMenuItem = displayModeSubMenu.findItem(R.id.action_displaymode_text);
            displayModeTextMenuItem.setChecked(true);
        }

        // DataMode
        MenuItem dataModeMenuItem = menu.findItem(R.id.action_datamode);
        dataModeMenuItem.setTitle(String.format(getString(R.string.uart_action_datamode_format), getString(mShowDataInHexFormat ? R.string.uart_format_hexadecimal : R.string.uart_format_ascii)));
        SubMenu dataModeSubMenu = dataModeMenuItem.getSubMenu();
        if (mShowDataInHexFormat) {
            MenuItem dataModeHexMenuItem = dataModeSubMenu.findItem(R.id.action_datamode_hex);
            dataModeHexMenuItem.setChecked(true);
        } else {
            MenuItem dataModeAsciiMenuItem = dataModeSubMenu.findItem(R.id.action_datamode_ascii);
            dataModeAsciiMenuItem.setChecked(true);
        }

        // Echo
        MenuItem echoMenuItem = menu.findItem(R.id.action_echo);
        echoMenuItem.setTitle(R.string.uart_action_echo);
        echoMenuItem.setChecked(mIsEchoEnabled);

        // Eol
        MenuItem eolMenuItem = menu.findItem(R.id.action_eol);
        eolMenuItem.setTitle(R.string.uart_action_eol);
        eolMenuItem.setChecked(mIsEolEnabled);

        //PHONENUMBER
        MenuItem PhoneNumber = menu.findItem(R.id.phoneNumber);
        PhoneNumber.setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(UartActivity.this);

                        final EditText input = new EditText(UartActivity.this);
                        input.setText(phoneNumber);
                        input.setInputType(InputType.TYPE_CLASS_PHONE);
                        builder.setView(input);

                        //TODO replace text with string resources (R.string.xx)
                        builder.setMessage("Enter number of selected contact")
                                .setTitle("Change Emergency Contact");

                        //set action on POSITIVE (ok) button press
                        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //collect phone number string, check for validity
                                String newNum = input.getText().toString();
                                if(newNum != phoneNumber) {
                                    File fileDir = getFilesDir();

                                    File file = new File(fileDir, "contactNumber");

                                    FileOutputStream outputStream;
                                    try {
                                        outputStream = openFileOutput("contactNumber", Context.MODE_PRIVATE);
                                        outputStream.write(newNum.getBytes());
                                        outputStream.close();

                                    } catch (Exception e) {
                                        Toast.makeText(UartActivity.this, "You should never see this",
                                                Toast.LENGTH_SHORT).show();
                                    }

                                    //make sure that this is legit

                                    phoneNumber = newNum;
                                }
                            }
                        });


                        //action for NEGATIVE (cancel) button press
                        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //don't change number
                                dialog.cancel();
                            }
                        });

                        builder.show();

                        return true;
                    }
                }

        );

        uiOptions = this.getWindow().getDecorView().getSystemUiVisibility();
        newUiOptions = uiOptions;

        MenuItem showShell = menu.findItem(R.id.showShell);
        final ImageView hideShell = findViewById(R.id.imageView);
        showShell.setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if(hideShell.getVisibility() == View.VISIBLE){
                            hideShell.setVisibility(View.GONE);

                            View decorView = getWindow().getDecorView();
                            decorView.setSystemUiVisibility(
                                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                            check = 1;
                            return true;

                        }

                        else{
                            hideShell.setVisibility(View.VISIBLE);
                            View decorView = getWindow().getDecorView();
                            decorView.setSystemUiVisibility(
                                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                            check = 1;
                            return true;
                        }

                    }
                }
        );
        if (check == 1)
        this.getWindow().getDecorView().setSystemUiVisibility(newUiOptions);


        // Eol Characters
        MenuItem eolModeMenuItem = menu.findItem(R.id.action_eolmode);
        eolModeMenuItem.setTitle(String.format(getString(R.string.uart_action_eolmode_format), getString(getEolCharactersStringId())));
        SubMenu eolModeSubMenu = eolModeMenuItem.getSubMenu();
        int selectedEolCharactersSubMenuId;
        switch (mEolCharactersId) {
            case 1:
                selectedEolCharactersSubMenuId = R.id.action_eolmode_r;
                break;
            case 2:
                selectedEolCharactersSubMenuId = R.id.action_eolmode_nr;
                break;
            case 3:
                selectedEolCharactersSubMenuId = R.id.action_eolmode_rn;
                break;
            default:
                selectedEolCharactersSubMenuId = R.id.action_eolmode_n;
                break;
        }
        MenuItem selectedEolCharacterMenuItem = eolModeSubMenu.findItem(selectedEolCharactersSubMenuId);
        selectedEolCharacterMenuItem.setChecked(true);

        return true;
    }

    private Runnable mMqttMenuItemAnimationRunnable = new Runnable() {
        @Override
        public void run() {
            updateMqttStatus();
            mMqttMenuItemAnimationHandler.postDelayed(mMqttMenuItemAnimationRunnable, 500);
        }
    };
    private int mMqttMenuItemAnimationFrame = 0;

    private void updateMqttStatus() {
        if (mMqttMenuItem == null)
            return;      // Hack: Sometimes this could have not been initialized so we don't update icons

        MqttManager mqttManager = MqttManager.getInstance(this);
        MqttManager.MqqtConnectionStatus status = mqttManager.getClientStatus();

        if (status == MqttManager.MqqtConnectionStatus.CONNECTING) {
            final int kConnectingAnimationDrawableIds[] = {R.drawable.mqtt_connecting1, R.drawable.mqtt_connecting2, R.drawable.mqtt_connecting3};
            mMqttMenuItem.setIcon(kConnectingAnimationDrawableIds[mMqttMenuItemAnimationFrame]);
            mMqttMenuItemAnimationFrame = (mMqttMenuItemAnimationFrame + 1) % kConnectingAnimationDrawableIds.length;
        } else if (status == MqttManager.MqqtConnectionStatus.CONNECTED) {
            mMqttMenuItem.setIcon(R.drawable.mqtt_connected);
            mMqttMenuItemAnimationHandler.removeCallbacks(mMqttMenuItemAnimationRunnable);
        } else {
            mMqttMenuItem.setIcon(R.drawable.mqtt_disconnected);
            mMqttMenuItemAnimationHandler.removeCallbacks(mMqttMenuItemAnimationRunnable);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        final int id = item.getItemId();

        switch (id) {
            case R.id.action_help:
                //startHelp();
                return true;

            case R.id.action_connected_settings:
                //startConnectedSettings();
                return true;

            case R.id.action_refreshcache:
                if (mBleManager != null) {
                    mBleManager.refreshDeviceCache();
                }
                break;

//            case R.id.action_mqttsettings:
//                Intent intent = new Intent(this, MqttUartSettingsActivity.class);
//                startActivityForResult(intent, kActivityRequestCode_MqttSettingsActivity);
//                break;

            case R.id.action_displaymode_timestamp:
                setDisplayFormatToTimestamp(true);
                recreateDataView();
                invalidateOptionsMenu();
                return true;

            case R.id.action_displaymode_text:
                setDisplayFormatToTimestamp(false);
                recreateDataView();
                invalidateOptionsMenu();
                return true;

            case R.id.action_datamode_hex:
                mShowDataInHexFormat = true;
                recreateDataView();
                invalidateOptionsMenu();
                return true;

            case R.id.action_datamode_ascii:
                mShowDataInHexFormat = false;
                recreateDataView();
                invalidateOptionsMenu();
                return true;

            case R.id.action_echo:
                mIsEchoEnabled = !mIsEchoEnabled;
                invalidateOptionsMenu();
                return true;

            case R.id.action_eol:
                mIsEolEnabled = !mIsEolEnabled;
                invalidateOptionsMenu();
                return true;

            case R.id.action_eolmode_n:
                mEolCharactersId = 0;
                invalidateOptionsMenu();
                return true;

            case R.id.action_eolmode_r:
                mEolCharactersId = 1;
                invalidateOptionsMenu();
                return true;


            case R.id.action_eolmode_nr:
                mEolCharactersId = 2;
                invalidateOptionsMenu();
                return true;

            case R.id.action_eolmode_rn:
                mEolCharactersId = 3;
                invalidateOptionsMenu();
                return true;

        }

        return super.onOptionsItemSelected(item);
    }

    /*private void startConnectedSettings() {
        // Launch connected settings activity
        Intent intent = new Intent(this, ConnectedSettingsActivity.class);
        startActivityForResult(intent, kActivityRequestCode_ConnectedSettingsActivity);
    }*/

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == kActivityRequestCode_ConnectedSettingsActivity && resultCode == RESULT_OK) {
            finish();
        } else if (requestCode == kActivityRequestCode_MqttSettingsActivity && resultCode == RESULT_OK) {

        }
    }

    /*private void startHelp() {
        // Launch app help activity
        Intent intent = new Intent(this, CommonHelpActivity.class);
        intent.putExtra("title", getString(R.string.uart_help_title));
        intent.putExtra("help", "uart_help.html");
        startActivity(intent);
    }*/
    // endregion

    // region BleManagerListener
    /*
    @Override
    public void onConnected() {

    }

    @Override
    public void onConnecting() {

    }
*/


    @Override
    public void onDisconnected() {
        super.onDisconnected();
        Log.d(TAG, "Disconnected. Back to previous activity");
        finish();
    }

    @Override
    public void onServicesDiscovered() {
        super.onServicesDiscovered();
        enableRxNotifications();
    }

    @Override
    public synchronized void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        super.onDataAvailable(characteristic);
        // UART RX
        if (characteristic.getService().getUuid().toString().equalsIgnoreCase(UUID_SERVICE)) {
            if (characteristic.getUuid().toString().equalsIgnoreCase(UUID_RX)) {
                final byte[] bytes = characteristic.getValue();
                mReceivedBytes += bytes.length;

                final UartDataChunk dataChunk = new UartDataChunk(System.currentTimeMillis(), UartDataChunk.TRANSFERMODE_RX, bytes);
                mDataBuffer.add(dataChunk);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mIsTimestampDisplayMode) {
                            final String currentDateTimeString = DateFormat.getTimeInstance().format(new Date(dataChunk.getTimestamp()));
                            final String formattedData = mShowDataInHexFormat ? BleUtils.bytesToHex2(bytes) : BleUtils.bytesToText(bytes, true);

                            mBufferListAdapter.add(new TimestampData("[" + currentDateTimeString + "] RX: " + formattedData + '\n', mRxColor));
                            //mBufferListAdapter.add("[" + currentDateTimeString + "] RX: " + formattedData);
                            //mBufferListView.smoothScrollToPosition(mBufferListAdapter.getCount() - 1);
                            mBufferListView.setSelection(mBufferListAdapter.getCount());
                        }
                        updateUI();
                    }
                });

                // MQTT publish to RX
                MqttSettings settings = MqttSettings.getInstance(UartActivity.this);
                if (settings.isPublishEnabled()) {
                    String topic = settings.getPublishTopic(MqttUartSettingsActivity.kPublishFeed_RX);
                    final int qos = settings.getPublishQos(MqttUartSettingsActivity.kPublishFeed_RX);
                    final String text = BleUtils.bytesToText(bytes, false);
                    mMqttManager.publish(topic, text, qos);
                }
            }
        }
    }


/*
    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {

    }

    @Override
    public void onReadRemoteRssi(int rssi) {

    }
    */
    // endregion

    private void addTextToSpanBuffer(SpannableStringBuilder spanBuffer, String text, int color) {

        if (kUseColorsForData) {
            final int from = spanBuffer.length();
            spanBuffer.append(text);
            spanBuffer.setSpan(new ForegroundColorSpan(color), from, from + text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            spanBuffer.append(text);
        }
    }

    private void updateUI() {
        mSentBytesTextView.setText(String.format(getString(R.string.uart_sentbytes_format), mSentBytes));
        mReceivedBytesTextView.setText(String.format(getString(R.string.uart_receivedbytes_format), mReceivedBytes));
    }

    private int mDataBufferLastSize = 0;

    private void updateTextDataUI() {

        if (!mIsTimestampDisplayMode) {
            if (mDataBufferLastSize != mDataBuffer.size()) {

                final int bufferSize = mDataBuffer.size();
                if (bufferSize > maxPacketsToPaintAsText) {
                    mDataBufferLastSize = bufferSize - maxPacketsToPaintAsText;
                    mTextSpanBuffer.clear();
                    addTextToSpanBuffer(mTextSpanBuffer, getString(R.string.uart_text_dataomitted) + "\n", kInfoColor);
                }

                // Log.d(TAG, "update packets: "+(bufferSize-mDataBufferLastSize));
                for (int i = mDataBufferLastSize; i < bufferSize; i++) {
                    final UartDataChunk dataChunk = mDataBuffer.get(i);
                    final boolean isRX = dataChunk.getMode() == UartDataChunk.TRANSFERMODE_RX;
                    final byte[] bytes = dataChunk.getData();
                    final String formattedData = mShowDataInHexFormat ? BleUtils.bytesToHex2(bytes) : BleUtils.bytesToText(bytes, true);
                    addTextToSpanBuffer(mTextSpanBuffer, formattedData + "\n", isRX ? mRxColor : mTxColor);
                    if (formattedData.charAt(4) =='p' || formattedData.charAt(3) == 'p' || formattedData.charAt(2) == 'p'){
                        smsType = 0;
                    }
                    else{
                        smsType = 1;
                    }
                }

                mDataBufferLastSize = mDataBuffer.size();
                mBufferTextView.setText(mTextSpanBuffer);
                mBufferTextView.setSelection(0, mTextSpanBuffer.length());        // to automatically scroll to the end
                System.out.println("motion?");
                //sms stuff
                final SmsManager smsManagger = SmsManager.getDefault();
                //TODO DON'T PUSH WITH A PHONE NUMBER
                if(smsType ==1)
                smsManagger.sendTextMessage(phoneNumber, null, "I am in need of immediate assistance(motion detected)", null, null);
                else
                    smsManagger.sendTextMessage(phoneNumber, null, "I am in need of assistance(buton pressed)", null, null);


            }
        }
    }

    private void recreateDataView() {

        if (mIsTimestampDisplayMode) {
            mBufferListAdapter.clear();

            final int bufferSize = mDataBuffer.size();
            for (int i = 0; i < bufferSize; i++) {

                final UartDataChunk dataChunk = mDataBuffer.get(i);
                final boolean isRX = dataChunk.getMode() == UartDataChunk.TRANSFERMODE_RX;
                final byte[] bytes = dataChunk.getData();
                final String formattedData = mShowDataInHexFormat ? BleUtils.bytesToHex2(bytes) : BleUtils.bytesToText(bytes, true);

                final String currentDateTimeString = DateFormat.getTimeInstance().format(new Date(dataChunk.getTimestamp()));
                mBufferListAdapter.add(new TimestampData("[" + currentDateTimeString + "] " + (isRX ? "RX" : "TX") + ": " + formattedData, isRX ? mRxColor : mTxColor));
//                mBufferListAdapter.add("[" + currentDateTimeString + "] " + (isRX ? "RX" : "TX") + ": " + formattedData);
            }
            mBufferListView.setSelection(mBufferListAdapter.getCount());
        } else {
            mDataBufferLastSize = 0;
            mTextSpanBuffer.clear();
            mBufferTextView.setText("");
        }
    }

    public void imageViewClick(View view) {
        ImageView imageView =  findViewById(R.id.imageView);
        imageView.setVisibility(View.GONE);

        uiOptions = this.getWindow().getDecorView().getSystemUiVisibility();
        newUiOptions = uiOptions;
        newUiOptions ^= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        newUiOptions ^= View.SYSTEM_UI_FLAG_FULLSCREEN;
        newUiOptions ^= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        this.getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
    }


    // region DataFragment
    public static class DataFragment extends Fragment {
        private boolean mShowDataInHexFormat;
        private SpannableStringBuilder mTextSpanBuffer;
        private ArrayList<UartDataChunk> mDataBuffer;
        private int mSentBytes;
        private int mReceivedBytes;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);
        }
    }

    private void restoreRetainedDataFragment() {
        // find the retained fragment
        FragmentManager fm = getFragmentManager();
        mRetainedDataFragment = (DataFragment) fm.findFragmentByTag(TAG);

        if (mRetainedDataFragment == null) {
            // Create
            mRetainedDataFragment = new DataFragment();
            fm.beginTransaction().add(mRetainedDataFragment, TAG).commit();

            mDataBuffer = new ArrayList<>();
            mTextSpanBuffer = new SpannableStringBuilder();
        } else {
            // Restore status
            mShowDataInHexFormat = mRetainedDataFragment.mShowDataInHexFormat;
            mTextSpanBuffer = mRetainedDataFragment.mTextSpanBuffer;
            mDataBuffer = mRetainedDataFragment.mDataBuffer;
            mSentBytes = mRetainedDataFragment.mSentBytes;
            mReceivedBytes = mRetainedDataFragment.mReceivedBytes;
        }
    }

    private void saveRetainedDataFragment() {
        mRetainedDataFragment.mShowDataInHexFormat = mShowDataInHexFormat;
        mRetainedDataFragment.mTextSpanBuffer = mTextSpanBuffer;
        mRetainedDataFragment.mDataBuffer = mDataBuffer;
        mRetainedDataFragment.mSentBytes = mSentBytes;
        mRetainedDataFragment.mReceivedBytes = mReceivedBytes;
    }
    // endregion


    // region MqttManagerListener

    @Override
    public void onMqttConnected() {
        updateMqttStatus();
    }

    @Override
    public void onMqttDisconnected() {
        updateMqttStatus();
    }

    @Override
    public void onMqttMessageArrived(String topic, MqttMessage mqttMessage) {
        final String message = new String(mqttMessage.getPayload());

        //Log.d(TAG, "Mqtt messageArrived from topic: " +topic+ " message: "+message);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                uartSendData(message, true);       // Don't republish to mqtt something received from mqtt
            }
        });

    }

    // endregion


    // region TimestampAdapter
    private class TimestampData {
        String text;
        int textColor;

        TimestampData(String text, int textColor) {
            this.text = text;
            this.textColor = textColor;
        }
    }

    private class TimestampListAdapter extends ArrayAdapter<TimestampData> {

        TimestampListAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.layout_uart_datachunkitem, parent, false);
            }

            TimestampData data = getItem(position);
            TextView textView = (TextView) convertView;
            textView.setText(data.text);
            textView.setTextColor(data.textColor);

            return convertView;
        }
    }
    // endregion
}
