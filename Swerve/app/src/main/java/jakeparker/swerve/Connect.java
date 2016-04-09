package jakeparker.swerve;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;
import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.TextView.OnEditorActionListener;

import com.dropbox.sync.android.DbxAccountManager;
import com.dropbox.sync.android.DbxFile;
import com.dropbox.sync.android.DbxFileSystem;
import com.dropbox.sync.android.DbxPath;

import org.json.JSONArray;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.net.URL;
import java.net.URLConnection;
import java.lang.String;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by jacobparker on 4/8/16.
 */
public class Connect extends AsyncTask<ArrayList<?>, Void, Boolean>
{
    //private DbxAccountManager mDbxAcctMgr;
    //private DbxFileSystem dbxFs;
    private DbxPath mDbxPath;

    public Connect()
    {
        Log.d("LOG", "Connect");
    }

    @Override
    protected Boolean doInBackground(ArrayList<?>... data)
    {
        ArrayList<?> motion = data[0];
        ArrayList<?> time = data[1];

        DbxFile mDbxFile = null;
        //dbxFs = Dropboxer.getDbxFs();
        mDbxPath = Dropboxer.getDbxPath();
        try
        {
            DbxFileSystem dbxFs = DbxFileSystem.forAccount(Dropboxer.mDbxAcctMgr.getLinkedAccount());
            if (dbxFs.isFile(mDbxPath)) {
                mDbxFile = dbxFs.open(mDbxPath);
            }
            else
            {
                mDbxFile = dbxFs.create(mDbxPath);
                mDbxFile.appendString("time(milliseconds): angle\n\n");
            }
            for (int i = 0; i < motion.size(); i++)
            {
                mDbxFile.appendString(time.get(i) + ": " + motion.get(i) + ", ");
            }
            mDbxFile.close();
        }
        catch (IOException e) {
            // TO DO
            e.printStackTrace();
            return false;
        }
        return true;
    }
}

