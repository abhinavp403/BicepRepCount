package cs.umass.edu.myactivitiestoolkit.services;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

import cs.umass.edu.myactivitiestoolkit.R;
import cs.umass.edu.myactivitiestoolkit.communication.MHLClientFilter;
import cs.umass.edu.myactivitiestoolkit.processing.Filter;
import cs.umass.edu.myactivitiestoolkit.constants.Constants;
import cs.umass.edu.myactivitiestoolkit.reps.OnRepListener;
import cs.umass.edu.myactivitiestoolkit.reps.RepDetector;
import edu.umass.cs.MHLClient.client.MessageReceiver;
import edu.umass.cs.MHLClient.client.MobileIOClient;
import edu.umass.cs.MHLClient.sensors.GyroscopeReading;

public class GyroService extends SensorService implements SensorEventListener{
    /** Used during debugging to identify logs by class */
    private static final String TAG = GyroService.class.getName();

    /** Sensor Manager object for registering and unregistering system sensors */
    private SensorManager mSensorManager;

    /** Manages the physical accelerometer sensor on the phone. */
    private Sensor mGyroSensor;

    /** Defines your step detection algorithm. **/
    private RepDetector repDetector;

    private Filter filter;

    /**
     * The rep count as predicted by your server-side step detection algorithm.
     */
    private int serverRepCount = 0;

    public GyroService(){
        filter = new Filter(3);
        repDetector = new RepDetector();
    }

    @Override
    protected void onServiceStarted() {
        broadcastMessage(Constants.MESSAGE.GYRO_SERVICE_STARTED);
    }

    @Override
    protected void onServiceStopped() {
        broadcastMessage(Constants.MESSAGE.GYRO_SERVICE_STOPPED);
        if (client != null)
            client.unregisterMessageReceivers();
    }

    @Override
    public void onConnected() {
        super.onConnected();

        client.registerMessageReceiver(new MessageReceiver(MHLClientFilter.AVERAGE_ANGULAR_VELOCITY) {
            @Override
            protected void onMessageReceived(JSONObject json) {
                Log.d(TAG, "Received angular velocity from server.");
                try {
                    JSONObject data = json.getJSONObject("data");
                    float average_X = (float)data.getDouble("average_X");
                    float average_Y = (float)data.getDouble("average_Y");
                    float average_Z = (float)data.getDouble("average_Z");
                    broadcastAverageAngularVelocity(average_X, average_Y, average_Z);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        client.registerMessageReceiver(new MessageReceiver(MHLClientFilter.REP) {
            @Override
            protected void onMessageReceived(JSONObject json) {
                Log.d(TAG, "Received exercise rep update from server.");
                try {
                    JSONObject data = json.getJSONObject("data");
                    long timestamp = data.getLong("timestamp");
                    Log.d(TAG, "Rep occurred at " + timestamp + ".");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                serverRepCount++;
                broadcastServerRepCount(serverRepCount);
            }
        });
    }

    /**
     * Register gyroscope sensor listener
     */
    @Override
    protected void registerSensors(){
        // TODO (Assignment 0) : Register the gyro sensor using the sensor manager
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        if (mSensorManager == null){
            Log.e(TAG, Constants.ERROR_MESSAGES.ERROR_NO_SENSOR_MANAGER);
            Toast.makeText(getApplicationContext(), Constants.ERROR_MESSAGES.ERROR_NO_SENSOR_MANAGER,Toast.LENGTH_LONG).show();
            return;
        }

        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        if (mGyroSensor != null) {
            mSensorManager.registerListener(this, mGyroSensor, SensorManager.SENSOR_DELAY_GAME);
        } else {
            Toast.makeText(getApplicationContext(), Constants.ERROR_MESSAGES.ERROR_NO_GYRO, Toast.LENGTH_LONG).show();
            Log.w(TAG, Constants.ERROR_MESSAGES.ERROR_NO_GYRO);
        }


        repDetector.registerOnRepListener(new OnRepListener() {
            @Override
            public void onRepCountUpdated(int repCount) {
            }

            @Override
            public void onRepDetected(long timestamp, float[] values) {
                broadcastRepDetected(timestamp, values);
            }
        });
        mSensorManager.registerListener(repDetector, mGyroSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected int getNotificationID() {
        return Constants.NOTIFICATION_ID.GYRO_SERVICE;
    }

    @Override
    protected int getNotificationIconResourceID() {
        return R.drawable.ic_running_white_24dp;
    }

    //This method is called when we receive a sensor reading. We will be interested in this method primarily.
    @Override
    public void onSensorChanged(SensorEvent event) {
        //ToDo -- 1. Filter for Gyroscope events (replace false)
        //ToDo -- 2. Apply a filter to the data (processing -> Filter.java)
        //ToDo -- 3. call broadCastGyroReading to inform other app components (i.e. RepFragment) about the new sensor reading.
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) { //1. replace with filter for gyro data
            double[] filteredValues = {0,0,0};
            //2. smooth gyro data with a low pass filter
            //filter = new Filter(event.values[0]);
            //filter = new Filter(event.values[1]);
            //filter = new Filter(event.values[2]);
            filter = new Filter(10.5);
            filteredValues = filter.getFilteredValues(event.values[0], event.values[1], event.values[2]);
            //Log.d("FV Values", Arrays.toString(filteredValues));
            /*Filter filter1= new Filter(event.values[0]);
            Filter filter2= new Filter(filteredValues[1]);
            Filter filter3= new Filter(filteredValues[2]);*/
            client.sendSensorReading(new GyroscopeReading(userID, "MOBILE", "", event.timestamp, (float)filteredValues[0], (float)filteredValues[1], (float)filteredValues[2]));

            float[] floatFilteredValues = new float[]{(float) filteredValues[0], (float) filteredValues[1], (float) filteredValues[2]};
            //3. call broadcastGyroReading to inform other app components of the new sensor readings
            broadcastGyroReading(event.timestamp, floatFilteredValues);
        }else {
            Log.w(TAG, Constants.ERROR_MESSAGES.WARNING_SENSOR_NOT_SUPPORTED);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.i(TAG, "Accuracy changed: " + accuracy);
    }

    /**
     * Unregister the sensor listener, this is essential for the battery life!
     */
    @Override
    protected void unregisterSensors() {
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this, mGyroSensor);
            mSensorManager.unregisterListener(repDetector, mGyroSensor);
        }
    }

    @Override
    protected String getNotificationContentText() {
        return getString(R.string.gyro_service_notification);
    }


    /**
     * Broadcasts the rep count computed by your server-side step detection algorithm
     * to other application components, e.g. the main UI.
     */
    public void broadcastServerRepCount(int repCount) {
        Intent intent = new Intent();
        intent.putExtra(Constants.KEY.REP_COUNT, repCount);
        intent.setAction(Constants.ACTION.BROADCAST_SERVER_REP_COUNT);
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.sendBroadcast(intent);
    }

    /**
     * Broadcasts a rep event to other application components, e.g. the main UI.
     */
    public void broadcastRepDetected(long timestamp, float[] values) {
        Intent intent = new Intent();
        intent.putExtra(Constants.KEY.GYRO_PEAK_TIMESTAMP, timestamp);
        intent.putExtra(Constants.KEY.GYRO_PEAK_VALUE, values);
        intent.setAction(Constants.ACTION.BROADCAST_GYRO_PEAK);
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.sendBroadcast(intent);
    }

    /**
     * Broadcasts the step count computed by your server-side step detection algorithm
     * to other application components, e.g. the main UI.
     */
    public void broadcastActivity(String activity) {
        Intent intent = new Intent();
        intent.putExtra(Constants.KEY.ACTIVITY, activity);
        intent.setAction(Constants.ACTION.BROADCAST_ACTIVITY);
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.sendBroadcast(intent);
    }

    /**
     * Broadcasts the step count computed by your server-side rep detection algorithm
     * to other application components, e.g. the main UI.
     */
    public void broadcastAverageAngularVelocity(float average_X, float average_Y, float average_Z) {
        Intent intent = new Intent();
        intent.putExtra(Constants.KEY.AVERAGE_ANGULAR_VELOCITY, new float[]{average_X, average_Y, average_Z});
        intent.setAction(Constants.ACTION.BROADCAST_AVERAGE_ANGULAR_VELOCITY);
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.sendBroadcast(intent);
    }

    /**
     * Broadcasts the gyro reading to other application components, e.g. the main UI.
     * @param gyroReadings the x, y, and z accelerometer readings
     */
    public void broadcastGyroReading(final long timestamp, final float[] gyroReadings) {
        Intent intent = new Intent();
        intent.putExtra(Constants.KEY.TIMESTAMP, timestamp);
        intent.putExtra(Constants.KEY.GYRO_DATA, gyroReadings);
        intent.setAction(Constants.ACTION.BROADCAST_GYRO_DATA);
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.sendBroadcast(intent);
    }

}
