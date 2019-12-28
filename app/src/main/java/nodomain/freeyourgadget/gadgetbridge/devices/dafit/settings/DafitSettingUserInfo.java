package nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings;

import org.apache.commons.lang3.NotImplementedException;

import java.nio.ByteBuffer;

import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;

public class DafitSettingUserInfo extends DafitSetting<ActivityUser> {
    public DafitSettingUserInfo(String name, byte cmdSet) {
        super(name, (byte)-1, cmdSet);
    }

    @Override
    public byte[] encode(ActivityUser value) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.put((byte)value.getHeightCm());
        buffer.put((byte)value.getWeightKg());
        buffer.put((byte)value.getAge());
        buffer.put((byte)value.getGender());
        return buffer.array();
    }

    @Override
    public ActivityUser decode(byte[] data) {
        throw new NotImplementedException("decode");
    }
}
