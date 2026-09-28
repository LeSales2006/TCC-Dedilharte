package com.example.dedilharte.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

public final class ActivityStore {

    private static final String FILE_NAME = "dedilharte_activity";
    private static final String PREFIX = "access_";
    private static final long WEEK_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    private final SharedPreferences preferences;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private String userId;

    public ActivityStore(Context context, String userId) {
        preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
        dateFormat.setTimeZone(TimeZone.getDefault());
        setUserId(userId);
    }

    public void setUserId(String userId) {
        this.userId = userId == null || userId.trim().isEmpty() ? "default" : userId;
    }

    public boolean recordToday() {
        String key = key(today());
        if (preferences.getBoolean(key, false)) {
            return false;
        }
        preferences.edit().putBoolean(key, true).apply();
        return true;
    }

    public int accessDaysThisWeek() {
        Calendar today = today();
        Calendar start = weekStart(today);
        return countDistinctDays(start, today);
    }

    public int accessDaysLastFourWeeks() {
        Calendar today = today();
        Calendar start = weekStart(today);
        start.add(Calendar.WEEK_OF_YEAR, -3);
        return countDistinctDays(start, today);
    }

    public int[] weeklyAccessesLastFourWeeks() {
        int[] result = new int[4];
        Calendar weekStart = weekStart(today());
        weekStart.add(Calendar.WEEK_OF_YEAR, -3);
        for (int i = 0; i < result.length; i++) {
            Calendar start = (Calendar) weekStart.clone();
            start.add(Calendar.WEEK_OF_YEAR, i);
            Calendar end = (Calendar) start.clone();
            end.add(Calendar.DATE, 6);
            Calendar today = today();
            if (end.after(today)) {
                end = today;
            }
            result[i] = countDistinctDays(start, end);
        }
        return result;
    }

    public int availableWeeks(long createdAtMillis) {
        Calendar today = weekStart(today());
        Calendar created = today();
        if (createdAtMillis > 0) {
            created.setTimeInMillis(createdAtMillis);
            startOfDay(created);
        }
        created = weekStart(created);
        long weeks = ((today.getTimeInMillis() - created.getTimeInMillis()) / WEEK_MILLIS) + 1;
        return (int) Math.max(1, Math.min(4, weeks));
    }

    public void deleteUserData(String deletedUserId) {
        String resolved = deletedUserId == null || deletedUserId.trim().isEmpty() ? "default" : deletedUserId;
        String prefix = PREFIX + resolved + "_";
        SharedPreferences.Editor editor = preferences.edit();
        for (String existingKey : preferences.getAll().keySet()) {
            if (existingKey.startsWith(prefix)) {
                editor.remove(existingKey);
            }
        }
        editor.apply();
    }

    private int countDistinctDays(Calendar start, Calendar endInclusive) {
        Set<String> days = new HashSet<>();
        Calendar cursor = (Calendar) start.clone();
        while (!cursor.after(endInclusive)) {
            if (preferences.getBoolean(key(cursor), false)) {
                days.add(dateFormat.format(cursor.getTime()));
            }
            cursor.add(Calendar.DATE, 1);
        }
        return days.size();
    }

    private Calendar today() {
        Calendar calendar = Calendar.getInstance();
        startOfDay(calendar);
        return calendar;
    }

    private Calendar weekStart(Calendar source) {
        Calendar calendar = (Calendar) source.clone();
        startOfDay(calendar);
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        return calendar;
    }

    private void startOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private String key(Calendar date) {
        return PREFIX + userId + "_" + dateFormat.format(date.getTime());
    }
}
