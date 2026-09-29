package com.simplebank.dto;

import com.simplebank.model.Address;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A postal address in requests and responses. The state is stored uppercase. */
public record AddressDto(
        @NotBlank(message = "street is required")
        @Size(max = 100, message = "street must be 100 characters or fewer")
        String street,

        @NotBlank(message = "city is required")
        @Size(max = 60, message = "city must be 60 characters or fewer")
        String city,

        @NotBlank(message = "state is required")
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "state must be a 2-letter code, e.g. MD")
        String state,

        @NotBlank(message = "zip is required")
        @Pattern(regexp = "^\\d{5}(-\\d{4})?$", message = "zip must be 5 digits or ZIP+4, e.g. 21201 or 21201-1234")
        String zip
) {
    public Address toAddress() {
        return new Address(street, city, state, zip);
    }

    /** Users created before addresses existed have none, so this can return null. */
    public static AddressDto from(Address address) {
        return address == null ? null
                : new AddressDto(address.street(), address.city(), address.state(), address.zip());
    }
}
