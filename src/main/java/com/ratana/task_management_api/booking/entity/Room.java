package com.ratana.task_management_api.booking.entity;

import com.ratana.task_management_api.common.entity.BaseEntity;

import com.ratana.task_management_api.booking.enums.RoomFacility;
import com.ratana.task_management_api.booking.enums.RoomStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
public class Room extends BaseEntity {

    @Version
    private Long version = 0L;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 20)
    private String code;

    @Column(length = 200)
    private String location;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Maximum number of people, organizer included. Bookings above this are rejected. */
    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private RoomStatus status = RoomStatus.ACTIVE;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "room_facilities", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "facility", length = 30, nullable = false)
    @Enumerated(EnumType.STRING)
    @BatchSize(size = 50)
    private Set<RoomFacility> facilities = new HashSet<>();

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, createdAt ASC")
    @BatchSize(size = 50)
    private List<RoomImage> images = new ArrayList<>();

    public boolean isBookable() {
        return status == RoomStatus.ACTIVE;
    }
}
