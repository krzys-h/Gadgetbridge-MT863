package nodomain.freeyourgadget.gadgetbridge.deviceevents;

public class GBDeviceEventConfigurationRead extends GBDeviceEvent {
    public String config;
    public Event event;

    public enum Event {
        IN_PROGRESS,
        SUCCESS,
        FAILURE
    }
}
