package connection

import (
	"io"
	"net"
	"sync"
	"time"

	"github.com/shardedmc/v2/proxy/pkg/protocol"
)

type ShardConn struct {
	Conn      net.Conn
	ShardID   string
	reader    io.Reader
	writer    io.Writer
	mu        sync.Mutex
	queue     []*protocol.Packet
	connected bool
	closed    bool
}

func NewShardConn(conn net.Conn, shardID string) *ShardConn {
	return &ShardConn{
		Conn:      conn,
		ShardID:   shardID,
		reader:    conn,
		writer:    conn,
		connected: true,
		queue:     make([]*protocol.Packet, 0),
	}
}

func (s *ShardConn) ReadPacket() (*protocol.Packet, error) {
	return protocol.ReadPacket(s.reader)
}

func (s *ShardConn) WritePacket(packet *protocol.Packet) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if !s.connected {
		s.queue = append(s.queue, packet)
		return nil
	}

	return protocol.WritePacket(s.writer, packet)
}

func (s *ShardConn) FlushQueue() error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if !s.connected {
		return nil
	}

	for _, packet := range s.queue {
		if err := protocol.WritePacket(s.writer, packet); err != nil {
			return err
		}
	}
	s.queue = s.queue[:0]
	return nil
}

func (s *ShardConn) SetConnected(connected bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.connected = connected
}

func (s *ShardConn) IsConnected() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.connected
}

func (s *ShardConn) Close() error {
	s.mu.Lock()
	if s.closed {
		s.mu.Unlock()
		return nil
	}
	s.closed = true
	s.connected = false
	s.mu.Unlock()
	return s.Conn.Close()
}

func (s *ShardConn) IsClosed() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.closed
}

func ForwardPackets(src, dst net.Conn, done <-chan struct{}) error {
	buf := make([]byte, 32*1024)
	for {
		select {
		case <-done:
			return nil
		default:
		}

		src.SetReadDeadline(time.Now().Add(100 * time.Millisecond))
		n, err := src.Read(buf)
		if err != nil {
			if netErr, ok := err.(net.Error); ok && netErr.Timeout() {
				continue
			}
			if err == io.EOF {
				return nil
			}
			return err
		}

		if _, err := dst.Write(buf[:n]); err != nil {
			return err
		}
	}
}
