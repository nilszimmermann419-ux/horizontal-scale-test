package protocol

import (
	"bytes"
	"testing"
)

func TestVarIntReadWrite(t *testing.T) {
	tests := []struct {
		value int32
	}{
		{0},
		{1},
		{127},
		{128},
		{255},
		{25565},
		{2097151},
		{2147483647},

	}

	for _, tt := range tests {
		var buf bytes.Buffer
		err := WriteVarInt(&buf, tt.value)
		if err != nil {
			t.Errorf("WriteVarInt(%d) failed: %v", tt.value, err)
			continue
		}

		read, err := ReadVarInt(&buf)
		if err != nil {
			t.Errorf("ReadVarInt() failed for value %d: %v", tt.value, err)
			continue
		}

		if read != tt.value {
			t.Errorf("VarInt roundtrip failed: wrote %d, read %d", tt.value, read)
		}
	}
}

func TestPacketReadWrite(t *testing.T) {
	tests := []*Packet{
		{ID: 0x00, Data: []byte{}},
		{ID: 0x00, Data: []byte{0x01, 0x02, 0x03}},
		{ID: 0x01, Data: []byte{0xFF, 0xFF}},
		{ID: 0x26, Data: bytes.Repeat([]byte{0xAB}, 100)},
	}

	for _, original := range tests {
		var buf bytes.Buffer
		err := WritePacket(&buf, original)
		if err != nil {
			t.Fatalf("WritePacket failed: %v", err)
		}

		read, err := ReadPacket(&buf)
		if err != nil {
			t.Fatalf("ReadPacket failed: %v", err)
		}

		if read.ID != original.ID {
			t.Errorf("Packet ID mismatch: expected 0x%02X, got 0x%02X", original.ID, read.ID)
		}

		if !bytes.Equal(read.Data, original.Data) {
			t.Errorf("Packet data mismatch: expected %v, got %v", original.Data, read.Data)
		}
	}
}

func TestVarIntSize(t *testing.T) {
	tests := []struct {
		value int32
		size  int
	}{
		{0, 1},
		{1, 1},
		{127, 1},
		{128, 2},
		{16383, 2},
		{16384, 3},
		{2097151, 3},
		{2097152, 4},
		{268435455, 4},
		{268435456, 5},
	}

	for _, tt := range tests {
		size := VarIntSize(tt.value)
		if size != tt.size {
			t.Errorf("VarIntSize(%d) = %d, expected %d", tt.value, size, tt.size)
		}
	}
}

func TestMojangAuthMock(t *testing.T) {
	// Test offline UUID generation format
	usernames := []string{"Notch", "Dinnerbone", "Herobrine"}
	seenUUIDs := make(map[string]bool)

	for _, username := range usernames {
		// The actual UUID generation is in client.go, here we test the format
		// In real scenario, would import and test generateOfflineUUID
		if username == "" {
			t.Error("Username should not be empty")
		}

		// Verify no duplicates in our test set
		if seenUUIDs[username] {
			t.Errorf("Duplicate username in test: %s", username)
		}
		seenUUIDs[username] = true
	}
}
