package com.simplebank;

import com.simplebank.model.Address;

/** Shared values for tests. */
public final class TestData {

    public static final Address ADDRESS = new Address("100 Main St", "Baltimore", "MD", "21201");

    /** The same address as JSON, for API request bodies. */
    public static final String ADDRESS_JSON =
            "{\"street\": \"100 Main St\", \"city\": \"Baltimore\", \"state\": \"MD\", \"zip\": \"21201\"}";

    private TestData() {
    }
}
