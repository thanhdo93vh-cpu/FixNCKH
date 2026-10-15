package com.example.hauiTrash.iot.service;

import com.fazecast.jSerialComm.SerialPort;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class ArduinoService {

    @Value("${arduino.port:COM3}")
    private String portName;

    @Value("${arduino.baud-rate:9600}")
    private int baudRate;

    @Value("${arduino.enabled:true}")
    private boolean enabled;

    private SerialPort serialPort;
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Phân tích label/material từ AI → gửi lệnh tương ứng sang Arduino.
     * Trả về lệnh đã gửi ("plastic"/"metal"/"glass"/"paper") hoặc null nếu không nhận ra.
     */
    public String sendMaterialCommand(String label) {
        if (!enabled) {
            log.debug("Arduino disabled, skipping command for label: {}", label);
            return null;
        }
        String command = resolveCommand(label);
        if (command == null) {
            log.warn("Cannot map label '{}' to Arduino command", label);
            return null;
        }
        send(command);
        return command;
    }

    private String resolveCommand(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = Normalizer.normalize(raw.toLowerCase().trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("d\\u0300|d\\u0301|d\\u0303|d\\u0309|d\\u0323|\\u0111", "d");

        if (normalized.contains("plastic") || normalized.contains("nhua") || normalized.contains("pet")
                || normalized.contains("hdpe") || normalized.contains("pp")) {
            return "plastic";
        }
        if (normalized.contains("metal") || normalized.contains("kim loai") || normalized.contains("kimloai")
                || normalized.contains("nhom") || normalized.contains("sat") || normalized.contains("thep")
                || normalized.contains("lon")) {
            return "metal";
        }
        if (normalized.contains("glass") || normalized.contains("thuy tinh") || normalized.contains("thuytinh")) {
            return "glass";
        }
        if (normalized.contains("paper") || normalized.contains("giay") || normalized.contains("carton")
                || normalized.contains("cardboard") || normalized.contains("bia")) {
            return "paper";
        }
        return null;
    }

    private void send(String command) {
        lock.lock();
        try {
            openIfNeeded();
            if (serialPort == null || !serialPort.isOpen()) {
                log.error("Serial port {} is not open, cannot send '{}'", portName, command);
                return;
            }
            String payload = command + "\n";
            byte[] bytes = payload.getBytes();
            int written = serialPort.writeBytes(bytes, bytes.length);
            if (written > 0) {
                log.info("Sent Arduino command: '{}' on {}", command, portName);
            } else {
                log.warn("Write returned 0 bytes for command '{}' on {}", command, portName);
            }
        } catch (Exception e) {
            log.error("Failed to send Arduino command '{}': {}", command, e.getMessage());
            closePort();
        } finally {
            lock.unlock();
        }
    }

    private void openIfNeeded() {
        if (serialPort != null && serialPort.isOpen()) return;
        serialPort = SerialPort.getCommPort(portName);
        serialPort.setBaudRate(baudRate);
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
        serialPort.setParity(SerialPort.NO_PARITY);
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, 0, 2000);

        if (serialPort.openPort()) {
            log.info("Arduino serial port opened: {} @ {} baud", portName, baudRate);
            // Chờ Arduino reset sau khi mở cổng (Arduino Uno reset khi có DTR)
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        } else {
            log.error("Cannot open serial port: {}", portName);
            serialPort = null;
        }
    }

    private void closePort() {
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
            log.info("Serial port {} closed", portName);
        }
        serialPort = null;
    }

    @PreDestroy
    public void destroy() {
        closePort();
    }
}
