// File: aidl/edu/uic/cs478/s2026/funcenter/aidl/IFunCenterService.aidl
package edu.uic.cs478.s2026.funcenter.aidl;

interface IFunCenterService {
    /**
     * Returns the picture with the given number as a byte array.
     * @param pictureNumber - picture number (1 to m)
     * @return byte array of the image, or null if invalid number
     */
    byte[] getPicture(int pictureNumber);

    /**
     * Returns the total number of available pictures.
     */
    int getPictureCount();

    /**
     * Returns the total number of available audio clips.
     */
    int getClipCount();

    /**
     * Starts playing the audio clip with the given number.
     * @param clipNumber - clip number (1 to n)
     * @return true if playback started successfully
     */
    boolean playClip(int clipNumber);

    /**
     * Pauses the currently playing clip.
     * @return true if paused successfully
     */
    boolean pauseClip();

    /**
     * Resumes the currently paused clip.
     * @return true if resumed successfully
     */
    boolean resumeClip();

    /**
     * Stops the playback altogether.
     * @return true if stopped successfully
     */
    boolean stopClip();

    /**
     * Returns whether a clip is currently playing.
     */
    boolean isPlaying();

    /**
     * Returns whether a clip is currently paused.
     */
    boolean isPaused();
}
