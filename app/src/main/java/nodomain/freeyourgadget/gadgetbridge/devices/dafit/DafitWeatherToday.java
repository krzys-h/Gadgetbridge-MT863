package nodomain.freeyourgadget.gadgetbridge.devices.dafit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;

public class DafitWeatherToday {
    public final byte conditionId;
    public final byte currentTemp;
    public final Short pm25; // (*)
    public final String lunar_or_festival; // (*)
    public final String city; // (*)

    public DafitWeatherToday(byte conditionId, byte currentTemp, @Nullable Short pm25, @NonNull String lunar_or_festival, @NonNull String city) {
        if (lunar_or_festival.length() != 4)
            throw new IllegalArgumentException("lunar_or_festival");
        if (city.length() != 4)
            throw new IllegalArgumentException("city");
        this.conditionId = conditionId;
        this.currentTemp = currentTemp;
        this.pm25 = pm25;
        this.lunar_or_festival = lunar_or_festival;
        this.city = city;
    }

    public DafitWeatherToday(WeatherSpec weatherSpec)
    {
        conditionId = DafitConstants.openWeatherConditionToDafitConditionId(weatherSpec.currentConditionCode);
        currentTemp = (byte)(weatherSpec.currentTemp - 273); // Kelvin -> Celcius
        pm25 = null;
        lunar_or_festival = StringUtils.pad("", 4);
        city = StringUtils.pad(weatherSpec.location.substring(0, 4), 4);
    }
}
