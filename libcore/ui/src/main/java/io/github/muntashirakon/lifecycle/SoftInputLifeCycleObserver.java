// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.lifecycle;

import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import java.lang.ref.WeakReference;

public class SoftInputLifeCycleObserver implements DefaultLifecycleObserver {
    @NonNull
    private final WeakReference<View> view;

    public SoftInputLifeCycleObserver(@NonNull WeakReference<View> view) {
        this.view = view;
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        if (view.get() == null) {
            return;
        }
        view.get().postDelayed(() -> {
            View v = view.get();
            if (v == null) return;
            v.requestFocus();
            InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
        }, 100);
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        if (view.get() == null) {
            return;
        }
        view.get().postDelayed(() -> {
            View v = view.get();
            if (v == null) return;
            InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }, 100);
    }
}
