package jakeparker.swerve;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import com.dropbox.sync.android.DbxAccountManager;
import com.dropbox.sync.android.DbxException;
import com.dropbox.sync.android.DbxFileSystem;
import com.dropbox.sync.android.DbxPath;

import org.w3c.dom.Text;

import java.util.ArrayList;

/*
 * Created by jacobparker on 3/29/16.
 */
public class MotionSensor extends Activity implements SensorEventListener
{
    private TextView tv;
    private SensorManager mgr; //the management
    private Sensor gyro;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private Sensor rotationVec;

    private static final float NS2S = 1.0f / 1000000000.0f;
    private final float[] deltaRotationVector = new float[4];
    private long timestamp;
    private long lastUpdate = 0;
    private final static long startTime = Dropboxer.startTime;

    private boolean hasInitialOrientation = false;
    private float[] initialRotationMatrix;
    private float[] currentRotationMatrixCalibrated;
    private float[] currentRotation = new float[9];
    private float[] msSway = new float[10];
    private float[] msPitch = new float[10];
    private float[] ms3d = new float[10];
    private int msIndex = 0;

    private float EPSILON = (float) Math.pow(1, -8);

    private float lastX, lastY, lastZ;

    private float deltaX = 0;
    private float deltaY = 0;
    private float deltaZ = 0;

    private float deltaXMax = 0;
    private float deltaYMax = 0;
    private float deltaZMax = 0;

    float[] inclineGravity = new float[3];
    float[] mGravity;
    float[] mGeomagnetic;
    float orientation[] = new float[3];
    float pitch;
    float roll;

    private ArrayList<Float> data = new ArrayList(1000);
    private ArrayList<Float> pitchData = new ArrayList(1000);
    private ArrayList<Float> d3Angles = new ArrayList(1000);
    private ArrayList<Long> time = new ArrayList(1000);

    private ImageView line;
    private Canvas canvas;
    private Bitmap bmp;
    private float lineLength = 200;

    private int index = 0;
    private long timeLimit = 300000L;

    /* dropbox */

    private static DbxAccountManager mDbxAcctMgr;
    private DbxFileSystem dbxFs;
    private DbxPath mDbxPath;
    static final int REQUEST_LINK_TO_DBX = 0;

    private TextView currentX, currentY, currentZ, currentMagnitude, currentAngle, lineX, lineY;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motionsensor);

        line = (ImageView) findViewById(R.id.line);
        initializeViews();

        mDbxAcctMgr = Dropboxer.getDbxAccountManager();
        if (mDbxAcctMgr.hasLinkedAccount())
        {
            Toast.makeText(this, "Still connected to dropbox...", Toast.LENGTH_LONG).show();
            try
            {
                dbxFs = DbxFileSystem.forAccount(mDbxAcctMgr.getLinkedAccount());
            }
            catch (DbxException.Unauthorized e) {
                // TO DO
                e.printStackTrace();
            }
            //mDbxPath = new DbxPath("SwerveDbx/motiondata.txt");
        }

        bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bmp);

        // Get an instance of the sensor service
        mgr = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        //accelerometer = mgr.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        accelerometer = mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        //magnetometer = mgr.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        //mgr.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);

        //gyro = mgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        if (!this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER))
        {
            Toast.makeText(getApplicationContext(),"Accelerometer sensor is not present", Toast.LENGTH_LONG).show();
        }
    }

    public void drawLine(int angle, int pitch)
    {
        Paint paint = new Paint();
        //bmp.eraseColor(Color.TRANSPARENT);
        paint.setColor(Color.BLACK);
        canvas.drawLine(0, 200, 400, 200, paint);
        canvas.drawLine(200, 0, 200, 400, paint);
        paint.setStrokeWidth(6);
        paint.setColor(Color.DKGRAY);
        canvas.drawPoint(lineLength - lineLength * (float) Math.sin(Math.toRadians(angle)), lineLength - lineLength * (float) Math.sin(Math.toRadians(pitch)), paint);
        //paint.setColor(Color.BLUE);
        //canvas.drawLine(lineLength, lineLength, lineLength - lineLength * (float) Math.sin(Math.toRadians(angle)), lineLength - lineLength * (float) Math.cos(Math.toRadians(angle)), paint);
        line.setImageBitmap(bmp);
        //lineX.setText(Double.toString(lineLength - lineLength * (float) Math.sin(Math.toRadians(angle))));
        //lineY.setText(Double.toString(lineLength * (float) Math.cos(Math.toRadians(angle))));
    }

    public void onAccelerometerSensorChange(SensorEvent event)
    {
        float[] g = new float[3];
        g = event.values.clone();

        float x = g[0];
        float y = g[1];
        float z = g[2];

        long curTime = System.currentTimeMillis();
        timestamp = curTime - startTime;

        if ((curTime - lastUpdate) > 100)
        {
            float diffTime = curTime - lastUpdate;
            lastUpdate = curTime;

            float accMagnitude = (float) Math.sqrt(x*x + y*y + z*z);
            float xyMagnitude = (float) Math.sqrt(x*x + y*y);
            float zyMagnitude = (float) Math.sqrt(y*y + z*z);

            currentX.setText(Float.toString(x) + " m/s^2");
            currentY.setText(Float.toString(y) + " m/s^2");
            currentZ.setText(Float.toString(z) + " m/s^2");
            currentMagnitude.setText(Float.toString(accMagnitude) + " m/s^2");

            if (xyMagnitude < 8)
            {
                x /= accMagnitude;
                y /= accMagnitude;
                z /= accMagnitude;
            }
            else
            {
                x *= 100;
                y *= 100;
                z *= 100;
            }

            int inclination = (int) Math.round(Math.toDegrees(Math.acos(z)));
            int pitch = (int) Math.round(Math.toDegrees(Math.atan2(z, y)));
            float fPitch = Math.round(Math.toDegrees(Math.atan2(z, y)));

            int sway = (int) Math.round(Math.toDegrees(Math.atan2(x, y)));
            float fSway = Math.round(Math.toDegrees(Math.atan2(x, y)));

            float normMagnitude = (float) Math.sqrt(x*x + y*y + z*z);
            int d3Angle = (int) Math.round(Math.toDegrees(Math.acos(y/normMagnitude)));
            ms3d[msIndex] = Math.round(Math.toDegrees(Math.acos(y/normMagnitude)));

            msSway[msIndex] = fSway;
            msPitch[msIndex] = fPitch;
            msIndex++;

            currentAngle.setText("3D Angle: " + Integer.toString(d3Angle) + " degrees");
            drawLine(sway, pitch);

            if (msIndex == 10)
            {
                float avgSway = calcAvgSway(msSway);
                float avg3d = calcAvgSway(ms3d);
                try
                {
                    data.set(index, avgSway);
                    d3Angles.set(index, avg3d);
                    time.set(index, timestamp);
                }
                catch (IndexOutOfBoundsException e)
                {
                    data.add(index, avgSway);
                    d3Angles.add(index, avg3d);
                    time.add(index, timestamp);
                }
                index++;
                msIndex = 0;
                //drawLine((int) avgSway, (int) avgPitch);
            }
        }

        if (index >= 20)
        {
            index = 0;
            Connect dbx = new Connect()
            {
                @Override
                public void onPostExecute(Boolean result)
                {
                    if (result == true)
                    {
                        System.out.println("Data sent to Dropbox");
                    }
                }
            };
            dbx.execute(data, d3Angles, time);
        }

        if (curTime - startTime > timeLimit)
        {
            super.onPause();
            mgr.unregisterListener(this);
            Toast.makeText(this, "5 minute time limit is up!", Toast.LENGTH_LONG).show();
        }
    }

    public float calcAvgSway(float[] msSway)
    {
        float sum = 0;
        for (float sway : msSway)
        {
            sum += sway;
        }
        return sum / msSway.length;
    }

    public void onGyroscopeSensorChange(SensorEvent event)
    {

    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something if sensor accuracy changes.
    }

    private void calculateInitialOrientation()
    {
    //    hasInitialOrientation = SensorManager.getRotationMatrix(
    //            initialRotationMatrix, null, acceleration, magnetic);

    }

    private float[] calculateNewRotationMatrix(float[] a, float[] b)
    {
        float[] result = new float[9];

        result[0] = a[0] * b[0] + a[1] * b[3] + a[2] * b[6];
        result[1] = a[0] * b[1] + a[1] * b[4] + a[2] * b[7];
        result[2] = a[0] * b[2] + a[1] * b[5] + a[2] * b[8];

        result[3] = a[3] * b[0] + a[4] * b[3] + a[5] * b[6];
        result[4] = a[3] * b[1] + a[4] * b[4] + a[5] * b[7];
        result[5] = a[3] * b[2] + a[4] * b[5] + a[5] * b[8];

        result[6] = a[6] * b[0] + a[7] * b[3] + a[8] * b[6];
        result[7] = a[6] * b[1] + a[7] * b[4] + a[8] * b[7];
        result[8] = a[6] * b[2] + a[7] * b[5] + a[8] * b[8];

        return result;
    }

    @Override
    public final void onSensorChanged(SensorEvent event)
    {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION)
        {
            mGravity = event.values;
            onAccelerometerSensorChange(event);
        }
        else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
        {
            onAccelerometerSensorChange(event);
        }
        else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
        {
            // magnetometer
        }
        else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE)
        {
            onGyroscopeSensorChange(event);
        }

        /*
        // Integrate around this axis with the angular speed by the timestep
        // in order to get a delta rotation from this sample over the
        // timestep. We will convert this axis-angle representation of the
        // delta rotation into a quaternion before turning it into the
        // rotation matrix.
        float thetaOverTwo = axis * dT / 2.0f;

        float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
        float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);

        deltaRotationVector[0] = sinThetaOverTwo * axisX;
        deltaRotationVector[1] = sinThetaOverTwo * axisY;
        deltaRotationVector[2] = sinThetaOverTwo * axisZ;
        deltaRotationVector[3] = cosThetaOverTwo;

        timestamp = event.timestamp;
        float[] deltaRotationMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(deltaRotationMatrix, deltaRotationVector);

        for (int i = 0; i < deltaRotationMatrix.length; i++)
        {
            currentRotation[i] += deltaRotationMatrix[i];
            //Log.d("CURRENT/DELTA", "Current["+i+"]="+currentRotation[i]+"\tDelta="+deltaRotationMatrix[i]);
        }

        // User code should concatenate the delta rotation we computed with the current rotation
        // in order to get the updated rotation.
        // rotationCurrent = rotationCurrent * deltaRotationMatrix;
    */
    }

    @Override
    protected void onResume()
    {
        // Register a listener for the sensor.
        super.onResume();
        mgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        //mgr.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause()
    {
        // important to unregister the sensor when the activity pauses.
        super.onPause();
        mgr.unregisterListener(this);
    }

    public void displayCleanValues()
    {
        currentX.setText("0.0 rad/s");
        currentY.setText("0.0 rad/s");
        currentZ.setText("0.0 rad/s");
    }

    // display the current x,y,z accelerometer values
    public void displayCurrentValues()
    {
        currentX.setText(Float.toString(deltaX));
        currentY.setText(Float.toString(deltaY));
        currentZ.setText(Float.toString(deltaZ));
    }

    public void initializeViews()
    {
        currentX = (TextView) findViewById(R.id.currentX);
        currentY = (TextView) findViewById(R.id.currentY);
        currentZ = (TextView) findViewById(R.id.currentZ);
        currentMagnitude = (TextView) findViewById(R.id.currentOmega);
        currentAngle = (TextView) findViewById(R.id.angle);
        //lineX = (TextView) findViewById(R.id.lineX);
        //lineY = (TextView) findViewById(R.id.lineY);

        for (int i = 0; i < currentRotation.length; i++)
        {
            currentRotation[i] = 0;
        }
    }

    public void detectTilt()
    {
        float[] R = new float[9];
        float[] I = new float[9];

        boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);

        if (success)
        {
            float[] orientation = new float[3];
            SensorManager.getOrientation(R, orientation);

            pitch = orientation[1];
            roll = orientation[2];

            inclineGravity = mGravity.clone();

            double norm_Of_g = Math.sqrt(inclineGravity[0] * inclineGravity[0] + inclineGravity[1] * inclineGravity[1] + inclineGravity[2] * inclineGravity[2]);

            // Normalize the accelerometer vector
            inclineGravity[0] = (float) (inclineGravity[0] / norm_Of_g);
            inclineGravity[1] = (float) (inclineGravity[1] / norm_Of_g);
            inclineGravity[2] = (float) (inclineGravity[2] / norm_Of_g);

            //Checks if device is flat on ground or not
            int inclination = (int) Math.round(Math.toDegrees(Math.acos(inclineGravity[2])));

            Float objPitch = new Float(pitch);
            Float objZero = new Float(0.0);
            Float objZeroPointTwo = new Float(0.2);
            Float objZeroPointTwoNegative = new Float(-0.2);

            int objPitchZeroResult = objPitch.compareTo(objZero);
            int objPitchZeroPointTwoResult = objZeroPointTwo.compareTo(objPitch);
            int objPitchZeroPointTwoNegativeResult = objPitch.compareTo(objZeroPointTwoNegative);
        }
    }
}
