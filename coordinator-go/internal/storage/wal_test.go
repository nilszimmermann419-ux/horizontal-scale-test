package storage

import (
	"encoding/binary"
	"fmt"
	"hash/crc32"
	"os"
	"path/filepath"
	"sync"
	"testing"
	"time"
)

func TestNewWAL(t *testing.T) {
	tmpDir := t.TempDir()
	path := filepath.Join(tmpDir, "test.wal")

	wal, err := NewWAL(path, 100*time.Millisecond)
	if err != nil {
		t.Fatalf("Failed to create WAL: %v", err)
	}
	defer wal.Close()

	if wal.path != path {
		t.Errorf("Expected path %s, got %s", path, wal.path)
	}

	// Verify file was created
	if _, err := os.Stat(path); os.IsNotExist(err) {
		t.Error("WAL file was not created")
	}
}

func TestWALAppend(t *testing.T) {
	tmpDir := t.TempDir()
	path := filepath.Join(tmpDir, "test.wal")

	wal, err := NewWAL(path, 100*time.Millisecond)
	if err != nil {
		t.Fatalf("Failed to create WAL: %v", err)
	}
	defer wal.Close()

	key := "test:chunk:0:0"
	value := []byte("chunk data")

	err = wal.Append(WALOpSet, key, value)
	if err != nil {
		t.Fatalf("Failed to append entry: %v", err)
	}

	// Force flush to ensure data is written
	err = wal.Flush()
	if err != nil {
		t.Fatalf("Failed to flush WAL: %v", err)
	}

	// Read back and verify
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("Failed to read WAL file: %v", err)
	}

	if len(data) == 0 {
		t.Error("WAL file is empty after append")
	}

	// Verify entry structure
	offset := 0
	timestamp := binary.LittleEndian.Uint64(data[offset : offset+8])
	offset += 8
	if timestamp == 0 {
		t.Error("Timestamp is zero")
	}

	op := data[offset]
	offset += 1
	if WALOp(op) != WALOpSet {
		t.Errorf("Expected op %d, got %d", WALOpSet, op)
	}

	keyLen := binary.LittleEndian.Uint32(data[offset : offset+4])
	offset += 4
	readKey := string(data[offset : offset+int(keyLen)])
	offset += int(keyLen)
	if readKey != key {
		t.Errorf("Expected key %s, got %s", key, readKey)
	}

	valueLen := binary.LittleEndian.Uint32(data[offset : offset+4])
	offset += 4
	readValue := data[offset : offset+int(valueLen)]
	offset += int(valueLen)
	if string(readValue) != string(value) {
		t.Errorf("Expected value %s, got %s", string(value), string(readValue))
	}

	storedCRC := binary.LittleEndian.Uint32(data[offset : offset+4])

	// Verify CRC
	h := crc32.NewIEEE()
	h.Write([]byte(key))
	h.Write(value)
	expectedCRC := h.Sum32()

	if storedCRC != expectedCRC {
		t.Errorf("CRC mismatch: expected %d, got %d", expectedCRC, storedCRC)
	}
}

func TestWALDelete(t *testing.T) {
	tmpDir := t.TempDir()
	path := filepath.Join(tmpDir, "test.wal")

	wal, err := NewWAL(path, 100*time.Millisecond)
	if err != nil {
		t.Fatalf("Failed to create WAL: %v", err)
	}
	defer wal.Close()

	key := "test:chunk:0:0"

	err = wal.Append(WALOpDelete, key, nil)
	if err != nil {
		t.Fatalf("Failed to append delete entry: %v", err)
	}

	err = wal.Flush()
	if err != nil {
		t.Fatalf("Failed to flush WAL: %v", err)
	}

	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("Failed to read WAL file: %v", err)
	}

	offset := 8 // skip timestamp
	op := data[offset]
	if WALOp(op) != WALOpDelete {
		t.Errorf("Expected op %d, got %d", WALOpDelete, op)
	}
}

func TestWALBackgroundFlush(t *testing.T) {
	tmpDir := t.TempDir()
	path := filepath.Join(tmpDir, "test.wal")

	wal, err := NewWAL(path, 50*time.Millisecond)
	if err != nil {
		t.Fatalf("Failed to create WAL: %v", err)
	}
	defer wal.Close()

	key := "test:chunk:0:0"
	value := []byte("chunk data")

	err = wal.Append(WALOpSet, key, value)
	if err != nil {
		t.Fatalf("Failed to append entry: %v", err)
	}

	// Wait for background flush
	time.Sleep(150 * time.Millisecond)

	// Verify data was flushed
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("Failed to read WAL file: %v", err)
	}

	if len(data) == 0 {
		t.Error("WAL file is empty after background flush")
	}
}

func TestWALThreadSafety(t *testing.T) {
	tmpDir := t.TempDir()
	path := filepath.Join(tmpDir, "test.wal")

	wal, err := NewWAL(path, 50*time.Millisecond)
	if err != nil {
		t.Fatalf("Failed to create WAL: %v", err)
	}
	defer wal.Close()

	var wg sync.WaitGroup
	numGoroutines := 100
	numEntries := 100

	for i := 0; i < numGoroutines; i++ {
		wg.Add(1)
		go func(id int) {
			defer wg.Done()
			for j := 0; j < numEntries; j++ {
				key := fmt.Sprintf("goroutine:%d:entry:%d", id, j)
				value := []byte(fmt.Sprintf("value-%d-%d", id, j))
				if err := wal.Append(WALOpSet, key, value); err != nil {
					t.Errorf("Failed to append: %v", err)
				}
			}
		}(i)
	}

	wg.Wait()

	// Flush and verify
	err = wal.Flush()
	if err != nil {
		t.Fatalf("Failed to flush WAL: %v", err)
	}

	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("Failed to read WAL file: %v", err)
	}

	if len(data) == 0 {
		t.Error("WAL file is empty after concurrent writes")
	}

	// We should have numGoroutines * numEntries entries
	// Each entry is at least 17 bytes (8 timestamp + 1 op + 4 key len + 4 value len)
	// So total size should be significant
	expectedMinSize := numGoroutines * numEntries * 17
	if len(data) < expectedMinSize {
		t.Errorf("WAL file too small: expected at least %d bytes, got %d", expectedMinSize, len(data))
	}
}

func TestWALClose(t *testing.T) {
	tmpDir := t.TempDir()
	path := filepath.Join(tmpDir, "test.wal")

	wal, err := NewWAL(path, 100*time.Millisecond)
	if err != nil {
		t.Fatalf("Failed to create WAL: %v", err)
	}

	key := "test:chunk:0:0"
	value := []byte("chunk data")

	err = wal.Append(WALOpSet, key, value)
	if err != nil {
		t.Fatalf("Failed to append entry: %v", err)
	}

	err = wal.Close()
	if err != nil {
		t.Fatalf("Failed to close WAL: %v", err)
	}

	// Verify data was flushed on close
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("Failed to read WAL file: %v", err)
	}

	if len(data) == 0 {
		t.Error("WAL file is empty after close")
	}

	// Verify we can close multiple times without panic (idempotent)
	err = wal.Close()
	if err != nil {
		t.Errorf("Unexpected error on second close: %v", err)
	}
}

func TestWALMultipleEntries(t *testing.T) {
	tmpDir := t.TempDir()
	path := filepath.Join(tmpDir, "test.wal")

	wal, err := NewWAL(path, 100*time.Millisecond)
	if err != nil {
		t.Fatalf("Failed to create WAL: %v", err)
	}
	defer wal.Close()

	entries := []struct {
		op    WALOp
		key   string
		value []byte
	}{
		{WALOpSet, "chunk:0:0", []byte("data1")},
		{WALOpSet, "chunk:0:1", []byte("data2")},
		{WALOpDelete, "chunk:0:0", nil},
		{WALOpSet, "player:abc", []byte("player data")},
	}

	for _, entry := range entries {
		if err := wal.Append(entry.op, entry.key, entry.value); err != nil {
			t.Fatalf("Failed to append entry: %v", err)
		}
	}

	if err := wal.Flush(); err != nil {
		t.Fatalf("Failed to flush: %v", err)
	}

	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("Failed to read WAL file: %v", err)
	}

	// Parse all entries and verify count
	count := 0
	offset := 0
	for offset < len(data) {
		if offset+8 > len(data) {
			break
		}
		binary.LittleEndian.Uint64(data[offset : offset+8])
		offset += 8

		if offset+1 > len(data) {
			break
		}
		offset += 1 // op

		if offset+4 > len(data) {
			break
		}
		keyLen := binary.LittleEndian.Uint32(data[offset : offset+4])
		offset += 4 + int(keyLen)

		if offset+4 > len(data) {
			break
		}
		valueLen := binary.LittleEndian.Uint32(data[offset : offset+4])
		offset += 4 + int(valueLen)

		if offset+4 > len(data) {
			break
		}
		offset += 4 // CRC

		count++
	}

	if count != len(entries) {
		t.Errorf("Expected %d entries, found %d", len(entries), count)
	}
}
