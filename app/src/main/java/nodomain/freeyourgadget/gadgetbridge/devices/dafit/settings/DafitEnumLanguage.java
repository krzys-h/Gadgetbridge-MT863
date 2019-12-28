package nodomain.freeyourgadget.gadgetbridge.devices.dafit.settings;

public enum DafitEnumLanguage implements DafitEnum {
    LANGUAGE_ENGLISH((byte)0),
    LANGUAGE_CHINESE((byte)1),
    LANGUAGE_JAPANESE((byte)2),
    LANGUAGE_KOREAN((byte)3),
    LANGUAGE_GERMAN((byte)4),
    LANGUAGE_FRENCH((byte)5),
    LANGUAGE_SPANISH((byte)6),
    LANGUAGE_ARABIC((byte)7),
    LANGUAGE_RUSSIAN((byte)8),
    LANGUAGE_TRADITIONAL((byte)9),
    LANGUAGE_UKRAINIAN((byte)10),
    LANGUAGE_ITALIAN((byte)11),
    LANGUAGE_PORTUGUESE((byte)12),
    LANGUAGE_DUTCH((byte)13),
    LANGUAGE_POLISH((byte)14),
    LANGUAGE_SWEDISH((byte)15),
    LANGUAGE_FINNISH((byte)16),
    LANGUAGE_DANISH((byte)17),
    LANGUAGE_NORWEGIAN((byte)18),
    LANGUAGE_HUNGARIAN((byte)19),
    LANGUAGE_CZECH((byte)20),
    LANGUAGE_BULGARIAN((byte)21),
    LANGUAGE_ROMANIAN((byte)22),
    LANGUAGE_SLOVAK_LANGUAGE((byte)23),
    LANGUAGE_LATVIAN((byte)24);

    public final byte value;

    DafitEnumLanguage(byte value) {
        this.value = value;
    }

    @Override
    public byte value() {
        return value;
    }
}
