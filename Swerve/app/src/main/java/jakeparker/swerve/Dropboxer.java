package jakeparker.swerve;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.content.Intent;

import com.dropbox.sync.android.DbxAccountManager;
import com.dropbox.sync.android.DbxException;
import com.dropbox.sync.android.DbxFile;
import com.dropbox.sync.android.DbxFileSystem;
import com.dropbox.sync.android.DbxPath;

import org.json.JSONObject;
import org.json.JSONArray;

/**
 * Created by jacobparker on 4/4/16.
 */
public class Dropboxer extends Activity //possibly extend async instead
{
    public static DbxAccountManager mDbxAcctMgr;
    private static DbxFileSystem dbxFs;
    private static DbxPath mDbxPath;
    static final int REQUEST_LINK_TO_DBX = 0;

    public static final long startTime = System.currentTimeMillis();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dropboxer);

        try
        {
            mDbxAcctMgr = DbxAccountManager.getInstance(getApplicationContext(), "KEY", "SECRET");
            if (!mDbxAcctMgr.hasLinkedAccount())
            {
                Toast.makeText(this, "Dropbox is not connected. Attempting to connect...", Toast.LENGTH_LONG).show();
                mDbxAcctMgr.startLink(Dropboxer.this, REQUEST_LINK_TO_DBX);
            }
            if (mDbxAcctMgr.hasLinkedAccount())
            {
                Toast.makeText(this, "Dropbox is connected", Toast.LENGTH_LONG).show();
            }
            dbxFs = DbxFileSystem.forAccount(mDbxAcctMgr.getLinkedAccount());
            mDbxPath = new DbxPath("SwerveDbx/swervedata.txt");
        }
        catch (Exception e)
        {
            // logic
        }
        go(null);
    }

    public void go(View v)
    {
        Intent intent = new Intent(this, MotionSensor.class);
        startActivity(intent);
    }

    public static DbxAccountManager getDbxAccountManager()
    {
        return mDbxAcctMgr;
    }

    public static DbxPath getDbxPath()
    {
        return mDbxPath;
    }

    public static DbxFileSystem getDbxFs()
    {
        return dbxFs;
    }
}
