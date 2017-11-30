
/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.activities;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import junit.framework.Assert;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import de.blinkt.openvpn.R;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ConfigParser.ConfigParseError;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.fragments.Utils;
import de.blinkt.openvpn.views.FileSelectLayout;

import static de.blinkt.openvpn.views.FileSelectLayout.FileSelectCallback;

public class ConfigConverter extends BaseActivity implements FileSelectCallback {

    public static final String IMPORT_PROFILE = "de.blinkt.openvpn.IMPORT_PROFILE";
    private static final int RESULT_INSTALLPKCS12 = 7;
    private static final int CHOOSE_FILE_OFFSET = 1000;
    public static final String VPNPROFILE = "vpnProfile";
    private static final int PERMISSION_REQUEST_EMBED_FILES = 37231;
    private static final int PERMISSION_REQUEST_READ_URL = PERMISSION_REQUEST_EMBED_FILES + 1;

    private VpnProfile mResult;

    private transient List<String> mPathsegments;

    private String mAliasName = null;


    private Map<Utils.FileType, FileSelectLayout> fileSelectMap = new HashMap<>();
    private String mEmbeddedPwFile;
    private Vector<String> mLogEntries = new Vector<>();
    private Uri mSourceUri;
    private AsyncTask<Void, Void, Integer> mImportTask;
    private LinearLayout mLogLayout;
    private TextView mProfilenameLabel;

    private int number = 0;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // Permission declined, do nothing
        if (grantResults.length == 0 || grantResults[0] == PackageManager.PERMISSION_DENIED)
            return;

        // Reset file select dialogs

        if (requestCode == PERMISSION_REQUEST_EMBED_FILES)
            embedFiles(null);

        else if (requestCode == PERMISSION_REQUEST_READ_URL) {
           /* if (mSourceUri != null)
                doImportUri(mSourceUri);*/
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mResult != null)
            outState.putSerializable(VPNPROFILE, mResult);
        outState.putString("mAliasName", mAliasName);


        String[] logentries = mLogEntries.toArray(new String[mLogEntries.size()]);

        outState.putStringArray("logentries", logentries);

        int[] fileselects = new int[fileSelectMap.size()];
        int k = 0;
        for (Utils.FileType key : fileSelectMap.keySet()) {
            fileselects[k] = key.getValue();
            k++;
        }
        outState.putIntArray("fileselects", fileselects);
        outState.putString("pwfile", mEmbeddedPwFile);
        outState.putParcelable("mSourceUri", mSourceUri);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent result) {
        Log.e("config", "206");
        if (requestCode == RESULT_INSTALLPKCS12 && resultCode == Activity.RESULT_OK) {
            showCertDialog();
        }

        if (resultCode == Activity.RESULT_OK && requestCode >= CHOOSE_FILE_OFFSET) {
            Utils.FileType type = Utils.FileType.getFileTypeByValue(requestCode - CHOOSE_FILE_OFFSET);


            FileSelectLayout fs = fileSelectMap.get(type);
            fs.parseResponse(result, this);

            String data = fs.getData();

            switch (type) {
                case USERPW_FILE:
                    mEmbeddedPwFile = data;
                    Log.e("config", "222");
                    break;
                case PKCS12:
                    mResult.mPKCS12Filename = data;
                    Log.e("config", "226");
                    break;
                case TLS_AUTH_FILE:
                    Log.e("config", "229");
                    mResult.mTLSAuthFilename = data;
                    break;
                case CA_CERTIFICATE:
                    Log.e("config", "233");
                    mResult.mCaFilename = data;
                    break;
                case CLIENT_CERTIFICATE:
                    Log.e("config", "237");
                    mResult.mClientCertFilename = data;
                    break;
                case KEYFILE:
                    Log.e("config", "241");
                    mResult.mClientKeyFilename = data;
                    break;
                case CRL_FILE:
                    Log.e("config", "245");
                    mResult.mCrlFilename = data;
                    break;
                default:
                    Log.e("config", "249");
                    Assert.fail();
            }
        }

        super.onActivityResult(requestCode, resultCode, result);
    }


    public void showCertDialog() {
        try {
            //noinspection WrongConstant
            KeyChain.choosePrivateKeyAlias(this,
                    new KeyChainAliasCallback() {

                        public void alias(String alias) {
                            // Credential alias selected.  Remember the alias selection for future use.
                            mResult.mAlias = alias;
                            saveProfile();
                        }


                    },
                    new String[]{"RSA"}, // List of acceptable key types. null for any
                    null,                        // issuer, null for any
                    mResult.mServerName,      // host name of server requesting the cert, null if unavailable
                    -1,                         // port of server requesting the cert, -1 if unavailable
                    mAliasName);                       // alias to preselect, null if unavailable
        } catch (ActivityNotFoundException anf) {
            Builder ab = new AlertDialog.Builder(this);
            ab.setTitle(R.string.broken_image_cert_title);
            ab.setMessage(R.string.broken_image_cert);
            ab.setPositiveButton(android.R.string.ok, null);
            ab.show();
        }
    }

    private String embedFile(String filename, Utils.FileType type, boolean onlyFindFileAndNullonNotFound) {
        if (filename == null)
            return null;

        // Already embedded, nothing to do
        if (VpnProfile.isEmbedded(filename))
            return filename;

        File possibleFile = findFile(filename, type);
        if (possibleFile == null)
            if (onlyFindFileAndNullonNotFound)
                return null;
            else
                return filename;
        else if (onlyFindFileAndNullonNotFound)
            return possibleFile.getAbsolutePath();
        else
            return readFileContent(possibleFile, type == Utils.FileType.PKCS12);

    }

    private File findFile(String filename, Utils.FileType fileType) {
        File foundfile = findFileRaw(filename);

        if (foundfile == null && filename != null && !filename.equals("")) {

        }
        fileSelectMap.put(fileType, null);

        return foundfile;
    }

    private File findFileRaw(String filename) {
        if (filename == null || filename.equals(""))
            return null;

        // Try diffent path relative to /mnt/sdcard
        File sdcard = Environment.getExternalStorageDirectory();
        File root = new File("/");

        HashSet<File> dirlist = new HashSet<>();

        for (int i = mPathsegments.size() - 1; i >= 0; i--) {
            String path = "";
            for (int j = 0; j <= i; j++) {
                path += "/" + mPathsegments.get(j);
            }
            // Do a little hackish dance for the Android File Importer
            // /document/primary:ovpn/openvpn-imt.conf


            if (path.indexOf(':') != -1 && path.lastIndexOf('/') > path.indexOf(':')) {
                String possibleDir = path.substring(path.indexOf(':') + 1, path.length());
                // Unquote chars in the  path
                try {
                    possibleDir = URLDecoder.decode(possibleDir, "UTF-8");
                } catch (UnsupportedEncodingException ignored) {
                }

                possibleDir = possibleDir.substring(0, possibleDir.lastIndexOf('/'));


                dirlist.add(new File(sdcard, possibleDir));

            }
            dirlist.add(new File(path));


        }
        dirlist.add(sdcard);
        dirlist.add(root);


        String[] fileparts = filename.split("/");
        for (File rootdir : dirlist) {
            String suffix = "";
            for (int i = fileparts.length - 1; i >= 0; i--) {
                if (i == fileparts.length - 1)
                    suffix = fileparts[i];
                else
                    suffix = fileparts[i] + "/" + suffix;

                File possibleFile = new File(rootdir, suffix);
                if (possibleFile.canRead())
                    return possibleFile;

            }
        }
        return null;
    }

    String readFileContent(File possibleFile, boolean base64encode) {
        byte[] filedata;
        try {
            filedata = readBytesFromFile(possibleFile);
        } catch (IOException e) {
            log(e.getLocalizedMessage());
            return null;
        }

        String data;
        if (base64encode) {
            data = Base64.encodeToString(filedata, Base64.DEFAULT);
        } else {
            data = new String(filedata);

        }

        return VpnProfile.DISPLAYNAME_TAG + possibleFile.getName() + VpnProfile.INLINE_TAG + data;

    }

    private byte[] readBytesFromFile(File file) throws IOException {
        InputStream input = new FileInputStream(file);

        long len = file.length();
        if (len > VpnProfile.MAX_EMBED_FILE_SIZE)
            throw new IOException("File size of file to import too large.");

        // Create the byte array to hold the data
        byte[] bytes = new byte[(int) len];

        // Read in the bytes
        int offset = 0;
        int bytesRead;
        while (offset < bytes.length
                && (bytesRead = input.read(bytes, offset, bytes.length - offset)) >= 0) {
            offset += bytesRead;
        }

        input.close();
        return bytes;
    }

    void embedFiles(ConfigParser cp) {
        // This where I would like to have a c++ style
        // void embedFile(std::string & option)

        if (mResult.mPKCS12Filename != null) {
            File pkcs12file = findFileRaw(mResult.mPKCS12Filename);
            if (pkcs12file != null) {
                mAliasName = pkcs12file.getName().replace(".p12", "");
            } else {
                mAliasName = "Imported PKCS12";
            }
        }


        mResult.mCaFilename = embedFile(mResult.mCaFilename, Utils.FileType.CA_CERTIFICATE, false);
        mResult.mClientCertFilename = embedFile(mResult.mClientCertFilename, Utils.FileType.CLIENT_CERTIFICATE, false);
        mResult.mClientKeyFilename = embedFile(mResult.mClientKeyFilename, Utils.FileType.KEYFILE, false);
        mResult.mTLSAuthFilename = embedFile(mResult.mTLSAuthFilename, Utils.FileType.TLS_AUTH_FILE, false);
        mResult.mPKCS12Filename = embedFile(mResult.mPKCS12Filename, Utils.FileType.PKCS12, false);
        mResult.mCrlFilename = embedFile(mResult.mCrlFilename, Utils.FileType.CRL_FILE, true);
        if (cp != null) {
            mEmbeddedPwFile = cp.getAuthUserPassFile();
            mEmbeddedPwFile = embedFile(cp.getAuthUserPassFile(), Utils.FileType.USERPW_FILE, false);
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.config_converter);
        new get().execute();
    }

    class get extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {

            String srt = new httpcontent().GET("http://sv.icodef.com/user/api/getserver", true);
            Log.e("config608", srt);
            JSONObject jsonObject = null;
            JSONArray rows;
            try {
                jsonObject = new JSONObject(srt);
                rows = jsonObject.getJSONArray("rows");
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject item = rows.getJSONObject(i);
                    saveprofile(item.getString("name"), item.getString("config"), item.getString("count"));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            finish();
        }
    }


    private Boolean saveprofile(String name, String config, String count) {
        FileOutputStream outputStream;
        try {
            outputStream = new FileOutputStream(getFilesDir() + "/" + name + ".ovpn");
            outputStream.write(config.getBytes());
            outputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        File file = new File(getFilesDir() + "/" + name + ".ovpn");


        try {
            doImport(new FileInputStream(file));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        mResult.mName = name + "丨在线：" + count;
        // saveProfile();
        ProfileManager vpl = ProfileManager.getInstance(this);

        if (!TextUtils.isEmpty(mEmbeddedPwFile))
            ConfigParser.useEmbbedUserAuth(mResult, mEmbeddedPwFile);
        vpl.addProfile(mResult);
        vpl.saveProfile(this, mResult);
        vpl.saveProfileList(this);
        return null;
    }

    private void saveProfile() {
        Log.e("name_config665", mResult.mName);
        //   Intent result = new Intent();
        ProfileManager vpl = ProfileManager.getInstance(this);

        if (!TextUtils.isEmpty(mEmbeddedPwFile))
            ConfigParser.useEmbbedUserAuth(mResult, mEmbeddedPwFile);

        finish();
        vpl.addProfile(mResult);
        vpl.saveProfile(this, mResult);
        vpl.saveProfileList(this);

        //result.putExtra(VpnProfile.EXTRA_PROFILEUUID, mResult.getUUID().toString());
        //setResult(Activity.RESULT_OK, result);


    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private void log(final String logmessage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tv = new TextView(ConfigConverter.this);
                mLogEntries.add(logmessage);
                tv.setText(logmessage);

                addViewToLog(tv);
            }
        });
    }

    private void addViewToLog(View view) {
        mLogLayout.addView(view, mLogLayout.getChildCount() - 1);
    }

    private void doImport(InputStream is) {
        ConfigParser cp = new ConfigParser();
        try {
            InputStreamReader isr = new InputStreamReader(is);

            cp.parseConfig(isr);
            mResult = cp.convertProfile();
            embedFiles(cp);
            return;

        } catch (IOException | ConfigParseError e) {

            log(e.getLocalizedMessage());
        }
        mResult = null;

    }


}
