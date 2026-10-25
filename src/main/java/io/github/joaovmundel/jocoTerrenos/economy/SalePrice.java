package io.github.joaovmundel.jocoTerrenos.economy;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Prices players type for a land they put up for sale, and how they are stored. Money is a {@link BigDecimal}
 * with at most two decimals; the database keeps it as a whole number of cents so no floating point is involved.
 */
public final class SalePrice {

    private static final int DECIMALS = 2;
    /** Digits, then optionally a . or , and one or two decimals. No signs, exponents or separators. */
    private static final Pattern FORMAT = Pattern.compile("^\\d{1,15}([.,]\\d{1,2})?$");

    private SalePrice() {
    }

    /**
     * @param max the largest price accepted
     * @return the price, or {@code null} when the text is not a plain positive amount of at most two decimals
     * or is above {@code max}
     */
    public static BigDecimal parse(String text, BigDecimal max) {
        if (text == null || !FORMAT.matcher(text).matches()) {
            return null;
        }
        BigDecimal price = new BigDecimal(text.replace(',', '.')).setScale(DECIMALS);
        if (price.signum() <= 0 || price.compareTo(max) > 0) {
            return null;
        }
        return price;
    }

    /** @throws ArithmeticException if the price has more than two decimals or does not fit in a long */
    public static long toCents(BigDecimal price) {
        return price.movePointRight(DECIMALS).longValueExact();
    }

    public static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, DECIMALS);
    }
}
