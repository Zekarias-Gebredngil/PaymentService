package com.payment.nadi.application.port.in;

import com.payment.nadi.application.dto.request.AdminCreateAccountRequest;
import com.payment.nadi.application.dto.request.ChangePasswordRequest;
import com.payment.nadi.application.dto.request.LoginRequest;
import com.payment.nadi.application.dto.request.RegisterRequest;
import com.payment.nadi.application.dto.response.PageResponse;
import com.payment.nadi.application.dto.response.UserAccountDto;
import com.payment.nadi.common.response.ApiResponse;

import java.util.Map;
import java.util.UUID;

public interface AccountUseCase {

    UserAccountDto register(RegisterRequest request);

    ApiResponse<UserAccountDto> login(LoginRequest request);

    UserAccountDto changePassword(ChangePasswordRequest request);

    UserAccountDto adminRegisterAccount(AdminCreateAccountRequest request);

    PageResponse<UserAccountDto> listAccounts(String status, int page, Integer size, Integer limit, String query, String sortBy, String direction);

    Map<String, Long> getAccountStats();

    UserAccountDto lookupAccount(String query);

    UserAccountDto approveAccount(UUID id);

    UserAccountDto suspendAccount(UUID id);

    UserAccountDto activateAccount(UUID id);
}
