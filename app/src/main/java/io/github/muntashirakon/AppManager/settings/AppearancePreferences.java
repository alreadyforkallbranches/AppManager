// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.transition.MaterialSharedAxis;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;
import io.github.muntashirakon.dialog.SearchableFlagsDialogBuilder;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;

public class AppearancePreferences extends PreferenceFragment {
    private static final List<Integer> THEME_CONST = Arrays.asList(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES);
    private static final List<Integer> LAYOUT_ORIENTATION_CONST = Arrays.asList(
            View.LAYOUT_DIRECTION_LOCALE,
            View.LAYOUT_DIRECTION_LTR,
            View.LAYOUT_DIRECTION_RTL);

    private int currentTheme;
    private int currentLayoutDirection;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_appearance, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        // App theme
        final String[] themes = getResources().getStringArray(R.array.themes);
        currentTheme = Prefs.Appearance.getNightMode();
        Preference appTheme = Objects.requireNonNull(findPreference("app_theme"));
        appTheme.setSummary(themes[THEME_CONST.indexOf(currentTheme)]);
        appTheme.setOnPreferenceClickListener(preference -> {
            new SearchableSingleChoiceDialogBuilder<>(requireActivity(), THEME_CONST, themes)
                    .setTitle(R.string.select_theme)
                    .setSelection(currentTheme)
                    .setPositiveButton(R.string.apply, (dialog, which, selectedTheme) -> {
                        if (selectedTheme != null && selectedTheme != currentTheme) {
                            currentTheme = selectedTheme;
                            Prefs.Appearance.setNightMode(currentTheme);
                            AppCompatDelegate.setDefaultNightMode(currentTheme);
                            appTheme.setSummary(themes[THEME_CONST.indexOf(currentTheme)]);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Black theme/custom theme
        SwitchPreferenceCompat fullBlackTheme = Objects.requireNonNull(findPreference("app_theme_pure_black"));
        fullBlackTheme.setVisible(BuildConfig.DEBUG);
        fullBlackTheme.setChecked(Prefs.Appearance.isPureBlackTheme());
        fullBlackTheme.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enabled = (boolean) newValue;
            Prefs.Appearance.setPureBlackTheme(enabled);
            AppearanceUtils.applyConfigurationChangesToActivities();
            return true;
        });
        // Layout orientation
        final String[] layoutOrientations = getResources().getStringArray(R.array.layout_orientations);
        currentLayoutDirection = Prefs.Appearance.getLayoutDirection();
        Preference layoutOrientation = Objects.requireNonNull(findPreference("layout_orientation"));
        layoutOrientation.setSummary(layoutOrientations[LAYOUT_ORIENTATION_CONST.indexOf(currentLayoutDirection)]);
        layoutOrientation.setOnPreferenceClickListener(preference -> {
            new SearchableSingleChoiceDialogBuilder<>(requireActivity(), LAYOUT_ORIENTATION_CONST, layoutOrientations)
                    .setTitle(R.string.pref_layout_orientation)
                    .setSelection(currentLayoutDirection)
                    .setPositiveButton(R.string.apply, (dialog, which, selectedLayoutOrientation) -> {
                        currentLayoutDirection = Objects.requireNonNull(selectedLayoutOrientation);
                        Prefs.Appearance.setLayoutDirection(currentLayoutDirection);
                        AppearanceUtils.applyConfigurationChangesToActivities();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Enable/disable features
        FeatureController fc = FeatureController.getInstance();
        ((Preference) Objects.requireNonNull(findPreference("enabled_features")))
                .setOnPreferenceClickListener(preference -> {
                    new SearchableFlagsDialogBuilder<>(requireActivity(), FeatureController.featureFlags, FeatureController.getFormattedFlagNames(requireActivity()), fc.getFlags())
                            .setTitle(R.string.enable_disable_features)
                            .setOnMultiChoiceClickListener((dialog, which, item, isChecked) ->
                                    fc.modifyState(FeatureController.featureFlags.get(which), isChecked))
                            .setNegativeButton(R.string.close, null)
                            .show();
                    return true;
                });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, true));
        setReturnTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, false));
    }

    @Override
    public int getTitle() {
        return R.string.pref_cat_appearance;
    }
}
