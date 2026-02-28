package packetanalyzer.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.geometry.Pos;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.layout.Priority;

public class App extends Application {

    // ================= LIVE DATA =================
    private static ObservableList<PacketRow> packetData
            = FXCollections.observableArrayList();

    // ================= LABELS =================
    public static Label tcpLabel = new Label("TCP: 0");
    public static Label udpLabel = new Label("UDP: 0");
    public static Label dnsLabel = new Label("DNS: 0");
    public static Label speedLabel = new Label("Speed: 0 KB/s");

    // ================= PIE =================
    private PieChart protocolChart;
    private PieChart.Data tcpSlice = new PieChart.Data("TCP", 0);
    private PieChart.Data udpSlice = new PieChart.Data("UDP", 0);
    private PieChart.Data dnsSlice = new PieChart.Data("DNS", 0);

    // ================= WEBSITE =================
    public static TextArea websiteArea = new TextArea();

    // ================= SPEED GRAPH =================
    private XYChart.Series<Number, Number> speedSeries
            = new XYChart.Series<>();
    private int timeIndex = 0;

    // ==================================================
    @Override
    public void start(Stage stage) {

        stage.setTitle("Smart Packet Analyzer");

        // ================= STATS =================
        VBox statsBox = new VBox(10,
                new Label("Protocol Statistics"),
                tcpLabel,
                udpLabel,
                dnsLabel,
                speedLabel
        );

        // ================= PIE CHART =================
        protocolChart = new PieChart(
                FXCollections.observableArrayList(
                        tcpSlice,
                        udpSlice,
                        dnsSlice
                )
        );

        protocolChart.setTitle("Protocol Distribution");
        protocolChart.setLabelsVisible(true);
        protocolChart.setLegendVisible(true);

        // ⭐ IMPORTANT SIZE FIX
        protocolChart.setMinHeight(260);
        protocolChart.setPrefHeight(250);
        protocolChart.setMaxHeight(Double.MAX_VALUE);

        // ================= SPEED GRAPH =================
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Time");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("KB/s");

        LineChart<Number, Number> speedChart
                = new LineChart<>(xAxis, yAxis);

        speedChart.setTitle("Live Bandwidth");
        speedChart.setPrefHeight(200);
        speedChart.setAnimated(false);

        speedSeries.setName("Speed");
        speedChart.getData().add(speedSeries);

        // ================= LIVE DATA LISTENER =================
        LiveDataBus.registerSpeedListener(speed -> {

            // ----- Speed Label -----
            speedLabel.setText(
                    String.format("Speed: %.2f KB/s", speed)
            );

            // ----- Line Graph -----
            speedSeries.getData().add(
                    new XYChart.Data<>(timeIndex++, speed)
            );

            if (speedSeries.getData().size() > 30) {
                speedSeries.getData().remove(0);
            }

            // ----- Pie Chart -----
            tcpSlice.setPieValue(packetanalyzer.Main.tcpCount.get());
            udpSlice.setPieValue(packetanalyzer.Main.udpCount.get());
            dnsSlice.setPieValue(packetanalyzer.Main.dnsCount.get());
        });

        // ================= TABLE =================
        TableView<PacketRow> table = new TableView<>(packetData);
        table.setPrefHeight(400);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<PacketRow, String> protocolCol
                = new TableColumn<>("Protocol");
        protocolCol.setCellValueFactory(
                new PropertyValueFactory<>("protocol"));

        TableColumn<PacketRow, String> srcCol
                = new TableColumn<>("Source");
        srcCol.setCellValueFactory(
                new PropertyValueFactory<>("source"));

        TableColumn<PacketRow, String> dstCol
                = new TableColumn<>("Destination");
        dstCol.setCellValueFactory(
                new PropertyValueFactory<>("destination"));

        TableColumn<PacketRow, String> serviceCol
                = new TableColumn<>("Service");
        serviceCol.setCellValueFactory(
                new PropertyValueFactory<>("service"));

        table.getColumns().addAll(
                protocolCol, srcCol, dstCol, serviceCol
        );

        // Row coloring
        table.setRowFactory(tv -> {
            TableRow<PacketRow> row = new TableRow<>();

            row.itemProperty().addListener((obs, o, n) -> {

                if (n == null) {
                    row.setStyle("");
                    return;
                }

                switch (n.getProtocol()) {
                    case "TCP":
                        row.setStyle("-fx-background-color:#e3f2fd;");
                        break;
                    case "UDP":
                        row.setStyle("-fx-background-color:#fff8e1;");
                        break;
                    case "DNS":
                        row.setStyle("-fx-background-color:#e8f5e9;");
                        break;
                    default:
                        row.setStyle("");
                }
            });

            return row;
        });

        LiveDataBus.registerTable(packetData);

        // ================= WEBSITE AREA =================
        websiteArea.setEditable(false);
        websiteArea.setPrefHeight(180);
        VBox.setVgrow(websiteArea, Priority.ALWAYS);

        LiveDataBus.registerWebsiteListener(site
                -> websiteArea.appendText("Visiting: " + site + "\n")
        );

        VBox websiteBox = new VBox(10,
                new Label("Detected Websites"),
                websiteArea
        );

        // ================= BUTTONS =================
        Button startBtn = new Button("Start Capture");
        Button stopBtn = new Button("Stop Capture");
        HBox buttonBar = new HBox(15, startBtn, stopBtn);
        buttonBar.setAlignment(Pos.CENTER_LEFT); // or CENTER

        stopBtn.setDisable(true);

        startBtn.setOnAction(e -> {
            startBtn.setDisable(true);
            stopBtn.setDisable(false);

            new Thread(() -> {
                try {
                    packetanalyzer.Main.startAnalyzer();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }).start();
        });

        stopBtn.setOnAction(e -> {
            packetanalyzer.Main.stopAnalyzer();
            startBtn.setDisable(false);
            stopBtn.setDisable(true);
        });

        // ================= ROOT =================
        VBox root = new VBox(
                20,
                statsBox,
                protocolChart,
                speedChart,
                table,
                websiteBox,
                buttonBar
        );

        root.setStyle("-fx-padding:20; -fx-font-size:14px;");

        // ⭐ MAKE UI SCROLLABLE
        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);

        // optional smooth look
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Scene scene = new Scene(scrollPane, 480, 760);

        stage.setScene(scene);
        stage.show();

        // ================= PIE COLORS =================
        Platform.runLater(() -> {
            tcpSlice.getNode().setStyle("-fx-pie-color:#42a5f5;");
            udpSlice.getNode().setStyle("-fx-pie-color:#ffca28;");
            dnsSlice.getNode().setStyle("-fx-pie-color:#66bb6a;");
        });
    }

    public static void main(String[] args) {
        launch();
    }
}
