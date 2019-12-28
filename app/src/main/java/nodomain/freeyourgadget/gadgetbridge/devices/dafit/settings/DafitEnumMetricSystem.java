package nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings;

public enum DafitEnumMetricSystem implements DafitEnum {
    METRIC_SYSTEM((byte)0),
    IMPERIAL_SYSTEM((byte)1);

    public final byte value;

    DafitEnumMetricSystem(byte value) {
        this.value = value;
    }

    @Override
    public byte value() {
        return value;
    }
}
