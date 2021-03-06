/*  Copyright (C) 2019 krzys_h

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.dafit;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.Logging;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.dafit.DaFitConstants;
import nodomain.freeyourgadget.gadgetbridge.devices.dafit.DaFitSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.DaFitActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEOperation;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.devices.miband.operations.OperationStatus;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class TrainingFinishedDataOperation extends AbstractBTLEOperation<DaFitDeviceSupport> {

    private static final Logger LOG = LoggerFactory.getLogger(FetchDataOperation.class);

    private final byte[] firstPacketData;
    private final long firstPacketTimeInMillis;
    ByteArrayOutputStream data = new ByteArrayOutputStream();

    private DaFitPacketIn packetIn = new DaFitPacketIn();

    public TrainingFinishedDataOperation(DaFitDeviceSupport support, byte[] firstPacketData) {
        super(support);
        this.firstPacketData = firstPacketData;
        this.firstPacketTimeInMillis = Calendar.getInstance().getTimeInMillis();
    }

    @Override
    protected void prePerform() {
        getDevice().setBusyTask(getContext().getString(R.string.busy_task_fetch_training_data));
        getDevice().sendDeviceUpdateIntent(getContext());
    }

    @Override
    protected void doPerform() {
        GB.updateTransferNotification(null, getContext().getString(R.string.busy_task_fetch_training_data), true, 0, getContext());
        handleTrainingHealthRatePacket(firstPacketData, true);
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
            if (charUuid.equals(DaFitConstants.UUID_CHARACTERISTIC_DATA_IN))
            {
                if (packetIn.putFragment(characteristic.getValue())) {
                    Pair<Byte, byte[]> packet = DaFitPacketIn.parsePacket(packetIn.getPacket());
                    packetIn = new DaFitPacketIn();
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
        if (packetType == DaFitConstants.CMD_QUERY_LAST_DYNAMIC_RATE) {
            handleTrainingHealthRatePacket(payload, false);
            return true;
        }
        if (packetType == DaFitConstants.CMD_QUERY_MOVEMENT_HEART_RATE) {
            handleTrainingSummaryDataPacket(payload);
            return true;
        }
        return false;
    }


    private void handleTrainingHealthRatePacket(byte[] payload, boolean isFirst) {
        Log.i("TRAINING DATA", "data: " + Logging.formatBytes(payload));
        byte sequenceType = payload[0];
        if (isFirst != (sequenceType == DaFitConstants.ARG_TRANSMISSION_FIRST))
            throw new IllegalArgumentException("Expected packet to be " + (isFirst ? "first" : "continued") + " but got packet of type " + sequenceType);
        if (sequenceType == DaFitConstants.ARG_TRANSMISSION_LAST && payload.length > 1)
            throw new IllegalArgumentException("Last packet shouldn't have any data");

        data.write(payload, 1, payload.length - 1);

        if (sequenceType != DaFitConstants.ARG_TRANSMISSION_LAST)
            queryMoreData();
        else
            processAllData();
    }

    private void queryMoreData() {
        try {
            TransactionBuilder builder = performInitialized("TrainingFinishedDataOperation");
            getSupport().sendPacket(builder, DaFitPacketOut.buildPacket(DaFitConstants.CMD_QUERY_LAST_DYNAMIC_RATE, new byte[0]));
            builder.queue(getQueue());
        } catch (IOException e) {
            e.printStackTrace();
            GB.toast(getContext(), "Error fetching training data: " + e.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
            GB.updateTransferNotification(null, "Data transfer failed", false, 0, getContext());
            operationFinished();
        }
    }

    private void processAllData() {
        byte[] completeData = data.toByteArray();
        Log.i("HAVE COMPLETE DATA", Logging.formatBytes(completeData));
        ByteBuffer dataBuffer = ByteBuffer.wrap(completeData);
        dataBuffer.order(ByteOrder.LITTLE_ENDIAN);

        Calendar dateRecorded = Calendar.getInstance();
        dateRecorded.setTime(DaFitConstants.WatchTimeToLocalTime(dataBuffer.getInt()));

        // NOTE: The first sample always matches dateRecorded (which is aligned to the minute)
        //       The last sample is saved at the moment the recording is stopped (and this code starts executing)

        try (DBHandler dbHandler = GBApplication.acquireDB()) {
            User user = DBHelper.getUser(dbHandler.getDaoSession());
            Device device = DBHelper.getDevice(getDevice(), dbHandler.getDaoSession());

            DaFitSampleProvider provider = new DaFitSampleProvider(getDevice(), dbHandler.getDaoSession());

            Log.i("START DATE", dateRecorded.getTime().toString());
            while (dataBuffer.hasRemaining())
            {
                int measurement = dataBuffer.get() & 0xFF;
                if (!dataBuffer.hasRemaining())
                    dateRecorded.setTimeInMillis(firstPacketTimeInMillis); // the last sample is captured exactly at the end of measurement

                Log.i("MEASUREMENT", "at " + dateRecorded.getTime().toString() + " was " + measurement);

                DaFitActivitySample sample = new DaFitActivitySample();
                sample.setDevice(device);
                sample.setUser(user);
                sample.setProvider(provider);
                sample.setTimestamp((int)(dateRecorded.getTimeInMillis() / 1000));

                sample.setRawKind(DaFitSampleProvider.ACTIVITY_NOT_MEASURED); // Training type will be taken later from CMD_QUERY_MOVEMENT_HEART_RATE (it's not present in the main data packet)
                sample.setDataSource(DaFitSampleProvider.SOURCE_TRAINING_HEARTRATE);

                sample.setBatteryLevel(ActivitySample.NOT_MEASURED);
                sample.setSteps(ActivitySample.NOT_MEASURED);
                sample.setDistanceMeters(ActivitySample.NOT_MEASURED);
                sample.setCaloriesBurnt(ActivitySample.NOT_MEASURED);

                sample.setHeartRate(measurement != 0 ? measurement : ActivitySample.NOT_MEASURED);
                sample.setBloodPressureSystolic(ActivitySample.NOT_MEASURED);
                sample.setBloodPressureDiastolic(ActivitySample.NOT_MEASURED);
                sample.setBloodOxidation(ActivitySample.NOT_MEASURED);

                provider.addGBActivitySample(sample);
                LOG.info("Adding a training sample: " + sample.toString());

                dateRecorded.add(Calendar.MINUTE, 1);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            GB.toast(getContext(), "Error saving samples: " + ex.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
            GB.updateTransferNotification(null, "Data transfer failed", false, 0, getContext());
        }

        try {
            TransactionBuilder builder = performInitialized("TrainingFinishedDataOperation fetch training type");
            getSupport().sendPacket(builder, DaFitPacketOut.buildPacket(DaFitConstants.CMD_QUERY_MOVEMENT_HEART_RATE, new byte[] { }));
            builder.queue(getQueue());
        } catch (IOException e) {
            e.printStackTrace();
            GB.toast(getContext(), "Error fetching training data: " + e.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
            GB.updateTransferNotification(null, "Data transfer failed", false, 0, getContext());
            operationFinished();
        }
    }

    private void handleTrainingSummaryDataPacket(byte[] payload)
    {
        getSupport().handleTrainingData(payload);

        GB.updateTransferNotification(null, getContext().getString(R.string.busy_task_fetch_training_data_finished), false, 0, getContext());
        operationFinished();
    }

    @Override
    protected void operationFinished() {
        operationStatus = OperationStatus.FINISHED;
        if (getDevice() != null && getDevice().isConnected()) {
            unsetBusy();
            GB.signalActivityDataFinish();
        }
    }
}
