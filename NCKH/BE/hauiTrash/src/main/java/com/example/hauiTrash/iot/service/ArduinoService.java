package com.example.hauiTrash.iot.service;

import com.fazecast.jSerialComm.SerialPort;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class ArduinoService {

    private static final long READY_TIMEOUT_MS = 3_500;
    private static final long ACK_TIMEOUT_MS = 2_000;

    public record SerialCommandResult(
            boolean commandSent,
            boolean arduinoAck,
            String ackStatus
    ) {}

    @Value("${arduino.port:COM3}")
    private String portName;

    @Value("${arduino.baud-rate:9600}")
    private int baudRate;

    @Value("${arduino.enabled:true}")
    private boolean enabled;

    private SerialPort serialPort;
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Phân tích label/material từ AI -> gửi lệnh tương ứng sang Arduino.
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
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = Normalizer
                .normalize(raw.toLowerCase().trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll(
                        "d\\u0300|d\\u0301|d\\u0303|d\\u0309|d\\u0323|\\u0111",
                        "d"
                );

        if (normalized.contains("plastic")
                || normalized.contains("nhua")
                || normalized.contains("pet")
                || normalized.contains("hdpe")
                || normalized.contains("pp")) {
            return "plastic";
        }

        if (normalized.contains("metal")
                || normalized.contains("kim loai")
                || normalized.contains("kimloai")
                || normalized.contains("nhom")
                || normalized.contains("sat")
                || normalized.contains("thep")
                || normalized.contains("lon")) {
            return "metal";
        }

        if (normalized.contains("glass")
                || normalized.contains("thuy tinh")
                || normalized.contains("thuytinh")) {
            return "glass";
        }

        if (normalized.contains("paper")
                || normalized.contains("giay")
                || normalized.contains("carton")
                || normalized.contains("cardboard")
                || normalized.contains("bia")) {
            return "paper";
        }

        return null;
    }

    /**
     * Gửi command tới Arduino.
     *
     * Latency của mỗi trial được đo từ thời điểm bắt đầu gửi command
     * tới thời điểm backend nhận được "OPENED" từ Arduino.
     */
    private SerialCommandResult send(String command) {

        lock.lock();

        try {
            boolean newlyOpened = openIfNeeded();

            if (serialPort == null || !serialPort.isOpen()) {
                log.error(
                        "Serial port {} is not open, cannot send '{}'",
                        portName,
                        command
                );

                return new SerialCommandResult(
                        false,
                        false,
                        "SERIAL_UNAVAILABLE"
                );
            }

            /*
             * Nếu COM vừa được mở, chờ Arduino reset và gửi READY.
             * Thời gian READY không được tính vào latency của trial.
             */
            if (newlyOpened && !waitForLine("READY", READY_TIMEOUT_MS)) {
                log.warn(
                        "Arduino READY timeout on {} after {} ms; sending command anyway",
                        portName,
                        READY_TIMEOUT_MS
                );
            }

            String payload = command + "\n";
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);

            log.info(
                    "Arduino TX: {} | expectedBytes={}",
                    command,
                    bytes.length
            );

            /*
             * ==============================
             * BẮT ĐẦU ĐO LATENCY CỦA TRIAL
             * ==============================
             */
            long startNs = System.nanoTime();

            int written = serialPort.writeBytes(
                    bytes,
                    bytes.length
            );

            log.info(
                    "Arduino TX result: {} | writtenBytes={}/{}",
                    command,
                    written,
                    bytes.length
            );

            if (written != bytes.length) {

                log.warn(
                        "Incomplete serial write for '{}' on {}: {}/{} bytes",
                        command,
                        portName,
                        written,
                        bytes.length
                );

                return new SerialCommandResult(
                        false,
                        false,
                        "SERIAL_WRITE_FAILED"
                );
            }

            /*
             * Chờ Arduino gửi OPENED.
             * Khi nhận OPENED, waitForAck() sẽ tính latency.
             */
            boolean ack = waitForAck(
                    command,
                    ACK_TIMEOUT_MS,
                    startNs
            );

            if (!ack) {
                log.warn(
                        "Arduino ACK timeout for {} after {} ms",
                        command,
                        ACK_TIMEOUT_MS
                );
            }

            return new SerialCommandResult(
                    true,
                    ack,
                    ack ? "OPENED_RECEIVED" : "ACK_TIMEOUT"
            );

        } catch (Exception e) {

            log.error(
                    "Failed to send Arduino command '{}': {}",
                    command,
                    e.getMessage()
            );

            closePort();

            return new SerialCommandResult(
                    false,
                    false,
                    "SERIAL_EXCEPTION"
            );

        } finally {
            lock.unlock();
        }
    }

    /**
     * Chờ Arduino phản hồi OPENED.
     *
     * Command echo (ví dụ BIN_03) KHÔNG được coi là ACK hoàn thành.
     * Chỉ khi nhận OPENED mới tính trial thành công.
     */
    private boolean waitForAck(
            String command,
            long timeoutMs,
            long startNs
    ) throws InterruptedException {

        long deadline =
                System.nanoTime() + timeoutMs * 1_000_000L;

        StringBuilder pending = new StringBuilder();

        while (System.nanoTime() < deadline) {

            for (String line : readAvailableLines(pending)) {

                log.info("Arduino RX: {}", line);

                /*
                 * Arduino có thể echo lại:
                 *
                 * BIN_01
                 * BIN_02
                 * BIN_03
                 *
                 * Không dùng echo này để kết thúc trial.
                 */

                if ("OPENED".equals(line)) {

                    /*
                     * ==============================
                     * KẾT THÚC ĐO LATENCY
                     * ==============================
                     */
                    long endNs = System.nanoTime();

                    double latencyMs =
                            (endNs - startNs) / 1_000_000.0;

                    double latencySec =
                            latencyMs / 1000.0;

                    log.info(
                            "ARDUINO TRIAL | bin={} | latency={} ms | {} s",
                            command,
                            String.format("%.2f", latencyMs),
                            String.format("%.3f", latencySec)
                    );

                    return true;
                }
            }

            Thread.sleep(10);
        }

        return false;
    }

    /**
     * Chờ một dòng cụ thể từ Arduino, ví dụ READY.
     */
    private boolean waitForLine(
            String expected,
            long timeoutMs
    ) throws InterruptedException {

        long deadline =
                System.nanoTime() + timeoutMs * 1_000_000L;

        StringBuilder pending = new StringBuilder();

        while (System.nanoTime() < deadline) {

            for (String line : readAvailableLines(pending)) {

                log.info("Arduino RX: {}", line);

                if (expected.equals(line)) {
                    return true;
                }
            }

            Thread.sleep(10);
        }

        return false;
    }

    /**
     * Đọc dữ liệu serial và tách theo từng dòng.
     */
    private List<String> readAvailableLines(
            StringBuilder pending
    ) {

        List<String> lines = new ArrayList<>();

        int available = serialPort.bytesAvailable();

        if (available <= 0) {
            return lines;
        }

        byte[] buffer =
                new byte[Math.min(available, 256)];

        int read =
                serialPort.readBytes(
                        buffer,
                        buffer.length
                );

        if (read <= 0) {
            return lines;
        }

        pending.append(
                new String(
                        buffer,
                        0,
                        read,
                        StandardCharsets.UTF_8
                )
        );

        int newline;

        while ((newline = pending.indexOf("\n")) >= 0) {

            String line =
                    pending
                            .substring(0, newline)
                            .replace("\r", "")
                            .trim();

            pending.delete(
                    0,
                    newline + 1
            );

            if (!line.isEmpty()) {
                lines.add(line);
            }
        }

        return lines;
    }

    /**
     * Gửi mã BIN và kiểm tra Arduino có nhận OPENED hay không.
     */
    public boolean sendBinCommandChecked(String binCode) {
        return sendBinCommandCheckedResult(binCode)
                .commandSent();
    }

    public SerialCommandResult sendBinCommandCheckedResult(
            String binCode
    ) {

        if (!"BIN_01".equals(binCode)
                && !"BIN_02".equals(binCode)
                && !"BIN_03".equals(binCode)) {

            throw new IllegalArgumentException(
                    "Invalid binCode"
            );
        }

        return enabled
                ? send(binCode)
                : new SerialCommandResult(
                false,
                false,
                "ARDUINO_DISABLED"
        );
    }

    public String sendBinCommand(String binCode) {

        if (!enabled) {
            log.debug(
                    "Arduino disabled, skipping command for bin: {}",
                    binCode
            );

            return null;
        }

        if (binCode == null || binCode.isBlank()) {
            log.warn("Bin code is empty");
            return null;
        }

        String command =
                binCode.trim().toUpperCase();

        if (!command.equals("BIN_01")
                && !command.equals("BIN_02")
                && !command.equals("BIN_03")) {

            log.warn(
                    "Invalid Arduino bin command: {}",
                    command
            );

            return null;
        }

        send(command);

        return command;
    }

    /**
     * Mở COM nếu chưa mở.
     */
    private boolean openIfNeeded() {

        if (serialPort != null
                && serialPort.isOpen()) {

            return false;
        }

        serialPort =
                SerialPort.getCommPort(portName);

        serialPort.setBaudRate(baudRate);
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(
                SerialPort.ONE_STOP_BIT
        );
        serialPort.setParity(
                SerialPort.NO_PARITY
        );

        serialPort.setComPortTimeouts(
                SerialPort.TIMEOUT_NONBLOCKING,
                0,
                2000
        );

        if (serialPort.openPort()) {

            log.info(
                    "Arduino serial port opened: {} @ {} baud",
                    portName,
                    baudRate
            );

            return true;

        } else {

            log.error(
                    "Cannot open serial port: {}",
                    portName
            );

            serialPort = null;

            return false;
        }
    }

    /**
     * Đóng COM.
     */
    private void closePort() {

        if (serialPort != null
                && serialPort.isOpen()) {

            serialPort.closePort();

            log.info(
                    "Serial port {} closed",
                    portName
            );
        }

        serialPort = null;
    }

    @PreDestroy
    public void destroy() {
        closePort();
    }
}