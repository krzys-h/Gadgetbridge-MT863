package nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings;

public class DafitSettingByte extends DafitSetting<Byte> {
    public DafitSettingByte(String name, byte cmdQuery, byte cmdSet) {
        super(name, cmdQuery, cmdSet);
    }

    @Override
    public byte[] encode(Byte value) {
        return new byte[] { value };
    }

    @Override
    public Byte decode(byte[] data) {
        if (data.length != 1)
            throw new IllegalArgumentException("Wrong data length, should be 1, was " + data.length);
        return data[0];
    }
}
