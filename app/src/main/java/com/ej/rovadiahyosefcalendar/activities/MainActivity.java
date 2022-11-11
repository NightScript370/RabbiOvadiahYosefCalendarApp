package com.ej.rovadiahyosefcalendar.activities;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static com.ej.rovadiahyosefcalendar.classes.JewishDateInfo.formatHebrewNumber;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.view.MenuCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ej.rovadiahyosefcalendar.R;
import com.ej.rovadiahyosefcalendar.classes.ChaiTables;
import com.ej.rovadiahyosefcalendar.classes.ChaiTablesScraper;
import com.ej.rovadiahyosefcalendar.classes.CustomDatePickerDialog;
import com.ej.rovadiahyosefcalendar.classes.JewishDateInfo;
import com.ej.rovadiahyosefcalendar.classes.LocationResolver;
import com.ej.rovadiahyosefcalendar.classes.ROZmanimCalendar;
import com.ej.rovadiahyosefcalendar.classes.ZmanAdapter;
import com.ej.rovadiahyosefcalendar.classes.ZmanListEntry;
import com.ej.rovadiahyosefcalendar.notifications.DailyNotifications;
import com.ej.rovadiahyosefcalendar.notifications.OmerNotifications;
import com.ej.rovadiahyosefcalendar.notifications.ZmanimNotifications;
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter;
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.kosherjava.zmanim.hebrewcalendar.YerushalmiYomiCalculator;
import com.kosherjava.zmanim.hebrewcalendar.YomiCalculator;
import com.kosherjava.zmanim.util.GeoLocation;
import com.kosherjava.zmanim.util.ZmanimFormatter;

import org.apache.commons.lang3.time.DateUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    public static boolean sShabbatMode;
    public static boolean sNetworkLocationServiceIsDisabled;
    public static boolean sGPSLocationServiceIsDisabled;
    public static boolean sUserIsOffline;
    public static boolean sFromSettings;
    private boolean mIsZmanimInHebrew;
    private boolean mIsZmanimEnglishTranslated;
    private boolean mBackHasBeenPressed = false;
    private boolean mUpdateTablesDialogShown;
    private boolean mInitialized = false;
    private int mCurrentPosition;//current position in the RecyclerView list of zmanim to return to when the user returns to the main screen
    private double mElevation = 0;
    public static double sLatitude;
    public static double sLongitude;
    private static final int TWENTY_FOUR_HOURS_IN_MILLI = 86_400_000;

    /**
     * This string is used to display the name of the current location in the app. We also use this string to save the elevation of a location to the
     * SharedPreferences, and we save the chai tables files under this name as well.
     */
    public static String sCurrentLocationName = "";
    public static String sCurrentTimeZoneID;//e.g. "America/New_York"

    //android views:
    private View mLayout;
    private Button mNextDate;
    private Button mPreviousDate;
    private Button mCalendarButton;
    private TextView mShabbatModeBanner;
    private RecyclerView mMainRecyclerView;

    //android views for weekly zmanim:
    private TextView mEnglishMonthYear;//E.G. "June 2021 - 2022"
    private TextView mLocationName;//E.G. "New York, NY"
    private TextView mHebrewMonthYear;//E.G. "Sivan 5781 - 5782"
    private final ListView[] mListViews = new ListView[7];//one for each day of the week
    private final TextView[] mSunday = new TextView[6];
    private final TextView[] mMonday = new TextView[6];
    private final TextView[] mTuesday = new TextView[6];
    private final TextView[] mWednesday = new TextView[6];
    private final TextView[] mThursday = new TextView[6];
    private final TextView[] mFriday = new TextView[6];
    private final TextView[] mSaturday = new TextView[6];
    private TextView mWeeklyParsha;
    private TextView mWeeklyDafs;

    //This array holds the zmanim that we want to display in the announcements section of the weekly view:
    private ArrayList<String> mZmanimForAnnouncements;

    //custom classes/kosherjava classes:
    private LocationResolver mLocationResolver;
    private ROZmanimCalendar mROZmanimCalendar;
    private JewishDateInfo mJewishDateInfo;
    private final ZmanimFormatter mZmanimFormatter = new ZmanimFormatter(TimeZone.getDefault());

    //android classes:
    private Handler mHandler = null;
    private Runnable mZmanimUpdater;
    private AlertDialog mAlertDialog;
    private GestureDetector mGestureDetector;
    private SharedPreferences mSharedPreferences;
    private SharedPreferences mSettingsPreferences;
    public static final String SHARED_PREF = "MyPrefsFile";
    private ActivityResultLauncher<Intent> mSetupLauncher;

    /**
     * The current date shown in the main activity.
     */
    private Calendar mCurrentDateShown = Calendar.getInstance();

    /**
     * The zman that is coming up next.
     */
    public static Date sNextUpcomingZman = null;

    /**
     * These calendars are used to know when daf/yerushalmi yomi started
     */
    private final static Calendar dafYomiStartDate = new GregorianCalendar(1923, Calendar.SEPTEMBER, 11);
    private final static Calendar dafYomiYerushalmiStartDate = new GregorianCalendar(1980, Calendar.FEBRUARY, 2);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme); //splash screen
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mLayout = findViewById(R.id.main_layout);
        mHandler = new Handler(getMainLooper());
        mSharedPreferences = getSharedPreferences(SHARED_PREF, MODE_PRIVATE);
        mSettingsPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mGestureDetector = new GestureDetector(MainActivity.this, new ZmanimGestureListener());
        mZmanimFormatter.setTimeFormat(ZmanimFormatter.SEXAGESIMAL_FORMAT);
        initAlertDialog();
        initSetupResult();
        setupShabbatModeBanner();
        mLocationResolver = new LocationResolver(this, this);
        mJewishDateInfo = new JewishDateInfo(mSharedPreferences.getBoolean("inIsrael", false), true);
        if (!ChaiTables.visibleSunriseFileExists(getExternalFilesDir(null), sCurrentLocationName, mJewishDateInfo.getJewishCalendar())
                && mSharedPreferences.getBoolean("UseTable" + sCurrentLocationName, true)
                && !mSharedPreferences.getBoolean("isSetup", false)
                && savedInstanceState == null) {//it should only not exist the first time running the app and only if the user has not set up the app
            mSetupLauncher.launch(new Intent(this, FullSetupActivity.class));
            initZmanimNotificationDefaults();
        } else {
            mLocationResolver.acquireLatitudeAndLongitude();
        }
        findAllWeeklyViews();
        if (sGPSLocationServiceIsDisabled && sNetworkLocationServiceIsDisabled) {
            Toast.makeText(MainActivity.this, "Please Enable GPS", Toast.LENGTH_SHORT).show();
        } else {
            if ((!mInitialized && ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED)
                    || mSharedPreferences.getBoolean("useZipcode", false)) {
                initMainView();
            }
        }
    }

    private void initZmanimNotificationDefaults() {
        mSettingsPreferences.edit().putBoolean("zmanim_notifications", true).apply();
        mSettingsPreferences.edit().putInt("Alot", -1).apply();
        mSettingsPreferences.edit().putInt("TalitTefilin", 15).apply();
        mSettingsPreferences.edit().putInt("HaNetz", -1).apply();
        mSettingsPreferences.edit().putInt("SofZmanShmaMGA", 15).apply();
        mSettingsPreferences.edit().putInt("SofZmanShmaGRA", -1).apply();
        mSettingsPreferences.edit().putInt("SofZmanTefila", 15).apply();
        mSettingsPreferences.edit().putInt("SofZmanAchilatChametz", 15).apply();
        mSettingsPreferences.edit().putInt("SofZmanBiurChametz", 15).apply();
        mSettingsPreferences.edit().putInt("Chatzot", -1).apply();
        mSettingsPreferences.edit().putInt("MinchaGedola", -1).apply();
        mSettingsPreferences.edit().putInt("MinchaKetana", -1).apply();
        mSettingsPreferences.edit().putInt("PlagHaMincha", 15).apply();
        mSettingsPreferences.edit().putInt("CandleLighting", 15).apply();
        mSettingsPreferences.edit().putInt("Shkia", 15).apply();
        mSettingsPreferences.edit().putInt("TzeitHacochavim", 15).apply();
        mSettingsPreferences.edit().putInt("FastEnd", 15).apply();
        mSettingsPreferences.edit().putInt("FastEndStringent", 15).apply();
        mSettingsPreferences.edit().putInt("ShabbatEnd", -1).apply();
        mSettingsPreferences.edit().putInt("RT", -1).apply();
        mSettingsPreferences.edit().putInt("NightChatzot", -1).apply();
    }

    /**
     * This method registers the setupLauncher to receive the data that the user entered in the
     * SetupActivity. When the user finishes setting up the app, the setupLauncher will receive the
     * data and set the SharedPreferences to indicate that the user has set up the app.
     * It will also reinitialize the main view with the updated settings.
     */
    private void initSetupResult() {
        mSetupLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    SharedPreferences.Editor editor = mSharedPreferences.edit();
                    mElevation = Double.parseDouble(mSharedPreferences.getString("elevation" + sCurrentLocationName, "0"));
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        if (result.getData() != null) {
                            editor.putString("lastLocation", sCurrentLocationName).apply();
                        }
                    }
                    if (!mInitialized) {
                        initMainView();
                    }
                    instantiateZmanimCalendar();
                    setZmanimLanguageBools();
                    if (mSharedPreferences.getBoolean("weeklyMode", false)) {
                        updateWeeklyZmanim();
                    } else {
                        mMainRecyclerView.setAdapter(new ZmanAdapter(this, getZmanimList()));
                    }
                }
        );
    }

    private void showWeeklyTextViews() {
        TextView englishMonthYear = findViewById(R.id.englishMonthYear);
        TextView locationName = findViewById(R.id.location_name);
        TextView hebrewMonthYear = findViewById(R.id.hebrewMonthYear);
        LinearLayout sunday = findViewById(R.id.sunday);
        LinearLayout monday = findViewById(R.id.monday);
        LinearLayout tuesday = findViewById(R.id.tuesday);
        LinearLayout wednesday = findViewById(R.id.wednesday);
        LinearLayout thursday = findViewById(R.id.thursday);
        LinearLayout friday = findViewById(R.id.friday);
        LinearLayout saturday = findViewById(R.id.saturday);
        TextView weeklyParsha = findViewById(R.id.weeklyParsha);
        TextView weeklyDafs = findViewById(R.id.weeklyDafs);

        englishMonthYear.setVisibility(View.VISIBLE);
        locationName.setVisibility(View.VISIBLE);
        hebrewMonthYear.setVisibility(View.VISIBLE);
        sunday.setVisibility(View.VISIBLE);
        monday.setVisibility(View.VISIBLE);
        tuesday.setVisibility(View.VISIBLE);
        wednesday.setVisibility(View.VISIBLE);
        thursday.setVisibility(View.VISIBLE);
        friday.setVisibility(View.VISIBLE);
        saturday.setVisibility(View.VISIBLE);
        weeklyParsha.setVisibility(View.VISIBLE);
        weeklyDafs.setVisibility(View.VISIBLE);
        mMainRecyclerView.setVisibility(View.GONE);
    }

    private void hideWeeklyTextViews() {
        LinearLayout sunday = findViewById(R.id.sunday);
        LinearLayout monday = findViewById(R.id.monday);
        LinearLayout tuesday = findViewById(R.id.tuesday);
        LinearLayout wednesday = findViewById(R.id.wednesday);
        LinearLayout thursday = findViewById(R.id.thursday);
        LinearLayout friday = findViewById(R.id.friday);
        LinearLayout saturday = findViewById(R.id.saturday);

        mEnglishMonthYear.setVisibility(View.GONE);
        mLocationName.setVisibility(View.GONE);
        mHebrewMonthYear.setVisibility(View.GONE);
        sunday.setVisibility(View.GONE);
        monday.setVisibility(View.GONE);
        tuesday.setVisibility(View.GONE);
        wednesday.setVisibility(View.GONE);
        thursday.setVisibility(View.GONE);
        friday.setVisibility(View.GONE);
        saturday.setVisibility(View.GONE);
        mWeeklyParsha.setVisibility(View.GONE);
        mWeeklyDafs.setVisibility(View.GONE);
        mMainRecyclerView.setVisibility(View.VISIBLE);
    }

    private void findAllWeeklyViews() {
        mEnglishMonthYear = findViewById(R.id.englishMonthYear);
        mLocationName = findViewById(R.id.location_name);
        mHebrewMonthYear = findViewById(R.id.hebrewMonthYear);
        //there are 7 of these sets of views
        mListViews[0] = findViewById(R.id.zmanim);
        mSunday[1] = findViewById(R.id.announcements);
        mSunday[2] = findViewById(R.id.hebrewDay);
        mSunday[3] = findViewById(R.id.hebrewDate);
        mSunday[4] = findViewById(R.id.englishDay);
        mSunday[5] = findViewById(R.id.englishDateNumber);

        mListViews[1] = findViewById(R.id.zmanim2);
        mMonday[1] = findViewById(R.id.announcements2);
        mMonday[2] = findViewById(R.id.hebrewDay2);
        mMonday[3] = findViewById(R.id.hebrewDate2);
        mMonday[4] = findViewById(R.id.englishDay2);
        mMonday[5] = findViewById(R.id.englishDateNumber2);

        mListViews[2] = findViewById(R.id.zmanim3);
        mTuesday[1] = findViewById(R.id.announcements3);
        mTuesday[2] = findViewById(R.id.hebrewDay3);
        mTuesday[3] = findViewById(R.id.hebrewDate3);
        mTuesday[4] = findViewById(R.id.englishDay3);
        mTuesday[5] = findViewById(R.id.englishDateNumber3);

        mListViews[3] = findViewById(R.id.zmanim4);
        mWednesday[1] = findViewById(R.id.announcements4);
        mWednesday[2] = findViewById(R.id.hebrewDay4);
        mWednesday[3] = findViewById(R.id.hebrewDate4);
        mWednesday[4] = findViewById(R.id.englishDay4);
        mWednesday[5] = findViewById(R.id.englishDateNumber4);

        mListViews[4] = findViewById(R.id.zmanim5);
        mThursday[1] = findViewById(R.id.announcements5);
        mThursday[2] = findViewById(R.id.hebrewDay5);
        mThursday[3] = findViewById(R.id.hebrewDate5);
        mThursday[4] = findViewById(R.id.englishDay5);
        mThursday[5] = findViewById(R.id.englishDateNumber5);

        mListViews[5] = findViewById(R.id.zmanim6);
        mFriday[1] = findViewById(R.id.announcements6);
        mFriday[2] = findViewById(R.id.hebrewDay6);
        mFriday[3] = findViewById(R.id.hebrewDate6);
        mFriday[4] = findViewById(R.id.englishDay6);
        mFriday[5] = findViewById(R.id.englishDateNumber6);

        mListViews[6] = findViewById(R.id.zmanim7);
        mSaturday[1] = findViewById(R.id.announcements7);
        mSaturday[2] = findViewById(R.id.hebrewDay7);
        mSaturday[3] = findViewById(R.id.hebrewDate7);
        mSaturday[4] = findViewById(R.id.englishDay7);
        mSaturday[5] = findViewById(R.id.englishDateNumber7);

        mWeeklyParsha = findViewById(R.id.weeklyParsha);
        mWeeklyDafs = findViewById(R.id.weeklyDafs);
    }

    /**
     * This method initializes the main view. This method should only be called when we are able to initialize the @{link mROZmanimCalendar} object
     * with the correct latitude, longitude, elevation, and timezone.
     */
    private void initMainView() {
        mInitialized = true;
        mLocationResolver.setTimeZoneID();
        getAndConfirmLastElevationAndVisibleSunriseData();
        instantiateZmanimCalendar();
        saveGeoLocationInfo();
        setZmanimLanguageBools();
        setNextUpcomingZman();
        setupRecyclerViewAndTextViews();
        createBackgroundThreadForNextUpcomingZman();
        setupButtons();
        setNotifications();
        checkIfUserIsInIsraelOrNot();
        askForBackgroundLocationPermission();
    }

    private void setZmanimLanguageBools() {
        if (mSharedPreferences.getBoolean("isZmanimInHebrew", false)) {
            mIsZmanimInHebrew = true;
            mIsZmanimEnglishTranslated = false;
        } else if (mSharedPreferences.getBoolean("isZmanimEnglishTranslated", false)) {
            mIsZmanimInHebrew = false;
            mIsZmanimEnglishTranslated = true;
        } else {
            mIsZmanimInHebrew = false;
            mIsZmanimEnglishTranslated = false;
        }
    }

    private void askForBackgroundLocationPermission() {
        if (mSharedPreferences.getBoolean("useZipcode", false)) {
            return;//if the user is using a zipcode, we don't need to ask for background location permission as we don't use the device's location
        }
        if (!mSharedPreferences.getBoolean("askedForRealtimeNotifications", false)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Would you like to receive real-time notifications for zmanim?");
            builder.setMessage("If you would like to receive real-time zmanim notifications for your current location, " +
                    "please navigate to the settings page and enable location services all the time for this app. " +
                    "Would you like to do this now?");
            builder.setCancelable(false);
            builder.setPositiveButton("Yes", (dialog, which) -> {
                if (ActivityCompat.checkSelfPermission(this, ACCESS_BACKGROUND_LOCATION) != PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{ACCESS_BACKGROUND_LOCATION}, 1);
                }
                mSharedPreferences.edit().putBoolean("askedForRealtimeNotifications", true).apply();
            });
            builder.setNegativeButton("No", (dialog, which) -> {
                mSharedPreferences.edit().putBoolean("askedForRealtimeNotifications", true).apply();
                dialog.dismiss();
            });
            builder.show();
        }
    }

    private void checkIfUserIsInIsraelOrNot() {
        if (mSharedPreferences.getBoolean("neverAskInIsraelOrNot", false)) {return;}

        if (sCurrentTimeZoneID.equals("Asia/Jerusalem")) {//user is in or near israel now
            mSharedPreferences.edit().putBoolean("askedNotInIsrael", false).apply();//reset that we asked outside israel for next time
            if (!mSharedPreferences.getBoolean("inIsrael", false) && //user was not in israel before
                    !mSharedPreferences.getBoolean("askedInIsrael", false)) {//and we did not ask already
                new AlertDialog.Builder(this)
                        .setTitle("Are you in Israel now?")
                        .setMessage("If you are in Israel now, please confirm below. Otherwise, ignore this message. (This setting only affects the holidays).")
                        .setPositiveButton("Yes, I am in Israel", (dialog, which) -> {
                            mSharedPreferences.edit().putBoolean("inIsrael", true).apply();
                            mJewishDateInfo = new JewishDateInfo(true, true);
                            Toast.makeText(this, "Settings updated", Toast.LENGTH_SHORT).show();
                            if (mSharedPreferences.getBoolean("weeklyMode", false)) {
                                updateWeeklyZmanim();
                            } else {
                                mMainRecyclerView.setAdapter(new ZmanAdapter(this, getZmanimList()));
                            }
                        })
                        .setNegativeButton("No, I am not in Israel", (dialog, which) -> {
                            mSharedPreferences.edit().putBoolean("askedInIsrael", true).apply();//save that we asked already
                            dialog.dismiss();
                        })
                        .setNeutralButton("Do not ask me again", (dialog, which) -> {
                            mSharedPreferences.edit().putBoolean("neverAskInIsraelOrNot", true).apply();//save that we should never ask again
                            dialog.dismiss();
                        })
                        .show();
            }
        } else {//user is not in israel
            mSharedPreferences.edit().putBoolean("askedInIsrael", false).apply();//reset that we asked in israel
            if (mSharedPreferences.getBoolean("inIsrael", false) && //user was in israel before
                    !mSharedPreferences.getBoolean("askedInNotIsrael", false)) {//and we did not ask already
                new AlertDialog.Builder(this)
                        .setTitle("Have you left Israel?")
                        .setMessage("If you are not in Israel now, please confirm below. Otherwise, ignore this message. (This setting only affects the holidays).")
                        .setPositiveButton("Yes, I have left Israel", (dialog, which) -> {
                            mSharedPreferences.edit().putBoolean("inIsrael", false).apply();
                            mJewishDateInfo = new JewishDateInfo(false, true);
                            Toast.makeText(this, "Settings updated", Toast.LENGTH_SHORT).show();
                            if (mSharedPreferences.getBoolean("weeklyMode", false)) {
                                updateWeeklyZmanim();
                            } else {
                                mMainRecyclerView.setAdapter(new ZmanAdapter(this, getZmanimList()));
                            }
                        })
                        .setNegativeButton("No, I have not left Israel", (dialog, which) -> {
                            mSharedPreferences.edit().putBoolean("askedInNotIsrael", true).apply();//save that we asked
                            dialog.dismiss();
                        })
                        .setNeutralButton("Do not ask me again", (dialog, which) -> {
                            mSharedPreferences.edit().putBoolean("neverAskInIsraelOrNot", true).apply();//save that we should never ask again
                            dialog.dismiss();
                        })
                        .show();
            }
        }
    }

    /**
     * This method will automatically update the tables if the user has setup the app before for the current location.
     * @param fromButton if the method is called from the buttons, it will not ask more than once if the user wants to update the tables.
     */
    private void seeIfTablesNeedToBeUpdated(boolean fromButton) {
        if (mSharedPreferences.getBoolean("isSetup", false) //only check after the app has been setup before
                && mSharedPreferences.getBoolean("UseTable" + sCurrentLocationName, false)) { //and only if the tables are being used

            if (!ChaiTables.visibleSunriseFileExists(getExternalFilesDir(null), sCurrentLocationName, mJewishDateInfo.getJewishCalendar())) {
                if (!mUpdateTablesDialogShown) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Update Tables?");
                    builder.setMessage("The visible sunrise tables for the current location and year need to be updated.\n\n" +
                            "Do you want to update the tables now?");
                    builder.setPositiveButton("Yes", (dialog, which) -> {
                        String chaitablesURL = mSharedPreferences.getString("chaitablesLink" + sCurrentLocationName, "");
                        if (!chaitablesURL.isEmpty()) {//it should not be empty if the user has set up the app, but it is good to check
                            String hebrewYear = String.valueOf(mJewishDateInfo.getJewishCalendar().getJewishYear());
                            Pattern pattern = Pattern.compile("&cgi_yrheb=\\d{4}");
                            Matcher matcher = pattern.matcher(chaitablesURL);
                            if (matcher.find()) {
                                chaitablesURL = chaitablesURL.replace(matcher.group(), "&cgi_yrheb=" + hebrewYear);//replace the year in the URL with the current year
                            }
                            ChaiTablesScraper scraper = new ChaiTablesScraper();
                            scraper.setDownloadSettings(chaitablesURL, getExternalFilesDir(null), mJewishDateInfo.getJewishCalendar());
                            scraper.start();
                            try {
                                scraper.join();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if (mSharedPreferences.getBoolean("weeklyMode", false)) {
                                updateWeeklyZmanim();
                            } else {
                                mMainRecyclerView.setAdapter(new ZmanAdapter(this, getZmanimList()));
                            }
                        }
                    });
                    builder.setNegativeButton("No", (dialog, which) -> dialog.dismiss());
                    builder.show();
                    if (fromButton) {
                        mUpdateTablesDialogShown = true;
                    }
                }
            }
        }
    }

    private void instantiateZmanimCalendar() {
        mROZmanimCalendar = new ROZmanimCalendar(new GeoLocation(
                sCurrentLocationName,
                sLatitude,
                sLongitude,
                mElevation,
                TimeZone.getTimeZone(sCurrentTimeZoneID)));
        mROZmanimCalendar.setExternalFilesDir(getExternalFilesDir(null));
        mROZmanimCalendar.setCandleLightingOffset(Double.parseDouble(mSettingsPreferences.getString("CandleLightingOffset", "20")));
        mROZmanimCalendar.setAteretTorahSunsetOffset(Double.parseDouble(mSettingsPreferences.getString("EndOfShabbatOffset", "40")));
    }

    private void setupRecyclerViewAndTextViews() {
        mMainRecyclerView = findViewById(R.id.mainRV);
        mMainRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mMainRecyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        mMainRecyclerView.setOnTouchListener((view, motionEvent) -> mGestureDetector.onTouchEvent(motionEvent));
        findViewById(R.id.main_layout).setOnTouchListener((view, motionEvent) -> mGestureDetector.onTouchEvent(motionEvent));
        if (mSharedPreferences.getBoolean("weeklyMode", false)) {
            showWeeklyTextViews();
            updateWeeklyZmanim();
        } else {
            hideWeeklyTextViews();
            mMainRecyclerView.setAdapter(new ZmanAdapter(this, getZmanimList()));
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mGestureDetector.onTouchEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        super.dispatchTouchEvent(ev);
        return mGestureDetector.onTouchEvent(ev);
    }

    /**
     * This method initializes the shabbat mode banner and sets up the functionality of hiding the banner when the user taps on it.
     */
    private void setupShabbatModeBanner() {
        mShabbatModeBanner = findViewById(R.id.shabbat_mode);
        mShabbatModeBanner.setSelected(true);
        mShabbatModeBanner.setOnClickListener(v -> {
            if (v.getVisibility() == View.VISIBLE) {
                v.setVisibility(View.GONE);
            } else {
                v.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setupButtons() {
        setupPreviousDayButton();
        setupCalendarButton();
        setupNextDayButton();
    }

    /**
     * Sets up the previous day button
     */
    private void setupPreviousDayButton() {
        mPreviousDate = findViewById(R.id.prev_day);
        mPreviousDate.setOnClickListener(v -> {
            if (!sShabbatMode) {
                mCurrentDateShown = (Calendar) mROZmanimCalendar.getCalendar().clone();//just get a calendar object with the same date as the current one
                if (mSharedPreferences.getBoolean("weeklyMode", false)) {
                    mCurrentDateShown.add(Calendar.DATE, -7);//subtract seven days
                } else {
                    mCurrentDateShown.add(Calendar.DATE, -1);//subtract one day
                }
                mROZmanimCalendar.setCalendar(mCurrentDateShown);
                mJewishDateInfo.setCalendar(mCurrentDateShown);
                if (mSharedPreferences.getBoolean("weeklyMode", false)) {
                    updateWeeklyZmanim();
                } else {
                    mMainRecyclerView.setAdapter(new ZmanAdapter(this, getZmanimList()));
                }
                mCalendarButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, getCurrentCalendarDrawable());
                seeIfTablesNeedToBeUpdated(true);
            }
        });
    }

    /**
     * Sets up the next day button
     */
    private void setupNextDayButton() {
        mNextDate = findViewById(R.id.next_day);
        mNextDate.setOnClickListener(v -> {
            if (!sShabbatMode) {
                mCurrentDateShown = (Calendar) mROZmanimCalendar.getCalendar().clone();
                if (mSharedPreferences.getBoolean("weeklyMode", false)) {
                    mCurrentDateShown.add(Calendar.DATE, 7);//add seven days
                } else {
                    mCurrentDateShown.add(Calendar.DATE, 1);//add one day
                }
                mROZmanimCalendar.setCalendar(mCurrentDateShown);
                mJewishDateInfo.setCalendar(mCurrentDateShown);
                if (mSharedPreferences.getBoolean("weeklyMode", false)) {
                    updateWeeklyZmanim();
                } else {
                    mMainRecyclerView.setAdapter(new ZmanAdapter(this, getZmanimList()));
                }
                mCalendarButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, getCurrentCalendarDrawable());
                seeIfTablesNeedToBeUpdated(true);
            }
        });
    }

    /**
     * Setup the calendar button.
     */
    private void setupCalendarButton() {
        mCalendarButton = findViewById(R.id.calendar);
        DatePickerDialog dialog = createDialog();

        mCalendarButton.setOnClickListener(v -> {
            if (!sShabbatMode) {
                dialog.updateDate(mROZmanimCalendar.getCalendar().get(Calendar.YEAR),
                        mROZmanimCalendar.getCalendar().get(Calendar.MONTH),
                        mROZmanimCalendar.getCalendar().get(Calendar.DAY_OF_MONTH));
                dialog.show();
            }
        });

        mCalendarButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, getCurrentCalendarDrawable());
    }

    /**
     * Sets up the dialog for the calendar button. Added a custom date picker to the dialog as well.
     * @return The dialog.
     * @see CustomDatePickerDialog for more information.
     */
    private DatePickerDialog createDialog() {
        DatePickerDialog.OnDateSetListener onDateSetListener = (view, year, month, day) -> {
            Calendar mUserChosenDate = Calendar.getInstance();
            mUserChosenDate.set(year, month, day);
            mROZmanimCalendar.setCalendar(mUserChosenDate);
            mJewishDateInfo.setCalendar(mUserChosenDate);
            mCurrentDateShown = (Calendar) mROZmanimCalendar.getCalendar().clone();
            if (mSharedPreferences.getBoolean("weeklyMode", false)) {
                updateWeeklyZmanim();
            } else {
                mMainRecyclerView.setAdapter(new ZmanAdapter(this, getZmanimList()));
            }
            mCalendarButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, MainActivity.this.getCurrentCalendarDrawable());
            seeIfTablesNeedToBeUpdated(true);
        };

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            return new CustomDatePickerDialog(this, onDateSetListener,
                    mROZmanimCalendar.getCalendar().get(Calendar.YEAR),
                    mROZmanimCalendar.getCalendar().get(Calendar.MONTH),
                    mROZmanimCalendar.getCalendar().get(Calendar.DAY_OF_MONTH),
                    mJewishDateInfo.getJewishCalendar());
        } else {
            return new DatePickerDialog(this, onDateSetListener,
                    mROZmanimCalendar.getCalendar().get(Calendar.YEAR),
                    mROZmanimCalendar.getCalendar().get(Calendar.MONTH),
                    mROZmanimCalendar.getCalendar().get(Calendar.DAY_OF_MONTH));
        }
    }

    /**
     * Returns the current calendar drawable depending on the current day of the month.
     */
    private int getCurrentCalendarDrawable() {
        switch (mROZmanimCalendar.getCalendar().get(Calendar.DATE)) {
            case (1):
                return R.drawable.calendar1;
            case (2):
                return R.drawable.calendar2;
            case (3):
                return R.drawable.calendar3;
            case (4):
                return R.drawable.calendar4;
            case (5):
                return R.drawable.calendar5;
            case (6):
                return R.drawable.calendar6;
            case (7):
                return R.drawable.calendar7;
            case (8):
                return R.drawable.calendar8;
            case (9):
                return R.drawable.calendar9;
            case (10):
                return R.drawable.calendar10;
            case (11):
                return R.drawable.calendar11;
            case (12):
                return R.drawable.calendar12;
            case (13):
                return R.drawable.calendar13;
            case (14):
                return R.drawable.calendar14;
            case (15):
                return R.drawable.calendar15;
            case (16):
                return R.drawable.calendar16;
            case (17):
                return R.drawable.calendar17;
            case (18):
                return R.drawable.calendar18;
            case (19):
                return R.drawable.calendar19;
            case (20):
                return R.drawable.calendar20;
            case (21):
                return R.drawable.calendar21;
            case (22):
                return R.drawable.calendar22;
            case (23):
                return R.drawable.calendar23;
            case (24):
                return R.drawable.calendar24;
            case (25):
                return R.drawable.calendar25;
            case (26):
                return R.drawable.calendar26;
            case (27):
                return R.drawable.calendar27;
            case (28):
                return R.drawable.calendar28;
            case (29):
                return R.drawable.calendar29;
            case (30):
                return R.drawable.calendar30;
            default:
                return R.drawable.calendar31;
        }
    }

    @Override
    protected void onPause() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (sShabbatMode) {
            startActivity(getIntent());
        }
        if (mMainRecyclerView != null) {
            mCurrentPosition = ((LinearLayoutManager)mMainRecyclerView.getLayoutManager()).findFirstVisibleItemPosition();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (mROZmanimCalendar == null) {
            super.onResume();
            return;
        }
        mJewishDateInfo = new JewishDateInfo(mSharedPreferences.getBoolean("inIsrael", false), true);
        mJewishDateInfo.setCalendar(mCurrentDateShown);
        setNextUpcomingZman();
        setZmanimLanguageBools();
        if (sFromSettings) {
            sFromSettings = false;
            instantiateZmanimCalendar();
            mROZmanimCalendar.setCalendar(mCurrentDateShown);
            if (mSharedPreferences.getBoolean("weeklyMode", false)) {
                updateWeeklyZmanim();
            } else {
                mMainRecyclerView.setAdapter(new ZmanAdapter(this, getZmanimList()));
                mMainRecyclerView.scrollToPosition(mCurrentPosition);
            }
        }
        getAndConfirmLastElevationAndVisibleSunriseData();
        resetTheme();
        if (mSharedPreferences.getBoolean("useImage", false)) {
            Bitmap bitmap = BitmapFactory.decodeFile(mSharedPreferences.getString("imageLocation", ""));
            Drawable drawable = new BitmapDrawable(getResources(), bitmap);
            mLayout.setBackground(drawable);
        }
        if (mSharedPreferences.getBoolean("useDefaultCalButtonColor", true)) {
            mCalendarButton.setBackgroundColor(getColor(R.color.dark_blue));
        } else {
            mCalendarButton.setBackgroundColor(mSharedPreferences.getInt("CalButtonColor", 0x18267C));
        }
        Intent zmanIntent = new Intent(getApplicationContext(), ZmanimNotifications.class);//this is to update the zmanim notifications if the user changed the settings to start showing them
        mSharedPreferences.edit().putBoolean("fromThisNotification", false).apply();
        PendingIntent zmanimPendingIntent = PendingIntent.getBroadcast(getApplicationContext(),0,zmanIntent,PendingIntent.FLAG_IMMUTABLE);
        try {
            zmanimPendingIntent.send();
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
        }
        super.onResume();
    }

    /**
     * sets the theme of the app according to the user's preferences.
     */
    private void resetTheme() {
        String theme = mSettingsPreferences.getString("theme", "Auto (Follow System Theme)");
        switch (theme) {
            case "Auto (Follow System Theme)":
                getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case "Day":
                getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "Night":
                getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
        }
    }

    /**
     * This method saves the information needed to restore a GeoLocation object in the notification classes.
     */
    private void saveGeoLocationInfo() {//needed for notifications
        SharedPreferences.Editor editor = getSharedPreferences(SHARED_PREF, MODE_PRIVATE).edit();
        editor.putString("name", sCurrentLocationName).apply();
        editor.putLong("lat", Double.doubleToRawLongBits(sLatitude)).apply();//see here: https://stackoverflow.com/a/18098090/13593159
        editor.putLong("long", Double.doubleToRawLongBits(sLongitude)).apply();
        editor.putString("timezoneID", sCurrentTimeZoneID).apply();
    }

    /**
     * This method will be called every time the user opens the app. It will reset the notifications every time the app is opened since the user might
     * have changed his location.
     */
    private void setNotifications() {
        Calendar calendar = (Calendar) mROZmanimCalendar.getCalendar().clone();
        calendar.setTimeInMillis(mROZmanimCalendar.getSunrise().getTime());
        if (calendar.getTime().compareTo(new Date()) < 0) {
            calendar.add(Calendar.DATE, 1);
        }
        PendingIntent dailyPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0,
                new Intent(getApplicationContext(), DailyNotifications.class), PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        am.cancel(dailyPendingIntent);//cancel any previous alarms
        am.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), dailyPendingIntent);

        calendar.setTimeInMillis(mROZmanimCalendar.getTzeit().getTime());
        if (calendar.getTime().compareTo(new Date()) < 0) {
            calendar.add(Calendar.DATE, 1);
        }
        PendingIntent omerPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0,
                new Intent(getApplicationContext(), OmerNotifications.class), PendingIntent.FLAG_IMMUTABLE);
        am.cancel(omerPendingIntent);//cancel any previous alarms
        am.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), omerPendingIntent);

        //zmanim notifications are set in the onResume method, doing it twice will cause the preferences to be reset to true mid way through
//        Intent zmanIntent = new Intent(getApplicationContext(), ZmanimNotifications.class);
//        mSharedPreferences.edit().putBoolean("fromThisNotification", false).apply();
//        PendingIntent zmanimPendingIntent = PendingIntent.getBroadcast(getApplicationContext(),0,zmanIntent,PendingIntent.FLAG_IMMUTABLE);
//        try {
//            zmanimPendingIntent.send();
//        } catch (PendingIntent.CanceledException e) {
//            e.printStackTrace();
//        }
    }

    /**
     * Override this method to make sure nothing is blocking the app over shabbat/yom tov
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus)
            if (sShabbatMode) {
                startActivity(getIntent());
            }
    }

    /**
     * This method is called when the user clicks on shabbat mode. The main point of this method is to automatically scroll through the list of zmanim
     * and update the date when the time reaches the next date at 12:00:02am. It will also update the shabbat banner to reflect the next day's date.
     * (The reason why I chose 12:00:02am is to avoid a hiccup if the device is too fast to update the time, although it is probably not a big deal.)
     * @see #startScrollingThread() to start the thread that will scroll through the list of zmanim
     * @see #setShabbatBannersText(boolean) to set the text of the shabbat banners
     */
    private void startShabbatMode() {
        if (!sShabbatMode) {
            sShabbatMode = true;
            setShabbatBannersText(true);
            mShabbatModeBanner.setVisibility(View.VISIBLE);
            int orientation;
            int rotation = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            switch (rotation) {
                case Surface.ROTATION_90:
                case Surface.ROTATION_270:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_180:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                case Surface.ROTATION_0:
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
            }
            setRequestedOrientation(orientation);
            Calendar calendar = Calendar.getInstance();
            Calendar calendar2 = (Calendar) calendar.clone();
            mZmanimUpdater = () -> {
                calendar.setTimeInMillis(new Date().getTime());
                mCurrentDateShown.setTimeInMillis(calendar.getTime().getTime());
                mROZmanimCalendar.setCalendar(calendar);
                mJewishDateInfo.setCalendar(calendar);
                setShabbatBannersText(false);
                if (mSharedPreferences.getBoolean("weeklyMode", false)) {
                    updateWeeklyZmanim();
                } else {
                    mMainRecyclerView.setAdapter(new ZmanAdapter(this, getZmanimList()));
                }
                mCalendarButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, getCurrentCalendarDrawable());
                mHandler.removeCallbacks(mZmanimUpdater);
                mHandler.postDelayed(mZmanimUpdater, TWENTY_FOUR_HOURS_IN_MILLI);//run the update in 24 hours
            };
            calendar.set(Calendar.HOUR_OF_DAY,0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 2);
            calendar.add(Calendar.DATE,1);
            mHandler.postDelayed(mZmanimUpdater,calendar.getTimeInMillis() - calendar2.getTimeInMillis());//time remaining until 12:00:02am the next day
            startScrollingThread();
        }
    }

    /**
     * Sets the text of the shabbat banners based on the NEXT day's date, since most people will start shabbat mode before shabbat/chag starts.
     * @param isFirstTime if true, the text will be set based on the next day's date, otherwise it will be set based on the current date.
     *                    Since it will be called at 12:00:02am the next day, we do not need to worry about the next day's date.
     */
    @SuppressLint("SetTextI18n")
    private void setShabbatBannersText(boolean isFirstTime) {
        if (isFirstTime) {
            mCurrentDateShown.add(Calendar.DATE,1);
            mJewishDateInfo.setCalendar(mCurrentDateShown);
        }

        boolean isShabbat = mCurrentDateShown.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY;

        StringBuilder sb = new StringBuilder();

        switch (mJewishDateInfo.getJewishCalendar().getYomTovIndex()) {
            case JewishCalendar.PESACH:
                for (int i = 0; i < 4; i++) {
                    sb.append("PESACH");
                    if (isShabbat) {
                        sb.append("/SHABBAT");
                    }
                    sb.append(" MODE                ");
                }
                mShabbatModeBanner.setText(sb.toString());
                mShabbatModeBanner.setBackgroundColor(getColor(R.color.lightYellow));
                mShabbatModeBanner.setTextColor(getColor(R.color.black));
                mCalendarButton.setBackgroundColor(getColor(R.color.lightYellow));
                break;
            case JewishCalendar.SHAVUOS:
                for (int i = 0; i < 4; i++) {
                    sb.append("SHAVUOT");
                    if (isShabbat) {
                        sb.append("/SHABBAT");
                    }
                    sb.append(" MODE                ");
                }
                mShabbatModeBanner.setText(sb.toString());
                mShabbatModeBanner.setBackgroundColor(getColor(R.color.light_blue));
                mShabbatModeBanner.setTextColor(getColor(R.color.white));
                mCalendarButton.setBackgroundColor(getColor(R.color.light_blue));
                break;
            case JewishCalendar.SUCCOS:
            case JewishCalendar.SHEMINI_ATZERES:
                for (int i = 0; i < 4; i++) {
                    sb.append("SHEMINI ATZERET");
                    if (isShabbat) {
                        sb.append("/SHABBAT");
                    }
                    sb.append(" MODE                ");
                }
                mShabbatModeBanner.setText(sb.toString());
                mShabbatModeBanner.setBackgroundColor(getColor(R.color.light_green));
                mShabbatModeBanner.setTextColor(getColor(R.color.black));
                mCalendarButton.setBackgroundColor(getColor(R.color.light_green));
                break;
            case JewishCalendar.SIMCHAS_TORAH:
                for (int i = 0; i < 4; i++) {
                    sb.append("SUCCOT");
                    if (isShabbat) {
                        sb.append("/SHABBAT");
                    }
                    sb.append(" MODE                ");
                }
                mShabbatModeBanner.setText(sb.toString());
                mShabbatModeBanner.setBackgroundColor(getColor(R.color.green));
                mShabbatModeBanner.setTextColor(getColor(R.color.black));
                mCalendarButton.setBackgroundColor(getColor(R.color.green));
                break;
            case JewishCalendar.ROSH_HASHANA:
                for (int i = 0; i < 4; i++) {
                    sb.append("ROSH HASHANA");
                    if (isShabbat) {
                        sb.append("/SHABBAT");
                    }
                    sb.append(" MODE                ");
                }
                mShabbatModeBanner.setText(sb.toString());
                mShabbatModeBanner.setBackgroundColor(getColor(R.color.dark_red));
                mShabbatModeBanner.setTextColor(getColor(R.color.white));
                mCalendarButton.setBackgroundColor(getColor(R.color.dark_red));
                break;
            case JewishCalendar.YOM_KIPPUR:
                for (int i = 0; i < 4; i++) {
                    sb.append("YOM KIPPUR");
                    if (isShabbat) {
                        sb.append("/SHABBAT");
                    }
                    sb.append(" MODE                ");
                }
                mShabbatModeBanner.setText(sb.toString());
                mShabbatModeBanner.setBackgroundColor(getColor(R.color.white));
                mShabbatModeBanner.setTextColor(getColor(R.color.black));
                mCalendarButton.setBackgroundColor(getColor(R.color.white));
                break;
            default:
                mShabbatModeBanner.setText("SHABBAT MODE                " +
                        "SHABBAT MODE               " +
                        "SHABBAT MODE               " +
                        "SHABBAT MODE               " +
                        "SHABBAT MODE");
                mShabbatModeBanner.setBackgroundColor(getColor(R.color.dark_blue));
                mShabbatModeBanner.setTextColor(getColor(R.color.white));
                mCalendarButton.setBackgroundColor(getColor(R.color.dark_blue));
        }

        if (isFirstTime) {
            mCurrentDateShown.add(Calendar.DATE,-1);
            mJewishDateInfo.setCalendar(mCurrentDateShown);
        }
    }

    /**
     * This method is called when the user clicks on shabbat mode. It will create another thread that will constantly try to scroll the recycler view
     * up and down.
     */
    @SuppressWarnings({"BusyWait"})
    private void startScrollingThread() {
        Thread scrollingThread = new Thread(() -> {
                    while (mMainRecyclerView.canScrollVertically(1)) {
                        if (!sShabbatMode) break;
                        if (mMainRecyclerView.canScrollVertically(1)) {
                            mMainRecyclerView.smoothScrollBy(0,5);
                        }
                        try {//must have these busy waits for scrolling to work properly. I assume it breaks because it is currently animating something. Will have to fix this in the future, but it works for now.
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    try {//must have these waits or else the RecyclerView will have corrupted info
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    while (mMainRecyclerView.canScrollVertically(-1)) {
                        if (!sShabbatMode) break;
                        if (mMainRecyclerView.canScrollVertically(-1)) {
                            mMainRecyclerView.smoothScrollBy(0,-5);
                        }
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                if (sShabbatMode) {
                    startScrollingThread();
                }
        });
        scrollingThread.start();
    }

    /**
     * This method is called when the user wants to end shabbat mode. It will hide the banner and remove the automatic zmanim updater queued task
     * from the handler. I will also reset the color of the calendar button to the default color.
     * @see #startScrollingThread()
     * @see #startShabbatMode()
     */
    private void endShabbatMode() {
        if (sShabbatMode) {
            sShabbatMode = false;
            mShabbatModeBanner.setVisibility(View.GONE);
            mHandler.removeCallbacksAndMessages(mZmanimUpdater);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            if (mSharedPreferences.getBoolean("useDefaultCalButtonColor", true)) {
                mCalendarButton.setBackgroundColor(getColor(R.color.dark_blue));
            } else {
                mCalendarButton.setBackgroundColor(mSharedPreferences.getInt("CalButtonColor", 0x18267C));
            }
        }
    }

    /**
     * This is the main method for updating the Zmanim in the recyclerview. It is called everytime the user changes the date or updates
     * any setting that affects the zmanim. This method returns a list of strings which are added to the recyclerview.
     * The strings that are zmanim are in the following format: "Zman= 12:00:00 AM" (seconds are optional). We parse the strings in @link{ZmanAdapter}
     * @return the updated information and Zmanim for the current day in a List of Strings with the following format: zman= 12:00:00 AM
     * (seconds are optional)
     */
    private List<ZmanListEntry> getZmanimList() {
        DateFormat zmanimFormat;
        if (mSettingsPreferences.getBoolean("ShowSeconds", false)) {
            zmanimFormat = new SimpleDateFormat("h:mm:ss aa", Locale.getDefault());
        } else {
            zmanimFormat = new SimpleDateFormat("h:mm aa", Locale.getDefault());
        }
        zmanimFormat.setTimeZone(TimeZone.getTimeZone(sCurrentTimeZoneID)); //set the formatters time zone

        List<ZmanListEntry> zmanim = new ArrayList<>();

        zmanim.add(new ZmanListEntry(mROZmanimCalendar.getGeoLocation().getLocationName()));

        StringBuilder sb = new StringBuilder();
        sb.append(mJewishDateInfo.getJewishCalendar().toString()
                .replace("Teves", "Tevet").replace("Tishrei", "Tishri"));
        if (DateUtils.isSameDay(mROZmanimCalendar.getCalendar().getTime(), new Date())) {
            sb.append("   ▼   ");//add a down arrow to indicate that this is the current day
        } else {
            sb.append("      ");
        }
        sb.append(mROZmanimCalendar.getCalendar().get(Calendar.DATE));
        sb.append(" ");
        sb.append(mROZmanimCalendar.getCalendar().getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()));
        sb.append(", ");
        sb.append(mROZmanimCalendar.getCalendar().get(Calendar.YEAR));
        zmanim.add(new ZmanListEntry(sb.toString()));

        zmanim.add(new ZmanListEntry(mJewishDateInfo.getThisWeeksParsha()));

        zmanim.add(new ZmanListEntry(mROZmanimCalendar.getCalendar()
                .getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault())
                + " / " +
                mJewishDateInfo.getJewishDayOfWeek()));

        String day = mJewishDateInfo.getSpecialDay();
        if (!day.isEmpty()) {
            zmanim.add(new ZmanListEntry(day));
        }

        String isOKToListenToMusic = mJewishDateInfo.isOKToListenToMusic();
        if (!isOKToListenToMusic.isEmpty()) {
            zmanim.add(new ZmanListEntry(isOKToListenToMusic));
        }

        String ulChaparatPesha = mJewishDateInfo.getIsUlChaparatPeshaSaid();
        if (!ulChaparatPesha.isEmpty()) {
            zmanim.add(new ZmanListEntry(ulChaparatPesha));
        }

        zmanim.add(new ZmanListEntry(mJewishDateInfo.getIsTachanunSaid()));

        String tonightStartOrEndBirchatLevana = mJewishDateInfo.getIsTonightStartOrEndBirchatLevana();
        if (!tonightStartOrEndBirchatLevana.isEmpty()) {
            zmanim.add(new ZmanListEntry(tonightStartOrEndBirchatLevana));
        }

        if (mJewishDateInfo.getJewishCalendar().isBirkasHachamah()) {
            zmanim.add(new ZmanListEntry("Birchat HaChamah is said today"));
        }

        addTekufaTime(zmanimFormat, zmanim, false);

        addZmanim(zmanim, false);

        if (!mCurrentDateShown.before(dafYomiStartDate)) {
            zmanim.add(new ZmanListEntry("Daf Yomi: " + YomiCalculator.getDafYomiBavli(mJewishDateInfo.getJewishCalendar()).getMasechta()
                    + " " +
                    formatHebrewNumber(YomiCalculator.getDafYomiBavli(mJewishDateInfo.getJewishCalendar()).getDaf())));
        }
        if (!mCurrentDateShown.before(dafYomiYerushalmiStartDate)) {
            String masechta = YerushalmiYomiCalculator.getDafYomiYerushalmi(mJewishDateInfo.getJewishCalendar()).getMasechta();
            String daf = formatHebrewNumber(YerushalmiYomiCalculator.getDafYomiYerushalmi(mJewishDateInfo.getJewishCalendar()).getDaf());
            if (daf == null) {
                zmanim.add(new ZmanListEntry("No Daf Yomi Yerushalmi"));
            } else {
                zmanim.add(new ZmanListEntry("Yerushalmi Yomi: " + masechta + " " + daf));
            }
        }

        zmanim.add(new ZmanListEntry(mJewishDateInfo.getIsMashivHaruchOrMoridHatalSaid()
                + " / "
                + mJewishDateInfo.getIsBarcheinuOrBarechAleinuSaid()));

        zmanim.add(new ZmanListEntry("Shaah Zmanit GR\"A: " + mZmanimFormatter.format(mROZmanimCalendar.getShaahZmanisGra()) +
                " MG\"A: " + mZmanimFormatter.format(mROZmanimCalendar.getShaahZmanis72MinutesZmanis())));

        if (mSettingsPreferences.getBoolean("ShowLeapYear", false)) {
            zmanim.add(new ZmanListEntry(mJewishDateInfo.isJewishLeapYear()));
        }

        if (mSettingsPreferences.getBoolean("ShowDST", false)) {
            if (mROZmanimCalendar.getGeoLocation().getTimeZone().inDaylightTime(mROZmanimCalendar.getSeaLevelSunrise())) {
                zmanim.add(new ZmanListEntry("Daylight Savings Time is on"));
            } else {
                zmanim.add(new ZmanListEntry("Daylight Savings Time is off"));
            }
        }

        if (mSettingsPreferences.getBoolean("ShowElevation", false)) {
            zmanim.add(new ZmanListEntry("Elevation: " + mElevation + " meters"));
        }

        return zmanim;
    }


    private void createBackgroundThreadForNextUpcomingZman() {
        Runnable nextZmanUpdater = () -> {
            setNextUpcomingZman();
            if (mMainRecyclerView != null && !mSharedPreferences.getBoolean("weeklyMode", false)) {
                mCurrentPosition = ((LinearLayoutManager)mMainRecyclerView.getLayoutManager()).findFirstVisibleItemPosition();
                mMainRecyclerView.setAdapter(new ZmanAdapter(this, getZmanimList()));
                mMainRecyclerView.smoothScrollToPosition(mCurrentPosition);
            }
            createBackgroundThreadForNextUpcomingZman();//start a new thread to update the next upcoming zman
        };
        if (sNextUpcomingZman != null) {
            mHandler.postDelayed(nextZmanUpdater,sNextUpcomingZman.getTime() - new Date().getTime() + 1_000);//add 1 second to make sure we don't get the same zman again
        }
    }

    public void setNextUpcomingZman() {
        Date theZman = null;
        List<Date> zmanim = new ArrayList<>();
        mROZmanimCalendar.getCalendar().add(Calendar.DATE, -1);
        addZmanimDates(zmanim);//for the previous day
        mROZmanimCalendar.getCalendar().add(Calendar.DATE, 1);
        addZmanimDates(zmanim);//for the current day
        mROZmanimCalendar.getCalendar().add(Calendar.DATE, 1);
        addZmanimDates(zmanim);//for the next day
        mROZmanimCalendar.getCalendar().add(Calendar.DATE, -1);//return to the current day
        //find the next upcoming zman that is after the current time and before all the other zmanim
        for (Date zman : zmanim) {
            if (zman != null && zman.after(new Date()) && (theZman == null || zman.before(theZman))) {
                theZman = zman;
            }
        }
        sNextUpcomingZman = theZman;
    }

    private void addZmanimDates(List<Date> zmanim) {
        zmanim.add(mROZmanimCalendar.getAlos72Zmanis());
        zmanim.add(mROZmanimCalendar.getEarliestTalitTefilin());
        if (mSettingsPreferences.getBoolean("ShowElevatedSunrise", false)) {
            zmanim.add(mROZmanimCalendar.getSunrise());
        }
        if (mROZmanimCalendar.getHaNetz() != null && !mSharedPreferences.getBoolean("showMishorSunrise" + sCurrentLocationName, true)) {
            zmanim.add(mROZmanimCalendar.getHaNetz());
        } else {
            zmanim.add(mROZmanimCalendar.getSeaLevelSunrise());
        }
        if (mROZmanimCalendar.getHaNetz() != null &&
                !mSharedPreferences.getBoolean("showMishorSunrise" + sCurrentLocationName, true) &&
                mSettingsPreferences.getBoolean("ShowMishorAlways", false)) {
            zmanim.add(mROZmanimCalendar.getSeaLevelSunrise());
        }
        zmanim.add(mROZmanimCalendar.getSofZmanShmaMGA72MinutesZmanis());
        zmanim.add(mROZmanimCalendar.getSofZmanShmaGRA());
        if (mJewishDateInfo.getJewishCalendar().getYomTovIndex() == JewishCalendar.EREV_PESACH) {
            zmanim.add(mROZmanimCalendar.getSofZmanTfilaMGA72MinutesZmanis());
            zmanim.add(mROZmanimCalendar.getSofZmanTfilaGRA());
            zmanim.add(mROZmanimCalendar.getSofZmanBiurChametzMGA());
        } else {
            zmanim.add(mROZmanimCalendar.getSofZmanTfilaGRA());
        }
        zmanim.add(mROZmanimCalendar.getChatzot());
        zmanim.add(mROZmanimCalendar.getMinchaGedolaGreaterThan30());
        zmanim.add(mROZmanimCalendar.getMinchaKetana());
        zmanim.add(mROZmanimCalendar.getPlagHamincha());
        if ((mJewishDateInfo.getJewishCalendar().hasCandleLighting() &&
                !mJewishDateInfo.getJewishCalendar().isAssurBemelacha()) ||
                mJewishDateInfo.getJewishCalendar().getGregorianCalendar().get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
            zmanim.add(mROZmanimCalendar.getCandleLighting());
        }
        zmanim.add(mROZmanimCalendar.getSunset());
        zmanim.add(mROZmanimCalendar.getTzeit());
        if (mJewishDateInfo.getJewishCalendar().hasCandleLighting() &&
                mJewishDateInfo.getJewishCalendar().isAssurBemelacha()) {
            if (mJewishDateInfo.getJewishCalendar().getGregorianCalendar().get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) {
                zmanim.add(mROZmanimCalendar.getTzeit());
            }
        }
        if (mJewishDateInfo.getJewishCalendar().isTaanis()
                && mJewishDateInfo.getJewishCalendar().getYomTovIndex() != JewishCalendar.YOM_KIPPUR) {
            zmanim.add(mROZmanimCalendar.getTzaitTaanit());
            zmanim.add(mROZmanimCalendar.getTzaitTaanitLChumra());
        }
        if (mJewishDateInfo.getJewishCalendar().isAssurBemelacha() && !mJewishDateInfo.getJewishCalendar().hasCandleLighting()) {
            zmanim.add(mROZmanimCalendar.getTzaisAteretTorah());
            if (mSettingsPreferences.getBoolean("RoundUpRT", true)) {
                zmanim.add(addMinuteToZman(mROZmanimCalendar.getTzais72Zmanis()));
            } else {
                zmanim.add(mROZmanimCalendar.getTzais72Zmanis());
            }
        }
        if (mSettingsPreferences.getBoolean("AlwaysShowRT", false)) {
            if (!(mJewishDateInfo.getJewishCalendar().isAssurBemelacha() && !mJewishDateInfo.getJewishCalendar().hasCandleLighting())) {//if we want to always show the zman for RT, we can just NOT the previous cases where we do show it
                if (mSettingsPreferences.getBoolean("RoundUpRT", true)) {
                    zmanim.add(addMinuteToZman(mROZmanimCalendar.getTzais72Zmanis()));
                } else {
                    zmanim.add(mROZmanimCalendar.getTzais72Zmanis());
                }
            }
        }
        zmanim.add(mROZmanimCalendar.getSolarMidnight());
    }

    private String getAnnouncements() {
        StringBuilder announcements = new StringBuilder();

        String day = mJewishDateInfo.getSpecialDay();
        if (!day.isEmpty()) {
            announcements.append(day.replace("/ ","\n")).append("\n");
        }

        String isOKToListenToMusic = mJewishDateInfo.isOKToListenToMusic();
        if (!isOKToListenToMusic.isEmpty()) {
            announcements.append(isOKToListenToMusic).append("\n");
        }

        String ulChaparatPesha = mJewishDateInfo.getIsUlChaparatPeshaSaid();
        if (!ulChaparatPesha.isEmpty()) {
            announcements.append(ulChaparatPesha).append("\n");
        }

        if (mJewishDateInfo.getJewishCalendar().isMashivHaruachEndDate()) {
            announcements.append("מוריד הטל/ברכנו").append("\n");
        }

        if (mJewishDateInfo.getJewishCalendar().isMashivHaruachStartDate()) {
            announcements.append("משיב הרוח").append("\n");
        }

        if (mJewishDateInfo.getJewishCalendar().isVeseinTalUmatarStartDate()) {
            announcements.append("ברך עלינו").append("\n");
        }

        String tachanun = mJewishDateInfo.getIsTachanunSaid();
        if (!tachanun.equals("There is Tachanun today")) {
            announcements.append(tachanun).append("\n");
        }

        String tonightStartOrEndBirchatLevana = mJewishDateInfo.getIsTonightStartOrEndBirchatLevana();
        if (!tonightStartOrEndBirchatLevana.isEmpty()) {
            announcements.append(tonightStartOrEndBirchatLevana).append("\n");
        }

        if (mJewishDateInfo.getJewishCalendar().isBirkasHachamah()) {
            announcements.append("Birchat HaChamah is said today").append("\n");
        }

        List<ZmanListEntry> tekufa = new ArrayList<>();
        addTekufaTime(new SimpleDateFormat("h:mm aa", Locale.getDefault()), tekufa, true);
        if (!tekufa.isEmpty()) {
            announcements.append(tekufa.get(0).getTitle()).append("\n");
        }

        if (!mCurrentDateShown.before(dafYomiYerushalmiStartDate)) {
            String daf = formatHebrewNumber(YerushalmiYomiCalculator.getDafYomiYerushalmi(mJewishDateInfo.getJewishCalendar()).getDaf());
            if (daf == null) {
                announcements.append("No Daf Yomi Yerushalmi").append("\n");
            }
        }
        return announcements.toString();
    }

    private void updateWeeklyZmanim() {
        Calendar backupCal = (Calendar) mCurrentDateShown.clone();
        while (mCurrentDateShown.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            mCurrentDateShown.add(Calendar.DATE, -1);
        }
        mROZmanimCalendar.setCalendar(mCurrentDateShown);//set the calendar to the sunday of that week
        mJewishDateInfo.setCalendar(mCurrentDateShown);

        HebrewDateFormatter hebrewDateFormatter = new HebrewDateFormatter();
        List<TextView[]> weeklyInfo = Arrays.asList(mSunday, mMonday, mTuesday, mWednesday, mThursday, mFriday, mSaturday);

        String month = mCurrentDateShown.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
        String year = String.valueOf(mCurrentDateShown.get(Calendar.YEAR));

        String hebrewMonth = hebrewDateFormatter.formatMonth(mJewishDateInfo.getJewishCalendar())
                .replace("Tishrei", "Tishri")
                .replace("Teves", "Tevet");
        String hebrewYear = String.valueOf(mJewishDateInfo.getJewishCalendar().getJewishYear());

        String masechta = "";
        String yerushalmiMasechta = "";
        String daf = "";
        String yerushalmiDaf = "";

        if (!mCurrentDateShown.before(dafYomiStartDate)) {
            masechta = YomiCalculator.getDafYomiBavli(mJewishDateInfo.getJewishCalendar()).getMasechta();
            daf = formatHebrewNumber(YomiCalculator.getDafYomiBavli(mJewishDateInfo.getJewishCalendar()).getDaf());
        }
        if (!mCurrentDateShown.before(dafYomiYerushalmiStartDate)) {
            yerushalmiMasechta = YerushalmiYomiCalculator.getDafYomiYerushalmi(mJewishDateInfo.getJewishCalendar()).getMasechta();
            yerushalmiDaf = formatHebrewNumber(YerushalmiYomiCalculator.getDafYomiYerushalmi(mJewishDateInfo.getJewishCalendar()).getDaf());
        }

        for (int i = 0; i < 7; i++) {
            if (DateUtils.isSameDay(mROZmanimCalendar.getCalendar().getTime(), new Date())) {
                weeklyInfo.get(i)[4].setBackgroundColor(getColor(R.color.dark_gold));
            } else {
                weeklyInfo.get(i)[4].setBackground(null);
            }
            StringBuilder announcements = new StringBuilder();
            mZmanimForAnnouncements = new ArrayList<>();//clear the list, it will be filled again in the getShortZmanim method
            mListViews[i].setAdapter(new ArrayAdapter<>(this, R.layout.zman_list_view, getShortZmanim()));//E.G. "Sunrise: 5:45 AM, Sunset: 8:30 PM, etc."
            if (!mZmanimForAnnouncements.isEmpty()) {
                for (String zman : mZmanimForAnnouncements) {
                    announcements.append(zman).append("\n");
                }
            }
            announcements.append(getAnnouncements());
            weeklyInfo.get(i)[1].setText(announcements.toString());//E.G. "Yom Tov, Yom Kippur, etc."
            weeklyInfo.get(i)[2].setText(mJewishDateInfo.getJewishDayOfWeek());//E.G. "יום ראשון"
            weeklyInfo.get(i)[3].setText(formatHebrewNumber(mJewishDateInfo.getJewishCalendar().getJewishDayOfMonth()));//E.G. "א"
            weeklyInfo.get(i)[4].setText(mROZmanimCalendar.getCalendar().getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()));//E.G. "Sun"
            weeklyInfo.get(i)[5].setText(String.valueOf(mROZmanimCalendar.getCalendar().get(Calendar.DAY_OF_MONTH)));//E.G. "6"
            if (i != 6) {
                mROZmanimCalendar.getCalendar().add(Calendar.DATE, 1);
                mJewishDateInfo.setCalendar(mROZmanimCalendar.getCalendar());
            }
        }
        if (month != null && !month.equals(mROZmanimCalendar.getCalendar().getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()))) {
            month += " - " + mROZmanimCalendar.getCalendar().getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
        }
        if (!year.equals(String.valueOf(mROZmanimCalendar.getCalendar().get(Calendar.YEAR)))) {
            year += " / " + mROZmanimCalendar.getCalendar().get(Calendar.YEAR);
        }
        if (!hebrewMonth.equals(hebrewDateFormatter.formatMonth(mJewishDateInfo.getJewishCalendar())
                        .replace("Tishrei", "Tishri")
                        .replace("Teves", "Tevet"))) {
            hebrewMonth += " - " + hebrewDateFormatter.formatMonth(mJewishDateInfo.getJewishCalendar())
                    .replace("Tishrei", "Tishri")
                    .replace("Teves", "Tevet");
        }
        if (!hebrewYear.equals(String.valueOf(mJewishDateInfo.getJewishCalendar().getJewishYear()))) {
            hebrewYear += " / " + mJewishDateInfo.getJewishCalendar().getJewishYear();
        }
        if (!masechta.equals(YomiCalculator.getDafYomiBavli(mJewishDateInfo.getJewishCalendar()).getMasechta())) {
            masechta += " " + daf + " - " + YomiCalculator.getDafYomiBavli(mJewishDateInfo.getJewishCalendar()).getMasechta() + " " +
                    formatHebrewNumber(YomiCalculator.getDafYomiBavli(mJewishDateInfo.getJewishCalendar()).getDaf());
        } else {
            masechta += " " + daf + " - " + formatHebrewNumber(YomiCalculator.getDafYomiBavli(mJewishDateInfo.getJewishCalendar()).getDaf());
        }
        if (!yerushalmiMasechta.equals(YerushalmiYomiCalculator.getDafYomiYerushalmi(mJewishDateInfo.getJewishCalendar()).getMasechta())) {
            if (YerushalmiYomiCalculator.getDafYomiYerushalmi(mJewishDateInfo.getJewishCalendar()).getDaf() == 0) {
                mROZmanimCalendar.getCalendar().add(Calendar.DATE, -1);
                mJewishDateInfo.setCalendar(mROZmanimCalendar.getCalendar());
            }
            yerushalmiMasechta += " " + yerushalmiDaf + " - " + YerushalmiYomiCalculator.getDafYomiYerushalmi(mJewishDateInfo.getJewishCalendar()).getMasechta() + " " +
                    formatHebrewNumber(YerushalmiYomiCalculator.getDafYomiYerushalmi(mJewishDateInfo.getJewishCalendar()).getDaf());
        } else {
            yerushalmiMasechta += " " + yerushalmiDaf + " - " + formatHebrewNumber(YerushalmiYomiCalculator.getDafYomiYerushalmi(mJewishDateInfo.getJewishCalendar()).getDaf());
        }
        String dafs = "Daf Yomi: " + masechta + "       Yerushalmi Yomi: " + yerushalmiMasechta;
        String monthYear = month + " " + year;
        mEnglishMonthYear.setText(monthYear);
        mLocationName.setText(sCurrentLocationName);
        String hebrewMonthYear = hebrewMonth + " " + hebrewYear;
        mHebrewMonthYear.setText(hebrewMonthYear);
        mWeeklyDafs.setText(dafs);
        mWeeklyParsha.setText(mJewishDateInfo.getThisWeeksParsha());
        mROZmanimCalendar.getCalendar().setTimeInMillis(backupCal.getTimeInMillis());
        mJewishDateInfo.setCalendar(backupCal);
        mCurrentDateShown = backupCal;
    }

    private String[] getShortZmanim() {
        List<ZmanListEntry> zmanim = new ArrayList<>();
        addZmanim(zmanim, true);
        DateFormat zmanimFormat;
        if (mSettingsPreferences.getBoolean("ShowSeconds", false)) {
            zmanimFormat = new SimpleDateFormat("h:mm:ss aa", Locale.getDefault());
        } else {
            zmanimFormat = new SimpleDateFormat("h:mm aa", Locale.getDefault());
        }
        zmanimFormat.setTimeZone(TimeZone.getTimeZone(sCurrentTimeZoneID));

        List<ZmanListEntry> zmansToRemove = new ArrayList<>();
        if (mIsZmanimInHebrew) {
            for (ZmanListEntry zman : zmanim) {
                if (zman.isNoteworthyZman()) {
                    if (zman.isRTZman() && PreferenceManager.getDefaultSharedPreferences(this).getBoolean("RoundUpRT", false)) {
                        DateFormat rtFormat = new SimpleDateFormat("h:mm aa", Locale.getDefault());
                        rtFormat.setTimeZone(TimeZone.getTimeZone(sCurrentTimeZoneID));
                        mZmanimForAnnouncements.add(rtFormat.format(zman.getZman()) + ":" + zman.getTitle().replaceAll("\\(.*\\)", "").trim());
                    } else {
                        mZmanimForAnnouncements.add(zmanimFormat.format(zman.getZman()) + ":" + zman.getTitle().replaceAll("\\(.*\\)", "").trim());
                    }
                    zmansToRemove.add(zman);
                }
            }
        } else {
            for (ZmanListEntry zman : zmanim) {
                if (zman.isNoteworthyZman()) {
                    if (zman.isRTZman() && PreferenceManager.getDefaultSharedPreferences(this).getBoolean("RoundUpRT", false)) {
                        DateFormat rtFormat = new SimpleDateFormat("h:mm aa", Locale.getDefault());
                        rtFormat.setTimeZone(TimeZone.getTimeZone(sCurrentTimeZoneID));
                        mZmanimForAnnouncements.add(zman.getTitle().replaceAll("\\(.*\\)", "").trim() + ":" + rtFormat.format(zman.getZman()));
                    } else {
                        mZmanimForAnnouncements.add(zman.getTitle().replaceAll("\\(.*\\)", "").trim() + ":" + zmanimFormat.format(zman.getZman()));
                    }
                    zmansToRemove.add(zman);
                }
            }
        }
        zmanim.removeAll(zmansToRemove);

        String[] shortZmanim = new String[zmanim.size()];
        if (mIsZmanimInHebrew) {
            for (ZmanListEntry zman : zmanim) {
                if (zman.isRTZman() && PreferenceManager.getDefaultSharedPreferences(this).getBoolean("RoundUpRT", false)) {
                    DateFormat rtFormat = new SimpleDateFormat("h:mm aa", Locale.getDefault());
                    rtFormat.setTimeZone(TimeZone.getTimeZone(sCurrentTimeZoneID));
                    shortZmanim[zmanim.indexOf(zman)] = rtFormat.format(zman.getZman()) + ":" + zman.getTitle();
                } else {
                    shortZmanim[zmanim.indexOf(zman)] = zmanimFormat.format(zman.getZman()) + ":" + zman.getTitle()
                            .replace("סוף זמן", "");
                }
            }
        } else {
            for (ZmanListEntry zman : zmanim) {
                if (zman.isRTZman() && PreferenceManager.getDefaultSharedPreferences(this).getBoolean("RoundUpRT", false)) {
                    DateFormat rtFormat = new SimpleDateFormat("h:mm aa", Locale.getDefault());
                    rtFormat.setTimeZone(TimeZone.getTimeZone(sCurrentTimeZoneID));
                    shortZmanim[zmanim.indexOf(zman)] = zman.getTitle() + ":" + rtFormat.format(zman.getZman());
                } else {
                    shortZmanim[zmanim.indexOf(zman)] = zman.getTitle()
                            .replace("Earliest ","")
                            .replace("Sof Zman ", "")
                            .replace("Hacochavim", "")
                            .replace("Latest ", "")
                            + ":" + zmanimFormat.format(zman.getZman());
                }
            }
        }
        return shortZmanim;
    }

    private void addZmanim(List<ZmanListEntry> zmanim, boolean isForWeeklyZmanim) {
        zmanim.add(new ZmanListEntry(getAlotString(), mROZmanimCalendar.getAlos72Zmanis(), true));
        zmanim.add(new ZmanListEntry(getTalitTefilinString(), mROZmanimCalendar.getEarliestTalitTefilin(), true));
        if (mSettingsPreferences.getBoolean("ShowElevatedSunrise", false)) {
            zmanim.add(new ZmanListEntry(getHaNetzString() + " " + getElevatedString(), mROZmanimCalendar.getSunrise(), true));
        }
        if (mROZmanimCalendar.getHaNetz() != null && !mSharedPreferences.getBoolean("showMishorSunrise" + sCurrentLocationName, true)) {
            zmanim.add(new ZmanListEntry(getHaNetzString(), mROZmanimCalendar.getHaNetz(), true));
        } else {
            zmanim.add(new ZmanListEntry(getHaNetzString() + " (" + getMishorString() + ")", mROZmanimCalendar.getSeaLevelSunrise(), true));
        }
        if (mROZmanimCalendar.getHaNetz() != null &&
                !mSharedPreferences.getBoolean("showMishorSunrise" + sCurrentLocationName, true) &&
                mSettingsPreferences.getBoolean("ShowMishorAlways", false)) {
            zmanim.add(new ZmanListEntry(getHaNetzString() + " (" + getMishorString() + ")", mROZmanimCalendar.getSeaLevelSunrise(), true));
        }
        zmanim.add(new ZmanListEntry(getShmaMgaString(), mROZmanimCalendar.getSofZmanShmaMGA72MinutesZmanis(), true));
        zmanim.add(new ZmanListEntry(getShmaGraString(), mROZmanimCalendar.getSofZmanShmaGRA(), true));
        if (mJewishDateInfo.getJewishCalendar().getYomTovIndex() == JewishCalendar.EREV_PESACH) {
            ZmanListEntry zman = new ZmanListEntry(getAchilatChametzString(), mROZmanimCalendar.getSofZmanTfilaMGA72MinutesZmanis(), true);
            zman.setNoteworthyZman(true);
            zmanim.add(zman);
            zmanim.add(new ZmanListEntry(getBrachotShmaString(), mROZmanimCalendar.getSofZmanTfilaGRA(), true));
            zman = new ZmanListEntry(getBiurChametzString(), mROZmanimCalendar.getSofZmanBiurChametzMGA(), true);
            zman.setNoteworthyZman(true);
            zmanim.add(zman);
        } else {
            zmanim.add(new ZmanListEntry(getBrachotShmaString(), mROZmanimCalendar.getSofZmanTfilaGRA(), true));
        }
        zmanim.add(new ZmanListEntry(getChatzotString(), mROZmanimCalendar.getChatzot(), true));
        zmanim.add(new ZmanListEntry(getMinchaGedolaString(), mROZmanimCalendar.getMinchaGedolaGreaterThan30(), true));
        zmanim.add(new ZmanListEntry(getMinchaKetanaString(), mROZmanimCalendar.getMinchaKetana(), true));
        zmanim.add(new ZmanListEntry(getPlagHaminchaString(), mROZmanimCalendar.getPlagHamincha(), true));
        if ((mJewishDateInfo.getJewishCalendar().hasCandleLighting() &&
                !mJewishDateInfo.getJewishCalendar().isAssurBemelacha()) ||
                mJewishDateInfo.getJewishCalendar().getGregorianCalendar().get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
            ZmanListEntry candleLightingZman = new ZmanListEntry(
                    getCandleLightingString() + " (" + (int) mROZmanimCalendar.getCandleLightingOffset() + ")",
                    mROZmanimCalendar.getCandleLighting(),
                    true);
            candleLightingZman.setNoteworthyZman(true);
            zmanim.add(candleLightingZman);
        }
        if (mSettingsPreferences.getBoolean("ShowWhenShabbatChagEnds", false) && !isForWeeklyZmanim) {
            if (mJewishDateInfo.getJewishCalendar().isTomorrowShabbosOrYomTov()) {
                mROZmanimCalendar.getCalendar().add(Calendar.DATE, 1);
                mJewishDateInfo.setCalendar(mROZmanimCalendar.getCalendar());
                if (!mJewishDateInfo.getJewishCalendar().isTomorrowShabbosOrYomTov()) {
                    Set<String> stringSet = mSettingsPreferences.getStringSet("displayRTOrShabbatRegTime", null);
                    if (stringSet != null) {
                        if (stringSet.contains("Show Regular Minutes")) {
                            zmanim.add(new ZmanListEntry(getTzaitString() + getShabbatAndOrChag() + getEndsString() + getMacharString()
                                    + "(" + (int) mROZmanimCalendar.getAteretTorahSunsetOffset() + ")", mROZmanimCalendar.getTzaisAteretTorah(), true));
                        }
                        if (stringSet.contains("Show Rabbeinu Tam")) {
                            if (mSettingsPreferences.getBoolean("RoundUpRT", true)) {
                                ZmanListEntry rt = new ZmanListEntry(getRTString() + getMacharString(), addMinuteToZman(mROZmanimCalendar.getTzais72Zmanis()), true);
                                rt.setRTZman(true);
                                zmanim.add(rt);
                            } else {
                                ZmanListEntry rt = new ZmanListEntry(getRTString() + getMacharString(), mROZmanimCalendar.getTzais72Zmanis(), true);
                                rt.setRTZman(true);
                                zmanim.add(rt);
                            }
                        }
                    }
                }
                mROZmanimCalendar.getCalendar().add(Calendar.DATE, -1);
                mJewishDateInfo.setCalendar(mROZmanimCalendar.getCalendar());
            }
        }
        zmanim.add(new ZmanListEntry(getSunsetString(), mROZmanimCalendar.getSunset(), true));
        zmanim.add(new ZmanListEntry(getTzaitHacochavimString(), mROZmanimCalendar.getTzeit(), true));
        if (mJewishDateInfo.getJewishCalendar().hasCandleLighting() &&
                mJewishDateInfo.getJewishCalendar().isAssurBemelacha()) {
            if (mJewishDateInfo.getJewishCalendar().getGregorianCalendar().get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) {
                zmanim.add(new ZmanListEntry(getCandleLightingString(), mROZmanimCalendar.getTzeit(), true));
            }
        }
        if (mJewishDateInfo.getJewishCalendar().isTaanis() && mJewishDateInfo.getJewishCalendar().getYomTovIndex() != JewishCalendar.YOM_KIPPUR) {
            ZmanListEntry fastEnds = new ZmanListEntry(getTzaitString() + getTaanitString() + getEndsString(), mROZmanimCalendar.getTzaitTaanit(), true);
            fastEnds.setNoteworthyZman(true);
            zmanim.add(fastEnds);
            fastEnds = new ZmanListEntry(getTzaitString() + getTaanitString() + getEndsString() + " " + getLChumraString(), mROZmanimCalendar.getTzaitTaanitLChumra(), true);
            fastEnds.setNoteworthyZman(true);
            zmanim.add(fastEnds);
        }
        if (mJewishDateInfo.getJewishCalendar().isAssurBemelacha() && !mJewishDateInfo.getJewishCalendar().hasCandleLighting()) {
            ZmanListEntry endShabbat = new ZmanListEntry(getTzaitString() + getShabbatAndOrChag() + getEndsString()
                    + " (" + (int) mROZmanimCalendar.getAteretTorahSunsetOffset() + ")", mROZmanimCalendar.getTzaisAteretTorah(), true);
            endShabbat.setNoteworthyZman(true);
            zmanim.add(endShabbat);
            if (mSettingsPreferences.getBoolean("RoundUpRT", true)) {
                ZmanListEntry rt = new ZmanListEntry(getRTString(), addMinuteToZman(mROZmanimCalendar.getTzais72Zmanis()), true);
                rt.setRTZman(true);
                rt.setNoteworthyZman(true);
                zmanim.add(rt);
            } else {
                ZmanListEntry rt = new ZmanListEntry(getRTString(), mROZmanimCalendar.getTzais72Zmanis(), true);
                rt.setRTZman(true);
                rt.setNoteworthyZman(true);
                zmanim.add(rt);
            }
        }
        if (mSettingsPreferences.getBoolean("AlwaysShowRT", false)) {
            if (!(mJewishDateInfo.getJewishCalendar().isAssurBemelacha() && !mJewishDateInfo.getJewishCalendar().hasCandleLighting())) {//if we want to always show the zman for RT, we can just NOT the previous cases where we do show it
                if (mSettingsPreferences.getBoolean("RoundUpRT", true)) {
                    ZmanListEntry rt = new ZmanListEntry(getRTString(), addMinuteToZman(mROZmanimCalendar.getTzais72Zmanis()), true);
                    rt.setRTZman(true);
                    zmanim.add(rt);
                } else {
                    ZmanListEntry rt = new ZmanListEntry(getRTString(), mROZmanimCalendar.getTzais72Zmanis(), true);
                    rt.setRTZman(true);
                    zmanim.add(rt);
                }
            }
        }
        zmanim.add(new ZmanListEntry(getChatzotLaylaString(), mROZmanimCalendar.getSolarMidnight(), true));
    }

    /**
     * This is a simple convenience method to add a minute to a date object. If the date is not null,
     * it will return the same date with a minute added to it. Otherwise, if the date is null, it will return null.
     * @param date the date object to add a minute to
     * @return the given date a minute ahead if not null
     */
    private Date addMinuteToZman(Date date) {
        if (date == null) {
            return null;
        }
        return new Date(date.getTime() + 60_000);
    }

    /**
     * This is a simple convenience method to check if the current date is on shabbat or yom tov or both and return the correct string.
     * @return a string that says whether it is shabbat and chag or just shabbat or just chag (in Hebrew or English)
     */
    private String getShabbatAndOrChag() {
        if (mSharedPreferences.getBoolean("isZmanimInHebrew", false)) {
            if (mJewishDateInfo.getJewishCalendar().isYomTovAssurBemelacha() &&
                    mJewishDateInfo.getJewishCalendar().getGregorianCalendar().get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
                return "\u05E9\u05D1\u05EA/\u05D7\u05D2";
            } else if (mJewishDateInfo.getJewishCalendar().getGregorianCalendar().get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
                return "\u05E9\u05D1\u05EA";
            } else {
                return "\u05D7\u05D2";
            }
        } else {
            if (mJewishDateInfo.getJewishCalendar().isYomTovAssurBemelacha() &&
                    mJewishDateInfo.getJewishCalendar().getGregorianCalendar().get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
                return "Shabbat/Chag";
            } else if (mJewishDateInfo.getJewishCalendar().getGregorianCalendar().get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
                return "Shabbat";
            } else {
                return "Chag";
            }
        }
    }

    /**
     * This method will check if the tekufa happens within the next 48 hours and it will add the tekufa to the list passed in if it happens
     * on the current date.
     * @param zmanimFormat the format to use for the zmanim
     * @param zmanim the list of zmanim to add to
     * @param shortStyle if the tekufa should be added as Tekufa NAME : TIME or Tekufa NAME is today at TIME
     */
    private void addTekufaTime(DateFormat zmanimFormat, List<ZmanListEntry> zmanim, boolean shortStyle) {
        mCurrentDateShown.add(Calendar.DATE,1);//check next day for tekufa, because the tekufa time can go back a day
        mJewishDateInfo.setCalendar(mCurrentDateShown);
        mCurrentDateShown.add(Calendar.DATE,-1);
        if (mJewishDateInfo.getJewishCalendar().getTekufa() != null &&
                DateUtils.isSameDay(mCurrentDateShown.getTime(), mJewishDateInfo.getJewishCalendar().getTekufaAsDate())) {
            if (shortStyle) {
                zmanim.add(new ZmanListEntry("Tekufa " + mJewishDateInfo.getJewishCalendar().getTekufaName() + " : " +
                        zmanimFormat.format(mJewishDateInfo.getJewishCalendar().getTekufaAsDate())));
            } else {
                zmanim.add(new ZmanListEntry("Tekufa " + mJewishDateInfo.getJewishCalendar().getTekufaName() + " is today at " +
                        zmanimFormat.format(mJewishDateInfo.getJewishCalendar().getTekufaAsDate())));
            }
        }
        mJewishDateInfo.setCalendar(mCurrentDateShown);//reset

        //else the tekufa time is on the same day as the current date, so we can add it normally
        if (mJewishDateInfo.getJewishCalendar().getTekufa() != null &&
                DateUtils.isSameDay(mCurrentDateShown.getTime(), mJewishDateInfo.getJewishCalendar().getTekufaAsDate())) {
            if (shortStyle) {
                zmanim.add(new ZmanListEntry("Tekufa " + mJewishDateInfo.getJewishCalendar().getTekufaName() + " : " +
                        zmanimFormat.format(mJewishDateInfo.getJewishCalendar().getTekufaAsDate())));
            } else {
                zmanim.add(new ZmanListEntry("Tekufa " + mJewishDateInfo.getJewishCalendar().getTekufaName() + " is today at " +
                        zmanimFormat.format(mJewishDateInfo.getJewishCalendar().getTekufaAsDate())));
            }
        }
    }

    /**
     * This method checks if the user has already setup the elevation and visible sunrise from the last time he started
     * the app. If he has setup the elevation and visible sunrise, then it checks if the user is in the
     * same city as the last time he setup the app based on the getLocationAsName method. If the user is in the same city,
     * all is good. If the user is in another city, we create an AlertDialog to warn the user that the elevation data
     * and visible sunrise data are not accurate.
     *
     * @see #initAlertDialog()
     * @see LocationResolver#getLocationAsName()
     */
    private void getAndConfirmLastElevationAndVisibleSunriseData() {
        String lastLocation = mSharedPreferences.getString("lastLocation", "");

        String message ="The elevation and visible sunrise data change depending on the city you are in. " +
                        "Therefore, it is recommended that you update your elevation and visible sunrise " +
                        "data according to your current location." + "\n\n" +
                        "Last Location: " + lastLocation + "\n" +
                        "Current Location: " + sCurrentLocationName + "\n\n" +
                        "Would you like to rerun the setup now?";

        try {//TODO this needs to be removed but cannot be removed for now because it is needed for people who have setup the app before we changed data types
            if (sCurrentLocationName.contains("Lat:") && sCurrentLocationName.contains("Long:")) {
                sUserIsOffline = true;
                mElevation = Double.parseDouble(mSharedPreferences.getString("elevation" + mSharedPreferences.getString("name", ""), "0"));//lastKnownLocation
            } else {//user is online
                mElevation = Double.parseDouble(mSharedPreferences.getString("elevation" + sCurrentLocationName, "0"));//get the last value of the current location or 0 if it doesn't exist
            }
        } catch (Exception e) {
            try {//legacy
                mElevation = mSharedPreferences.getFloat("elevation", 0);
            } catch (Exception e1) {
                mElevation = 0;
                e1.printStackTrace();
            }
        }

        if (mSharedPreferences.getBoolean("askagain", true)) {//only prompt user if he has not asked to be left alone
            if (!lastLocation.isEmpty() && !sCurrentLocationName.isEmpty() && //location name should never be empty. It either has the name or it has Lat: and Long:
                    !lastLocation.equals(sCurrentLocationName) &&
                    !sUserIsOffline) {//don't ask if the user is offline

                if (!mSharedPreferences.getBoolean("isElevationSetup" + sCurrentLocationName, false)) {//user should update his elevation in another city
                    mAlertDialog.setMessage(message);
                    mAlertDialog.show();
                } else {//if the user is in the same place, then we just need to check if his tables need to be updated
                    seeIfTablesNeedToBeUpdated(false);
                }

            }
        } else {//if the user has asked to be left alone for elevation, then we just need to check if his tables need to be updated
            seeIfTablesNeedToBeUpdated(false);
        }
    }

    /**
     * This method initializes the AlertDialog that will be shown to the user if the user is in another city and he has setup the app before.
     * The AlertDialog will have two buttons: "Yes" and "No". If the user clicks "Yes", then the user will be taken to the
     * elevation and visible sunrise setup activity. If the user clicks "No", then the user will be taken to the main activity.
     * There is also a "Do not ask again" button that will stop the user from being prompted again.
     * @see #getAndConfirmLastElevationAndVisibleSunriseData()
     */
    private void initAlertDialog() {
        mAlertDialog = new AlertDialog.Builder(this)
                .setTitle("You are not in the same city as the last time that you " +
                        "setup the app!")
                .setPositiveButton("Yes", (dialogInterface, i) ->
                        mSetupLauncher.launch(new Intent(this, SetupChooserActivity.class)
                                .putExtra("fromMenu",true)))
                .setNegativeButton("No", (dialogInterface, i) -> Toast.makeText(
                        this, "Using mishor/sea level values", Toast.LENGTH_LONG)
                        .show())
                .setNeutralButton("Do not ask again", (dialogInterface, i) -> {
                    mSharedPreferences.edit().putBoolean("askagain", false).apply();
                    Toast.makeText(this, "Your current elevation is: " + mElevation, Toast.LENGTH_LONG)
                            .show();
                }).create();
    }

    /**
     * This method will create a new AlertDialog that asks the user to use their location and it
     * will also give the option to enter an address/zipcode through the EditText field.
     */
    private void createZipcodeDialog() {
        final EditText input = new EditText(this);
        input.setGravity(Gravity.CENTER_HORIZONTAL);
        new AlertDialog.Builder(this)
                .setTitle("Search for a place")
                .setMessage("WARNING! Zmanim will be based on your approximate area and will not be accurate! Using an address/zipcode will give " +
                        "you zmanim based on approximately where you are. For more accurate zmanim, please allow the app to see your location.")
                .setView(input)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    if (input.getText().toString().isEmpty()) {
                        Toast.makeText(this, "Please enter something", Toast.LENGTH_SHORT).show();
                        createZipcodeDialog();
                    } else {
                        SharedPreferences.Editor editor = mSharedPreferences.edit();
                        editor.putBoolean("useZipcode", true).apply();
                        editor.putString("Zipcode", input.getText().toString()).apply();
                        mLocationResolver = new LocationResolver(this, this);
                        mLocationResolver.getLatitudeAndLongitudeFromSearchQuery();
                        if (mSharedPreferences.getBoolean("isElevationSetup" + sCurrentLocationName, true)) {
                            mLocationResolver.start();
                            try {
                                mLocationResolver.join();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        if (!mInitialized) {
                            initMainView();
                        } else {
                            mLocationResolver.setTimeZoneID();
                            getAndConfirmLastElevationAndVisibleSunriseData();
                            instantiateZmanimCalendar();
                            if (mSharedPreferences.getBoolean("weeklyMode", false)) {
                                updateWeeklyZmanim();
                            } else {
                                mMainRecyclerView.setAdapter(new ZmanAdapter(this, getZmanimList()));
                            }
                        }
                    }
                })
                .setNeutralButton("Use location", (dialog, which) -> {
                    SharedPreferences.Editor editor = mSharedPreferences.edit();
                    editor.putBoolean("useZipcode", false).apply();
                    mLocationResolver = new LocationResolver(this, this);
                    mLocationResolver.acquireLatitudeAndLongitude();
                    mLocationResolver.setTimeZoneID();
                    if (mSharedPreferences.getBoolean("isElevationSetup" + sCurrentLocationName, true)) {
                        mLocationResolver.start();
                        try {
                            mLocationResolver.join();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    getAndConfirmLastElevationAndVisibleSunriseData();
                    instantiateZmanimCalendar();
                    if (mSharedPreferences.getBoolean("weeklyMode", false)) {
                        updateWeeklyZmanim();
                    } else {
                        mMainRecyclerView.setAdapter(new ZmanAdapter(this, getZmanimList()));
                    }
                })
                .create()
                .show();
    }

    @Override
    public void onBackPressed() {
        if (!mBackHasBeenPressed) {
            mBackHasBeenPressed = true;
            Toast.makeText(this, "Press back again to close the app", Toast.LENGTH_SHORT).show();
            return;
        }
        finish();
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.weekly_mode).setChecked(mSharedPreferences.getBoolean("weeklyMode", false));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.refresh) {
            if (mLocationResolver == null) {
                mLocationResolver = new LocationResolver(this, this);
            }
            mLocationResolver.acquireLatitudeAndLongitude();
            mLocationResolver.setTimeZoneID();
            if (mCurrentDateShown != null
                    && mJewishDateInfo != null
                    && mROZmanimCalendar != null
                    && mMainRecyclerView != null) {// Some users were getting a crash here, so I added this check.
                mCurrentDateShown.setTime(new Date());
                mJewishDateInfo.setCalendar(new GregorianCalendar());
                mROZmanimCalendar.setCalendar(new GregorianCalendar());
                if (mSharedPreferences.getBoolean("weeklyMode", false)) {
                    updateWeeklyZmanim();
                } else {
                    mMainRecyclerView.setAdapter(new ZmanAdapter(this, getZmanimList()));
                }
                getAndConfirmLastElevationAndVisibleSunriseData();
            }
            return true;
        } else if (id == R.id.enterZipcode) {
            createZipcodeDialog();
            return true;
        } else if (id == R.id.shabbat_mode) {
            if (!sShabbatMode && mJewishDateInfo != null && mROZmanimCalendar != null && mMainRecyclerView != null) {
                mCurrentDateShown.setTime(new Date());
                mJewishDateInfo.setCalendar(new GregorianCalendar());
                mROZmanimCalendar.setCalendar(new GregorianCalendar());
                if (mSharedPreferences.getBoolean("weeklyMode", false)) {
                    updateWeeklyZmanim();
                } else {
                    mMainRecyclerView.setAdapter(new ZmanAdapter(this, getZmanimList()));
                }
                startShabbatMode();
                item.setChecked(true);
            } else {
                endShabbatMode();
                item.setChecked(false);
            }
            return true;
        } else if (id == R.id.weekly_mode) {
            mSharedPreferences.edit().putBoolean("weeklyMode", !mSharedPreferences.getBoolean("weeklyMode", false)).apply();
            item.setChecked(mSharedPreferences.getBoolean("weeklyMode", false));//save the state of the menu item
            if (mMainRecyclerView == null) {
                return true;// This is to prevent a crash
            }
            if (mSharedPreferences.getBoolean("weeklyMode", false)) {
                showWeeklyTextViews();
                updateWeeklyZmanim();
            } else {
                hideWeeklyTextViews();
                mMainRecyclerView.setAdapter(new ZmanAdapter(this, getZmanimList()));
            }
            return true;
        } else if (id == R.id.molad) {
            startActivity(new Intent(this, MoladActivity.class));
            return true;
        } else if (id == R.id.setupChooser) {
            mSetupLauncher.launch(new Intent(this, SetupChooserActivity.class)
                    .putExtra("fromMenu",true));
            return true;
        } else if (id == R.id.fullSetup) {
            mSetupLauncher.launch(new Intent(this, FullSetupActivity.class)
                    .putExtra("fromMenu",true));
            return true;
        } else if (id == R.id.settings) {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            return true;
        } else if (id == R.id.help) {
            new AlertDialog.Builder(this, R.style.Theme_AppCompat_DayNight)
                    .setTitle("Help using this app:")
                    .setPositiveButton("ok", null)
                    .setMessage(R.string.helper_text)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * This class is used to change the date in the main activity if the user swipes left or right.
     */
    private class ZmanimGestureListener extends GestureDetector.SimpleOnGestureListener {

        private static final int SWIPE_MIN_DISTANCE = 180;

        @Override
        public boolean onFling(MotionEvent startMotionEvent, MotionEvent endMotionEvent, float xVelocity, float yVelocity) {

            if ( ( startMotionEvent == null ) || ( endMotionEvent == null ) ) {
                return false;
            }

            float xDiff = startMotionEvent.getRawX() - endMotionEvent.getRawX();

            if (Math.abs(xDiff) < SWIPE_MIN_DISTANCE) {
                return false;
            }

            if (Math.abs(xVelocity) > Math.abs(yVelocity)) {
                if (xDiff > 0) {
                    mNextDate.performClick();
                } else {
                    mPreviousDate.performClick();
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }
    }

    private String getChatzotLaylaString() {
        if (mIsZmanimInHebrew) {
            return "חצות לילה";
        } else if (mIsZmanimEnglishTranslated) {
            return "Midnight";
        } else {
            return "Chatzot Layla";
        }
    }

    private String getLChumraString() {
        if (mIsZmanimInHebrew) {
            return "לחומרה";
        } else if (mIsZmanimEnglishTranslated) {
            return "(Stringent)";
        } else {
            return "L'Chumra";
        }
    }

    private String getTaanitString() {
        if (mIsZmanimInHebrew) {
            return "תענית";
        } else if (mIsZmanimEnglishTranslated) {
            return "Fast";
        } else {
            return "Taanit";
        }
    }

    private String getTzaitHacochavimString() {
        if (mIsZmanimInHebrew) {
            return "צאת הכוכבים";
        } else if (mIsZmanimEnglishTranslated) {
            return "Nightfall";
        } else {
            return "Tzait Hacochavim";
        }
    }

    private String getSunsetString() {
        if (mIsZmanimInHebrew) {
            return "שקיעה";
        } else if (mIsZmanimEnglishTranslated) {
            return "Sunset";
        } else {
            return "Shkia";
        }
    }

    private String getRTString() {
        if (mIsZmanimInHebrew) {
            return "רבינו תם";
        } else {
            return "Rabbeinu Tam";
        }
    }

    private String getMacharString() {
        if (mIsZmanimInHebrew) {
            return " (מחר) ";
        } else {
            return " (Tom) ";
        }
    }

    private String getEndsString() {
        if (mIsZmanimEnglishTranslated) {
            return " Ends";
        } else {
            return "";
        }
    }

    private String getTzaitString() {
        if (mIsZmanimInHebrew) {
            return "צאת ";
        } else if (!mIsZmanimEnglishTranslated) {
            return "Tzait ";
        } else {
            return "";//if we are translating to English, we don't want to show the word Tzait first, just {Zman} Ends
        }
    }

    private String getCandleLightingString() {
        if (mIsZmanimInHebrew) {
            return "הדלקת נרות";
        } else {
            return "Candle Lighting";
        }
    }

    private String getPlagHaminchaString() {
        if (mIsZmanimInHebrew) {
            return "פלג המנחה";
        } else {
            return "Plag HaMincha";
        }
    }

    private String getMinchaKetanaString() {
        if (mIsZmanimInHebrew) {
            return "מנחה קטנה";
        } else {
            return "Mincha Ketana";
        }
    }

    private String getMinchaGedolaString() {
        if (mIsZmanimInHebrew) {
            return "מנחה גדולה";
        } else {
            return "Mincha Gedola";
        }
    }

    private String getChatzotString() {
        if (mIsZmanimInHebrew) {
            return "חצות";
        } else if (mIsZmanimEnglishTranslated) {
            return "Mid-day";
        } else {
            return "Chatzot";
        }
    }

    private String getBiurChametzString() {
        if (mIsZmanimInHebrew) {
            return "סוף זמן ביעור חמץ";
        } else if (mIsZmanimEnglishTranslated) {
            return "Latest time to burn Chametz";
        } else {
            return "Sof Zman Biur Chametz";
        }
    }

    private String getBrachotShmaString() {
        if (mIsZmanimInHebrew) {
            return "סוף זמן ברכות שמע";
        } else if (mIsZmanimEnglishTranslated) {
            return "Latest Brachot Shma";
        } else {
            return "Sof Zman Brachot Shma";
        }
    }

    private String getAchilatChametzString() {
        if (mIsZmanimInHebrew) {
            return "סוף זמן אכילת חמץ";
        } else if (mIsZmanimEnglishTranslated) {
            return "Latest time to eat Chametz";
        } else {
            return "Sof Zman Achilat Chametz";
        }
    }

    private String getShmaGraString() {
        if (mIsZmanimInHebrew) {
            return "סוף זמן שמע גר\"א";
        } else if (mIsZmanimEnglishTranslated) {
            return "Latest Shma GR\"A";
        } else {
            return "Sof Zman Shma GR\"A";
        }
    }

    private String getShmaMgaString() {
        if (mIsZmanimInHebrew) {
            return "סוף זמן שמע מג\"א";
        } else if (mIsZmanimEnglishTranslated) {
            return "Latest Shma MG\"A";
        } else {
            return "Sof Zman Shma MG\"A";
        }
    }

    private String getMishorString() {
        if (mIsZmanimInHebrew) {
            return "מישור";
        } else if (mIsZmanimEnglishTranslated) {
            return "Sea Level";
        } else {
            return "Mishor";
        }
    }

    private String getElevatedString() {
        if (mIsZmanimInHebrew) {
            return "(גבוה)";
        } else {
            return "(Elevated)";
        }
    }

    private String getHaNetzString() {
        if (mIsZmanimInHebrew) {
            return "הנץ";
        } else if (mIsZmanimEnglishTranslated) {
            return "Sunrise";
        } else {
            return "HaNetz";
        }
    }

    private String getTalitTefilinString() {
        if (mIsZmanimInHebrew) {
            return "טלית ותפילין";
        } else {
            return "Earliest Talit/Tefilin";
        }
    }

    private String getAlotString() {
        if (mIsZmanimInHebrew) {
            return "עלות השחר";
        } else if (mIsZmanimEnglishTranslated) {
            return "Dawn";
        } else {
            return "Alot Hashachar";
        }
    }
}