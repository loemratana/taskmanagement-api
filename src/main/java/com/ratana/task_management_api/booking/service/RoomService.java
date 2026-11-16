package com.ratana.task_management_api.booking.service;

import com.ratana.task_management_api.booking.dto.request.RoomFilterRequest;
import com.ratana.task_management_api.booking.dto.request.RoomRequest;
import com.ratana.task_management_api.common.pagination.PageResponse;
import com.ratana.task_management_api.booking.dto.response.RoomCalendarResponse;
import com.ratana.task_management_api.booking.dto.response.RoomResponse;
import com.ratana.task_management_api.booking.dto.response.RoomScheduleResponse;
import com.ratana.task_management_api.booking.enums.RoomFacility;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public interface RoomService {

    RoomResponse create(RoomRequest request);

    /** Response carries warnings when the change affects future bookings. */
    RoomResponse update(UUID id, RoomRequest request);

    RoomResponse get(UUID id);

    PageResponse<RoomResponse> search(RoomFilterRequest filter, Pageable pageable);

    /** Only rooms that were never booked; otherwise set status INACTIVE. */
    void delete(UUID id);

    RoomResponse uploadImages(UUID roomId, List<MultipartFile> files);

    RoomResponse setCoverImage(UUID roomId, UUID imageId);

    RoomResponse reorderImages(UUID roomId, List<UUID> imageIds);

    RoomResponse deleteImage(UUID roomId, UUID imageId);

    RoomScheduleResponse schedule(UUID roomId, LocalDate date);

    /**
     * Booked and free slots per room per day, {@code from} to {@code to} inclusive (max 31 days).
     * With {@code roomId}: that room only (any status). Otherwise: active rooms matching the filters.
     */
    RoomCalendarResponse calendar(LocalDate from, LocalDate to, UUID roomId,
                                  Integer minCapacity, RoomFacility facility);

    /** Active rooms free for the whole window, with at least {@code minCapacity} seats. */
    List<RoomResponse> findAvailable(LocalDate date, LocalTime startTime, LocalTime endTime,
                                     Integer minCapacity, RoomFacility facility);
}
