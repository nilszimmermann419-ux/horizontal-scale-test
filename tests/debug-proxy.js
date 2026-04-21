const net = require('net');

const DEFAULT_HOST = process.env.SHARDEDMC_HOST || 'localhost';
const DEFAULT_PROXY_PORT = parseInt(process.env.SHARDEDMC_PROXY_PORT, 10) || 25577;

console.log('Testing proxy with delays...');

let receivedData = false;

// Wait 5 seconds for shards to register
setTimeout(() => {
    console.log('Connecting to proxy...');

    const socket = net.createConnection({ host: DEFAULT_HOST, port: DEFAULT_PROXY_PORT });
    
    socket.on('connect', () => {
        console.log('Connected to proxy');
    });
    
    socket.on('data', (data) => {
        receivedData = true;
        console.log('Received', data.length, 'bytes');
        console.log('First bytes:', data.slice(0, 20));
    });
    
    socket.on('error', (err) => {
        console.error('Error:', err.message);
        process.exit(1);
    });
    
    socket.on('close', () => {
        console.log('Connection closed');
        if (receivedData) {
            console.log('✅ PASS: Proxy responded with data');
            process.exit(0);
        } else {
            console.log('❌ FAIL: No data received from proxy');
            process.exit(1);
        }
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

// Overall timeout
setTimeout(() => {
    if (!receivedData) {
        console.log('❌ FAIL: Timeout waiting for proxy response');
        process.exit(1);
    }
}, 15000);
