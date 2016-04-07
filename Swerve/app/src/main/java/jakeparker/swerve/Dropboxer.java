package jakeparker.swerve;

import android.app.Activity;
import android.os.Bundle;
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
    private static DbxAccountManager mDbxAcctMgr;
    private DbxFileSystem dbxFs;
    private DbxPath mDbxPath;
    static final int REQUEST_LINK_TO_DBX = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dropboxer);

        mDbxAcctMgr = DbxAccountManager.getInstance(getApplicationContext(), "utyphk8d2i17a6v", "011iw45c0g7r1jm");
        if (!mDbxAcctMgr.hasLinkedAccount())
        {
            Toast.makeText(this, "Dropbox is not connected. Attempting to connect...", Toast.LENGTH_LONG).show();
            mDbxAcctMgr.startLink(Dropboxer.this, REQUEST_LINK_TO_DBX);
        }
        if (mDbxAcctMgr.hasLinkedAccount())
        {
            Toast.makeText(this, "Dropbox is connected", Toast.LENGTH_LONG).show();
        }
        go();
    }

    public void writeToDbx(DbxFile mDbxFile)
    {

    }

    public void readFromDbx(DbxFile mDbxFile)
    {

    }

    public void go()
    {
        Intent intent = new Intent(this, MotionSensor.class);
        startActivity(intent);
    }

    public static DbxAccountManager getDbxAccountManager()
    {
        return mDbxAcctMgr;
    }
}
