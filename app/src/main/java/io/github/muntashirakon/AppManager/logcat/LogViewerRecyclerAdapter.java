// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logcat.struct.LogLine;
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.widget.MultiSelectionView;

// Copyright 2012 Nolan Lawson
// Copyright 2021 Muntashir Al-Islam
public class LogViewerRecyclerAdapter extends MultiSelectionView.Adapter<LogViewerRecyclerAdapter.ViewHolder>
        implements Filterable {
    public static final String TAG = LogViewerRecyclerAdapter.class.getSimpleName();

    public static final int CONTEXT_MENU_FILTER_ID = 0;
    public static final int CONTEXT_MENU_COPY_ID = 1;
    public static final int CONTEXT_MENU_SELECT_ID = 2;

    private static final SparseArrayCompat<Integer> BACKGROUND_COLORS = new SparseArrayCompat<Integer>(7) {
        {
            put(android.util.Log.VERBOSE, R.color.the_brown_shirts);
            put(android.util.Log.DEBUG, R.color.night_blue_shadow);
            put(android.util.Log.INFO, R.color.blue_popsicle);
            put(android.util.Log.WARN, R.color.red_orange);
            put(android.util.Log.ERROR, R.color.pure_red);
            put(android.util.Log.ASSERT, R.color.pure_red);
            put(LogLine.LOG_FATAL, R.color.electric_red);
        }
    };

    private static final SparseArrayCompat<Integer> FOREGROUND_COLORS = new SparseArrayCompat<Integer>(7) {
        {
            put(android.util.Log.VERBOSE, R.color.brian_wrinkle_white);
            put(android.util.Log.DEBUG, R.color.brian_wrinkle_white);
            put(android.util.Log.INFO, R.color.brian_wrinkle_white);
            put(android.util.Log.WARN, R.color.brian_wrinkle_white);
            put(android.util.Log.ERROR, R.color.brian_wrinkle_white);
            put(android.util.Log.ASSERT, R.color.brian_wrinkle_white);
            put(LogLine.LOG_FATAL, R.color.brian_wrinkle_white);
        }
    };

    private static int[] tagColors;

    @ColorInt
    private static int getBackgroundColorForLogLevel(Context context, int logLevel) {
        Integer result = BACKGROUND_COLORS.get(logLevel);
        if (result == null) return UIUtils.getPrimaryColor(context);
        return ContextCompat.getColor(context, result);
    }

    @ColorInt
    private static int getForegroundColorForLogLevel(Context context, int logLevel) {
        Integer result = FOREGROUND_COLORS.get(logLevel);
        if (result == null) return UIUtils.getAccentColor(context);
        return ContextCompat.getColor(context, result);
    }

    private static synchronized int getOrCreateTagColor(Context context, String tag) {
        if (tagColors == null) {
            tagColors = context.getResources().getIntArray(R.array.random_colors);
        }
        // Ensure consistency
        int hashCode = (tag == null) ? 0 : tag.hashCode();
        int smear = Math.abs(hashCode) % tagColors.length;
        return tagColors[smear];
    }

    /**
     * Lock used to modify the content of {@link #mObjects}. Any write operation
     * performed on the array should be synchronized on this lock. This lock is also
     * used by the filter (see {@link #getFilter()} to make a synchronized copy of
     * the original array of data.
     */
    private final Object mLock = new Object();
    /**
     * Contains the list of objects that represent the data of this ArrayAdapter.
     * The content of this list is referred to as "the array" in the documentation.
     */
    @GuardedBy("mLock")
    private List<LogLine> mObjects;

    private ViewHolder.OnClickListener mClickListener;

    private ArrayList<LogLine> mOriginalValues;
    private ArrayFilter mFilter;

    private int logLevelLimit = Prefs.LogViewer.getLogLevel();
    private final Set<LogLine> mSelectedLogLines = new LinkedHashSet<>();
    @ColorInt
    private int highlightColor;

    public LogViewerRecyclerAdapter() {
        mObjects = new ArrayList<>();
        setHasStableIds(true);
    }

    /**
     * Adds the specified object at the end of the array.
     *
     * @param object The object to add at the end of the array.
     */
    @GuardedBy("mLock")
    public void add(LogLine object, boolean notify) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                mOriginalValues.add(object);
            }
            mObjects.add(object);
            if (notify) {
                notifyItemInserted(mObjects.size());
            }
        }
    }

    @GuardedBy("mLock")
    public void readAll(LogLine object, boolean notify) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                mOriginalValues.add(object);
            }
            mObjects.add(object);
            if (notify) {
                notifyItemInserted(mObjects.size());
            }
        }
    }

    public void addWithFilter(@NonNull LogLine object, @Nullable CharSequence text, boolean notify) {
        if (mOriginalValues != null) {
            List<LogLine> inputList = Collections.singletonList(object);
            if (mFilter == null) {
                mFilter = new ArrayFilter();
            }
            List<LogLine> filteredObjects = mFilter.performFilteringOnList(inputList, text);
            synchronized (mLock) {
                mOriginalValues.add(object);
                mObjects.addAll(filteredObjects);
                if (notify) {
                    notifyItemRangeInserted(mObjects.size() - filteredObjects.size(), filteredObjects.size());
                }
            }
        } else {
            synchronized (mLock) {
                mObjects.add(object);
                if (notify) {
                    notifyItemInserted(mObjects.size());
                }
            }
        }
    }

    /**
     * Inserts the specified object at the specified index in the array.
     *
     * @param object The object to insert into the array.
     * @param index  The index at which the object must be inserted.
     */
    @GuardedBy("mLock")
    public void insert(LogLine object, int index) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                mOriginalValues.add(index, object);
            } else {
                mObjects.add(index, object);
            }
            notifyDataSetChanged();
        }
    }

    /**
     * Removes the specified object from the array.
     *
     * @param object The object to remove.
     */
    @GuardedBy("mLock")
    public void remove(LogLine object) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                mOriginalValues.remove(object);
            } else {
                mObjects.remove(object);
            }
            notifyDataSetChanged();
        }
    }

    public void removeFirst(int n) {
        StopWatch stopWatch = new StopWatch("removeFirst()");
        if (mOriginalValues != null) {
            synchronized (mLock) {
                List<LogLine> subList = mOriginalValues.subList(n, mOriginalValues.size());
                for (int i = 0; i < n; i++) {
                    mObjects.remove(mOriginalValues.get(i));
                }
                mOriginalValues = new ArrayList<>(subList);
                notifyDataSetChanged();
            }
        } else {
            synchronized (mLock) {
                mObjects = new ArrayList<>(mObjects.subList(n, mObjects.size()));
                notifyDataSetChanged();
            }
        }
        stopWatch.log();
    }

    /**
     * Remove all elements from the list.
     */
    @GuardedBy("mLock")
    public void clear() {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                mOriginalValues.clear();
            }
            mObjects.clear();
            notifyDataSetChanged();
        }
    }

    @GuardedBy("mLock")
    public LogLine getItem(int position) {
        synchronized (mLock) {
            return mObjects.get(position);
        }
    }

    @GuardedBy("mLock")
    public int getRealSize() {
        synchronized (mLock) {
            return (mOriginalValues != null ? mOriginalValues : mObjects).size();
        }
    }

    public Set<LogLine> getSelectedLogLines() {
        return mSelectedLogLines;
    }

    @GuardedBy("mLock")
    public void setCollapseMode(boolean isCollapsed) {
        synchronized (mLock) {
            List<LogLine> list = mOriginalValues != null ? mOriginalValues : mObjects;
            for (LogLine logLine : list) {
                logLine.setExpanded(!isCollapsed);
            }
        }
    }

    @Override
    public int getHighlightColor() {
        return highlightColor;
    }

    @Override
    protected void select(int position) {
        synchronized (mSelectedLogLines) {
            mSelectedLogLines.add(getItem(position));
        }
    }

    @Override
    protected void deselect(int position) {
        synchronized (mSelectedLogLines) {
            mSelectedLogLines.remove(getItem(position));
        }
    }

    @Override
    protected boolean isSelected(int position) {
        synchronized (mSelectedLogLines) {
            return mSelectedLogLines.contains(getItem(position));
        }
    }

    @Override
    protected void cancelSelection() {
        super.cancelSelection();
        synchronized (mSelectedLogLines) {
            mSelectedLogLines.clear();
        }
    }

    @Override
    protected int getSelectedItemCount() {
        synchronized (mSelectedLogLines) {
            return mSelectedLogLines.size();
        }
    }

    @Override
    protected int getTotalItemCount() {
        return getItemCount();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        highlightColor = ColorCodes.getListItemSelectionColor(parent.getContext());
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_logcat, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Context context = holder.itemView.getContext();
        LogLine logLine = getItem(position);
        holder.logLine = logLine;

        int levelColor = getBackgroundColorForLogLevel(context, logLine.getLogLevel());
        TextView t = holder.logLevel;
        t.setText(logLine.getProcessIdText());
        t.setBackgroundColor(levelColor);
        t.setTextColor(getForegroundColorForLogLevel(context, logLine.getLogLevel()));
        t.setVisibility(logLine.getLogLevel() == -1 ? View.GONE : View.VISIBLE);

        holder.itemView.setBackgroundResource(0);
        View contentView = holder.itemView.findViewById(R.id.log_content);
        contentView.setBackgroundResource(position % 2 == 0 ? R.drawable.item_semi_transparent : R.drawable.item_transparent);

        //OUTPUT TEXT VIEW
        TextView output = holder.output;
        output.setSingleLine(!logLine.isExpanded());
        output.setText(logLine.getLogOutput());

        //TAG TEXT VIEW
        TextView tag = holder.tag;
        tag.setSingleLine(!logLine.isExpanded());
        tag.setText(logLine.getTagName());
        tag.setVisibility(logLine.getLogLevel() == -1 ? View.GONE : View.VISIBLE);

        //EXPANDED INFO
        boolean extraInfoIsVisible = logLine.isExpanded() && logLine.getProcessId() != -1 // -1 marks lines like 'beginning of /dev/log...'
                && Prefs.LogViewer.showPidTidTimestamp();

        TextView pidText = holder.pid;
        pidText.setVisibility(extraInfoIsVisible ? View.VISIBLE : View.GONE);
        TextView timestampText = holder.itemView.findViewById(R.id.timestamp_text);
        timestampText.setVisibility(extraInfoIsVisible ? View.VISIBLE : View.GONE);

        if (extraInfoIsVisible) {
            pidText.setText(logLine.getProcessId() != -1 ? Integer.toString(logLine.getProcessId()) : null);
            timestampText.setText(logLine.getTimestamp());
        }

        tag.setTextColor(getOrCreateTagColor(context, logLine.getTagName()));
        // Allow selection
        holder.itemView.setOnClickListener(v -> {
            if (isInSelectionMode()) {
                toggleSelection(position);
            } else {
                logLine.setExpanded(!logLine.isExpanded());
                notifyItemChanged(position);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            PopupMenu menu = new PopupMenu(v.getContext(), v);
            menu.getMenu().add(0, CONTEXT_MENU_FILTER_ID, 0, R.string.filter_choice);
            menu.getMenu().add(0, CONTEXT_MENU_COPY_ID, 0, R.string.copy_to_clipboard);
            menu.getMenu().add(0, CONTEXT_MENU_SELECT_ID, 0, R.string.item_select);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == CONTEXT_MENU_SELECT_ID) {
                    toggleSelection(position);
                    return true;
                }
                if (mClickListener != null) {
                    return mClickListener.onMenuItemClick(item, logLine);
                }
                return false;
            });
            menu.show();
            return true;
        });
        super.onBindViewHolder(holder, position);
    }

    @GuardedBy("mLock")
    @Override
    public long getItemId(int position) {
        synchronized (mLock) {
            return mObjects.get(position).getOriginalLine().hashCode();
        }
    }

    @GuardedBy("mLock")
    @Override
    public int getItemCount() {
        synchronized (mLock) {
            return mObjects.size();
        }
    }

    public int getLogLevelLimit() {
        return logLevelLimit;
    }

    public void setLogLevelLimit(int logLevelLimit) {
        this.logLevelLimit = logLevelLimit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Filter getFilter() {
        if (mFilter == null) {
            mFilter = new ArrayFilter();
        }
        return mFilter;
    }

    public void setClickListener(ViewHolder.OnClickListener clickListener) {
        mClickListener = clickListener;
    }

    /**
     * <p>An array filter constrains the content of the array adapter with
     * a prefix. Each item that does not start with the supplied prefix
     * is removed from the list.</p>
     */
    private class ArrayFilter extends Filter {
        @NonNull
        @Override
        protected FilterResults performFiltering(CharSequence prefix) {
            FilterResults results = new FilterResults();

            if (mOriginalValues == null) {
                synchronized (mLock) {
                    mOriginalValues = new ArrayList<>(mObjects);
                }
            }

            ArrayList<LogLine> allValues = performFilteringOnList(mOriginalValues, prefix);

            results.values = allValues;
            results.count = allValues.size();

            return results;
        }

        public ArrayList<LogLine> performFilteringOnList(List<LogLine> inputList, CharSequence query) {
            SearchCriteria searchCriteria = new SearchCriteria(query);

            // search by log level
            ArrayList<LogLine> allValues = new ArrayList<>();

            ArrayList<LogLine> logLines;
            synchronized (mLock) {
                logLines = new ArrayList<>(inputList);
            }

            for (LogLine logLine : logLines) {
                if (logLine != null && logLine.getLogLevel() >= logLevelLimit) {
                    allValues.add(logLine);
                }
            }
            ArrayList<LogLine> finalValues = allValues;

            // search by criteria
            if (!searchCriteria.isEmpty()) {
                final int count = allValues.size();
                final ArrayList<LogLine> newValues = new ArrayList<>(count);
                for (final LogLine value : allValues) {
                    // search the logline based on the criteria
                    if (searchCriteria.matches(value)) {
                        newValues.add(value);
                    }
                }
                finalValues = newValues;
            }
            return finalValues;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            synchronized (mLock) {
                mObjects = (List<LogLine>) results.values;
                notifyDataSetChanged();
            }
        }
    }

    private static class StopWatch {
        private long startTime;
        private String name;

        public StopWatch(String name) {
            if (BuildConfig.DEBUG) {
                this.name = name;
                this.startTime = System.currentTimeMillis();
            }
        }

        public void log() {
            Log.d(TAG, name + " took " + (System.currentTimeMillis() - startTime) + " ms");
        }
    }

    public static class ViewHolder extends MultiSelectionView.ViewHolder {
        LogLine logLine;
        TextView logLevel;
        TextView tag;
        TextView output;
        TextView pid;

        public ViewHolder(View itemView) {
            super(itemView);
            logLevel = itemView.findViewById(R.id.log_level_text);
            tag = itemView.findViewById(R.id.tag_text);
            output = itemView.findViewById(R.id.log_output_text);
            pid = itemView.findViewById(R.id.pid_text);
        }

        public interface OnClickListener {
            boolean onMenuItemClick(MenuItem item, LogLine logLine);
        }
    }
}
