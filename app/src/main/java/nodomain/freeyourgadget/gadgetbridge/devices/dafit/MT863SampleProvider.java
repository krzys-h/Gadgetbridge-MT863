package nodomain.freeyourgadget.gadgetbridge.devices.dafit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.MT863ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.MT863ActivitySampleDao;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;

public class MT863SampleProvider extends AbstractSampleProvider<MT863ActivitySample> {
    public static int ACTIVITY_NOT_MEASURED = ActivitySample.NOT_MEASURED;
    public static int ACTIVITY_SINGLE_MEASURE = 0;
    public static int ACTIVITY_STEPS = 1;
    public static int ACTIVITY_SLEEP_LIGHT = 2;
    public static int ACTIVITY_SLEEP_RESTFUL = 3;
    public static int ACTIVITY_SLEEP_START = 4;
    public static int ACTIVITY_SLEEP_END = 5;

    public MT863SampleProvider(GBDevice device, DaoSession session) {
        super(device, session);
    }

    @Override
    public AbstractDao<MT863ActivitySample, ?> getSampleDao() {
        return getSession().getMT863ActivitySampleDao();
    }

    @NonNull
    @Override
    protected Property getTimestampSampleProperty() {
        return MT863ActivitySampleDao.Properties.Timestamp;
    }

    @Nullable
    @Override
    protected Property getRawKindSampleProperty() {
        return MT863ActivitySampleDao.Properties.RawKind;
    }

    @NonNull
    @Override
    protected Property getDeviceIdentifierSampleProperty() {
        return MT863ActivitySampleDao.Properties.DeviceId;
    }

    @Override
    public MT863ActivitySample createActivitySample() {
        return new MT863ActivitySample();
    }

    @Override
    public int normalizeType(int rawType) {
        if (rawType == ACTIVITY_NOT_MEASURED)
            return ActivityKind.TYPE_NOT_MEASURED;
        else if (rawType == ACTIVITY_SLEEP_LIGHT)
            return ActivityKind.TYPE_LIGHT_SLEEP;
        else if (rawType == ACTIVITY_SLEEP_RESTFUL)
            return ActivityKind.TYPE_DEEP_SLEEP;
        else if (rawType == ACTIVITY_SLEEP_START || rawType == ACTIVITY_SLEEP_END)
            return ActivityKind.TYPE_ACTIVITY; // this is needed to get the charts correct - we can't use NOT_MEASURED
        else if (rawType == ACTIVITY_STEPS)
            return ActivityKind.TYPE_ACTIVITY;
        else if (rawType == ACTIVITY_SINGLE_MEASURE)
            return ActivityKind.TYPE_NOT_MEASURED;
        else
            throw new IllegalArgumentException("Invalid raw type: " + rawType);
    }

    @Override
    public int toRawActivityKind(int activityKind) {
        if (activityKind == ActivityKind.TYPE_NOT_MEASURED)
            return ACTIVITY_NOT_MEASURED;
        else if (activityKind == ActivityKind.TYPE_LIGHT_SLEEP)
            return ACTIVITY_SLEEP_LIGHT;
        else if (activityKind == ActivityKind.TYPE_DEEP_SLEEP)
            return ACTIVITY_SLEEP_RESTFUL;
        else if (activityKind == ActivityKind.TYPE_ACTIVITY)
            return ACTIVITY_STEPS;
        else
            throw new IllegalArgumentException("Invalid Gadgetbridge activity kind: " + activityKind);
    }

    @Override
    public float normalizeIntensity(int rawIntensity) {
        if (rawIntensity == ActivitySample.NOT_MEASURED)
            return Float.NEGATIVE_INFINITY;
        else
            return rawIntensity;
    }
}