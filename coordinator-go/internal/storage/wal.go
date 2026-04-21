package storage

import (
	"bufio"
	"encoding/binary"
	"fmt"
	"hash/crc32"
	"log"
	"os"
	"path/filepath"
	"sync"
	"time"
)

const (
	MaxWALSize = 1 * 1024 * 1024 * 1024 // 1GB max WAL size before rotation
)

// WAL provides write-ahead logging for durability
type WAL struct {
	path      string
	file      *os.File
	writer    *bufio.Writer
	mu        sync.Mutex
	bytesWritten int64

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

	// Get current file size for tracking
	info, err := file.Stat()
	if err != nil {
		file.Close()
		return nil, err
	}

	wal := &WAL{
		path:          path,
		file:          file,
		writer:        bufio.NewWriterSize(file, 64*1024), // 64KB buffer
		flushInterval: flushInterval,
		stopFlush:     make(chan struct{}),
		bytesWritten:  info.Size(),
	}

	// Start background flush
	go wal.backgroundFlush()

	return wal, nil
}

func (w *WAL) Append(op WALOp, key string, value []byte) error {
	w.mu.Lock()
	defer w.mu.Unlock()

	// Check if we need to rotate
	if w.bytesWritten > MaxWALSize {
		if err := w.rotate(); err != nil {
			return fmt.Errorf("failed to rotate WAL: %w", err)
		}
	}

	entry := WALEntry{
		Timestamp: time.Now().UnixNano(),
		Op:        op,
		Key:       key,
		Value:     value,
	}

	// Calculate CRC over ALL fields
	entry.CRC = w.calculateCRC(entry)

	// Write entry
	n, err := w.writeEntry(entry)
	if err != nil {
		return err
	}
	w.bytesWritten += int64(n)

	return nil
}

func (w *WAL) writeEntry(entry WALEntry) (int, error) {
	// Write header
	buf := make([]byte, 0, 64+len(entry.Key)+len(entry.Value))
	buf = binary.LittleEndian.AppendUint64(buf, uint64(entry.Timestamp))
	buf = append(buf, byte(entry.Op))
	buf = binary.LittleEndian.AppendUint32(buf, uint32(len(entry.Key)))
	buf = append(buf, entry.Key...)
	buf = binary.LittleEndian.AppendUint32(buf, uint32(len(entry.Value)))
	buf = append(buf, entry.Value...)
	buf = binary.LittleEndian.AppendUint32(buf, entry.CRC)

	n, err := w.writer.Write(buf)
	return n, err
}

func (w *WAL) calculateCRC(entry WALEntry) uint32 {
	h := crc32.NewIEEE()
	// Include ALL fields in CRC calculation
	timestampBytes := make([]byte, 8)
	binary.LittleEndian.PutUint64(timestampBytes, uint64(entry.Timestamp))
	h.Write(timestampBytes)
	h.Write([]byte{byte(entry.Op)})
	h.Write([]byte(entry.Key))
	h.Write(entry.Value)
	return h.Sum32()
}

func (w *WAL) Flush() error {
	w.mu.Lock()
	defer w.mu.Unlock()
	return w.flushLocked()
}

func (w *WAL) flushLocked() error {
	if err := w.writer.Flush(); err != nil {
		return err
	}
	// Sync to disk for durability
	if err := w.file.Sync(); err != nil {
		return fmt.Errorf("failed to sync WAL: %w", err)
	}
	return nil
}

func (w *WAL) backgroundFlush() {
	ticker := time.NewTicker(w.flushInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ticker.C:
			if err := w.Flush(); err != nil {
				// Log error but don't crash - WAL should continue operating
				// In production, this should be reported to a monitoring system
				log.Printf("WAL background flush error: %v", err)
			}
		case <-w.stopFlush:
			if err := w.Flush(); err != nil {
				log.Printf("WAL final flush error: %v", err)
			}
			return
		}
	}
}

func (w *WAL) rotate() error {
	// Flush and close current file
	if err := w.flushLocked(); err != nil {
		return err
	}

	// Close current file
	if err := w.file.Close(); err != nil {
		return err
	}

	// Rename current WAL to .old
	oldPath := w.path + ".old"
	if err := os.Rename(w.path, oldPath); err != nil {
		return err
	}

	// Create new WAL file
	file, err := os.OpenFile(w.path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0644)
	if err != nil {
		return err
	}

	w.file = file
	w.writer = bufio.NewWriterSize(file, 64*1024)
	w.bytesWritten = 0

	return nil
}

func (w *WAL) Close() error {
	var err error
	w.closeOnce.Do(func() {
		close(w.stopFlush)
		if flushErr := w.Flush(); flushErr != nil {
			err = flushErr
		}
		if closeErr := w.file.Close(); closeErr != nil && err == nil {
			err = closeErr
		}
	})
	return err
}
