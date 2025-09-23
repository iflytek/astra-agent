package com.iflytek.astra.console.toolkit.util.ssrf;

import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.exception.BusinessException;
import okhttp3.Dns;

import java.net.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SSRF (Server-Side Request Forgery) protection utility class.
 *
 * <p>
 * Provides multiple validation methods for URL, domain, and IP to defend against SSRF attacks. Can
 * be used together with whitelist, scheme restriction, port restriction, and safe DNS resolution.
 * </p>
 *
 * <p>
 * Thread safety: this is a stateless utility class and is thread-safe.
 * </p>
 *
 * author clliu19
 */
public final class SsrfValidators {

    /** Pattern for valid hostnames (letters, digits, '-', '.', length 1~253) */
    private static final Pattern HOSTNAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9.-]{1,253}$");

    /** Allowed URL schemes */
    private static final Set<String> ALLOWED_SCHEMES =
            new HashSet<>(Arrays.asList("http", "https", "ws", "wss"));

    private SsrfValidators() {}

    /**
     * Check whether the URL scheme is in the allowed list (e.g., http, https).
     *
     * @param scheme URL scheme
     * @param allowed allowed scheme set
     * @return true if allowed, false otherwise
     */
    public static boolean isAllowedScheme(String scheme, Set<String> allowed) {
        return scheme != null && allowed.contains(scheme.toLowerCase(Locale.ROOT));
    }

    /**
     * Check whether the host is a valid hostname (not an IP).
     *
     * @param host hostname
     * @return true if valid hostname, false otherwise
     */
    public static boolean isHostName(String host) {
        return host != null && HOSTNAME_PATTERN.matcher(host).matches() && !host.endsWith(".");
    }

    /**
     * Check whether the host is an IP literal (IPv4 or IPv6).
     *
     * @param host hostname or IP
     * @return true if it is an IP literal, false otherwise
     */
    public static boolean isIpLiteral(String host) {
        if (host == null)
            return false;
        try {
            InetAddress.getByName(host);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    /**
     * Check whether the port is allowed.
     *
     * @param port port number from URL (-1 means default port)
     * @param allowedPorts allowed port set
     * @return true if allowed, false otherwise
     */
    public static boolean portAllowed(int port, Set<Integer> allowedPorts) {
        int p = (port == -1 ? -1 : port);
        if (allowedPorts == null || allowedPorts.isEmpty()) {
            // By default only allow HTTP/HTTPS ports
            return p == -1 || p == 80 || p == 443;
        }
        return p == -1 ? allowedPorts.contains(80) || allowedPorts.contains(443) : allowedPorts.contains(p);
    }

    /**
     * Check whether a domain is in the whitelist (supports wildcard like *.example.com).
     *
     * @param host hostname to validate
     * @param blaklist whitelist entries
     * @param allowSub whether subdomains are allowed
     * @return true if whitelisted, false otherwise
     */
    public static boolean isDomainWhitelisted(String host, List<String> blaklist, boolean allowSub) {
        if (blaklist == null || blaklist.isEmpty())
            return false;
        String h = host == null ? "" : host.toLowerCase(Locale.ROOT);
        for (String rule : blaklist) {
            String r = rule.toLowerCase(Locale.ROOT);
            if (r.startsWith("*.")) { // wildcard
                String suffix = r.substring(1); // ".example.com"
                if (allowSub && h.endsWith(suffix) && h.length() > suffix.length() + 1)
                    return true;
            } else {
                if (h.equals(r))
                    return true;
            }
        }
        return false;
    }

    /**
     * Normalize a URL by removing encoding and fragment part.
     *
     * @param url original URL string
     * @return normalized URL
     * @throws MalformedURLException if URL format is invalid
     * @throws BusinessException if URI syntax is invalid
     */
    public static URL normalize(String url) throws MalformedURLException {
        URL u = new URL(url);
        try {
            URI uri = new URI(u.getProtocol(), u.getUserInfo(), u.getHost(), u.getPort(),
                    u.getPath(), u.getQuery(), null);
            return uri.normalize().toURL();
        } catch (URISyntaxException e) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Bad URL: " + e.getMessage());
        }
    }

    /**
     * Check whether the host hits the IP blacklist.
     *
     * <p>
     * Features:
     * </p>
     * <ul>
     * <li>Host can be domain or IP (IPv4/IPv6), domain resolves all A/AAAA records.</li>
     * <li>Blacklist supports both exact IP and CIDR (e.g., 192.168.0.0/16, fd00::/8).</li>
     * <li>IP canonicalization avoids misjudgment from different notations (::1, 0:0:0:0:0:0:0:1).</li>
     * <li>DNS resolution error is treated as "not hit".</li>
     * </ul>
     *
     * @param host target host (domain or IP, IPv6 can include [])
     * @param ipBlacklist blacklist entries (exact IP or CIDR)
     * @param dns DNS implementation (e.g., SafeDns / Dns.SYSTEM)
     * @return true if in blacklist, false otherwise
     */
    public static boolean isHostBlockedByIpBlacklist(String host, List<String> ipBlacklist, Dns dns) {
        if (host == null || ipBlacklist == null || ipBlacklist.isEmpty())
            return false;

        try {
            // 1) Resolve target IPs
            List<InetAddress> targetIps;
            String literal = normalizeHostLiteral(host); // remove [] and trim
            if (isIpLiteral(literal)) {
                targetIps = Collections.singletonList(InetAddress.getByName(literal));
            } else {
                // Domain: resolve all A/AAAA records; caller can use SafeDns for private net blocking & DNS
                // rebinding defense
                targetIps = resolveAll(dns, literal);
            }
            if (targetIps.isEmpty())
                return false;

            // 2) Preprocess blacklist
            Set<String> exactIpSet = new HashSet<>();
            List<Cidr> cidrList = new ArrayList<>();
            for (String entry : ipBlacklist) {
                if (entry == null || entry.trim().isEmpty())
                    continue;
                String e = entry.trim();
                if (e.contains("/")) {
                    Cidr cidr = parseCidr(e);
                    if (cidr != null)
                        cidrList.add(cidr);
                } else {
                    String canon = canonicalIp(e);
                    if (canon != null)
                        exactIpSet.add(canon);
                }
            }

            // 3) Match each IP
            for (InetAddress ip : targetIps) {
                String canon = canonicalIp(ip);
                if (canon != null && exactIpSet.contains(canon)) {
                    return true;
                }
                for (Cidr cidr : cidrList) {
                    if (ipInCidr(ip, cidr)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignore) {
            // Resolution failure / DNS exception treated as not hit; stricter handling can return true
        }
        return false;
    }

    /** Remove brackets from IPv6 literals and trim. */
    private static String normalizeHostLiteral(String host) {
        String h = host.trim();
        if (h.startsWith("[") && h.endsWith("]")) {
            return h.substring(1, h.length() - 1);
        }
        return h;
    }

    /** Generate canonical IP string for IPv4/IPv6; return null if invalid. */
    private static String canonicalIp(String ipText) {
        try {
            return canonicalIp(InetAddress.getByName(ipText));
        } catch (Exception e) {
            return null;
        }
    }

    /** Generate canonical IP string for IPv4/IPv6; return null if invalid. */
    private static String canonicalIp(InetAddress addr) {
        try {
            String raw = addr.getHostAddress();
            int percent = raw.indexOf('%');
            if (percent >= 0)
                raw = raw.substring(0, percent);
            if (raw.startsWith("::ffff:")) {
                return raw.substring(7);
            }
            return raw;
        } catch (Exception e) {
            return null;
        }
    }

    /** Parse CIDR text for IPv4/IPv6; return null if invalid. */
    private static Cidr parseCidr(String cidrText) {
        try {
            String s = cidrText.trim();
            int slash = s.indexOf('/');
            if (slash <= 0 || slash == s.length() - 1)
                return null;

            String base = s.substring(0, slash).trim();
            int prefix = Integer.parseInt(s.substring(slash + 1).trim());

            InetAddress baseAddr = InetAddress.getByName(base);
            int maxBits = baseAddr.getAddress().length * 8;
            if (prefix < 0 || prefix > maxBits)
                return null;

            return new Cidr(baseAddr, prefix);
        } catch (Exception e) {
            return null;
        }
    }

    /** Check if given IP falls within CIDR range. */
    private static boolean ipInCidr(InetAddress ip, Cidr cidr) {
        byte[] ipBytes = ip.getAddress();
        byte[] netBytes = cidr.network.getAddress();
        if (ipBytes.length != netBytes.length)
            return false;

        int fullBytes = cidr.prefix / 8;
        int remainBits = cidr.prefix % 8;

        for (int i = 0; i < fullBytes; i++) {
            if (ipBytes[i] != netBytes[i])
                return false;
        }
        if (remainBits == 0)
            return true;

        int mask = 0xFF << (8 - remainBits);
        return (ipBytes[fullBytes] & mask) == (netBytes[fullBytes] & mask);
    }

    /** Simple CIDR struct. */
    private static final class Cidr {
        final InetAddress network;
        final int prefix;

        Cidr(InetAddress network, int prefix) {
            this.network = network;
            this.prefix = prefix;
        }
    }

    /**
     * Remove userInfo from URL (prevent bypass with user:pass@host).
     *
     * @param url original URL string
     * @return URL string without userInfo
     */
    public static String stripUserInfo(String url) {
        try {
            URL u = new URL(url);
            try {
                URI uri = new URI(u.getProtocol(), null, u.getHost(), u.getPort(),
                        u.getPath(), u.getQuery(), null);
                return uri.toString();
            } catch (URISyntaxException e) {
                return url;
            }
        } catch (MalformedURLException e) {
            return url;
        }
    }

    /**
     * Resolve host using given DNS implementation and return all IPs (deduplicated).
     *
     * @param dns custom DNS implementation (e.g., SafeDns)
     * @param host hostname to resolve
     * @return list of resolved IP addresses
     * @throws UnknownHostException if host cannot be resolved
     */
    public static List<InetAddress> resolveAll(Dns dns, String host) throws UnknownHostException {
        List<InetAddress> addrs = dns.lookup(host);
        return addrs.stream().distinct().collect(Collectors.toList());
    }

    /** Container for normalized URL with original scheme info. */
    public static final class Normalized {
        /** Converted URL (ws→http, wss→https for parsing/validation) */
        public final URL effectiveUrl;
        /** Original scheme (ws/wss/http/https, null if unsupported) */
        public final String originalScheme;
        /** Whether it was mapped from ws/wss */
        public final boolean wsLike;

        Normalized(URL effectiveUrl, String originalScheme, boolean wsLike) {
            this.effectiveUrl = effectiveUrl;
            this.originalScheme = originalScheme;
            this.wsLike = wsLike;
        }
    }

    /**
     * Normalize URL while supporting ws/wss.
     *
     * <p>
     * Steps:
     * </p>
     * <ul>
     * <li>Temporarily map ws→http, wss→https for URI parsing and SSRF checks.</li>
     * <li>Retain original scheme for restoring to frontend or persistence.</li>
     * <li>Normalize with IDN-to-ASCII for internationalized domain names.</li>
     * </ul>
     *
     * @param raw raw URL string
     * @return normalized wrapper containing effective URL and scheme info
     * @throws MalformedURLException if parsing or normalization fails
     */
    public static Normalized normalizeFlex(String raw) throws MalformedURLException {
        if (raw == null)
            throw new MalformedURLException("URL is null");
        String s = raw.trim();
        int sep = s.indexOf("://");
        if (sep <= 0)
            throw new MalformedURLException("Missing or bad scheme");

        String originalScheme = s.substring(0, sep).toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(originalScheme)) {
            throw new MalformedURLException("Unsupported scheme: " + originalScheme);
        }

        boolean wsLike = "ws".equals(originalScheme) || "wss".equals(originalScheme);
        String mappedScheme = "ws".equals(originalScheme) ? "http"
                : "wss".equals(originalScheme) ? "https"
                        : originalScheme;

        String rest = s.substring(sep + 3);
        String toParse = mappedScheme + "://" + rest;

        URL tmp = new URL(toParse);
        String host = tmp.getHost();
        if (host == null || host.isEmpty()) {
            throw new MalformedURLException("Missing host");
        }
        String asciiHost;
        try {
            asciiHost = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED);
            if (asciiHost.isEmpty())
                throw new IllegalArgumentException();
        } catch (Exception e) {
            throw new MalformedURLException("Bad host: " + host);
        }
        int port = tmp.getPort();
        String path = tmp.getPath();
        if (path == null || path.isEmpty())
            path = "/";
        String query = tmp.getQuery();

        try {
            URI uri = new URI(mappedScheme, null, asciiHost, port, path, query, null);
            URL normalized = uri.normalize().toURL();
            return new Normalized(normalized, originalScheme, wsLike);
        } catch (URISyntaxException e) {
            throw new MalformedURLException("Bad URL: " + e.getMessage());
        }
    }

    /**
     * Restore the original scheme from {@link Normalized}.
     *
     * @param effective effective URL
     * @param originalScheme original scheme string
     * @param wsLike whether it was mapped from ws/wss
     * @return original or restored scheme
     */
    public static String rebuildWithOriginalScheme(URL effective, String originalScheme, boolean wsLike) {
        String scheme = effective.getProtocol();
        if (wsLike) {
            if ("http".equalsIgnoreCase(scheme))
                return "ws";
            if ("https".equalsIgnoreCase(scheme))
                return "wss";
        }
        return (originalScheme != null ? originalScheme : scheme);
    }
}
