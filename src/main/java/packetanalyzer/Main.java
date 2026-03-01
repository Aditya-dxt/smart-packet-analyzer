package packetanalyzer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.pcap4j.core.PacketListener;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.ArpPacket;
import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.TcpPacket;
import org.pcap4j.packet.UdpPacket;

import javafx.application.Platform;
import packetanalyzer.ui.App;
import packetanalyzer.ui.LiveDataBus;

public class Main {

    // ================= CONTROL =================
    private static AtomicBoolean captureRunning = new AtomicBoolean(false);
    private static PcapHandle handle;

    // ================= STATS =================
    public static AtomicInteger tcpCount = new AtomicInteger();
    public static AtomicInteger udpCount = new AtomicInteger();
    public static AtomicInteger dnsCount = new AtomicInteger();
    public static AtomicInteger arpCount = new AtomicInteger();

    static Map<String, Integer> ipTraffic = new ConcurrentHashMap<>();
    static Map<String, String> ipToDomain = new ConcurrentHashMap<>();

    static long totalBytes = 0;
    static long startTime = System.currentTimeMillis();
    private static long lastSecondBytes = 0;
    private static long previousBytes = 0;

    // =====================================================
    public static void startAnalyzer() throws Exception {

        if (captureRunning.get()) return;

        captureRunning.set(true);
        startTime = System.currentTimeMillis();
        totalBytes = 0;

        System.out.println("=== Capture Started ===");

        // ================= SPEED THREAD =================
        new Thread(() -> {
            try {
                while (captureRunning.get()) {

                    Thread.sleep(1000);

                    lastSecondBytes = totalBytes - previousBytes;
                    previousBytes = totalBytes;

                    double speed = lastSecondBytes / 1024.0;
                    LiveDataBus.publishSpeed(speed);
                }
            } catch (Exception ignored) {}
        }).start();

        // ================= DEVICE SELECTION =================
        List<PcapNetworkInterface> allDevs = Pcaps.findAllDevs();

        if (allDevs == null || allDevs.isEmpty()) {
            System.out.println("❌ No network devices found.");
            return;
        }

        PcapNetworkInterface nif = null;

        for (PcapNetworkInterface dev : allDevs) {
            try {
                if (!dev.isLoopBack() && !dev.getAddresses().isEmpty()) {
                    nif = dev;
                    break;
                }
            } catch (Exception ignored) {}
        }

        if (nif == null) {
            throw new IllegalStateException("No active network interface found");
        }

        System.out.println("✅ Using device: " + nif.getDescription());

        // ================= OPEN HANDLE =================
        handle = nif.openLive(
                65536,
                PcapNetworkInterface.PromiscuousMode.PROMISCUOUS,
                10
        );

        PacketListener listener = packet -> {
            if (captureRunning.get()) {
                analyzePacket(packet);
            }
        };

        while (captureRunning.get()) {
            handle.loop(1, listener);
        }

        handle.close();
        System.out.println("=== Capture Stopped ===");
    }

    // =====================================================
    public static void stopAnalyzer() {
        captureRunning.set(false);
    }

    // =====================================================
    private static void analyzePacket(Packet packet) {

        totalBytes += packet.length();

        if (packet.contains(ArpPacket.class)) {
            arpCount.incrementAndGet();
        }

        if (packet.contains(UdpPacket.class)) {
            udpCount.incrementAndGet();
            handleUDP(packet);
        }

        if (packet.contains(TcpPacket.class)) {
            tcpCount.incrementAndGet();
            handleTCP(packet);
        }

        updateUIStats();
    }

    // ================= TCP ====================
    private static void handleTCP(Packet packet) {

        IpPacket ip = packet.get(IpPacket.class);
        TcpPacket tcp = packet.get(TcpPacket.class);

        if (ip == null || tcp == null) return;

        String src = ip.getHeader().getSrcAddr().getHostAddress();
        String dst = ip.getHeader().getDstAddr().getHostAddress();

        int srcPort = tcp.getHeader().getSrcPort().valueAsInt();
        int dstPort = tcp.getHeader().getDstPort().valueAsInt();

        trackIP(src);

        String service = detectService(src, dst);

        if (srcPort == 443 || dstPort == 443) {
            LiveDataBus.addPacket(
                    new packetanalyzer.ui.PacketRow("TCP", src, dst, service)
            );
        }
    }

    // ================= UDP ====================
    private static void handleUDP(Packet packet) {

        IpPacket ip = packet.get(IpPacket.class);
        UdpPacket udp = packet.get(UdpPacket.class);

        if (ip == null || udp == null) return;

        String src = ip.getHeader().getSrcAddr().getHostAddress();
        String dst = ip.getHeader().getDstAddr().getHostAddress();

        int srcPort = udp.getHeader().getSrcPort().valueAsInt();
        int dstPort = udp.getHeader().getDstPort().valueAsInt();

        trackIP(src);

        if (srcPort == 53 || dstPort == 53) {
            dnsCount.incrementAndGet();
            learnDomainFromDNS(packet);
        }

        LiveDataBus.addPacket(
                new packetanalyzer.ui.PacketRow("UDP", src, dst, "Internet")
        );
    }

    // ================= DNS LEARNING ====================
    private static void learnDomainFromDNS(Packet packet) {
        try {
            String payload = new String(packet.getRawData()).toLowerCase();

            IpPacket ip = packet.get(IpPacket.class);
            if (ip == null) return;

            String srcIP = ip.getHeader().getSrcAddr().getHostAddress();

            String domain = null;

            if (payload.contains("google")) domain = "google.com";
            else if (payload.contains("github")) domain = "github.com";

            if (domain != null) {
                ipToDomain.put(srcIP, domain);
                LiveDataBus.addWebsite(domain);
            }

        } catch (Exception ignored) {}
    }

    // ================= SERVICE DETECTION ====================
    private static String detectService(String src, String dst) {

        if (src.startsWith("140.82") || dst.startsWith("140.82"))
            return "GitHub";

        if (src.startsWith("142.250") || dst.startsWith("142.250")
                || src.startsWith("74.125") || dst.startsWith("74.125"))
            return "Google/YouTube";

        if (src.startsWith("192.168") || dst.startsWith("192.168"))
            return "Local Network";

        return "Internet";
    }

    private static void trackIP(String ip) {
        ipTraffic.merge(ip, 1, Integer::sum);
    }

    private static void updateUIStats() {

        Platform.runLater(() -> {

            App.tcpLabel.setText("TCP: " + tcpCount.get());
            App.udpLabel.setText("UDP: " + udpCount.get());
            App.dnsLabel.setText("DNS: " + dnsCount.get());

            long now = System.currentTimeMillis();
            double seconds = (now - startTime) / 1000.0;

            if (seconds > 0) {
                double speed = (totalBytes / 1024.0) / seconds;
                App.speedLabel.setText(
                        String.format("Speed: %.2f KB/s", speed)
                );
            }
        });
    }
}