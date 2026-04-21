package storage

import (
	"bufio"
	"encoding/binary"
	"fmt"
	"hash/crc32"
	"log"
	"math"
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
	if err := os.MkdirAll(filepath.Dir(path), 0750); err != nil {
		return nil, err
	}

	cleanPath := filepath.Clean(path)
	// #nosec G304 -- path is sanitized via filepath.Clean before use
	file, err := os.OpenFile(cleanPath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0600)
	if err != nil {
		return nil, err
	}

	// Get current file size for tracking
	info, err := file.Stat()
	if err != nil {
		if closeErr := file.Close(); closeErr != nil {
			log.Printf("Error closing WAL file: %v", closeErr)
		}
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
	// Safe conversion: Timestamp is always positive (set via time.Now().UnixNano())
	buf = binary.LittleEndian.AppendUint64(buf, uint64(entry.Timestamp)) // #nosec G115
	buf = append(buf, byte(entry.Op))
	// Bounds check: WAL key length is realistically bounded
	if len(entry.Key) > math.MaxUint32 {
		return 0, fmt.Errorf("WAL key length %d exceeds uint32 max", len(entry.Key))
	}
	buf = binary.LittleEndian.AppendUint32(buf, uint32(len(entry.Key))) // #nosec G115 -- bounds checked above
	buf = append(buf, entry.Key...)
	// Bounds check: WAL value length is realistically bounded
	if len(entry.Value) > math.MaxUint32 {
		return 0, fmt.Errorf("WAL value length %d exceeds uint32 max", len(entry.Value))
	}
	buf = binary.LittleEndian.AppendUint32(buf, uint32(len(entry.Value))) // #nosec G115 -- bounds checked above
	buf = append(buf, entry.Value...)
	buf = binary.LittleEndian.AppendUint32(buf, entry.CRC)

	n, err := w.writer.Write(buf)
	return n, err
}

func (w *WAL) calculateCRC(entry WALEntry) uint32 {
	h := crc32.NewIEEE()
	// Include ALL fields in CRC calculation
	timestampBytes := make([]byte, 8)
	// Safe conversion: Timestamp is always positive (set via time.Now().UnixNano())
	binary.LittleEndian.PutUint64(timestampBytes, uint64(entry.Timestamp)) // #nosec G115
	if _, err := h.Write(timestampBytes); err != nil {
		log.Printf("Error writing timestamp to CRC: %v", err)
	}
	if _, err := h.Write([]byte{byte(entry.Op)}); err != nil {
		log.Printf("Error writing op to CRC: %v", err)
	}
	if _, err := h.Write([]byte(entry.Key)); err != nil {
		log.Printf("Error writing key to CRC: %v", err)
	}
	if _, err := h.Write(entry.Value); err != nil {
		log.Printf("Error writing value to CRC: %v", err)
	}
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
	// Flush current file
	if err := w.flushLocked(); err != nil {
		return err
	}

	oldFile := w.file

	// Rename current WAL to .old
	oldPath := w.path + ".old"
	if err := os.Rename(w.path, oldPath); err != nil {
		return err
	}

	// Create new WAL file
	file, err := os.OpenFile(w.path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0600)
	if err != nil {
		// Recovery: try to rename old file back and reopen it
		if renameErr := os.Rename(oldPath, w.path); renameErr == nil {
			if reopenFile, reopenErr := os.OpenFile(w.path, os.O_WRONLY|os.O_APPEND, 0600); reopenErr == nil {
				w.file = reopenFile
				w.writer = bufio.NewWriterSize(reopenFile, 64*1024)
			}
		}
		return fmt.Errorf("failed to create new WAL file: %w", err)
	}

	if err := oldFile.Close(); err != nil {
		log.Printf("Error closing old WAL file: %v", err)
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
