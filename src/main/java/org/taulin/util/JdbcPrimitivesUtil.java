package org.taulin.util;

import lombok.experimental.UtilityClass;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@UtilityClass
public class JdbcPrimitivesUtil {
    public static int handleInteger(Integer value) {
        return Objects.nonNull(value)
                ? value
                : Integer.MIN_VALUE;
    }

    public static String handleCharSequence(CharSequence value) {
        return Objects.nonNull(value)
                ? value.toString()
                : null;
    }

    public static Long handleLong(Long value) {
        return Objects.nonNull(value)
                ? value
                : Long.MIN_VALUE;
    }

    public static Boolean handleBoolean(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    public static String handleEpoch(Long value) {
        Long parsedValue = handleLong(value);

        if (parsedValue == Long.MIN_VALUE) {
            return null;
        } else {
            return parseEpochToFormattedDateStr(value);
        }
    }

    private static String parseEpochToFormattedDateStr(Long epoch) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                LocalDateTime.ofEpochSecond(epoch, 0, ZoneOffset.UTC));
    }
}
