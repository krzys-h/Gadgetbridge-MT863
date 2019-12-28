package nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings;

public class DafitSettingBool extends DafitSetting<Boolean> {
    public DafitSettingBool(String name, byte cmdQuery, byte cmdSet) {
        super(name, cmdQuery, cmdSet);
    }

    @Override
    public byte[] encode(Boolean value) {
        return new byte[] { value ? (byte)1 : (byte)0 };
    }

    @Override
    public Boolean decode(byte[] data) {
        if (data.length != 1)
            throw new IllegalArgumentException("Wrong data length, should be 1, was " + data.length);
        if (data[0] != 0 && data[0] != 1)
            throw new IllegalArgumentException("Expected a boolean, got " + data[0]);
        return data[0] != 0;
    }
}
