package nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings;

public enum DafitEnumTimeSystem implements DafitEnum {
    TIME_SYSTEM_12((byte)0),
    TIME_SYSTEM_24((byte)1);

    public final byte value;

    DafitEnumTimeSystem(byte value) {
        this.value = value;
    }

    @Override
    public byte value() {
        return value;
    }
}
