package nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings;

public enum DafitEnumDominantHand implements DafitEnum {
    LEFT_HAND((byte)0),
    RIGHT_HAND((byte)1);

    public final byte value;

    DafitEnumDominantHand(byte value) {
        this.value = value;
    }

    @Override
    public byte value() {
        return value;
    }
}
