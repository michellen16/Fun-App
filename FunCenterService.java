package edu.uic.cs478.s2026.funcenter;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import edu.uic.cs478.s2026.funcenter.aidl.IFunCenterService;

import java.io.ByteArrayOutputStream;

/**
 * FunCenterService - A bound service that provides pictures and audio playback
 * functionality to client applications via AIDL.
 *
 * Thread Safety: All methods accessing shared mutable state (MediaPlayer,
 * playback flags) are synchronized on a common lock object.
 */
public class FunCenterService extends Service {

    private static final String TAG = "FunCenterService";

    // Lock object for thread safety
    private final Object mLock = new Object();

    // MediaPlayer instance for audio playback
    private MediaPlayer mMediaPlayer;
    private boolean mIsPaused = false;
    private int mCurrentClip = -1;

    // Arrays of resource IDs for pictures and audio clips
    // Place your actual drawable resources (pic1, pic2, pic3) in res/drawable
    // Place your actual raw audio resources (clip1, clip2, clip3) in res/raw
    private final int[] mPictureResIds = {
            R.drawable.pic1,
            R.drawable.pic2,
            R.drawable.pic3
    };

    private final int[] mClipResIds = {
            R.raw.clip1,
            R.raw.clip2,
            R.raw.clip3
    };

    /**
     * AIDL Stub implementation - this is the API exposed to clients.
     * Every method is synchronized on mLock for thread safety.
     */
    private final IFunCenterService.Stub mBinder = new IFunCenterService.Stub() {

        @Override
        public byte[] getPicture(int pictureNumber) throws RemoteException {
            Log.d(TAG, "getPicture() called with pictureNumber=" + pictureNumber);

            if (pictureNumber < 1 || pictureNumber > mPictureResIds.length) {
                Log.w(TAG, "Invalid picture number: " + pictureNumber);
                return null;
            }

            synchronized (mLock) {
                try {
                    int resId = mPictureResIds[pictureNumber - 1];
                    Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resId);
                    if (bitmap == null) {
                        Log.e(TAG, "Failed to decode picture resource for number: "
                                + pictureNumber);
                        return null;
                    }

                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    byte[] byteArray = stream.toByteArray();
                    bitmap.recycle();
                    stream.close();

                    Log.d(TAG, "Returning picture " + pictureNumber
                            + ", size=" + byteArray.length + " bytes");
                    return byteArray;

                } catch (Exception e) {
                    Log.e(TAG, "Error getting picture: " + e.getMessage(), e);
                    return null;
                }
            }
        }

        @Override
        public int getPictureCount() throws RemoteException {
            return mPictureResIds.length;
        }

        @Override
        public int getClipCount() throws RemoteException {
            return mClipResIds.length;
        }

        @Override
        public boolean playClip(int clipNumber) throws RemoteException {
            Log.d(TAG, "playClip() called with clipNumber=" + clipNumber);

            if (clipNumber < 1 || clipNumber > mClipResIds.length) {
                Log.w(TAG, "Invalid clip number: " + clipNumber);
                return false;
            }

            synchronized (mLock) {
                try {
                    // Release any existing MediaPlayer
                    releaseMediaPlayerLocked();

                    int resId = mClipResIds[clipNumber - 1];
                    mMediaPlayer = MediaPlayer.create(
                            FunCenterService.this, resId);

                    if (mMediaPlayer == null) {
                        Log.e(TAG, "Failed to create MediaPlayer for clip: "
                                + clipNumber);
                        return false;
                    }

                    mMediaPlayer.setOnCompletionListener(mp -> {
                        synchronized (mLock) {
                            Log.d(TAG, "Clip playback completed.");
                            releaseMediaPlayerLocked();
                        }
                    });

                    mMediaPlayer.setOnErrorListener((mp, what, extra) -> {
                        synchronized (mLock) {
                            Log.e(TAG, "MediaPlayer error: what=" + what
                                    + ", extra=" + extra);
                            releaseMediaPlayerLocked();
                        }
                        return true;
                    });

                    mMediaPlayer.start();
                    mIsPaused = false;
                    mCurrentClip = clipNumber;
                    Log.d(TAG, "Started playing clip " + clipNumber);
                    return true;

                } catch (Exception e) {
                    Log.e(TAG, "Error playing clip: " + e.getMessage(), e);
                    releaseMediaPlayerLocked();
                    return false;
                }
            }
        }

        @Override
        public boolean pauseClip() throws RemoteException {
            Log.d(TAG, "pauseClip() called");

            synchronized (mLock) {
                if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                    mMediaPlayer.pause();
                    mIsPaused = true;
                    Log.d(TAG, "Clip paused");
                    return true;
                }
                Log.w(TAG, "Cannot pause: no clip is playing");
                return false;
            }
        }

        @Override
        public boolean resumeClip() throws RemoteException {
            Log.d(TAG, "resumeClip() called");

            synchronized (mLock) {
                if (mMediaPlayer != null && mIsPaused) {
                    mMediaPlayer.start();
                    mIsPaused = false;
                    Log.d(TAG, "Clip resumed");
                    return true;
                }
                Log.w(TAG, "Cannot resume: no clip is paused");
                return false;
            }
        }

        @Override
        public boolean stopClip() throws RemoteException {
            Log.d(TAG, "stopClip() called");

            synchronized (mLock) {
                if (mMediaPlayer != null) {
                    releaseMediaPlayerLocked();
                    Log.d(TAG, "Clip stopped");
                    return true;
                }
                Log.w(TAG, "Cannot stop: no clip loaded");
                return false;
            }
        }

        @Override
        public boolean isPlaying() throws RemoteException {
            synchronized (mLock) {
                return mMediaPlayer != null && mMediaPlayer.isPlaying();
            }
        }

        @Override
        public boolean isPaused() throws RemoteException {
            synchronized (mLock) {
                return mMediaPlayer != null && mIsPaused;
            }
        }
    };

    /**
     * Releases the MediaPlayer. Must be called while holding mLock.
     */
    private void releaseMediaPlayerLocked() {
        if (mMediaPlayer != null) {
            try {
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.stop();
                }
                mMediaPlayer.release();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error releasing MediaPlayer: " + e.getMessage());
            }
            mMediaPlayer = null;
            mIsPaused = false;
            mCurrentClip = -1;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "FunCenterService created");
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "FunCenterService bound");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "FunCenterService unbound");
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "FunCenterService rebound");
        super.onRebind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "FunCenterService onStartCommand");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "FunCenterService destroyed");
        synchronized (mLock) {
            releaseMediaPlayerLocked();
        }
        super.onDestroy();
    }
}
