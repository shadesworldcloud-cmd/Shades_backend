package com.sunglassstore.service;

import com.sunglassstore.dto.request.AddressRequest;
import com.sunglassstore.entity.Address;

import java.util.List;

public interface AddressService {
    List<Address> getUserAddresses(Long userId);
    Address createAddress(Long userId, AddressRequest request);
    Address updateAddress(Long userId, Long addressId, AddressRequest request);
    void deleteAddress(Long userId, Long addressId);
    Address setDefault(Long userId, Long addressId);
}
