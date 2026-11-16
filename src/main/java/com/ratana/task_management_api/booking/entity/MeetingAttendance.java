package com.ratana.task_management_api.booking.entity;

import com.ratana.task_management_api.common.entity.BaseEntity;
import com.ratana.task_management_api.user.entity.User;

import com.ratana.task_management_api.booking.enums.AttendanceStatus;
import com.ratana.task_management_api.booking.enums.CheckInMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One invitee's attendance at a booking. Kept apart from the booking_attendees join table
 * so editing a booking's invitees never rewrites attendance already recorded.
 */
@Entity
@Table(name = "booking_attendance")
@Getter
@Setter
@NoArgsConstructor
public class MeetingAttendance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private MeetingBooking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private AttendanceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private CheckInMethod method;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marked_by_id")
    private User markedBy;

    @Column(length = 300)
    private String note;
}
