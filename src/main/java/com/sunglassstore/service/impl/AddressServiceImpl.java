package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.AddressRequest;
import com.sunglassstore.entity.Address;
import com.sunglassstore.entity.User;
import com.sunglassstore.entity.enums.AddressType;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.AddressRepository;
import com.sunglassstore.service.AddressService;
import com.sunglassstore.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final UserService userService;

    @Override
    @Transactional(readOnly = true)
    public List<Address> getUserAddresses(Long userId) {
        return addressRepository.findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
    }

    @Override
    @Transactional
    public Address createAddress(Long userId, AddressRequest request) {
        User user = userService.findById(userId);
        Address address = new Address();
        mapRequestToAddress(request, address);
        address.setUser(user);

        if (Boolean.TRUE.equals(request.getIsDefault()) || !addressRepository.existsByUserUserId(userId)) {
            addressRepository.clearDefaultForUser(userId, -1L);
            address.setIsDefault(true);
        }

        return addressRepository.save(address);
    }

    @Override
    @Transactional
    public Address updateAddress(Long userId, Long addressId, AddressRequest request) {
        Address address = addressRepository.findByAddressIdAndUserUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        mapRequestToAddress(request, address);

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            addressRepository.clearDefaultForUser(userId, addressId);
            address.setIsDefault(true);
        }

        return addressRepository.save(address);
    }

    @Override
    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        Address address = addressRepository.findByAddressIdAndUserUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        addressRepository.delete(address);
    }

    @Override
    @Transactional
    public Address setDefault(Long userId, Long addressId) {
        Address address = addressRepository.findByAddressIdAndUserUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        addressRepository.clearDefaultForUser(userId, addressId);
        address.setIsDefault(true);
        return addressRepository.save(address);
    }

    private void mapRequestToAddress(AddressRequest request, Address address) {
        address.setAddressType(AddressType.valueOf(request.getAddressType()));
        address.setRecipientName(request.getRecipientName());
        address.setHouseNumber(request.getHouseNumber());
        address.setPhoneNumber(com.sunglassstore.validation.PhoneNumbers.toStored(request.getPhoneNumber()));
        address.setAddressLine1(request.getAddressLine1());
        address.setAddressLine2(request.getAddressLine2());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setPincode(normalisePincode(request.getPincode(), request.getCountry()));
        address.setCountry(request.getCountry());
    }

    /**
     * Trims surrounding whitespace and enforces the postal-code length for the destination country.
     * The value stays a String throughout so a leading zero is never lost to numeric coercion.
     * Only India has a fixed national rule here; every other destination is accepted at a generic
     * 3-10 digits rather than guessed at, so a legitimate foreign address is never rejected.
     */
    static String normalisePincode(String pincode, String country) {
        String trimmed = pincode == null ? "" : pincode.trim();
        if (!trimmed.matches("^[0-9]+$")) {
            throw new BadRequestException("Pincode must contain digits only.");
        }
        boolean india = country != null && country.trim().equalsIgnoreCase("India");
        if (india && !trimmed.matches("^[1-9][0-9]{5}$")) {
            throw new BadRequestException("Enter a valid 6-digit Indian PIN code.");
        }
        if (!india && (trimmed.length() < 3 || trimmed.length() > 10)) {
            throw new BadRequestException("Enter a valid postal code of 3 to 10 digits.");
        }
        return trimmed;
    }
}
