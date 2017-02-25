package com.example.ericm.stickmancontroller;

/**
 * Created by eric on 10/28/16.
 */

public class Packet {
    private byte packetType;
    private byte[] payload;

    public Packet(byte packetType, byte[] payload) {
        this.packetType = packetType;
        this.payload = payload;
    }

    public byte getPacketType() {
        return packetType;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPacketType(byte packetType) {
        this.packetType = packetType;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }
}
