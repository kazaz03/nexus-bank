package com.nexusbank.userservice;

import com.nexusbank.userservice.model.*;
import com.nexusbank.userservice.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class DataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final TellerRepository tellerRepository;
    private final LoanOfficerRepository loanOfficerRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    public DataLoader(UserRepository userRepository,
                      CustomerRepository customerRepository,
                      TellerRepository tellerRepository,
                      LoanOfficerRepository loanOfficerRepository,
                      AdminRepository adminRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.tellerRepository = tellerRepository;
        this.loanOfficerRepository = loanOfficerRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() > 0) {
            return;
        }

        // User 1 - Customer
        User user1 = new User();
        user1.setEmail("marko.nikolic@nexusbank.com");
        user1.setPasswordHash(passwordEncoder.encode("password1"));
        user1.setFirstName("Marko");
        user1.setLastName("Nikolić");
        user1.setRole(User.Role.CUSTOMER);
        user1.setCreatedAt(LocalDateTime.now());
        user1.setIsActive(true);
        userRepository.save(user1);

        Customer customer1 = new Customer();
        customer1.setUser(user1);
        customer1.setDateOfBirth(LocalDate.of(1990, 5, 15));
        customer1.setIdCardNumber("BA123456");
        customer1.setAddress("Titova 1, Sarajevo");
        customer1.setPhone("+38761111111");
        customer1.setKycStatus(Customer.KycStatus.VERIFIED);
        customer1.setUpdatedAt(LocalDateTime.now());
        customerRepository.save(customer1);

        // User 2 - Teller
        User user2 = new User();
        user2.setEmail("ana.kovacevic@nexusbank.com");
        user2.setPasswordHash(passwordEncoder.encode("password2"));
        user2.setFirstName("Ana");
        user2.setLastName("Kovačević");
        user2.setRole(User.Role.TELLER);
        user2.setCreatedAt(LocalDateTime.now());
        user2.setIsActive(true);
        userRepository.save(user2);

        Teller teller1 = new Teller();
        teller1.setUser(user2);
        teller1.setBranch("Sarajevo Centar");
        tellerRepository.save(teller1);

        // User 3 - Loan Officer
        User user3 = new User();
        user3.setEmail("edin.hasanovic@nexusbank.com");
        user3.setPasswordHash(passwordEncoder.encode("password3"));
        user3.setFirstName("Edin");
        user3.setLastName("Hasanović");
        user3.setRole(User.Role.LOAN_OFFICER);
        user3.setCreatedAt(LocalDateTime.now());
        user3.setIsActive(true);
        userRepository.save(user3);

        LoanOfficer loanOfficer1 = new LoanOfficer();
        loanOfficer1.setUser(user3);
        loanOfficer1.setDepartment("Retail Lending");
        loanOfficerRepository.save(loanOfficer1);

        // User 4 - Admin
        User user4 = new User();
        user4.setEmail("admin@nexusbank.com");
        user4.setPasswordHash(passwordEncoder.encode("admin123"));
        user4.setFirstName("System");
        user4.setLastName("Admin");
        user4.setRole(User.Role.ADMIN);
        user4.setCreatedAt(LocalDateTime.now());
        user4.setIsActive(true);
        userRepository.save(user4);

        Admin admin1 = new Admin();
        admin1.setUser(user4);
        adminRepository.save(admin1);

        System.out.println("User Service - data loaded");
    }
}
