package protocol

import (
	"encoding/binary"
	"errors"
	"math"
)

const MaxPacketSize = 16 * 1024 * 1024 // 16MB max packet size

var (
	ErrBufferUnderflow = errors.New("buffer underflow")
	ErrPacketTooLarge  = errors.New("packet too large")
	ErrInvalidLength   = errors.New("invalid string/bytes length")
)

type PacketDecoder struct {
	buf []byte
	pos int
}

func NewDecoder(buf []byte) (*PacketDecoder, error) {
	if len(buf) > MaxPacketSize {
		return nil, ErrPacketTooLarge
	}
	return &PacketDecoder{
		buf: buf,
		pos: 0,
	}, nil
}

func (d *PacketDecoder) ReadUint8() (uint8, error) {
	if d.pos+1 > len(d.buf) {
		return 0, ErrBufferUnderflow
	}
	v := d.buf[d.pos]
	d.pos++
	return v, nil
}

func (d *PacketDecoder) ReadUint16() (uint16, error) {
	if d.pos+2 > len(d.buf) {
		return 0, ErrBufferUnderflow
	}
	v := binary.LittleEndian.Uint16(d.buf[d.pos:])
	d.pos += 2
	return v, nil
}

func (d *PacketDecoder) ReadUint32() (uint32, error) {
	if d.pos+4 > len(d.buf) {
		return 0, ErrBufferUnderflow
	}
	v := binary.LittleEndian.Uint32(d.buf[d.pos:])
	d.pos += 4
	return v, nil
}

func (d *PacketDecoder) ReadUint64() (uint64, error) {
	if d.pos+8 > len(d.buf) {
		return 0, ErrBufferUnderflow
	}
	v := binary.LittleEndian.Uint64(d.buf[d.pos:])
	d.pos += 8
	return v, nil
}

func (d *PacketDecoder) ReadFloat32() (float32, error) {
	if d.pos+4 > len(d.buf) {
		return 0, ErrBufferUnderflow
	}
	v := binary.LittleEndian.Uint32(d.buf[d.pos:])
	d.pos += 4
	return math.Float32frombits(v), nil
}

func (d *PacketDecoder) ReadFloat64() (float64, error) {
	if d.pos+8 > len(d.buf) {
		return 0, ErrBufferUnderflow
	}
	v := binary.LittleEndian.Uint64(d.buf[d.pos:])
	d.pos += 8
	return math.Float64frombits(v), nil
}

func (d *PacketDecoder) ReadString() (string, error) {
	length, err := d.ReadUint16()
	if err != nil {
		return "", err
	}
	if d.pos+int(length) > len(d.buf) {
		return "", ErrInvalidLength
	}
	v := string(d.buf[d.pos : d.pos+int(length)])
	d.pos += int(length)
	return v, nil
}

func (d *PacketDecoder) ReadBytes() ([]byte, error) {
	length, err := d.ReadUint32()
	if err != nil {
		return nil, err
	}
	if length > uint32(MaxPacketSize) {
		return nil, ErrInvalidLength
	}
	if d.pos+int(length) > len(d.buf) {
		return nil, ErrInvalidLength
	}
	v := make([]byte, length)
	copy(v, d.buf[d.pos:d.pos+int(length)])
	d.pos += int(length)
	return v, nil
}

func (d *PacketDecoder) Remaining() int {
	return len(d.buf) - d.pos
}

func (d *PacketDecoder) Reset(buf []byte) error {
	if len(buf) > MaxPacketSize {
		return ErrPacketTooLarge
	}
	d.buf = buf
	d.pos = 0
	return nil
}
