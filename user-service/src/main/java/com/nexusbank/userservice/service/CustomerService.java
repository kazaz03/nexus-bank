package com.nexusbank.userservice.service;

import com.nexusbank.userservice.dto.request.RegisterCustomerRequest;
import com.nexusbank.userservice.dto.request.UpdateCustomerRequest;
import com.nexusbank.userservice.dto.response.CustomerResponse;
import com.nexusbank.userservice.exception.EmailAlreadyExistsException;
import com.nexusbank.userservice.exception.ResourceNotFoundException;
import com.nexusbank.userservice.model.Customer;
import com.nexusbank.userservice.model.User;
import com.nexusbank.userservice.repository.CustomerRepository;
import com.nexusbank.userservice.repository.UserRepository;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CustomerService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final ModelMapper modelMapper;

    public CustomerService(UserRepository userRepository,
                           CustomerRepository customerRepository,
                           PasswordEncoder passwordEncoder,
                           ModelMapper modelMapper) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.modelMapper = modelMapper;
    }

    @Transactional
    public CustomerResponse registerCustomer(RegisterCustomerRequest request, Long createdByUserId) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setRole(User.Role.CUSTOMER);
        user.setCreatedAt(LocalDateTime.now());
        user.setIsActive(true);
        userRepository.save(user);

        Customer customer = new Customer();
        customer.setUser(user);
        customer.setDateOfBirth(request.getDateOfBirth());
        customer.setIdCardNumber(request.getIdCardNumber());
        customer.setAddress(request.getAddress());
        customer.setPhone(request.getPhone());
        customer.setKycStatus(Customer.KycStatus.PENDING);
        customer.setUpdatedAt(LocalDateTime.now());

        if (createdByUserId != null) {
            userRepository.findById(createdByUserId)
                    .ifPresent(customer::setUpdatedBy);
        }

        customerRepository.save(customer);
        return toResponse(customer);
    }

    public CustomerResponse getCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));
        return toResponse(customer);
    }

    public CustomerResponse getCustomerByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        Customer customer = customerRepository.findAll().stream()
                .filter(c -> c.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found for user: " + userId));
        return toResponse(customer);
    }

    public List<CustomerResponse> getAllCustomers() {
        return customerRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CustomerResponse updateCustomer(Long customerId, UpdateCustomerRequest request, Long updatedByUserId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));

        if (request.getFirstName() != null) customer.getUser().setFirstName(request.getFirstName());
        if (request.getLastName() != null) customer.getUser().setLastName(request.getLastName());
        if (request.getAddress() != null) customer.setAddress(request.getAddress());
        if (request.getPhone() != null) customer.setPhone(request.getPhone());

        customer.setUpdatedAt(LocalDateTime.now());
        if (updatedByUserId != null) {
            userRepository.findById(updatedByUserId)
                    .ifPresent(customer::setUpdatedBy);
        }

        userRepository.save(customer.getUser());
        customerRepository.save(customer);
        return toResponse(customer);
    }

    private CustomerResponse toResponse(Customer customer) {
        CustomerResponse response = new CustomerResponse();
        response.setId(customer.getId());
        response.setUserId(customer.getUser().getId());
        response.setEmail(customer.getUser().getEmail());
        response.setFirstName(customer.getUser().getFirstName());
        response.setLastName(customer.getUser().getLastName());
        response.setDateOfBirth(customer.getDateOfBirth());
        response.setIdCardNumber(customer.getIdCardNumber());
        response.setAddress(customer.getAddress());
        response.setPhone(customer.getPhone());
        response.setKycStatus(customer.getKycStatus().name());
        response.setCreatedAt(customer.getUser().getCreatedAt());
        response.setIsActive(customer.getUser().getIsActive());
        response.setUpdatedAt(customer.getUpdatedAt());
        return response;
    }
}
