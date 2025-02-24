// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseIntArray;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.ListOptions;
import io.github.muntashirakon.AppManager.profiles.ProfileManager;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.widget.AnyFilterArrayAdapter;

public class MainListOptions extends ListOptions {
    public static final String TAG = MainListOptions.class.getSimpleName();

    @IntDef(value = {
            SORT_BY_DOMAIN,
            SORT_BY_APP_LABEL,
            SORT_BY_PACKAGE_NAME,
            SORT_BY_LAST_UPDATE,
            SORT_BY_SHARED_ID,
            SORT_BY_TARGET_SDK,
            SORT_BY_SHA,
            SORT_BY_FROZEN_APP,
            SORT_BY_BLOCKED_COMPONENTS,
            SORT_BY_BACKUP,
            SORT_BY_TRACKERS,
            SORT_BY_LAST_ACTION,
            SORT_BY_INSTALLATION_DATE,
            SORT_BY_TOTAL_SIZE,
            SORT_BY_DATA_USAGE,
            SORT_BY_OPEN_COUNT,
            SORT_BY_SCREEN_TIME,
            SORT_BY_LAST_USAGE_TIME,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SortOrder {
    }

    public static final int SORT_BY_DOMAIN = 0;  // User/system app
    public static final int SORT_BY_APP_LABEL = 1;
    public static final int SORT_BY_PACKAGE_NAME = 2;
    public static final int SORT_BY_LAST_UPDATE = 3;
    public static final int SORT_BY_SHARED_ID = 4;
    public static final int SORT_BY_TARGET_SDK = 5;
    public static final int SORT_BY_SHA = 6;  // Signature
    public static final int SORT_BY_FROZEN_APP = 7;
    public static final int SORT_BY_BLOCKED_COMPONENTS = 8;
    public static final int SORT_BY_BACKUP = 9;
    public static final int SORT_BY_TRACKERS = 10;
    public static final int SORT_BY_LAST_ACTION = 11;
    public static final int SORT_BY_INSTALLATION_DATE = 12;
    public static final int SORT_BY_TOTAL_SIZE = 13;
    public static final int SORT_BY_DATA_USAGE = 14;
    public static final int SORT_BY_OPEN_COUNT = 15;
    public static final int SORT_BY_SCREEN_TIME = 16;
    public static final int SORT_BY_LAST_USAGE_TIME = 17;

    @IntDef(flag = true, value = {
            FILTER_NO_FILTER,
            FILTER_USER_APPS,
            FILTER_SYSTEM_APPS,
            FILTER_FROZEN_APPS,
            FILTER_APPS_WITH_RULES,
            FILTER_APPS_WITH_ACTIVITIES,
            FILTER_APPS_WITH_BACKUPS,
            FILTER_RUNNING_APPS,
            FILTER_APPS_WITH_SPLITS,
            FILTER_INSTALLED_APPS,
            FILTER_UNINSTALLED_APPS,
            FILTER_APPS_WITHOUT_BACKUPS,
            FILTER_APPS_WITH_KEYSTORE,
            FILTER_APPS_WITH_SAF,
            FILTER_APPS_WITH_SSAID,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Filter {
    }

    public static final int FILTER_NO_FILTER = 0;
    public static final int FILTER_USER_APPS = 1;
    public static final int FILTER_SYSTEM_APPS = 1 << 1;
    public static final int FILTER_FROZEN_APPS = 1 << 2;
    public static final int FILTER_APPS_WITH_RULES = 1 << 3;
    public static final int FILTER_APPS_WITH_ACTIVITIES = 1 << 4;
    public static final int FILTER_APPS_WITH_BACKUPS = 1 << 5;
    public static final int FILTER_RUNNING_APPS = 1 << 6;
    public static final int FILTER_APPS_WITH_SPLITS = 1 << 7;
    public static final int FILTER_INSTALLED_APPS = 1 << 8;
    public static final int FILTER_UNINSTALLED_APPS = 1 << 9;
    public static final int FILTER_APPS_WITHOUT_BACKUPS = 1 << 10;
    public static final int FILTER_APPS_WITH_KEYSTORE = 1 << 11;
    public static final int FILTER_APPS_WITH_SAF = 1 << 12;
    public static final int FILTER_APPS_WITH_SSAID = 1 << 13;

    private MainViewModel model;
    private final List<String> profileNames = new ArrayList<>();

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity activity = (MainActivity) requireActivity();
        model = activity.mModel;
        profileNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null) {
                    String profileName = s.toString().trim();
                    if (profileNames.contains(profileName)) {
                        model.setFilterProfileName(profileName);
                        return;
                    }
                }
                model.setFilterProfileName(null);
            }
        });
        if (activity.mModel != null) {
            activity.mModel.executor.submit(() -> {
                profileNames.clear();
                profileNames.addAll(ProfileManager.getProfileNames());
                if (isDetached()) return;
                activity.runOnUiThread(() -> {
                    profileNameInput.setAdapter(new AnyFilterArrayAdapter<>(activity, R.layout.item_checked_text_view,
                            profileNames));
                    profileNameInput.setText(model.getFilterProfileName());
                });
            });
        }
    }

    @Nullable
    @Override
    public SparseIntArray getSortIdLocaleMap() {
        return new SparseIntArray() {{
            put(SORT_BY_DOMAIN, R.string.sort_by_domain);
            put(SORT_BY_APP_LABEL, R.string.sort_by_app_label);
            put(SORT_BY_PACKAGE_NAME, R.string.sort_by_package_name);
            put(SORT_BY_LAST_UPDATE, R.string.sort_by_last_update);
            put(SORT_BY_SHARED_ID, R.string.sort_by_shared_user_id);
            put(SORT_BY_TARGET_SDK, R.string.sort_by_target_sdk);
            put(SORT_BY_SHA, R.string.sort_by_sha);
            put(SORT_BY_FROZEN_APP, R.string.sort_by_frozen_app);
            put(SORT_BY_BLOCKED_COMPONENTS, R.string.sort_by_blocked_components);
            put(SORT_BY_BACKUP, R.string.sort_by_backup);
            put(SORT_BY_TRACKERS, R.string.trackers);
            put(SORT_BY_LAST_ACTION, R.string.last_actions);
            put(SORT_BY_INSTALLATION_DATE, R.string.sort_by_installation_date);
            if (FeatureController.isUsageAccessEnabled()) {
                put(SORT_BY_TOTAL_SIZE, R.string.sort_by_total_size);
                put(SORT_BY_DATA_USAGE, R.string.sort_by_data_usage);
                put(SORT_BY_OPEN_COUNT, R.string.sort_by_times_opened);
                put(SORT_BY_SCREEN_TIME, R.string.sort_by_screen_time);
                put(SORT_BY_LAST_USAGE_TIME, R.string.sort_by_last_used);
            }
        }};
    }

    @Nullable
    @Override
    public SparseIntArray getFilterFlagLocaleMap() {
        return new SparseIntArray() {{
            put(FILTER_USER_APPS, R.string.filter_user_apps);
            put(FILTER_SYSTEM_APPS, R.string.filter_system_apps);
            put(FILTER_FROZEN_APPS, R.string.filter_frozen_apps);
            put(FILTER_APPS_WITH_RULES, R.string.filter_apps_with_rules);
            put(FILTER_APPS_WITH_ACTIVITIES, R.string.filter_apps_with_activities);
            put(FILTER_APPS_WITH_BACKUPS, R.string.filter_apps_with_backups);
            put(FILTER_APPS_WITHOUT_BACKUPS, R.string.filter_apps_without_backups);
            put(FILTER_RUNNING_APPS, R.string.filter_running_apps);
            put(FILTER_APPS_WITH_SPLITS, R.string.filter_apps_with_splits);
            put(FILTER_INSTALLED_APPS, R.string.installed_apps);
            put(FILTER_UNINSTALLED_APPS, R.string.uninstalled_apps);
            if (Ops.isRoot()) {
                put(FILTER_APPS_WITH_KEYSTORE, R.string.filter_apps_with_keystore);
                put(FILTER_APPS_WITH_SAF, R.string.filter_apps_with_saf);
                put(FILTER_APPS_WITH_SSAID, R.string.filter_apps_with_ssaid);
            }
        }};
    }

    @Nullable
    @Override
    public SparseIntArray getOptionIdLocaleMap() {
        return null;
    }

    @Override
    public boolean enableProfileNameInput() {
        return true;
    }
}
