package com.example.nicholas.mybttouchmouse;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    private String mConnectedDeviceName = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothCommandService mCommandService = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        final FrameLayout target = (FrameLayout) findViewById(R.id.panel);
        target.post(new Runnable() {
            @Override
            public void run() {
                target.setOnTouchListener(new PanelTouchLinstner());
            }
        });

        ButtonListener l3 = new ButtonListener(3);
        Button rightButton = (Button) findViewById(R.id.rightButton);
        rightButton.setOnTouchListener(l3);

        ButtonListener l1 = new ButtonListener(1);
        Button leftButton = (Button) findViewById(R.id.leftButton);
        leftButton.setOnTouchListener(l1);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            if (mCommandService == null) {
                setupCommand();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mCommandService != null) {
            if (mCommandService.getState() == BluetoothCommandService.STATE_NONE) {
                mCommandService.start();
            }
        }
    }

    private void setupCommand() {
        mCommandService = new BluetoothCommandService(this, mHandler);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCommandService != null) {
            mCommandService.stop();
        }
    }

    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothCommandService.STATE_CONNECTED:
                            String subtitle = getString(R.string.title_connected_to) + mConnectedDeviceName;
                            getActionBar().setSubtitle(subtitle);
                            break;
                        case BluetoothCommandService.STATE_CONNECTING:
                            getActionBar().setSubtitle(R.string.title_connecting);
                            break;
                        case BluetoothCommandService.STATE_LISTEN:
                        case BluetoothCommandService.STATE_NONE:
                            getActionBar().setSubtitle(R.string.title_not_connected);
                            break;
                    }
                    break;
                case MESSAGE_DEVICE_NAME:
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to " + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    mCommandService.connect(device);
                }
                break;
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                    setupCommand();
                } else {
                    Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.scan:
                Intent serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                return true;
            case R.id.discoverable:
                ensureDiscoverable();
                return true;
        }
        return false;
    }


    private class PanelTouchLinstner implements View.OnTouchListener {
        float[] temp = new float[]{-1.0f, -1.0f};
        boolean moved = false;
        boolean click = false;
        long clickTime = -1;
        private Handler mHandlerTime = new Handler();
        private Runnable sendClick = new Runnable() {
            @Override
            public void run() {
                mCommandService.pushString("c1");
                click = false;
            }
        };

        @Override
        public boolean onTouch(View v, MotionEvent evt) {
            int n;
            int action = evt.getAction();
            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    Log.d("mouse_down", evt.getX(0) + "," + evt.getY(0));
                    if (click && checkSame(evt)) {
                        removeReadyToSend();
                    }
                    moved = false;
                    temp[0] = evt.getX(0);
                    temp[1] = evt.getY(0);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    Log.d("mouse_up", evt.getX(0) + "," + evt.getY(0));
                    if (!moved && evt.getX(0) == temp[0] && evt.getY(0) == temp[1]) {
                        click = true;
                        readyToSend(sendClick);
                    } else {
                        mCommandService.pushString("u1");
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    moved = true;
                    n = evt.getHistorySize();
                    try {
                        for (int j = 0; j < n; j++) {
                            float x = evt.getHistoricalX(0, j);
                            float y = evt.getHistoricalY(0, j);
                            float dx = (x - temp[0]), dy = (y - temp[1]);
                            mCommandService.pushString("m=" + (int) dx + ":" + (int) dy);
                            temp[0] = x;
                            temp[1] = y;
                        }
                    } catch (IllegalArgumentException e) {
                    }
                    break;
                default:
                    return false;
            }
            return true;
        }

        private boolean checkSame(MotionEvent evt) {
            float dx = Math.abs(evt.getX(0) - temp[0]);
            float dy = Math.abs(evt.getY(0) - temp[1]);
            double r = Math.sqrt(dx * dx + dy * dy);
            Log.d("R", String.valueOf(r) + "   " + dx + "," + dy);
            return r <= 50;
        }

        private void readyToSend(Runnable sendClick2) {
            Log.d("mouse", "readyToSend");
            clickTime = System.currentTimeMillis();
            mHandlerTime.postDelayed(sendClick, 300);
        }

        private void removeReadyToSend() {
            Log.d("mouse", "removeReadyToSend");
            if (System.currentTimeMillis() - clickTime < 300) {
                mHandlerTime.removeCallbacks(sendClick);
                mCommandService.pushString("d1");
                click = false;
            }
            clickTime = -1;
        }
    }

    private class ButtonListener implements View.OnTouchListener {
        private int buttonId = 0;

        ButtonListener(int buttonId) {
            this.buttonId = buttonId;
        }

        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                mCommandService.pushString("u" + buttonId);
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mCommandService.pushString("d" + buttonId);
            }
            return false;
        }
    }
}
