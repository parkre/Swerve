package jakeparker.swerve;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.dropbox.sync.android.DbxAccountManager;


public class SplashScreenActivity extends Activity
{
    private static DbxAccountManager mDbxAcctMgr;
    static int REQUEST_LINK_TO_DBX = 0;

    private static long SPLASH_MILLIS = 450;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        /*requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    */
        setContentView(R.layout.activity_splash_screen);

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable()
        {

            @Override
            public void run()
            {

                Intent intent = new Intent(SplashScreenActivity.this,
                        MotionSensor.class);
                startActivity(intent);

            }

        }, SPLASH_MILLIS);
    }
}
