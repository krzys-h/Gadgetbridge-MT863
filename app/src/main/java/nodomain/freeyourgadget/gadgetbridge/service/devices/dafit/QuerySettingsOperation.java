package nodomain.freeyourgadget.gadgetbridge.service.devices.dafit;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;
import android.util.Pair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.devices.dafit.DafitConstants;
import nodomain.freeyourgadget.gadgetbridge.devices.dafit.MT863DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings.DafitSetting;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEOperation;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.devices.miband.operations.OperationStatus;
import nodomain.freeyourgadget.gadgetbridge.util.DeviceHelper;

public class QuerySettingsOperation extends AbstractBTLEOperation<MT863DeviceSupport> {

    private static final Logger LOG = LoggerFactory.getLogger(QuerySettingsOperation.class);

    private final DafitSetting[] settingsToQuery;
    private boolean[] received;

    private DafitPacketIn packetIn = new DafitPacketIn();

    public QuerySettingsOperation(MT863DeviceSupport support, DafitSetting[] settingsToQuery) {
        super(support);
        this.settingsToQuery = settingsToQuery;
    }

    public QuerySettingsOperation(MT863DeviceSupport support) {
        super(support);
        MT863DeviceCoordinator coordinator = (MT863DeviceCoordinator) DeviceHelper.getInstance().getCoordinator(getDevice());
        this.settingsToQuery = coordinator.getSupportedSettings();
    }

    @Override
    protected void prePerform() {
        getDevice().setBusyTask("Querying settings"); // mark as busy quickly to avoid interruptions from the outside
        getDevice().sendDeviceUpdateIntent(getContext());
    }

    @Override
    protected void doPerform() throws IOException {
        received = new boolean[settingsToQuery.length];
        TransactionBuilder builder = performInitialized("QuerySettingsOperation");
        for (DafitSetting setting : settingsToQuery)
        {
            if (setting.cmdQuery == -1)
                continue;

            getSupport().sendPacket(builder, DafitPacketOut.buildPacket(setting.cmdQuery, new byte[0]));
        }
        builder.queue(getQueue());
    }

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (!isOperationRunning())
        {
            LOG.error("onCharacteristicChanged but operation is not running!");
        }
        else
        {
            UUID charUuid = characteristic.getUuid();
            if (charUuid.equals(DafitConstants.UUID_CHARACTERISTIC_DATA_IN))
            {
                if (packetIn.putFragment(characteristic.getValue())) {
                    Pair<Byte, byte[]> packet = DafitPacketIn.parsePacket(packetIn.getPacket());
                    packetIn = new DafitPacketIn();
                    if (packet != null) {
                        byte packetType = packet.first;
                        byte[] payload = packet.second;

                        if (handlePacket(packetType, payload))
                            return true;
                    }
                }
            }
        }

        return super.onCharacteristicChanged(gatt, characteristic);
    }

    private boolean handlePacket(byte packetType, byte[] payload) {
        boolean handled = false;
        boolean receivedEverything = true;
        for(int i = 0; i < settingsToQuery.length; i++)
        {
            DafitSetting setting = settingsToQuery[i];
            if (setting.cmdQuery == -1)
                continue;
            if (setting.cmdQuery == packetType)
            {
                Object value = setting.decode(payload);
                Log.i("SETTING QUERY", setting.name + " = " + value.toString());
                received[i] = true;
                handled = true;
            }
            else if (!received[i])
                receivedEverything = false;
        }
        if (receivedEverything)
            operationFinished();

        return handled;
    }

    @Override
    protected void operationFinished() {
        operationStatus = OperationStatus.FINISHED;
        if (getDevice() != null && getDevice().isConnected()) {
            unsetBusy();
        }
    }
}
