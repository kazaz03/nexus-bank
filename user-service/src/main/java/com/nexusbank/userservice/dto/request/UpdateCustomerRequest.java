package com.nexusbank.userservice.dto.request;

import lombok.Data;

@Data
public class UpdateCustomerRequest {

    private String firstName;
    private String lastName;
    private String address;
    private String phone;
}
