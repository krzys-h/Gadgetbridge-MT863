package nodomain.freeyourgadget.gadgetbridge.service.devices.dafit;

import androidx.annotation.NonNull;

/**
 * A class for handling fragmentation of outgoing packets<br>
 * <br>
 * Usage:
 * <pre>
 * {@code
 * DafitPacketOut packetOut = new DafitPacketOut(DafitPacketOut.buildPacket(type, payload));
 * byte[] fragment = new byte[MTU];
 * while(packetOut.getFragment(fragment))
 *     send(fragment);
 * }
 * </pre>
 */
public class DafitPacketOut extends DafitPacket {
    public DafitPacketOut(byte[] packet)
    {
        this.packet = packet;
    }

    /**
     * Get the next fragment of this packet to be sent
     *
     * @param fragmentBuffer The buffer to store the output in, of desired size (i.e. == MTU)
     * @return true if there is more data to be sent, false otherwise
     */
    public boolean getFragment(byte[] fragmentBuffer)
    {
        if (position >= packet.length)
            return false;
        int remainingToTransfer = Math.min(fragmentBuffer.length, packet.length - position);
        System.arraycopy(packet, position, fragmentBuffer, 0, remainingToTransfer);
        position += remainingToTransfer;
        return true;
    }

    /**
     * Encode the packet
     * @param packetType The packet type
     * @param payload The packet payload
     * @return The encoded packet
     */
    public static byte[] buildPacket(byte packetType, @NonNull byte[] payload)
    {
        byte[] packet = new byte[payload.length + 5];
        packet[0] = (byte)0xFE;
        packet[1] = (byte)0xEA;
        if (MT863DeviceSupport.MTU == 20)
        {
            packet[2] = 16;
            packet[3] = (byte)(packet.length & 0xFF);
        }
        else
        {
            packet[2] = (byte)(32 + (packet.length >> 8) & 0xFF);
            packet[3] = (byte)(packet.length & 0xFF);
        }
        packet[4] = packetType;
        System.arraycopy(payload, 0, packet, 5, payload.length);
        return packet;
    }
}
