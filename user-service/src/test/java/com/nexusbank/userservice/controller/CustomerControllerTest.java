package com.nexusbank.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbank.userservice.dto.request.RegisterCustomerRequest;
import com.nexusbank.userservice.dto.request.UpdateCustomerRequest;
import com.nexusbank.userservice.dto.response.CustomerResponse;
import com.nexusbank.userservice.exception.EmailAlreadyExistsException;
import com.nexusbank.userservice.exception.GlobalExceptionHandler;
import com.nexusbank.userservice.exception.ResourceNotFoundException;
import com.nexusbank.userservice.security.JwtAuthFilter;
import com.nexusbank.userservice.security.JwtUtil;
import com.nexusbank.userservice.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerController.class)
@Import(GlobalExceptionHandler.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerService customerService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @Autowired
    private ObjectMapper objectMapper;

    private CustomerResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = new CustomerResponse();
        sampleResponse.setId(10L);
        sampleResponse.setUserId(1L);
        sampleResponse.setEmail("john.doe@bank.com");
        sampleResponse.setFirstName("John");
        sampleResponse.setLastName("Doe");
        sampleResponse.setIdCardNumber("ID123456");
        sampleResponse.setKycStatus("PENDING");
        sampleResponse.setCreatedAt(LocalDateTime.now());
        sampleResponse.setIsActive(true);
    }

    // ── POST /api/customers ───────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "TELLER")
    void registerCustomer_withValidRequest_returns201() throws Exception {
        RegisterCustomerRequest request = validRegisterRequest();

        when(customerService.registerCustomer(any(RegisterCustomerRequest.class), any()))
                .thenReturn(sampleResponse);
        when(jwtUtil.extractUserId(any())).thenReturn(null);

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer token")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.email").value("john.doe@bank.com"));
    }

    @Test
    @WithMockUser(roles = "TELLER")
    void registerCustomer_withDuplicateEmail_returns409() throws Exception {
        RegisterCustomerRequest request = validRegisterRequest();

        when(customerService.registerCustomer(any(), any()))
                .thenThrow(new EmailAlreadyExistsException("john.doe@bank.com"));

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer token")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @WithMockUser(roles = "TELLER")
    void registerCustomer_withInvalidEmail_returns400WithFieldErrors() throws Exception {
        RegisterCustomerRequest request = validRegisterRequest();
        request.setEmail("not-an-email");

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer token")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    @WithMockUser(roles = "TELLER")
    void registerCustomer_withBlankFirstName_returns400() throws Exception {
        RegisterCustomerRequest request = validRegisterRequest();
        request.setFirstName("");

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer token")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists());
    }

    // ── GET /api/customers ────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "TELLER")
    void getAllCustomers_returns200WithList() throws Exception {
        when(customerService.getAllCustomers()).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("john.doe@bank.com"))
                .andExpect(jsonPath("$[0].kycStatus").value("PENDING"));
    }

    // ── GET /api/customers/{id} ───────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void getCustomer_whenFound_returns200() throws Exception {
        when(customerService.getCustomer(10L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/customers/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.firstName").value("John"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void getCustomer_whenNotFound_returns404WithErrorShape() throws Exception {
        when(customerService.getCustomer(99L))
                .thenThrow(new ResourceNotFoundException("Customer not found: 99"));

        mockMvc.perform(get("/api/customers/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Customer not found: 99"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── PUT /api/customers/{id} ───────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void updateCustomer_withValidRequest_returns200() throws Exception {
        UpdateCustomerRequest request = new UpdateCustomerRequest();
        request.setAddress("456 New Street");

        CustomerResponse updated = new CustomerResponse();
        updated.setId(10L);
        updated.setEmail("john.doe@bank.com");
        updated.setAddress("456 New Street");

        when(customerService.updateCustomer(eq(10L), any(UpdateCustomerRequest.class), any()))
                .thenReturn(updated);
        when(jwtUtil.extractUserId(any())).thenReturn(null);

        mockMvc.perform(put("/api/customers/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer token")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("456 New Street"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void updateCustomer_whenNotFound_returns404() throws Exception {
        when(customerService.updateCustomer(eq(99L), any(), any()))
                .thenThrow(new ResourceNotFoundException("Customer not found: 99"));
        when(jwtUtil.extractUserId(any())).thenReturn(null);

        mockMvc.perform(put("/api/customers/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer token")
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private RegisterCustomerRequest validRegisterRequest() {
        RegisterCustomerRequest req = new RegisterCustomerRequest();
        req.setEmail("john.doe@bank.com");
        req.setPassword("secret123");
        req.setFirstName("John");
        req.setLastName("Doe");
        req.setDateOfBirth(LocalDate.of(1990, 1, 15));
        req.setIdCardNumber("ID123456");
        return req;
    }
}
