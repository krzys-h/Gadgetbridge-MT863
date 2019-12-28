package nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings;

public enum DafitEnumDeviceVersion implements DafitEnum {
    CHINESE_EDITION((byte)0),
    INTERNATIONAL_EDITION((byte)1);

    public final byte value;

    DafitEnumDeviceVersion(byte value) {
        this.value = value;
    }

    @Override
    public byte value() {
        return value;
    }
}
