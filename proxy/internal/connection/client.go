package connection

import (
	"crypto/rand"
	"fmt"
	"io"
	"net"

	"github.com/shardedmc/v2/proxy/pkg/protocol"
)

type State int

const (
	StateHandshake State = iota
	StateLogin
	StatePlay
)

type ClientConn struct {
	Conn     net.Conn
	State    State
	UUID     string
	Username string
	reader   io.Reader
	writer   io.Writer
}

func NewClientConn(conn net.Conn) *ClientConn {
	return &ClientConn{
		Conn:   conn,
		State:  StateHandshake,
		reader: conn,
		writer: conn,
	}
}

func (c *ClientConn) ReadPacket() (*protocol.Packet, error) {
	return protocol.ReadPacket(c.reader)
}

func (c *ClientConn) WritePacket(packet *protocol.Packet) error {
	return protocol.WritePacket(c.writer, packet)
}

func (c *ClientConn) Close() error {
	return c.Conn.Close()
}

func (c *ClientConn) HandleHandshake(packet *protocol.Packet) error {
	if packet.ID != protocol.HandshakePacketID {
		return fmt.Errorf("expected handshake packet, got 0x%02X", packet.ID)
	}

	if len(packet.Data) < 5 {
		return fmt.Errorf("handshake packet too short")
	}

	buf := &packetReader{data: packet.Data}

	protocolVersion, err := protocol.ReadVarInt(buf)
	if err != nil {
		return fmt.Errorf("read protocol version: %w", err)
	}
	_ = protocolVersion

	serverAddress, err := readString(buf)
	if err != nil {
		return fmt.Errorf("read server address: %w", err)
	}
	_ = serverAddress

	port, err := buf.readUint16()
	if err != nil {
		return fmt.Errorf("read port: %w", err)
	}
	_ = port

	nextState, err := protocol.ReadVarInt(buf)
	if err != nil {
		return fmt.Errorf("read next state: %w", err)
	}

	switch nextState {
	case 1:
		c.State = StateLogin
	case 2:
		c.State = StateLogin
	default:
		return fmt.Errorf("invalid next state: %d", nextState)
	}

	return nil
}

func (c *ClientConn) HandleLoginStart(packet *protocol.Packet) error {
	if packet.ID != protocol.LoginStartPacketID {
		return fmt.Errorf("expected login start packet, got 0x%02X", packet.ID)
	}

	buf := &packetReader{data: packet.Data}
	username, err := readString(buf)
	if err != nil {
		return fmt.Errorf("read username: %w", err)
	}

	c.Username = username
	c.UUID = generateOfflineUUID(username)
	c.State = StatePlay

	return nil
}

func generateOfflineUUID(username string) string {
	b := make([]byte, 16)
	_, err := rand.Read(b)
	if err != nil {
		return "00000000-0000-0000-0000-000000000000"
	}
	b[6] = (b[6] & 0x0F) | 0x40
	b[8] = (b[8] & 0x3F) | 0x80
	return fmt.Sprintf("%08x-%04x-%04x-%04x-%012x",
		b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}

func readString(r *packetReader) (string, error) {
	length, err := protocol.ReadVarInt(r)
	if err != nil {
		return "", err
	}
	if length < 0 || int(length) > len(r.data)-r.pos {
		return "", fmt.Errorf("invalid string length: %d", length)
	}
	str := string(r.data[r.pos : r.pos+int(length)])
	r.pos += int(length)
	return str, nil
}

type packetReader struct {
	data []byte
	pos  int
}

func (r *packetReader) ReadByte() (byte, error) {
	if r.pos >= len(r.data) {
		return 0, io.EOF
	}
	b := r.data[r.pos]
	r.pos++
	return b, nil
}

func (r *packetReader) readUint16() (uint16, error) {
	if r.pos+2 > len(r.data) {
		return 0, io.EOF
	}
	value := uint16(r.data[r.pos])<<8 | uint16(r.data[r.pos+1])
	r.pos += 2
	return value, nil
}
