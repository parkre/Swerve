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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;

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
    private long timeLimit = 60000L; // 1 minute

    // accelerometer-based data structures
    private ArrayList<Float> msSway = new ArrayList();
    private ArrayList<Float> msPitch = new ArrayList();
    private ArrayList<Float> ms3d = new ArrayList();

    // D3 features
    float rangeAccX, rangeAccZ, rangeGyroX, rangeGyroZ, rangeOriX, rangeOriZ;
    float meanAccX, meanAccZ, stdAccX, stdAccZ, maxAccX, maxAccZ, minAccX, minAccZ;
    float meanOriX, meanOriZ, stdOriX, stdOriZ, maxOriX, maxOriZ, minOriX, minOriZ;
    float meanGyroX, meanGyroZ, stdGyroX, stdGyroZ, maxGyroX, maxGyroZ, minGyroX, minGyroZ;
    long t, tStart, tEnd;
    ArrayList<Float> instance = new ArrayList();

    // D3 data structures
    ArrayList<Float> accX = new ArrayList();
    ArrayList<Float> accZ = new ArrayList();
    ArrayList<Float> gyroX = new ArrayList();
    ArrayList<Float> gyroZ = new ArrayList();
    ArrayList<Float> oriX = new ArrayList();
    ArrayList<Float> oriZ = new ArrayList();

    // svm variables
    boolean trainMode = false;
    boolean predictMode = false;

    final String POS = "1";
    final String NEG = "0";

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

    float[] gClone = new float[3];
    float[] g;
    float[] m;
    float[] r;
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
    private String name;

    /* dropbox */

    private static DbxAccountManager mDbxAcctMgr;
    private DbxFileSystem dbxFs;
    private DbxPath mDbxPath;

    boolean started = false;
    boolean paused = false;

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

    /*
     * add user options for:
     *
     * 3) how often to write to dropbox (default: 1 minute) - add [at the end] option
     */

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motionsensor);

        startTime = System.currentTimeMillis();
        lastUpdate = System.currentTimeMillis();

        systemPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/";
        appFolderPath = systemPath + "libsvm/";

        // create necessary folder to save model files
        CreateAppFolderIfNeed();
        copyAssetsDataIfNeed();

        Log.d(TAG, "APP FOLDER PATH = " + appFolderPath);

        name = getIntent().getStringExtra("name");

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
            catch (DbxException.Unauthorized e)
            {
                // TO DO
                e.printStackTrace();
            }
        }

        // initialize display
        bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bmp);

        tStart = System.currentTimeMillis();
        started = true;

        // Get an instance of the sensor service
        mgr = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // accelerometer
        accelerometer = mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        //mgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        mgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        // gyroscope
        gyroscope = mgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        //mgr.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
        mgr.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);

        // magnetic field sensor
        magnetometer = mgr.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        //mgr.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_FASTEST);
        mgr.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    /*
     *
     */
    public void getEventFeatures()
    {
        if (g != null && m != null)
        {
            float R[] = new float[9];
            float I[] = new float[9];
            boolean itWorks = SensorManager.getRotationMatrix(R, I, g, m);

            if (itWorks)
            {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);

                // D3 orientation features
                oriZ.add(Math.abs(orientation[0])); // rotation around z axis --> azimut
                oriX.add(Math.abs(orientation[1])); // rotation around x axis --> pitch
                //orientation[2]; // rotation around y axis --> roll
                gClone = g.clone();
                accX.add(Math.abs(gClone[0]));
                accZ.add(Math.abs(gClone[2]));

                // display and dropbox
                swerve(gClone);
            }
        }
    }

    public void getAccEventFeatures(float[] e)
    {
        // acceleration along x, y, z, respectively
        float x = e[0];
        float y = e[1];
        float z = e[2];

        accX.add(Math.abs(x));
        accZ.add(Math.abs(z));

        // display and dropbox
        swerve(e);
    }

    public void getGyroEventFeatures(float[] e)
    {
        // rate of rotation around x, y, z, respectively
        float x = e[0];
        //float y = e[1];
        float z = e[2];

        gyroX.add(Math.abs(x));
        gyroZ.add(Math.abs(z));
    }


    /*
     *
     */
    public void swerve(float[] g)
    {
        float x = g[0];
        float y = g[1];
        float z = g[2];

        long curTime = System.currentTimeMillis();
        float diffTime = curTime - lastUpdate;
        timestamp = curTime - tStart;

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
        draw(iSway, iPitch);

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
            sendToDropbox();
        }

        // implement 5 minute time limit
        // timeLimit = 300000 ms
        if (curTime - tStart > timeLimit)
        {
            sendToDropbox();
            stopSwerve(null);
        }
    }

    public void calcSvmFeatures()
    {
        meanAccX = calcAvg(accX);
        meanAccZ = calcAvg(accZ);
        meanGyroX = calcAvg(gyroX);
        meanGyroZ = calcAvg(gyroZ);
        //meanOriX = calcAvg(oriX);
        //meanOriZ = calcAvg(oriZ);

        maxAccX = Collections.max(accX);
        minAccX = Collections.min(accX);
        maxAccZ = Collections.max(accZ);
        minAccZ = Collections.min(accZ);

        maxGyroX = Collections.max(gyroX);
        minGyroX = Collections.min(gyroX);
        maxGyroZ = Collections.max(gyroZ);
        minGyroZ = Collections.min(gyroZ);

        //maxOriX = Collections.max(oriX);
        //minOriX = Collections.min(oriX);
        //maxOriZ = Collections.max(oriZ);
        //minOriZ = Collections.min(oriZ);

        rangeAccX = maxAccX - minAccX;
        rangeAccZ = maxAccZ - minAccZ;
        rangeGyroX = maxGyroX - minGyroX;
        rangeGyroZ = maxGyroZ - minGyroZ;
        //rangeOriX = maxOriX - minOriX;
        //rangeOriZ = maxOriZ - minOriZ;

        stdAccX = calcStd(accX, meanAccX);
        stdAccZ = calcStd(accZ, meanAccZ);
        stdGyroX = calcStd(gyroX, meanGyroX);
        stdGyroZ = calcStd(gyroZ, meanGyroZ);
        //stdOriX = calcStd(oriX, meanOriX);
        //stdOriZ = calcStd(oriZ, meanOriZ);

        /* store in arraylist */
        instance.add(rangeAccX);    // 1
        instance.add(rangeAccZ);    // 2
        //instance.add(rangeOriX);
        //instance.add(rangeOriZ);
        instance.add(stdAccX);      // 3
        instance.add(stdAccZ);      // 4
        instance.add(stdGyroX);     // 5
        instance.add(stdGyroZ);     // 6
        instance.add(meanAccX);     // 7
        instance.add(meanAccZ);     // 8
        instance.add(meanGyroX);    // 9
        instance.add(meanGyroZ);    // 10
        //instance.add(maxAccX);
        //instance.add(minAccX);
        //instance.add(maxAccZ);
        instance.add(maxGyroX);     // 11
        instance.add(maxGyroZ);     // 12
        //instance.add(minOriX);
        instance.add(maxAccX);      // 12
        instance.add(maxAccZ);      // 14
        //instance.add(minOriZ);
        instance.add((float)t);     // 15
    }

    /*
     * take motion plus given class and append it to set
     */
    public void svmEvaluate()
    {
        // assign model/output paths
        String dataTrainPath = appFolderPath+"heart_scale ";
        String dataPredictPath = appFolderPath+"heart_scale ";
        String modelPath = appFolderPath+"model ";
        String outputPath = appFolderPath+"predict ";

        // make SVM train
        String svmTrainOptions = "-t 2 ";
        jniSvmTrain(svmTrainOptions + dataTrainPath + modelPath);

        // make SVM predict
        jniSvmPredict(dataPredictPath + modelPath + outputPath);

        //displaySvmResults("eval_predict");
    }

    public void svmPredict(String line)
    {
        Log.d(TAG, "CURRENTLY IN SVM PREDICT");
        // assign model/output paths
        String dataTrainPath = appFolderPath+"swerve_data ";
        String dataPredictPath = appFolderPath+"swerve_instance ";
        String modelPath = appFolderPath+"swerve_model ";
        String outputPath = appFolderPath+"swerve_realtime_predict ";

        String svmTrainOptions = "-t 2 ";
        jniSvmTrain(svmTrainOptions + dataTrainPath + modelPath);

        // make SVM predict
        jniSvmPredict(dataPredictPath + modelPath + outputPath);

        displaySvmResults("swerve_realtime_predict");
    }

    /*
     * take motion plus given class and append it to set
     */
    public void appendSvmDataSet(String line)
    {
        Log.d(TAG, "IN APPENDSVMDATASET");
        try
        {
            FileWriter fw = new FileWriter(appFolderPath + "swerve_data", true);
            fw.write(line + "\n");
            fw.close();
        }
        catch(IOException e)
        {
            Log.d(TAG, "APPENDSVMDATASET" + e.getMessage());
        }
    }

    public void displaySvmResults(String filename)
    {
        Log.d(TAG, "IN DISPLAY PREDICTION");
        File file = new File(appFolderPath, filename);
        try
        {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line, content = "";
            while ((line = br.readLine()) != null)
            {
                content += line;
            }
            currentAngle.setText(content);
        }
        catch(IOException e)
        {
            Log.d(TAG, "DISPLAYSVMRESULTS" + e.getMessage());
        }
    }

    public void makeModel(View v)
    {
        int trainClass = v.getId();
        if (predictMode == false)
        {
            calcSvmFeatures();
            trainMode = true;
            ((Button) findViewById(R.id.predict)).setEnabled(false);
            StringBuilder sb = new StringBuilder();
            if (trainClass == R.id.train_normal)
            {
                ((Button) findViewById(R.id.train_notnormal)).setEnabled(false);
                sb.append(NEG);
            }
            else
            {
                ((Button) findViewById(R.id.train_normal)).setEnabled(false);
                sb.append(POS);
            }
            /* cycle through arraylist instance */
            int i = 1;
            for (float f : instance)
            {
                sb.append(" " + i + ":" + f);
                i++;
            }
            svmEvaluate();
            appendSvmDataSet(sb.toString());
            started = false;
            // reset arraylists
            clearD3Structures();
        }
    }

    public void makePrediction(View v)
    {
        if (trainMode == false)
        {
            calcSvmFeatures();
            ((Button) findViewById(R.id.train_normal)).setEnabled(false);
            ((Button) findViewById(R.id.train_notnormal)).setEnabled(false);
            predictMode = true;

            StringBuilder sb = new StringBuilder();
            /* cycle through arraylist instance */
            int i = 1;
            for (float f : instance)
            {
                sb.append(i + ":" + f + " ");
                i++;
            }

            try
            {
                FileWriter fw = new FileWriter(appFolderPath + "/swerve_instance", false);
                fw.write(sb.toString());
                fw.close();
            }
            catch(IOException e)
            {
                Log.d(TAG, "MAKEPREDICTION_IOE " + e.getMessage());
            }
            svmPredict(sb.toString());
            started = false;
            // reset arraylists
            clearD3Structures();
        }
    }

    public void startSwerve(View v)
    {
        if (started == false || paused == true)
        {
            if (paused == false)
            {
                tStart = System.currentTimeMillis();
            }

            // reset arraylists
            //clearD3Structures();

            // start sensors
            mgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
            mgr.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_FASTEST);

            ((Button) findViewById(R.id.train_normal)).setEnabled(false);
            ((Button) findViewById(R.id.train_notnormal)).setEnabled(false);
            ((Button) findViewById(R.id.predict)).setEnabled(false);

            started = true;
        }
    }

    public void stopSwerve(View v)
    {
        Log.d(TAG, "STOP SWERVE");
        if (started == true)
        {
            super.onPause();
            mgr.unregisterListener(this);
            tEnd = System.currentTimeMillis();
            t = tEnd - tStart;
            //currentTime.setText(Float.toString(t));
            //calcSvmFeatures();
            //started = false;
            paused = true;
            ((Button) findViewById(R.id.train_normal)).setEnabled(true);
            ((Button) findViewById(R.id.train_notnormal)).setEnabled(true);
            ((Button) findViewById(R.id.predict)).setEnabled(true);
        }
    }

    @Override
    public final void onSensorChanged(SensorEvent event)
    {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
        {
            g = event.values.clone();
            getAccEventFeatures(g);
            //onAccelerometerSensorChange(event);
        }
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE)
        {
            r = event.values.clone();
            getGyroEventFeatures(r);
        }
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
        {
            //m = event.values.clone();
        }

        //getEventFeatures();
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

    public void sendToDropbox()
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

    public void draw(int angle, int pitch)
    {
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        canvas.drawLine(0, 200, 400, 200, paint);
        canvas.drawLine(200, 0, 200, 400, paint);
        paint.setStrokeWidth(4);
        paint.setColor(Color.DKGRAY);
        canvas.drawPoint(lineLength - lineLength * (float) Math.sin(Math.toRadians(angle)), lineLength - lineLength * (float) Math.sin(Math.toRadians(pitch)), paint);
        line.setImageBitmap(bmp);
    }

    public void clearD3Structures()
    {
        instance.clear();
        accX.clear();
        accZ.clear();
        oriX.clear();
        oriZ.clear();
        gyroZ.clear();
        gyroZ.clear();
        tStart = 0;
        timestamp = 0;
        started = false;
        paused = false;
        //clear plot display
    }

    /*
    * Some utility functions
    * */
    private void CreateAppFolderIfNeed()
    {
        // create app folder if necessary
        File folder = new File(appFolderPath);

        if (!folder.exists())
        {
            folder.mkdir();
            Log.d(TAG,"Appfolder is not existed, create one");
        }
        else
        {
            Log.w(TAG, "WARN: Appfolder has not been deleted");
        }
    }

    private void copyAssetsDataIfNeed()
    {
        String assetsToCopy[] = {"heart_scale","heart_scale_predict","heart_scale_train"};
        //String targetPath[] = {C.systemPath+C.INPUT_FOLDER+C.INPUT_PREFIX+AudioConfigManager.inputConfigTrain+".wav", C.systemPath+C.INPUT_FOLDER+C.INPUT_PREFIX+AudioConfigManager.inputConfigPredict+".wav",C.systemPath+C.INPUT_FOLDER+"SomeoneLikeYouShort.mp3"};

        for (int i = 0; i < assetsToCopy.length; i++)
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

    /*
    Usage: svm-train [options] training_set_file [model_file]"
		"options:
		-s svm_type : set type of SVM (default 0)
			0 -- C-SVC		(multi-class classification)\n
			1 -- nu-SVC		(multi-class classification)\n
			2 -- one-class SVM\n
			3 -- epsilon-SVR	(regression)\n
			4 -- nu-SVR		(regression)\n
		-t kernel_type : set type of kernel function (default 2)\n
			0 -- linear: u'*v\n
			1 -- polynomial: (gamma*u'*v + coef0)^degree\n
			2 -- radial basis function: exp(-gamma*|u-v|^2)\n
			3 -- sigmoid: tanh(gamma*u'*v + coef0)\n
			4 -- precomputed kernel (kernel values in training_set_file)\n
		-d degree : set degree in kernel function (default 3)\n
		-g gamma : set gamma in kernel function (default 1/num_features)\n
		-r coef0 : set coef0 in kernel function (default 0)\n
		-c cost : set the parameter C of C-SVC, epsilon-SVR, and nu-SVR (default 1)\n
		-n nu : set the parameter nu of nu-SVC, one-class SVM, and nu-SVR (default 0.5)\n
		-p epsilon : set the epsilon in loss function of epsilon-SVR (default 0.1)\n
		-m cachesize : set cache memory size in MB (default 100)\n
		-e epsilon : set tolerance of termination criterion (default 0.001)\n
		-h shrinking : whether to use the shrinking heuristics, 0 or 1 (default 1)\n
		-b probability_estimates : whether to train a SVC or SVR model for probability estimates, 0 or 1 (default 0)\n
		-wi weight : set the parameter C of class i to weight*C, for C-SVC (default 1)\n
		-v n: n-fold cross validation mode\n
	    -q : quiet mode (no outputs)\n
     */
}
