/*  Copyright (C) 2019 Andreas Shimokawa, Cre3per

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities.devicesettings;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiConst;
import nodomain.freeyourgadget.gadgetbridge.devices.makibeshr3.MakibesHR3Constants;
import nodomain.freeyourgadget.gadgetbridge.devices.miband.MiBandConst;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;
import nodomain.freeyourgadget.gadgetbridge.util.XTimePreference;
import nodomain.freeyourgadget.gadgetbridge.util.XTimePreferenceFragment;

import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_DATEFORMAT;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SCREEN_ORIENTATION;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_TIMEFORMAT;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_WEARLOCATION;
import static nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiConst.PREF_ACTIVATE_DISPLAY_ON_LIFT;
import static nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiConst.PREF_DISCONNECT_NOTIFICATION;
import static nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiConst.PREF_DISCONNECT_NOTIFICATION_END;
import static nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiConst.PREF_DISCONNECT_NOTIFICATION_START;
import static nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiConst.PREF_DISPLAY_ITEMS;
import static nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiConst.PREF_DISPLAY_ON_LIFT_END;
import static nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiConst.PREF_DISPLAY_ON_LIFT_START;
import static nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiConst.PREF_EXPOSE_HR_THIRDPARTY;
import static nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiConst.PREF_LANGUAGE;
import static nodomain.freeyourgadget.gadgetbridge.devices.miband.MiBandConst.PREF_DO_NOT_DISTURB;
import static nodomain.freeyourgadget.gadgetbridge.devices.miband.MiBandConst.PREF_DO_NOT_DISTURB_END;
import static nodomain.freeyourgadget.gadgetbridge.devices.miband.MiBandConst.PREF_DO_NOT_DISTURB_OFF;
import static nodomain.freeyourgadget.gadgetbridge.devices.miband.MiBandConst.PREF_DO_NOT_DISTURB_START;
import static nodomain.freeyourgadget.gadgetbridge.devices.miband.MiBandConst.PREF_MI2_DATEFORMAT;
import static nodomain.freeyourgadget.gadgetbridge.devices.miband.MiBandConst.PREF_MI2_ROTATE_WRIST_TO_SWITCH_INFO;
import static nodomain.freeyourgadget.gadgetbridge.devices.miband.MiBandConst.PREF_NIGHT_MODE;
import static nodomain.freeyourgadget.gadgetbridge.devices.miband.MiBandConst.PREF_NIGHT_MODE_END;
import static nodomain.freeyourgadget.gadgetbridge.devices.miband.MiBandConst.PREF_NIGHT_MODE_START;
import static nodomain.freeyourgadget.gadgetbridge.devices.miband.MiBandConst.PREF_SWIPE_UNLOCK;

public class DeviceSpecificSettingsFragment extends PreferenceFragmentCompat {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceSpecificSettingsFragment.class);

    static final String FRAGMENT_TAG = "DEVICE_SPECIFIC_SETTINGS_FRAGMENT";

    private void setSettingsFileSuffix(String settingsFileSuffix, @NonNull int[] supportedSettings) {
        Bundle args = new Bundle();
        args.putString("settingsFileSuffix", settingsFileSuffix);
        args.putIntArray("supportedSettings", supportedSettings);
        setArguments(args);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Bundle arguments = getArguments();
        if (arguments == null) {
            return;
        }
        String settingsFileSuffix = arguments.getString("settingsFileSuffix", null);
        int[] supportedSettings = arguments.getIntArray("supportedSettings");
        if (settingsFileSuffix == null || supportedSettings == null) {
            return;
        }

        getPreferenceManager().setSharedPreferencesName("devicesettings_" + settingsFileSuffix);

        if (rootKey == null) {
            // we are the main preference screen
            boolean first = true;
            for (int setting : supportedSettings) {
                if (first) {
                    setPreferencesFromResource(setting, null);
                    first = false;
                } else {
                    addPreferencesFromResource(setting);
                }
            }
        } else {
            // Now, this is ugly: search all the xml files for the rootKey
            for (int setting : supportedSettings) {
                try {
                    setPreferencesFromResource(setting, rootKey);
                } catch (Exception ignore) {
                    continue;
                }
                break;
            }
        }
        setChangeListener();
    }

    /*
     * delayed execution so that the preferences are applied first
     */
    private void invokeLater(Runnable runnable) {
        getListView().post(runnable);
    }

    private void setChangeListener() {
        Prefs prefs = new Prefs(getPreferenceManager().getSharedPreferences());

        // TODO: Is there any reason we are not simply iterating over all properties on screen
        //       and listing them manually one by one instead?
        addPreferenceHandlerFor(PREF_DISCONNECT_NOTIFICATION);
        addPreferenceHandlerFor(PREF_DISCONNECT_NOTIFICATION_START);
        addPreferenceHandlerFor(PREF_DISCONNECT_NOTIFICATION_END);
        addPreferenceHandlerFor(PREF_NIGHT_MODE);
        addPreferenceHandlerFor(PREF_NIGHT_MODE_START);
        addPreferenceHandlerFor(PREF_NIGHT_MODE_END);
        addPreferenceHandlerFor(PREF_DO_NOT_DISTURB);
        addPreferenceHandlerFor(PREF_DO_NOT_DISTURB_START);
        addPreferenceHandlerFor(PREF_DO_NOT_DISTURB_END);
        addPreferenceHandlerFor(PREF_SWIPE_UNLOCK);
        addPreferenceHandlerFor(PREF_MI2_DATEFORMAT);
        addPreferenceHandlerFor(PREF_DATEFORMAT);
        addPreferenceHandlerFor(PREF_DISPLAY_ITEMS);
        addPreferenceHandlerFor(PREF_LANGUAGE);
        addPreferenceHandlerFor(PREF_EXPOSE_HR_THIRDPARTY);
        addPreferenceHandlerFor(PREF_WEARLOCATION);
        addPreferenceHandlerFor(PREF_SCREEN_ORIENTATION);
        addPreferenceHandlerFor(PREF_TIMEFORMAT);
        addPreferenceHandlerFor(PREF_MI2_ROTATE_WRIST_TO_SWITCH_INFO);
        addPreferenceHandlerFor(PREF_ACTIVATE_DISPLAY_ON_LIFT);
        addPreferenceHandlerFor(PREF_DISPLAY_ON_LIFT_START);
        addPreferenceHandlerFor(PREF_DISPLAY_ON_LIFT_END);

        // This is a special case that can't be handled inside the XML files - a preference from one
        // file depends on another
        // TODO: This didn't update properly before I touched it when changing PREF_ACTIVATE_DISPLAY_ON_LIFT
        //       and still doesn't (probably because that property is in a separate subscreen). It does work
        //       upon initial open of the settings though.
        final Preference rotateWristCycleInfo = findPreference(PREF_MI2_ROTATE_WRIST_TO_SWITCH_INFO);
        final Preference displayOnLift = findPreference(PREF_ACTIVATE_DISPLAY_ON_LIFT);
        if (rotateWristCycleInfo != null && displayOnLift != null) {
            String displayOnLiftState = prefs.getString(PREF_ACTIVATE_DISPLAY_ON_LIFT, PREF_DO_NOT_DISTURB_OFF);
            rotateWristCycleInfo.onDependencyChanged(displayOnLift, PREF_DO_NOT_DISTURB_OFF.equals(displayOnLiftState));
            final Preference.OnPreferenceChangeListener displayOnLiftDefaultChangeListener = displayOnLift.getOnPreferenceChangeListener();
            displayOnLift.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newVal) {
                    rotateWristCycleInfo.onDependencyChanged(displayOnLift, PREF_DO_NOT_DISTURB_OFF.equals(newVal.toString()));
                    return displayOnLiftDefaultChangeListener.onPreferenceChange(preference, newVal);
                }
            });
        }

        setInputTypeFor(HuamiConst.PREF_BUTTON_ACTION_BROADCAST_DELAY, InputType.TYPE_CLASS_NUMBER);
        setInputTypeFor(HuamiConst.PREF_BUTTON_ACTION_PRESS_MAX_INTERVAL, InputType.TYPE_CLASS_NUMBER);
        setInputTypeFor(HuamiConst.PREF_BUTTON_ACTION_PRESS_COUNT, InputType.TYPE_CLASS_NUMBER);
        setInputTypeFor(MiBandConst.PREF_MIBAND_DEVICE_TIME_OFFSET_HOURS, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        setInputTypeFor(MakibesHR3Constants.PREF_FIND_PHONE_DURATION, InputType.TYPE_CLASS_NUMBER);
        setInputTypeFor(DeviceSettingsPreferenceConst.PREF_RESERVER_ALARMS_CALENDAR, InputType.TYPE_CLASS_NUMBER);
    }

    static DeviceSpecificSettingsFragment newInstance(String settingsFileSuffix, @NonNull int[] supportedSettings) {
        DeviceSpecificSettingsFragment fragment = new DeviceSpecificSettingsFragment();
        fragment.setSettingsFileSuffix(settingsFileSuffix, supportedSettings);

        return fragment;
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        DialogFragment dialogFragment;
        if (preference instanceof XTimePreference) {
            dialogFragment = new XTimePreferenceFragment();
            Bundle bundle = new Bundle(1);
            bundle.putString("key", preference.getKey());
            dialogFragment.setArguments(bundle);
            dialogFragment.setTargetFragment(this, 0);
            if (getFragmentManager() != null) {
                dialogFragment.show(getFragmentManager(), "androidx.preference.PreferenceFragment.DIALOG");
            }
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    private void addPreferenceHandlerFor(final String preferenceKey) {
        Preference pref = findPreference(preferenceKey);
        if (pref != null) {
            pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newVal) {
                    invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            GBApplication.deviceService().onSendConfiguration(preferenceKey);
                        }
                    });
                    return true;
                }
            });
        }
    }

    private void setInputTypeFor(final String preferenceKey, final int editTypeFlags) {
        EditTextPreference textPreference = findPreference(preferenceKey);
        if (textPreference != null) {
            textPreference.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
                @Override
                public void onBindEditText(@NonNull EditText editText) {
                    editText.setInputType(editTypeFlags);
                }
            });
        }
    }
}
