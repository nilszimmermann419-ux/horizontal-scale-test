const net = require('net');

function testConnection(name, port) {
    return new Promise((resolve) => {
        console.log(`\nTesting ${name} (port ${port})...`);
        const startTime = Date.now();
        
        const socket = net.createConnection({ host: 'localhost', port: port });
        
        socket.on('connect', () => {
            console.log(`✅ ${name}: Connected in ${Date.now() - startTime}ms`);
            
            // Send some data
            socket.write(Buffer.from([0x10, 0x00]));
            
            setTimeout(() => {
                socket.end();
                resolve(true);
            }, 1000);
        });
        
        socket.on('data', (data) => {
            console.log(`📥 ${name}: Received ${data.length} bytes`);
        });
        
        socket.on('error', (err) => {
            console.error(`❌ ${name}: Error - ${err.message}`);
            resolve(false);
        });
        
        socket.on('close', () => {
            console.log(`🔒 ${name}: Connection closed`);
        });
        
        setTimeout(() => {
            console.error(`❌ ${name}: Timeout`);
            socket.destroy();
            resolve(false);
        }, 5000);
    });
}

async function run() {
    const directResult = await testConnection('Direct', 25565);
    await new Promise(r => setTimeout(r, 1000));
    const proxyResult = await testConnection('Proxy', 25577);
    
    if (directResult && proxyResult) {
        console.log('\n✅ PASS: Both connections succeeded');
        process.exit(0);
    } else {
        console.log('\n❌ FAIL: One or more connections failed');
        process.exit(1);
    }
}

run();
