/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.activities;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.security.KeyChain;
import android.support.v4n.view.ViewPager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.LinearLayout;
import java.util.Random;
import de.blinkt.openvpn.R;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.fragments.VPNProfileList;
import de.blinkt.openvpn.views.ScreenSlidePagerAdapter;
import de.blinkt.openvpn.views.SlidingTabLayout;
import de.blinkt.openvpn.views.TabBarView;


public class MainActivity extends BaseActivity {

    public static Context context;
    private ViewPager mPager;
    private ScreenSlidePagerAdapter mPagerAdapter;
    private SlidingTabLayout mSlidingTabLayout;
    public static final String VPNPROFILE = "vpnProfile";
    public static String ACCOUNT, PASSWORD;
    public static String uid_token;
    public static String outtime_date;
    private static final String FEATURE_TELEVISION = "android.hardware.type.television";
    private static final String FEATURE_LEANBACK = "android.software.leanback";

    protected void onCreate(android.os.Bundle savedInstanceState) {
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        context = getApplicationContext();
        //  mResult = (VpnProfile) savedInstanceState.getSerializable(VPNPROFILE);
        setContentView(R.layout.main_activity);
        LinearLayout main = findViewById(R.id.main);
        mPager = (ViewPager) findViewById(R.id.pager);
        int[] resource = {R.drawable.background1, R.drawable.background2,R.drawable.background3,R.drawable.background4,R.drawable.background5};
        Bitmap bitmap;
        BitmapDrawable bitmapDrawable;
        Random random = new Random();
        bitmap = BitmapFactory.decodeResource(getResources(), resource[random.nextInt(resource.length)]);
        bitmapDrawable = new BitmapDrawable(getResources(), bitmap);
        bitmapDrawable.setDither(true);
        bitmapDrawable.setGravity(Gravity.CENTER);
        main.setBackground(bitmapDrawable);

        mPagerAdapter = new ScreenSlidePagerAdapter(getFragmentManager(), this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            disableToolbarElevation();
        }
        mPagerAdapter.addTab(R.string.vpn_list_title, VPNProfileList.class);
//        mPagerAdapter.addTab(R.string.graph, GraphFragment.class);
        // mPagerAdapter.addTab(R.string.generalsettings, GeneralSettings.class);
        // mPagerAdapter.addTab(R.string.faq, FaqFragment.class);
//        if (SendDumpFragment.getLastestDump(this) != null) {
//            mPagerAdapter.addTab(R.string.crashdump, SendDumpFragment.class);
//        }
//        if (isDirectToTV())
//            mPagerAdapter.addTab(R.string.openvpn_log, LogFragment.class);
        // mPagerAdapter.addTab(R.string.about, AboutFragment.class);
        mPager.setAdapter(mPagerAdapter);
        //  mTabs.setViewPager(mPager);

        if (ACCOUNT == null) {
            Intent intent = new Intent(MainActivity.this, Login.class);
            startActivityForResult(intent, 1);
        }
    }


    private boolean isDirectToTV() {
        return (getPackageManager().hasSystemFeature(FEATURE_TELEVISION)
                || getPackageManager().hasSystemFeature(FEATURE_LEANBACK));
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void disableToolbarElevation() {
//        ActionBar toolbar = getActionBar();
//        toolbar.setDisplayShowHomeEnabled(false);
//        toolbar.setElevation(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getIntent() != null) {
            String page = getIntent().getStringExtra("PAGE");
            if ("graph".equals(page)) {
                mPager.setCurrentItem(0);
            }
            setIntent(null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.exit_login) {
            Intent intent = new Intent(MainActivity.this, Login.class);
            startActivityForResult(intent, 1);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == 1) {
            Intent startImport = new Intent(MainActivity.this, ConfigConverter.class);
            startImport.setAction(ConfigConverter.IMPORT_PROFILE);
            startActivityForResult(startImport, 231);
        }
    }
}