package jakeparker.swerve;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.dropbox.sync.android.DbxAccountManager;
import com.dropbox.sync.android.DbxException;
import com.dropbox.sync.android.DbxFileSystem;
import com.dropbox.sync.android.DbxPath;

import org.w3c.dom.Text;

/**
 * Created by jacobparker on 3/29/16.
 */
public class MotionSensor extends Activity implements SensorEventListener
{
    private TextView tv;
    private SensorManager mgr; //the management
    private Sensor gyro;
    private Sensor rotationVec;

    private static final float NS2S = 1.0f / 1000000000.0f;
    private final float[] deltaRotationVector = new float[4];
    private float timestamp;

    private boolean hasInitialOrientation = false;
    private float[] initialRotationMatrix;
    private float[] currentRotationMatrixCalibrated;
    private float[] currentRotation = new float[9];

    private float EPSILON = (float) Math.pow(1, -8);

    private float lastX, lastY, lastZ;

    private float deltaX = 0;
    private float deltaY = 0;
    private float deltaZ = 0;

    private float deltaXMax = 0;
    private float deltaYMax = 0;
    private float deltaZMax = 0;

    /* dropbox */

    private static DbxAccountManager mDbxAcctMgr;
    private DbxFileSystem dbxFs;
    private DbxPath mDbxPath;
    static final int REQUEST_LINK_TO_DBX = 0;

    private TextView currentX, currentY, currentZ, currentOmega, maxX, maxY, maxZ;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motionsensor);
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
            mDbxPath = new DbxPath("SwerveDbx/motiondata.txt");
        }

        // Get an instance of the sensor service
        mgr = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        gyro = mgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        rotationVec = mgr.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        if (!this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE))
        {
            Toast.makeText(getApplicationContext(),"Gyroscope sensor is not present", Toast.LENGTH_LONG).show();
        }
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
        // This timestep's delta rotation to be multiplied by the current rotation
        // after computing it from the gyro sample data
        if (timestamp != 0)
        {
            final float dT = (event.timestamp - timestamp) * NS2S;
            float axisX = event.values[0];
            float axisY = event.values[1];
            float axisZ = event.values[2];

            float axis = (float) Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);

            // Normalize the rotation vector if it's big enough to get the axis
            if (axis > EPSILON)
            {
                axisX /= axis;
                axisY /= axis;
                axisZ /= axis;
            }

            currentX.setText(Float.toString(axisX) + " rads/s");
            currentY.setText(Float.toString(axisY) + " rads/s");
            currentZ.setText(Float.toString(axisZ) + " rads/s");
            currentOmega.setText(Float.toString(axis) + " rads/s");

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
        }
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
    }

    @Override
    protected void onResume()
    {
        // Register a listener for the sensor.
        super.onResume();
        mgr.registerListener(this, gyro, SensorManager.SENSOR_DELAY_NORMAL);
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
        if (deltaZ > 0)
        {
            deltaZMax = deltaZ;
            maxZ.setText(Float.toString(deltaZMax));
        }
    }

    public void initializeViews()
    {
        currentX = (TextView) findViewById(R.id.currentX);
        currentY = (TextView) findViewById(R.id.currentY);
        currentZ = (TextView) findViewById(R.id.currentZ);
        currentOmega = (TextView) findViewById(R.id.currentOmega);

        maxX = (TextView) findViewById(R.id.maxX);
        maxY = (TextView) findViewById(R.id.maxY);
        maxZ = (TextView) findViewById(R.id.maxZ);

        for (int i = 0; i < currentRotation.length; i++)
        {
            currentRotation[i] = 0;
        }
    }

    public void linkToDropbox()
    {
        mDbxAcctMgr.unlink();
        mDbxAcctMgr.startLink(this, REQUEST_LINK_TO_DBX);
        if (mDbxAcctMgr.hasLinkedAccount())
        {
            mDbxPath = new DbxPath("Swerve/motiondata.txt");
        }
    }

}
