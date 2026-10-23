package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/**
 * Remote-friendly settings owned by the launcher. On custom-home: «الموقع», «الأرقام», «أوقات الإقامة» and apps per row
 * on a frosted panel, then the match sources (read-only); the upstream rows stay in the layout, hidden. Choices are
 * kept on the TV (CustomHomeConfig) and Home rebuilds when this screen closes (RESULT_OK).
 */
public final class LauncherSettingsActivity extends Activity {
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private TextView wallpaperChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView cityChoice;
    /** A choice changed here: Home rebuilds when this screen closes. */
    private boolean changed;
    private final TextView[] digitsChoices = new TextView[2];
    /** Minutes the «أوقات الإقامة» list offers for one prayer. */
    private static final int[] IQAMA_CHOICES = {5, 10, 15, 20, 25, 30, 35, 40, 45};
    private int columns;
    private int rowsBelowDock;
    private boolean animations;
    private boolean advancedEffects;
    /** «تحديث الأداة»: the newer release found by the last check, and whether a check or download is running. */
    private AppUpdate.Release update;
    private boolean updating;
    private TextView updateStatus;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        // The soft wallpaper behind the frosted panel, in place of the blur Android 11 does not have.
        ((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0).setBackground(SoftBackdrop.create(this));
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_SETTINGS)));
        cityChoice = findViewById(R.id.cityChoice);
        cityChoice.setOnClickListener(v -> showCityList());
        digitsChoices[0] = findViewById(R.id.digitsLatin);
        digitsChoices[1] = findViewById(R.id.digitsArabic);
        for (int i = 0; i < digitsChoices.length; i++) {
            final String value = i == 1 ? "arabic" : "latin";
            digitsChoices[i].setOnClickListener(v -> {
                CustomHomeConfig.saveDigits(this, value);
                changed();
                refresh();
            });
        }
        findViewById(R.id.iqamaChoice).setOnClickListener(v -> showIqamaList());
        findViewById(R.id.hiddenAppsChoice).setOnClickListener(v -> showHiddenApps());
        updateStatus = findViewById(R.id.updateStatus);
        updateStatus.setText(getString(R.string.update_current, AppUpdater.installedName(this)));
        findViewById(R.id.updateChoice).setOnClickListener(v -> onUpdate());
        LinearLayout sources = findViewById(R.id.matchSources);
        // Open-Meteo's data is CC BY 4.0: credited here, under the match sources.
        java.util.List<String> lines = new java.util.ArrayList<>(MatchFeed.sourceLines(this, CustomHomeConfig.load(this)));
        lines.add(getString(R.string.custom_weather_source));
        for (String line : lines) {
            TextView t = new TextView(this);
            t.setText(line);
            t.setTextColor(0x99FFFFFF);
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
            t.setPadding(0, Math.round(4 * getResources().getDisplayMetrics().density), 0, 0);
            sources.addView(t);
        }
        refresh();
        cityChoice.requestFocus();
    }

    /** First OK: ask GitHub for the latest release. When it is newer, the next OK downloads, verifies and installs it. */
    private void onUpdate() {
        if (updating) return;
        updating = true;
        String current = AppUpdater.installedName(this);
        AppUpdate.Release found = update;
        if (found == null) updateStatus.setText(getString(R.string.update_checking, current));
        new Thread(() -> {
            try {
                if (found == null) {
                    AppUpdate.Release r = AppUpdater.latest(this);
                    boolean newer = r.versionCode > AppUpdater.installedCode(this);
                    runOnUiThread(() -> {
                        update = newer ? r : null;
                        updateStatus.setText(getString(newer ? R.string.update_available : R.string.update_up_to_date,
                                current, r.versionName));
                    });
                } else {
                    java.io.File apk = AppUpdater.download(this, found, p -> runOnUiThread(() ->
                            updateStatus.setText(getString(R.string.update_downloading, found.versionName, p))));
                    runOnUiThread(() -> updateStatus.setText(R.string.update_confirm));
                    AppUpdater.install(this, apk);
                }
            } catch (AppUpdater.Failure f) {
                android.util.Log.w("TvHome.Update", f.getMessage() == null ? "update failed" : f.getMessage());
                runOnUiThread(() -> {
                    update = null;
                    updateStatus.setText(f.message);
                });
            } finally {
                runOnUiThread(() -> updating = false);
            }
        }).start();
    }

    private void changed() {
        changed = true;
        setResult(RESULT_OK);
    }

    /** «التطبيقات المخفية»: apps hidden with a tile's «إخفاء» (LibraryApps); OK on one shows it on Home again. */
    private void showHiddenApps() {
        List<String> pkgs = new java.util.ArrayList<>(LibraryApps.loadRemoved(this));
        if (pkgs.isEmpty()) {
            Toast.makeText(this, R.string.hidden_apps_none, Toast.LENGTH_SHORT).show();
            return;
        }
        android.content.pm.PackageManager pm = getPackageManager();
        List<String> labels = new java.util.ArrayList<>();
        for (String pkg : pkgs) {
            String label = pkg;
            try {
                label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
            } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
                // uninstalled since: its package name still lets it be cleared from the list
            }
            labels.add(label);
        }
        GlassList.show(this, cityChoice, labels, new boolean[labels.size()], i -> {
            if (LibraryApps.save(this, pkgs.get(i), true)) changed();
        });
    }

    /** «الموقع»: one Saudi city for the name top left, prayer times and weather. */
    private void showCityList() {
        String current = CustomHomeConfig.load(this).city.name;
        List<String> names = new java.util.ArrayList<>();
        boolean[] checked = new boolean[Cities.ALL.size()];
        for (int i = 0; i < Cities.ALL.size(); i++) {
            names.add(Cities.ALL.get(i).name);
            checked[i] = Cities.ALL.get(i).name.equals(current);
        }
        GlassList.show(this, cityChoice, names, checked, i -> {
            CustomHomeConfig.saveCity(this, Cities.ALL.get(i).name);
            changed();
            refresh();
        });
    }

    /** «أوقات الإقامة»: the five prayers with their minutes from adhan to iqama; OK on one opens its minutes. */
    private void showIqamaList() {
        CustomHomeConfig c = CustomHomeConfig.load(this);
        List<String> labels = new java.util.ArrayList<>();
        for (int i = 0; i < PrayerTimes.NAMES_AR.length; i++) {
            labels.add(digitsLike(c, getString(R.string.custom_iqama_row, PrayerTimes.NAMES_AR[i], ArabicText.minutes(c.iqamaMinutes[i]))));
        }
        GlassList.show(this, cityChoice, labels, new boolean[labels.size()], this::showIqamaMinutes);
    }

    private void showIqamaMinutes(int prayer) {
        CustomHomeConfig c = CustomHomeConfig.load(this);
        List<String> labels = new java.util.ArrayList<>();
        boolean[] checked = new boolean[IQAMA_CHOICES.length];
        for (int i = 0; i < IQAMA_CHOICES.length; i++) {
            labels.add(digitsLike(c, ArabicText.minutes(IQAMA_CHOICES[i])));
            checked[i] = IQAMA_CHOICES[i] == c.iqamaMinutes[prayer];
        }
        GlassList.show(this, cityChoice, labels, checked, i -> {
            CustomHomeConfig.saveIqama(this, prayer, IQAMA_CHOICES[i]);
            changed();
        });
    }

    /** Menu text with the view's digits («الأرقام»): ٠١٢٣ when that is the choice. */
    private static String digitsLike(CustomHomeConfig c, String text) {
        return "arabic".equals(c.digits) ? NumberStyle.toArabic(text) : text;
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        CustomHomeConfig home = CustomHomeConfig.load(this);
        cityChoice.setText(getString(R.string.custom_location) + ": " + home.city.name);
        digitsChoices[0].setSelected(!"arabic".equals(home.digits));
        digitsChoices[1].setSelected("arabic".equals(home.digits));
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
