package packetanalyzer.ui;

import javafx.beans.property.SimpleStringProperty;

public class PacketRow {

    private final SimpleStringProperty protocol;
    private final SimpleStringProperty source;
    private final SimpleStringProperty destination;
    private final SimpleStringProperty service;

    public PacketRow(String protocol, String source,
                     String destination, String service) {

        this.protocol = new SimpleStringProperty(protocol);
        this.source = new SimpleStringProperty(source);
        this.destination = new SimpleStringProperty(destination);
        this.service = new SimpleStringProperty(service);
    }

    public String getProtocol() { return protocol.get(); }
    public String getSource() { return source.get(); }
    public String getDestination() { return destination.get(); }
    public String getService() { return service.get(); }
}