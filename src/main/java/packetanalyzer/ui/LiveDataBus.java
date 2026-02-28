package packetanalyzer.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.collections.ObservableList;

public class LiveDataBus {

    // ================= PACKET TABLE =================

    private static ObservableList<PacketRow> table;

    public static void registerTable(ObservableList<PacketRow> data) {
        table = data;
    }

    public static void addPacket(PacketRow row) {
        if (table == null) return;

        Platform.runLater(() -> table.add(0, row));
    }

    // ================= SPEED GRAPH =================

    private static final List<Consumer<Double>> speedListeners =
            new ArrayList<>();

    public static void registerSpeedListener(Consumer<Double> listener) {
        speedListeners.add(listener);
    }

    public static void publishSpeed(double speed) {
        Platform.runLater(() ->
            speedListeners.forEach(l -> l.accept(speed))
        );
    }

    // ================= WEBSITE EVENTS ================= ⭐ NEW

    private static final List<Consumer<String>> websiteListeners =
            new ArrayList<>();

    public static void registerWebsiteListener(Consumer<String> listener) {
        websiteListeners.add(listener);
    }

    public static void addWebsite(String website) {
        Platform.runLater(() ->
            websiteListeners.forEach(l -> l.accept(website))
        );
    }
}