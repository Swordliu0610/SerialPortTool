package com.unistrong.system.serialporttool;

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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class MainActivity extends AppCompatActivity {

    private EditText gOutputBox     = null;
    private EditText gSerialPortBox = null;
    private EditText gBaudRateBox   = null;
    private Button   gOpenButton    = null;
    private Button   gSendButton    = null;
    private CheckBox gLoopBox       = null;
    private EditText gInputBox      = null;

    private int gScreenWidth     = 0;
    private int gScreenHeight    = 0;
    private int gScreenDpi       = 0;
    private boolean gOpenFlag    = false;
    private boolean gLoopFlag    = false;
    private boolean gStopFlag    = false;

    private SerialPort     gSerialPort   = null;
    protected InputStream  gInputStream  = null;
    protected OutputStream gOutputStream = null;

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

        // Default set send button and loop check box disabled.
        gSendButton.setEnabled(false);
        gLoopBox.setEnabled(false);

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

            // Create thread to read data.
            gReadingThread = new ReadingThread();
            gReadingThread.start();
            Log.d(TAG, "Open Serial Port" + mPath + " Success!!!");
        }
        else // close Serial Port.
        {
            if (gSerialPort != null) {
                gInputStream  = null;
                gOutputStream = null;
                gSerialPort.close();
                gSerialPort = null;
                gOpenFlag = false;
                gOpenButton.setText(R.string.text_of_open_button);
            }

            if (gReadingThread != null) {
                gReadingThread.interrupt();
            }

            // Disable send button and loop checkbox
            gSendButton.setEnabled(false);
            gLoopBox.setEnabled(false);
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

                    byte[] mBuffer = new byte[64];
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
            }
        });
    }
}
