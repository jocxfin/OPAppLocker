package com.android.settingslib.datetime;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.icu.text.TimeZoneNames;
import android.icu.text.TimeZoneNames.NameType;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.util.Log;
import com.android.settingslib.R;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.xmlpull.v1.XmlPullParserException;

public class ZoneGetter {
    public static final String KEY_DISPLAYNAME = "name";
    public static final String KEY_GMT = "gmt";
    public static final String KEY_ID = "id";
    public static final String KEY_OFFSET = "offset";
    private static final String TAG = "ZoneGetter";
    private static final String XMLTAG_TIMEZONE = "timezone";

    private ZoneGetter() {
    }

    public static String getTimeZoneOffsetAndName(TimeZone tz, Date now) {
        Locale locale = Locale.getDefault();
        String gmtString = getGmtOffsetString(locale, tz, now);
        String zoneNameString = getZoneLongName(TimeZoneNames.getInstance(locale), tz, now);
        if (zoneNameString == null) {
            return gmtString;
        }
        return gmtString + " " + zoneNameString;
    }

    public static List<Map<String, Object>> getZonesList(Context context) {
        int i;
        Locale locale = Locale.getDefault();
        Date now = new Date();
        TimeZoneNames timeZoneNames = TimeZoneNames.getInstance(locale);
        List<String> olsonIdsToDisplayList = readTimezonesToDisplay(context);
        int zoneCount = olsonIdsToDisplayList.size();
        String[] olsonIdsToDisplay = new String[zoneCount];
        TimeZone[] timeZones = new TimeZone[zoneCount];
        String[] gmtOffsetStrings = new String[zoneCount];
        for (i = 0; i < zoneCount; i++) {
            String olsonId = (String) olsonIdsToDisplayList.get(i);
            olsonIdsToDisplay[i] = olsonId;
            TimeZone tz = TimeZone.getTimeZone(olsonId);
            timeZones[i] = tz;
            gmtOffsetStrings[i] = getGmtOffsetString(locale, tz, now);
        }
        Set<String> localZoneIds = new HashSet();
        for (String olsonId2 : libcore.icu.TimeZoneNames.forLocale(locale)) {
            localZoneIds.add(olsonId2);
        }
        Set<String> localZoneNames = new HashSet();
        boolean useExemplarLocationForLocalNames = false;
        for (i = 0; i < zoneCount; i++) {
            String displayName;
            if (localZoneIds.contains(olsonIdsToDisplay[i])) {
                displayName = getZoneLongName(timeZoneNames, timeZones[i], now);
                if (displayName == null) {
                    displayName = gmtOffsetStrings[i];
                }
                if (!localZoneNames.add(displayName)) {
                    useExemplarLocationForLocalNames = true;
                    break;
                }
            }
        }
        List<Map<String, Object>> zones = new ArrayList();
        for (i = 0; i < zoneCount; i++) {
            olsonId2 = olsonIdsToDisplay[i];
            tz = timeZones[i];
            String gmtOffsetString = gmtOffsetStrings[i];
            boolean preferLongName = localZoneIds.contains(olsonId2) && !useExemplarLocationForLocalNames;
            if (preferLongName) {
                displayName = getZoneLongName(timeZoneNames, tz, now);
            } else {
                displayName = timeZoneNames.getExemplarLocationName(tz.getID());
                if (displayName == null || displayName.isEmpty()) {
                    displayName = getZoneLongName(timeZoneNames, tz, now);
                }
            }
            if (displayName == null || displayName.isEmpty()) {
                displayName = gmtOffsetString;
            }
            zones.add(createDisplayEntry(tz, gmtOffsetString, displayName, tz.getOffset(now.getTime())));
        }
        return zones;
    }

    private static Map<String, Object> createDisplayEntry(TimeZone tz, String gmtOffsetString, String displayName, int offsetMillis) {
        Map<String, Object> map = new HashMap();
        map.put(KEY_ID, tz.getID());
        map.put(KEY_DISPLAYNAME, displayName);
        map.put(KEY_GMT, gmtOffsetString);
        map.put(KEY_OFFSET, Integer.valueOf(offsetMillis));
        return map;
    }

    private static List<String> readTimezonesToDisplay(Context context) {
        Throwable th;
        Throwable th2 = null;
        List<String> olsonIds = new ArrayList();
        XmlResourceParser xmlResourceParser = null;
        xmlResourceParser = context.getResources().getXml(R.xml.timezones);
        do {
        } while (xmlResourceParser.next() != 2);
        xmlResourceParser.next();
        while (xmlResourceParser.getEventType() != 3) {
            while (xmlResourceParser.getEventType() != 2) {
                if (xmlResourceParser.getEventType() == 1) {
                    if (xmlResourceParser != null) {
                        try {
                            xmlResourceParser.close();
                        } catch (Throwable th3) {
                            th2 = th3;
                        }
                    }
                    if (th2 == null) {
                        return olsonIds;
                    }
                    try {
                        throw th2;
                    } catch (XmlPullParserException e) {
                        Log.e(TAG, "Ill-formatted timezones.xml file");
                    } catch (IOException e2) {
                        Log.e(TAG, "Unable to read timezones.xml file");
                    }
                } else {
                    xmlResourceParser.next();
                }
            }
            try {
                if (xmlResourceParser.getName().equals(XMLTAG_TIMEZONE)) {
                    olsonIds.add(xmlResourceParser.getAttributeValue(0));
                }
                while (xmlResourceParser.getEventType() != 3) {
                    xmlResourceParser.next();
                }
                xmlResourceParser.next();
            } catch (Throwable th22) {
                Throwable th4 = th22;
                th22 = th;
                th = th4;
            }
        }
        if (xmlResourceParser != null) {
            try {
                xmlResourceParser.close();
            } catch (Throwable th5) {
                th22 = th5;
            }
        }
        if (th22 != null) {
            throw th22;
        }
        return olsonIds;
        if (xmlResourceParser != null) {
            try {
                xmlResourceParser.close();
            } catch (Throwable th6) {
                if (th22 == null) {
                    th22 = th6;
                } else if (th22 != th6) {
                    th22.addSuppressed(th6);
                }
            }
        }
        if (th22 != null) {
            throw th22;
        } else {
            throw th;
        }
    }

    private static String getZoneLongName(TimeZoneNames names, TimeZone tz, Date now) {
        NameType nameType;
        if (tz.inDaylightTime(now)) {
            nameType = NameType.LONG_DAYLIGHT;
        } else {
            nameType = NameType.LONG_STANDARD;
        }
        return names.getDisplayName(tz.getID(), nameType, now.getTime());
    }

    private static String getGmtOffsetString(Locale locale, TimeZone tz, Date now) {
        boolean isRtl = true;
        SimpleDateFormat gmtFormatter = new SimpleDateFormat("ZZZZ");
        gmtFormatter.setTimeZone(tz);
        String gmtString = gmtFormatter.format(now);
        BidiFormatter bidiFormatter = BidiFormatter.getInstance();
        if (TextUtils.getLayoutDirectionFromLocale(locale) != 1) {
            isRtl = false;
        }
        return bidiFormatter.unicodeWrap(gmtString, isRtl ? TextDirectionHeuristics.RTL : TextDirectionHeuristics.LTR);
    }
}
