package nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class DafitSettingInt extends DafitSetting<Integer> {
    public DafitSettingInt(String name, byte cmdQuery, byte cmdSet) {
        super(name, cmdQuery, cmdSet);
    }

    @Override
    public byte[] encode(Integer value) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.BIG_ENDIAN); // <- this is what happens when somebody in China designs a communication protocol
        buffer.putInt(value);
        return buffer.array();
    }

    @Override
    public Integer decode(byte[] data) {
        if (data.length != 4)
            throw new IllegalArgumentException("Wrong data length, should be 4, was " + data.length);
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN); // <- yes, it's different here
        return buffer.getInt();
    }
}
