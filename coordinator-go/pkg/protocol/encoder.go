package protocol

import (
	"encoding/binary"
	"fmt"
	"math"
)

// PacketEncoder encodes primitive types into a byte buffer
// using Minecraft's protocol format

type PacketEncoder struct {
	buf []byte
}

// NewEncoder creates a new packet encoder with a default capacity
func NewEncoder() *PacketEncoder {
	return &PacketEncoder{
		buf: make([]byte, 0, 4096),
	}
}

// WriteUint8 appends an unsigned 8-bit integer
func (e *PacketEncoder) WriteUint8(v uint8) {
	e.buf = append(e.buf, v)
}

// WriteUint16 appends an unsigned 16-bit integer (little-endian)
func (e *PacketEncoder) WriteUint16(v uint16) {
	e.buf = binary.LittleEndian.AppendUint16(e.buf, v)
}

// WriteUint32 appends an unsigned 32-bit integer (little-endian)
func (e *PacketEncoder) WriteUint32(v uint32) {
	e.buf = binary.LittleEndian.AppendUint32(e.buf, v)
}

// WriteUint64 appends an unsigned 64-bit integer (little-endian)
func (e *PacketEncoder) WriteUint64(v uint64) {
	e.buf = binary.LittleEndian.AppendUint64(e.buf, v)
}

// WriteFloat32 appends a 32-bit float (little-endian)
func (e *PacketEncoder) WriteFloat32(v float32) {
	e.buf = binary.LittleEndian.AppendUint32(e.buf, math.Float32bits(v))
}

// WriteFloat64 appends a 64-bit float (little-endian)
func (e *PacketEncoder) WriteFloat64(v float64) {
	e.buf = binary.LittleEndian.AppendUint64(e.buf, math.Float64bits(v))
}

// WriteString appends a length-prefixed UTF-8 string
func (e *PacketEncoder) WriteString(v string) {
	if len(v) > math.MaxUint16 {
		panic(fmt.Sprintf("string length %d exceeds uint16 max", len(v)))
	}
	// #nosec G115 -- bounds checked above
	e.WriteUint16(uint16(len(v)))
	e.buf = append(e.buf, v...)
}

// WriteBytes appends a length-prefixed byte slice
func (e *PacketEncoder) WriteBytes(v []byte) error {
	// Bounds check: Minecraft protocol packets are realistically < 4GB
	if len(v) > math.MaxUint32 {
		return fmt.Errorf("byte slice length %d exceeds uint32 max", len(v))
	}
	// #nosec G115 -- bounds checked above
	e.WriteUint32(uint32(len(v)))
	e.buf = append(e.buf, v...)
	return nil
}

// Bytes returns the encoded byte slice
func (e *PacketEncoder) Bytes() []byte {
	return e.buf
}

// Reset clears the encoder buffer
func (e *PacketEncoder) Reset() {
	e.buf = e.buf[:0]
}
