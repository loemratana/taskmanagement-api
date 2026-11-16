package com.ratana.task_management_api.user.entity;

import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.organization.entity.Position;
import com.ratana.task_management_api.rbac.entity.Role;


import com.ratana.task_management_api.user.enums.UserStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@NamedEntityGraph(
        name = "User.withRolesAndPermissions",
        attributeNodes = {
                @NamedAttributeNode(value = "roles",subgraph = "rolesWithPermissions")
        }
        ,
        subgraphs = {
                @NamedSubgraph(
                        name = "rolesWithPermissions",
                        attributeNodes = @NamedAttributeNode("permissions")
                )
        }
)
public class User  {
    @Id
    @GeneratedValue(generator = "uuid2")
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(length = 255, nullable = false, unique = true)
    private String email;

    @Column(name = "full_name", length = 200, nullable = false)
    private String fullName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "password_hash", columnDefinition = "TEXT")
    private String passwordHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private Position position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "is_active")
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;


    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    /**
     * Single definition of "may sign in and use the API". {@code status} is authoritative;
     * {@code is_active} is still honoured so rows deactivated before status was kept in sync stay locked out.
     */
    public boolean isAccountEnabled() {
        return status == UserStatus.ACTIVE && active;
    }

}
