const net = require('net');

console.log('Testing proxy data forwarding...\n');

// Connect to proxy and wait for data from server
const socket = net.createConnection({ host: 'localhost', port: 25577 });

let receivedData = false;

socket.on('connect', () => {
    console.log('Connected to proxy');
    console.log('Waiting for server data (Minecraft status response)...');
});

socket.on('data', (data) => {
    receivedData = true;
    console.log('Received data from server:', data.length, 'bytes');
    console.log('First 20 bytes:', data.slice(0, 20));
    
    // Close after receiving data
    socket.end();
});

socket.on('error', (err) => {
    console.error('Error:', err.message);
});

socket.on('close', () => {
    console.log('Connection closed');
    if (!receivedData) {
        console.log('⚠️  No data received from server');
    }
});

// Wait up to 10 seconds for data
setTimeout(() => {
    if (!receivedData) {
        console.log('❌ No data received after 10 seconds');
        socket.destroy();
    }
}, 10000);
