package com.ratana.task_management_api.auth.service;

import com.ratana.task_management_api.auth.dto.request.ForgotPasswordRequest;
import com.ratana.task_management_api.auth.dto.request.ResetPasswordRequest;

public interface PasswordResetService {

    /**
     * Emails a 6-digit reset code if the address belongs to an active user. Completes silently
     * otherwise, so the endpoint can't be used to discover which emails exist.
     */
    void requestReset(ForgotPasswordRequest request, String clientIp);

    /** Sets a new password if the code is valid, then signs the user out on every device. */
    void resetPassword(ResetPasswordRequest request);

    /** Housekeeping for the cleanup scheduler. Returns how many rows were deleted. */
    int deleteExpiredCodes();
}
