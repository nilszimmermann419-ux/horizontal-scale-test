package protocol

import (
	"encoding/binary"
	"math"
)

type PacketEncoder struct {
	buf []byte
}

func NewEncoder() *PacketEncoder {
	return &PacketEncoder{
		buf: make([]byte, 0, 4096),
	}
}

func (e *PacketEncoder) WriteUint8(v uint8) {
	e.buf = append(e.buf, v)
}

func (e *PacketEncoder) WriteUint16(v uint16) {
	e.buf = binary.LittleEndian.AppendUint16(e.buf, v)
}

func (e *PacketEncoder) WriteUint32(v uint32) {
	e.buf = binary.LittleEndian.AppendUint32(e.buf, v)
}

func (e *PacketEncoder) WriteUint64(v uint64) {
	e.buf = binary.LittleEndian.AppendUint64(e.buf, v)
}

func (e *PacketEncoder) WriteFloat32(v float32) {
	e.buf = binary.LittleEndian.AppendUint32(e.buf, math.Float32bits(v))
}

func (e *PacketEncoder) WriteFloat64(v float64) {
	e.buf = binary.LittleEndian.AppendUint64(e.buf, math.Float64bits(v))
}

func (e *PacketEncoder) WriteString(v string) {
	e.WriteUint16(uint16(len(v)))
	e.buf = append(e.buf, v...)
}

func (e *PacketEncoder) WriteBytes(v []byte) {
	e.WriteUint32(uint32(len(v)))
	e.buf = append(e.buf, v...)
}

func (e *PacketEncoder) Bytes() []byte {
	return e.buf
}

func (e *PacketEncoder) Reset() {
	e.buf = e.buf[:0]
}
