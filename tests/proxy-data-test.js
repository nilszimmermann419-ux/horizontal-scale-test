const net = require('net');

const DEFAULT_HOST = process.env.SHARDEDMC_HOST || 'localhost';
const DEFAULT_PROXY_PORT = parseInt(process.env.SHARDEDMC_PROXY_PORT, 10) || 25577;

console.log('Testing proxy data forwarding...\n');

// Connect to proxy and wait for data from server
const socket = net.createConnection({ host: DEFAULT_HOST, port: DEFAULT_PROXY_PORT });

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
    process.exit(1);
});

socket.on('close', () => {
    console.log('Connection closed');
    if (receivedData) {
        console.log('✅ PASS: Received data from server');
        process.exit(0);
    } else {
        console.log('❌ FAIL: No data received from server');
        process.exit(1);
    }
});

// Wait up to 10 seconds for data
setTimeout(() => {
    if (!receivedData) {
        console.log('❌ FAIL: No data received after 10 seconds');
        socket.destroy();
        process.exit(1);
    }
}, 10000);
