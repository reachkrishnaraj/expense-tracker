package com.expensetracker.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptDto {

    private UUID id;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private LocalDateTime createdAt;
}
