/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListFragment;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.RequiresApi;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import javax.security.auth.login.LoginException;

import de.blinkt.openvpn.LaunchVPN;
import de.blinkt.openvpn.R;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.activities.ConfigConverter;
import de.blinkt.openvpn.activities.DisconnectVPN;
import de.blinkt.openvpn.activities.FileSelect;
import de.blinkt.openvpn.activities.Login;
import de.blinkt.openvpn.activities.MainActivity;
import de.blinkt.openvpn.activities.VPNPreferences;
import de.blinkt.openvpn.core.ConnectionStatus;
import de.blinkt.openvpn.core.OpenVPNManagement;
import de.blinkt.openvpn.core.Preferences;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VpnStatus;

import static de.blinkt.openvpn.core.OpenVPNService.DISCONNECT_VPN;
import static de.blinkt.openvpn.core.OpenVPNService.humanReadableByteCount;


public class VPNProfileList extends ListFragment implements OnClickListener, VpnStatus.StateListener, VpnStatus.ByteCountListener {

    public final static int RESULT_VPN_DELETED = Activity.RESULT_FIRST_USER;
    public final static int RESULT_VPN_DUPLICATE = Activity.RESULT_FIRST_USER + 1;
    private static final int MENU_ADD_PROFILE = Menu.FIRST;
    private static final int START_VPN_CONFIG = 92;
    private static final int SELECT_PROFILE = 43;
    private static final int IMPORT_PROFILE = 231;
    private static final int FILE_PICKER_RESULT_KITKAT = 392;
    private static final int MENU_IMPORT_PROFILE = Menu.FIRST + 1;
    private static final int MENU_CHANGE_SORTING = Menu.FIRST + 2;
    private static final String PREF_SORT_BY_LRU = "sortProfilesByLRU";
    public static String noticeText;
    private String mLastStatusMessage;
    public static TextView notice;
    public static TextView flowMsg;
    public static TextView outtime;
    // public static TextView speedMsg;

    @Override
    public void updateState(String state, String logmessage, final int localizedResId, ConnectionStatus level) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLastStatusMessage = VpnStatus.getLastCleanLogMessage(getActivity());
                mArrayadapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void setConnectedVPN(String uuid) {
    }

    @Override
    public void onStart() {
        super.onStart();
        VpnStatus.addByteCountListener(this);
    }

    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        //%2$s/s %1$s - ↑%4$s/s %3$s
        Resources res = getActivity().getResources();
        final String nowDown = String.format("%1$s", humanReadableByteCount(diffIn / OpenVPNManagement.mBytecountInterval, true, res));
        final String nowUp = String.format("%1$s", humanReadableByteCount(diffOut / OpenVPNManagement.mBytecountInterval, true, res));

        final String allDown = String.format("%1$s", humanReadableByteCount(in, false, getResources()));
        final String allUp = String.format("%1$s", humanReadableByteCount(out, false, getResources()));

        if (flowMsg != null) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //↓%2$s %1$s - ↑%4$s %3$s
                        flowMsg.setText(String.format("总流量: 已下载:%1$s 已上传:%2$s", allDown, allUp));
                        // speedMsg.setText(String.format("实时速率: 下载:%1$s 上传:%2$s", nowDown, nowUp));
                    }
                });
            }
        }
    }

    private class VPNArrayAdapter extends ArrayAdapter<VpnProfile> {

        public VPNArrayAdapter(Context context, int resource,
                               int textViewResourceId) {
            super(context, resource, textViewResourceId);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View v = super.getView(position, convertView, parent);

            final VpnProfile profile = (VpnProfile) getListAdapter().getItem(position);

            View titleview = v.findViewById(R.id.vpn_list_item_left);
            titleview.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    startOrStopVPN(profile);
                }
            });

        /*    View settingsview = v.findViewById(R.id.quickedit_settings);
            settingsview.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    editVPN(profile);

                }
            });*/


            TextView subtitle = (TextView) v.findViewById(R.id.vpn_item_subtitle);
            if (profile.getUUIDString().equals(VpnStatus.getLastConnectedVPNProfile())) {
                subtitle.setText(mLastStatusMessage);
                subtitle.setVisibility(View.VISIBLE);
            } else {
                subtitle.setText("");
                subtitle.setVisibility(View.GONE);
            }


            return v;
        }
    }

    private void startOrStopVPN(VpnProfile profile) {
        if (VpnStatus.isVPNActive() && profile.getUUIDString().equals(VpnStatus.getLastConnectedVPNProfile())) {
            Intent disconnectVPN = new Intent(getActivity(), DisconnectVPN.class);
            startActivity(disconnectVPN);
        } else {
            startVPN(profile);
        }
    }


    private ArrayAdapter<VpnProfile> mArrayadapter;

    protected VpnProfile mEditProfile = null;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

    }


    // Shortcut version is increased to refresh all shortcuts
    final static int SHORTCUT_VERSION = 1;

    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    void updateDynamicShortcuts() {
        PersistableBundle versionExtras = new PersistableBundle();
        versionExtras.putInt("version", SHORTCUT_VERSION);

        ShortcutManager shortcutManager = getContext().getSystemService(ShortcutManager.class);
        if (shortcutManager.isRateLimitingActive())
            return;

        List<ShortcutInfo> shortcuts = shortcutManager.getDynamicShortcuts();
        int maxvpn = shortcutManager.getMaxShortcutCountPerActivity() - 1;


        ShortcutInfo disconnectShortcut = new ShortcutInfo.Builder(getContext(), "disconnectVPN")
                .setShortLabel("Disconnect")
                .setLongLabel("Disconnect VPN")
                .setIntent(new Intent(getContext(), DisconnectVPN.class).setAction(DISCONNECT_VPN))
                .setIcon(Icon.createWithResource(getContext(), R.drawable.ic_shortcut_cancel))
                .setExtras(versionExtras)
                .build();

        LinkedList<ShortcutInfo> newShortcuts = new LinkedList<>();
        LinkedList<ShortcutInfo> updateShortcuts = new LinkedList<>();

        LinkedList<String> removeShortcuts = new LinkedList<>();
        LinkedList<String> disableShortcuts = new LinkedList<>();

        boolean addDisconnect = true;


        TreeSet<VpnProfile> sortedProfilesLRU = new TreeSet<VpnProfile>(new VpnProfileLRUComparator());
        ProfileManager profileManager = ProfileManager.getInstance(getContext());
        sortedProfilesLRU.addAll(profileManager.getProfiles());

        LinkedList<VpnProfile> LRUProfiles = new LinkedList<>();
        maxvpn = Math.min(maxvpn, sortedProfilesLRU.size());

        for (int i = 0; i < maxvpn; i++) {
            LRUProfiles.add(sortedProfilesLRU.pollFirst());
        }

        for (ShortcutInfo shortcut : shortcuts) {
            if (shortcut.getId().equals("disconnectVPN")) {
                addDisconnect = false;
                if (shortcut.getExtras() == null
                        || shortcut.getExtras().getInt("version") != SHORTCUT_VERSION)
                    updateShortcuts.add(disconnectShortcut);

            } else {
                VpnProfile p = ProfileManager.get(getContext(), shortcut.getId());
                if (p == null || p.profileDeleted) {
                    if (shortcut.isEnabled()) {
                        disableShortcuts.add(shortcut.getId());
                        removeShortcuts.add(shortcut.getId());
                    }
                    if (!shortcut.isPinned())
                        removeShortcuts.add(shortcut.getId());
                } else {

                    if (LRUProfiles.contains(p))
                        LRUProfiles.remove(p);
                    else
                        removeShortcuts.add(p.getUUIDString());

                    if (!p.getName().equals(shortcut.getShortLabel())
                            || shortcut.getExtras() == null
                            || shortcut.getExtras().getInt("version") != SHORTCUT_VERSION)
                        updateShortcuts.add(createShortcut(p));


                }

            }

        }
        if (addDisconnect)
            newShortcuts.add(disconnectShortcut);
        for (VpnProfile p : LRUProfiles)
            newShortcuts.add(createShortcut(p));

        if (updateShortcuts.size() > 0)
            shortcutManager.updateShortcuts(updateShortcuts);
        if (removeShortcuts.size() > 0)
            shortcutManager.removeDynamicShortcuts(removeShortcuts);
        if (newShortcuts.size() > 0)
            shortcutManager.addDynamicShortcuts(newShortcuts);
        if (disableShortcuts.size() > 0)
            shortcutManager.disableShortcuts(disableShortcuts, "VpnProfile does not exist anymore.");
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    ShortcutInfo createShortcut(VpnProfile profile) {
        Intent shortcutIntent = new Intent(Intent.ACTION_MAIN);
        shortcutIntent.setClass(getActivity(), LaunchVPN.class);
        shortcutIntent.putExtra(LaunchVPN.EXTRA_KEY, profile.getUUID().toString());
        shortcutIntent.setAction(Intent.ACTION_MAIN);
        shortcutIntent.putExtra("EXTRA_HIDELOG", true);

        PersistableBundle versionExtras = new PersistableBundle();
        versionExtras.putInt("version", SHORTCUT_VERSION);

        return new ShortcutInfo.Builder(getContext(), profile.getUUIDString())
                .setShortLabel(profile.getName())
                .setLongLabel(getString(R.string.qs_connect, profile.getName()))
                .setIcon(Icon.createWithResource(getContext(), R.drawable.ic_shortcut_vpn_key))
                .setIntent(shortcutIntent)
                .setExtras(versionExtras)
                .build();
    }

    class MiniImageGetter implements ImageGetter {


        @Override
        public Drawable getDrawable(String source) {
            Drawable d = null;
            if ("ic_menu_add".equals(source))
                d = getActivity().getResources().getDrawable(R.drawable.ic_menu_add_grey);
            else if ("ic_menu_archive".equals(source))
                d = getActivity().getResources().getDrawable(R.drawable.ic_menu_import_grey);


            if (d != null) {
                d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
                return d;
            } else {
                return null;
            }
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        Log.e("vpn301", "dada");
        setListAdapter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            Log.e("vpn303", "dada");
            updateDynamicShortcuts();
        }
        VpnStatus.addStateListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        VpnStatus.removeStateListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.vpn_profile_list, container, false);

//        TextView newvpntext = (TextView) v.findViewById(R.id.add_new_vpn_hint);
//        TextView importvpntext = (TextView) v.findViewById(R.id.import_vpn_hint);

//        newvpntext.setText(Html.fromHtml(getString(R.string.add_new_vpn_hint), new MiniImageGetter(), null));
//        importvpntext.setText(Html.fromHtml(getString(R.string.vpn_import_hint), new MiniImageGetter(), null));
        ImageButton fab_add = (ImageButton) v.findViewById(R.id.fab_add);
        ImageButton fab_import = (ImageButton) v.findViewById(R.id.fab_import);
        if (fab_add != null)
            fab_add.setOnClickListener(this);

        if (fab_import != null)
            fab_import.setOnClickListener(this);

        return v;

    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setListAdapter();
    }

    static class VpnProfileNameComparator implements Comparator<VpnProfile> {

        @Override
        public int compare(VpnProfile lhs, VpnProfile rhs) {
            if (lhs == rhs)
                // Catches also both null
                return 0;

            if (lhs == null)
                return -1;
            if (rhs == null)
                return 1;

            if (lhs.mName == null)
                return -1;
            if (rhs.mName == null)
                return 1;

            return lhs.mName.compareTo(rhs.mName);
        }

    }

    static class VpnProfileLRUComparator implements Comparator<VpnProfile> {

        VpnProfileNameComparator nameComparator = new VpnProfileNameComparator();

        @Override
        public int compare(VpnProfile lhs, VpnProfile rhs) {
            if (lhs == rhs)
                // Catches also both null
                return 0;

            if (lhs == null)
                return -1;
            if (rhs == null)
                return 1;

            // Copied from Long.compare
            if (lhs.mLastUsed > rhs.mLastUsed)
                return -1;
            if (lhs.mLastUsed < rhs.mLastUsed)
                return 1;
            else
                return nameComparator.compare(lhs, rhs);
        }
    }


    private void setListAdapter() {
        if (mArrayadapter == null) {
            mArrayadapter = new VPNArrayAdapter(getActivity(), R.layout.vpn_list_item, R.id.vpn_item_title);
            notice = getActivity().findViewById(R.id.notice);
            flowMsg = getActivity().findViewById(R.id.flow_msg);
            outtime = getActivity().findViewById(R.id.outtime);
            // speedMsg = getActivity().findViewById(R.id.speed_msg);
            notice.setText(noticeText);
            outtime.setText("到期时间：" +MainActivity.ACCOUNT+"|"+ MainActivity.outtime_date);
            ((Button) getActivity().findViewById(R.id.login_out)).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getActivity(), Login.class);
                    startActivityForResult(intent, 1);
                }
            });
        }
        populateVpnList();
    }

    private void populateVpnList() {
        boolean sortByLRU = Preferences.getDefaultSharedPreferences(getActivity()).getBoolean(PREF_SORT_BY_LRU, false);
        Collection<VpnProfile> allvpn = getPM().getProfiles();
        TreeSet<VpnProfile> sortedset;
        if (sortByLRU)
            sortedset = new TreeSet<>(new VpnProfileLRUComparator());
        else
            sortedset = new TreeSet<>(new VpnProfileNameComparator());


        sortedset.addAll(allvpn);
        mArrayadapter.clear();
        mArrayadapter.addAll(sortedset);
        setListAdapter(mArrayadapter);
        mArrayadapter.notifyDataSetChanged();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == MENU_ADD_PROFILE) {
            Log.e("?", "443");
            /*onAddOrDuplicateProfile(null);*/
            return true;
        } else if (itemId == MENU_IMPORT_PROFILE) {
            Log.e("vpn", "448");
            startConfigImport();//启动
            return true;
        } else if (itemId == MENU_CHANGE_SORTING) {
            return changeSorting();
        } else {
            Log.e("?", "452 ");
            return super.onOptionsItemSelected(item);
        }
    }

    private boolean changeSorting() {
        SharedPreferences prefs = Preferences.getDefaultSharedPreferences(getActivity());
        boolean oldValue = prefs.getBoolean(PREF_SORT_BY_LRU, false);
        SharedPreferences.Editor prefsedit = prefs.edit();
        if (oldValue) {
            Toast.makeText(getActivity(), R.string.sorted_az, Toast.LENGTH_SHORT).show();
            prefsedit.putBoolean(PREF_SORT_BY_LRU, false);
        } else {
            prefsedit.putBoolean(PREF_SORT_BY_LRU, true);
            Toast.makeText(getActivity(), R.string.sorted_lru, Toast.LENGTH_SHORT).show();
        }
        prefsedit.apply();
        populateVpnList();
        return true;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fab_import:
                startImportConfigFilePicker();
                Log.e("TATAT", "TATA");
                break;
            case R.id.fab_add:
                /*onAddOrDuplicateProfile(null);*/
                Log.e("TATAT", "TATA");
                break;
        }
    }


    private boolean startImportConfigFilePicker() {
        Log.e("vpn488", "488");
        boolean startOldFileDialog = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            startOldFileDialog = !startFilePicker();
            Log.e("vpn492", String.valueOf(startOldFileDialog));
        }
        if (startOldFileDialog) {
            Log.e("vpn495", "495");
            startImportConfig();
        }
        return true;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private boolean startFilePicker() {
        //use
        Log.e("vpn503", "503");
        Intent i = Utils.getFilePickerIntent(getActivity(), Utils.FileType.OVPN_CONFIG);
        if (i != null) {
            Log.e("vpn506", "506");
            startActivityForResult(i, FILE_PICKER_RESULT_KITKAT);//打开文件选择
            return true;
        } else
            return false;
    }

    private void startImportConfig() {
        //no user
        Log.e("510", "fileselect.class");
        Intent intent = new Intent(getActivity(), FileSelect.class);
        intent.putExtra(FileSelect.NO_INLINE_SELECTION, true);
        intent.putExtra(FileSelect.WINDOW_TITLE, R.string.import_configuration_file);
        startActivityForResult(intent, SELECT_PROFILE);
    }

/*
    private void onAddOrDuplicateProfile(final VpnProfile mCopyProfile) {
        Context context = getActivity();
        if (context != null) {
            final EditText entry = new EditText(context);
            entry.setSingleLine();

            AlertDialog.Builder dialog = new AlertDialog.Builder(context);
            if (mCopyProfile == null)
                dialog.setTitle(R.string.menu_add_profile);
            else {
                dialog.setTitle(context.getString(R.string.duplicate_profile_title, mCopyProfile.mName));
                entry.setText(getString(R.string.copy_of_profile, mCopyProfile.mName));
            }

            dialog.setMessage(R.string.add_profile_name_prompt);
            dialog.setView(entry);

            dialog.setNeutralButton(R.string.menu_import_short,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startImportConfigFilePicker();
                        }
                    });
            dialog.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String name = entry.getText().toString();
                            if (getPM().getProfileByName(name) == null) {
                                VpnProfile profile;
                                if (mCopyProfile != null)
                                    profile = mCopyProfile.copy(name);
                                else
                                    profile = new VpnProfile(name);

                                addProfile(profile);
                                */
/*editVPN(profile);*//*

                            } else {
                                Toast.makeText(getActivity(), R.string.duplicate_profile_name, Toast.LENGTH_LONG).show();
                            }
                        }


                    });
            dialog.setNegativeButton(android.R.string.cancel, null);
            dialog.create().show();
            Log.e("??", "show");
        }

    }
*/

    private void addProfile(VpnProfile profile) {
        getPM().addProfile(profile);
        getPM().saveProfileList(getActivity());
        getPM().saveProfile(getActivity(), profile);
        mArrayadapter.add(profile);
    }

    private ProfileManager getPM() {
        return ProfileManager.getInstance(getActivity());
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1) {
            outtime.setText("到期时间：" +MainActivity.ACCOUNT+"|"+ MainActivity.outtime_date);
        }


        // test=data.getData();


    /*    if (resultCode == RESULT_VPN_DELETED) {
            Log.e("vpn","600");
            if (mArrayadapter != null && mEditProfile != null)
                Log.e("vpn","602");
                mArrayadapter.remove(mEditProfile);
        } else if (resultCode == RESULT_VPN_DUPLICATE && data != null) {
            String profileUUID = data.getStringExtra(VpnProfile.EXTRA_PROFILEUUID);
            Log.e("594",profileUUID);
            VpnProfile profile = ProfileManager.get(getActivity(), profileUUID);
            if (profile != null)
                onAddOrDuplicateProfile(profile);
        }
*/
        if (resultCode != Activity.RESULT_OK)
            return;


  /*      if (requestCode == START_VPN_CONFIG) {
            String configuredVPN = data.getStringExtra(VpnProfile.EXTRA_PROFILEUUID);
            Log.e("608",configuredVPN);

            VpnProfile profile = ProfileManager.get(getActivity(), configuredVPN);
            getPM().saveProfile(getActivity(), profile);
            // Name could be modified, reset List adapter
            setListAdapter();

        } else if (requestCode == SELECT_PROFILE) {
            String fileData = data.getStringExtra(FileSelect.RESULT_DATA);
            Log.e("617",fileData);
            Uri uri = new Uri.Builder().path(fileData).scheme("file").build();

            startConfigImport(uri);
        } else if (requestCode == IMPORT_PROFILE) {
            String profileUUID = data.getStringExtra(VpnProfile.EXTRA_PROFILEUUID);
            Log.e("623",profileUUID);
            mArrayadapter.add(ProfileManager.get(getActivity(), profileUUID));
        } else */

        if (requestCode == FILE_PICKER_RESULT_KITKAT) {
            Log.e("641", "641");
            if (data != null) {
                // Uri uri = data.getData();

               /* test=data.getData();
                test();*/
                // Uri uri=Uri.parse("com.android.externalstorage.documents/document/primary%3ADownload%2F%E4%B8%89%E5%8F%B7%E7%BA%BF%E8%B7%AF(%E7%94%B5%E4%BF%A1).ovpn");

                // Uri uri=Uri.fromFile(new File("com.android.externalstorage.documents/document/primary%3ADownload%2F%E4%B8%89%E5%8F%B7%E7%BA%BF%E8%B7%AF(%E7%94%B5%E4%BF%A1).ovpn"));

                // Uri insertedUserUri = ContentUris.withAppendedId(uri, 1);
                // startConfigImport(url);//文件选择器选中地址后

            }
        }

    }

    private void startConfigImport() {

        Intent startImport = new Intent(getActivity(), ConfigConverter.class);
        startImport.setAction(ConfigConverter.IMPORT_PROFILE);
        startActivityForResult(startImport, IMPORT_PROFILE);


        // new ConfigConverter();

    }


/*    private void editVPN(VpnProfile profile) {
        Log.e("VPN", "651");
        mEditProfile = profile;
        Intent vprefintent = new Intent(getActivity(), VPNPreferences.class)
                .putExtra(getActivity().getPackageName() + ".profileUUID", profile.getUUID().toString());

        startActivityForResult(vprefintent, START_VPN_CONFIG);
    }*/

    private void startVPN(VpnProfile profile) {

        getPM().saveProfile(getActivity(), profile);

        Intent intent = new Intent(getActivity(), LaunchVPN.class);
        intent.putExtra(LaunchVPN.EXTRA_KEY, profile.getUUID().toString());
        intent.setAction(Intent.ACTION_MAIN);
        startActivity(intent);
    }
}
