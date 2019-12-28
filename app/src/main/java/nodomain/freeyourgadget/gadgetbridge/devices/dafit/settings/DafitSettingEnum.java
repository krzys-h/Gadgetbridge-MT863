package nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings;

public class DafitSettingEnum<T extends Enum <?> & DafitEnum> extends DafitSetting<T> {
    protected final Class<T> clazz;

    public DafitSettingEnum(String name, byte cmdQuery, byte cmdSet, Class<T> clazz) {
        super(name, cmdQuery, cmdSet);
        this.clazz = clazz;
    }

    public T findByValue(byte value)
    {
        for (T e : clazz.getEnumConstants()) {
            if (e.value() == value) {
                return e;
            }
        }

        throw new IllegalArgumentException("No enum value for " + value);
    }

    @Override
    public byte[] encode(T value) {
        return new byte[] { value.value() };
    }

    @Override
    public T decode(byte[] data) {
        if (data.length != 1)
            throw new IllegalArgumentException("Wrong data length, should be 1, was " + data.length);

        return findByValue(data[0]);
    }

    public T[] decodeSupportedValues(byte[] data) {
        return clazz.getEnumConstants();
    }
}
