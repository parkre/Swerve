package jakeparker.swerve;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
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

    public static long startTime = 0;
    public String name = "";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dropboxer);


        EditText editName = (EditText) findViewById(R.id.name);
        editName.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
            {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_GO)
                {
                    go(null);
                    handled = true;
                }
                return handled;
            }
        });


        try
        {
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
            dbxFs = DbxFileSystem.forAccount(mDbxAcctMgr.getLinkedAccount());
            //mDbxPath = new DbxPath("SwerveDbx/swervedata.txt");
        }
        catch (Exception e)
        {
            // logic
        }
        //go(null);
    }

    public void go(View v)
    {
        name = ((EditText) findViewById(R.id.name)).getEditableText().toString();
        if (name != null)
        {
            try
            {
                mDbxPath = new DbxPath("SwerveDbx/" + name + "/swervedata.txt");
            }
            catch (Exception e)
            {
                // logic
            }
        }
        Intent intent = new Intent(this, MotionSensor.class);
        startTime = System.currentTimeMillis();
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
