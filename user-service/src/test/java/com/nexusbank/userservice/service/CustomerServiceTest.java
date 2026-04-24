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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private CustomerService customerService;

    private User sampleUser;
    private Customer sampleCustomer;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(1L);
        sampleUser.setEmail("john.doe@bank.com");
        sampleUser.setPasswordHash("hashed");
        sampleUser.setFirstName("John");
        sampleUser.setLastName("Doe");
        sampleUser.setRole(User.Role.CUSTOMER);
        sampleUser.setCreatedAt(LocalDateTime.now());
        sampleUser.setIsActive(true);

        sampleCustomer = new Customer();
        sampleCustomer.setId(10L);
        sampleCustomer.setUser(sampleUser);
        sampleCustomer.setDateOfBirth(LocalDate.of(1990, 1, 15));
        sampleCustomer.setIdCardNumber("ID123456");
        sampleCustomer.setAddress("123 Main St");
        sampleCustomer.setPhone("+38761000000");
        sampleCustomer.setKycStatus(Customer.KycStatus.PENDING);
        sampleCustomer.setUpdatedAt(LocalDateTime.now());
    }

    private CustomerResponse buildExpectedResponse() {
        CustomerResponse resp = new CustomerResponse();
        resp.setId(10L);
        resp.setUserId(1L);
        resp.setEmail("john.doe@bank.com");
        resp.setFirstName("John");
        resp.setLastName("Doe");
        resp.setKycStatus("PENDING");
        return resp;
    }

    // ── registerCustomer ──────────────────────────────────────────────────────

    @Test
    void registerCustomer_withUniqueEmail_createsUserAndCustomer() {
        RegisterCustomerRequest request = new RegisterCustomerRequest();
        request.setEmail("john.doe@bank.com");
        request.setPassword("secret123");
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setDateOfBirth(LocalDate.of(1990, 1, 15));
        request.setIdCardNumber("ID123456");

        when(userRepository.findByEmail("john.doe@bank.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(customerRepository.save(any(Customer.class))).thenReturn(sampleCustomer);

        CustomerResponse result = customerService.registerCustomer(request, null);

        assertThat(result).isNotNull();
        verify(userRepository).save(any(User.class));
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void registerCustomer_withDuplicateEmail_throwsEmailAlreadyExistsException() {
        RegisterCustomerRequest request = new RegisterCustomerRequest();
        request.setEmail("john.doe@bank.com");
        request.setPassword("secret123");
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setDateOfBirth(LocalDate.of(1990, 1, 15));
        request.setIdCardNumber("ID123456");

        when(userRepository.findByEmail("john.doe@bank.com")).thenReturn(Optional.of(sampleUser));

        assertThrows(EmailAlreadyExistsException.class,
                () -> customerService.registerCustomer(request, null));
        verify(userRepository, never()).save(any());
    }

    // ── getCustomer ───────────────────────────────────────────────────────────

    @Test
    void getCustomer_whenFound_returnsResponse() {
        when(customerRepository.findById(10L)).thenReturn(Optional.of(sampleCustomer));

        CustomerResponse result = customerService.getCustomer(10L);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getEmail()).isEqualTo("john.doe@bank.com");
    }

    @Test
    void getCustomer_whenNotFound_throwsResourceNotFoundException() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> customerService.getCustomer(99L));
    }

    // ── getAllCustomers ────────────────────────────────────────────────────────

    @Test
    void getAllCustomers_returnsMappedList() {
        when(customerRepository.findAll()).thenReturn(List.of(sampleCustomer));

        List<CustomerResponse> result = customerService.getAllCustomers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("john.doe@bank.com");
    }

    // ── updateCustomer ────────────────────────────────────────────────────────

    @Test
    void updateCustomer_withNewAddress_updatesOnlyAddress() {
        UpdateCustomerRequest request = new UpdateCustomerRequest();
        request.setAddress("456 New Street");

        when(customerRepository.findById(10L)).thenReturn(Optional.of(sampleCustomer));
        when(customerRepository.save(any())).thenReturn(sampleCustomer);
        when(userRepository.save(any())).thenReturn(sampleUser);

        CustomerResponse result = customerService.updateCustomer(10L, request, null);

        // Address was updated
        assertThat(sampleCustomer.getAddress()).isEqualTo("456 New Street");
        // firstName/lastName unchanged since request had null values
        assertThat(sampleUser.getFirstName()).isEqualTo("John");
        assertThat(sampleUser.getLastName()).isEqualTo("Doe");
    }

    @Test
    void updateCustomer_withNewFirstAndLastName_updatesUserFields() {
        UpdateCustomerRequest request = new UpdateCustomerRequest();
        request.setFirstName("Jane");
        request.setLastName("Smith");

        when(customerRepository.findById(10L)).thenReturn(Optional.of(sampleCustomer));
        when(customerRepository.save(any())).thenReturn(sampleCustomer);
        when(userRepository.save(any())).thenReturn(sampleUser);

        customerService.updateCustomer(10L, request, null);

        assertThat(sampleUser.getFirstName()).isEqualTo("Jane");
        assertThat(sampleUser.getLastName()).isEqualTo("Smith");
    }

    @Test
    void updateCustomer_whenCustomerNotFound_throwsResourceNotFoundException() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> customerService.updateCustomer(99L, new UpdateCustomerRequest(), null));
    }
}
