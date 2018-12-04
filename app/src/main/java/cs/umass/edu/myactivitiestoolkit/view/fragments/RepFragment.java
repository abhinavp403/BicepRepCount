package cs.umass.edu.myactivitiestoolkit.view.fragments;

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.util.PixelUtils;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;

import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;

import cs.umass.edu.myactivitiestoolkit.R;
import cs.umass.edu.myactivitiestoolkit.constants.Constants;
import cs.umass.edu.myactivitiestoolkit.services.GyroService;
import cs.umass.edu.myactivitiestoolkit.services.ServiceManager;
import cs.umass.edu.myactivitiestoolkit.services.msband.BandService;

public class RepFragment extends Fragment {
    /** Used during debugging to identify logs by class. */
    @SuppressWarnings("unused")
    private static final String TAG = RepFragment.class.getName();

    /** The switch which toggles the {@link GyroService}. **/
    private Switch switchGyro;

    /** Displays the Gyroscope x, y and z-readings. **/
    private TextView txtGyroReading;

    /** Displays the step count computed by your server-side step detection algorithm. **/
    private TextView txtServerRepCount;

    /** The plot which displays the Gyro data in real-time. **/
    private XYPlot plot;

    /** The series formatter that defines how the x-axis signal should be displayed. **/
    private LineAndPointFormatter xSeriesFormatter;

    /** The series formatter that defines how the y-axis signal should be displayed. **/
    private LineAndPointFormatter ySeriesFormatter;

    /** The series formatter that defines how the z-axis signal should be displayed. **/
    private LineAndPointFormatter zSeriesFormatter;

    /** The series formatter that defines how the peaks should be displayed. **/
    private LineAndPointFormatter peakSeriesFormatter;

    /** The number of data points to display in the graph. **/
    private static final int GRAPH_CAPACITY = 100;

    /** The number of points displayed on the plot. This should only ever be less than
     * {@link #GRAPH_CAPACITY} before the plot is fully populated. **/
    private int numberOfPoints = 0;

    /**
     * The queue of timestamps.
     */
    private final Queue<Number> timestamps = new LinkedList<>();

    /**
     * The queue of accelerometer values along the x-axis.
     */
    private final Queue<Number> xValues = new LinkedList<>();

    /**
     * The queue of accelerometer values along the y-axis.
     */
    private final Queue<Number> yValues = new LinkedList<>();

    /**
     * The queue of accelerometer values along the z-axis.
     */
    private final Queue<Number> zValues = new LinkedList<>();

    /**
     * The queue of peak timestamps.
     */
    private final Queue<Number> peakTimestamps = new LinkedList<>();

    /**
     * The queue of peak values.
     */
    private final Queue<Number> peakValues = new LinkedList<>();

    /** Reference to the service manager which communicates to the {@link GyroService}. **/
    private ServiceManager serviceManager;

    /**
     * The receiver listens for messages from the {@link GyroService}, e.g. was the
     * service started/stopped, and updates the status views accordingly. It also
     * listens for sensor data and displays the sensor readings to the user.
     */
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null) {
                if (intent.getAction().equals(Constants.ACTION.BROADCAST_MESSAGE)) {
                    int message = intent.getIntExtra(Constants.KEY.MESSAGE, -1);
                    if (message == Constants.MESSAGE.GYRO_SERVICE_STOPPED){
                        switchGyro.setChecked(false);
                    } else if (message == Constants.MESSAGE.BAND_SERVICE_STOPPED){
                        switchGyro.setChecked(false);
                    }
                } else if (intent.getAction().equals(Constants.ACTION.BROADCAST_GYRO_DATA)) {
                    long timestamp = intent.getLongExtra(Constants.KEY.TIMESTAMP, -1);
                    float[] gyroValues = intent.getFloatArrayExtra(Constants.KEY.GYRO_DATA);

                    //ToDo: 1. Add gyro data and timestamps to the graph using xValues, yValues, zValues, and timestamps
                    xValues.add(gyroValues[0]);
                    yValues.add(gyroValues[1]);
                    zValues.add(gyroValues[2]);
                    timestamps.add(timestamp);

                    if (numberOfPoints >= GRAPH_CAPACITY) {
                        timestamps.poll();
                        xValues.poll();
                        yValues.poll();
                        zValues.poll();
                        while (peakTimestamps.size() > 0 && (peakTimestamps.peek().longValue() < timestamps.peek().longValue())){
                            peakTimestamps.poll();
                            peakValues.poll();
                        }
                    }
                    else
                        numberOfPoints++;

                    //This function updates the plot with the data from above.
                    updatePlot();

                } else if (intent.getAction().equals(Constants.ACTION.BROADCAST_SERVER_REP_COUNT)) {
                    int repCount = intent.getIntExtra(Constants.KEY.REP_COUNT, 0);
                    displayServerRepCount(repCount);
                } else if (intent.getAction().equals(Constants.ACTION.BROADCAST_AVERAGE_ANGULAR_VELOCITY)) {
                    float[] average_gyro = intent.getFloatArrayExtra(Constants.KEY.AVERAGE_ANGULAR_VELOCITY);
                    displayGyroReading(average_gyro[0], average_gyro[1], average_gyro[2]);
                    String output = String.format(Locale.getDefault(), "The average angular velocity is (%f,%f,%f).", average_gyro[0], average_gyro[1], average_gyro[2]);
                    Toast.makeText(getActivity().getApplicationContext(), output, Toast.LENGTH_LONG).show();
                    Log.d(TAG, output);
                }else if (intent.getAction().equals(Constants.ACTION.BROADCAST_GYRO_PEAK)){
                    long timestamp = intent.getLongExtra(Constants.KEY.GYRO_PEAK_TIMESTAMP, -1);
                    float[] values = intent.getFloatArrayExtra(Constants.KEY.GYRO_PEAK_VALUE);
                    if (timestamp > 0) {
                        peakTimestamps.add(timestamp);
                        peakValues.add(values[2]); //place on z-axis signal
                    }
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.serviceManager = ServiceManager.getInstance(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_reps, container, false);

        //obtain a reference to the accelerometer reading text field
        txtGyroReading = (TextView) view.findViewById(R.id.txtGyroReading);

        //obtain references to the step count text fields
        txtServerRepCount = (TextView) view.findViewById(R.id.txtServerRepCount);

        //obtain references to the on/off switches and handle the toggling appropriately
        switchGyro = (Switch) view.findViewById(R.id.switchGyro);
        switchGyro.setChecked(serviceManager.isServiceRunning(GyroService.class));
        switchGyro.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean enabled) {
                if (enabled){
                    clearPlotData();

                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    serviceManager.startSensorService(GyroService.class);
                }

                else {
                    serviceManager.stopSensorService(GyroService.class);
                }
            }
        });

        // initialize plot and set plot parameters
        plot = (XYPlot) view.findViewById(R.id.gyroPlot);
        plot.setRangeBoundaries(-30, 30, BoundaryMode.FIXED);
        plot.setRangeStep(StepMode.SUBDIVIDE, 5);
        plot.getGraph().getDomainOriginLinePaint().setColor(Color.TRANSPARENT);
        plot.getGraph().getDomainGridLinePaint().setColor(Color.TRANSPARENT);
        plot.getGraph().getRangeGridLinePaint().setColor(Color.TRANSPARENT);
        plot.setDomainStep(StepMode.SUBDIVIDE, 1);

        // To remove the x labels, just set each label to an empty string:
        plot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).setFormat(new Format() {
            @Override
            public StringBuffer format(Object obj, @NonNull StringBuffer toAppendTo, @NonNull FieldPosition pos) {
                return toAppendTo.append("");
            }
            @Override
            public Object parseObject(String source, @NonNull ParsePosition pos) {
                return null;
            }
        });
        plot.setPlotPaddingBottom(-150); //TODO: This isn't device-dependent, and may need to be changed.
        plot.getLegend().setPaddingBottom(280);

        // set formatting parameters for each signal (accelerometer and accelerometer peaks)
        xSeriesFormatter = new LineAndPointFormatter(Color.RED, null, null, null);
        ySeriesFormatter = new LineAndPointFormatter(Color.GREEN, null, null, null);
        zSeriesFormatter = new LineAndPointFormatter(Color.BLUE, null, null, null);

        peakSeriesFormatter = new LineAndPointFormatter(null, Color.BLUE, null, null);
        peakSeriesFormatter.getVertexPaint().setStrokeWidth(PixelUtils.dpToPix(10)); //enlarge the peak points

        return view;
    }

    /**
     * When the fragment starts, register a {@link #receiver} to receive messages from the
     * {@link GyroService}. The intent filter defines messages we are interested in receiving.
     * <br><br>
     *
     * We would like to receive sensor data, so we specify {@link Constants.ACTION#BROADCAST_GYRO_DATA}.
     * We would also like to receive rep count updates, so include  {@link Constants.ACTION#BROADCAST_SERVER_REP_COUNT}.
     * <br><br>
     *
     * To optionally display the peak values you compute, include
     * {@link Constants.ACTION#BROADCAST_GYRO_PEAK}.
     * <br><br>
     *
     * Lastly to update the state of the accelerometer switch properly, we listen for additional
     * messages, using {@link Constants.ACTION#BROADCAST_MESSAGE}.
     *
     * @see Constants.ACTION
     * @see IntentFilter
     * @see LocalBroadcastManager
     * @see #receiver
     */
    @Override
    public void onStart() {
        super.onStart();

        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(getActivity());
        IntentFilter filter = new IntentFilter();
        filter.addAction(Constants.ACTION.BROADCAST_MESSAGE);
        filter.addAction(Constants.ACTION.BROADCAST_AVERAGE_ANGULAR_VELOCITY);
        filter.addAction(Constants.ACTION.BROADCAST_ACTIVITY);
        filter.addAction(Constants.ACTION.BROADCAST_GYRO_DATA);
        filter.addAction(Constants.ACTION.BROADCAST_GYRO_PEAK);
        filter.addAction(Constants.ACTION.BROADCAST_LOCAL_REP_COUNT);
        filter.addAction(Constants.ACTION.BROADCAST_SERVER_REP_COUNT);
        broadcastManager.registerReceiver(receiver, filter);
    }

    /**
     * When the fragment stops, e.g. the user closes the application or opens a new activity,
     * then we should unregister the {@link #receiver}.
     */
    @Override
    public void onStop() {
        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(getActivity());
        try {
            broadcastManager.unregisterReceiver(receiver);
        }catch (IllegalArgumentException e){
            e.printStackTrace();
        }
        super.onStop();
    }

    /**
     * Displays the accelerometer reading on the UI.
     * @param x angular velocity along the x-axis
     * @param y angular velocity along the y-axis
     * @param z angular velocity along the z-axis
     */
    private void displayGyroReading(final float x, final float y, final float z){
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //ToDo: Update the TextView txtGyroReading with the most recent angular velocity X,Y,Z data from the gyro
                txtGyroReading.setText(String.format(Locale.getDefault(), getActivity().getString(R.string.gyro_reading_format_string), x, y, z));
            }
        });
    }

    /**
     * Displays the rep count as computed by your server-side step detection algorithm.
     * @param repCount the number of steps taken since the service started
     */
    private void displayServerRepCount(final int repCount){
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //ToDo: update the TextView txtServerRepCount as your Python App detects subsequent exercise reps.
                txtServerRepCount.setText(String.format(Locale.getDefault(), getString(R.string.android_step_count), repCount));
            }
        });
    }


    /**
     * Clears the x, y, z and peak plot data series.
     */
    private void clearPlotData(){
        peakTimestamps.clear();
        peakValues.clear();
        timestamps.clear();
        xValues.clear();
        yValues.clear();
        zValues.clear();
        numberOfPoints = 0;
    }

    /**
     * Updates and redraws the gyroscope plot, along with the peaks detected.
     */
    private void updatePlot(){
        Log.d("TS", String.valueOf(timestamps.size()));
        Log.d("zV", String.valueOf(zValues.size()));
        XYSeries xSeries = new SimpleXYSeries(new ArrayList<>(timestamps), new ArrayList<>(xValues), "X");
        XYSeries ySeries = new SimpleXYSeries(new ArrayList<>(timestamps), new ArrayList<>(yValues), "Y");
        XYSeries zSeries = new SimpleXYSeries(new ArrayList<>(timestamps), new ArrayList<>(zValues), "Z");

        XYSeries peaks = new SimpleXYSeries(new ArrayList<>(peakTimestamps), new ArrayList<>(peakValues), "REP");

        //redraw the plot:
        plot.clear();
        plot.addSeries(xSeries, xSeriesFormatter);
        plot.addSeries(ySeries, ySeriesFormatter);
        plot.addSeries(zSeries, zSeriesFormatter);
        plot.addSeries(peaks, peakSeriesFormatter);
        plot.redraw();
    }
}
