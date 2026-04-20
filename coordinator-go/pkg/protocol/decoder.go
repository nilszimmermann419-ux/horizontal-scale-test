package protocol

import (
	"encoding/binary"
	"math"
)

type PacketDecoder struct {
	buf []byte
	pos int
}

func NewDecoder(buf []byte) *PacketDecoder {
	return &PacketDecoder{
		buf: buf,
		pos: 0,
	}
}

func (d *PacketDecoder) ReadUint8() uint8 {
	v := d.buf[d.pos]
	d.pos++
	return v
}

func (d *PacketDecoder) ReadUint16() uint16 {
	v := binary.LittleEndian.Uint16(d.buf[d.pos:])
	d.pos += 2
	return v
}

func (d *PacketDecoder) ReadUint32() uint32 {
	v := binary.LittleEndian.Uint32(d.buf[d.pos:])
	d.pos += 4
	return v
}

func (d *PacketDecoder) ReadUint64() uint64 {
	v := binary.LittleEndian.Uint64(d.buf[d.pos:])
	d.pos += 8
	return v
}

func (d *PacketDecoder) ReadFloat32() float32 {
	v := binary.LittleEndian.Uint32(d.buf[d.pos:])
	d.pos += 4
	return math.Float32frombits(v)
}

func (d *PacketDecoder) ReadFloat64() float64 {
	v := binary.LittleEndian.Uint64(d.buf[d.pos:])
	d.pos += 8
	return math.Float64frombits(v)
}

func (d *PacketDecoder) ReadString() string {
	length := d.ReadUint16()
	v := string(d.buf[d.pos : d.pos+int(length)])
	d.pos += int(length)
	return v
}

func (d *PacketDecoder) ReadBytes() []byte {
	length := d.ReadUint32()
	v := d.buf[d.pos : d.pos+int(length)]
	d.pos += int(length)
	return v
}

func (d *PacketDecoder) Remaining() int {
	return len(d.buf) - d.pos
}

func (d *PacketDecoder) Reset(buf []byte) {
	d.buf = buf
	d.pos = 0
}
