const mineflayer = require('mineflayer');

console.log('Testing connection through proxy (port 25577)...');

const bot = mineflayer.createBot({
    host: 'localhost',
    port: 25577,
    username: 'ProxyTest',
    auth: 'offline'
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

setTimeout(() => {
    console.error('❌ FAILED: Connection timeout');
    process.exit(1);
}, 10000);
