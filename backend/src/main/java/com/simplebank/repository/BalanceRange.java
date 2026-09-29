package com.simplebank.repository;

import org.bson.Document;
import org.bson.types.Decimal128;

import java.math.BigDecimal;

/** Builds a MongoDB range condition on money: { $gte: min, $lte: max }. */
final class BalanceRange {

    private BalanceRange() {
    }

    /** Returns null when neither bound is set. Values are Decimal128, matching how balances are stored. */
    static Document of(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return null;
        }
        Document range = new Document();
        if (min != null) {
            range.append("$gte", new Decimal128(min));
        }
        if (max != null) {
            range.append("$lte", new Decimal128(max));
        }
        return range;
    }
}
