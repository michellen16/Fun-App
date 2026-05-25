package edu.uic.cs478.s2026.funclient;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import edu.uic.cs478.s2026.funcenter.aidl.IFunCenterService;

/**
 * FunClient MainActivity - Binds to FunCenter's service and provides UI
 * for requesting pictures and controlling audio playback.
 *
 * Lifecycle behavior:
 * - onStart():   binds to the service
 * - onStop():    unbinds from the service (audio keeps playing)
 * - onDestroy(): stops the service entirely (stops audio)
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FunClient";

    // FunCenter package and service class constants
    private static final String FUNCENTER_PACKAGE =
            "edu.uic.cs478.s2026.funcenter";
    private static final String FUNCENTER_SERVICE =
            "edu.uic.cs478.s2026.funcenter.FunCenterService";

    // Service reference
    private IFunCenterService mService = null;
    private boolean mBound = false;

    // UI elements
    private TextView tvConnectionStatus;
    private TextView tvPlaybackStatus;
    private TextView tvPicturePlaceholder;
    private TextView tvLog;
    private EditText etPictureNumber;
    private EditText etClipNumber;
    private ImageView ivPicture;
    private Button btnRequestPicture;
    private Button btnPlayClip;
    private Button btnPause;
    private Button btnResume;
    private Button btnStop;

    /**
     * ServiceConnection - handles binding and unbinding callbacks.
     */
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Service connected: " + name);
            mService = IFunCenterService.Stub.asInterface(service);
            mBound = true;

            runOnUiThread(() -> {
                tvConnectionStatus.setText("Status: Connected ✓");
                tvConnectionStatus.setTextColor(0xFF00AA00);
                appendLog("Connected to FunCenter service");

                try {
                    int picCount = mService.getPictureCount();
                    int clipCount = mService.getClipCount();
                    appendLog("Available: " + picCount + " pictures, "
                            + clipCount + " clips");
                } catch (RemoteException e) {
                    appendLog("Error querying counts: " + e.getMessage());
                }
            });

            enableButtons(true);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Service disconnected: " + name);
            mService = null;
            mBound = false;

            runOnUiThread(() -> {
                tvConnectionStatus.setText("Status: Disconnected ✗");
                tvConnectionStatus.setTextColor(0xFFFF0000);
                appendLog("Disconnected from FunCenter service");
            });

            enableButtons(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI references
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvPlaybackStatus = findViewById(R.id.tvPlaybackStatus);
        tvPicturePlaceholder = findViewById(R.id.tvPicturePlaceholder);
        tvLog = findViewById(R.id.tvLog);
        etPictureNumber = findViewById(R.id.etPictureNumber);
        etClipNumber = findViewById(R.id.etClipNumber);
        ivPicture = findViewById(R.id.ivPicture);
        btnRequestPicture = findViewById(R.id.btnRequestPicture);
        btnPlayClip = findViewById(R.id.btnPlayClip);
        btnPause = findViewById(R.id.btnPause);
        btnResume = findViewById(R.id.btnResume);
        btnStop = findViewById(R.id.btnStop);

        // Initially disable buttons until service is connected
        enableButtons(false);

        // Set up button click listeners
        setupClickListeners();

        // Start the service so it persists even when unbound
        startFunCenterService();

        appendLog("FunClient started");
    }

    /**
     * Starts the FunCenter service so it runs independently of binding.
     * This ensures audio continues playing when the activity is stopped.
     */
    private void startFunCenterService() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                FUNCENTER_PACKAGE, FUNCENTER_SERVICE));
        try {
            startService(intent);
            Log.d(TAG, "startService() called");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start service: " + e.getMessage());
            appendLog("Warning: Could not start service. Is FunCenter installed?");
        }
    }

    /**
     * Binds to the FunCenter service.
     */
    private void bindToService() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                FUNCENTER_PACKAGE, FUNCENTER_SERVICE));
        intent.setAction("edu.uic.cs478.s2026.funcenter.aidl.IFunCenterService");

        boolean result = bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "bindService() returned: " + result);
        if (!result) {
            appendLog("Failed to bind. Ensure FunCenter app is installed.");
            Toast.makeText(this,
                    "Cannot bind to FunCenter. Is it installed?",
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Sets up click listeners for all buttons.
     */
    private void setupClickListeners() {

        // ---- Request Picture ----
        btnRequestPicture.setOnClickListener(v -> {
            if (!mBound || mService == null) {
                showNotConnected();
                return;
            }

            String input = etPictureNumber.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "Enter a picture number",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            int picNum;
            try {
                picNum = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // Request picture on background thread so it doesn't block UI
            // (allows requesting pictures while audio is playing)
            new Thread(() -> {
                try {
                    appendLogOnUiThread("Requesting picture #" + picNum + "...");
                    byte[] pictureData = mService.getPicture(picNum);

                    if (pictureData != null && pictureData.length > 0) {
                        Bitmap bitmap = BitmapFactory.decodeByteArray(
                                pictureData, 0, pictureData.length);

                        runOnUiThread(() -> {
                            if (bitmap != null) {
                                ivPicture.setImageBitmap(bitmap);
                                tvPicturePlaceholder.setVisibility(View.GONE);
                                appendLog("Picture #" + picNum + " loaded ("
                                        + pictureData.length + " bytes)");
                            } else {
                                appendLog("Failed to decode picture #" + picNum);
                                Toast.makeText(this,
                                        "Failed to decode picture",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        runOnUiThread(() -> {
                            appendLog("Picture #" + picNum
                                    + " not found or invalid number");
                            Toast.makeText(this,
                                    "Picture not found. Check the number.",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "RemoteException getting picture", e);
                    appendLogOnUiThread("Error: " + e.getMessage());
                }
            }).start();
        });

        // ---- Play Clip ----
        btnPlayClip.setOnClickListener(v -> {
            if (!mBound || mService == null) {
                showNotConnected();
                return;
            }

            String input = etClipNumber.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "Enter a clip number",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            int clipNum;
            try {
                clipNum = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                boolean success = mService.playClip(clipNum);
                if (success) {
                    tvPlaybackStatus.setText("Playback: Playing clip #" + clipNum);
                    appendLog("Playing clip #" + clipNum);
                } else {
                    tvPlaybackStatus.setText("Playback: Error");
                    appendLog("Failed to play clip #" + clipNum);
                    Toast.makeText(this,
                            "Failed to play clip. Check the number.",
                            Toast.LENGTH_SHORT).show();
                }
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException playing clip", e);
                appendLog("Error: " + e.getMessage());
            }
        });

        // ---- Pause ----
        btnPause.setOnClickListener(v -> {
            if (!mBound || mService == null) {
                showNotConnected();
                return;
            }

            try {
                boolean success = mService.pauseClip();
                if (success) {
                    tvPlaybackStatus.setText("Playback: Paused");
                    appendLog("Playback paused");
                } else {
                    appendLog("Nothing to pause");
                    Toast.makeText(this, "No clip is playing",
                            Toast.LENGTH_SHORT).show();
                }
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException pausing", e);
                appendLog("Error: " + e.getMessage());
            }
        });

        // ---- Resume ----
        btnResume.setOnClickListener(v -> {
            if (!mBound || mService == null) {
                showNotConnected();
                return;
            }

            try {
                boolean success = mService.resumeClip();
                if (success) {
                    tvPlaybackStatus.setText("Playback: Playing (resumed)");
                    appendLog("Playback resumed");
                } else {
                    appendLog("Nothing to resume");
                    Toast.makeText(this, "No clip is paused",
                            Toast.LENGTH_SHORT).show();
                }
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException resuming", e);
                appendLog("Error: " + e.getMessage());
            }
        });

        // ---- Stop ----
        btnStop.setOnClickListener(v -> {
            if (!mBound || mService == null) {
                showNotConnected();
                return;
            }

            try {
                boolean success = mService.stopClip();
                if (success) {
                    tvPlaybackStatus.setText("Playback: Stopped");
                    appendLog("Playback stopped");
                } else {
                    appendLog("Nothing to stop");
                    Toast.makeText(this, "No clip is loaded",
                            Toast.LENGTH_SHORT).show();
                }
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException stopping", e);
                appendLog("Error: " + e.getMessage());
            }
        });
    }

    /**
     * Binds to the service when the activity becomes visible.
     */
    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart() - binding to service");
        bindToService();
    }

    /**
     * Unbinds from the service when the activity is no longer visible.
     * The service continues running (and playing audio) because it was
     * also started with startService().
     */
    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop() - unbinding from service");
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
            mService = null;
            enableButtons(false);
            tvConnectionStatus.setText("Status: Unbound (service running)");
            tvConnectionStatus.setTextColor(0xFFFF8800);
        }
    }

    /**
     * Stops the service entirely when the activity is destroyed.
     * This stops any playing audio clip.
     */
    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy() - stopping service");

        // Stop the clip if still connected
        if (mBound && mService != null) {
            try {
                mService.stopClip();
            } catch (RemoteException e) {
                Log.e(TAG, "Error stopping clip on destroy", e);
            }
        }

        // Stop the service entirely
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                FUNCENTER_PACKAGE, FUNCENTER_SERVICE));
        stopService(intent);

        super.onDestroy();
    }

    /**
     * Enables or disables all action buttons.
     */
    private void enableButtons(final boolean enabled) {
        runOnUiThread(() -> {
            btnRequestPicture.setEnabled(enabled);
            btnPlayClip.setEnabled(enabled);
            btnPause.setEnabled(enabled);
            btnResume.setEnabled(enabled);
            btnStop.setEnabled(enabled);
        });
    }

    /**
     * Shows a toast indicating the service is not connected.
     */
    private void showNotConnected() {
        Toast.makeText(this, "Not connected to FunCenter service",
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Appends a log message to the log TextView (must be called on UI thread).
     */
    private void appendLog(String message) {
        String current = tvLog.getText().toString();
        tvLog.setText(current + message + "\n");
    }

    /**
     * Appends a log message from a background thread.
     */
    private void appendLogOnUiThread(String message) {
        runOnUiThread(() -> appendLog(message));
    }
}
