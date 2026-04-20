const net = require('net');

console.log('Testing raw TCP connection to proxy...');

const socket = net.createConnection({ host: 'localhost', port: 25577 });

socket.on('connect', () => {
    console.log('✅ Connected to proxy');
    
    // Send Minecraft handshake packet (simplified)
    // Just send some bytes to see if proxy forwards
    socket.write(Buffer.from([0x10, 0x00])); // Length + packet ID
    
    setTimeout(() => {
        console.log('Closing connection');
        socket.end();
    }, 2000);
});

socket.on('data', (data) => {
    console.log('Received data:', data.length, 'bytes');
});

socket.on('error', (err) => {
    console.error('❌ Error:', err.message);
});

socket.on('close', () => {
    console.log('Connection closed');
});

setTimeout(() => {
    console.log('Test timeout');
    socket.destroy();
}, 10000);
