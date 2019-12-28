package nodomain.freeyourgadget.gadgetbridge.devices.dafit;

import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;

public class DafitWeatherForecast {
    public final byte conditionId;
    public final byte minTemp;
    public final byte maxTemp;

    public DafitWeatherForecast(byte conditionId, byte minTemp, byte maxTemp) {
        this.conditionId = conditionId;
        this.minTemp = minTemp;
        this.maxTemp = maxTemp;
    }

    public DafitWeatherForecast(WeatherSpec.Forecast forecast)
    {
        conditionId = DafitConstants.openWeatherConditionToDafitConditionId(forecast.conditionCode);
        minTemp = (byte)(forecast.minTemp - 273); // Kelvin -> Celcius
        maxTemp = (byte)(forecast.maxTemp - 273); // Kelvin -> Celcius
    }
}
