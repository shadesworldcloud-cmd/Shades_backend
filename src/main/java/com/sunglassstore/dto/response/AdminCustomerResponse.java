package com.sunglassstore.dto.response;

import com.sunglassstore.entity.Address;
import com.sunglassstore.entity.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminCustomerResponse(Long userId, String name, String email, String phoneNumber,
                                    Boolean isActive, Boolean emailVerified, Boolean accountLocked,
                                    LocalDateTime createdAt, LocalDateTime lastLoginAt,
                                    long orderCount, BigDecimal totalSpent, LocalDateTime lastOrderAt,
                                    List<AddressInfo> addresses) {
    public static AdminCustomerResponse fromEntity(User user, long orderCount, BigDecimal totalSpent,
                                                   LocalDateTime lastOrderAt, List<Address> addresses) {
        return new AdminCustomerResponse(user.getUserId(), user.getName(), user.getEmail(),
                user.getPhoneNumber(), user.getIsActive(), user.getEmailVerified(),
                user.getAccountLocked(), user.getCreatedAt(), user.getLastLoginAt(), orderCount,
                totalSpent == null ? BigDecimal.ZERO : totalSpent, lastOrderAt,
                addresses.stream().map(a -> new AddressInfo(a.getAddressId(), a.getAddressType().name(),
                        a.getRecipientName(), a.getPhoneNumber(), a.getAddressLine1(), a.getAddressLine2(),
                        a.getCity(), a.getState(), a.getPincode(), a.getCountry(), a.getIsDefault())).toList());
    }
    public record AddressInfo(Long addressId, String type, String recipientName, String phoneNumber,
                              String line1, String line2, String city, String state, String pincode,
                              String country, Boolean isDefault) {}
}
