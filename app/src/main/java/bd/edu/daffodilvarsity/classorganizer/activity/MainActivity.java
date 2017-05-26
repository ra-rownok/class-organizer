package bd.edu.daffodilvarsity.classorganizer.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.firebase.crash.FirebaseCrash;

import org.polaric.colorful.Colorful;
import org.polaric.colorful.ColorfulActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import bd.edu.daffodilvarsity.classorganizer.utils.DatabaseHelper;
import bd.edu.daffodilvarsity.classorganizer.data.DayData;
import bd.edu.daffodilvarsity.classorganizer.adapter.DayFragmentPagerAdapter;
import bd.edu.daffodilvarsity.classorganizer.utils.PrefManager;
import bd.edu.daffodilvarsity.classorganizer.R;
import bd.edu.daffodilvarsity.classorganizer.utils.RoutineLoader;

public class MainActivity extends ColorfulActivity implements NavigationView.OnNavigationItemSelectedListener {
    private PrefManager prefManager;
    private ArrayList<DayData> mDayData;
    private boolean onStart = false;
    private boolean onCreate = false;
    private RoutineLoader routineLoader;
    private boolean isActivityRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Colorful.applyTheme(this);
        setContentView(R.layout.activity_main);
        prefManager = new PrefManager(this);
        //Maintaining compatibility with previous versions
        prefManager.setCompat2point2();
        prefManager.deleteSnapshotDayData();

        routineLoader = new RoutineLoader(prefManager.getLevel(), prefManager.getTerm(), prefManager.getSection(), this, prefManager.getDept(), prefManager.getCampus(), prefManager.getProgram());

        //If there is a new routine, update
        if (prefManager.getSemester() != null && !prefManager.getSemester().equals(getResources().getString(R.string.current_semester))) {
            //We will add department checks later if there is a specific update
            upgradeRoutine();
        } else if (DatabaseHelper.DATABASE_VERSION > prefManager.getDatabaseVersion()) {
            boolean isNotUpdated = updateRoutine(true);
            if (isNotUpdated) {
                showSnackBar(this, "Error loading updated routine!");
                FirebaseCrash.report(new Exception("Error loading updated routine. Database version: "
                        + DatabaseHelper.DATABASE_VERSION
                        + "Section: " + prefManager.getSection()
                        + " Term: " + prefManager.getTerm()
                        + " Level: " + prefManager.getLevel()));
            }
        }

        if (prefManager.showSnack()) {
            showSnackBar(this, prefManager.getSnackData());
            prefManager.saveShowSnack(false);
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //If primary color and accent are same we are setting tab indicator to white
        if (Colorful.getThemeDelegate().getAccentColor() == Colorful.getThemeDelegate().getPrimaryColor()) {
            TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
            tabLayout.setSelectedTabIndicatorColor(getResources().getColor(android.R.color.white));
        }

        // Making navigation bar colored
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setNavigationBarColor(getResources().getColor(Colorful.getThemeDelegate().getPrimaryColor().getColorRes()));
        }

        //Setting drawer up
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        loadData();
        onCreate = true;
    }

    @Override
    public void onBackPressed() {
        if (isActivityRunning) {
            DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
            if (drawer.isDrawerOpen(GravityCompat.START)) {
                drawer.closeDrawer(GravityCompat.START);
            } else {
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.add_button) {
            Intent intent = new Intent(this, AddActivity.class);
            startActivity(intent);
        } else if (item.getItemId() == R.id.search_button_main) {
            Intent intent = new Intent(this, SearchActivity.class);
            startActivity(intent);
        }
        return true;
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_manage) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_share) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, getResources().getString(R.string.share_text_extra));
            startActivity(Intent.createChooser(shareIntent, getResources().getString(R.string.share_title)));
        } else if (id == R.id.nav_send) {
            composeEmail();
        }
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!onCreate) {
            loadData();
        }
        //won't run again on onResume
        onStart = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!onStart) {
            loadData();
        }
        if (prefManager.getReCreate()) {
            loadData();
            prefManager.saveReCreate(false);
        }
        isActivityRunning = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityRunning = false;
    }

    public void loadData() {
        //Load Data
        if (mDayData != null) {
            mDayData.clear();
        }

        mDayData = prefManager.getSavedDayData();

        if (mDayData != null) {

            // Find the view pager that will allow the user to swipe between fragments
            ViewPager viewPager = (ViewPager) findViewById(R.id.viewpager);
            // Create an adapter that knows which fragment should be shown on each page
            DayFragmentPagerAdapter adapter = new DayFragmentPagerAdapter(this, getSupportFragmentManager(), mDayData);
            // Set the adapter onto the view pager
            viewPager.setAdapter(adapter);
            //Setting current date and tab
            Calendar calendar = Calendar.getInstance();
            Date date = calendar.getTime();
            String currentDay = new SimpleDateFormat("EEEE", Locale.ENGLISH).format(date.getTime());
            for (int i = 0; i < adapter.getCount(); i++) {
                if (adapter.getPageTitle(i).toString().equalsIgnoreCase(currentDay)) {
                    viewPager.setCurrentItem(i);
                }
            }
            // Find the tab layout that shows the tabs
            TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
            // Connect the tab layout with the view pager. This will
            //   1. Update the tab layout when the view pager is swiped
            //   2. Update the view pager when a tab is selected
            //   3. Set the tab layout's tab names with the view pager's adapter's titles
            //      by calling onPageTitle()
            tabLayout.setupWithViewPager(viewPager);
            if (prefManager.showSnack()) {
                showSnackBar(this, prefManager.getSnackData());
                prefManager.saveShowSnack(false);
            }
        }
    }

    public void composeEmail() {
        String message = "Your suggestions: ";
        String subject = "Suggestions for DIU Class Organizer";
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:")); // only email apps should handle this
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{getResources().getString(R.string.auth_email)});
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    /*Method to display snackbar properly*/
    public void showSnackBar(Activity activity, String message) {
        View rootView = activity.getWindow().getDecorView().findViewById(android.R.id.content);
        Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).show();
    }

    private void upgradeRoutine() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Updated routine available!");
        builder.setMessage("The routine was updated as per " + getResources().getString(R.string.current_semester) + " semester.\n" +
                "Do you want to update your routine to the new one?\n" +
                "Note: Your level and term will automatically get updated based on current selection and your modifications will be reset. ");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int currentLevel = prefManager.getLevel();
                int currentTerm = prefManager.getTerm();
                if (currentTerm == 2) {
                    if (currentLevel < 3) {
                        currentLevel++;
                        prefManager.saveLevel(currentLevel);
                    }
                    currentTerm = 0;
                    prefManager.saveTerm(currentTerm);
                } else {
                    currentTerm++;
                    prefManager.saveTerm(currentTerm);
                }
                //Deleting modified data before loading new semester routine
                prefManager.resetModification(true, true, true, true);
                boolean loadCheck = updateRoutine(false);
                if (!loadCheck) {
                    prefManager.saveShowSnack(true);
                    prefManager.saveSnackData("Routine updated");
                    prefManager.saveSemester(getResources().getString(R.string.current_semester));
                    loadData();
                } else {
                    Toast.makeText(getApplicationContext(), "Error loading routine", Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private boolean updateRoutine(boolean personalRoutine) {
        prefManager.saveDatabaseVersion(DatabaseHelper.DATABASE_VERSION);
        routineLoader = new RoutineLoader(prefManager.getLevel(), prefManager.getTerm(), prefManager.getSection(), this, prefManager.getDept(), prefManager.getCampus(), prefManager.getProgram());
        ArrayList<DayData> updatedRoutine = routineLoader.loadRoutine(personalRoutine);
        if (updatedRoutine != null) {
            if (updatedRoutine.size() > 0) {
                prefManager.saveDayData(updatedRoutine);
                return false;
            }
        }
        return true;
    }
}