const net = require('net');

console.log('Testing proxy with delays...');

// Wait 5 seconds for shards to register
setTimeout(() => {
    console.log('Connecting to proxy...');
    
    const socket = net.createConnection({ host: 'localhost', port: 25577 });
    
    socket.on('connect', () => {
        console.log('Connected to proxy');
    });
    
    socket.on('data', (data) => {
        console.log('Received', data.length, 'bytes');
        console.log('First bytes:', data.slice(0, 20));
    });
    
    socket.on('error', (err) => {
        console.error('Error:', err.message);
    });
    
    socket.on('close', () => {
        console.log('Connection closed');
    });
    
    // Send Minecraft handshake packet
    // Packet format: [length] [packet_id] [protocol_version] [server_address] [server_port] [next_state]
    setTimeout(() => {
        console.log('Sending handshake...');
        
        // Simple handshake packet
        const protocolVersion = 769; // 1.21.2
        const serverAddress = 'localhost';
        const serverPort = 25577;
        const nextState = 2; // Login
        
        // Build packet
        const addressBytes = Buffer.from(serverAddress, 'utf8');
        const packet = Buffer.alloc(1 + 1 + addressBytes.length + 2 + 1 + 1);
        let offset = 0;
        
        // This is a simplified version - real Minecraft uses VarInt encoding
        packet.writeUInt8(0x00, offset++); // Packet ID
        packet.writeUInt8(protocolVersion, offset++);
        packet.writeUInt8(addressBytes.length, offset++);
        addressBytes.copy(packet, offset);
        offset += addressBytes.length;
        packet.writeUInt16BE(serverPort, offset);
        offset += 2;
        packet.writeUInt8(nextState, offset++);
        
        const length = packet.length;
        const finalPacket = Buffer.concat([Buffer.from([length]), packet]);
        
        socket.write(finalPacket);
        
        setTimeout(() => {
            socket.end();
        }, 3000);
    }, 1000);
    
}, 5000);
