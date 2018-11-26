package cs.umass.edu.myactivitiestoolkit.reps;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

import java.util.ArrayList;

import cs.umass.edu.myactivitiestoolkit.constants.Constants;
import cs.umass.edu.myactivitiestoolkit.processing.Filter;
import cs.umass.edu.myactivitiestoolkit.reps.OnRepListener;
import cs.umass.edu.myactivitiestoolkit.reps.RepDetector;


public class RepDetector implements SensorEventListener{
    /** Used for debugging purposes. */
    @SuppressWarnings("unused")
    private static final String TAG = RepDetector.class.getName();

    /** Maintains the set of listeners registered to handle step events. **/
    private ArrayList<OnRepListener> mRepListeners;

    private final Filter mFilter;

    /**
     * The number of reps taken.
     */
    private int repCount;

    public RepDetector(){
        mFilter = new Filter(3);
        mRepListeners = new ArrayList<>();
        repCount = 0;
    }

    /**
     * Registers a rep listener for handling rep events.
     * @param repListener defines how rep events are handled.
     */
    public void registerOnRepListener(final OnRepListener repListener){
        mRepListeners.add(repListener);
    }

    /**
     * Unregisters the specified rep listener.
     * @param repListener the listener to be unregistered. It must already be registered.
     */
    public void unregisterOnRepListener(final OnRepListener repListener){
        mRepListeners.remove(repListener);
    }

    /**
     * Unregisters all step listeners.
     */
    public void unregisterOnRepListeners(){
        mRepListeners.clear();
    }

    public void detectReps(long timestamp_in_milliseconds, float... values){

    }

    /**
     * Here is where you will receive gyro readings, buffer them if necessary
     * and run your excercise rep detection algorithm. When a rep is detected, call
     * {@link #onRepDetected(long, float[])} to notify all listeners.
     *
     * @param event sensor reading
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            double[] filteredValues = mFilter.getFilteredValues(event.values);
            long timestamp_in_milliseconds = (long) ((double) event.timestamp / Constants.TIMESTAMPS.NANOSECONDS_PER_MILLISECOND);
            float[] floatFilteredValues = new float[filteredValues.length];
            for (int i = 0; i < filteredValues.length; i++){
                floatFilteredValues[i] = (float) filteredValues[i];
            }
            detectReps(timestamp_in_milliseconds, floatFilteredValues);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // do nothing
    }

    /**
     * This method is called when a rep is detected. It updates the current rep count,
     * notifies all listeners that a step has occurred and also notifies all listeners
     * of the current rep count.
     */
    private void onRepDetected(long timestamp, float[] values){
        repCount++;
        for (OnRepListener repListener : mRepListeners){
            repListener.onRepDetected(timestamp, values);
            repListener.onRepCountUpdated(repCount);
        }
    }
}
