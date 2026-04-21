package connection

import (
	"bytes"
	"net"
	"testing"
	"time"

	"github.com/shardedmc/v2/proxy/pkg/protocol"
)

func TestClientConnection(t *testing.T) {
	// Use net.Pipe for synchronous testing
	serverConn, clientConn := net.Pipe()
	defer serverConn.Close()
	defer clientConn.Close()

	// Create client connection
	client := NewClientConn(clientConn)

	// Verify initial state
	if client.State != StateHandshake {
		t.Errorf("Expected initial state Handshake, got %v", client.State)
	}
}

func TestPacketForwarding(t *testing.T) {
	// Create test server and client
	serverConn, clientConn := net.Pipe()
	defer serverConn.Close()
	defer clientConn.Close()

	client := NewClientConn(clientConn)

	// Test writing a packet
	testPacket := &protocol.Packet{
		ID:   0x00,
		Data: []byte{0x01, 0x02, 0x03},
	}

	// Read in background to avoid deadlock
	readDone := make(chan *protocol.Packet, 1)
	readErr := make(chan error, 1)
	go func() {
		packet, err := protocol.ReadPacket(serverConn)
		if err != nil {
			readErr <- err
			return
		}
		readDone <- packet
	}()

	err := client.WritePacket(testPacket)
	if err != nil {
		t.Fatalf("WritePacket failed: %v", err)
	}

	// Read the packet back from server side
	var readPacket *protocol.Packet
	select {
	case readPacket = <-readDone:
	case err = <-readErr:
		t.Fatalf("ReadPacket failed: %v", err)
	case <-time.After(2 * time.Second):
		t.Fatal("Timeout waiting for packet read")
	}

	if readPacket.ID != testPacket.ID {
		t.Errorf("Expected packet ID 0x00, got 0x%02X", readPacket.ID)
	}

	if !bytes.Equal(readPacket.Data, testPacket.Data) {
		t.Errorf("Packet data mismatch: expected %v, got %v", testPacket.Data, readPacket.Data)
	}
}

func TestHandshakeHandling(t *testing.T) {
	serverConn, clientConn := net.Pipe()
	defer serverConn.Close()
	defer clientConn.Close()

	client := NewClientConn(clientConn)

	// Create a valid handshake packet
	var buf bytes.Buffer

	// Write protocol version (VarInt)
	protocol.WriteVarInt(&buf, 760) // 1.19.1 protocol version

	// Write server address (string as VarInt length + bytes)
	addr := "localhost"
	protocol.WriteVarInt(&buf, int32(len(addr)))
	buf.WriteString(addr)

	// Write port (unsigned short)
	buf.WriteByte(0x63) // 25565 >> 8
	buf.WriteByte(0xDD) // 25565 & 0xFF

	// Write next state (VarInt) - 2 for login
	protocol.WriteVarInt(&buf, 2)

	handshakePacket := &protocol.Packet{
		ID:   protocol.HandshakePacketID,
		Data: buf.Bytes(),
	}

	// Read in background to avoid deadlock
	readDone := make(chan *protocol.Packet, 1)
	readErr := make(chan error, 1)
	go func() {
		packet, err := client.ReadPacket()
		if err != nil {
			readErr <- err
			return
		}
		readDone <- packet
	}()

	// Write packet to server side (simulating client sending)
	err := protocol.WritePacket(serverConn, handshakePacket)
	if err != nil {
		t.Fatalf("Failed to write handshake packet: %v", err)
	}

	// Wait for packet read
	var packet *protocol.Packet
	select {
	case packet = <-readDone:
	case err = <-readErr:
		t.Fatalf("Failed to read packet: %v", err)
	case <-time.After(2 * time.Second):
		t.Fatal("Timeout waiting for packet read")
	}

	err = client.HandleHandshake(packet)
	if err != nil {
		t.Fatalf("HandleHandshake failed: %v", err)
	}

	if client.State != StateLogin {
		t.Errorf("Expected state Login after handshake, got %v", client.State)
	}
}

func TestLoginStartHandling(t *testing.T) {
	serverConn, clientConn := net.Pipe()
	defer serverConn.Close()
	defer clientConn.Close()

	client := NewClientConn(clientConn)
	client.State = StateLogin

	// Create a login start packet
	var buf bytes.Buffer
	username := "TestPlayer"
	protocol.WriteVarInt(&buf, int32(len(username)))
	buf.WriteString(username)

	loginPacket := &protocol.Packet{
		ID:   protocol.LoginStartPacketID,
		Data: buf.Bytes(),
	}

	// Read in background to avoid deadlock
	readDone := make(chan *protocol.Packet, 1)
	readErr := make(chan error, 1)
	go func() {
		packet, err := client.ReadPacket()
		if err != nil {
			readErr <- err
			return
		}
		readDone <- packet
	}()

	// Write packet to server side
	err := protocol.WritePacket(serverConn, loginPacket)
	if err != nil {
		t.Fatalf("Failed to write login packet: %v", err)
	}

	// Wait for packet read
	var packet *protocol.Packet
	select {
	case packet = <-readDone:
	case err = <-readErr:
		t.Fatalf("Failed to read packet: %v", err)
	case <-time.After(2 * time.Second):
		t.Fatal("Timeout waiting for packet read")
	}

	err = client.HandleLoginStart(packet)
	if err != nil {
		t.Fatalf("HandleLoginStart failed: %v", err)
	}

	if client.State != StatePlay {
		t.Errorf("Expected state Play after login, got %v", client.State)
	}

	if client.Username != username {
		t.Errorf("Expected username %s, got %s", username, client.Username)
	}

	if client.UUID == "" {
		t.Error("Expected UUID to be set after login")
	}
}
