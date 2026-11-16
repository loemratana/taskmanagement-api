package com.ratana.task_management_api.booking.service;

import com.ratana.task_management_api.booking.dto.request.BookingFilterRequest;
import com.ratana.task_management_api.booking.dto.request.BookingPreviewRequest;
import com.ratana.task_management_api.booking.dto.request.BookingRequest;
import com.ratana.task_management_api.booking.dto.response.BookingPreviewResponse;
import com.ratana.task_management_api.booking.dto.response.BookingResponse;
import com.ratana.task_management_api.common.pagination.PageResponse;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface MeetingBookingService {

    BookingResponse create(BookingRequest request, UserPrincipal actor);

    /** Full replace: room, time, title and targets. Only while the meeting is upcoming. */
    BookingResponse update(UUID id, BookingRequest request, UserPrincipal actor);

    BookingResponse cancel(UUID id, String reason, UserPrincipal actor);

    BookingResponse get(UUID id, UserPrincipal actor);

    /** Meetings the actor organizes or attends. */
    PageResponse<BookingResponse> myBookings(BookingFilterRequest filter, Pageable pageable, UserPrincipal actor);

    /** Every booking; requires bookings:view:all. */
    PageResponse<BookingResponse> search(BookingFilterRequest filter, Pageable pageable, UserPrincipal actor);

    BookingPreviewResponse preview(BookingPreviewRequest request, UserPrincipal actor);
}
