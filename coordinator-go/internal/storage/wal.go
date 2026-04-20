package storage

import (
	"bufio"
	"encoding/binary"
	"hash/crc32"
	"os"
	"path/filepath"
	"sync"
	"time"
)

// WAL provides write-ahead logging for durability
type WAL struct {
	path   string
	file   *os.File
	writer *bufio.Writer
	mu     sync.Mutex

	// Background flush
	flushInterval time.Duration
	stopFlush     chan struct{}
	closeOnce     sync.Once
}

type WALEntry struct {
	Timestamp int64
	Op        WALOp
	Key       string
	Value     []byte
	CRC       uint32
}

type WALOp uint8

const (
	WALOpSet WALOp = iota
	WALOpDelete
)

func NewWAL(path string, flushInterval time.Duration) (*WAL, error) {
	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		return nil, err
	}

	file, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0644)
	if err != nil {
		return nil, err
	}

	wal := &WAL{
		path:          path,
		file:          file,
		writer:        bufio.NewWriterSize(file, 64*1024), // 64KB buffer
		flushInterval: flushInterval,
		stopFlush:     make(chan struct{}),
	}

	// Start background flush
	go wal.backgroundFlush()

	return wal, nil
}

func (w *WAL) Append(op WALOp, key string, value []byte) error {
	w.mu.Lock()
	defer w.mu.Unlock()

	entry := WALEntry{
		Timestamp: time.Now().UnixNano(),
		Op:        op,
		Key:       key,
		Value:     value,
	}

	// Calculate CRC
	entry.CRC = w.calculateCRC(entry)

	// Write entry
	if err := w.writeEntry(entry); err != nil {
		return err
	}

	return nil
}

func (w *WAL) writeEntry(entry WALEntry) error {
	// Write header
	buf := make([]byte, 0, 64)
	buf = binary.LittleEndian.AppendUint64(buf, uint64(entry.Timestamp))
	buf = append(buf, byte(entry.Op))
	buf = binary.LittleEndian.AppendUint32(buf, uint32(len(entry.Key)))
	buf = append(buf, entry.Key...)
	buf = binary.LittleEndian.AppendUint32(buf, uint32(len(entry.Value)))
	buf = append(buf, entry.Value...)
	buf = binary.LittleEndian.AppendUint32(buf, entry.CRC)

	_, err := w.writer.Write(buf)
	return err
}

func (w *WAL) calculateCRC(entry WALEntry) uint32 {
	h := crc32.NewIEEE()
	h.Write([]byte(entry.Key))
	h.Write(entry.Value)
	return h.Sum32()
}

func (w *WAL) Flush() error {
	w.mu.Lock()
	defer w.mu.Unlock()
	return w.writer.Flush()
}

func (w *WAL) backgroundFlush() {
	ticker := time.NewTicker(w.flushInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ticker.C:
			w.Flush()
		case <-w.stopFlush:
			w.Flush()
			return
		}
	}
}

func (w *WAL) Close() error {
	var err error
	w.closeOnce.Do(func() {
		close(w.stopFlush)
		w.Flush()
		err = w.file.Close()
	})
	return err
}
