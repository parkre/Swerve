package jakeparker.swerve;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.widget.TextView;

public class Accelerometer extends Activity implements SensorEventListener
{
    private float lastX, lastY, lastZ, lastZ3;

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private float deltaXMax = 0;
    private float deltaYMax = 0;
    private float deltaZMax = 0;

    private float deltaX = 0;
    private float deltaY = 0;
    private float deltaZ = 0;
    private float deltaZ3 = 0;

    private float vibrateThreshold = 0;

    private TextView currentX, currentY, currentZ, currentZ3, maxX, maxY, maxZ;

    public Vibrator v;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accelerometer);
        initializeViews();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null)
        {
            // success! we have a rotation vector
            //try to used old Type_ORIENTATION gives results in degrees -does not work !!!! 2/11/16

            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            vibrateThreshold = accelerometer.getMaximumRange() / 2;


        }
        else
        {
            // fail we do not have an Rotation !
        }

        //initialize vibration
        v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

    }

    public void initializeViews()
    {
        currentX = (TextView) findViewById(R.id.currentX);
        currentY = (TextView) findViewById(R.id.currentY);
        currentZ = (TextView) findViewById(R.id.currentZ);
        //       currentZ3 = (TextView) findViewById(R.id.currentZ3);
    }

    //onResume() register the accelerometer for listening the events
    protected void onResume()
    {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    //onPause() unregister the accelerometer for stop listening the events
    protected void onPause()
    {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {
        //logic
    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {

        // clean current values
        displayCleanValues();
        // display the current x,y,z accelerometer values
        displayCurrentValues();
        // display the max x,y,z accelerometer values
        //displayMaxValues();

        // get the change of the x,y,z values of the accelerometer
        deltaX = Math.abs(lastX - event.values[0]);
        deltaY = Math.abs(lastY - event.values[1]);
        deltaZ = Math.abs(lastZ - event.values[2]);
        deltaZ3 = Math.abs(lastZ3 - event.values[3]);


  /*      // if the change is below 2, it is just plain noise
        if (deltaX < 2)
            deltaX = 0;
        if (deltaY < 2)
            deltaY = 0;
        if (deltaZ < 2)     // av 2/10/16
             deltaZ = 0;
*/
//        if ((deltaX > vibrateThreshold) || (deltaY > vibrateThreshold) || (deltaZ > vibrateThreshold)) {
//            v.vibrate(50);
//        }
    }

    public void displayCleanValues()
    {
        currentX.setText("0.0");
        currentY.setText("0.0");
        currentZ.setText("0.0");
        //   currentZ3.setText("0.0");
    }

    // display the current x,y,z accelerometer values
    public void displayCurrentValues()
    {
        currentX.setText(Float.toString(deltaX));
        currentY.setText(Float.toString(deltaY));
        currentZ.setText(Float.toString(deltaZ));
        //   currentZ3.setText(Float.toString(deltaZ3));
    }
/*
    // display the max x,y,z accelerometer values
    public void displayMaxValues()
    {
        if (deltaX > deltaXMax)
        {
            deltaXMax = deltaX;
            maxX.setText(Float.toString(deltaXMax));
        }
        if (deltaY > deltaYMax)
        {
            deltaYMax = deltaY;
            maxY.setText(Float.toString(deltaYMax));
        }
/*  instead of Zmax show values[3] which is cos (teta/2)
        if (deltaZ > deltaZMax) {
            deltaZMax = deltaZ;
            maxZ.setText(Float.toString(deltaZMax));
        }
 /
        if (deltaZ > 0)
        {
            deltaZMax = deltaZ3;
            maxZ.setText(Float.toString(deltaZMax));
        }
    }
*/
}
