package cs.umass.edu.myactivitiestoolkit.reps;

/**
 * Clients may register an OnRepListener to receive rep events and rep count
 * notifications.
 */
public interface OnRepListener {
    void onRepCountUpdated(int repCount);
    void onRepDetected(long timestamp, float[] values);
}
