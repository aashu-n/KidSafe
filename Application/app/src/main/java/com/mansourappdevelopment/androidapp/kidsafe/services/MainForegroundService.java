package com.mansourappdevelopment.androidapp.kidsafe.services;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.mansourappdevelopment.androidapp.kidsafe.R;
import com.mansourappdevelopment.androidapp.kidsafe.activities.BlockedAppActivity;
import com.mansourappdevelopment.androidapp.kidsafe.activities.ChildSignedInActivity;
import com.mansourappdevelopment.androidapp.kidsafe.broadcasts.AppInstalledReceiver;
import com.mansourappdevelopment.androidapp.kidsafe.broadcasts.AppRemovedReceiver;
import com.mansourappdevelopment.androidapp.kidsafe.broadcasts.PhoneStateReceiver;
import com.mansourappdevelopment.androidapp.kidsafe.broadcasts.SmsReceiver;
import com.mansourappdevelopment.androidapp.kidsafe.utils.App;
import com.mansourappdevelopment.androidapp.kidsafe.utils.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import static com.mansourappdevelopment.androidapp.kidsafe.utils.NotificationChannelCreator.CHANNEL_ID;

public class MainForegroundService extends Service {
    public static final int NOTIFICATION_ID = 27;
    public static final String TAG = "MainServiceTAG";
    public static final String BLOCKED_APP_NAME_EXTRA = "com.mansourappdevelopment.androidapp.kidsafe.services.BLOCKED_APP_NAME_EXTRA";
    public static final int LOCATION_UPDATE_INTERVAL = 1;    //every 5 seconds
    public static final int LOCATION_UPDATE_DISPLACEMENT = 5;  //every 10 meters
    private ExecutorService executorService;
    private ArrayList<App> apps;
    private PhoneStateReceiver phoneStateReceiver;
    private SmsReceiver smsReceiver;
    private AppInstalledReceiver appInstalledReceiver;
    private AppRemovedReceiver appRemovedReceiver;
    private String uid;
    private String childEmail;
    private FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
    private DatabaseReference databaseReference = firebaseDatabase.getReference("users");


    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newSingleThreadExecutor();
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        LockerThread thread = new LockerThread();
        executorService.submit(thread);
        new Thread(new Runnable() {
            @Override
            public void run() {
                getInstalledApplications();
            }
        }).start();
        Log.i(TAG, "onCreate: executed");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //String childEmail = intent.getStringExtra(CHILD_EMAIL);
        //String notificationContent = "Monitoring device";

        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        childEmail = user.getEmail();
        uid = user.getUid();

        Intent notificationIntent = new Intent(this, ChildSignedInActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                //.setContentTitle(notificationContent)
                .setSmallIcon(R.drawable.ic_location)   //TODO:: add the app icon
                .setContentIntent(pendingIntent)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        getUserLocation();

        /*FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
        databaseReference = firebaseDatabase.getReference("users");*/

        Query appsQuery = databaseReference.child("childs").child(uid).child("apps");
        appsQuery.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    getApps();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        Query webFilterQuery = databaseReference.child("childs").child(uid).child("webFilter");
        webFilterQuery.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    boolean checked = (boolean) dataSnapshot.getValue();
                    if (checked) {
                        Toast.makeText(MainForegroundService.this, "Web Filter Enabled", Toast.LENGTH_SHORT).show();
                        /*String primaryDNS = "185.228.168.168";
                        String secondaryDNS = "185.228.169.168";
                        changeDNS(primaryDNS, secondaryDNS);
                        String newDNS1 = Settings.System.getString(getContentResolver(), Settings.System.WIFI_STATIC_DNS1);
                        String newDNS2 = Settings.System.getString(getContentResolver(), Settings.System.WIFI_STATIC_DNS2);
                        Log.i(TAG, "onDataChange: new DNS1: " + newDNS1);
                        Log.i(TAG, "onDataChange: new DNS2: " + newDNS2);*/
                    } else {
                        Toast.makeText(MainForegroundService.this, "Web Filter Disabled", Toast.LENGTH_SHORT).show();
                        /*String primaryDNS = "0.0.0.0";
                        String secondaryDNS = "0.0.0.0";
                        changeDNS(primaryDNS, secondaryDNS);
                        String newDNS1 = Settings.System.getString(getContentResolver(), Settings.System.WIFI_STATIC_DNS1);
                        String newDNS2 = Settings.System.getString(getContentResolver(), Settings.System.WIFI_STATIC_DNS2);
                        Log.i(TAG, "onDataChange: new DNS1: " + newDNS1);
                        Log.i(TAG, "onDataChange: new DNS2: " + newDNS2);*/
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        phoneStateReceiver = new PhoneStateReceiver(user);
        IntentFilter callIntentFilter = new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        registerReceiver(phoneStateReceiver, callIntentFilter);

        smsReceiver = new SmsReceiver(user);
        IntentFilter smsIntentFilter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        registerReceiver(smsReceiver, smsIntentFilter);

        appInstalledReceiver = new AppInstalledReceiver(user);
        IntentFilter appInstalledIntentFilter = new IntentFilter();
        appInstalledIntentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        appInstalledIntentFilter.addAction(Intent.ACTION_PACKAGE_INSTALL);
        appInstalledIntentFilter.addDataScheme("package");
        registerReceiver(appInstalledReceiver, appInstalledIntentFilter);

        appRemovedReceiver = new AppRemovedReceiver(user);
        IntentFilter appRemovedIntentFilter = new IntentFilter();
        appRemovedIntentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        appRemovedIntentFilter.addDataScheme("package");
        registerReceiver(appRemovedReceiver, appRemovedIntentFilter);


        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
        if (phoneStateReceiver != null) {
            unregisterReceiver(phoneStateReceiver);
        }
        if (smsReceiver != null) {
            unregisterReceiver(smsReceiver);
        }
        if (appInstalledReceiver != null) {
            unregisterReceiver(appInstalledReceiver);
        }
        if (appRemovedReceiver != null) {
            unregisterReceiver(appRemovedReceiver);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void getApps() {
        Query query = databaseReference.child("childs").orderByChild("email").equalTo(childEmail);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    //Log.i(TAG, "onDataChange: dataSnapshot value: "+dataSnapshot.getValue());
                    //Log.i(TAG, "onDataChange: dataSnapshot as a string: "+dataSnapshot.toString());
                    //Log.i(TAG, "onDataChange: dataSnapshot children: " + dataSnapshot.getChildren());
                    //Log.i(TAG, "onDataChange: dataSnapshot key: " + dataSnapshot.getKey());

                    DataSnapshot nodeShot = dataSnapshot.getChildren().iterator().next();
                    User child = nodeShot.getValue(User.class);     //TODO:: Failed to convert value of type java.lang.Double to String
                    apps = child.getApps();

                    Log.i(TAG, "onDataChange: child name: " + child.getName());

                    //updateAppStats(apps);

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    public String getTopAppPackageName() {
        String appPackageName = "";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                appPackageName = getLollipopForegroundAppPackageName();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return appPackageName;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    private String getLollipopForegroundAppPackageName() {
        //Log.i(TAG, "getLollipopForegroundAppPackageName: executed");
        try {
            UsageStatsManager usageStatsManager = (UsageStatsManager) this.getSystemService(USAGE_STATS_SERVICE);
            long milliSecs = 60 * 1000;
            Date date = new Date();
            List<UsageStats> foregroundApps = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, date.getTime() - milliSecs, date.getTime());
            if (foregroundApps.size() == 0) {
                Log.i(TAG, "getLollipopForegroundAppPackageName: queryUsageSize: empty");
            }


            long recentTime = 0;
            String recentPkg = "";
            for (UsageStats stats : foregroundApps) {
                /*if (i == 0 && !"com.mansourappdevelopment.androidapp.kidsafe".equals(stats.getPackageName())) {
                    Log.i(TAG, "PackageName: " + stats.getPackageName() + " " + stats.getLastTimeStamp());
                }*/
                if (stats.getLastTimeStamp() > recentTime) {
                    recentTime = stats.getLastTimeStamp();
                    recentPkg = stats.getPackageName();
                }

            }

            //Log.i(TAG, "getLollipopForegroundAppPackageName: appPackageName: " + recentPkg);
            return recentPkg;
        } catch (Exception e) {
            e.printStackTrace();
        }


        return "";
    }

    class LockerThread implements Runnable {

        Intent intnet = null;

        public LockerThread() {
            intnet = new Intent(MainForegroundService.this, BlockedAppActivity.class);
            intnet.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        @Override
        public void run() {
            while (true) {
                Log.i(TAG, "run: thread running");

                if (apps != null) {

                    String foregroundAppPackageName = getTopAppPackageName();
                    Log.i(TAG, "run: foreground app: " + foregroundAppPackageName);

                    //TODO:: need to handle com.google.android.gsf &  com.sec.android.provider.badge
                    for (final App app : apps) {
                        //Log.i(TAG, "run: app name: " + app.getAppName() + " blocked: " + app.isBlocked() + "\n");
                        if (foregroundAppPackageName.equals(app.getPackageName()) && app.isBlocked()) {
                            //Log.i(TAG, "run: " + app.getPackageName() + " is running");
                            intnet.putExtra(BLOCKED_APP_NAME_EXTRA, app.getAppName());
                            startActivity(intnet);
                        }

                    }
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private void getUserLocation() {
        Log.i(TAG, "getUserLocation: executed");
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        LocationListener locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (location != null) {
                    Log.i(TAG, "onLocationChanged: latitude: " + location.getLatitude());
                    Log.i(TAG, "onLocationChanged: longitude: " + location.getLongitude());
                    addUserLocationToDatabase(location, uid);
                } else {
                    Log.i(TAG, "onLocationChanged: location is null");
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };

        //these two statements will be only executed when the permission is granted.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_UPDATE_INTERVAL, LOCATION_UPDATE_DISPLACEMENT, locationListener);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_UPDATE_INTERVAL, LOCATION_UPDATE_DISPLACEMENT, locationListener);
            return;
        }

    }

    private void addUserLocationToDatabase(Location location, String uid) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        HashMap<String, Object> update = new HashMap<>();
        update.put("latitude", latitude);
        update.put("longitude", longitude);
        databaseReference.child("childs").child(uid).child("location").updateChildren(update);
    }

    /*private void changeDNS(String primaryDNS, String secondaryDNS) {
        Settings.System.putString(getContentResolver(), Settings.System.WIFI_STATIC_DNS1, primaryDNS);  //TODO:: DEPRECATED
        Settings.System.putString(getContentResolver(), Settings.System.WIFI_STATIC_DNS2, secondaryDNS);
    }*/

    private void getInstalledApplications(/*ArrayList<App> onlineAppsList*/) {
        PackageManager packageManager = getPackageManager();
        List<ApplicationInfo> applicationInfoList = packageManager.getInstalledApplications(0);
        Collections.sort(applicationInfoList, new ApplicationInfo.DisplayNameComparator(packageManager));
        Iterator<ApplicationInfo> iterator = applicationInfoList.iterator();
        while (iterator.hasNext()) {
            ApplicationInfo applicationInfo = iterator.next();
            if (applicationInfo.packageName.contains("com.google") || applicationInfo.packageName.matches("com.android.chrome"))
                continue;
            if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                iterator.remove();
            }
        }
        prepareData(applicationInfoList, packageManager/*, onlineAppsList*/);
    }

    private void prepareData(List<ApplicationInfo> applicationInfoList, PackageManager packageManager/*, ArrayList<App> onlineAppsList*/) {
        ArrayList<App> appsList = new ArrayList<>();
        for (ApplicationInfo applicationInfo : applicationInfoList) {
            if (applicationInfo.packageName != null) {
                appsList.add(new App((String) applicationInfo.loadLabel(packageManager), (String) applicationInfo.packageName, false));
            }
        }
        /*if (onlineAppsList.isEmpty()) {
            Log.i(TAG, "prepareData: online appsList empty");
            for (ApplicationInfo applicationInfo : applicationInfoList) {
                if (applicationInfo.packageName != null) {
                    appsList.add(new App((String) applicationInfo.loadLabel(packageManager), (String) applicationInfo.packageName, false));
                }
            }
            //if not, check the app's blocked attribute and update it.
        } else {
            for (ApplicationInfo applicationInfo : applicationInfoList) {
                for (App app : onlineAppsList) {
                    if (app.getPackageName().equals((String) applicationInfo.packageName)) {
                        appsList.add(new App((String) applicationInfo.loadLabel(packageManager), (String) applicationInfo.packageName, app.isBlocked()));
                    }
                }

            }

        }*/

        writeDataToDB(appsList);

    }

    private void writeDataToDB(ArrayList<App> appsList) {
        databaseReference.child("childs").child(uid).child("apps").setValue(appsList);
        Log.i(TAG, "writeDataToDB: done");
    }
}