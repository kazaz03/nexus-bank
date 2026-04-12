package com.nexusbank.userservice.service;

import com.nexusbank.userservice.dto.response.AdminStatsResponse;
import com.nexusbank.userservice.model.User;
import com.nexusbank.userservice.repository.AdminRepository;
import com.nexusbank.userservice.repository.CustomerRepository;
import com.nexusbank.userservice.repository.LoanOfficerRepository;
import com.nexusbank.userservice.repository.TellerRepository;
import com.nexusbank.userservice.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final TellerRepository tellerRepository;
    private final LoanOfficerRepository loanOfficerRepository;
    private final AdminRepository adminRepository;

    public AdminService(UserRepository userRepository,
                        CustomerRepository customerRepository,
                        TellerRepository tellerRepository,
                        LoanOfficerRepository loanOfficerRepository,
                        AdminRepository adminRepository) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.tellerRepository = tellerRepository;
        this.loanOfficerRepository = loanOfficerRepository;
        this.adminRepository = adminRepository;
    }

    public AdminStatsResponse getUserStats() {
        long totalUsers = userRepository.count();
        long totalCustomers = customerRepository.count();
        long totalTellers = tellerRepository.count();
        long totalLoanOfficers = loanOfficerRepository.count();
        long totalAdmins = adminRepository.count();
        long activeUsers = userRepository.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .count();

        return new AdminStatsResponse(
                totalUsers, totalCustomers, totalTellers,
                totalLoanOfficers, totalAdmins, activeUsers
        );
    }
}
