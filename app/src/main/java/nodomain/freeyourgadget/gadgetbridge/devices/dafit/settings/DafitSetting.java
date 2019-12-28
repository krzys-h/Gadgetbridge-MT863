package nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings;

public abstract class DafitSetting<T> {
    public final String name; // TODO
    public final byte cmdQuery;
    public final byte cmdSet;

    public DafitSetting(String name, byte cmdQuery, byte cmdSet) {
        this.name = name;
        this.cmdQuery = cmdQuery;
        this.cmdSet = cmdSet;
    }

    public abstract byte[] encode(T value);
    public abstract T decode(byte[] data);
}
