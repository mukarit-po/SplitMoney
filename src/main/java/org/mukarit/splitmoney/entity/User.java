package org.mukarit.splitmoney.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    private Long id; // Telegram ID
    private String name;
    private String username; // Telegram Username (optional)
    private Long currentGroupId; // Current active group
}
