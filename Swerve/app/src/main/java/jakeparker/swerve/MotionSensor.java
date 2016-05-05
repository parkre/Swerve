package jakeparker.swerve;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Button;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;

/*
 * Created by jacobparker on 3/29/16.
 */
public class MotionSensor extends Activity implements SensorEventListener
{
    public static final String TAG = "MOTIONSENSOR";

    private TextView tv;
    private SensorManager mgr; //the management
    private Sensor accelerometer;
    private Sensor magnetometer;
    private Sensor gyroscope;

    private long timestamp;
    private long lastUpdate;// = 0;
    private long startTime;// = System.currentTimeMillis(); //Dropboxer.getStartTime();
    private long timeLimit = 300000L; // 5 minutes

    // accelerometer-based data structures
    private ArrayList<Float> msSway = new ArrayList();
    private ArrayList<Float> msPitch = new ArrayList();
    private ArrayList<Float> ms3d = new ArrayList();

    // D3 features
    float rangeAccX, rangeAccZ;
    float meanAccX, meanAccZ, stdAccX, stdAccZ, maxAccX, maxAccZ, minAccX, minAccZ;
    float meanOriX, meanOriZ, stdOriX, stdOriZ, maxOriX, maxOriZ, minOriX, minOriZ;
    float meanGyroX, meanGyroZ, stdGyroX, stdGyroZ, maxGyroX, maxGyroZ, minGyroX, minGyroZ;
    float t, tStart, tEnd;

    // D3 data structures
    ArrayList<Float> accX = new ArrayList();
    ArrayList<Float> accZ = new ArrayList();
    ArrayList<Float> oriX = new ArrayList();
    ArrayList<Float> oriZ = new ArrayList();
    ArrayList<Float> gyroX = new ArrayList();
    ArrayList<Float> gyroZ = new ArrayList();

    /* ark variables */
    private float lastX, lastY, lastZ;
    private float deltaXMax = 0;
    private float deltaYMax = 0;
    private float deltaZMax = 0;
    private double tetaDegrees = 0;
    private float deltaX = 0;
    private float deltaY = 0;
    private float deltaZ = 0;
    private float tetaDeg = 0;
    private float timeFl = 0;
    private float timeSkip = 0;

    float[] mGravityClone = new float[3];
    float[] mGravity;
    float[] mGeomagnetic;
    float orientation[] = new float[3];

    // magnetic sensor
    float[] inclination = new float[5];
    float azimut;
    float pitch;
    float roll;
    /* end ark */

    // Dropbox data
    private ArrayList<Float> data3d = new ArrayList(1000);
    private ArrayList<Float> dataAzim = new ArrayList(1000);
    private ArrayList<Float> dataPitch = new ArrayList(1000);
    private ArrayList<Long> time = new ArrayList(1000);
    private int index = 0;

    // file structure
    String appFolderPath;
    String systemPath;

    // display
    private ImageView line;
    private Canvas canvas;
    private Bitmap bmp;
    private float lineLength = 200;
    private TextView currentX, currentY, currentZ, currentMagnitude, currentAngle, currentTime;
    private TextView lineX, lineY;

    /* dropbox */

    private static DbxAccountManager mDbxAcctMgr;
    private DbxFileSystem dbxFs;
    private DbxPath mDbxPath;

    boolean started = false;

    static
    {
        try
        {
            System.loadLibrary("jnilibsvm");
        }
        catch (UnsatisfiedLinkError e)
        {
            Log.d("JNILIBSVM", "FAILED TO LOAD");
        }
    }

    // connect the native functions
    private native void jniSvmTrain(String cmd);
    private native void jniSvmPredict(String cmd);
    private native void jniHelloWorld(String jstring);

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motionsensor);

        startTime = System.currentTimeMillis();
        lastUpdate = System.currentTimeMillis();

        systemPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/";
        appFolderPath = systemPath + "libsvm/";

        jniHelloWorld("test");

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

        // magnetic field sensor
        magnetometer = mgr.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mgr.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_FASTEST);

        if (!this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER))
        {
            Toast.makeText(getApplicationContext(), "Accelerometer sensor is not present", Toast.LENGTH_LONG).show();
        }
    }

    public void drawLine(int angle, int pitch)
    {
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        canvas.drawLine(0, 200, 400, 200, paint);
        canvas.drawLine(200, 0, 200, 400, paint);
        paint.setStrokeWidth(4);
        paint.setColor(Color.DKGRAY);
        canvas.drawPoint(lineLength - lineLength * (float) Math.sin(Math.toRadians(angle)), lineLength - lineLength * (float) Math.sin(Math.toRadians(pitch)), paint);
        line.setImageBitmap(bmp);
        //lineX.setText(Double.toString(lineLength - lineLength * (float) Math.sin(Math.toRadians(angle))));
        //lineY.setText(Double.toString(lineLength * (float) Math.cos(Math.toRadians(angle))));
    }

    public void getEventFeatures()
    {
        if (mGravity != null && mGeomagnetic != null)
        {
            float R[] = new float[9];
            float I[] = new float[9];
            boolean itWorks = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);

            if (itWorks)
            {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);

                // D3 orientation features
                oriZ.add(orientation[0]); // rotation around z axis --> azimut
                oriX.add(orientation[1]); // rotation around x axis --> pitch
                //orientation[2]; // rotation around y axis --> roll

                mGravityClone = mGravity.clone();
                accX.add(mGravityClone[0]);
                accZ.add(mGravityClone[2]);

                // display
                manageRawData(mGravityClone);
            }
        }
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
                dataAzim.set(index, avgSway);
                time.set(index, timestamp);
            }
            catch (IndexOutOfBoundsException e)
            {
                data3d.add(index, avg3d);
                dataAzim.add(index, avgSway);
                time.add(index, timestamp);
            }
            index++;
            ms3d.clear();
            msSway.clear();
            msPitch.clear();
        }

        // write to Dropbox every 60 seconds (100ms * 600 = 60000 ms = 60 seconds)
        if (index >= 600)
        {
            index = 0;
            ArrayList<Float> data3dClone = new ArrayList(data3d);
            ArrayList<Float> dataAzimClone = new ArrayList(dataAzim);
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
            dbx.execute(data3dClone, dataAzimClone, timeClone);
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
        // Java_MotionSensor_Unused
    }

    public void onMagneticFieldSensorChange(SensorEvent event)
    {
        // nothing
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

    public float calcStd(ArrayList<Float> data, float dataAvg)
    {
        float std = 0;
        for (float d : data)
        {
            std += Math.pow(d - dataAvg, 2.0D);
        }
        return (float) Math.sqrt(std);
    }

    public void calcSvmFeatures()
    {
        meanAccX = calcAvg(accX);
        meanAccZ = calcAvg(accZ);
        meanOriX = calcAvg(oriX);
        meanOriZ = calcAvg(oriZ);

        maxAccX = Collections.max(accX);
        minAccX = Collections.min(accX);
        maxAccZ = Collections.max(accZ);
        minAccZ = Collections.min(accZ);

        maxOriX = Collections.max(oriX);
        minOriX = Collections.min(oriX);
        maxOriZ = Collections.max(oriZ);
        minOriZ = Collections.min(oriZ);

        rangeAccX = maxAccX - minAccX;
        rangeAccZ = maxAccZ - minAccZ;

        stdAccX = calcStd(accX, meanAccX);
        stdAccZ = calcStd(accZ, meanAccZ);
        stdOriX = calcStd(oriX, meanOriX);
        stdOriZ = calcStd(oriZ, meanOriZ);
    }

    public void svmTrain()
    {
        // assign model/output paths
        String dataTrainPath = appFolderPath+"heart_scale ";
        String dataPredictPath = appFolderPath+"heart_scale ";
        String modelPath = appFolderPath+"model ";
        String outputPath = appFolderPath+"predict ";

        // make SVM train
        String svmTrainOptions = "-t 2 ";
        jniSvmTrain(svmTrainOptions+dataTrainPath+modelPath);
    }

    public void svmPredict()
    {
        // assign model/output paths
        String dataTrainPath = appFolderPath+"heart_scale ";
        String dataPredictPath = appFolderPath+"heart_scale ";
        String modelPath = appFolderPath+"model ";
        String outputPath = appFolderPath+"predict ";

        // make SVM predict
        jniSvmPredict(dataPredictPath+modelPath+outputPath);
    }

    public void manageRawData(float[] g)
    {
        float x = g[0];
        float y = g[1];
        float z = g[2];

        long curTime = System.currentTimeMillis();
        float diffTime = curTime - lastUpdate;
        timestamp = curTime - startTime;

        float accMagnitude = (float) Math.sqrt(x*x + y*y + z*z);
        float xyMagnitude = (float) Math.sqrt(x*x + y*y);

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
            float avgSway = calcAvg(msSway);
            float avgPitch = calcAvg(msPitch);
            try
            {
                dataAzim.set(index, avgSway);
                dataPitch.set(index, avgPitch);
                time.set(index, timestamp);
            }
            catch (IndexOutOfBoundsException e)
            {
                dataAzim.add(index, avgSway);
                dataPitch.add(index, avgSway);
                time.add(index, timestamp);
            }
            index++;
            ms3d.clear();
            msSway.clear();
            msPitch.clear();
        }

        // write to Dropbox every 60 seconds (100ms * 600 = 60000 ms = 60 seconds)
        if (index >= 600)
        {
            index = 0;
            ArrayList<Float> dataAzimClone = new ArrayList(dataAzim);
            ArrayList<Float> dataPitchClone = new ArrayList(dataPitch);
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
            dbx.execute(dataAzimClone, dataPitchClone, timeClone);
        }
    }

    @Override
    public final void onSensorChanged(SensorEvent event)
    {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
        {
            mGravity = event.values.clone();
            //onAccelerometerSensorChange(event);
        }
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE)
        {
            //onGyroscopeSensorChange(event);
        }
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
        {
            mGeomagnetic = event.values.clone();
            //onMagneticFieldSensorChange(event);
        }

        getEventFeatures();
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy)
    {
        // suh dude
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        mgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        mgr.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_FASTEST);
        //mgr.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        mgr.unregisterListener(this);
    }

    public void displayCleanValues()
    {
        //currentX.setText("–");
        //currentY.setText("–");
        //currentZ.setText("–");
    }

    public void initializeViews()
    {
        //currentX = (TextView) findViewById(R.id.currentX);
        //currentY = (TextView) findViewById(R.id.currentY);
        //currentZ = (TextView) findViewById(R.id.currentZ);
        //currentMagnitude = (TextView) findViewById(R.id.currentOmega);
        currentAngle = (TextView) findViewById(R.id.angle);
        currentTime = (TextView) findViewById(R.id.time);
    }

    public void startSwerve(View v)
    {
        if (started == false)
        {
            tStart = System.currentTimeMillis();

            // start sensors
            mgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
            mgr.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_FASTEST);

            started = true;
        }
    }

    public void stopSwerve(View v)
    {
        if (started == true)
        {
            super.onPause();
            mgr.unregisterListener(this);
            tEnd = System.currentTimeMillis();
            t = tEnd - tStart;
            displayCleanValues();
            currentTime.setText(Float.toString(t));
        }
    }

    /*
    * Some utility functions
    * */
    private void CreateAppFolderIfNeed()
    {
        // 1. create app folder if necessary
        File folder = new File(appFolderPath);

        if (!folder.exists())
        {
            folder.mkdir();
            Log.d(TAG,"Appfolder is not existed, create one");
        }
        else
        {
            Log.w(TAG,"WARN: Appfolder has not been deleted");
        }
    }

    private void copyAssetsDataIfNeed()
    {
        String assetsToCopy[] = {"swerve_predict","swerve_train","swerve"};
        //String targetPath[] = {C.systemPath+C.INPUT_FOLDER+C.INPUT_PREFIX+AudioConfigManager.inputConfigTrain+".wav", C.systemPath+C.INPUT_FOLDER+C.INPUT_PREFIX+AudioConfigManager.inputConfigPredict+".wav",C.systemPath+C.INPUT_FOLDER+"SomeoneLikeYouShort.mp3"};

        for(int i=0; i < assetsToCopy.length; i++)
        {
            String from = assetsToCopy[i];
            String to = appFolderPath+from;

            // 1. check if file exist
            File file = new File(to);
            if (file.exists())
            {
                Log.d(TAG, "copyAssetsDataIfNeed: file exist, no need to copy:"+from);
            }
            else
            {
                // do copy
                boolean copyResult = copyAsset(getAssets(), from, to);
                Log.d(TAG, "copyAssetsDataIfNeed: copy result = " + copyResult + " of file = " + from);
            }
        }
    }

    private boolean copyAsset(AssetManager assetManager, String fromAssetPath, String toPath)
    {
        InputStream in = null;
        OutputStream out = null;
        try
        {
            in = assetManager.open(fromAssetPath);
            new File(toPath).createNewFile();
            out = new FileOutputStream(toPath);
            copyFile(in, out);
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
            return true;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            Log.e(TAG, "[ERROR]: copyAsset: unable to copy file = " + fromAssetPath);
            return false;
        }
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException
    {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1)
        {
            out.write(buffer, 0, read);
        }
    }
}
