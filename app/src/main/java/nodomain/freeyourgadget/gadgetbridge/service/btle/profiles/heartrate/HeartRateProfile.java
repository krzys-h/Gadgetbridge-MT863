/*  Copyright (C) 2016-2019 Andreas Shimokawa, Carsten Pfeiffer, Daniele
    Gobbetti

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
package nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.heartrate;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattCharacteristic;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.AbstractBleProfile;

/**
 * https://www.bluetooth.com/wp-content/uploads/Sitecore-Media-Library/Gatt/Xml/Services/org.bluetooth.service.heart_rate.xml
 */
public class HeartRateProfile<T extends AbstractBTLEDeviceSupport> extends AbstractBleProfile<T> {
    private static final Logger LOG = LoggerFactory.getLogger(HeartRateProfile.class);

    public static final UUID SERVICE_UUID = GattService.UUID_SERVICE_HEART_RATE;
    public static final UUID UUID_CHARACTERISTIC_HEART_RATE_CONTROL_POINT = GattCharacteristic.UUID_CHARACTERISTIC_HEART_RATE_CONTROL_POINT;
    public static final UUID UUID_CHARACTERISTIC_HEART_RATE_MEASUREMENT = GattCharacteristic.UUID_CHARACTERISTIC_HEART_RATE_MEASUREMENT;
    public static final UUID UUID_CHARACTERISTIC_CLIENT_CHARACTERISTIC_CONFIG = GattCharacteristic.UUID_CHARACTERISTIC_CLIENT_CHARACTERISTIC_CONFIG;


    public HeartRateProfile(T support) {
        super(support);
    }

    public void resetEnergyExpended(TransactionBuilder builder) {
        writeToControlPoint((byte) 0x01, builder);
    }

    protected void writeToControlPoint(byte value, TransactionBuilder builder) {
        writeToControlPoint(new byte[] { value }, builder);
    }

    protected void writeToControlPoint(byte[] value, TransactionBuilder builder) {
        builder.write(getCharacteristic(UUID_CHARACTERISTIC_HEART_RATE_CONTROL_POINT), value);
    }

    public void requestBodySensorLocation(TransactionBuilder builder) {

    }

    public void enableNotify(TransactionBuilder builder) {
        builder.notify(getCharacteristic(UUID_CHARACTERISTIC_HEART_RATE_MEASUREMENT), true);
    }

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        UUID charUuid = characteristic.getUuid();
        if (charUuid.equals(UUID_CHARACTERISTIC_HEART_RATE_MEASUREMENT)) {
            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            LOG.info("Heart rate: " + heartRate);
        }
        return false;
    }
}
