package com.sunglassstore.dto.response;

import com.sunglassstore.entity.Address;

public record AddressResponse(Long addressId, String addressType, String recipientName, String phoneNumber,
                              String houseNumber, String addressLine1, String addressLine2, String city,
                              String state, String pincode, String country, Boolean isDefault) {
    public static AddressResponse fromEntity(Address address) {
        return new AddressResponse(address.getAddressId(), address.getAddressType().name(),
                address.getRecipientName(), address.getPhoneNumber(), address.getHouseNumber(),
                address.getAddressLine1(), address.getAddressLine2(), address.getCity(), address.getState(),
                address.getPincode(), address.getCountry(), address.getIsDefault());
    }
}
