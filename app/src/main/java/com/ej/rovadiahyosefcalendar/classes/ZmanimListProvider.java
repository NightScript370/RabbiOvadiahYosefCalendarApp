package com.ej.rovadiahyosefcalendar.classes;

import android.content.SharedPreferences;

import java.util.List;

/**
 * A calendar class that decides its own list of zmanim (as each page of the website has its own zmanim.yml).
 * {@link ZmanimFactory#addZmanim} hands the list to a calendar implementing this instead of building the default one, so
 * a calendar with other opinions brings its own rows without the default list knowing about it.
 */
public interface ZmanimListProvider {
    void addZmanim(List<ZmanListEntry> zmanim,
                   boolean isForWeeklyZmanim,
                   SharedPreferences settingsPreferences,
                   SharedPreferences sharedPreferences,
                   JewishDateInfo jewishDateInfo);
}
