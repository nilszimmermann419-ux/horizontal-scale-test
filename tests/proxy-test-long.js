const mineflayer = require('mineflayer');

console.log('Testing connection through proxy (port 25577) with longer timeout...');

const bot = mineflayer.createBot({
    host: 'localhost',
    port: 25577,
    username: 'ProxyTest',
    auth: 'offline',
    checkTimeoutInterval: 120000, // 2 minutes
    connectTimeout: 30000 // 30 seconds
});

bot.on('spawn', () => {
    console.log('✅ SUCCESS: Connected through proxy!');
    bot.end();
    process.exit(0);
});

bot.on('error', (err) => {
    console.error('❌ FAILED:', err.message);
    process.exit(1);
});

bot.on('kicked', (reason) => {
    console.error('❌ KICKED:', reason);
    process.exit(1);
});

setTimeout(() => {
    console.error('❌ FAILED: Connection timeout after 30s');
    process.exit(1);
}, 30000);
