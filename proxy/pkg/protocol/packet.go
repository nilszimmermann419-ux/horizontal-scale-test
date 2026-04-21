package protocol

import (
	"bytes"
	"errors"
	"io"
)

const (
	HandshakePacketID  = 0x00
	LoginStartPacketID = 0x00
	JoinGamePacketID   = 0x26
)

type Packet struct {
	Length int32
	ID     int32
	Data   []byte
}

func ReadVarInt(r io.ByteReader) (int32, error) {
	var value int32
	var position uint8
	for {
		b, err := r.ReadByte()
		if err != nil {
			return 0, err
		}
		value |= int32(b&0x7F) << position
		if b&0x80 == 0 {
			return value, nil
		}
		position += 7
		if position >= 32 {
			return 0, errors.New("VarInt too big")
		}
	}
}

func WriteVarInt(w io.Writer, value int32) error {
	var buf [5]byte
	n := 0
	for {
		b := byte(value & 0x7F)
		value >>= 7
		if value != 0 {
			b |= 0x80
		}
		buf[n] = b
		n++
		if value == 0 {
			break
		}
	}
	_, err := w.Write(buf[:n])
	return err
}

func VarIntSize(value int32) int {
	size := 0
	for {
		size++
		value >>= 7
		if value == 0 {
			break
		}
	}
	return size
}

func ReadPacket(reader io.Reader) (*Packet, error) {
	br, ok := reader.(io.ByteReader)
	if !ok {
		br = &byteReader{r: reader}
	}

	length, err := ReadVarInt(br)
	if err != nil {
		return nil, err
	}

	if length <= 0 {
		return nil, errors.New("invalid packet length")
	}

	data := make([]byte, length)
	if _, err := io.ReadFull(reader, data); err != nil {
		return nil, err
	}

	buf := bytes.NewReader(data)
	packetID, err := ReadVarInt(buf)
	if err != nil {
		return nil, err
	}

	remaining := make([]byte, buf.Len())
	buf.Read(remaining)

	return &Packet{
		Length: length,
		ID:     packetID,
		Data:   remaining,
	}, nil
}

func WritePacket(writer io.Writer, packet *Packet) error {
	idSize := VarIntSize(packet.ID)
	dataSize := len(packet.Data)
	length := int32(idSize + dataSize)

	if err := WriteVarInt(writer, length); err != nil {
		return err
	}
	if err := WriteVarInt(writer, packet.ID); err != nil {
		return err
	}
	if dataSize > 0 {
		if _, err := writer.Write(packet.Data); err != nil {
			return err
		}
	}
	return nil
}

type byteReader struct {
	r io.Reader
	b [1]byte
}

func (br *byteReader) ReadByte() (byte, error) {
	_, err := io.ReadFull(br.r, br.b[:])
	return br.b[0], err
}
