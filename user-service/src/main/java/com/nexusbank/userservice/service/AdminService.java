package com.nexusbank.userservice.service;

import com.nexusbank.userservice.client.StatsClient;
import com.nexusbank.userservice.client.dto.AccountStatsView;
import com.nexusbank.userservice.client.dto.DailyVolumeView;
import com.nexusbank.userservice.client.dto.LoanStatsView;
import com.nexusbank.userservice.client.dto.TransactionStatsView;
import com.nexusbank.userservice.dto.response.AdminStatsResponse;
import com.nexusbank.userservice.dto.response.DailyVolumeResponse;
import com.nexusbank.userservice.repository.AdminRepository;
import com.nexusbank.userservice.repository.CustomerRepository;
import com.nexusbank.userservice.repository.LoanOfficerRepository;
import com.nexusbank.userservice.repository.TellerRepository;
import com.nexusbank.userservice.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final TellerRepository tellerRepository;
    private final LoanOfficerRepository loanOfficerRepository;
    private final AdminRepository adminRepository;
    private final StatsClient statsClient;

    public AdminService(UserRepository userRepository,
                        CustomerRepository customerRepository,
                        TellerRepository tellerRepository,
                        LoanOfficerRepository loanOfficerRepository,
                        AdminRepository adminRepository,
                        StatsClient statsClient) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.tellerRepository = tellerRepository;
        this.loanOfficerRepository = loanOfficerRepository;
        this.adminRepository = adminRepository;
        this.statsClient = statsClient;
    }

    public AdminStatsResponse getUserStats() {
        AdminStatsResponse stats = new AdminStatsResponse();

        // ── Users (local) ───────────────────────────────────────────
        stats.setTotalUsers(userRepository.count());
        stats.setTotalCustomers(customerRepository.count());
        stats.setTotalTellers(tellerRepository.count());
        stats.setTotalLoanOfficers(loanOfficerRepository.count());
        stats.setTotalAdmins(adminRepository.count());
        stats.setActiveUsers(userRepository.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .count());

        // ── Accounts (account-service) ──────────────────────────────
        AccountStatsView accounts = statsClient.fetchAccountStats();
        if (accounts != null) {
            stats.setTotalAccounts(accounts.getTotalAccounts());
            stats.setActiveAccounts(accounts.getActiveAccounts());
            stats.setCheckingAccounts(accounts.getCheckingAccounts());
            stats.setSavingsAccounts(accounts.getSavingsAccounts());
            stats.setForeignAccounts(accounts.getForeignAccounts());
        }

        // ── Transactions (transaction-service) ──────────────────────
        TransactionStatsView transactions = statsClient.fetchTransactionStats();
        if (transactions != null) {
            stats.setTransactionsToday(transactions.getTransactionsToday());
            stats.setTotalTransactions(transactions.getTotalTransactions());
            stats.setVolumeLast7Days(mapVolume(transactions.getLast7Days()));
        } else {
            stats.setVolumeLast7Days(Collections.emptyList());
        }

        // ── Loans (loan-service) ────────────────────────────────────
        LoanStatsView loans = statsClient.fetchLoanStats();
        if (loans != null) {
            stats.setTotalLoans(loans.getTotalLoans());
            stats.setPendingLoans(loans.getPendingLoans());
            stats.setApprovedLoans(loans.getApprovedLoans());
            stats.setDisbursedLoans(loans.getDisbursedLoans());
            stats.setRejectedLoans(loans.getRejectedLoans());
        }

        return stats;
    }

    private List<DailyVolumeResponse> mapVolume(List<DailyVolumeView> series) {
        if (series == null) {
            return Collections.emptyList();
        }
        return series.stream()
                .map(v -> new DailyVolumeResponse(v.getDate(), v.getCount(), v.getTotalAmount()))
                .toList();
    }
}
