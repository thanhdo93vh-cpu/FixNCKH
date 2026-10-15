package com.example.hauiTrash.iot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IotOpenResponseDTO {
    private String binCode;
    private boolean commandSent;
    private boolean arduinoAck;
    private String ackStatus;
    private String status;
}
