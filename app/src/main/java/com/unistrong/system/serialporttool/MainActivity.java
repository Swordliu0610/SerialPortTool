package com.unistrong.system.serialporttool;

import android.Manifest;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Button;
import android.widget.CheckBox;
import android.util.DisplayMetrics;
import android_serialport_api.SerialPort;
import android.util.Log;
import android.view.View;
import android.app.AlertDialog;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import android.support.v7.app.AppCompatActivity;
import android.content.pm.PackageManager;

public class MainActivity extends AppCompatActivity {

    private EditText gOutputBox     = null;
    private EditText gSerialPortBox = null;
    private EditText gBaudRateBox   = null;
    private Button   gOpenButton    = null;
    private Button   gSendButton    = null;
    private CheckBox gLoopBox       = null;
    private EditText gInputBox      = null;
    private Button   gSaveButton    = null;

    private int gScreenWidth     = 0;
    private int gScreenHeight    = 0;
    private int gScreenDpi       = 0;
    private boolean gOpenFlag    = false;
    private boolean gLoopFlag    = false;
    private boolean gStopFlag    = false;
    private boolean gSaveFlag    = false;

    private File gFile = null;

    private SerialPort     gSerialPort   = null;
    protected InputStream  gInputStream  = null;
    protected OutputStream gOutputStream = null;


    protected FileOutputStream  gSaveOutStream = null;

    private String gSendData = null;
    private static final String TAG = "SerialPortTool";

    private SendingThread    gSendingThread = null;
    private ReadingThread    gReadingThread = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // load layout xml file.

        DrawMainUI();
    }

    private boolean DrawMainUI() {

        DisplayMetrics dm = getResources().getDisplayMetrics();
        gScreenWidth   = dm.widthPixels;
        gScreenHeight  = dm.heightPixels;
        gScreenDpi     = dm.densityDpi;

        gOutputBox     = findViewById(R.id.OutputBox);
        gSerialPortBox = findViewById(R.id.SerialPort);
        gBaudRateBox   = findViewById(R.id.BaudRate);
        gOpenButton    = findViewById(R.id.OpenButton);
        gSendButton    = findViewById(R.id.SendButton);
        gLoopBox       = findViewById(R.id.LoopBox);
        gInputBox      = findViewById(R.id.InputBox);
        gSaveButton    = findViewById(R.id.SaveButton);

        // Default set send button and loop check box disabled.
        gSendButton.setEnabled(false);
        gLoopBox.setEnabled(false);
        gSaveButton.setEnabled(false);

        // Register Open Button click event.
        gOpenButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                OpenSerialPort();
            }
        });

        // Register send data button click event.
        gSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                gLoopFlag = gLoopBox.isChecked();
                SendData();
            }
        });

        // Register save data button click event.
        gSaveButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                SaveData();
            }
        });
        return true;
    }

    private boolean OpenSerialPort() {
        String mSerialPort;
        String mBaudRateStr;
        String mPath;
        int    mBaudRate;

        if (!gOpenFlag) { // Try to open Serial Port.

            // Get Serial Port name.
            mSerialPort = gSerialPortBox.getText().toString();
            if ("".equals(mSerialPort)) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.title_of_waring_1)
                        .setMessage(R.string.message_of_waring_1)
                        .setPositiveButton(R.string.ok_of_waring, null)
                        .show();
                return false;
            }

            // Get Serial Port baud rate.
            mBaudRateStr = gBaudRateBox.getText().toString();
            if ("".equals(mBaudRateStr)) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.title_of_waring_2)
                        .setMessage(R.string.message_of_waring_2)
                        .setPositiveButton(R.string.ok_of_waring, null)
                        .show();
                return false;
            }
            mBaudRate = Integer.parseInt(mBaudRateStr);
            Log.d(TAG, "SerialPort: " + mSerialPort);
            Log.d(TAG, "BaudRate: " + mBaudRate);

            // Device path.
            mPath = "/dev/" + mSerialPort;
            try {
                gSerialPort = new SerialPort(new File(mPath), mBaudRate, 0);
            } catch (IOException e) {
                Log.d(TAG, "Open Serial Port failed: " + e.toString());
                new AlertDialog.Builder(this)
                        .setTitle(R.string.title_of_waring_3)
                        .setMessage(R.string.message_of_waring_3)
                        .setPositiveButton(R.string.ok_of_waring, null)
                        .show();
                return false;
            }

            //
            // Great!!! We already open Serial Port.
            //
            gInputStream  = gSerialPort.getInputStream();
            gOutputStream = gSerialPort.getOutputStream();
            gOpenFlag = true;
            gOpenButton.setText(R.string.text_of_close_button);

            // Enable send button and loop checkbox
            gSendButton.setEnabled(true);
            gLoopBox.setEnabled(true);
            gSaveButton.setEnabled(true);

            // Create thread to read data.
            gReadingThread = new ReadingThread();
            gReadingThread.start();
            Log.d(TAG, "Open Serial Port" + mPath + " Success!!!");
        }
        else // close Serial Port.
        {
            // stop read data thread before close serial port.
            if (gReadingThread != null) {
                gReadingThread.interrupt();
            }

            if (gSerialPort != null) {
                gInputStream  = null;
                gOutputStream = null;
                gSerialPort.close();
                gSerialPort = null;
                gOpenFlag = false;
                gOpenButton.setText(R.string.text_of_open_button);
            }

            gSendButton.setEnabled(false); // Disable send button.
            gLoopBox.setEnabled(false); // Disable loop checkbox.
            gSaveButton.setEnabled(false); // Disable save button.

            StopToSaveData(); // stop save data.
        }
        return true;
    }

    private boolean SendData(){

        if (gSerialPort == null) {
            return false;
        }
        gSendData = gInputBox.getText().toString();
        if ("".equals(gSendData)) {
            return false;
        }

        if (gLoopFlag) { // if select loop, create a new thread to send data.
            if (!gStopFlag) {
                gSendingThread = new SendingThread();
                gSendingThread.start();

                gStopFlag = true;
                gSendButton.setText(R.string.text_of_stop_button);
                gLoopBox.setEnabled(false); // Disable loop checkbox during send data.
            }
            else
            {
                if (gSendingThread != null) {
                    gSendingThread.interrupt();
                }
                gStopFlag = false;
                gSendButton.setText(R.string.text_of_send_button);
                gLoopBox.setEnabled(true);
            }
        }
        else
        {
            SendDataOnce();
        }
        return true;
    }

    private boolean SendDataOnce() {
        char[] mTempBuffer = gSendData.toCharArray();
        byte[] mBuffer     = new byte[gSendData.length()];
        int Index;

        for (Index = 0; Index < gSendData.length(); Index++) {
            mBuffer[Index] = (byte)mTempBuffer[Index];
        }

        try {
            if (gOutputStream != null) {
                gOutputStream.write(mBuffer);
            } else {
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } // try

        return true;
    }
    private class SendingThread extends Thread {
        @Override
        public void run() {

            char[] mTempBuffer = gSendData.toCharArray();
            byte[] mBuffer     = new byte[gSendData.length()];
            int Index;

            for (Index = 0; Index < gSendData.length(); Index++) {
                mBuffer[Index] = (byte)mTempBuffer[Index];
            }

            while (!isInterrupted()) {
                try {
                    if (gOutputStream != null) {
                        gOutputStream.write(mBuffer);
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                            return;
                        }
                    } else {
                        return;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                } // try
            } // while (!isInterrupted())
        } // run ()
    } // private class SendingThread extends Thread

    private class ReadingThread extends Thread {
        public void run() {
            while(!isInterrupted()) {
                int mSize;
                try {
                    if (gInputStream == null) {
                        return;
                    }

                    byte[] mBuffer = new byte[8192];
                    mSize = gInputStream.read(mBuffer);
                    if (mSize > 0) {
                        onDataReceived(mBuffer, mSize);
                    }
                } catch(IOException e) {
                    e.printStackTrace();
                    return;
                }

            }
        }
    }

    protected void onDataReceived(final byte[] buffer, final int size) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (gOutputBox != null) {
                    gOutputBox.append(new String(buffer, 0, size));
                }
                if (gSaveFlag) {
                    try {
                        byte[] TempBuffer = new byte[size];
                        int mIndex;

                        for (mIndex = 0; mIndex < size; mIndex++) {
                            TempBuffer[mIndex] = buffer[mIndex];
                        }
                        gSaveOutStream.write(TempBuffer);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.d(TAG, "Write failed.");
                    }
                }
            }
        });
    }

    private boolean SaveData() {

        if (!gSaveFlag) { // Start to save.
            StartToSaveData();
        }
        else // stop to save.
        {
            StopToSaveData();
        }
        return true;
    }

    private void StartToSaveData() {
        if (!checkPermission()) {
            return;
        }
        try {
            Log.d(TAG, Environment.getExternalStorageDirectory().getPath());
            gSaveOutStream = new FileOutputStream(Environment.getExternalStorageDirectory().getPath() + "/" +
                    gSerialPortBox.getText().toString() +
                    ".txt", true);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        gSaveButton.setText(R.string.text_stop_of_save_button);
        gSaveFlag = true;
    }

    private void StopToSaveData() {
        gSaveFlag = false;
        gSaveButton.setText(R.string.text_save_of_save_button);
        try {
            gSaveOutStream.flush();
            gSaveOutStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean checkPermission() {
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE};
            Log.d(TAG, "Current permission:" + checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE));
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(PERMISSIONS_STORAGE, PackageManager.PERMISSION_GRANTED);
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Log.d(TAG, "Request permission failed.");
                    return false;
                }
            }
        return true;
    }
}
