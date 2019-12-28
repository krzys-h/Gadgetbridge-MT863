package nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class DafitSettingTimeRange extends DafitSetting<DafitSettingTimeRange.TimeRange> {
    public static class TimeRange {
        public byte start_h;
        public byte start_m;
        public byte end_h;
        public byte end_m;

        public TimeRange() {
        }

        public TimeRange(byte start_h, byte start_m, byte end_h, byte end_m) {
            this.start_h = start_h;
            this.start_m = start_m;
            this.end_h = end_h;
            this.end_m = end_m;
        }

        @Override
        public String toString() {
            return "TimeRange{" +
                "start_h=" + start_h +
                ", start_m=" + start_m +
                ", end_h=" + end_h +
                ", end_m=" + end_m +
                '}';
        }
    }

    public DafitSettingTimeRange(String name, byte cmdQuery, byte cmdSet) {
        super(name, cmdQuery, cmdSet);
    }

    // Yes, these are different. Was somebody drunk when designing this?

    @Override
    public byte[] encode(TimeRange value) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.put(value.start_h);
        buffer.put(value.start_m);
        buffer.put(value.end_h);
        buffer.put(value.end_m);
        return buffer.array();
    }

    @Override
    public TimeRange decode(byte[] data) {
        if (data.length != 4)
            throw new IllegalArgumentException("Wrong data length, should be 4, was " + data.length);
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        short start = buffer.getShort();
        short end = buffer.getShort();
        return new TimeRange((byte)(start / 60), (byte)(start % 60), (byte)(end / 60), (byte)(start % 60));
    }
}
