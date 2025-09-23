package com.iflytek.astra.console.toolkit.util.sid;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Short ID generator designed for distributed environments.
 *
 * <p>
 * Output format (example):
 *
 * <pre>
 *   {sub}{pid(2B hex)}{index(2B hex)}@{location}{time(11 hex)}{ipLast2Bytes(4 hex)}{portFirst2Chars}{sid2}
 *   eg: src00ff0001@hf0b2d3a1c2d34ab80
 * </pre>
 *
 * Composition:
 * <ul>
 * <li><b>sub</b>: Business sub-identifier, passed in during construction; falls back to "src" in
 * {@link #gen()} if blank</li>
 * <li><b>pid</b>: Low 8 bits of current process ID (2-byte hex, zero-padded)</li>
 * <li><b>index</b>: 16-bit auto-increment counter, thread-safe, wraps around on overflow</li>
 * <li><b>location</b>: Identifier for data center/region, no semantic validation</li>
 * <li><b>time</b>: Last 11 hex digits of current millisecond timestamp</li>
 * <li><b>ipLast2Bytes</b>: Last two segments of local IPv4 address (each 1 byte, hex)</li>
 * <li><b>portFirst2Chars</b>: First 2 characters of the port string (for historical
 * compatibility)</li>
 * <li><b>sid2</b>: Fixed suffix "2" (for historical compatibility)</li>
 * </ul>
 *
 * <p>
 * Thread safety: safe for concurrent usage, counter ensured by {@link AtomicInteger}.
 * </p>
 */
public final class SidGenerator2 {

    /** Historical fixed suffix (kept for backward compatibility) */
    private static final int SID2 = 2;
    /** 16-bit counter mask */
    private static final int COUNTER_MASK = 0xFFFF;
    /** Low 8 bits of process ID (computed once at startup to avoid repeated MXBean parsing) */
    private static final int PID_LOW8 = (int) (ProcessHandle.current().pid() & 0xFF);

    /** 16-bit cyclic counter (thread-safe) */
    private final AtomicInteger index = new AtomicInteger(0);

    /** Business sub-identifier, may be blank, falls back to "src" when generating */
    private String sub;
    /** Data center/region identifier, no semantic validation */
    private final String location;
    /** Last two segments of local IPv4 (2 bytes) as hex string (fixed length 4) */
    private final String shortLocalIP;
    /** Port string (only the first 2 characters are used for concatenation, per historical rule) */
    private final String port;

    /**
     * Construct a SID generator.
     *
     * @param sub Business sub-identifier (nullable/blank allowed, defaults to "src")
     * @param location Location identifier (must not be null or blank)
     * @param localIp Local IPv4 string (only IPv4 supported, e.g., 192.168.1.10)
     * @param localPort Port string (length must be ≥ 4; historical check preserved)
     * @throws UnknownHostException if parsing {@code localIp} fails
     * @throws IllegalArgumentException if {@code location}, {@code localIp}, or {@code localPort} is
     *         invalid
     */
    public SidGenerator2(String sub, String location, String localIp, String localPort)
            throws UnknownHostException {

        // ---------- Parameter validation and normalization ----------
        this.sub = sub == null ? "" : sub.trim();

        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location must not be blank");
        }
        this.location = location;

        // Only IPv4 is supported: take the last two segments as short IP marker
        InetAddress ip = InetAddress.getByName(localIp);
        if (!(ip instanceof Inet4Address)) {
            throw new IllegalArgumentException("Only IPv4 is supported for localIp: " + localIp);
        }
        byte[] ipBytes = ip.getAddress(); // length must be 4
        int ip3 = ipBytes[2] & 0xFF;
        int ip4 = ipBytes[3] & 0xFF;
        this.shortLocalIP = String.format("%02x%02x", ip3, ip4);

        // Historical logic: require port length ≥ 4, use only first 2 chars
        if (localPort == null || localPort.length() < 4) {
            throw new IllegalArgumentException("Bad Port!!");
        }
        this.port = localPort;
    }

    /**
     * Generate the next SID.
     *
     * <p>
     * Lock-free and thread-safe: uses atomic operations only on the 16-bit counter.
     * </p>
     *
     * @return A short ID string following the defined format
     */
    public String gen() {
        // ---------- Fallback when sub is blank ----------
        final String effectiveSub = (this.sub == null || this.sub.isEmpty()) ? "src" : this.sub;

        // ---------- 16-bit auto-increment counter, wraps around ----------
        int next = index.getAndUpdate(prev -> (prev + 1) & COUNTER_MASK);

        // ---------- 11 hex-digit timestamp (last 11 digits) ----------
        long millis = Instant.now().toEpochMilli();
        String hexTime = String.format("%011x", millis); // always 11 digits

        // ---------- Assemble ----------
        // pid: low 8 bits; index: 16 bit; time: last 11 digits; IP: last two segments; port: first 2 chars;
        // suffix: SID2
        return String.format(
                "%s%04x%04x@%s%s%s%s%s",
                effectiveSub,
                PID_LOW8,
                next,
                this.location,
                hexTime.substring(hexTime.length() - 11),
                this.shortLocalIP,
                this.port.substring(0, 2),
                SID2);
    }
}
