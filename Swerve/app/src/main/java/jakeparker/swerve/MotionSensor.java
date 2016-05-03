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
    private Sensor accelerometer;
    private Sensor gyroscope;

    private long timestamp;
    private long lastUpdate;// = 0;
    private long startTime;// = System.currentTimeMillis(); //Dropboxer.getStartTime();
    private long timeLimit = 300000L; // 5 minutes

    // accelerometer-based data structures
    private ArrayList<Float> msSway = new ArrayList();
    private ArrayList<Float> msPitch = new ArrayList();
    private ArrayList<Float> ms3d = new ArrayList();

    // gyroscope-based data structures
    private ArrayList<Float> gyroX = new ArrayList();
    private ArrayList<Float> gyroY = new ArrayList();

    // D3 features
    float rangeAccX;
    float rangeAccY;
    float stdAccX;
    float stdAccY;
    float stdGyroX;
    float stdGyroY;
    float meanAccX;
    float meanAccY;
    float meanGyroX;
    float meanGyroY;
    float maxAccX;
    float maxAccY;
    float minAccX; // not in paper
    float minAccY;
    float maxGyroX;
    float maxGyroY;
    float minGyroX; // not in paper
    float minGyroY; // not in paper
    float t;


    // Dropbox data
    private ArrayList<Float> data3d = new ArrayList(1000);
    private ArrayList<Float> dataXY = new ArrayList(1000);
    private ArrayList<Long> time = new ArrayList(1000);
    private int index = 0;

    float[] inclineGravity = new float[3];
    float[] mGravity;
    float[] mGeomagnetic;
    float orientation[] = new float[3];
    float pitch;
    float roll;

    // display
    private ImageView line;
    private Canvas canvas;
    private Bitmap bmp;
    private float lineLength = 200;
    private TextView currentX, currentY, currentZ, currentMagnitude, currentAngle, currentTime;
    private TextView lineX, lineY;

    // extra
    private float[] currentRotation = new float[9];

    /* dropbox */

    private static DbxAccountManager mDbxAcctMgr;
    private DbxFileSystem dbxFs;
    private DbxPath mDbxPath;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motionsensor);

        startTime = System.currentTimeMillis();
        lastUpdate = System.currentTimeMillis();

        line = (ImageView) findViewById(R.id.line);
        initializeViews();

        mDbxAcctMgr = Dropboxer.getDbxAccountManager();
        if (mDbxAcctMgr.hasLinkedAccount())
        {
            try
            {
                dbxFs = DbxFileSystem.forAccount(mDbxAcctMgr.getLinkedAccount());
            }
            catch (DbxException.Unauthorized e) {
                // TO DO
                e.printStackTrace();
            }
        }

        // initialize display
        bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bmp);

        // Get an instance of the sensor service
        mgr = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // accelerometer
        accelerometer = mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);

        // gyroscope
        gyroscope = mgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mgr.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);

        if (!this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER))
        {
            Toast.makeText(getApplicationContext(),"Accelerometer sensor is not present", Toast.LENGTH_LONG).show();
        }
    }

    public void drawLine(int angle, int pitch)
    {
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        canvas.drawLine(0, 200, 400, 200, paint);
        canvas.drawLine(200, 0, 200, 400, paint);
        paint.setStrokeWidth(6);
        paint.setColor(Color.DKGRAY);
        canvas.drawPoint(lineLength - lineLength * (float) Math.sin(Math.toRadians(angle)), lineLength - lineLength * (float) Math.sin(Math.toRadians(pitch)), paint);
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
        float diffTime = curTime - lastUpdate;
        timestamp = curTime - startTime;

        float accMagnitude = (float) Math.sqrt(x*x + y*y + z*z);
        float xyMagnitude = (float) Math.sqrt(x*x + y*y);
        float zyMagnitude = (float) Math.sqrt(y*y + z*z);

        currentX.setText(Float.toString(x) + " m/s^2");
        currentY.setText(Float.toString(y) + " m/s^2");
        currentZ.setText(Float.toString(z) + " m/s^2");
        currentMagnitude.setText(Float.toString(accMagnitude) + " m/s^2");

        // normalize
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
        float normMagnitude = (float) Math.sqrt(x*x + y*y + z*z);

        // sway
        int iSway = (int) Math.round(Math.toDegrees(Math.atan2(x, y)));
        float fSway = Math.round(Math.toDegrees(Math.atan2(x, y)));

        // pitch
        int iPitch = (int) Math.round(Math.toDegrees(Math.atan2(z, y)));
        float fPitch = Math.round(Math.toDegrees(Math.atan2(z, y)));

        // 3d orientation
        int i3dAngle = (int) Math.round(Math.toDegrees(Math.acos(y/normMagnitude)));
        float f3dAngle = Math.round(Math.toDegrees(Math.acos(y/normMagnitude)));

        // 100 ms avg
        ms3d.add(f3dAngle);
        msSway.add(fSway);
        msPitch.add(fPitch);

        // display motion
        currentAngle.setText("3D Angle: " + Integer.toString(i3dAngle) + " degrees");
        currentTime.setText("Time: " + Float.toString(timestamp) + " ms");
        drawLine(iSway, iPitch);

        // if 100 ms or more has elapsed, average all the data collected
        // over the last appx 100 ms time interval
        if (diffTime >= 100)
        {
            lastUpdate = curTime;
            float avg3d = calcAvg(ms3d);
            float avgSway = calcAvg(msSway);
            try
            {
                data3d.set(index, avg3d);
                dataXY.set(index, avgSway);
                time.set(index, timestamp);
            }
            catch (IndexOutOfBoundsException e)
            {
                data3d.add(index, avg3d);
                dataXY.add(index, avgSway);
                time.add(index, timestamp);
            }
            index++;
            ms3d.clear();
            msSway.clear();
            msPitch.clear();
        }

        // write to Dropbox every 4 seconds (100ms * 40 = 4000 ms = 4 seconds)
        if (index >= 40)
        {
            index = 0;
            ArrayList<Float> data3dClone = new ArrayList(data3d);
            ArrayList<Float> dataXYClone = new ArrayList(dataXY);
            ArrayList<Float> timeClone = new ArrayList(time);
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
            dbx.execute(data3dClone, dataXYClone, timeClone);
        }

        // implement 5 minute time limit
        if (curTime - startTime > timeLimit)
        {
            super.onPause();
            mgr.unregisterListener(this);
            displayCleanValues();
            currentTime.setText("5 minutes");
            Toast.makeText(this, "5 minute time limit is up!", Toast.LENGTH_LONG).show();
        }
    }

    public void onGyroscopeSensorChange(SensorEvent event)
    {
        float[] e = new float[3];
        e = event.values.clone();

        // angular speed around x, y, z, respectively
        float x = e[0];
        float y = e[1];
        float z = e[2];

        // Calculate the angular speed of the sample
        float omegaMagnitude = (float) Math.sqrt(x * x + y * y + z * z);

        // Normalize the rotation vector if it's big enough to get the axis
        if (omegaMagnitude > 1)
        {
            x /= omegaMagnitude;
            y /= omegaMagnitude;
            z /= omegaMagnitude;
        }

        gyroX.add(x);
        gyroY.add(y);
    }

    public float calcAvg(ArrayList<Float> data)
    {
        float dataSum = 0;
        for (float d : data)
        {
            dataSum += d;
        }
        return dataSum / data.size();
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something if sensor accuracy changes.
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
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
        {
            onAccelerometerSensorChange(event);
        }
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE)
        {
            onGyroscopeSensorChange(event);
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        mgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        mgr.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
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
        currentX.setText("–");
        currentY.setText("–");
        currentZ.setText("–");
    }

    public void initializeViews()
    {
        currentX = (TextView) findViewById(R.id.currentX);
        currentY = (TextView) findViewById(R.id.currentY);
        currentZ = (TextView) findViewById(R.id.currentZ);
        currentMagnitude = (TextView) findViewById(R.id.currentOmega);
        currentAngle = (TextView) findViewById(R.id.angle);
        currentTime = (TextView) findViewById(R.id.time);

        for (int i = 0; i < currentRotation.length; i++)
        {
            currentRotation[i] = 0;
        }
    }

    // unused
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
