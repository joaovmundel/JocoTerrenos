package io.github.joaovmundel.jocoTerrenos.economy;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SalePriceTest {

    private static final BigDecimal MAX = new BigDecimal("1000000000");

    @Test
    public void plainIntegersAreAccepted() {
        assertEquals(new BigDecimal("50000.00"), SalePrice.parse("50000", MAX));
    }

    @Test
    public void dotAndCommaDecimalsAreAccepted() {
        assertEquals(new BigDecimal("12.50"), SalePrice.parse("12.5", MAX));
        assertEquals(new BigDecimal("12.50"), SalePrice.parse("12,5", MAX));
        assertEquals(new BigDecimal("0.99"), SalePrice.parse("0.99", MAX));
    }

    @Test
    public void zeroNegativeAndEmptyAreRejected() {
        assertNull(SalePrice.parse("0", MAX));
        assertNull(SalePrice.parse("0.00", MAX));
        assertNull(SalePrice.parse("-5", MAX));
        assertNull(SalePrice.parse("", MAX));
        assertNull(SalePrice.parse(null, MAX));
    }

    @Test
    public void tooManyDecimalsAreRejected() {
        assertNull(SalePrice.parse("1.234", MAX));
    }

    @Test
    public void exponentsSignsAndSeparatorsAreRejected() {
        assertNull(SalePrice.parse("1e9", MAX));
        assertNull(SalePrice.parse("+10", MAX));
        assertNull("a thousands separator is ambiguous with a decimal comma", SalePrice.parse("1,000", MAX));
        assertNull(SalePrice.parse("10 ", MAX));
        assertNull(SalePrice.parse("abc", MAX));
        assertNull(SalePrice.parse("NaN", MAX));
        assertNull(SalePrice.parse("1.", MAX));
    }

    @Test
    public void theMaximumIsInclusive() {
        assertEquals(new BigDecimal("1000000000.00"), SalePrice.parse("1000000000", MAX));
        assertNull(SalePrice.parse("1000000000.01", MAX));
        assertNull(SalePrice.parse("1000000001", MAX));
    }

    @Test
    public void absurdLengthsAreRejectedInsteadOfOverflowing() {
        assertNull(SalePrice.parse("99999999999999999999999999", new BigDecimal("1e40")));
    }

    @Test
    public void centsRoundTrip() {
        BigDecimal price = new BigDecimal("1234.56");

        assertEquals(123456L, SalePrice.toCents(price));
        assertEquals(price, SalePrice.fromCents(123456L));
        assertEquals(new BigDecimal("0.05"), SalePrice.fromCents(5L));
    }

    @Test(expected = ArithmeticException.class)
    public void centsRefuseASubCentAmount() {
        SalePrice.toCents(new BigDecimal("1.234"));
    }
}
