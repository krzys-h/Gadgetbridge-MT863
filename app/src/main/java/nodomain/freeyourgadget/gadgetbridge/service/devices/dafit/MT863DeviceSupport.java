package nodomain.freeyourgadget.gadgetbridge.service.devices.dafit;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.Logging;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventCallControl;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventMusicControl;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.devices.dafit.DafitConstants;
import nodomain.freeyourgadget.gadgetbridge.devices.dafit.DafitWeatherForecast;
import nodomain.freeyourgadget.gadgetbridge.devices.dafit.DafitWeatherToday;
import nodomain.freeyourgadget.gadgetbridge.devices.dafit.MT863SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.MT863ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.model.Alarm;
import nodomain.freeyourgadget.gadgetbridge.model.CalendarEventSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CannedMessagesSpec;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;
import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicStateSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes;
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.SetDeviceStateAction;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.IntentListener;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.battery.BatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.battery.BatteryInfoProfile;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfo;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfoProfile;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.heartrate.HeartRateProfile;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.NotificationUtils;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;

// TODO: figure out the training data

public class MT863DeviceSupport extends AbstractBTLEDeviceSupport {

    private static final Logger LOG = LoggerFactory.getLogger(MT863DeviceSupport.class);
    private static final long IDLE_STEPS_INTERVAL = 5 * 60 * 1000;

    private final DeviceInfoProfile<MT863DeviceSupport> deviceInfoProfile;
    private final BatteryInfoProfile<MT863DeviceSupport> batteryInfoProfile;
    private final HeartRateProfile<MT863DeviceSupport> heartRateProfile;
    private final GBDeviceEventVersionInfo versionCmd = new GBDeviceEventVersionInfo();
    private final GBDeviceEventBatteryInfo batteryCmd = new GBDeviceEventBatteryInfo();
    private final IntentListener mListener = new IntentListener() {
        @Override
        public void notify(Intent intent) {
            String s = intent.getAction();
            if (Objects.equals(s, DeviceInfoProfile.ACTION_DEVICE_INFO)) {
                handleDeviceInfo((DeviceInfo) intent.getParcelableExtra(DeviceInfoProfile.EXTRA_DEVICE_INFO));
            }
            if (Objects.equals(s, BatteryInfoProfile.ACTION_BATTERY_INFO)) {
                handleBatteryInfo((BatteryInfo) intent.getParcelableExtra(BatteryInfoProfile.EXTRA_BATTERY_INFO));
            }
        }
    };

    private Handler idleUpdateHandler = new Handler();

    public static final int MTU = 20; // TODO: there seems to be some way to change this value...?
    private DafitPacketIn packetIn = new DafitPacketIn();

    private boolean realTimeHeartRate;

    public MT863DeviceSupport() {
        super(LOG);

        addSupportedService(GattService.UUID_SERVICE_GENERIC_ACCESS);
        addSupportedService(GattService.UUID_SERVICE_GENERIC_ATTRIBUTE);
        addSupportedService(GattService.UUID_SERVICE_DEVICE_INFORMATION);
        addSupportedService(GattService.UUID_SERVICE_BATTERY_SERVICE);
        addSupportedService(GattService.UUID_SERVICE_HEART_RATE);
        addSupportedService(DafitConstants.UUID_SERVICE_DAFIT);

        deviceInfoProfile = new DeviceInfoProfile<>(this);
        deviceInfoProfile.addListener(mListener);
        batteryInfoProfile = new BatteryInfoProfile<>(this);
        batteryInfoProfile.addListener(mListener);
        heartRateProfile = new HeartRateProfile<>(this);
        heartRateProfile.addListener(mListener);
        addSupportedProfile(deviceInfoProfile);
        addSupportedProfile(batteryInfoProfile);
        addSupportedProfile(heartRateProfile); // TODO: this profile doesn't seem to work...
    }

    @Override
    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZING, getContext()));
        builder.notify(getCharacteristic(DafitConstants.UUID_CHARACTERISTIC_DATA_IN), true);
        deviceInfoProfile.requestDeviceInfo(builder);
        setTime(builder);
        batteryInfoProfile.requestBatteryInfo(builder);
        batteryInfoProfile.enableNotify(builder);
        heartRateProfile.enableNotify(builder);
        builder.notify(getCharacteristic(DafitConstants.UUID_CHARACTERISTIC_STEPS), true);
        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZED, getContext()));

        return builder;
    }

    @Override
    public void dispose() {
        super.dispose();
        idleUpdateHandler.removeCallbacks(updateIdleStepsRunnable);
    }

    private BluetoothGattCharacteristic getTargetCharacteristicForPacketType(byte packetType)
    {
        if (packetType == 1)
            return getCharacteristic(DafitConstants.UUID_CHARACTERISTIC_DATA_SPECIAL_1);
        else if (packetType == 2)
            return getCharacteristic(DafitConstants.UUID_CHARACTERISTIC_DATA_SPECIAL_2);
        else
            return getCharacteristic(DafitConstants.UUID_CHARACTERISTIC_DATA_OUT);
    }

    public void sendPacket(TransactionBuilder builder, byte[] packet)
    {
        DafitPacketOut packetOut = new DafitPacketOut(packet);

        byte[] fragment = new byte[MTU];
        while(packetOut.getFragment(fragment))
        {
            builder.write(getTargetCharacteristicForPacketType(packet[4]), fragment.clone());
        }
    }

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        UUID charUuid = characteristic.getUuid();
        if (charUuid.equals(DafitConstants.UUID_CHARACTERISTIC_STEPS))
        {
            byte[] payload = characteristic.getValue();
            Log.i("AAAAAAAAAAAAAAAA", "Update step count: " + Logging.formatBytes(characteristic.getValue()));
            handleStepsHistory(0, payload, true);
            return true;
        }
        if (charUuid.equals(DafitConstants.UUID_CHARACTERISTIC_DATA_IN))
        {
            if (packetIn.putFragment(characteristic.getValue())) {
                Pair<Byte, byte[]> packet = DafitPacketIn.parsePacket(packetIn.getPacket());
                packetIn = new DafitPacketIn();
                if (packet != null) {
                    byte packetType = packet.first;
                    byte[] payload = packet.second;

                    Log.i("AAAAAAAAAAAAAAAA", "Response for: " + packetType);

                    if (handlePacket(packetType, payload))
                        return true;
                }
            }
        }

        return super.onCharacteristicChanged(gatt, characteristic);
    }

    private boolean handlePacket(byte packetType, byte[] payload)
    {
        if (packetType == DafitConstants.CMD_TRIGGER_MEASURE_HEARTRATE)
        {
            int heartRate = payload[0];
            Log.i("XXXXXXXX", "Measure heart rate finished: " + heartRate + " BPM");

            MT863ActivitySample sample = new MT863ActivitySample();
            sample.setTimestamp((int) (System.currentTimeMillis() / 1000));

            sample.setRawKind(MT863SampleProvider.ACTIVITY_SINGLE_MEASURE);

            sample.setSteps(ActivitySample.NOT_MEASURED);
            sample.setDistanceMeters(ActivitySample.NOT_MEASURED);
            sample.setCaloriesBurnt(ActivitySample.NOT_MEASURED);

            sample.setHeartRate(heartRate);
            sample.setBloodPressureSystolic(ActivitySample.NOT_MEASURED);
            sample.setBloodPressureDiastolic(ActivitySample.NOT_MEASURED);
            sample.setBloodOxidation(ActivitySample.NOT_MEASURED);

            addGBActivitySample(sample);
            broadcastSample(sample);

            if (realTimeHeartRate)
                onHeartRateTest();

            return true;
        }
        if (packetType == DafitConstants.CMD_TRIGGER_MEASURE_BLOOD_OXYGEN)
        {
            int percent = payload[0];
            Log.i("XXXXXXXX", "Measure blood oxygen finished: " + percent + "%");

            MT863ActivitySample sample = new MT863ActivitySample();
            sample.setTimestamp((int) (System.currentTimeMillis() / 1000));

            sample.setRawKind(MT863SampleProvider.ACTIVITY_SINGLE_MEASURE);

            sample.setSteps(ActivitySample.NOT_MEASURED);
            sample.setDistanceMeters(ActivitySample.NOT_MEASURED);
            sample.setCaloriesBurnt(ActivitySample.NOT_MEASURED);

            sample.setHeartRate(ActivitySample.NOT_MEASURED);
            sample.setBloodPressureSystolic(ActivitySample.NOT_MEASURED);
            sample.setBloodPressureDiastolic(ActivitySample.NOT_MEASURED);
            sample.setBloodOxidation(percent);

            addGBActivitySample(sample);
            broadcastSample(sample);

            return true;
        }
        if (packetType == DafitConstants.CMD_TRIGGER_MEASURE_BLOOD_PRESSURE)
        {
            int dataUnknown = payload[0];
            int data1 = payload[1];
            int data2 = payload[2];
            Log.i("XXXXXXXX", "Measure blood pressure finished: " + data1 + "/" + data2 + " (" + dataUnknown + ")");


            MT863ActivitySample sample = new MT863ActivitySample();
            sample.setTimestamp((int) (System.currentTimeMillis() / 1000));

            sample.setRawKind(MT863SampleProvider.ACTIVITY_SINGLE_MEASURE);

            sample.setSteps(ActivitySample.NOT_MEASURED);
            sample.setDistanceMeters(ActivitySample.NOT_MEASURED);
            sample.setCaloriesBurnt(ActivitySample.NOT_MEASURED);

            sample.setHeartRate(ActivitySample.NOT_MEASURED);
            sample.setBloodPressureSystolic(data1);
            sample.setBloodPressureDiastolic(data2);
            sample.setBloodOxidation(ActivitySample.NOT_MEASURED);

            addGBActivitySample(sample);
            broadcastSample(sample);

            return true;
        }

        if (packetType == DafitConstants.CMD_NOTIFY_PHONE_OPERATION)
        {
            byte operation = payload[0];
            if (operation == DafitConstants.ARG_OPERATION_PLAY_PAUSE)
            {
                GBDeviceEventMusicControl musicCmd = new GBDeviceEventMusicControl();
                musicCmd.event = GBDeviceEventMusicControl.Event.PLAYPAUSE;
                evaluateGBDeviceEvent(musicCmd);
                return true;
            }
            if (operation == DafitConstants.ARG_OPERATION_PREV_SONG)
            {
                GBDeviceEventMusicControl musicCmd = new GBDeviceEventMusicControl();
                musicCmd.event = GBDeviceEventMusicControl.Event.PREVIOUS;
                evaluateGBDeviceEvent(musicCmd);
                return true;
            }
            if (operation == DafitConstants.ARG_OPERATION_NEXT_SONG)
            {
                GBDeviceEventMusicControl musicCmd = new GBDeviceEventMusicControl();
                musicCmd.event = GBDeviceEventMusicControl.Event.NEXT;
                evaluateGBDeviceEvent(musicCmd);
                return true;
            }
            if (operation == DafitConstants.ARG_OPERATION_DROP_INCOMING_CALL)
            {
                GBDeviceEventCallControl callCmd = new GBDeviceEventCallControl();
                callCmd.event = GBDeviceEventCallControl.Event.REJECT;
                evaluateGBDeviceEvent(callCmd);
                return true;
            }
        }

        if (packetType == DafitConstants.CMD_SWITCH_CAMERA_VIEW)
        {
            // TODO: trigger camera photo
            return true;
        }

        LOG.warn("Unhandled packet " + packetType + ": " + Logging.formatBytes(payload));
        return false;
    }

    private void addGBActivitySample(MT863ActivitySample sample) {
        addGBActivitySamples(new MT863ActivitySample[] { sample });
    }

    private void addGBActivitySamples(MT863ActivitySample[] samples) {
        try (DBHandler dbHandler = GBApplication.acquireDB()) {
            User user = DBHelper.getUser(dbHandler.getDaoSession());
            Device device = DBHelper.getDevice(getDevice(), dbHandler.getDaoSession());

            MT863SampleProvider provider = new MT863SampleProvider(getDevice(), dbHandler.getDaoSession());

            for (MT863ActivitySample sample : samples) {
                sample.setDevice(device);
                sample.setUser(user);
                sample.setProvider(provider);
                provider.addGBActivitySample(sample);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            GB.toast(getContext(), "Error saving samples: " + ex.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
            GB.updateTransferNotification(null, "Data transfer failed", false, 0, getContext());
        }
    }

    private void broadcastSample(MT863ActivitySample sample) {
        Intent intent = new Intent(DeviceService.ACTION_REALTIME_SAMPLES)
            .putExtra(DeviceService.EXTRA_REALTIME_SAMPLE, sample)
            .putExtra(DeviceService.EXTRA_TIMESTAMP, sample.getTimestamp());
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
    }

    private void handleDeviceInfo(DeviceInfo info) {
        LOG.warn("Device info: " + info);
        versionCmd.hwVersion = info.getHardwareRevision();
        versionCmd.fwVersion = info.getSoftwareRevision();
        handleGBDeviceEvent(versionCmd);
    }

    private void handleBatteryInfo(BatteryInfo info) {
        LOG.warn("Battery info: " + info);
        batteryCmd.level = (short) info.getPercentCharged();
        handleGBDeviceEvent(batteryCmd);
    }

    @Override
    public boolean useAutoConnect() {
        return true;
    }

    private void sendNotification(byte type, String text)
    {
        try {
            TransactionBuilder builder = performInitialized("sendNotification");
            byte[] str = text.getBytes();
            byte[] payload = new byte[str.length + 1];
            payload[0] = type;
            System.arraycopy(str, 0, payload, 1, str.length);
            sendPacket(builder, DafitPacketOut.buildPacket(DafitConstants.CMD_SEND_MESSAGE, payload));
            builder.queue(getQueue());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onNotification(NotificationSpec notificationSpec) {
        String text = NotificationUtils.getPreferredTextFor(notificationSpec, 40, 40, getContext());
        if (text.isEmpty())
            text = StringUtils.getFirstOf(StringUtils.getFirstOf(notificationSpec.title, notificationSpec.subject), notificationSpec.body);

        sendNotification(DafitConstants.notificationType(notificationSpec.type), text);
    }

    @Override
    public void onDeleteNotification(int id) {
        // not supported :(
    }

    @Override
    public void onSetCallState(CallSpec callSpec) {
        if (callSpec.command == CallSpec.CALL_INCOMING)
            sendNotification(DafitConstants.NOTIFICATION_TYPE_CALL, NotificationUtils.getPreferredTextFor(callSpec));
        else
            sendNotification(DafitConstants.NOTIFICATION_TYPE_CALL_OFF_HOOK, "");
    }

    @Override
    public void onSetCannedMessages(CannedMessagesSpec cannedMessagesSpec) {
        // not supported :(
    }

    private void setTime(TransactionBuilder builder) {
        TimeZone tzGmt8 = TimeZone.getTimeZone("GMT+8"); // The watch is hardcoded to GMT+8 internally...
        TimeZone tzCurrent = TimeZone.getDefault();
        long timeInUTC = Calendar.getInstance().getTimeInMillis();
        long timeInGmt8 = timeInUTC - tzGmt8.getOffset(timeInUTC) + tzCurrent.getOffset(timeInUTC);

        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.putInt((int)(timeInGmt8 / 1000));
        buffer.put((byte)8); // I guess this means GMT+8 but changing it has no effect at all (it was hardcoded in the original app too)
        sendPacket(builder, DafitPacketOut.buildPacket(DafitConstants.CMD_SYNC_TIME, buffer.array()));
    }

    @Override
    public void onSetTime() {
        try {
            TransactionBuilder builder = performInitialized("onSetTime");
            setTime(builder);
            builder.queue(getQueue());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSetAlarms(ArrayList<? extends Alarm> alarms) {
        // TODO: set alarms
    }

    @Override
    public void onSetMusicState(MusicStateSpec stateSpec) {
        // not supported :(
    }

    @Override
    public void onSetMusicInfo(MusicSpec musicSpec) {
        // not supported :(
    }

    @Override
    public void onInstallApp(Uri uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onAppInfoReq() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onAppStart(UUID uuid, boolean start) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onAppDelete(UUID uuid) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onAppConfiguration(UUID appUuid, String config, Integer id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onAppReorder(UUID[] uuids) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onFetchRecordedData(int dataTypes) {
        if ((dataTypes & RecordedDataTypes.TYPE_ACTIVITY) != 0)
        {
            try {
                new FetchDataOperation(this).perform();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static int BytesToInt24(byte[] bArr) {
        if (bArr.length != 3)
            throw new IllegalArgumentException();
        return ((bArr[2] << 24) >>> 8) | ((bArr[1] << 8) & 0xFF00) | (bArr[0] & 0xFF);
    }

    private Runnable updateIdleStepsRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                updateIdleSteps();
            } finally {
                idleUpdateHandler.postDelayed(updateIdleStepsRunnable, IDLE_STEPS_INTERVAL);
            }
        }
    };

    private void updateIdleSteps()
    {
        // The steps value hasn't changed for a while, so the user is not moving
        // Store this information in the database to improve the averaging over long periods of time

        if (!getDevice().isConnected())
        {
            LOG.warn("updateIdleSteps but device not connected?!");
            return;
        }

        try (DBHandler dbHandler = GBApplication.acquireDB()) {
            User user = DBHelper.getUser(dbHandler.getDaoSession());
            Device device = DBHelper.getDevice(getDevice(), dbHandler.getDaoSession());

            MT863SampleProvider provider = new MT863SampleProvider(getDevice(), dbHandler.getDaoSession());

            int currentSampleTimestamp = (int)(Calendar.getInstance().getTimeInMillis() / 1000);

            MT863ActivitySample sample = new MT863ActivitySample();
            sample.setDevice(device);
            sample.setUser(user);
            sample.setProvider(provider);
            sample.setTimestamp(currentSampleTimestamp);

            sample.setRawKind(MT863SampleProvider.ACTIVITY_NOT_MEASURED);

            sample.setSteps(0);
            sample.setDistanceMeters(0);
            sample.setCaloriesBurnt(0);

            sample.setHeartRate(ActivitySample.NOT_MEASURED);
            sample.setBloodPressureSystolic(ActivitySample.NOT_MEASURED);
            sample.setBloodPressureDiastolic(ActivitySample.NOT_MEASURED);
            sample.setBloodOxidation(ActivitySample.NOT_MEASURED);

            provider.addGBActivitySample(sample);
            broadcastSample(sample);

            LOG.info("Adding an idle sample: " + sample.toString());
        } catch (Exception ex) {
            ex.printStackTrace();
            GB.toast(getContext(), "Error saving samples: " + ex.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
            GB.updateTransferNotification(null, "Data transfer failed", false, 0, getContext());
        }
    }

    public void handleStepsHistory(int daysAgo, byte[] data, boolean isRealtime)
    {
        if (data.length != 9)
            throw new IllegalArgumentException();

        byte[] bArr2 = new byte[3];
        System.arraycopy(data, 0, bArr2, 0, 3);
        int steps = BytesToInt24(bArr2);
        System.arraycopy(data, 3, bArr2, 0, 3);
        int distance = BytesToInt24(bArr2);
        System.arraycopy(data, 6, bArr2, 0, 3);
        int calories = BytesToInt24(bArr2);

        Log.i("steps[" + daysAgo + "]", "steps=" + steps + ", distance=" + distance + ", calories=" + calories);

        try (DBHandler dbHandler = GBApplication.acquireDB()) {
            User user = DBHelper.getUser(dbHandler.getDaoSession());
            Device device = DBHelper.getDevice(getDevice(), dbHandler.getDaoSession());

            MT863SampleProvider provider = new MT863SampleProvider(getDevice(), dbHandler.getDaoSession());

            Calendar thisSample = Calendar.getInstance();
            if (daysAgo != 0)
            {
                thisSample.add(Calendar.DATE, -daysAgo);
                thisSample.set(Calendar.HOUR_OF_DAY, 23);
                thisSample.set(Calendar.MINUTE, 59);
                thisSample.set(Calendar.SECOND, 59);
                thisSample.set(Calendar.MILLISECOND, 999);
            }
            else
            {
                // no change needed - use current time
            }

            Calendar startOfDay = (Calendar) thisSample.clone();
            startOfDay.set(Calendar.HOUR_OF_DAY, 0);
            startOfDay.set(Calendar.MINUTE, 0);
            startOfDay.set(Calendar.SECOND, 0);
            startOfDay.set(Calendar.MILLISECOND, 0);

            int startOfDayTimestamp = (int) (startOfDay.getTimeInMillis() / 1000);
            int thisSampleTimestamp = (int) (thisSample.getTimeInMillis() / 1000);

            int previousSteps = 0;
            int previousDistance = 0;
            int previousCalories = 0;
            for (MT863ActivitySample sample : provider.getAllActivitySamples(startOfDayTimestamp, thisSampleTimestamp))
            {
                if (sample.getSteps() != ActivitySample.NOT_MEASURED)
                    previousSteps += sample.getSteps();
                if (sample.getDistanceMeters() != ActivitySample.NOT_MEASURED)
                    previousDistance += sample.getDistanceMeters();
                if (sample.getCaloriesBurnt() != ActivitySample.NOT_MEASURED)
                    previousCalories += sample.getCaloriesBurnt();
            }

            int newSteps = steps - previousSteps;
            int newDistance = distance - previousDistance;
            int newCalories = calories - previousCalories;

            if (newSteps < 0 || newDistance < 0 || newCalories < 0)
            {
                LOG.warn("Ignoring a sample that would generate negative values: steps += " + newSteps + ", distance +=" + newDistance + ", calories += " + newCalories);
            }
            else if (newSteps != 0 || newDistance != 0 || newCalories != 0 || daysAgo == 0)
            {
                MT863ActivitySample sample = new MT863ActivitySample();
                sample.setDevice(device);
                sample.setUser(user);
                sample.setProvider(provider);
                sample.setTimestamp(thisSampleTimestamp);

                sample.setRawKind(MT863SampleProvider.ACTIVITY_STEPS);

                sample.setSteps(newSteps);
                sample.setDistanceMeters(newDistance);
                sample.setCaloriesBurnt(newCalories);

                sample.setHeartRate(ActivitySample.NOT_MEASURED);
                sample.setBloodPressureSystolic(ActivitySample.NOT_MEASURED);
                sample.setBloodPressureDiastolic(ActivitySample.NOT_MEASURED);
                sample.setBloodOxidation(ActivitySample.NOT_MEASURED);

                provider.addGBActivitySample(sample);
                if (isRealtime)
                {
                    idleUpdateHandler.removeCallbacks(updateIdleStepsRunnable);
                    idleUpdateHandler.postDelayed(updateIdleStepsRunnable, IDLE_STEPS_INTERVAL);
                    broadcastSample(sample);
                }

                LOG.info("Adding a sample: " + sample.toString());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            GB.toast(getContext(), "Error saving samples: " + ex.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
            GB.updateTransferNotification(null, "Data transfer failed", false, 0, getContext());
        }
    }

    public void handleSleepHistory(int daysAgo, byte[] data)
    {
        if (data.length % 3 != 0)
            throw new IllegalArgumentException();

        int prevActivityType = MT863SampleProvider.ACTIVITY_SLEEP_START;

        for(int i = 0; i < data.length / 3; i++)
        {
            int type = data[3*i];
            int start_h = data[3*i + 1];
            int start_m = data[3*i + 2];

            Log.i("sleep[" + daysAgo + "][" + i + "]", "type=" + type + ", start_h=" + start_h + ", start_m=" + start_m);

            // SleepAnalysis measures sleep fragment type by marking the END of the fragment.
            // The watch provides data by marking the START of the fragment.

            // Additionally, ActivityAnalysis (used by the weekly view...) does AVERAGING when
            // adjacent samples are not of the same type..

            // FIXME: The way Gadgetbridge does it seems kinda broken...

            // This means that we have to convert the data when importing. Each sample gets
            // converted to two samples - one marking the beginning of the segment, and another
            // marking the end.

            // Watch:           SLEEP_LIGHT       ...       SLEEP_DEEP       ...      SLEEP_LIGHT        ...       SLEEP_SOBER
            // Gadgetbridge: ANYTHING,SLEEP_LIGHT ... SLEEP_LIGHT,SLEEP_DEEP ... SLEEP_DEEP,SLEEP_LIGHT  ... SLEEP_LIGHT,ANYTHING
            //                       ^     ^- this is important, it MUST be sleep, to ensure proper detection
            //  Time since the last -|        of sleepStart, see SleepAnalysis.calculateSleepSessions
            //  sample must be 0
            //  (otherwise SleepAnalysis will include this fragment...)

            // This means that when inserting samples:
            // * every sample is converted to (previous_sample_type, current_sample_type) happening
            //   roughly at the same time (but in this order)
            // * the first sample is prefixed by unspecified activity
            // * the last sample (SOBER) is converted to unspecified activity

            try (DBHandler dbHandler = GBApplication.acquireDB()) {
                User user = DBHelper.getUser(dbHandler.getDaoSession());
                Device device = DBHelper.getDevice(getDevice(), dbHandler.getDaoSession());

                MT863SampleProvider provider = new MT863SampleProvider(getDevice(), dbHandler.getDaoSession());

                Calendar thisSample = Calendar.getInstance();
                thisSample.add(Calendar.HOUR_OF_DAY, 4); // the clock assumes the sleep day changes at 20:00, so move the time forward to make the day correct
                thisSample.set(Calendar.MINUTE, 0);
                thisSample.add(Calendar.DATE, -daysAgo);

                thisSample.set(Calendar.HOUR_OF_DAY, start_h);
                thisSample.set(Calendar.MINUTE, start_m);
                thisSample.set(Calendar.SECOND, 0);
                thisSample.set(Calendar.MILLISECOND, 0);
                int thisSampleTimestamp = (int) (thisSample.getTimeInMillis() / 1000);

                int activityType;
                if (type == DafitConstants.SLEEP_SOBER)
                    activityType = MT863SampleProvider.ACTIVITY_SLEEP_END;
                else if (type == DafitConstants.SLEEP_LIGHT)
                    activityType = MT863SampleProvider.ACTIVITY_SLEEP_LIGHT;
                else if (type == DafitConstants.SLEEP_RESTFUL)
                    activityType = MT863SampleProvider.ACTIVITY_SLEEP_RESTFUL;
                else
                    throw new IllegalArgumentException("Invalid sleep type");

                // Insert the end of previous segment sample
                MT863ActivitySample prevSegmentSample = new MT863ActivitySample();
                prevSegmentSample.setDevice(device);
                prevSegmentSample.setUser(user);
                prevSegmentSample.setProvider(provider);
                prevSegmentSample.setTimestamp(thisSampleTimestamp - 1);

                prevSegmentSample.setRawKind(prevActivityType);

                prevSegmentSample.setSteps(ActivitySample.NOT_MEASURED);
                prevSegmentSample.setDistanceMeters(ActivitySample.NOT_MEASURED);
                prevSegmentSample.setCaloriesBurnt(ActivitySample.NOT_MEASURED);

                prevSegmentSample.setHeartRate(ActivitySample.NOT_MEASURED);
                prevSegmentSample.setBloodPressureSystolic(ActivitySample.NOT_MEASURED);
                prevSegmentSample.setBloodPressureDiastolic(ActivitySample.NOT_MEASURED);
                prevSegmentSample.setBloodOxidation(ActivitySample.NOT_MEASURED);

                addGBActivitySampleIfNotExists(provider, prevSegmentSample);

                // Insert the start of new segment sample
                MT863ActivitySample nextSegmentSample = new MT863ActivitySample();
                nextSegmentSample.setDevice(device);
                nextSegmentSample.setUser(user);
                nextSegmentSample.setProvider(provider);
                nextSegmentSample.setTimestamp(thisSampleTimestamp);

                nextSegmentSample.setRawKind(activityType);

                nextSegmentSample.setSteps(ActivitySample.NOT_MEASURED);
                nextSegmentSample.setDistanceMeters(ActivitySample.NOT_MEASURED);
                nextSegmentSample.setCaloriesBurnt(ActivitySample.NOT_MEASURED);

                nextSegmentSample.setHeartRate(ActivitySample.NOT_MEASURED);
                nextSegmentSample.setBloodPressureSystolic(ActivitySample.NOT_MEASURED);
                nextSegmentSample.setBloodPressureDiastolic(ActivitySample.NOT_MEASURED);
                nextSegmentSample.setBloodOxidation(ActivitySample.NOT_MEASURED);

                addGBActivitySampleIfNotExists(provider, nextSegmentSample);

                prevActivityType = activityType;
            } catch (Exception ex) {
                ex.printStackTrace();
                GB.toast(getContext(), "Error saving samples: " + ex.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
                GB.updateTransferNotification(null, "Data transfer failed", false, 0, getContext());
            }
        }
    }

    private void addGBActivitySampleIfNotExists(MT863SampleProvider provider, MT863ActivitySample sample)
    {
        boolean alreadyHaveThisSample = false;
        for (MT863ActivitySample sample2 : provider.getAllActivitySamples(sample.getTimestamp() - 1, sample.getTimestamp() + 1))
        {
            if (sample2.getTimestamp() == sample2.getTimestamp() && sample2.getRawKind() == sample.getRawKind())
                alreadyHaveThisSample = true;
        }

        if (!alreadyHaveThisSample)
        {
            provider.addGBActivitySample(sample);
            LOG.info("Adding a sample: " + sample.toString());
        }
    }

    @Override
    public void onReset(int flags) {
        // TODO: this shuts down the watch, rather than rebooting it - perhaps add a new operation type?
        // (reboot is not supported, btw)

        try {
            TransactionBuilder builder = performInitialized("shutdown");
            sendPacket(builder, DafitPacketOut.buildPacket(DafitConstants.CMD_SHUTDOWN, new byte[] { -1 }));
            builder.queue(getQueue());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void triggerHeartRateTest(boolean start)
    {
        try {
            TransactionBuilder builder = performInitialized("onHeartRateTest");
            sendPacket(builder, DafitPacketOut.buildPacket(DafitConstants.CMD_TRIGGER_MEASURE_HEARTRATE, new byte[] { start ? (byte)0 : (byte)-1 }));
            builder.queue(getQueue());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onHeartRateTest() {
        triggerHeartRateTest(true);
    }

    public void onAbortHeartRateTest() {
        triggerHeartRateTest(false);
    }

    // TODO: starting other tests

    @Override
    public void onEnableRealtimeSteps(boolean enable) {
        // enabled all the time :D that's the only way to get more than a daily sum from this watch...
    }

    @Override
    public void onEnableRealtimeHeartRateMeasurement(boolean enable) {
        if (realTimeHeartRate == enable)
            return;
        realTimeHeartRate = enable; // will do another measurement immediately
        if (realTimeHeartRate)
            onHeartRateTest();
        else
            onAbortHeartRateTest();
    }

    @Override
    public void onSetHeartRateMeasurementInterval(int seconds) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onEnableHeartRateSleepSupport(boolean enable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onFindDevice(boolean start) {
        if (start)
        {
            try {
                TransactionBuilder builder = performInitialized("onFindDevice");
                sendPacket(builder, DafitPacketOut.buildPacket(DafitConstants.CMD_FIND_MY_WATCH, new byte[0]));
                builder.queue(getQueue());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else
        {
            // Not supported - the device vibrates three times and then stops automatically
        }
    }

    @Override
    public void onSetConstantVibration(int integer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onScreenshotReq() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onAddCalendarEvent(CalendarEventSpec calendarEventSpec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onDeleteCalendarEvent(byte type, long id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onSendConfiguration(String config) {
        // TODO
    }

    @Override
    public void onReadConfiguration(String config) {
        // TODO
    }

    @Override
    public void onTestNewFunction() {
    }

    @Override
    public void onSendWeather(WeatherSpec weatherSpec) {
        try {
            TransactionBuilder builder = performInitialized("onSendWeather");

            DafitWeatherToday weatherToday = new DafitWeatherToday(weatherSpec);
            ByteBuffer packetWeatherToday = ByteBuffer.allocate(weatherToday.pm25 != null ? 21 : 19);
            packetWeatherToday.put(weatherToday.pm25 != null ? (byte)1 : (byte)0);
            packetWeatherToday.put(weatherToday.conditionId);
            packetWeatherToday.put(weatherToday.currentTemp);
            if (weatherToday.pm25 != null)
                packetWeatherToday.putShort(weatherToday.pm25);
            packetWeatherToday.put(weatherToday.lunar_or_festival.getBytes("unicodebigunmarked"));
            packetWeatherToday.put(weatherToday.city.getBytes("unicodebigunmarked"));
            sendPacket(builder, DafitPacketOut.buildPacket(DafitConstants.CMD_SET_WEATHER_TODAY, packetWeatherToday.array()));

            ByteBuffer packetWeatherForecast = ByteBuffer.allocate(7 * 3);
            for(int i = 0; i < 7; i++)
            {
                DafitWeatherForecast forecast;
                if (weatherSpec.forecasts.size() > i)
                    forecast = new DafitWeatherForecast(weatherSpec.forecasts.get(i));
                else
                    forecast = new DafitWeatherForecast(DafitConstants.WEATHER_HAZE, (byte)-100, (byte)-100); // I don't think there is a way to send less (my watch shows only tomorrow anyway...)
                packetWeatherForecast.put(forecast.conditionId);
                packetWeatherForecast.put(forecast.minTemp);
                packetWeatherForecast.put(forecast.maxTemp);
            }
            sendPacket(builder, DafitPacketOut.buildPacket(DafitConstants.CMD_SET_WEATHER_FUTURE, packetWeatherForecast.array()));

            builder.queue(getQueue());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSetFmFrequency(float frequency) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onSetLedColor(int color) {
        throw new UnsupportedOperationException();
    }
}
